#include "Module.h"
#include "ProfileIds.h"
#include <msctf.h>
#include <mutex>

namespace {
HMODULE image = nullptr;
std::mutex factoryMutex;
Ime::ComPtr<autopunct::Module> module;

autopunct::Module& factory() {
    std::lock_guard lock(factoryMutex);
    if (!module) {
        module = Ime::ComPtr<autopunct::Module>::make(image);
    }
    return *module;
}
}

BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) {
        image = instance;
        DisableThreadLibraryCalls(instance);
    }
    return TRUE;
}

extern "C" HRESULT STDAPICALLTYPE DllCanUnloadNow() {
    std::lock_guard lock(factoryMutex);
    return module ? module->canUnloadNow() : S_OK;
}

extern "C" HRESULT STDAPICALLTYPE DllGetClassObject(REFCLSID clsid, REFIID iid, void** value) {
    if (!IsEqualGUID(clsid, autopunct::ProfileClass)) {
        *value = nullptr;
        return CLASS_E_CLASSNOTAVAILABLE;
    }
    try {
        return factory().getClassObject(clsid, iid, value);
    } catch (...) {
        return E_FAIL;
    }
}

extern "C" HRESULT STDAPICALLTYPE DllRegisterServer() {
    HRESULT initialized = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    Ime::ComPtr<ITfInputProcessorProfiles> profiles;
    HRESULT result = CoCreateInstance(CLSID_TF_InputProcessorProfiles, nullptr, CLSCTX_INPROC_SERVER, IID_ITfInputProcessorProfiles, reinterpret_cast<void**>(&profiles));
    if (SUCCEEDED(result)) {
        result = profiles->Register(autopunct::ProfileClass);
    }
    if (SUCCEEDED(result)) {
        const wchar_t* name = L"Автозапятые";
        result = profiles->AddLanguageProfile(autopunct::ProfileClass, 0x0419, autopunct::RussianProfile, name, 11, nullptr, 0, 0);
    }
    if (SUCCEEDED(result)) {
        result = profiles->SubstituteKeyboardLayout(autopunct::ProfileClass, 0x0419, autopunct::RussianProfile, reinterpret_cast<HKL>(0x04190419));
    }
    if (SUCCEEDED(result)) {
        result = profiles->EnableLanguageProfile(autopunct::ProfileClass, 0x0419, autopunct::RussianProfile, TRUE);
    }
    Ime::ComPtr<ITfCategoryMgr> categories;
    if (SUCCEEDED(result)) {
        result = CoCreateInstance(CLSID_TF_CategoryMgr, nullptr, CLSCTX_INPROC_SERVER, IID_ITfCategoryMgr, reinterpret_cast<void**>(&categories));
    }
    if (SUCCEEDED(result)) {
        result = categories->RegisterCategory(autopunct::ProfileClass, GUID_TFCAT_TIP_KEYBOARD, autopunct::ProfileClass);
    }
    if (SUCCEEDED(initialized)) {
        CoUninitialize();
    }
    return result;
}

extern "C" HRESULT STDAPICALLTYPE DllUnregisterServer() {
    HRESULT initialized = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    Ime::ComPtr<ITfInputProcessorProfiles> profiles;
    HRESULT result = CoCreateInstance(CLSID_TF_InputProcessorProfiles, nullptr, CLSCTX_INPROC_SERVER, IID_ITfInputProcessorProfiles, reinterpret_cast<void**>(&profiles));
    if (SUCCEEDED(result)) {
        result = profiles->Unregister(autopunct::ProfileClass);
    }
    Ime::ComPtr<ITfCategoryMgr> categories;
    if (SUCCEEDED(CoCreateInstance(CLSID_TF_CategoryMgr, nullptr, CLSCTX_INPROC_SERVER, IID_ITfCategoryMgr, reinterpret_cast<void**>(&categories)))) {
        categories->UnregisterCategory(autopunct::ProfileClass, GUID_TFCAT_TIP_KEYBOARD, autopunct::ProfileClass);
    }
    if (SUCCEEDED(initialized)) {
        CoUninitialize();
    }
    return result;
}
