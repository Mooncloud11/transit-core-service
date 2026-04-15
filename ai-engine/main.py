import json
import pandas as pd
from datetime import datetime
from fastapi import FastAPI, HTTPException
import uvicorn
from google import genai
from google.genai import types

# =====================================================================
# AYARLAR
# =====================================================================
API_KEYS = ["AIzaSyAtUtdlcYEBACkezzAv5P065F_ciCDobkU"]
client = genai.Client(api_key=API_KEYS[0])

app = FastAPI(title="Sivas Predictive Transit AI")


# =====================================================================
# VERİ TOPLAMA FONKSİYONU (ÖMER'İN MANTIĞI)
# =====================================================================
def fetch_all_data_for_line(line_code: str, hour: int, minute: int):
    try:
        stops = pd.read_csv("bus_stops.csv")
        flow = pd.read_csv("passenger_flow.csv")
        weather = pd.read_csv("weather_observations.csv")
        trips = pd.read_csv("bus_trips.csv")

        # 1. Hattın Duraklarını ve Aylık İstatistiklerini Çek
        line_stops = stops[stops['line_id'] == line_code]
        if line_stops.empty:
            return {"error": f"{line_code} hattı bulunamadı."}

        # 1 Aylık Verilerin Ortalaması (Doluluk ve Gecikme)
        line_trips = trips[trips['line_id'] == line_code]
        avg_occupancy = int(line_trips['avg_occupancy_pct'].mean()) if not line_trips.empty else 0
        avg_monthly_delay = round(line_trips['total_delay_min'].mean(), 1) if not line_trips.empty else 0

        # 2. Hava Durumu ve Kalabalık (O anki saat için)
        current_weather = weather.iloc[0]['weather_condition']

        # Hattın başlangıç durağındaki yoğunluk
        first_stop_id = line_stops['stop_id'].iloc[0]
        stop_flow = flow[(flow['stop_id'] == first_stop_id) & (flow['hour_of_day'] == hour)]
        crowding = stop_flow['crowding_level'].iloc[0] if not stop_flow.empty else "Normal"
        passengers = int(stop_flow['avg_passengers_waiting'].iloc[0]) if not stop_flow.empty else 0

        return {
            "line_id": line_code,
            "line_name": line_stops['line_name'].iloc[0],
            "request_time": f"{hour:02d}:{minute:02d}",
            "weather": current_weather,
            "monthly_stats": {
                "avg_occupancy_pct": avg_occupancy,
                "avg_delay_min": avg_monthly_delay
            },
            "real_time_factors": {
                "origin_stop": first_stop_id,
                "crowding_level": crowding,
                "passengers_waiting": passengers
            }
        }
    except Exception as e:
        return {"error": str(e)}


# =====================================================================
# JAVA BACKEND İÇİN ENDPOINT
# =====================================================================
@app.get("/predict")
def predict_delay(line_code: str, hour: int, minute: int):
    # 1. Veri toplama
    raw_data = fetch_all_data_for_line(line_code, hour, minute)

    if "error" in raw_data:
        raise HTTPException(status_code=404, detail=raw_data["error"])

    # 2. Gemini 2.0 Flash'a Gönderilecek Prompt
    prompt = f"""
    Analyze this transit data for Sivas Smart City project.
    Data: {json.dumps(raw_data)}

    Instructions:
    - Estimate real-time delay based on weather and crowding.
    - Provide colors: 'GREEN' (efficient), 'YELLOW' (busy), 'RED' (delayed/crowded).
    - Provide a short, human-like advice for the passenger in Turkish.

    Output ONLY a strict JSON in this format:
    {{
      "real_time_delay_min": 5,
      "status_color": "GREEN|YELLOW|RED",
      "passenger_advice": "Tavsiye cümlesi",
      "route_details": {{
          "line": "Hat Adı",
          "monthly_occupancy": "%X",
          "crowding_status": "Durum"
      }}
    }}
    """

    try:
        response = client.models.generate_content(
            model="gemini-flash-latest",
            config=types.GenerateContentConfig(
                response_mime_type="application/json"
            ),
            contents=prompt
        )

        # 3. Java'ya doğrudan temiz JSON'ı paslıyoruz
        return json.loads(response.text)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"AI Error: {str(e)}")


if __name__ == "__main__":
    # Sunucuyu 8000 portunda başlat
    uvicorn.run(app, host="0.0.0.0", port=8000)