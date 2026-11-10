package com.buddygo.buddygoserver.domain.notification.push;

import java.util.Map;

/** 기기 하나에 보낼 푸시. data 값은 FCM 규칙대로 모두 문자열이다. */
public record PushMessage(String token, String title, String body, Map<String, String> data) {
}
