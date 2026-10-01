package ru.autopunct.typing.commas.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AllowedInsertionsTest {
    private final AllowedInsertions policy = new AllowedInsertions();

    @Test
    void acceptsOnlyAddedCommasAndKeepsUtf16Offsets() {
        assertEquals(List.of(7), this.policy.between("😀 знаю что", "😀 знаю, что").orElseThrow());
    }

    @Test
    void preservesExistingPunctuationAndWhitespace() {
        assertEquals(List.of(2), this.policy.between("да  нет!", "да,  нет!").orElseThrow());
        assertTrue(this.policy.between("да, нет", "да нет,").isEmpty());
        assertTrue(this.policy.between("да  нет", "да, нет").isEmpty());
        assertTrue(this.policy.between("да нет", "Да, нет").isEmpty());
        assertTrue(this.policy.between("да нет", "да, нет.").isEmpty());
        assertTrue(this.policy.between("да нет", "да нет").isEmpty());
    }

    @Test
    void rejectsRepeatedAndEdgeCommas() {
        assertTrue(this.policy.between("да, нет", "да,, нет").isEmpty());
        assertTrue(this.policy.between("да", ",да").isEmpty());
        assertTrue(this.policy.between("да", "да,").isEmpty());
    }
}
