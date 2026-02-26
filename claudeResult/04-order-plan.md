# Order 패키지 리팩터링 계획서

## 1. 기능 범위 정의

### 담당 패키지/클래스 목록

| 클래스 | 경로 | 역할 |
|--------|------|------|
| `Order` | `gift/order/Order.java` | 주문 엔티티 (JPA) |
| `OrderController` | `gift/order/OrderController.java` | 주문 REST 컨트롤러 |
| `OrderRepository` | `gift/order/OrderRepository.java` | 주문 영속성 접근 (JpaRepository) |
| `OrderRequest` | `gift/order/OrderRequest.java` | 주문 생성 요청 DTO (record) |
| `OrderResponse` | `gift/order/OrderResponse.java` | 주문 응답 DTO (record) |
| `KakaoMessageClient` | `gift/order/KakaoMessageClient.java` | 카카오 메시지 전송 클라이언트 |

### 기능 경계 (다른 패키지와의 인터페이스/의존)

**OrderController가 직접 의존하는 외부 패키지:**
- `gift.auth.AuthenticationResolver` -- 인증 토큰에서 Member 추출
- `gift.member.Member` -- 회원 엔티티 (포인트 차감, 카카오 토큰 접근)
- `gift.member.MemberRepository` -- 포인트 차감 후 회원 저장
- `gift.option.Option` -- 옵션 엔티티 (재고 차감)
- `gift.option.OptionRepository` -- 옵션 조회 및 재고 차감 후 저장
- `gift.wish.WishRepository` -- 주입되지만 **사용되지 않음** (미사용 의존)

**KakaoMessageClient가 의존하는 외부 패키지:**
- `gift.product.Product` -- 상품명/가격 정보 접근

**외부에서 order 패키지를 참조하는 곳:**
- 없음 (order 패키지는 현재 다른 패키지에서 참조되지 않음)

---

## 2. 현상 진단

### 2.1 Controller 비대 -- 심각

`OrderController.createOrder()` 메서드에 다음 비즈니스 로직이 **전부** 포함되어 있다:

1. **인증 처리** (L74-78): `authenticationResolver.extractMember()` 호출 및 null 체크
2. **옵션 검증** (L81-84): `optionRepository.findById()` 및 존재 여부 확인
3. **재고 차감** (L87-88): `option.subtractQuantity()` 호출 + `optionRepository.save()`
4. **포인트 차감** (L91-93): 가격 계산 + `member.deductPoint()` + `memberRepository.save()`
5. **주문 저장** (L96): `orderRepository.save()`
6. **카카오 알림 발송** (L99): `sendKakaoMessageIfPossible()` private 메서드 호출

`getOrders()` 메서드도 인증 + 조회 로직을 직접 수행한다.

**핵심 문제:** Controller가 6개의 Repository/Client를 직접 의존하며, 트랜잭션 경계 없이 여러 엔티티를 변경하고 있다.

### 2.2 Service 클래스 부재 -- 심각

프로젝트 전체에 **Service 클래스가 단 하나도 존재하지 않는다.** `OrderService`는 물론이고 다른 도메인(`member`, `product`, `option`, `wish`, `category`)에도 Service 레이어가 없다.

### 2.3 트랜잭션 경계 부재 -- 심각

`createOrder()`에서 3개의 엔티티를 변경한다:
- `Option` -- 재고 차감 후 save
- `Member` -- 포인트 차감 후 save
- `Order` -- 신규 저장

**`@Transactional`이 없으므로** 각 `save()`가 개별 트랜잭션으로 수행된다. 예를 들어 재고 차감 후 포인트 차감에서 예외가 발생하면 재고만 차감되고 롤백되지 않는 **데이터 정합성 위험**이 존재한다.

> **주의:** `@Transactional` 추가는 기존 동작의 트랜잭션 범위를 변경하므로, Service 추출과 함께 신중하게 진행해야 한다. 현재 동작은 각 save가 독립 트랜잭션이므로, 동일한 동작을 유지하되 서비스 추출 후 명시적으로 트랜잭션 경계를 설정하는 것을 계획한다.

### 2.4 미사용 코드 후보

