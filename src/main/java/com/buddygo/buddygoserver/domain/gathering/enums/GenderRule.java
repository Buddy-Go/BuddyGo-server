package com.buddygo.buddygoserver.domain.gathering.enums;

import com.buddygo.buddygoserver.domain.user.enums.Gender;

public enum GenderRule {
    ANY,
    FEMALE_ONLY,
    MALE_ONLY;

    public boolean allows(Gender gender) {
        return switch (this) {
            case ANY -> true;
            case FEMALE_ONLY -> gender == Gender.FEMALE;
            case MALE_ONLY -> gender == Gender.MALE;
        };
    }
}
