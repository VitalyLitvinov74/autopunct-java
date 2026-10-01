#include <windows.h>
#include <objbase.h>

int wmain(int count, wchar_t** arguments) {
    if (count != 2) return 1;
    HMODULE library = LoadLibraryW(arguments[1]);
    if (!library) return 2;
    bool valid = GetProcAddress(library, "DllGetClassObject") && GetProcAddress(library, "DllCanUnloadNow")
        && GetProcAddress(library, "DllRegisterServer") && GetProcAddress(library, "DllUnregisterServer");
    FreeLibrary(library);
    return valid ? 0 : 3;
}
