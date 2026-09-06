#pragma once
#include <cstdint>
#include <string_view>

class ScoreboardSyncGate {
public:
    enum class Phase { Live, BeforeMarker, AfterMarker };
    std::uint64_t begin() { ++generation_; phase_=Phase::BeforeMarker; return generation_; }
    void cancel() { ++generation_; phase_=Phase::Live; }
    bool mark(std::uint64_t g) {
        if(g!=generation_ || phase_!=Phase::BeforeMarker) return false;
        phase_=Phase::AfterMarker; return true;
    }
    bool complete(std::uint64_t g) {
        if(g!=generation_ || phase_!=Phase::AfterMarker) return false;
        phase_=Phase::Live; return true;
    }
    bool current(std::uint64_t g) const { return g==generation_ && pending(); }
    bool pending() const { return phase_!=Phase::Live; }
    Phase phase() const { return phase_; }
private:
    std::uint64_t generation_=0;
    Phase phase_=Phase::Live;
};
inline bool IsScoreboardPacket(std::string_view name) {
    constexpr std::string_view prefix="net.minecraft.network.protocol.game.";
    if(name.substr(0,prefix.size())!=prefix) return false;
    name.remove_prefix(prefix.size());
    return name=="ClientboundSetObjectivePacket" || name=="ClientboundSetScorePacket" ||
           name=="ClientboundSetDisplayObjectivePacket" || name=="ClientboundSetPlayerTeamPacket";
}
