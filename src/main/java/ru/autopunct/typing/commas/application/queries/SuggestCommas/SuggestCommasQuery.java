package ru.autopunct.typing.commas.application.queries.SuggestCommas;
import an.awesome.pipelinr.Command;
public record SuggestCommasQuery(String text, String application) implements Command<SuggestCommasResult> {}
