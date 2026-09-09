package com.theron.wallet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI walletServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Theron Wallet Service API")
                        .description("""
                                Digital wallet and payment API for Theron Group.
                                Supports product user JWT (login/refresh) and B2B OAuth 2.0 Client Credentials.
                                Obtain M2M tokens via POST /api/v1/oauth/token. See docs/ifriend/ for partner integration.
                                """)
                        .version("v1.1.0")
                        .contact(new Contact()
                                .name("Theron Group Engineering")
                                .email("engineering@therongroup.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local"),
                        new Server().url("https://api.therongroup.com/wallet").description("Production")
                ))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("User access token or OAuth client access token")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