| 항목 | 위치 | 상태 | 근거 |
|------|------|------|------|
| `WishRepository wishRepository` 필드 | `OrderController` L26, L34, L41 | **주입만 되고 한 번도 사용되지 않음** | 주석 "6. cleanup wish"에 기록되어 있으나 구현체가 없음 |
| `import gift.option.Option` | `OrderController` L6 | 사용됨 | `option` 변수에서 사용 |
| `import gift.member.Member` | `OrderController` L4 | 사용됨 | `member` 변수에서 사용 |

**WishRepository 미사용 분석:**
- **git blame 결과:** 초기 커밋(`55ca9e4`, wotjd243, 2026-02-18)에서 주입됨
- **주석 근거:** L67에 `// 6. cleanup wish`라는 주석이 있으나, 실제 주문 생성 로직에서 wish 정리가 구현되지 않았음
- **결론:** 주석으로 볼 때 **향후 구현 예정이었던 기능이 미완성 상태**. 두 가지 선택지:
  - (A) 삭제 -- 미구현 코드를 정리하고, 필요 시 별도 이슈로 추적
  - (B) wish cleanup 로직 구현 -- 그러나 이는 **새로운 동작 추가**이므로 리팩터링 범위를 벗어남
  - **결론: (A) 삭제하고, TODO 이슈로 추적** (동작 변경 금지 원칙 준수)

### 2.5 책임 혼재 분석

| 현재 위치 | 수행 중인 책임 | 올바른 위치 |
|-----------|---------------|-------------|
| `OrderController` | 인증 처리 (extractMember + null 체크) | Controller에서 수행 가능하나, 횡단 관심사로 분리 가능 |
| `OrderController` | 옵션 조회 및 검증 | **Service** |
| `OrderController` | 재고 차감 (option.subtractQuantity + save) | **Service** |
| `OrderController` | 가격 계산 (product.price * quantity) | **Service 또는 Domain** |
| `OrderController` | 포인트 차감 (member.deductPoint + save) | **Service** |
| `OrderController` | 주문 저장 | **Service** |
| `OrderController` | 카카오 알림 발송 판단 + 호출 | **Service** (호출 판단) + **Client** (실제 호출) |
| `KakaoMessageClient` | 외부 API 호출 + 메시지 템플릿 생성 | Client (적절). 단, `gift.order` 패키지에 위치하는 것이 적절한지는 검토 필요 |

### 2.6 스타일 불일치 항목

| 항목 | 현재 상태 | 개선 방향 |
|------|----------|----------|
| null 처리 패턴 | `orElse(null)` + null 체크 (L81-84) | `orElseThrow()`로 통일 (Service 레이어에서) |
| 인증 처리 | 각 메서드에서 수동 `extractMember` + null 체크 반복 | 공통 패턴이나 ArgumentResolver 등으로 분리 가능 (다른 Controller도 동일) |
| 예외 처리 | `catch (Exception ignored)` (L111) -- 예외를 무시 | 최소한 로깅 추가 필요 (warn 레벨) |
| `@ExceptionHandler` 부재 | OrderController에 `IllegalArgumentException` 핸들러 없음 | 다른 Controller(`OptionController`, `ProductController`, `MemberController`)는 있음. 통일 필요 |
| ResponseEntity 제네릭 | `ResponseEntity<?>` 와일드카드 사용 | 구체적 타입 지정 검토 (`ResponseEntity<OrderResponse>` 등) |
| import 순서 | 비표준 정렬 없이 혼재 | 표준 정렬 적용 (java.* / jakarta.* / org.* / gift.*) |
| 주석 스타일 | `// order flow:` 절차형 주석 (L61-68) | Service 추출 시 메서드 분리로 자체 문서화 |
| `KakaoMessageClient`의 JSON 직접 생성 | 문자열 템플릿으로 JSON 구성 (L36-49) | ObjectMapper 또는 DTO 직렬화 검토 (동작 변경 없이 가능) |
| `sendKakaoMessageIfPossible` | Controller의 private 메서드 | Service로 이동 대상 |

---

## 3. 구현해야 할 기능 목록 (체크리스트)

### 미사용 코드 정리
- [ ] `OrderController`에서 `WishRepository` 필드, 생성자 파라미터, import 제거
- [ ] `WishRepository` import 제거 후 "wish cleanup 미구현" TODO 이슈 별도 기록

