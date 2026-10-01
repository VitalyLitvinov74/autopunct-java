package ru.autopunct;

import an.awesome.pipelinr.Pipeline;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import ru.autopunct.typing.assistance.application.commands.SwitchPause.SwitchPauseCommand;
import ru.autopunct.typing.assistance.application.commands.ExcludeApplication.ExcludeApplicationCommand;
import ru.autopunct.typing.assistance.infrastructure.ProfileRepository;
import ru.autopunct.typing.commas.application.queries.SuggestCommas.SuggestCommasQuery;
import java.util.UUID;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"autopunct.pipe.enabled=false", "autopunct.tray.enabled=false", "autopunct.preferences-node=/autopunct-tests/integration"})
@DirtiesContext
class CqrsIntegrationTest {
    @Autowired
    private Pipeline pipeline;

    @Test
    void registeredCommandsChangeTheNextRealGrammarQuery() {
        var query = new SuggestCommasQuery("Известно что на улице красивый вид ", "notepad.exe");
        assertFalse(this.pipeline.send(query).commas().isEmpty());
        this.pipeline.send(new SwitchPauseCommand());
        assertFalse(this.pipeline.send(query).permitted());
        this.pipeline.send(new SwitchPauseCommand());
        this.pipeline.send(new ExcludeApplicationCommand("notepad.exe", true));
        assertFalse(this.pipeline.send(query).permitted());
        this.pipeline.send(new ExcludeApplicationCommand("notepad.exe", false));
        assertFalse(this.pipeline.send(query).commas().isEmpty());
    }

    @Test
    void profileRoundtripKeepsDecisionsWithoutPersistingPendingEvents() throws Exception {
        var storage = Preferences.userRoot().node("autopunct-tests/" + UUID.randomUUID());
        try {
            var repository = new ProfileRepository(storage);
            var profile = repository.current();
            profile.exclude("telegram.exe", true);
            repository.save(profile);
            var restored = new ProfileRepository(storage).current();
            assertFalse(restored.permits("telegram.exe"));
            assertTrue(restored.releaseEvents().isEmpty());
        } finally {
            storage.removeNode();
        }
    }
}
