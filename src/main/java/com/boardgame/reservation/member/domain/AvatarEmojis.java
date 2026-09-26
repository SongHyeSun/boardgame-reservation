package com.boardgame.reservation.member.domain;

import java.util.List;

/**
 * 아바타로 고를 수 있는 이모지 목록. 프론트 상수와 반드시 같은 목록·같은 문자열이어야 한다.
 * (♟️ 은 U+265F + U+FE0F 두 코드포인트)
 */
public final class AvatarEmojis {

    public static final String DEFAULT = "🎲";

    public static final List<String> ALLOWED = List.of(
            "🎲", "🃏", "♟️", "🧩", "🎯", "🏆", "🐉", "🦊",
            "🐱", "🐻", "🐧", "🦉", "🌟", "🔥", "🍀", "🎩");

    private AvatarEmojis() {
    }

    public static boolean isAllowed(String emoji) {
        return emoji != null && ALLOWED.contains(emoji);
    }
}
