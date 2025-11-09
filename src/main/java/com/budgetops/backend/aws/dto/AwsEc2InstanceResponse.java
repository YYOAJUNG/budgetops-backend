package com.budgetops.backend.aws.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AwsEc2InstanceResponse {
    String instanceId;
    String name;
    String instanceType;
    String state;
    String availabilityZone;
    String publicIp;
    String privateIp;
    String launchTime;
}

