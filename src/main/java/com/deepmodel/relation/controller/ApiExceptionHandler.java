package com.deepmodel.relation.controller;

import com.deepmodel.relation.env.EnvServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EnvServiceException.class)
    public ResponseEntity<Map<String, Object>> handleEnvService(EnvServiceException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getUserMessage());
        body.put("code", ex.getCode());
        body.put("env", ex.getEnv());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
