package com.budgetops.backend.config;

import com.siot.IamportRestClient.IamportClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IamportConfig 테스트")
class IamportConfigTest {

    @Test
    @DisplayName("IamportClient 빈 생성")
    void iamportClient_Created() {
        // given
        IamportConfig config = new IamportConfig();
        ReflectionTestUtils.setField(config, "apiKey", "test_api_key");
        ReflectionTestUtils.setField(config, "apiSecret", "test_api_secret");

        // when
        IamportClient client = config.iamportClient();

        // then
        assertThat(client).isNotNull();
    }
}

