# Category 패키지 리팩터링 계획서

## 1. 기능 범위 정의

### 1.1 담당 패키지/클래스 목록

| 클래스 | 경로 | 역할 |
|---|---|---|
| `Category` | `gift.category.Category` | JPA 엔티티 (도메인 모델) |
| `CategoryController` | `gift.category.CategoryController` | REST API 컨트롤러 (`/api/categories`) |
| `CategoryRepository` | `gift.category.CategoryRepository` | JPA Repository 인터페이스 |
| `CategoryRequest` | `gift.category.CategoryRequest` | 요청 DTO (Java record) |
| `CategoryResponse` | `gift.category.CategoryResponse` | 응답 DTO (Java record) |

### 1.2 기능 경계 및 외부 의존 관계

**Category를 참조하는 외부 클래스:**

| 외부 클래스 | 참조 대상 | 참조 방식 |
|---|---|---|
| `gift.product.Product` | `Category` | `@ManyToOne @JoinColumn(name="category_id")` FK 관계 |
| `gift.product.ProductController` | `Category`, `CategoryRepository` | 카테고리 조회 후 Product 생성/수정에 사용 |
| `gift.product.AdminProductController` | `Category`, `CategoryRepository` | 카테고리 조회 후 Product 생성/수정에 사용 |
| `gift.product.ProductRequest` | `Category` | `toEntity(Category category)` 파라미터로 사용 |

**DB 스키마 제약 (V1 마이그레이션):**
- `product` 테이블의 `category_id`가 `category(id)`를 FK로 참조 (NOT NULL, ON DELETE 제약 없음)
- `category.name`에 `UNIQUE` 제약 조건 존재 (엔티티에 미반영)

**핵심 인터페이스 계약:**
- `CategoryRepository`가 product 패키지에서 직접 주입되어 사용됨 -- 서비스 레이어 추출 시 이 의존 관계를 함께 리팩터링해야 함

---

## 2. 현상 진단

### 2.1 Controller 비대 여부 (심각도: 높음)

`CategoryController`가 `CategoryRepository`를 직접 주입받아 모든 CRUD 로직을 직접 수행하고 있다. **Service 레이어가 존재하지 않는다.**

구체적인 문제점:
- **`getCategories()`**: Repository 호출 + 스트림 변환을 컨트롤러에서 직접 수행
- **`createCategory()`**: `request.toEntity()` + `repository.save()` + URI 생성을 컨트롤러에서 직접 수행
- **`updateCategory()`**: `findById` + null 체크 + `entity.update()` + `repository.save()` -- 비즈니스 로직(존재 확인, 갱신)이 컨트롤러에 존재
- **`deleteCategory()`**: `repository.deleteById()` 직접 호출 -- 삭제 시 해당 카테고리를 참조하는 Product 존재 여부 확인 로직 부재

### 2.2 Service 부재 (심각도: 높음)

프로젝트 전체에 `@Service` 어노테이션이 붙은 클래스가 **하나도 없다.** 모든 패키지(product, option, order, member, category)가 Controller에서 Repository를 직접 호출하는 동일한 안티패턴을 보인다. Category 패키지에서 Service를 먼저 추출하면 프로젝트 전체의 리팩터링 패턴을 확립할 수 있다.

### 2.3 책임 혼재 항목

| 위치 | 현재 책임 | 올바른 위치 |
|---|---|---|
| `CategoryController.updateCategory()` | 엔티티 존재 확인 (null 체크) | Service |
| `CategoryController.updateCategory()` | 엔티티 상태 변경 (`category.update(...)`) | Service |
| `CategoryController.createCategory()` | DTO -> Entity 변환 호출 | Service |
| `CategoryController.deleteCategory()` | 참조 무결성 확인 없이 바로 삭제 | Service (삭제 전 Product 참조 확인 필요) |
| `CategoryRequest.toEntity()` | DTO에서 Entity 생성 | 유지 가능하나 Service에서 호출하도록 이동 |

### 2.4 미사용 코드 후보

| 항목 | 종류 | 현황 | 결론 |
|---|---|---|---|
| 모든 import | import문 | 전 파일에서 사용되지 않는 import 없음 | **미사용 import 없음** |
| `Category.getColor()` | getter | `CategoryResponse.from()`에서 사용 | **사용 중 -- 유지** |
| `Category.getDescription()` | getter | `CategoryResponse.from()`에서 사용 | **사용 중 -- 유지** |
| `Category.getImageUrl()` | getter | `CategoryResponse.from()`에서 사용 | **사용 중 -- 유지** |
| `Category.update()` | 메서드 | `CategoryController.updateCategory()`에서 사용 | **사용 중 -- 유지** |

