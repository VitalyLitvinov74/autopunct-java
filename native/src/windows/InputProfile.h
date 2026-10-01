#pragma once
#include <TextService.h>
#include <Window.h>
#include <nlohmann/json.hpp>
#include "PipeConnection.h"
#include "Snapshot.h"
#include "typing/commas/infrastructure/VerifiedEdit.h"
#include <condition_variable>
#include <memory>
#include <mutex>
#include <optional>
#include <thread>

namespace autopunct {
// Владеет сеансом TSF, актуальностью снимков и обратимостью собственной последней правки.
class InputProfile final : public Ime::TextService, private Ime::Window {
public:
    explicit InputProfile(Ime::ImeModule* module);
    void onActivate() override;
    void onDeactivate() override;
    void onSetFocus() override;
    void onKillFocus() override;
    bool filterKeyDown(Ime::KeyEvent&) override;
    bool filterKeyUp(Ime::KeyEvent&) override;
    bool onPreservedKey(const GUID& guid) override;
    STDMETHODIMP OnEndEdit(ITfContext* context, TfEditCookie cookie, ITfEditRecord* record) override;
protected:
    ~InputProfile() override;
    LRESULT wndProc(UINT message, WPARAM word, LPARAM data) override;
private:
    void changeContext();
    void requestSession(DWORD flags, std::function<void(Ime::EditSession*, TfEditCookie)> callback);
    std::optional<Snapshot> read(ITfContext* context, TfEditCookie cookie);
    void queue(const Snapshot& snapshot);
    void apply(const Snapshot& snapshot, const nlohmann::json& reply);
    std::unique_ptr<PipeConnection> pipe_;
    std::jthread worker_;
    std::condition_variable condition_;
    std::mutex mutex_;
    std::optional<Snapshot> pending_;
    std::optional<std::pair<Snapshot, nlohmann::json>> reply_;
    bool stopping_ = true;
    bool ownEdit_ = false;
    std::uint64_t revision_ = 0;
    std::uint64_t requestId_ = 0;
    std::string contextId_;
    std::string application_;
    Ime::ComPtr<ITfContext> context_;
    Ime::ComPtr<ITfRange> start_;
    Ime::ComPtr<ITfRange> undoStart_;
    std::unique_ptr<VerifiedEdit> undo_;
    std::uint64_t undoRevision_ = 0;
    std::uintptr_t undoWindow_ = 0;
};
}
