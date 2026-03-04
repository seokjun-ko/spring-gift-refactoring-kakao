# Product-Option 리팩터링 계획서

## 1. 기능 범위 정의

### 1.1 담당 패키지/클래스 목록

| 패키지 | 클래스 | 역할 |
|--------|--------|------|
| `gift.product` | `Product` | 상품 엔티티 (JPA `@Entity`) |
| `gift.product` | `ProductController` | REST API 컨트롤러 (`/api/products`) |
| `gift.product` | `AdminProductController` | 관리자 MVC 컨트롤러 (`/admin/products`) |
| `gift.product` | `ProductNameValidator` | 상품명 검증 유틸리티 (정적 메서드) |
| `gift.product` | `ProductRepository` | JPA Repository 인터페이스 |
| `gift.product` | `ProductRequest` | 요청 DTO (Java record) |
| `gift.product` | `ProductResponse` | 응답 DTO (Java record) |
| `gift.option` | `Option` | 옵션 엔티티 (JPA `@Entity`) |
| `gift.option` | `OptionController` | REST API 컨트롤러 (`/api/products/{productId}/options`) |
| `gift.option` | `OptionNameValidator` | 옵션명 검증 유틸리티 (정적 메서드) |
| `gift.option` | `OptionRepository` | JPA Repository 인터페이스 |
| `gift.option` | `OptionRequest` | 요청 DTO (Java record) |
| `gift.option` | `OptionResponse` | 응답 DTO (Java record) |

### 1.2 기능 경계 -- 다른 기능과의 인터페이스/의존

| 외부 패키지 | 의존 방향 | 설명 |
|-------------|-----------|------|
| `gift.category` | **Product -> Category** | `Product` 엔티티가 `Category`를 `@ManyToOne`으로 참조. `ProductController`와 `AdminProductController`가 `CategoryRepository`를 직접 주입받아 사용 |
| `gift.wish` | **Wish -> Product** | `Wish` 엔티티가 `Product`를 `@ManyToOne`으로 참조. `WishController`가 `ProductRepository`를 직접 주입 |
| `gift.order` | **Order -> Option** | `Order` 엔티티가 `Option`을 `@ManyToOne`으로 참조. `OrderController`가 `OptionRepository`를 직접 주입. `KakaoMessageClient`가 `Product`를 파라미터로 받아 메시지 구성 |
| `gift.product` <-> `gift.option` | **양방향** | `Product`가 `Option`을 `@OneToMany(mappedBy, cascade=ALL, orphanRemoval=true)`로 보유. `OptionController`가 `ProductRepository`를 직접 주입 |

> **핵심 관찰**: 현재 Service 계층이 프로젝트 전체에 존재하지 않는다. 모든 비즈니스 로직이 Controller에 직접 작성되어 있으며, 외부 패키지들이 다른 패키지의 Repository를 직접 주입받아 사용하는 구조이다.

---

## 2. 현상 진단

### 2.1 Controller 비대 / Service 부재

**ProductController** (101줄)
- Repository를 직접 호출하여 CRUD 전체를 수행
- `CategoryRepository`를 직접 주입받아 카테고리 조회까지 컨트롤러에서 처리
- `validateName()` private 메서드로 비즈니스 검증 로직을 직접 수행
- `@ExceptionHandler`를 컨트롤러 내부에 선언 (컨트롤러별 중복)
- `findById().orElse(null)` + null 체크 패턴으로 존재 여부를 확인 (예외 기반 패턴 미사용)

**AdminProductController** (133줄)
- 동일한 CRUD 로직을 MVC 방식으로 중복 구현
- `ProductNameValidator.validate(name, true)` -- `allowKakao=true`를 하드코딩하여 호출 (API 쪽은 `false`)
- `populateNewForm()`/`populateEditForm()` 두 private 메서드가 Model 속성을 세팅하는 뷰 관련 로직 (이 부분은 Controller 레벨 책임이지만, 비즈니스 로직과 혼재)

