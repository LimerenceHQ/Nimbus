#include "b_server.h"

#include "classfile.h"
#include "random_name.h"
#include "relay_handler.h"
#include "world_cache.h"
#include "player_snapshot.h"
#include "scoreboard_snapshot.h"
#include "bootstrap_ack.h"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cmath>
#include <mutex>
#include <string>
#include <thread>

void replayBootstrap(JNIEnv* env, jobject ch, bool skipLogin);
void finishBootstrapToPlay(JNIEnv* env, jobject ch);

namespace {

constexpr const char* kInitSuper         = "io/netty/channel/ChannelInitializer";

constexpr const char* kInitChannelDesc     = "(Lio/netty/channel/Channel;)V";

enum class BState { AwaitHandshake, AwaitLogin, Play };

struct BServer {

    jobject listenerChannel = nullptr;
    jobject eventLoopGroup = nullptr;

    jclass    initClass    = nullptr;
    jmethodID initCtor     = nullptr;
    std::string pipelineName;

    jclass    connectionCls              = nullptr;
    jmethodID connectionConfigureSerMid  = nullptr;
    jmethodID connectionSendMid          = nullptr;
    jfieldID  connectionAttrProtocolFid  = nullptr;

    jobject   protoHandshaking = nullptr;
    jobject   protoLogin       = nullptr;
    jobject   protoPlay        = nullptr;
    jobject   protoStatus      = nullptr;

    jclass    statusResponsePacketCls   = nullptr;
    jmethodID statusResponsePacketCtor  = nullptr;
    jclass    serverStatusCls           = nullptr;
    jmethodID serverStatusCtor          = nullptr;
    jclass    componentCls              = nullptr;
    jmethodID componentLiteralMid       = nullptr;
    jclass    pongResponsePacketCls     = nullptr;
    jmethodID pongResponsePacketCtor    = nullptr;
    jclass    statusRequestPacketCls    = nullptr;
    jclass    pingRequestPacketCls      = nullptr;
    jfieldID  pingRequestPacketTimeFid  = nullptr;
    jfieldID  intentionPacketIntentFid  = nullptr;

    jobject   flowServerbound  = nullptr;
    jobject   flowClientbound  = nullptr;

    jclass    channelCls              = nullptr;
    jmethodID channelPipelineMid      = nullptr;
    jmethodID channelWriteAndFlushMid = nullptr;
    jmethodID channelAttrMid          = nullptr;
    jmethodID channelConfigMid        = nullptr;
    jmethodID channelCloseMid         = nullptr;
    jfieldID  connectionChannelFid    = nullptr;

    jmethodID configSetOptionMid      = nullptr;
    jobject   tcpNoDelayOption        = nullptr;
    jobject   booleanTrue             = nullptr;
    jclass    pipelineCls             = nullptr;
    jmethodID pipelineAddLastMid      = nullptr;
    jmethodID pipelineRemoveNameMid   = nullptr;
    jmethodID pipelineGetHandlerMid   = nullptr;
    jclass    attributeCls            = nullptr;
    jmethodID attributeSetMid         = nullptr;

    jclass    bundlerInfoCls          = nullptr;
    jfieldID  bundlerProviderFid      = nullptr;

    jclass    packetEncoderCls        = nullptr;
    jmethodID packetEncoderSetProtoMid = nullptr;
    jclass    packetDecoderCls        = nullptr;
    jmethodID packetDecoderSetProtoMid = nullptr;

    jclass    gameProfileCls          = nullptr;
    jmethodID gameProfileCtor         = nullptr;

    jclass    minecraftCls            = nullptr;
    jmethodID mcGetInstanceMid        = nullptr;
    jmethodID mcGetProfilePropsMid    = nullptr;
    jclass    friendlyBufCls          = nullptr;
    jmethodID friendlyBufCtor         = nullptr;
    jmethodID fbbWriteByteMid         = nullptr;
    jmethodID fbbWriteBooleanMid      = nullptr;
    jmethodID fbbWriteVarIntMid       = nullptr;
    jmethodID fbbWriteUUIDMid         = nullptr;
    jmethodID fbbWriteUtfMid          = nullptr;
    jmethodID fbbWriteGpPropsMid      = nullptr;
    jclass    unpooledCls             = nullptr;
    jmethodID unpooledBufferMid       = nullptr;
    jmethodID unpooledWrappedMid      = nullptr;
    jclass    playerInfoUpdatePacketCls     = nullptr;
    jmethodID playerInfoUpdatePacketBufCtor = nullptr;
    jmethodID playerInfoUpdatePacketWriteMid = nullptr;

    jmethodID piuEntriesMidA          = nullptr;
    jmethodID piuEntriesMidB          = nullptr;
    jclass    piEntryCls              = nullptr;
    jmethodID piEntryProfileIdMid     = nullptr;
    jmethodID piEntryGameModeMid      = nullptr;
    jmethodID piEntryListedMid        = nullptr;
    jmethodID piEntryLatencyMid       = nullptr;
    jmethodID piEntryDisplayNameMid   = nullptr;
    jmethodID gameTypeGetIdMid        = nullptr;
    jmethodID fbbWriteComponentMid    = nullptr;
    jmethodID listSizeMid             = nullptr;
    jmethodID listGetMid              = nullptr;
    jmethodID byteBufGetByteMid       = nullptr;
    jobject   bUuidObj                = nullptr;

    jclass    addPlayerPacketCls      = nullptr;
    jfieldID  addPlayerPacketUuidFid  = nullptr;

    jclass    customPayloadPacketCls  = nullptr;

    jclass    setPlayerTeamPacketCls  = nullptr;
    jmethodID setPlayerTeamPacketBufCtor = nullptr;
    jfieldID  setPlayerTeamMethodFid  = nullptr;
    jfieldID  setPlayerTeamNameFid    = nullptr;
    jfieldID  setPlayerTeamPlayersFid = nullptr;
    jmethodID collectionContainsMid   = nullptr;

    jmethodID userGetGameProfileMid   = nullptr;
    jmethodID gameProfileGetNameMid   = nullptr;
    jstring   aName                   = nullptr;
    jstring   bName                   = nullptr;

    jmethodID byteBufReadableBytesMid = nullptr;
    jmethodID byteBufReaderIndexMid   = nullptr;
    jmethodID byteBufGetBytesMid      = nullptr;

    jobject   aRealUuid               = nullptr;
    unsigned char aUuidBytes[16]      = {0};
    bool      aUuidReady              = false;
    unsigned char bUuidBytes[16]      = {0};
    bool      bUuidReady              = false;

    jclass    userCls                 = nullptr;
    jmethodID userGetProfileIdMid     = nullptr;
    jmethodID mcGetUserMid            = nullptr;

    jmethodID uuidGetMsbMid           = nullptr;
    jmethodID uuidGetLsbMid           = nullptr;

    jclass    loginFinishedPacketCls  = nullptr;
    jmethodID loginFinishedPacketCtor = nullptr;

    jclass    helloPacketCls          = nullptr;
    jfieldID  helloPacketNameFid      = nullptr;

    jclass    intentPacketCls         = nullptr;

    jclass    playerPositionPacketCls = nullptr;
    jmethodID playerPositionPacketCtor = nullptr;
    jclass    setCls                  = nullptr;
    jmethodID setOfMid                = nullptr;
    jclass    chunkCacheCenterPacketCls = nullptr;
    jmethodID chunkCacheCenterPacketCtor = nullptr;
    jclass    chunkCacheRadiusPacketCls = nullptr;
    jmethodID chunkCacheRadiusPacketCtor = nullptr;
    jclass    defaultSpawnPacketCls = nullptr;
    jmethodID defaultSpawnPacketCtor = nullptr;
    jclass    blockPosCls = nullptr;
    jmethodID blockPosCtor = nullptr;
    jclass    keepAlivePacketCls      = nullptr;
    jmethodID keepAlivePacketCtor     = nullptr;
    jclass    acceptTeleportPacketCls = nullptr;
    BootstrapAck bootstrapAck;

    jclass    uuidCls                 = nullptr;
    jmethodID uuidNameUuidFromBytesMid = nullptr;

    jclass    bundlePacketCls         = nullptr;
    jmethodID bundleSubPacketsMid     = nullptr;
    jmethodID iterableIteratorMid     = nullptr;
    jmethodID iteratorHasNextMid      = nullptr;
    jmethodID iteratorNextMid         = nullptr;

    std::mutex targetMu;
    jobject   targetAConnection = nullptr;

    std::mutex bMu;
    // 统一串行化发往 B 的数据，避免初始化快照与实时包交错。
    std::recursive_mutex bWriteMu;
    jobject   bChannel = nullptr;
    uint64_t  bGeneration = 0;
    std::vector<jobject> bootstrapQueue;
    static constexpr size_t kBootstrapMax = 256;

    std::atomic<BState> bState{BState::AwaitHandshake};

    bool bServerBound = false;

    std::atomic<bool> midSession{false};
    jmethodID mcGetConnectionMid      = nullptr;
    jclass    clientPacketListenerCls = nullptr;
    jmethodID cplGetConnectionMid     = nullptr;
    jmethodID cplLevelsMid            = nullptr;
    jmethodID cplRegistryAccessMid    = nullptr;
    jmethodID registryAccessFreezeMid = nullptr;
    jfieldID  mcPlayerFid             = nullptr;
    jfieldID  mcInventoryMenuFid      = nullptr;
    jfieldID  mcGameModeFid           = nullptr;
    jfieldID  mcLevelFid              = nullptr;
    jmethodID gameModeGetTypeMid      = nullptr;
    jclass    levelCls                = nullptr;
    jmethodID levelDimMidA            = nullptr;
    jmethodID levelDimMidB            = nullptr;
    jmethodID objToStringMid          = nullptr;
    jfieldID  clientLevelChunkSourceFid = nullptr;
    // 当前客户端实体快照所需的 JNI 类与方法引用。
    jclass    entityCls                  = nullptr;
    jmethodID entityGetIdMid             = nullptr;
    jmethodID entityIsRemovedMid         = nullptr;
    jmethodID entityGetAddEntityPacketMid = nullptr;
    jmethodID entityGetEntityDataMid     = nullptr;
    jclass    synchedEntityDataCls       = nullptr;
    jmethodID synchedGetNonDefaultValuesMid = nullptr;
    jmethodID clientLevelEntitiesForRenderingMid = nullptr;
    jclass    setEntityDataPacketCls     = nullptr;
    jmethodID setEntityDataPacketCtor    = nullptr;
    jclass    setEntityMotionPacketCls   = nullptr;
    jmethodID setEntityMotionPacketCtor  = nullptr;
    jclass    setPassengersPacketCls     = nullptr;
    jmethodID setPassengersPacketCtor    = nullptr;
    jclass    clientChunkCacheCls     = nullptr;
    jmethodID clientChunkGetMid       = nullptr;
    jmethodID clientChunkLightEngineMid = nullptr;
    jclass    chunkStatusCls          = nullptr;
    jfieldID  chunkStatusFullFid      = nullptr;
    jclass    chunkWithLightPacketCls = nullptr;
    jmethodID chunkWithLightPacketCtor = nullptr;
    jclass    containerMenuCls         = nullptr;
    jmethodID containerGetItemsMid     = nullptr;
    jmethodID containerGetCarriedMid   = nullptr;
    jmethodID containerGetStateIdMid   = nullptr;
    jclass    containerContentPacketCls = nullptr;
    jmethodID containerContentPacketCtor = nullptr;
    jmethodID playerGetInventoryMid      = nullptr;
    jclass    inventoryCls               = nullptr;
    jfieldID  inventorySelectedFid       = nullptr;
    jclass    carriedItemPacketCls       = nullptr;
    jmethodID carriedItemPacketCtor      = nullptr;
    jclass    loginPacketCls          = nullptr;
    jmethodID loginPacketCtor         = nullptr;
    jclass    optionalCls             = nullptr;
    jmethodID optionalEmptyMid        = nullptr;
};
BServer g_bs;

std::mutex              g_bConnMu;
std::condition_variable g_bConnCv;
bool                    g_bConnected = false;

jmethodID findMethodByDesc(jclass klass, const char* desc, bool wantStatic) {
    jint count = 0;
    jmethodID* mids = nullptr;
    if (g_jvmti->GetClassMethods(klass, &count, &mids) != JVMTI_ERROR_NONE) return nullptr;
    jmethodID hit = nullptr;
    for (jint i = 0; i < count && !hit; ++i) {
        char *n=nullptr, *s=nullptr, *g=nullptr;
        if (g_jvmti->GetMethodName(mids[i], &n, &s, &g) != JVMTI_ERROR_NONE) continue;
        jint mods = 0;
        g_jvmti->GetMethodModifiers(mids[i], &mods);
        bool isStatic = (mods & 0x0008) != 0;
        if (s && std::strcmp(s, desc) == 0 && isStatic == wantStatic) {
            hit = mids[i];
            LogTo("  findMethodByDesc(%s, static=%d): '%s'", desc, wantStatic?1:0, n?n:"?");
        }
        if (n) g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(n));
        if (s) g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(s));
        if (g) g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(g));
    }
    g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(mids));
    return hit;
}

int findMethodsByDesc(jclass klass, const char* desc, bool wantStatic,
                      jmethodID* out, int maxOut) {
    jint count = 0;
    jmethodID* mids = nullptr;
    if (g_jvmti->GetClassMethods(klass, &count, &mids) != JVMTI_ERROR_NONE) return 0;
    int n = 0;
    for (jint i = 0; i < count && n < maxOut; ++i) {
        char *nm=nullptr, *s=nullptr, *g=nullptr;
        if (g_jvmti->GetMethodName(mids[i], &nm, &s, &g) != JVMTI_ERROR_NONE) continue;
        jint mods = 0;
        g_jvmti->GetMethodModifiers(mids[i], &mods);
        bool isStatic = (mods & 0x0008) != 0;
        if (s && std::strcmp(s, desc) == 0 && isStatic == wantStatic) {
            out[n++] = mids[i];
            LogTo("  findMethodsByDesc(%s)[%d]: '%s'", desc, n-1, nm?nm:"?");
        }
        if (nm) g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(nm));
        if (s)  g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(s));
        if (g)  g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(g));
    }
    g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(mids));
    return n;
}

jmethodID findMethodByDescExcl(jclass klass, const char* desc, bool wantStatic,
                               const char* const* excl, int nExcl) {
    jint count = 0;
    jmethodID* mids = nullptr;
    if (g_jvmti->GetClassMethods(klass, &count, &mids) != JVMTI_ERROR_NONE) return nullptr;
    jmethodID hit = nullptr;
    for (jint i = 0; i < count && !hit; ++i) {
        char *nm=nullptr, *s=nullptr, *g=nullptr;
        if (g_jvmti->GetMethodName(mids[i], &nm, &s, &g) != JVMTI_ERROR_NONE) continue;
        jint mods = 0;
        g_jvmti->GetMethodModifiers(mids[i], &mods);
        bool isStatic = (mods & 0x0008) != 0;
        bool excluded = false;
        for (int e = 0; nm && e < nExcl; ++e)
            if (std::strcmp(nm, excl[e]) == 0) { excluded = true; break; }
        if (!excluded && s && std::strcmp(s, desc) == 0 && isStatic == wantStatic) {
            hit = mids[i];
            LogTo("  findMethodByDescExcl(%s): '%s'", desc, nm?nm:"?");
        }
        if (nm) g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(nm));
        if (s)  g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(s));
        if (g)  g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(g));
    }
    g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(mids));
    return hit;
}

jfieldID findFieldByDesc(jclass klass, const char* desc, bool wantStatic) {
    jint count = 0;
    jfieldID* fids = nullptr;
    if (g_jvmti->GetClassFields(klass, &count, &fids) != JVMTI_ERROR_NONE) return nullptr;
    jfieldID hit = nullptr;
    for (jint i = 0; i < count && !hit; ++i) {
        char *n=nullptr, *s=nullptr, *g=nullptr;
        if (g_jvmti->GetFieldName(klass, fids[i], &n, &s, &g) != JVMTI_ERROR_NONE) continue;
        jint mods = 0;
        g_jvmti->GetFieldModifiers(klass, fids[i], &mods);
        bool isStatic = (mods & 0x0008) != 0;
        if (s && std::strcmp(s, desc) == 0 && isStatic == wantStatic) {
            hit = fids[i];
            LogTo("  findFieldByDesc(%s, static=%d): '%s'", desc, wantStatic?1:0, n?n:"?");
        }
        if (n) g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(n));
        if (s) g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(s));
        if (g) g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(g));
    }
    g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(fids));
    return hit;
}

jclass findLoadedBySig(JNIEnv* env, const char* sig) {
    jint count = 0;
    jclass* classes = nullptr;
    if (g_jvmti->GetLoadedClasses(&count, &classes) != JVMTI_ERROR_NONE) return nullptr;
    jclass hit = nullptr;
    for (jint i = 0; i < count; ++i) {
        char* s = nullptr;
        if (g_jvmti->GetClassSignature(classes[i], &s, nullptr) != JVMTI_ERROR_NONE) continue;
        if (s && std::strcmp(s, sig) == 0) {
            hit = (jclass)env->NewLocalRef(classes[i]);
            g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(s));
            break;
        }
        if (s) g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(s));
    }
    for (jint i = 0; i < count; ++i) env->DeleteLocalRef(classes[i]);
    g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(classes));
    return hit;
}

jclass loadOrFind(JNIEnv* env, jobject mcLoader, const char* dot, const char* sig) {
    jclass c = LoadClassInLoader(env, mcLoader, dot);
    if (!c) c = findLoadedBySig(env, sig);
    if (!c) LogTo("BServer: cannot find class %s", dot);
    return c;
}

void clearBootstrapQueue(JNIEnv* env) {
    if (!env) return;
    std::lock_guard<std::mutex> lock(g_bs.bMu);
    for (jobject packet : g_bs.bootstrapQueue) env->DeleteGlobalRef(packet);
    g_bs.bootstrapQueue.clear();
}

std::string classNameForB(JNIEnv* env, jobject o) {
    if (!o || !g_jvmti) return {};
    jclass c = env->GetObjectClass(o);
    if (!c) return {};
    char* sig = nullptr;
    jvmtiError rc = g_jvmti->GetClassSignature(c, &sig, nullptr);
    env->DeleteLocalRef(c);
    if (rc != JVMTI_ERROR_NONE || !sig) return {};
    std::string out;
    const char* p = sig;
    if (*p == 'L') { ++p; for (; *p && *p != ';'; ++p) out.push_back(*p == '/' ? '.' : *p); }
    else out = sig;
    g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(sig));
    return out;
}

void setChannelAttr(JNIEnv* env, jobject channel, jfieldID keyFid, jclass keyOwnerCls,
                    jobject value) {
    jobject key = env->GetStaticObjectField(keyOwnerCls, keyFid);
    if (!key) return;
    jobject attr = env->CallObjectMethod(channel, g_bs.channelAttrMid, key);
    if (env->ExceptionCheck() || !attr) { env->ExceptionClear(); return; }
    env->CallVoidMethod(attr, g_bs.attributeSetMid, value);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(attr);
    env->DeleteLocalRef(key);
}

