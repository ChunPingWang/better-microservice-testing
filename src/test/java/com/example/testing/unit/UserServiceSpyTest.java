package com.example.testing.unit;

import com.example.testing.dto.UserDto;
import com.example.testing.entity.User;
import com.example.testing.repository.UserRepository;
import com.example.testing.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spy 測試範例 — 展示 @Spy 與 @Mock 的差異
 *
 * @Spy 的特性：
 * - 保留真實物件的行為，只覆蓋你指定的方法
 * - 可以驗證真實物件的內部方法呼叫
 * - 適用於「大部分邏輯用真的，只想替換一小部分」的場景
 *
 * 對比：
 * - @Mock：所有方法都是假的（預設回傳 null / 0 / false）
 * - @Spy：所有方法都是真的，除非你用 doReturn().when() 覆蓋
 */
@ExtendWith(MockitoExtension.class)
class UserServiceSpyTest {

    @Mock
    private UserRepository userRepository;

    @Spy
    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Spy — update() 內部應呼叫 findById()")
    void update_shouldCallFindByIdInternally() {
        // Arrange
        User existing = new User("Alice", "alice@example.com");
        existing.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenReturn(existing);

        // Act
        UserDto updateDto = new UserDto("Alice Updated", "alice.new@example.com");
        userService.update(1L, updateDto);

        // Assert — Spy 讓我們能驗證 update() 內部確實呼叫了 findById()
        verify(userService).findById(1L);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Spy — 真實方法仍然執行")
    void spy_realMethodStillExecutes() {
        // Arrange
        User user = new User("Alice", "alice@example.com");
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // Act — findById 是真實的 UserService 邏輯在執行
        User result = userService.findById(1L);

        // Assert — 結果來自真實邏輯，不是 mock 的回傳值
        assertThat(result.getName()).isEqualTo("Alice");
    }
}
