# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 1) Gradle wrapper 먼저 복사하고 실행권한 부여
COPY gradlew ./gradlew
COPY gradle/ ./gradle/
RUN chmod +x ./gradlew

# 2) 프로젝트 메타만 먼저 복사해서 의존성 캐시
COPY build.gradle settings.gradle ./
# (있다면) gradle.properties도 같이
# COPY gradle.properties ./

# Wrapper/의존성 확인(로그 자세히)
RUN ./gradlew --no-daemon --vers갑ion
RUN cat gradle/wrapper/gradle-wrapper.properties

# 3) 소스 복사 후 빌드 (테스트 제외)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test --info --stacktrace

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# build/libs 안의 산출물 이름이 일정치 않으면 와일드카드 사용
COPY --from=build /app/build/libs/*-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
