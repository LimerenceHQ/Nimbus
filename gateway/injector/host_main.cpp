#include <windows.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cwchar>
#include <string>

#include "../native/gateway_state.h"

extern "C" BOOL DoInject(DWORD processId, const char* dllFile,
                          char* outMessage, int maxLen);

namespace {

struct HideContext {
    DWORD pid;
    bool hidden;
};

BOOL CALLBACK hideWindowForProcess(HWND hwnd, LPARAM value) {
    auto* context = reinterpret_cast<HideContext*>(value);
    DWORD owner = 0;
    GetWindowThreadProcessId(hwnd, &owner);
    if (owner != context->pid || !IsWindowVisible(hwnd)) return TRUE;
    // Hiding the white client is a presentation concern: the process keeps
    // running normally so its authorised protocol components remain active.
    ShowWindow(hwnd, SW_HIDE);
    context->hidden = true;
    return TRUE;
}

bool hideProcessWindows(DWORD pid, DWORD timeoutMs) {
    const ULONGLONG deadline = GetTickCount64() + timeoutMs;
    do {
        HideContext context{pid, false};
        EnumWindows(hideWindowForProcess, reinterpret_cast<LPARAM>(&context));
        if (context.hidden) return true;
        Sleep(50);
    } while (GetTickCount64() < deadline);
    return false;
}

const char* stageName(LONG stage) {
    switch (stage) {
        case NIMBUS_GATEWAY_INJECTED: return "INJECTED";
        case NIMBUS_GATEWAY_JVM_ATTACHED: return "JVM_ATTACHED";
        case NIMBUS_GATEWAY_HOOK_INSTALLED: return "HOOK_INSTALLED";
        case NIMBUS_GATEWAY_BSERVER_READY: return "BSERVER_READY";
        case NIMBUS_GATEWAY_A_CAPTURED: return "A_CONNECTION_CAPTURED";
        case NIMBUS_GATEWAY_B_ACTIVE: return "BRIDGE_READY";
        case NIMBUS_GATEWAY_FAILED: return "FAILED";
        default: return "CREATED";
    }
}

std::string jsonEscape(const char* value) {
    std::string out;
    if (!value) return out;
    for (const unsigned char ch : std::string(value)) {
        switch (ch) {
            case '\\': out += "\\\\"; break;
            case '"': out += "\\\""; break;
            case '\r': out += "\\r"; break;
            case '\n': out += "\\n"; break;
            case '\t': out += "\\t"; break;
            default:
                if (ch >= 0x20) out += static_cast<char>(ch);
                break;
        }
    }
    return out;
}

void emit(LONG stage, DWORD pid, DWORD port, const char* detail = nullptr) {
    std::printf("{\"event\":\"%s\",\"pid\":%lu,\"port\":%lu",
                stageName(stage), pid, port);
    if (detail && *detail) {
        std::string escaped = jsonEscape(detail);
        std::printf(",\"detail\":\"%s\"", escaped.c_str());
    }
    std::printf("}\n");
    std::fflush(stdout);
}

bool parseDword(const char* value, DWORD& result) {
    if (!value || !*value) return false;
    char* end = nullptr;
    unsigned long parsed = std::strtoul(value, &end, 10);
    if (!end || *end != '\0' || parsed == 0) return false;
    result = static_cast<DWORD>(parsed);
    return true;
}

int usage() {
    std::fprintf(stderr,
        "usage: nimbus-gateway-host --pid <pid> --dll <path> --port <port> "
        "[--timeout <milliseconds>]\n");
    return 2;
}

}

int main(int argc, char** argv) {
    DWORD pid = 0;
    DWORD port = 0;
    DWORD timeoutMs = 60000;
    const char* dll = nullptr;
    for (int i = 1; i + 1 < argc; i += 2) {
        if (std::strcmp(argv[i], "--pid") == 0) parseDword(argv[i + 1], pid);
        else if (std::strcmp(argv[i], "--port") == 0) parseDword(argv[i + 1], port);
        else if (std::strcmp(argv[i], "--timeout") == 0) parseDword(argv[i + 1], timeoutMs);
        else if (std::strcmp(argv[i], "--dll") == 0) dll = argv[i + 1];
        else return usage();
    }
    if (!pid || !port || port > 65535 || !dll) return usage();
    if (GetFileAttributesA(dll) == INVALID_FILE_ATTRIBUTES) {
        emit(NIMBUS_GATEWAY_FAILED, pid, port, "bridge DLL not found");
        return 2;
    }

    wchar_t mappingName[64];
    std::swprintf(mappingName, sizeof(mappingName) / sizeof(mappingName[0]),
                  L"Local\\NimbusGateway-%lu", pid);
    HANDLE mapping = CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr,
        PAGE_READWRITE, 0, sizeof(NimbusGatewaySharedState), mappingName);
    if (!mapping) {
        emit(NIMBUS_GATEWAY_FAILED, pid, port, "unable to create shared state");
        return 3;
    }
    auto* state = static_cast<NimbusGatewaySharedState*>(
        MapViewOfFile(mapping, FILE_MAP_ALL_ACCESS, 0, 0,
                      sizeof(NimbusGatewaySharedState)));
    if (!state) {
        CloseHandle(mapping);
        emit(NIMBUS_GATEWAY_FAILED, pid, port, "unable to map shared state");
        return 3;
    }
    ZeroMemory(state, sizeof(*state));
    state->magic = NIMBUS_GATEWAY_STATE_MAGIC;
    state->version = NIMBUS_GATEWAY_STATE_VERSION;
    state->targetPid = pid;
    state->loopbackPort = port;
    state->stage = NIMBUS_GATEWAY_CREATED;
    emit(NIMBUS_GATEWAY_CREATED, pid, port);

    char message[1024] = {};
    if (!DoInject(pid, dll, message, static_cast<int>(sizeof(message)))) {
        state->stage = NIMBUS_GATEWAY_FAILED;
        strncpy_s(state->error, sizeof(state->error), message, _TRUNCATE);
        emit(NIMBUS_GATEWAY_FAILED, pid, port, message);
        UnmapViewOfFile(state);
        CloseHandle(mapping);
        return 4;
    }

    // The white client remains the real authenticated server connection.  We
    // only remove its visible top-level windows after injection succeeds.
    hideProcessWindows(pid, 5000);

    HANDLE target = OpenProcess(SYNCHRONIZE, FALSE, pid);
    LONG lastStage = NIMBUS_GATEWAY_CREATED;
    ULONGLONG deadline = GetTickCount64() + timeoutMs;
    bool ready = false;
    while (true) {
        LONG stage = state->stage;
        if (stage != lastStage) {
            emit(stage, pid, port, stage == NIMBUS_GATEWAY_FAILED ? state->error : nullptr);
            lastStage = stage;
        }
        if (stage == NIMBUS_GATEWAY_FAILED) break;
        if (stage >= NIMBUS_GATEWAY_BSERVER_READY) ready = true;
        if (!ready && GetTickCount64() >= deadline) {
            emit(NIMBUS_GATEWAY_FAILED, pid, port, "timed out waiting for bridge readiness");
            break;
        }
        if (target && WaitForSingleObject(target, 50) == WAIT_OBJECT_0) {
            emit(NIMBUS_GATEWAY_FAILED, pid, port, "white client exited");
            break;
        }
        Sleep(50);
    }

    if (target) CloseHandle(target);
    UnmapViewOfFile(state);
    CloseHandle(mapping);
    return ready ? 0 : 5;
}
