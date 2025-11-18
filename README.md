# Nexus Backend

Backend API para la aplicación Nexus - Calendario Social

## Tecnologías

- Java 17
- Spring Boot 3.2.0
- Spring Data JPA
- Spring Security
- MySQL 8.0
- Maven
- Lombok
- JWT (JSON Web Tokens)

## Configuración

### Base de Datos

1. Asegúrate de tener MySQL instalado y ejecutándose
2. Crea la base de datos ejecutando el script SQL proporcionado
3. Actualiza las credenciales en `application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/nexus
spring.datasource.username=tu_usuario
spring.datasource.password=tu_contraseña
```

### Configuración de Email (Opcional)

Para habilitar el envío de emails de verificación, configura:

```properties
spring.mail.username=tu-email@gmail.com
spring.mail.password=tu-app-password
```

## Ejecución

### Con Maven

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

### Con IDE

Importa el proyecto como proyecto Maven y ejecuta la clase `NexusApplication.java`

## API Endpoints

### Autenticación

#### Registro de Usuario
```
POST /api/auth/register
Content-Type: application/json

{
  "email": "usuario@ejemplo.com",
  "password": "Password123",
  "confirmPassword": "Password123",
  "displayName": "Juan Pérez",
  "nickname": "juanp",
  "birthDate": "1995-05-15"
}
```

**Respuesta Exitosa (201 Created):**
```json
{
  "userId": 1,
  "email": "usuario@ejemplo.com",
  "displayName": "Juan Pérez",
  "nickname": "juanp",
  "linkCode": "AB7K-M9P2",
  "emailConfirmed": false,
  "createdAt": "2024-11-17T10:30:00Z",
  "message": "Registro exitoso. Por favor verifica tu email."
}
```

**Errores Comunes:**
- `400 Bad Request`: Email ya registrado, contraseñas no coinciden, edad menor a 13 años
- `400 Bad Request`: Errores de validación (formato de email, longitud de contraseña, etc.)

#### Health Check
```
GET /api/auth/health
```

## Validaciones

### Registro de Usuario

- **Email**: Requerido, formato válido, único en el sistema
- **Contraseña**: 
  - Mínimo 8 caracteres
  - Al menos una letra mayúscula
  - Al menos una letra minúscula
  - Al menos un número
- **Nombre para mostrar**: Requerido, entre 2 y 255 caracteres
- **Fecha de nacimiento**: Requerida, debe ser mayor a 13 años

## Características Implementadas

### Registro de Usuarios
- ✅ Validación completa de datos
- ✅ Encriptación de contraseñas con BCrypt
- ✅ Generación automática de Link Code único (formato: XXXX-XXXX)
- ✅ Validación de edad mínima (13 años)
- ✅ Verificación de email único
- ✅ Creación de token de verificación de email
- ✅ Manejo de errores con mensajes descriptivos

### Seguridad
- ✅ Spring Security configurado
- ✅ CORS habilitado para desarrollo
- ✅ Endpoints públicos para autenticación
- ✅ Sesiones stateless (preparado para JWT)

### Base de Datos
- ✅ Entidades JPA mapeadas
- ✅ Relaciones entre User, UserProfile y VerificationToken
- ✅ Soft delete preparado (campo deleted_at)
- ✅ Auditoría automática (created_at, updated_at)

## Próximos Pasos

1. **Login de Usuario**
   - Endpoint POST /api/auth/login
   - Generación de JWT tokens
   - Refresh token

2. **Verificación de Email**
   - Endpoint POST /api/auth/verify-email
   - Envío de emails con JavaMailSender

3. **Recuperación de Contraseña**
   - Endpoint POST /api/auth/forgot-password
   - Endpoint POST /api/auth/reset-password

4. **Link Code (Vinculación de Usuarios)**
   - Endpoint POST /api/link/connect
   - Endpoint GET /api/link/status
   - Endpoint POST /api/link/disconnect

5. **Eventos y Calendario**
   - CRUD de eventos
   - Sincronización con calendarios externos
   - Sistema de aprobaciones

## Estructura del Proyecto

```
backend/
├── src/
│   └── main/
│       ├── java/com/nexus/
│       │   ├── config/          # Configuraciones (Security, CORS)
│       │   ├── controller/      # Controladores REST
│       │   ├── dto/             # Data Transfer Objects
│       │   ├── entity/          # Entidades JPA
│       │   ├── exception/       # Excepciones personalizadas
│       │   ├── repository/      # Repositorios JPA
│       │   ├── service/         # Lógica de negocio
│       │   ├── util/            # Utilidades
│       │   └── NexusApplication.java
│       └── resources/
│           └── application.properties
└── pom.xml
```

## Puerto

La aplicación se ejecuta por defecto en:
```
http://localhost:8080/api
```

## Logs

Los logs se configuran en `application.properties` con diferentes niveles:
- Root: INFO
- com.nexus: DEBUG
- Spring Security: DEBUG
- Hibernate SQL: DEBUG
