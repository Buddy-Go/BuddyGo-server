# 1단계: 빌드
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# 빌드 스크립트만 먼저 복사해 의존성 레이어를 캐싱한다 (소스만 바뀌면 이 단계는 재사용).
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon > /dev/null

# 테스트는 CI(ci.yml)에서 돌리므로 여기선 실행 jar만 만든다. bootJar 옆에 생기는 -plain.jar는 제외한다.
COPY src src
RUN ./gradlew bootJar --no-daemon \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

# 2단계: 실행
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /app/app.jar app.jar
USER app

EXPOSE 8080

# 컨테이너 메모리 한도 기준으로 힙을 잡는다 (작은 인스턴스에서 OOM Kill 방지).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java", "-jar", "app.jar"]
