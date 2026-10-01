#pragma once
#include <ImeModule.h>

namespace autopunct {
// Фабрика COM наследует готовую реализацию LibIME2.
class Module final : public Ime::ImeModule {
public:
    explicit Module(HMODULE handle);
    Ime::TextService* createTextService() override;
};
}
