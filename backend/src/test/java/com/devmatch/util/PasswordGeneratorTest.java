package com.devmatch.util;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    private final PasswordGenerator generator = new PasswordGenerator();

    @Test
    void generate_길이는_12자() {
        String pwd = generator.generate();
        assertThat(pwd).hasSize(12);
    }

    @RepeatedTest(20)
    void generate_각_문자종류를_최소_1자씩_포함() {
        String pwd = generator.generate();
        assertThat(pwd).matches(".*[A-Z].*");      // 대문자
        assertThat(pwd).matches(".*[a-z].*");      // 소문자
        assertThat(pwd).matches(".*\\d.*");        // 숫자
        assertThat(pwd).matches(".*[!@#$%^&*].*"); // 특수문자
    }

    @RepeatedTest(5)
    void generate_매번_다른_값을_반환() {
        String a = generator.generate();
        String b = generator.generate();
        assertThat(a).isNotEqualTo(b);
    }
}
