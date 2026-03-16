# Spring Boot Testing Guide — 從測試角度學習 Spring Boot

> **本專案翻轉學習順序：先理解「怎麼測」，再理解「怎麼寫」。**
>
> 大多數教學從 Entity → Repository → Service → Controller 由下而上教，但初學者常常不知道為什麼要這樣分層。
> 本專案從測試的角度出發——每一層的存在，都是因為它能被獨立測試、獨立驗證。當你理解「這一層該怎麼測」，你就真正理解了「這一層為什麼存在」。

---

## 目錄

- [快速開始](#快速開始)
- **Part I — 觀念篇**
  - [1. 從測試看分層：為什麼要分 Controller / Service / Repository？](#1-從測試看分層為什麼要分-controller--service--repository)
  - [2. 測試金字塔](#2-測試金字塔)
  - [3. Swagger 手動測試 vs Spring Boot Test 自動化測試](#3-swagger-手動測試-vs-spring-boot-test-自動化測試)
  - [4. Test Doubles 全解析：Mock、Stub、Spy、Fake](#4-test-doubles-全解析mockstubspyfake)
  - [5. Arrange-Act-Assert 與好測試的原則](#5-arrange-act-assert-與好測試的原則)
  - [6. 什麼時候該 Mock？什麼時候不該？](#6-什麼時候該-mock什麼時候不該)
- **Part II — 實作篇：各類測試詳解**
  - [7. 切片測試完整對照表](#7-切片測試完整對照表)
  - [8. Unit Test（單元測試）](#8-unit-test單元測試)
  - [9. @WebMvcTest（Controller 切片測試）](#9-webmvctestcontroller-切片測試)
  - [10. @DataJpaTest（Repository 切片測試）](#10-datajpatestrepository-切片測試)
  - [11. @JsonTest（JSON 序列化測試）](#11-jsontestjson-序列化測試)
  - [12. @RestClientTest（REST Client 測試）](#12-restclienttestrest-client-測試)
  - [13. @SpringBootTest（整合測試）](#13-springboottestintegration-整合測試)
- **Part III — 進階篇**
  - [14. Testcontainers 整合](#14-testcontainers-整合)
  - [15. 合約測試（Contract Testing）](#15-合約測試contract-testing)
  - [16. TDD 實戰示範：先寫測試再寫實作](#16-tdd-實戰示範先寫測試再寫實作)

---

## 快速開始

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
│   ├── UserServiceTest.java           ← Unit Test (Mockito) — 含 TDD 搜尋範例
│   └── UserServiceSpyTest.java        ← Spy 測試範例 (Mock vs Spy 差異)
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

### 學習建議

1. **先跑測試** — `mvn test`，看看測試如何全部通過
2. **讀測試程式碼** — 從 `UserServiceTest` 開始，理解 Arrange-Act-Assert 模式
3. **對照實作** — 看完測試後再看對應的 Service / Controller 實作
4. **動手破壞** — 故意改壞一行程式碼，看哪個測試先失敗，理解測試的守護範圍
5. **新增功能** — 試著加一個新的 API（例如搜尋功能），先寫測試再寫實作

---

# Part I — 觀念篇

## 1. 從測試看分層：為什麼要分 Controller / Service / Repository？

> 初學者最常問的問題：「為什麼要分這麼多層？全部寫在 Controller 裡不行嗎？」
>
> 答案是：**因為分層讓每一層都能被獨立測試。** 可測試性（Testability）是好架構的核心指標。

### 傳統教學 vs 測試驅動思維

```
傳統學習路線（由下而上）：
  Entity → Repository → Service → Controller → 最後才學測試
  問題：學了半天不知道「分層到底有什麼好處」

測試驅動學習路線（本專案）：
  先看測試怎麼寫 → 理解每一層為什麼存在 → 再看實作
  好處：每一層的職責因為「能被怎麼測」而變得清晰
```

### 每一層為什麼存在？用測試來回答

| 層 | 為什麼獨立存在？ | 測試怎麼證明？ |
|---|---|---|
| **Controller** | 負責 HTTP 協議（路由、狀態碼、驗證） | `@WebMvcTest` 不需要 Service 和 DB 就能測試 HTTP 行為 |
| **Service** | 負責業務邏輯（規則、計算、流程） | `@ExtendWith(MockitoExtension.class)` 不需要 Spring 就能測試邏輯 |
| **Repository** | 負責資料存取（SQL、查詢方法） | `@DataJpaTest` 不需要 Controller 和 Service 就能測試 DB 操作 |
| **DTO** | 負責資料傳輸格式（JSON 結構） | `@JsonTest` 不需要任何其他層就能測試 JSON 序列化 |
| **Client** | 負責呼叫外部 API | `@RestClientTest` 不需要真實外部服務就能測試呼叫邏輯 |

### 一句話總結

> **如果你把所有邏輯放在同一層，你就無法單獨測試任何一件事。**
> 分層的真正價值，不只是「程式碼整潔」，而是「每一層都能獨立驗證其正確性」。

---

## 2. 測試金字塔

> 測試金字塔是 Mike Cohn 在 2009 年提出的概念。核心思想很簡單：**底層的測試寫得越多、跑得越快；頂層的測試只挑關鍵流程來寫。**

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
| **Integration Test** | @SpringBootTest | 少量 | 5-15 秒 | 完整應用 |
| **E2E Test** | @SpringBootTest + Testcontainers | 最少 | 10-30 秒 | 含真實基礎設施 |

**為什麼是金字塔形？**

越往上的測試，環境越複雜、速度越慢、除錯也越困難。如果大部分邏輯已經被底層的 Unit Test 驗證過了，上層的整合測試只需要驗證「各層串起來有沒有問題」即可。

---

## 3. Swagger 手動測試 vs Spring Boot Test 自動化測試

許多團隊習慣使用 **Swagger UI** 手動測試 API，這在開發初期方便快速驗證，但存在明顯的限制。Spring Boot Test 提供了更高效、更可靠的測試方式。

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

Spring Boot 提供了**切片測試（Slice Test）**機制，關鍵在於 **Application Context 的選擇性載入**：

```
Swagger 測試流程：
  啟動整個 Spring Boot App → 啟動 Tomcat → 載入所有 Bean → 人工操作 Swagger UI → 肉眼驗證

@WebMvcTest 測試流程：
  只載入 Controller + Filter + ControllerAdvice → 使用 MockMvc 模擬 HTTP → 自動驗證
  ❌ 不啟動 Tomcat
  ❌ 不載入 Service、Repository、DataSource
```

這就是為什麼 `@WebMvcTest` 只需 1-3 秒，而啟動整個應用需要 10-30 秒以上。

### 結論

> **Swagger 適合開發階段的快速 API 探索和文件產出**，但不能取代自動化測試。
> **Spring Boot Test 才是保障程式品質和持續交付的正確方式。**
>
> 最佳實踐：兩者搭配使用 — Swagger 做 API 文件 + 互動式探索，Spring Boot Test 做自動化品質保障。

---

## 4. Test Doubles 全解析：Mock、Stub、Spy、Fake

> 初學者常把 Mock、Stub、Spy 全部叫做「Mock」，但它們其實是不同的東西，用途也不同。
> 理解差異後，你會更清楚「這個測試場景該用哪一種」。

### 五種 Test Double 對照

| 類型 | 一句話解釋 | Mockito 對應 | 什麼時候用？ |
|------|-----------|-------------|------------|
| **Dummy** | 佔位用的替身 | `mock(...)` 但不設定行為 | 只是填滿參數列表，不會真的被使用 |
| **Stub** | 回傳固定答案的替身 | `when(...).thenReturn(...)` | 你只關心「回傳什麼」，不關心「有沒有被呼叫」 |
| **Mock** | 會驗證互動的替身 | `verify(mock).method()` | 你需要確認「某個方法確實被呼叫了」 |
| **Spy** | 包裝真實物件，可部分覆蓋 | `@Spy` + `doReturn(...).when(spy)` | 大部分用真實邏輯，只替換其中一個方法 |
| **Fake** | 簡化版的真實實作 | 手動寫一個簡化類別 | 真實物件太重（如用 HashMap 取代 DB） |

### 用程式碼說明差異

```java
// ===== Dummy：只是佔位，不會真正使用 =====
UserRepository dummyRepo = mock(UserRepository.class);
new SomeOtherService(dummyRepo);  // 只是需要一個參數填入，不會呼叫它的方法

// ===== Stub：只設定回傳值，不驗證互動 =====
@Mock
private UserRepository userRepository;

@Test
void stub_example() {
    // 這是 Stub 行為：「當呼叫 findById 時，回傳這個值」
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    User result = userService.findById(1L);

    // 只驗證結果，不驗證 repository 是否被呼叫
    assertThat(result.getName()).isEqualTo("Alice");
}

// ===== Mock：驗證互動（方法是否被呼叫） =====
@Test
void mock_example() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    userService.delete(1L);

    // 驗證 delete 確實呼叫了 repository.delete()
    verify(userRepository).delete(user);
    // 驗證沒有其他多餘的互動
    verifyNoMoreInteractions(userRepository);
}

// ===== Spy：真實物件 + 部分覆蓋 =====
@Spy
private List<String> spyList = new ArrayList<>();

@Test
void spy_example() {
    spyList.add("real");           // 真實呼叫，list 真的加了元素
    assertThat(spyList).hasSize(1); // 真實結果

    doReturn(100).when(spyList).size(); // 只覆蓋 size() 方法
    assertThat(spyList.size()).isEqualTo(100); // 被覆蓋的結果
    assertThat(spyList.get(0)).isEqualTo("real"); // 其他方法仍是真實的
}

// ===== Fake：手動簡化實作 =====
// 不用 Mockito，自己寫一個簡化版 Repository
class FakeUserRepository {
    private Map<Long, User> store = new HashMap<>();
    private Long idSeq = 1L;

    public User save(User user) {
        user.setId(idSeq++);
        store.put(user.getId(), user);
        return user;
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }
}
```

### Spy 的實際應用場景

**範例檔案**：[`src/test/java/.../unit/UserServiceSpyTest.java`](src/test/java/com/example/testing/unit/UserServiceSpyTest.java)

```java
@ExtendWith(MockitoExtension.class)
class UserServiceSpyTest {

    @Mock
    private UserRepository userRepository;

    @Spy
    @InjectMocks
    private UserService userService;

    @Test
    void update_shouldCallFindByIdInternally() {
        User existing = new User("Alice", "alice@example.com");
        existing.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenReturn(existing);

        // Spy 讓我們驗證 update() 內部確實呼叫了 findById()
        userService.update(1L, new UserDto("Updated", "updated@example.com"));

        // 驗證內部方法呼叫鏈
        verify(userService).findById(1L);
        verify(userRepository).save(any(User.class));
    }
}
```

### Stub vs Mock：什麼時候用哪個？

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

### 選擇指南

```
你需要什麼？
  │
  ├─ 只需要「回傳假資料」→ Stub（when().thenReturn()）
  │
  ├─ 需要「驗證某方法有沒有被呼叫」→ Mock（verify()）
  │
  ├─ 需要「用真實邏輯，但覆蓋其中一個方法」→ Spy（@Spy）
  │
  └─ 需要「完整的替代實作，但比真實物件輕量」→ Fake（手動實作）
```

---

## 5. Arrange-Act-Assert 與好測試的原則

### 5.1 AAA 模式（Arrange-Act-Assert）

每個測試方法都應該有清晰的三段結構：

```java
@Test
void findById_whenExists_shouldReturnUser() {
    // ===== Arrange（準備）=====
    // 設定測試資料和前置條件
    User user = new User("Alice", "alice@example.com");
    user.setId(1L);
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    // ===== Act（執行）=====
    // 呼叫被測試的方法（通常只有一行）
    User result = userService.findById(1L);

    // ===== Assert（驗證）=====
    // 檢查結果是否符合預期
    assertThat(result.getName()).isEqualTo("Alice");
    assertThat(result.getEmail()).isEqualTo("alice@example.com");
}
```

**關鍵原則**：
- **Arrange**：可以很長（準備複雜場景），沒問題
- **Act**：通常只有 **一行**——就是你要測的那個方法呼叫
- **Assert**：驗證結果，可以多個斷言，但都應該針對同一個行為的結果

### 5.2 測試命名：讓測試名稱就是文件

好的測試名稱 = **被測方法_場景_預期行為**

```java
// ✅ 好的命名：讀名稱就知道在測什麼
void findById_whenExists_shouldReturnUser()
void findById_whenNotExists_shouldThrowException()
void create_withValidDto_shouldSaveAndReturnUser()
void delete_whenUserNotFound_shouldThrowException()

// ❌ 差的命名：不知道在測什麼
void test1()
void testFindById()
void findByIdTest()
void itWorks()
```

搭配 `@DisplayName` 提供中文描述，兩全其美：

```java
@Test
@DisplayName("findById - 存在時應回傳使用者")
void findById_whenExists_shouldReturnUser() { ... }
```

### 5.3 測試行為，不要測實作

```java
// ❌ 測試實作細節（脆弱：重構就壞）
@Test
void create_shouldCallRepositorySaveExactlyOnce() {
    userService.create(dto);
    verify(userRepository, times(1)).save(any());
    // 問題：如果 Service 內部改成 saveAndFlush()，測試就壞了
    // 但功能其實沒壞！
}

// ✅ 測試行為（穩固：重構不會壞）
@Test
void create_shouldReturnUserWithId() {
    when(userRepository.save(any())).thenReturn(savedUser);

    User result = userService.create(dto);

    assertThat(result.getId()).isNotNull();
    assertThat(result.getName()).isEqualTo("Alice");
    // 只驗證「結果正確」，不管內部怎麼做到的
}
```

### 5.4 好測試的 F.I.R.S.T 原則

| 原則 | 說明 | 範例 |
|------|------|------|
| **F**ast（快速） | 單元測試應在毫秒內完成 | 不要在 Unit Test 裡連資料庫 |
| **I**ndependent（獨立） | 測試之間不互相依賴 | 不要依賴其他測試的執行順序 |
| **R**epeatable（可重複） | 每次執行結果一致 | 不要依賴外部 API、系統時間、隨機數 |
| **S**elf-validating（自驗證） | 測試自動判斷 pass/fail | 不要靠 `System.out.println` 人眼驗證 |
| **T**imely（及時） | 在寫產品程式碼時就寫測試 | 不要等上線前才補測試 |

### 5.5 一個測試只測一件事

```java
// ❌ 一個測試做太多事
@Test
void testUserCrud() {
    // 測建立
    User created = userService.create(dto);
    assertThat(created.getId()).isNotNull();
    // 測查詢
    User found = userService.findById(created.getId());
    assertThat(found.getName()).isEqualTo("Alice");
    // 測刪除
    userService.delete(created.getId());
    assertThatThrownBy(() -> userService.findById(created.getId()));
    // 問題：如果失敗了，是哪一步出錯？
}

// ✅ 每個測試只驗證一個行為
@Test void create_shouldReturnUserWithId() { ... }
@Test void findById_whenExists_shouldReturnUser() { ... }
@Test void delete_whenExists_shouldRemoveUser() { ... }
```

> **例外**：Integration Test（如 `UserIntegrationTest.fullCrudFlow()`）可以測完整流程，
> 因為它的目的就是驗證「端到端流程是否正確」，這是有意的設計。

---

## 6. 什麼時候該 Mock？什麼時候不該？

> Mock 是強大的工具，但濫用 Mock 會讓測試變得脆弱且沒有意義。
> 核心原則：**在邊界處 Mock，在邊界內用真實物件。**

### 6.1 邊界原則

```
你的程式碼
┌──────────────────────────────────┐
│                                  │
│   Service ──→ Repository ──→ DB  │ ← DB 是邊界，該 Mock
│      │                           │
│      └──→ ExternalApiClient ──→  │ ← 外部 API 是邊界，該 Mock
│             外部 HTTP 服務        │
│                                  │
└──────────────────────────────────┘
```

### 6.2 該 Mock 的場景

| 場景 | 為什麼要 Mock | 範例 |
|------|-------------|------|
| **資料庫存取** | Unit Test 不該依賴 DB | Mock `UserRepository` |
| **外部 API 呼叫** | 不可控、不穩定、慢 | `MockRestServiceServer` |
| **第三方服務** | 可能收費、有頻率限制 | Mock 付款 API、SMS API |
| **系統資源** | 檔案系統、網路、時鐘 | Mock `Clock.now()` 固定時間 |
| **尚未實作的依賴** | 另一個團隊還沒寫完 | Mock 介面先寫測試 |

### 6.3 不該 Mock 的場景

| 場景 | 為什麼不該 Mock | 該怎麼做 |
|------|---------------|---------|
| **被測類別自己** | Mock 了就不是在測它了 | 直接 `new` 或 `@InjectMocks` |
| **簡單的 Value Object** | DTO、Entity 不需要 Mock | 直接 `new UserDto("Alice", "alice@example.com")` |
| **Java 標準庫** | `String`、`List`、`Map` 永遠不該 Mock | 用真實物件 |
| **私有方法** | 想 Mock 私有方法代表設計有問題 | 重構，把邏輯抽到可測的類別 |

### 6.4 過度 Mock 的反模式

```java
// ❌ 過度 Mock：測試什麼都沒測到
@Test
void overMocking_example() {
    UserDto dto = mock(UserDto.class);            // ❌ DTO 不該 Mock
    when(dto.getName()).thenReturn("Alice");
    when(dto.getEmail()).thenReturn("alice@example.com");

    User user = mock(User.class);                 // ❌ Entity 不該 Mock
    when(user.getId()).thenReturn(1L);

    when(userRepository.save(any())).thenReturn(user);

    User result = userService.create(dto);

    // 這個測試看似通過了，但其實什麼都沒驗證
    // 因為所有物件都是假的，你只是在測「Mockito 能不能正常運作」
    verify(userRepository).save(any());
}

// ✅ 正確做法：只 Mock 邊界依賴
@Test
void correctMocking_example() {
    UserDto dto = new UserDto("Alice", "alice@example.com"); // ✅ 真實 DTO
    User savedUser = new User("Alice", "alice@example.com"); // ✅ 真實 Entity
    savedUser.setId(1L);
    when(userRepository.save(any(User.class))).thenReturn(savedUser); // ✅ 只 Mock Repository

    User result = userService.create(dto);

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getName()).isEqualTo("Alice");
}
```

### 6.5 判斷流程圖

```
你要測試的依賴是什麼？
  │
  ├─ 資料庫 / 外部 API / 第三方服務 / 檔案系統
  │   └─→ ✅ Mock 它
  │
  ├─ DTO / Entity / Value Object / 標準庫
  │   └─→ ❌ 不要 Mock，用真實物件
  │
  ├─ 你自己寫的另一個 Service
  │   ├─ 在 Unit Test 中 → ✅ Mock 它（隔離測試）
  │   └─ 在 Integration Test 中 → ❌ 用真實的（驗證協作）
  │
  └─ 被測試的類別本身
      └─→ ❌ 絕對不要 Mock（那還測什麼？）
```

---

# Part II — 實作篇：各類測試詳解

## 7. 切片測試完整對照表

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

## 8. Unit Test（單元測試）

**目的**：測試單一類別的業務邏輯，完全隔離外部依賴。

**特點**：
- 不啟動 Spring Context → 速度最快
- 使用 `@Mock` 模擬依賴、`@InjectMocks` 注入被測對象
- 專注驗證邏輯正確性

**範例檔案**：[`src/test/java/.../unit/UserServiceTest.java`](src/test/java/com/example/testing/unit/UserServiceTest.java)

本範例同時示範 Stub（驗證回傳值）和 Mock（驗證互動）的用法：

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
        List<User> users = Arrays.asList(
                new User("Alice", "alice@example.com"),
                new User("Bob", "bob@example.com")
        );
        when(userRepository.findAll()).thenReturn(users);

        List<User> result = userService.findAll();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findById - 存在時應回傳使用者")
    void findById_whenExists_shouldReturnUser() {
        User user = new User("Alice", "alice@example.com");
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.findById(1L);

        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("findById - 不存在時應拋出例外")
    void findById_whenNotExists_shouldThrowException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(UserNotFoundException.class);
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
    @DisplayName("delete - 應刪除使用者")
    void delete_whenExists_shouldDelete() {
        User user = new User("Alice", "alice@example.com");
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

## 9. @WebMvcTest（Controller 切片測試）

**目的**：測試 Controller 層的路由、HTTP 狀態碼、請求/回應格式、參數驗證。

**特點**：
- 只載入 Web 層 Bean（Controller, ControllerAdvice, Filter）
- **不啟動 Tomcat**，使用 MockMvc 模擬 HTTP 請求
- Service 層用 `@MockitoBean` 替代（Spring Boot 3.4+ 取代了舊版 `@MockBean`）

**範例檔案**：[`src/test/java/.../controller/UserControllerTest.java`](src/test/java/com/example/testing/controller/UserControllerTest.java)

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    // ---- 查詢測試 ----

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
        User created = new User("Alice", "alice@example.com");
        created.setId(1L);
        when(userService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserDto("Alice", "alice@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Alice")));
    }

    @Test
    @DisplayName("POST /api/users - 名字為空應回傳 400")
    void createUser_withBlankName_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserDto("", "alice@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());
    }

    @Test
    @DisplayName("POST /api/users - Email 格式錯誤應回傳 400")
    void createUser_withInvalidEmail_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserDto("Alice", "not-an-email"))))
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
        User updated = new User("Alice Updated", "alice@example.com");
        updated.setId(1L);
        when(userService.update(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserDto("Alice Updated", "alice@example.com"))))
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

## 10. @DataJpaTest（Repository 切片測試）

**目的**：測試 JPA Repository 的 Query Method、自定義查詢。

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
        entityManager.persistAndFlush(new User("Alice", "alice@example.com"));

        Optional<User> found = userRepository.findByEmail("alice@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Alice");
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
        User user = new User("Alice", "alice@example.com");

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(entityManager.find(User.class, saved.getId())).isNotNull();
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

**適用場景**：Repository Method 驗證、Custom Query（@Query）測試、Entity Mapping 驗證、資料完整性約束

---

## 11. @JsonTest（JSON 序列化測試）

**目的**：測試 DTO / Value Object 的 JSON 序列化與反序列化。

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
        UserDto original = new UserDto("Alice", "alice@example.com");

        String jsonString = json.write(original).getJson();
        UserDto restored = json.parseObject(jsonString);

        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getEmail()).isEqualTo(original.getEmail());
    }
}
```

**適用場景**：API 回應格式驗證、複雜 JSON 結構測試、日期格式轉換

---

## 12. @RestClientTest（REST Client 測試）

**目的**：測試應用程式呼叫外部 API 的行為。

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
        mockServer.expect(requestTo("https://api.example.com/users/123"))
                .andRespond(withSuccess(
                    "{\"name\":\"External User\",\"email\":\"ext@example.com\"}",
                    MediaType.APPLICATION_JSON));

        UserDto result = externalApiClient.fetchUserFromExternalApi("123");

        assertThat(result.getName()).isEqualTo("External User");
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

**適用場景**：第三方 API 呼叫測試、HTTP 錯誤處理、回應解析

---

## 13. @SpringBootTest（Integration 整合測試）

**目的**：端到端驗證完整的 Controller → Service → Repository 流程。

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

    @Test
    @DisplayName("完整 CRUD 流程")
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

# Part III — 進階篇

## 14. Testcontainers 整合

### 為什麼需要 Testcontainers？

還記得前面 Test Doubles 章節提到的 **Fake** 嗎？H2 記憶體資料庫就是一個 Fake — 它是真的資料庫，但比 PostgreSQL 輕量。大部分情況下 H2 夠用了，但有時你會踩到 H2 和真實資料庫之間的差異：

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

    @Test
    void uniqueEmailConstraint_withRealPostgres() {
        // 這個測試驗證 PostgreSQL 的 unique constraint 行為
        // 在 H2 上可能行為不同！
        restTemplate.postForEntity("/api/users",
                new UserDto("Alice", "same@example.com"), User.class);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/users",
                new UserDto("Bob", "same@example.com"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

### Testcontainers 支援的服務

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

## 15. 合約測試（Contract Testing）

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

### 15.1 Design by Contract（契約式設計）

**本質**：一種**軟體設計原則**，源自 Bertrand Meyer 在 1986 年設計的 Eiffel 程式語言。

**核心概念**：
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

### 15.2 Contract Test（合約測試）

**本質**：一種**測試策略**，用於微服務架構中驗證服務之間的 API 契約。

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

### 15.3 Spring Cloud Contract

**本質**：Contract Test 策略的**具體實作框架**。

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

    @MockitoBean
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

### 15.4 實作建議

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

## 16. TDD 實戰示範：先寫測試再寫實作

> TDD（Test-Driven Development）是由 Kent Beck 在 2003 年正式提出的開發方法論。核心是 **Red → Green → Refactor** 循環。
> 用本專案的 UserService 新增一個「根據名稱搜尋」的功能來示範。

### 16.1 Red-Green-Refactor 循環

```
    ┌─── Red ──────────────────┐
    │ 先寫一個會失敗的測試       │
    │ （因為功能還沒實作）       │
    └──────────┬───────────────┘
               │
    ┌──────────▼───────────────┐
    │ Green                     │
    │ 寫最少的程式碼讓測試通過   │
    │ （不求完美，只求通過）     │
    └──────────┬───────────────┘
               │
    ┌──────────▼───────────────┐
    │ Refactor                  │
    │ 在測試保護下重構程式碼     │
    │ （測試仍然通過 = 安全）    │
    └──────────┬───────────────┘
               │
               └──→ 回到 Red，加下一個測試
```

### 16.2 實戰：為 UserService 新增搜尋功能

#### Step 1: Red — 先寫失敗的測試

```java
// 在 UserServiceTest 中新增：
@Test
@DisplayName("searchByName - 應回傳名稱包含關鍵字的使用者")
void searchByName_shouldReturnMatchingUsers() {
    // Arrange
    List<User> allUsers = Arrays.asList(
        new User("Alice Wang", "alice@example.com"),
        new User("Bob Smith", "bob@example.com"),
        new User("Alice Chen", "alice.chen@example.com")
    );
    when(userRepository.findByNameContainingIgnoreCase("Alice"))
        .thenReturn(Arrays.asList(allUsers.get(0), allUsers.get(2)));

    // Act
    List<User> result = userService.searchByName("Alice");

    // Assert
    assertThat(result).hasSize(2);
    assertThat(result).extracting(User::getName)
        .containsExactly("Alice Wang", "Alice Chen");
}
```

此時執行測試 → **Red**（編譯失敗，因為 `searchByName` 和 `findByNameContainingIgnoreCase` 都還不存在）

#### Step 2: Green — 寫最少的程式碼讓測試通過

```java
// 1. 在 UserRepository 加入查詢方法
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByNameContainingIgnoreCase(String name); // 新增
}

// 2. 在 UserService 加入搜尋方法
public List<User> searchByName(String name) {
    return userRepository.findByNameContainingIgnoreCase(name);
}
```

此時執行測試 → **Green**（測試通過！）

#### Step 3: Refactor — 在測試保護下優化

```java
// 也許我們想加入「空字串就回傳全部」的邏輯
public List<User> searchByName(String name) {
    if (name == null || name.isBlank()) {
        return userRepository.findAll();
    }
    return userRepository.findByNameContainingIgnoreCase(name);
}
```

但在重構前，先加一個測試來描述這個行為：

```java
@Test
@DisplayName("searchByName - 空字串應回傳所有使用者")
void searchByName_whenBlank_shouldReturnAllUsers() {
    List<User> allUsers = Arrays.asList(
        new User("Alice", "alice@example.com"),
        new User("Bob", "bob@example.com")
    );
    when(userRepository.findAll()).thenReturn(allUsers);

    List<User> result = userService.searchByName("");

    assertThat(result).hasSize(2);
    verify(userRepository).findAll();
    verify(userRepository, never()).findByNameContainingIgnoreCase(any());
}
```

#### Step 4: 繼續循環

接下來你可以繼續加：
- Controller 層的 `GET /api/users/search?name=Alice` 端點
- 對應的 `@WebMvcTest` 測試
- Repository 的 `@DataJpaTest` 測試確認 `findByNameContainingIgnoreCase` 真的能查到資料

### 16.3 TDD 的好處

| 好處 | 說明 |
|------|------|
| **設計更好的 API** | 寫測試時你是「使用者」，會自然設計出好用的介面 |
| **100% 測試覆蓋** | 每行程式碼都是為了讓測試通過而寫的 |
| **安全重構** | 有測試保護，重構不會怕 |
| **活文件** | 測試就是最新的規格說明書 |

### 16.4 TDD 不是教條

> 不需要「所有程式碼都用 TDD」。以下是實用建議：

| 場景 | 建議 |
|------|------|
| 商業邏輯複雜 | **強烈建議 TDD** — 先用測試釐清需求 |
| CRUD 簡單操作 | 可以先寫實作再補測試 |
| 修 Bug | **用 TDD** — 先寫重現 Bug 的測試，再修 |
| 探索性開發（Spike） | 先寫 prototype，確定方向後再用 TDD 重寫 |

> **務實的做法**：你不需要 100% TDD。對核心業務邏輯用 TDD，對簡單的 CRUD 和膠水程式碼（glue code），先寫實作再補測試也完全可以。重要的是**有測試**，而不是**什麼時候寫測試**。
