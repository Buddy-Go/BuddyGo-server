package com.buddygo.buddygoserver.domain.notification.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FCM SDK 는 목으로 바꾸고, 메시지 변환·나눠 보내기·지울 토큰 판정만 확인한다. */
class FcmPushClientTest {

    final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    final FcmPushClient client = new FcmPushClient(messaging);

    @Test
    void UNREGISTERED_토큰만_지울_토큰으로_돌려준다() throws Exception {
        List<SendResponse> responses = List.of(success(), failure(MessagingErrorCode.UNREGISTERED),
                failure(MessagingErrorCode.SENDER_ID_MISMATCH), failure(MessagingErrorCode.INVALID_ARGUMENT),
                failure(MessagingErrorCode.UNAVAILABLE));
        BatchResponse batch = batch(responses);
        when(messaging.sendEach(anyList())).thenReturn(batch);

        assertThat(client.send(messages(5))).containsExactly("token-1");
    }

    @Test
    void 메시지를_FCM_형식으로_바꾼다() throws Exception {
        BatchResponse batch = batch(List.of(success()));
        when(messaging.sendEach(anyList())).thenReturn(batch);

        client.send(List.of(new PushMessage("token-0", "버디고", "본문",
                Map.of("type", "APPLICATION_ACCEPTED", "notificationId", "7", "gatheringId", "3"))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(messaging).sendEach(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void 오백건씩_나눠_보내고_한_묶음이_실패해도_나머지는_보낸다() throws Exception {
        FirebaseMessagingException error = mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);
        BatchResponse second = batch(List.of(failure(MessagingErrorCode.UNREGISTERED)));
        when(messaging.sendEach(anyList())).thenThrow(error).thenReturn(second);

        // 501건 = 500 + 1. 첫 묶음은 통째로 실패, 둘째 묶음(token-500)은 UNREGISTERED
        assertThat(client.send(messages(FcmPushClient.BATCH_SIZE + 1))).containsExactly("token-500");
        verify(messaging, times(2)).sendEach(anyList());
    }

    private static List<PushMessage> messages(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new PushMessage("token-" + i, "버디고", "본문", Map.of("type", "APPLICATION_ACCEPTED")))
                .toList();
    }

    private static BatchResponse batch(List<SendResponse> responses) {
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(responses);
        return batch;
    }

    private static SendResponse success() {
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(true);
        return response;
    }

    private static SendResponse failure(MessagingErrorCode code) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(false);
        when(response.getException()).thenReturn(exception);
        return response;
    }
}
