# 微服務開發中，最困難的其實是測試

微服務開發中，我覺得最困難的就是測試。

試想，在這麼多服務與 API 的交互作用下，每增加一個微服務，測試的難度可能是以指數級方式成長。回首過去與現在，很多人還是習慣啟動服務，再用 Swagger 執行測試。系統複雜度不高、微服務與 API 數量不多時，這樣做問題不大。但大規模開發時，這樣做是最佳解嗎？更不要說，加上容器與 K8s 所產生的額外負擔。

如果你是用 Spring Boot 開發微服務，**強烈推薦深入研究 Spring Boot Test**，因為 Spring 開發團隊早就為你準備好測試所需要的一切。

在這裡，我整理了一些微服務測試所需要的案例與方法，提供大家參考。

---

## 目錄

- [1. 軟體測試基礎觀念](#1-軟體測試基礎觀念)
  - [1.1 為什麼要寫測試？](#11-為什麼要寫測試)
  - [1.2 Test Double（測試替身）](#12-test-double測試替身)
  - [1.3 測試分層與測試金字塔](#13-測試分層與測試金字塔)
  - [1.4 TDD（測試驅動開發）](#14-tdd測試驅動開發)
- [2. Swagger 手動測試 vs Spring Boot Test 自動化測試](#2-swagger-手動測試-vs-spring-boot-test-自動化測試)
- [3. Spring Boot Test 切片測試總覽](#3-spring-boot-test-切片測試總覽)
- [4. 各類測試詳解與範例](#4-各類測試詳解與範例)
  - [4.1 Unit Test（單元測試）](#41-unit-test單元測試)
  - [4.2 @WebMvcTest（Controller 切片測試）](#42-webmvctestcontroller-切片測試)
  - [4.3 @DataJpaTest（Repository 切片測試）](#43-datajpatestrepository-切片測試)
  - [4.4 @JsonTest（JSON 序列化測試）](#44-jsontestjson-序列化測試)
  - [4.5 @RestClientTest（REST Client 測試）](#45-restclienttestrest-client-測試)
  - [4.6 @SpringBootTest（整合測試）](#46-springboottestintegration-整合測試)
- [5. Testcontainers 整合](#5-testcontainers-整合)
- [6. 合約測試（Contract Testing）](#6-合約測試contract-testing)
- [7. 如何執行測試](#7-如何執行測試)

---

## 1. 軟體測試基礎觀念

在進入 Spring Boot Test 的具體用法之前，我想先聊一些測試的基礎觀念。這些觀念不限於 Spring Boot，而是適用於所有軟體開發的通用知識。理解了這些，後面在看各種測試註解時，會更清楚「為什麼要這樣設計」。

### 1.1 為什麼要寫測試？

很多人覺得寫測試是「額外的工作」，但事實上，**不寫測試才是在給未來的自己埋雷**。

- **信心**：改了一行程式碼，跑一次測試就知道有沒有弄壞其他東西
- **文件**：好的測試本身就是最好的文件，告訴你這個方法「預期」的行為是什麼
- **設計**：難以測試的程式碼，通常也是設計不好的程式碼。寫測試會迫使你寫出更鬆耦合的設計
- **速度**：自動化測試跑一秒，手動測試可能要花好幾分鐘。在微服務架構下，這個差距會被放大幾十倍

### 1.2 Test Double（測試替身）

在測試中，我們經常需要「替換」真實的依賴。這些替代品統稱為 **Test Double**，概念來自 Gerard Meszaros 的《xUnit Test Patterns》。就像電影中的替身演員，Test Double 在測試中「替代」真實的物件。

```
                    Test Double（測試替身）
                            │
          ┌─────────┬───────┼───────┬──────────┐
          ▼         ▼       ▼       ▼          ▼
       Dummy      Stub    Spy    Mock        Fake
       佔位用    回傳固定值  記錄呼叫  驗證互動   簡化實作
```

| 類型 | 用途 | 說明 | 範例 |
|------|------|------|------|
| **Dummy** | 佔位 | 只是填滿參數列表，不會真的被使用 | 傳入一個不會被呼叫的 Logger |
| **Stub** | 提供固定回應 | 對特定呼叫回傳預設值，不驗證行為 | `when(repo.findById(1L)).thenReturn(user)` |
| **Spy** | 記錄互動 | 包裝真實物件，記錄呼叫情況，可選擇性覆寫方法 | `@Spy` + `verify()` |
| **Mock** | 驗證互動 | 完全模擬的物件，驗證方法是否被正確呼叫 | `@Mock` + `verify(repo).save(any())` |
| **Fake** | 簡化實作 | 有實際行為但走捷徑的實作 | H2 記憶體資料庫取代 PostgreSQL |

#### 在 Mockito 中的對應

```java
// Dummy — 只是佔位，測試中不會真正使用它
UserRepository dummyRepo = mock(UserRepository.class);
new SomeOtherService(dummyRepo);  // 只是需要一個參數填入

// Stub — 設定固定回傳值
@Mock UserRepository stubRepo;
when(stubRepo.findById(1L)).thenReturn(Optional.of(user));  // 固定回傳 user
when(stubRepo.existsByEmail("test@example.com")).thenReturn(true);

// Mock — 驗證互動行為
@Mock UserRepository mockRepo;
userService.delete(1L);
verify(mockRepo).delete(any(User.class));         // 驗證 delete 被呼叫了
verify(mockRepo, never()).save(any());             // 驗證 save 沒有被呼叫
verify(mockRepo, times(1)).findById(1L);           // 驗證 findById 被呼叫了 1 次

// Spy — 包裝真實物件，部分覆寫
@Spy UserService spyService = new UserService(realRepo);
doReturn(user).when(spyService).findById(1L);      // 覆寫 findById，其他方法走真實邏輯

// Fake — 簡化的真實實作
// H2 記憶體資料庫就是一個 Fake，它是真的資料庫，但比 PostgreSQL 輕量
// @DataJpaTest 預設就是用 H2 作為 Fake Database
```

#### Stub vs Mock：什麼時候用哪個？

這是最常被問到的問題。簡單來說：

- **Stub**：你關心的是「回傳值」— 給定輸入 X，應該回傳 Y
- **Mock**：你關心的是「互動」— 某個方法應該被呼叫（或不該被呼叫）

```java
// Stub 用法：我只關心 findById 回傳什麼
when(userRepository.findById(1L)).thenReturn(Optional.of(user));
User result = userService.findById(1L);
assertThat(result.getName()).isEqualTo("Alice");  // 驗證回傳值

// Mock 用法：我關心 delete 是否真的被執行了
userService.delete(1L);
verify(userRepository).delete(user);  // 驗證互動
```

> **實務建議**：在 Mockito 中，`@Mock` 註解同時支援 Stub 和 Mock 行為。大多數情況下你會混合使用 — 先 Stub（`when...thenReturn`），再 Mock（`verify`）。不需要刻意區分，但理解概念有助於你寫出更好的測試。

### 1.3 測試分層與測試金字塔

測試金字塔是 Mike Cohn 在 2009 年提出的概念。核心思想很簡單：**底層的測試寫得越多、跑得越快；頂層的測試只挑關鍵流程來寫。**

```
              ╱╲
             ╱  ╲
            ╱ E2E╲          ← 端到端測試 (少量，速度慢)
           ╱──────╲           驗證完整業務流程
          ╱        ╲
         ╱  Slice   ╲       ← 切片測試 / 整合測試 (適量)
        ╱   Tests    ╲        驗證元件之間的協作
       ╱──────────────╲
      ╱                ╲
     ╱   Unit Tests     ╲   ← 單元測試 (大量，速度快)
    ╱                    ╲     驗證單一類別的邏輯
   ╱──────────────────────╲
```

| 層級 | 對應 Spring Boot | 數量 | 速度 | 測試範圍 |
|------|-----------------|------|------|---------|
| **Unit Test** | Mockito + JUnit 5 | 最多 | < 1 秒 | 單一類別的業務邏輯 |
| **Slice Test** | @WebMvcTest, @DataJpaTest, @JsonTest 等 | 適量 | 1-3 秒 | 特定技術層 |
| **Integration Test** | @SpringBootTest | 少量 | 5-15 秒 | 完整應用流程 |
| **E2E Test** | @SpringBootTest + Testcontainers | 最少 | 10-30 秒 | 含真實基礎設施 |

**為什麼是金字塔形？**

越往上的測試，環境越複雜、速度越慢、除錯也越困難。如果大部分邏輯已經被底層的 Unit Test 驗證過了，上層的整合測試只需要驗證「各層串起來有沒有問題」即可。

### 1.4 TDD（測試驅動開發）

TDD（Test-Driven Development）是由 Kent Beck 在 2003 年正式提出的開發方法論。核心流程是著名的 **Red-Green-Refactor** 三步循環：

```
    ┌───────────────────────────────────────┐
    │                                       │
    ▼                                       │
  🔴 Red                                    │
  寫一個失敗的測試                            │
    │                                       │
    ▼                                       │
  🟢 Green                                  │
  用最簡單的方式讓測試通過                     │
    │                                       │
    ▼                                       │
  🔵 Refactor                               │
  重構程式碼，保持測試通過                     │
    │                                       │
    └───────────────────────────────────────┘
```

#### TDD 實戰範例：開發 UserService.create()

讓我們用 TDD 的方式來開發一個「建立使用者」的功能。

**Step 1 — Red：先寫失敗的測試**

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void create_shouldSaveAndReturnUserWithId() {
        // Arrange
        UserDto dto = new UserDto("Alice", "alice@example.com");
        User savedUser = new User("Alice", "alice@example.com");
        savedUser.setId(1L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        User result = userService.create(dto);

        // Assert
        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("Alice");
        verify(userRepository).save(any(User.class));
    }
}
```

此時 `UserService.create()` 還不存在，編譯都不會過 — 這就是 Red。

**Step 2 — Green：寫最少的程式碼讓測試通過**

```java
public User create(UserDto userDto) {
    User user = new User(userDto.getName(), userDto.getEmail());
    return userRepository.save(user);
}
```

跑測試 — 通過了，這就是 Green。

**Step 3 — Refactor：重構（如果需要）**

目前程式碼已經很簡潔，不需要重構。但如果後來需要加上「email 不能重複」的業務規則，就再從 Red 開始：

```java
@Test
void create_whenEmailAlreadyExists_shouldThrowException() {
    UserDto dto = new UserDto("Alice", "alice@example.com");
    when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

    assertThatThrownBy(() -> userService.create(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
}
```

#### TDD 的取捨

TDD 不是銀彈。我的建議是：

| 場景 | 是否適合 TDD |
|------|------------|
| 業務邏輯複雜、規則多 | 非常適合 — TDD 幫你釐清需求 |
| CRUD 操作 | 不一定 — 邏輯太單純，TDD 反而拖慢速度 |
| 探索性開發（不確定怎麼做） | 不適合 — 先寫 Spike 探索，穩定後再補測試 |
| Bug 修復 | 非常適合 — 先寫測試重現 Bug，再修復 |

> **務實的做法**：不需要 100% TDD。對於核心業務邏輯用 TDD，對於簡單的 CRUD 和膠水代碼（glue code），寫完再補測試也完全可以。重要的是**有測試**，而不是**什麼時候寫測試**。

---

## 2. Swagger 手動測試 vs Spring Boot Test 自動化測試

理解了測試的基礎觀念後，我們來看一個實際場景。

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

## 3. Spring Boot Test 切片測試總覽

在深入各類測試的程式碼之前，先看一張全景圖。Spring Boot 針對不同的技術層，提供了對應的切片測試註解。每個註解只載入該層需要的 Bean，避免啟動整個應用。

| 註解 | 測試對象 | 載入的 Bean | 典型用途 |
|------|---------|------------|---------|
| `@WebMvcTest` | Controller | Controller, Filter, ControllerAdvice | REST API 路由與驗證 |
| `@DataJpaTest` | Repository | Entity, Repository, EntityManager | JPA Query 測試 |
| `@DataMongoTest` | MongoDB Repository | MongoTemplate, MongoRepository | MongoDB 操作測試 |
| `@DataRedisTest` | Redis Repository | RedisTemplate | Redis 操作測試 |
| `@JdbcTest` | JDBC | JdbcTemplate, DataSource | 原生 SQL 測試 |
| `@JsonTest` | JSON 序列化 | ObjectMapper, JacksonTester | JSON 格式驗證 |
| `@RestClientTest` | REST Client | RestTemplate/RestClient, MockRestServiceServer | 外部 API 呼叫 |
| `@WebFluxTest` | WebFlux Controller | WebTestClient | Reactive 端點測試 |

看到這張表，你就能理解為什麼切片測試這麼快了 — 它只載入你需要的那一塊。想測 Controller？用 `@WebMvcTest`，不會載入 Repository 和 Database。想測 Repository？用 `@DataJpaTest`，不會載入 Controller 和 Service。

下面我們就來看每一種測試怎麼寫。

---

## 4. 各類測試詳解與範例

接下來，我用一個簡單的 User CRUD 微服務，示範每一種測試怎麼寫。所有範例都可以在這個 repo 中直接執行。

### 4.1 Unit Test（單元測試）

這是最基本、也是你應該寫最多的測試。不啟動 Spring Context，速度最快，專注驗證單一類別的業務邏輯。

**特點**：
- 使用 `@ExtendWith(MockitoExtension.class)` — 不需要 Spring
- 使用 `@Mock` 建立測試替身，`@InjectMocks` 自動注入
- 只測邏輯，不測框架

**範例檔案**：[`src/test/java/.../unit/UserServiceTest.java`](src/test/java/com/example/testing/unit/UserServiceTest.java)

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    // ---- Stub 用法：驗證回傳值 ----

    @Test
    @DisplayName("findAll - 應回傳所有使用者")
    void findAll_shouldReturnAllUsers() {
        // Arrange — 設定 Stub 行為
        List<User> users = Arrays.asList(
                new User("Alice", "alice@example.com"),
                new User("Bob", "bob@example.com")
        );
        when(userRepository.findAll()).thenReturn(users);

        // Act
        List<User> result = userService.findAll();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Alice");
        verify(userRepository).findAll();
    }

    @Test
    @DisplayName("findById - 存在時應回傳使用者")
    void findById_whenExists_shouldReturnUser() {
        User user = new User("Alice", "alice@example.com");
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.findById(1L);

        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("findById - 不存在時應拋出 UserNotFoundException")
    void findById_whenNotExists_shouldThrowException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ---- Mock 用法：驗證互動 ----

    @Test
    @DisplayName("create - 應成功建立使用者並呼叫 repository.save()")
    void create_shouldSaveAndReturnUser() {
        UserDto dto = new UserDto("Alice", "alice@example.com");
        User savedUser = new User("Alice", "alice@example.com");
        savedUser.setId(1L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = userService.create(dto);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Alice");
        verify(userRepository).save(any(User.class));  // 驗證 save 確實被呼叫
    }

    @Test
    @DisplayName("update - 應更新使用者資料")
    void update_shouldModifyAndSaveUser() {
        User existingUser = new User("Alice", "alice@example.com");
        existingUser.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

        User updatedUser = new User("Alice Updated", "alice.new@example.com");
        updatedUser.setId(1L);
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        UserDto updateDto = new UserDto("Alice Updated", "alice.new@example.com");
        User result = userService.update(1L, updateDto);

        assertThat(result.getName()).isEqualTo("Alice Updated");
        verify(userRepository).findById(1L);           // 驗證先查詢
        verify(userRepository).save(any(User.class));   // 驗證再儲存
    }

    @Test
    @DisplayName("delete - 存在時應成功刪除")
    void delete_whenExists_shouldDelete() {
        User user = new User("Alice", "alice@example.com");
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.delete(1L);

        verify(userRepository).delete(user);            // 驗證 delete 被呼叫
        verify(userRepository, never()).save(any());    // 驗證 save 沒有被呼叫
    }

    @Test
    @DisplayName("delete - 不存在時應拋出例外")
    void delete_whenNotExists_shouldThrowException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(99L))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).delete(any());  // 驗證 delete 沒有被呼叫
    }
}
```

**適用場景**：Service 層邏輯、工具類、轉換器、計算邏輯

---

### 4.2 @WebMvcTest（Controller 切片測試）

當你只想測 Controller 的路由、HTTP 狀態碼、請求驗證，而不想等整個應用啟動時，`@WebMvcTest` 就是你的好朋友。它只載入 Web 層的 Bean，**不啟動 Tomcat**。

**特點**：
- 只載入 Web 層 Bean（Controller, ControllerAdvice, Filter）
- Service 層用 `@MockBean` 替代
- 使用 `MockMvc` 模擬 HTTP 請求

**範例檔案**：[`src/test/java/.../controller/UserControllerTest.java`](src/test/java/com/example/testing/controller/UserControllerTest.java)

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    // ---- 查詢測試 ----

    @Test
    @DisplayName("GET /api/users - 應回傳所有使用者 (200)")
    void getAllUsers_shouldReturnList() throws Exception {
        User user1 = new User("Alice", "alice@example.com");
        user1.setId(1L);
        User user2 = new User("Bob", "bob@example.com");
        user2.setId(2L);
        when(userService.findAll()).thenReturn(Arrays.asList(user1, user2));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Alice")))
                .andExpect(jsonPath("$[1].name", is("Bob")));
    }

    @Test
    @DisplayName("GET /api/users/{id} - 存在時應回傳使用者 (200)")
    void getUserById_whenExists_shouldReturnUser() throws Exception {
        User user = new User("Alice", "alice@example.com");
        user.setId(1L);
        when(userService.findById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Alice")))
                .andExpect(jsonPath("$.email", is("alice@example.com")));
    }

    @Test
    @DisplayName("GET /api/users/{id} - 不存在時應回傳 404")
    void getUserById_whenNotExists_shouldReturn404() throws Exception {
        when(userService.findById(99L)).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with id: 99"));
    }

    // ---- 建立測試 ----

    @Test
    @DisplayName("POST /api/users - 有效資料應建立使用者 (201)")
    void createUser_withValidData_shouldReturn201() throws Exception {
        UserDto dto = new UserDto("Alice", "alice@example.com");
        User created = new User("Alice", "alice@example.com");
        created.setId(1L);
        when(userService.create(any(UserDto.class))).thenReturn(created);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Alice")));
    }

    @Test
    @DisplayName("POST /api/users - 名字為空應回傳 400")
    void createUser_withBlankName_shouldReturn400() throws Exception {
        UserDto dto = new UserDto("", "alice@example.com");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());
    }

    @Test
    @DisplayName("POST /api/users - Email 格式錯誤應回傳 400")
    void createUser_withInvalidEmail_shouldReturn400() throws Exception {
        UserDto dto = new UserDto("Alice", "not-an-email");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());
    }

    @Test
    @DisplayName("POST /api/users - 缺少 Content-Type 應回傳 415")
    void createUser_withoutContentType_shouldReturn415() throws Exception {
        mockMvc.perform(post("/api/users")
                        .content("{\"name\":\"Alice\",\"email\":\"alice@example.com\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---- 更新測試 ----

    @Test
    @DisplayName("PUT /api/users/{id} - 應更新使用者 (200)")
    void updateUser_shouldReturnUpdatedUser() throws Exception {
        UserDto dto = new UserDto("Alice Updated", "alice.new@example.com");
        User updated = new User("Alice Updated", "alice.new@example.com");
        updated.setId(1L);
        when(userService.update(eq(1L), any(UserDto.class))).thenReturn(updated);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Alice Updated")));
    }

    // ---- 刪除測試 ----

    @Test
    @DisplayName("DELETE /api/users/{id} - 應回傳 204")
    void deleteUser_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());
    }
}
```

**適用場景**：REST API 路由測試、HTTP Status 驗證、Request Validation、Exception Handler 測試

---

### 4.3 @DataJpaTest（Repository 切片測試）

想驗證你的 Repository Query Method 是否正確？`@DataJpaTest` 只載入 JPA 相關的 Bean，搭配 H2 記憶體資料庫（這就是一個 Fake），每個測試結束後自動 Rollback，乾淨又快速。

**特點**：
- 只載入 JPA 相關 Bean（Entity, Repository, EntityManager）
- 預設使用 H2 記憶體資料庫
- 每個測試方法結束後自動 Rollback

**範例檔案**：[`src/test/java/.../repository/UserRepositoryTest.java`](src/test/java/com/example/testing/repository/UserRepositoryTest.java)

```java
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    // ---- 自定義查詢方法測試 ----

    @Test
    @DisplayName("findByEmail - 存在時應回傳使用者")
    void findByEmail_whenExists_shouldReturnUser() {
        // Arrange - 使用 TestEntityManager 插入測試資料
        User user = new User("Alice", "alice@example.com");
        entityManager.persistAndFlush(user);

        // Act
        Optional<User> found = userRepository.findByEmail("alice@example.com");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("findByEmail - 不存在時應回傳 empty")
    void findByEmail_whenNotExists_shouldReturnEmpty() {
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByEmail - 存在時應回傳 true")
    void existsByEmail_whenExists_shouldReturnTrue() {
        User user = new User("Bob", "bob@example.com");
        entityManager.persistAndFlush(user);

        boolean exists = userRepository.existsByEmail("bob@example.com");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByEmail - 不存在時應回傳 false")
    void existsByEmail_whenNotExists_shouldReturnFalse() {
        boolean exists = userRepository.existsByEmail("nobody@example.com");

        assertThat(exists).isFalse();
    }

    // ---- CRUD 基本操作測試 ----

    @Test
    @DisplayName("save - 應成功儲存使用者並產生 ID")
    void save_shouldPersistUserWithGeneratedId() {
        User user = new User("Charlie", "charlie@example.com");

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Charlie");

        // 驗證確實寫入資料庫
        User found = entityManager.find(User.class, saved.getId());
        assertThat(found).isNotNull();
        assertThat(found.getEmail()).isEqualTo("charlie@example.com");
    }

    @Test
    @DisplayName("findAll - 應回傳所有使用者")
    void findAll_shouldReturnAllUsers() {
        entityManager.persistAndFlush(new User("Alice", "alice@example.com"));
        entityManager.persistAndFlush(new User("Bob", "bob@example.com"));

        var users = userRepository.findAll();

        assertThat(users).hasSize(2);
    }

    @Test
    @DisplayName("delete - 刪除後應無法再查到")
    void delete_shouldRemoveUser() {
        User user = new User("Alice", "alice@example.com");
        entityManager.persistAndFlush(user);
        Long userId = user.getId();

        userRepository.deleteById(userId);
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findById(userId)).isEmpty();
    }

    // ---- 資料完整性測試 ----

    @Test
    @DisplayName("save - email 重複應拋出例外")
    void save_withDuplicateEmail_shouldThrowException() {
        entityManager.persistAndFlush(new User("Alice", "same@example.com"));

        assertThatThrownBy(() -> {
            userRepository.save(new User("Bob", "same@example.com"));
            entityManager.flush();  // 強制寫入才會觸發 unique constraint
        }).isInstanceOf(Exception.class);
    }
}
```

**適用場景**：Repository Method 驗證、Custom Query（@Query）測試、Entity Mapping 驗證、資料完整性驗證

---

### 4.4 @JsonTest（JSON 序列化測試）

在微服務之間，JSON 是最常見的資料交換格式。如果序列化/反序列化出了問題，debug 起來往往很痛苦。`@JsonTest` 讓你可以單獨驗證 DTO 的 JSON 行為，不需要啟動 Web 層或資料庫。

**特點**：
- 只載入 JSON 相關 Bean（ObjectMapper, JacksonTester）
- 不載入 Web 層、資料庫
- 適合驗證 `@JsonProperty`、`@JsonIgnore`、`@JsonFormat` 等

**範例檔案**：[`src/test/java/.../json/UserDtoJsonTest.java`](src/test/java/com/example/testing/json/UserDtoJsonTest.java)

```java
@JsonTest
class UserDtoJsonTest {

    @Autowired
    private JacksonTester<UserDto> json;

    // ---- 序列化測試 ----

    @Test
    @DisplayName("序列化 - UserDto 應正確轉為 JSON")
    void serialize_shouldProduceCorrectJson() throws Exception {
        UserDto dto = new UserDto("Alice", "alice@example.com");

        assertThat(json.write(dto))
                .hasJsonPathStringValue("$.name")
                .extractingJsonPathStringValue("$.name").isEqualTo("Alice");

        assertThat(json.write(dto))
                .hasJsonPathStringValue("$.email")
                .extractingJsonPathStringValue("$.email").isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("序列化 - 應只包含預期的欄位")
    void serialize_shouldContainExpectedFieldsOnly() throws Exception {
        UserDto dto = new UserDto("Bob", "bob@example.com");

        String jsonString = json.write(dto).getJson();
        assertThat(jsonString).contains("\"name\"");
        assertThat(jsonString).contains("\"email\"");
    }

    // ---- 反序列化測試 ----

    @Test
    @DisplayName("反序列化 - JSON 應正確轉為 UserDto")
    void deserialize_shouldProduceCorrectObject() throws Exception {
        String content = """
                {
                    "name": "Bob",
                    "email": "bob@example.com"
                }
                """;

        UserDto result = json.parseObject(content);

        assertThat(result.getName()).isEqualTo("Bob");
        assertThat(result.getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    @DisplayName("反序列化 - 忽略未知欄位不應出錯")
    void deserialize_withExtraFields_shouldIgnoreUnknown() throws Exception {
        String content = """
                {
                    "name": "Charlie",
                    "email": "charlie@example.com",
                    "unknownField": "should be ignored"
                }
                """;

        UserDto result = json.parseObject(content);

        assertThat(result.getName()).isEqualTo("Charlie");
        assertThat(result.getEmail()).isEqualTo("charlie@example.com");
    }

    // ---- Round-trip 測試 ----

    @Test
    @DisplayName("序列化後再反序列化 - 應保持一致")
    void roundTrip_shouldMaintainConsistency() throws Exception {
        UserDto original = new UserDto("Charlie", "charlie@example.com");

        String jsonString = json.write(original).getJson();
        UserDto restored = json.parseObject(jsonString);

        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getEmail()).isEqualTo(original.getEmail());
    }
}
```

**適用場景**：API 回應格式驗證、複雜 JSON 結構測試、日期格式轉換、`@JsonIgnore` / `@JsonProperty` 行為驗證

---

### 4.5 @RestClientTest（REST Client 測試）

微服務之間經常需要呼叫其他服務的 API。但測試時，你不可能要求所有外部服務都跑起來。`@RestClientTest` 搭配 `MockRestServiceServer`，讓你模擬外部 API 的回應，完全不需要真實的外部服務。

**特點**：
- 只載入指定的 Client 元件
- 使用 `MockRestServiceServer` 模擬外部 API 回應
- 不需要真實的外部服務

**範例檔案**：[`src/test/java/.../client/ExternalApiClientTest.java`](src/test/java/com/example/testing/client/ExternalApiClientTest.java)

```java
@RestClientTest(ExternalApiClient.class)
class ExternalApiClientTest {

    @Autowired
    private ExternalApiClient externalApiClient;

    @Autowired
    private MockRestServiceServer mockServer;

    @Test
    @DisplayName("fetchUser - 應正確解析外部 API 回應")
    void fetchUser_shouldParseResponse() {
        String responseJson = """
                {
                    "name": "External User",
                    "email": "external@example.com"
                }
                """;

        mockServer.expect(requestTo("https://api.example.com/users/123"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        UserDto result = externalApiClient.fetchUserFromExternalApi("123");

        assertThat(result.getName()).isEqualTo("External User");
        assertThat(result.getEmail()).isEqualTo("external@example.com");
        mockServer.verify();
    }

    @Test
    @DisplayName("fetchUser - 外部 API 回傳 404 時應拋出例外")
    void fetchUser_when404_shouldThrowException() {
        mockServer.expect(requestTo("https://api.example.com/users/999"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> externalApiClient.fetchUserFromExternalApi("999"))
                .isInstanceOf(Exception.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("fetchUser - 外部 API 回傳 500 時應拋出例外")
    void fetchUser_when500_shouldThrowException() {
        mockServer.expect(requestTo("https://api.example.com/users/123"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> externalApiClient.fetchUserFromExternalApi("123"))
                .isInstanceOf(Exception.class);

        mockServer.verify();
    }
}
```

**適用場景**：第三方 API 呼叫測試、HTTP 錯誤處理、回應解析、超時行為驗證

---

### 4.6 @SpringBootTest（Integration 整合測試）

當你需要端到端驗證完整的 Controller → Service → Repository 流程時，就用 `@SpringBootTest`。它會啟動完整的 Spring Context + 內嵌 Tomcat，最接近生產環境，但也是速度最慢的。所以建議只針對關鍵業務流程來寫。

**特點**：
- 啟動完整 Spring Context + 內嵌 Tomcat
- 使用 `TestRestTemplate` 發送真實 HTTP 請求
- 最接近生產環境，但速度最慢

**範例檔案**：[`src/test/java/.../integration/UserIntegrationTest.java`](src/test/java/com/example/testing/integration/UserIntegrationTest.java)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("完整 CRUD 流程 — 建立、查詢、更新、刪除")
    void fullCrudFlow() {
        // 1. Create
        UserDto createDto = new UserDto("Alice", "alice@example.com");
        ResponseEntity<User> createResponse = restTemplate.postForEntity(
                "/api/users", createDto, User.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        Long userId = createResponse.getBody().getId();
        assertThat(userId).isNotNull();

        // 2. Read (single)
        ResponseEntity<User> getResponse = restTemplate.getForEntity(
                "/api/users/" + userId, User.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getName()).isEqualTo("Alice");

        // 3. Read (all)
        ResponseEntity<List<User>> listResponse = restTemplate.exchange(
                "/api/users", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(listResponse.getBody()).hasSize(1);

        // 4. Update
        UserDto updateDto = new UserDto("Alice Updated", "alice.updated@example.com");
        ResponseEntity<User> updateResponse = restTemplate.exchange(
                "/api/users/" + userId, HttpMethod.PUT,
                new HttpEntity<>(updateDto), User.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().getName()).isEqualTo("Alice Updated");

        // 5. Delete
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/users/" + userId, HttpMethod.DELETE, null, Void.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 6. Verify deleted
        ResponseEntity<String> verifyResponse = restTemplate.getForEntity(
                "/api/users/" + userId, String.class);

        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("建立使用者 — 無效資料應回傳 400")
    void createUser_withInvalidData_shouldReturn400() {
        UserDto invalidDto = new UserDto("", "not-an-email");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/users", invalidDto, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("建立多筆使用者後查詢列表")
    void createMultipleUsers_shouldReturnAll() {
        restTemplate.postForEntity("/api/users",
                new UserDto("Alice", "alice@example.com"), User.class);
        restTemplate.postForEntity("/api/users",
                new UserDto("Bob", "bob@example.com"), User.class);
        restTemplate.postForEntity("/api/users",
                new UserDto("Charlie", "charlie@example.com"), User.class);

        ResponseEntity<List<User>> response = restTemplate.exchange(
                "/api/users", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getBody()).hasSize(3);
    }

    @Test
    @DisplayName("查詢不存在的使用者應回傳 404")
    void getUserById_whenNotExists_shouldReturn404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/users/99999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```

**適用場景**：關鍵業務流程的端到端驗證、跨層互動測試

---

## 5. Testcontainers 整合

### 你踩過這個坑嗎？

用 H2 記憶體資料庫跑測試，全部綠燈通過。部署到生產環境連上 PostgreSQL，結果直接炸掉。

這不是你的問題，是 H2 跟真實資料庫本來就有差異：

| 問題 | 說明 |
|------|------|
| **SQL 方言差異** | H2 支援的 SQL 語法與 PostgreSQL/MySQL 不完全相同 |
| **特定功能不支援** | JSON 型別、陣列型別、全文搜尋等功能在 H2 上行為不同 |
| **False Positive** | 測試在 H2 上通過，但在生產 DB 上失敗 |

還記得第一章提到的 Test Double 嗎？H2 就是一個 **Fake** — 它有真實的行為，但走的是捷徑。大部分情況下 Fake 夠用了，但當你需要驗證特定資料庫的行為時，就需要更真實的環境。

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

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void createAndRetrieveUser_withRealPostgres() {
        // 這裡的操作全部跑在真實的 PostgreSQL 上！
        UserDto dto = new UserDto("Alice", "alice@example.com");
        ResponseEntity<User> response = restTemplate.postForEntity("/api/users", dto, User.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Long userId = response.getBody().getId();
        ResponseEntity<User> getResponse = restTemplate.getForEntity("/api/users/" + userId, User.class);
        assertThat(getResponse.getBody().getName()).isEqualTo("Alice");
    }

    @Test
    void uniqueEmailConstraint_withRealPostgres() {
        // 在真實的 PostgreSQL 上測試 unique constraint
        restTemplate.postForEntity("/api/users",
                new UserDto("Alice", "same@example.com"), User.class);

        // 第二次用相同 email 建立應該失敗
        ResponseEntity<String> response = restTemplate.postForEntity("/api/users",
                new UserDto("Bob", "same@example.com"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
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

## 6. 合約測試（Contract Testing）

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

### 6.1 Design by Contract（契約式設計）

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

### 6.2 Contract Test（合約測試）

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

### 6.3 Spring Cloud Contract

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

### 6.4 什麼時候該導入合約測試？

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
