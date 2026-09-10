#include "proxy.h"
#include "trampolines.h"
#include "relay_handler.h"
#include "connection_hook.h"
#include "b_server.h"

#include <mutex>

namespace {
std::mutex g_initMutex;
bool g_running = false;
unsigned short g_port = 0;
}

int InitializeProxy(JavaVM* vm, unsigned short port) {
    std::lock_guard<std::mutex> lock(g_initMutex);
    if (g_running) return port == g_port ? 0 : 16;
    if (!vm || (g_vm && g_vm != vm)) return 11;
    g_vm = vm;
    if (!g_jvmti && vm->GetEnv(reinterpret_cast<void**>(&g_jvmti), JVMTI_VERSION_1_2) != JNI_OK) return 11;
    JniAttach attach;
    if (!attach) return 11;
    JNIEnv* env = attach.env;
    if (env->PushLocalFrame(256) != JNI_OK) return 11;
    struct Frame { JNIEnv* env; ~Frame() { env->PopLocalFrame(nullptr); } } frame{env};
    if (!InstallHookBridge(env)) { BServer_StopListener(env); return 12; }
    if (!InstallRelayHandler(env)) { BServer_StopListener(env); return 13; }
    // Arm the class-load hook before BServer resolves Connection.  A fresh
    // client can then be transformed on first definition without a later
    // RetransformClasses call.
    if (!InstallConnectionHook(env)) { BServer_StopListener(env); return 14; }
    bool bReady = false;
    for (int attempt = 0; attempt < 30 && !bReady; ++attempt) {
        bReady = InstallBServer(env, port);
        if (!bReady && attempt + 1 < 30) {
            LogTo("attach: B listener initialization pending (attempt %d/30)", attempt + 1);
            Sleep(100);
        }
    }
    if (!bReady) {
        LogTo("attach: B listener bind failed; A hooks were not installed");
        BServer_StopListener(env);
        return 15;
    }
    if (BServer_TryCaptureLiveConnection(env)) {
        LogTo("attach: captured the live A connection; relay armed");
    } else {
        if (!ConnectionHook_CanCaptureFutureConnection()) {
            BServer_StopListener(env);
            return 14;
        }
        LogTo("attach: Connection hook armed for the first A channel");
    }
    g_port = port;
    g_running = true;
    LogTo("attach: A listener ready on 127.0.0.1:%u", static_cast<unsigned>(port));
    return 0;
}

BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) DisableThreadLibraryCalls(module);
    return TRUE;
}