**OptionController** (104줄)
- `ProductRepository`를 직접 주입받아 상품 존재 여부를 확인
- 옵션 중복명 체크(`existsByProductIdAndName`) 비즈니스 규칙을 컨트롤러에서 직접 수행
- 옵션 최소 1개 보장 규칙(`options.size() <= 1` 체크)을 컨트롤러에서 직접 수행
- `@ExceptionHandler`를 컨트롤러 내부에 중복 선언

**결론**: 3개의 Controller 모두에 비즈니스 로직이 혼재되어 있으며, **Service 클래스가 프로젝트 전체에 단 하나도 존재하지 않는다**. `ProductService`와 `OptionService` 추출이 필수적이다.

### 2.2 책임 혼재 항목

| 위치 | 문제 | 올바른 위치 |
|------|------|-------------|
| `ProductController.validateName()` | 비즈니스 검증 로직 | Service 또는 Validator |
| `ProductController.createProduct()` | CategoryRepository 직접 조회 + 엔티티 생성 | Service |
| `ProductController.updateProduct()` | Category 조회 + Product 조회 + update + save | Service |
| `AdminProductController.create()` | CategoryRepository 직접 조회 + 엔티티 생성 + 검증 | Service |
| `AdminProductController.update()` | 동일한 로직 중복 | Service (ProductController와 공유) |
| `OptionController.createOption()` | 상품 존재 확인 + 옵션명 중복 체크 + 엔티티 생성 | Service |
| `OptionController.deleteOption()` | 최소 1개 옵션 보장 규칙 + 소유권 검증 | Service |
| `OptionController.validateName()` | 비즈니스 검증 로직 | Service 또는 Validator |
| 각 Controller `@ExceptionHandler` | 3곳에 동일한 핸들러 중복 | `@ControllerAdvice` 글로벌 핸들러 |

### 2.3 미사용 코드 후보

| 위치 | 항목 | 상태 | 결론 |
|------|------|------|------|
| `ProductController` (20줄) | `import java.util.List` | `validateName()` 메서드의 `List<String> errors`에서 **사용 중** | 유지 (Service 추출 시 Controller에서 제거될 수 있음) |
| `Product.getOptions()` (70줄) | `List<Option> getOptions()` | Java 코드에서 직접 호출처 없음. Thymeleaf 템플릿에서도 `options` 참조 없음 | **삭제 후보** -- 단, `@OneToMany` 매핑 자체는 JPA cascade/orphanRemoval에 필요하므로 필드는 유지, getter만 접근 제한 검토. `OrderController.sendKakaoMessageIfPossible()`에서 `option.getProduct()`를 호출하고 Product를 통해 간접적으로 접근할 가능성이 있으므로, 삭제 전 충분한 검증 필요. **현 시점 결론: getter는 유지, unmodifiableList 래핑 추가 권장** |
| `OptionController` (18줄) | `import java.util.stream.Collectors` | `Collectors.toList()` 사용 중이나, `.toList()`로 대체 가능 | **활용 개선**: `.collect(Collectors.toList())` -> `.toList()` 변경 후 import 삭제 |
| `ProductNameValidator.validate(String, boolean)` | `allowKakao` 오버로드 | `AdminProductController`에서 `true`, `ProductController`에서 `false`(기본값)로 호출 | **사용 중** -- 유지 |
| `Option.subtractQuantity()` | 수량 차감 메서드 | `OrderController`에서 `option.subtractQuantity(request.quantity())`로 **사용 중** | 유지 |

### 2.4 스타일 불일치 항목

