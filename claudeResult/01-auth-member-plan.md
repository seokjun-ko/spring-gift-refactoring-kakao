# auth-member 리팩터링 계획서

> 담당 패키지: `gift.auth`, `gift.member`
> 절대 조건: 작동(Behavior) 변경 금지 -- 리팩터링/정리/구조 변경만 수행

---

## 1. 기능 범위 정의

### 1.1 담당 클래스 목록

| 패키지 | 클래스 | 역할 |
|--------|--------|------|
| `gift.auth` | `AuthenticationResolver` | Authorization 헤더에서 JWT를 파싱하여 Member 조회 |
| `gift.auth` | `JwtProvider` | JWT 토큰 생성 및 검증 |
| `gift.auth` | `KakaoAuthController` | 카카오 OAuth2 로그인 플로우 (redirect + callback) |
| `gift.auth` | `KakaoLoginClient` | 카카오 API 호출 (토큰 교환, 사용자 정보 조회) |
| `gift.auth` | `KakaoLoginProperties` | 카카오 로그인 설정값 (`@ConfigurationProperties`) |
| `gift.auth` | `TokenResponse` | JWT 토큰 응답 DTO (record) |
| `gift.member` | `Member` | 회원 엔티티 (`@Entity`) |
| `gift.member` | `MemberController` | 회원 가입/로그인 REST API |
| `gift.member` | `AdminMemberController` | 관리자 회원 관리 (Thymeleaf MVC) |
| `gift.member` | `MemberRepository` | 회원 영속성 접근 (`JpaRepository`) |
| `gift.member` | `MemberRequest` | 회원 가입/로그인 요청 DTO (record) |

### 1.2 기능 경계 -- 다른 패키지와의 의존 관계

**auth/member를 사용하는 쪽 (인바운드 의존):**

| 사용처 | 사용하는 클래스 | 용도 |
|--------|----------------|------|
| `gift.wish.WishController` | `AuthenticationResolver` | 인증된 회원 추출 |
| `gift.order.OrderController` | `AuthenticationResolver`, `MemberRepository`, `Member` | 인증 + 포인트 차감 + 저장 |
| `gift.member.MemberController` | `JwtProvider`, `TokenResponse` | 가입/로그인 후 JWT 발급 |

**auth/member가 사용하는 쪽 (아웃바운드 의존):**

| 클래스 | 의존 대상 | 용도 |
|--------|-----------|------|
| `AuthenticationResolver` | `MemberRepository` | 이메일로 회원 조회 |
| `KakaoAuthController` | `MemberRepository` | 회원 자동가입/조회, 카카오 토큰 저장 |

### 1.3 핵심 인터페이스 계약

- `AuthenticationResolver.extractMember(authorization)` -- 외부 패키지(wish, order)가 사용하는 유일한 인증 진입점. 반환: `Member` 또는 `null`
- `JwtProvider.createToken(email)` / `JwtProvider.getEmail(token)` -- 토큰 생성/파싱
- `MemberRepository.findByEmail()`, `existsByEmail()` -- 여러 곳에서 사용

---

## 2. 현상 진단

### 2.1 Controller 비대 여부

#### MemberController (비대 -- 심각)
- `register()`: 이메일 중복 검사 + 엔티티 생성 + 저장 + JWT 발급까지 모두 Controller에서 수행
- `login()`: 회원 조회 + 비밀번호 평문 비교 로직이 Controller에 직접 존재
- `@ExceptionHandler`가 Controller 내부에 로컬로 정의됨

#### AdminMemberController (비대 -- 심각)
- `create()`: 이메일 중복 검사 + 엔티티 생성 + 저장
- `update()`: 회원 조회 + 수정 + 저장
- `chargePoint()`: 회원 조회 + 포인트 충전 + 저장
- `delete()`: 직접 `deleteById` 호출
- 모든 메서드에서 `memberRepository.findById().orElseThrow()` 패턴 반복 (DRY 위반)

#### KakaoAuthController (비대 -- 중간)
- `callback()`: 카카오 토큰 교환 + 사용자 정보 조회 + 회원 자동가입/조회 + 카카오 토큰 갱신 + 저장 + JWT 발급
- 비즈니스 흐름이 Controller에 직접 기술되어 있음

### 2.2 Service 계층 부재

**프로젝트 전체에 `@Service` 클래스가 단 하나도 없다.**
**프로젝트 전체에 `@Transactional` 어노테이션이 단 하나도 없다.**