void setProtocolState(JNIEnv* env, jobject channel, jobject protoValue) {
    if (!channel || !protoValue) return;
    if (g_bs.connectionAttrProtocolFid)
        setChannelAttr(env, channel, g_bs.connectionAttrProtocolFid, g_bs.connectionCls, protoValue);
    if (g_bs.bundlerProviderFid && g_bs.bundlerInfoCls)
        setChannelAttr(env, channel, g_bs.bundlerProviderFid, g_bs.bundlerInfoCls, protoValue);

    jobject pipeline = env->CallObjectMethod(channel, g_bs.channelPipelineMid);
    if (!pipeline || env->ExceptionCheck()) { env->ExceptionClear(); return; }

    for (const char* handlerName : {"encoder", "decoder"}) {
        jstring nm = env->NewStringUTF(handlerName);
        jobject handler = env->CallObjectMethod(pipeline, g_bs.pipelineGetHandlerMid, nm);
        env->DeleteLocalRef(nm);
        if (!handler || env->ExceptionCheck()) { env->ExceptionClear(); continue; }
        jclass hCls = env->GetObjectClass(handler);

        jmethodID sp = findMethodByDesc(hCls,
            "(Lnet/minecraft/network/ConnectionProtocol;)V", false);
        if (!sp) {
            sp = env->GetMethodID(hCls, "setProtocol",
                "(Lnet/minecraft/network/ConnectionProtocol;)V");
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
        if (sp) {
            env->CallVoidMethod(handler, sp, protoValue);
            if (env->ExceptionCheck()) env->ExceptionClear();
            else LogTo("setProtocolState: called %s.setProtocol(PLAY)", handlerName);
        }
        env->DeleteLocalRef(hCls);
        env->DeleteLocalRef(handler);

    }
    env->DeleteLocalRef(pipeline);
}

void JNICALL Native_ServerInit_initChannel(JNIEnv* env, jobject , jobject ch) {
    LogTo("BServer: initChannel for incoming ch=%p", (void*)ch);

    if (g_bs.channelConfigMid && g_bs.configSetOptionMid &&
        g_bs.tcpNoDelayOption && g_bs.booleanTrue) {
        jobject cfg = env->CallObjectMethod(ch, g_bs.channelConfigMid);
        if (cfg && !env->ExceptionCheck()) {
            env->CallBooleanMethod(cfg, g_bs.configSetOptionMid,
                                   g_bs.tcpNoDelayOption, g_bs.booleanTrue);
            if (env->ExceptionCheck()) env->ExceptionClear();
            else LogTo("BServer: TCP_NODELAY set on B channel");
            env->DeleteLocalRef(cfg);
        } else if (env->ExceptionCheck()) env->ExceptionClear();
    }

    jobject pipeline = env->CallObjectMethod(ch, g_bs.channelPipelineMid);
    if (env->ExceptionCheck() || !pipeline) {
        env->ExceptionClear(); LogTo("  pipeline() failed"); return;
    }

    setProtocolState(env, ch, g_bs.protoHandshaking);

    env->CallStaticVoidMethod(g_bs.connectionCls, g_bs.connectionConfigureSerMid,
                              pipeline, g_bs.flowServerbound);
    if (env->ExceptionCheck()) { LogAndClearException(env, "  configureSerialization"); }

    bool haveBundlerInfo = (g_bs.bundlerProviderFid != nullptr &&
                            g_bs.bundlerInfoCls   != nullptr);
    if (!haveBundlerInfo) {
        for (const char* h : {"unbundler", "bundler"}) {
            jstring nm = env->NewStringUTF(h);
            env->CallObjectMethod(pipeline, g_bs.pipelineRemoveNameMid, nm);
            if (env->ExceptionCheck()) env->ExceptionClear();
            env->DeleteLocalRef(nm);
        }
        LogTo("  bundle handlers removed (no BundlerInfo)");
    } else {
        LogTo("  bundle handlers kept (BundlerInfo available)");
    }

    jobject handler = env->NewObject(g_relay.klass, g_relay.ctor);
    jstring name = env->NewStringUTF(g_bs.pipelineName.c_str());
    env->CallObjectMethod(pipeline, g_bs.pipelineAddLastMid, name, handler);
    if (env->ExceptionCheck()) LogAndClearException(env, "  addLast(bside)");
    env->DeleteLocalRef(name);
    env->DeleteLocalRef(handler);
    env->DeleteLocalRef(pipeline);

    {
        std::lock_guard<std::mutex> l(g_bs.bMu);
        if (g_bs.bChannel) env->DeleteGlobalRef(g_bs.bChannel);
        g_bs.bChannel = env->NewGlobalRef(ch);
        ++g_bs.bGeneration;
        g_bs.bState.store(BState::AwaitHandshake, std::memory_order_release);
    }

    // 新 B 连接必须先完成自己的 LOGIN/PLAY，避免旧连接尚未触发
    // channelInactive 时把旧的 connected 状态带入新连接。
    {
        std::lock_guard<std::mutex> l(g_bConnMu);
        g_bConnected = false;
    }
    clearBootstrapQueue(env);
    g_bs.bootstrapAck.reset();
    if (g_bs.bUuidObj) {
        env->DeleteGlobalRef(g_bs.bUuidObj);
        g_bs.bUuidObj = nullptr;
    }
    if (g_bs.bName) {
        env->DeleteGlobalRef(g_bs.bName);
        g_bs.bName = nullptr;
    }
    g_bs.bUuidReady = false;
    std::memset(g_bs.bUuidBytes, 0, sizeof(g_bs.bUuidBytes));

    LogTo("  B channel captured, state=AwaitHandshake");
}

void closeARemoteConnection(JNIEnv* env) {
    if (!g_bs.connectionChannelFid || !g_bs.channelCloseMid) return;
    jobject conn;
    {
        std::lock_guard<std::mutex> l(g_bs.targetMu);
        conn = g_bs.targetAConnection
                    ? env->NewLocalRef(g_bs.targetAConnection)
                    : nullptr;
    }
    if (!conn) { LogTo("[A-CLOSE] no target A connection cached; nothing to close"); return; }
    jobject aChannel = env->GetObjectField(conn, g_bs.connectionChannelFid);
    env->DeleteLocalRef(conn);
    if (!aChannel) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        LogTo("[A-CLOSE] Connection.channel is null (not yet channelActive?)");
        return;
    }
    jobject future = env->CallObjectMethod(aChannel, g_bs.channelCloseMid);
    if (env->ExceptionCheck()) LogAndClearException(env, "[A-CLOSE] channel.close");
    else LogTo("[A-CLOSE] A's netty channel to remote server closed directly");
    if (future) env->DeleteLocalRef(future);
    env->DeleteLocalRef(aChannel);

    std::lock_guard<std::mutex> l(g_bs.targetMu);
    if (g_bs.targetAConnection) {
        env->DeleteGlobalRef(g_bs.targetAConnection);
        g_bs.targetAConnection = nullptr;
    }
}

void JNICALL Native_BSide_channelInactive(JNIEnv* env, jobject , jobject ctx) {
    jobject channel = nullptr;
    if (ctx) {
        jclass ctxCls = env->GetObjectClass(ctx);
        jmethodID channelMid = ctxCls ? env->GetMethodID(
            ctxCls, "channel", "()Lio/netty/channel/Channel;") : nullptr;
        if (channelMid && !env->ExceptionCheck())
            channel = env->CallObjectMethod(ctx, channelMid);
        if (env->ExceptionCheck()) { env->ExceptionClear(); channel = nullptr; }
        if (ctxCls) env->DeleteLocalRef(ctxCls);
    }

    bool current = false;
    {
        std::lock_guard<std::mutex> l(g_bs.bMu);
        current = channel && g_bs.bChannel && env->IsSameObject(channel, g_bs.bChannel);
        if (current) {
            env->DeleteGlobalRef(g_bs.bChannel);
            g_bs.bChannel = nullptr;
            g_bs.bState.store(BState::AwaitHandshake, std::memory_order_release);
        }
    }
    if (channel) env->DeleteLocalRef(channel);
    if (!current) return;

    clearBootstrapQueue(env);
    g_bs.bootstrapAck.reset();

    LogTo("BServer: current B channel inactive; waiting for reconnect");
    {
        std::lock_guard<std::mutex> gateLock(g_bConnMu);
        g_bConnected = false;
    }

    if (g_bs.midSession.load(std::memory_order_acquire)) {
        LogTo("BServer: B gone; mid-session — leaving A's connection intact, A resumes control");
    } else {

        closeARemoteConnection(env);
    }
}

}
void BSide_OnPacket(JNIEnv* env, jobject ctx, jobject msg);
namespace {

bool defineInitClass(JNIEnv* env, jobject mcLoader) {
    std::string simple   = GenerateRandomClassName(2, 3);
    std::string internal = MakeInternalName(GetTrampolinePackage(), simple);

    ClassBuilder cb(internal, kInitSuper, 52);
    u2 superInit = cb.methodRef(kInitSuper, "<init>", "()V");
    std::vector<u1> ctor = {
        0x2A, 0xB7, u1((superInit >> 8) & 0xFF), u1(superInit & 0xFF), 0xB1
    };
    cb.addCodedMethod("<init>", "()V", ACC_PUBLIC, ctor, 1, 1);
    cb.addNativeMethod("initChannel", kInitChannelDesc, ACC_PUBLIC | ACC_NATIVE);
    std::vector<u1> bytes = cb.build();

    jclass defined = env->DefineClass(internal.c_str(), mcLoader,
                                      reinterpret_cast<const jbyte*>(bytes.data()),
                                      static_cast<jsize>(bytes.size()));
    if (!defined) { LogAndClearException(env, "BServer/DefineInit"); return false; }
    JNINativeMethod nats[] = {
        {const_cast<char*>("initChannel"), const_cast<char*>(kInitChannelDesc),
         reinterpret_cast<void*>(&Native_ServerInit_initChannel)},
    };
    if (env->RegisterNatives(defined, nats, 1) != 0) {
        LogAndClearException(env, "BServer/RegisterInit"); env->DeleteLocalRef(defined); return false;
    }
    g_bs.initCtor = env->GetMethodID(defined, "<init>", "()V");
    g_bs.initClass = static_cast<jclass>(env->NewGlobalRef(defined));
    if (g_bs.pipelineName.empty()) g_bs.pipelineName = GenerateRandomClassName(1, 1);
    env->DeleteLocalRef(defined);
    LogTo("BServer: defined ServerChannelInit as %s", internal.c_str());
    return true;
}

bool cacheJavaRefs(JNIEnv* env, jobject mcLoader) {

    jclass connCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.Connection", "Lnet/minecraft/network/Connection;");
    if (!connCls) return false;
    g_bs.connectionCls = static_cast<jclass>(env->NewGlobalRef(connCls));
    g_bs.connectionConfigureSerMid = findMethodByDesc(connCls,
        "(Lio/netty/channel/ChannelPipeline;Lnet/minecraft/network/protocol/PacketFlow;)V", true);
    g_bs.connectionSendMid = findMethodByDesc(connCls,
        "(Lnet/minecraft/network/protocol/Packet;)V", false);
    g_bs.connectionAttrProtocolFid = findFieldByDesc(connCls,
        "Lio/netty/util/AttributeKey;", true);

    g_bs.connectionChannelFid = findFieldByDesc(connCls,
        "Lio/netty/channel/Channel;", false);
    env->DeleteLocalRef(connCls);

    jclass bundlerCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.BundlerInfo", "Lnet/minecraft/network/protocol/BundlerInfo;");
    if (bundlerCls) {
        g_bs.bundlerInfoCls = static_cast<jclass>(env->NewGlobalRef(bundlerCls));
        g_bs.bundlerProviderFid = findFieldByDesc(bundlerCls, "Lio/netty/util/AttributeKey;", true);
        env->DeleteLocalRef(bundlerCls);
    } else {
        LogTo("BServer: BundlerInfo not found (bundle attr not set — may cause issues on modern servers)");
    }

    jclass protoCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.ConnectionProtocol", "Lnet/minecraft/network/ConnectionProtocol;");
    if (protoCls) {
        auto readEnum = [&](const char* n) -> jobject {
            jfieldID f = env->GetStaticFieldID(protoCls, n, "Lnet/minecraft/network/ConnectionProtocol;");
            if (!f) return nullptr;
            jobject v = env->GetStaticObjectField(protoCls, f);
            return v ? env->NewGlobalRef(v) : nullptr;
        };
        g_bs.protoHandshaking = readEnum("HANDSHAKING");
        g_bs.protoLogin       = readEnum("LOGIN");
        g_bs.protoPlay        = readEnum("PLAY");
        g_bs.protoStatus      = readEnum("STATUS");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(protoCls);
    }

    jclass flowCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.PacketFlow", "Lnet/minecraft/network/protocol/PacketFlow;");
    if (!flowCls) return false;
    jfieldID fSB = env->GetStaticFieldID(flowCls, "SERVERBOUND", "Lnet/minecraft/network/protocol/PacketFlow;");
    jfieldID fCB = env->GetStaticFieldID(flowCls, "CLIENTBOUND", "Lnet/minecraft/network/protocol/PacketFlow;");
    if (fSB) g_bs.flowServerbound = env->NewGlobalRef(env->GetStaticObjectField(flowCls, fSB));
    if (fCB) g_bs.flowClientbound = env->NewGlobalRef(env->GetStaticObjectField(flowCls, fCB));
    env->DeleteLocalRef(flowCls);

    jclass chCls = loadOrFind(env, mcLoader, "io.netty.channel.Channel", "Lio/netty/channel/Channel;");
    if (!chCls) return false;
    g_bs.channelCls = static_cast<jclass>(env->NewGlobalRef(chCls));
    g_bs.channelPipelineMid      = env->GetMethodID(chCls, "pipeline",      "()Lio/netty/channel/ChannelPipeline;");
    g_bs.channelWriteAndFlushMid = env->GetMethodID(chCls, "writeAndFlush", "(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;");
    g_bs.channelAttrMid          = env->GetMethodID(chCls, "attr",          "(Lio/netty/util/AttributeKey;)Lio/netty/util/Attribute;");
    g_bs.channelConfigMid        = env->GetMethodID(chCls, "config",        "()Lio/netty/channel/ChannelConfig;");
    g_bs.channelCloseMid         = env->GetMethodID(chCls, "close",         "()Lio/netty/channel/ChannelFuture;");
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(chCls);

    jclass cfgCls = loadOrFind(env, mcLoader, "io.netty.channel.ChannelConfig",
                               "Lio/netty/channel/ChannelConfig;");
    if (cfgCls) {
        g_bs.configSetOptionMid = env->GetMethodID(cfgCls, "setOption",
            "(Lio/netty/channel/ChannelOption;Ljava/lang/Object;)Z");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(cfgCls);
    }
    jclass optCls = loadOrFind(env, mcLoader, "io.netty.channel.ChannelOption",
                               "Lio/netty/channel/ChannelOption;");
    if (optCls) {
        jfieldID f = env->GetStaticFieldID(optCls, "TCP_NODELAY", "Lio/netty/channel/ChannelOption;");
        if (f) {
            jobject v = env->GetStaticObjectField(optCls, f);
            if (v) g_bs.tcpNoDelayOption = env->NewGlobalRef(v);
        }
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(optCls);
    }
    jclass boolCls = env->FindClass("java/lang/Boolean");
    if (boolCls) {
        jfieldID f = env->GetStaticFieldID(boolCls, "TRUE", "Ljava/lang/Boolean;");
        if (f) {
            jobject v = env->GetStaticObjectField(boolCls, f);
            if (v) g_bs.booleanTrue = env->NewGlobalRef(v);
        }
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(boolCls);
    }

    jclass pipCls = loadOrFind(env, mcLoader, "io.netty.channel.ChannelPipeline",
                               "Lio/netty/channel/ChannelPipeline;");
    if (!pipCls) return false;
    g_bs.pipelineCls = static_cast<jclass>(env->NewGlobalRef(pipCls));
    g_bs.pipelineAddLastMid = env->GetMethodID(pipCls, "addLast",
        "(Ljava/lang/String;Lio/netty/channel/ChannelHandler;)Lio/netty/channel/ChannelPipeline;");
    g_bs.pipelineRemoveNameMid = env->GetMethodID(pipCls, "remove",
        "(Ljava/lang/String;)Lio/netty/channel/ChannelHandler;");
    g_bs.pipelineGetHandlerMid = env->GetMethodID(pipCls, "get",
        "(Ljava/lang/String;)Lio/netty/channel/ChannelHandler;");
    env->DeleteLocalRef(pipCls);

    jclass attrCls = loadOrFind(env, mcLoader, "io.netty.util.Attribute", "Lio/netty/util/Attribute;");
    if (!attrCls) return false;
    g_bs.attributeCls = static_cast<jclass>(env->NewGlobalRef(attrCls));
    g_bs.attributeSetMid = env->GetMethodID(attrCls, "set", "(Ljava/lang/Object;)V");
    env->DeleteLocalRef(attrCls);

    jclass gpCls = loadOrFind(env, mcLoader, "com.mojang.authlib.GameProfile",
                              "Lcom/mojang/authlib/GameProfile;");
    if (gpCls) {
        g_bs.gameProfileCls = static_cast<jclass>(env->NewGlobalRef(gpCls));
        g_bs.gameProfileCtor = env->GetMethodID(gpCls, "<init>", "(Ljava/util/UUID;Ljava/lang/String;)V");

        g_bs.gameProfileGetNameMid = env->GetMethodID(gpCls, "getName", "()Ljava/lang/String;");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(gpCls);
    }

    jclass mcCls = loadOrFind(env, mcLoader, "net.minecraft.client.Minecraft",
                              "Lnet/minecraft/client/Minecraft;");
    if (mcCls) {
        g_bs.minecraftCls = static_cast<jclass>(env->NewGlobalRef(mcCls));
        g_bs.mcGetInstanceMid = findMethodByDesc(mcCls, "()Lnet/minecraft/client/Minecraft;", true);
        g_bs.mcGetProfilePropsMid = findMethodByDesc(mcCls,
            "()Lcom/mojang/authlib/properties/PropertyMap;", false);
        g_bs.mcGetUserMid = findMethodByDesc(mcCls,
            "()Lnet/minecraft/client/User;", false);
        env->DeleteLocalRef(mcCls);
    }
    jclass userCls = loadOrFind(env, mcLoader, "net.minecraft.client.User",
                                "Lnet/minecraft/client/User;");
    if (userCls) {
        g_bs.userCls = static_cast<jclass>(env->NewGlobalRef(userCls));
        g_bs.userGetProfileIdMid = findMethodByDesc(userCls,
            "()Ljava/util/UUID;", false);

        g_bs.userGetGameProfileMid = findMethodByDesc(userCls,
            "()Lcom/mojang/authlib/GameProfile;", false);
        env->DeleteLocalRef(userCls);
    }
    jclass fbbCls = loadOrFind(env, mcLoader, "net.minecraft.network.FriendlyByteBuf",
                               "Lnet/minecraft/network/FriendlyByteBuf;");
    if (fbbCls) {
        g_bs.friendlyBufCls = static_cast<jclass>(env->NewGlobalRef(fbbCls));
        g_bs.friendlyBufCtor = env->GetMethodID(fbbCls, "<init>", "(Lio/netty/buffer/ByteBuf;)V");

        g_bs.fbbWriteByteMid    = env->GetMethodID(fbbCls, "writeByte",    "(I)Lio/netty/buffer/ByteBuf;");
        g_bs.fbbWriteBooleanMid = env->GetMethodID(fbbCls, "writeBoolean", "(Z)Lio/netty/buffer/ByteBuf;");
        if (env->ExceptionCheck()) env->ExceptionClear();
        g_bs.fbbWriteVarIntMid = findMethodByDesc(fbbCls,
            "(I)Lnet/minecraft/network/FriendlyByteBuf;", false);
        g_bs.fbbWriteUUIDMid = findMethodByDesc(fbbCls,
            "(Ljava/util/UUID;)Lnet/minecraft/network/FriendlyByteBuf;", false);

        g_bs.fbbWriteUtfMid = findMethodByDesc(fbbCls,
            "(Ljava/lang/String;I)Lnet/minecraft/network/FriendlyByteBuf;", false);
        g_bs.fbbWriteGpPropsMid = findMethodByDesc(fbbCls,
            "(Lcom/mojang/authlib/properties/PropertyMap;)V", false);
        env->DeleteLocalRef(fbbCls);
    }
    jclass byteBufCls = loadOrFind(env, mcLoader, "io.netty.buffer.ByteBuf",
                                   "Lio/netty/buffer/ByteBuf;");
    if (byteBufCls) {
        g_bs.byteBufReadableBytesMid = env->GetMethodID(byteBufCls, "readableBytes",  "()I");
        g_bs.byteBufReaderIndexMid   = env->GetMethodID(byteBufCls, "readerIndex",    "()I");
        g_bs.byteBufGetBytesMid      = env->GetMethodID(byteBufCls, "getBytes",       "(I[B)Lio/netty/buffer/ByteBuf;");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(byteBufCls);
    }
    jclass unpCls = loadOrFind(env, mcLoader, "io.netty.buffer.Unpooled",
                               "Lio/netty/buffer/Unpooled;");
    if (unpCls) {
        g_bs.unpooledCls = static_cast<jclass>(env->NewGlobalRef(unpCls));
        g_bs.unpooledBufferMid  = env->GetStaticMethodID(unpCls, "buffer",        "()Lio/netty/buffer/ByteBuf;");
        g_bs.unpooledWrappedMid = env->GetStaticMethodID(unpCls, "wrappedBuffer", "([B)Lio/netty/buffer/ByteBuf;");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(unpCls);
    }
    jclass piuCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket",
        "Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket;");
    if (piuCls) {
        g_bs.playerInfoUpdatePacketCls = static_cast<jclass>(env->NewGlobalRef(piuCls));
        g_bs.playerInfoUpdatePacketBufCtor = env->GetMethodID(piuCls, "<init>",
            "(Lnet/minecraft/network/FriendlyByteBuf;)V");

        g_bs.playerInfoUpdatePacketWriteMid = findMethodByDesc(piuCls,
            "(Lnet/minecraft/network/FriendlyByteBuf;)V", false);

        jmethodID listMids[2] = {nullptr, nullptr};
        int nList = findMethodsByDesc(piuCls, "()Ljava/util/List;", false, listMids, 2);
        g_bs.piuEntriesMidA = listMids[0];
        g_bs.piuEntriesMidB = listMids[1];
        LogTo("  piu: %d ()List accessor(s) cached", nList);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(piuCls);
    }

