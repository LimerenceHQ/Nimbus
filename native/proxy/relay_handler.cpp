#include "relay_handler.h"

#include "b_server.h"
#include "classfile.h"
#include "random_name.h"
#include "world_cache.h"
#include "trampolines.h"

#include <atomic>
#include <chrono>
#include <cstdio>
#include <mutex>
#include <string>
#include <vector>

RelayHandler g_relay;

namespace {

constexpr const char* kSuperInternal     = "io/netty/channel/ChannelDuplexHandler";
constexpr const char* kContextInternal   = "io/netty/channel/ChannelHandlerContext";
constexpr const char* kPipelineInternal  = "io/netty/channel/ChannelPipeline";
constexpr const char* kHandlerInternal   = "io/netty/channel/ChannelHandler";
constexpr const char* kPromiseInternal   = "io/netty/channel/ChannelPromise";

constexpr const char* kChannelReadDesc =
    "(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V";
constexpr const char* kWriteDesc =
    "(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)V";

std::atomic<uint64_t> g_channelSeq{0};

std::mutex g_bypassMu;
struct Bypass { jobject channel; jobject packet; std::chrono::steady_clock::time_point time; };
std::vector<Bypass> g_bypassPending;
void releaseMark(JNIEnv* env,const Bypass& mark){env->DeleteGlobalRef(mark.channel);env->DeleteGlobalRef(mark.packet);}
jobject contextChannel(JNIEnv* env,jobject ctx){
    jclass c=env->GetObjectClass(ctx); if(!c)return nullptr;
    auto m=env->GetMethodID(c,"channel","()Lio/netty/channel/Channel;");env->DeleteLocalRef(c);
    return m&&!env->ExceptionCheck()?env->CallObjectMethod(ctx,m):nullptr;
}
bool consumeBypassMark(JNIEnv* env,jobject ctx,jobject msg){
    jobject channel=contextChannel(env,ctx);if(!channel)return false;
    std::lock_guard<std::mutex> lock(g_bypassMu);bool found=false;
    auto now=std::chrono::steady_clock::now();
    for(auto it=g_bypassPending.begin();it!=g_bypassPending.end();){
        bool match=env->IsSameObject(it->channel,channel)&&env->IsSameObject(it->packet,msg);
        if(match||now-it->time>std::chrono::seconds(10)){found|=match;releaseMark(env,*it);it=g_bypassPending.erase(it);}else ++it;
    }
    env->DeleteLocalRef(channel);return found;
}

void printlnUtf8(JNIEnv* env, const char* line) {
    jclass sysCls = env->FindClass("java/lang/System");
    jfieldID outFid =
        env->GetStaticFieldID(sysCls, "out", "Ljava/io/PrintStream;");
    jobject out = env->GetStaticObjectField(sysCls, outFid);
    jclass psCls = env->GetObjectClass(out);
    jmethodID pmid =
        env->GetMethodID(psCls, "println", "(Ljava/lang/String;)V");
    jstring js = env->NewStringUTF(line);
    env->CallVoidMethod(out, pmid, js);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(js);
    env->DeleteLocalRef(psCls);
    env->DeleteLocalRef(out);
    env->DeleteLocalRef(sysCls);
}

std::string javaClassName(JNIEnv* env, jobject o) {
    if (!o) return "null";
    if (!g_jvmti) return "<no-jvmti>";
    jclass c = env->GetObjectClass(o);
    if (!c) return "<no-class>";

    char* sig = nullptr;
    jvmtiError rc = g_jvmti->GetClassSignature(c, &sig, nullptr);
    env->DeleteLocalRef(c);
    if (rc != JVMTI_ERROR_NONE || !sig) return "<?>";

    std::string out;
    const char* p = sig;
    if (*p == 'L') {
        ++p;
        for (; *p && *p != ';'; ++p) out.push_back(*p == '/' ? '.' : *p);
    } else {
        out = sig;
    }
    g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(sig));
    return out;
}

