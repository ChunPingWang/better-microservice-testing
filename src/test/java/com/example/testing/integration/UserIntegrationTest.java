package com.example.testing.integration;

import com.example.testing.dto.UserDto;
import com.example.testing.entity.User;
import com.example.testing.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @SpringBootTest 整合測試範例 — 端到端測試
 *
 * 特點：
 * - 啟動完整 Spring Context + 內嵌 Tomcat（RANDOM_PORT）
 * - 使用 TestRestTemplate 發送真實 HTTP 請求
 * - 測試完整的 Controller → Service → Repository 流程
 * - 最接近生產環境的測試方式，但速度最慢
 */
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
