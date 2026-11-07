package com.buddygo.buddygoserver.domain.user.entity;

import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.enums.Provider;
import com.buddygo.buddygoserver.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /** 소수 2자리로 저장하고 응답에는 1자리로 보여준다(getMannerTemperature). 바꾸는 곳은 applyMannerDelta 뿐이다. */
    @Column(name = "manner_temperature", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal mannerTemperature = new BigDecimal("36.50");

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

    /** 화면에 보여주는 매너온도(소수 1자리 반올림). 모든 응답은 이 값을 쓴다. */
    public BigDecimal getMannerTemperature() {
        return mannerTemperature.setScale(1, RoundingMode.HALF_UP);
    }

    /** 저장된 그대로의 매너온도(소수 2자리). 온도 변화를 계산할 때만 쓴다. */
    public BigDecimal getExactMannerTemperature() {
        return mannerTemperature;
    }

    /** 곡선을 적용한 변화량을 더한다. 동시 변경이 유실되지 않도록 사용자 행을 잠근 뒤 불러야 한다. */
    public void applyMannerDelta(BigDecimal delta) {
        this.mannerTemperature = mannerTemperature.add(delta);
    }

    /** 마이페이지의 전체 알림 ON/OFF */
    public void changePushEnabled(boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }
}