> **결론**: category 패키지 내에 미사용 코드(import, 메서드, 필드, 상수)는 발견되지 않았다. 모든 코드가 활발히 사용되고 있으므로 삭제 대상은 없다.

**git blame 요약**: 모든 category 파일이 동일 커밋 `55ca9e43`(wotjd243, 2026-02-18)에서 한번에 작성되었다. TODO/주석이 전혀 없으며, 이후 수정 이력도 없다.

### 2.5 스타일 불일치 항목

| 항목 | 현재 상태 | 문제점 |
|---|---|---|
| **null 처리 패턴** | `findById(id).orElse(null)` + null 체크 (CategoryController:46-48) | 프로젝트 내 AdminProductController는 `orElseThrow()` 사용. 패턴 불일치 |
| **예외 처리** | `CategoryController`에 `@ExceptionHandler` 없음 | ProductController, OptionController, MemberController에는 존재. 일관성 부재 |
| **DB 제약 vs 엔티티** | DB에 `category.name UNIQUE` 제약 존재 | `Category` 엔티티에 `@Column(unique=true)` 미선언. DB-엔티티 불일치 |
| **DB 제약 vs 엔티티** | DB에 `color VARCHAR(7)`, `image_url VARCHAR(255)` NOT NULL | `Category` 엔티티에 `@Column(nullable=false)`, `@Column(length=7)` 미선언 |
| **DTO 검증** | `CategoryRequest.description`에 `@NotBlank` 없음 | DB 스키마에서 `description`은 nullable이므로 의도적. 하지만 명시적 `@Nullable` 어노테이션 부재 |
| **delete 안전성** | `deleteCategory()`가 바로 `deleteById()` 호출 | FK 제약으로 인해 Product가 참조하는 Category 삭제 시 DB 에러 발생. 적절한 에러 처리 없음 |
| **`@Transactional` 부재** | updateCategory에서 find+update+save 수행 | 트랜잭션 경계가 명시되지 않음. dirty checking 대신 명시적 save() 호출 |

---

## 3. 구현해야 할 기능 목록 (체크리스트)

### 3.1 서비스 추출 및 로직 재분배
- [ ] `CategoryService` 클래스 신규 생성 (`@Service`, `@Transactional` 적용)
- [ ] `findAll()` 로직을 Service로 이동 (조회 시 `@Transactional(readOnly = true)`)
- [ ] `findById()` + 존재 확인 로직을 Service로 이동
- [ ] `create()` 로직을 Service로 이동 (DTO -> Entity 변환 + 저장)
- [ ] `update()` 로직을 Service로 이동 (존재 확인 + 상태 변경). dirty checking 활용으로 명시적 `save()` 제거 검토
- [ ] `delete()` 로직을 Service로 이동 (삭제 전 Product 참조 존재 여부 확인 로직 추가)
- [ ] `CategoryController`를 Service 위임 전용으로 변경 (Repository 의존 제거)

### 3.2 외부 패키지 의존 정리
- [ ] `ProductController`에서 `CategoryRepository` 직접 참조를 `CategoryService`로 변경 (product 리팩터링 시 처리 -- 여기서는 계획만)
- [ ] `AdminProductController`에서 `CategoryRepository` 직접 참조를 `CategoryService`로 변경 (product 리팩터링 시 처리 -- 여기서는 계획만)

### 3.3 스타일 통일
- [ ] null 처리 패턴을 `orElseThrow()` + 커스텀 예외(또는 `NoSuchElementException`)로 통일
- [ ] `CategoryController`에 `@ExceptionHandler` 추가 또는 글로벌 `@ControllerAdvice` 도입 검토
- [ ] `Category` 엔티티에 `@Column` 어노테이션 보강 (`nullable`, `unique`, `length` 등 DB 스키마와 일치)
- [ ] `updateCategory()`에서 dirty checking 활용 시 명시적 `save()` 제거 (Service에 `@Transactional` 적용 후)

### 3.4 테스트
- [ ] `CategoryService` 단위 테스트 작성 (Mockito 기반 -- Repository mock)
- [ ] `CategoryController` 통합 테스트 작성 (`@WebMvcTest` 기반)
- [ ] Category 삭제 시 Product 참조 존재 케이스에 대한 예외 테스트
- [ ] 전체 CRUD E2E 테스트 작성 (`@SpringBootTest` + `TestRestTemplate` 또는 `MockMvc`)

---

## 4. 전략 (단계별)

### Step 1: 안전장치 확보
**목표**: 리팩터링 전 현재 동작을 검증할 수 있는 테스트 확보

