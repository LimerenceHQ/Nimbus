#include "gateway_state.h"

#include <cstdio>
#include <cstring>
#include <cwchar>

namespace {
HANDLE g_mapping = nullptr;
NimbusGatewaySharedState* g_state = nullptr;
}

void GatewayState_OpenForCurrentProcess() {
    if (g_state) return;
    wchar_t name[64];
    std::swprintf(name, sizeof(name) / sizeof(name[0]),
                  L"Local\\NimbusGateway-%lu", GetCurrentProcessId());
    g_mapping = OpenFileMappingW(FILE_MAP_ALL_ACCESS, FALSE, name);
    if (!g_mapping) return;
    g_state = static_cast<NimbusGatewaySharedState*>(
        MapViewOfFile(g_mapping, FILE_MAP_ALL_ACCESS, 0, 0,
                      sizeof(NimbusGatewaySharedState)));
    if (!g_state || g_state->magic != NIMBUS_GATEWAY_STATE_MAGIC ||
        g_state->version != NIMBUS_GATEWAY_STATE_VERSION) {
        GatewayState_Close();
    }
}

void GatewayState_Close() {
    if (g_state) UnmapViewOfFile(g_state);
    if (g_mapping) CloseHandle(g_mapping);
    g_state = nullptr;
    g_mapping = nullptr;
}

void GatewayState_SetAtLeast(NimbusGatewayStage stage) {
    if (!g_state || stage < 0) return;
    LONG current = g_state->stage;
    while (current >= 0 && current < static_cast<LONG>(stage)) {
        LONG observed = InterlockedCompareExchange(
            const_cast<LONG*>(&g_state->stage), static_cast<LONG>(stage), current);
        if (observed == current) break;
        current = observed;
    }
}

void GatewayState_Fail(const char* message) {
    if (!g_state) return;
    if (message) {
        strncpy_s(g_state->error, sizeof(g_state->error), message, _TRUNCATE);
    }
    InterlockedExchange(const_cast<LONG*>(&g_state->stage), NIMBUS_GATEWAY_FAILED);
}

unsigned short GatewayState_Port() {
    if (!g_state || g_state->loopbackPort == 0 || g_state->loopbackPort > 65535) {
        return 25565;
    }
    return static_cast<unsigned short>(g_state->loopbackPort);
}
