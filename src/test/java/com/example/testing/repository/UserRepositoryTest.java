package com.example.testing.repository;

import com.example.testing.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest 切片測試範例 — 測試 Repository 層
 *
 * 特點：
 * - 只載入 JPA 相關的 Bean（Entity, Repository, TestEntityManager）
 * - 預設使用內嵌的 H2 資料庫
 * - 每個測試方法結束後自動 rollback
 * - 不載入 Controller、Service 等其他層的 Bean
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

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
}
