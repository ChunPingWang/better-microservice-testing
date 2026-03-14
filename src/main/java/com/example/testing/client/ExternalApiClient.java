package com.example.testing.client;

import com.example.testing.dto.UserDto;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExternalApiClient {

    private final RestClient restClient;

    public ExternalApiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.example.com")
                .build();
    }

    public UserDto fetchUserFromExternalApi(String userId) {
        return restClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .body(UserDto.class);
    }
}
