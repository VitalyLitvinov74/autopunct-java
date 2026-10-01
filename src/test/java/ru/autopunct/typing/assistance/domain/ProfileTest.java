package ru.autopunct.typing.assistance.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileTest {
    @Test
    void invalidApplicationDoesNotChangeProfileOrProduceAnEvent() {
        var profile = new Profile();
        assertThrows(IllegalArgumentException.class, () -> profile.exclude("folder/app.exe", true));
        assertThrows(IllegalArgumentException.class, () -> profile.exclude("", true));
        assertTrue(profile.permits("notepad.exe"));
        assertTrue(profile.releaseEvents().isEmpty());
    }

    @Test
    void pauseAndApplicationExclusionsAreOwnedByTheProfile() {
        var profile = new Profile();
        assertTrue(profile.permits("notepad.exe"));
        assertFalse(profile.permits("cmd.exe"));
        profile.switchPause();
        assertFalse(profile.permits("notepad.exe"));
        profile.switchPause();
        profile.exclude("notepad.exe", true);
        profile.exclude("notepad.exe", true);
        assertFalse(profile.permits("notepad.exe"));
        assertEquals(3, profile.releaseEvents().size());
        assertTrue(profile.releaseEvents().isEmpty());
        profile.exclude("notepad.exe", false);
        assertTrue(profile.permits("notepad.exe"));
    }
}
