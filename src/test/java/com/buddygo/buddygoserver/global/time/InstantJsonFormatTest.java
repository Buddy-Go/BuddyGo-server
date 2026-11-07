package com.buddygo.buddygoserver.global.time;

import com.buddygo.buddygoserver.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 응답의 시각은 UTC ISO-8601 문자열(…Z)로 나간다는 앱과의 약속 */
class InstantJsonFormatTest extends IntegrationTest {

    @Autowired JsonMapper jsonMapper;

    @Test
    void Instant는_UTC_ISO_문자열로_직렬화된다() {
        String json = jsonMapper.writeValueAsString(Map.of("startAt", Instant.parse("2026-11-05T09:30:00Z")));

        assertThat(json).isEqualTo("{\"startAt\":\"2026-11-05T09:30:00Z\"}");
    }
}
