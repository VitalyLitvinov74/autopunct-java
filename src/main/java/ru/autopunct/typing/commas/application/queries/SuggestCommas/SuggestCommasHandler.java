package ru.autopunct.typing.commas.application.queries.SuggestCommas;

import an.awesome.pipelinr.Command;
import an.awesome.pipelinr.Pipeline;
import org.springframework.stereotype.Component;
import ru.autopunct.typing.assistance.application.queries.Permission.PermissionQuery;
import ru.autopunct.typing.commas.infrastructure.Grammar;
import java.io.IOException;
import java.util.List;

@Component
public final class SuggestCommasHandler implements Command.Handler<SuggestCommasQuery, SuggestCommasResult> {
    private final Pipeline pipeline;
    private final Grammar grammar;

    public SuggestCommasHandler(Pipeline pipeline, Grammar grammar) {
        this.pipeline = pipeline;
        this.grammar = grammar;
    }

    @Override
    public SuggestCommasResult handle(SuggestCommasQuery query) {
        if (!this.pipeline.send(new PermissionQuery(query.application()))) {
            return new SuggestCommasResult(false, List.of());
        }
        try {
            return new SuggestCommasResult(true, this.grammar.commas(query.text()));
        } catch (IOException failure) {
            throw new IllegalStateException("grammar_failed", failure);
        }
    }
}
