CREATE TABLE salary_history (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id    INTEGER        NOT NULL REFERENCES employees (id),
    amount         DECIMAL(15, 2) NOT NULL CHECK (amount > 0),
    currency       CHAR(3)        NOT NULL,
    effective_date TEXT           NOT NULL,
    recorded_at    TIMESTAMP      NOT NULL
);
