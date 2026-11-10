package com.buddygo.buddygoserver.domain.notification.push;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/** Firebase 키가 없을 때(로컬·CI·테스트). 보내지 않고 로그만 남긴다. */
@Slf4j
public class DisabledPushClient implements PushClient {

    @Override
    public Set<String> send(List<PushMessage> messages) {
        log.debug("푸시 꺼짐: {}건을 보내지 않음", messages.size());
        return Set.of();
    }
}
