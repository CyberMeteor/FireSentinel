package com.firesentinel.nettytransport.config;

import com.firesentinel.nettytransport.handler.DeviceAuthHandler;
import com.firesentinel.nettytransport.handler.DeviceDataHandler;
import com.firesentinel.nettytransport.handler.HeartbeatHandler;
import com.firesentinel.nettytransport.handler.PreProcessingHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.json.JsonObjectDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration class for the Netty server.
 * Sets up the Netty server with appropriate handlers and channel options.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class NettyServerConfig {

    private final DeviceAuthHandler deviceAuthHandler;
    private final DeviceDataHandler deviceDataHandler;
    private final HeartbeatHandler heartbeatHandler;
    private final PreProcessingHandler preProcessingHandler;

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

    /**
     * Creates and configures the Netty server bootstrap.
     *
     * @return The configured ServerBootstrap
     */
    @Bean(destroyMethod = "shutdown")
    public NettyServer nettyServer() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(bossThreads);
        EventLoopGroup workerGroup = new NioEventLoopGroup(workerThreads);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                // Add a timeout handler to detect idle connections
                                // If no data is received for 10 seconds, the connection is considered idle
                                .addLast(new IdleStateHandler(10, 0, 0, TimeUnit.SECONDS))
                                // Add JSON decoder to parse incoming JSON messages
                                .addLast(new JsonObjectDecoder())
                                // Add string decoder and encoder for text-based communication
                                .addLast(new StringDecoder(CharsetUtil.UTF_8))
                                .addLast(new StringEncoder(CharsetUtil.UTF_8))
                                // Add custom handlers
                                .addLast(deviceAuthHandler) // Authenticate devices
                                .addLast(heartbeatHandler) // Handle heartbeats
                                .addLast(preProcessingHandler) // Pre-process data
                                .addLast(deviceDataHandler); // Process device data
                    }
                })
                .option(ChannelOption.SO_BACKLOG, soBacklog)
                .childOption(ChannelOption.SO_KEEPALIVE, soKeepAlive)
                .childOption(ChannelOption.TCP_NODELAY, tcpNoDelay);

        return new NettyServer(bootstrap, port, bossGroup, workerGroup);
    }

    /**
     * Inner class to manage the Netty server lifecycle.
     */
    public static class NettyServer {
        private final ServerBootstrap bootstrap;
        private final int port;
        private final EventLoopGroup bossGroup;
        private final EventLoopGroup workerGroup;

        public NettyServer(ServerBootstrap bootstrap, int port, EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
            this.bootstrap = bootstrap;
            this.port = port;
            this.bossGroup = bossGroup;
            this.workerGroup = workerGroup;
        }

        /**
         * Starts the Netty server.
         */
        public void start() {
            try {
                bootstrap.bind(port).sync();
                log.info("Netty server started on port {}", port);
            } catch (InterruptedException e) {
                log.error("Failed to start Netty server", e);
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Shuts down the Netty server.
         */
        public void shutdown() {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            log.info("Netty server shut down");
        }
    }
} 