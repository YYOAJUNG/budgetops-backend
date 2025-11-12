package com.budgetops.backend.aws.service;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;

import java.time.Duration;

@Component
public class AwsCredentialValidator {
    public boolean isValid(String accessKeyId, String secretAccessKey, String region) {
        try {
            var creds = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            try (StsClient sts = StsClient.builder()
                    .region(region != null ? Region.of(region) : Region.AWS_GLOBAL)
                    .credentialsProvider(StaticCredentialsProvider.create(creds))
                    .overrideConfiguration(c -> c
                            .apiCallTimeout(Duration.ofSeconds(5))
                            .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                    .build()) {
                sts.getCallerIdentity(GetCallerIdentityRequest.builder().build());
                return true;
            }
        } catch (Exception ex) {
            return false;
        }
    }
}


