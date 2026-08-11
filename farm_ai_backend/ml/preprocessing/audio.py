import os
import tempfile
import requests
import numpy as np
import librosa

def preprocess_audio(file_path_or_url, target_sr=22050):
    """
    Downloads (if URL), resamples, removes stationary noise, and normalizes audio.
    Returns:
        np.ndarray: Waveform (1D array of normalized floats)
        int: Sampling rate
    """
    temp_local_file = None
    
    # 1. Download audio file if URL
    if file_path_or_url.startswith(("http://", "https://")):
        try:
            r = requests.get(file_path_or_url, timeout=15)
            r.raise_for_status()
            # Write to a temp file
            suffix = os.path.splitext(file_path_or_url.split('/')[-1])[1] or ".wav"
            fd, temp_local_file = tempfile.mkstemp(suffix=suffix)
            with os.fdopen(fd, 'wb') as tmp:
                tmp.write(r.content)
            file_path = temp_local_file
        except Exception as e:
            raise ValueError(f"Failed to download audio from URL: {str(e)}")
    else:
        file_path = file_path_or_url

    # 2. Load and resample audio
    try:
        y, sr = librosa.load(file_path, sr=target_sr)
    except Exception as e:
        # Clean up temp file
        if temp_local_file and os.path.exists(temp_local_file):
            try:
                os.remove(temp_local_file)
            except Exception:
                pass
        raise ValueError(f"Failed to load/resample audio file: {str(e)}")

    # Cleanup temp file
    if temp_local_file and os.path.exists(temp_local_file):
        try:
            os.remove(temp_local_file)
        except Exception:
            pass

    # 3. Noise removal using basic spectral subtraction
    # If the file is too short for STFT, skip spectral subtraction
    if len(y) > 512:
        try:
            stft = librosa.stft(y)
            # Estimate background noise from the first 5 frames
            num_noise_frames = min(5, stft.shape[1])
            if num_noise_frames > 0:
                noise_profile = np.mean(np.abs(stft[:, :num_noise_frames]), axis=1, keepdims=True)
                # Subtract noise profile with flooring to avoid mathematical artifacts
                stft_clean = np.abs(stft) - 1.5 * noise_profile
                stft_clean = np.maximum(stft_clean, 0.0) * np.exp(1j * np.angle(stft))
                y = librosa.istft(stft_clean)
        except Exception as ne:
            print(f"[Sound Preprocessing] Warning: noise reduction skipped due to: {ne}")

    # 4. Amplitude normalization
    max_amp = np.max(np.abs(y))
    if max_amp > 1e-6:
        y = y / max_amp

    return y, target_sr


def is_valid_sound_clip(y, sr, min_rms=0.005, min_peak=0.01):
    """
    Checks that the audio clip contains actual sound (activity) and is not silent.
    """
    if len(y) == 0:
        return False
        
    # Peak amplitude check
    peak = np.max(np.abs(y))
    if peak < min_peak:
        return False
        
    # RMS (Root Mean Square) energy check
    rms = librosa.feature.rms(y=y)
    mean_rms = np.mean(rms)
    
    if mean_rms < min_rms:
        return False
        
    return True


def should_trigger_audio_processing(sound_level, last_processed_time=None, force_request=False, sound_threshold=78.0):
    """
    Decides whether audio recording processing should be triggered:
    - Always if forced (farmer request).
    - If sound level exceeds the abnormal noise threshold (e.g. 78.0 dB).
    - If 10 minutes (600s) have elapsed since the last processing run.
    """
    if force_request:
        return True
        
    if sound_level is not None and float(sound_level) >= sound_threshold:
        return True
        
    if last_processed_time is not None:
        # last_processed_time is expected to be a datetime object or timestamp in seconds
        # Return True if elapsed time is >= 600 seconds (10 minutes)
        import time
        current_time = time.time()
        # Handle datetime object conversion if passed instead of timestamp
        if hasattr(last_processed_time, 'timestamp'):
            elapsed = current_time - last_processed_time.timestamp()
        else:
            elapsed = current_time - float(last_processed_time)
            
        if elapsed >= 600.0:
            return True
        else:
            return False
            
    # Default trigger if no historical records exist
    return True
