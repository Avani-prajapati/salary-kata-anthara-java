package com.acme.salary.common.error;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ProblemDetailAdvice extends ResponseEntityExceptionHandler {

    private static final String CODE = "code";
    private static final String MESSAGE = "message";

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFound(EntityNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidReferenceException.class)
    public ProblemDetail handleInvalidReference(InvalidReferenceException exception) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REFERENCE", exception.getMessage());
        problem.setProperty("field", exception.field());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ARGUMENT", exception.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Request validation failed");
        problem.setProperty("errors", fieldErrors(exception));
        return ResponseEntity.unprocessableEntity().headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            withCodeAndMessage(problem, HttpStatus.valueOf(status.value()).name(), problem.getDetail());
        }
        return super.handleExceptionInternal(exception, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        return withCodeAndMessage(ProblemDetail.forStatusAndDetail(status, message), code, message);
    }

    private static ProblemDetail withCodeAndMessage(ProblemDetail problem, String code, String message) {
        problem.setProperty(CODE, code);
        problem.setProperty(MESSAGE, message);
        return problem;
    }

    private static List<Map<String, String>> fieldErrors(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
                .map(ProblemDetailAdvice::toFieldError)
                .toList();
    }

    private static Map<String, String> toFieldError(FieldError error) {
        return Map.of("field", error.getField(), MESSAGE, String.valueOf(error.getDefaultMessage()));
    }
}
