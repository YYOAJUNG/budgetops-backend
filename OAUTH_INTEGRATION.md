# Google OAuth2 로그인 통합 가이드

## 목차
1. [아키텍처 개요](#아키텍처-개요)
2. [백엔드 구성 요소](#백엔드-구성-요소)
3. [OAuth2 인증 플로우](#oauth2-인증-플로우)
4. [프론트엔드 연동 방법](#프론트엔드-연동-방법)
5. [설정 방법](#설정-방법)
6. [API 엔드포인트](#api-엔드포인트)

---

## 아키텍처 개요

이 프로젝트는 **Spring Security OAuth2 Client**를 사용하여 Google OAuth2.0 로그인을 구현합니다.

### 주요 특징
- **세션 기반 인증**: 로그인 성공 시 서버에서 세션을 생성하고 쿠키로 관리
- **단순하고 확장 가능한 구조**: 최소한의 설정으로 로그인 기능만 구현, 나중에 JWT 등으로 변경 용이
- **CORS 설정 완료**: 프론트엔드(localhost:3000)와의 통신 가능

---

## 백엔드 구성 요소

### 1. 의존성 (build.gradle)

```gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
```

### 2. 설정 파일 (application-local.yml)

**위치**: `src/main/resources/application-local.yml`

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - email
              - profile
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          google:
            authorization-uri: https://accounts.google.com/o/oauth2/v2/auth
            token-uri: https://oauth2.googleapis.com/token
            user-info-uri: https://www.googleapis.com/oauth2/v3/userinfo

app:
  oauth2:
    redirect-uri: http://localhost:3000/oauth/callback
```

### 3. Security Configuration

**파일**: `src/main/java/com/budgetops/backend/config/SecurityConfig.java`

**주요 기능**:
- CSRF 비활성화 (개발 편의성)
- CORS 설정 (프론트엔드 통신 허용)
- 세션 관리 (IF_REQUIRED: OAuth2 로그인 시에만 세션 생성)
- 인증 예외 경로 설정 (Swagger, Actuator 등)
- OAuth2 로그인 설정 (성공/실패 핸들러 연결)

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    // 성공/실패 핸들러 주입
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        // CSRF 비활성화, CORS 설정, 세션 관리, OAuth2 로그인 설정
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // localhost:3000에서의 요청 허용
        // credentials: true (쿠키 포함)
    }
}
```

### 4. OAuth2 로그인 핸들러

#### 4.1 성공 핸들러

**파일**: `src/main/java/com/budgetops/backend/oauth/handler/OAuth2AuthenticationSuccessHandler.java`

**역할**:
- Google 로그인 성공 시 사용자 정보 로깅
- 프론트엔드 콜백 URL로 리다이렉트

```java
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(...) {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        // 프론트엔드 콜백 페이지로 리다이렉트
        getRedirectStrategy().sendRedirect(request, response, redirectUri);
    }
}
```

#### 4.2 실패 핸들러

**파일**: `src/main/java/com/budgetops/backend/oauth/handler/OAuth2AuthenticationFailureHandler.java`

**역할**:
- Google 로그인 실패 시 에러 로깅
- 에러 메시지와 함께 프론트엔드 콜백 URL로 리다이렉트

```java
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationFailure(...) {
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", exception.getLocalizedMessage())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
```

### 5. 사용자 정보 API

#### 5.1 DTO

**파일**: `src/main/java/com/budgetops/backend/oauth/dto/UserInfo.java`

```java
@Getter
@Builder
public class UserInfo {
    private String email;   // 사용자 이메일
    private String name;    // 사용자 이름
    private String picture; // 프로필 이미지 URL
}
```

#### 5.2 컨트롤러

**파일**: `src/main/java/com/budgetops/backend/oauth/controller/AuthController.java`

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/user")
    public ResponseEntity<UserInfo> getCurrentUser(@AuthenticationPrincipal OAuth2User oAuth2User) {
        if (oAuth2User == null) {
            return ResponseEntity.status(401).build(); // 인증되지 않음
        }

        UserInfo userInfo = UserInfo.builder()
                .email(oAuth2User.getAttribute("email"))
                .name(oAuth2User.getAttribute("name"))
                .picture(oAuth2User.getAttribute("picture"))
                .build();

        return ResponseEntity.ok(userInfo);
    }
}
```

---

## OAuth2 인증 플로우

```
1. [프론트엔드]
   사용자가 "Google로 로그인" 버튼 클릭
   ↓
   window.location.href = 'http://localhost:8080/oauth2/authorization/google'

2. [백엔드 - Spring Security]
   Google 로그인 페이지로 리다이렉트
   ↓

3. [Google]
   사용자가 Google 계정으로 로그인
   ↓
   승인 후 백엔드로 인증 코드 전달
   (리다이렉트: http://localhost:8080/login/oauth2/code/google?code=...)

4. [백엔드 - Spring Security]
   인증 코드를 사용해 Google에게 액세스 토큰 요청
   ↓
   액세스 토큰으로 사용자 정보 요청
   ↓
   세션 생성 및 쿠키 발급
   ↓
   OAuth2AuthenticationSuccessHandler 실행
   ↓
   프론트엔드로 리다이렉트 (http://localhost:3000/oauth/callback)

5. [프론트엔드]
   콜백 페이지에서 사용자 정보 API 호출
   ↓
   GET http://localhost:8080/api/auth/user (credentials: 'include')
   ↓
   사용자 정보 받아서 상태 관리 및 홈으로 이동
```

---

## 프론트엔드 연동 방법

### 1. 로그인 버튼

사용자가 클릭하면 백엔드의 OAuth2 엔드포인트로 이동합니다.

```javascript
// React 예시
<button onClick={() => {
  window.location.href = 'http://localhost:8080/oauth2/authorization/google';
}}>
  Google로 로그인
</button>
```

### 2. OAuth 콜백 페이지 (`/oauth/callback`)

Google 로그인 성공 후 리다이렉트되는 페이지입니다.

```javascript
// src/pages/OAuthCallback.jsx
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

function OAuthCallback() {
  const navigate = useNavigate();

  useEffect(() => {
    // URL에서 에러 파라미터 확인
    const urlParams = new URLSearchParams(window.location.search);
    const error = urlParams.get('error');

    if (error) {
      console.error('로그인 실패:', error);
      navigate('/login');
      return;
    }

    // 로그인 성공 - 사용자 정보 가져오기
    fetch('http://localhost:8080/api/auth/user', {
      credentials: 'include' // 중요! 세션 쿠키 포함
    })
      .then(res => {
        if (res.status === 401) {
          throw new Error('Unauthorized');
        }
        return res.json();
      })
      .then(userInfo => {
        console.log('사용자 정보:', userInfo);
        // 상태 관리(Redux, Context 등)에 저장
        // setUser(userInfo);
        navigate('/home');
      })
      .catch(err => {
        console.error('사용자 정보 가져오기 실패:', err);
        navigate('/login');
      });
  }, [navigate]);

  return <div>로그인 처리 중...</div>;
}

export default OAuthCallback;
```

### 3. 로그인 상태 확인

앱 시작 시 또는 페이지 이동 시 로그인 상태를 확인합니다.

```javascript
// React 예시 - App.jsx 또는 useAuth hook
useEffect(() => {
  fetch('http://localhost:8080/api/auth/user', {
    credentials: 'include' // 세션 쿠키 포함
  })
    .then(res => {
      if (res.status === 401) {
        return null; // 로그인하지 않음
      }
      return res.json();
    })
    .then(userInfo => {
      if (userInfo) {
        setUser(userInfo); // 로그인됨
      }
    })
    .catch(err => {
      console.error('로그인 상태 확인 실패:', err);
    });
}, []);
```

### 4. 중요: credentials: 'include'

**모든 API 요청에 `credentials: 'include'` 옵션을 추가해야 합니다.**

이 옵션이 없으면 브라우저가 세션 쿠키를 전송하지 않아 인증이 실패합니다.

```javascript
// Axios 사용 시
axios.defaults.withCredentials = true;

// Fetch 사용 시
fetch('http://localhost:8080/api/auth/user', {
  credentials: 'include'
})
```

---

## 설정 방법

### 1. Google Cloud Console 설정

1. https://console.cloud.google.com/ 접속
2. 프로젝트 생성 또는 선택
3. "API 및 서비스" > "사용자 인증 정보" 이동
4. "사용자 인증 정보 만들기" > "OAuth 2.0 클라이언트 ID" 선택
5. 애플리케이션 유형: "웹 애플리케이션"
6. 승인된 리디렉션 URI 추가:
   ```
   http://localhost:8080/login/oauth2/code/google
   ```
7. 생성된 **클라이언트 ID**와 **클라이언트 보안 비밀번호** 복사

### 2. 로컬 환경변수 설정

#### Option 1: 터미널에서 설정 (매번 실행 필요)

```bash
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret
```

#### Option 2: application-local.yml에 직접 입력 (권장하지 않음)

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: 실제-클라이언트-ID
            client-secret: 실제-클라이언트-시크릿
```

⚠️ **주의**: Git에 커밋하지 않도록 주의! `.gitignore`에 추가 권장

#### Option 3: IntelliJ Run Configuration 사용

1. Run > Edit Configurations
2. Environment variables에 추가:
   ```
   GOOGLE_CLIENT_ID=your-client-id;GOOGLE_CLIENT_SECRET=your-secret
   ```

### 3. 백엔드 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

서버가 `http://localhost:8080`에서 실행됩니다.

### 4. 프론트엔드 설정

프론트엔드가 `http://localhost:3000`이 아닌 다른 포트를 사용한다면:

**SecurityConfig.java** 수정:
```java
configuration.setAllowedOrigins(List.of("http://localhost:YOUR_PORT"));
```

**application-local.yml** 수정:
```yaml
app:
  oauth2:
    redirect-uri: http://localhost:YOUR_PORT/oauth/callback
```

---

## API 엔드포인트

### 1. OAuth2 로그인 시작

**URL**: `GET /oauth2/authorization/google`

**설명**: Google 로그인 페이지로 리다이렉트

**사용 예시**:
```javascript
window.location.href = 'http://localhost:8080/oauth2/authorization/google';
```

### 2. OAuth2 콜백 (자동 처리)

**URL**: `GET /login/oauth2/code/google`

**설명**: Google에서 인증 코드를 받아 처리 (Spring Security가 자동으로 처리)

**프론트엔드 호출 불필요**

### 3. 현재 사용자 정보 조회

**URL**: `GET /api/auth/user`

**인증**: 필수 (세션 쿠키)

**응답 성공 (200)**:
```json
{
  "email": "user@example.com",
  "name": "홍길동",
  "picture": "https://lh3.googleusercontent.com/..."
}
```

**응답 실패 (401)**:
```
Unauthorized
```

**사용 예시**:
```javascript
fetch('http://localhost:8080/api/auth/user', {
  credentials: 'include'
})
  .then(res => res.json())
  .then(userInfo => console.log(userInfo));
```

---

## 테스트 시나리오

### 성공 시나리오

1. 프론트엔드에서 "Google로 로그인" 버튼 클릭
2. Google 로그인 페이지로 이동
3. Google 계정으로 로그인 및 권한 승인
4. `/oauth/callback` 페이지로 리다이렉트
5. 자동으로 `/api/auth/user` 호출하여 사용자 정보 표시
6. 홈 페이지로 이동

### 실패 시나리오

1. Google 로그인 중 사용자가 취소 또는 거부
2. `/oauth/callback?error=...` 로 리다이렉트
3. 에러 메시지 표시 후 로그인 페이지로 이동

---

## 향후 개선 방향

현재 구현은 **세션 기반**으로 되어 있어 확장성에 제한이 있습니다. 향후 다음과 같이 개선할 수 있습니다:

### 1. JWT 토큰 기반 인증

**변경 필요 파일**:
- `OAuth2AuthenticationSuccessHandler`: JWT 생성 후 프론트엔드로 전달
- `SecurityConfig`: JWT 필터 추가
- 프론트엔드: JWT를 로컬 스토리지에 저장, Authorization 헤더에 포함

### 2. User 엔티티 및 DB 저장

**추가 필요 사항**:
- `User` 엔티티 생성
- `UserRepository` 생성
- `OAuth2AuthenticationSuccessHandler`에서 사용자 정보 DB 저장
- 이메일 기반 중복 체크 로직

### 3. 로그아웃 기능

**추가 엔드포인트**:
```java
@PostMapping("/api/auth/logout")
public ResponseEntity<Void> logout(HttpServletRequest request) {
    request.getSession().invalidate();
    return ResponseEntity.ok().build();
}
```

### 4. 다중 OAuth2 제공자 지원

현재는 Google만 지원하지만, GitHub, Facebook 등 추가 가능:

**application-local.yml**:
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            # ...
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
```

---

## 트러블슈팅

### 1. CORS 에러

**증상**: 프론트엔드에서 API 호출 시 CORS 에러

**해결**:
- `SecurityConfig.java`의 `allowedOrigins`에 프론트엔드 URL 추가
- `credentials: 'include'` 옵션 사용

### 2. 401 Unauthorized

**증상**: `/api/auth/user` 호출 시 401 에러

**원인**:
- 세션 쿠키가 전송되지 않음
- 로그인하지 않은 상태

**해결**:
- `credentials: 'include'` 옵션 추가
- 브라우저 쿠키 확인 (JSESSIONID 존재 여부)

### 3. 무한 리다이렉트

**증상**: Google 로그인 후 계속 리다이렉트됨

**원인**:
- `redirect-uri` 설정 불일치
- Google Cloud Console의 승인된 리디렉션 URI 설정 누락

**해결**:
- `application-local.yml`의 `redirect-uri` 확인
- Google Cloud Console에서 `http://localhost:8080/login/oauth2/code/google` 추가

### 4. 포트 충돌 (Port 8080 already in use)

**해결**:
```bash
lsof -ti:8080 | xargs kill -9
./gradlew bootRun --args='--spring.profiles.active=local'
```

---

## 참고 자료

- [Spring Security OAuth2 Client 공식 문서](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html)
- [Google OAuth2 가이드](https://developers.google.com/identity/protocols/oauth2)