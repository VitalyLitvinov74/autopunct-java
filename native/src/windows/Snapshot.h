#pragma once
#include <cstdint>
#include <string>

namespace autopunct {
// Неизменяемые значения одного снимка; позиции измеряются в UTF-16.
struct Snapshot {
    std::uint64_t requestId;
    std::string contextId;
    std::uint64_t revision;
    std::wstring text;
    std::string application;
    std::uintptr_t window;
};
}
