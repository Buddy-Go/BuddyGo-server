package com.buddygo.buddygoserver.domain.notification.push;

import java.util.List;
import java.util.Set;

/** 푸시 전송. 전송 실패는 던지지 않고 로그로 남긴다. */
public interface PushClient {

    /** @return 더 이상 쓸 수 없는 토큰(앱 삭제 등). 부르는 쪽이 지운다. */
    Set<String> send(List<PushMessage> messages);
}
