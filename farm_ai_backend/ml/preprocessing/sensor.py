import numpy as np

def clean_and_parse_value(val, default=0.0):
    """
    Clean and convert string or numeric sensor reading to float.
    Handles unit qualifiers (e.g. °C, %, ppm) and converts Fahrenheit to Celsius.
    """
    if val is None or (isinstance(val, float) and np.isnan(val)):
        return default
        
    if isinstance(val, (int, float)):
        return float(val)
        
    s = str(val).strip().lower()
    if not s:
        return default
        
    # Check for temperature scale (Fahrenheit vs Celsius)
    is_fahrenheit = False
    if 'f' in s:
        is_fahrenheit = True
        
    # Extract only digit characters, decimal separators, and negative sign
    clean_str = ""
    for char in s:
        if char.isdigit() or char in ['.', '-']:
            clean_str += char
            
    if not clean_str:
        return default
        
    try:
        parsed_val = float(clean_str)
        if is_fahrenheit:
            # Convert to Celsius: (F - 32) * 5/9
            parsed_val = (parsed_val - 32.0) * 5.0 / 9.0
        return parsed_val
    except ValueError:
        return default


def preprocess_sensor_data(temperature, humidity, ammonia, fill_missing=True):
    """
    Cleans telemetry data, parses units, and handles missing/nan inputs.
    Returns:
        list: [temp_celsius, humidity_percentage, ammonia_ppm]
    """
    # Standard comfortable fallback conditions for chicken house telemetry
    temp_default = 24.0
    hum_default = 60.0
    ammonia_default = 10.0
    
    t = clean_and_parse_value(temperature, default=temp_default if fill_missing else None)
    h = clean_and_parse_value(humidity, default=hum_default if fill_missing else None)
    a = clean_and_parse_value(ammonia, default=ammonia_default if fill_missing else None)
    
    # Standardize fraction to percentage (e.g., 0.65 -> 65.0%) if needed
    if h is not None and 0.0 <= h <= 1.0:
        h = h * 100.0
        
    return [t, h, a]


def scale_sensor_features(features, method="minmax"):
    """
    Scale features to normalized space:
    - Min-Max scales inputs to [0.0, 1.0] using typical poultry shed physical boundaries.
    - Z-Score normalizes inputs based on standard poultry house statistics.
    """
    if not features or len(features) < 3:
        return features
        
    # Physical sensor boundaries for typical poultry sensor ranges
    ranges = {
        "temp": (10.0, 45.0),
        "hum": (20.0, 100.0),
        "ammonia": (0.0, 50.0)
    }
    
    t, h, a = features
    
    # Guard divisions by zero
    t = t or ranges["temp"][0]
    h = h or ranges["hum"][0]
    a = a or ranges["ammonia"][0]
    
    if method == "minmax":
        t_scaled = (t - ranges["temp"][0]) / (ranges["temp"][1] - ranges["temp"][0])
        h_scaled = (h - ranges["hum"][0]) / (ranges["hum"][1] - ranges["hum"][0])
        a_scaled = (a - ranges["ammonia"][0]) / (ranges["ammonia"][1] - ranges["ammonia"][0])
        
        return [
            float(np.clip(t_scaled, 0.0, 1.0)),
            float(np.clip(h_scaled, 0.0, 1.0)),
            float(np.clip(a_scaled, 0.0, 1.0))
        ]
        
    elif method == "zscore":
        # Mean & standard dev for standard comfort zones
        t_scaled = (t - 26.0) / 4.0
        h_scaled = (h - 65.0) / 10.0
        a_scaled = (a - 12.0) / 5.0
        return [float(t_scaled), float(h_scaled), float(a_scaled)]
        
    return features


def engineer_sensor_features(temperature, humidity, ammonia):
    """
    Perform feature engineering from environmental data:
    - Temperature-Humidity Index (THI): Assess comfort zones and heat stress inside sheds.
      Formula: THI = 0.8 * T + (H / 100) * (T - 14.3) + 46.4
    - Gas-Thermal stress level multiplier: Combined impact of heat and high gas.
    """
    t = temperature or 24.0
    h = humidity or 60.0
    a = ammonia or 10.0
    
    # 1. Poultry THI
    thi = 0.8 * t + (h / 100.0) * (t - 14.3) + 46.4
    
    # 2. Combined Ammonia / Thermal Stress indicator
    gas_temp_stress = (t / 28.0) * (a / 15.0)
    
    return {
        "thi": float(thi),
        "gas_temp_stress": float(gas_temp_stress)
    }
