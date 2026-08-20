import socket
import json
import time
import threading
import paho.mqtt.client as mqtt

# Configuration
BROKER_URL = "broker.hivemq.com"
PORT = 1883
STATUS_TOPIC_TEMPLATE = "poultry/device/{}/status"

def provision_device(device_id, ssid, password):
    """
    Simulates the ESP32 connection handshake and publishes status updates over MQTT.
    No raw telemetry readings are published to avoid bypassing the production data pipeline.
    """
    print(f"[ESP32] Provisioning credentials: SSID={ssid}, Password={password}")
    
    client = mqtt.Client(client_id=f"esp32_sim_{device_id}")
    
    try:
        client.connect(BROKER_URL, PORT, 60)
        client.loop_start()
    except Exception as e:
        print(f"[ESP32 Error] Failed to connect to MQTT broker: {e}")
        return
        
    status_topic = STATUS_TOPIC_TEMPLATE.format(device_id)
    
    def log_and_publish(status, message, ip=None):
        payload = {"status": status, "message": message}
        if ip:
            payload["ip"] = ip
        payload_str = json.dumps(payload)
        print(f"[ESP32 MQTT Publish] Topic: {status_topic} | Payload: {payload_str}")
        client.publish(status_topic, payload_str)
        time.sleep(1.2)
        
    log_and_publish("PROVISIONING", "Provisioning requested.")
    log_and_publish("CONNECTING_WIFI", f"Connecting to Wi-Fi SSID: {ssid}...")
    log_and_publish("CONNECTED", "Handshake successful!", ip="192.168.4.150")
    log_and_publish("ONLINE", "Gateway online.")
    
    # Stay connected to keep status active, then disconnect cleanly
    time.sleep(2)
    client.loop_stop()
    client.disconnect()
    print(f"[ESP32] Completed provisioning flow for {device_id}.")

def start_softap_server():
    """
    Starts a local TCP socket server on port 8080 to simulate SoftAP/BLE transfer.
    """
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind(('0.0.0.0', 8080))
    server_socket.listen(5)
    
    print("[ESP32 SoftAP Simulator] Listening on TCP port 8080 (simulated SoftAP/BLE)...")
    
    while True:
        try:
            client_socket, address = server_socket.accept()
            print(f"[ESP32 SoftAP Simulator] Connection accepted from: {address}")
            data = client_socket.recv(1024).decode('utf-8')
            if data:
                try:
                    payload = json.loads(data)
                    device_id = payload.get("deviceId", "unknown_device")
                    ssid = payload.get("ssid", "")
                    password = payload.get("password", "")
                    
                    # Run the provisioning process in a separate thread
                    prov_thread = threading.Thread(target=provision_device, args=(device_id, ssid, password))
                    prov_thread.start()
                except Exception as je:
                    print(f"[ESP32 SoftAP Error] Failed to parse JSON data: {je}")
            client_socket.close()
        except KeyboardInterrupt:
            break
        except Exception as e:
            print(f"[ESP32 SoftAP Error] Socket accept failed: {e}")
            time.sleep(1)
            
    server_socket.close()

if __name__ == "__main__":
    start_softap_server()