1. `CategoryController`에 대한 통합 테스트 작성 (`@SpringBootTest` + `MockMvc`)
   - `GET /api/categories` -- 200 + 목록 반환
   - `POST /api/categories` -- 201 + Location 헤더 + body 반환
   - `PUT /api/categories/{id}` -- 200 + 갱신된 body / 존재하지 않는 id -> 404
   - `DELETE /api/categories/{id}` -- 204
2. 테스트가 모두 green인지 확인

### Step 2: 미사용 코드 정리
**목표**: 불필요한 코드 제거

1. 현재 분석 결과 미사용 코드가 없으므로, 이 단계는 **스킵 가능**
2. 추후 리팩터링 과정에서 불필요해지는 코드(예: Controller의 Repository 의존)는 해당 단계에서 제거

### Step 3: CategoryService 추출
**목표**: 비즈니스 로직을 Service 레이어로 이동

1. `CategoryService` 클래스 생성
   ```java
   @Service
   @Transactional(readOnly = true)
   public class CategoryService {
       private final CategoryRepository categoryRepository;
       // ...
   }
   ```
2. 메서드 추출:
   - `List<CategoryResponse> findAll()`
   - `CategoryResponse findById(Long id)` -- 존재하지 않으면 예외
   - `CategoryResponse create(CategoryRequest request)` -- `@Transactional`
   - `CategoryResponse update(Long id, CategoryRequest request)` -- `@Transactional`
   - `void delete(Long id)` -- `@Transactional`
3. 각 메서드 추출 후 즉시 Step 1 테스트 실행하여 green 확인

### Step 4: 로직 재분배 (Controller 경량화)
**목표**: Controller에서 비즈니스 로직 제거, 순수 위임만 수행

1. `CategoryController`의 의존을 `CategoryRepository` -> `CategoryService`로 교체
2. 각 핸들러 메서드를 Service 위임 코드로 교체:
   ```java
   @GetMapping
   public ResponseEntity<List<CategoryResponse>> getCategories() {
       return ResponseEntity.ok(categoryService.findAll());
   }
   ```
3. Controller에서 `CategoryRepository` import 및 필드 제거
4. Step 1 테스트 재실행하여 green 확인
5. 외부 패키지(ProductController, AdminProductController)에서의 `CategoryRepository` 직접 사용은 **product 리팩터링 단계에서 처리** -- category 패키지에서는 `CategoryService`를 public 인터페이스로 노출

### Step 5: 테스트 보강
**목표**: Service 레이어에 대한 단위 테스트 추가

1. `CategoryServiceTest` 작성 (Mockito 기반)
   - 정상 CRUD 케이스
   - `findById` -- 존재하지 않는 id -> 예외 발생 검증
   - `delete` -- Product 참조 존재 시 예외 발생 검증 (향후 로직 추가 시)
   - `update` -- 존재하지 않는 id -> 예외 발생 검증
2. Step 1에서 작성한 통합 테스트가 여전히 green인지 확인

### Step 6: 스타일 정리
**목표**: 코드 스타일 일관성 확보

1. `Category` 엔티티에 `@Column` 어노테이션 보강:
   ```java
   @Column(nullable = false, unique = true)
   private String name;

   @Column(nullable = false, length = 7)
   private String color;

   @Column(nullable = false)
   private String imageUrl;

   // description은 nullable이므로 @Column 생략 가능 (기본값)
   ```
2. null 처리 패턴 통일: `orElse(null)` -> `orElseThrow(() -> new NoSuchElementException(...))`
   - 이미 Step 3에서 Service 추출 시 적용됨
3. 예외 처리 일관성: `@ExceptionHandler` 추가 또는 글로벌 `@ControllerAdvice` 도입
   - **권장**: 글로벌 `@ControllerAdvice`를 공통 패키지에 신설하여 프로젝트 전체 적용
   - category 단독으로 진행한다면 Controller에 `@ExceptionHandler(NoSuchElementException.class)` 추가
4. `@Transactional` 적용 후 `updateCategory`에서 명시적 `save()` 호출 제거 (dirty checking 활용)
5. 전체 테스트 green 확인

---

## 5. 리스크 & 작동 동일성 검증 방법

### 5.1 리스크 항목

