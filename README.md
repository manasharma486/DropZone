# Dropzone

Dropzone is a self-hosted file drive built with Spring Boot. It provides a browser interface for managing files and folders, with user accounts, JWT authentication, a recycle bin, and local disk storage.

> **Release:** v1.1 · **Runtime:** Java 21 · **Database:** MariaDB

## Features

- Sign up and authenticate with bearer tokens
- Upload, download, search, sort, rename, copy, move, and delete files
- Create and manage nested folders, including folder archives
- Restore deleted files and folders from the recycle bin
- Track storage usage and enforce account quotas
- Store file content on local disk and metadata in MariaDB
- Use the included responsive web interface or call the REST API

## Requirements

- Java 21
- MariaDB 10.6 or newer
- A database named `dropzone`

Maven is not required; the repository includes the Maven Wrapper.

## Run locally

1. Create the database:

   ```sql
   CREATE DATABASE dropzone CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. Copy `.env.example` to `.env`, update the database credentials, and generate a private JWT signing key. The key must contain at least 32 bytes for the configured HMAC signing algorithm.

   ```bash
   cp .env.example .env
   ```

   Replace the `JWT_SECRET` value with the output of `openssl rand -base64 48`, then load the file into your shell before launching the app:

   ```bash
   set -a
   source .env
   set +a
   ./mvnw spring-boot:run
   ```

3. Open [http://localhost:8080](http://localhost:8080), create an account, and start uploading files.

The real `.env` file is ignored by Git. Commit `.env.example` with placeholders only; never commit database passwords or production JWT secrets.

The default storage directory is `./storage`. Keep it on a persistent disk and back it up alongside the database. Never commit database passwords or production JWT secrets.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:mariadb://localhost:3306/dropzone` | JDBC connection URL |
| `DB_USERNAME` | `root` | MariaDB username |
| `DB_PASSWORD` | empty | MariaDB password |
| `JWT_SECRET` | required | HMAC signing key, at least 32 bytes |
| `DROPZONE_STORAGE_PATH` | `./storage` | Directory for uploaded file content |

Spring Boot's `spring.jpa.hibernate.ddl-auto=update` is enabled for development convenience. Use managed schema migrations before operating production data.

## API overview

Protected endpoints expect `Authorization: Bearer <token>`.

| Area | Routes |
| --- | --- |
| Accounts | `POST /api/users/signup`, `GET /api/users/me` |
| Authentication | `POST /api/auth/login` |
| Drive | `GET /api/drive` |
| Files | `/api/files` — upload, list, search, download, copy, move, rename, delete |
| Folders | `/api/folders` — create, browse, download, copy, move, rename, delete, restore |
| Recycle bin | `/api/bin` — list, restore, empty |

The `postman/` directory contains request definitions for several API operations. The endpoint controllers are the source of truth for request fields and response shapes.

## Build and tests

```bash
./mvnw clean package
./mvnw test
```

Tests use the project's Spring Boot test dependencies. Configure any required test database settings through the same environment variables used by the application.

## Project structure

```text
src/main/java/       Spring Boot API and application services
src/main/resources/  Application configuration and browser UI
src/test/java/      Unit and integration tests
postman/             API request definitions
```

## Current limitations

- File bytes are stored on the local filesystem; a single instance must share access to that storage path.
- Database schema updates use Hibernate's `update` mode; there are no versioned migrations yet.
- The application is not containerized in this release.
- Configure CORS, database access, TLS, backups, and secret management for the environment where you deploy it.

## Future scope

- Container images and a Compose development stack
- Versioned database migrations and deployment profiles
- S3-compatible object storage and storage-provider abstraction
- Resumable uploads, file version history, and sharing links
- Observability, health checks, and deployment guidance
- Expanded API documentation and automated CI checks

## License

No license has been selected yet. Until one is added, standard copyright restrictions apply to reuse and redistribution.
