#pragma once
#include <windows.h>
#include <cstdint>
#include <mutex>
#include <optional>
#include <string>

namespace autopunct {
// Владеет ограниченным по времени соединением с Java в текущем сеансе пользователя.
class PipeConnection {
public:
    PipeConnection();
    std::optional<std::string> exchange(const std::string& request);
    void cancel();
private:
    bool transfer(HANDLE pipe, void* bytes, DWORD length, DWORD& count, bool writing, std::uint64_t generation);
    std::wstring name_;
    std::mutex mutex_;
    HANDLE active_ = INVALID_HANDLE_VALUE;
    std::uint64_t generation_ = 0;
};
}
