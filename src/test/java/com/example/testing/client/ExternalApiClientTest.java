package com.example.testing.client;

import com.example.testing.dto.UserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @RestClientTest 切片測試範例 — 測試 REST Client（外部 API 呼叫）
 *
 * 特點：
 * - 只載入指定的 Client 元件 + MockRestServiceServer
 * - 不啟動完整 Spring Context
 * - 使用 MockRestServiceServer 模擬外部 API 回應
 * - 適合測試 RestTemplate / RestClient 的呼叫邏輯
 */
@RestClientTest(ExternalApiClient.class)
class ExternalApiClientTest {

    @Autowired
    private ExternalApiClient externalApiClient;

    @Autowired
    private MockRestServiceServer mockServer;

    @Test
    @DisplayName("fetchUserFromExternalApi - 應正確解析外部 API 回應")
    void fetchUser_shouldParseResponse() {
        String responseJson = """
                {
                    "name": "External User",
                    "email": "external@example.com"
                }
                """;

        mockServer.expect(requestTo("https://api.example.com/users/123"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        UserDto result = externalApiClient.fetchUserFromExternalApi("123");

        assertThat(result.getName()).isEqualTo("External User");
        assertThat(result.getEmail()).isEqualTo("external@example.com");
        mockServer.verify();
    }
}
