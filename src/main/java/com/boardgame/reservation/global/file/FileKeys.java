package com.boardgame.reservation.global.file;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 파일 key 형식과 URL 변환을 한 곳에 모은다.
 * key = {dir}/{uuid}.{ext}  예) avatars/3f2b...-....jpg
 * 저장소 구현이 바뀌어도(S3 등) key 형식과 imageUrl 변환은 여기서만 관리한다.
 */
public final class FileKeys {

    public static final String AVATARS = "avatars";
    public static final String BOARDGAMES = "boardgames";
    public static final Set<String> DIRS = Set.of(AVATARS, BOARDGAMES);

    /** 경로 조작(../ 등) 차단: 이 정규식과 일치하는 key 만 저장소가 다룬다 */
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^(avatars|boardgames)/[0-9a-f-]{36}\\.(jpg|jpeg|png|webp)$");

    private static final String URL_PREFIX = "/api/files/";

    private FileKeys() {
    }

    public static boolean isValid(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }

    /** 응답에는 파일 key 가 아니라 이 URL 을 내려준다. key 가 없으면 null */
    public static String toUrl(String key) {
        return key == null ? null : URL_PREFIX + key;
    }

    /** key 의 확장자(소문자). isValid 를 통과한 key 에만 사용 */
    public static String extensionOf(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }
}
