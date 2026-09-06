#pragma once
#include <cstdint>
#include <limits>
class BootstrapAck {
public:
    int begin() { pending_=true; return id_; }
    bool consumeLocalTeleport(int id) { if(!pending_ || id!=id_)return false;return true; }
    void reset(){pending_=false;if(id_== -1)id_=std::numeric_limits<int>::min();else ++id_;}
private:
    int id_=std::numeric_limits<int>::min();bool pending_=false;
};
