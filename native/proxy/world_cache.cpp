#include "world_cache.h"

#include <cstring>

WorldStateCache g_cache;

namespace {

bool starts(std::string_view fqcn, std::string_view prefix) {
    return fqcn.size() >= prefix.size() &&
           std::memcmp(fqcn.data(), prefix.data(), prefix.size()) == 0;
}

std::string classNameFor(JNIEnv* env, jobject o) {
    if (!o || !g_jvmti) return {};
    jclass c = env->GetObjectClass(o);
    if (!c) return {};
    char* sig = nullptr;
    jvmtiError rc = g_jvmti->GetClassSignature(c, &sig, nullptr);
    env->DeleteLocalRef(c);
    if (rc != JVMTI_ERROR_NONE || !sig) return {};
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

bool isSame(JNIEnv* env, jobject a, jobject b) {
    return a && b && env->IsSameObject(a, b);
}

}

WorldStateCache::Category WorldStateCache::categorize(std::string_view fqcn) {
    static constexpr char kGame[] = "net.minecraft.network.protocol.game.";
    if (!starts(fqcn, kGame)) return Category::Ignored;
    std::string_view leaf = fqcn.substr(sizeof(kGame) - 1);
    auto dollar = leaf.find('$');
    if (dollar != std::string_view::npos) leaf = leaf.substr(0, dollar);

    if (leaf == "ClientboundGameEventPacket") return Category::LatestGameEvent;
    if (leaf == "ClientboundSetTimePacket") return Category::LatestSetTime;
    if (leaf == "ClientboundPlayerPositionPacket") return Category::LatestPlayerPosition;
    if (leaf == "ClientboundPlayerInfoUpdatePacket" ||
        leaf == "ClientboundPlayerInfoRemovePacket") return Category::AppendPlayerInfo;
    if (leaf == "ClientboundAddEntityPacket" ||
        leaf == "ClientboundAddPlayerPacket" ||
        leaf == "ClientboundRemoveEntitiesPacket") return Category::AppendEntity;
    if (leaf == "ClientboundLevelChunkWithLightPacket" ||
        leaf == "ClientboundForgetLevelChunkPacket") return Category::AppendChunk;
    return Category::Ignored;
}

void WorldStateCache::clearLocked(JNIEnv* env) {
    auto drop = [&](jobject& slot) {
        if (slot) {
            env->DeleteGlobalRef(slot);
            slot = nullptr;
        }
    };
    auto dropAll = [&](std::vector<jobject>& list) {
        for (jobject packet : list) env->DeleteGlobalRef(packet);
        list.clear();
    };

    drop(login_);
    drop(gameEvent_);
    drop(setTime_);
    drop(playerPos_);
    dropAll(ordered_);
    dropAll(playerInfo_);
    dropAll(entities_);
    dropAll(chunks_);
    loginSeen_ = false;
}

void WorldStateCache::clearWorldTailLocked(JNIEnv* env) {
    auto drop = [&](jobject& slot) {
        if (slot) {
            env->DeleteGlobalRef(slot);
            slot = nullptr;
        }
    };
    auto dropAll = [&](std::vector<jobject>& list) {
        for (jobject packet : list) env->DeleteGlobalRef(packet);
        list.clear();
    };
    drop(gameEvent_);
    drop(setTime_);
    drop(playerPos_);
    dropAll(ordered_);
    dropAll(playerInfo_);
    dropAll(entities_);
    dropAll(chunks_);
    loginSeen_ = login_ != nullptr;
    if (login_) {
        jobject orderedLogin = env->NewGlobalRef(login_);
        if (orderedLogin) ordered_.push_back(orderedLogin);
    }
}
void WorldStateCache::observe(JNIEnv* env, jobject packetLocalRef) {
    if (!env || !packetLocalRef) return;
    const std::string cls = classNameFor(env, packetLocalRef);
    if (cls.rfind("net.minecraft.network.protocol.game.", 0) != 0) return;

    const bool isLogin = cls == "net.minecraft.network.protocol.game.ClientboundLoginPacket";
    const bool isRespawn = cls == "net.minecraft.network.protocol.game.ClientboundRespawnPacket";

    std::lock_guard<std::mutex> lock(m_);
    if (isLogin) {
        clearLocked(env);
        login_ = env->NewGlobalRef(packetLocalRef);
        jobject ordered = env->NewGlobalRef(packetLocalRef);
        if (!login_ || !ordered) {
            if (login_) env->DeleteGlobalRef(login_);
            if (ordered) env->DeleteGlobalRef(ordered);
            login_ = nullptr;
            loginSeen_ = false;
            return;
        }
        ordered_.push_back(ordered);
        loginSeen_ = true;
        LogTo("world-cache: captured real login packet");
        return;
    }

    if (isRespawn) {
        clearWorldTailLocked(env);
        LogTo("world-cache: reset world tail on respawn; retained real login");
    }
    if (!loginSeen_) return;

    jobject ordered = nullptr;
    if (ordered_.size() < kMaxOrdered) ordered = env->NewGlobalRef(packetLocalRef);

    const Category cat = categorize(cls);
    jobject categorized = cat == Category::Ignored ? nullptr : env->NewGlobalRef(packetLocalRef);
    if (!ordered && !categorized) return;

    if (ordered) ordered_.push_back(ordered);

    auto replaceLatest = [&](jobject& slot) {
        if (slot) env->DeleteGlobalRef(slot);
        slot = categorized;
        categorized = nullptr;
    };
    auto pushCapped = [&](std::vector<jobject>& list, size_t cap) {
        if (!categorized) return;
        list.push_back(categorized);
        categorized = nullptr;
        while (list.size() > cap) {
            env->DeleteGlobalRef(list.front());
            list.erase(list.begin());
        }
    };

    switch (cat) {
        case Category::LatestGameEvent:      replaceLatest(gameEvent_); break;
        case Category::LatestSetTime:        replaceLatest(setTime_); break;
        case Category::LatestPlayerPosition: replaceLatest(playerPos_); break;
        case Category::AppendPlayerInfo:     pushCapped(playerInfo_, kMaxPlayerInfo); break;
        case Category::AppendEntity:         pushCapped(entities_, kMaxEntities); break;
        case Category::AppendChunk:          pushCapped(chunks_, kMaxChunks); break;
        default: break;
    }
    if (categorized) env->DeleteGlobalRef(categorized);
}

std::vector<jobject> WorldStateCache::snapshot(JNIEnv* env) const {
    std::vector<jobject> out;
    if (!env) return out;
    std::lock_guard<std::mutex> lock(m_);
    out.reserve(ordered_.size() + playerInfo_.size() + entities_.size() + chunks_.size() + 4);

    auto appendUnique = [&](jobject packet) {
        if (!packet) return;
        for (jobject existing : out) {
            if (isSame(env, existing, packet)) return;
        }
        jobject local = env->NewLocalRef(packet);
        if (local) out.push_back(local);
    };

    for (jobject packet : ordered_) appendUnique(packet);
    appendUnique(gameEvent_);
    appendUnique(setTime_);
    for (jobject packet : playerInfo_) appendUnique(packet);
    for (jobject packet : entities_) appendUnique(packet);
    for (jobject packet : chunks_) appendUnique(packet);
    appendUnique(playerPos_);
    return out;
}

void WorldStateCache::replay(JNIEnv* env, jobject connection, jmethodID connSendMid) const {
    if (!env || !connection || !connSendMid) return;
    auto packets = snapshot(env);
    LogTo("world-cache: replaying %zu real packets", packets.size());
    for (size_t i = 0; i < packets.size(); ++i) {
        jobject packet = packets[i];
        env->CallVoidMethod(connection, connSendMid, packet);
        env->DeleteLocalRef(packet);
        packets[i] = nullptr;
        if (env->ExceptionCheck()) {
            LogAndClearException(env, "world-cache: replay");
            for (jobject rest : packets) if (rest) env->DeleteLocalRef(rest);
            return;
        }
    }
}
void WorldStateCache::clear(JNIEnv* env) {
    if (!env) return;
    std::lock_guard<std::mutex> lock(m_);
    clearLocked(env);
}

void WorldStateCache::dump() const {
    std::lock_guard<std::mutex> lock(m_);
    LogTo("WorldStateCache: login=%d ordered=%zu gameEvent=%d setTime=%d pos=%d playerInfo=%zu entities=%zu chunks=%zu",
          login_ ? 1 : 0, ordered_.size(), gameEvent_ ? 1 : 0, setTime_ ? 1 : 0,
          playerPos_ ? 1 : 0, playerInfo_.size(), entities_.size(), chunks_.size());
}
