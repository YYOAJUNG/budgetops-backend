package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.aws.dto.AwsEc2MetricsResponse;
import com.budgetops.backend.aws.service.AwsEc2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/aws/accounts")
@RequiredArgsConstructor
public class AwsEc2Controller {

    private final AwsEc2Service ec2Service;

    @GetMapping("/{accountId}/ec2/instances")
    public ResponseEntity<List<AwsEc2InstanceResponse>> listInstances(
            @PathVariable Long accountId,
            @RequestParam(value = "region", required = false) String regionOverride
    ) {
        List<AwsEc2InstanceResponse> instances = ec2Service.listInstances(accountId, regionOverride);
        return ResponseEntity.ok(instances);
    }

    @GetMapping("/{accountId}/ec2/instances/{instanceId}")
    public ResponseEntity<AwsEc2InstanceResponse> getInstance(
            @PathVariable Long accountId,
            @PathVariable String instanceId,
            @RequestParam(value = "region", required = false) String regionOverride
    ) {
        AwsEc2InstanceResponse instance = ec2Service.getEc2Instance(accountId, instanceId, regionOverride);
        return ResponseEntity.ok(instance);
    }

    @GetMapping("/{accountId}/ec2/instances/{instanceId}/metrics")
    public ResponseEntity<AwsEc2MetricsResponse> getInstanceMetrics(
            @PathVariable Long accountId,
            @PathVariable String instanceId,
            @RequestParam(value = "region", required = false) String regionOverride,
            @RequestParam(value = "hours", required = false, defaultValue = "1") Integer hours
    ) {
        AwsEc2MetricsResponse metrics = ec2Service.getInstanceMetrics(accountId, instanceId, regionOverride, hours);
        return ResponseEntity.ok(metrics);
    }
}