| # | 리스크 | 심각도 | 대응 방안 |
|---|---|---|---|
| R1 | Service 추출 시 트랜잭션 경계 변경으로 동작 차이 발생 | 중 | 현재 Controller에 `@Transactional` 없으므로, 각 Repository 호출이 개별 트랜잭션. Service에 `@Transactional` 추가 시 하나의 트랜잭션으로 묶임. `update()`에서 find+update가 하나의 트랜잭션이 되므로 오히려 정합성 향상. 동작 변경이 아닌 개선에 해당 |
| R2 | dirty checking 전환 시 `save()` 제거로 동작 차이 | 낮 | `@Transactional` 내에서 managed entity의 변경은 자동 flush됨. 기존 `save()` 호출과 동일 결과. 통합 테스트로 검증 |
| R3 | `orElse(null)` -> `orElseThrow()` 전환 시 응답 코드 변경 | 중 | 현재: 404 (ResponseEntity.notFound), 변경 후: 예외 -> `@ExceptionHandler`에서 404 반환. HTTP 응답 레벨에서 동일하도록 ExceptionHandler 구성 필수 |
| R4 | `deleteCategory()`에 참조 확인 로직 추가 시 기존 동작 변경 | 높 | 현재는 Product가 참조하는 Category 삭제 시 DB FK 위반 에러가 500으로 반환됨. 서비스에서 사전 확인 후 400/409 반환은 **동작 개선**이지 변경이 아님. 단, API 스펙이 변경되므로 문서화 필요 |
| R5 | 외부 패키지(ProductController, AdminProductController)가 `CategoryRepository`를 직접 사용 | 높 | category 리팩터링 단계에서는 `CategoryService`를 추가로 공개만 하고, 외부 패키지의 의존 변경은 **product 리팩터링 단계**에서 처리. `CategoryRepository`의 public 접근 제한(package-private)은 외부 의존 전환 완료 후에만 수행 |
| R6 | `Category` 엔티티에 `@Column` 어노테이션 추가 시 Flyway 마이그레이션 충돌 | 낮 | `@Column` 어노테이션은 JPA 메타데이터일 뿐, `spring.jpa.hibernate.ddl-auto`가 `validate`인 경우에만 영향. 기존 Flyway 마이그레이션과 일치하므로 문제 없음 |

### 5.2 작동 동일성 검증 방법

1. **Step 1 통합 테스트를 기준선(Baseline)으로 활용**
   - 모든 리팩터링 단계마다 Step 1 테스트를 실행하여 green 확인
   - 테스트 항목: 4개 엔드포인트 x 정상/비정상 케이스 = 최소 6개 테스트

2. **HTTP 응답 비교**
   - 리팩터링 전후 동일한 요청에 대해 동일한 HTTP 상태 코드 + 응답 body 반환 확인
   - 특히 `PUT /api/categories/{존재하지않는id}` -> 404, `DELETE /api/categories/{id}` -> 204

3. **트랜잭션 동작 검증**
   - `update` 시 DB에 실제 반영되는지 확인 (조회 -> 수정 -> 재조회)
   - `create` 후 auto-generated id가 응답에 포함되는지 확인

4. **외부 패키지 영향 없음 확인**
   - `CategoryRepository`가 여전히 public이므로 ProductController/AdminProductController 컴파일 통과 확인
   - 전체 `./gradlew build` 성공 확인

---

## 6. 완료 조건 (Definition of Done)

- [ ] **테스트 전체 green**: `./gradlew test` 실행 시 모든 테스트 통과
- [ ] **Controller 경량화**: `CategoryController`가 `CategoryService`에만 의존하며, Repository 직접 접근 코드 없음
- [ ] **Service 레이어 존재**: `CategoryService`가 `@Service` + `@Transactional`로 선언되고, 모든 비즈니스 로직(존재 확인, CRUD, 참조 검증)을 포함
- [ ] **책임 분리 준수**:
  - Controller: 요청 수신 -> 검증 -> Service 위임 -> 응답 반환만 수행
  - Service: 유즈케이스 로직 + 트랜잭션 경계 관리
  - Repository: 영속성 접근만 (커스텀 쿼리 없으면 변경 없음)
  - Entity: 도메인 불변식 (`update()` 메서드 유지)
- [ ] **미사용 코드 제거 근거 문서화**: 현 분석에서 미사용 코드 없음을 확인함 (본 문서 2.4절)
- [ ] **스타일 일관성**:
  - `@Column` 어노테이션이 DB 스키마와 일치
  - null 처리가 `orElseThrow()` 패턴으로 통일
  - 예외 처리 패턴이 다른 컨트롤러와 일관적
- [ ] **외부 의존 안전성**: `CategoryRepository`가 여전히 접근 가능하여 외부 패키지(product) 컴파일 에러 없음
- [ ] **빌드 성공**: `./gradlew build` 전체 통과
- [ ] **작동 동일성**: 리팩터링 전후 API 응답(상태 코드, body)이 동일함을 통합 테스트로 검증 완료
