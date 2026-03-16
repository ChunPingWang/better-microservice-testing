# 微服務開發中，最困難的其實是測試

微服務開發中，我覺得最困難的就是測試。

試想，在這麼多服務與 API 的交互作用下，每增加一個微服務，測試的難度可能是以指數級方式成長。回首過去與現在，很多人還是習慣啟動服務，再用 Swagger 執行測試。系統複雜度不高、微服務與 API 數量不多時，這樣做問題不大。但大規模開發時，這樣做是最佳解嗎？更不要說，加上容器與 K8s 所產生的額外負擔。

如果你是用 Spring Boot 開發微服務，**強烈推薦深入研究 Spring Boot Test**，因為 Spring 開發團隊早就為你準備好測試所需要的一切。

在這裡，我整理了一些微服務測試所需要的案例與方法，提供大家參考。

---

## 目錄

- [1. Swagger 手動測試 vs Spring Boot Test 自動化測試](#1-swagger-手動測試-vs-spring-boot-test-自動化測試)
- [2. 測試金字塔](#2-測試金字塔)
- [3. 各類測試詳解](#3-各類測試詳解)
  - [3.1 Unit Test（單元測試）](#31-unit-test單元測試)
  - [3.2 @WebMvcTest（Controller 切片測試）](#32-webmvctestcontroller-切片測試)
  - [3.3 @DataJpaTest（Repository 切片測試）](#33-datajpatestrepository-切片測試)
  - [3.4 @JsonTest（JSON 序列化測試）](#34-jsontestjson-序列化測試)
  - [3.5 @RestClientTest（REST Client 測試）](#35-restclienttestrest-client-測試)
  - [3.6 @SpringBootTest（整合測試）](#36-springboottestintegration-整合測試)
- [4. Testcontainers 整合](#4-testcontainers-整合)
- [5. 合約測試（Contract Testing）](#5-合約測試contract-testing)
- [6. Spring Boot Test 切片測試完整對照表](#6-spring-boot-test-切片測試完整對照表)
- [7. 如何執行測試](#7-如何執行測試)

---

## 1. Swagger 手動測試 vs Spring Boot Test 自動化測試

相信很多團隊都有這樣的經驗：開發完一個 API，第一件事就是打開 **Swagger UI**，手動輸入參數、按下 Execute，看回應是不是正確的。開發初期，這樣做直覺又快速，沒什麼問題。

但隨著微服務數量增長，你會發現：**每次改一個 API，就得手動重測一輪相關的功能**。更麻煩的是，Swagger 測試必須先啟動整個應用程式，光是等待啟動就要花上好幾十秒。

Spring Boot Test 提供了一個完全不同的思路。

### 核心差異：需不需要啟動整包程式？

| 比較項目 | Swagger（手動測試） | Spring Boot Test（自動化測試） |
|---------|-------------------|------------------------------|
| **啟動需求** | 必須啟動**整個應用程式**（Tomcat + 所有 Bean） | 切片測試（如 `@WebMvcTest`）**不需啟動整包程式**，只載入必要的 Bean |
| **測試速度** | 慢（完整啟動 10-30 秒 + 人工操作） | 快（切片測試 1-3 秒，Unit Test < 1 秒） |
| **可重複性** | 低 — 每次人工操作可能不一致 | 高 — 自動化，每次結果一致 |
| **CI/CD 整合** | 無法整合 | 完美整合，每次提交自動驗證 |
| **覆蓋範圍** | 僅能測試 HTTP API 層 | 可測試所有層級（Service、Repository、JSON、Client 等） |
| **回歸測試** | 不可能 — 依賴人工重複測試 | 自動化 — 每次 commit 自動執行 |
| **測試外部 API** | 需要外部服務可用 | `@RestClientTest` + MockServer 模擬，不需真實外部服務 |
| **測試資料管理** | 手動準備、難以復原 | 自動建立/銷毀，每次測試獨立 |

### 為什麼 Spring Boot Test 可以不啟動整包程式？

這是我覺得 Spring Boot Test 最厲害的地方 — **切片測試（Slice Test）**。它的關鍵在於 Application Context 的選擇性載入：

```
Swagger 測試流程：
  啟動整個 Spring Boot App → 啟動 Tomcat → 載入所有 Bean → 人工操作 Swagger UI → 肉眼驗證

@WebMvcTest 測試流程：
  只載入 Controller + Filter + ControllerAdvice → 使用 MockMvc 模擬 HTTP → 自動驗證
  ❌ 不啟動 Tomcat
  ❌ 不載入 Service、Repository、DataSource
```

這就是為什麼 `@WebMvcTest` 只需 1-3 秒，而啟動整個應用需要 10-30 秒以上。

### 小結

> **Swagger 適合開發階段的快速 API 探索和文件產出**，但它不能取代自動化測試。
> **Spring Boot Test 才是保障程式品質和持續交付的正確方式。**
>
> 最佳實踐：兩者搭配使用 — Swagger 做 API 文件 + 互動式探索，Spring Boot Test 做自動化品質保障。

---

## 2. 測試金字塔

在設計測試策略時，測試金字塔是一個很好的參考框架。底層的測試寫得越多、跑得越快；頂層的測試只挑關鍵流程來寫。

```
              ╱╲
             ╱  ╲
            ╱ E2E╲          ← @SpringBootTest (少量)
           ╱──────╲           端到端整合測試
          ╱        ╲
         ╱  Slice   ╲       ← @WebMvcTest, @DataJpaTest, @JsonTest (適量)
        ╱   Tests    ╲        切片測試，針對特定層
       ╱──────────────╲
      ╱                ╲
     ╱   Unit Tests     ╲   ← Mockito + JUnit 5 (大量)
    ╱                    ╲     速度最快，數量最多
   ╱──────────────────────╲
```

| 層級 | 測試方式 | 數量 | 速度 | 範圍 |
|------|---------|------|------|------|
| **Unit Test** | Mockito + JUnit 5 | 最多 | < 1 秒 | 單一類別 |
| **Slice Test** | @WebMvcTest 等 | 適量 | 1-3 秒 | 特定層 |
| **Integration Test** | @SpringBootTest | 最少 | 5-15 秒 | 完整應用 |

---

## 3. 各類測試詳解

接下來，我用一個簡單的 User CRUD 微服務，示範每一種測試怎麼寫。所有範例都可以在這個 repo 中直接執行。

### 3.1 Unit Test（單元測試）

這是最基本、也是你應該寫最多的測試。不啟動 Spring Context，速度最快，專注驗證單一類別的業務邏輯。

**範例檔案**：[`src/test/java/.../unit/UserServiceTest.java`](src/test/java/com/example/testing/unit/UserServiceTest.java)

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void findById_whenExists_shouldReturnUser() {
        User user = new User("Alice", "alice@example.com");
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.findById(1L);

        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    void findById_whenNotExists_shouldThrowException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
```

**適用場景**：Service 層邏輯、工具類、轉換器、計算邏輯

---

### 3.2 @WebMvcTest（Controller 切片測試）

當你只想測 Controller 的路由、HTTP 狀態碼、請求驗證，而不想等整個應用啟動時，`@WebMvcTest` 就是你的好朋友。它只載入 Web 層的 Bean，**不啟動 Tomcat**。

**範例檔案**：[`src/test/java/.../controller/UserControllerTest.java`](src/test/java/com/example/testing/controller/UserControllerTest.java)

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void createUser_withValidData_shouldReturn201() throws Exception {
        User created = new User("Alice", "alice@example.com");
        created.setId(1L);
        when(userService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"email\":\"alice@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Alice")));
    }

    @Test
    void createUser_withInvalidData_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

**適用場景**：REST API 路由測試、HTTP Status 驗證、Request Validation、Exception Handler 測試

---

### 3.3 @DataJpaTest（Repository 切片測試）

想驗證你的 Repository Query Method 是否正確？`@DataJpaTest` 只載入 JPA 相關的 Bean，搭配 H2 記憶體資料庫，每個測試結束後自動 Rollback，乾淨又快速。

**範例檔案**：[`src/test/java/.../repository/UserRepositoryTest.java`](src/test/java/com/example/testing/repository/UserRepositoryTest.java)

```java
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_whenExists_shouldReturnUser() {
        entityManager.persistAndFlush(new User("Alice", "alice@example.com"));

        Optional<User> found = userRepository.findByEmail("alice@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Alice");
    }
}
```

**適用場景**：Repository Method 驗證、Custom Query（@Query）測試、Entity Mapping 驗證

---

### 3.4 @JsonTest（JSON 序列化測試）

在微服務之間，JSON 是最常見的資料交換格式。如果序列化/反序列化出了問題，debug 起來往往很痛苦。`@JsonTest` 讓你可以單獨驗證 DTO 的 JSON 行為，不需要啟動 Web 層或資料庫。

**範例檔案**：[`src/test/java/.../json/UserDtoJsonTest.java`](src/test/java/com/example/testing/json/UserDtoJsonTest.java)

```java
@JsonTest
class UserDtoJsonTest {

    @Autowired
    private JacksonTester<UserDto> json;

    @Test
    void serialize_shouldProduceCorrectJson() throws Exception {
        UserDto dto = new UserDto("Alice", "alice@example.com");

        assertThat(json.write(dto))
                .hasJsonPathStringValue("$.name")
                .extractingJsonPathStringValue("$.name").isEqualTo("Alice");
    }

    @Test
    void deserialize_shouldProduceCorrectObject() throws Exception {
        String content = "{\"name\":\"Bob\",\"email\":\"bob@example.com\"}";

        UserDto result = json.parseObject(content);

        assertThat(result.getName()).isEqualTo("Bob");
    }
}
```

**適用場景**：API 回應格式驗證、複雜 JSON 結構測試、日期格式轉換

---

### 3.5 @RestClientTest（REST Client 測試）

微服務之間經常需要呼叫其他服務的 API。但測試時，你不可能要求所有外部服務都跑起來。`@RestClientTest` 搭配 `MockRestServiceServer`，讓你模擬外部 API 的回應，完全不需要真實的外部服務。

**範例檔案**：[`src/test/java/.../client/ExternalApiClientTest.java`](src/test/java/com/example/testing/client/ExternalApiClientTest.java)

```java
@RestClientTest(ExternalApiClient.class)
class ExternalApiClientTest {

    @Autowired
    private ExternalApiClient externalApiClient;

    @Autowired
    private MockRestServiceServer mockServer;

    @Test
    void fetchUser_shouldParseResponse() {
        mockServer.expect(requestTo("https://api.example.com/users/123"))
                .andRespond(withSuccess(
                    "{\"name\":\"External User\",\"email\":\"ext@example.com\"}",
                    MediaType.APPLICATION_JSON));

        UserDto result = externalApiClient.fetchUserFromExternalApi("123");

        assertThat(result.getName()).isEqualTo("External User");
        mockServer.verify();
    }
}
```

**適用場景**：第三方 API 呼叫測試、HTTP 錯誤處理、回應解析

---

### 3.6 @SpringBootTest（Integration 整合測試）

當你需要端到端驗證完整的 Controller → Service → Repository 流程時，就用 `@SpringBootTest`。它會啟動完整的 Spring Context + 內嵌 Tomcat，最接近生產環境，但也是速度最慢的。所以建議只針對關鍵業務流程來寫。

**範例檔案**：[`src/test/java/.../integration/UserIntegrationTest.java`](src/test/java/com/example/testing/integration/UserIntegrationTest.java)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void fullCrudFlow() {
        // Create
        UserDto dto = new UserDto("Alice", "alice@example.com");
        ResponseEntity<User> response = restTemplate.postForEntity("/api/users", dto, User.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Read
        Long id = response.getBody().getId();
        ResponseEntity<User> getResponse = restTemplate.getForEntity("/api/users/" + id, User.class);
        assertThat(getResponse.getBody().getName()).isEqualTo("Alice");
    }
}
```

**適用場景**：關鍵業務流程的端到端驗證、跨層互動測試

---

## 4. Testcontainers 整合

### 你踩過這個坑嗎？

用 H2 記憶體資料庫跑測試，全部綠燈通過。部署到生產環境連上 PostgreSQL，結果直接炸掉。

這不是你的問題，是 H2 跟真實資料庫本來就有差異：

| 問題 | 說明 |
|------|------|
| **SQL 方言差異** | H2 支援的 SQL 語法與 PostgreSQL/MySQL 不完全相同 |
| **特定功能不支援** | JSON 型別、陣列型別、全文搜尋等功能在 H2 上行為不同 |
| **False Positive** | 測試在 H2 上通過，但在生產 DB 上失敗 |

**Testcontainers** 解決了這個問題 — 它透過 Docker 啟動真實的資料庫容器，讓測試環境與生產環境使用相同的資料庫。

### 運作原理

```
測試啟動 → Testcontainers 啟動 Docker 容器（PostgreSQL）
         → @DynamicPropertySource 動態注入 JDBC URL
         → Spring 連線到容器中的 PostgreSQL
         → 執行測試
         → 測試結束 → 容器自動銷毀
```

### 設定方式

**1. Maven 依賴**（已包含在本專案 `pom.xml` 中）：

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

**2. 測試類別範例**：

**範例檔案**：[`src/test/java/.../testcontainers/UserPostgresIntegrationTest.java`](src/test/java/com/example/testing/testcontainers/UserPostgresIntegrationTest.java)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserPostgresIntegrationTest {

    // 宣告 PostgreSQL 容器
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    // 動態注入容器的連線資訊到 Spring
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Test
    void createAndRetrieveUser_withRealPostgres() {
        // 這裡的操作全部跑在真實的 PostgreSQL 上！
        UserDto dto = new UserDto("Alice", "alice@example.com");
        ResponseEntity<User> response = restTemplate.postForEntity("/api/users", dto, User.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
```

### Testcontainers 支援的服務

除了 PostgreSQL，Testcontainers 還支援很多常見的基礎設施：

| 服務 | Artifact | 用途 |
|------|----------|------|
| MySQL | `testcontainers:mysql` | MySQL 資料庫測試 |
| MongoDB | `testcontainers:mongodb` | MongoDB 測試 |
| Redis | `testcontainers:redis` (via GenericContainer) | 快取測試 |
| Kafka | `testcontainers:kafka` | 訊息佇列測試 |
| Elasticsearch | `testcontainers:elasticsearch` | 搜尋引擎測試 |
| LocalStack | `testcontainers:localstack` | AWS 服務模擬 |

### 使用建議

- **Slice Test（如 @DataJpaTest）**：使用 H2 即可，速度快
- **Integration Test（如 @SpringBootTest）**：搭配 Testcontainers 使用真實 DB
- **CI/CD 環境**：確保 CI 環境有 Docker（大部分 CI 服務預設支援）

---

## 5. 合約測試（Contract Testing）

微服務架構中，還有一個常被忽略但極其重要的測試面向：**服務之間的 API 契約**。

當 Service B 悄悄改了回應格式，Service A 完全不知道，直到上線那一刻才爆炸 — 相信不少人都踩過這個坑。合約測試就是為了解決這個問題。

### 三者的關係：Design by Contract、Contract Test、Spring Cloud Contract

這三個概念容易混淆，但它們處於不同的層次：

```
Design by Contract (DbC)
  │  軟體設計原則（1986, Bertrand Meyer）
  │  定義前置條件、後置條件、不變量
  │
  ├─ 啟發
  │
  ▼
Contract Test（合約測試）
  │  一種測試策略
  │  驗證服務間 API 契約的相容性
  │
  ├─ 實作工具
  │
  ▼
Spring Cloud Contract
     具體的測試框架
     提供 DSL 定義契約、自動產生測試與 Stub
```

### 5.1 Design by Contract（契約式設計）

這是一種**軟體設計原則**，源自 Bertrand Meyer 在 1986 年設計的 Eiffel 程式語言。核心概念是為每個方法定義明確的「契約」：

- **前置條件（Precondition）**：呼叫方法前必須滿足的條件
- **後置條件（Postcondition）**：方法執行完成後保證的結果
- **不變量（Invariant）**：物件在其生命週期內始終為真的條件

```java
// Design by Contract 的思維方式（概念示範）
public class UserService {

    /**
     * Contract:
     * - Precondition: userDto != null, userDto.email 格式正確
     * - Postcondition: 回傳的 User.id != null
     * - Invariant: repository 中不存在重複 email
     */
    public User create(UserDto userDto) {
        // 前置條件檢查
        Objects.requireNonNull(userDto, "UserDto must not be null");

        User user = new User(userDto.getName(), userDto.getEmail());
        User saved = userRepository.save(user);

        // 後置條件保證
        assert saved.getId() != null : "Saved user must have an ID";
        return saved;
    }
}
```

**重點**：DbC 是一種**設計思想**，不是測試框架。它影響了 Contract Test 的概念。

### 5.2 Contract Test（合約測試）

這是一種**測試策略**，專門用來驗證微服務之間的 API 契約是否相容。

**問題場景**：

```
Service A (Consumer)  ←── HTTP ──→  Service B (Producer)
     │                                    │
     │  Service B 更改了 API 回應格式       │
     │  Service A 不知道，直到上線才爆炸！   │
     └──────────── 💥 ──────────────────┘
```

**Contract Test 的解法**：

```
1. Producer 定義 API 契約（請求格式 + 回應格式）
2. 自動產生 Producer 端的驗證測試 → 確保 Producer 遵守契約
3. 自動產生 Stub → Consumer 用 Stub 測試，確保 Consumer 正確解析回應
4. Producer 改 API 時，契約測試先失敗 → 提前發現破壞性變更
```

### 5.3 Spring Cloud Contract

這是 Contract Test 策略的**具體實作框架**，也是 Spring 生態系中的官方解決方案。

#### 工作流程

```
                    ┌─────────────────────────┐
                    │   Contract DSL 定義檔     │
                    │  (Groovy / YAML)         │
                    └───────────┬─────────────┘
                                │
                  ┌─────────────┼─────────────┐
                  ▼                            ▼
        ┌─────────────────┐         ┌──────────────────┐
        │ Producer 端       │         │ Consumer 端       │
        │                  │         │                   │
        │ 自動產生驗證測試   │         │ 自動產生 Stub JAR   │
        │ 確保 API 符合契約  │         │ Consumer 用 Stub   │
        │                  │         │ 測試自己的呼叫邏輯   │
        └─────────────────┘         └──────────────────┘
```

#### 契約定義範例（Groovy DSL）

```groovy
// contracts/shouldReturnUser.groovy
Contract.make {
    description "should return user by ID"

    request {
        method GET()
        url "/api/users/1"
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            id   : 1,
            name : "Alice",
            email: "alice@example.com"
        ])
    }
}
```

#### 契約定義範例（YAML）

```yaml
# contracts/shouldReturnUser.yml
description: should return user by ID
request:
  method: GET
  url: /api/users/1
response:
  status: 200
  headers:
    Content-Type: application/json
  body:
    id: 1
    name: "Alice"
    email: "alice@example.com"
```

#### Producer 端設定

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-contract-verifier</artifactId>
    <scope>test</scope>
</dependency>

<plugin>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-contract-maven-plugin</artifactId>
    <extensions>true</extensions>
    <configuration>
        <testFramework>JUNIT5</testFramework>
        <baseClassForTests>com.example.testing.BaseContractTest</baseClassForTests>
    </configuration>
</plugin>
```

```java
// BaseContractTest.java — Producer 端的基底測試類別
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public abstract class BaseContractTest {

    @Autowired
    private UserController userController;

    @MockBean
    private UserService userService;

    @BeforeEach
    void setup() {
        RestAssuredMockMvc.standaloneSetup(userController);

        // 設定 mock 行為以符合契約
        User user = new User("Alice", "alice@example.com");
        user.setId(1L);
        when(userService.findById(1L)).thenReturn(user);
    }
}
```

執行 `mvn test` 後，Spring Cloud Contract 會自動根據契約檔案產生測試類別，驗證 Producer 的 API 是否符合契約。

#### Consumer 端設定

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-contract-stub-runner</artifactId>
    <scope>test</scope>
</dependency>
```

```java
// Consumer 端測試
@SpringBootTest
@AutoConfigureStubRunner(
    ids = "com.example:user-service:+:stubs:8080",
    stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
class UserClientContractTest {

    @Autowired
    private ExternalApiClient client;

    @Test
    void shouldGetUserFromProducer() {
        // 這裡呼叫的是自動產生的 Stub，不是真實的 Producer
        UserDto user = client.fetchUserFromExternalApi("1");

        assertThat(user.getName()).isEqualTo("Alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
    }
}
```

### 5.4 什麼時候該導入合約測試？

| 情境 | 建議 |
|------|------|
| 單體架構 | 不需要 Contract Test，Unit + Integration Test 即可 |
| 2-3 個微服務 | 可選擇性使用，先從關鍵 API 開始 |
| 多個微服務（5+） | 強烈建議使用 Spring Cloud Contract |
| 跨團隊開發 | 必須使用，避免介面不相容的問題 |

**導入步驟建議**：
1. 先從最核心的 API 開始定義契約
2. Producer 端先加入 Contract Verifier
3. 產生 Stub JAR 後，Consumer 端加入 Stub Runner
4. 將契約檔案納入版本控制，作為 API 規格的 Single Source of Truth

---

## 6. Spring Boot Test 切片測試完整對照表

最後，附上一張完整的切片測試對照表，方便大家快速查閱：

| 註解 | 測試對象 | 載入的 Bean | 典型用途 |
|------|---------|------------|---------|
| `@WebMvcTest` | Controller | Controller, Filter, ControllerAdvice | REST API 路由與驗證 |
| `@DataJpaTest` | Repository | Entity, Repository, EntityManager | JPA Query 測試 |
| `@DataMongoTest` | MongoDB Repository | MongoTemplate, MongoRepository | MongoDB 操作測試 |
| `@DataRedisTest` | Redis Repository | RedisTemplate | Redis 操作測試 |
| `@JdbcTest` | JDBC | JdbcTemplate, DataSource | 原生 SQL 測試 |
| `@JsonTest` | JSON 序列化 | ObjectMapper, JacksonTester | JSON 格式驗證 |
| `@RestClientTest` | REST Client | RestTemplate, MockRestServiceServer | 外部 API 呼叫 |
| `@WebFluxTest` | WebFlux Controller | WebTestClient | Reactive 端點測試 |

---

## 7. 如何執行測試

```bash
# 執行所有測試（不含 Testcontainers）
mvn test

# 執行所有測試（含 Testcontainers，需要 Docker）
mvn test -P testcontainers

# 執行特定測試類別
mvn test -Dtest=UserServiceTest

# 執行特定 package 下的所有測試
mvn test -Dtest="com.example.testing.unit.*"

# 執行測試並產生報告
mvn test surefire-report:report
```

### 專案結構總覽

```
src/test/java/com/example/testing/
├── unit/
│   └── UserServiceTest.java           ← Unit Test (Mockito)
├── controller/
│   └── UserControllerTest.java        ← @WebMvcTest (切片測試)
├── repository/
│   └── UserRepositoryTest.java        ← @DataJpaTest (切片測試)
├── integration/
│   └── UserIntegrationTest.java       ← @SpringBootTest (整合測試)
├── json/
│   └── UserDtoJsonTest.java           ← @JsonTest (切片測試)
├── client/
│   └── ExternalApiClientTest.java     ← @RestClientTest (切片測試)
└── testcontainers/
    └── UserPostgresIntegrationTest.java ← Testcontainers (Docker 整合測試)
```

---

如果你也在做微服務測試，歡迎留言分享你的經驗和做法。有任何問題也歡迎討論！