이로 인한 문제:
- 비즈니스 로직이 모두 Controller에 존재 (Controller가 Repository를 직접 호출)
- 트랜잭션 경계가 명시되지 않음 (JPA 기본 동작에 의존)
- 로직 재사용 불가 (예: 회원 가입 로직이 `MemberController.register()`와 `KakaoAuthController.callback()`에 중복)
- 단위 테스트가 어려움 (Controller를 통째로 테스트해야 함)

### 2.3 책임 혼재 항목

| 위치 | 문제 | 상세 |
|------|------|------|
| `MemberController.login()` | 비밀번호 비교 로직이 Controller에 있음 | `member.getPassword().equals(request.password())` -- 평문 비교이며 도메인/서비스 책임 |
| `MemberController.register()` | 중복 이메일 검사가 Controller에 있음 | `memberRepository.existsByEmail()` -- 비즈니스 규칙 |
| `KakaoAuthController.callback()` | 회원 자동가입 + 토큰 갱신 + JWT 발급 | 3가지 유즈케이스가 한 메서드에 혼재 |
| `AdminMemberController` 전체 | CRUD 전부 Controller에서 직접 수행 | Service 위임 없음 |
| `AuthenticationResolver` | `@Component`이지만 실질적으로 Service 역할 | 토큰 파싱 + 회원 조회 로직을 포함하며 null 반환으로 에러 처리 |

### 2.4 미사용 코드 후보

| 대상 | 종류 | 사용 여부 | 근거 | 결론 |
|------|------|-----------|------|------|
| `AuthenticationResolver`의 `@Autowired` | 어노테이션 | 불필요 | 생성자가 1개인 경우 Spring 4.3+에서 자동 주입. 프로젝트 내 다른 클래스(`KakaoAuthController`, `KakaoLoginClient`)는 `@Autowired` 없이 생성자 주입 사용 | **삭제** -- 스타일 통일 |
| `MemberController`의 `@Autowired` | 어노테이션 | 불필요 | 위와 동일 | **삭제** -- 스타일 통일 |
| `AdminMemberController`의 `@Autowired` | 어노테이션 | 불필요 | 위와 동일 | **삭제** -- 스타일 통일 |
| `JwtProvider`의 `@Autowired` | 어노테이션 | 불필요 | 위와 동일 | **삭제** -- 스타일 통일 |
| `org.springframework.beans.factory.annotation.Autowired` import | import | `@Autowired` 삭제 시 미사용 | 4개 파일에서 `@Autowired` 제거 후 해당 import도 제거 필요 | **삭제** |
| `org.springframework.stereotype.Component` import (AuthenticationResolver) | import | 사용 중 | `@Component` 어노테이션에서 사용 | **유지** |

**git blame 확인 결과:** 모든 파일이 단일 커밋(`feat: set up the project`, author: wotjd243)에서 작성됨. TODO/FIXME 주석 없음. 삭제 시 이후 단계와의 충돌 가능성 없음.

### 2.5 스타일 불일치 항목

| 항목 | 현재 상태 | 문제 |
|------|-----------|------|
| `@Autowired` 사용 | `AuthenticationResolver`, `JwtProvider`, `MemberController`, `AdminMemberController`에는 있고, `KakaoAuthController`, `KakaoLoginClient`에는 없음 | 불일치 -- 생성자 1개인 경우 모두 제거해야 일관적 |
| 예외 처리 패턴 | `MemberController`에만 `@ExceptionHandler` 존재, `AdminMemberController`/`KakaoAuthController`에는 없음 | 불일치 -- 글로벌 `@ControllerAdvice` 없음 |
| null 처리 | `AuthenticationResolver.extractMember()`는 null 반환, 호출자(WishController, OrderController)는 매번 `if (member == null)` 체크 | 중복 보일러플레이트 + null-safety 미흡 |
| Javadoc | `JwtProvider`, `TokenResponse`, `MemberController`, `Member`, `MemberRepository`, `MemberRequest`에는 있고, `KakaoAuthController`, `KakaoLoginClient`, `KakaoLoginProperties`에는 없음 | 불일치 |
| 주석 언어 | `Member.deductPoint()` -- 한국어(`"차감 금액은 1 이상이어야 합니다."`) / `Member.chargePoint()` -- 영어(`"Amount must be greater than zero."`) | 불일치 -- 에러 메시지 언어 혼재 |
| 주석 스타일 | `KakaoAuthController` -- `/* */` 블록 주석, 나머지 -- `/** */` Javadoc | 불일치 |
| 비밀번호 비교 | `member.getPassword().equals(request.password())` -- 평문 비교 | 보안 이슈이나 동작 변경 없이는 수정 불가, 단 비교 로직의 위치(Controller vs Domain/Service)는 변경 가능 |

