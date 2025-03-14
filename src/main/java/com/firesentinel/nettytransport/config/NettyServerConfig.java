package com.firesentinel.nettytransport.config;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for the Netty server.
 * Sets up the Netty server with the appropriate configuration for handling
 * device connections and data streaming.
 */
@Configuration
@RequiredArgsConstructor
public class NettyServerConfig {

    @Value("${netty.server.port}")
    private int port;

    @Value("${netty.server.boss-threads}")
    private int bossThreads;

    @Value("${netty.server.worker-threads}")
    private int workerThreads;

    @Value("${netty.server.so-backlog}")
    private int soBacklog;

    @Value("${netty.server.so-keepalive}")
    private boolean soKeepAlive;

    @Value("${netty.server.tcp-nodelay}")
    private boolean tcpNoDelay;

    @Bean(destroyMethod = "shutdownGracefully")
    public NioEventLoopGroup bossGroup() {
        return new NioEventLoopGroup(bossThreads);
    }

    @Bean(destroyMethod = "shutdownGracefully")
    public NioEventLoopGroup workerGroup() {
        return new NioEventLoopGroup(workerThreads);
    }

    @Bean
    public ServerBootstrap serverBootstrap(NioEventLoopGroup bossGroup, NioEventLoopGroup workerGroup) {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, soBacklog)
                .childOption(ChannelOption.SO_KEEPALIVE, soKeepAlive)
                .childOption(ChannelOption.TCP_NODELAY, tcpNoDelay);
        
        return bootstrap;
    }
} 