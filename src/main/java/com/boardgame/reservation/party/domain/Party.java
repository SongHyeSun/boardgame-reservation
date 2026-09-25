package com.boardgame.reservation.party.domain;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.global.common.BaseTimeEntity;
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

import java.time.LocalDateTime;

/**
 * ERD: PARTY (id, board_game_id, host_id, title, description, capacity, status, play_at, created_at)
 * capacity 는 호스트 포함 인원. 호스트는 개설과 동시에 첫 참여자로 PARTY_MEMBER 에 들어간다.
 */
@Entity
@Table(name = "party")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Party extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_game_id", nullable = false)
    private BoardGame boardGame;

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

    private Party(BoardGame boardGame, Member host, String title, String description,
                  int capacity, LocalDateTime playAt) {
        this.boardGame = boardGame;
        this.host = host;
        this.title = title;
        this.description = description;
        this.capacity = capacity;
        this.playAt = playAt;
        this.status = PartyStatus.RECRUITING;
    }

    public static Party create(BoardGame boardGame, Member host, String title, String description,
                               int capacity, LocalDateTime playAt) {
        return new Party(boardGame, host, title, description, capacity, playAt);
    }

    public void close() {
        this.status = PartyStatus.CLOSED;
    }

    public boolean isHost(Long memberId) {
        return host.getId().equals(memberId);
    }

    public boolean isRecruiting() {
        return status == PartyStatus.RECRUITING;
    }
}
