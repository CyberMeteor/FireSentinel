package com.firesentinel.nettytransport.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Initializes the Netty server on application startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NettyServerInitializer {

    private final NettyServerConfig.NettyServer nettyServer;

    /**
     * Starts the Netty server when the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startNettyServer() {
        log.info("Starting Netty server...");
        nettyServer.start();
    }
} 