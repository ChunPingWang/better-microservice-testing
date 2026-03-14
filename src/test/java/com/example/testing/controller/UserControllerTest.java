package com.example.testing.controller;

import com.example.testing.dto.UserDto;
import com.example.testing.entity.User;
import com.example.testing.exception.GlobalExceptionHandler;
import com.example.testing.exception.UserNotFoundException;
import com.example.testing.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest 切片測試範例 — 測試 Controller 層
 *
 * 特點：
 * - 只載入 Web 層相關的 Bean（Controller, Filter, ControllerAdvice）
 * - 不啟動完整 Spring Context，不啟動 Tomcat
 * - 使用 MockMvc 模擬 HTTP 請求
 * - Service 層用 @MockitoBean 模擬（Spring Boot 3.4+ 取代 @MockBean）
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

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
    @DisplayName("POST /api/users - 無效資料應回傳 400 (Validation)")
    void createUser_withInvalidData_shouldReturn400() throws Exception {
        UserDto dto = new UserDto("", "not-an-email");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.email").exists());
    }

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

    @Test
    @DisplayName("DELETE /api/users/{id} - 應回傳 204")
    void deleteUser_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/users/search?name=Alice - 應回傳匹配的使用者")
    void searchUsers_shouldReturnMatchingUsers() throws Exception {
        User user1 = new User("Alice Wang", "alice@example.com");
        user1.setId(1L);
        when(userService.searchByName("Alice")).thenReturn(Arrays.asList(user1));

        mockMvc.perform(get("/api/users/search").param("name", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Alice Wang")));
    }
}
