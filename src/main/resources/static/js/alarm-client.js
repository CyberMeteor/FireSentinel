/**
 * FireSentinel Alarm Dashboard Client
 * 
 * This client connects to the FireSentinel alarm system using both WebSocket and MQTT
 * to receive real-time alarm notifications and display them to the user.
 */

// Configuration
const config = {
    websocket: {
        endpoint: '/ws-alarm',
        topics: {
            all: '/topic/alarm/all',
            high: '/topic/alarm/high'
        }
    },
    mqtt: {
        host: location.hostname,
        port: 9001, // Default for MQTT over WebSockets
        path: '/mqtt',
        clientId: 'firesentinel-dashboard-' + Math.random().toString(16).substr(2, 8),
        topics: {
            all: 'firesentinel/alarm/all',
            high: 'firesentinel/alarm/high'
        }
    },
    ui: {
        maxAlarms: 100,
        refreshInterval: 30000, // 30 seconds
        notificationTimeout: 5000 // 5 seconds
    }
};

// Global variables
let stompClient = null;
let mqttClient = null;
let alarms = [];
let currentFilter = 'all';

/**
 * Initializes the client when the page is loaded.
 */
document.addEventListener('DOMContentLoaded', function() {
    // Initialize UI
    initializeUI();
    
    // Connect to WebSocket
    connectWebSocket();
    
    // Connect to MQTT
    connectMQTT();
    
    // Set up periodic refresh
    setInterval(refreshAlarmList, config.ui.refreshInterval);
    
    // Request notification permission
    requestNotificationPermission();
});

/**
 * Initializes the UI event handlers.
 */
function initializeUI() {
    // Set up severity filter
    const severityFilter = document.getElementById('severity-filter');
    if (severityFilter) {
        severityFilter.addEventListener('change', function() {
            currentFilter = this.value;
            refreshAlarmList();
        });
    }
    
    // Display empty message
    const alarmList = document.getElementById('alarm-list');
    if (alarmList) {
        alarmList.innerHTML = '<div class="empty-message">No alarms to display</div>';
    }
}

/**
 * Connects to the WebSocket server.
 */
function connectWebSocket() {
    const socket = new SockJS(config.websocket.endpoint);
    stompClient = Stomp.over(socket);
    
    // Disable debug logging
    stompClient.debug = null;
    
    stompClient.connect({}, function(frame) {
        // Update connection status
        updateConnectionStatus('websocket', true);
        
        // Subscribe to all alarms
        stompClient.subscribe(config.websocket.topics.all, function(message) {
            handleAlarmNotification(JSON.parse(message.body), 'websocket');
        });
        
        // Subscribe to high severity alarms
        stompClient.subscribe(config.websocket.topics.high, function(message) {
            // This is redundant with the 'all' subscription, but useful for demonstration
            console.log('Received high severity alarm via WebSocket');
        });
        
    }, function(error) {
        // Handle connection error
        updateConnectionStatus('websocket', false);
        console.error('WebSocket connection error:', error);
        
        // Try to reconnect after a delay
        setTimeout(connectWebSocket, 5000);
    });
}

/**
 * Connects to the MQTT broker.
 */
function connectMQTT() {
    // Create MQTT client
    mqttClient = new Paho.MQTT.Client(
        config.mqtt.host,
        config.mqtt.port,
        config.mqtt.path,
        config.mqtt.clientId
    );
    
    // Set callback handlers
    mqttClient.onConnectionLost = function(response) {
        updateConnectionStatus('mqtt', false);
        console.error('MQTT connection lost:', response.errorMessage);
        
        // Try to reconnect after a delay
        setTimeout(connectMQTT, 5000);
    };
    
    mqttClient.onMessageArrived = function(message) {
        try {
            const alarm = JSON.parse(message.payloadString);
            handleAlarmNotification(alarm, 'mqtt');
        } catch (e) {
            console.error('Error parsing MQTT message:', e);
        }
    };
    
    // Connect to the broker
    mqttClient.connect({
        onSuccess: function() {
            updateConnectionStatus('mqtt', true);
            
            // Subscribe to topics
            mqttClient.subscribe(config.mqtt.topics.all);
            mqttClient.subscribe(config.mqtt.topics.high);
            
            console.log('Connected to MQTT broker');
        },
        onFailure: function(response) {
            updateConnectionStatus('mqtt', false);
            console.error('MQTT connection failed:', response.errorMessage);
            
            // Try to reconnect after a delay
            setTimeout(connectMQTT, 5000);
        }
    });
}

/**
 * Updates the connection status in the UI.
 * 
 * @param {string} type - The connection type ('websocket' or 'mqtt')
 * @param {boolean} connected - Whether the connection is established
 */
function updateConnectionStatus(type, connected) {
    const statusElement = document.getElementById(type + '-status');
    if (statusElement) {
        statusElement.textContent = connected ? 'Connected' : 'Disconnected';
        statusElement.className = connected ? 'connected' : 'disconnected';
    }
}

