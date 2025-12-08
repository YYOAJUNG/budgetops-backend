# BudgetOps Backend

멀티 클라우드 비용 최적화 플랫폼 백엔드 API 서버

## 🚀 빠른 시작

### 로컬 개발

```bash
# 애플리케이션 실행
./gradlew bootRun

# 적용 프로파일
# - application.yml (공통 설정)
# - application-local.yml (로컬 환경)

# 데이터베이스: H2 (in-memory)
```

### 배포 환경

```bash
# Docker Compose로 배포
docker-compose up -d

# 적용 프로파일
# - application.yml (공통 설정)
# - application-docker.yml (배포 환경)

# 데이터베이스: PostgreSQL (배포 서버)
```

## 🧪 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "AwsAccountServiceTest"

# 테스트 보고서 확인
open build/reports/tests/test/index.html
```

## 📚 문서

모든 기술 문서는 [`docs/`](./docs/) 폴더에 있습니다:

- **[테스트 가이드](./docs/TESTING_GUIDE.md)** - 테스트 작성 기준 및 예시
- **[OAuth 통합](./docs/OAUTH_INTEGRATION.md)** - 소셜 로그인 구현 가이드
- **[AWS EC2 가이드](./docs/AWS_EC2_GUIDE.md)** - AWS EC2 서비스 통합
- **[MCP 통합 계획](./docs/MCP_INTEGRATION_PLAN.md)** - Model Context Protocol 통합

## 🏗️ 프로젝트 구조

```
src/
├── main/
│   ├── java/com/budgetops/backend/
│   │   ├── admin/          # 관리자 기능
│   │   ├── ai/             # AI 채팅 (Claude)
│   │   ├── aws/            # AWS 통합
│   │   ├── azure/          # Azure 통합
│   │   ├── billing/        # 구독/결제 관리
│   │   ├── budget/         # 예산 관리
│   │   ├── config/         # 설정
│   │   ├── costs/          # 비용 분석
│   │   ├── domain/         # 도메인 엔티티
│   │   ├── gcp/            # GCP 통합
│   │   ├── ncp/            # NCP 통합
│   │   ├── notification/   # 알림 (Slack 등)
│   │   ├── oauth/          # OAuth 2.0 인증
│   │   └── simulator/      # 비용 시뮬레이션 (UCAS)
│   └── resources/
│       ├── application*.yml
│       └── costs/          # 비용 최적화 규칙 (YAML)
└── test/
    └── java/com/           # 단위 테스트
```

## 🔧 주요 기능

- ☁️ **멀티 클라우드 통합**: AWS, GCP, Azure, NCP
- 💰 **비용 관리**: 실시간 비용 조회 및 분석
- 📊 **비용 시뮬레이션**: UCAS (Universal Cost Action Simulator)
- 🔔 **알림 시스템**: 임계치 기반 알림 (Slack 연동)
- 🤖 **AI 채팅**: Claude API 기반 비용 최적화 상담
- 💳 **구독/결제**: Stripe 연동
- 🔐 **OAuth 2.0**: Google, Kakao, Naver 소셜 로그인

## 🛠️ 기술 스택

- **Framework**: Spring Boot 3.2.x
- **Language**: Java 17
- **Database**: PostgreSQL (배포), H2 (개발)
- **Build Tool**: Gradle 9.0
- **Testing**: JUnit 5, Mockito, AssertJ
- **Cloud SDKs**: AWS SDK, GCP Java SDK, Azure SDK
- **AI**: Anthropic Claude API
- **Payment**: Stripe API

## 📦 빌드

```bash
# JAR 빌드
./gradlew clean bootJar

# 생성 위치
build/libs/budgetops-backend-0.0.1-SNAPSHOT.jar
```

## 🐳 Docker

```bash
# 이미지 빌드
docker build -t budgetops-backend .

# 컨테이너 실행
docker run -p 8080:8080 budgetops-backend
```

## 🌍 환경 변수

주요 환경 변수는 `application-docker.yml` 참고

## 📝 라이센스

Copyright © 2024 BudgetOps Team. All rights reserved.