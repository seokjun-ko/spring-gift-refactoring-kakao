# Wish 패키지 리팩터링 계획서

## 1. 기능 범위 정의

### 담당 패키지 및 클래스 목록

| 클래스 | 경로 | 역할 |
|--------|------|------|
| `Wish` | `gift.wish.Wish` | JPA 엔티티 (위시 도메인 모델) |
| `WishController` | `gift.wish.WishController` | REST 컨트롤러 (위시 CRUD API) |
| `WishRepository` | `gift.wish.WishRepository` | JPA 리포지토리 인터페이스 |
| `WishRequest` | `gift.wish.WishRequest` | 요청 DTO (record) |
| `WishResponse` | `gift.wish.WishResponse` | 응답 DTO (record) |

### 기능 경계 및 외부 의존

**wish 패키지가 의존하는 외부 패키지:**
- `gift.auth.AuthenticationResolver` -- 인증 처리 (Authorization 헤더에서 Member 추출)
- `gift.product.Product` -- Wish 엔티티가 `@ManyToOne`으로 Product를 참조
- `gift.product.ProductRepository` -- WishController가 직접 ProductRepository를 주입받아 상품 조회

**외부 패키지에서 wish 패키지를 참조하는 곳:**
- `gift.order.OrderController` -- `WishRepository`를 import하고 주입받지만 **실제 사용하지 않음** (미사용 의존)

### Service 클래스 존재 여부
- **프로젝트 전체에 Service 클래스가 단 하나도 없음** -- 모든 비즈니스 로직이 Controller에 직접 구현되어 있음
- WishService 역시 존재하지 않음

---

## 2. 현상 진단

### 2.1 Controller 비대 (Fat Controller)

`WishController`는 현재 3개의 엔드포인트(GET, POST, DELETE)를 가지며, 각 메서드에 다음 비즈니스 로직이 직접 구현되어 있다:

| 메서드 | 포함된 비즈니스 로직 | 문제 |
|--------|---------------------|------|
| `getWishes` | 인증 확인 + 조회 + DTO 변환 | 인증/조회 로직 Controller에 위치 |
| `addWish` | 인증 확인 + 상품 존재 검증 + 중복 위시 검사 + 저장 + DTO 변환 | 비즈니스 규칙(중복 검사)이 Controller에 위치, ProductRepository 직접 참조 |
| `removeWish` | 인증 확인 + 위시 존재 검증 + 소유권 검증 + 삭제 | 권한 검증 로직이 Controller에 위치 |

**핵심 문제:**
- Controller가 Repository 2개(`WishRepository`, `ProductRepository`)와 `AuthenticationResolver`를 직접 의존
- 중복 위시 검사, 소유권 검증 등 비즈니스 규칙이 Controller에 산재
- 트랜잭션 경계가 명시적으로 설정되어 있지 않음 (`@Transactional` 없음)
- `addWish`의 "중복이면 기존 것 반환" 로직은 비즈니스 정책 결정인데 Controller에 존재

### 2.2 Service 부재

- WishService가 존재하지 않음
- 비즈니스 로직(중복 검사, 소유권 확인, 상품 유효성 검증)이 모두 Controller 내에 있음
- 트랜잭션 경계를 Service 레이어에서 관리할 수 없음

### 2.3 인증 처리 패턴 문제

모든 엔드포인트에서 동일한 인증 보일러플레이트 코드 반복:
```java
var member = authenticationResolver.extractMember(authorization);
if (member == null) {
    return ResponseEntity.status(401).build();
}
```
- 3개 메서드 모두 동일한 코드 반복 (DRY 위반)
- 인증 실패 시 `null` 반환 후 수동 401 처리 -- Spring Security의 인증 메커니즘이나 HandlerMethodArgumentResolver 활용이 더 적합
- 단, 이 인증 방식은 프로젝트 전체적으로 동일한 패턴이므로 wish 패키지만 단독 변경하면 일관성이 깨짐 -> **wish 범위에서는 Service로 추출만 하고, 인증 방식 자체 변경은 보류**

### 2.4 미사용 코드 후보