void JNICALL RelayChannelReadImpl(JNIEnv* env,
                                     jobject ,
                                     jobject ctx,
                                     jobject msg) {

    std::string cls = javaClassName(env, msg);
    bool mirrorToB = BServer_IsBActive() &&
        cls.rfind("net.minecraft.network.protocol.game.", 0) == 0;
    if (mirrorToB) {
        BServer_ForwardToB(env, msg);
    }

    env->CallObjectMethod(ctx, g_relay.netty.fireChannelReadMid, msg);
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (cls == "net.minecraft.network.protocol.game.ClientboundLoginPacket") {
        BServer_TryCaptureLiveConnection(env);
    }
}

bool shouldAllowC2S(const std::string& fqcn) {

    static constexpr const char kGamePrefix[] =
        "net.minecraft.network.protocol.game.";
    if (fqcn.rfind(kGamePrefix, 0) != 0) return true;

    if (!BServer_IsBActive()) return true;

    const char* simple = fqcn.c_str() + (sizeof(kGamePrefix) - 1);
    return std::strcmp(simple, "ServerboundCustomPayloadPacket") == 0;
}

void JNICALL RelayWriteImpl(JNIEnv* env,
                               jobject ,
                               jobject ctx,
                               jobject msg,
                               jobject promise) {
    std::string cls = javaClassName(env, msg);
    bool bypass = consumeBypassMark(env, ctx, msg);
    bool allow = bypass || shouldAllowC2S(cls);
    LogTo("[C2S %s] %s", bypass ? "ROUTE" : (allow ? "PASS" : "DROP"), cls.c_str());

    if (allow) {
        env->CallObjectMethod(ctx, g_relay.netty.ctxWriteMid, msg, promise);
    } else {
        if (promise && g_relay.netty.promiseSetSuccessMid) {
            env->CallObjectMethod(promise, g_relay.netty.promiseSetSuccessMid);
        }
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
}

bool cacheNettyRefs(JNIEnv* env, jobject mcLoader) {

    jclass ctxCls = LoadClassInLoader(env, mcLoader,
                                      "io.netty.channel.ChannelHandlerContext");
    if (!ctxCls) { Dbg("Relay: couldn't load ChannelHandlerContext"); return false; }
    g_relay.netty.contextCls = static_cast<jclass>(env->NewGlobalRef(ctxCls));
    g_relay.netty.pipelineMid = env->GetMethodID(
        ctxCls, "pipeline", "()Lio/netty/channel/ChannelPipeline;");
    g_relay.netty.fireChannelReadMid = env->GetMethodID(
        ctxCls, "fireChannelRead",
        "(Ljava/lang/Object;)Lio/netty/channel/ChannelHandlerContext;");
    g_relay.netty.ctxWriteMid = env->GetMethodID(
        ctxCls, "write",
        "(Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)Lio/netty/channel/ChannelFuture;");
    g_relay.netty.ctxWriteFlushMid = env->GetMethodID(
        ctxCls, "writeAndFlush",
        "(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;");
    env->DeleteLocalRef(ctxCls);

    jclass pipCls = LoadClassInLoader(env, mcLoader,
                                      "io.netty.channel.ChannelPipeline");
    if (!pipCls) { Dbg("Relay: couldn't load ChannelPipeline"); return false; }
    g_relay.netty.pipelineCls = static_cast<jclass>(env->NewGlobalRef(pipCls));
    g_relay.netty.addFirstMid = env->GetMethodID(
        pipCls, "addFirst",
        "(Ljava/lang/String;Lio/netty/channel/ChannelHandler;)Lio/netty/channel/ChannelPipeline;");
    g_relay.netty.addBeforeMid = env->GetMethodID(
        pipCls, "addBefore",
        "(Ljava/lang/String;Ljava/lang/String;Lio/netty/channel/ChannelHandler;)Lio/netty/channel/ChannelPipeline;");
    g_relay.netty.getMid = env->GetMethodID(
        pipCls, "get", "(Ljava/lang/String;)Lio/netty/channel/ChannelHandler;");
    env->DeleteLocalRef(pipCls);

    jclass promCls = LoadClassInLoader(env, mcLoader,
                                       "io.netty.channel.ChannelPromise");
    if (!promCls) { Dbg("Relay: couldn't load ChannelPromise"); return false; }
    g_relay.netty.promiseCls = static_cast<jclass>(env->NewGlobalRef(promCls));
    g_relay.netty.promiseSetSuccessMid = env->GetMethodID(
        promCls, "setSuccess", "()Lio/netty/channel/ChannelPromise;");
    env->DeleteLocalRef(promCls);

    if (!g_relay.netty.pipelineMid || !g_relay.netty.fireChannelReadMid ||
        !g_relay.netty.ctxWriteMid  || !g_relay.netty.addFirstMid ||
        !g_relay.netty.addBeforeMid || !g_relay.netty.getMid || !g_relay.netty.promiseSetSuccessMid) {
        Dbg("Relay: one or more netty method IDs missing");
        return false;
    }
    return true;
}

}

