package com.boardgame.reservation.boardgame.domain;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 유튜브 링크에서 영상 ID(11자)만 뽑아낸다. 저장은 ID 만 하고, 재생은 프론트가 nocookie embed 로 한다.
 *
 * 허용 형식 (https:// 또는 http://)
 *   youtube.com | www.youtube.com | m.youtube.com   /watch?v=ID (다른 쿼리가 섞여도 됨), /shorts/ID, /embed/ID
 *   youtu.be/ID  (?si=..., ?t=... 무시)
 * scheme 없이 위 4개 host 로 시작하는 입력은 앞에 https:// 를 붙여 해석한다. 그 밖의 scheme 없는 입력은 거부.
 * 빈 값/null 은 null(영상 제거), 그 외 형식은 INVALID_YOUTUBE_URL.
 */
public final class YoutubeUrlParser {

    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern SCHEMELESS = Pattern.compile("^((www\\.|m\\.)?youtube\\.com|youtu\\.be)/", Pattern.CASE_INSENSITIVE);
    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com");
    private static final String SHORT_HOST = "youtu.be";

    private YoutubeUrlParser() {
    }

    public static String parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String candidate = url.trim();
        if (!candidate.contains("://") && SCHEMELESS.matcher(candidate).find()) {
            candidate = "https://" + candidate;
        }

        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            throw invalid();
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null
                || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
            throw invalid();
        }
        host = host.toLowerCase(Locale.ROOT);

        String[] segments = segmentsOf(uri.getRawPath());
        String id;
        if (host.equals(SHORT_HOST)) {
            id = segments.length == 1 ? segments[0] : null;
        } else if (YOUTUBE_HOSTS.contains(host)) {
            id = fromYoutubeUrl(segments, uri.getRawQuery());
        } else {
            id = null;
        }

        if (id == null || !VIDEO_ID.matcher(id).matches()) {
            throw invalid();
        }
        return id;
    }

    private static String fromYoutubeUrl(String[] segments, String rawQuery) {
        if (segments.length == 1 && segments[0].equals("watch")) {
            return queryParam(rawQuery, "v");
        }
        if (segments.length == 2 && (segments[0].equals("shorts") || segments[0].equals("embed"))) {
            return segments[1];
        }
        return null;
    }

    /** "/a/b/" → ["a","b"]. 빈 세그먼트는 버린다 (트레일링 슬래시 허용) */
    private static String[] segmentsOf(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return new String[0];
        }
        return Arrays.stream(rawPath.split("/"))
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        String prefix = name + "=";
        for (String pair : rawQuery.split("&")) {
            if (pair.startsWith(prefix)) {
                return pair.substring(prefix.length());
            }
        }
        return null;
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_YOUTUBE_URL);
    }
}
