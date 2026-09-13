#include <windows.h>
#include <tlhelp32.h>
#include <jni.h>
#include <algorithm>
#include <cwchar>
#include <cstdio>
#include <string>
#include <vector>

namespace {
struct Handle { HANDLE value; ~Handle() { if(value && value != INVALID_HANDLE_VALUE) CloseHandle(value); } };
struct Process { DWORD pid; std::wstring name; ULONGLONG created = 0; DWORD queryError = 0; bool window = false; };
void fail(JNIEnv* env, const char* operation, DWORD error) {
    char message[160]; std::snprintf(message, sizeof(message), "%s failed (Windows error %lu)", operation, error);
    jclass cls = env->FindClass("java/io/IOException");
    if (cls) { env->ThrowNew(cls, message); env->DeleteLocalRef(cls); }
}
bool javaName(const std::wstring& name) {
    return _wcsicmp(name.c_str(), L"java.exe") == 0 || _wcsicmp(name.c_str(), L"javaw.exe") == 0;
}
std::vector<Process> snapshot(JNIEnv* env) {
    Handle snap{CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0)};
    if (snap.value == INVALID_HANDLE_VALUE) { fail(env, "CreateToolhelp32Snapshot", GetLastError()); return {}; }
    PROCESSENTRY32W entry{}; entry.dwSize = sizeof(entry);
    std::vector<Process> result;
    if (!Process32FirstW(snap.value, &entry)) {
        DWORD error = GetLastError(); if(error != ERROR_NO_MORE_FILES) fail(env, "Process32FirstW", error);
        return result;
    }
    do { result.push_back({entry.th32ProcessID, entry.szExeFile}); } while(Process32NextW(snap.value, &entry));
    DWORD error = GetLastError(); if(error != ERROR_NO_MORE_FILES) fail(env, "Process32NextW", error);
    return result;
}
void creationTime(Process& process) {
    Handle handle{OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, process.pid)};
    if (!handle.value) { process.queryError = GetLastError(); return; }
    FILETIME created{}, exited{}, kernel{}, user{};
    if(!GetProcessTimes(handle.value, &created, &exited, &kernel, &user)) { process.queryError=GetLastError(); return; }
    ULARGE_INTEGER ticks{}; ticks.LowPart=created.dwLowDateTime; ticks.HighPart=created.dwHighDateTime;
    process.created=ticks.QuadPart;
}
BOOL CALLBACK window(HWND hwnd, LPARAM data) {
    if (!IsWindowVisible(hwnd)) return TRUE;
    wchar_t title[512]{};
    if(GetWindowTextW(hwnd,title,512)<=0 || !std::wcsstr(title,L"布吉岛")) return TRUE;
    DWORD pid=0; GetWindowThreadProcessId(hwnd,&pid);
    auto& processes=*reinterpret_cast<std::vector<Process>*>(data);
    for(auto& process:processes) if(process.pid==pid) { process.window=true; break; }
    return TRUE;
}
struct WindowQuery { DWORD pid; bool found; };
BOOL CALLBACK findBuchidaoWindow(HWND hwnd, LPARAM data) {
    auto& query = *reinterpret_cast<WindowQuery*>(data);
    if (!IsWindowVisible(hwnd)) return TRUE;
    DWORD pid = 0;
    GetWindowThreadProcessId(hwnd, &pid);
    if (pid != query.pid) return TRUE;
    wchar_t title[512]{};
    if (GetWindowTextW(hwnd, title, 512) > 0 && std::wcsstr(title, L"布吉岛")) {
        query.found = true;
        return FALSE;
    }
    return TRUE;
}
jstring text(JNIEnv* env,const std::wstring& value) {
    return env->NewString(reinterpret_cast<const jchar*>(value.data()),static_cast<jsize>(value.size()));
}
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_cc_nimbus_features_proxy_NativeProcessScanner_discoverNative(JNIEnv* env,jclass) {
    try {
        auto processes=snapshot(env); if(env->ExceptionCheck()) return nullptr;
        SetLastError(ERROR_SUCCESS);
        EnumWindows(window,reinterpret_cast<LPARAM>(&processes));
        std::vector<Process> javaProcesses;
        for (const auto& entry : processes) {
            if (javaName(entry.name)) javaProcesses.push_back(entry);
        }
        processes.swap(javaProcesses);
        for(auto& p:processes) creationTime(p);
        std::sort(processes.begin(),processes.end(),[](const Process& a,const Process& b){
            if(a.window!=b.window) return a.window>b.window;
            if(a.created!=b.created) return a.created>b.created;
            return a.pid>b.pid;
        });
        std::vector<jlong> ids; for(const auto& p:processes) ids.push_back(p.pid);
        auto out=env->NewLongArray(static_cast<jsize>(ids.size()));
        if(out && !ids.empty()) env->SetLongArrayRegion(out,0,static_cast<jsize>(ids.size()),ids.data());
        return out;
    } catch(...) { fail(env,"Process discovery",ERROR_NOT_ENOUGH_MEMORY); return nullptr; }
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_cc_nimbus_features_proxy_NativeProcessScanner_inspectNative(JNIEnv* env,jclass,jlong pid) {
    if(pid<=0 || pid>MAXDWORD) { fail(env,"Invalid PID",ERROR_INVALID_PARAMETER); return nullptr; }
    try {
        auto processes=snapshot(env); if(env->ExceptionCheck()) return nullptr;
        for(auto& p:processes) if(p.pid==static_cast<DWORD>(pid)) {
            creationTime(p);
            jclass strings=env->FindClass("java/lang/String"); if(!strings) return nullptr;
            auto out=env->NewObjectArray(3,strings,nullptr); env->DeleteLocalRef(strings); if(!out) return nullptr;
            std::wstring fields[]={p.name,std::to_wstring(p.created),std::to_wstring(p.queryError)};
            for(int i=0;i<3 && !env->ExceptionCheck();++i) { auto value=text(env,fields[i]); if(!value) break; env->SetObjectArrayElement(out,i,value); env->DeleteLocalRef(value); }
            return out;
        }
        return nullptr;
    } catch(...) { fail(env,"Process query",ERROR_NOT_ENOUGH_MEMORY); return nullptr; }
}
extern "C" JNIEXPORT jboolean JNICALL
Java_cc_nimbus_features_proxy_NativeProcessScanner_hasBuchidaoWindowNative(JNIEnv*,jclass,jlong pid) {
    if (pid <= 0 || pid > MAXDWORD) return JNI_FALSE;
    WindowQuery query{static_cast<DWORD>(pid), false};
    EnumWindows(findBuchidaoWindow, reinterpret_cast<LPARAM>(&query));
    return query.found ? JNI_TRUE : JNI_FALSE;
}

