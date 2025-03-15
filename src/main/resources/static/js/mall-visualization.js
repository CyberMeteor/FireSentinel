/**
 * FireSentinel Mall Visualization
 * 
 * This script handles the 3D visualization of a shopping mall with real-time device status updates.
 * It implements a hybrid push/pull data model for efficient updates and historical data retrieval.
 */

// Configuration
const config = {
    // WebSocket configuration
    websocket: {
        endpoint: '/firesentinel-websocket',
        topics: {
            alarms: '/topic/alarm/all',
            deviceUpdates: '/topic/device/updates',
            snapshot: '/topic/alarm/snapshot'
        }
    },
    
    // REST API endpoints
    api: {
        alarmHistory: '/api/alarm-history',
        dataSync: '/api/data-sync',
        devices: '/api/devices'
    },
    
    // Data sync configuration
    dataSync: {
        clientId: 'mall-visualization-' + Math.random().toString(36).substring(2, 10),
        snapshotInterval: 60000, // 1 minute
        deltaInterval: 5000,     // 5 seconds
        retryInterval: 3000      // 3 seconds
    },
    
    // Visualization configuration
    visualization: {
        colors: {
            floor: 0xcccccc,
            wall: 0x999999,
            activeDevice: 0x00ff00,
            inactiveDevice: 0xff0000,
            alarmLow: 0xffff00,
            alarmMedium: 0xff9900,
            alarmHigh: 0xff0000
        },
        deviceSize: 0.5,
        floorHeight: 0.1,
        wallHeight: 3.0
    }
};

// Global variables
let scene, camera, renderer, controls;
let stompClient = null;
let devices = [];
let alarms = [];
let lastSyncTimestamp = null;
let isWebSocketConnected = false;
let snapshotTimer = null;
let deltaTimer = null;

// Initialize the application
function init() {
    console.log('Initializing mall visualization...');
    
    // Initialize the 3D scene
    initScene();
    
    // Initialize the data connections
    initDataConnections();
    
    // Initialize UI event handlers
    initUIHandlers();
    
    // Start the animation loop
    animate();
    
    // Show the loading overlay initially
    showLoadingOverlay(true);
}

// Initialize the 3D scene
function initScene() {
    // Create the scene
    scene = new THREE.Scene();
    scene.background = new THREE.Color(0xf0f0f0);
    
    // Create the camera
    camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000);
    camera.position.set(20, 20, 20);
    
    // Create the renderer
    renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.shadowMap.enabled = true;
    document.getElementById('visualization-container').appendChild(renderer.domElement);
    
    // Create the controls
    controls = new THREE.OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.25;
    
    // Add ambient light
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.5);
    scene.add(ambientLight);
    
    // Add directional light
    const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8);
    directionalLight.position.set(10, 20, 10);
    directionalLight.castShadow = true;
    scene.add(directionalLight);
    
    // Add a grid helper
    const gridHelper = new THREE.GridHelper(50, 50);
    scene.add(gridHelper);
    
    // Handle window resize
    window.addEventListener('resize', onWindowResize);
}

// Initialize data connections (WebSocket and REST)
function initDataConnections() {
    // Connect to WebSocket
    connectWebSocket();
    
    // Initial data load
    loadInitialData();
}

// Connect to WebSocket
function connectWebSocket() {
    const socket = new SockJS(config.websocket.endpoint);
    stompClient = Stomp.over(socket);
    
    // Disable debug logging
    stompClient.debug = null;
    
    stompClient.connect({}, function(frame) {
        console.log('Connected to WebSocket: ' + frame);
        isWebSocketConnected = true;
        updateConnectionStatus('websocket', true);
        
        // Subscribe to alarm topics
        stompClient.subscribe(config.websocket.topics.alarms, function(message) {
            handleAlarmNotification(JSON.parse(message.body));
        });
        
        // Subscribe to device update topics
        stompClient.subscribe(config.websocket.topics.deviceUpdates, function(message) {
            handleDeviceUpdate(JSON.parse(message.body));
        });
        
        // Subscribe to snapshot topic
        stompClient.subscribe(config.websocket.topics.snapshot, function(message) {
            handleSnapshotUpdate(JSON.parse(message.body));
        });
        
        // Hide the loading overlay
        showLoadingOverlay(false);
        
    }, function(error) {
        console.error('Error connecting to WebSocket:', error);
        isWebSocketConnected = false;
        updateConnectionStatus('websocket', false);
        
        // Retry connection after a delay
        setTimeout(connectWebSocket, config.dataSync.retryInterval);
    });
}

// Load initial data
function loadInitialData() {
    // Load devices
    loadDevices();
    
    // Load initial alarm snapshot
    loadAlarmSnapshot();
    
    // Start the snapshot and delta timers
    startDataSyncTimers();
}

// Load devices from the API
function loadDevices() {
    fetch(config.api.devices)
        .then(response => response.json())
        .then(data => {
            devices = data;
            updateDeviceCount(devices.length);
            createDeviceObjects();
        })
        .catch(error => {
            console.error('Error loading devices:', error);
            // Retry after a delay
            setTimeout(loadDevices, config.dataSync.retryInterval);
        });
}

