# spring-gift-refactoring

## 리팩터링 전략

### 1. 미사용 코드 정리
- 미사용되는 코드를 제거하거나, 실제로 사용하도록 수정

### 2. 로직 재분배
1. 각 기능에 대한 책임 나누기
2. 기능별로 책임에 맞게 일을 하고 있는지, 월권하는지 체크하기
3. 문제 있는 것만 End-to-End 테스트 짜기 (연관성 있는 범위만)
4. 기능을 각자 위치로 이동시키기
5. 테스트 성공시키기

### 3. 코드 스타일 통일화

---

## 수행해야 할 기능 목록

### Auth / Member (`01-auth-member-plan`)

#### 미사용 코드 제거
- [x] `AuthenticationResolver`에서 불필요한 `@Autowired` 어노테이션 및 import 제거
- [x] `JwtProvider`에서 불필요한 `@Autowired` 어노테이션 및 import 제거
- [x] `MemberController`에서 불필요한 `@Autowired` 어노테이션 및 import 제거
- [x] `AdminMemberController`에서 불필요한 `@Autowired` 어노테이션 및 import 제거

#### 서비스 계층 추출
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

#### 로직 재분배 (Controller -> Service 위임)
- [ ] `MemberController.register()` -- `MemberService.register()` 위임으로 변경
- [ ] `MemberController.login()` -- `MemberService.login()` 위임으로 변경
- [ ] `AdminMemberController` 모든 메서드 -- `MemberService` 위임으로 변경
- [ ] `KakaoAuthController.callback()` -- `KakaoAuthService.kakaoLogin()` 위임으로 변경
- [ ] `KakaoAuthController.login()` -- `KakaoAuthService.buildKakaoAuthUrl()` 위임으로 변경
- [ ] `MemberController`의 `@ExceptionHandler` 제거 후 글로벌 `@ControllerAdvice` 도입 검토 (다른 기능 팀과 조율 필요)

#### AuthenticationResolver 개선
- [ ] 예외 발생 시 null 반환 대신 명확한 예외를 던지도록 개선 검토 (주의: 호출자 동작에 영향)
- [ ] 또는 `Optional<Member>` 반환으로 변경하여 null-safety 확보 (호출자도 함께 수정 필요 -- wish/order 팀 조율 필요)

#### 코드 스타일 통일
- [ ] Javadoc 누락 클래스에 Javadoc 추가: `KakaoAuthController`, `KakaoLoginClient`, `KakaoLoginProperties`
- [ ] `KakaoAuthController`의 `/* */` 블록 주석을 `/** */` Javadoc으로 변경
- [ ] 에러 메시지 언어 통일: `Member.chargePoint()`와 `Member.deductPoint()`의 에러 메시지를 한 언어로 통일
- [ ] 주석 언어 통일: `Member.deductPoint()` 위의 영어 주석(`// point deduction for order payment`)과 한국어 에러 메시지 혼재 정리
- [ ] import 정리: `@Autowired` 제거 후 불필요한 import 일괄 제거 확인

#### 테스트 작성
- [ ] `MemberService` 단위 테스트 작성 (register, login, chargePoint, deductPoint 시나리오)
- [ ] `KakaoAuthService` 단위 테스트 작성 (신규 회원 자동가입, 기존 회원 토큰 갱신 시나리오)
- [ ] `MemberController` 통합 테스트 작성 (HTTP 요청/응답 검증)
- [ ] `AdminMemberController` 통합 테스트 작성 (뷰 반환 검증)
- [ ] `KakaoAuthController` 통합 테스트 작성 (리다이렉트 + 콜백 검증)

---

### Category (`02-category-plan`)

#### 서비스 추출 및 로직 재분배
- [ ] `CategoryService` 클래스 신규 생성 (`@Service`, `@Transactional` 적용)
- [ ] `findAll()` 로직을 Service로 이동 (조회 시 `@Transactional(readOnly = true)`)
- [ ] `findById()` + 존재 확인 로직을 Service로 이동
- [ ] `create()` 로직을 Service로 이동 (DTO -> Entity 변환 + 저장)
- [ ] `update()` 로직을 Service로 이동 (존재 확인 + 상태 변경). dirty checking 활용으로 명시적 `save()` 제거 검토
- [ ] `delete()` 로직을 Service로 이동 (삭제 전 Product 참조 존재 여부 확인 로직 추가)
- [ ] `CategoryController`를 Service 위임 전용으로 변경 (Repository 의존 제거)

