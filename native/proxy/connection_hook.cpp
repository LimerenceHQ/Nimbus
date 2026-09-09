#include "connection_hook.h"

#include "class_edit.h"
#include "classfile.h"
#include "trampolines.h"

#include <cstring>
#include <atomic>
#include <mutex>
#include <stdexcept>
#include <string>

namespace {

constexpr const char* kConnectionInternal = "net/minecraft/network/Connection";
constexpr const char* kChannelActiveName  = "channelActive";
constexpr const char* kChannelActiveDesc  =
    "(Lio/netty/channel/ChannelHandlerContext;)V";

std::atomic<bool> g_hookArmed{false};
std::atomic<bool> g_hookPatched{false};
std::atomic<bool> g_callbacksRegistered{false};
std::mutex g_callbackMu;

void DisableClassLoadHook(jvmtiEnv* jvmti) {
    if (!jvmti || !g_hookArmed.exchange(false, std::memory_order_acq_rel)) return;
    jvmti->SetEventNotificationMode(
        JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
}

void JNICALL ClassFileLoadHookCallback(
        jvmtiEnv* jvmti,
        JNIEnv*   ,
        jclass    ,
        jobject   ,
        const char* name,
        jobject   ,
        jint      class_data_len,
        const unsigned char* class_data,
        jint*     new_class_data_len,
        unsigned char** new_class_data) {

    if (!name) return;

    if (std::strcmp(name, kConnectionInternal) != 0) return;
    LogTo("ClassFileLoadHook fired for %s (len=%d)", name, (int)class_data_len);
    if (!g_hookArmed.load(std::memory_order_acquire)) return;
    if (!g_hook.valid()) { LogTo("  ...but hook bridge is not ready, skipping"); return; }

    try {
        ClassFileEditor ed(class_data, static_cast<size_t>(class_data_len));

        u2 mref = ed.addMethodRef(
            g_hook.internalName,
            "onChannelActive",
            "(Ljava/lang/Object;Ljava/lang/Object;)V");

        std::vector<u1> insertion = {
            0x2A,
            0x2B,
            0xB8, static_cast<u1>((mref >> 8) & 0xFF), static_cast<u1>(mref & 0xFF),
        };

        bool ok = ed.prependToMethodCode(
            kChannelActiveName, kChannelActiveDesc,
            insertion, 2);
        if (!ok) {
            LogTo("  channelActive(%s) NOT FOUND in %s", kChannelActiveDesc, name);
            return;
        }
        LogTo("  patched channelActive OK, mref=%u", (unsigned)mref);

        std::vector<u1> patched = ed.serialize();
        unsigned char* buf = nullptr;
        if (jvmti->Allocate(static_cast<jlong>(patched.size()), &buf) != JVMTI_ERROR_NONE) {
            LogTo("  jvmti->Allocate failed");
            return;
        }
        std::memcpy(buf, patched.data(), patched.size());
        *new_class_data     = buf;
        *new_class_data_len = static_cast<jint>(patched.size());
        g_hookPatched.store(true, std::memory_order_release);
        // 首次成功改写后关闭事件；后续 A 连接复用已改写的 Connection 类。
        DisableClassLoadHook(jvmti);
        LogTo("  emitted patched bytes: %u -> %u", (unsigned)class_data_len,
              (unsigned)patched.size());
    } catch (const std::exception& e) {
        LogTo("  exception in patcher: %s", e.what());
    } catch (...) {
        LogTo("  unknown exception in patcher");
    }
}

bool EnableCapabilitiesAndCallbacks(jvmtiEnv* jvmti) {
    std::lock_guard<std::mutex> lock(g_callbackMu);
    if (!g_callbacksRegistered.load(std::memory_order_acquire)) {
        jvmtiEventCallbacks cb{};
        cb.ClassFileLoadHook = &ClassFileLoadHookCallback;
        if (jvmti->SetEventCallbacks(&cb, sizeof(cb)) != JVMTI_ERROR_NONE) {
            Dbg("Connection hook: SetEventCallbacks failed");
            return false;
        }
        g_callbacksRegistered.store(true, std::memory_order_release);
    }

    if (jvmti->SetEventNotificationMode(
            JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr)
        != JVMTI_ERROR_NONE) {
        Dbg("Connection hook: SetEventNotificationMode failed");
        return false;
    }
    g_hookArmed.store(true, std::memory_order_release);
    return true;
}

bool ConnectionAlreadyLoaded(JNIEnv* env, jvmtiEnv* jvmti) {
    jint count = 0;
    jclass* classes = nullptr;
    if (jvmti->GetLoadedClasses(&count, &classes) != JVMTI_ERROR_NONE) return false;
    bool found = false;
    for (jint i = 0; i < count; ++i) {
        char* sig = nullptr;
        if (jvmti->GetClassSignature(classes[i], &sig, nullptr) != JVMTI_ERROR_NONE) continue;
        if (sig && std::strcmp(sig, "Lnet/minecraft/network/Connection;") == 0) {
            found = true;
        }
        if (sig) jvmti->Deallocate(reinterpret_cast<unsigned char*>(sig));
    }
    for (jint i = 0; i < count; ++i) env->DeleteLocalRef(classes[i]);
    jvmti->Deallocate(reinterpret_cast<unsigned char*>(classes));
    return found;
}

}

bool InstallConnectionHook(JNIEnv* env) {
    if (!g_jvmti || !env) return false;
    if (g_hookPatched.load(std::memory_order_acquire)) return true;
    if (!g_hook.valid()) {
        Dbg("Connection hook: called before HookBridge installed");
        return false;
    }
    if (!EnableCapabilitiesAndCallbacks(g_jvmti)) return false;
    // Never retransform an already-defined game class.  Existing connections
    // are handled by BServer_TryCaptureLiveConnection; otherwise the caller
    // asks the user to attach earlier on the next launch.
    if (ConnectionAlreadyLoaded(env, g_jvmti)) DisableClassLoadHook(g_jvmti);
    return true;
}

bool ConnectionHook_CanCaptureFutureConnection() {
    return g_hookPatched.load(std::memory_order_acquire) ||
           g_hookArmed.load(std::memory_order_acquire);
}
