package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Year;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

/** Correos con código (RN-02 y RN-04): textos del recorrido 2026-11-02, modo claro y oscuro. */
class EmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService service = new EmailService(mailSender);

    @BeforeEach
    void setUp() {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage(Session.getInstance(new Properties())));
        ReflectionTestUtils.setField(service, "fromEmail", "nexus@example.com");
        ReflectionTestUtils.setField(service, "appName", "Nexus");
        ReflectionTestUtils.setField(service, "appLogoUrl", "");
    }

    private MimeMessage sent() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        try {
            message.saveChanges(); // fija los tipos MIME de cada parte, como al enviarlo
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException(e);
        }
        return message;
    }

    private static String html(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) {
            return part.isMimeType("text/html") ? text : "";
        }
        StringBuilder all = new StringBuilder();
        Multipart multipart = (Multipart) content;
        for (int i = 0; i < multipart.getCount(); i++) {
            all.append(html(multipart.getBodyPart(i)));
        }
        return all.toString();
    }

    @Test
    @DisplayName("should send the verification code with the new copy and both color schemes")
    void shouldSendTheVerificationCodeWithTheNewCopyAndBothColorSchemes() throws Exception {
        service.sendVerificationEmail("ana@example.com", "482913");

        MimeMessage message = sent();
        String body = html(message);
        assertThat(message.getSubject()).isEqualTo("Tu código para comenzar en Nexus");
        assertThat(body)
                .contains("482913")
                .contains("¡Te damos la bienvenida a Nexus!")
                .contains("Este es tu código de confirmación:")
                .contains("Ingrésalo en la app para activar tu cuenta y comenzar.")
                .contains("15 minutos")
                .contains("Si no solicitaste este código, puedes ignorar este mensaje.")
                .contains("Nexus © " + Year.now().getValue())
                .contains("name=\"color-scheme\" content=\"light dark\"")
                .contains("@media (prefers-color-scheme: dark)")
                .doesNotContain("Bienvenido")
                .doesNotContain("por favor no responder");
    }

    @Test
    @DisplayName("should send the password reset code with the same layout")
    void shouldSendThePasswordResetCodeWithTheSameLayout() throws Exception {
        service.sendPasswordResetEmail("ana@example.com", "551200");

        MimeMessage message = sent();
        String body = html(message);
        assertThat(message.getSubject()).isEqualTo("Tu código para restablecer tu contraseña en Nexus");
        assertThat(body)
                .contains("551200")
                .contains("1 hora")
                .contains("Si no solicitaste este código, puedes ignorar este mensaje.")
                .contains("@media (prefers-color-scheme: dark)");
    }
}
