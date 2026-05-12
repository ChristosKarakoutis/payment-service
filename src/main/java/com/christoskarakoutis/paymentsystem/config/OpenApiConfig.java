package com.christoskarakoutis.paymentsystem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        var schemeName = "cookieAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Payment System API")
                        .version("1.0.0")
                        .description("""
                                Stateless payment microservice with cookie-based JWT authentication.
                                
                                **Auth:** Obtain a JWT from the auth-service at `/api/auth/login`, then all requests
                                to this API must include the `jwt_access_token` cookie (HTTP-only, Secure, SameSite=Strict).
                                """))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("jwt_access_token")
                                .description("JWT access token issued by the auth-service")));
    }
}