---

## 3. 구현해야 할 기능 목록 (체크리스트)

### 미사용 코드 제거
- [ ] `AuthenticationResolver`에서 불필요한 `@Autowired` 어노테이션 및 import 제거
- [ ] `JwtProvider`에서 불필요한 `@Autowired` 어노테이션 및 import 제거
- [ ] `MemberController`에서 불필요한 `@Autowired` 어노테이션 및 import 제거
- [ ] `AdminMemberController`에서 불필요한 `@Autowired` 어노테이션 및 import 제거

### 서비스 계층 추출
- [ ] `MemberService` 클래스 신규 생성 (`gift.member` 패키지)
  - [ ] `register(email, password)` -- 중복 검사 + 저장 + 토큰 발급
  - [ ] `login(email, password)` -- 조회 + 비밀번호 검증 + 토큰 발급
  - [ ] `findById(id)` -- 회원 조회 (AdminMemberController용)
  - [ ] `findAll()` -- 전체 회원 조회 (AdminMemberController용)
  - [ ] `update(id, email, password)` -- 회원 정보 수정
  - [ ] `chargePoint(id, amount)` -- 포인트 충전
  - [ ] `delete(id)` -- 회원 삭제
  - [ ] `findOrCreateByKakaoEmail(email)` -- 카카오 자동가입/조회
  - [ ] 각 메서드에 `@Transactional` 적용
- [ ] `AuthService` 또는 `KakaoAuthService` 클래스 신규 생성 (`gift.auth` 패키지)
  - [ ] `kakaoLogin(code)` -- 카카오 토큰 교환 + 사용자 정보 조회 + 회원 처리 + JWT 발급 통합
  - [ ] `buildKakaoAuthUrl()` -- 카카오 인증 URL 생성
  - [ ] `@Transactional` 적용

### 로직 재분배 (Controller -> Service 위임)
- [ ] `MemberController.register()` -- `MemberService.register()` 위임으로 변경
- [ ] `MemberController.login()` -- `MemberService.login()` 위임으로 변경
- [ ] `AdminMemberController` 모든 메서드 -- `MemberService` 위임으로 변경
- [ ] `KakaoAuthController.callback()` -- `KakaoAuthService.kakaoLogin()` 위임으로 변경
- [ ] `KakaoAuthController.login()` -- `KakaoAuthService.buildKakaoAuthUrl()` 위임으로 변경
- [ ] `MemberController`의 `@ExceptionHandler` 제거 후 글로벌 `@ControllerAdvice` 도입 검토 (다른 기능 팀과 조율 필요)

### AuthenticationResolver 개선
- [ ] 예외 발생 시 null 반환 대신 명확한 예외를 던지도록 개선 검토 (주의: 호출자 동작에 영향)
- [ ] 또는 `Optional<Member>` 반환으로 변경하여 null-safety 확보 (호출자도 함께 수정 필요 -- wish/order 팀 조율 필요)

### 코드 스타일 통일
- [ ] Javadoc 누락 클래스에 Javadoc 추가: `KakaoAuthController`, `KakaoLoginClient`, `KakaoLoginProperties`
- [ ] `KakaoAuthController`의 `/* */` 블록 주석을 `/** */` Javadoc으로 변경
- [ ] 에러 메시지 언어 통일: `Member.chargePoint()`와 `Member.deductPoint()`의 에러 메시지를 한 언어로 통일
- [ ] 주석 언어 통일: `Member.deductPoint()` 위의 영어 주석(`// point deduction for order payment`)과 한국어 에러 메시지 혼재 정리
- [ ] import 정리: `@Autowired` 제거 후 불필요한 import 일괄 제거 확인

### 테스트 작성
- [ ] `MemberService` 단위 테스트 작성 (register, login, chargePoint, deductPoint 시나리오)
- [ ] `KakaoAuthService` 단위 테스트 작성 (신규 회원 자동가입, 기존 회원 토큰 갱신 시나리오)
- [ ] `MemberController` 통합 테스트 작성 (HTTP 요청/응답 검증)
- [ ] `AdminMemberController` 통합 테스트 작성 (뷰 반환 검증)
- [ ] `KakaoAuthController` 통합 테스트 작성 (리다이렉트 + 콜백 검증)

---

