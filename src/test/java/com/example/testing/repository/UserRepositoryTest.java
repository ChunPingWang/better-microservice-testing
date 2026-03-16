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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @DataJpaTest 切片測試範例 — 測試 Repository 層
 *
 * 特點：
 * - 只載入 JPA 相關的 Bean（Entity, Repository, TestEntityManager）
 * - 預設使用內嵌的 H2 資料庫（Fake）
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
