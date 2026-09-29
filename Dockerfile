# 백엔드 배포 이미지 (Render Docker 런타임). 프론트(frontend/)는 Vercel 이 따로 배포하므로 포함하지 않는다.

# ---- build ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 래퍼 스크립트: 윈도우에서 커밋돼 CRLF 가 섞여도 실행되도록 CR 제거 (.gitattributes 에 eol=lf 가 이미 있지만 이중 방어)
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 의존성만 먼저 받아 레이어 캐시 (소스가 바뀌어도 재다운로드 안 함)
RUN ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
# 테스트는 CI/로컬에서 돌린다 (Testcontainers 는 이미지 빌드 환경에 Docker 가 없다). 데몬은 빌드 1회용이라 끈다.
RUN ./gradlew bootJar -x test --no-daemon \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)" /workspace/app.jar

# ---- run ----
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app

# Render Free(512MB / 0.1 CPU) 시작값. Render 메모리 그래프를 보고 조정하고, 대시보드의 JAVA_TOOL_OPTIONS 로 덮어쓸 수 있다.
ENV JAVA_TOOL_OPTIONS="-Xmx300m -Xss512k -XX:MaxMetaspaceSize=150m -XX:+UseSerialGC -XX:ActiveProcessorCount=1"
ENV SPRING_PROFILES_ACTIVE=prod

# 포트는 Render 가 PORT 로 알려준다 (application-prod.yml: server.port=${PORT:8080})
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
