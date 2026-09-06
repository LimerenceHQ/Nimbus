#pragma once
#include "include/jni.h"
#include <functional>
struct ScoreboardSnapshotStats { int objectives=0, teams=0, scores=0, displays=0; };
bool WriteScoreboardSnapshot(JNIEnv* env, jobject loader, jobject level,
    const std::function<bool(jobject)>& writer, ScoreboardSnapshotStats& stats);
jstring ReadProxyPlayerName(JNIEnv* env, jobject user, jobject player = nullptr);
