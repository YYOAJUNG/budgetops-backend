# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# gradle wrapper/설정만 먼저 복사 → 의존성 캐시
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
# 있으면 복사 (없으면 무시해도 OK)
# COPY gradle.properties ./

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true

# 소스 복사 후 빌드 (테스트 제외 추천)
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# 보안: non-root
RUN useradd -ms /bin/bash appuser
USER appuser

# JAR 복사 (버전 붙은 파일도 커버)
COPY --from=build /app/build/libs/*.jar /app/app.jar

# JVM 옵션 (필요시 조정)
ENV JAVA_OPTS="-Xms256m -Xmx512m -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# (선택) Actuator 사용 시 헬스체크
# HEALTHCHECK --interval=30s --timeout=3s CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
