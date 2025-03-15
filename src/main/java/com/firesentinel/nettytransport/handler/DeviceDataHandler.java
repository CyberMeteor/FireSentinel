package com.firesentinel.nettytransport.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Netty handler for processing device data.
 * Forwards valid data to Kafka for further processing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ChannelHandler.Sharable
public class DeviceDataHandler extends ChannelInboundHandlerAdapter {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String content = (String) msg;
        
        try {
            JsonNode json = objectMapper.readTree(content);
            
            // Only process data messages
            if (json.has("type") && "data".equals(json.get("type").asText())) {
                processData(ctx, json, content);
            }
            
            // We're the last handler in the pipeline, so we don't need to forward the message
        } catch (Exception e) {
            log.error("Error processing data: {}", e.getMessage());
        }
    }

    /**
     * Processes device data.
     *
     * @param ctx The channel handler context
     * @param json The data message
     * @param rawContent The raw message content
     */
    private void processData(ChannelHandlerContext ctx, JsonNode json, String rawContent) {
        try {
            String deviceId = ctx.channel().attr(DeviceAuthHandler.DEVICE_ID).get();
            if (deviceId == null) {
                return;
            }
            
            // Send the data to Kafka for further processing
            kafkaTemplate.send("sensor-data-topic", deviceId, rawContent);
            
            log.debug("Sent data from device {} to Kafka", deviceId);
        } catch (Exception e) {
            log.error("Error sending data to Kafka: {}", e.getMessage());
        }
    }
} 