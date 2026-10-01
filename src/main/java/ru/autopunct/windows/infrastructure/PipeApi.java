package ru.autopunct.windows.infrastructure;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

/** Дополняет JNA тремя штатными функциями Windows, отсутствующими в готовом отображении 5.17. */
public interface PipeApi extends Kernel32 {
    PipeApi INSTANCE = Native.load("kernel32", PipeApi.class, W32APIOptions.UNICODE_OPTIONS);
    boolean CancelIoEx(WinNT.HANDLE handle, Pointer operation);
    boolean GetOverlappedResult(WinNT.HANDLE handle, Pointer operation, IntByReference transferred, boolean wait);
    boolean ConnectNamedPipe(WinNT.HANDLE handle, Pointer operation);
    boolean ReadFile(WinNT.HANDLE handle, Pointer buffer, int size, IntByReference transferred, Pointer operation);
    boolean WriteFile(WinNT.HANDLE handle, Pointer buffer, int size, IntByReference transferred, Pointer operation);
}
