package com.buddygo.buddygoserver.domain.user.entity;

import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.enums.Provider;
import com.buddygo.buddygoserver.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}))
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** LOCAL 가입자만 · BCrypt */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Provider provider = Provider.LOCAL;

    /** 소셜 회원번호 */
    @Column(name = "provider_id")
    private String providerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(name = "manner_temperature", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal mannerTemperature = new BigDecimal("36.5");

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private boolean pushEnabled = true;

    /** 이메일(LOCAL) 가입. 이메일은 정규화된 값, 비밀번호는 해시된 값을 받는다. */
    public static User signUpWithEmail(String email, String passwordHash, String name, String nickname, Gender gender) {
        return User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .provider(Provider.LOCAL)
                .name(name)
                .nickname(nickname)
                .gender(gender)
                .build();
    }

    /** 마이페이지의 전체 알림 ON/OFF */
    public void changePushEnabled(boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }
}
