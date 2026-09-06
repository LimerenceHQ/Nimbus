#include <windows.h>
#include <shellapi.h>
#include <objbase.h>
#include <jni.h>
#include <string>
#include <cstdio>
#include "windows_arguments.h"
namespace {
void fail(JNIEnv* env,const char* text,DWORD code){char m[240];std::snprintf(m,sizeof(m),"%s (Windows error %lu)",text,code);auto c=env->FindClass("java/io/IOException");if(c){env->ThrowNew(c,m);env->DeleteLocalRef(c);}}
std::wstring read(JNIEnv* env,jstring value){
    if(!value)return {};auto p=env->GetStringChars(value,nullptr);if(!p)return {};
    std::wstring out(reinterpret_cast<const wchar_t*>(p),env->GetStringLength(value));env->ReleaseStringChars(value,p);return out;
}
}
extern "C" JNIEXPORT jint JNICALL Java_cc_nimbus_features_proxy_NativeProcessScanner_runElevatedNative(JNIEnv* env,jclass,jstring java,jobjectArray arguments,jstring directory){
    try {
        std::wstring executable=read(env,java),cwd=read(env,directory),parameters;
        if(env->ExceptionCheck())return -1;
        if(!arguments||executable.empty()||cwd.empty()){fail(env,"Invalid helper request",ERROR_INVALID_PARAMETER);return -1;}
        for(jsize i=0;i<env->GetArrayLength(arguments);++i){
            auto a=static_cast<jstring>(env->GetObjectArrayElement(arguments,i));auto value=read(env,a);if(a)env->DeleteLocalRef(a);
            if(env->ExceptionCheck())return -1;
            if(value.find(L'\0')!=std::wstring::npos){fail(env,"Invalid helper argument",ERROR_INVALID_PARAMETER);return -1;}
            if(i)parameters+=L' ';parameters+=QuoteWindowsArgument(value);
        }
        HRESULT hr=CoInitializeEx(nullptr,COINIT_APARTMENTTHREADED|COINIT_DISABLE_OLE1DDE);
        struct Com { bool initialized;~Com(){if(initialized)CoUninitialize();} } com{SUCCEEDED(hr)};
        SHELLEXECUTEINFOW info{};info.cbSize=sizeof(info);
        info.fMask=SEE_MASK_NOCLOSEPROCESS|SEE_MASK_NOASYNC|SEE_MASK_FLAG_NO_UI;
        info.lpVerb=L"runas";info.lpFile=executable.c_str();info.lpParameters=parameters.c_str();info.lpDirectory=cwd.c_str();info.nShow=SW_HIDE;
        if(!ShellExecuteExW(&info)) {DWORD error=GetLastError();if(error==ERROR_CANCELLED)return ERROR_CANCELLED;fail(env,"Administrator helper launch failed",error);return -1;}
        struct Handle{HANDLE h;~Handle(){if(h)CloseHandle(h);}}process{info.hProcess};
        if(!process.h){fail(env,"Helper process handle missing",ERROR_INVALID_HANDLE);return -1;}
        DWORD wait=WaitForSingleObject(process.h,90000);
        if(wait!=WAIT_OBJECT_0){fail(env,"Helper completion not confirmed; inspect A before retry",wait==WAIT_TIMEOUT?ERROR_TIMEOUT:GetLastError());return -1;}
        DWORD code=0;if(!GetExitCodeProcess(process.h,&code)){fail(env,"Helper result unavailable",GetLastError());return -1;}
        return static_cast<jint>(code);
    }catch(...){fail(env,"Helper launch allocation failed",ERROR_NOT_ENOUGH_MEMORY);return -1;}
}
