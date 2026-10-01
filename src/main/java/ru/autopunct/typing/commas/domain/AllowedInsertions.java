package ru.autopunct.typing.commas.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Разрешает только добавление запятых между словами, сохраняя исходный текст посимвольно. */
public final class AllowedInsertions {
    public Optional<List<Integer>> between(String original, String replacement) {
        var offsets = new ArrayList<Integer>();
        int source = 0;
        for (int target = 0; target < replacement.length(); target++) {
            char next = replacement.charAt(target);
            if (source < original.length() && next == original.charAt(source)) {
                source++;
                continue;
            }
            if (next != ',' || source == 0 || source >= original.length()
                    || original.charAt(source - 1) == ',' || original.charAt(source) == ','
                    || offsets.contains(source)) {
                return Optional.empty();
            }
            offsets.add(source);
        }
        if (source != original.length() || offsets.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(offsets));
    }
}
