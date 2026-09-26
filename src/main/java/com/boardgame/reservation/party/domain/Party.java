package com.boardgame.reservation.party.domain;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.PlayMode;
import com.boardgame.reservation.global.common.BaseTimeEntity;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * ERD: PARTY (id, board_game_id, custom_game_name, host_id, title, description, capacity, status, play_at,
 *             play_mode, online_platform, online_link, location, created_at)
 * capacity 는 호스트 포함 인원. 호스트는 개설과 동시에 첫 참여자로 PARTY_MEMBER 에 들어간다.
 * 게임은 등록된 보드게임(board_game_id) 또는 직접 입력한 기타 게임(custom_game_name) 중 정확히 하나.
 * @ColumnDefault 는 기존 행이 있는 테이블에 NOT NULL 컬럼을 추가(ddl-auto: update)할 수 있게 하는 기본값이다.
 */
@Entity
@Table(name = "party")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Party extends BaseTimeEntity {

    private static final Pattern HTTP_LINK = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 기타 게임 파티면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_game_id")
    private BoardGame boardGame;

    /** 보드게임 파티면 null */
    @Column(length = 50)
    private String customGameName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private Member host;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartyStatus status;

    private LocalDateTime playAt;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'OFFLINE'")
    @Column(nullable = false, length = 20)
    private PlayMode playMode;

    @Column(length = 30)
    private String onlinePlatform;

    /** 호스트·참여자(JOINED)에게만 응답에 노출한다 */
    @Column(length = 300)
    private String onlineLink;

    @Column(length = 100)
    private String location;

    /**
     * 진행 방식과 그에 딸린 정보. 방식과 맞지 않는 필드는 버리고(ONLINE 이면 location, OFFLINE 이면 platform·link),
     * 공백은 null 로 정리하며, 접속 링크는 http:// / https:// 만 허용한다.
     */
    public record PlayInfo(PlayMode mode, String platform, String link, String location) {

        public static PlayInfo of(PlayMode mode, String platform, String link, String location) {
            if (mode == null) {
                throw new BusinessException(ErrorCode.INVALID_PLAY_MODE);
            }
            if (mode == PlayMode.OFFLINE) {
                return new PlayInfo(mode, null, null, blankToNull(location));
            }
            String normalizedLink = blankToNull(link);
            if (normalizedLink != null && !HTTP_LINK.matcher(normalizedLink).matches()) {
                throw new BusinessException(ErrorCode.INVALID_ONLINE_LINK);
            }
            return new PlayInfo(mode, blankToNull(platform), normalizedLink, null);
        }

        public static PlayInfo offline() {
            return new PlayInfo(PlayMode.OFFLINE, null, null, null);
        }
    }

    private Party(BoardGame boardGame, String customGameName, Member host, String title, String description,
                  int capacity, LocalDateTime playAt, PlayInfo play) {
        this.boardGame = boardGame;
        this.customGameName = customGameName;
        this.host = host;
        this.title = title;
        this.description = description;
        this.capacity = capacity;
        this.playAt = playAt;
        this.status = PartyStatus.RECRUITING;
        this.playMode = play.mode();
        this.onlinePlatform = play.platform();
        this.onlineLink = play.link();
        this.location = play.location();
    }

    public static Party createWithBoardGame(BoardGame boardGame, Member host, String title, String description,
                                            int capacity, LocalDateTime playAt, PlayInfo play) {
        return new Party(boardGame, null, host, title, description, capacity, playAt, play);
    }

    /** 앞뒤 공백을 제거한 이름이 비어 있으면 INVALID_GAME_SELECTION */
    public static Party createWithCustomGame(String customGameName, Member host, String title, String description,
                                             int capacity, LocalDateTime playAt, PlayInfo play) {
        String name = blankToNull(customGameName);
        if (name == null) {
            throw new BusinessException(ErrorCode.INVALID_GAME_SELECTION);
        }
        return new Party(null, name, host, title, description, capacity, playAt, play);
    }

    /** 오프라인 보드게임 파티. 진행 방식이 필요 없는 곳(테스트 시드 등)용 */
    public static Party create(BoardGame boardGame, Member host, String title, String description,
                               int capacity, LocalDateTime playAt) {
        return createWithBoardGame(boardGame, host, title, description, capacity, playAt, PlayInfo.offline());
    }

    public void close() {
        this.status = PartyStatus.CLOSED;
    }

    /** 게임 운영 중지 등으로 모집을 접는다. 마감(CLOSED)된 파티는 이력으로 남기므로 호출부가 RECRUITING 만 골라 부른다 */
    public void cancel() {
        this.status = PartyStatus.CANCELLED;
    }

    public boolean isHost(Long memberId) {
        return host.getId().equals(memberId);
    }

    public boolean isRecruiting() {
        return status == PartyStatus.RECRUITING;
    }

    public boolean isCustomGame() {
        return boardGame == null;
    }

    /** 보드게임 이름 또는 직접 입력한 게임 이름. 보드게임 파티면 boardGame 을 읽으므로 fetch 된 상태여야 한다 */
    public String getGameName() {
        return isCustomGame() ? customGameName : boardGame.getName();
    }

    /** 기타 게임은 숨길 수 없으므로 항상 true */
    public boolean isBoardGameVisible() {
        return isCustomGame() || boardGame.isVisible();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
