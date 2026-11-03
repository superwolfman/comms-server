package io.unicomms;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:unicomms-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "comms.registration-enabled=true",
    "comms.allowed-origins=http://localhost:5173"
})
class ChatFlowIntegrationTest {
    @Autowired TestRestTemplate http;

    @Test void registrationCreateSendIdempotencyAndHistoryAreDurable() {
        JsonNode alice = register("alice"); JsonNode bob = register("bob"); JsonNode mallory = register("mallory");
        String aliceToken = alice.path("token").asText(); String bobToken = bob.path("token").asText();
        String aliceId = alice.path("user").path("id").asText(); String bobId = bob.path("user").path("id").asText();
        JsonNode conversation = post("/api/v1/conversations", aliceToken, Map.of("title", "integration", "memberIds", new String[]{bobId}));
        String conversationId = conversation.path("id").asText();
        Map<String, Object> body = Map.of("clientMessageId", "stable-message-01", "kind", "text", "payload", Map.of("text", "hello"));
        JsonNode sent = post("/api/v1/conversations/" + conversationId + "/messages", aliceToken, body);
        JsonNode duplicate = post("/api/v1/conversations/" + conversationId + "/messages", aliceToken, body);
        assertThat(sent.path("id").asText()).isEqualTo(duplicate.path("id").asText());
        assertThat(sent.path("seq").asLong()).isEqualTo(1);
        Map<String, Object> conflictBody = Map.of("clientMessageId", "stable-message-01", "kind", "text", "payload", Map.of("text", "different body"));
        ResponseEntity<JsonNode> conflict = http.exchange("/api/v1/conversations/" + conversationId + "/messages", HttpMethod.POST, entity(aliceToken, conflictBody), JsonNode.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<JsonNode> history = http.exchange("/api/v1/conversations/" + conversationId + "/messages?after=0&limit=20", HttpMethod.GET, bearer(aliceToken), JsonNode.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody().path("items").size()).isEqualTo(1);
        assertThat(history.getBody().path("items").get(0).path("payload").path("text").asText()).isEqualTo("hello");

        ResponseEntity<JsonNode> memberHistory = http.exchange("/api/v1/conversations/" + conversationId + "/messages?after=0", HttpMethod.GET, bearer(bobToken), JsonNode.class);
        assertThat(memberHistory.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> forbidden = http.exchange("/api/v1/conversations/" + conversationId + "/messages?after=0", HttpMethod.GET, bearer(mallory.path("token").asText()), JsonNode.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private JsonNode register(String username) {
        return post("/api/v1/auth/register", null, Map.of("tenant", "test", "username", username, "password", "correct-horse-123", "displayName", username));
    }
    private JsonNode post(String path, String token, Object body) {
        ResponseEntity<JsonNode> response = http.exchange(path, HttpMethod.POST, entity(token, body), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
    private HttpEntity<Object> entity(String token, Object body) {
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); if (token != null) headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }
    private HttpEntity<Void> bearer(String token) { HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token); return new HttpEntity<>(headers); }
}