void JNICALL Native_RelayChannelRead(JNIEnv* env, jobject self, jobject ctx, jobject msg) {
    if (BServer_IsCurrentChannel(env, ctx)) {
        BServer_OnPacket(env, ctx, msg);
        return;
    }
    RelayChannelReadImpl(env, self, ctx, msg);
}

void JNICALL Native_RelayWrite(JNIEnv* env, jobject self, jobject ctx, jobject msg, jobject promise) {
    if (BServer_IsCurrentChannel(env, ctx)) {
        env->CallObjectMethod(ctx, g_relay.netty.ctxWriteMid, msg, promise);
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }
    RelayWriteImpl(env, self, ctx, msg, promise);
}

void RelayFilter_ClearBypass(JNIEnv* env,jobject channel){
    std::lock_guard<std::mutex> lock(g_bypassMu);
    for(auto it=g_bypassPending.begin();it!=g_bypassPending.end();){
        if(!channel||env->IsSameObject(channel,it->channel)){releaseMark(env,*it);it=g_bypassPending.erase(it);}else ++it;
    }
}
void RelayFilter_MarkBypass(JNIEnv* env,jobject channel,jobject packet){
    if(!channel||!packet)return;
    jobject c=env->NewGlobalRef(channel),p=env->NewGlobalRef(packet);
    if(!c||!p){if(c)env->DeleteGlobalRef(c);if(p)env->DeleteGlobalRef(p);return;}
    std::lock_guard<std::mutex> lock(g_bypassMu);
    auto now=std::chrono::steady_clock::now();
    for(auto it=g_bypassPending.begin();it!=g_bypassPending.end();){
        if(now-it->time>std::chrono::seconds(10)){releaseMark(env,*it);it=g_bypassPending.erase(it);}else ++it;
    }
    if(g_bypassPending.size()>=4096){releaseMark(env,g_bypassPending.front());g_bypassPending.erase(g_bypassPending.begin());}
    g_bypassPending.push_back({c,p,now});
}
void JNICALL Native_RelayInactive(JNIEnv* env,jobject,jobject ctx){
    if (BServer_IsCurrentChannel(env, ctx)) {
        BServer_OnChannelInactive(env, ctx);
        return;
    }
    jobject channel=contextChannel(env,ctx);if(channel){RelayFilter_ClearBypass(env,channel);env->DeleteLocalRef(channel);}
    jclass c=env->GetObjectClass(ctx);
    auto fire=c?env->GetMethodID(c,"fireChannelInactive","()Lio/netty/channel/ChannelHandlerContext;"):nullptr;
    if(fire&&!env->ExceptionCheck()){auto r=env->CallObjectMethod(ctx,fire);if(r)env->DeleteLocalRef(r);}
    if(c)env->DeleteLocalRef(c);if(env->ExceptionCheck())LogAndClearException(env,"A inactive propagation");
}

