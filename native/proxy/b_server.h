

#pragma once

#include "proxy.h"

bool InstallBServer(JNIEnv* env, unsigned short port);
void BServer_StopListener(JNIEnv* env);

void BServer_SetTargetConnection(JNIEnv* env, jobject connection);

bool BServer_IsBActive();

bool BServer_IsLoginIntention(JNIEnv* env, jobject packet);

bool BServer_WaitForBConnected(int timeoutMs);

void BServer_ForwardToB(JNIEnv* env, jobject packet);
void BServer_ObserveAndRouteFromA(JNIEnv* env, jobject packet);

bool BServer_TryCaptureLiveConnection(JNIEnv* env);
bool BServer_IsCurrentChannel(JNIEnv* env, jobject ctx);
void BServer_OnPacket(JNIEnv* env, jobject ctx, jobject msg);
void BServer_OnChannelInactive(JNIEnv* env, jobject ctx);
void BServer_OnAChannelInactive(JNIEnv* env, jobject ctx);
