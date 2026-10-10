package com.eventease.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import static org.junit.jupiter.api.Assertions.*;

class JwtConfigurationSecurityTest {

    @Configuration
    static class TestContextWithoutJwtSecret {
        @Bean
        public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public JwtService jwtService() {
            return new JwtService();
        }
    }

    @Test
    @DisplayName("Should fail fast with BeanCreationException when jwt.secret is missing rather than using an insecure default")
    void shouldFailFastWhenJwtSecretIsMissing() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TestContextWithoutJwtSecret.class);

        BeanCreationException exception = assertThrows(BeanCreationException.class, context::refresh);
        assertTrue(
                exception.getMessage().contains("jwt.secret") || exception.getCause().getMessage().contains("jwt.secret"),
                "Context initialization failure must clearly identify missing jwt.secret"
        );
        context.close();
    }
}
