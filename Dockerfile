# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle 빌드 최적화를 위한 환경 변수 설정
# 네트워크 타임아웃 증가 및 재시도 설정
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.caching=true -Xmx2048m -XX:MaxMetaspaceSize=512m -Dorg.gradle.internal.http.connectionTimeout=120000 -Dorg.gradle.internal.http.socketTimeout=120000"
ENV GRADLE_USER_HOME=/root/.gradle

# 1) Gradle wrapper 먼저 복사하고 실행권한 부여
COPY gradlew ./gradlew
COPY gradle/ ./gradle/
RUN chmod +x ./gradlew

# 2) 프로젝트 메타만 먼저 복사해서 의존성 캐시
COPY build.gradle settings.gradle ./

# Gradle 네트워크 설정 파일 생성 (재시도 및 타임아웃 설정)
RUN mkdir -p /root/.gradle && \
    echo "systemProp.org.gradle.internal.http.connectionTimeout=120000" > /root/.gradle/gradle.properties && \
    echo "systemProp.org.gradle.internal.http.socketTimeout=120000" >> /root/.gradle/gradle.properties && \
    echo "systemProp.http.connectionTimeout=120000" >> /root/.gradle/gradle.properties && \
    echo "systemProp.http.socketTimeout=120000" >> /root/.gradle/gradle.properties

# Wrapper/의존성 확인(로그 자세히)
RUN ./gradlew --no-daemon --version || (echo "Gradle version check failed" && exit 1)
RUN cat gradle/wrapper/gradle-wrapper.properties

# 의존성 다운로드 (재시도 로직 포함)
RUN for i in 1 2 3; do \
        echo "Attempting dependency download (attempt $i/3)..."; \
        if ./gradlew --no-daemon --refresh-dependencies dependencies --configuration compileClasspath; then \
            echo "Dependency download successful!"; \
            exit 0; \
        else \
            echo "Dependency download failed (attempt $i/3)."; \
            if [ $i -eq 3 ]; then \
                echo "Dependency download failed after 3 attempts"; \
                exit 1; \
            fi; \
            echo "Retrying in 5 seconds..."; \
            sleep 5; \
        fi; \
    done

# 3) 소스 복사 후 빌드 (테스트 제외)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test --stacktrace --warning-mode all 2>&1 | tee /tmp/build.log || (echo "Build failed. Last 100 lines:" && tail -100 /tmp/build.log && exit 1)

# 빌드 결과 확인
RUN ls -lah /app/build/libs/ || (echo "JAR file not found in build/libs/" && exit 1)

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# build/libs 안의 산출물 이름이 일정치 않으면 와일드카드 사용
# 빌드 스테이지에서 JAR 파일이 생성되었는지 확인 후 복사
COPY --from=build /app/build/libs/*-SNAPSHOT.jar /app/app.jar
RUN ls -lah /app/app.jar || (echo "JAR file not found!" && exit 1)
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
