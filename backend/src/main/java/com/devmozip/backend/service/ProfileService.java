package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfileDetailResponse;
import com.devmozip.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {
    private final UserRepository userRepository;

    public ProfileDetailResponse findBySlug(String slug) {
        // 존재하지 않는 계정과 공개할 수 없는 계정은 동일하게 처리한다.
        return userRepository.findByTagAndStatus(slug, "ACTIVE")
                .map(ProfileDetailResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다."));
    }
}
