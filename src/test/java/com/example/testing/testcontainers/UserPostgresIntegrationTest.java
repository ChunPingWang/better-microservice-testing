package com.example.testing.testcontainers;

import com.example.testing.dto.UserDto;
import com.example.testing.entity.User;
import com.example.testing.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers 整合測試範例 — 使用真實 PostgreSQL 容器
 *
 * 特點：
 * - 使用 Docker 啟動真實的 PostgreSQL 資料庫
 * - 比 H2 更接近生產環境（相同的 SQL 方言、行為）
 * - @DynamicPropertySource 動態注入容器的連線資訊
 * - 需要 Docker 環境才能執行
 *
 * 注意：執行此測試需要本機安裝 Docker。
 * 使用 mvn test -P testcontainers 來啟用此測試。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserPostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

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
    @DisplayName("使用真實 PostgreSQL — 建立並查詢使用者")
    void createAndRetrieveUser_withRealPostgres() {
        // Create
        UserDto dto = new UserDto("Alice", "alice@example.com");
        ResponseEntity<User> createResponse = restTemplate.postForEntity(
                "/api/users", dto, User.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long userId = createResponse.getBody().getId();

        // Retrieve
        ResponseEntity<User> getResponse = restTemplate.getForEntity(
                "/api/users/" + userId, User.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getName()).isEqualTo("Alice");
        assertThat(getResponse.getBody().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("使用真實 PostgreSQL — 驗證 Repository 直接操作")
    void repository_shouldWorkWithRealPostgres() {
        User user = new User("Bob", "bob@example.com");
        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(userRepository.findByEmail("bob@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
    }
}
