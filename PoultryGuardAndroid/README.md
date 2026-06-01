# Poultry Guard AI - Farmer Android App

A premium, production-ready Android mobile dashboard built with **Kotlin**, **Jetpack Compose**, and the **MVVM (Model-View-ViewModel)** architectural pattern. Optimized specifically for the **Farmer** role to monitor broiler house environment parameters in real-time.

---

## 🎨 Brand Aesthetic & Design System

The visual theme draws inspiration from modern agriculture, adopting a premium **Green, White, and Blue** design system:
- **Forest Green (`#2E7D32`):** Primary theme color symbolizing healthy chick growth, nature, and organic agriculture.
- **Sky/Water Blue (`#1E88E5`):** Secondary accent color symbolizing technology flow, clean hydration, and sensor networks.
- **Warm Off-White (`#F6F9F6`):** Soft organic background that elevates card layouts and reduces visual fatigue during high-frequency checks.
- **Status Accents:** Dynamic color pills (Ideal: Green, Warning: Orange, Critical: Red) immediately flag anomalies.

---

## 🏗️ Architecture (MVVM)

This project strictly follows the recommended Android Architecture guidelines:
1. **Model (`data.model.SensorReading`):** Self-contained, immutably designed data representation of environmental telemetry.
2. **ViewModel (`ui.dashboard.DashboardViewModel`):** Controls telemetry state, maintains reactive `StateFlow` structures, manages pull-to-refresh logic, and drives simulation actions.
3. **View Layer (`ui/` screens):** Completely declarative Compose views:
   - **`DashboardScreen`:** Core card-based grid (Temperature, Humidity, Ammonia, and Sound) alongside environmental controls and the simulator controls deck.
   - **`ControlsScreen`:** Adjustment deck for Ventilation temperatures, heater cut-in thresholds, lighting periods, and sprinkler configurations.
   - **`AlertsScreen`:** Event-driven notification lists for AI predictions (flock stress sounds matched by sound models, gas alarms, etc.).
   - **`ProfileScreen`:** Owner settings, contact points, and secure SMS alarm routing numbers.

---

## 🛠️ Testing the Replay/Simulator Deck

To showcase how the UI handles real-time data flow and warning states, we've integrated a **Telemetry Simulator Deck** at the bottom of the Monitor tab:
- **Heat Spike:** Spikes temperature to `31.8°C` (triggers Red Alert & Misting recommendation banner).
- **Ammonia Gas:** Spikes ammonia to `32.5 ppm` (triggers Red Alert & Litter venting advice).
- **Panic Noise:** Spikes sound to `88.0 dB` (triggers Red Alert for sudden high panic scream/predators).
- **Reset Live:** Restores the environment to normal, calm agricultural metrics.

---

## 🚀 How to Run in Android Studio

1. Open **Android Studio** (Jellyfish or newer recommended).
2. Select **Open** and select the `/PoultryGuardAndroid` directory.
3. Allow Gradle to sync and resolve libraries (Uses Gradle Wrapper 8.5, JDK 17, AGP 8.2.2).
4. Run the `:app` configuration on your Emulator or Physical Device!
