package ru.autopunct.typing.commas.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.autopunct.typing.commas.domain.AllowedInsertions;
import static org.junit.jupiter.api.Assertions.*;

class GrammarTest {
    private final Grammar grammar = new Grammar(new AllowedInsertions());

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Известно что на улице красивый вид. | Известно, что на улице красивый вид.",
        "Не по дням а по часам. | Не по дням, а по часам.",
        "Это те же ребята которые играли в хоккей. | Это те же ребята, которые играли в хоккей.",
        "Это тот город в котором прошло детство. | Это тот город, в котором прошло детство.",
        "К Тамбову подступали две с половиной тысячи сабель то есть всего три полка. | К Тамбову подступали две с половиной тысячи сабель, то есть всего три полка.",
        "Я вышел на крыльцо чтобы освежиться. | Я вышел на крыльцо, чтобы освежиться."
    })
    void usesRealRussianRulesWithoutChangingAnythingElse(String before, String expected) throws Exception {
        var offsets = this.grammar.commas(before);
        var actual = new StringBuilder(before);
        for (int i = offsets.size() - 1; i >= 0; i--) {
            actual.insert(offsets.get(i).intValue(), ',');
        }
        assertEquals(expected, actual.toString());
        assertTrue(this.grammar.commas(expected).isEmpty());
    }

    @ParameterizedTest
    @CsvSource(value = {
        "Я купил хлеб и молоко.",
        "Он сказал что-то странное.",
        "Во что бы то ни стало.",
        "Не то чтобы я был против.",
        "Я работаю как переводчик.",
        "Говорите что угодно.",
        "Что делать?",
        "На то есть веские причины.",
        "Привет мир"
    })
    void leavesAmbiguousAndUnsupportedCasesAlone(String text) throws Exception {
        assertTrue(this.grammar.commas(text).isEmpty());
    }

    @Test
    void analyzesUnpunctuatedCompletedWordsWithoutAddingPeriods() throws Exception {
        assertFalse(this.grammar.commas("Известно что на улице красивый вид ").isEmpty());
        assertTrue(this.grammar.commas("   ").isEmpty());
    }
}
