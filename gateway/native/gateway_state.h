#pragma once

#include <windows.h>

constexpr DWORD NIMBUS_GATEWAY_STATE_MAGIC = 0x53474D4E; // NMGS
constexpr DWORD NIMBUS_GATEWAY_STATE_VERSION = 1;

enum NimbusGatewayStage : LONG {
    NIMBUS_GATEWAY_FAILED = -1,
    NIMBUS_GATEWAY_CREATED = 0,
    NIMBUS_GATEWAY_INJECTED = 1,
    NIMBUS_GATEWAY_JVM_ATTACHED = 2,
    NIMBUS_GATEWAY_HOOK_INSTALLED = 3,
    NIMBUS_GATEWAY_BSERVER_READY = 4,
    NIMBUS_GATEWAY_A_CAPTURED = 5,
    NIMBUS_GATEWAY_B_ACTIVE = 6,
};

struct NimbusGatewaySharedState {
    DWORD magic;
    DWORD version;
    DWORD targetPid;
    DWORD loopbackPort;
    volatile LONG stage;
    char error[256];
};

void GatewayState_OpenForCurrentProcess();
void GatewayState_Close();
void GatewayState_SetAtLeast(NimbusGatewayStage stage);
void GatewayState_Fail(const char* message);
unsigned short GatewayState_Port();

