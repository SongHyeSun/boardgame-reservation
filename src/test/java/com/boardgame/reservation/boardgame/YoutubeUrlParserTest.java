package com.boardgame.reservation.boardgame;

import com.boardgame.reservation.boardgame.domain.YoutubeUrlParser;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YoutubeUrlParserTest {

    private static final String ID = "dQw4w9WgXcQ";

    @ParameterizedTest(name = "{0}")
    @DisplayName("허용 형식에서 영상 ID 를 추출한다")
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123&t=42s",
            "https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abcDEF123&t=10",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ/",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ?start=10",
            "  https://youtu.be/dQw4w9WgXcQ  ",
            "HTTPS://WWW.YOUTUBE.COM/watch?v=dQw4w9WgXcQ"
    })
    void parse_supportedFormats(String url) {
        assertThat(YoutubeUrlParser.parse(url)).isEqualTo(ID);
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("scheme 없는 유튜브 host 입력은 https:// 를 붙여 해석한다")
    @ValueSource(strings = {
            "youtube.com/watch?v=dQw4w9WgXcQ",
            "www.youtube.com/watch?v=dQw4w9WgXcQ",
            "m.youtube.com/watch?v=dQw4w9WgXcQ",
            "youtu.be/dQw4w9WgXcQ?si=x",
            "www.youtube.com/shorts/dQw4w9WgXcQ",
            "www.youtube.com/embed/dQw4w9WgXcQ",
            "  www.youtube.com/watch?v=dQw4w9WgXcQ"
    })
    void parse_schemeless(String url) {
        assertThat(YoutubeUrlParser.parse(url)).isEqualTo(ID);
    }

    @ParameterizedTest
    @DisplayName("빈 값/null 은 null (영상 제거)")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void parse_blank_returnsNull(String url) {
        assertThat(YoutubeUrlParser.parse(url)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("잘못된 링크는 INVALID_YOUTUBE_URL")
    @ValueSource(strings = {
            "hello",
            "https://www.google.com/watch?v=dQw4w9WgXcQ",
            "https://vimeo.com/dQw4w9WgXcQ",
            "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
            "youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
            "https://evil.com/youtube.com/watch?v=dQw4w9WgXcQ",
            "evil.com/watch?v=dQw4w9WgXcQ",
            "//www.youtube.com/watch?v=dQw4w9WgXcQ",
            "javascript:alert(1)",
            "ftp://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/watch?v=dQw4w9WgXc",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXc!",
            "https://www.youtube.com/watch",
            "https://www.youtube.com/watch?list=PL123",
            "https://www.youtube.com/playlist?list=PL123",
            "https://www.youtube.com/",
            "https://www.youtube.com/channel/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/",
            "https://www.youtube.com/embed/dQw4w9WgXcQ/extra",
            "https://youtu.be/",
            "https://youtu.be/dQw4w9WgXcQ/extra",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&x=a b",
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"
    })
    void parse_invalid_throws(String url) {
        assertThatThrownBy(() -> YoutubeUrlParser.parse(url))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_YOUTUBE_URL);
    }

    @Test
    @DisplayName("ID 에 허용된 문자(영문·숫자·-·_)만 통과한다")
    void parse_idCharset() {
        assertThat(YoutubeUrlParser.parse("https://youtu.be/a-b_C1d2E3f")).isEqualTo("a-b_C1d2E3f");
    }
}