## 4. 전략 (단계별)

### Step 1: 안전장치 마련
1. 현재 코드가 정상 동작하는지 확인 (빌드 + 수동 테스트 시나리오 정리)
2. 기존 동작을 보존하기 위한 E2E/통합 테스트 시나리오 작성:
   - 회원 가입 -> JWT 반환 검증
   - 로그인 -> JWT 반환 검증
   - 잘못된 비밀번호 -> 400 에러 검증
   - 중복 이메일 가입 -> 400 에러 검증
   - 카카오 콜백 -> JWT 반환 검증 (외부 API Mock)
   - 관리자 회원 목록/생성/수정/삭제 -> 뷰 반환 검증
3. git branch 생성: `refactor/auth-member`

### Step 2: 미사용 코드 정리
1. 4개 파일에서 불필요한 `@Autowired` 어노테이션 제거
2. 그에 따른 `org.springframework.beans.factory.annotation.Autowired` import 제거
3. 빌드 확인

### Step 3: 서비스 클래스 추출
1. `MemberService` 클래스 생성 (`@Service`, `@Transactional`)
   - `MemberController`와 `AdminMemberController`에서 비즈니스 로직을 그대로 이동 (복사 -> 위임 -> 원본 삭제)
   - `MemberRepository` 의존성을 `MemberService`로 이동
   - `JwtProvider` 의존성도 필요 시 `MemberService`로 이동 (또는 별도 `AuthService`로)
2. `KakaoAuthService` 클래스 생성 (`@Service`, `@Transactional`)
   - `KakaoAuthController.callback()` 로직을 이동
   - `KakaoLoginClient`, `MemberRepository` (`MemberService`), `JwtProvider` 의존

### Step 4: 로직 재분배 (Controller 얇게 만들기)
1. `MemberController`:
   - `register()` -> `memberService.register(request)` 위임, HTTP 상태 코드만 제어
   - `login()` -> `memberService.login(request)` 위임, HTTP 상태 코드만 제어
   - `@ExceptionHandler` 제거 -> 글로벌 `@ControllerAdvice` 이동 (또는 다른 팀과 협의 후 진행)
2. `AdminMemberController`:
   - 모든 메서드 -> `memberService.xxx()` 위임
   - `populateNewFormError()` 유틸 메서드는 Controller에 유지 (뷰 관련)
3. `KakaoAuthController`:
   - `login()` -> URL 생성을 `KakaoAuthService`에 위임
   - `callback()` -> `kakaoAuthService.processCallback(code)` 위임

### Step 5: 테스트 보강
1. `MemberService` 단위 테스트:
   - 정상 가입 / 중복 이메일 / 정상 로그인 / 잘못된 비밀번호 / 포인트 충전 / 포인트 부족
2. `KakaoAuthService` 단위 테스트:
   - Mock: `KakaoLoginClient`, `MemberRepository`, `JwtProvider`
   - 신규 회원 자동가입 시나리오 / 기존 회원 토큰 갱신 시나리오
3. Controller 통합 테스트 (`@WebMvcTest`):
   - Service를 Mock하고 HTTP 요청/응답만 검증
4. Step 1에서 정의한 E2E 시나리오 재실행하여 동작 동일성 확인

### Step 6: 코드 스타일 정리
1. Javadoc 통일: 누락된 3개 클래스에 Javadoc 추가
2. 주석 스타일 통일: `/* */` -> `/** */`
3. 에러 메시지 언어 통일: 한국어 또는 영어 중 하나로 (프로젝트 컨벤션 확인 필요)
4. import 정리: IDE 자동 import 정리 수행
5. 빌드 + 전체 테스트 실행

---

## 5. 리스크 & 작동 동일성 검증 방법

### 5.1 리스크 항목

