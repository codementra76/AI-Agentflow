package com.agentflow.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SystemController.class)
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/system/info returns backend identification JSON")
    void shouldReturnSystemInfo() throws Exception {
        mockMvc.perform(get("/api/v1/system/info")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("AgentFlow backend online"))
            .andExpect(jsonPath("$.data.platform").value("AgentFlow AI Workflow Automation Platform"))
            .andExpect(jsonPath("$.data.status").value("ONLINE"))
            .andExpect(jsonPath("$.data.runtime").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
