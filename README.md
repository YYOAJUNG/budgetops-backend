# budgetops-backend

로컬 개발

명령어: 그냥 ./gradlew bootRun

적용: application.yml + application-local.yml

DB: H2(in-memory)

배포(OCI + docker-compose)

docker-compose.yml에서
SPRING_PROFILES_ACTIVE: docker 환경변수 사용

적용: application.yml + application-docker.yml

DB: Postgres (in 배포 서버)