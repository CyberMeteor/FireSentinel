package com.firesentinel.nettytransport.manager;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for device channels.
 * Maintains a mapping of deviceId -> channel.
 */
@Component
@Slf4j
public class ChannelManager {

    // Map of device ID to channel
    private final Map<String, Channel> deviceChannels = new ConcurrentHashMap<>();

    /**
     * Adds a channel for a device.
     *
     * @param deviceId The device ID
     * @param channel The channel
     */
    public void addChannel(String deviceId, Channel channel) {
        Channel oldChannel = deviceChannels.put(deviceId, channel);
        if (oldChannel != null && oldChannel.isActive()) {
            log.info("Closing old connection for device: {}", deviceId);
            oldChannel.close();
        }
        log.debug("Added channel for device: {}", deviceId);
    }

    /**
     * Removes a channel for a device.
     *
     * @param deviceId The device ID
     */
    public void removeChannel(String deviceId) {
        deviceChannels.remove(deviceId);
        log.debug("Removed channel for device: {}", deviceId);
    }

    /**
     * Gets the channel for a device.
     *
     * @param deviceId The device ID
     * @return The channel, or null if not found
     */
    public Channel getChannel(String deviceId) {
        return deviceChannels.get(deviceId);
    }

    /**
     * Checks if a device is connected.
     *
     * @param deviceId The device ID
     * @return true if the device is connected, false otherwise
     */
    public boolean isConnected(String deviceId) {
        Channel channel = deviceChannels.get(deviceId);
        return channel != null && channel.isActive();
    }

    /**
     * Gets the number of connected devices.
     *
     * @return The number of connected devices
     */
    public int getConnectedDeviceCount() {
        return (int) deviceChannels.values().stream()
                .filter(Channel::isActive)
                .count();
    }

    /**
     * Gets all connected device IDs.
     *
     * @return A set of connected device IDs
     */
    public java.util.Set<String> getConnectedDeviceIds() {
        return deviceChannels.keySet();
    }

    /**
     * Sends a message to a device.
     *
     * @param deviceId The device ID
     * @param message The message to send
     * @return true if the message was sent, false otherwise
     */
    public boolean sendMessage(String deviceId, String message) {
        Channel channel = deviceChannels.get(deviceId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
            return true;
        }
        return false;
    }

    /**
     * Broadcasts a message to all connected devices.
     *
     * @param message The message to broadcast
     * @return The number of devices the message was sent to
     */
    public int broadcastMessage(String message) {
        int count = 0;
        for (Channel channel : deviceChannels.values()) {
            if (channel.isActive()) {
                channel.writeAndFlush(message);
                count++;
            }
        }
        return count;
    }
} 