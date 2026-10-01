#include "PipeConnection.h"
#include <sddl.h>
#include <stdexcept>
#include <vector>

namespace autopunct {
PipeConnection::PipeConnection() {
    HANDLE token = nullptr;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) {
        throw std::runtime_error("token_unavailable");
    }
    DWORD size = 0;
    GetTokenInformation(token, TokenUser, nullptr, 0, &size);
    std::vector<unsigned char> bytes(size);
    bool success = GetTokenInformation(token, TokenUser, bytes.data(), size, &size) != FALSE;
    CloseHandle(token);
    if (!success) {
        throw std::runtime_error("identity_unavailable");
    }
    LPWSTR sid = nullptr;
    if (!ConvertSidToStringSidW(reinterpret_cast<TOKEN_USER*>(bytes.data())->User.Sid, &sid)) {
        throw std::runtime_error("sid_unavailable");
    }
    DWORD session = 0;
    success = ProcessIdToSessionId(GetCurrentProcessId(), &session) != FALSE;
    this->name_ = std::wstring(L"\\\\.\\pipe\\AutoPunct-") + sid + L"-" + std::to_wstring(session);
    LocalFree(sid);
    if (!success) {
        throw std::runtime_error("session_unavailable");
    }
}

bool PipeConnection::transfer(HANDLE pipe, void* bytes, DWORD length, DWORD& count, bool writing, std::uint64_t generation) {
    OVERLAPPED operation{};
    operation.hEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (!operation.hEvent) {
        return false;
    }
    std::unique_lock lock(this->mutex_);
    if (generation != this->generation_) {
        CloseHandle(operation.hEvent);
        return false;
    }
    BOOL complete = writing
        ? WriteFile(pipe, bytes, length, &count, &operation)
        : ReadFile(pipe, bytes, length, &count, &operation);
    DWORD error = complete ? ERROR_SUCCESS : GetLastError();
    lock.unlock();
    if (!complete && error == ERROR_IO_PENDING) {
        DWORD waited = WaitForSingleObject(operation.hEvent, 1000);
        if (waited == WAIT_OBJECT_0) {
            complete = GetOverlappedResult(pipe, &operation, &count, FALSE);
        } else {
            CancelIoEx(pipe, &operation);
            GetOverlappedResult(pipe, &operation, &count, TRUE);
        }
    }
    CloseHandle(operation.hEvent);
    return complete != FALSE;
}

std::optional<std::string> PipeConnection::exchange(const std::string& request) {
    if (request.size() > 8192) {
        return std::nullopt;
    }
    std::uint64_t generation;
    {
        std::lock_guard lock(this->mutex_);
        generation = this->generation_;
    }
    HANDLE pipe = CreateFileW(
        this->name_.c_str(),
        GENERIC_READ | GENERIC_WRITE,
        0,
        nullptr,
        OPEN_EXISTING,
        FILE_FLAG_OVERLAPPED,
        nullptr
    );
    if (pipe == INVALID_HANDLE_VALUE) {
        return std::nullopt;
    }
    {
        std::lock_guard lock(this->mutex_);
        this->active_ = pipe;
    }
    DWORD mode = PIPE_READMODE_MESSAGE;
    DWORD count = 0;
    std::optional<std::string> result;
    if (SetNamedPipeHandleState(pipe, &mode, nullptr, nullptr)
        && this->transfer(pipe, const_cast<char*>(request.data()), static_cast<DWORD>(request.size()), count, true, generation)
        && count == request.size()) {
        char response[8192];
        if (this->transfer(pipe, response, sizeof(response), count, false, generation)) {
            result = std::string(response, count);
        }
    }
    {
        std::lock_guard lock(this->mutex_);
        this->active_ = INVALID_HANDLE_VALUE;
        CloseHandle(pipe);
    }
    return result;
}

void PipeConnection::cancel() {
    std::lock_guard lock(this->mutex_);
    ++this->generation_;
    if (this->active_ != INVALID_HANDLE_VALUE) {
        CancelIoEx(this->active_, nullptr);
    }
}
}
