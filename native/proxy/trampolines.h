

#pragma once

#include "proxy.h"

#include <string>

struct HookBridge {

    jclass    klass       = nullptr;
    jmethodID ctor          = nullptr;
    jmethodID onChannelActive = nullptr;
    jmethodID channelRead   = nullptr;
    jmethodID write         = nullptr;

    std::string internalName;
    std::string dotName;

    bool valid() const { return klass != nullptr; }
};

extern HookBridge g_hook;

bool InstallHookBridge(JNIEnv* env);
