package com.example.pfe.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogRedactorTest {

    @Test
    void masksCommonSecretsAndKeepsLineCount() {
        String log = String.join("\n",
                "curl -H \"Authorization: Bearer abcdefghijklmnop1234\" https://x",
                "git clone https://build:s3cretPass@dev.azure.com/org/repo",
                "DefaultEndpointsProtocol=https;AccountName=a;AccountKey=Zm9vYmFyYmF6==;",
                "password=hunter2 next",
                "npm error code ERESOLVE");

        LogRedactor.Result r = LogRedactor.redact(log);

        assertThat(r.text()).doesNotContain("abcdefghijklmnop1234", "s3cretPass", "Zm9vYmFyYmF6", "hunter2");
        assertThat(r.text()).contains("npm error code ERESOLVE");
        assertThat(r.text().split("\n", -1)).hasSize(5);
        assertThat(r.count()).isGreaterThanOrEqualTo(4);
    }
}
