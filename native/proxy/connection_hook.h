

#pragma once

#include "proxy.h"

bool InstallConnectionHook(JNIEnv* env);
bool ConnectionHook_CanCaptureFutureConnection();
