package com.boardgame.reservation.member.service;

import com.boardgame.reservation.global.common.TransactionCallbacks;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.global.file.FileKeys;
import com.boardgame.reservation.global.file.FileStorage;
import com.boardgame.reservation.member.domain.AvatarEmojis;
import com.boardgame.reservation.member.domain.AvatarType;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.dto.ChangePasswordRequest;
import com.boardgame.reservation.member.dto.MemberResponse;
import com.boardgame.reservation.member.dto.SignupRequest;
import com.boardgame.reservation.member.dto.UpdateProfileRequest;
import com.boardgame.reservation.member.event.AdminRequestedEvent;
import com.boardgame.reservation.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorage fileStorage;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // ───────────── 가입 ─────────────

    @Transactional
    public MemberResponse signup(SignupRequest request, MultipartFile image) {
        String email = normalizeEmail(request.email());

        // 1차 방어: 애플리케이션 레벨 중복 체크 (친절한 에러 메시지용). 파일을 저장하기 전에 먼저 거른다.
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        String emoji = validatedEmoji(request.avatarEmoji());

        String imageKey = storeAvatar(image);
        try {
            Member member = Member.register(
                    email,
                    passwordEncoder.encode(request.password()),
                    request.nickname().trim(),
                    toProfile(request.name(), request.birthDate(), request.affiliation(), request.job(), request.bio()),
                    emoji,
                    imageKey,
                    request.adminRequested() ? LocalDateTime.now(clock) : null);

            // 2차 방어: 동시에 같은 이메일로 가입 요청이 오면 exists 체크를 둘 다 통과할 수 있음
            //          → DB UNIQUE 제약 위반을 잡아서 409로 변환
            //          (IDENTITY 전략이라 save 시점에 INSERT가 바로 나간다)
            Member saved = save(member);
            if (request.adminRequested()) {
                eventPublisher.publishEvent(new AdminRequestedEvent(saved.getId()));
            }
            return MemberResponse.from(saved);
        } catch (RuntimeException e) {
            // DB 에 반영되지 않았으니 방금 올린 파일도 지운다 (커밋 시점 실패는 storeAvatar 의 afterRollback 이 처리)
            fileStorage.delete(imageKey);
            throw e;
        }
    }

    // ───────────── 내 정보 ─────────────

    public MemberResponse getMember(Long memberId) {
        return MemberResponse.from(findMember(memberId));
    }

    @Transactional
    public MemberResponse updateProfile(Long memberId, UpdateProfileRequest request, MultipartFile image) {
        Member member = findMember(memberId);
        boolean remove = request.imageRemoved();
        String emoji = validatedEmoji(request.avatarEmoji());
        String oldKey = member.getAvatarImageKey();

        // 파일을 저장하기 전에 아바타 규칙부터 모두 검증한다 (실패했을 때 지울 파일이 생기지 않도록)
        if (image != null && remove) {
            throw new BusinessException(ErrorCode.INVALID_AVATAR);
        }
        boolean hasImageAfterUpdate = image != null || (oldKey != null && !remove);
        if (request.avatarType() == AvatarType.IMAGE && !hasImageAfterUpdate) {
            throw new BusinessException(ErrorCode.INVALID_AVATAR);
        }

        member.updateProfile(
                request.nickname().trim(),
                toProfile(request.name(), request.birthDate(), request.affiliation(), request.job(), request.bio()));
        if (emoji != null) {
            member.changeAvatarEmoji(emoji);
        }

        if (image != null) {
            member.useImageAvatar(storeAvatar(image));
            deleteAfterCommit(oldKey);
        } else if (remove) {
            member.removeAvatarImage();
            deleteAfterCommit(oldKey);
        } else if (request.avatarType() == AvatarType.IMAGE) {
            member.useImageAvatar(oldKey);
        } else {
            member.useEmojiAvatar(member.getAvatarEmoji());
        }
        return MemberResponse.from(member);
    }

    @Transactional
    public void changePassword(Long memberId, ChangePasswordRequest request) {
        Member member = findMember(memberId);
        if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        member.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /** 관리자 신청(재신청 포함). USER 이고 상태가 NONE/REJECTED 일 때만, 아니면 INVALID_ADMIN_REQUEST(409) */
    @Transactional
    public MemberResponse requestAdmin(Long memberId) {
        Member member = findMember(memberId);
        member.requestAdmin(LocalDateTime.now(clock));
        eventPublisher.publishEvent(new AdminRequestedEvent(member.getId()));
        return MemberResponse.from(member);
    }

    /** "Test@Mail.com " 과 "test@mail.com" 을 같은 계정으로 취급 */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // ───────────── 내부 헬퍼 ─────────────

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Member save(Member member) {
        try {
            return memberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    /** 이미지 파트가 없으면 null. 있으면 검증·저장 후 key 를 돌려주고, 트랜잭션이 롤백되면 그 파일을 지우도록 등록 */
    private String storeAvatar(MultipartFile image) {
        if (image == null) {
            return null;
        }
        String key = fileStorage.store(image, FileKeys.AVATARS);
        TransactionCallbacks.afterRollback(() -> fileStorage.delete(key));
        return key;
    }

    /** 이전 파일 삭제는 DB 커밋 이후에 (커밋 전에 지우면 롤백 시 이미지만 사라진다) */
    private void deleteAfterCommit(String key) {
        if (key != null) {
            TransactionCallbacks.afterCommit(() -> fileStorage.delete(key));
        }
    }

    /** 이모지가 비어 있으면 null(기본값/현재값 사용), 있으면 허용 목록 안의 값만 통과 */
    private static String validatedEmoji(String emoji) {
        String value = blankToNull(emoji);
        if (value != null && !AvatarEmojis.isAllowed(value)) {
            throw new BusinessException(ErrorCode.INVALID_AVATAR);
        }
        return value;
    }

    private static Member.Profile toProfile(String name, LocalDate birthDate, String affiliation, String job, String bio) {
        return new Member.Profile(
                name.trim(),
                birthDate,
                blankToNull(affiliation),
                blankToNull(job),
                blankToNull(bio));
    }

    /** 선택 문자열: 앞뒤 공백 제거 후 비어 있으면 null */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
