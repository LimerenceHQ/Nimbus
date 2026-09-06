#include "scoreboard_snapshot.h"
#include "proxy.h"

namespace {
struct Frame {
    JNIEnv* env; bool valid;
    explicit Frame(JNIEnv* e,int n=64):env(e),valid(e->PushLocalFrame(n)==JNI_OK) {}
    ~Frame(){if(valid) env->PopLocalFrame(nullptr);}
};
jmethodID method(JNIEnv* env,jclass cls,const char* named,const char* srg,const char* desc,bool isStatic=false) {
    if(!cls || env->ExceptionCheck()) return nullptr;
    for(const char* name : {named,srg}) {
        jmethodID id=isStatic?env->GetStaticMethodID(cls,name,desc):env->GetMethodID(cls,name,desc);
        if(env->ExceptionCheck()){env->ExceptionClear();continue;}
        if(id) return id;
    }
    return nullptr;
}
struct Iteration {
    JNIEnv* env;
    jmethodID iterator,hasNext,next;
    bool each(jobject collection,const std::function<bool(jobject)>& visit) const {
        Frame frame(env,8);
        if(!frame.valid || !collection || env->ExceptionCheck()) return false;
        jobject it=env->CallObjectMethod(collection,iterator);
        if(!it || env->ExceptionCheck()) return false;
        for(;;){
            bool more=env->CallBooleanMethod(it,hasNext);
            if(env->ExceptionCheck()) return false;
            if(!more) return true;
            Frame item(env,32);
            if(!item.valid) return false;
            jobject value=env->CallObjectMethod(it,next);
            if(!value || env->ExceptionCheck() || !visit(value)) return false;
        }
    }
};
}
bool WriteScoreboardSnapshot(JNIEnv* env,jobject loader,jobject level,
    const std::function<bool(jobject)>& writer,ScoreboardSnapshotStats& stats) {
    Frame frame(env,96);
    if(!frame.valid || !loader || !level || env->ExceptionCheck()) return false;
    auto load=[&](const char* name){return env->ExceptionCheck()?nullptr:LoadClassInLoader(env,loader,name);};
    jclass levelClass=load("net.minecraft.world.level.Level");
    jclass boardClass=load("net.minecraft.world.scores.Scoreboard");
    jclass objectiveClass=load("net.minecraft.world.scores.Objective");
    jclass scoreClass=load("net.minecraft.world.scores.Score");
    jclass objectivePacket=load("net.minecraft.network.protocol.game.ClientboundSetObjectivePacket");
    jclass scorePacket=load("net.minecraft.network.protocol.game.ClientboundSetScorePacket");
    jclass displayPacket=load("net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket");
    jclass teamPacket=load("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket");
    jclass changeClass=load("net.minecraft.server.ServerScoreboard$Method");
    if(!levelClass || !boardClass || !objectiveClass || !scoreClass || !objectivePacket ||
       !scorePacket || !displayPacket || !teamPacket || !changeClass || env->ExceptionCheck()) return false;
    auto getBoard=method(env,levelClass,"getScoreboard","m_6188_","()Lnet/minecraft/world/scores/Scoreboard;");
    auto objectives=method(env,boardClass,"getObjectives","m_83466_","()Ljava/util/Collection;");
    auto teams=method(env,boardClass,"getPlayerTeams","m_83491_","()Ljava/util/Collection;");
    auto scores=method(env,boardClass,"getPlayerScores","m_83498_","(Lnet/minecraft/world/scores/Objective;)Ljava/util/Collection;");
    auto display=method(env,boardClass,"getDisplayObjective","m_83416_","(I)Lnet/minecraft/world/scores/Objective;");
    auto objectiveName=method(env,objectiveClass,"getName","m_83320_","()Ljava/lang/String;");
    auto owner=method(env,scoreClass,"getOwner","m_83405_","()Ljava/lang/String;");
    auto value=method(env,scoreClass,"getScore","m_83400_","()I");
    auto objectiveCtor=method(env,objectivePacket,"<init>","<init>","(Lnet/minecraft/world/scores/Objective;I)V");
    auto scoreCtor=method(env,scorePacket,"<init>","<init>","(Lnet/minecraft/server/ServerScoreboard$Method;Ljava/lang/String;Ljava/lang/String;I)V");
    auto displayCtor=method(env,displayPacket,"<init>","<init>","(ILnet/minecraft/world/scores/Objective;)V");
    auto teamCreate=method(env,teamPacket,"createAddOrModifyPacket","m_179332_","(Lnet/minecraft/world/scores/PlayerTeam;Z)Lnet/minecraft/network/protocol/game/ClientboundSetPlayerTeamPacket;",true);
    if(!getBoard || !objectives || !teams || !scores || !display || !objectiveName || !owner || !value ||
       !objectiveCtor || !scoreCtor || !displayCtor || !teamCreate) {
        LogTo("scoreboard-bootstrap: required 1.20.1 member mapping unavailable"); return false;
    }
    jclass iterable=env->FindClass("java/lang/Iterable");
    jclass iterator=env->FindClass("java/util/Iterator");
    if(!iterable || !iterator || env->ExceptionCheck()) return false;
    Iteration walk{env,env->GetMethodID(iterable,"iterator","()Ljava/util/Iterator;"),
        env->GetMethodID(iterator,"hasNext","()Z"),env->GetMethodID(iterator,"next","()Ljava/lang/Object;")};
    if(!walk.iterator || !walk.hasNext || !walk.next || env->ExceptionCheck()) return false;
    auto changeField=env->GetStaticFieldID(changeClass,"CHANGE","Lnet/minecraft/server/ServerScoreboard$Method;");
    if(!changeField || env->ExceptionCheck()) return false;
    jobject change=env->GetStaticObjectField(changeClass,changeField);
    jobject board=env->CallObjectMethod(level,getBoard);
    if(!board || !change || env->ExceptionCheck()) return false;
    jobject allObjectives=env->CallObjectMethod(board,objectives);
    if(!allObjectives || env->ExceptionCheck()) return false;
    jobject allTeams=env->CallObjectMethod(board,teams);
    if(!allTeams || env->ExceptionCheck()) return false;
    int emitted=0;
    auto emit=[&](jobject packet){
        if(!packet || env->ExceptionCheck()) return false;
        if(++emitted>32768){LogTo("scoreboard-bootstrap: snapshot exceeds 32768 packet limit");return false;}
        return writer(packet);
    };
    if(!walk.each(allObjectives,[&](jobject objective){
        if(!emit(env->NewObject(objectivePacket,objectiveCtor,objective,(jint)0))) return false;
        ++stats.objectives;return true;
    })) return false;
    if(!walk.each(allTeams,[&](jobject team){
        if(!emit(env->CallStaticObjectMethod(teamPacket,teamCreate,team,(jboolean)JNI_TRUE))) return false;
        ++stats.teams;return true;
    })) return false;
    if(!walk.each(allObjectives,[&](jobject objective){
        jobject name=env->CallObjectMethod(objective,objectiveName);
        if(!name || env->ExceptionCheck()) return false;
        jobject allScores=env->CallObjectMethod(board,scores,objective);
        return walk.each(allScores,[&](jobject score){
            jobject scoreOwner=env->CallObjectMethod(score,owner);
            if(!scoreOwner || env->ExceptionCheck()) return false;
            jint amount=env->CallIntMethod(score,value);
            if(env->ExceptionCheck()) return false;
            if(!emit(env->NewObject(scorePacket,scoreCtor,change,name,scoreOwner,amount))) return false;
            ++stats.scores;return true;
        });
    })) return false;
    for(jint slot=0;slot<19;++slot){
        Frame item(env,8);if(!item.valid)return false;
        jobject objective=env->CallObjectMethod(board,display,slot);
        if(env->ExceptionCheck())return false;
        if(!objective)continue;
        if(!emit(env->NewObject(displayPacket,displayCtor,slot,objective)))return false;
        ++stats.displays;
    }
    return !env->ExceptionCheck();
}

