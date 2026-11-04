package com.buddygo.buddygoserver.domain.gathering.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 모임 장소. 앱이 카카오 장소 검색(또는 지도에서 찍은 집결지의 좌표→주소 변환) 결과를 그대로 보낸다.
 * 카카오 장소면 kakaoPlaceId 로 기존 장소를 재사용하고, 집결지는 kakaoPlaceId 없이 매번 새로 만든다.
 */
public record PlaceRequest(
        @Schema(description = "카카오 장소 ID. 지도에서 찍은 집결지는 비운다.", example = "27484196")
        @Size(max = 255)
        String kakaoPlaceId,

        @Schema(example = "버디짐 강남점")
        @NotBlank(message = "장소 이름은 필수입니다.")
        @Size(max = 255)
        String name,

        @Schema(example = "서울 강남구 테헤란로 123")
        @Size(max = 255)
        String roadAddress,

        @Schema(description = "지번 주소. 지도 핀 묶음 기준", example = "서울 강남구 역삼동 123-45")
        @NotBlank(message = "지번 주소는 필수입니다.")
        @Size(max = 255)
        String jibunAddress,

        @Schema(description = "법정동 코드(카카오 b_code, 10자리)", example = "1168010100")
        @NotNull(message = "법정동 코드는 필수입니다.")
        @Pattern(regexp = "\\d{10}", message = "법정동 코드는 숫자 10자리입니다.")
        String regionCode,

        @Schema(example = "37.5009")
        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90") @DecimalMax(value = "90")
        Double latitude,

        @Schema(example = "127.0364")
        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180") @DecimalMax(value = "180")
        Double longitude
) {
}
