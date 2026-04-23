package com.devmatch.util;

import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDisplayTest {

    @Test
    void displayName_ACTIVE_사용자_원래_이름_반환() {
        User u = User.builder().name("김멘티").status(UserStatus.ACTIVE).build();
        assertThat(UserDisplay.displayName(u)).isEqualTo("김멘티");
    }

    @Test
    void displayName_DEACTIVATED_사용자_원래_이름_반환() {
        User u = User.builder().name("이멘토").status(UserStatus.DEACTIVATED).build();
        assertThat(UserDisplay.displayName(u)).isEqualTo("이멘토");
    }

    @Test
    void displayName_DELETED_사용자_탈퇴한_회원으로_마스킹() {
        User u = User.builder().name("박삭제").status(UserStatus.DELETED).build();
        assertThat(UserDisplay.displayName(u)).isEqualTo("탈퇴한 회원");
    }

    @Test
    void displayName_null_사용자_탈퇴한_회원_반환() {
        assertThat(UserDisplay.displayName(null)).isEqualTo("탈퇴한 회원");
    }
}
