package ru.autopunct.typing.assistance.domain;

import ru.autopunct.typing.assistance.domain.events.ProfileChangedEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Управляет паузой и исключениями текущего пользователя; изменения входят только через этот корень. */
public final class Profile {
    private boolean paused;
    private Set<String> excludedApplications = new HashSet<>(Set.of(
        "cmd.exe", "powershell.exe", "pwsh.exe", "windowsterminal.exe", "code.exe",
        "idea64.exe", "pycharm64.exe", "devenv.exe", "notepad++.exe"
    ));
    private transient List<ProfileChangedEvent> events = new ArrayList<>();

    public void switchPause() {
        this.paused = !this.paused;
        this.events.add(new ProfileChangedEvent());
    }

    public void exclude(String application, boolean excluded) {
        if (application == null || !application.endsWith(".exe") || application.contains("/") || application.contains("\\")) {
            throw new IllegalArgumentException("application_must_be_executable_name");
        }
        boolean changed = excluded
            ? this.excludedApplications.add(application)
            : this.excludedApplications.remove(application);
        if (changed) {
            this.events.add(new ProfileChangedEvent());
        }
    }

    public boolean permits(String application) {
        return !this.paused && !this.excludedApplications.contains(application);
    }

    public List<ProfileChangedEvent> releaseEvents() {
        var released = List.copyOf(this.events);
        this.events.clear();
        return released;
    }
}
