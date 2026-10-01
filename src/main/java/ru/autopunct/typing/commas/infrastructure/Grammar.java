package ru.autopunct.typing.commas.infrastructure;

import org.languagetool.JLanguageTool;
import org.languagetool.Languages;
import ru.autopunct.typing.commas.domain.AllowedInsertions;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Владеет локальным экземпляром LanguageTool и выдаёт только проверенные вставки запятых. */
public final class Grammar {
    private final JLanguageTool languageTool;
    private final AllowedInsertions allowedInsertions;
    private final Set<String> rules = Set.of(
        "CHTO_COMMA",
        "A_NO_DA",
        "PUNKT_KOTORIJ",
        "PUNKT_KOTORIJ1",
        "comma_and_to_jest",
        "PUNCT_VB_INF_OBOROT"
    );

    public Grammar(AllowedInsertions allowedInsertions) {
        this.allowedInsertions = allowedInsertions;
        this.languageTool = new JLanguageTool(Languages.getLanguageForShortCode("ru"));
        for (var rule : this.languageTool.getAllRules()) {
            this.languageTool.disableRule(rule.getId());
        }
        for (String ruleId : this.rules) {
            this.languageTool.enableRule(ruleId);
        }
    }

    public synchronized List<Integer> commas(String text) throws IOException {
        if (text.isBlank()) {
            return List.of();
        }
        var offsets = new TreeSet<Integer>();
        for (var match : this.languageTool.check(text)) {
            if (!this.rules.contains(match.getRule().getId()) || match.getSuggestedReplacements().size() != 1) {
                continue;
            }
            String original = text.substring(match.getFromPos(), match.getToPos());
            var insertions = this.allowedInsertions.between(original, match.getSuggestedReplacements().get(0));
            if (insertions.isPresent()) {
                for (int offset : insertions.get()) {
                    offsets.add(match.getFromPos() + offset);
                }
            }
        }
        return List.copyOf(offsets);
    }
}