#### 외부 패키지 의존 정리
- [ ] `ProductController`에서 `CategoryRepository` 직접 참조를 `CategoryService`로 변경 (product 리팩터링 시 처리 -- 여기서는 계획만)
- [ ] `AdminProductController`에서 `CategoryRepository` 직접 참조를 `CategoryService`로 변경 (product 리팩터링 시 처리 -- 여기서는 계획만)

#### 스타일 통일
- [ ] null 처리 패턴을 `orElseThrow()` + 커스텀 예외(또는 `NoSuchElementException`)로 통일
- [ ] `CategoryController`에 `@ExceptionHandler` 추가 또는 글로벌 `@ControllerAdvice` 도입 검토
- [ ] `Category` 엔티티에 `@Column` 어노테이션 보강 (`nullable`, `unique`, `length` 등 DB 스키마와 일치)
- [ ] `updateCategory()`에서 dirty checking 활용 시 명시적 `save()` 제거 (Service에 `@Transactional` 적용 후)

#### 테스트
- [ ] `CategoryService` 단위 테스트 작성 (Mockito 기반 -- Repository mock)
- [ ] `CategoryController` 통합 테스트 작성 (`@WebMvcTest` 기반)
- [ ] Category 삭제 시 Product 참조 존재 케이스에 대한 예외 테스트
- [ ] 전체 CRUD E2E 테스트 작성 (`@SpringBootTest` + `TestRestTemplate` 또는 `MockMvc`)

---

### Product / Option (`03-product-option-plan`)

#### 안전장치
- [ ] 기존 동작을 검증할 수 있는 통합 테스트(E2E) 작성 (Product CRUD, Option CRUD)
- [ ] `@Transactional` 부재 확인 및 테스트 시나리오에 트랜잭션 경계 포함

#### 미사용 코드 정리
- [ ] `Product.getOptions()` 반환값을 `Collections.unmodifiableList()`로 래핑

#### 서비스 추출
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

#### 로직 재분배
- [ ] `ProductController`에서 비즈니스 로직 제거, `ProductService`에 위임만 수행
- [ ] `AdminProductController`에서 비즈니스 로직 제거, `ProductService`에 위임만 수행 (폼 Model 세팅은 Controller에 유지)
- [ ] `OptionController`에서 비즈니스 로직 제거, `OptionService`에 위임만 수행
- [ ] `ProductController`에서 `CategoryRepository` 직접 의존 제거 (Service 내부로 이동)
- [ ] `OptionController`에서 `ProductRepository` 직접 의존 제거 (Service 내부로 이동)
- [ ] Validator 호출을 Service 내부로 이동 (Controller의 `validateName()` private 메서드 제거)
- [ ] `@ExceptionHandler` 중복 제거 -- `@ControllerAdvice` 글로벌 핸들러 생성 (프로젝트 공통이므로 다른 기능 팀과 조율 필요)

#### 테스트
- [ ] `ProductService` 단위 테스트 작성 (Repository mock)
- [ ] `OptionService` 단위 테스트 작성 (Repository mock)
- [ ] `ProductController` 슬라이스 테스트 (`@WebMvcTest`) -- Service를 mock하여 위임만 확인
- [ ] `OptionController` 슬라이스 테스트 (`@WebMvcTest`) -- Service를 mock하여 위임만 확인
- [ ] `AdminProductController` 슬라이스 테스트 -- Service를 mock하여 위임만 확인
- [ ] 기존 통합 테스트가 모두 green인지 확인

#### 스타일 정리
- [ ] `findById().orElse(null)` + null 체크 패턴을 `orElseThrow()` 패턴으로 통일 (Service 내부)
- [ ] `Collectors.toList()` -> `.toList()` 전환
- [ ] `OptionRequest`에 `toEntity(Product)` 팩토리 메서드 추가 (또는 Service에서 변환 통일)
- [ ] import 정렬 통일 (IDE 포맷터 적용)
- [ ] 불필요한 주석 제거 또는 의미 있는 Javadoc으로 전환

---

### Order (`04-order-plan`)

#### 미사용 코드 정리
- [x] `OrderController`에서 `WishRepository` 필드, 생성자 파라미터, import 제거
- [x] `WishRepository` import 제거 후 "wish cleanup 미구현" TODO 이슈 별도 기록

#### 서비스 추출 (OrderService 신규 생성)
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

#### Controller 슬림화
- [ ] `OrderController`에서 `OptionRepository`, `MemberRepository`, `KakaoMessageClient` 직접 의존 제거
- [ ] `OrderController`는 `AuthenticationResolver`와 `OrderService`만 의존하도록 변경
- [ ] Controller 메서드는 인증 추출 + Service 위임 + 응답 변환만 수행

