package ru.autopunct.windows.infrastructure;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.Locale;

/** Владеет доступом к текущему сеансу Windows и записи автозапуска установленного приложения. */
@Component
public final class Desktop {
    private volatile String recentApplication = "";
    private final String runKey = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    public String pipeName() {
        if (!Platform.isWindows()) {
            throw new IllegalStateException("windows_required");
        }
        var token = new WinNT.HANDLEByReference();
        if (!Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(), WinNT.TOKEN_QUERY, token)) {
            throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
        }
        try {
            String sid = Advapi32Util.getTokenAccount(token.getValue()).sidString;
            var session = new IntByReference();
            if (!Kernel32.INSTANCE.ProcessIdToSessionId(Kernel32.INSTANCE.GetCurrentProcessId(), session)) {
                throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
            }
            return "\\\\.\\pipe\\AutoPunct-" + sid + "-" + session.getValue();
        } finally {
            Kernel32.INSTANCE.CloseHandle(token.getValue());
        }
    }

    public void rememberApplication(String application) {
        this.recentApplication = application;
    }

    public String foregroundApplication() {
        if (!Platform.isWindows()) {
            return "";
        }
        var window = User32.INSTANCE.GetForegroundWindow();
        var processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(window, processId);
        var process = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, processId.getValue());
        if (process == null) {
            return this.recentApplication;
        }
        try {
            char[] path = new char[32768];
            var length = new IntByReference(path.length);
            if (!Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, path, length)) {
                return this.recentApplication;
            }
            String name = Path.of(new String(path, 0, length.getValue())).getFileName().toString().toLowerCase(Locale.ROOT);
            return name.equals("autopunct.exe") || name.equals("java.exe") || name.equals("javaw.exe")
                ? this.recentApplication : name;
        } finally {
            Kernel32.INSTANCE.CloseHandle(process);
        }
    }

    public boolean autoStartEnabled() {
        return Platform.isWindows() && Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, this.runKey, "AutoPunct");
    }

    public void autoStart(boolean enabled) {
        if (!Platform.isWindows()) {
            throw new IllegalStateException("windows_required");
        }
        if (enabled) {
            String executable = ProcessHandle.current().info().command().orElseThrow();
            if (!Path.of(executable).getFileName().toString().equalsIgnoreCase("AutoPunct.exe")) {
                throw new IllegalStateException("installed_launcher_required");
            }
            Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, this.runKey);
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, this.runKey, "AutoPunct", "\"" + executable + "\"");
        } else if (this.autoStartEnabled()) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, this.runKey, "AutoPunct");
        }
    }
}
