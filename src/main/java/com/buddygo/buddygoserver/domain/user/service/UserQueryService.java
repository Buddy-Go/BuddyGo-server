package com.buddygo.buddygoserver.domain.user.service;

import com.buddygo.buddygoserver.domain.user.dto.response.MyInfoResponse;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    public MyInfoResponse getMe(Long userId) {
        return userRepository.findById(userId)
                .map(MyInfoResponse::from)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
    }
}