jstring ReadProxyPlayerName(JNIEnv* env,jobject user,jobject player) {
    if(env->ExceptionCheck()) return nullptr;
    if(player){
        jclass cls=env->GetObjectClass(player);
        if(!cls || env->ExceptionCheck()) return nullptr;
        auto getProfile=method(env,cls,"getGameProfile","m_36316_","()Lcom/mojang/authlib/GameProfile;");
        env->DeleteLocalRef(cls);
        if(!getProfile || env->ExceptionCheck()) return nullptr;
        jobject profile=env->CallObjectMethod(player,getProfile);
        if(!profile || env->ExceptionCheck()) return nullptr;
        jclass profileClass=env->GetObjectClass(profile);
        auto getName=method(env,profileClass,"getName","getName","()Ljava/lang/String;");
        if(profileClass)env->DeleteLocalRef(profileClass);
        if(!getName || env->ExceptionCheck()){env->DeleteLocalRef(profile);return nullptr;}
        auto name=static_cast<jstring>(env->CallObjectMethod(profile,getName));
        bool failed=env->ExceptionCheck();env->DeleteLocalRef(profile);
        return failed?nullptr:name;
    }
    if(!user) return nullptr;
    jclass cls=env->GetObjectClass(user);
    if(!cls || env->ExceptionCheck()) return nullptr;
    auto getName=method(env,cls,"getName","m_92546_","()Ljava/lang/String;");
    env->DeleteLocalRef(cls);
    if(!getName || env->ExceptionCheck()) return nullptr;
    auto name=static_cast<jstring>(env->CallObjectMethod(user,getName));
    if(!name || env->ExceptionCheck()) return nullptr;
    return name;
}
