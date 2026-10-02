package com.poultryguard.ai.data.mqtt

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

class MqttManager(
    private val context: Context,
    private val brokerUrl: String = "tcp://broker.hivemq.com:1883", // Public sandbox broker for ESP32/Raspberry Pi
    private val onReadingReceived: (topic: String, value: Float) -> Unit,
    private val onConnectionStateChanged: (connected: Boolean) -> Unit,
    private val onStringMessageReceived: ((topic: String, payload: String) -> Unit)? = null
) {
    private var mqttClient: MqttAsyncClient? = null
    private var isConnected = false

    // Standard MQTT topics matching farm nodes
    companion object {
        const val TOPIC_TEMP = "poultry/shed4/temp"
        const val TOPIC_HUMID = "poultry/shed4/humid"
        const val TOPIC_AMMONIA = "poultry/shed4/ammonia"
        const val TOPIC_SOUND = "poultry/shed4/sound"
    }

    init {
        connectToBroker()
    }

    fun connectToBroker() {
        try {
            val clientId = "poultry_guard_client_${UUID.randomUUID().toString().take(8)}"
            mqttClient = MqttAsyncClient(brokerUrl, clientId, MemoryPersistence())
            
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 5
                keepAliveInterval = 60
                isAutomaticReconnect = true
            }

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    isConnected = true
                    onConnectionStateChanged(true)
                    Log.d("PoultryGuardMqtt", "MQTT Connected successfully to: $brokerUrl")

                    // Subscribe to all hardware nodes
                    subscribeToTopic(TOPIC_TEMP)
                    subscribeToTopic(TOPIC_HUMID)
                    subscribeToTopic(TOPIC_AMMONIA)
                    subscribeToTopic(TOPIC_SOUND)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnected = false
                    onConnectionStateChanged(false)
                    Log.w("PoultryGuardMqtt", "MQTT Broker connection failed: ${exception?.localizedMessage}")
                }
            })

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    isConnected = false
                    onConnectionStateChanged(false)
                    Log.w("PoultryGuardMqtt", "MQTT connection lost: ${cause?.localizedMessage}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic != null && message != null) {
                        try {
                            val payload = String(message.payload)
                            
                            // Forward all string payloads to the optional callback
                            onStringMessageReceived?.invoke(topic, payload)
                            
                            val floatVal = payload.toFloatOrNull()
                            if (floatVal != null) {
                                onReadingReceived(topic, floatVal)
                            }
                        } catch (e: Exception) {
                            Log.e("PoultryGuardMqtt", "Error parsing MQTT payload: ${e.localizedMessage}")
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

        } catch (e: Exception) {
            Log.e("PoultryGuardMqtt", "MQTT setup error: ${e.localizedMessage}")
        }
    }

    fun subscribeToTopic(topic: String) {
        try {
            mqttClient?.subscribe(topic, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("PoultryGuardMqtt", "Subscribed to MQTT Topic: $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.w("PoultryGuardMqtt", "Subscription failed for: $topic")
                }
            })
        } catch (e: Exception) {
            Log.e("PoultryGuardMqtt", "Subscription error: ${e.localizedMessage}")
        }
    }

    fun isBrokerConnected(): Boolean {
        return isConnected
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            // Graceful exit
        }
    }
}
