import io
import requests
import numpy as np
from PIL import Image

def preprocess_image(file_path_or_url, target_size=(224, 224), normalize=True):
    """
    Downloads (if URL), loads, forces RGB conversion, resizes, and normalizes pixels.
    Returns:
        np.ndarray: Preprocessed image as a numpy float array
    """
    # 1. Load image (from URL or file path)
    if isinstance(file_path_or_url, str) and file_path_or_url.startswith(("http://", "https://")):
        try:
            r = requests.get(file_path_or_url, timeout=15)
            r.raise_for_status()
            img = Image.open(io.BytesIO(r.content))
        except Exception as e:
            raise ValueError(f"Failed to fetch/load image from URL: {str(e)}")
    elif isinstance(file_path_or_url, str):
        try:
            img = Image.open(file_path_or_url)
        except Exception as e:
            raise ValueError(f"Failed to open image from path: {str(e)}")
    elif isinstance(file_path_or_url, Image.Image):
        img = file_path_or_url
    elif hasattr(file_path_or_url, 'read'): # File-like object
        try:
            img = Image.open(file_path_or_url)
        except Exception as e:
            raise ValueError(f"Failed to open image file-like object: {str(e)}")
    else:
        raise ValueError("Invalid input format for image preprocessing")

    # 2. Convert to RGB if not already
    if img.mode != "RGB":
        if img.mode == "RGBA":
            # Composite over a white background to avoid dark artifacts
            background = Image.new("RGB", img.size, (255, 255, 255))
            background.paste(img, mask=img.split()[3]) # 3 is the alpha channel
            img = background
        else:
            img = img.convert("RGB")

    # 3. Resize to target dimensions
    img = img.resize(target_size, Image.Resampling.BILINEAR)

    # 4. Standardize and normalize pixel intensities
    img_array = np.array(img, dtype=np.float32)
    if normalize:
        img_array = img_array / 255.0

    return img_array


def should_trigger_image_capture(temp, hum, sound, prev_temp=None, prev_hum=None, prev_sound=None, last_capture_time=None, time_threshold_minutes=10):
    """
    Checks if an image capture should be triggered:
    - If last_capture_time is more than 10 minutes ago.
    - If there are sudden sensor anomalies (e.g. temperature jump, humidity warning, sound spike).
    """
    # 1. Check time interval first
    if last_capture_time is not None:
        import time
        current_time = time.time()
        if hasattr(last_capture_time, 'timestamp'):
            elapsed_sec = current_time - last_capture_time.timestamp()
        else:
            elapsed_sec = current_time - float(last_capture_time)
            
        if elapsed_sec >= (time_threshold_minutes * 60.0):
            return True

    # 2. Check current critical thresholds
    # High temp (> 30.0), high hum (> 75%), or high noise (> 78 dB)
    if temp is not None and float(temp) >= 30.0:
        return True
    if hum is not None and float(hum) >= 75.0:
        return True
    if sound is not None and float(sound) >= 78.0:
        return True

    # 3. Check sudden spikes relative to previous readings
    if prev_temp is not None and temp is not None:
        if (float(temp) - float(prev_temp)) >= 2.0: # Sudden temp spike of 2°C or more
            return True
            
    if prev_hum is not None and hum is not None:
        if (float(hum) - float(prev_hum)) >= 10.0: # Sudden humidity spike of 10%
            return True
            
    if prev_sound is not None and sound is not None:
        if (float(sound) - float(prev_sound)) >= 15.0: # Sudden noise surge of 15dB
            return True

    # Default to True if no capture has ever occurred
    if last_capture_time is None:
        return True

    return False
