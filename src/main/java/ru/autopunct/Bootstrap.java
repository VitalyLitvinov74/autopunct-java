package ru.autopunct;

import an.awesome.pipelinr.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.autopunct.typing.commas.domain.AllowedInsertions;
import ru.autopunct.typing.commas.infrastructure.Grammar;
import ru.autopunct.typing.commas.presentation.SnapshotEndpoint;
import java.util.function.Function;
import java.util.prefs.Preferences;

@Configuration(proxyBeanMethods = false)
public class Bootstrap {
    @Bean
    public Preferences preferences(@Value("${autopunct.preferences-node:/ru/autopunct}") String node) {
        return Preferences.userRoot().node(node);
    }

    @Bean
    public AllowedInsertions allowedInsertions() {
        return new AllowedInsertions();
    }

    @Bean
    public Grammar grammar(AllowedInsertions allowedInsertions) {
        return new Grammar(allowedInsertions);
    }

    @Bean
    public Function<byte[], byte[]> snapshotRequests(SnapshotEndpoint endpoint) {
        return endpoint::respond;
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public Pipeline pipeline(ObjectProvider<Command.Handler> handlers) {
        return new Pipelinr().with((CommandHandlers) handlers::stream);
    }
}