| 위치 | 항목 | 상태 | 근거 | 결론 |
|------|------|------|------|------|
| `OrderController` (외부) | `WishRepository` import 및 필드 주입 | **미사용** | `wishRepository.` 호출이 OrderController 내에 단 한 건도 없음. git blame: 최초 커밋(feat: set up the project)에서 추가됨. 주석 `// 6. cleanup wish`에 "위시 정리" 언급이 있으나 구현되지 않음 | **삭제 권장** -- 단, order 패키지 담당 에이전트와 협의 필요. 향후 주문 시 위시 자동 삭제 기능 구현 예정일 수 있으므로 주석을 근거로 판단 필요 |
| `Wish` 엔티티 | `// primitive FK - no entity reference` 주석 (16행) | 주석 부정확 | `memberId`는 primitive FK이지만, 이 주석은 설계 의도를 설명하는 것이지 미사용은 아님 | **주석 정확도 개선** -- Member 엔티티 연관관계 미설정 이유를 명확히 기술 |
| `WishController` | 모든 import | **모두 사용 중** | 미사용 import 없음 | 유지 |

### 2.5 코드 스타일 불일치

| 항목 | 현재 상태 | 문제점 |
|------|-----------|--------|
| **null 처리 방식** | `orElse(null)` + `if (x == null)` 패턴 | Optional을 활용하지 않음. `orElseThrow()`나 `ifPresent()` 등이 더 적합 |
| **예외 처리 패턴** | 인증 실패 시 `ResponseEntity.status(401).build()`, 리소스 미발견 시 `ResponseEntity.notFound().build()` | 다른 컨트롤러(ProductController, MemberController)는 `@ExceptionHandler`를 사용하나 WishController는 사용하지 않음. 예외를 던지지 않고 직접 ResponseEntity 구성 |
| **Javadoc/주석** | 인라인 주석만 존재 (`// check auth`, `// check product`) | MemberController, AuthenticationResolver는 클래스 수준 Javadoc(`@author`, `@since`)이 있으나 WishController에는 없음 |
| **생성자 주입 스타일** | `@Autowired` 없는 생성자 주입 | MemberController, AuthenticationResolver는 `@Autowired` 명시. 프로젝트 내 혼재 |
| **DTO-Entity 변환** | `WishResponse.from(Wish)` 정적 팩토리 메서드 사용 | 이 부분은 일관성 있으며 좋은 패턴 |
| **Pageable 사용** | `getWishes`에서 `Pageable` 파라미터 사용 | 일관성 있으며 양호 |
| **URI 생성** | `URI.create("/api/wishes/" + saved.getId())` 하드코딩 | `ServletUriComponentsBuilder` 미사용. 다른 컨트롤러도 동일한 패턴이므로 프로젝트 전체 이슈 |
| **memberId 관리** | Wish 엔티티에 `Long memberId`로 primitive FK 사용 (Member 엔티티 참조 없음) | Product는 `@ManyToOne`으로 참조하면서 Member는 ID만 저장 -- 비대칭적 설계. 단, 이 변경은 엔티티 구조 변경이므로 리스크가 높아 별도 판단 필요 |

### 2.6 트랜잭션 안전성

- `addWish`: 중복 검사(`findByMemberIdAndProductId`)와 저장(`save`)이 동일 트랜잭션 안에 있지 않아 race condition 가능
- `removeWish`: 조회(`findById`)와 삭제(`delete`)가 동일 트랜잭션 안에 있지 않음
- `@Transactional` 어노테이션이 어디에도 없음 -- Service 추출 시 반드시 추가해야 함

---

## 3. 구현해야 할 기능 목록 (체크리스트)

### 미사용 코드 정리
- [ ] `OrderController`에서 미사용 `WishRepository` import/필드/생성자 파라미터 제거 (order 담당 에이전트와 협의)
- [ ] `Wish` 엔티티의 `// primitive FK - no entity reference` 주석을 설계 의도가 명확한 문구로 개선

### WishService 추출
- [ ] `WishService` 클래스 생성 (`gift.wish.WishService`)
- [ ] `@Service`, `@Transactional(readOnly = true)` 클래스 레벨 어노테이션 적용
- [ ] `getWishes(Long memberId, Pageable pageable)` 메서드 추출 -- 위시 목록 조회
- [ ] `addWish(Long memberId, Long productId)` 메서드 추출 -- 상품 검증 + 중복 검사 + 저장, `@Transactional` 적용
- [ ] `removeWish(Long memberId, Long wishId)` 메서드 추출 -- 존재 확인 + 소유권 검증 + 삭제, `@Transactional` 적용
- [ ] Service에서 비즈니스 예외 발생 시 적절한 예외 클래스 사용 (예: `NoSuchElementException`, `IllegalArgumentException`, 또는 커스텀 예외)

