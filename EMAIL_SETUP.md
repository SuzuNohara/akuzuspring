# Configuración de Email para Nexus Backend

## Configuración SMTP

Para que el sistema de verificación de email funcione, necesitas configurar las credenciales SMTP en `application.properties`.

### Opción 1: Gmail

1. Habilita la verificación en dos pasos en tu cuenta de Gmail
2. Genera una contraseña de aplicación:
   - Ve a https://myaccount.google.com/apppasswords
   - Selecciona "Mail" y "Other (Custom name)"
   - Copia la contraseña generada (16 caracteres sin espacios)

3. Actualiza `application.properties`:
```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=tu-email@gmail.com
spring.mail.password=tu-contraseña-de-aplicacion
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### Opción 2: Outlook/Hotmail

```properties
spring.mail.host=smtp-mail.outlook.com
spring.mail.port=587
spring.mail.username=tu-email@outlook.com
spring.mail.password=tu-contraseña
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### Opción 3: SendGrid (Recomendado para producción)

1. Crea una cuenta en https://sendgrid.com
2. Genera un API Key en Settings > API Keys
3. Actualiza `application.properties`:
```properties
spring.mail.host=smtp.sendgrid.net
spring.mail.port=587
spring.mail.username=apikey
spring.mail.password=TU_API_KEY_DE_SENDGRID
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

## Variables de Entorno (Recomendado para producción)

En lugar de hardcodear las credenciales, usa variables de entorno:

```properties
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
```

Luego configura las variables en tu sistema:

**Windows (PowerShell):**
```powershell
$env:MAIL_USERNAME="tu-email@gmail.com"
$env:MAIL_PASSWORD="tu-contraseña"
```

**Linux/Mac:**
```bash
export MAIL_USERNAME=tu-email@gmail.com
export MAIL_PASSWORD=tu-contraseña
```

## Funcionalidades del EmailService

### 1. Email de Verificación (RN-02)
- Se envía automáticamente al registrarse
- Contiene un código de 6 dígitos
- Validez: 1 hora
- Plantilla HTML con diseño del app (gradiente rosa-morado)

### 2. Email de Recuperación de Contraseña (RN-02)
- Se envía cuando el usuario solicita restablecer contraseña
- Contiene un código de 6 dígitos
- Validez: 1 hora
- Plantilla HTML con advertencias de seguridad

## Testing

Para probar el envío de emails en desarrollo sin configurar SMTP real:

1. **Opción 1: MailHog** (Servidor SMTP local para testing)
```bash
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
```

Actualiza `application.properties`:
```properties
spring.mail.host=localhost
spring.mail.port=1025
spring.mail.username=test
spring.mail.password=test
spring.mail.properties.mail.smtp.auth=false
spring.mail.properties.mail.smtp.starttls.enable=false
```

Accede a la interfaz web en: http://localhost:8025

2. **Opción 2: Mailtrap** (Servicio online gratuito)
- Crea cuenta en https://mailtrap.io
- Copia las credenciales SMTP de tu inbox
- Los emails se capturan en la interfaz web

## Solución de Problemas

### Error: Authentication failed
- Verifica que las credenciales sean correctas
- Si usas Gmail, asegúrate de usar una contraseña de aplicación, no tu contraseña normal
- Revisa que la verificación en dos pasos esté habilitada

### Error: Could not connect to SMTP host
- Verifica tu conexión a internet
- Algunos ISPs bloquean el puerto 587, intenta con 465 + SSL
- Verifica que el firewall no esté bloqueando la conexión

### Los emails llegan a spam
- Configura SPF, DKIM y DMARC records en tu dominio
- Usa un servicio dedicado como SendGrid o Amazon SES
- Incluye un enlace de "Unsubscribe"

## Seguridad

⚠️ **NUNCA** commits credenciales SMTP en el repositorio

✅ Usa variables de entorno o servicios de secrets management (AWS Secrets Manager, Azure Key Vault, etc.)

✅ En producción, usa servicios especializados (SendGrid, Amazon SES, Mailgun) con límites de envío

✅ Implementa rate limiting para prevenir abuso del sistema de emails
