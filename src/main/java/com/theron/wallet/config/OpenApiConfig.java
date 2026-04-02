package com.theron.wallet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI walletServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Theron Wallet Service API")
                        .description("Digital wallet and payment integration with Asaas for Theron Group")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Theron Group Engineering")
                                .email("engineering@therongroup.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local"),
                        new Server().url("https://api.therongroup.com/wallet").description("Production")
                ));
    }
}
