package com.buddygo.buddygoserver.domain.sport.gym.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 부위에 맞는 값만, 허용 범위 안으로 들어왔는지 검사한다. 오류는 recordValue · armLevel 필드에 붙는다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GymRecordValidator.class)
public @interface ValidGymRecord {

    String message() default "부위에 맞지 않는 기록입니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