| 항목 | 현상 | 개선 방향 |
|------|------|-----------|
| **null 처리 패턴 불일치** | `ProductController`: `findById().orElse(null)` + `if (x == null)` 패턴. `AdminProductController`: `findById().orElseThrow()` 패턴. 두 가지가 혼재 | 통일: Service 계층에서 `orElseThrow()`로 일원화 |
| **예외 처리 중복** | `ProductController`, `OptionController`, `MemberController` 각각에 동일한 `@ExceptionHandler(IllegalArgumentException.class)` 선언 | `@ControllerAdvice` 글로벌 핸들러로 통합 |
| **Collectors.toList() vs .toList()** | `OptionController`에서 `Collectors.toList()` 사용 | Java 16+ `.toList()` 사용으로 통일 |
| **생성자 주입 스타일** | `MemberController`는 `@Autowired` 명시, 나머지는 생략 | 현 프로젝트에서는 `@Autowired` 생략이 다수이므로 생략으로 통일 (단, MemberController는 담당 범위 밖이므로 참고만) |
| **record DTO의 toEntity 메서드** | `ProductRequest`에는 `toEntity(Category)` 존재, `OptionRequest`에는 없음 (Controller에서 직접 `new Option(...)` 호출) | 일관성을 위해 `OptionRequest`에도 `toEntity(Product)` 추가 또는, Service에서 변환하는 방식으로 통일 |
| **import 정렬** | 대체로 패키지 그룹 순서가 일관적이나, `java.*`와 `jakarta.*` 순서가 파일마다 미세하게 다름 | IDE 포맷터 설정 통일 후 일괄 정리 |
| **주석 스타일** | `OptionController`/`OptionNameValidator`에만 블록 주석 존재, 나머지는 주석 없음 | 주석이 필요한 곳에만 의미 있는 주석 유지. 자명한 코드에는 주석 불필요 |
| **응답 코드 불일치** | `ProductController.getProduct()`: 존재하지 않을 때 `404`. `ProductController.createProduct()`: 카테고리 없을 때도 `404`. 의미가 다른 두 상황에 동일 코드 | Service 계층에서 의미에 맞는 커스텀 예외 도입 권장 (`ProductNotFoundException`, `CategoryNotFoundException` 등) |
| **Product.getOptions() 불변성** | `getOptions()`가 mutable한 내부 `ArrayList`를 그대로 반환 | `Collections.unmodifiableList()` 래핑 권장 |

---

## 3. 구현해야 할 기능 목록 (체크리스트)

### 3.1 안전장치

- [ ] 기존 동작을 검증할 수 있는 통합 테스트(E2E) 작성 (Product CRUD, Option CRUD)
- [ ] `@Transactional` 부재 확인 및 테스트 시나리오에 트랜잭션 경계 포함

### 3.2 미사용 코드 정리

- [ ] `OptionController`의 `import java.util.stream.Collectors` 제거 (`.toList()` 전환 후)
- [ ] `Product.getOptions()` 반환값을 `Collections.unmodifiableList()`로 래핑
- [ ] 기타 IDE 경고 기반 미사용 import 일괄 정리

### 3.3 서비스 추출

- [ ] `ProductService` 클래스 생성 (`gift.product` 패키지)
- [ ] `OptionService` 클래스 생성 (`gift.option` 패키지)
- [ ] `ProductService`에 다음 메서드 추출:
  - `getProducts(Pageable): Page<ProductResponse>`
  - `getProduct(Long): ProductResponse`
  - `createProduct(ProductRequest): ProductResponse`
  - `updateProduct(Long, ProductRequest): ProductResponse`
  - `deleteProduct(Long): void`
  - `findById(Long): Product` (내부용 -- 다른 서비스에서 Product 엔티티가 필요할 때)
- [ ] `OptionService`에 다음 메서드 추출:
  - `getOptions(Long productId): List<OptionResponse>`
  - `createOption(Long productId, OptionRequest): OptionResponse`
  - `deleteOption(Long productId, Long optionId): void`
  - `subtractQuantity(Long optionId, int amount): void` (OrderController가 현재 직접 수행 중인 로직)
- [ ] 두 서비스에 `@Service` + `@Transactional` 적용

### 3.4 로직 재분배

- [ ] `ProductController`에서 비즈니스 로직 제거, `ProductService`에 위임만 수행
- [ ] `AdminProductController`에서 비즈니스 로직 제거, `ProductService`에 위임만 수행 (폼 Model 세팅은 Controller에 유지)
- [ ] `OptionController`에서 비즈니스 로직 제거, `OptionService`에 위임만 수행
- [ ] `ProductController`에서 `CategoryRepository` 직접 의존 제거 (Service 내부로 이동)
- [ ] `OptionController`에서 `ProductRepository` 직접 의존 제거 (Service 내부로 이동)
- [ ] Validator 호출을 Service 내부로 이동 (Controller의 `validateName()` private 메서드 제거)
- [ ] `@ExceptionHandler` 중복 제거 -- `@ControllerAdvice` 글로벌 핸들러 생성 (단, 이 부분은 프로젝트 공통이므로 다른 서브에이전트와 협의 필요)