    jclass piEntryCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry",
        "Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket$Entry;");
    if (piEntryCls) {
        g_bs.piEntryCls = static_cast<jclass>(env->NewGlobalRef(piEntryCls));
        g_bs.piEntryProfileIdMid   = findMethodByDesc(piEntryCls, "()Ljava/util/UUID;", false);
        g_bs.piEntryGameModeMid     = findMethodByDesc(piEntryCls,
            "()Lnet/minecraft/world/level/GameType;", false);
        g_bs.piEntryListedMid       = findMethodByDesc(piEntryCls, "()Z", false);
        static const char* const kHashExcl[] = { "hashCode" };
        g_bs.piEntryLatencyMid      = findMethodByDescExcl(piEntryCls, "()I", false, kHashExcl, 1);
        g_bs.piEntryDisplayNameMid  = findMethodByDesc(piEntryCls,
            "()Lnet/minecraft/network/chat/Component;", false);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(piEntryCls);
    }

    jclass gameTypeCls = loadOrFind(env, mcLoader, "net.minecraft.world.level.GameType",
                                    "Lnet/minecraft/world/level/GameType;");
    if (gameTypeCls) {
        static const char* const kIdExcl[] = { "ordinal", "hashCode" };
        g_bs.gameTypeGetIdMid = findMethodByDescExcl(gameTypeCls, "()I", false, kIdExcl, 2);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(gameTypeCls);
    }

    if (g_bs.friendlyBufCls) {
        g_bs.fbbWriteComponentMid = findMethodByDesc(g_bs.friendlyBufCls,
            "(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/FriendlyByteBuf;", false);
    }

    jclass listCls = env->FindClass("java/util/List");
    if (listCls) {
        g_bs.listSizeMid = env->GetMethodID(listCls, "size", "()I");
        g_bs.listGetMid  = env->GetMethodID(listCls, "get", "(I)Ljava/lang/Object;");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(listCls);
    }

    {
        jclass bbCls = loadOrFind(env, mcLoader, "io.netty.buffer.ByteBuf",
                                  "Lio/netty/buffer/ByteBuf;");
        if (bbCls) {
            g_bs.byteBufGetByteMid = env->GetMethodID(bbCls, "getByte", "(I)B");
            if (env->ExceptionCheck()) env->ExceptionClear();
            env->DeleteLocalRef(bbCls);
        }
    }

    jclass appCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundAddPlayerPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundAddPlayerPacket;");
    if (appCls) {
        g_bs.addPlayerPacketCls = static_cast<jclass>(env->NewGlobalRef(appCls));
        g_bs.addPlayerPacketUuidFid = findFieldByDesc(appCls, "Ljava/util/UUID;", false);
        env->DeleteLocalRef(appCls);
    }

    jclass cpp = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundCustomPayloadPacket;");
    if (cpp) {
        g_bs.customPayloadPacketCls = static_cast<jclass>(env->NewGlobalRef(cpp));
        env->DeleteLocalRef(cpp);
    }

    jclass sptCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundSetPlayerTeamPacket;");
    if (sptCls) {
        g_bs.setPlayerTeamPacketCls = static_cast<jclass>(env->NewGlobalRef(sptCls));
        g_bs.setPlayerTeamPacketBufCtor = env->GetMethodID(sptCls, "<init>",
            "(Lnet/minecraft/network/FriendlyByteBuf;)V");
        g_bs.setPlayerTeamMethodFid  = findFieldByDesc(sptCls, "I", false);
        g_bs.setPlayerTeamNameFid    = findFieldByDesc(sptCls, "Ljava/lang/String;", false);
        g_bs.setPlayerTeamPlayersFid = findFieldByDesc(sptCls, "Ljava/util/Collection;", false);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(sptCls);
    }
    jclass collCls = env->FindClass("java/util/Collection");
    if (collCls) {
        g_bs.collectionContainsMid = env->GetMethodID(collCls, "contains", "(Ljava/lang/Object;)Z");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(collCls);
    }

    jclass uuidClsL = env->FindClass("java/util/UUID");
    if (uuidClsL) {
        g_bs.uuidGetMsbMid = env->GetMethodID(uuidClsL, "getMostSignificantBits",  "()J");
        g_bs.uuidGetLsbMid = env->GetMethodID(uuidClsL, "getLeastSignificantBits", "()J");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(uuidClsL);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();

    jclass lfp = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.login.ClientboundGameProfilePacket",
        "Lnet/minecraft/network/protocol/login/ClientboundGameProfilePacket;");
    if (lfp) {
        g_bs.loginFinishedPacketCls = static_cast<jclass>(env->NewGlobalRef(lfp));

        g_bs.loginFinishedPacketCtor = findMethodByDesc(lfp,
            "(Lcom/mojang/authlib/GameProfile;)V", false);
        env->DeleteLocalRef(lfp);
    }

    jclass hello = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.login.ServerboundHelloPacket",
        "Lnet/minecraft/network/protocol/login/ServerboundHelloPacket;");
    if (hello) {
        g_bs.helloPacketCls = static_cast<jclass>(env->NewGlobalRef(hello));
        g_bs.helloPacketNameFid = findFieldByDesc(hello, "Ljava/lang/String;", false);
        env->DeleteLocalRef(hello);
    }

    jclass intent = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.handshake.ClientIntentionPacket",
        "Lnet/minecraft/network/protocol/handshake/ClientIntentionPacket;");
    if (intent) {
        g_bs.intentPacketCls = static_cast<jclass>(env->NewGlobalRef(intent));
        g_bs.intentionPacketIntentFid = findFieldByDesc(intent,
            "Lnet/minecraft/network/ConnectionProtocol;", false);
        env->DeleteLocalRef(intent);
    }

    jclass sReq = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.status.ServerboundStatusRequestPacket",
        "Lnet/minecraft/network/protocol/status/ServerboundStatusRequestPacket;");
    if (sReq) { g_bs.statusRequestPacketCls = static_cast<jclass>(env->NewGlobalRef(sReq)); env->DeleteLocalRef(sReq); }

    jclass pReq = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.status.ServerboundPingRequestPacket",
        "Lnet/minecraft/network/protocol/status/ServerboundPingRequestPacket;");
    if (pReq) {
        g_bs.pingRequestPacketCls = static_cast<jclass>(env->NewGlobalRef(pReq));
        g_bs.pingRequestPacketTimeFid = findFieldByDesc(pReq, "J", false);
        env->DeleteLocalRef(pReq);
    }

    jclass pong = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.status.ClientboundPongResponsePacket",
        "Lnet/minecraft/network/protocol/status/ClientboundPongResponsePacket;");
    if (pong) {
        g_bs.pongResponsePacketCls = static_cast<jclass>(env->NewGlobalRef(pong));
        g_bs.pongResponsePacketCtor = findMethodByDesc(pong, "(J)V", false);
        env->DeleteLocalRef(pong);
    }

    jclass sResp = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.status.ClientboundStatusResponsePacket",
        "Lnet/minecraft/network/protocol/status/ClientboundStatusResponsePacket;");
    if (sResp) {
        g_bs.statusResponsePacketCls = static_cast<jclass>(env->NewGlobalRef(sResp));

        g_bs.statusResponsePacketCtor = findMethodByDesc(sResp,
            "(Lnet/minecraft/network/protocol/status/ServerStatus;)V", false);
        env->DeleteLocalRef(sResp);
    }
    jclass ss = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.status.ServerStatus",
        "Lnet/minecraft/network/protocol/status/ServerStatus;");
    if (ss) {
        g_bs.serverStatusCls = static_cast<jclass>(env->NewGlobalRef(ss));
        g_bs.serverStatusCtor = findMethodByDesc(ss,
            "(Lnet/minecraft/network/chat/Component;Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;Z)V", false);
        env->DeleteLocalRef(ss);
    }
    jclass component = loadOrFind(env, mcLoader, "net.minecraft.network.chat.Component",
                                  "Lnet/minecraft/network/chat/Component;");
    if (component) {
        g_bs.componentCls = static_cast<jclass>(env->NewGlobalRef(component));
        g_bs.componentLiteralMid = findMethodByDesc(component,
            "(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;", true);
        env->DeleteLocalRef(component);
    }

    jclass pp = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;");
    if (pp) {
        g_bs.playerPositionPacketCls = static_cast<jclass>(env->NewGlobalRef(pp));
        g_bs.playerPositionPacketCtor = findMethodByDesc(pp,
            "(DDDFFLjava/util/Set;I)V", false);
        env->DeleteLocalRef(pp);
    }

    jclass ccc = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundSetChunkCacheCenterPacket;");
    if (ccc) {
        g_bs.chunkCacheCenterPacketCls = static_cast<jclass>(env->NewGlobalRef(ccc));
        g_bs.chunkCacheCenterPacketCtor = findMethodByDesc(ccc, "(II)V", false);
        env->DeleteLocalRef(ccc);
    }
    jclass ccr = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundSetChunkCacheRadiusPacket;");
    if (ccr) {
        g_bs.chunkCacheRadiusPacketCls = static_cast<jclass>(env->NewGlobalRef(ccr));
        g_bs.chunkCacheRadiusPacketCtor = findMethodByDesc(ccr, "(I)V", false);
        env->DeleteLocalRef(ccr);
    }
    jclass acceptTeleport = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket",
        "Lnet/minecraft/network/protocol/game/ServerboundAcceptTeleportationPacket;");
    if (acceptTeleport) {
        g_bs.acceptTeleportPacketCls = static_cast<jclass>(env->NewGlobalRef(acceptTeleport));
        env->DeleteLocalRef(acceptTeleport);
    }
    jclass blockPos = loadOrFind(env, mcLoader, "net.minecraft.core.BlockPos",
                                 "Lnet/minecraft/core/BlockPos;");
    if (blockPos) {
        g_bs.blockPosCls = static_cast<jclass>(env->NewGlobalRef(blockPos));
        g_bs.blockPosCtor = findMethodByDesc(blockPos, "(III)V", false);
        env->DeleteLocalRef(blockPos);
    }
    jclass defaultSpawn = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundSetDefaultSpawnPositionPacket;");
    if (defaultSpawn) {
        g_bs.defaultSpawnPacketCls = static_cast<jclass>(env->NewGlobalRef(defaultSpawn));
        g_bs.defaultSpawnPacketCtor = findMethodByDesc(defaultSpawn,
            "(Lnet/minecraft/core/BlockPos;F)V", false);
        env->DeleteLocalRef(defaultSpawn);
    }

    jclass setC = env->FindClass("java/util/Set");
    if (setC) {
        g_bs.setCls = static_cast<jclass>(env->NewGlobalRef(setC));
        g_bs.setOfMid = env->GetStaticMethodID(setC, "of", "()Ljava/util/Set;");
        env->DeleteLocalRef(setC);
    }

    jclass kap = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundKeepAlivePacket",
        "Lnet/minecraft/network/protocol/game/ClientboundKeepAlivePacket;");
    if (kap) {
        g_bs.keepAlivePacketCls = static_cast<jclass>(env->NewGlobalRef(kap));
        g_bs.keepAlivePacketCtor = findMethodByDesc(kap, "(J)V", false);
        env->DeleteLocalRef(kap);
    }

    jclass cbp = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundBundlePacket",
        "Lnet/minecraft/network/protocol/game/ClientboundBundlePacket;");
    if (cbp) {
        g_bs.bundlePacketCls = static_cast<jclass>(env->NewGlobalRef(cbp));
        jclass bp = loadOrFind(env, mcLoader,
            "net.minecraft.network.protocol.BundlePacket",
            "Lnet/minecraft/network/protocol/BundlePacket;");
        if (bp) {
            g_bs.bundleSubPacketsMid = findMethodByDesc(bp, "()Ljava/lang/Iterable;", false);
            env->DeleteLocalRef(bp);
        }
        env->DeleteLocalRef(cbp);
    } else {
        LogTo("BServer: ClientboundBundlePacket not found — bundles won't be expanded");
    }
    jclass iterableCls = env->FindClass("java/lang/Iterable");
    if (iterableCls) {
        g_bs.iterableIteratorMid = env->GetMethodID(iterableCls, "iterator", "()Ljava/util/Iterator;");
        env->DeleteLocalRef(iterableCls);
    }
    jclass iteratorCls = env->FindClass("java/util/Iterator");
    if (iteratorCls) {
        g_bs.iteratorHasNextMid = env->GetMethodID(iteratorCls, "hasNext", "()Z");
        g_bs.iteratorNextMid    = env->GetMethodID(iteratorCls, "next", "()Ljava/lang/Object;");
        env->DeleteLocalRef(iteratorCls);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();

    jclass uuidCls = env->FindClass("java/util/UUID");
    if (uuidCls) {
        g_bs.uuidCls = static_cast<jclass>(env->NewGlobalRef(uuidCls));
        g_bs.uuidNameUuidFromBytesMid = env->GetStaticMethodID(uuidCls,
            "nameUUIDFromBytes", "([B)Ljava/util/UUID;");
        env->DeleteLocalRef(uuidCls);
    }

    LogTo("cacheJavaRefs: configureSer=%p send=%p attrProtoFid=%p bundlerFid=%p "
          "protoHS=%p protoLOGIN=%p protoPLAY=%p flowSB=%p pipMid=%p addLast=%p "
          "attrSet=%p gpCtor=%p lfpCtor=%p helloName=%p uuidFromBytes=%p",
          (void*)g_bs.connectionConfigureSerMid, (void*)g_bs.connectionSendMid,
          (void*)g_bs.connectionAttrProtocolFid, (void*)g_bs.bundlerProviderFid,
          (void*)g_bs.protoHandshaking, (void*)g_bs.protoLogin, (void*)g_bs.protoPlay,
          (void*)g_bs.flowServerbound, (void*)g_bs.channelPipelineMid,
          (void*)g_bs.pipelineAddLastMid, (void*)g_bs.attributeSetMid,
          (void*)g_bs.gameProfileCtor, (void*)g_bs.loginFinishedPacketCtor,
          (void*)g_bs.helloPacketNameFid, (void*)g_bs.uuidNameUuidFromBytesMid);
    if (env->ExceptionCheck()) env->ExceptionClear();

    if (g_bs.minecraftCls) {
        g_bs.mcGetConnectionMid = findMethodByDesc(g_bs.minecraftCls,
            "()Lnet/minecraft/client/multiplayer/ClientPacketListener;", false);
        g_bs.mcPlayerFid   = findFieldByDesc(g_bs.minecraftCls,
            "Lnet/minecraft/client/player/LocalPlayer;", false);
        g_bs.mcGameModeFid = findFieldByDesc(g_bs.minecraftCls,
            "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;", false);
        g_bs.mcLevelFid    = findFieldByDesc(g_bs.minecraftCls,
            "Lnet/minecraft/client/multiplayer/ClientLevel;", false);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    jclass cplCls = loadOrFind(env, mcLoader,
        "net.minecraft.client.multiplayer.ClientPacketListener",
        "Lnet/minecraft/client/multiplayer/ClientPacketListener;");
    if (cplCls) {
        g_bs.clientPacketListenerCls = static_cast<jclass>(env->NewGlobalRef(cplCls));
        g_bs.cplGetConnectionMid  = findMethodByDesc(cplCls, "()Lnet/minecraft/network/Connection;", false);
        g_bs.cplLevelsMid         = findMethodByDesc(cplCls, "()Ljava/util/Set;", false);
        g_bs.cplRegistryAccessMid = findMethodByDesc(cplCls, "()Lnet/minecraft/core/RegistryAccess;", false);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(cplCls);
    }
    jclass raCls = loadOrFind(env, mcLoader, "net.minecraft.core.RegistryAccess",
                              "Lnet/minecraft/core/RegistryAccess;");
    if (raCls) {
        g_bs.registryAccessFreezeMid = findMethodByDesc(raCls,
            "()Lnet/minecraft/core/RegistryAccess$Frozen;", false);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(raCls);
    }
    jclass mpgmCls = loadOrFind(env, mcLoader,
        "net.minecraft.client.multiplayer.MultiPlayerGameMode",
        "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;");
    if (mpgmCls) {

        g_bs.gameModeGetTypeMid = findMethodByDesc(mpgmCls,
            "()Lnet/minecraft/world/level/GameType;", false);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(mpgmCls);
    }
    jclass lvlCls = loadOrFind(env, mcLoader, "net.minecraft.world.level.Level",
                               "Lnet/minecraft/world/level/Level;");
    if (lvlCls) {
        g_bs.levelCls = static_cast<jclass>(env->NewGlobalRef(lvlCls));

        jmethodID dimMids[2] = {nullptr, nullptr};
        int nDim = findMethodsByDesc(lvlCls, "()Lnet/minecraft/resources/ResourceKey;", false, dimMids, 2);
        g_bs.levelDimMidA = dimMids[0];
        g_bs.levelDimMidB = dimMids[1];
        LogTo("  level: %d ()ResourceKey method(s)", nDim);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(lvlCls);
    }
    jclass clientLevelCls = loadOrFind(env, mcLoader, "net.minecraft.client.multiplayer.ClientLevel",
                                       "Lnet/minecraft/client/multiplayer/ClientLevel;");
    if (clientLevelCls) {
        g_bs.clientLevelChunkSourceFid = findFieldByDesc(clientLevelCls,
            "Lnet/minecraft/client/multiplayer/ClientChunkCache;", false);
        // 枚举注入前已经存在的 NPC 与其他实体。
        g_bs.clientLevelEntitiesForRenderingMid = findMethodByDesc(
            clientLevelCls, "()Ljava/lang/Iterable;", false);
        env->DeleteLocalRef(clientLevelCls);
    }
    // 解析 Entity 基类方法，用实体当前状态构造完整初始化包。
    jclass entityCls = loadOrFind(env, mcLoader, "net.minecraft.world.entity.Entity",
                                  "Lnet/minecraft/world/entity/Entity;");
    if (entityCls) {
        g_bs.entityCls = static_cast<jclass>(env->NewGlobalRef(entityCls));
        g_bs.entityGetIdMid = findMethodByDesc(entityCls, "()I", false);
        g_bs.entityIsRemovedMid = findMethodByDesc(entityCls, "()Z", false);
        g_bs.entityGetAddEntityPacketMid = findMethodByDesc(entityCls,
            "()Lnet/minecraft/network/protocol/Packet;", false);
        g_bs.entityGetEntityDataMid = findMethodByDesc(entityCls,
            "()Lnet/minecraft/network/syncher/SynchedEntityData;", false);
        env->DeleteLocalRef(entityCls);
    }
    jclass sedCls = loadOrFind(env, mcLoader, "net.minecraft.network.syncher.SynchedEntityData",
                               "Lnet/minecraft/network/syncher/SynchedEntityData;");
    if (sedCls) {
        g_bs.synchedEntityDataCls = static_cast<jclass>(env->NewGlobalRef(sedCls));
        g_bs.synchedGetNonDefaultValuesMid = findMethodByDesc(sedCls,
            "()Ljava/util/List;", false);
        env->DeleteLocalRef(sedCls);
    }
    // 元数据、速度和乘客包均使用 1.20.1 原生构造器生成。
    jclass sedPacketCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundSetEntityDataPacket;");
    if (sedPacketCls) {
        g_bs.setEntityDataPacketCls = static_cast<jclass>(env->NewGlobalRef(sedPacketCls));
        g_bs.setEntityDataPacketCtor = env->GetMethodID(sedPacketCls, "<init>",
            "(ILjava/util/List;)V");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(sedPacketCls);
    }
    jclass motionPacketCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundSetEntityMotionPacket;");
    if (motionPacketCls) {
        g_bs.setEntityMotionPacketCls = static_cast<jclass>(env->NewGlobalRef(motionPacketCls));
        g_bs.setEntityMotionPacketCtor = env->GetMethodID(motionPacketCls, "<init>",
            "(Lnet/minecraft/world/entity/Entity;)V");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(motionPacketCls);
    }
    jclass passengersPacketCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundSetPassengersPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundSetPassengersPacket;");
    if (passengersPacketCls) {
        g_bs.setPassengersPacketCls = static_cast<jclass>(env->NewGlobalRef(passengersPacketCls));
        g_bs.setPassengersPacketCtor = env->GetMethodID(passengersPacketCls, "<init>",
            "(Lnet/minecraft/world/entity/Entity;)V");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(passengersPacketCls);
    }
    jclass playerCls = loadOrFind(env, mcLoader, "net.minecraft.world.entity.player.Player",
                                  "Lnet/minecraft/world/entity/player/Player;");
    if (playerCls) {
        g_bs.mcInventoryMenuFid = findFieldByDesc(playerCls,
            "Lnet/minecraft/world/inventory/InventoryMenu;", false);
        g_bs.playerGetInventoryMid = findMethodByDesc(playerCls,
            "()Lnet/minecraft/world/entity/player/Inventory;", false);
        env->DeleteLocalRef(playerCls);
    }
    jclass inventoryCls = loadOrFind(env, mcLoader,
        "net.minecraft.world.entity.player.Inventory",
        "Lnet/minecraft/world/entity/player/Inventory;");
    if (inventoryCls) {
        g_bs.inventoryCls = static_cast<jclass>(env->NewGlobalRef(inventoryCls));
        g_bs.inventorySelectedFid = env->GetFieldID(inventoryCls, "selected", "I");
        if (!g_bs.inventorySelectedFid && env->ExceptionCheck()) env->ExceptionClear();
        if (!g_bs.inventorySelectedFid) {
            g_bs.inventorySelectedFid = env->GetFieldID(inventoryCls, "f_35977_", "I");
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
        env->DeleteLocalRef(inventoryCls);
    }
    jclass containerMenuCls = loadOrFind(env, mcLoader,
        "net.minecraft.world.inventory.AbstractContainerMenu",
        "Lnet/minecraft/world/inventory/AbstractContainerMenu;");
    if (containerMenuCls) {
        g_bs.containerMenuCls = static_cast<jclass>(env->NewGlobalRef(containerMenuCls));
        g_bs.containerGetItemsMid = findMethodByDesc(containerMenuCls,
            "()Lnet/minecraft/core/NonNullList;", false);
        g_bs.containerGetCarriedMid = findMethodByDesc(containerMenuCls,
            "()Lnet/minecraft/world/item/ItemStack;", false);
        g_bs.containerGetStateIdMid = findMethodByDesc(containerMenuCls, "()I", false);
        env->DeleteLocalRef(containerMenuCls);
    }
    jclass contentPacketCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundContainerSetContentPacket;");
    if (contentPacketCls) {
        g_bs.containerContentPacketCls = static_cast<jclass>(env->NewGlobalRef(contentPacketCls));
        g_bs.containerContentPacketCtor = env->GetMethodID(contentPacketCls, "<init>",
            "(IILnet/minecraft/core/NonNullList;Lnet/minecraft/world/item/ItemStack;)V");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(contentPacketCls);
    }
    jclass carriedItemPacketCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundSetCarriedItemPacket;");
    if (carriedItemPacketCls) {
        g_bs.carriedItemPacketCls = static_cast<jclass>(env->NewGlobalRef(carriedItemPacketCls));
        g_bs.carriedItemPacketCtor = env->GetMethodID(carriedItemPacketCls, "<init>", "(I)V");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(carriedItemPacketCls);
    }
    jclass chunkCacheCls = loadOrFind(env, mcLoader, "net.minecraft.client.multiplayer.ClientChunkCache",
                                      "Lnet/minecraft/client/multiplayer/ClientChunkCache;");
    if (chunkCacheCls) {
        g_bs.clientChunkCacheCls = static_cast<jclass>(env->NewGlobalRef(chunkCacheCls));
        g_bs.clientChunkGetMid = findMethodByDesc(chunkCacheCls,
            "(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;", false);
        g_bs.clientChunkLightEngineMid = findMethodByDesc(chunkCacheCls,
            "()Lnet/minecraft/world/level/lighting/LevelLightEngine;", false);
        env->DeleteLocalRef(chunkCacheCls);
    }
    jclass chunkStatusCls = loadOrFind(env, mcLoader, "net.minecraft.world.level.chunk.ChunkStatus",
                                       "Lnet/minecraft/world/level/chunk/ChunkStatus;");
    if (chunkStatusCls) {
        g_bs.chunkStatusCls = static_cast<jclass>(env->NewGlobalRef(chunkStatusCls));
        g_bs.chunkStatusFullFid = findFieldByDesc(chunkStatusCls,
            "Lnet/minecraft/world/level/chunk/ChunkStatus;", true);
        env->DeleteLocalRef(chunkStatusCls);
    }
    jclass chunkPacketCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;");
    if (chunkPacketCls) {
        g_bs.chunkWithLightPacketCls = static_cast<jclass>(env->NewGlobalRef(chunkPacketCls));
        g_bs.chunkWithLightPacketCtor = findMethodByDesc(chunkPacketCls,
            "(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/lighting/LevelLightEngine;Ljava/util/BitSet;Ljava/util/BitSet;)V", false);
        env->DeleteLocalRef(chunkPacketCls);
    }
    jclass objCls = env->FindClass("java/lang/Object");
    if (objCls) {
        g_bs.objToStringMid = env->GetMethodID(objCls, "toString", "()Ljava/lang/String;");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(objCls);
    }
    jclass optionalClsL = env->FindClass("java/util/Optional");
    if (optionalClsL) {
        g_bs.optionalCls = static_cast<jclass>(env->NewGlobalRef(optionalClsL));
        g_bs.optionalEmptyMid = env->GetStaticMethodID(optionalClsL, "empty", "()Ljava/util/Optional;");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(optionalClsL);
    }
    jclass loginCls = loadOrFind(env, mcLoader,
        "net.minecraft.network.protocol.game.ClientboundLoginPacket",
        "Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;");
    if (loginCls) {
        g_bs.loginPacketCls = static_cast<jclass>(env->NewGlobalRef(loginCls));

        g_bs.loginPacketCtor = env->GetMethodID(loginCls, "<init>",
            "(IZLnet/minecraft/world/level/GameType;Lnet/minecraft/world/level/GameType;"
            "Ljava/util/Set;Lnet/minecraft/core/RegistryAccess$Frozen;"
            "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;"
            "JIIIZZZZLjava/util/Optional;I)V");
        if (env->ExceptionCheck()) env->ExceptionClear();
        LogTo("  login ctor %s", g_bs.loginPacketCtor ? "resolved" : "MISSING");
        env->DeleteLocalRef(loginCls);
    }

    return g_bs.connectionConfigureSerMid && g_bs.connectionSendMid &&
           g_bs.flowServerbound && g_bs.channelPipelineMid && g_bs.pipelineAddLastMid &&
           g_bs.channelWriteAndFlushMid && g_bs.channelAttrMid && g_bs.attributeSetMid;
}

bool bindServer(JNIEnv* env, jobject mcLoader, unsigned short port) {
    jclass elgCls  = loadOrFind(env, mcLoader, "io.netty.channel.nio.NioEventLoopGroup",
                                "Lio/netty/channel/nio/NioEventLoopGroup;");
    jclass sbCls   = loadOrFind(env, mcLoader, "io.netty.bootstrap.ServerBootstrap",
                                "Lio/netty/bootstrap/ServerBootstrap;");
    jclass sscCls  = loadOrFind(env, mcLoader, "io.netty.channel.socket.nio.NioServerSocketChannel",
                                "Lio/netty/channel/socket/nio/NioServerSocketChannel;");
    jclass isaCls  = env->FindClass("java/net/InetSocketAddress");
    if (!elgCls || !sbCls || !sscCls || !isaCls) {
        LogTo("BServer: bind prerequisites missing (eventLoop=%p bootstrap=%p channel=%p address=%p)",
              (void*)elgCls, (void*)sbCls, (void*)sscCls, (void*)isaCls);
        return false;
    }

    jmethodID elgCtor = env->GetMethodID(elgCls, "<init>", "(I)V");
    if (!elgCtor && env->ExceptionCheck()) env->ExceptionClear();
    jobject elg = elgCtor ? env->NewObject(elgCls, elgCtor, (jint)1) : nullptr;
    if (!elg) {
        jmethodID fallbackCtor = env->GetMethodID(elgCls, "<init>", "()V");
        if (!fallbackCtor && env->ExceptionCheck()) env->ExceptionClear();
        if (fallbackCtor) elg = env->NewObject(elgCls, fallbackCtor);
    }
    if (env->ExceptionCheck()) { LogAndClearException(env, "NioELG"); return false; }

    auto stopGroup = [&](jobject group) {
        if (!group) return;
        jclass cls = env->GetObjectClass(group);
        jmethodID stop = cls ? env->GetMethodID(cls, "shutdownGracefully", "()Lio/netty/util/concurrent/Future;") : nullptr;
        if (stop && !env->ExceptionCheck()) {
            jobject f = env->CallObjectMethod(group, stop);
            if (f) env->DeleteLocalRef(f);
        }
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (cls) env->DeleteLocalRef(cls);
    };

    jobject sb  = env->NewObject(sbCls, env->GetMethodID(sbCls, "<init>", "()V"));
    if (!sb || env->ExceptionCheck()) {
        LogAndClearException(env, "BServer: ServerBootstrap ctor");
        stopGroup(elg);
        if (elg) env->DeleteLocalRef(elg);
        return false;
    }
    env->CallObjectMethod(sb,
        env->GetMethodID(sbCls, "group",
                         "(Lio/netty/channel/EventLoopGroup;)Lio/netty/bootstrap/ServerBootstrap;"),
        elg);
    env->CallObjectMethod(sb,
        env->GetMethodID(sbCls, "channel",
                         "(Ljava/lang/Class;)Lio/netty/bootstrap/AbstractBootstrap;"),
        sscCls);
    jobject init = env->NewObject(g_bs.initClass, g_bs.initCtor);
    env->CallObjectMethod(sb,
        env->GetMethodID(sbCls, "childHandler",
                         "(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/ServerBootstrap;"),
        init);

    env->DeleteLocalRef(init);

    jstring host = env->NewStringUTF("127.0.0.1");
    jobject isa  = env->NewObject(isaCls,
        env->GetMethodID(isaCls, "<init>", "(Ljava/lang/String;I)V"),
        host, (jint)port);
    env->DeleteLocalRef(host);

    jobject future = env->CallObjectMethod(sb,
        env->GetMethodID(sbCls, "bind",
                         "(Ljava/net/SocketAddress;)Lio/netty/channel/ChannelFuture;"),
        isa);
    env->DeleteLocalRef(isa);
    if (env->ExceptionCheck()) {
        LogAndClearException(env, "BServer: bind");
        env->DeleteLocalRef(sb);
        stopGroup(elg);
        env->DeleteLocalRef(elg);
        return false;
    }
    jobject boundChannel = nullptr;
    if (future) {
        jclass fCls = env->GetObjectClass(future);
        env->CallObjectMethod(future, env->GetMethodID(fCls, "sync", "()Lio/netty/channel/ChannelFuture;"));
        if (env->ExceptionCheck()) {
            LogAndClearException(env, "BServer: bind.sync");
            env->DeleteLocalRef(fCls);
            env->DeleteLocalRef(future);
            env->DeleteLocalRef(sb);
            stopGroup(elg);
            env->DeleteLocalRef(elg);
            return false;
        }
        jmethodID channelMid = env->GetMethodID(fCls, "channel", "()Lio/netty/channel/Channel;");
        if (channelMid && !env->ExceptionCheck()) boundChannel = env->CallObjectMethod(future, channelMid);
        env->DeleteLocalRef(fCls);
    }
    if (boundChannel && !env->ExceptionCheck()) {
        g_bs.listenerChannel = env->NewGlobalRef(boundChannel);
        env->DeleteLocalRef(boundChannel);
    } else if (env->ExceptionCheck()) env->ExceptionClear();
    if (future) env->DeleteLocalRef(future);
    g_bs.eventLoopGroup = env->NewGlobalRef(elg);
    env->DeleteLocalRef(sb);
    env->DeleteLocalRef(elg);
    LogTo("BServer: bound 127.0.0.1:%u", static_cast<unsigned>(port));
    return true;
}

}

bool InstallBServer(JNIEnv* env, unsigned short port) {
    if (g_bs.bServerBound) return true;
    jobject mcLoader = GetMinecraftClassLoader(env, g_jvmti);
    if (!mcLoader) return false;
    bool defined = defineInitClass(env, mcLoader);
    bool refs = defined && cacheJavaRefs(env, mcLoader);
    bool bound = refs && bindServer(env, mcLoader, port);
    LogTo("BServer: install stages define=%d refs=%d bind=%d port=%u",
          defined ? 1 : 0, refs ? 1 : 0, bound ? 1 : 0, static_cast<unsigned>(port));
    bool ok = defined && refs && bound;
    env->DeleteGlobalRef(mcLoader);
    if (ok) g_bs.bServerBound = true;
    return ok;
}

void BServer_StopListener(JNIEnv* env) {
    if (!env) return;
    jobject listener = g_bs.listenerChannel ? env->NewLocalRef(g_bs.listenerChannel) : nullptr;
    jobject group = g_bs.eventLoopGroup ? env->NewLocalRef(g_bs.eventLoopGroup) : nullptr;
    if (g_bs.listenerChannel) { env->DeleteGlobalRef(g_bs.listenerChannel); g_bs.listenerChannel = nullptr; }
    if (g_bs.eventLoopGroup) { env->DeleteGlobalRef(g_bs.eventLoopGroup); g_bs.eventLoopGroup = nullptr; }
    if (listener && g_bs.channelCloseMid) {
        jobject f = env->CallObjectMethod(listener, g_bs.channelCloseMid);
        if (f) env->DeleteLocalRef(f);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(listener);
    }
    if (group) {
        jclass cls = env->GetObjectClass(group);
        jmethodID stop = cls ? env->GetMethodID(cls, "shutdownGracefully", "()Lio/netty/util/concurrent/Future;") : nullptr;
        if (stop && !env->ExceptionCheck()) {
            jobject f = env->CallObjectMethod(group, stop);
            if (f) env->DeleteLocalRef(f);
        }
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (cls) env->DeleteLocalRef(cls);
        env->DeleteLocalRef(group);
    }
    g_bs.bServerBound = false;
}

bool BServer_IsBActive() {
    return g_bs.bState.load(std::memory_order_acquire) == BState::Play;
}

bool BServer_IsLoginIntention(JNIEnv* env, jobject packet) {
    if (!env || !packet) return false;
    if (!g_bs.intentPacketCls || !env->IsInstanceOf(packet, g_bs.intentPacketCls))
        return false;
    if (!g_bs.intentionPacketIntentFid || !g_bs.protoLogin) return false;
    jobject intent = env->GetObjectField(packet, g_bs.intentionPacketIntentFid);
    if (!intent) return false;
    bool isLogin = env->IsSameObject(intent, g_bs.protoLogin);
    env->DeleteLocalRef(intent);
    return isLogin;
}

bool BServer_WaitForBConnected(int timeoutMs) {
    std::unique_lock<std::mutex> lock(g_bConnMu);
    if (g_bConnected) return true;
    if (timeoutMs < 0) {
        g_bConnCv.wait(lock, [] { return g_bConnected; });
        return true;
    }
    g_bConnCv.wait_for(lock, std::chrono::milliseconds(timeoutMs),
                       [] { return g_bConnected; });
    return g_bConnected;
}

void BServer_SetTargetConnection(JNIEnv* env, jobject connection) {
    std::lock_guard<std::mutex> l(g_bs.targetMu);
    if (g_bs.targetAConnection) env->DeleteGlobalRef(g_bs.targetAConnection);
    g_bs.targetAConnection = connection ? env->NewGlobalRef(connection) : nullptr;
    LogTo("BServer: target A connection = %p", (void*)g_bs.targetAConnection);
}

void BServer_OnAChannelInactive(JNIEnv* env, jobject ctx) {
    if (!env || !ctx) return;
    jclass cls = env->GetObjectClass(ctx);
    jmethodID channelMid = cls ? env->GetMethodID(cls, "channel", "()Lio/netty/channel/Channel;") : nullptr;
    jobject channel = channelMid && !env->ExceptionCheck() ? env->CallObjectMethod(ctx, channelMid) : nullptr;
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (cls) env->DeleteLocalRef(cls);
    bool matches = false;
    {
        std::lock_guard<std::mutex> lock(g_bs.targetMu);
        if (channel && g_bs.targetAConnection && g_bs.connectionChannelFid) {
            jobject targetChannel = env->GetObjectField(g_bs.targetAConnection, g_bs.connectionChannelFid);
            if (targetChannel && !env->ExceptionCheck()) matches = env->IsSameObject(channel, targetChannel);
            if (targetChannel) env->DeleteLocalRef(targetChannel);
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
        if (matches && g_bs.targetAConnection) {
            env->DeleteGlobalRef(g_bs.targetAConnection);
            g_bs.targetAConnection = nullptr;
        }
    }
    if (channel) env->DeleteLocalRef(channel);
    if (!matches) return;
    g_bs.midSession.store(false, std::memory_order_release);
    g_cache.clear(env);
    clearBootstrapQueue(env);
    LogTo("A connection inactive: target connection, world cache and mid-session state cleared");
}

bool BServer_TryCaptureLiveConnection(JNIEnv* env) {
    if (!env) return false;
    if (!g_bs.minecraftCls || !g_bs.mcGetInstanceMid || !g_bs.mcGetConnectionMid ||
        !g_bs.cplGetConnectionMid || !g_bs.connectionChannelFid ||
        !g_bs.channelPipelineMid) {
        LogTo("mid-session: refs missing, cannot capture live connection");
        return false;
    }
    jobject mc = env->CallStaticObjectMethod(g_bs.minecraftCls, g_bs.mcGetInstanceMid);
    if (!mc || env->ExceptionCheck()) { env->ExceptionClear(); return false; }
    jobject cpl = env->CallObjectMethod(mc, g_bs.mcGetConnectionMid);
    env->DeleteLocalRef(mc);
    if (!cpl || env->ExceptionCheck()) { env->ExceptionClear(); return false; }
    jobject conn = env->CallObjectMethod(cpl, g_bs.cplGetConnectionMid);
    env->DeleteLocalRef(cpl);
    if (!conn || env->ExceptionCheck()) { env->ExceptionClear(); return false; }

    BServer_SetTargetConnection(env, conn);

    jobject channel = env->GetObjectField(conn, g_bs.connectionChannelFid);
    env->DeleteLocalRef(conn);
    if (!channel || env->ExceptionCheck()) { env->ExceptionClear(); LogTo("mid-session: connection.channel null"); return false; }
    jobject pipeline = env->CallObjectMethod(channel, g_bs.channelPipelineMid);
    env->DeleteLocalRef(channel);
    if (!pipeline || env->ExceptionCheck()) { env->ExceptionClear(); LogTo("mid-session: channel.pipeline() null"); return false; }
    RelayHandler_AttachToPipelineObject(env, pipeline);
    env->DeleteLocalRef(pipeline);

    g_bs.midSession.store(true, std::memory_order_release);
    LogTo("mid-session: captured A's live Connection + attached relay (A already in-game)");
    return true;
}

namespace {

bool replayLoadedChunkToB(JNIEnv* env, jobject ch, jint x, jint z);

void writeToB(JNIEnv* env, jobject packet) {
    jobject ch;
    { std::lock_guard<std::mutex> l(g_bs.bMu); ch = g_bs.bChannel ? env->NewLocalRef(g_bs.bChannel) : nullptr; }
    if (!ch) return;
    std::lock_guard<std::recursive_mutex> writeLock(g_bs.bWriteMu);
    env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, packet);
    if (env->ExceptionCheck()) LogAndClearException(env, "writeAndFlush");
    env->DeleteLocalRef(ch);
}

void routeToA(JNIEnv* env, jobject packet) {
    jobject target;
    {
        std::lock_guard<std::mutex> l(g_bs.targetMu);
        target = g_bs.targetAConnection
            ? env->NewLocalRef(g_bs.targetAConnection)
            : nullptr;
    }
    if (!target) return;
    if (!g_bs.connectionSendMid) {
        env->DeleteLocalRef(target);
        return;
    }

    jobject channel = g_bs.connectionChannelFid
        ? env->GetObjectField(target, g_bs.connectionChannelFid) : nullptr;
    if (channel && !env->ExceptionCheck()) {
        RelayFilter_MarkBypass(env, channel, packet);
        env->DeleteLocalRef(channel);
    } else if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    env->CallVoidMethod(target, g_bs.connectionSendMid, packet);
    if (env->ExceptionCheck()) LogAndClearException(env, "routeToA");
    env->DeleteLocalRef(target);
}

bool shouldRouteBToA(const std::string& fqcn) {
    static constexpr const char kGamePrefix[] =
        "net.minecraft.network.protocol.game.";

    if (fqcn.rfind(kGamePrefix, 0) != 0) return false;
    const char* simple = fqcn.c_str() + (sizeof(kGamePrefix) - 1);
    if (std::strcmp(simple, "ServerboundCustomPayloadPacket") == 0) return false;

    return true;
}

bool uuidToBytes(JNIEnv* env, jobject uuid, unsigned char out[16]) {
    if (!uuid || !g_bs.uuidGetMsbMid || !g_bs.uuidGetLsbMid) return false;
    jlong msb = env->CallLongMethod(uuid, g_bs.uuidGetMsbMid);
    if (env->ExceptionCheck()) { env->ExceptionClear(); return false; }
    jlong lsb = env->CallLongMethod(uuid, g_bs.uuidGetLsbMid);
    if (env->ExceptionCheck()) { env->ExceptionClear(); return false; }
    for (int i = 0; i < 8; ++i) out[i]     = (unsigned char)((msb >> ((7 - i) * 8)) & 0xFF);
    for (int i = 0; i < 8; ++i) out[8 + i] = (unsigned char)((lsb >> ((7 - i) * 8)) & 0xFF);
    return true;
}

void ensureARealUuid(JNIEnv* env) {
    if (g_bs.aUuidReady && g_bs.aName) return;
    if (!g_bs.minecraftCls || !g_bs.mcGetInstanceMid || !g_bs.mcGetUserMid) return;
    jobject mc = env->CallStaticObjectMethod(g_bs.minecraftCls, g_bs.mcGetInstanceMid);
    if (!mc || env->ExceptionCheck()) { env->ExceptionClear(); return; }
    jobject user = env->CallObjectMethod(mc, g_bs.mcGetUserMid);
    env->DeleteLocalRef(mc);
    if (!user || env->ExceptionCheck()) { env->ExceptionClear(); return; }

    if (!g_bs.aUuidReady && g_bs.userGetProfileIdMid) {
        jobject uuid = env->CallObjectMethod(user, g_bs.userGetProfileIdMid);
        if (uuid && !env->ExceptionCheck()) {
            unsigned char bytes[16];
            if (uuidToBytes(env, uuid, bytes)) {
                g_bs.aRealUuid = env->NewGlobalRef(uuid);
                std::memcpy(g_bs.aUuidBytes, bytes, 16);
                g_bs.aUuidReady = true;
                LogTo("mirror: cached A's real UUID for tab-list mirroring");
            }
            env->DeleteLocalRef(uuid);
        } else if (env->ExceptionCheck()) env->ExceptionClear();
    }
    if (!g_bs.aName && g_bs.userGetGameProfileMid && g_bs.gameProfileGetNameMid) {
        jobject profile = env->CallObjectMethod(user, g_bs.userGetGameProfileMid);
        if (profile && !env->ExceptionCheck()) {
            jstring nm = (jstring)env->CallObjectMethod(profile, g_bs.gameProfileGetNameMid);
            if (nm && !env->ExceptionCheck()) {
                g_bs.aName = (jstring)env->NewGlobalRef(nm);
                const char* utf = env->GetStringUTFChars(nm, nullptr);
                LogTo("team-mirror: cached A's name = '%s'", utf ? utf : "?");
                if (utf) env->ReleaseStringUTFChars(nm, utf);
                env->DeleteLocalRef(nm);
            } else if (env->ExceptionCheck()) env->ExceptionClear();
            env->DeleteLocalRef(profile);
        } else if (env->ExceptionCheck()) env->ExceptionClear();
    }
    env->DeleteLocalRef(user);
}

void sendSelfInfoToB(JNIEnv* env, jobject offlineUuid, jstring name) {
    if (!g_bs.minecraftCls || !g_bs.mcGetInstanceMid || !g_bs.mcGetProfilePropsMid ||
        !g_bs.friendlyBufCls || !g_bs.friendlyBufCtor || !g_bs.fbbWriteByteMid ||
        !g_bs.fbbWriteBooleanMid || !g_bs.fbbWriteVarIntMid ||
        !g_bs.fbbWriteUUIDMid || !g_bs.fbbWriteUtfMid || !g_bs.fbbWriteGpPropsMid ||
        !g_bs.unpooledCls || !g_bs.unpooledBufferMid ||
        !g_bs.playerInfoUpdatePacketCls || !g_bs.playerInfoUpdatePacketBufCtor) {
        LogTo("self-info: missing refs, skipping push");
        return;
    }
    jobject mc = env->CallStaticObjectMethod(g_bs.minecraftCls, g_bs.mcGetInstanceMid);
    if (!mc || env->ExceptionCheck()) { env->ExceptionClear(); LogTo("self-info: no Minecraft"); return; }
    jobject props = env->CallObjectMethod(mc, g_bs.mcGetProfilePropsMid);
    env->DeleteLocalRef(mc);
    if (!props || env->ExceptionCheck()) { env->ExceptionClear(); LogTo("self-info: no profile props"); return; }

    jobject bb = env->CallStaticObjectMethod(g_bs.unpooledCls, g_bs.unpooledBufferMid);
    if (!bb || env->ExceptionCheck()) { env->ExceptionClear(); env->DeleteLocalRef(props); return; }
    jobject buf = env->NewObject(g_bs.friendlyBufCls, g_bs.friendlyBufCtor, bb);
    env->DeleteLocalRef(bb);
    if (!buf || env->ExceptionCheck()) { env->ExceptionClear(); env->DeleteLocalRef(props); return; }

    env->CallObjectMethod(buf, g_bs.fbbWriteByteMid, (jint)0x1D);
    env->CallObjectMethod(buf, g_bs.fbbWriteByteMid, (jint)0x01);
    env->CallObjectMethod(buf, g_bs.fbbWriteUUIDMid, offlineUuid);

    env->CallObjectMethod(buf, g_bs.fbbWriteUtfMid, name, (jint)16);
    env->CallVoidMethod  (buf, g_bs.fbbWriteGpPropsMid, props);

    env->CallObjectMethod(buf, g_bs.fbbWriteVarIntMid, (jint)0);

    env->CallObjectMethod(buf, g_bs.fbbWriteBooleanMid, (jboolean)JNI_TRUE);

    env->CallObjectMethod(buf, g_bs.fbbWriteVarIntMid, (jint)0);
    env->DeleteLocalRef(props);
    if (env->ExceptionCheck()) { LogAndClearException(env, "self-info: buffer build"); env->DeleteLocalRef(buf); return; }

    jobject pkt = env->NewObject(g_bs.playerInfoUpdatePacketCls,
                                 g_bs.playerInfoUpdatePacketBufCtor, buf);
    env->DeleteLocalRef(buf);
    if (!pkt || env->ExceptionCheck()) { LogAndClearException(env, "self-info: packet ctor"); return; }
    writeToB(env, pkt);
    env->DeleteLocalRef(pkt);
    LogTo("self-info: pushed B's own player-info to B (ADD_PLAYER+LISTED+GAME_MODE+LATENCY, A's skin)");
}

bool reconstructAndSendLoginToB(JNIEnv* env, jobject ch) {
    if (!g_bs.loginPacketCtor || !g_bs.minecraftCls || !g_bs.mcGetInstanceMid ||
        !g_bs.mcGetConnectionMid || !g_bs.cplLevelsMid || !g_bs.cplRegistryAccessMid ||
        !g_bs.registryAccessFreezeMid || !g_bs.mcLevelFid || !g_bs.mcGameModeFid ||
        !g_bs.gameModeGetTypeMid || !g_bs.levelDimMidA || !g_bs.objToStringMid ||
        !g_bs.optionalCls || !g_bs.optionalEmptyMid) {
        LogTo("mid-login: refs missing, cannot rebuild login");
        return false;
    }
    jobject mc = env->CallStaticObjectMethod(g_bs.minecraftCls, g_bs.mcGetInstanceMid);
    if (!mc || env->ExceptionCheck()) { env->ExceptionClear(); return false; }
    jobject cpl = env->CallObjectMethod(mc, g_bs.mcGetConnectionMid);
    if (!cpl || env->ExceptionCheck()) { env->ExceptionClear(); env->DeleteLocalRef(mc); return false; }

    jobject levels = env->CallObjectMethod(cpl, g_bs.cplLevelsMid);
    jobject ra     = (levels && !env->ExceptionCheck()) ? env->CallObjectMethod(cpl, g_bs.cplRegistryAccessMid) : nullptr;
    env->DeleteLocalRef(cpl);
    jobject frozen = (ra && !env->ExceptionCheck()) ? env->CallObjectMethod(ra, g_bs.registryAccessFreezeMid) : nullptr;
    if (ra) env->DeleteLocalRef(ra);
    if (!levels || !frozen || env->ExceptionCheck()) {
        env->ExceptionClear();
        LogTo("mid-login: levels/registry unavailable");
        if (levels) env->DeleteLocalRef(levels);
        if (frozen) env->DeleteLocalRef(frozen);
        env->DeleteLocalRef(mc);
        return false;
    }

    jobject gameMode = env->GetObjectField(mc, g_bs.mcGameModeFid);
    jobject gameType = (gameMode && !env->ExceptionCheck()) ?
        env->CallObjectMethod(gameMode, g_bs.gameModeGetTypeMid) : nullptr;
    if (gameMode) env->DeleteLocalRef(gameMode);
    if (env->ExceptionCheck()) env->ExceptionClear();

    jobject level = env->GetObjectField(mc, g_bs.mcLevelFid);
    jobject dimKey = nullptr, dimTypeKey = nullptr;
    if (level && !env->ExceptionCheck()) {
        jmethodID mids[2] = { g_bs.levelDimMidA, g_bs.levelDimMidB };
        for (int i = 0; i < 2 && mids[i]; ++i) {
            jobject rk = env->CallObjectMethod(level, mids[i]);
            if (!rk || env->ExceptionCheck()) { env->ExceptionClear(); if (rk) env->DeleteLocalRef(rk); continue; }
            jstring s = (jstring)env->CallObjectMethod(rk, g_bs.objToStringMid);
            bool isDimType = false;
            if (s && !env->ExceptionCheck()) {
                const char* c = env->GetStringUTFChars(s, nullptr);
                if (c) { isDimType = std::strstr(c, "dimension_type") != nullptr; env->ReleaseStringUTFChars(s, c); }
                env->DeleteLocalRef(s);
            } else if (env->ExceptionCheck()) env->ExceptionClear();
            if (isDimType) { if (dimTypeKey) env->DeleteLocalRef(dimTypeKey); dimTypeKey = rk; }
            else           { if (dimKey)     env->DeleteLocalRef(dimKey);     dimKey = rk; }
        }
    }
    if (level) env->DeleteLocalRef(level);

    if (!gameType || !dimKey || !dimTypeKey) {
        LogTo("mid-login: incomplete (gameType=%p dim=%p dimType=%p), abort",
              (void*)gameType, (void*)dimKey, (void*)dimTypeKey);
        if (levels) env->DeleteLocalRef(levels);
        if (frozen) env->DeleteLocalRef(frozen);
        if (gameType) env->DeleteLocalRef(gameType);
        if (dimKey) env->DeleteLocalRef(dimKey);
        if (dimTypeKey) env->DeleteLocalRef(dimTypeKey);
        return false;
    }

    jobject empty = env->CallStaticObjectMethod(g_bs.optionalCls, g_bs.optionalEmptyMid);
    if (!empty || env->ExceptionCheck()) {

        env->ExceptionClear();
        LogTo("mid-login: Optional.empty() failed, abort");
        if (empty) env->DeleteLocalRef(empty);
        env->DeleteLocalRef(levels); env->DeleteLocalRef(frozen);
        env->DeleteLocalRef(gameType); env->DeleteLocalRef(dimKey); env->DeleteLocalRef(dimTypeKey);
        return false;
    }

    jint playerId = 2000000000;
    PlayerSnapshot livePlayer;
    bool haveLivePlayer = false;
    jobject live = g_bs.mcPlayerFid ? env->GetObjectField(mc, g_bs.mcPlayerFid) : nullptr;
    if (live && !env->ExceptionCheck()) {
        haveLivePlayer = ReadPlayerSnapshot(env, live, livePlayer);
        if (haveLivePlayer) playerId = livePlayer.entityId;
        env->DeleteLocalRef(live);
    } else if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(mc);
    const jboolean hardcore    = JNI_FALSE;
    const jlong    seed        = 0;
    const jint     maxPlayers  = 20;
    const jint     chunkRadius = 4;
    const jint     simDist     = 4;
    jobject login = env->NewObject(g_bs.loginPacketCls, g_bs.loginPacketCtor,
        playerId, hardcore, gameType, (jobject)nullptr, levels, frozen,
        dimTypeKey, dimKey, seed, maxPlayers, chunkRadius, simDist,
        (jboolean)JNI_FALSE, (jboolean)JNI_TRUE, (jboolean)JNI_FALSE, (jboolean)JNI_FALSE,
        empty, (jint)0);

    env->DeleteLocalRef(levels);
    env->DeleteLocalRef(frozen);
    env->DeleteLocalRef(gameType);
    env->DeleteLocalRef(dimKey);
    env->DeleteLocalRef(dimTypeKey);
    if (empty) env->DeleteLocalRef(empty);

    if (!login || env->ExceptionCheck()) { LogAndClearException(env, "mid-login: ctor"); return false; }
    env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, login);
    bool sent = !env->ExceptionCheck();

    if (!sent) LogAndClearException(env, "mid-login: writeAndFlush");
    env->DeleteLocalRef(login);
    if (!sent) return false;
    LogTo("mid-login: rebuilt ClientboundLoginPacket sent to B");

    if (haveLivePlayer) {
        const jint cx = static_cast<jint>(std::floor(livePlayer.x / 16.0));
        const jint cz = static_cast<jint>(std::floor(livePlayer.z / 16.0));
        if (g_bs.chunkCacheRadiusPacketCtor) {
            jobject radius = env->NewObject(g_bs.chunkCacheRadiusPacketCls, g_bs.chunkCacheRadiusPacketCtor, (jint)4);
            if (radius && !env->ExceptionCheck()) {
                env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, radius);
                if (env->ExceptionCheck()) env->ExceptionClear();
                env->DeleteLocalRef(radius);
            } else if (env->ExceptionCheck()) env->ExceptionClear();
        }
        if (g_bs.chunkCacheCenterPacketCtor) {
            jobject center = env->NewObject(g_bs.chunkCacheCenterPacketCls, g_bs.chunkCacheCenterPacketCtor, cx, cz);
            if (center && !env->ExceptionCheck()) {
                env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, center);
                if (env->ExceptionCheck()) env->ExceptionClear();
                env->DeleteLocalRef(center);
            } else if (env->ExceptionCheck()) env->ExceptionClear();
        }
        replayLoadedChunkToB(env, ch, cx, cz);
    }
    if (g_bs.playerPositionPacketCtor && g_bs.setOfMid && g_bs.setCls) {
        jobject relSet = env->CallStaticObjectMethod(g_bs.setCls, g_bs.setOfMid);
        if (relSet && !env->ExceptionCheck()) {
            const jint teleportId = g_bs.bootstrapAck.begin();
            jobject pos = env->NewObject(g_bs.playerPositionPacketCls, g_bs.playerPositionPacketCtor,
                haveLivePlayer ? livePlayer.x : (jdouble)0.0,
                haveLivePlayer ? livePlayer.y : (jdouble)100.0,
                haveLivePlayer ? livePlayer.z : (jdouble)0.0,
                haveLivePlayer ? livePlayer.yaw : (jfloat)0.0f,
                haveLivePlayer ? livePlayer.pitch : (jfloat)0.0f, relSet, teleportId);
            if (pos && !env->ExceptionCheck()) {
                env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, pos);
                if (env->ExceptionCheck()) env->ExceptionClear();
                env->DeleteLocalRef(pos);
                LogTo("mid-login: sent live spawn position to B (local teleport=%d)", (int)teleportId);
            } else if (env->ExceptionCheck()) env->ExceptionClear();
            env->DeleteLocalRef(relSet);
        } else if (env->ExceptionCheck()) env->ExceptionClear();
    }
    if (haveLivePlayer && g_bs.defaultSpawnPacketCtor && g_bs.blockPosCtor) {
        jobject spawnPos = env->NewObject(g_bs.blockPosCls, g_bs.blockPosCtor,
            static_cast<jint>(std::floor(livePlayer.x)),
            static_cast<jint>(std::floor(livePlayer.y)),
            static_cast<jint>(std::floor(livePlayer.z)));
        jobject marker = spawnPos && !env->ExceptionCheck()
            ? env->NewObject(g_bs.defaultSpawnPacketCls, g_bs.defaultSpawnPacketCtor,
                             spawnPos, livePlayer.yaw) : nullptr;
        if (marker && !env->ExceptionCheck()) {
            env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, marker);
            if (env->ExceptionCheck()) env->ExceptionClear();
            env->DeleteLocalRef(marker);
            LogTo("mid-login: sent loading-complete spawn marker");
        } else if (env->ExceptionCheck()) env->ExceptionClear();
        if (spawnPos) env->DeleteLocalRef(spawnPos);
    }
    return true;
}

