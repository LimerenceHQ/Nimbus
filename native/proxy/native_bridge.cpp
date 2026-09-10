#include "proxy.h"
#include <cerrno>
#include <cstdlib>
#include <cstring>

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options, void*) {
    if (!options || std::strncmp(options, "port=", 5) != 0) return 10;
    const char* separator = std::strchr(options + 5, ';');
    char* end = nullptr;
    errno = 0;
    long port = std::strtol(options + 5, &end, 10);
    if (errno || end == options + 5 || (separator ? end != separator : *end != '\0') ||
        port < 1 || port > 65535) return 10;
    if (separator && std::strncmp(separator, ";log=", 5) == 0 && separator[5] != '\0')
        SetProxyLogPath(separator + 5);
    try { return InitializeProxy(vm, static_cast<unsigned short>(port)); }
    catch (...) { LogTo("attach: unexpected initialization failure"); return 19; }
}