### Controller 경량화
- [ ] `WishController`에서 `ProductRepository` 직접 의존 제거
- [ ] `WishController`에서 비즈니스 로직 제거 -- Service에 위임만
- [ ] 인증 로직은 Controller에 유지하되 (프로젝트 전체 패턴 일관성), 향후 ArgumentResolver/Interceptor 전환 가능하도록 Service 메서드 시그니처에서 `authorization`이 아닌 `memberId`를 받도록 설계
- [ ] `@ExceptionHandler` 추가하여 Service 예외를 적절한 HTTP 응답으로 변환

### 코드 스타일 통일
- [ ] `orElse(null)` + null 체크를 `orElseThrow()`로 변경 (Service 내부)
- [ ] Controller 인라인 주석(`// check auth` 등) 제거 -- 메서드 추출로 코드 자체가 의도를 표현
- [ ] `@Autowired` 사용 여부 통일 -- 프로젝트 관례 따르기 (현재 혼재 상태이므로 생략 방향이 Spring 권장)
- [ ] 클래스 수준 Javadoc 추가 여부 결정 (프로젝트 관례: 일부만 있음 -> 추가하지 않는 쪽이 현재 wish와 일관)

### 테스트 작성
- [ ] `WishService` 단위 테스트 작성 (Mockito 기반)
  - 정상 조회 테스트
  - 상품 미존재 시 예외 테스트
  - 중복 위시 시 기존 반환 테스트
  - 소유권 불일치 시 예외 테스트
- [ ] `WishController` 통합 테스트 작성 (MockMvc 기반, E2E 관점)
  - 인증 실패 시 401 응답 테스트
  - 정상 CRUD 흐름 테스트

---

## 4. 전략 (단계별)

### Step 1: 안전장치 마련
1. 현재 코드가 컴파일되고 기존 동작이 정상임을 확인 (`./gradlew build`)
2. `WishController`의 현재 API 계약을 문서화:
   - `GET /api/wishes` -- 인증된 사용자의 위시 목록 (페이징)
   - `POST /api/wishes` -- 위시 추가 (중복 시 기존 반환)
   - `DELETE /api/wishes/{id}` -- 위시 삭제 (소유자만)
3. 각 엔드포인트의 HTTP 응답 코드 계약:
   - 200: 정상 조회/중복 위시
   - 201: 새 위시 생성
   - 204: 삭제 성공
   - 401: 인증 실패
   - 403: 소유권 불일치
   - 404: 리소스 미발견

### Step 2: 미사용 코드 정리
1. `OrderController`의 미사용 `WishRepository` 의존 제거 (order 담당과 협의 후)
   - **주의**: `// 6. cleanup wish` 주석이 향후 구현 예정을 암시 -- 삭제 시 이 사실을 커밋 메시지에 기록
2. `Wish` 엔티티의 주석 개선: `// memberId만 저장 -- Member 엔티티 양방향 참조를 의도적으로 피함`

### Step 3: WishService 추출
1. `gift.wish.WishService` 클래스 생성
2. `WishRepository`와 `ProductRepository` 주입
3. 3개의 public 메서드 추출:

```java
@Service
@Transactional(readOnly = true)
public class WishService {
    private final WishRepository wishRepository;
    private final ProductRepository productRepository;

    // 생성자

    public Page<WishResponse> getWishes(Long memberId, Pageable pageable) {
        return wishRepository.findByMemberId(memberId, pageable).map(WishResponse::from);
    }

    @Transactional
    public WishResponse addWish(Long memberId, Long productId) {
        var product = productRepository.findById(productId)
            .orElseThrow(() -> new NoSuchElementException("상품을 찾을 수 없습니다: " + productId));

        return wishRepository.findByMemberIdAndProductId(memberId, product.getId())
            .map(WishResponse::from)
            .orElseGet(() -> {
                var saved = wishRepository.save(new Wish(memberId, product));
                return WishResponse.from(saved);
            });
    }

    @Transactional
    public void removeWish(Long memberId, Long wishId) {
        var wish = wishRepository.findById(wishId)
            .orElseThrow(() -> new NoSuchElementException("위시를 찾을 수 없습니다: " + wishId));

        if (!wish.getMemberId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 위시만 삭제할 수 있습니다.");
        }

        wishRepository.delete(wish);
    }
}
```