bool replayLoadedChunkToB(JNIEnv* env, jobject ch, jint x, jint z) {
    if (!env || !ch || !g_bs.mcLevelFid || !g_bs.clientLevelChunkSourceFid ||
        !g_bs.clientChunkGetMid || !g_bs.clientChunkLightEngineMid || !g_bs.chunkStatusCls ||
        !g_bs.chunkStatusFullFid || !g_bs.chunkWithLightPacketCls ||
        !g_bs.chunkWithLightPacketCtor || !g_bs.minecraftCls || !g_bs.mcGetInstanceMid) return false;
    jobject mc = env->CallStaticObjectMethod(g_bs.minecraftCls, g_bs.mcGetInstanceMid);
    jobject level = mc && !env->ExceptionCheck() ? env->GetObjectField(mc, g_bs.mcLevelFid) : nullptr;
    jobject cache = level && !env->ExceptionCheck() ? env->GetObjectField(level, g_bs.clientLevelChunkSourceFid) : nullptr;
    jobject full = env->GetStaticObjectField(g_bs.chunkStatusCls, g_bs.chunkStatusFullFid);
    jobject light = cache && !env->ExceptionCheck() ? env->CallObjectMethod(cache, g_bs.clientChunkLightEngineMid) : nullptr;
    jobject chunk = cache && full && !env->ExceptionCheck()
        ? env->CallObjectMethod(cache, g_bs.clientChunkGetMid, x, z, full, JNI_FALSE) : nullptr;
    jobject packet = chunk && light && !env->ExceptionCheck()
        ? env->NewObject(g_bs.chunkWithLightPacketCls, g_bs.chunkWithLightPacketCtor,
                         chunk, light, nullptr, nullptr) : nullptr;
    bool sent = false;
    if (packet && !env->ExceptionCheck()) {
        env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, packet);
        sent = !env->ExceptionCheck();
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (packet) env->DeleteLocalRef(packet); if (chunk) env->DeleteLocalRef(chunk);
    if (light) env->DeleteLocalRef(light); if (full) env->DeleteLocalRef(full);
    if (cache) env->DeleteLocalRef(cache); if (level) env->DeleteLocalRef(level); if (mc) env->DeleteLocalRef(mc);
    return sent;
}

