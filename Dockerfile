# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle 빌드 최적화를 위한 환경 변수 설정
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.caching=true -Xmx2048m -XX:MaxMetaspaceSize=512m"

# 1) Gradle wrapper 먼저 복사하고 실행권한 부여
COPY gradlew ./gradlew
COPY gradle/ ./gradle/
RUN chmod +x ./gradlew

# 2) 프로젝트 메타만 먼저 복사해서 의존성 캐시
COPY build.gradle settings.gradle ./
# (있다면) gradle.properties도 같이
# COPY gradle.properties ./

# Wrapper/의존성 확인(로그 자세히)
RUN ./gradlew --no-daemon --version || (echo "Gradle version check failed" && exit 1)
RUN cat gradle/wrapper/gradle-wrapper.properties

# 의존성 다운로드 (빌드 전에 미리 다운로드하여 실패 원인 파악)
RUN ./gradlew --no-daemon dependencies --configuration compileClasspath || (echo "Dependency download failed" && exit 1)

# 3) 소스 복사 후 빌드 (테스트 제외)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test --stacktrace --warning-mode all 2>&1 | tee /tmp/build.log || (echo "Build failed. Last 100 lines:" && tail -100 /tmp/build.log && exit 1)

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# build/libs 안의 산출물 이름이 일정치 않으면 와일드카드 사용
COPY --from=build /app/build/libs/*-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
