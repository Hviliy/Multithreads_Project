package ru.kopylov.multithreads.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder b) {
        return b.additionalInterceptors((req, body, ex) ->
                {req.getHeaders().add(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (compatible; MultithreadsParser/1.0)");
            return ex.execute(req, body);
        }).build();
    }
}