### 3.5 테스트

- [ ] `ProductService` 단위 테스트 작성 (Repository mock)
- [ ] `OptionService` 단위 테스트 작성 (Repository mock)
- [ ] `ProductController` 슬라이스 테스트 (`@WebMvcTest`) -- Service를 mock하여 위임만 확인
- [ ] `OptionController` 슬라이스 테스트 (`@WebMvcTest`) -- Service를 mock하여 위임만 확인
- [ ] `AdminProductController` 슬라이스 테스트 -- Service를 mock하여 위임만 확인
- [ ] 기존 통합 테스트가 모두 green인지 확인

### 3.6 스타일 정리

- [ ] `findById().orElse(null)` + null 체크 패턴을 `orElseThrow()` 패턴으로 통일 (Service 내부)
- [ ] `Collectors.toList()` -> `.toList()` 전환
- [ ] `OptionRequest`에 `toEntity(Product)` 팩토리 메서드 추가 (또는 Service에서 변환 통일)
- [ ] import 정렬 통일 (IDE 포맷터 적용)
- [ ] 불필요한 주석 제거 또는 의미 있는 Javadoc으로 전환
- [ ] `Product.getOptions()`에 `Collections.unmodifiableList()` 래핑

---

## 4. 전략 (단계별)

### Step 1: 안전장치 마련

**목적**: 리팩터링 전 기존 동작을 보장하는 테스트 그물망 확보

1. `ProductControllerIntegrationTest` 작성
   - GET `/api/products` (페이징 포함)
   - GET `/api/products/{id}` (존재/미존재)
   - POST `/api/products` (정상/검증 실패/카테고리 미존재)
   - PUT `/api/products/{id}` (정상/미존재)
   - DELETE `/api/products/{id}`
2. `AdminProductControllerIntegrationTest` 작성
   - GET `/admin/products` (목록 뷰)
   - POST `/admin/products` (생성 + 검증 실패 시 폼 재표시)
   - POST `/admin/products/{id}/edit` (수정)
   - POST `/admin/products/{id}/delete` (삭제)
3. `OptionControllerIntegrationTest` 작성
   - GET `/api/products/{productId}/options`
   - POST `/api/products/{productId}/options` (정상/중복명/상품 미존재)
   - DELETE `/api/products/{productId}/options/{optionId}` (정상/최소 1개 위반/미존재)
4. 모든 테스트 green 확인 후 다음 단계 진행

### Step 2: 미사용 코드 정리

**목적**: 불필요한 코드를 제거하여 리팩터링 대상을 줄임

1. `OptionController`의 `.collect(Collectors.toList())` -> `.toList()`로 변경
2. 변경 후 `import java.util.stream.Collectors` 제거
3. IDE 정적 분석으로 미사용 import 일괄 정리
4. 안전장치 테스트 재실행으로 동작 동일성 확인

### Step 3: 서비스 클래스 추출

**목적**: 비즈니스 로직의 올바른 위치를 만듦

1. `ProductService` 클래스 생성
   ```java
   @Service
   @Transactional(readOnly = true)
   public class ProductService {
       private final ProductRepository productRepository;
       private final CategoryRepository categoryRepository;
       // ...
   }
   ```
2. `OptionService` 클래스 생성
   ```java
   @Service
   @Transactional(readOnly = true)
   public class OptionService {
       private final OptionRepository optionRepository;
       private final ProductRepository productRepository;
       // ...
   }
   ```
3. 각 Controller에서 비즈니스 로직을 **복사** (아직 제거하지 않음)하여 Service 메서드로 이동
4. 안전장치 테스트 재실행으로 동작 동일성 확인

### Step 4: 로직 재분배 (Controller 얇게 만들기)

**목적**: Controller가 요청 검증/변환/위임만 수행하도록 변경

