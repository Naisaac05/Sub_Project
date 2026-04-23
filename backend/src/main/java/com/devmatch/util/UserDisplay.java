package com.devmatch.util;

import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;

/**
 * 사용자 표시 이름 마스킹.
 * DELETED 상태 또는 null 사용자에 대해 "탈퇴한 회원" 으로 일관되게 표시.
 */
public final class UserDisplay {

    private static final String DELETED_LABEL = "탈퇴한 회원";

    private UserDisplay() { }

    public static String displayName(User user) {
        if (user == null || user.getStatus() == UserStatus.DELETED) {
            return DELETED_LABEL;
        }
        return user.getName();
    }
}
