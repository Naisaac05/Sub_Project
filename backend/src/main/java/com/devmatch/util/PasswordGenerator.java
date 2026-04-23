package com.devmatch.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 관리자 액션(비번 리셋, 관리자 생성) 시 1회용 임시 비밀번호 생성.
 *
 * 정책: 12자, 대소문자+숫자+특수문자 각각 최소 1자, SecureRandom 기반.
 */
@Component
public class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";   // I, O 제외 (가독성)
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";   // l, o 제외
    private static final String DIGIT = "23456789";                    // 0, 1 제외
    private static final String SPECIAL = "!@#$%^&*";
    private static final String ALL = UPPER + LOWER + DIGIT + SPECIAL;
    private static final int LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        List<Character> chars = new ArrayList<>(LENGTH);
        chars.add(pick(UPPER));
        chars.add(pick(LOWER));
        chars.add(pick(DIGIT));
        chars.add(pick(SPECIAL));
        for (int i = chars.size(); i < LENGTH; i++) {
            chars.add(pick(ALL));
        }
        Collections.shuffle(chars, random);

        StringBuilder sb = new StringBuilder(LENGTH);
        for (char c : chars) sb.append(c);
        return sb.toString();
    }

    private char pick(String pool) {
        return pool.charAt(random.nextInt(pool.length()));
    }
}