1. `ProductController`의 의존성을 `ProductService`로 교체
   - `ProductRepository`, `CategoryRepository` 직접 의존 제거
   - 각 핸들러 메서드를 Service 호출로 대체
   - `validateName()` private 메서드 제거 (Service 내부로 이동됨)
2. `AdminProductController`의 비즈니스 로직을 `ProductService`로 위임
   - 폼 Model 세팅 로직(`populateNewForm`, `populateEditForm`)은 Controller에 유지
   - 저장/수정/삭제 로직만 Service에 위임
   - Admin용 메서드가 필요하면 Service에 별도 메서드 추가 (`createProductForAdmin` 등 -- `allowKakao=true` 처리)
3. `OptionController`의 의존성을 `OptionService`로 교체
   - `ProductRepository` 직접 의존 제거
   - 옵션 중복명 체크, 최소 1개 보장 규칙 등이 Service 내부에 있는지 확인
4. `@ExceptionHandler` 중복 제거 계획 수립
   - `GlobalExceptionHandler` (`@ControllerAdvice`) 생성
   - `IllegalArgumentException` -> 400 BAD REQUEST
   - `NoSuchElementException` -> 404 NOT FOUND
   - 각 Controller의 `@ExceptionHandler` 메서드 제거
   - **주의**: 이 부분은 `member`, `order` 등 다른 패키지에도 영향을 미치므로, 프로젝트 공통 리팩터링으로 분류하거나 다른 서브에이전트와 조율 필요
5. 안전장치 테스트 재실행으로 동작 동일성 확인

### Step 5: 테스트 보강

**목적**: Service 계층에 대한 단위 테스트 추가, 전체 커버리지 확보

1. `ProductServiceTest` 작성 (JUnit 5 + Mockito)
   - 정상 CRUD 시나리오
   - 상품 미존재 시 예외
   - 카테고리 미존재 시 예외
   - 상품명 검증 실패 시 예외
2. `OptionServiceTest` 작성 (JUnit 5 + Mockito)
   - 정상 CRUD 시나리오
   - 옵션명 중복 시 예외
   - 옵션 최소 1개 보장 위반 시 예외
   - 상품 미존재 시 예외
3. Controller 슬라이스 테스트 (`@WebMvcTest`)
   - Service를 `@MockBean`으로 mock
   - HTTP 요청/응답 형식, 상태 코드만 검증
4. 전체 테스트 green 확인

### Step 6: 스타일 정리

**목적**: 코드 일관성 확보 (동작 변경 없음)

1. null 처리 패턴 통일
   - Service 내부: `repository.findById(id).orElseThrow(() -> new NoSuchElementException(...))`
   - Controller: 예외는 글로벌 핸들러가 처리
2. `Product.getOptions()` 불변 래핑
   ```java
   public List<Option> getOptions() {
       return Collections.unmodifiableList(options);
   }
   ```
3. `OptionRequest`에 `toEntity(Product)` 추가
   ```java
   public Option toEntity(Product product) {
       return new Option(product, name, quantity);
   }
   ```
4. import 정렬 및 포맷 통일 (IDE 자동 포맷 적용)
5. 블록 주석 정리 -- 자명한 코드의 불필요한 주석 제거
6. 최종 테스트 green 확인

---

## 5. 리스크 및 작동 동일성 검증 방법

### 5.1 리스크 항목