bool InstallRelayHandler(JNIEnv* env) {
    if (!env || !g_hook.valid()) return false;
    if (g_relay.valid()) return true;

    jobject mcLoader = GetMinecraftClassLoader(env, g_jvmti);
    if (!mcLoader) return false;
    bool ok = cacheNettyRefs(env, mcLoader);
    env->DeleteGlobalRef(mcLoader);
    if (!ok) return false;

    g_relay.klass = g_hook.klass;
    g_relay.ctor = g_hook.ctor;
    g_relay.internalName = g_hook.internalName;
    g_relay.dotName = g_hook.dotName;
    // 每次注入只生成一个 pipeline key，所有 A 重连复用它，避免固定名称冲突。
    g_relay.pipelineName = GenerateRandomClassName(1, 1);
    return true;
}

static void attachHandlerToPipeline(JNIEnv* env, jobject pipeline) {
    const char* handlerName = g_relay.pipelineName.c_str();
    jstring name = env->NewStringUTF(handlerName);
    jobject existing = env->CallObjectMethod(pipeline, g_relay.netty.getMid, name);
    if (env->ExceptionCheck()) { env->ExceptionClear(); existing = nullptr; }
    if (existing) {
        env->DeleteLocalRef(existing);
        env->DeleteLocalRef(name);
        LogTo("Attach: handler already present");
        return;
    }

    jobject handler = env->NewObject(g_relay.klass, g_relay.ctor);
    if (!handler || env->ExceptionCheck()) {
        LogTo("Attach: NewObject(RelayHandler) FAILED");
        env->ExceptionClear();
        env->DeleteLocalRef(name);
        return;
    }

    jstring base = env->NewStringUTF("packet_handler");
    jobject unused = env->CallObjectMethod(
        pipeline, g_relay.netty.addBeforeMid, base, name, handler);
    bool addBeforeFailed = env->ExceptionCheck();
    const char* mode;
    if (addBeforeFailed) {
        env->ExceptionClear();
        LogTo("Attach: addBefore(packet_handler,...) THREW, falling back to addFirst");
        unused = env->CallObjectMethod(
            pipeline, g_relay.netty.addFirstMid, name, handler);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LogTo("Attach: addFirst ALSO threw — giving up");
            mode = "FAILED";
        } else {
            mode = "HEAD-fallback";
        }
    } else {
        mode = "before-packet_handler";
    }
    LogTo("Attach: %s", mode);
    if (unused) env->DeleteLocalRef(unused);
    env->DeleteLocalRef(base);
    env->DeleteLocalRef(handler);
    env->DeleteLocalRef(name);
}

void RelayHandler_AttachToPipeline(JNIEnv* env, jobject ctx) {
    LogTo("Attach: channel pipeline");
    if (!ctx) return;
    if (!g_relay.valid()) {
        LogTo("Attach: g_relay invalid, installing");
        if (!InstallRelayHandler(env)) { LogTo("Attach: install failed"); return; }
    }

    jobject pipeline = env->CallObjectMethod(ctx, g_relay.netty.pipelineMid);
    if (!pipeline || env->ExceptionCheck()) {
        LogTo("Attach: ctx.pipeline() FAILED");
        env->ExceptionClear();
        return;
    }
    attachHandlerToPipeline(env, pipeline);
    env->DeleteLocalRef(pipeline);
}

void RelayHandler_AttachToPipelineObject(JNIEnv* env, jobject pipeline) {
    if (!pipeline) return;
    if (!g_relay.valid()) {
        if (!InstallRelayHandler(env)) { LogTo("AttachObj: install failed"); return; }
    }
    attachHandlerToPipeline(env, pipeline);
}
