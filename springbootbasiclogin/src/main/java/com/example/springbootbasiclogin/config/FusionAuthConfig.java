package com.example.springbootbasiclogin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class FusionAuthConfig {

    @Bean(name = "fusionAuthWebClient")
    public WebClient fusionAuthWebClient(ApplicationPropertiesConfig applicationProperties) {
        return WebClient.builder()
                .baseUrl(applicationProperties.getAuth().getFusionauth().getBaseUrl())
                .build();
    }
}
