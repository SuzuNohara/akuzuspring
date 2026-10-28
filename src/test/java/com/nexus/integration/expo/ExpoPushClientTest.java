package com.nexus.integration.expo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nexus.config.PushProperties;
import com.nexus.integration.expo.ExpoPushClient.PushMessage;
import com.nexus.integration.expo.ExpoPushClient.PushResult;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Envio a Expo y lectura de su ticket ([push-avisos] U1). */
class ExpoPushClientTest {

    private static final String URL = "http://expo.test/push";

    private static final PushMessage MESSAGE = new PushMessage(
            "ExponentPushToken[abc]", "Evento confirmado", "Tu pareja acepto \"Cena\".",
            Map.of("type", "EVENT_CONFIRMED", "route", "/(events)/notifications"));

    private MockRestServiceServer server;
    private ExpoPushClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ExpoPushClient(builder, new PushProperties(true, URI.create(URL), Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName("should post the message with the default channel, sound, high priority and data")
    void shouldPostTheMessageWithTheDefaultChannelSoundHighPriorityAndData() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.to").value("ExponentPushToken[abc]"))
                .andExpect(jsonPath("$.title").value("Evento confirmado"))
                .andExpect(jsonPath("$.body").value("Tu pareja acepto \"Cena\"."))
                .andExpect(jsonPath("$.channelId").value("default"))
                .andExpect(jsonPath("$.sound").value("default"))
                .andExpect(jsonPath("$.priority").value("high"))
                .andExpect(jsonPath("$.data.type").value("EVENT_CONFIRMED"))
                .andExpect(jsonPath("$.data.route").value("/(events)/notifications"))
                .andRespond(withSuccess("{\"data\":{\"status\":\"ok\",\"id\":\"t1\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.send(MESSAGE)).isEqualTo(PushResult.SENT);
        server.verify();
    }

    @Test
    @DisplayName("should read the first ticket when Expo answers with a list")
    void shouldReadTheFirstTicketWhenExpoAnswersWithAList() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"data\":[{\"status\":\"ok\",\"id\":\"t1\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.send(MESSAGE)).isEqualTo(PushResult.SENT);
    }

    @Test
    @DisplayName("should report an invalid token when the device is not registered")
    void shouldReportAnInvalidTokenWhenTheDeviceIsNotRegistered() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"data\":{\"status\":\"error\",\"message\":\"not a valid token\","
                                + "\"details\":{\"error\":\"DeviceNotRegistered\"}}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.send(MESSAGE)).isEqualTo(PushResult.TOKEN_INVALID);
    }

    @Test
    @DisplayName("should fail without throwing when Expo rejects the message for another reason")
    void shouldFailWithoutThrowingWhenExpoRejectsTheMessageForAnotherReason() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"data\":{\"status\":\"error\",\"message\":\"too big\","
                                + "\"details\":{\"error\":\"MessageTooBig\"}}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.send(MESSAGE)).isEqualTo(PushResult.FAILED);
    }

    @Test
    @DisplayName("should fail without throwing when Expo answers 500")
    void shouldFailWithoutThrowingWhenExpoAnswers500() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThat(client.send(MESSAGE)).isEqualTo(PushResult.FAILED);
    }

    @Test
    @DisplayName("should fail without throwing when Expo times out")
    void shouldFailWithoutThrowingWhenExpoTimesOut() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        assertThat(client.send(MESSAGE)).isEqualTo(PushResult.FAILED);
    }

    @Test
    @DisplayName("should fail without throwing when Expo answers without a ticket")
    void shouldFailWithoutThrowingWhenExpoAnswersWithoutATicket() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"errors\":[{\"code\":\"VALIDATION_ERROR\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.send(MESSAGE)).isEqualTo(PushResult.FAILED);
    }
}
