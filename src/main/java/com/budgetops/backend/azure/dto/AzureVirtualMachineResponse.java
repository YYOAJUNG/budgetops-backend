package com.budgetops.backend.azure.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AzureVirtualMachineResponse {
    String id;
    String name;
    String resourceGroup;
    String location;
    String vmSize;
    String provisioningState;
    String powerState;
    String osType;
    String computerName;
    String privateIp;
    String publicIp;
    String availabilityZone;
    String timeCreated;
}

