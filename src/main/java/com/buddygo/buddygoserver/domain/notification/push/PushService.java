package com.buddygo.buddygoserver.domain.notification.push;

import com.buddygo.buddygoserver.domain.notification.push.PushEvent.Item;
import com.buddygo.buddygoserver.domain.notification.support.NotificationTarget;
import com.buddygo.buddygoserver.domain.user.repository.DeviceTokenRepository;
import com.buddygo.buddygoserver.domain.user.repository.DeviceTokenRepository.PushTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 받는 사람의 기기 토큰으로 푸시를 보낸다. 전체 알림을 끈 사람(push_enabled=false)은 보내지 않는다.
 * FCM 을 기다리는 동안 DB 커넥션을 잡지 않도록 토큰 조회·전송·토큰 삭제를 나눈다. 어떤 실패도 밖으로 던지지 않는다.
 */
@Slf4j
@Service
public class PushService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final PushClient pushClient;
    private final PushProperties properties;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;
    private final Clock clock;

    public PushService(DeviceTokenRepository deviceTokenRepository, PushClient pushClient, PushProperties properties,
                       PlatformTransactionManager transactionManager, Clock clock) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushClient = pushClient;
        this.properties = properties;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void push(List<Item> items) {
        try {
            Instant readAt = Instant.now(clock);
            Set<Long> receiverIds = items.stream().map(Item::receiverId).collect(Collectors.toSet());
            Map<Long, List<String>> tokens = readTx.execute(status ->
                    deviceTokenRepository.findPushTargets(receiverIds).stream()
                            .collect(Collectors.groupingBy(PushTarget::getUserId,
                                    Collectors.mapping(PushTarget::getToken, Collectors.toList()))));
            List<PushMessage> messages = items.stream()
                    .flatMap(item -> tokens.getOrDefault(item.receiverId(), List.of()).stream()
                            .map(token -> new PushMessage(token, properties.title(), item.message(), dataOf(item))))
                    .toList();
            if (messages.isEmpty()) {
                return;
            }
            Set<String> dead = pushClient.send(messages);
            if (!dead.isEmpty()) {
                // 읽은 뒤 다시 등록된 토큰(updated_at 이 더 늦음)은 살아 있으므로 지우지 않는다
                Integer deleted = writeTx.execute(status -> deviceTokenRepository.deleteStale(dead, readAt));
                log.info("만료된 푸시 토큰 {}개를 지웠습니다.", deleted);
            }
        } catch (RuntimeException e) {
            log.warn("푸시 처리 실패: 알림 {}건", items.size(), e);
        }
    }

    /** 알림을 눌렀을 때 앱이 쓸 값: 종류, 읽음 처리할 알림 ID, 이동할 대상 */
    private static Map<String, String> dataOf(Item item) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", item.type().name());
        data.put("notificationId", String.valueOf(item.notificationId()));
        NotificationTarget target = item.target();
        if (target.gatheringId() != null) {
            data.put("gatheringId", String.valueOf(target.gatheringId()));
        }
        if (target.noticeId() != null) {
            data.put("noticeId", String.valueOf(target.noticeId()));
        }
        if (target.buddyRequestId() != null) {
            data.put("buddyRequestId", String.valueOf(target.buddyRequestId()));
        }
        return data;
    }
}