void replayLoadedChunksToB(JNIEnv* env, jobject ch, const PlayerSnapshot& player) {
    if (!env || !ch || !g_bs.mcLevelFid || !g_bs.clientLevelChunkSourceFid ||
        !g_bs.clientChunkGetMid || !g_bs.clientChunkLightEngineMid || !g_bs.chunkStatusCls ||
        !g_bs.chunkStatusFullFid ||
        !g_bs.chunkWithLightPacketCls || !g_bs.chunkWithLightPacketCtor || !g_bs.minecraftCls ||
        !g_bs.mcGetInstanceMid) {
        LogTo("mid-chunks: client chunk refs unavailable; relying on live stream");
        return;
    }
    jobject mc = env->CallStaticObjectMethod(g_bs.minecraftCls, g_bs.mcGetInstanceMid);
    jobject level = mc && !env->ExceptionCheck() ? env->GetObjectField(mc, g_bs.mcLevelFid) : nullptr;
    jobject cache = level && !env->ExceptionCheck() ? env->GetObjectField(level, g_bs.clientLevelChunkSourceFid) : nullptr;
    jobject full = env->GetStaticObjectField(g_bs.chunkStatusCls, g_bs.chunkStatusFullFid);
    jobject light = cache && !env->ExceptionCheck() ? env->CallObjectMethod(cache, g_bs.clientChunkLightEngineMid) : nullptr;
    if (env->ExceptionCheck() || !cache || !full || !light) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        LogTo("mid-chunks: failed to access ClientChunkCache");
        if (mc) env->DeleteLocalRef(mc); if (level) env->DeleteLocalRef(level); if (cache) env->DeleteLocalRef(cache);
        if (full) env->DeleteLocalRef(full); if (light) env->DeleteLocalRef(light);
        return;
    }
    const jint cx = static_cast<jint>(std::floor(player.x / 16.0));
    const jint cz = static_cast<jint>(std::floor(player.z / 16.0));
    int sent = 0;
    // 扩大扫描范围；未加载的区块会由 ClientChunkCache 返回空并跳过。
    constexpr int radius = 8;
    for (int dz = -radius; dz <= radius; ++dz) for (int dx = -radius; dx <= radius; ++dx) {
        jobject chunk = env->CallObjectMethod(cache, g_bs.clientChunkGetMid, cx + dx, cz + dz, full, JNI_FALSE);
        if (env->ExceptionCheck()) { env->ExceptionClear(); continue; }
        if (!chunk) continue;
        jobject packet = env->NewObject(g_bs.chunkWithLightPacketCls, g_bs.chunkWithLightPacketCtor,
                                        chunk, light, nullptr, nullptr);
        env->DeleteLocalRef(chunk);
        if (packet && !env->ExceptionCheck()) {
            std::lock_guard<std::recursive_mutex> writeLock(g_bs.bWriteMu);
            env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, packet);
            if (env->ExceptionCheck()) env->ExceptionClear();
            env->DeleteLocalRef(packet);
            ++sent;
        } else if (env->ExceptionCheck()) env->ExceptionClear();
    }
    LogTo("mid-chunks: replayed %d loaded chunks around %d,%d", sent, (int)cx, (int)cz);
    env->DeleteLocalRef(mc); env->DeleteLocalRef(level); env->DeleteLocalRef(cache);
    env->DeleteLocalRef(full); env->DeleteLocalRef(light);
}

