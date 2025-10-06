# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 1) Gradle wrapper & 메타 먼저 복사 (캐시층 최적화)
COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# 2) gradlew 실행권한 부여
RUN chmod +x gradlew

# (선택) 의존성 미리 내려받아 캐시 생성
# 실패해도 전체 빌드 계속되도록 || true
RUN ./gradlew --no-daemon dependencies || true

# 3) 소스 복사 후 빌드
COPY src/ src/
# 디버깅이 필요하면 --info --stacktrace 붙이세요
RUN ./gradlew --no-daemon clean bootJar -x test --info --stacktrace

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
