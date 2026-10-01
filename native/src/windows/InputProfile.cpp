#include "InputProfile.h"
#include "ProfileIds.h"
#include <inputscope.h>
#include <algorithm>
#include <cwctype>
#include <functional>

namespace {
std::string utf8(const std::wstring& text) {
    int length = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, text.data(), static_cast<int>(text.size()), nullptr, 0, nullptr, nullptr);
    if (!length && !text.empty()) throw std::runtime_error("invalid_utf16");
    std::string bytes(length, '\0');
    WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, text.data(), static_cast<int>(text.size()), bytes.data(), length, nullptr, nullptr);
    return bytes;
}

std::u16string utf16(const std::wstring& text) {
    static_assert(sizeof(wchar_t) == sizeof(char16_t));
    return std::u16string(reinterpret_cast<const char16_t*>(text.data()), text.size());
}
}

namespace autopunct {
InputProfile::InputProfile(Ime::ImeModule* module) : Ime::TextService(module) {
    wchar_t path[32768];
    DWORD count = GetModuleFileNameW(nullptr, path, 32768);
    std::wstring name(path, count);
    name = name.substr(name.find_last_of(L"\\/") + 1);
    std::transform(name.begin(), name.end(), name.begin(), [](wchar_t character) { return std::towlower(character); });
    this->application_ = utf8(name);
}

InputProfile::~InputProfile() {
    this->onDeactivate();
}

void InputProfile::onActivate() {
    try {
        if (this->isConsole() || !this->Ime::Window::create(HWND_MESSAGE, 0)) return;
        this->pipe_ = std::make_unique<PipeConnection>();
        this->stopping_ = false;
        this->addPreservedKey(VK_BACK, TF_MOD_CONTROL | TF_MOD_ALT, UndoKey);
        this->setKeyboardOpen(true);
        this->worker_ = std::jthread([this](std::stop_token token) {
            while (!token.stop_requested()) {
                std::unique_lock lock(this->mutex_);
                this->condition_.wait(lock, [this, &token] { return this->pending_.has_value() || this->stopping_ || token.stop_requested(); });
                if (this->stopping_ || token.stop_requested()) return;
                Snapshot snapshot = std::move(*this->pending_);
                this->pending_.reset();
                lock.unlock();
                try {
                    nlohmann::json request = {
                        {"version", 1}, {"requestId", snapshot.requestId}, {"contextId", snapshot.contextId},
                        {"revision", snapshot.revision}, {"text", utf8(snapshot.text)},
                        {"caretOffset", snapshot.text.size()}, {"application", snapshot.application}
                    };
                    auto bytes = this->pipe_->exchange(request.dump());
                    if (!bytes) continue;
                    auto response = nlohmann::json::parse(*bytes);
                    lock.lock();
                    if (this->stopping_) return;
                    this->reply_ = std::make_pair(snapshot, std::move(response));
                    lock.unlock();
                    PostMessageW(this->Ime::Window::hwnd(), ReplyMessage, 0, 0);
                } catch (...) {
                    // Автокоррекция является необязательной: отказ транспорта оставляет обычный ввод.
                    OutputDebugStringW(L"AutoPunct: request skipped\n");
                }
            }
        });
        this->changeContext();
    } catch (...) {
        OutputDebugStringW(L"AutoPunct: activation unavailable\n");
        this->onDeactivate();
    }
}

void InputProfile::onDeactivate() {
    {
        std::lock_guard lock(this->mutex_);
        this->stopping_ = true;
        this->pending_.reset();
        this->reply_.reset();
    }
    this->worker_.request_stop();
    if (this->pipe_) this->pipe_->cancel();
    this->condition_.notify_all();
    if (this->worker_.joinable()) this->worker_.join();
    if (this->Ime::Window::isWindow()) {
        KillTimer(this->Ime::Window::hwnd(), 1);
        this->Ime::Window::destroy();
    }
    this->undo_.reset();
    this->start_ = nullptr;
    this->undoStart_ = nullptr;
    this->context_ = nullptr;
}

void InputProfile::changeContext() {
    auto current = this->currentContext();
    this->context_ = static_cast<ITfContext*>(current);
    GUID identity{};
    if (FAILED(CoCreateGuid(&identity))) {
        this->contextId_.clear();
        return;
    }
    wchar_t name[40];
    StringFromGUID2(identity, name, 40);
    this->contextId_ = utf8(name);
    ++this->revision_;
    this->undo_.reset();
    this->start_ = nullptr;
    this->undoStart_ = nullptr;
    KillTimer(this->Ime::Window::hwnd(), 1);
    {
        std::lock_guard lock(this->mutex_);
        this->pending_.reset();
        this->reply_.reset();
    }
    if (this->pipe_) this->pipe_->cancel();
}

void InputProfile::onSetFocus() { this->changeContext(); }
void InputProfile::onKillFocus() { this->changeContext(); }
bool InputProfile::filterKeyDown(Ime::KeyEvent&) { return false; }
bool InputProfile::filterKeyUp(Ime::KeyEvent&) { return false; }

STDMETHODIMP InputProfile::OnEndEdit(ITfContext* context, TfEditCookie cookie, ITfEditRecord* record) {
    HRESULT result = Ime::TextService::OnEndEdit(context, cookie, record);
    ++this->revision_;
    if (this->ownEdit_) {
        this->ownEdit_ = false;
        this->undoRevision_ = this->revision_;
        return result;
    }
    this->undo_.reset();
    {
        std::lock_guard lock(this->mutex_);
        this->pending_.reset();
        this->reply_.reset();
    }
    if (this->Ime::Window::isWindow()) SetTimer(this->Ime::Window::hwnd(), 1, 600, nullptr);
    return result;
}

void InputProfile::requestSession(DWORD flags, std::function<void(Ime::EditSession*, TfEditCookie)> callback) {
    auto context = this->currentContext();
    if (!context) return;
    Ime::ComPtr<InputProfile> owner(this);
    auto guarded = [owner, callback = std::move(callback)](Ime::EditSession* session, TfEditCookie cookie) {
        if (owner->stopping_) return;
        try {
            callback(session, cookie);
        } catch (...) {
            OutputDebugStringW(L"AutoPunct: edit session skipped\n");
        }
    };
    auto session = Ime::ComPtr<Ime::EditSession>::make(context, std::move(guarded));
    HRESULT accepted = E_FAIL;
    context->RequestEditSession(this->clientId(), session, TF_ES_ASYNC | flags, &accepted);
}

std::optional<Snapshot> InputProfile::read(ITfContext* context, TfEditCookie cookie) {
    if (!context || this->stopping_ || this->isConsole() || this->isKeyboardDisabled(context) || this->isComposing()) return std::nullopt;
    auto focused = this->currentContext();
    if (focused != context) return std::nullopt;
    if (this->context_ != context) this->changeContext();
    Ime::ComPtr<ITfContextView> view;
    HWND documentWindow = nullptr;
    if (FAILED(context->GetActiveView(&view)) || FAILED(view->GetWnd(&documentWindow)) || !documentWindow
        || GetAncestor(documentWindow, GA_ROOT) != GetAncestor(GetForegroundWindow(), GA_ROOT)) return std::nullopt;
    TF_SELECTION selection{};
    ULONG count = 0;
    if (FAILED(context->GetSelection(cookie, TF_DEFAULT_SELECTION, 1, &selection, &count)) || count != 1) return std::nullopt;
    auto caret = Ime::ComPtr<ITfRange>::takeover(std::move(selection.range));
    BOOL empty = FALSE;
    if (selection.style.fInterimChar || FAILED(caret->IsEmpty(cookie, &empty)) || !empty) return std::nullopt;
    TF_STATUS status{};
    if (FAILED(context->GetStatus(&status)) || (status.dwDynamicFlags & TF_SD_READONLY)) return std::nullopt;

    bool knownPlain = false;
    HWND focus = GetFocus();
    wchar_t className[256]{};
    GetClassNameW(focus, className, 256);
    std::wstring name(className);
    if (name == L"Edit" || name.starts_with(L"RichEdit") || name.starts_with(L"RICHEDIT")) {
        if (GetWindowLongPtrW(focus, GWL_STYLE) & ES_PASSWORD) return std::nullopt;
        knownPlain = true;
    }
    Ime::ComPtr<ITfProperty> scopeProperty;
    VARIANT value;
    VariantInit(&value);
    if (SUCCEEDED(context->GetProperty(GUID_PROP_INPUTSCOPE, &scopeProperty))
        && SUCCEEDED(scopeProperty->GetValue(cookie, caret, &value)) && value.vt == VT_UNKNOWN && value.punkVal) {
        Ime::ComPtr<ITfInputScope> scope;
        if (SUCCEEDED(value.punkVal->QueryInterface(IID_ITfInputScope, reinterpret_cast<void**>(&scope)))) {
            InputScope* scopes = nullptr;
            UINT scopeCount = 0;
            if (SUCCEEDED(scope->GetInputScopes(&scopes, &scopeCount))) {
                for (UINT index = 0; index < scopeCount; ++index) {
                    if (scopes[index] == IS_PASSWORD || scopes[index] == IS_NUMERIC_PASSWORD || scopes[index] == IS_NUMERIC_PIN) {
                        CoTaskMemFree(scopes);
                        VariantClear(&value);
                        return std::nullopt;
                    }
                    knownPlain = true;
                }
                CoTaskMemFree(scopes);
            }
        }
    }
    VariantClear(&value);
    if (!knownPlain) return std::nullopt;

    Ime::ComPtr<ITfRange> following;
    if (FAILED(caret->Clone(&following))) return std::nullopt;
    LONG shifted = 0;
    if (FAILED(following->ShiftEnd(cookie, 1, &shifted, nullptr))) return std::nullopt;
    wchar_t next = 0;
    ULONG nextCount = 0;
    if (FAILED(following->GetText(cookie, 0, &next, 1, &nextCount))) return std::nullopt;
    if (nextCount && next != L'\r' && next != L'\n') return std::nullopt;
    Ime::ComPtr<ITfRange> before;
    if (FAILED(caret->Clone(&before))) return std::nullopt;
    if (FAILED(before->ShiftStart(cookie, -513, &shifted, nullptr))) return std::nullopt;
    wchar_t buffer[513];
    ULONG length = 0;
    if (FAILED(before->GetText(cookie, 0, buffer, 513, &length)) || !length) return std::nullopt;
    std::wstring text(buffer, length);
    if (std::iswalnum(text.back())) return std::nullopt;
    std::size_t significant = text.find_last_not_of(L" \t");
    if (significant == std::wstring::npos) return std::nullopt;
    std::size_t scan = significant;
    if (std::wstring_view(L".!?\r\n").find(text[scan]) != std::wstring_view::npos && scan > 0) --scan;
    std::size_t boundary = text.find_last_of(L".!?\r\n", scan);
    std::size_t beginning = boundary == std::wstring::npos ? 0 : boundary + 1;
    if (text.size() == 513 && beginning == 0) return std::nullopt;
    if (beginning) {
        if (FAILED(before->ShiftStart(cookie, static_cast<LONG>(beginning), &shifted, nullptr))
            || shifted != static_cast<LONG>(beginning)) return std::nullopt;
        text.erase(0, beginning);
    }
    if (text.empty() || text.size() > 512 || (text.front() >= 0xDC00 && text.front() <= 0xDFFF)) return std::nullopt;
    this->start_ = before;
    return Snapshot{++this->requestId_, this->contextId_, this->revision_, text, this->application_, reinterpret_cast<std::uintptr_t>(GetFocus())};
}

void InputProfile::queue(const Snapshot& snapshot) {
    std::lock_guard lock(this->mutex_);
    if (this->stopping_) return;
    this->pending_ = snapshot;
    this->condition_.notify_one();
}

LRESULT InputProfile::wndProc(UINT message, WPARAM word, LPARAM data) {
    if (message == WM_TIMER && word == 1) {
        KillTimer(this->Ime::Window::hwnd(), 1);
        this->requestSession(TF_ES_READ, [this](Ime::EditSession* session, TfEditCookie cookie) {
            if (auto snapshot = this->read(session->context(), cookie)) this->queue(*snapshot);
        });
        return 0;
    }
    if (message == ReplyMessage) {
        std::optional<std::pair<Snapshot, nlohmann::json>> reply;
        {
            std::lock_guard lock(this->mutex_);
            reply.swap(this->reply_);
        }
        if (reply) this->apply(reply->first, reply->second);
        return 0;
    }
    return Ime::Window::wndProc(message, word, data);
}

void InputProfile::apply(const Snapshot& snapshot, const nlohmann::json& reply) {
    try {
        if (reply.at("version").get<int>() != 1 || reply.at("status").get<std::string>() != "ok"
            || reply.at("requestId").get<std::uint64_t>() != snapshot.requestId
            || reply.at("contextId").get<std::string>() != snapshot.contextId
            || reply.at("revision").get<std::uint64_t>() != snapshot.revision) return;
        auto offsets = reply.at("commas").get<std::vector<int>>();
        if (offsets.empty()) return;
        auto expected = std::make_shared<VerifiedEdit>(utf16(snapshot.text), offsets, snapshot.contextId, snapshot.revision, snapshot.window);
        this->requestSession(TF_ES_READWRITE, [this, snapshot, offsets, expected](Ime::EditSession* session, TfEditCookie cookie) {
            auto current = this->read(session->context(), cookie);
            if (!current || !expected->isCurrent(utf16(current->text), current->contextId, current->revision, current->window)) return;
            auto edit = std::make_unique<VerifiedEdit>(*expected);
            TF_SELECTION selection{};
            ULONG count = 0;
            if (FAILED(session->context()->GetSelection(cookie, TF_DEFAULT_SELECTION, 1, &selection, &count)) || count != 1) return;
            auto caret = Ime::ComPtr<ITfRange>::takeover(std::move(selection.range));
            if (FAILED(caret->SetGravity(cookie, TF_GRAVITY_FORWARD, TF_GRAVITY_FORWARD))) return;
            std::vector<Ime::ComPtr<ITfRange>> positions;
            positions.reserve(offsets.size());
            for (int offset : offsets) {
                Ime::ComPtr<ITfRange> position;
                if (FAILED(this->start_->Clone(&position)) || FAILED(position->Collapse(cookie, TF_ANCHOR_START))) return;
                LONG moved = 0;
                BOOL empty = FALSE;
                if (FAILED(position->ShiftEnd(cookie, offset, &moved, nullptr)) || moved != offset
                    || FAILED(position->Collapse(cookie, TF_ANCHOR_END))
                    || FAILED(position->IsEmpty(cookie, &empty)) || !empty) return;
                positions.push_back(position);
            }
            this->ownEdit_ = true;
            for (int index = static_cast<int>(positions.size()) - 1; index >= 0; --index) {
                if (FAILED(positions[index]->SetText(cookie, 0, L",", 1))) {
                    // Сохранённые диапазоны указывают только на вставленные собственные запятые.
                    for (std::size_t restored = index + 1; restored < positions.size(); ++restored) {
                        wchar_t characters[2]{};
                        ULONG length = 0;
                        if (SUCCEEDED(positions[restored]->GetText(cookie, 0, characters, 2, &length))
                            && length == 1 && characters[0] == L',') positions[restored]->SetText(cookie, 0, L"", 0);
                    }
                    this->undo_.reset();
                    return;
                }
            }
            selection.range = caret;
            if (FAILED(session->context()->SetSelection(cookie, 1, &selection))) {
                for (auto position = positions.rbegin(); position != positions.rend(); ++position) {
                    wchar_t characters[2]{};
                    ULONG length = 0;
                    if (SUCCEEDED((*position)->GetText(cookie, 0, characters, 2, &length))
                        && length == 1 && characters[0] == L',') (*position)->SetText(cookie, 0, L"", 0);
                }
                this->undo_.reset();
                return;
            }
            this->undoStart_ = this->start_;
            this->undoRevision_ = this->revision_;
            this->undoWindow_ = snapshot.window;
            this->undo_ = std::move(edit);
        });
    } catch (...) {
        OutputDebugStringW(L"AutoPunct: invalid proposal skipped\n");
    }
}

bool InputProfile::onPreservedKey(const GUID& guid) {
    if (!IsEqualGUID(guid, UndoKey) || !this->undo_ || this->undoRevision_ != this->revision_) return false;
    this->requestSession(TF_ES_READWRITE, [this](Ime::EditSession* session, TfEditCookie cookie) {
        if (!this->undo_ || this->undoRevision_ != this->revision_ || session->context() != this->context_
            || this->undoWindow_ != reinterpret_cast<std::uintptr_t>(GetFocus())) return;
        TF_SELECTION selection{};
        ULONG count = 0;
        if (FAILED(session->context()->GetSelection(cookie, TF_DEFAULT_SELECTION, 1, &selection, &count)) || count != 1) return;
        auto caret = Ime::ComPtr<ITfRange>::takeover(std::move(selection.range));
        BOOL empty = FALSE;
        if (FAILED(caret->IsEmpty(cookie, &empty)) || !empty) return;
        Ime::ComPtr<ITfRange> range;
        if (FAILED(this->undoStart_->Clone(&range))) return;
        if (FAILED(range->ShiftEndToRange(cookie, caret, TF_ANCHOR_END))) return;
        wchar_t buffer[1024];
        ULONG length = 0;
        if (FAILED(range->GetText(cookie, 0, buffer, 1024, &length)) || !this->undo_->canUndo(utf16(std::wstring(buffer, length)))) return;
        const auto offsets = this->undo_->offsets();
        std::vector<Ime::ComPtr<ITfRange>> positions;
        positions.reserve(offsets.size());
        for (std::size_t index = 0; index < offsets.size(); ++index) {
            Ime::ComPtr<ITfRange> position;
            if (FAILED(this->undoStart_->Clone(&position)) || FAILED(position->Collapse(cookie, TF_ANCHOR_START))) return;
            LONG shifted = 0;
            LONG offset = offsets[index] + static_cast<LONG>(index);
            if (FAILED(position->ShiftEnd(cookie, offset, &shifted, nullptr)) || shifted != offset
                || FAILED(position->Collapse(cookie, TF_ANCHOR_END))
                || FAILED(position->ShiftEnd(cookie, 1, &shifted, nullptr)) || shifted != 1) return;
            wchar_t character = 0;
            ULONG length = 0;
            if (FAILED(position->GetText(cookie, 0, &character, 1, &length)) || length != 1 || character != L',') return;
            positions.push_back(position);
        }
        this->ownEdit_ = true;
        for (int index = static_cast<int>(positions.size()) - 1; index >= 0; --index) {
            if (FAILED(positions[index]->SetText(cookie, 0, L"", 0))) {
                for (std::size_t restore = index + 1; restore < positions.size(); ++restore) {
                    BOOL empty = FALSE;
                    if (SUCCEEDED(positions[restore]->IsEmpty(cookie, &empty)) && empty) positions[restore]->SetText(cookie, 0, L",", 1);
                }
                this->undo_.reset();
                return;
            }
        }
        selection.range = caret;
        session->context()->SetSelection(cookie, 1, &selection);
        this->undo_.reset();
    });
    return true;
}
}
