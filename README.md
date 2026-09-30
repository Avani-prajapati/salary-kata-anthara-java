# ACME Salary Management

A web app for ACME's HR manager to browse, edit, and report on 10,000 employees' pay.
See [`docs/specs/001-employee-salary-management-spec.md`](docs/specs/001-employee-salary-management-spec.md).

| Part | Stack |
|---|---|
| `backend/` | Java 21, Spring Boot 3.5 (Web, Data JPA, Validation), SQLite, Flyway, Maven |
| `frontend/` | Angular 21 (standalone, zoneless), Angular Material, Vitest |

## Run it

```bash
# 1. Create the schema and seed 10,000 employees (idempotent: a no-op if the database already has employees)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed

# 2. Start the API on http://localhost:8080
./mvnw spring-boot:run

# 3. Start the UI on http://localhost:4200 (proxies /api to the backend)
cd ../frontend
npm install
npm start
```

The SQLite file defaults to `backend/salary-kata.db`; override it with `SALARY_DB_PATH=/path/to/file.db`.

## Test it

```bash
cd backend && ./mvnw test          # JUnit 5 + AssertJ + Mockito; repository tests run against a temporary SQLite file
cd frontend && npx ng test --watch=false   # Vitest + Angular TestBed + Material harnesses
```

## API

`GET /api/employees` — paginated directory.

| Query parameter | Meaning |
|---|---|
| `name` | Case-insensitive partial name match |
| `department`, `countryCode`, `jobLevel` | Exact-match filters, combinable |
| `page`, `size` | Zero-based page; size defaults to 25 and is capped at 100 |
| `sort` | `name`, `department`, `jobLevel`, `countryCode`, or `salaryAmount`, with `,asc` / `,desc` |

Errors are RFC 9457 Problem Details carrying `code` and `message`.
