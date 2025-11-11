package com.budgetops.backend.aws.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AwsAccountCreateRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String defaultRegion; // 예: ap-northeast-2

    // AccessKeyId 대략 16~24자의 영대문자+숫자
    @NotBlank
    @Pattern(regexp = "^[A-Z0-9]{16,24}$", message = "accessKeyId 형식이 올바르지 않습니다.")
    private String accessKeyId;

    // Secret은 길이만 완화 체크. 응답에 절대 노출 금지
    @NotBlank
    @Size(min = 32, max = 128)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String secretAccessKey;

    // Workspace ID
    private Long workspaceId;
}


