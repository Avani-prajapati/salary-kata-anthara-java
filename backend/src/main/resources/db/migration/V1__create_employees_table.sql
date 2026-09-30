CREATE TABLE employees (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            VARCHAR(200)   NOT NULL,
    department      VARCHAR(40)    NOT NULL,
    job_level       VARCHAR(40)    NOT NULL,
    country_code    CHAR(2)        NOT NULL,
    hire_date       TEXT           NOT NULL,
    manager_id      INTEGER        REFERENCES employees (id),
    salary_amount   DECIMAL(15, 2) NOT NULL CHECK (salary_amount > 0),
    salary_currency CHAR(3)        NOT NULL,
    created_at      TIMESTAMP      NOT NULL,
    updated_at      TIMESTAMP      NOT NULL
);
