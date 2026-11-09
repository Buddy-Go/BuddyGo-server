package com.buddygo.buddygoserver.domain.manner;

import com.buddygo.buddygoserver.domain.manner.enums.MannerTemperatureReason;
import com.buddygo.buddygoserver.domain.manner.repository.MannerTemperatureLogRepository;
import com.buddygo.buddygoserver.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모임당 한 번뿐인 사유 목록이 세 곳에서 같아야 한다: enum 의 oncePerGathering, V8 부분 유니크 인덱스 조건,
 * 저장소 ON CONFLICT 조건. 인덱스에 빠지면 조용히 두 번 들어가고, ON CONFLICT 조건이 인덱스와 다르면 INSERT 가 실패한다.
 */
class OncePerGatheringReasonSyncTest extends IntegrationTest {

    private static final Pattern QUOTED = Pattern.compile("'([A-Z_]+)'");

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void 인덱스_조건_ON_CONFLICT_조건_enum_플래그가_같다() throws Exception {
        Set<String> flagged = Arrays.stream(MannerTemperatureReason.values())
                .filter(MannerTemperatureReason::isOncePerGathering)
                .map(Enum::name)
                .collect(Collectors.toSet());

        String indexDef = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'uk_manner_temperature_logs_once_per_gathering'",
                String.class);
        String insert = MannerTemperatureLogRepository.class
                .getMethod("insertOncePerGatheringIgnoringDuplicate", Long.class, java.math.BigDecimal.class,
                        String.class, Long.class, java.time.Instant.class)
                .getAnnotation(Query.class).value();
        String onConflict = insert.substring(insert.indexOf("ON CONFLICT"), insert.indexOf("DO NOTHING"));

        assertThat(quoted(indexDef)).isEqualTo(flagged);
        assertThat(quoted(onConflict)).isEqualTo(flagged);
    }

    private static Set<String> quoted(String text) {
        Matcher m = QUOTED.matcher(text);
        Set<String> values = new java.util.HashSet<>();
        while (m.find()) {
            values.add(m.group(1));
        }
        return values;
    }
}
