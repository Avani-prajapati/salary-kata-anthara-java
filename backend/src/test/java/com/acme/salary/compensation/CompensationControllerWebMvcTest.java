package com.acme.salary.compensation;

import com.acme.salary.compensation.dto.SalaryChangeRequest;
import com.acme.salary.compensation.dto.SalaryHistoryEntryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompensationController.class)
class CompensationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CompensationService compensationService;

    @Test
    void records_a_salary_change_and_returns_the_created_entry() throws Exception {
        SalaryChangeRequest request = new SalaryChangeRequest(new BigDecimal("100000.00"), "USD", LocalDate.of(2024, 6, 1));
        when(compensationService.changeSalary(eq(7L), eq(request))).thenReturn(new SalaryHistoryEntryResponse(
                new BigDecimal("100000.00"), "USD", LocalDate.of(2024, 6, 1), Instant.parse("2024-06-01T00:00:00Z")));

        mockMvc.perform(post("/api/employees/7/salary-changes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(100000.00))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.effectiveDate").value("2024-06-01"))
                .andExpect(jsonPath("$.recordedAt").value("2024-06-01T00:00:00Z"));
    }

    @Test
    void rejects_a_zero_or_negative_amount_with_a_validation_problem_detail() throws Exception {
        String payload = """
                {"amount": 0, "currencyCode": "USD", "effectiveDate": "2024-06-01"}
                """;

        mockMvc.perform(post("/api/employees/7/salary-changes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("amount")));
    }

    @Test
    void rejects_a_missing_effective_date_with_a_validation_problem_detail() throws Exception {
        String payload = """
                {"amount": 100000.00, "currencyCode": "USD", "effectiveDate": null}
                """;

        mockMvc.perform(post("/api/employees/7/salary-changes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("effectiveDate")));
    }
}
