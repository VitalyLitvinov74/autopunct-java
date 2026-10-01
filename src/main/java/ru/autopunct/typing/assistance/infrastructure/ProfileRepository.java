package ru.autopunct.typing.assistance.infrastructure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;
import ru.autopunct.typing.assistance.domain.Profile;
import java.io.IOException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Сохраняет корень профиля целиком через стандартное отображение полей Jackson. */
@Component
public final class ProfileRepository {
    private final Preferences preferences;
    private volatile String saved;
    private final JsonMapper storage = JsonMapper.builder()
        .visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
        .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
        .build();

    public ProfileRepository(Preferences preferences) throws IOException, BackingStoreException {
        this.preferences = preferences;
        this.saved = this.preferences.get("profile", null);
        if (this.saved == null) {
            this.save(new Profile());
        }
    }

    public synchronized Profile current() throws IOException {
        return this.storage.readValue(this.saved, Profile.class);
    }

    public synchronized void save(Profile profile) throws IOException, BackingStoreException {
        String previous = this.saved;
        String next = this.storage.writeValueAsString(profile);
        this.preferences.put("profile", next);
        try {
            this.preferences.flush();
            this.saved = next;
        } catch (BackingStoreException failure) {
            if (previous == null) {
                this.preferences.remove("profile");
            } else {
                this.preferences.put("profile", previous);
            }
            throw failure;
        }
    }
}
