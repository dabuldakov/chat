package com.chat.server.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    private final TokenHasher hasher = new TokenHasher();

    @Test
    void hashesDeterministicallyWithSha256() {
        assertThat(hasher.hash("token"))
                .isEqualTo("3c469e9d6c5875d37a43f353d4f88e61fcf812c66eee3457465a40b0da4153e0");
    }

    @Test
    void differentTokensProduceDifferentHashes() {
        assertThat(hasher.hash("a")).isNotEqualTo(hasher.hash("b"));
    }

    @Test
    void nullTokenStaysNull() {
        assertThat(hasher.hash(null)).isNull();
    }
}
