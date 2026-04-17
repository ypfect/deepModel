package com.deepmodel.relation.controller;

import com.deepmodel.relation.service.ExpressionValidatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ValidationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ExpressionValidatorService validatorService;

    @InjectMocks
    private ValidationController validationController;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(validationController).build();
    }

    @Test
    public void testCheckObject() throws Exception {
        mockMvc.perform(get("/api/validation/check").param("objectType", "ArReceipt"))
                .andExpect(status().isOk());
    }
}
