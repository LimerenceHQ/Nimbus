#include "trampolines.h"

#include "classfile.h"
#include "random_name.h"
#include "relay_handler.h"
#include "b_server.h"

#include <cstdio>
#include <string>

HookBridge g_hook;

namespace {

void JNICALL Native_onChannelActive(JNIEnv* env,
                                    jclass,
                                    jobject connection,
                                    jobject ctx) {
    LogTo("Native_onChannelActive fired: conn=%p ctx=%p",
          (void*)connection, (void*)ctx);

    BServer_SetTargetConnection(env, connection);
    RelayHandler_AttachToPipeline(env, ctx);

    LogTo("Native_onChannelActive returning");
}

}

bool InstallHookBridge(JNIEnv* env) {
    if (g_hook.valid()) return true;
    if (!env) return false;

    jobject mcLoader = GetMinecraftClassLoader(env, g_jvmti);
    if (!mcLoader) {

        return false;
    }

    std::string simple = GenerateRandomClassName(2, 3);
    std::string internal = MakeInternalName(GetTrampolinePackage(), simple);
    std::string dotted   = internal;
    for (char& ch : dotted) if (ch == '/') ch = '.';

    ClassBuilder cb(internal, "io/netty/channel/ChannelDuplexHandler", 52);
    u2 superInitRef = cb.methodRef("io/netty/channel/ChannelDuplexHandler", "<init>", "()V");
    cb.addCodedMethod("<init>", "()V", ACC_PUBLIC, {0x2A, 0xB7, static_cast<u1>((superInitRef >> 8) & 0xFF), static_cast<u1>(superInitRef & 0xFF), 0xB1}, 1, 1);
    cb.addNativeMethod("onChannelActive", "(Ljava/lang/Object;Ljava/lang/Object;)V");
    cb.addNativeMethod("channelRead", "(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V", ACC_PUBLIC | ACC_NATIVE);
    cb.addNativeMethod("write", "(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)V", ACC_PUBLIC | ACC_NATIVE);
    cb.addNativeMethod("channelInactive", "(Lio/netty/channel/ChannelHandlerContext;)V", ACC_PUBLIC | ACC_NATIVE);
    std::vector<u1> bytes = cb.build();

    jclass defined = env->DefineClass(
        internal.c_str(),
        mcLoader,
        reinterpret_cast<const jbyte*>(bytes.data()),
        static_cast<jsize>(bytes.size()));
    if (!defined) {
        LogAndClearException(env, "InstallHookBridge/DefineClass");
        env->DeleteGlobalRef(mcLoader);
        return false;
    }

    JNINativeMethod natives[] = {
        { const_cast<char*>("onChannelActive"),
          const_cast<char*>("(Ljava/lang/Object;Ljava/lang/Object;)V"),
          reinterpret_cast<void*>(&Native_onChannelActive) },
        { const_cast<char*>("channelRead"),
          const_cast<char*>("(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V"),
          reinterpret_cast<void*>(&Native_RelayChannelRead) },
        { const_cast<char*>("write"),
          const_cast<char*>("(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)V"),
          reinterpret_cast<void*>(&Native_RelayWrite) },
        { const_cast<char*>("channelInactive"), const_cast<char*>("(Lio/netty/channel/ChannelHandlerContext;)V"), reinterpret_cast<void*>(&Native_RelayInactive) },
    };
    if (env->RegisterNatives(defined, natives, 4) != 0) {
        LogAndClearException(env, "InstallHookBridge/RegisterNatives");
        env->DeleteLocalRef(defined);
        env->DeleteGlobalRef(mcLoader);
        return false;
    }

    jmethodID onActiveMid =
        env->GetStaticMethodID(defined, "onChannelActive",
                               "(Ljava/lang/Object;Ljava/lang/Object;)V");
    if (!onActiveMid) {
        LogAndClearException(env, "InstallHookBridge/GetStaticMethodID");
        env->DeleteLocalRef(defined);
        env->DeleteGlobalRef(mcLoader);
        return false;
    }

    g_hook.klass = static_cast<jclass>(env->NewGlobalRef(defined));
    g_hook.ctor = env->GetMethodID(defined, "<init>", "()V");
    g_hook.onChannelActive = onActiveMid;
    g_hook.channelRead = env->GetMethodID(defined, "channelRead", "(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V");
    g_hook.write = env->GetMethodID(defined, "write", "(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)V");
    g_hook.internalName = std::move(internal);
    g_hook.dotName      = std::move(dotted);

    env->DeleteLocalRef(defined);
    env->DeleteGlobalRef(mcLoader);
    return true;
}
