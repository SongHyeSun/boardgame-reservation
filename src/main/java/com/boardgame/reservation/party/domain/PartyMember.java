package com.boardgame.reservation.party.domain;

import com.boardgame.reservation.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ERD: PARTY_MEMBER (id, party_id, member_id, joined_at)
 * UNIQUE(party_id, member_id) 는 중복 참여의 최종 방어선 (Redis 게이트가 뚫려도 DB가 막는다).
 */
@Entity
@Table(name = "party_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_party_member", columnNames = {"party_id", "member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    private PartyMember(Party party, Member member) {
        this.party = party;
        this.member = member;
        this.joinedAt = LocalDateTime.now();
    }

    public static PartyMember create(Party party, Member member) {
        return new PartyMember(party, member);
    }
}
