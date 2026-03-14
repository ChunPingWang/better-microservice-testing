package com.example.testing.unit;

import com.example.testing.dto.UserDto;
import com.example.testing.entity.User;
import com.example.testing.exception.UserNotFoundException;
import com.example.testing.repository.UserRepository;
import com.example.testing.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit Test 範例 — 使用 Mockito 測試 Service 層
 *
 * 特點：
 * - 不啟動 Spring Context，速度最快
 * - 使用 @Mock 模擬依賴，@InjectMocks 注入測試對象
 * - 專注測試業務邏輯，隔離外部依賴
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("findAll - 應回傳所有使用者")
    void findAll_shouldReturnAllUsers() {
        // Arrange
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

    @Test
    @DisplayName("create - 應成功建立使用者")
    void create_shouldSaveAndReturnUser() {
        UserDto dto = new UserDto("Alice", "alice@example.com");
        User savedUser = new User("Alice", "alice@example.com");
        savedUser.setId(1L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = userService.create(dto);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Alice");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("delete - 存在時應成功刪除")
    void delete_whenExists_shouldDelete() {
        User user = new User("Alice", "alice@example.com");
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.delete(1L);

        verify(userRepository).delete(user);
    }

    // ===== 以下為 TDD 範例新增的測試（Section 12）=====

    @Test
    @DisplayName("searchByName - 應回傳名稱包含關鍵字的使用者")
    void searchByName_shouldReturnMatchingUsers() {
        // Arrange
        List<User> matched = Arrays.asList(
                new User("Alice Wang", "alice@example.com"),
                new User("Alice Chen", "alice.chen@example.com")
        );
        when(userRepository.findByNameContainingIgnoreCase("Alice")).thenReturn(matched);

        // Act
        List<User> result = userService.searchByName("Alice");

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(User::getName)
                .containsExactly("Alice Wang", "Alice Chen");
    }

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

    @Test
    @DisplayName("searchByName - null 應回傳所有使用者")
    void searchByName_whenNull_shouldReturnAllUsers() {
        List<User> allUsers = Arrays.asList(
                new User("Alice", "alice@example.com")
        );
        when(userRepository.findAll()).thenReturn(allUsers);

        List<User> result = userService.searchByName(null);

        assertThat(result).hasSize(1);
        verify(userRepository).findAll();
    }
}
