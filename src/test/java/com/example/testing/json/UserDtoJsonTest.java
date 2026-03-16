package com.example.testing.json;

import com.example.testing.dto.UserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @JsonTest 切片測試範例 — 測試 JSON 序列化/反序列化
 *
 * 特點：
 * - 只載入 JSON 相關的 Bean（ObjectMapper, JacksonTester）
 * - 不啟動 Web 層、不啟動資料庫
 * - 適合驗證 DTO 的 JSON 格式是否正確
 * - 可測試 @JsonProperty、@JsonIgnore、@JsonFormat 等註解
 */
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
