package ru.autopunct.windows.infrastructure;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.W32APIOptions;

/** Использует штатное преобразование SDDL для доступа только текущего пользователя. */
public interface SecurityApi extends Advapi32 {
    SecurityApi INSTANCE = Native.load("advapi32", SecurityApi.class, W32APIOptions.UNICODE_OPTIONS);
    boolean ConvertStringSecurityDescriptorToSecurityDescriptor(String sddl, int revision, PointerByReference descriptor, IntByReference length);
}
