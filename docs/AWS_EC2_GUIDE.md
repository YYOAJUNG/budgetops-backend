# AWS EC2 리소스 조회 API

feature/8 브랜치에 구현된 EC2 인스턴스 조회 기능입니다.

## 구현 내용

### 1. 새로 추가된 파일
- `AwsEc2Service.java`: EC2 인스턴스 조회 비즈니스 로직
- `AwsResourceController.java`: EC2 API 엔드포인트 추가

### 2. API 엔드포인트

#### 2.1 EC2 인스턴스 목록 조회
```
GET /api/aws/accounts/{accountId}/ec2/instances
```

**Parameters:**
- `accountId` (Path): AWS 계정 ID
- `region` (Query, Optional): 조회할 AWS 리전 (예: ap-northeast-2, us-east-1)

**Response:**
```json
[
  {
    "instanceId": "i-1234567890abcdef0",
    "name": "Production Web Server",
    "instanceType": "t3.micro",
    "state": "running",
    "availabilityZone": "ap-northeast-2a",
    "publicIp": "13.125.xxx.xxx",
    "privateIp": "172.31.xx.xx",
    "launchTime": "2024-11-06T10:30:00"
  }
]
```

#### 2.2 특정 EC2 인스턴스 상세 조회
```
GET /api/aws/accounts/{accountId}/ec2/instances/{instanceId}
```

**Parameters:**
- `accountId` (Path): AWS 계정 ID
- `instanceId` (Path): EC2 인스턴스 ID (예: i-1234567890abcdef0)
- `region` (Query, Optional): AWS 리전

**Response:**
```json
{
  "instanceId": "i-1234567890abcdef0",
  "name": "Production Web Server",
  "instanceType": "t3.micro",
  "state": "running",
  "availabilityZone": "ap-northeast-2a",
  "publicIp": "13.125.xxx.xxx",
  "privateIp": "172.31.xx.xx",
  "launchTime": "2024-11-06T10:30:00"
}
```

## 테스트 방법

### 1. AWS 계정 등록

먼저 AWS 계정을 등록해야 합니다.

```bash
curl -X POST http://localhost:8080/api/aws/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My AWS Account",
    "accessKeyId": "YOUR_ACCESS_KEY_ID",
    "secretAccessKey": "YOUR_SECRET_ACCESS_KEY",
    "defaultRegion": "ap-northeast-2"
  }'
```

**Response:**
```json
{
  "id": 1,
  "name": "My AWS Account",
  "defaultRegion": "ap-northeast-2",
  "accessKeyId": "YOUR_ACCESS_KEY_ID",
  "secretKeyLast4": "****XXXX",
  "active": true
}
```

### 2. 등록된 계정 목록 확인

```bash
curl http://localhost:8080/api/aws/accounts
```

### 3. EC2 인스턴스 조회

#### 기본 리전으로 조회
```bash
curl http://localhost:8080/api/aws/accounts/1/ec2/instances
```

#### 특정 리전 지정
```bash
# 서울 리전
curl http://localhost:8080/api/aws/accounts/1/ec2/instances?region=ap-northeast-2

# 버지니아 북부 리전
curl http://localhost:8080/api/aws/accounts/1/ec2/instances?region=us-east-1

# 도쿄 리전
curl http://localhost:8080/api/aws/accounts/1/ec2/instances?region=ap-northeast-1
```

#### 특정 인스턴스 상세 조회
```bash
curl http://localhost:8080/api/aws/accounts/1/ec2/instances/i-1234567890abcdef0?region=ap-northeast-2
```

### 4. Swagger UI로 테스트

브라우저에서 아래 URL로 접속:
```
http://localhost:8080/swagger-ui/index.html
```

`aws-resource-controller` 섹션에서 EC2 API를 확인할 수 있습니다.

## 필요한 IAM 권한

EC2 인스턴스를 조회하려면 다음 IAM 권한이 필요합니다:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ec2:DescribeInstances",
        "ec2:DescribeInstanceStatus",
        "ec2:DescribeTags"
      ],
      "Resource": "*"
    }
  ]
}
```

## 주요 리전 코드

| 리전 이름 | 리전 코드 |
|---------|----------|
| 서울 | ap-northeast-2 |
| 도쿄 | ap-northeast-1 |
| 싱가포르 | ap-southeast-1 |
| 미국 동부 (버지니아 북부) | us-east-1 |
| 미국 서부 (오레곤) | us-west-2 |

## 에러 처리

### 404 Not Found
- AWS 계정을 찾을 수 없음
- EC2 인스턴스를 찾을 수 없음

### 400 Bad Request
- 비활성화된 계정

### 500 Internal Server Error
- AWS 자격증명 오류
- 네트워크 오류
- 권한 부족

## 응답 필드 설명

- `instanceId`: EC2 인스턴스 ID
- `name`: Name 태그 값 (없으면 빈 문자열)
- `instanceType`: 인스턴스 타입 (예: t3.micro, t2.small)
- `state`: 인스턴스 상태 (running, stopped, pending, terminated 등)
- `availabilityZone`: 가용 영역 (예: ap-northeast-2a)
- `publicIp`: 퍼블릭 IP 주소 (없으면 빈 문자열)
- `privateIp`: 프라이빗 IP 주소
- `launchTime`: 인스턴스 시작 시간 (ISO-8601 형식)

## 보안 고려사항

1. **자격증명 암호화**: Secret Access Key는 DB에 암호화되어 저장됩니다.
2. **환경 변수**: `APP_CRED_KEY` 환경 변수를 반드시 설정해야 합니다.
3. **최소 권한 원칙**: EC2 조회에 필요한 최소한의 IAM 권한만 부여하세요.
4. **리전 제한**: 필요한 리전만 접근하도록 제한할 수 있습니다.

## 다음 구현 예정

- [ ] EBS 볼륨 조회
- [ ] Security Group 조회
- [ ] VPC 조회
- [ ] RDS 인스턴스 조회
- [ ] S3 버킷 조회
- [ ] Lambda 함수 조회

