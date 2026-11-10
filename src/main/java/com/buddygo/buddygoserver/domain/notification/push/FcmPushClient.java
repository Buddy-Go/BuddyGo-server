package com.buddygo.buddygoserver.domain.notification.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FCM HTTP v1 로 보낸다. sendEach 한 번에 최대 500건이라 나눠 보낸다.
 * 토큰이 없어졌다고 확실한 UNREGISTERED(앱 삭제·토큰 만료)만 지울 토큰으로 돌려준다. INVALID_ARGUMENT(메시지가 잘못돼도 남)와
 * SENDER_ID_MISMATCH(서버에 다른 Firebase 프로젝트 키를 넣어도 남)는 서버 설정 실수 한 번에 모든 토큰을 지울 수 있어 로그만 남긴다.
 */
@Slf4j
@RequiredArgsConstructor
public class FcmPushClient implements PushClient {

    static final int BATCH_SIZE = 500;

    private final FirebaseMessaging messaging;

    @Override
    public Set<String> send(List<PushMessage> messages) {
        Set<String> dead = new HashSet<>();
        for (int from = 0; from < messages.size(); from += BATCH_SIZE) {
            List<PushMessage> batch = messages.subList(from, Math.min(from + BATCH_SIZE, messages.size()));
            try {
                BatchResponse response = messaging.sendEach(batch.stream().map(FcmPushClient::toMessage).toList());
                collectFailures(batch, response.getResponses(), dead);
            } catch (FirebaseMessagingException e) {
                log.warn("푸시 {}건 전송 실패: {}", batch.size(), e.getMessagingErrorCode(), e);
            }
        }
        return dead;
    }

    private static void collectFailures(List<PushMessage> batch, List<SendResponse> responses, Set<String> dead) {
        for (int i = 0; i < responses.size(); i++) {
            SendResponse response = responses.get(i);
            if (response.isSuccessful()) {
                continue;
            }
            MessagingErrorCode code = response.getException().getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED) {
                dead.add(batch.get(i).token());
            } else {
                log.warn("푸시 전송 실패: {}", code, response.getException());
            }
        }
    }

    private static Message toMessage(PushMessage message) {
        return Message.builder()
                .setToken(message.token())
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .build();
    }
}
