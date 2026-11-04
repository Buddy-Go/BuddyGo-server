package com.buddygo.buddygoserver.domain.sport.gym.dto.request;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class GymRecordValidator implements ConstraintValidator<ValidGymRecord, GymRecordRequest> {

    static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("300");
    static final BigDecimal MAX_REPS = new BigDecimal("100");

    @Override
    public boolean isValid(GymRecordRequest record, ConstraintValidatorContext context) {
        if (record == null || record.bodyPart() == null) {
            return true; // 부위 누락은 @NotNull 이 잡는다
        }
        String error;
        String field;
        switch (record.bodyPart().getRecordType()) {
            case WEIGHT -> {
                field = record.armLevel() != null ? "armLevel" : "recordValue";
                error = record.armLevel() != null ? "숙련도는 팔에만 입력합니다." : checkWeight(record.recordValue());
            }
            case REPS -> {
                field = record.armLevel() != null ? "armLevel" : "recordValue";
                error = record.armLevel() != null ? "숙련도는 팔에만 입력합니다." : checkReps(record.recordValue());
            }
            case LEVEL -> {
                field = record.recordValue() != null ? "recordValue" : "armLevel";
                error = record.recordValue() != null ? "팔은 숙련도만 입력합니다."
                        : record.armLevel() == null ? "팔은 숙련도가 필수입니다." : null;
            }
            default -> throw new IllegalStateException("Unexpected record type: " + record.bodyPart());
        }
        if (error == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(error).addPropertyNode(field).addConstraintViolation();
        return false;
    }

    private static String checkWeight(BigDecimal value) {
        if (value == null) {
            return "1RM(kg)은 필수입니다.";
        }
        if (value.stripTrailingZeros().scale() > 0) {
            return "1RM은 정수(kg)여야 합니다.";
        }
        if (value.signum() <= 0 || value.compareTo(MAX_WEIGHT_KG) > 0) {
            return "1RM은 1kg 이상 " + MAX_WEIGHT_KG + "kg 이하여야 합니다.";
        }
        return null;
    }

    private static String checkReps(BigDecimal value) {
        if (value == null) {
            return "턱걸이 횟수는 필수입니다.";
        }
        if (value.stripTrailingZeros().scale() > 0) {
            return "턱걸이 횟수는 정수여야 합니다.";
        }
        if (value.signum() < 0 || value.compareTo(MAX_REPS) > 0) {
            return "턱걸이 횟수는 0회 이상 " + MAX_REPS + "회 이하여야 합니다.";
        }
        return null;
    }
}
