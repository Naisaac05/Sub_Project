package com.devmatch.ragverify;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConceptExampleVerificationTest {

    static class GreetingService {
        String greet() {
            return "hello";
        }
    }

    @Test
    void appliesAroundAdvice() {
        ProxyFactory factory = new ProxyFactory(new GreetingService());
        factory.addAdvice((MethodInterceptor) invocation -> {
            long started = System.nanoTime();
            Object result = invocation.proceed();
            assertThat(System.nanoTime()).isGreaterThanOrEqualTo(started);
            return result;
        });

        GreetingService proxy = (GreetingService) factory.getProxy();

        assertThat(proxy.greet()).isEqualTo("hello");
    }

    @Test
    void buildsResponseEntity() {
        ResponseEntity<String> response = ResponseEntity.status(201)
                .header("Location", "/users/7")
                .body("created");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getFirst("Location")).isEqualTo("/users/7");
        assertThat(response.getBody()).isEqualTo("created");
    }

    interface Update {
    }

    static class UserRequest {
        @NotBlank(groups = Update.class)
        String name = "";
    }

    @Test
    void validatesRequestedGroup() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<UserRequest>> violations = validator.validate(new UserRequest(), Update.class);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("name");
    }

    @Configuration
    static class ProfileConfig {
        @Bean
        @Profile("dev")
        String dataSourceKind() {
            return "h2";
        }
    }

    @Test
    void selectsBeanForActiveProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            context.register(ProfileConfig.class);
            context.refresh();

            assertThat(context.getBean(String.class)).isEqualTo("h2");
        }
    }
}
