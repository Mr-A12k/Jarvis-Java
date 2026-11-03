package com.jarvis.config;

import java.net.http.HttpClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import io.netty.channel.ChannelOption;

@Configuration
public class ModelConfiguration {
    @Bean
    WebClient ollamaWebClient(JarvisProperties p) { return webBuilder(p).baseUrl(p.ollamaUrl().toString()).build(); }

    private WebClient.Builder webBuilder(JarvisProperties p) {
        var http = reactor.netty.http.client.HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int)p.connectTimeout().toMillis())
                .responseTimeout(p.responseTimeout());
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(http))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(65536));
    }

    @Bean
    OllamaChatModel chatModel(JarvisProperties p) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(p.connectTimeout()).build());
        factory.setReadTimeout(p.responseTimeout());
        var api = OllamaApi.builder().baseUrl(p.ollamaUrl().toString())
                .restClientBuilder(RestClient.builder().requestFactory(factory))
                .webClientBuilder(webBuilder(p)).build();
        var options = OllamaChatOptions.builder().model(p.chatModel()).numCtx(p.contextLength())
                .numPredict(p.outputTokens()).temperature(0.7).repeatPenalty(1.15).repeatLastN(64)
                .keepAlive(p.keepAlive()).disableThinking().internalToolExecutionEnabled(false).build();
        return OllamaChatModel.builder().ollamaApi(api).defaultOptions(options).build();
    }
}
