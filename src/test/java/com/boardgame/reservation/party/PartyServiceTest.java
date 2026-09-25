package com.boardgame.reservation.party;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.PartyMember;
import com.boardgame.reservation.party.dto.PartyCreateRequest;
import com.boardgame.reservation.party.repository.PartyMemberRepository;
import com.boardgame.reservation.party.repository.PartyRedisRepository;
import com.boardgame.reservation.party.repository.PartyRepository;
import com.boardgame.reservation.party.service.PartyMemberWriter;
import com.boardgame.reservation.party.service.PartyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** DB/Redis/스프링 없이 서비스 규칙만 검증하는 단위 테스트 */
@ExtendWith(MockitoExtension.class)
class PartyServiceTest {

    private static final Long HOST_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long PARTY_ID = 10L;

    @Mock
    PartyRepository partyRepository;
    @Mock
    PartyMemberRepository partyMemberRepository;
    @Mock
    BoardGameRepository boardGameRepository;
    @Mock
    MemberRepository memberRepository;
    @Mock
    PartyMemberWriter partyMemberWriter;
    @Mock
    PartyRedisRepository partyRedisRepository;

    @InjectMocks
    PartyService partyService;

    private static Member member(Long id) {
        Member member = Member.createUser("m" + id + "@test.com", "pw", "nick" + id);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static Party party(Member host, int capacity) {
        BoardGame boardGame = BoardGame.create("Catan", 2, 4, 60, Difficulty.NORMAL, "설명");
        Party party = Party.create(boardGame, host, "같이 해요", "설명", capacity, null);
        ReflectionTestUtils.setField(party, "id", PARTY_ID);
        return party;
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    // ───────────── 개설 ─────────────

    @Test
    @DisplayName("capacity 가 보드게임 인원 범위를 벗어나면 INVALID_CAPACITY (하한/상한 모두)")
    void create_capacityOutOfRange() {
        BoardGame boardGame = BoardGame.create("Catan", 2, 4, 60, Difficulty.NORMAL, "설명");
        given(boardGameRepository.findById(1L)).willReturn(Optional.of(boardGame));
        given(memberRepository.findById(HOST_ID)).willReturn(Optional.of(member(HOST_ID)));

        for (int capacity : new int[]{1, 5}) {
            assertThatThrownBy(() -> partyService.create(HOST_ID,
                    new PartyCreateRequest(1L, "제목", null, capacity, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(PartyServiceTest::codeOf)
                    .isEqualTo(ErrorCode.INVALID_CAPACITY);
        }
        verify(partyRepository, never()).save(any(Party.class));
    }

    @Test
    @DisplayName("개설 시 호스트가 첫 참여자로 저장되고 Redis 키는 capacity-1 로 세팅된다")
    void create_hostIsFirstMember() {
        Member host = member(HOST_ID);
        BoardGame boardGame = BoardGame.create("Catan", 2, 4, 60, Difficulty.NORMAL, "설명");
        given(boardGameRepository.findById(1L)).willReturn(Optional.of(boardGame));
        given(memberRepository.findById(HOST_ID)).willReturn(Optional.of(host));
        given(partyRepository.save(any(Party.class))).willAnswer(inv -> {
            Party saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", PARTY_ID);
            return saved;
        });

        partyService.create(HOST_ID, new PartyCreateRequest(1L, " 제목 ", null, 4, null));

        verify(partyMemberRepository).save(any(PartyMember.class));
        // 트랜잭션이 없는 단위 테스트에서는 afterCommit 대신 즉시 실행된다
        verify(partyRedisRepository).init(PARTY_ID, 3L, List.of(HOST_ID));
    }

    // ───────────── 참여 ─────────────

    @Test
    @DisplayName("모집 중이 아닌 파티에 join 하면 PARTY_NOT_RECRUITING, Redis 는 건드리지 않는다")
    void join_notRecruiting() {
        Party party = party(member(HOST_ID), 4);
        party.close();
        given(partyRepository.findById(PARTY_ID)).willReturn(Optional.of(party));

        assertThatThrownBy(() -> partyService.join(PARTY_ID, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(PartyServiceTest::codeOf)
                .isEqualTo(ErrorCode.PARTY_NOT_RECRUITING);

        verify(partyRedisRepository, never()).addMember(anyLong(), anyLong());
    }

    @Test
    @DisplayName("없는 파티에 join 하면 PARTY_NOT_FOUND")
    void join_notFound() {
        given(partyRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> partyService.join(99L, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(PartyServiceTest::codeOf)
                .isEqualTo(ErrorCode.PARTY_NOT_FOUND);
    }

    @Test
    @DisplayName("Redis 에 이미 참여자로 있으면 ALREADY_JOINED, DECR/DB 는 시도하지 않는다")
    void join_alreadyJoined() {
        given(partyRepository.findById(PARTY_ID)).willReturn(Optional.of(party(member(HOST_ID), 4)));
        given(partyRedisRepository.hasRemaining(PARTY_ID)).willReturn(true);
        given(partyRedisRepository.addMember(PARTY_ID, OTHER_ID)).willReturn(false);

        assertThatThrownBy(() -> partyService.join(PARTY_ID, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(PartyServiceTest::codeOf)
                .isEqualTo(ErrorCode.ALREADY_JOINED);

        verify(partyRedisRepository, never()).decrement(anyLong());
        verify(partyMemberWriter, never()).add(anyLong(), anyLong());
    }

    @Test
    @DisplayName("DECR 결과가 음수면 원복하고 PARTY_FULL, DB 에는 쓰지 않는다")
    void join_full_rollsBack() {
        given(partyRepository.findById(PARTY_ID)).willReturn(Optional.of(party(member(HOST_ID), 4)));
        given(partyRedisRepository.hasRemaining(PARTY_ID)).willReturn(true);
        given(partyRedisRepository.addMember(PARTY_ID, OTHER_ID)).willReturn(true);
        given(partyRedisRepository.decrement(PARTY_ID)).willReturn(-1L);

        assertThatThrownBy(() -> partyService.join(PARTY_ID, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(PartyServiceTest::codeOf)
                .isEqualTo(ErrorCode.PARTY_FULL);

        verify(partyRedisRepository).rollbackJoin(PARTY_ID, OTHER_ID);
        verify(partyMemberWriter, never()).add(anyLong(), anyLong());
    }

    @Test
    @DisplayName("DB INSERT 가 UNIQUE 위반이면 자리만 원복(SREM 없음)하고 ALREADY_JOINED")
    void join_uniqueViolation_compensates() {
        given(partyRepository.findById(PARTY_ID)).willReturn(Optional.of(party(member(HOST_ID), 4)));
        given(partyRedisRepository.hasRemaining(PARTY_ID)).willReturn(true);
        given(partyRedisRepository.addMember(PARTY_ID, OTHER_ID)).willReturn(true);
        given(partyRedisRepository.decrement(PARTY_ID)).willReturn(2L);
        doThrow(new DataIntegrityViolationException("uk_party_member"))
                .when(partyMemberWriter).add(PARTY_ID, OTHER_ID);

        assertThatThrownBy(() -> partyService.join(PARTY_ID, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(PartyServiceTest::codeOf)
                .isEqualTo(ErrorCode.ALREADY_JOINED);

        verify(partyRedisRepository).restoreRemaining(PARTY_ID);
        verify(partyRedisRepository, never()).rollbackJoin(anyLong(), anyLong());
    }

    @Test
    @DisplayName("DB INSERT 가 그 외 예외로 실패해도 Redis 보상 후 예외를 그대로 던진다")
    void join_dbFailure_compensatesAndRethrows() {
        given(partyRepository.findById(PARTY_ID)).willReturn(Optional.of(party(member(HOST_ID), 4)));
        given(partyRedisRepository.hasRemaining(PARTY_ID)).willReturn(true);
        given(partyRedisRepository.addMember(PARTY_ID, OTHER_ID)).willReturn(true);
        given(partyRedisRepository.decrement(PARTY_ID)).willReturn(2L);
        RuntimeException failure = new IllegalStateException("db down");
        doThrow(failure).when(partyMemberWriter).add(PARTY_ID, OTHER_ID);

        assertThatThrownBy(() -> partyService.join(PARTY_ID, OTHER_ID)).isSameAs(failure);

        verify(partyRedisRepository).rollbackJoin(PARTY_ID, OTHER_ID);
    }

    @Test
    @DisplayName("성공하면 DECR 결과를 remaining 으로 돌려준다")
    void join_success() {
        given(partyRepository.findById(PARTY_ID)).willReturn(Optional.of(party(member(HOST_ID), 4)));
        given(partyRedisRepository.hasRemaining(PARTY_ID)).willReturn(true);
        given(partyRedisRepository.addMember(PARTY_ID, OTHER_ID)).willReturn(true);
        given(partyRedisRepository.decrement(PARTY_ID)).willReturn(2L);

        assertThat(partyService.join(PARTY_ID, OTHER_ID).remaining()).isEqualTo(2L);

        verify(partyMemberWriter).add(PARTY_ID, OTHER_ID);
        verify(partyRedisRepository, never()).rollbackJoin(anyLong(), anyLong());
    }

    @Test
    @DisplayName("remaining 키가 없으면 DB 기준(capacity - 참여자 수)으로 복구한 뒤 진행한다")
    void join_recoversMissingKeys() {
        given(partyRepository.findById(PARTY_ID)).willReturn(Optional.of(party(member(HOST_ID), 4)));
        given(partyRedisRepository.hasRemaining(PARTY_ID)).willReturn(false);
        given(partyMemberRepository.findMemberIdsByPartyId(PARTY_ID)).willReturn(List.of(HOST_ID, 3L));
        given(partyRedisRepository.addMember(PARTY_ID, OTHER_ID)).willReturn(true);
        given(partyRedisRepository.decrement(PARTY_ID)).willReturn(1L);

        partyService.join(PARTY_ID, OTHER_ID);

        verify(partyRedisRepository).recover(PARTY_ID, 2L, List.of(HOST_ID, 3L));
    }

    // ───────────── 취소 / 마감 ─────────────

    @Test
    @DisplayName("호스트는 탈퇴할 수 없다 (HOST_CANNOT_LEAVE)")
    void leave_host_throws() {
        given(partyRepository.findWithDetailsById(PARTY_ID)).willReturn(Optional.of(party(member(HOST_ID), 4)));

        assertThatThrownBy(() -> partyService.leave(PARTY_ID, HOST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(PartyServiceTest::codeOf)
                .isEqualTo(ErrorCode.HOST_CANNOT_LEAVE);

        verify(partyMemberWriter, never()).remove(anyLong(), anyLong());
    }

    @Test
    @DisplayName("참여하지 않은 회원이 탈퇴하면 NOT_JOINED, Redis 는 건드리지 않는다")
    void leave_notJoined_throws() {
        given(partyRepository.findWithDetailsById(PARTY_ID)).willReturn(Optional.of(party(member(HOST_ID), 4)));
        given(partyMemberWriter.remove(PARTY_ID, OTHER_ID)).willReturn(false);

        assertThatThrownBy(() -> partyService.leave(PARTY_ID, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(PartyServiceTest::codeOf)
                .isEqualTo(ErrorCode.NOT_JOINED);

        verify(partyRedisRepository, never()).release(anyLong(), anyLong());
    }

    @Test
    @DisplayName("탈퇴는 DB 삭제가 성공한 뒤에만 Redis 자리를 반환한다")
    void leave_success_releasesAfterDelete() {
        given(partyRepository.findWithDetailsById(PARTY_ID)).willReturn(Optional.of(party(member(HOST_ID), 4)));
        given(partyMemberWriter.remove(PARTY_ID, OTHER_ID)).willReturn(true);

        partyService.leave(PARTY_ID, OTHER_ID);

        verify(partyRedisRepository).release(PARTY_ID, OTHER_ID);
    }

    @Test
    @DisplayName("호스트가 아니면 close 할 수 없다 (NOT_PARTY_HOST)")
    void close_nonHost_throws() {
        Party party = party(member(HOST_ID), 4);
        given(partyRepository.findWithDetailsById(PARTY_ID)).willReturn(Optional.of(party));

        assertThatThrownBy(() -> partyService.close(PARTY_ID, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(PartyServiceTest::codeOf)
                .isEqualTo(ErrorCode.NOT_PARTY_HOST);

        assertThat(party.isRecruiting()).isTrue();
        verify(partyRedisRepository, never()).delete(anyLong());
    }

    @Test
    @DisplayName("호스트가 close 하면 CLOSED 가 되고 Redis 키가 삭제된다")
    void close_host_closesAndDeletesKeys() {
        Party party = party(member(HOST_ID), 4);
        given(partyRepository.findWithDetailsById(PARTY_ID)).willReturn(Optional.of(party));

        partyService.close(PARTY_ID, HOST_ID);

        assertThat(party.isRecruiting()).isFalse();
        verify(partyRedisRepository).delete(PARTY_ID);
    }
}
