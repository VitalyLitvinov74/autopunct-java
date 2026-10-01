#include "VerifiedEdit.h"
#include <stdexcept>
#include <utility>

namespace autopunct {
VerifiedEdit::VerifiedEdit(std::u16string original, std::vector<int> offsets, std::string context, std::uint64_t revision, std::uintptr_t window)
    : original_(std::move(original)), offsets_(std::move(offsets)), context_(std::move(context)), revision_(revision), window_(window) {
    if (this->original_.size() > 512 || this->offsets_.empty()) {
        throw std::invalid_argument("invalid_edit");
    }
    int previous = -1;
    for (int position : this->offsets_) {
        if (position <= previous || position <= 0 || position >= static_cast<int>(this->original_.size())
            || this->original_[position - 1] == u',' || this->original_[position] == u','
            || (this->original_[position] >= 0xDC00 && this->original_[position] <= 0xDFFF)) {
            throw std::invalid_argument("invalid_comma_offset");
        }
        previous = position;
    }
}

std::u16string VerifiedEdit::replacement() const {
    std::u16string result = this->original_;
    for (auto position = this->offsets_.rbegin(); position != this->offsets_.rend(); ++position) {
        result.insert(static_cast<std::size_t>(*position), 1, u',');
    }
    return result;
}

bool VerifiedEdit::canUndo(const std::u16string& text) const {
    return text == this->replacement();
}

bool VerifiedEdit::isCurrent(const std::u16string& text, const std::string& context, std::uint64_t revision, std::uintptr_t window) const {
    return text == this->original_ && context == this->context_ && revision == this->revision_ && window == this->window_;
}

const std::vector<int>& VerifiedEdit::offsets() const {
    return this->offsets_;
}
}