// 检查异步任务是否仍属于当前 B 连接，阻止旧连接线程继续写包。
bool isCurrentBGeneration(JNIEnv* env, jobject channel, uint64_t generation) {
    if (!env || !channel) return false;
    std::lock_guard<std::mutex> lock(g_bs.bMu);
    return g_bs.bChannel && g_bs.bGeneration == generation &&
           env->IsSameObject(channel, g_bs.bChannel) &&
           g_bs.bState.load(std::memory_order_acquire) == BState::Play;
}

void replayLoadedChunksToBAsync(JNIEnv* env, jobject ch, const PlayerSnapshot& player) {
    if (!env || !ch) return;
    uint64_t generation = 0;
    {
        std::lock_guard<std::mutex> lock(g_bs.bMu);
        generation = g_bs.bGeneration;
    }
    jobject channelRef = env->NewGlobalRef(ch);
    if (!channelRef) return;
    std::thread([channelRef, player, generation]() {
        JniAttach attach;
        if (!attach) {
            return;
        }
        if (isCurrentBGeneration(attach.env, channelRef, generation)) {
            replayLoadedChunksToB(attach.env, channelRef, player);
        } else {
            LogTo("mid-chunks: stale async replay ignored (generation=%llu)",
                  static_cast<unsigned long long>(generation));
        }
        attach.env->DeleteGlobalRef(channelRef);
    }).detach();
}

void sendLocalInventorySnapshotToB(JNIEnv* env, jobject ch) {
    if (!env || !ch || !g_bs.minecraftCls || !g_bs.mcGetInstanceMid ||
        !g_bs.mcPlayerFid || !g_bs.mcInventoryMenuFid ||
        !g_bs.containerGetItemsMid || !g_bs.containerGetCarriedMid ||
        !g_bs.containerGetStateIdMid || !g_bs.containerContentPacketCls ||
        !g_bs.containerContentPacketCtor) {
        LogTo("inventory-bootstrap: local container refs unavailable");
        return;
    }
    jobject mc = env->CallStaticObjectMethod(g_bs.minecraftCls, g_bs.mcGetInstanceMid);
    jobject player = mc && !env->ExceptionCheck() ? env->GetObjectField(mc, g_bs.mcPlayerFid) : nullptr;
    jobject menu = player && !env->ExceptionCheck() ? env->GetObjectField(player, g_bs.mcInventoryMenuFid) : nullptr;
    jobject items = menu && !env->ExceptionCheck() ? env->CallObjectMethod(menu, g_bs.containerGetItemsMid) : nullptr;
    jobject carried = menu && !env->ExceptionCheck() ? env->CallObjectMethod(menu, g_bs.containerGetCarriedMid) : nullptr;
    jint stateId = menu && !env->ExceptionCheck() ? env->CallIntMethod(menu, g_bs.containerGetStateIdMid) : 0;
    if (env->ExceptionCheck() || !items || !carried) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        LogTo("inventory-bootstrap: local inventory unavailable");
        if (carried) env->DeleteLocalRef(carried);
        if (items) env->DeleteLocalRef(items);
        if (menu) env->DeleteLocalRef(menu);
        if (player) env->DeleteLocalRef(player);
        if (mc) env->DeleteLocalRef(mc);
        return;
    }
    // The player inventory menu is always container id 0 for the client.
    jobject packet = env->NewObject(g_bs.containerContentPacketCls,
        g_bs.containerContentPacketCtor, (jint)0, stateId, items, carried);
    if (packet && !env->ExceptionCheck()) {
        env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, packet);
        if (env->ExceptionCheck()) LogAndClearException(env, "inventory-bootstrap: write");
        else LogTo("inventory-bootstrap: sent local container contents (state=%d)", (int)stateId);
    } else if (env->ExceptionCheck()) {
        LogAndClearException(env, "inventory-bootstrap: packet ctor");
    }
    if (packet) env->DeleteLocalRef(packet);

    // Container contents do not include which hotbar slot is selected.  A
    // freshly connected B otherwise defaults to slot 0 while the server still
    // has A's old selected slot.  ServerboundUseItemPacket contains only the
    // hand, so that mismatch makes lobby/menu items appear usable on B but the
    // server actually activates a different slot.
    if (g_bs.playerGetInventoryMid && g_bs.inventorySelectedFid &&
        g_bs.carriedItemPacketCls && g_bs.carriedItemPacketCtor) {
        jobject inventory = env->CallObjectMethod(player, g_bs.playerGetInventoryMid);
        if (inventory && !env->ExceptionCheck()) {
            jint selected = env->GetIntField(inventory, g_bs.inventorySelectedFid);
            if (!env->ExceptionCheck() && selected >= 0 && selected < 9) {
                jobject selectedPacket = env->NewObject(g_bs.carriedItemPacketCls,
                    g_bs.carriedItemPacketCtor, selected);
                if (selectedPacket && !env->ExceptionCheck()) {
                    env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, selectedPacket);
                    if (env->ExceptionCheck()) {
                        LogAndClearException(env, "inventory-bootstrap: selected-slot write");
                    } else {
                        LogTo("inventory-bootstrap: sent selected hotbar slot=%d", (int)selected);
                    }
                    env->DeleteLocalRef(selectedPacket);
                } else if (env->ExceptionCheck()) {
                    LogAndClearException(env, "inventory-bootstrap: selected-slot packet ctor");
                }
            } else if (env->ExceptionCheck()) {
                LogAndClearException(env, "inventory-bootstrap: selected-slot read");
            }
            env->DeleteLocalRef(inventory);
        } else {
            if (env->ExceptionCheck()) LogAndClearException(env, "inventory-bootstrap: get inventory");
            LogTo("inventory-bootstrap: selected hotbar slot unavailable");
        }
    } else {
        LogTo("inventory-bootstrap: selected-slot refs unavailable");
    }
    env->DeleteLocalRef(carried);
    env->DeleteLocalRef(items);
    env->DeleteLocalRef(menu);
    env->DeleteLocalRef(player);
    env->DeleteLocalRef(mc);
}

void sendScoreboardSnapshotToB(JNIEnv* env, jobject ch) {
    if (!env || !ch || !g_bs.minecraftCls || !g_bs.mcGetInstanceMid || !g_bs.mcLevelFid) return;
    jobject loader = GetMinecraftClassLoader(env, g_jvmti);
    jobject mc = env->CallStaticObjectMethod(g_bs.minecraftCls, g_bs.mcGetInstanceMid);
    jobject level = mc && !env->ExceptionCheck() ? env->GetObjectField(mc, g_bs.mcLevelFid) : nullptr;
    if (!loader || !level || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        LogTo("scoreboard-bootstrap: client level unavailable");
        if (level) env->DeleteLocalRef(level);
        if (mc) env->DeleteLocalRef(mc);
        if (loader) env->DeleteGlobalRef(loader);
        return;
    }
    ScoreboardSnapshotStats stats;
    bool ok = WriteScoreboardSnapshot(env, loader, level, [&](jobject packet) {
        if (!packet || env->ExceptionCheck()) return false;
        env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, packet);
        return !env->ExceptionCheck();
    }, stats);
    if (env->ExceptionCheck()) env->ExceptionClear();
    LogTo("scoreboard-bootstrap: %s objectives=%d teams=%d scores=%d displays=%d",
          ok ? "sent" : "unavailable", stats.objectives, stats.teams,
          stats.scores, stats.displays);
    env->DeleteLocalRef(level);
    env->DeleteLocalRef(mc);
    env->DeleteGlobalRef(loader);
}

// 将当前 A 世界中已存在的实体快照发送给 B，解决中途接入时 NPC 缺少生成包的问题。
void sendCurrentEntitiesSnapshotToB(JNIEnv* env, jobject ch) {
    if (!env || !ch || !g_bs.minecraftCls || !g_bs.mcGetInstanceMid || !g_bs.mcLevelFid ||
        !g_bs.clientLevelEntitiesForRenderingMid || !g_bs.entityGetIdMid ||
        !g_bs.entityIsRemovedMid || !g_bs.entityGetAddEntityPacketMid ||
        !g_bs.entityGetEntityDataMid || !g_bs.synchedGetNonDefaultValuesMid ||
        !g_bs.setEntityDataPacketCls || !g_bs.setEntityDataPacketCtor ||
        !g_bs.iterableIteratorMid || !g_bs.iteratorHasNextMid || !g_bs.iteratorNextMid) {
        LogTo("entity-bootstrap: entity JNI refs unavailable");
        return;
    }
    // 整个快照串行写入，保持 AddEntity -> metadata -> motion/passengers 的顺序。
    std::lock_guard<std::recursive_mutex> writeLock(g_bs.bWriteMu);
    jobject mc = env->CallStaticObjectMethod(g_bs.minecraftCls, g_bs.mcGetInstanceMid);
    jobject level = mc && !env->ExceptionCheck() ? env->GetObjectField(mc, g_bs.mcLevelFid) : nullptr;
    jobject localPlayer = mc && g_bs.mcPlayerFid && !env->ExceptionCheck()
        ? env->GetObjectField(mc, g_bs.mcPlayerFid) : nullptr;
    jint selfId = localPlayer && !env->ExceptionCheck()
        ? env->CallIntMethod(localPlayer, g_bs.entityGetIdMid) : static_cast<jint>(-2147483647 - 1);
    jobject iterable = level && !env->ExceptionCheck()
        ? env->CallObjectMethod(level, g_bs.clientLevelEntitiesForRenderingMid) : nullptr;
    jobject iterator = iterable && !env->ExceptionCheck()
        ? env->CallObjectMethod(iterable, g_bs.iterableIteratorMid) : nullptr;
    if (env->ExceptionCheck() || !iterable || !iterator) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        LogTo("entity-bootstrap: entitiesForRendering unavailable");
        if (iterator) env->DeleteLocalRef(iterator);
        if (iterable) env->DeleteLocalRef(iterable);
        if (localPlayer) env->DeleteLocalRef(localPlayer);
        if (level) env->DeleteLocalRef(level);
        if (mc) env->DeleteLocalRef(mc);
        return;
    }

    int sent = 0, skipped = 0;
    while (env->CallBooleanMethod(iterator, g_bs.iteratorHasNextMid) == JNI_TRUE) {
        if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
        jobject entity = env->CallObjectMethod(iterator, g_bs.iteratorNextMid);
        if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
        if (!entity) continue;
        jint id = env->CallIntMethod(entity, g_bs.entityGetIdMid);
        jboolean removed = env->CallBooleanMethod(entity, g_bs.entityIsRemovedMid);
        if (env->ExceptionCheck() || removed == JNI_TRUE || id == selfId) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            ++skipped;
            env->DeleteLocalRef(entity);
            continue;
        }

        // 先创建实体，否则后续元数据包会被客户端忽略。
        jobject addPacket = env->CallObjectMethod(entity, g_bs.entityGetAddEntityPacketMid);
        if (env->ExceptionCheck()) { env->ExceptionClear(); addPacket = nullptr; }
        if (addPacket) {
            env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, addPacket);
            if (env->ExceptionCheck()) env->ExceptionClear();
            env->DeleteLocalRef(addPacket);
        }

        // 恢复 NPC 名称、姿态、旗标等非默认元数据。
        jobject data = env->CallObjectMethod(entity, g_bs.entityGetEntityDataMid);
        jobject values = data && !env->ExceptionCheck()
            ? env->CallObjectMethod(data, g_bs.synchedGetNonDefaultValuesMid) : nullptr;
        if (values && !env->ExceptionCheck()) {
            jobject metaPacket = env->NewObject(g_bs.setEntityDataPacketCls,
                g_bs.setEntityDataPacketCtor, id, values);
            if (metaPacket && !env->ExceptionCheck()) {
                env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, metaPacket);
                if (env->ExceptionCheck()) env->ExceptionClear();
                env->DeleteLocalRef(metaPacket);
            } else if (env->ExceptionCheck()) env->ExceptionClear();
        } else if (env->ExceptionCheck()) env->ExceptionClear();
        if (values) env->DeleteLocalRef(values);
        if (data) env->DeleteLocalRef(data);

        // 补充速度与乘客关系，避免移动 NPC 首帧停滞或坐骑关系缺失。
        if (g_bs.setEntityMotionPacketCls && g_bs.setEntityMotionPacketCtor) {
            jobject motion = env->NewObject(g_bs.setEntityMotionPacketCls,
                g_bs.setEntityMotionPacketCtor, entity);
            if (motion && !env->ExceptionCheck()) {
                env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, motion);
                if (env->ExceptionCheck()) env->ExceptionClear();
                env->DeleteLocalRef(motion);
            } else if (env->ExceptionCheck()) env->ExceptionClear();
        }
        if (g_bs.setPassengersPacketCls && g_bs.setPassengersPacketCtor) {
            jobject passengers = env->NewObject(g_bs.setPassengersPacketCls,
                g_bs.setPassengersPacketCtor, entity);
            if (passengers && !env->ExceptionCheck()) {
                env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, passengers);
                if (env->ExceptionCheck()) env->ExceptionClear();
                env->DeleteLocalRef(passengers);
            } else if (env->ExceptionCheck()) env->ExceptionClear();
        }
        ++sent;
        env->DeleteLocalRef(entity);
    }
    LogTo("entity-bootstrap: replayed %d entities (skipped=%d)", sent, skipped);
    env->DeleteLocalRef(iterator);
    env->DeleteLocalRef(iterable);
    if (localPlayer) env->DeleteLocalRef(localPlayer);
    if (level) env->DeleteLocalRef(level);
    if (mc) env->DeleteLocalRef(mc);
}

