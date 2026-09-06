

#pragma once

#include "proxy.h"

#include <string>

struct RelayHandler {
    jclass    klass  = nullptr;
    jmethodID ctor   = nullptr;
    std::string internalName;
    std::string dotName;

    struct NettyRefs {
        jclass    contextCls           = nullptr;
        jmethodID pipelineMid          = nullptr;
        jmethodID fireChannelReadMid   = nullptr;
        jmethodID ctxWriteMid          = nullptr;
        jmethodID ctxWriteFlushMid     = nullptr;

        jclass    pipelineCls          = nullptr;
        jmethodID addFirstMid          = nullptr;
        jmethodID addBeforeMid         = nullptr;
        jmethodID getMid               = nullptr;

        jclass    promiseCls           = nullptr;
        jmethodID promiseSetSuccessMid = nullptr;
    } netty;

    bool valid() const { return klass != nullptr; }
};

extern RelayHandler g_relay;

bool InstallRelayHandler(JNIEnv* env);

void JNICALL Native_RelayChannelRead(JNIEnv* env, jobject self, jobject ctx, jobject msg);
void JNICALL Native_RelayWrite(JNIEnv* env, jobject self, jobject ctx, jobject msg, jobject promise);

void RelayHandler_AttachToPipeline(JNIEnv* env, jobject ctx);

void RelayHandler_AttachToPipelineObject(JNIEnv* env, jobject pipeline);

void JNICALL Native_RelayInactive(JNIEnv* env, jobject self, jobject ctx);
void RelayFilter_MarkBypass(JNIEnv* env, jobject channel, jobject packet);
void RelayFilter_ClearBypass(JNIEnv* env, jobject channel = nullptr);
