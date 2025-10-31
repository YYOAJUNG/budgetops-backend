package com.budgetops.backend.gcp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration-style test for /gcp/onboarding/service-account/test that can run
 * against a real GCP service account JSON. This test is SKIPPED by default and
 * only runs when both conditions are met:
 * - Environment variable RUN_GCP_IT = "true"
 * - Environment variable SERVICE_ACCOUNT_KEY_FILE points to a readable JSON file
 *
 * The service account id (email) is read from the JSON field "client_email".
 */
@SpringBootTest
@AutoConfigureMockMvc
class GcpOnboardingControllerIT {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("[IT] /service-account/test works with real service account credentials (conditionally)")
    void serviceAccountTest_withRealCredentials_conditionally() throws Exception {
        String runIt = System.getenv("RUN_GCP_IT");
        Assumptions.assumeTrue("true".equalsIgnoreCase(runIt),
                "RUN_GCP_IT!=true -> integration test skipped");

        String keyFilePath = System.getenv("SERVICE_ACCOUNT_KEY_FILE");
        Assumptions.assumeTrue(keyFilePath != null && !keyFilePath.isBlank(),
                "SERVICE_ACCOUNT_KEY_FILE not set -> integration test skipped");

        Path path = Path.of(keyFilePath);
        Assumptions.assumeTrue(Files.exists(path) && Files.isReadable(path),
                "SERVICE_ACCOUNT_KEY_FILE not found/readable -> integration test skipped");

        // Read JSON and extract client_email to use as serviceAccountId
        String keyJson = readAll(path);
        String serviceAccountEmail = extractClientEmail(keyJson);
        Assumptions.assumeTrue(serviceAccountEmail != null && !serviceAccountEmail.isBlank(),
                "client_email not found in key json -> integration test skipped");

        // 1) set service account id
        mockMvc.perform(post("/gcp/onboarding/service-account/id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"serviceAccountId\":\"" + escapeJson(serviceAccountEmail) + "\"}"))
                .andExpect(status().isOk());

        // 2) upload service account key json
        //    Wrap the raw JSON as a JSON-string field value
        String bodyWithEmbeddedJson = objectMapper.createObjectNode()
                .put("serviceAccountKeyJson", keyJson)
                .toString();

        mockMvc.perform(post("/gcp/onboarding/service-account/key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithEmbeddedJson))
                .andExpect(status().isOk());

        // 3) call test endpoint; require real GCP success (strict)
        mockMvc.perform(post("/gcp/onboarding/service-account/test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.missingRoles.length()").value(0))
                .andExpect(jsonPath("$.grantedPermissions").isArray())
                .andExpect(jsonPath("$.grantedPermissions").isNotEmpty())
                .andExpect(jsonPath("$.grantedPermissions").value(org.hamcrest.Matchers.hasItems(
                        "resourcemanager.projects.get",
                        "monitoring.timeSeries.list",
                        "bigquery.datasets.get",
                        "bigquery.jobs.create"
                )));
    }

    @Test
    @DisplayName("[IT] /billing-account/test verifies dataset and latest table with real GCP (conditionally)")
    void billingTest_withRealGcp_conditionally() throws Exception {
        String runIt = System.getenv("RUN_GCP_IT");
        Assumptions.assumeTrue("true".equalsIgnoreCase(runIt),
                "RUN_GCP_IT!=true -> integration test skipped");

        String keyFilePath = System.getenv("SERVICE_ACCOUNT_KEY_FILE");
        Assumptions.assumeTrue(keyFilePath != null && !keyFilePath.isBlank(),
                "SERVICE_ACCOUNT_KEY_FILE not set -> integration test skipped");

        String billingAccountId = System.getenv("BILLING_ACCOUNT_ID");
        Assumptions.assumeTrue(billingAccountId != null && !billingAccountId.isBlank(),
                "BILLING_ACCOUNT_ID not set -> integration test skipped");

        Path path = Path.of(keyFilePath);
        Assumptions.assumeTrue(Files.exists(path) && Files.isReadable(path),
                "SERVICE_ACCOUNT_KEY_FILE not found/readable -> integration test skipped");

        // Read JSON and extract client_email to use as serviceAccountId
        String keyJson = readAll(path);
        String serviceAccountEmail = extractClientEmail(keyJson);
        Assumptions.assumeTrue(serviceAccountEmail != null && !serviceAccountEmail.isBlank(),
                "client_email not found in key json -> integration test skipped");

        // 1) set service account id
        mockMvc.perform(post("/gcp/onboarding/service-account/id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"serviceAccountId\":\"" + escapeJson(serviceAccountEmail) + "\"}"))
                .andExpect(status().isOk());

        // 2) upload service account key json
        String bodyWithEmbeddedJson = objectMapper.createObjectNode()
                .put("serviceAccountKeyJson", keyJson)
                .toString();
        mockMvc.perform(post("/gcp/onboarding/service-account/key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithEmbeddedJson))
                .andExpect(status().isOk());

        // 3) set billing account id
        String billingBody = objectMapper.createObjectNode()
                .put("billingAccountId", billingAccountId)
                .toString();
        mockMvc.perform(post("/gcp/onboarding/billing-account/id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billingBody))
                .andExpect(status().isOk());

        // 4) call billing test endpoint; require real success
        mockMvc.perform(post("/gcp/onboarding/billing-account/test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.datasetExists").value(true))
                .andExpect(jsonPath("$.latestTable").isString())
                .andExpect(jsonPath("$.latestTable").isNotEmpty());
    }

    private static String readAll(Path p) throws IOException {
        return Files.readString(p);
    }

    private static String extractClientEmail(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode email = root.path("client_email");
        return email.isTextual() ? email.asText() : null;
    }

    private static String escapeJson(String s) {
        // minimal escaping for embedding into JSON string
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}