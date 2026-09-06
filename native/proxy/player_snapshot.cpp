#include "player_snapshot.h"
#include <cmath>
namespace {
jmethodID method(JNIEnv* e,jclass c,const char* named,const char* srg,const char* descriptor) {
    auto m=e->GetMethodID(c,named,descriptor);
    if(e->ExceptionCheck()){e->ExceptionClear();m=e->GetMethodID(c,srg,descriptor);}
    if(e->ExceptionCheck()){e->ExceptionClear();return nullptr;} return m;
}
}
bool ReadPlayerSnapshot(JNIEnv* e,jobject player,PlayerSnapshot& out) {
    if(!player || e->ExceptionCheck() || e->PushLocalFrame(8)!=JNI_OK) return false;
    struct Frame { JNIEnv* e; ~Frame(){e->PopLocalFrame(nullptr);} } frame{e};
    auto c=e->GetObjectClass(player); if(!c) return false;
    auto id=method(e,c,"getId","m_19879_","()I");
    auto x=method(e,c,"getX","m_20185_","()D");
    auto y=method(e,c,"getY","m_20186_","()D");
    auto z=method(e,c,"getZ","m_20189_","()D");
    auto yaw=method(e,c,"getYRot","m_146908_","()F");
    auto pitch=method(e,c,"getXRot","m_146909_","()F");
    if(!id||!x||!y||!z||!yaw||!pitch) return false;
    PlayerSnapshot p;
    p.entityId=e->CallIntMethod(player,id); if(e->ExceptionCheck())return false;
    p.x=e->CallDoubleMethod(player,x); if(e->ExceptionCheck())return false;
    p.y=e->CallDoubleMethod(player,y); if(e->ExceptionCheck())return false;
    p.z=e->CallDoubleMethod(player,z); if(e->ExceptionCheck())return false;
    p.yaw=e->CallFloatMethod(player,yaw); if(e->ExceptionCheck())return false;
    p.pitch=e->CallFloatMethod(player,pitch); if(e->ExceptionCheck())return false;
    if(!std::isfinite(p.x)||!std::isfinite(p.y)||!std::isfinite(p.z)||!std::isfinite(p.yaw)||!std::isfinite(p.pitch)) return false;
    out=p;return true;
}
bool ReadTeleportId(JNIEnv* e,jobject packet,jint& out) {
    if(!packet || e->ExceptionCheck()) return false;
    auto c=e->GetObjectClass(packet);if(!c)return false;
    auto m=method(e,c,"getId","m_133795_","()I");e->DeleteLocalRef(c);
    if(!m)return false; out=e->CallIntMethod(packet,m);return !e->ExceptionCheck();
}
