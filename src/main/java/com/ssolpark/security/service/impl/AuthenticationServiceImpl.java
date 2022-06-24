package com.ssolpark.security.service.impl;

import com.ssolpark.security.common.ApiResponse;
import com.ssolpark.security.common.ResponseType;
import com.ssolpark.security.common.DataApiResponse;
import com.ssolpark.security.dto.RegMemberDto;
import com.ssolpark.security.dto.auth.JwtRequest;
import com.ssolpark.security.dto.auth.JwtResponse;
import com.ssolpark.security.exception.BusinessException;
import com.ssolpark.security.model.Member;
import com.ssolpark.security.model.MemberRefreshToken;
import com.ssolpark.security.repository.MemberRefreshTokenRepository;
import com.ssolpark.security.repository.MemberRepository;
import com.ssolpark.security.security.JwtProvider;
import com.ssolpark.security.service.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    @Value("${refreshToken.duration}")
    private long REFRESH_TOKEN_VALID_TIME;

    private final MemberRepository memberRepo;

    private final MemberRefreshTokenRepository memberRefreshTokenRepo;

    private final JwtProvider jwtProvider;

    private final PasswordEncoder passwordEncoder;

    public AuthenticationServiceImpl(MemberRepository memberRepo, MemberRefreshTokenRepository memberRefreshTokenRepo, JwtProvider jwtProvider, PasswordEncoder passwordEncoder) {
        this.memberRepo = memberRepo;
        this.memberRefreshTokenRepo = memberRefreshTokenRepo;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public ApiResponse registration(RegMemberDto regMemberDto) {

        Member member = findByEmail(regMemberDto.getEmail());

        if(member != null) {
            return new ApiResponse(ResponseType.REGISTERED_MEMBER.getCode(), ResponseType.REGISTERED_MEMBER.getMessage());
        }

        Member saveMember = Member.builder()
                .email(regMemberDto.getEmail())
                .password(passwordEncoder.encode(regMemberDto.getPassword()))
                .name(regMemberDto.getName())
                .build();

        memberRepo.save(saveMember);

        return new ApiResponse(ResponseType.SUCCESS.getCode(), ResponseType.SUCCESS.getMessage());
    }

    @Override
    public DataApiResponse authenticateForJwt(JwtRequest jwtRequest) {

        Member member;

        switch (jwtRequest.getGrantType()) {
            case CLIENT_CREDENTIALS:
                member = authEmailAndPassword(jwtRequest).orElseThrow(() -> new BusinessException(ResponseType.WRONG_EMAIL_OR_PASSWORD));
                
                break;
            case REFRESH_TOKEN:
                MemberRefreshToken refreshToken = memberRefreshTokenRepo.findByRefreshToken(jwtRequest.getRefreshToken())
                        .orElseThrow(() -> new BusinessException(ResponseType.REFRESH_TOKEN_NOT_FOUND));

                if(refreshToken.getExpiredOn().before(new Date())) {
                    throw new BusinessException(ResponseType.REFRESH_TOKEN_EXPIRED);
                }

                if(refreshToken.getMember() == null) {
                    throw new BusinessException(ResponseType.MEMBER_NOT_FOUND);
                }

                member = refreshToken.getMember();

                break;
            default:
                throw new BusinessException(ResponseType.GRANT_TYPE_NOT_FOUND);
        }

        JwtResponse jwtResponse = processJwt(member);

        return new DataApiResponse(jwtResponse);
    }

    private JwtResponse processJwt(Member member) {

        final String email = member.getEmail();

        JwtResponse jwtResponse = jwtProvider.generateToken(email);

        MemberRefreshToken refToken = memberRefreshTokenRepo.findById(member.getMemberId()).orElse(null);

        if(refToken != null && refToken.getExpiredOn().after(new Date())) {

            log.info("FOUND A VALID REFRESH TOKEN, EMAIL : {}", email);
            jwtResponse.setRefreshToken(refToken.getRefreshToken());

        } else {

            log.info("START CREATING A NEW REFRESH TOKEN, EMAIL : {}", email);

            jwtResponse.setRefreshToken(UUID.randomUUID().toString().replace("-","").toLowerCase());

            Date expiredDate = new Date(System.currentTimeMillis() + REFRESH_TOKEN_VALID_TIME * 1000);

            MemberRefreshToken memberRefreshToken = MemberRefreshToken.builder()
                    .refreshToken(jwtResponse.getRefreshToken())
                    .member(member)
                    .expiredOn(expiredDate)
                    .build();

            memberRefreshTokenRepo.save(memberRefreshToken);

            log.info("END CREATING A NEW REFRESH TOKEN, EXPIRED_DATE : {}, EMAIL : {}", expiredDate, email);
        }

        return jwtResponse;
    }

    private Optional<Member> authEmailAndPassword(JwtRequest jwtRequest) {

        Member member = findByEmail(jwtRequest.getEmail());

        if(member != null) {
            return passwordEncoder.matches(jwtRequest.getPassword(), member.getPassword()) ? Optional.of(member) : Optional.empty();
        }

        return Optional.empty();
    }

    private Member findByEmail(String email) {
        return memberRepo.findByEmail(email).orElse(null);
    }
}
