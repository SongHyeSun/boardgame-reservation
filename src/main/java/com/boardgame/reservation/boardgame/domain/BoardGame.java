package com.boardgame.reservation.boardgame.domain;

import com.boardgame.reservation.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ERD: BOARD_GAME (id, name, min_players, max_players, play_time, difficulty, description, created_at)
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

    private BoardGame(String name, int minPlayers, int maxPlayers, int playTime,
                      Difficulty difficulty, String description) {
        this.name = name;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.playTime = playTime;
        this.difficulty = difficulty;
        this.description = description;
    }

    public static BoardGame create(String name, int minPlayers, int maxPlayers, int playTime,
                                   Difficulty difficulty, String description) {
        return new BoardGame(name, minPlayers, maxPlayers, playTime, difficulty, description);
    }

    /** PUT 전체 교체용 */
    public void update(String name, int minPlayers, int maxPlayers, int playTime,
                       Difficulty difficulty, String description) {
        this.name = name;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.playTime = playTime;
        this.difficulty = difficulty;
        this.description = description;
    }
}
