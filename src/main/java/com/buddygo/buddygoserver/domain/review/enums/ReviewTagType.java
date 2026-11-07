package com.buddygo.buddygoserver.domain.review.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 평가 태그. review_tags.tag 에 이름(코드)을 저장하고 문구는 태그 목록 API 로 내려준다.
 * 판정과 상관없이 고를 수 있고, 점수는 태그 성격(긍정·부정)대로 매긴다.
 */
@Getter
@RequiredArgsConstructor
public enum ReviewTagType {
    PUNCTUAL("시간 약속을 잘 지켜요", Polarity.POSITIVE),
    GOOD_MANNERS("매너가 좋아요", Polarity.POSITIVE),
    KIND("친절하고 말투가 편안해요", Polarity.POSITIVE),
    FUN_ATMOSPHERE("운동 분위기를 즐겁게 만들어줘요", Polarity.POSITIVE),
    GOOD_COACHING("자세·운동법을 잘 알려줘요", Polarity.POSITIVE),
    SIMILAR_LEVEL("운동 수준이 비슷해서 잘 맞았어요", Polarity.POSITIVE),
    SHARES_EQUIPMENT("기구를 배려하며 같이 써요", Polarity.POSITIVE),
    GOOD_SPOTTER("보조를 잘 해줘요", Polarity.POSITIVE),
    GOOD_COMMUNICATION("소통이 잘 돼요", Polarity.POSITIVE),
    WANT_AGAIN("또 같이 운동하고 싶어요", Polarity.POSITIVE),

    NOT_PUNCTUAL("시간 약속을 안 지켜요 (지각 포함)", Polarity.NEGATIVE),
    ON_PHONE("운동 중 휴대폰만 봐요", Polarity.NEGATIVE),
    UNCOMFORTABLE_TONE("말투가 불편했어요", Polarity.NEGATIVE),
    LEVEL_MISMATCH("운동 수준이 기준과 많이 달랐어요", Polarity.NEGATIVE),
    HOGS_EQUIPMENT("기구를 독점하거나 정리를 안 해요", Polarity.NEGATIVE),
    UNWANTED_ADVICE("운동 방식을 강요하거나 훈수를 둬요", Polarity.NEGATIVE),
    POOR_COMMUNICATION("소통이 잘 안 돼요", Polarity.NEGATIVE),
    NOT_AS_DESCRIBED("모임 설명과 달랐어요", Polarity.NEGATIVE);

    private final String label;
    private final Polarity polarity;

    public enum Polarity {
        POSITIVE,
        NEGATIVE
    }
}