### 서비스 추출 (OrderService 신규 생성)
- [ ] `OrderService` 클래스 생성 (`gift.order` 패키지)
- [ ] `createOrder()` 비즈니스 로직을 `OrderService.createOrder(Member, OrderRequest)` 로 이동
  - [ ] 옵션 조회 및 검증
  - [ ] 재고 차감
  - [ ] 가격 계산 및 포인트 차감
  - [ ] 주문 엔티티 생성 및 저장
  - [ ] 카카오 알림 발송 (best-effort)
- [ ] `getOrders()` 조회 로직을 `OrderService.getOrders(Long memberId, Pageable)` 로 이동
- [ ] `@Transactional` 적용 (createOrder에 쓰기 트랜잭션, getOrders에 readOnly)
- [ ] `OrderService`에 `@Service` 어노테이션 부여

### Controller 슬림화
- [ ] `OrderController`에서 `OptionRepository`, `MemberRepository`, `KakaoMessageClient` 직접 의존 제거
- [ ] `OrderController`는 `AuthenticationResolver`와 `OrderService`만 의존하도록 변경
- [ ] Controller 메서드는 인증 추출 + Service 위임 + 응답 변환만 수행

### 예외 처리 통일
- [ ] `OrderController`에 `@ExceptionHandler(IllegalArgumentException.class)` 추가
- [ ] `catch (Exception ignored)` 블록에 로깅(warn) 추가 (Service로 이동 후)
- [ ] `orElse(null)` + null 체크 패턴을 Service에서 `orElseThrow()` 패턴으로 변경

### 스타일 정리
- [ ] import 순서 정렬 (java.* -> jakarta.* -> org.* -> gift.*)
- [ ] `ResponseEntity<?>` 와일드카드를 구체 타입으로 변경 가능 여부 검토
- [ ] `KakaoMessageClient`의 JSON 문자열 템플릿을 유지하되, 이스케이프 안전성 검증
- [ ] `sendKakaoMessageIfPossible` 메서드를 Service로 이동하며 메서드명 유지

### 테스트
- [ ] `OrderService` 단위 테스트 작성 (주문 생성 정상 플로우)
- [ ] `OrderService` 단위 테스트 작성 (재고 부족 시 예외)
- [ ] `OrderService` 단위 테스트 작성 (포인트 부족 시 예외)
- [ ] `OrderService` 단위 테스트 작성 (존재하지 않는 옵션 시 예외)
- [ ] `OrderController` 통합 테스트 작성 (E2E: 정상 주문 생성 -> 201 응답)
- [ ] `OrderController` 통합 테스트 작성 (E2E: 인증 실패 -> 401 응답)
- [ ] `KakaoMessageClient` 단위 테스트 작성 (템플릿 생성 검증)

---

## 4. 전략 (단계별)

### Step 1: 안전장치 마련

**목적:** 리팩터링 전 현재 동작을 검증할 수 있는 기반 구축

1. 현재 `OrderController`에 대한 통합 테스트를 **먼저** 작성한다.
   - `@SpringBootTest` + `@AutoConfigureMockMvc` 사용
   - 정상 주문 생성, 인증 실패, 옵션 미존재 케이스를 커버
2. 테스트가 green인 상태에서 리팩터링을 시작한다.
3. 각 Step 완료 후 전체 테스트를 실행하여 green을 확인한다.

### Step 2: 미사용 코드 정리

**목적:** 불필요한 의존과 코드 제거

1. `OrderController`에서 `WishRepository` 관련 코드 일괄 제거:
   - 필드 선언 (`private final WishRepository wishRepository;`)
   - 생성자 파라미터 및 할당 (`WishRepository wishRepository`, `this.wishRepository = wishRepository;`)
   - import 문 (`import gift.wish.WishRepository;`)
2. 주석 `// 6. cleanup wish` 제거 (해당 로직이 없으므로)
3. 테스트 실행 -> green 확인
4. 커밋: "remove unused WishRepository dependency from OrderController"

### Step 3: OrderService 추출

**목적:** 비즈니스 로직을 Service 레이어로 분리

1. `gift.order.OrderService` 클래스 생성:
   ```java
   @Service
   public class OrderService {
       private final OrderRepository orderRepository;
       private final OptionRepository optionRepository;
       private final MemberRepository memberRepository;
       private final KakaoMessageClient kakaoMessageClient;
       // 생성자 주입
   }
   ```