// Load alarm snapshot from the API
function loadAlarmSnapshot() {
    const url = `${config.api.dataSync}/snapshot?clientId=${config.dataSync.clientId}`;
    
    fetch(url)
        .then(response => response.json())
        .then(data => {
            handleSnapshotUpdate(data);
        })
        .catch(error => {
            console.error('Error loading alarm snapshot:', error);
            // Retry after a delay
            setTimeout(loadAlarmSnapshot, config.dataSync.retryInterval);
        });
}

// Start the data sync timers
function startDataSyncTimers() {
    // Clear any existing timers
    if (snapshotTimer) clearInterval(snapshotTimer);
    if (deltaTimer) clearInterval(deltaTimer);
    
    // Start the snapshot timer
    snapshotTimer = setInterval(loadAlarmSnapshot, config.dataSync.snapshotInterval);
    
    // Start the delta timer
    deltaTimer = setInterval(loadAlarmDelta, config.dataSync.deltaInterval);
}

// Load alarm delta from the API
function loadAlarmDelta() {
    // Only load delta if we have a last sync timestamp
    if (!lastSyncTimestamp) return;
    
    const url = `${config.api.dataSync}/delta?clientId=${config.dataSync.clientId}`;
    
    fetch(url)
        .then(response => response.json())
        .then(data => {
            handleDeltaUpdate(data);
        })
        .catch(error => {
            console.error('Error loading alarm delta:', error);
        });
}

// Handle snapshot update
function handleSnapshotUpdate(data) {
    console.log('Received alarm snapshot:', data);
    
    // Update the last sync timestamp
    lastSyncTimestamp = data.timestamp;
    
    // Update alarms
    if (data.alarms && Array.isArray(data.alarms)) {
        alarms = data.alarms;
        updateAlarmCount(alarms.length);
        updateAlarmList();
        updateDeviceColors();
    }
}

// Handle delta update
function handleDeltaUpdate(data) {
    console.log('Received alarm delta:', data);
    
    // Update the last sync timestamp
    lastSyncTimestamp = data.timestamp;
    
    // Update alarms
    if (data.alarms && Array.isArray(data.alarms)) {
        // Add new alarms to the existing list
        for (const alarm of data.alarms) {
            // Check if the alarm already exists
            const existingIndex = alarms.findIndex(a => a.id === alarm.id);
            
            if (existingIndex >= 0) {
                // Update existing alarm
                alarms[existingIndex] = alarm;
            } else {
                // Add new alarm
                alarms.push(alarm);
            }
        }
        
        updateAlarmCount(alarms.length);
        updateAlarmList();
        updateDeviceColors();
    }
}

// Handle alarm notification
function handleAlarmNotification(alarm) {
    console.log('Received alarm notification:', alarm);
    
    // Check if the alarm already exists
    const existingIndex = alarms.findIndex(a => a.id === alarm.id);
    
    if (existingIndex >= 0) {
        // Update existing alarm
        alarms[existingIndex] = alarm;
    } else {
        // Add new alarm
        alarms.push(alarm);
    }
    
    updateAlarmCount(alarms.length);
    updateAlarmList();
    updateDeviceColors();
    
    // Play sound for high severity alarms
    if (alarm.severity === 'HIGH') {
        playAlarmSound();
    }
}

// Handle device update
function handleDeviceUpdate(device) {
    console.log('Received device update:', device);
    
    // Check if the device already exists
    const existingIndex = devices.findIndex(d => d.id === device.id);
    
    if (existingIndex >= 0) {
        // Update existing device
        devices[existingIndex] = device;
    } else {
        // Add new device
        devices.push(device);
        // Create a new device object
        createDeviceObject(device);
    }
    
    updateDeviceCount(devices.length);
    updateDeviceColors();
}

// Create device objects in the 3D scene
function createDeviceObjects() {
    // Clear existing device objects
    scene.children.forEach(child => {
        if (child.userData.isDevice) {
            scene.remove(child);
        }
    });
    
    // Create a new device object for each device
    devices.forEach(device => {
        createDeviceObject(device);
    });
}

// Create a device object in the 3D scene
function createDeviceObject(device) {
    // Create a sphere for the device
    const geometry = new THREE.SphereGeometry(config.visualization.deviceSize, 32, 32);
    const material = new THREE.MeshStandardMaterial({
        color: getDeviceColor(device),
        metalness: 0.3,
        roughness: 0.4
    });
    
    const deviceObj = new THREE.Mesh(geometry, material);
    
    // Set the position based on the device's location
    deviceObj.position.set(
        device.location.x || 0,
        device.location.y || 0,
        device.location.z || 0
    );
    
    // Add user data for identification
    deviceObj.userData = {
        isDevice: true,
        deviceId: device.id,
        deviceType: device.type
    };
    
    // Add to the scene
    scene.add(deviceObj);
}

