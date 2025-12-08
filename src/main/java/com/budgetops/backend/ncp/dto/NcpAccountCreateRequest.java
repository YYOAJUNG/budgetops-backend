package com.budgetops.backend.ncp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NcpAccountCreateRequest {
    @NotBlank
    private String name;

    private String regionCode; // KR, JP, US 등 (optional)

    @NotBlank
    @Size(min = 10, max = 64)
    private String accessKey;

    @NotBlank
    @Size(min = 10, max = 128)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String secretKey;
}
