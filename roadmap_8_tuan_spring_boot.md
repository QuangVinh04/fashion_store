# 🗺️ Roadmap 8 Tuần: Ôn Java/Spring Boot + Xây Project Website Bán Quần Áo

> **Đối tượng:** Người ở mức cơ bản → trung bình, có 2–4 giờ/ngày
> **Tech stack:** Spring Boot 3.5, Java 21, PostgreSQL, JPA/Hibernate, Lombok, Swagger
> **Kiến trúc:** Modular Monolith
> **Project:** `clothes-retail-api` (đã khởi tạo)

---

## 📚 3 Khóa Học Udemy Sử Dụng

| # | Khóa học | Viết tắt | Nội dung chính |
|---|----------|----------|----------------|
| 1 | [Mastering Modern Java Programming: Beginner to Pro](https://www.udemy.com/course/java-programming-a-comprehensive-bootcamp-from-zero-to-hero/) | **☕ JAVA** | Java Core: OOP, Collections, Stream, Exception, Generics, Lambda (30 sections) |
| 2 | [Master Spring 7, Spring Boot 4, REST, JPA, Security](https://www.udemy.com/course/spring-springboot-jpa-hibernate-zero-to-master/) | **🍃 SPRING** | Spring Core, REST API, JPA, Relationships, Validation, Docker, Transactions, Caching, Deploy (23 sections) |
| 3 | [Spring Security Zero to Master + JWT, OAuth2](https://www.udemy.com/course/spring-security-zero-to-master/) | **🔐 SECURITY** | Security Architecture, JWT, CSRF, Roles/Authorities, OAuth2 (14+ sections) |

---

## 🏛️ Kiến Trúc: Modular Monolith

### Cấu Trúc Package

```
com.fashionstore.clothes_retail_api/
├── common/           ← Shared: BaseEntity, ApiResponse, exceptions, config
├── auth/             ← Authentication: User entity, JWT, Security config
├── user/             ← User profile: xem/sửa thông tin, đổi password
├── category/         ← CRUD danh mục (ADMIN)
├── product/          ← CRUD sản phẩm, tìm kiếm, phân trang
├── cart/             ← Giỏ hàng
├── order/            ← Đặt hàng, quản lý đơn
├── upload/           ← Upload ảnh (tuần 7)
├── admin/            ← Dashboard thống kê (tuần 7)
└── ClothesRetailApiApplication.java
```

**Mỗi module có cấu trúc nội bộ:**
```
module/
├── entity/        ← JPA entities
├── repository/    ← Spring Data repositories
├── service/       ← Business logic (interface + impl)
├── dto/           ← Request/Response DTOs
└── controller/    ← REST controllers
```

### Nguyên Tắc Giao Tiếp Giữa Module
- ✅ Inject qua **interface** (`CartService` inject `ProductService`)
- ✅ Truyền dữ liệu bằng **DTO**, không truyền entity
- ❌ KHÔNG gọi chéo repository module khác

---

## 🔀 Git Convention: Conventional Commits

### Format

```
<type>(<scope>): <mô tả ngắn gọn>

[body - tùy chọn: giải thích chi tiết]
```

### Types

| Type | Khi nào dùng | Ví dụ |
|------|-------------|-------|
| `init` | Khởi tạo project, setup ban đầu | `init: setup spring boot project structure` |
| `feat` | Thêm tính năng mới | `feat(product): add search by name endpoint` |
| `fix` | Sửa bug | `fix(cart): fix duplicate item when adding same product` |
| `refactor` | Tái cấu trúc code (không thay đổi behavior) | `refactor(order): extract price calculation to helper` |
| `docs` | Thêm/sửa documentation | `docs: add swagger annotations for auth endpoints` |
| `test` | Thêm/sửa tests | `test(product): add unit tests for ProductService` |
| `chore` | Config, dependencies, CI/CD | `chore: add spring security dependency` |
| `style` | Format code, sắp xếp imports | `style: format code and organize imports` |

### Scopes (theo module)

```
common | category | product | auth | user | cart | order | upload | admin
```

### Quy tắc
- ✅ Viết bằng **tiếng Anh**, chữ thường, **không dấu chấm cuối**
- ✅ Mỗi commit = **1 đơn vị công việc** hoàn chỉnh (entity, service, controller...)
- ✅ Dùng **Present tense**: `add`, `create`, `implement`, `update`, `fix`, `remove`
- ❌ KHÔNG commit code bị lỗi, không compile
- ❌ KHÔNG commit quá lớn (ví dụ: cả module 1 commit)

### Workflow — Push thẳng `main`

> **Project cá nhân, code 1 mình → không cần chia nhánh.** Tập trung vào commit messages chất lượng.

```bash
# Workflow hàng ngày:
git add .
git commit -m "feat(category): add Category entity extending BaseEntity"
git push origin main
```

---

## 📅 CHI TIẾT TỪNG TUẦN

---

## 🟢 TUẦN 1 — Java Core + Setup Modular Monolith + Category Module

### 📚 Khóa học cần xem

| Khóa | Sections | Nội dung | ~Giờ video |
|------|----------|----------|------------|
| ☕ JAVA | S1–S3 | Hello Java, Primitive types, JDK/JRE/JVM | ~6.5h |
| ☕ JAVA | S4 | Classes, methods, constructors, OOP, encapsulation | ~5h |
| ☕ JAVA | S9 | Packages, Access modifiers, POJO | ~2h |
| ☕ JAVA | S10 | Inheritance, Polymorphism, Abstract, final | ~4h |
| ☕ JAVA | S11 | Interfaces, Functional Interface | ~1.5h |
| 🍃 SPRING | S1 | Spring Core & Maven: IoC, DI, Beans, ApplicationContext | ~2h |

> **Tổng: ~21h video** — Xem ~3h/ngày, vừa xem vừa code theo = vừa đủ 1 tuần
> **Mẹo:** Nếu đã biết Java cơ bản, xem tốc độ 1.5x cho S1–S3, tập trung kỹ S4, S10, S11

### 🎯 Mục tiêu project
- Setup package structure Modular Monolith
- Tạo **common module**: `BaseEntity`, `ApiResponse`, `GlobalExceptionHandler`
- Tạo **category module**: CRUD hoàn chỉnh

### 📆 Breakdown theo ngày

| Ngày | Học (~1.5h) | Project (~2h) |
|------|------------|---------------|
| **T2** | ☕ S1–S2: Hello Java, Primitive types | Tạo toàn bộ package structure modular |
| **T3** | ☕ S3–S4 (phần 1): JDK/JVM, Classes, methods | Tạo `common/`: `BaseEntity`, `ApiResponse`, `PageResponse` |
| **T4** | ☕ S4 (phần 2): Constructors, static, OOP | Tạo `common/exception/`: exceptions + `GlobalExceptionHandler` |
| **T5** | ☕ S9–S10 (phần 1): Packages, Inheritance | Tạo `category/entity/Category.java` + `CategoryRepository` |
| **T6** | ☕ S10 (phần 2)–S11: Polymorphism, Interface | Tạo `category/service/`, `category/dto/` |
| **T7** | 🍃 S1: Spring Core, IoC, DI, Beans | Tạo `category/controller/` — CRUD hoàn chỉnh |
| **CN** | Review + thực hành OOP exercises | Config Swagger, test API Category bằng Swagger UI |

### 📦 Module: category

**Entity: `Category` extends `BaseEntity`**
| Field | Type | Annotation |
|-------|------|------------|
| name | String | `@Column(nullable=false, unique=true)` |
| slug | String | `@Column(nullable=false, unique=true)` |
| description | String | |
| parent | Category | `@ManyToOne @JoinColumn(name="parent_id")` — nullable, self-reference |
| children | List\<Category\> | `@OneToMany(mappedBy="parent")` |

> Hỗ trợ danh mục phân cấp: Thời trang nam → Áo → Áo thun

**API:**
| Method | Endpoint | Mô tả |
|--------|----------|--------|
| GET | `/api/categories` | Lấy tất cả danh mục (dạng cây) |
| GET | `/api/categories/{id}` | Lấy theo ID (kèm children) |
| POST | `/api/categories` | Tạo mới (có thể truyền `parentId`) |
| PUT | `/api/categories/{id}` | Cập nhật |
| DELETE | `/api/categories/{id}` | Xóa |

### 🔀 Git Commits — Tuần 1

```bash
# T2
init: setup modular monolith package structure

# T3
feat(common): add BaseEntity with audit fields
feat(common): add ApiResponse and PageResponse DTOs
chore: configure application.yaml for PostgreSQL and JPA

# T4
feat(common): add ResourceNotFoundException and BadRequestException
feat(common): add GlobalExceptionHandler with @RestControllerAdvice
chore: enable JPA auditing with AuditConfig

# T5
feat(category): add Category entity with parent-child hierarchy
feat(category): add CategoryRepository

# T6
feat(category): add CategoryRequest and CategoryResponse DTOs
feat(category): add CategoryService interface and implementation

# T7
feat(category): add CategoryController with full CRUD endpoints

# CN
docs(common): add SwaggerConfig for API documentation
test(category): verify all CRUD endpoints via Swagger UI
```

---

## 🟢 TUẦN 2 — Collections + REST API + Product Module

### 📚 Khóa học cần xem

| Khóa | Sections | Nội dung | ~Giờ video |
|------|----------|----------|------------|
| ☕ JAVA | S14 | Exception handling: try-catch, custom exceptions | ~3h |
| ☕ JAVA | S16–S17 | Collections intro, Wrapper, ArrayList | ~3.5h |
| ☕ JAVA | S19–S20 | Generics, HashMap, TreeMap | ~3h |
| ☕ JAVA | S21 | HashSet, TreeSet | ~1.5h |
| 🍃 SPRING | S2 | Beans Deep Dive: Autowiring, Scopes, Lifecycle | ~2.5h |
| 🍃 SPRING | S3 | REST API: @RestController, @PathVariable, ResponseEntity | ~3.5h |
| 🍃 SPRING | S5 | Spring Data JPA: Entities, Repositories, DTO, Lombok, CORS | ~2.5h |

> **Tổng: ~19.5h video** — Ưu tiên 🍃 S3 + S5 vì trực tiếp áp vào project

### 🎯 Mục tiêu project
- Hoàn thành **product module** — CRUD + **ProductVariant** (size/color) + tìm kiếm + phân trang

### 📆 Breakdown theo ngày

| Ngày | Học (~1.5h) | Project (~2h) |
|------|------------|---------------|
| **T2** | ☕ S14: Exception handling | Tạo `Product` entity + `@ManyToOne Category` |
| **T3** | ☕ S16–S17: Collections, ArrayList | Tạo `ProductVariant` entity (size, color, sku, stock, price) |
| **T4** | 🍃 S3 (phần 1): REST API basics | Tạo Repositories + DTOs + validation |
| **T5** | 🍃 S3 (phần 2): ResponseEntity, versioning | Tạo `ProductService` + `ProductServiceImpl` (quản lý cả variants) |
| **T6** | 🍃 S5: JPA, Repositories, DTO, Lombok | Tạo `ProductController` — CRUD Product + Variant + phân trang |
| **T7** | ☕ S19–S20: Generics, Map | Thêm search + filter endpoints |
| **CN** | 🔴 **CHECKPOINT 1** — Test + fix bugs | Postman collection, review code |

### 📦 Module: product

**Entity: `Product` extends `BaseEntity`** — Thông tin chung của sản phẩm
| Field | Type | Annotation |
|-------|------|------------|
| name | String | `@NotBlank @Size(max=200)` |
| description | String | `@Column(columnDefinition="TEXT")` |
| basePrice | BigDecimal | `@NotNull @Min(0)` — giá gốc tham khảo |
| category | Category | `@ManyToOne @JoinColumn(name="category_id")` |
| active | Boolean | default `true` |
| variants | List\<ProductVariant\> | `@OneToMany(mappedBy="product", cascade=ALL)` |

**Entity: `ProductVariant` extends `BaseEntity`** — Biến thể theo size/màu
| Field | Type | Annotation |
|-------|------|------------|
| product | Product | `@ManyToOne @JoinColumn(name="product_id")` |
| size | String | `S`, `M`, `L`, `XL`, `XXL` |
| color | String | Đỏ, Xanh, Đen... |
| sku | String | `@Column(unique=true)` — mã biến thể |
| price | BigDecimal | `@NotNull @Min(0)` — giá riêng variant |
| stock | Integer | `@NotNull @Min(0)` — tồn kho riêng variant |

> ⚠️ **Stock quản lý ở variant level.** Ví dụ: "Áo thun" có variant size M/Đỏ stock=10, size L/Xanh stock=5.

**API:**
| Method | Endpoint | Mô tả |
|--------|----------|--------|
| GET | `/api/products?page=0&size=10&sort=basePrice,asc` | Phân trang |
| GET | `/api/products/{id}` | Chi tiết (kèm danh sách variants) |
| GET | `/api/products/search?keyword=áo` | Tìm kiếm |
| GET | `/api/products/category/{categoryId}` | Lọc theo danh mục |
| POST | `/api/products` | Tạo SP + variants (ADMIN) |
| PUT | `/api/products/{id}` | Cập nhật SP (ADMIN) |
| DELETE | `/api/products/{id}` | Xóa SP (ADMIN) |
| POST | `/api/products/{id}/variants` | Thêm variant (ADMIN) |
| PUT | `/api/products/{id}/variants/{variantId}` | Sửa variant (ADMIN) |
| DELETE | `/api/products/{id}/variants/{variantId}` | Xóa variant (ADMIN) |

### 🔀 Git Commits — Tuần 2

```bash
# T2
feat(product): add Product entity with ManyToOne Category relationship
feat(category): add OneToMany products mapping to Category entity

# T3
feat(product): add ProductVariant entity with size, color, sku, stock, price
feat(product): add ProductVariantRepository

# T4
feat(product): add ProductCreateRequest with nested variant DTOs
feat(product): add ProductResponse and VariantResponse DTOs
feat(product): add validation annotations to RegisterRequest DTOs

# T5
feat(product): add ProductService managing product and variants

# T6
feat(product): add ProductController with CRUD and pagination
feat(product): add variant CRUD endpoints under product

# T7
feat(product): add search endpoint by product name
feat(product): add filter endpoint by category and price range

# CN — Checkpoint 1
fix(product): fix validation error response format
style: clean up code and organize imports across modules
docs: create Postman collection for category and product APIs
```

### 🔴 CHECKPOINT 1 (Cuối tuần 2)
- [ ] Category CRUD hoạt động (có hỗ trợ parent-child)
- [ ] Product CRUD + **ProductVariant** (size/color) hoạt động
- [ ] Phân trang + tìm kiếm hoạt động
- [ ] Validation trả 400 khi input sai
- [ ] Exception handling nhất quán
- [ ] Swagger UI hiển thị đủ API (kể cả variant endpoints)
- [ ] Đã xem: ☕ S1–S4, S9–S11, S14, S16–S17, S19–S21 + 🍃 S1–S3, S5

---

## 🟡 TUẦN 3 — Spring Security + JWT + Auth Module

### 📚 Khóa học cần xem

| Khóa | Sections | Nội dung | ~Giờ video |
|------|----------|----------|------------|
| 🍃 SPRING | S10 | Spring Security Essentials: behavior, custom config | ~1.8h |
| 🍃 SPRING | S11 | Authentication: Hashing, JWT, Custom filters | ~2h |
| 🍃 SPRING | S12 | DB Auth: Users & Roles, CSRF | ~1.5h |
| 🔐 SECURITY | S1–S3 | Getting Started, Default config, InMemoryUserDetails | ~4h |
| 🔐 SECURITY | S4–S5 | DB users, PasswordEncoders, AuthenticationProvider | ~4h |
| 🔐 SECURITY | S6–S8 | Customizations, CORs/CSRF, Authorities/Roles, Custom Filters | ~7.5h |

> **Tổng: ~20.8h** — Tuần nặng nhất! Ưu tiên 🔐 S1–S6 trước, S7–S8 xem song song khi code
> **⚠️ Nếu không kịp:** Xem 🍃 S10–S12 trước (tổng quan), rồi làm project. 🔐 SECURITY xem bổ sung tuần 4

### 🎯 Mục tiêu project
- Hoàn thành **auth module**: register, login, JWT, phân quyền

### 📆 Breakdown theo ngày

| Ngày | Học (~1.5–2h) | Project (~2h) |
|------|---------------|---------------|
| **T2** | 🔐 S1: Getting Started — Security internal flow | Thêm dependencies: `spring-boot-starter-security`, `jjwt` |
| **T3** | 🔐 S2–S3: Custom config, InMemory users | Tạo `auth/entity/User.java` + `Role.java` enum |
| **T4** | 🔐 S4: DB users, PasswordEncoders | Tạo `UserRepository`, `auth/service/JwtService` |
| **T5** | 🍃 S10–S11: Security Essentials, JWT | Tạo `CustomUserDetailsService`, `JwtAuthenticationFilter` |
| **T6** | 🔐 S5–S6: AuthenticationProvider, Roles | Tạo `auth/config/SecurityConfig` |
| **T7** | 🍃 S12: CSRF, DB Authentication | Tạo `AuthService` + `AuthController` (register/login) |
| **CN** | 🔐 S7–S8: CORs/CSRF, Custom Filters | Test: register → login → JWT → protected API |

### 📦 Module: auth

**Entity: `User` extends `BaseEntity`**
| Field | Type | Annotation |
|-------|------|------------|
| email | String | `@Column(unique=true, nullable=false)` |
| password | String | `@Column(nullable=false)` |
| fullName | String | `@NotBlank` |
| phone | String | |
| address | String | |
| avatar | String | |
| role | Role (enum) | `USER` / `ADMIN` — default `USER` |

**API:**
| Method | Endpoint | Access | Mô tả |
|--------|----------|--------|--------|
| POST | `/api/auth/register` | Public | Đăng ký |
| POST | `/api/auth/login` | Public | Đăng nhập, trả JWT |
| GET | `/api/auth/me` | Authenticated | Thông tin user |

### 🔀 Git Commits — Tuần 3

```bash
# T2
chore: add spring-boot-starter-security dependency
chore: add jjwt-api, jjwt-impl, jjwt-jackson dependencies

# T3
feat(auth): add User entity with role-based access
feat(auth): add Role enum (USER, ADMIN)
feat(auth): add UserRepository with findByEmail

# T4
feat(auth): implement JwtService for token generation and validation

# T5
feat(auth): implement CustomUserDetailsService
feat(auth): add JwtAuthenticationFilter extending OncePerRequestFilter

# T6
feat(auth): add SecurityConfig with JWT filter chain

# T7
feat(auth): add RegisterRequest, LoginRequest, AuthResponse DTOs
feat(auth): implement AuthService for register and login
feat(auth): add AuthController with register, login, me endpoints

# CN
feat(auth): secure category and product admin endpoints
fix(auth): fix JWT token parsing for expired tokens
```

---

## 🟡 TUẦN 4 — JPA Relationships + User Profile + Cart Module

### 📚 Khóa học cần xem

| Khóa | Sections | Nội dung | ~Giờ video |
|------|----------|----------|------------|
| 🍃 SPRING | S4 | Spring Boot Essentials: Package structure, DevTools, H2 | ~1.3h |
| 🍃 SPRING | S8 | Essential Skills: Exceptions, Validation, JPA Auditing, Swagger | ~1.7h |
| 🍃 SPRING | S9 | JPA Relationships: OneToMany, ManyToOne, Cascade, Fetch | ~1h |
| 🍃 SPRING | S15 | Advanced Queries: JPQL, Native, N+1, Batch fetching | ~1.5h |
| 🍃 SPRING | S17 | Transactions: @Transactional, Propagation, Rollback | ~1.5h |
| ☕ JAVA | S22 | Enums in Java | ~1h |
| ☕ JAVA | S24 | Lambda Expressions, Functional Interfaces | ~3h |

> **Tổng: ~11h** — Tuần nhẹ hơn! Dành nhiều thời gian cho project

### 🎯 Mục tiêu project
- **user module**: xem/sửa profile, đổi password
- **cart module**: thêm/sửa/xóa sản phẩm → inject `ProductService` (cross-module)

### 📆 Breakdown theo ngày

| Ngày | Học (~1h) | Project (~2.5h) |
|------|----------|-----------------|
| **T2** | 🍃 S8: Validation, Auditing, Swagger | Tạo `user/service/UserService` + `UserController` |
| **T3** | 🍃 S9: JPA Relationships | Tạo API đổi password (`change-password`) |
| **T4** | 🍃 S15: JPQL, N+1 | Tạo `cart/entity/CartItem.java` |
| **T5** | 🍃 S17: @Transactional, Propagation | Tạo `CartService` — inject `ProductService` (cross-module) |
| **T6** | ☕ S22 + S24: Enum, Lambda | Tạo `CartController`: add, update, remove, get |
| **T7** | 🍃 S4: DevTools, best practices | Validate stock, tính tổng giỏ hàng |
| **CN** | 🔴 **CHECKPOINT 2** | Review + fix bugs |

### 📦 Module: cart

**Entity: `CartItem` extends `BaseEntity`**
| Field | Type | Annotation |
|-------|------|------------|
| user | User | `@ManyToOne @JoinColumn(name="user_id")` |
| variant | ProductVariant | `@ManyToOne @JoinColumn(name="variant_id")` |
| quantity | Integer | `@NotNull @Min(1)` |

> ⚠️ Cart trỏ vào **ProductVariant** (không phải Product) — vì khách chọn size + màu cụ thể

**API:**
| Method | Endpoint | Mô tả |
|--------|----------|--------|
| GET | `/api/cart` | Giỏ hàng hiện tại |
| POST | `/api/cart/items` | Thêm sản phẩm |
| PUT | `/api/cart/items/{id}` | Cập nhật số lượng |
| DELETE | `/api/cart/items/{id}` | Xóa sản phẩm |
| DELETE | `/api/cart` | Xóa toàn bộ giỏ |

### 🔀 Git Commits — Tuần 4

```bash
# T2
feat(user): add UserService interface and implementation
feat(user): add UserProfileResponse and UpdateProfileRequest DTOs
feat(user): add UserController with GET and PUT profile endpoints

# T3
feat(user): add ChangePasswordRequest DTO
feat(user): implement change-password with old password verification

# T4
feat(cart): add CartItem entity with User and ProductVariant relationships
feat(cart): add CartItemRepository with findByUserId

# T5
feat(cart): add CartService with cross-module ProductService injection
feat(cart): implement add-to-cart with duplicate product handling

# T6
feat(cart): add AddToCartRequest, UpdateCartRequest, CartResponse DTOs
feat(cart): add CartController with CRUD endpoints

# T7
feat(cart): add stock validation when adding item to cart
feat(cart): add cart total price calculation

# CN — Checkpoint 2
fix(cart): fix quantity update when product already in cart
style: clean up all modules and organize imports
docs: update Postman collection with auth, user, cart endpoints
```

### 🔴 CHECKPOINT 2 (Cuối tuần 4)
- [ ] Auth flow hoàn chỉnh (register, login, JWT)
- [ ] ADMIN-only APIs hoạt động
- [ ] User profile: xem/sửa/đổi password
- [ ] Cart CRUD + validate stock
- [ ] Cross-module đúng pattern (qua service interface)
- [ ] Đã xem: 🍃 S4, S8–S9, S15, S17 + 🔐 S1–S8 + ☕ S22, S24

---

## 🟠 TUẦN 5 — Stream API + Order Module

### 📚 Khóa học cần xem

| Khóa | Sections | Nội dung | ~Giờ video |
|------|----------|----------|------------|
| ☕ JAVA | S26 | Stream API: map, filter, collect, reduce, groupingBy | ~4h |
| ☕ JAVA | S27 | Optional | ~1h |
| 🍃 SPRING | S7 | Building Real Features: Contact API, Hibernate schema | ~1h |
| 🍃 SPRING | S16 | Authorization: Roles vs Authorities, Sorting, Pagination | ~1.5h |

> **Tổng: ~7.5h** — Tuần nhẹ về lý thuyết, nặng về project (Order phức tạp nhất)

### 🎯 Mục tiêu project
- Hoàn thành **order module** — cross-module orchestration: Cart + Product

### 📆 Breakdown theo ngày

| Ngày | Học (~1h) | Project (~2.5h) |
|------|----------|-----------------|
| **T2** | ☕ S26 (phần 1): Stream basics | Tạo `order/entity/` — `Order`, `OrderItem`, `OrderStatus` |
| **T3** | ☕ S26 (phần 2): collect, groupingBy | Tạo `OrderRepository`, `OrderItemRepository` |
| **T4** | ☕ S27: Optional | Tạo `order/dto/` + `OrderService` |
| **T5** | 🍃 S16: Roles vs Authorities | Logic `placeOrder`: validate → create → trừ stock → xóa cart |
| **T6** | 🍃 S7: Building features | Tạo `OrderController` (user endpoints) |
| **T7** | Review Stream + Optional | Tạo `AdminOrderController` (admin: getAll, updateStatus) |
| **CN** | Test E2E flow | Register → Login → Browse → Cart → Checkout → View orders |

### 📦 Module: order

**Entity: `Order` extends `BaseEntity`**
| Field | Type |
|-------|------|
| user | User (`@ManyToOne`) |
| orderCode | String — mã đơn hàng (VD: `ORD-20260401-001`) |
| orderItems | List\<OrderItem\> (`@OneToMany(cascade=ALL)`) |
| totalPrice | BigDecimal |
| status | OrderStatus (`PENDING → CONFIRMED → SHIPPING → DELIVERED → CANCELLED`) |
| shippingAddress | String |
| phone | String |
| note | String |

**Entity: `OrderItem` extends `BaseEntity`** — Lưu snapshot thông tin tại thời điểm mua
| Field | Type |
|-------|------|
| order | Order (`@ManyToOne`) |
| variant | ProductVariant (`@ManyToOne`) |
| productName | String — snapshot tên SP |
| size | String — snapshot size |
| color | String — snapshot màu |
| quantity | Integer |
| price | BigDecimal — giá tại thời điểm mua |

**API:**
| Method | Endpoint | Access |
|--------|----------|--------|
| POST | `/api/orders` | User |
| GET | `/api/orders` | User |
| GET | `/api/orders/{id}` | User |
| GET | `/api/admin/orders` | Admin |
| PUT | `/api/admin/orders/{id}/status` | Admin |

### 🔀 Git Commits — Tuần 5

```bash
# T2
feat(order): add OrderStatus enum (PENDING, CONFIRMED, SHIPPING, DELIVERED, CANCELLED)
feat(order): add Order entity with orderCode, status, and shipping info
feat(order): add OrderItem entity with variant snapshot (productName, size, color, price)

# T3
feat(order): add OrderRepository and OrderItemRepository

# T4
feat(order): add PlaceOrderRequest, OrderResponse, OrderItemResponse DTOs
feat(order): add OrderService interface

# T5
feat(order): implement placeOrder with transactional orchestration

# T6
feat(order): add OrderController with place, list, detail endpoints

# T7
feat(order): add AdminOrderController with list-all and update-status
feat(order): add order status transition validation

# CN
test(order): verify E2E flow from cart to order placement
fix(order): fix stock not decreasing after order placement
```

---

## 🟠 TUẦN 6 — Logging, AOP + Polish MVP

### 📚 Khóa học cần xem

| Khóa | Sections | Nội dung | ~Giờ video |
|------|----------|----------|------------|
| 🍃 SPRING | S13 | Logging: Logback, Structured logging | ~1h |
| 🍃 SPRING | S14 | AOP: @Around, @Before, @AfterThrowing | ~1.5h |
| 🍃 SPRING | S18 | Spring Cache: @Cacheable, @CacheEvict, Caffeine | ~1h |
| 🍃 SPRING | S19 | Real Feature Dev: Profile management, ManyToMany | ~1.5h |
| ☕ JAVA | S25 | Method References | ~1h |

> **Tổng: ~6h** — Tuần nhẹ nhất! Tập trung polish toàn bộ MVP

### 🎯 Mục tiêu project
- Swagger annotations hoàn chỉnh
- Data seeder, CORS, Logging
- Fix bugs, clean code → **MVP hoàn thành!**

### 📆 Breakdown theo ngày

| Ngày | Học (~1h) | Project (~2.5h) |
|------|----------|-----------------|
| **T2** | 🍃 S13: Logging | Thêm Swagger annotations tất cả controllers |
| **T3** | 🍃 S14: AOP | Config CORS, thêm `@Slf4j` logging |
| **T4** | 🍃 S18: Caching | Tạo `DataSeeder`: seed categories + products + admin user |
| **T5** | 🍃 S19: Real features | Review code: module boundaries, naming, validation |
| **T6** | ☕ S25: Method References | Tạo Postman collection hoàn chỉnh |
| **T7** | Review tổng quát | Fix bugs, refactor |
| **CN** | 🔴 **CHECKPOINT 3** | Demo MVP hoàn chỉnh |

### 🔀 Git Commits — Tuần 6

```bash
# T2
docs(category): add Swagger @Operation and @ApiResponse annotations
docs(product): add Swagger annotations for all product endpoints
docs(auth): add Swagger annotations for auth endpoints
docs(order): add Swagger annotations for order endpoints

# T3
feat(common): add CorsConfig for cross-origin requests
chore: add @Slf4j logging to all service implementations

# T4
feat(common): add DataSeeder with sample categories and products
feat(common): seed default admin user in DataSeeder

# T5
refactor(common): review and improve error messages across modules
refactor(product): improve validation messages for create and update

# T6
docs: create comprehensive Postman collection with test assertions

# T7
fix(auth): fix token refresh edge case
refactor: clean up unused imports and dead code
style: apply consistent code formatting across all modules
```

### 🔴 CHECKPOINT 3 (Cuối tuần 6) — MVP Complete ✅
- [ ] Full auth (register, login, JWT, roles)
- [ ] Category CRUD (Admin)
- [ ] Product CRUD + search + filter + pagination
- [ ] Cart management
- [ ] Order + admin management
- [ ] User profile
- [ ] Swagger docs + Postman collection + Data seeder
- [ ] Đã xem: ☕ S1–S4, S9–S11, S14, S16–S17, S19–S27 + 🍃 S1–S19 + 🔐 S1–S8

> **🎉 MVP DONE!**

---

## 🔵 TUẦN 7 — Upload Ảnh + Admin Dashboard + Testing

### 📚 Khóa học cần xem

| Khóa | Sections | Nội dung | ~Giờ video |
|------|----------|----------|------------|
| 🍃 SPRING | S20 | Configuration & Profiles: @ConfigurationProperties, DEV/PROD | ~1.5h |
| ☕ JAVA | S12–S13 | Arrays, BufferedReader, Scanner | ~2.5h (xem nhanh) |
| ☕ JAVA | S15 | Object class: hashCode, equals, toString, Record | ~2h |
| ☕ JAVA | S28 | MultiThreading (overview) | ~3h |

> **Tổng: ~9h** — Xem các phần Java còn lại + testing bằng thực hành

### 🎯 Mục tiêu project
- **upload module** — Upload ảnh sản phẩm
- **admin module** — Thống kê cơ bản
- Viết unit tests cho services

### 📆 Breakdown theo ngày

| Ngày | Học (~1h) | Project (~2.5h) |
|------|----------|-----------------|
| **T2** | 🍃 S20: Profiles, Config | Tạo `upload/service/FileUploadService` |
| **T3** | ☕ S15: Object class, Record | Tạo `ProductImage` entity + tích hợp upload ảnh vào product |
| **T4** | ☕ S28 (phần 1): MultiThreading | Tạo `admin/service/StatsService` + Controller |
| **T5** | ☕ S28 (phần 2): synchronized | Unit test: `AuthServiceImpl` (JUnit 5 + Mockito) |
| **T6** | ☕ S12–S13: Arrays, I/O | Unit test: `ProductServiceImpl` |
| **T7** | Review testing patterns | Unit test: `OrderServiceImpl` |
| **CN** | Review test coverage | Fix failing tests |

### 📦 Module: upload + admin

**Entity: `ProductImage` extends `BaseEntity`** — Nhiều ảnh cho mỗi sản phẩm
| Field | Type | Annotation |
|-------|------|------------|
| product | Product | `@ManyToOne @JoinColumn(name="product_id")` |
| imageUrl | String | `@Column(nullable=false)` |
| isPrimary | Boolean | default `false` — ảnh chính hiển thị ở listing |
| sortOrder | Integer | Thứ tự hiển thị |

**upload API:**
| Method | Endpoint | Mô tả |
|--------|----------|--------|
| POST | `/api/upload` | Upload ảnh, trả URL |
| POST | `/api/products/{id}/images` | Upload ảnh cho sản phẩm (ADMIN) |
| DELETE | `/api/products/{id}/images/{imageId}` | Xóa ảnh (ADMIN) |

**admin API:**
| Method | Endpoint | Mô tả |
|--------|----------|--------|
| GET | `/api/admin/stats/summary` | Tổng users, products, orders, revenue |
| GET | `/api/admin/stats/top-products` | Top sản phẩm bán chạy |

### 🔀 Git Commits — Tuần 7

```bash
# T2
feat(upload): add FileUploadService for local file storage
feat(upload): add UploadResponse DTO
feat(upload): add UploadController with POST /api/upload

# T3
feat(product): add ProductImage entity with isPrimary and sortOrder
feat(product): add product image upload and delete endpoints
feat(upload): add file type and size validation

# T4
feat(admin): add StatsService with dashboard summary query
feat(admin): add DashboardSummary and TopProductResponse DTOs
feat(admin): add AdminStatsController with summary and top-products

# T5
test(auth): add unit tests for AuthServiceImpl with Mockito

# T6
test(product): add unit tests for ProductServiceImpl

# T7
test(order): add unit tests for OrderServiceImpl placeOrder logic

# CN
fix(test): fix failing tests and improve assertions
chore: update test configuration for H2 in-memory database
```

---

## 🔵 TUẦN 8 — Docker + Deploy + Tổng Kết

### 📚 Khóa học cần xem

| Khóa | Sections | Nội dung | ~Giờ video |
|------|----------|----------|------------|
| 🍃 SPRING | S6 | Docker: MySQL with Docker, Docker Compose | ~1.2h |
| 🍃 SPRING | S21 | Actuator, Observability, Metrics | ~1.5h |
| 🍃 SPRING | S22 | Consuming REST APIs: RestClient | ~1h |
| 🍃 SPRING | S23 | Deploy to AWS: RDS, Elastic Beanstalk | ~1h |
| 🔐 SECURITY | S9+ | OAuth2, OpenID Connect (xem thêm nếu kịp) | bonus |
| ☕ JAVA | S29–S30 | Java 22–25 new features (bonus) | bonus |

> **Tổng: ~4.7h + bonus** — Tập trung Docker & Deploy

### 🎯 Mục tiêu project
- Dockerize, deploy, viết README

### 📆 Breakdown theo ngày

| Ngày | Học (~1h) | Project (~2.5h) |
|------|----------|-----------------|
| **T2** | 🍃 S6: Docker + MySQL | Viết `Dockerfile` |
| **T3** | 🍃 S6 (tiếp): Docker Compose | Viết `docker-compose.yml` (app + PostgreSQL) |
| **T4** | 🍃 S20 (recap): Profiles | Tạo `application-dev.yml`, `application-prod.yml` |
| **T5** | 🍃 S21: Actuator, Monitoring | Git cleanup, [.gitignore](file:///d:/clothes-retail-api/clothes-retail-api/.gitignore) chuẩn |
| **T6** | 🍃 S23: Deploy AWS/Railway | Deploy app lên Railway/Render |
| **T7** | 🍃 S22: REST Client (bonus) | Viết `README.md` hoàn chỉnh |
| **CN** | 🔴 **CHECKPOINT 4** — FINAL | Demo toàn bộ project |

### 🔀 Git Commits — Tuần 8

```bash
# T2
chore: add Dockerfile for Spring Boot application

# T3
chore: add docker-compose.yml with app and PostgreSQL services
chore: add .dockerignore file

# T4
chore: add application-dev.yml and application-prod.yml profiles
chore: externalize sensitive config with environment variables

# T5
chore: update .gitignore with proper exclusions
refactor: clean up git history and remove unused files

# T6
chore: configure Railway/Render deployment settings
chore: add Procfile for cloud deployment

# T7
docs: add comprehensive README with project overview and setup guide
docs: add architecture diagram to README
docs: add API documentation links to README

# CN — Checkpoint 4: Final
git tag -a v1.0.0 -m "release: clothes retail API v1.0.0"
git push origin main --tags
```

### 🔴 CHECKPOINT 4 (Cuối tuần 8) — Final Review
- [ ] MVP ổn định trên production
- [ ] Upload ảnh hoạt động
- [ ] Admin dashboard
- [ ] Unit tests pass (≥ 60% coverage)
- [ ] Docker chạy local
- [ ] Deployed lên cloud
- [ ] README hoàn chỉnh
- [ ] Đã xem: 🍃 S1–S23 + 🔐 S1–S8 + ☕ S1–S28

---

## 📊 Tổng Kết Video Theo Tuần

| Tuần | ☕ JAVA | 🍃 SPRING | 🔐 SECURITY | Tổng | Ghi chú |
|------|---------|-----------|-------------|------|---------|
| 1 | ~19h | ~2h | — | ~21h | Nặng Java, xem 1.5x nếu đã biết |
| 2 | ~8h | ~8.5h | — | ~16.5h | Cân bằng Java + Spring |
| 3 | — | ~5.3h | ~15.5h | ~20.8h | ⚠️ Nặng nhất — Security |
| 4 | ~4h | ~7h | — | ~11h | Nhẹ, tập trung project |
| 5 | ~5h | ~2.5h | — | ~7.5h | Nhẹ, Order phức tạp |
| 6 | ~1h | ~5h | — | ~6h | Nhẹ nhất — polish |
| 7 | ~7.5h | ~1.5h | — | ~9h | Java còn lại + testing |
| 8 | bonus | ~4.7h | bonus | ~4.7h | Docker + Deploy |
| **Tổng** | **~44.5h** | **~36.5h** | **~15.5h** | **~96.5h** | |

> Trung bình **~12h video/tuần** ≈ **~1.7h/ngày** (tốc độ 1x). Xem 1.25–1.5x → ~1–1.3h/ngày → còn **1–3h/ngày code project**

---

## ⚠️ Quản Lý Rủi Ro

| Rủi ro | Mức độ | Giải pháp |
|--------|--------|-----------|
| Tuần 3 (Security) quá nặng video | Cao | Xem 🍃 S10–S12 trước làm project, 🔐 SECURITY bổ sung tuần 4 |
| Thiếu thời gian (< 2h/ngày) | Cao | Skip phần Java đã biết (xem 2x), ưu tiên 🍃 SPRING |
| Cross-module dependency rối | Trung bình | Luôn inject qua interface |
| Tuần 1 quá nhiều video Java | Trung bình | Xem 1.5x cho S1–S3, tập trung S4 + S10 + S11 |
| Docker/Deploy lỗi | Trung bình | Dùng template, theo hướng dẫn 🍃 S6 + S23 |

---

## 📊 Checklist Tiến Độ

### Giai đoạn 1: Foundation (Tuần 1–2)
- [ ] ☕ Java Core cơ bản hoàn thành (S1–S4, S9–S11, S14, S16–S21)
- [ ] Modular Monolith structure ✅
- [ ] Common module ✅
- [ ] Category CRUD ✅
- [ ] Product CRUD + search + pagination ✅
- [ ] 🔴 Checkpoint 1

### Giai đoạn 2: Core Features (Tuần 3–4)
- [ ] 🔐 Spring Security + JWT hiểu rõ
- [ ] Auth module ✅
- [ ] User module ✅
- [ ] Cart module ✅
- [ ] 🔴 Checkpoint 2

### Giai đoạn 3: Business Logic (Tuần 5–6)
- [ ] ☕ Stream API + Lambda nắm vững
- [ ] Order module + cross-module ✅
- [ ] MVP polished ✅
- [ ] 🔴 Checkpoint 3 — **MVP DONE**

### Giai đoạn 4: Enhancement (Tuần 7–8)
- [ ] Upload + Admin module ✅
- [ ] Unit tests ✅
- [ ] Docker + Deploy ✅
- [ ] 🔴 Checkpoint 4 — **PROJECT COMPLETE**

---

## 💡 Mẹo Học Hiệu Quả

1. **Xem video tốc độ 1.25–1.5x** cho phần đã quen, 1x cho phần mới
2. **Code theo video** — đừng chỉ xem, hãy gõ code theo
3. **Áp ngay vào project** — học JPA Relationships → làm Product entity ngay
4. **Không cần xem hết** — nếu hiểu rồi thì skip, tập trung phần liên quan project
5. **Commit ngay khi xong 1 đơn vị công việc** — đừng để cuối ngày mới commit
6. **Dùng `git log --oneline`** để kiểm tra history có rõ ràng không

---

## 📋 Tổng Hợp Git Commits (~70 commits trên `main`)

| Tuần | Module | ~Commits | Commits chính |
|------|--------|----------|---------------|
| 1 | common + category | ~11 | `init: setup`, `feat(common): BaseEntity`, `feat(category): CRUD` |
| 2 | product | ~10 | `feat(product): entity`, DTOs, search, filter, pagination |
| 3 | auth | ~10 | `feat(auth): User`, JWT, Security, register/login |
| 4 | user + cart | ~12 | `feat(user): profile`, `feat(cart): CartItem`, stock validation |
| 5 | order | ~9 | `feat(order): entities`, placeOrder, admin endpoints |
| 6 | polish | ~8 | `docs:` Swagger, `refactor:` clean code, `feat:` seeder |
| 7 | upload + admin + tests | ~10 | `feat(upload)`, `feat(admin)`, `test:` services |
| 8 | deploy | ~8 | `chore:` Docker, profiles, `docs:` README |
