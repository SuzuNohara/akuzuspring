# Ejemplos de Uso de la API Nexus

## Registro de Usuario

### Registro Exitoso
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "juan@ejemplo.com",
    "password": "Password123",
    "confirmPassword": "Password123",
    "displayName": "Juan Pérez",
    "nickname": "juanp",
    "birthDate": "1995-05-15"
  }'
```

### Registro sin nickname (opcional)
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "maria@ejemplo.com",
    "password": "SecurePass456",
    "confirmPassword": "SecurePass456",
    "displayName": "María González",
    "birthDate": "1998-08-20"
  }'
```

### Verificación de Email
```bash
curl -X POST http://localhost:8080/api/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{
    "email": "juan@ejemplo.com",
    "code": "123456"
  }'
```

### Health Check
```bash
curl http://localhost:8080/api/auth/health
```

## PowerShell (Windows)

### Registro de Usuario
```powershell
$body = @{
    email = "juan@ejemplo.com"
    password = "Password123"
    confirmPassword = "Password123"
    displayName = "Juan Pérez"
    nickname = "juanp"
    birthDate = "1995-05-15"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body
```

### Verificación de Email
```powershell
$body = @{
    email = "juan@ejemplo.com"
    code = "123456"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/auth/verify-email" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body
```

### Health Check
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/auth/health" -Method Get
```

## Postman / Insomnia

### POST /api/auth/register

**URL:** `http://localhost:8080/api/auth/register`  
**Method:** POST  
**Headers:**
```
Content-Type: application/json
```

**Body (JSON):**
```json
{
  "email": "usuario@ejemplo.com",
  "password": "Password123",
  "confirmPassword": "Password123",
  "displayName": "Usuario Ejemplo",
  "nickname": "usuario123",
  "birthDate": "1995-05-15"
}
```

### POST /api/auth/verify-email

**URL:** `http://localhost:8080/api/auth/verify-email`  
**Method:** POST  
**Headers:**
```
Content-Type: application/json
```

**Body (JSON):**
```json
{
  "email": "usuario@ejemplo.com",
  "code": "123456"
}
```

## Casos de Prueba

### 1. Email Duplicado
```json
{
  "email": "juan@ejemplo.com",
  "password": "Password123",
  "confirmPassword": "Password123",
  "displayName": "Otro Juan",
  "birthDate": "1995-05-15"
}
```
**Esperado:** `400 Bad Request` - "El email ya está registrado"

### 2. Contraseñas no Coinciden
```json
{
  "email": "nuevo@ejemplo.com",
  "password": "Password123",
  "confirmPassword": "DifferentPass456",
  "displayName": "Nuevo Usuario",
  "birthDate": "1995-05-15"
}
```
**Esperado:** `400 Bad Request` - "Las contraseñas no coinciden"

### 3. Contraseña Débil
```json
{
  "email": "nuevo@ejemplo.com",
  "password": "12345678",
  "confirmPassword": "12345678",
  "displayName": "Nuevo Usuario",
  "birthDate": "1995-05-15"
}
```
**Esperado:** `400 Bad Request` - Error de validación sobre formato de contraseña

### 4. Edad Menor a 13 años
```json
{
  "email": "nino@ejemplo.com",
  "password": "Password123",
  "confirmPassword": "Password123",
  "displayName": "Niño Pequeño",
  "birthDate": "2020-01-01"
}
```
**Esperado:** `400 Bad Request` - "Debes tener al menos 13 años para registrarte"

### 5. Email Inválido
```json
{
  "email": "correo-invalido",
  "password": "Password123",
  "confirmPassword": "Password123",
  "displayName": "Usuario",
  "birthDate": "1995-05-15"
}
```
**Esperado:** `400 Bad Request` - Error de validación de formato de email

## Respuestas Esperadas

### Registro Exitoso (201 Created)
```json
{
  "userId": 1,
  "email": "juan@ejemplo.com",
  "displayName": "Juan Pérez",
  "nickname": "juanp",
  "linkCode": "AB7K-M9P2",
  "emailConfirmed": false,
  "createdAt": "2024-11-17T10:30:00.123Z",
  "message": "Registro exitoso. Por favor verifica tu email."
}
```

### Error de Validación (400 Bad Request)
```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "Los datos proporcionados no son válidos",
  "timestamp": "2024-11-17T10:30:00.123Z",
  "path": "/api/auth/register",
  "validationErrors": {
    "email": "El formato del email no es válido",
    "password": "La contraseña debe tener al menos 8 caracteres"
  }
}
```

### Error de Lógica de Negocio (400 Bad Request)
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "El email ya está registrado",
  "timestamp": "2024-11-17T10:30:00.123Z",
  "path": "/api/auth/register"
}
```
