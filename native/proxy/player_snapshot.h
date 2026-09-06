#pragma once
#include "include/jni.h"
struct PlayerSnapshot { jint entityId=0; jdouble x=0,y=0,z=0; jfloat yaw=0,pitch=0; };
bool ReadPlayerSnapshot(JNIEnv* env, jobject player, PlayerSnapshot& out);
bool ReadTeleportId(JNIEnv* env, jobject packet, jint& out);
