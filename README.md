# CDA Backend - Universidad del Valle Sede Palmira

Backend en **Spring Boot 3** para el sistema **CDA (Carga Docente y Asignación Académica)** de la Universidad del Valle, Sede Palmira.

---

## 🚀 Requisitos Previos

- **Java JDK 17** o superior.
- **PostgreSQL 14+** con base de datos configurada (`cda_db`).
- **Gradle 8.x / 9.x** (incluido en el repositorio mediante `./gradlew`).
- Credenciales de cliente de Google OAuth2 para el inicio de sesión institucional (`@correounivalle.edu.co`).

---

## 🛠️ Configuración y Ejecución

### 1. Variables de Entorno / `application.yaml`
Configura las credenciales de base de datos y Google en `src/main/resources/application.yaml` o mediante variables de entorno:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cda_db
    username: postgres
    password: tu_password
```

### 2. Comandos Principales con Gradle

```bash
# Compilar el proyecto
./gradlew build -x test

# Ejecutar las pruebas unitarias e integrales
./gradlew test

# Iniciar la aplicación
./gradlew bootRun
```

La aplicación se ejecutará en: `http://localhost:8080`.

---

## 📚 Documentación de API (Swagger)

Una vez iniciada la aplicación, la documentación interactiva OpenAPI 3 está disponible en:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

---

## 📂 Documentación Completa del Sistema

Para consultar la descripción detallada de la arquitectura, mapa de paquetes, migraciones de base de datos y catálogo de servicios, consulta:
- 📖 [Documentación Técnica Integral del Sistema](docs/DOCUMENTACION_SISTEMA.md)
