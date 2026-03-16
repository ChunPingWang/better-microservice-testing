package com.example.testing.controller;

import com.example.testing.dto.UserDto;
import com.example.testing.entity.User;
import com.example.testing.exception.UserNotFoundException;
import com.example.testing.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
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
 * - Service 層用 @MockBean 模擬
 */
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
