#pragma once
#include <string>
#include <vector>
#include <cstdint>

namespace autopunct {
// Защищает исходный текст и обратимость пакета вставок на границе Windows.
class VerifiedEdit {
public:
    VerifiedEdit(std::u16string original, std::vector<int> offsets, std::string context = "", std::uint64_t revision = 0, std::uintptr_t window = 0);
    std::u16string replacement() const;
    bool isCurrent(const std::u16string& text, const std::string& context, std::uint64_t revision, std::uintptr_t window) const;
    bool canUndo(const std::u16string& text) const;
    const std::vector<int>& offsets() const;
private:
    std::u16string original_;
    std::vector<int> offsets_;
    std::string context_;
    std::uint64_t revision_;
    std::uintptr_t window_;
};
}
