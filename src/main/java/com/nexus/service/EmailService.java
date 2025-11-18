package com.nexus.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
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
    
    /**
     * Envía un código de verificación de email (RN-02: validez 1 hora)
     * @param toEmail Email del destinatario
     * @param verificationCode Código de 6 dígitos
     */
    public void sendVerificationEmail(String toEmail, String verificationCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Verifica tu correo electrónico");
            
            String htmlContent = buildVerificationEmailHtml(verificationCode);
            helper.setText(htmlContent, true);

            // Adjuntar el logo inline si se usa un CID (mejor compatibilidad que data URIs)
            if (appLogoUrl != null && appLogoUrl.startsWith("cid:")) {
                String cid = appLogoUrl.substring(4);
                try {
                    // Intenta primero con PNG (mejor compatibilidad con clientes de email)
                    ClassPathResource logoRes = new ClassPathResource("static/images/app-logo.png");
                    if (logoRes.exists()) {
                        helper.addInline(cid, logoRes, "image/png");
                    } else {
                        // Fallback a SVG si no existe PNG
                        logoRes = new ClassPathResource("static/images/app-logo.svg");
                        if (logoRes.exists()) {
                            helper.addInline(cid, logoRes, "image/svg+xml");
                        } else {
                            log.warn("Logo inline no encontrado en classpath");
                        }
                    }
                } catch (Exception ex) {
                    log.warn("No se pudo adjuntar el logo inline: {}", ex.getMessage());
                }
            }
            
            mailSender.send(message);
            log.info("Email de verificación enviado exitosamente a: {}", toEmail);
            
        } catch (MessagingException e) {
            log.error("Error al enviar email de verificación a: {}", toEmail, e);
            throw new RuntimeException("No se pudo enviar el email de verificación", e);
        }
    }
    
    /**
     * Envía un código de recuperación de contraseña (RN-02: validez 1 hora)
     * @param toEmail Email del destinatario
     * @param resetCode Código de 6 dígitos
     */
    public void sendPasswordResetEmail(String toEmail, String resetCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Recupera tu contraseña");
            
            String htmlContent = buildPasswordResetEmailHtml(resetCode);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Email de recuperación de contraseña enviado exitosamente a: {}", toEmail);
            
        } catch (MessagingException e) {
            log.error("Error al enviar email de recuperación a: {}", toEmail, e);
            throw new RuntimeException("No se pudo enviar el email de recuperación", e);
        }
    }
    
    private String buildVerificationEmailHtml(String code) {
        // Construir bloque de logo: si hay URL configurada, usar imagen; si no, usar emoji de festejo
        String logoBlock = (appLogoUrl != null && !appLogoUrl.isBlank())
            ? ("<img class='logo-img' src='" + appLogoUrl + "' alt='" + appName + " logo' />")
            : "<div class='logo'>&#127881;</div>";

        String html = "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "<meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
            "<style>" +
            "* { margin: 0; padding: 0; box-sizing: border-box; }" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; background: linear-gradient(135deg, #FF4F81 0%, #8A2BE2 100%); padding: 40px 20px; min-height: 100vh; }" +
            ".email-wrapper { max-width: 600px; margin: 0 auto; }" +
            ".email-header { text-align: center; margin-bottom: 30px; }" +
            ".logo { font-size: 48px; margin-bottom: 10px; }" +
            ".logo-img { height: 56px; width: auto; margin-bottom: 10px; border: 0; outline: none; text-decoration: none; }" +
            ".app-name { color: white; font-size: 32px; font-weight: bold; text-shadow: 0 2px 4px rgba(0,0,0,0.2); }" +
            ".email-content { background: white; border-radius: 24px; padding: 50px 40px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); }" +
            ".greeting { font-size: 28px; font-weight: bold; color: #333; margin-bottom: 20px; text-align: center; }" +
            ".message { font-size: 16px; color: #666; text-align: center; margin-bottom: 30px; line-height: 1.8; }" +
            ".code-section { background: linear-gradient(135deg, #FFF5F7 0%, #F3E5F5 100%); border-radius: 16px; padding: 30px; margin: 30px 0; text-align: center; }" +
            ".code-label { font-size: 14px; color: #666; margin-bottom: 15px; font-weight: 500; text-transform: uppercase; letter-spacing: 1px; }" +
            ".code-box { background: white; color: #FF4F81; font-size: 40px; font-weight: bold; letter-spacing: 12px; padding: 25px 30px; border-radius: 12px; margin: 0 auto; display: inline-block; box-shadow: 0 4px 20px rgba(255, 79, 129, 0.2); border: 2px solid rgba(255, 79, 129, 0.1); }" +
            ".validity { margin-top: 20px; font-size: 14px; color: #666; }" +
            ".validity strong { color: #FF4F81; font-weight: 600; }" +
            ".divider { height: 1px; background: linear-gradient(90deg, transparent, #E0E0E0, transparent); margin: 30px 0; }" +
            ".info-box { background: #F8F9FA; border-left: 4px solid #FF4F81; border-radius: 8px; padding: 20px; margin: 25px 0; }" +
            ".info-box p { font-size: 14px; color: #555; margin: 0; line-height: 1.6; }" +
            ".footer { margin-top: 40px; padding-top: 30px; border-top: 1px solid #E0E0E0; text-align: center; }" +
            ".footer p { font-size: 13px; color: #999; margin: 8px 0; }" +
            ".footer-brand { font-weight: 600; color: #FF4F81; }" +
            "@media only screen and (max-width: 600px) { " +
            "body { padding: 20px 10px; } " +
            ".email-content { padding: 30px 20px; } " +
            ".code-box { font-size: 32px; letter-spacing: 8px; padding: 20px; } " +
            ".greeting { font-size: 24px; } " +
            "}" +
            "</style>" +
            "</head>" +
            "<body>" +
            "<div class='email-wrapper'>" +
            "<div class='email-header'>" +
            logoBlock +
            "<div class='app-name'>&#161;Bienvenido a " + appName + "!</div>" +
            "</div>" +
            "<div class='email-content'>" +
            "<div class='greeting'>Tu c&oacute;digo de verificaci&oacute;n es:</div>" +
            "<div class='code-section'>" +
            "<div class='code-label'>C&oacute;digo de Verificaci&oacute;n</div>" +
            "<div class='code-box'>" + code + "</div>" +
            "<div class='validity'>Este c&oacute;digo es v&aacute;lido por <strong>1 hora</strong></div>" +
            "</div>" +
            "<div class='divider'></div>" +
            "<div class='message'>" +
            "Ingresa este c&oacute;digo en la aplicaci&oacute;n para completar tu registro y comenzar a usar " + appName + "." +
            "</div>" +
            "<div class='info-box'>" +
            "<p><strong>&#9888; Importante:</strong> Si no solicitaste este c&oacute;digo, puedes ignorar este mensaje.</p>" +
            "</div>" +
            "<div class='footer'>" +
            "<p>Este es un correo autom&aacute;tico, por favor no responder.</p>" +
            "<p class='footer-brand'>" + appName + " &copy; 2025</p>" +
            "</div>" +
            "</div>" +
            "</div>" +
            "</body>" +
            "</html>";
        return html;
    }
    
    private String buildPasswordResetEmailHtml(String code) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    .container {
                        background: linear-gradient(135deg, #FF4F81 0%, #8A2BE2 100%);
                        border-radius: 16px;
                        padding: 40px;
                        text-align: center;
                        color: white;
                    }
                    .code-box {
                        background: white;
                        color: #FF4F81;
                        font-size: 32px;
                        font-weight: bold;
                        letter-spacing: 8px;
                        padding: 20px;
                        border-radius: 12px;
                        margin: 30px 0;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                    }
                    .warning {
                        background: rgba(255,255,255,0.2);
                        padding: 15px;
                        border-radius: 8px;
                        margin-top: 20px;
                    }
                    .footer {
                        margin-top: 30px;
                        font-size: 14px;
                        color: rgba(255,255,255,0.8);
                    }
                    h1 {
                        margin: 0 0 20px 0;
                        font-size: 28px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>🔒 Recuperación de contraseña</h1>
                    <p>Recibimos una solicitud para restablecer tu contraseña.</p>
                    <p>Tu código de verificación es:</p>
                    <div class="code-box">""" + code + """
</div>
                    <p>Este código es válido por <strong>1 hora</strong>.</p>
                    <div class="warning">
                        <strong>⚠️ Importante:</strong> Si no solicitaste este cambio, 
                        ignora este mensaje y tu contraseña permanecerá sin cambios.
                    </div>
                    <div class="footer">
                        <p>Este es un email automático, por favor no respondas este mensaje.</p>
                    </div>
                </div>
            </body>
            </html>
            """;
    }
}