2. `createOrder(Member member, OrderRequest request)` 메서드 추출:
   - `OrderController.createOrder()`의 인증 이후 모든 로직을 이동
   - 반환 타입: `Order` (Controller에서 `OrderResponse`로 변환)
3. `getOrders(Long memberId, Pageable pageable)` 메서드 추출:
   - 반환 타입: `Page<Order>`
4. `sendKakaoMessageIfPossible()` private 메서드를 Service로 이동
5. 테스트 실행 -> green 확인
6. 커밋: "extract OrderService from OrderController"

### Step 4: 로직 재분배 및 트랜잭션 적용

**목적:** 책임 분리 완성 및 트랜잭션 경계 설정

1. `OrderService.createOrder()`에 `@Transactional` 적용
   - 재고 차감, 포인트 차감, 주문 저장이 하나의 트랜잭션으로 묶임
   - **주의:** 카카오 알림 발송은 트랜잭션 내에서 수행되지만, best-effort이므로 실패해도 롤백하지 않음 (기존 동작 동일)
2. `OrderService.getOrders()`에 `@Transactional(readOnly = true)` 적용
3. `OrderController`에서 불필요한 직접 의존 제거:
   - `OptionRepository` 제거
   - `MemberRepository` 제거
   - `KakaoMessageClient` 제거
   - `OrderRepository` 제거 (Service를 통해서만 접근)
4. Controller 최종 형태:
   ```java
   @RestController
   @RequestMapping("/api/orders")
   public class OrderController {
       private final AuthenticationResolver authenticationResolver;
       private final OrderService orderService;
       // 생성자 주입 (2개만)
   }
   ```
5. Controller 메서드는 인증 추출 -> Service 위임 -> 응답 생성만 수행
6. 테스트 실행 -> green 확인
7. 커밋: "slim down OrderController, apply @Transactional to OrderService"

### Step 5: 테스트 보강

**목적:** Service 레이어 단위 테스트 추가

1. `OrderServiceTest` 작성:
   - Mock 기반 단위 테스트 (Mockito)
   - 정상 주문 생성 플로우
   - 존재하지 않는 옵션 -> 예외 발생
   - 재고 부족 -> IllegalArgumentException
   - 포인트 부족 -> IllegalArgumentException
   - 카카오 토큰 없는 경우 -> 알림 미발송
   - 카카오 API 실패 -> 예외 무시 (best-effort)
2. `KakaoMessageClientTest` 작성 (선택):
   - 템플릿 문자열 생성 검증
3. 기존 통합 테스트와 함께 전체 green 확인
4. 커밋: "add unit tests for OrderService"

### Step 6: 스타일 정리

**목적:** 코드 스타일 통일 및 품질 개선

1. **예외 처리 통일:**
   - Service에서 `optionRepository.findById().orElseThrow(() -> new NoSuchElementException("옵션을 찾을 수 없습니다."))` 패턴 적용
   - `OrderController`에 `@ExceptionHandler` 추가:
     - `IllegalArgumentException` -> 400 Bad Request
     - `NoSuchElementException` -> 404 Not Found
2. **로깅 추가:**
   - `sendKakaoMessageIfPossible()`의 `catch (Exception ignored)` 블록에 `log.warn("카카오 메시지 발송 실패", e)` 추가
   - `OrderService`에 SLF4J Logger 선언
3. **import 정리:**
   - 순서: `java.*` -> `jakarta.*` -> `org.*` -> `gift.*`
   - 미사용 import 제거 확인
4. **ResponseEntity 타입 개선:**
   - `ResponseEntity<?>` -> 가능한 곳은 구체 타입 사용
   - `getOrders`: `ResponseEntity<Page<OrderResponse>>`
   - `createOrder`: `ResponseEntity<OrderResponse>`
5. **주석 정리:**
   - `// order flow:` 절차형 주석 제거 (Service 메서드 분리로 자체 문서화됨)
   - `// auth check`, `// validate option` 등 명백한 주석 제거
6. 테스트 실행 -> green 확인
7. 커밋: "unify code style in order package"

---

## 5. 리스크 & 작동 동일성 검증 방법

### 리스크 항목

