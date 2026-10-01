package ru.autopunct.typing.commas.application.queries.SuggestCommas;
import java.util.List;
/** Описывает разрешённые позиции запятых без изменения документа Windows. */
public record SuggestCommasResult(boolean permitted, List<Integer> commas) {}