**주의사항:**
- `addWish`에서 중복 위시 시 기존 것을 반환하는 동작은 유지 (작동 변경 금지)
- 단, Service에서 "새로 생성됨" vs "기존 반환"을 구분할 수 있는 방법이 필요 (Controller에서 201 vs 200 응답 코드 분기). 이를 위해 `addWish`의 반환 타입을 커스텀 래퍼 또는 별도 플래그로 설계:

```java
// 옵션 A: 반환 타입에 생성 여부 포함
public record WishAddResult(WishResponse wish, boolean created) {}

// 옵션 B: Controller에서 별도 중복 체크 유지 (단순한 방법)
```

**권장: 옵션 A** -- 비즈니스 판단(신규 vs 기존)을 Service에 위임하면서 Controller가 HTTP 응답 코드를 결정할 수 있음

### Step 4: Controller 경량화 (로직 재분배)
1. `WishController`에서 `ProductRepository` 의존 제거
2. 각 메서드를 Service 위임으로 변경:

```java
@RestController
@RequestMapping("/api/wishes")
public class WishController {
    private final WishService wishService;
    private final AuthenticationResolver authenticationResolver;

    // 생성자

    @GetMapping
    public ResponseEntity<Page<WishResponse>> getWishes(
        @RequestHeader("Authorization") String authorization,
        Pageable pageable
    ) {
        var member = authenticationResolver.extractMember(authorization);
        if (member == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(wishService.getWishes(member.getId(), pageable));
    }

    @PostMapping
    public ResponseEntity<WishResponse> addWish(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody WishRequest request
    ) {
        var member = authenticationResolver.extractMember(authorization);
        if (member == null) {
            return ResponseEntity.status(401).build();
        }
        var result = wishService.addWish(member.getId(), request.productId());
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/wishes/" + result.wish().id()))
                .body(result.wish());
        }
        return ResponseEntity.ok(result.wish());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeWish(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Long id
    ) {
        var member = authenticationResolver.extractMember(authorization);
        if (member == null) {
            return ResponseEntity.status(401).build();
        }
        wishService.removeWish(member.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(403).body(e.getMessage());
    }
}
```

### Step 5: 테스트 작성
1. **WishServiceTest (단위 테스트)**
   - `WishRepository`, `ProductRepository`를 Mockito로 모킹
   - 테스트 케이스:
     - `getWishes_정상조회_페이지반환`
     - `addWish_상품존재_신규위시생성`
     - `addWish_상품존재_중복위시반환`
     - `addWish_상품미존재_예외발생`
     - `removeWish_정상삭제`
     - `removeWish_위시미존재_예외발생`
     - `removeWish_소유자불일치_예외발생`

2. **WishControllerTest (통합 테스트, MockMvc)**
   - `WishService`를 `@MockBean`으로 모킹
   - 테스트 케이스:
     - `인증실패시_401반환`
     - `위시목록조회_200반환`
     - `위시추가_신규_201반환`
     - `위시추가_중복_200반환`
     - `위시삭제_204반환`
     - `상품미존재_404반환`
     - `소유권불일치_403반환`

### Step 6: 코드 스타일 정리
1. `orElse(null)` 패턴을 Service에서 `orElseThrow()`로 교체 (Step 3에서 이미 적용)
2. 인라인 주석 제거 (`// check auth`, `// check product`, `// check duplicate`)
3. `Wish` 엔티티 주석 개선
4. `@Autowired` 어노테이션 -- WishController/WishService에서는 생략 (Spring 4.3+ 단일 생성자 자동 주입)
5. 최종 import 정리 (`WishController`에서 `ProductRepository` import 제거 등)

---

## 5. 리스크 및 작동 동일성 검증 방법

### 리스크 항목

