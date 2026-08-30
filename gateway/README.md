# Nimbus Gateway

Nimbus Gateway is the single-server bridge used by Nimbus for the authorised
网易「布吉岛」白端. It does not add or load a new Minecraft/Fabric Mod. The
existing white client is started normally, and the native bridge is injected
into that JVM by PID. The white client therefore remains responsible for the
real account authentication and remote server connection (A side); Nimbus
connects only to the loopback BServer exposed by the injected bridge (B side).

The native build produces two Windows x64 binaries:

* `nimbus-gateway-bridge.dll` — loaded inside the white-client JVM.
* `nimbus-gateway-host.exe` — injector and session-status host.

The host reports line-delimited JSON stages and stays alive while the white
client owns the real connection. Nimbus owns the lifecycle and terminates the
host and white client together. The target profile is intentionally fixed to
`BujiIslandProfile`; this is not a general-purpose proxy.

Build from the repository root with:

```text
gradlew build -Pnimbus.gateway.native=true
```

The native bridge requires an authorised white-client distribution and its
original protocol components. Nimbus Gateway does not provide credentials,
server bypasses, or replacement protocol components.
