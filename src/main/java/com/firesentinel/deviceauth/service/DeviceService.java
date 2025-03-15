package com.firesentinel.deviceauth.service;

import com.firesentinel.deviceauth.model.Device;
import com.firesentinel.deviceauth.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing devices in the system.
 * Handles CRUD operations for devices and device authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;

    /**
     * Finds a device by its unique device ID.
     *
     * @param deviceId The device ID
     * @return The device if found, null otherwise
     */
    public Device findByDeviceId(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId).orElse(null);
    }

    /**
     * Finds a device by its device ID and API key.
     * Used for device authentication.
     *
     * @param deviceId The device ID
     * @param apiKey The API key
     * @return The device if found and credentials match, null otherwise
     */
    public Device findByDeviceIdAndApiKey(String deviceId, String apiKey) {
        return deviceRepository.findByDeviceIdAndApiKey(deviceId, apiKey).orElse(null);
    }

    /**
     * Saves a device to the database.
     *
     * @param device The device to save
     * @return The saved device
     */
    @Transactional
    public Device save(Device device) {
        return deviceRepository.save(device);
    }

    /**
     * Registers a new device in the system.
     *
     * @param device The device to register
     * @return The registered device
     */
    @Transactional
    public Device registerDevice(Device device) {
        device.setRegistrationDate(LocalDateTime.now());
        device.setEnabled(true);
        return deviceRepository.save(device);
    }

    /**
     * Disables a device.
     *
     * @param deviceId The device ID
     * @return true if the device was disabled, false otherwise
     */
    @Transactional
    public boolean disableDevice(String deviceId) {
        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isPresent()) {
            Device device = deviceOpt.get();
            device.setEnabled(false);
            deviceRepository.save(device);
            log.info("Device disabled: {}", deviceId);
            return true;
        }
        return false;
    }

    /**
     * Enables a device.
     *
     * @param deviceId The device ID
     * @return true if the device was enabled, false otherwise
     */
    @Transactional
    public boolean enableDevice(String deviceId) {
        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isPresent()) {
            Device device = deviceOpt.get();
            device.setEnabled(true);
            deviceRepository.save(device);
            log.info("Device enabled: {}", deviceId);
            return true;
        }
        return false;
    }

    /**
     * Gets all devices in the system.
     *
     * @return A list of all devices
     */
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    /**
     * Gets all enabled devices in the system.
     *
     * @return A list of all enabled devices
     */
    public List<Device> getAllEnabledDevices() {
        return deviceRepository.findByEnabled(true);
    }
} 