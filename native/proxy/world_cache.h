#pragma once

#include "proxy.h"

#include <mutex>
#include <string_view>
#include <vector>

class WorldStateCache {
public:
    void observe(JNIEnv* env, jobject packetLocalRef);

    std::vector<jobject> snapshot(JNIEnv* env) const;

    void replay(JNIEnv* env, jobject connection, jmethodID connSendMid) const;
    void clear(JNIEnv* env);
    void dump() const;

    bool hasLogin() const {
        std::lock_guard<std::mutex> lock(m_);
        return login_ != nullptr;
    }

    size_t size() const {
        std::lock_guard<std::mutex> lock(m_);
        return ordered_.size();
    }

private:
    enum class Category {
        Ignored,
        LatestGameEvent,
        LatestSetTime,
        LatestPlayerPosition,
        AppendPlayerInfo,
        AppendEntity,
        AppendChunk,
    };

    static Category categorize(std::string_view fqcn);
    void clearLocked(JNIEnv* env);
    void clearWorldTailLocked(JNIEnv* env);

    mutable std::mutex m_;
    jobject login_ = nullptr;
    bool loginSeen_ = false;

    std::vector<jobject> ordered_;

    jobject gameEvent_ = nullptr;
    jobject setTime_ = nullptr;
    jobject playerPos_ = nullptr;
    std::vector<jobject> playerInfo_;
    std::vector<jobject> entities_;
    std::vector<jobject> chunks_;

    static constexpr size_t kMaxOrdered = 2048;
    static constexpr size_t kMaxChunks = 512;
    static constexpr size_t kMaxEntities = 2048;
    static constexpr size_t kMaxPlayerInfo = 512;
};

extern WorldStateCache g_cache;
