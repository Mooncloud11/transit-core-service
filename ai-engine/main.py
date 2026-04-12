from fastapi import FastAPI

app = FastAPI()

@app.get("/predict")
def get_prediction(route_id: str, stop_id: str):
    # Bu veri şu anlık sahtedir (Mock Data). 
    # Pazartesi günü gerçek veriler gelince buraya makine öğrenmesi modelimizi entegre edeceğiz.
    return {
        "predicted_arrival_minutes": 4,
        "crowd_level": "High",
        "confidence_score": 0.88,
        "message": f"{route_id} numaralı otobüs, {stop_id} durağına yaklaşıyor."
    }