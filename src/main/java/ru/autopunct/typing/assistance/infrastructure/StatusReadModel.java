package ru.autopunct.typing.assistance.infrastructure;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;
import ru.autopunct.typing.assistance.application.queries.Status.StatusResult;
import ru.autopunct.windows.infrastructure.Desktop;
import java.io.IOException;
import java.util.prefs.Preferences;

/** Читает сохранённую форму профиля для трея, не раскрывая состояние доменного корня. */
@Component
public final class StatusReadModel {
    private final Preferences preferences;
    private final Desktop desktop;
    private final JsonMapper json = new JsonMapper();
    public StatusReadModel(Preferences preferences, Desktop desktop) {
        this.preferences = preferences;
        this.desktop = desktop;
    }
    public StatusResult forApplication(String application) throws IOException {
        var stored = this.json.readTree(this.preferences.get("profile", null));
        boolean excluded = false;
        for (var name : stored.get("excludedApplications")) {
            if (name.asText().equals(application)) {
                excluded = true;
            }
        }
        return new StatusResult(stored.get("paused").booleanValue(), excluded, this.desktop.autoStartEnabled());
    }
}
