#include "random_name.h"

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <bcrypt.h>

#include <cstdint>
#include <mutex>

namespace {

uint64_t secureWord() {
    uint64_t value = 0;
    if (BCryptGenRandom(nullptr, reinterpret_cast<PUCHAR>(&value), sizeof(value),
                        BCRYPT_USE_SYSTEM_PREFERRED_RNG) == 0) {
        return value;
    }

    LARGE_INTEGER counter{};
    QueryPerformanceCounter(&counter);
    return static_cast<uint64_t>(counter.QuadPart) ^
           (static_cast<uint64_t>(GetCurrentProcessId()) << 32) ^
           static_cast<uint64_t>(GetCurrentThreadId());
}

std::string randomIdentifier(size_t length) {
    static constexpr char alphabet[] =
        "abcdefghijklmnopqrstuvwxyz0123456789";
    std::string result;
    result.reserve(length);
    result.push_back(static_cast<char>('a' + secureWord() % 26));
    for (size_t i = 1; i < length; ++i) {
        result.push_back(alphabet[secureWord() % (sizeof(alphabet) - 1)]);
    }
    return result;
}

std::string GenerateRandomPackageName() {
    return "p/" + randomIdentifier(8) + "/q" + randomIdentifier(8);
}

std::string g_trampolinePackage;
std::once_flag g_packageInitFlag;

}

std::string GenerateRandomClassName(int minWords, int maxWords) {
    (void)minWords;
    (void)maxWords;
    return randomIdentifier(12 + secureWord() % 5);
}

std::string MakeInternalName(const std::string& internalPackage,
                             const std::string& simpleName) {
    if (internalPackage.empty()) return simpleName;
    std::string result = internalPackage;
    if (result.back() != '/') result.push_back('/');
    result += simpleName;
    return result;
}

const std::string& GetTrampolinePackage() {
    std::call_once(g_packageInitFlag, []() {
        g_trampolinePackage = GenerateRandomPackageName();
    });
    return g_trampolinePackage;
}
