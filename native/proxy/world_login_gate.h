#pragma once
#include <cstdint>

class WorldLoginGate {
public:
    enum class Decision { Closed, BeginWorld, Forward, MissingLogin };
    void reset() { ++generation_; awaiting_ = false; ready_ = false; }
    void begin() { awaiting_ = true; ready_ = false; }
    Decision inspect(bool login) const {
        if (!awaiting_ && !ready_) return Decision::Closed;
        if (ready_) return Decision::Forward;
        return login ? Decision::BeginWorld : Decision::MissingLogin;
    }
    bool commit(std::uint64_t generation, bool accepted) {
        if (generation != generation_ || !awaiting_ || !accepted) return false;
        awaiting_ = false; ready_ = true; return true;
    }
    std::uint64_t generation() const { return generation_; }
    bool ready() const { return ready_; }
private:
    std::uint64_t generation_ = 0;
    bool awaiting_ = false;
    bool ready_ = false;
};