/**
 * Handles an alarm notification from either WebSocket or MQTT.
 * 
 * @param {Object} alarm - The alarm notification
 * @param {string} source - The source of the notification ('websocket' or 'mqtt')
 */
function handleAlarmNotification(alarm, source) {
    console.log(`Received alarm from ${source}:`, alarm);
    
    // Add the alarm to the list (avoid duplicates)
    const existingIndex = alarms.findIndex(a => a.id === alarm.id);
    if (existingIndex >= 0) {
        // Update existing alarm
        alarms[existingIndex] = alarm;
    } else {
        // Add new alarm
        alarms.unshift(alarm);
        
        // Limit the number of alarms
        if (alarms.length > config.ui.maxAlarms) {
            alarms.pop();
        }
        
        // Play sound for high severity alarms
        if (alarm.severity === 'HIGH') {
            playAlarmSound();
        }
        
        // Show desktop notification for high severity alarms
        if (alarm.severity === 'HIGH') {
            showDesktopNotification(alarm);
        }
    }
    
    // Update the UI
    refreshAlarmList();
}

/**
 * Refreshes the alarm list in the UI.
 */
function refreshAlarmList() {
    const alarmList = document.getElementById('alarm-list');
    const alarmCount = document.getElementById('alarm-count');
    
    if (!alarmList) return;
    
    // Filter alarms based on current filter
    const filteredAlarms = currentFilter === 'all' 
        ? alarms 
        : alarms.filter(alarm => alarm.severity === currentFilter);
    
    // Update alarm count
    if (alarmCount) {
        alarmCount.textContent = filteredAlarms.length;
    }
    
    // Clear the list
    alarmList.innerHTML = '';
    
    // Display empty message if no alarms
    if (filteredAlarms.length === 0) {
        alarmList.innerHTML = '<div class="empty-message">No alarms to display</div>';
        return;
    }
    
    // Add each alarm to the list
    filteredAlarms.forEach(alarm => {
        const alarmItem = document.createElement('div');
        alarmItem.className = `alarm-item ${alarm.severity.toLowerCase()}`;
        
        // Format the timestamp
        const timestamp = new Date(alarm.timestamp);
        const formattedTime = timestamp.toLocaleTimeString();
        const formattedDate = timestamp.toLocaleDateString();
        
        alarmItem.innerHTML = `
            <div class="alarm-info">
                <div class="alarm-header">
                    <span class="alarm-severity ${alarm.severity.toLowerCase()}">${alarm.severity}</span>
                    <span class="alarm-title">${alarm.alarmType}</span>
                </div>
                <div class="alarm-details">
                    <div class="alarm-detail">
                        <span class="detail-label">Device ID</span>
                        <span class="detail-value">${alarm.deviceId}</span>
                    </div>
                    <div class="alarm-detail">
                        <span class="detail-label">Location</span>
                        <span class="detail-value">${alarm.buildingId || ''} ${alarm.roomId || ''}</span>
                    </div>
                    <div class="alarm-detail">
                        <span class="detail-label">Value</span>
                        <span class="detail-value">${alarm.value || 'N/A'} ${alarm.unit || ''}</span>
                    </div>
                </div>
            </div>
            <div class="alarm-time">
                <div>${formattedTime}</div>
                <div>${formattedDate}</div>
            </div>
        `;
        
        alarmList.appendChild(alarmItem);
    });
}

/**
 * Plays the alarm sound.
 */
function playAlarmSound() {
    const alarmSound = document.getElementById('alarm-sound');
    if (alarmSound) {
        alarmSound.play().catch(e => {
            console.error('Error playing alarm sound:', e);
        });
    }
}

/**
 * Shows a desktop notification for an alarm.
 * 
 * @param {Object} alarm - The alarm notification
 */
function showDesktopNotification(alarm) {
    if (!('Notification' in window) || Notification.permission !== 'granted') {
        return;
    }
    
    const title = `${alarm.severity} Alarm: ${alarm.alarmType}`;
    const options = {
        body: `Device: ${alarm.deviceId}\nLocation: ${alarm.buildingId || ''} ${alarm.roomId || ''}`,
        icon: '/images/alarm-icon.png',
        tag: `alarm-${alarm.id}`,
        requireInteraction: alarm.severity === 'HIGH'
    };
    
    const notification = new Notification(title, options);
    
    // Close the notification after a timeout (except for high severity)
    if (alarm.severity !== 'HIGH') {
        setTimeout(() => {
            notification.close();
        }, config.ui.notificationTimeout);
    }
    
    // Handle notification click
    notification.onclick = function() {
        window.focus();
        notification.close();
    };
}

/**
 * Requests permission for desktop notifications.
 */
function requestNotificationPermission() {
    if (!('Notification' in window)) {
        console.log('This browser does not support desktop notifications');
        return;
    }
    
    if (Notification.permission !== 'granted' && Notification.permission !== 'denied') {
        Notification.requestPermission();
    }
} 