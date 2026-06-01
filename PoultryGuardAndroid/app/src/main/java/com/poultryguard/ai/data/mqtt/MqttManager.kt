package com.poultryguard.ai.data.mqtt

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import kotlin.concurrent.fixedRateTimer

class MqttManager(
    private val context: Context,
    private val brokerUrl: String = "tcp://broker.hivemq.com:1883", // Public sandbox broker for ESP32/Raspberry Pi
    private val onReadingReceived: (topic: String, value: Float) -> Unit,
    private val onConnectionStateChanged: (connected: Boolean) -> Unit
) {
    private var mqttClient: MqttAsyncClient? = null
    private var isConnected = false
    private var fallbackTimer: java.util.Timer? = null

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
                    
                    // Stop simulated data timer if running
                    fallbackTimer?.cancel()
                    fallbackTimer = null

                    // Subscribe to all hardware nodes
                    subscribeToTopic(TOPIC_TEMP)
                    subscribeToTopic(TOPIC_HUMID)
                    subscribeToTopic(TOPIC_AMMONIA)
                    subscribeToTopic(TOPIC_SOUND)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnected = false
                    onConnectionStateChanged(false)
                    Log.w("PoultryGuardMqtt", "MQTT Broker connection failed: ${exception?.localizedMessage}. Launching local simulator feed.")
                    startSimulatedMqttFeed()
                }
            })

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    isConnected = false
                    onConnectionStateChanged(false)
                    Log.w("PoultryGuardMqtt", "MQTT connection lost: ${cause?.localizedMessage}. Fallback engaged.")
                    startSimulatedMqttFeed()
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic != null && message != null) {
                        try {
                            val payload = String(message.payload)
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
            startSimulatedMqttFeed()
        }
    }

    private fun subscribeToTopic(topic: String) {
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

    // High fidelity offline fallback to feed MQTT subscribers
    private fun startSimulatedMqttFeed() {
        if (fallbackTimer != null) return

        var simTemp = 24.2f
        var simHumid = 61.5f
        var simAmmonia = 12.0f
        var simSound = 54.0f

        fallbackTimer = fixedRateTimer("mqtt_sim", daemon = true, initialDelay = 1000L, period = 4000L) {
            simTemp += (kotlin.random.Random.nextFloat() - 0.5f) * 0.3f
            simHumid += (kotlin.random.Random.nextFloat() - 0.5f) * 0.6f
            simAmmonia += (kotlin.random.Random.nextFloat() - 0.5f) * 0.4f
            simSound += (kotlin.random.Random.nextFloat() - 0.5f) * 1.2f

            simTemp = simTemp.coerceIn(19f, 32f)
            simHumid = simHumid.coerceIn(45f, 85f)
            simAmmonia = simAmmonia.coerceIn(4f, 35f)
            simSound = simSound.coerceIn(40f, 90f)

            // Direct callbacks to simulate broker broadcasts
            onReadingReceived(TOPIC_TEMP, simTemp)
            onReadingReceived(TOPIC_HUMID, simHumid)
            onReadingReceived(TOPIC_AMMONIA, simAmmonia)
            onReadingReceived(TOPIC_SOUND, simSound)
        }
    }

    fun isBrokerConnected(): Boolean {
        return isConnected
    }

    fun disconnect() {
        fallbackTimer?.cancel()
        fallbackTimer = null
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            // Graceful exit
        }
    }
}
