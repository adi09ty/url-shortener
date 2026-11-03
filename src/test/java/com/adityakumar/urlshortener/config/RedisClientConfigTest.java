package com.adityakumar.urlshortener.config;

import io.lettuce.core.ClientOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisClientConfigTest {

    @Test
    void commandsAreRejectedWhileDisconnectedAndConnectingGivesUpQuickly() {
        ClientOptions options = RedisClientConfig.failFast(ClientOptions.builder()).build();

        assertEquals(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS, options.getDisconnectedBehavior());
        assertEquals(RedisClientConfig.CONNECT_TIMEOUT, options.getSocketOptions().getConnectTimeout());
    }
}