void completeLogin(JNIEnv* env, jobject hello) {

    ensureARealUuid(env);
    jobject uuid = nullptr;
    jstring jname = nullptr;
    if (g_bs.aUuidReady && g_bs.aRealUuid && g_bs.minecraftCls &&
        g_bs.mcGetInstanceMid && g_bs.mcGetUserMid) {
        jobject mc = env->CallStaticObjectMethod(g_bs.minecraftCls, g_bs.mcGetInstanceMid);
        jobject user = mc && !env->ExceptionCheck() ? env->CallObjectMethod(mc, g_bs.mcGetUserMid) : nullptr;
        jobject player = mc && g_bs.mcPlayerFid && !env->ExceptionCheck()
            ? env->GetObjectField(mc, g_bs.mcPlayerFid) : nullptr;
        jname = !env->ExceptionCheck() ? ReadProxyPlayerName(env, user, player) : nullptr;
        if (player) env->DeleteLocalRef(player);
        if (user) env->DeleteLocalRef(user);
        if (mc) env->DeleteLocalRef(mc);
        if (jname && !env->ExceptionCheck()) {
            uuid = env->NewLocalRef(g_bs.aRealUuid);
            LogTo("login: using canonical A profile for B");
        }
    }
    if (!uuid || !jname || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (uuid) env->DeleteLocalRef(uuid);
        if (jname) env->DeleteLocalRef(jname);
        if (!hello || !g_bs.helloPacketNameFid) {
            LogTo("login: no canonical A identity and no B hello profile");
            return;
        }
        jname = (jstring)env->GetObjectField(hello, g_bs.helloPacketNameFid);
        if (!jname || env->ExceptionCheck()) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            LogTo("login: hello.name is null");
            return;
        }
        const char* utfName = env->GetStringUTFChars(jname, nullptr);
        std::string composite = std::string("OfflinePlayer:") + (utfName ? utfName : "");
        if (utfName) env->ReleaseStringUTFChars(jname, utfName);
        jbyteArray arr = env->NewByteArray((jsize)composite.size());
        env->SetByteArrayRegion(arr, 0, (jsize)composite.size(),
                                reinterpret_cast<const jbyte*>(composite.data()));
        uuid = env->CallStaticObjectMethod(g_bs.uuidCls, g_bs.uuidNameUuidFromBytesMid, arr);
        env->DeleteLocalRef(arr);
        if (!uuid || env->ExceptionCheck()) {
            LogAndClearException(env, "login: nameUUIDFromBytes");
            env->DeleteLocalRef(jname);
            return;
        }
        LogTo("login: using B offline profile fallback");
    }

    if (!g_bs.gameProfileCtor) { LogTo("login: no GameProfile ctor"); env->DeleteLocalRef(uuid); env->DeleteLocalRef(jname); return; }
    jobject profile = env->NewObject(g_bs.gameProfileCls, g_bs.gameProfileCtor, uuid, jname);
    if (!profile || env->ExceptionCheck()) {
        LogAndClearException(env, "login: GameProfile ctor"); env->DeleteLocalRef(uuid); env->DeleteLocalRef(jname); return;
    }

    if (!g_bs.loginFinishedPacketCtor) { LogTo("login: no LoginFinished ctor"); return; }
    jobject lfp = env->NewObject(g_bs.loginFinishedPacketCls, g_bs.loginFinishedPacketCtor, profile);
    env->DeleteLocalRef(profile);
    if (!lfp || env->ExceptionCheck()) {
        LogAndClearException(env, "login: LoginFinished ctor"); return;
    }

    writeToB(env, lfp);
    env->DeleteLocalRef(lfp);
    LogTo("login: sent ClientboundGameProfilePacket to B");

    jobject ch;
    { std::lock_guard<std::mutex> l(g_bs.bMu); ch = g_bs.bChannel ? env->NewLocalRef(g_bs.bChannel) : nullptr; }
    if (ch) {
        setProtocolState(env, ch, g_bs.protoPlay);
        LogTo("login: protocol switched to PLAY");

        if (g_bs.midSession.load(std::memory_order_acquire)) {
            LogTo("login: mid-session — rebuilding login for B from A's state");
            reconstructAndSendLoginToB(env, ch);
            if (g_bs.minecraftCls && g_bs.mcGetInstanceMid && g_bs.mcPlayerFid) {
                jobject mcNow = env->CallStaticObjectMethod(g_bs.minecraftCls, g_bs.mcGetInstanceMid);
                jobject playerNow = mcNow && !env->ExceptionCheck() ? env->GetObjectField(mcNow, g_bs.mcPlayerFid) : nullptr;
                PlayerSnapshot snapshot;
                if (playerNow && !env->ExceptionCheck() && ReadPlayerSnapshot(env, playerNow, snapshot)) {
                    // 初始化阶段同步回放区块，避免与实时数据交错。
                    replayLoadedChunksToB(env, ch, snapshot);
                }
                if (playerNow) env->DeleteLocalRef(playerNow);
                if (mcNow) env->DeleteLocalRef(mcNow);
                if (env->ExceptionCheck()) env->ExceptionClear();
            }
        }
        // 主动发送注入前已存在的实体（包括 NPC）。
        sendCurrentEntitiesSnapshotToB(env, ch);
        replayBootstrap(env, ch, g_bs.midSession.load(std::memory_order_acquire));
        // These are server-side state snapshots rather than world chunks. The
        // A connection may have sent them long before injection, so rebuild
        // them from the live client objects after the protocol bootstrap.
        if (!g_cache.hasScoreboard()) sendScoreboardSnapshotToB(env, ch);
        sendLocalInventorySnapshotToB(env, ch);
        // 原子切换 PLAY 并补发 bootstrap drain 之后到达的尾包。
        finishBootstrapToPlay(env, ch);
        env->DeleteLocalRef(ch);
    }

    if (g_bs.keepAlivePacketCtor && g_bs.bChannel) {
        jobject ka = env->NewObject(g_bs.keepAlivePacketCls,
                                    g_bs.keepAlivePacketCtor, (jlong)1);
        if (ka) { writeToB(env, ka); env->DeleteLocalRef(ka); }
    }

    sendSelfInfoToB(env, uuid, jname);

    unsigned char bBytes[16];
    if (uuidToBytes(env, uuid, bBytes)) {
        std::memcpy(g_bs.bUuidBytes, bBytes, 16);
        g_bs.bUuidReady = true;
    }

    if (g_bs.bUuidObj) env->DeleteGlobalRef(g_bs.bUuidObj);
    g_bs.bUuidObj = env->NewGlobalRef(uuid);

    if (g_bs.bName) env->DeleteGlobalRef(g_bs.bName);
    g_bs.bName = (jstring)env->NewGlobalRef(jname);
    ensureARealUuid(env);

    env->DeleteLocalRef(uuid);
    env->DeleteLocalRef(jname);

    {
        std::lock_guard<std::mutex> l(g_bConnMu);
        g_bConnected = true;
    }
    g_bConnCv.notify_all();
    LogTo("login: B in PLAY; asynchronous A relay is active");
}

}

void hideAFromBTab(JNIEnv* env, jobject ch) {
    if (!g_bs.aRealUuid || !g_bs.friendlyBufCls || !g_bs.friendlyBufCtor ||
        !g_bs.unpooledCls || !g_bs.unpooledBufferMid || !g_bs.fbbWriteByteMid ||
        !g_bs.fbbWriteVarIntMid || !g_bs.fbbWriteUUIDMid || !g_bs.fbbWriteBooleanMid ||
        !g_bs.playerInfoUpdatePacketCls || !g_bs.playerInfoUpdatePacketBufCtor) return;

    jobject bb = env->CallStaticObjectMethod(g_bs.unpooledCls, g_bs.unpooledBufferMid);
    if (!bb || env->ExceptionCheck()) { env->ExceptionClear(); return; }
    jobject buf = env->NewObject(g_bs.friendlyBufCls, g_bs.friendlyBufCtor, bb);
    env->DeleteLocalRef(bb);
    if (!buf || env->ExceptionCheck()) { env->ExceptionClear(); return; }

    env->CallObjectMethod(buf, g_bs.fbbWriteByteMid, (jint)0x08);
    env->CallObjectMethod(buf, g_bs.fbbWriteVarIntMid, (jint)1);
    env->CallObjectMethod(buf, g_bs.fbbWriteUUIDMid, g_bs.aRealUuid);
    env->CallObjectMethod(buf, g_bs.fbbWriteBooleanMid, (jboolean)JNI_FALSE);
    if (env->ExceptionCheck()) { env->ExceptionClear(); env->DeleteLocalRef(buf); return; }

    jobject pkt = env->NewObject(g_bs.playerInfoUpdatePacketCls,
                                 g_bs.playerInfoUpdatePacketBufCtor, buf);
    env->DeleteLocalRef(buf);
    if (!pkt || env->ExceptionCheck()) { LogAndClearException(env, "hide-A: ctor"); return; }
    env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, pkt);
    if (env->ExceptionCheck()) LogAndClearException(env, "hide-A: writeAndFlush");
    env->DeleteLocalRef(pkt);
}

void mirrorPlayerInfoUpdateToB(JNIEnv* env, jobject ch, jobject packet) {
    if (!g_bs.playerInfoUpdatePacketCls || !g_bs.playerInfoUpdatePacketBufCtor ||
        !g_bs.playerInfoUpdatePacketWriteMid || !g_bs.friendlyBufCls ||
        !g_bs.friendlyBufCtor || !g_bs.unpooledCls || !g_bs.unpooledBufferMid ||
        !g_bs.byteBufGetByteMid || !g_bs.listSizeMid || !g_bs.listGetMid ||
        !g_bs.piuEntriesMidA || !g_bs.piEntryProfileIdMid ||
        !g_bs.fbbWriteByteMid || !g_bs.fbbWriteVarIntMid || !g_bs.fbbWriteUUIDMid ||
        !g_bs.fbbWriteBooleanMid) return;
    if (!g_bs.aUuidReady || !g_bs.bUuidReady || !g_bs.bUuidObj) return;
    if (!env->IsInstanceOf(packet, g_bs.playerInfoUpdatePacketCls)) return;

    jobject bb0 = env->CallStaticObjectMethod(g_bs.unpooledCls, g_bs.unpooledBufferMid);
    if (!bb0 || env->ExceptionCheck()) { env->ExceptionClear(); return; }
    jobject sbuf = env->NewObject(g_bs.friendlyBufCls, g_bs.friendlyBufCtor, bb0);
    env->DeleteLocalRef(bb0);
    if (!sbuf || env->ExceptionCheck()) { env->ExceptionClear(); return; }
    env->CallVoidMethod(packet, g_bs.playerInfoUpdatePacketWriteMid, sbuf);
    if (env->ExceptionCheck()) { env->ExceptionClear(); env->DeleteLocalRef(sbuf); return; }
    jint bits = env->CallByteMethod(sbuf, g_bs.byteBufGetByteMid, (jint)0) & 0xFF;
    env->DeleteLocalRef(sbuf);
    if (env->ExceptionCheck()) { env->ExceptionClear(); return; }

    bool hasGameMode = bits & 0x04;
    bool hasListed   = bits & 0x08;
    bool hasLatency  = bits & 0x10;
    bool hasDisplay  = bits & 0x20;
    int outBits = (hasGameMode ? 0x04 : 0) | (hasListed ? 0x08 : 0) |
                  (hasLatency ? 0x10 : 0) | (hasDisplay ? 0x20 : 0);

    jobject listA = env->CallObjectMethod(packet, g_bs.piuEntriesMidA);
    if (env->ExceptionCheck()) { env->ExceptionClear(); listA = nullptr; }
    jobject listB = g_bs.piuEntriesMidB ? env->CallObjectMethod(packet, g_bs.piuEntriesMidB) : nullptr;
    if (env->ExceptionCheck()) { env->ExceptionClear(); listB = nullptr; }
    jint sizeA = -1, sizeB = -1;
    if (listA) { sizeA = env->CallIntMethod(listA, g_bs.listSizeMid);
                 if (env->ExceptionCheck()) { env->ExceptionClear(); sizeA = -1; } }
    if (listB) { sizeB = env->CallIntMethod(listB, g_bs.listSizeMid);
                 if (env->ExceptionCheck()) { env->ExceptionClear(); sizeB = -1; } }
    jobject entries = (sizeB > sizeA) ? listB : listA;
    jint    nEntries = (sizeB > sizeA) ? sizeB : sizeA;

    jobject aEntry = nullptr;
    for (jint i = 0; i < nEntries && !aEntry; ++i) {
        jobject e = env->CallObjectMethod(entries, g_bs.listGetMid, i);
        if (!e || env->ExceptionCheck()) { env->ExceptionClear(); if (e) env->DeleteLocalRef(e); continue; }
        jobject euuid = env->CallObjectMethod(e, g_bs.piEntryProfileIdMid);
        if (euuid && !env->ExceptionCheck()) {
            unsigned char eb[16];
            if (uuidToBytes(env, euuid, eb) && std::memcmp(eb, g_bs.aUuidBytes, 16) == 0)
                aEntry = env->NewLocalRef(e);
            env->DeleteLocalRef(euuid);
        } else if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(e);
    }
    if (listA) env->DeleteLocalRef(listA);
    if (listB) env->DeleteLocalRef(listB);
    if (!aEntry) return;

    hideAFromBTab(env, ch);

    if (outBits == 0) { env->DeleteLocalRef(aEntry); return; }

    jobject bb = env->CallStaticObjectMethod(g_bs.unpooledCls, g_bs.unpooledBufferMid);
    if (!bb || env->ExceptionCheck()) { env->ExceptionClear(); env->DeleteLocalRef(aEntry); return; }
    jobject buf = env->NewObject(g_bs.friendlyBufCls, g_bs.friendlyBufCtor, bb);
    env->DeleteLocalRef(bb);
    if (!buf || env->ExceptionCheck()) { env->ExceptionClear(); env->DeleteLocalRef(aEntry); return; }

    env->CallObjectMethod(buf, g_bs.fbbWriteByteMid, (jint)outBits);
    env->CallObjectMethod(buf, g_bs.fbbWriteVarIntMid, (jint)1);
    env->CallObjectMethod(buf, g_bs.fbbWriteUUIDMid, g_bs.bUuidObj);

    if (hasGameMode && g_bs.piEntryGameModeMid && g_bs.gameTypeGetIdMid) {
        jobject gm = env->CallObjectMethod(aEntry, g_bs.piEntryGameModeMid);
        jint id = 0;
        if (gm && !env->ExceptionCheck()) id = env->CallIntMethod(gm, g_bs.gameTypeGetIdMid);
        if (gm) env->DeleteLocalRef(gm);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->CallObjectMethod(buf, g_bs.fbbWriteVarIntMid, id);
    }
    if (hasListed && g_bs.piEntryListedMid) {
        jboolean listed = env->CallBooleanMethod(aEntry, g_bs.piEntryListedMid);
        if (env->ExceptionCheck()) { env->ExceptionClear(); listed = JNI_TRUE; }
        env->CallObjectMethod(buf, g_bs.fbbWriteBooleanMid, listed);
    }
    if (hasLatency && g_bs.piEntryLatencyMid) {
        jint lat = env->CallIntMethod(aEntry, g_bs.piEntryLatencyMid);
        if (env->ExceptionCheck()) { env->ExceptionClear(); lat = 0; }
        env->CallObjectMethod(buf, g_bs.fbbWriteVarIntMid, lat);
    }
    if (hasDisplay) {
        jobject dn = g_bs.piEntryDisplayNameMid ?
            env->CallObjectMethod(aEntry, g_bs.piEntryDisplayNameMid) : nullptr;
        if (env->ExceptionCheck()) { env->ExceptionClear(); dn = nullptr; }

        if (dn && g_bs.fbbWriteComponentMid) {
            env->CallObjectMethod(buf, g_bs.fbbWriteBooleanMid, (jboolean)JNI_TRUE);
            env->CallObjectMethod(buf, g_bs.fbbWriteComponentMid, dn);
        } else {
            env->CallObjectMethod(buf, g_bs.fbbWriteBooleanMid, (jboolean)JNI_FALSE);
        }
        if (dn) env->DeleteLocalRef(dn);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    env->DeleteLocalRef(aEntry);

    jobject pkt = env->NewObject(g_bs.playerInfoUpdatePacketCls,
                                 g_bs.playerInfoUpdatePacketBufCtor, buf);
    env->DeleteLocalRef(buf);
    if (!pkt || env->ExceptionCheck()) { LogAndClearException(env, "mirror: single-entry ctor"); return; }
    env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, pkt);
    if (env->ExceptionCheck()) LogAndClearException(env, "mirror: writeAndFlush");
    env->DeleteLocalRef(pkt);
    LogTo("mirror: single-entry PlayerInfoUpdate to B (bits=0x%02x)", outBits);
}

