import numpy as np
import librosa

def extract_spectrogram(file_path):
    """
    Load audio file and extract the mel-spectrogram with the exact configurations
    used during the model's training: sr=22050, n_mels=128, duration=4s, target width=173.
    """
    try:
        y, sr = librosa.load(file_path, sr=22050, duration=4)
        mel = librosa.feature.melspectrogram(y=y, sr=sr, n_mels=128)
        log_mel = librosa.power_to_db(mel)

        # Pad or truncate to fixed shape (128, 173)
        if log_mel.shape[1] < 173:
            pad = 173 - log_mel.shape[1]
            log_mel = np.pad(log_mel, ((0, 0), (0, pad)))
        else:
            log_mel = log_mel[:, :173]

        return log_mel
    except Exception as e:
        print(f"[Sound Preprocessing] Error extracting spectrogram from {file_path}: {e}")
        return None
