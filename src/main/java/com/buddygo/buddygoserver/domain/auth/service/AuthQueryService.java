package com.buddygo.buddygoserver.domain.auth.service;

import com.buddygo.buddygoserver.domain.auth.dto.response.AvailabilityResponse;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthQueryService {

    private final UserRepository userRepository;

    public AvailabilityResponse checkEmail(String email) {
        return new AvailabilityResponse(!userRepository.existsByEmail(EmailNormalizer.normalize(email)));
    }

    public AvailabilityResponse checkNickname(String nickname) {
        return new AvailabilityResponse(!userRepository.existsByNickname(nickname.strip()));
    }
}