| # | 리스크 | 영향도 | 발생 가능성 | 대응 |
|---|--------|--------|-------------|------|
| R1 | `MemberService` 추출 시 `@Transactional` 도입으로 트랜잭션 경계가 변경됨 | 중간 | 낮음 | 현재 각 Repository 호출이 개별 트랜잭션이던 것이 Service 메서드 단위로 묶임. 기존 동작에서 중간 실패 시 부분 저장되던 것이 전체 롤백으로 바뀔 수 있음 -> 이는 오히려 데이터 정합성 개선이므로 허용 가능하되, 주문 플로우(`OrderController`)처럼 여러 Repository를 호출하는 곳은 주의 필요 (이 패키지 범위 밖이므로 order 팀에 전달) |
| R2 | `AuthenticationResolver`의 null 반환을 예외로 변경할 경우, 호출자(wish/order)의 동작이 변경됨 | 높음 | 높음 | 이번 리팩터링에서는 null 반환 유지. Optional 반환으로 변경 시 wish/order 팀과 합의 필요. 문서에만 기록 |
| R3 | `@ExceptionHandler` 제거 + `@ControllerAdvice` 도입 시, 예외 응답 포맷이 변경될 수 있음 | 중간 | 중간 | 기존 응답 포맷(`String body`)을 정확히 유지하도록 `@ControllerAdvice`에서 동일 형태 반환. 도입 전 다른 Controller(`ProductController`, `OptionController`)의 `@ExceptionHandler`도 함께 이동해야 하므로 다른 팀과 조율 |
| R4 | `AdminMemberController`가 Thymeleaf 뷰를 반환하므로, 뷰 템플릿(`member/list.html` 등)과의 `model.addAttribute` 키 이름이 변경되면 뷰가 깨짐 | 높음 | 낮음 | Service 추출 시 Model 속성 이름과 구조를 절대 변경하지 않음. 뷰 템플릿 확인 후 진행 |
| R5 | 카카오 로그인 플로우에서 `MemberRepository` 직접 사용을 `MemberService` 위임으로 변경 시, 기존과 동일한 저장 순서 유지 필요 | 중간 | 낮음 | `findByEmail -> orElseGet(new) -> updateKakaoAccessToken -> save` 순서를 Service에서 그대로 유지 |

### 5.2 작동 동일성 검증 방법

| 검증 항목 | 방법 | 성공 기준 |
|-----------|------|-----------|
| 회원 가입 | POST `/api/members/register` with `{"email":"test@test.com","password":"1234"}` | 201 + `{"token":"..."}` |
| 회원 가입 중복 | 동일 이메일로 재가입 | 400 + `"Email is already registered."` |
| 로그인 | POST `/api/members/login` | 200 + `{"token":"..."}` |
| 로그인 실패 | 잘못된 비밀번호 | 400 + `"Invalid email or password."` |
| 카카오 로그인 리다이렉트 | GET `/api/auth/kakao/login` | 302 + Location 헤더에 카카오 URL |
| 카카오 콜백 | GET `/api/auth/kakao/callback?code=xxx` (Mock) | 200 + `{"token":"..."}` |
| 관리자 목록 | GET `/admin/members` | 200 + HTML with member list |
| 관리자 생성 | POST `/admin/members` | 302 redirect |
| 관리자 수정 | POST `/admin/members/{id}/edit` | 302 redirect |
| 관리자 포인트 충전 | POST `/admin/members/{id}/charge-point?amount=1000` | 302 redirect |
| 관리자 삭제 | POST `/admin/members/{id}/delete` | 302 redirect |
| JWT 인증 | Authorization 헤더 -> `AuthenticationResolver.extractMember()` | 유효 토큰: Member 반환, 무효 토큰: null 반환 |

---

## 6. 완료 조건 (Definition of Done)

- [ ] **모든 테스트 GREEN**: 신규 작성된 단위 테스트 + 통합 테스트 + 기존 테스트(현재 없음) 전부 통과
- [ ] **빌드 성공**: `./gradlew build` 성공
- [ ] **Controller 얇기 달성**: 모든 Controller 메서드가 요청 검증/변환/Service 위임/응답 변환만 수행
  - `MemberController`: 비즈니스 로직 0줄
  - `AdminMemberController`: 비즈니스 로직 0줄, 뷰 관련 로직만 유지
  - `KakaoAuthController`: 비즈니스 로직 0줄
- [ ] **Service 계층 존재**: `MemberService`와 `KakaoAuthService`(또는 `AuthService`)가 존재하며 `@Service` + `@Transactional` 적용
- [ ] **미사용 코드 제거 완료**: 불필요한 `@Autowired` 4건 + 관련 import 제거, 근거 문서화 (본 문서 2.4절)
- [ ] **스타일 일관성**: Javadoc 통일, 주석 스타일 통일, 에러 메시지 언어 통일, import 정리 완료
- [ ] **작동 동일성 확인**: 섹션 5.2의 모든 검증 항목 통과
- [ ] **다른 패키지 영향 없음**: `gift.wish`, `gift.order` 등 외부 패키지의 기존 코드가 변경 없이 동작
  - 단, `AuthenticationResolver`의 시그니처를 변경하는 경우 wish/order 팀과 합의 후 진행