| # | 리스크 | 영향도 | 발생 가능성 | 대응 방안 |
|---|--------|--------|-------------|-----------|
| R1 | `addWish`의 201/200 분기 로직이 Service 추출 과정에서 누락될 수 있음 | 높음 | 중간 | `WishAddResult` 래퍼 타입으로 생성 여부 전달. 테스트로 검증 |
| R2 | `@Transactional` 추가 시 기존 동작과 미세한 차이 발생 가능 (예: 지연 쓰기, 프록시 동작) | 중간 | 낮음 | 기존에 트랜잭션이 없었으므로 추가는 개선임. 기존 응답과 동일한지 E2E 테스트로 확인 |
| R3 | `OrderController`에서 `WishRepository` 제거 시, 향후 "주문 시 위시 자동 삭제" 기능 구현이 필요할 수 있음 | 중간 | 중간 | 삭제 시 커밋 메시지에 기록. 향후 필요 시 WishService를 OrderService에서 호출하도록 설계 (Repository 직접 참조보다 나은 구조) |
| R4 | `@ExceptionHandler`에서 `IllegalArgumentException`을 403으로 매핑하면, 다른 원인의 `IllegalArgumentException`도 403이 됨 | 중간 | 낮음 | 커스텀 예외 클래스(`WishAccessDeniedException` 등) 도입 검토. 또는 `removeWish`에서만 발생하므로 현재 범위에서는 안전 |
| R5 | WishResponse.from()이 `wish.getProduct()`를 호출하는데 지연 로딩 시 Service 밖에서 `LazyInitializationException` 발생 가능 | 높음 | 중간 | Service 메서드 내에서 DTO 변환까지 완료하여 트랜잭션 범위 내에서 Product 접근 보장 |

### 작동 동일성 검증 방법

1. **API 계약 검증 (수동 테스트)**
   - 리팩터링 전후로 아래 시나리오를 HTTP 클라이언트(curl/Postman)로 실행하여 응답 코드와 바디가 동일한지 확인:
     - 인증 없이 요청 -> 401
     - 유효 토큰으로 위시 목록 조회 -> 200 + 페이지 응답
     - 존재하는 상품으로 위시 추가 -> 201 + WishResponse
     - 동일 상품으로 위시 재추가 -> 200 + 기존 WishResponse
     - 존재하지 않는 상품으로 위시 추가 -> 404
     - 본인 위시 삭제 -> 204
     - 타인 위시 삭제 -> 403
     - 존재하지 않는 위시 삭제 -> 404

2. **자동 테스트**
   - Step 5에서 작성한 단위/통합 테스트가 모두 통과하는지 확인
   - `./gradlew test` 전체 green 확인

3. **컴파일 검증**
   - 리팩터링 각 단계 후 `./gradlew compileJava` 실행하여 컴파일 에러 없음 확인

4. **리그레션 방지**
   - 기존 프로젝트에 테스트가 없으므로(test 디렉토리에 파일 0개), 리팩터링 전에 최소한의 E2E 테스트를 먼저 작성하는 것을 권장 (Step 5를 Step 3 이전으로 앞당기는 것도 고려)

---

## 6. 완료 조건 (Definition of Done)

- [ ] **컴파일 성공**: `./gradlew compileJava`가 에러 없이 통과
- [ ] **모든 테스트 통과**: `./gradlew test`가 green (새로 작성한 WishServiceTest, WishControllerTest 포함)
- [ ] **Controller 경량화 달성**: WishController가 WishService에만 위임하고 비즈니스 로직을 포함하지 않음
  - Controller의 Repository 직접 의존: `WishRepository`(X), `ProductRepository`(X) -- 모두 제거됨
  - Controller의 의존: `WishService`, `AuthenticationResolver`만
- [ ] **Service 레이어 존재**: `WishService`가 `@Service` + `@Transactional`로 올바르게 구성됨
- [ ] **미사용 코드 제거 완료**: 제거/유지 결정이 근거와 함께 문서화됨
  - `OrderController`의 미사용 `WishRepository` 처리 완료
- [ ] **코드 스타일 일관성**:
  - `orElse(null)` + null 체크 패턴이 Service 내에서 `orElseThrow()`로 대체됨
  - 불필요한 인라인 주석 제거됨
  - import 정리 완료
- [ ] **작동 동일성 확인**: 리팩터링 전후 모든 API 엔드포인트의 HTTP 응답 코드 및 응답 바디가 동일함
  - 201(신규 위시), 200(중복 위시), 200(목록 조회), 204(삭제), 401(인증 실패), 403(권한 없음), 404(미발견) 모두 동일
- [ ] **트랜잭션 안전성**: `addWish`, `removeWish`에 `@Transactional`이 적용되어 race condition 방지
- [ ] **LazyInitializationException 방지**: DTO 변환이 트랜잭션 범위 내에서 완료됨