// Update device colors based on their status and alarms
function updateDeviceColors() {
    scene.children.forEach(child => {
        if (child.userData.isDevice) {
            const deviceId = child.userData.deviceId;
            const device = devices.find(d => d.id === deviceId);
            
            if (device) {
                child.material.color.set(getDeviceColor(device));
            }
        }
    });
}

// Get the color for a device based on its status and alarms
function getDeviceColor(device) {
    // Check if there are any active alarms for this device
    const deviceAlarms = alarms.filter(alarm => alarm.deviceId === device.id);
    
    if (deviceAlarms.length > 0) {
        // Find the highest severity alarm
        const highestSeverity = deviceAlarms.reduce((highest, alarm) => {
            const severityRank = { 'HIGH': 3, 'MEDIUM': 2, 'LOW': 1 };
            return Math.max(highest, severityRank[alarm.severity] || 0);
        }, 0);
        
        // Return the color based on the highest severity
        switch (highestSeverity) {
            case 3: return config.visualization.colors.alarmHigh;
            case 2: return config.visualization.colors.alarmMedium;
            case 1: return config.visualization.colors.alarmLow;
            default: return device.active ? 
                config.visualization.colors.activeDevice : 
                config.visualization.colors.inactiveDevice;
        }
    }
    
    // No alarms, return color based on device status
    return device.active ? 
        config.visualization.colors.activeDevice : 
        config.visualization.colors.inactiveDevice;
}

// Update the alarm list in the UI
function updateAlarmList() {
    const alarmList = document.getElementById('alarm-list');
    
    // Sort alarms by timestamp (newest first)
    const sortedAlarms = [...alarms].sort((a, b) => {
        return new Date(b.timestamp) - new Date(a.timestamp);
    });
    
    // Limit to the most recent 10 alarms
    const recentAlarms = sortedAlarms.slice(0, 10);
    
    // Clear the current list
    alarmList.innerHTML = '';
    
    // Add each alarm to the list
    recentAlarms.forEach(alarm => {
        const alarmItem = document.createElement('div');
        alarmItem.className = `alarm-item alarm-${alarm.severity.toLowerCase()}`;
        
        // Format the timestamp
        const timestamp = new Date(alarm.timestamp);
        const formattedTime = timestamp.toLocaleTimeString();
        
        alarmItem.innerHTML = `
            <div class="alarm-header">
                <span class="alarm-severity">${alarm.severity}</span>
                <span class="alarm-time">${formattedTime}</span>
            </div>
            <div class="alarm-details">
                <div class="alarm-type">${alarm.type}</div>
                <div class="alarm-location">Device: ${alarm.deviceId}</div>
                <div class="alarm-message">${alarm.message}</div>
            </div>
        `;
        
        alarmList.appendChild(alarmItem);
    });
}

// Update the connection status in the UI
function updateConnectionStatus(type, connected) {
    const statusElement = document.getElementById(`${type}-status`);
    
    if (statusElement) {
        statusElement.className = connected ? 'status-connected' : 'status-disconnected';
        statusElement.textContent = connected ? 'Connected' : 'Disconnected';
    }
}

// Update the device count in the UI
function updateDeviceCount(count) {
    const countElement = document.getElementById('device-count');
    
    if (countElement) {
        countElement.textContent = count;
    }
}

// Update the alarm count in the UI
function updateAlarmCount(count) {
    const countElement = document.getElementById('alarm-count');
    
    if (countElement) {
        countElement.textContent = count;
    }
}

// Play an alarm sound
function playAlarmSound() {
    const audio = document.getElementById('alarm-sound');
    
    if (audio) {
        audio.play().catch(error => {
            console.warn('Could not play alarm sound:', error);
        });
    }
}

// Show or hide the loading overlay
function showLoadingOverlay(show) {
    const overlay = document.getElementById('loading-overlay');
    
    if (overlay) {
        overlay.style.display = show ? 'flex' : 'none';
    }
}

// Initialize UI event handlers
function initUIHandlers() {
    // Reset camera button
    const resetCameraButton = document.getElementById('reset-camera');
    if (resetCameraButton) {
        resetCameraButton.addEventListener('click', () => {
            camera.position.set(20, 20, 20);
            camera.lookAt(0, 0, 0);
        });
    }
    
    // Toggle floor button
    const toggleFloorButton = document.getElementById('toggle-floor');
    if (toggleFloorButton) {
        toggleFloorButton.addEventListener('click', () => {
            // Toggle floor visibility
            scene.children.forEach(child => {
                if (child.userData.isFloor) {
                    child.visible = !child.visible;
                }
            });
        });
    }
}

// Handle window resize
function onWindowResize() {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
}

// Animation loop
function animate() {
    requestAnimationFrame(animate);
    
    // Update controls
    controls.update();
    
    // Render the scene
    renderer.render(scene, camera);
}

// Initialize the application when the DOM is loaded
document.addEventListener('DOMContentLoaded', init); 