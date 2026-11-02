package com.nexus.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.name}")
    private String appName;

    // URL del logo para usar en los correos (opcional)
    @Value("${app.logo.url:}")
    private String appLogoUrl;

    private static final String IGNORE_NOTE = "Si no solicitaste este código, puedes ignorar este mensaje.";

    /**
     * Envía un código de verificación de email (RN-02: validez 15 minutos)
     * @param toEmail Email del destinatario
     * @param verificationCode Código de 6 dígitos
     */
    public void sendVerificationEmail(String toEmail, String verificationCode) {
        String html = buildCodeEmailHtml(
                "¡Te damos la bienvenida a " + appName + "!",
                "Este es tu código de confirmación:",
                verificationCode,
                "15 minutos",
                "Ingrésalo en la app para activar tu cuenta y comenzar.",
                IGNORE_NOTE);
        send(toEmail, "Tu código para comenzar en " + appName, html, "verificación");
    }

    /**
     * Envía un código de recuperación de contraseña (RN-04: validez 1 hora)
     * @param toEmail Email del destinatario
     * @param resetCode Código de 6 dígitos
     */
    public void sendPasswordResetEmail(String toEmail, String resetCode) {
        String html = buildCodeEmailHtml(
                "Restablece tu contraseña",
                "Recibimos una solicitud para restablecer tu contraseña. Este es tu código:",
                resetCode,
                "1 hora",
                "Ingrésalo en la app para elegir una contraseña nueva.",
                IGNORE_NOTE + " Tu contraseña no cambiará.");
        send(toEmail, "Tu código para restablecer tu contraseña en " + appName, html, "recuperación");
    }

    private void send(String toEmail, String subject, String html, String kind) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            attachInlineLogo(helper);
            mailSender.send(message);
            log.info("Email de {} enviado exitosamente a: {}", kind, toEmail);
        } catch (MessagingException e) {
            log.error("Error al enviar email de {} a: {}", kind, toEmail, e);
            throw new RuntimeException("No se pudo enviar el email de " + kind, e);
        }
    }

    // Adjuntar el logo inline si se usa un CID (mejor compatibilidad que data URIs)
    private void attachInlineLogo(MimeMessageHelper helper) {
        if (appLogoUrl == null || !appLogoUrl.startsWith("cid:")) {
            return;
        }
        String cid = appLogoUrl.substring(4);
        try {
            // PNG primero (mejor compatibilidad con clientes de email); SVG si no existe
            ClassPathResource logoRes = new ClassPathResource("static/images/app-logo.png");
            if (logoRes.exists()) {
                helper.addInline(cid, logoRes, "image/png");
                return;
            }
            logoRes = new ClassPathResource("static/images/app-logo.svg");
            if (logoRes.exists()) {
                helper.addInline(cid, logoRes, "image/svg+xml");
            } else {
                log.warn("Logo inline no encontrado en classpath");
            }
        } catch (Exception ex) {
            log.warn("No se pudo adjuntar el logo inline: {}", ex.getMessage());
        }
    }

    /**
     * Plantilla de los dos correos con código. Tablas y estilos en línea para Gmail y Outlook; el modo
     * oscuro lo toman los clientes que leen prefers-color-scheme (Apple Mail, Outlook, Gmail web).
     * Colores de la app (constants/colors.ts): rosa #FF4F81, rosa fuerte #D81B60, morado #8A2BE2.
     */
    private String buildCodeEmailHtml(String title, String intro, String code, String validity,
                                      String instruction, String note) {
        String logo = (appLogoUrl != null && !appLogoUrl.isBlank())
                ? "<img src=\"" + appLogoUrl + "\" alt=\"" + appName + "\" height=\"48\" style=\"display:block;margin:0 auto 12px;height:48px;width:auto;border:0;\">"
                : "";
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <meta name="color-scheme" content="light dark">
            <meta name="supported-color-schemes" content="light dark">
            <title>%1$s</title>
            <style>
              :root { color-scheme: light dark; supported-color-schemes: light dark; }
              @media (prefers-color-scheme: dark) {
                .bg { background-color: #15111C !important; }
                .card { background-color: #1F1A29 !important; border-color: #3A3247 !important; }
                .text { color: #F5F3F8 !important; }
                .muted { color: #C9C3D3 !important; }
                .code { background-color: #2A2335 !important; border-color: #4A3F5C !important; color: #FF8FAE !important; }
                .accent { color: #FF8FAE !important; }
                .rule { border-color: #3A3247 !important; }
              }
              [data-ogsc] .card { background-color: #1F1A29 !important; }
              [data-ogsc] .text { color: #F5F3F8 !important; }
              [data-ogsc] .muted { color: #C9C3D3 !important; }
              [data-ogsc] .code { background-color: #2A2335 !important; color: #FF8FAE !important; }
              @media only screen and (max-width: 520px) {
                .pad { padding: 28px 20px !important; }
                .code { font-size: 30px !important; letter-spacing: 6px !important; }
              }
            </style>
            </head>
            <body class="bg" style="margin:0;padding:0;background-color:#FAF9FC;">
            <table role="presentation" class="bg" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#FAF9FC;">
            <tr><td align="center" style="padding:32px 16px;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;">
                <tr><td style="height:6px;border-radius:6px 6px 0 0;background-color:#FF4F81;background-image:linear-gradient(90deg,#FF4F81,#8A2BE2);font-size:0;line-height:0;">&nbsp;</td></tr>
                <tr><td class="card pad" align="center" style="background-color:#FFFFFF;border:1px solid #E4E0EB;border-top:0;border-radius:0 0 20px 20px;padding:40px 36px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;">
                  %2$s
                  <p class="accent" style="margin:0 0 8px;font-size:15px;font-weight:700;letter-spacing:0.5px;color:#D81B60;">%3$s</p>
                  <h1 class="text" style="margin:0 0 16px;font-size:24px;line-height:1.3;font-weight:700;color:#1F1A29;">%1$s</h1>
                  <p class="muted" style="margin:0 0 24px;font-size:16px;line-height:1.6;color:#5B5566;">%4$s</p>
                  <div class="code" style="display:inline-block;margin:0 0 16px;padding:18px 28px;border:1px solid #FFC9D9;border-radius:14px;background-color:#FFF0F5;color:#D81B60;font-size:36px;font-weight:700;letter-spacing:10px;font-family:'SF Mono',Menlo,Consolas,monospace;">%5$s</div>
                  <p class="muted" style="margin:0 0 24px;font-size:14px;color:#5B5566;">Este código es válido por <strong class="accent" style="color:#D81B60;">%6$s</strong>.</p>
                  <p class="text" style="margin:0 0 28px;font-size:16px;line-height:1.6;color:#1F1A29;">%7$s</p>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"><tr><td class="rule" style="border-top:1px solid #E4E0EB;padding-top:20px;">
                    <p class="muted" style="margin:0 0 8px;font-size:13px;line-height:1.5;color:#6F697A;">%8$s</p>
                    <p class="accent" style="margin:0;font-size:13px;font-weight:600;color:#D81B60;">%3$s © %9$s</p>
                  </td></tr></table>
                </td></tr>
              </table>
            </td></tr>
            </table>
            </body>
            </html>
            """.formatted(title, logo, appName, intro, code, validity, instruction, note, Year.now().getValue());
    }
}
