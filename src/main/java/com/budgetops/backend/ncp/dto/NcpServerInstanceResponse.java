package com.budgetops.backend.ncp.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class NcpServerInstanceResponse {
    private String serverInstanceNo;
    private String serverName;
    private String serverDescription;
    private Integer cpuCount;
    private Long memorySize;
    private String platformType;
    private String publicIp;
    private String privateIp;
    private String serverInstanceStatus; // INIT, CREAT, RUN, NSTOP
    private String serverInstanceStatusName; // running, stopped 등
    private String createDate;
    private String uptime;
    private String serverImageProductCode;
    private String serverProductCode;
    private String zoneCode;
    private String regionCode;
    private String vpcNo;
    private String subnetNo;
}
