#pragma once
#include "include/jni.h"

inline jmethodID FindCurrentGameModeGetter(JNIEnv* env, jclass gameModeClass) {
    if (!env || !gameModeClass || env->ExceptionCheck()) return nullptr;
    const char* names[] = {"getPlayerMode", "m_105295_"};
    for (const char* name : names) {
        jmethodID method = env->GetMethodID(gameModeClass, name,
            "()Lnet/minecraft/world/level/GameType;");
        if (env->ExceptionCheck()) { env->ExceptionClear(); continue; }
        if (method) return method;
    }
    return nullptr;
}
