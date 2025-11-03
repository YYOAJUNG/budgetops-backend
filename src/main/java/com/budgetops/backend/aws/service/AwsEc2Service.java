package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class AwsEc2Service {
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final AwsAccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<AwsEc2InstanceResponse> listInstances(Long accountId, String regionOverride) {
        AwsAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "계정을 찾을 수 없습니다."));

        String accessKeyId = account.getAccessKeyId();
        String secretAccessKey = account.getSecretKeyEnc();
        if (accessKeyId == null || secretAccessKey == null) {
            throw new ResponseStatusException(NOT_FOUND, "계정 자격 증명이 존재하지 않습니다.");
        }

        String effectiveRegion = (regionOverride != null && !regionOverride.isBlank())
                ? regionOverride
                : account.getDefaultRegion();
        Region region = resolveRegion(effectiveRegion);

        try (Ec2Client ec2 = Ec2Client.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .overrideConfiguration(c -> c
                        .apiCallAttemptTimeout(Duration.ofSeconds(10))
                        .apiCallTimeout(Duration.ofSeconds(30)))
                .build()) {

            return ec2.describeInstancesPaginator(DescribeInstancesRequest.builder().build())
                    .reservations().stream()
                    .flatMap(reservation -> reservation.instances().stream())
                    .map(this::toResponse)
                    .toList();

        } catch (SdkException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "EC2 인스턴스 정보를 조회하지 못했습니다.", ex);
        }
    }

    private Region resolveRegion(String region) {
        if (region == null || region.isBlank()) {
            return Region.US_EAST_1;
        }
        try {
            return Region.of(region);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "지원하지 않는 AWS Region 입니다: " + region, ex);
        }
    }

    private AwsEc2InstanceResponse toResponse(Instance instance) {
        return AwsEc2InstanceResponse.builder()
                .instanceId(instance.instanceId())
                .name(extractNameTag(instance))
                .instanceType(instance.instanceTypeAsString())
                .state(instance.state() != null ? instance.state().nameAsString() : null)
                .availabilityZone(instance.placement() != null ? instance.placement().availabilityZone() : null)
                .publicIp(instance.publicIpAddress())
                .privateIp(instance.privateIpAddress())
                .launchTime(Optional.ofNullable(instance.launchTime())
                        .map(ISO_FORMATTER::format)
                        .orElse(null))
                .build();
    }

    private String extractNameTag(Instance instance) {
        if (instance.tags() == null) {
            return null;
        }
        return instance.tags().stream()
                .filter(tag -> "Name".equalsIgnoreCase(tag.key()))
                .map(Tag::value)
                .findFirst()
                .orElse(null);
    }
}