#### 예외 처리 통일
- [ ] `OrderController`에 `@ExceptionHandler(IllegalArgumentException.class)` 추가
- [ ] `catch (Exception ignored)` 블록에 로깅(warn) 추가 (Service로 이동 후)
- [ ] `orElse(null)` + null 체크 패턴을 Service에서 `orElseThrow()` 패턴으로 변경

#### 스타일 정리
- [ ] import 순서 정렬 (java.* -> jakarta.* -> org.* -> gift.*)
- [ ] `ResponseEntity<?>` 와일드카드를 구체 타입으로 변경 가능 여부 검토
- [ ] `KakaoMessageClient`의 JSON 문자열 템플릿을 유지하되, 이스케이프 안전성 검증
- [ ] `sendKakaoMessageIfPossible` 메서드를 Service로 이동하며 메서드명 유지

#### 테스트
- [ ] `OrderService` 단위 테스트 작성 (주문 생성 정상 플로우)
- [ ] `OrderService` 단위 테스트 작성 (재고 부족 시 예외)
- [ ] `OrderService` 단위 테스트 작성 (포인트 부족 시 예외)
- [ ] `OrderService` 단위 테스트 작성 (존재하지 않는 옵션 시 예외)
- [ ] `OrderController` 통합 테스트 작성 (E2E: 정상 주문 생성 -> 201 응답)
- [ ] `OrderController` 통합 테스트 작성 (E2E: 인증 실패 -> 401 응답)
- [ ] `KakaoMessageClient` 단위 테스트 작성 (템플릿 생성 검증)

---

### Wish (`05-wish-plan`)

> `OrderController`에서 미사용 `WishRepository` 제거 항목은 **Order** 섹션에 통합 (중복 제거)

#### WishService 추출
- [ ] `WishService` 클래스 생성 (`gift.wish.WishService`)
- [ ] `@Service`, `@Transactional(readOnly = true)` 클래스 레벨 어노테이션 적용
- [ ] `getWishes(Long memberId, Pageable pageable)` 메서드 추출 -- 위시 목록 조회
- [ ] `addWish(Long memberId, Long productId)` 메서드 추출 -- 상품 검증 + 중복 검사 + 저장, `@Transactional` 적용
- [ ] `removeWish(Long memberId, Long wishId)` 메서드 추출 -- 존재 확인 + 소유권 검증 + 삭제, `@Transactional` 적용
- [ ] Service에서 비즈니스 예외 발생 시 적절한 예외 클래스 사용 (예: `NoSuchElementException`, `IllegalArgumentException`, 또는 커스텀 예외)

#### Controller 경량화
- [ ] `WishController`에서 `ProductRepository` 직접 의존 제거
- [ ] `WishController`에서 비즈니스 로직 제거 -- Service에 위임만
- [ ] 인증 로직은 Controller에 유지하되, 향후 ArgumentResolver/Interceptor 전환 가능하도록 Service 메서드 시그니처에서 `authorization`이 아닌 `memberId`를 받도록 설계
- [ ] `@ExceptionHandler` 추가하여 Service 예외를 적절한 HTTP 응답으로 변환

#### 코드 스타일 통일
- [ ] `orElse(null)` + null 체크를 `orElseThrow()`로 변경 (Service 내부)
- [ ] Controller 인라인 주석(`// check auth` 등) 제거 -- 메서드 추출로 코드 자체가 의도를 표현
- [ ] `@Autowired` 사용 여부 통일 -- 프로젝트 관례에 따라 생략 (Spring 4.3+ 단일 생성자 자동 주입)
- [ ] 클래스 수준 Javadoc 추가 여부 결정 (프로젝트 관례: 일부만 있음 -> 추가하지 않는 쪽이 현재 wish와 일관)

#### 테스트 작성
- [ ] `WishService` 단위 테스트 작성 (Mockito 기반)
  - 정상 조회 테스트
  - 상품 미존재 시 예외 테스트
  - 중복 위시 시 기존 반환 테스트
  - 소유권 불일치 시 예외 테스트
- [ ] `WishController` 통합 테스트 작성 (MockMvc 기반, E2E 관점)
  - 인증 실패 시 401 응답 테스트
  - 정상 CRUD 흐름 테스트

---

### 요약

| 기능 (파일) | 체크리스트 항목 수 |
|---|---|
| Auth / Member (`01-auth-member-plan`) | 36 |
| Category (`02-category-plan`) | 17 |
| Product / Option (`03-product-option-plan`) | 28 |
| Order (`04-order-plan`) | 29 |
| Wish (`05-wish-plan`) | 17 |
| **총계** | **127** |