void synthesiseMinimalPlayerInfoForUuid(JNIEnv* env, jobject ch,
                                        const unsigned char uuidBytes[16]) {
    if (!g_bs.unpooledCls || !g_bs.unpooledWrappedMid || !g_bs.friendlyBufCls ||
        !g_bs.friendlyBufCtor || !g_bs.playerInfoUpdatePacketCls ||
        !g_bs.playerInfoUpdatePacketBufCtor) return;

    unsigned char wire[20];
    wire[0] = 0x01;
    wire[1] = 0x01;
    std::memcpy(wire + 2, uuidBytes, 16);
    wire[18] = 0x00;
    wire[19] = 0x00;

    jbyteArray raw = env->NewByteArray(20);
    if (!raw) return;
    env->SetByteArrayRegion(raw, 0, 20, reinterpret_cast<const jbyte*>(wire));

    jobject wrapped = env->CallStaticObjectMethod(g_bs.unpooledCls,
                                                  g_bs.unpooledWrappedMid, raw);
    env->DeleteLocalRef(raw);
    if (!wrapped || env->ExceptionCheck()) { env->ExceptionClear(); return; }
    jobject buf = env->NewObject(g_bs.friendlyBufCls, g_bs.friendlyBufCtor, wrapped);
    env->DeleteLocalRef(wrapped);
    if (!buf || env->ExceptionCheck()) { env->ExceptionClear(); return; }
    jobject pkt = env->NewObject(g_bs.playerInfoUpdatePacketCls,
                                 g_bs.playerInfoUpdatePacketBufCtor, buf);
    env->DeleteLocalRef(buf);
    if (!pkt || env->ExceptionCheck()) {
        LogAndClearException(env, "synth-tab: packet ctor"); return;
    }
    env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, pkt);
    if (env->ExceptionCheck()) LogAndClearException(env, "synth-tab: writeAndFlush");
    env->DeleteLocalRef(pkt);
}

void ensureTabEntryBeforeAddPlayer(JNIEnv* env, jobject ch, jobject packet) {
    if (!g_bs.addPlayerPacketCls || !g_bs.addPlayerPacketUuidFid) return;
    if (!env->IsInstanceOf(packet, g_bs.addPlayerPacketCls)) return;
    jobject uuid = env->GetObjectField(packet, g_bs.addPlayerPacketUuidFid);
    if (!uuid) { if (env->ExceptionCheck()) env->ExceptionClear(); return; }
    unsigned char bytes[16];
    bool ok = uuidToBytes(env, uuid, bytes);
    env->DeleteLocalRef(uuid);
    if (!ok) return;
    synthesiseMinimalPlayerInfoForUuid(env, ch, bytes);
}

void mirrorTeamPacketToB(JNIEnv* env, jobject ch, jobject packet) {
    if (!g_bs.setPlayerTeamPacketCls || !g_bs.setPlayerTeamPacketBufCtor ||
        !g_bs.setPlayerTeamMethodFid || !g_bs.setPlayerTeamNameFid ||
        !g_bs.setPlayerTeamPlayersFid || !g_bs.collectionContainsMid ||
        !g_bs.friendlyBufCls || !g_bs.friendlyBufCtor ||
        !g_bs.fbbWriteByteMid || !g_bs.fbbWriteVarIntMid || !g_bs.fbbWriteUtfMid ||
        !g_bs.unpooledCls || !g_bs.unpooledBufferMid) return;
    if (!g_bs.aName || !g_bs.bName) return;
    if (!env->IsInstanceOf(packet, g_bs.setPlayerTeamPacketCls)) return;

    jint method = env->GetIntField(packet, g_bs.setPlayerTeamMethodFid);

    if (method != 0 && method != 3 && method != 4) return;

    jobject players = env->GetObjectField(packet, g_bs.setPlayerTeamPlayersFid);
    if (!players) return;
    jboolean hasA = env->CallBooleanMethod(players, g_bs.collectionContainsMid, g_bs.aName);
    env->DeleteLocalRef(players);
    if (env->ExceptionCheck() || !hasA) { env->ExceptionClear(); return; }

    jstring teamName = (jstring)env->GetObjectField(packet, g_bs.setPlayerTeamNameFid);
    if (!teamName) return;

    jint outMethod = (method == 4) ? 4 : 3;

    jobject bb = env->CallStaticObjectMethod(g_bs.unpooledCls, g_bs.unpooledBufferMid);
    if (!bb || env->ExceptionCheck()) { env->ExceptionClear(); env->DeleteLocalRef(teamName); return; }
    jobject buf = env->NewObject(g_bs.friendlyBufCls, g_bs.friendlyBufCtor, bb);
    env->DeleteLocalRef(bb);
    if (!buf || env->ExceptionCheck()) { env->ExceptionClear(); env->DeleteLocalRef(teamName); return; }

    env->CallObjectMethod(buf, g_bs.fbbWriteUtfMid, teamName, (jint)32767);
    env->CallObjectMethod(buf, g_bs.fbbWriteByteMid, (jint)outMethod);
    env->CallObjectMethod(buf, g_bs.fbbWriteVarIntMid, (jint)1);
    env->CallObjectMethod(buf, g_bs.fbbWriteUtfMid, g_bs.bName, (jint)32767);
    env->DeleteLocalRef(teamName);
    if (env->ExceptionCheck()) { LogAndClearException(env, "team-mirror: buffer build"); env->DeleteLocalRef(buf); return; }

    jobject pkt = env->NewObject(g_bs.setPlayerTeamPacketCls,
                                 g_bs.setPlayerTeamPacketBufCtor, buf);
    env->DeleteLocalRef(buf);
    if (!pkt || env->ExceptionCheck()) { LogAndClearException(env, "team-mirror: packet ctor"); return; }
    env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, pkt);
    if (env->ExceptionCheck()) LogAndClearException(env, "team-mirror: writeAndFlush");
    env->DeleteLocalRef(pkt);
    LogTo("team-mirror: synthesised %s for B (server method=%d)",
          outMethod == 3 ? "JOIN" : "LEAVE", method);
}

void writeOneToBWithTabGuard(JNIEnv* env, jobject ch, jobject packet) {
    if (g_bs.customPayloadPacketCls &&
        env->IsInstanceOf(packet, g_bs.customPayloadPacketCls)) {
        static std::atomic<int> dropped{0};
        int n = dropped.fetch_add(1, std::memory_order_relaxed) + 1;
        if (n == 1 || (n & 0x3FF) == 0)
            LogTo("ForwardToB: skipping ClientboundCustomPayloadPacket (mod channel, count=%d)", n);
        return;
    }
    std::lock_guard<std::recursive_mutex> writeLock(g_bs.bWriteMu);
    env->CallObjectMethod(ch, g_bs.channelWriteAndFlushMid, packet);
    if (env->ExceptionCheck()) LogAndClearException(env, "ForwardToB/writeOne");
}

bool forwardBundleExpanded(JNIEnv* env, jobject ch, jobject packet) {
    if (!g_bs.bundlePacketCls || !g_bs.bundleSubPacketsMid ||
        !g_bs.iterableIteratorMid || !g_bs.iteratorHasNextMid ||
        !g_bs.iteratorNextMid)
        return false;
    if (!env->IsInstanceOf(packet, g_bs.bundlePacketCls)) return false;

    jobject iterable = env->CallObjectMethod(packet, g_bs.bundleSubPacketsMid);
    if (env->ExceptionCheck() || !iterable) {
        LogAndClearException(env, "bundle.subPackets");
        return true;
    }
    jobject it = env->CallObjectMethod(iterable, g_bs.iterableIteratorMid);
    env->DeleteLocalRef(iterable);
    if (env->ExceptionCheck() || !it) {
        LogAndClearException(env, "bundle.iterator");
        return true;
    }

    int forwarded = 0, nulls = 0;
    while (true) {
        jboolean more = env->CallBooleanMethod(it, g_bs.iteratorHasNextMid);
        if (env->ExceptionCheck()) {
            LogAndClearException(env, "bundle.iter.hasNext");
            break;
        }
        if (!more) break;

        jobject sub = env->CallObjectMethod(it, g_bs.iteratorNextMid);
        if (env->ExceptionCheck()) {
            LogAndClearException(env, "bundle.iter.next");
            break;
        }
        if (!sub) { ++nulls; continue; }


        if (!forwardBundleExpanded(env, ch, sub)) {
            writeOneToBWithTabGuard(env, ch, sub);
        }
        env->DeleteLocalRef(sub);
        ++forwarded;
    }
    env->DeleteLocalRef(it);
    if (nulls > 0)
        LogTo("bundle: forwarded=%d, null-subs=%d (skipped)", forwarded, nulls);
    else
        LogTo("bundle: forwarded=%d sub-packets", forwarded);
    return true;
}

void BServer_ForwardToB(JNIEnv* env, jobject packet) {
    if (!packet) return;
    if (g_bs.bState.load(std::memory_order_acquire) != BState::Play) return;

    std::string cls = classNameForB(env, packet);
    if (cls.rfind("net.minecraft.network.protocol.game.", 0) != 0) return;

    jobject ch;
    { std::lock_guard<std::mutex> l(g_bs.bMu);
      ch = g_bs.bChannel ? env->NewLocalRef(g_bs.bChannel) : nullptr; }
    if (!ch) return;

    if (!forwardBundleExpanded(env, ch, packet)) {
        writeOneToBWithTabGuard(env, ch, packet);
    }

    env->DeleteLocalRef(ch);
}

void BServer_ObserveAndRouteFromA(JNIEnv* env, jobject packet) {
    if (!env || !packet) return;
    if (g_bs.bundlePacketCls && g_bs.bundleSubPacketsMid && g_bs.iterableIteratorMid &&
        g_bs.iteratorHasNextMid && g_bs.iteratorNextMid && env->IsInstanceOf(packet, g_bs.bundlePacketCls)) {
        jobject items = env->CallObjectMethod(packet, g_bs.bundleSubPacketsMid);
        jobject it = items && !env->ExceptionCheck() ? env->CallObjectMethod(items, g_bs.iterableIteratorMid) : nullptr;
        if (items) env->DeleteLocalRef(items);
        while (it && env->CallBooleanMethod(it, g_bs.iteratorHasNextMid) && !env->ExceptionCheck()) {
            jobject sub = env->CallObjectMethod(it, g_bs.iteratorNextMid);
            if (sub && !env->ExceptionCheck()) {
                BServer_ObserveAndRouteFromA(env, sub);
                env->DeleteLocalRef(sub);
            } else if (env->ExceptionCheck()) env->ExceptionClear();
        }
        if (it) env->DeleteLocalRef(it);
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }
    g_cache.observe(env, packet);
    if (g_bs.bState.load(std::memory_order_acquire) == BState::Play) {
        BServer_ForwardToB(env, packet);
        return;
    }
    std::lock_guard<std::mutex> lock(g_bs.bMu);
    if (!g_bs.bChannel) return;
    if (g_bs.bootstrapQueue.size() >= BServer::kBootstrapMax) {
        env->DeleteGlobalRef(g_bs.bootstrapQueue.front());
        g_bs.bootstrapQueue.erase(g_bs.bootstrapQueue.begin());
    }
    jobject ref = env->NewGlobalRef(packet);
    if (ref) g_bs.bootstrapQueue.push_back(ref);
}

void replayBootstrap(JNIEnv* env, jobject ch, bool skipLogin) {
    if (!env || !ch) return;
    auto packets = g_cache.snapshot(env);
    std::vector<jobject> queued;
    {
        std::lock_guard<std::mutex> lock(g_bs.bMu);
        for (jobject packet : g_bs.bootstrapQueue) {
            jobject local = env->NewLocalRef(packet);
            if (local) queued.push_back(local);
            env->DeleteGlobalRef(packet);
        }
        g_bs.bootstrapQueue.clear();
    }
    auto send = [&](jobject packet) {
        if (!packet) return;
        std::string cls = classNameForB(env, packet);
        if (skipLogin && (cls == "net.minecraft.network.protocol.game.ClientboundLoginPacket" ||
                          cls == "net.minecraft.network.protocol.game.ClientboundRespawnPacket" ||
                          cls == "net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket" ||
                          cls == "net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket" ||
                          cls == "net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket" ||
                          cls == "net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket" ||
                          cls == "net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket")) return;
        if (!forwardBundleExpanded(env, ch, packet)) writeOneToBWithTabGuard(env, ch, packet);
    };
    for (jobject packet : packets) { send(packet); env->DeleteLocalRef(packet); }
    for (jobject packet : queued) { send(packet); env->DeleteLocalRef(packet); }
    LogTo("bootstrap: replayed cache=%zu queued=%zu skipLogin=%d", packets.size(), queued.size(), skipLogin ? 1 : 0);
}

// 原子结束 bootstrap：新实时包会等待尾队列发完后再继续写入。
void finishBootstrapToPlay(JNIEnv* env, jobject ch) {
    if (!env || !ch) return;
    std::lock_guard<std::recursive_mutex> writeLock(g_bs.bWriteMu);
    std::vector<jobject> tail;
    {
        std::lock_guard<std::mutex> lock(g_bs.bMu);
        g_bs.bState.store(BState::Play, std::memory_order_release);
        for (jobject packet : g_bs.bootstrapQueue) {
            jobject local = env->NewLocalRef(packet);
            if (local) tail.push_back(local);
            env->DeleteGlobalRef(packet);
        }
        g_bs.bootstrapQueue.clear();
    }
    for (jobject packet : tail) {
        if (!forwardBundleExpanded(env, ch, packet)) {
            writeOneToBWithTabGuard(env, ch, packet);
        }
        env->DeleteLocalRef(packet);
    }
    LogTo("bootstrap: switched atomically to PLAY; replayed tail=%zu", tail.size());
}

void BSide_OnPacket(JNIEnv* env, jobject , jobject msg) {
    std::string cls = classNameForB(env, msg);
    BState state = g_bs.bState.load(std::memory_order_acquire);
    LogTo("BServer: RX %s (state=%d)", cls.c_str(), (int)state);

    if (g_bs.intentPacketCls && env->IsInstanceOf(msg, g_bs.intentPacketCls)) {
        jobject nextProto = g_bs.protoLogin;
        if (g_bs.intentionPacketIntentFid) {
            jobject intent = env->GetObjectField(msg, g_bs.intentionPacketIntentFid);
            if (intent) {
                if (env->IsSameObject(intent, g_bs.protoStatus)) nextProto = g_bs.protoStatus;
                else                                             nextProto = g_bs.protoLogin;
                env->DeleteLocalRef(intent);
            }
        }
        jobject ch;
        { std::lock_guard<std::mutex> l(g_bs.bMu); ch = g_bs.bChannel ? env->NewLocalRef(g_bs.bChannel) : nullptr; }
        if (ch) {
            setProtocolState(env, ch, nextProto);
            env->DeleteLocalRef(ch);
        }
        g_bs.bState.store(
            (nextProto == g_bs.protoStatus) ? BState::AwaitHandshake
                                             : BState::AwaitLogin,
            std::memory_order_release);
        LogTo("BServer: intention → %s",
              nextProto == g_bs.protoStatus ? "STATUS" : "LOGIN");
        return;
    }

    if (g_bs.pingRequestPacketCls && env->IsInstanceOf(msg, g_bs.pingRequestPacketCls)) {
        jlong t = 0;
        if (g_bs.pingRequestPacketTimeFid)
            t = env->GetLongField(msg, g_bs.pingRequestPacketTimeFid);
        if (g_bs.pongResponsePacketCtor) {
            jobject pong = env->NewObject(g_bs.pongResponsePacketCls,
                                          g_bs.pongResponsePacketCtor, t);
            if (pong) { writeToB(env, pong); env->DeleteLocalRef(pong); }
            LogTo("BServer: replied Pong(%lld)", (long long)t);
        }
        return;
    }

    if (g_bs.statusRequestPacketCls && env->IsInstanceOf(msg, g_bs.statusRequestPacketCls)) {
        if (g_bs.statusResponsePacketCtor && g_bs.serverStatusCtor && g_bs.optionalCls && g_bs.optionalEmptyMid &&
            g_bs.componentCls && g_bs.componentLiteralMid) {
            jstring text = env->NewStringUTF("Server");
            jobject description = env->CallStaticObjectMethod(g_bs.componentCls, g_bs.componentLiteralMid, text);
            env->DeleteLocalRef(text);
            jobject emptyPlayers = env->CallStaticObjectMethod(g_bs.optionalCls, g_bs.optionalEmptyMid);
            jobject emptyVersion = env->CallStaticObjectMethod(g_bs.optionalCls, g_bs.optionalEmptyMid);
            jobject emptyIcon = env->CallStaticObjectMethod(g_bs.optionalCls, g_bs.optionalEmptyMid);
            jobject status = description && emptyPlayers && emptyVersion && emptyIcon && !env->ExceptionCheck()
                ? env->NewObject(g_bs.serverStatusCls, g_bs.serverStatusCtor,
                                 description, emptyPlayers, emptyVersion, emptyIcon, JNI_FALSE)
                : nullptr;
            if (description) env->DeleteLocalRef(description);
            if (emptyPlayers) env->DeleteLocalRef(emptyPlayers);
            if (emptyVersion) env->DeleteLocalRef(emptyVersion);
            if (emptyIcon) env->DeleteLocalRef(emptyIcon);
            if (status && !env->ExceptionCheck()) {
                jobject response = env->NewObject(g_bs.statusResponsePacketCls,
                                                  g_bs.statusResponsePacketCtor, status);
                if (response && !env->ExceptionCheck()) {
                    writeToB(env, response);
                    env->DeleteLocalRef(response);
                    LogTo("BServer: replied STATUS response");
                } else if (env->ExceptionCheck()) env->ExceptionClear();
                env->DeleteLocalRef(status);
            } else if (env->ExceptionCheck()) {
                LogAndClearException(env, "BServer: status response");
            }
        } else {
            LogTo("BServer: STATUS request ignored; response refs unavailable");
        }
        return;
    }

    if (g_bs.helloPacketCls && env->IsInstanceOf(msg, g_bs.helloPacketCls)) {
        completeLogin(env, msg);
        return;
    }

    if (g_bs.bState.load(std::memory_order_acquire) == BState::Play) {
        if (g_bs.acceptTeleportPacketCls && env->IsInstanceOf(msg, g_bs.acceptTeleportPacketCls)) {
            jint id = 0;
            if (ReadTeleportId(env, msg, id) && g_bs.bootstrapAck.consumeLocalTeleport(id)) {
                g_bs.bootstrapAck.reset();
                LogTo("bootstrap: consumed local teleport ACK %d", (int)id);
                return;
            }
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
        if (shouldRouteBToA(cls)) {
            routeToA(env, msg);
        }
    }
}

bool BServer_IsCurrentChannel(JNIEnv* env, jobject ctx) {
    if (!env || !ctx) return false;
    jclass cls = env->GetObjectClass(ctx);
    jmethodID mid = cls ? env->GetMethodID(cls, "channel", "()Lio/netty/channel/Channel;") : nullptr;
    if (!mid && env->ExceptionCheck()) env->ExceptionClear();
    jobject channel = mid ? env->CallObjectMethod(ctx, mid) : nullptr;
    if (env->ExceptionCheck()) { env->ExceptionClear(); channel = nullptr; }
    bool current = false;
    if (channel) {
        std::lock_guard<std::mutex> l(g_bs.bMu);
        current = g_bs.bChannel && env->IsSameObject(channel, g_bs.bChannel);
        env->DeleteLocalRef(channel);
    }
    if (cls) env->DeleteLocalRef(cls);
    return current;
}

void BServer_OnPacket(JNIEnv* env, jobject ctx, jobject msg) {
    BSide_OnPacket(env, ctx, msg);
}

void BServer_OnChannelInactive(JNIEnv* env, jobject ctx) {
    Native_BSide_channelInactive(env, nullptr, ctx);
}
