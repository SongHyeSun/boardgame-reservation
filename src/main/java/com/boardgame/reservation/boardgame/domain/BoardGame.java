package com.boardgame.reservation.boardgame.domain;

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

/**
 * ERD: BOARD_GAME (id, name, min_players, max_players, play_time, difficulty, description,
 *                  image_key, youtube_video_id, offline_available, online_available, stock, visible, created_by, created_at)
 *
 * 진행 방식 두 가지 중 최소 하나는 true, 오프라인 가능이면 stock 1 이상, 온라인 전용이면 stock 은 0 으로 저장한다.
 * created_by 는 기존 게임(등록 관리자 없음) 때문에 DB 에서는 nullable — 기동 시 SUPER_ADMIN 으로 채워진다.
 * @ColumnDefault 는 기존 행이 있는 테이블에 NOT NULL 컬럼을 추가(ddl-auto: update)할 수 있게 하는 기본값이다.
 */
@Entity
@Table(name = "board_game")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자, 외부에서 new BoardGame() 금지
public class BoardGame extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int minPlayers;

    @Column(nullable = false)
    private int maxPlayers;

    /** 플레이 시간 (분) */
    @Column(nullable = false)
    private int playTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @Column(columnDefinition = "text")
    private String description;

    /** FileStorage key (dir boardgames) */
    @Column(length = 100)
    private String imageKey;

    /** 유튜브 링크에서 추출한 영상 ID 만 저장 */
    @Column(length = 11)
    private String youtubeVideoId;

    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean offlineAvailable;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean onlineAvailable;

    @ColumnDefault("1")
    @Column(nullable = false)
    private int stock;

    /** false 면 "운영 중지" — 목록에서 빠지고 파티 개설이 막힌다 */
    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean visible;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Member createdBy;

    /** 등록·수정 때 함께 다루는 게임 정보 (이미지·유튜브는 별도 메서드) */
    public record Details(String name, int minPlayers, int maxPlayers, int playTime, Difficulty difficulty,
                          String description, boolean offlineAvailable, boolean onlineAvailable, int stock) {
    }

    private BoardGame(Details details, Member createdBy) {
        applyDetails(details);
        this.createdBy = createdBy;
        this.visible = true;
    }

    public static BoardGame create(Details details, Member createdBy) {
        return new BoardGame(details, createdBy);
    }

    /** 오프라인 전용·재고 1·등록 관리자 없음. 진행 방식·소유자가 필요 없는 곳(테스트 시드 등)용 */
    public static BoardGame create(String name, int minPlayers, int maxPlayers, int playTime,
                                   Difficulty difficulty, String description) {
        return create(new Details(name, minPlayers, maxPlayers, playTime, difficulty, description, true, false, 1), null);
    }

    /** PUT 전체 교체용 */
    public void update(Details details) {
        applyDetails(details);
    }

    /** 규칙을 모두 검증한 뒤에 필드를 바꾼다 (실패하면 아무것도 바뀌지 않는다) */
    private void applyDetails(Details details) {
        if (!details.offlineAvailable() && !details.onlineAvailable()) {
            throw new BusinessException(ErrorCode.INVALID_PLAY_MODE);
        }
        if (details.offlineAvailable() && details.stock() < 1) {
            throw new BusinessException(ErrorCode.INVALID_STOCK);
        }
        this.name = details.name();
        this.minPlayers = details.minPlayers();
        this.maxPlayers = details.maxPlayers();
        this.playTime = details.playTime();
        this.difficulty = details.difficulty();
        this.description = details.description();
        this.offlineAvailable = details.offlineAvailable();
        this.onlineAvailable = details.onlineAvailable();
        this.stock = details.offlineAvailable() ? details.stock() : 0;
    }

    public void changeImage(String imageKey) {
        this.imageKey = imageKey;
    }

    public void removeImage() {
        this.imageKey = null;
    }

    /** null 이면 영상 제거 */
    public void changeYoutube(String youtubeVideoId) {
        this.youtubeVideoId = youtubeVideoId;
    }

    public void hide() {
        this.visible = false;
    }

    public void show() {
        this.visible = true;
    }

    public boolean supports(PlayMode mode) {
        return mode == PlayMode.ONLINE ? onlineAvailable : offlineAvailable;
    }

    /** 등록 관리자가 없는(레거시) 게임은 아무도 소유자가 아니다 */
    public boolean isOwnedBy(Long memberId) {
        return createdBy != null && createdBy.getId().equals(memberId);
    }
}