| # | 리스크 | 영향도 | 발생 가능성 | 대응 방안 |
|---|--------|--------|------------|----------|
| R1 | `@Transactional` 추가로 트랜잭션 범위 변경 | 높음 | 확실 | 현재는 각 save가 독립 트랜잭션이므로, `@Transactional` 추가 시 하나의 트랜잭션으로 묶인다. 이는 **정합성 개선**이지만 기존 동작과 다르다. 카카오 알림 실패 시에도 주문이 롤백되지 않도록 try-catch를 유지해야 한다. |
| R2 | 카카오 알림 발송이 트랜잭션 내에서 수행 | 중간 | 확실 | 외부 API 호출이 느릴 경우 DB 커넥션을 오래 점유할 수 있다. 현재도 같은 메서드 내에서 수행되므로 동작은 동일하다. 향후 이벤트 기반 비동기 발송을 검토할 수 있으나, 이번 리팩터링 범위 밖이다. |
| R3 | `WishRepository` 제거 후 wish cleanup 기능 구현 불가 | 낮음 | 가능 | 현재 미구현 상태이므로 제거해도 동작 변경 없음. TODO 이슈로 추적한다. |
| R4 | `orElse(null)` -> `orElseThrow()` 변경 시 예외 타입 변경 | 중간 | 확실 | 기존: null 반환 -> 404 응답. 변경 후: 예외 발생 -> `@ExceptionHandler`에서 404 응답. **HTTP 응답은 동일**하나, 내부 흐름이 달라진다. 통합 테스트로 응답 동일성 검증 필수. |
| R5 | Spring 컨텍스트 로딩 변경 (새 빈 추가) | 낮음 | 낮음 | `OrderService` 빈 추가로 DI 구성이 변경됨. 통합 테스트에서 컨텍스트 로딩 성공 확인. |

### 작동 동일성 검증 방법

1. **Step 1에서 작성한 통합 테스트를 매 Step 완료 후 실행**
   - 주문 생성 정상 케이스: 201 응답 + 응답 바디 검증
   - 인증 실패 케이스: 401 응답
   - 옵션 미존재 케이스: 404 응답
   - 주문 목록 조회 케이스: 200 응답 + 페이징 검증

2. **HTTP 응답 동일성 확인 항목:**
   - Status code 동일
   - Response body 구조 동일 (OrderResponse의 필드)
   - Location 헤더 동일 (`/api/orders/{id}`)
   - Content-Type 동일

3. **부수 효과(side-effect) 검증:**
   - 주문 생성 후 DB에 Order 레코드 존재 확인
   - 주문 생성 후 Option 재고 감소 확인
   - 주문 생성 후 Member 포인트 감소 확인
   - 카카오 알림 실패 시에도 주문이 정상 저장되는지 확인

4. **수동 검증 (선택):**
   - 리팩터링 전후로 동일한 curl 요청을 보내 응답 비교
   - 카카오 API mock 서버를 활용한 알림 발송 검증

---

## 6. 완료 조건 (Definition of Done)

- [ ] **모든 테스트 green** -- 기존 통합 테스트 + 신규 단위 테스트 전부 통과
- [ ] **Controller 얇고 위임만** -- `OrderController`는 `AuthenticationResolver`와 `OrderService`만 의존하며, 인증 추출/위임/응답 변환만 수행
- [ ] **Service 레이어 존재** -- `OrderService`가 모든 비즈니스 로직(옵션 검증, 재고 차감, 포인트 차감, 주문 저장, 카카오 알림)을 포함
- [ ] **트랜잭션 경계 명시** -- `@Transactional`이 Service 메서드에 적용되어 있음
- [ ] **미사용 코드 제거 근거 문서화** -- `WishRepository` 제거 사유 및 TODO 기록 완료
- [ ] **예외 처리 통일** -- `@ExceptionHandler` 존재, `orElseThrow()` 패턴 사용, 예외 무시 블록에 로깅 추가
- [ ] **스타일 일관성** -- import 순서, 네이밍, 공백/줄바꿈이 프로젝트 내 다른 패키지와 일관됨
- [ ] **KakaoMessageClient 위치 적절** -- `gift.order` 패키지에 유지 (주문 알림 전용이므로 적절)
- [ ] **HTTP 응답 동일** -- 리팩터링 전후 모든 엔드포인트의 응답 코드, 바디 구조, 헤더가 동일함을 테스트로 증명
