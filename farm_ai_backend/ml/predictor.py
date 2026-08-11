from ml.manager import ModelManager

def predict_disease(temperature, humidity):
    """
    Perform disease prediction using environmental telemetry.
    Delegates to the pre-loaded in-memory SensorPredictor in ModelManager.
    """
    return ModelManager.predict_sensor([temperature, humidity, 0.0])
