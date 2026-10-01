#include "Module.h"
#include "InputProfile.h"
#include "ProfileIds.h"

namespace autopunct {
Module::Module(HMODULE handle) : Ime::ImeModule(handle, ProfileClass, {}) {}
Ime::TextService* Module::createTextService() {
    return new InputProfile(this);
}
}
