package com.firesentinel.deviceauth.repository;

import com.firesentinel.deviceauth.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing Device entities.
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    
    /**
     * Finds a device by its unique device ID.
     *
     * @param deviceId The device ID
     * @return An Optional containing the device if found
     */
    Optional<Device> findByDeviceId(String deviceId);
    
    /**
     * Finds a device by its device ID and API key.
     *
     * @param deviceId The device ID
     * @param apiKey The API key
     * @return An Optional containing the device if found
     */
    Optional<Device> findByDeviceIdAndApiKey(String deviceId, String apiKey);
    
    /**
     * Finds all devices with the specified enabled status.
     *
     * @param enabled The enabled status
     * @return A list of devices with the specified enabled status
     */
    List<Device> findByEnabled(boolean enabled);
} 