| # | 리스크 | 영향도 | 완화 방법 |
|---|--------|--------|-----------|
| R1 | **Service 추출 시 트랜잭션 경계 변경** | 높음 | 현재 `@Transactional`이 전혀 없으므로, Service에 추가 시 auto-commit에서 트랜잭션 관리로 전환됨. 정상 케이스에서는 동작 동일하나, 예외 발생 시 롤백 동작이 달라질 수 있음. 이는 **버그 수정에 해당**하므로 허용 범위 내이지만, 기존 테스트로 부작용 검증 필요 |
| R2 | **외부 패키지의 Repository 직접 참조 변경** | 중간 | `WishController -> ProductRepository`, `OrderController -> OptionRepository` 등 외부 패키지가 직접 참조 중. product-option 범위에서는 Service 인터페이스를 제공하되, 외부 패키지의 변경은 해당 서브에이전트 담당으로 남김 |
| R3 | **AdminProductController의 allowKakao=true 분기** | 중간 | API는 `allowKakao=false`, Admin은 `allowKakao=true`로 호출. Service 추출 시 이 분기를 파라미터로 유지하거나, 별도 메서드로 분리. 기존 동작을 변경하지 않도록 주의 |
| R4 | **@ExceptionHandler 글로벌화 시 기존 응답 형식 변경 가능** | 중간 | 현재 각 Controller의 `@ExceptionHandler`가 `ResponseEntity<String>`을 반환. 글로벌 핸들러로 이동 시 동일한 응답 형식을 유지해야 함 |
| R5 | **Product.getOptions() unmodifiableList 래핑** | 낮음 | 현재 외부에서 이 리스트를 수정하는 코드가 없으므로 영향 없음. JPA의 cascade/orphanRemoval은 내부 필드(`options`)를 직접 사용하므로 getter 래핑에 영향받지 않음 |
| R6 | **Collectors.toList() -> .toList() 전환** | 낮음 | 반환 타입이 `List<T>`로 동일. 단, `.toList()`는 불변 리스트를 반환하므로 이후 코드에서 리스트를 수정하는 곳이 없는지 확인 필요 (현재 없음) |

### 5.2 작동 동일성 검증 방법

| 검증 방법 | 설명 |
|-----------|------|
| **Step 1 통합 테스트** | 리팩터링 전에 작성한 통합 테스트를 매 Step 완료 후 실행. 모든 테스트가 green이면 동작 동일 |
| **HTTP 응답 비교** | 주요 엔드포인트의 HTTP 상태 코드 + 응답 body 형식이 리팩터링 전후로 동일한지 테스트에서 검증 |
| **예외 시나리오 검증** | 검증 실패, 리소스 미존재, 옵션 최소 1개 위반 등 에러 케이스의 HTTP 상태 코드 + 메시지가 동일한지 확인 |
| **수동 스모크 테스트** | Admin 페이지 (`/admin/products`) 접근하여 상품 목록/생성/수정/삭제가 정상 동작하는지 브라우저로 확인 |
| **git diff 코드 리뷰** | 각 Step의 diff를 리뷰하여 동작 변경이 없는지 확인. 특히 조건문, 분기, 예외 처리 변경 여부를 중점 검토 |

---

## 6. 완료 조건 (Definition of Done)

| # | 조건 | 검증 방법 |
|---|------|-----------|
| D1 | **모든 테스트 green** | `./gradlew test` 실행 시 전체 통과 |
| D2 | **Controller가 얇고 위임만 수행** | ProductController, AdminProductController, OptionController 각각에 비즈니스 로직 없음. Repository 직접 의존 없음 (Service만 주입). 각 핸들러 메서드가 5줄 이내 |
| D3 | **Service 계층 존재** | `ProductService`, `OptionService`가 `@Service` + `@Transactional`로 선언되어 있으며, 모든 비즈니스 로직을 포함 |
| D4 | **미사용 코드 제거 근거 문서화** | 본 문서 섹션 2.3에 모든 미사용 후보가 "삭제"/"유지" 결론과 근거를 포함 |
| D5 | **스타일 일관성** | null 처리 패턴, import 정렬, DTO 변환 방식, 예외 처리 패턴이 전체 담당 범위에서 일관적 |
| D6 | **외부 인터페이스 호환** | `WishController`, `OrderController` 등 외부 패키지가 사용하는 `ProductRepository`, `OptionRepository`에 대한 접근 경로가 유지되거나, Service 인터페이스로 대체 경로 제공 |
| D7 | **@ExceptionHandler 중복 제거** | 글로벌 `@ControllerAdvice`가 존재하고, 각 Controller에 개별 `@ExceptionHandler`가 없음 (프로젝트 공통이므로 다른 서브에이전트와 협의 후 최종 적용) |
| D8 | **트랜잭션 경계 적용** | 쓰기 연산(create/update/delete)에 `@Transactional`, 읽기 연산에 `@Transactional(readOnly = true)` 적용 |
