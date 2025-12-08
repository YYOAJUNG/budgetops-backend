# BudgetOps 백엔드 테스트 가이드

## 목차
1. [테스트 전략](#테스트-전략)
2. [테스트 작성 기준](#테스트-작성-기준)
3. [테스트 구조](#테스트-구조)
4. [테스트 범위](#테스트-범위)
5. [테스트 실행](#테스트-실행)

---

## 테스트 전략

BudgetOps 백엔드는 **단위 테스트(Unit Test)** 중심의 테스트 전략을 사용합니다.

### 핵심 원칙
- **격리된 테스트**: 각 테스트는 독립적으로 실행 가능해야 함
- **빠른 실행**: 외부 의존성(DB, API) 없이 빠르게 실행
- **명확한 의도**: 테스트 이름과 DisplayName으로 테스트 목적 명시
- **신뢰성**: 테스트는 항상 동일한 결과를 반환해야 함

### 테스트 도구
- **JUnit 5**: 테스트 프레임워크
- **Mockito**: Mock 객체 생성 및 의존성 관리
- **AssertJ**: 가독성 높은 Assertion

---

## 테스트 작성 기준

### 1. 테스트 대상 선정
다음과 같은 컴포넌트에 대해 테스트를 작성합니다:

#### ✅ 필수 테스트 대상
- **Service 클래스**: 비즈니스 로직을 포함한 모든 서비스
- **Controller 클래스**: API 엔드포인트 (선택적)
- **복잡한 유틸리티 클래스**: 계산 로직, 변환 로직 등

#### ❌ 테스트 제외 대상
- **Entity 클래스**: 단순 데이터 클래스
- **DTO 클래스**: 단순 데이터 전송 객체
- **Repository 인터페이스**: Spring Data JPA가 관리

### 2. 테스트 메서드 명명 규칙

```java
@Test
@DisplayName("[메서드명] - [시나리오] [예상결과]")
void methodName_scenario_expectedResult() {
    // 테스트 코드
}
```

**예시:**
```java
@Test
@DisplayName("createWithVerify - 새 계정 생성 성공")
void createWithVerify_NewAccount_Success() {
    // ...
}

@Test
@DisplayName("createWithVerify - 중복된 Access Key로 예외 발생")
void createWithVerify_DuplicateAccessKey_ThrowsException() {
    // ...
}
```

### 3. Given-When-Then 패턴

모든 테스트는 **Given-When-Then** 구조를 따릅니다:

```java
@Test
@DisplayName("테스트 설명")
void testMethod() {
    // given (준비): 테스트에 필요한 데이터와 Mock 동작 설정
    given(repository.findById(1L)).willReturn(Optional.of(entity));
    
    // when (실행): 테스트 대상 메서드 실행
    Result result = service.someMethod(1L);
    
    // then (검증): 결과 검증
    assertThat(result).isNotNull();
    assertThat(result.getValue()).isEqualTo(expectedValue);
    verify(repository).findById(1L);
}
```

---

## 테스트 구조

### 기본 테스트 클래스 구조

```java
package com.budgetops.backend.[module].service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("[클래스명] 테스트")
class ServiceNameTest {

    @Mock
    private DependencyRepository repository;

    @Mock
    private DependencyService service;

    @InjectMocks
    private TargetService targetService;

    private TestEntity testEntity;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        testEntity = new TestEntity();
        testEntity.setId(1L);
        testEntity.setName("Test");
    }

    @Test
    @DisplayName("메서드명 - 성공 케이스")
    void methodName_Success() {
        // given
        given(repository.findById(1L)).willReturn(Optional.of(testEntity));
        
        // when
        var result = targetService.methodName(1L);
        
        // then
        assertThat(result).isNotNull();
        verify(repository).findById(1L);
    }

    @Test
    @DisplayName("메서드명 - 실패 케이스")
    void methodName_Failure() {
        // given
        given(repository.findById(1L)).willReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> targetService.methodName(1L))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Entity not found");
    }
}
```

### 주요 애노테이션

| 애노테이션 | 설명 |
|---------|------|
| `@ExtendWith(MockitoExtension.class)` | Mockito 기능 활성화 |
| `@DisplayName` | 테스트 설명 (한글 권장) |
| `@Mock` | Mock 객체 생성 |
| `@InjectMocks` | Mock을 주입받는 테스트 대상 객체 |
| `@BeforeEach` | 각 테스트 전 실행되는 초기화 메서드 |
| `@Test` | 테스트 메서드 표시 |

---

## 테스트 범위

### 현재 구현된 테스트 모듈

#### 1. AWS 관련 테스트
- `AwsAccountServiceTest`: AWS 계정 관리 (CRUD, 검증)
- `AwsEc2ServiceTest`: EC2 인스턴스 조회
- `AwsAlertServiceTest`: AWS 통합 알림 서비스
- `AwsEc2AlertServiceTest`: EC2 알림 규칙 검증
- `AwsCostServiceTest`: AWS 비용 조회

**주요 테스트 시나리오:**
- 계정 생성/조회/수정/삭제
- 중복 Access Key 검증
- EC2 인스턴스 목록 조회
- 알림 규칙 로딩 및 검증
- 임계치 초과 알림 생성
- 비용 데이터 조회

#### 2. GCP 관련 테스트
- `GcpAccountServiceTest`: GCP 계정 관리
- `GcpAccountControllerTest`: GCP API 엔드포인트
- `GcpAlertServiceTest`: GCP 알림 서비스
- `GcpCostServiceTest`: GCP 비용 조회

**주요 테스트 시나리오:**
- 계정 생성 (Service Account Key 검증)
- 리소스 조회
- 알림 규칙 검증
- 비용 데이터 조회

#### 3. Azure 관련 테스트
- `AzureAccountServiceTest`: Azure 계정 관리
- `AzureComputeServiceTest`: Azure VM 조회
- `AzureAlertServiceTest`: Azure 알림 서비스
- `AzureCostServiceTest`: Azure 비용 조회

**주요 테스트 시나리오:**
- 계정 생성 (Service Principal 검증)
- VM 목록 조회
- 알림 규칙 검증
- 비용 데이터 조회

#### 4. NCP 관련 테스트
- `NcpAccountServiceTest`: NCP 계정 관리
- `NcpServerServiceTest`: NCP 서버 조회
- `NcpAlertServiceTest`: NCP 알림 서비스
- `NcpCostServiceTest`: NCP 비용 조회

**주요 테스트 시나리오:**
- 계정 생성 (API Key 검증)
- 서버 인스턴스 조회
- 알림 규칙 검증
- 비용 데이터 조회

#### 5. 기타 모듈 테스트
- `BillingServiceTest`: 구독 및 결제 관리
- `BudgetServiceTest`: 예산 관리
- `PaymentServiceTest`: 결제 처리
- `NotificationSettingsServiceTest`: 알림 설정 관리
- `NotificationSettingsControllerTest`: 알림 설정 API
- `SlackNotificationServiceTest`: Slack 알림 발송

---

## 테스트 실행

### 1. 전체 테스트 실행

```bash
# Gradle을 통한 전체 테스트 실행
./gradlew test

# 테스트 결과 확인
./gradlew test --info
```

### 2. 특정 테스트 클래스 실행

```bash
# 특정 클래스만 실행
./gradlew test --tests "com.budgetops.backend.aws.service.AwsAccountServiceTest"
```

### 3. 특정 테스트 메서드 실행

```bash
# 특정 메서드만 실행
./gradlew test --tests "AwsAccountServiceTest.createWithVerify_NewAccount_Success"
```

### 4. 테스트 보고서 확인

테스트 실행 후 HTML 보고서가 생성됩니다:
```
build/reports/tests/test/index.html
```

---

## 테스트 작성 체크리스트

새로운 Service 클래스를 작성할 때 다음 체크리스트를 따르세요:

- [ ] 테스트 클래스 생성 (`[ServiceName]Test`)
- [ ] `@ExtendWith(MockitoExtension.class)` 추가
- [ ] `@DisplayName` 추가 (한글로 클래스 설명)
- [ ] 필요한 의존성 `@Mock` 선언
- [ ] 테스트 대상 `@InjectMocks` 선언
- [ ] `@BeforeEach`로 테스트 데이터 초기화
- [ ] 각 public 메서드에 대해 최소 2개 테스트 (성공/실패)
- [ ] Given-When-Then 패턴 준수
- [ ] AssertJ를 사용한 명확한 Assertion
- [ ] Mock 호출 검증 (`verify`)

---

## 테스트 작성 예시

### 예시 1: 계정 생성 성공 케이스

```java
@Test
@DisplayName("createWithVerify - 새 계정 생성 성공")
void createWithVerify_NewAccount_Success() {
    // given
    given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
    given(accountRepo.findByAccessKeyId(anyString())).willReturn(Optional.empty());
    given(accountRepo.save(any(AwsAccount.class))).willAnswer(invocation -> {
        AwsAccount account = invocation.getArgument(0);
        account.setId(101L);
        return account;
    });

    // when
    AwsAccount result = awsAccountService.createWithVerify(createRequest, 1L);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(101L);
    assertThat(result.getName()).isEqualTo("New AWS Account");
    assertThat(result.getActive()).isTrue();
    
    verify(memberRepository).findById(1L);
    verify(accountRepo).findByAccessKeyId(anyString());
    verify(accountRepo).save(any(AwsAccount.class));
}
```

### 예시 2: 계정 생성 실패 케이스 (중복 키)

```java
@Test
@DisplayName("createWithVerify - 중복된 Access Key로 예외 발생")
void createWithVerify_DuplicateAccessKey_ThrowsException() {
    // given
    given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
    given(accountRepo.findByAccessKeyId(anyString())).willReturn(Optional.of(testAccount));

    // when & then
    assertThatThrownBy(() -> awsAccountService.createWithVerify(createRequest, 1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("이미 등록된 Access Key입니다");
    
    verify(accountRepo).findByAccessKeyId(anyString());
    verify(accountRepo, never()).save(any());
}
```

### 예시 3: 알림 규칙 검증

```java
@Test
@DisplayName("checkAccount - 알림 규칙 로딩 및 검증 성공")
void checkAccount_LoadRulesAndCheck_Success() {
    // given
    AlertRule rule = new AlertRule();
    rule.setId("cpu_underutilized");
    rule.setTitle("CPU 저사용률");
    
    given(accountRepository.findById(1L)).willReturn(Optional.of(testAccount));
    given(ruleLoader.loadRules()).willReturn(List.of(rule));
    given(resourceService.listResources(anyLong(), anyLong()))
        .willReturn(new GcpResourceListResponse(List.of()));

    // when
    List<GcpAlert> alerts = gcpAlertService.checkAccount(1L);

    // then
    assertThat(alerts).isNotNull();
    verify(accountRepository).findById(1L);
    verify(ruleLoader).loadRules();
    verify(resourceService).listResources(anyLong(), anyLong());
}
```

---

## 테스트 커버리지 목표

현재 프로젝트의 테스트 커버리지 목표:

| 계층 | 목표 커버리지 | 현재 상태 |
|-----|-------------|----------|
| Service | 80% 이상 | ✅ 달성 |
| Controller | 60% 이상 | 🔄 진행중 |
| Repository | N/A | - |
| Util | 80% 이상 | 🔄 진행중 |

---

## 참고 자료

- [JUnit 5 공식 문서](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito 공식 문서](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ 공식 문서](https://assertj.github.io/doc/)

---

## 업데이트 이력

- 2024-12-08: 초기 테스트 가이드 작성
  - AWS, GCP, Azure, NCP 모듈 테스트 구현
  - Service 계층 테스트 80% 이상 달성

