"""
bus_finder.py
-------------
Girdi : stop_id (str)
Çıktı : {
    "stop_id":           str,
    "line_id":           str,
    "trip_id":           str,
    "estimated_minutes": float,
    "ai_explanation":    str,
    "crowding_level":    str,
    "avg_passengers_waiting": int,
    "weather_condition": str,
    "temperature_c":     float,
    "status":            str   # "ok" | "no_data" | "error"
}
"""

import json
import os
from datetime import datetime
import pandas as pd
from anthropic import Anthropic

DATA_DIR = os.path.dirname(os.path.abspath(__file__))

def load_data():
    stop_arrivals   = pd.read_csv(os.path.join(DATA_DIR, "stop_arrivals.csv"))
    bus_stops       = pd.read_csv(os.path.join(DATA_DIR, "bus_stops.csv"))
    passenger_flow  = pd.read_csv(os.path.join(DATA_DIR, "passenger_flow.csv"))
    weather_obs     = pd.read_csv(os.path.join(DATA_DIR, "weather_observations.csv"))
    return stop_arrivals, bus_stops, passenger_flow, weather_obs


def find_next_bus(stop_id: str) -> dict:
    try:
        stop_arrivals, bus_stops, passenger_flow, weather_obs = load_data()

        # Durak var mı?
        if bus_stops[bus_stops["stop_id"] == stop_id].empty:
            return {"status": "no_data", "message": f"'{stop_id}' ID'li durak bulunamadı."}

        # O durağa ait kayıtları çek, en güncel olanı al
        arrivals = stop_arrivals[stop_arrivals["stop_id"] == stop_id].copy()
        if arrivals.empty:
            return {"status": "no_data", "message": f"'{stop_id}' durağına ait varış verisi yok."}

        arrivals["actual_arrival"] = pd.to_datetime(arrivals["actual_arrival"])
        latest = arrivals.sort_values("actual_arrival", ascending=False).iloc[0]

        raw_minutes       = float(latest["minutes_to_next_bus"])
        delay_min         = float(latest["delay_min"])
        traffic_level     = latest["traffic_level"]
        speed_factor      = float(latest["speed_factor"])
        trip_id           = latest["trip_id"]
        line_id           = latest["line_id"]

        # Yolcu kalabalığı - mevcut saat ve durağa göre en yakın kayıt
        current_hour = datetime.now().hour
        current_dow  = datetime.now().weekday() + 1  # 1-7

        pf = passenger_flow[
            (passenger_flow["stop_id"] == stop_id) &
            (passenger_flow["line_id"] == line_id)
        ].copy()

        if not pf.empty:
            pf["hour_diff"] = abs(pf["hour_of_day"] - current_hour)
            pf["dow_diff"]  = abs(pf["day_of_week"] - current_dow)
            pf["score"]     = pf["hour_diff"] + pf["dow_diff"]
            pf_row          = pf.sort_values("score").iloc[0]
            crowding_level          = pf_row["crowding_level"]
            avg_passengers_waiting  = int(pf_row["avg_passengers_waiting"])
        else:
            crowding_level         = "unknown"
            avg_passengers_waiting = -1

        # Hava durumu - en son gözlem
        weather_obs["timestamp"] = pd.to_datetime(weather_obs["timestamp"])
        latest_weather = weather_obs.sort_values("timestamp", ascending=False).iloc[0]
        weather_condition = latest_weather["weather_condition"]
        temperature_c     = float(latest_weather["temperature_c"])

        # --- AI KAPALI: Hazır olunca aşağıdaki bloğun yorumunu kaldır ---
        # estimated_minutes, ai_explanation = ask_claude(
        #     stop_id=stop_id,
        #     trip_id=trip_id,
        #     raw_minutes=raw_minutes,
        #     delay_min=delay_min,
        #     weather_condition=weather_condition,
        #     traffic_level=traffic_level,
        #     speed_factor=speed_factor,
        # )

        return {
            "stop_id":                stop_id,
            "line_id":                line_id,
            "trip_id":                trip_id,
            "estimated_minutes":      raw_minutes,
            "ai_explanation":         "AI devre disi - ham veri",
            "crowding_level":         crowding_level,
            "avg_passengers_waiting": avg_passengers_waiting,
            "weather_condition":      weather_condition,
            "temperature_c":          temperature_c,
            "status":                 "ok",
        }

    except Exception as e:
        return {"status": "error", "message": str(e)}


def ask_claude(stop_id, trip_id, raw_minutes, delay_min, weather_condition, traffic_level, speed_factor):
    client = Anthropic()

    prompt = f"""
Aşağıdaki otobüs verilerine bakarak tahmini varış süresini hesapla.

Durak        : {stop_id}
Sefer        : {trip_id}
Ham süre     : {raw_minutes:.1f} dakika
Gecikme      : {delay_min:.1f} dakika
Hava         : {weather_condition}
Trafik       : {traffic_level}
Hız faktörü  : {speed_factor:.3f}

Sadece şu JSON formatında yanıt ver, başka hiçbir şey yazma:
{{"estimated_minutes": <sayı>, "explanation": "<kısa Türkçe açıklama>"}}
""".strip()

    message = client.messages.create(
        model="claude-sonnet-4-20250514",
        max_tokens=256,
        messages=[{"role": "user", "content": prompt}],
    )

    raw_text = message.content[0].text.strip()

    try:
        clean = raw_text.replace("```json", "").replace("```", "").strip()
        data = json.loads(clean)
        return float(data["estimated_minutes"]), data["explanation"]
    except Exception:
        return raw_minutes, raw_text


# Test
if __name__ == "__main__":
    import sys
    stop = sys.argv[1] if len(sys.argv) > 1 else "STP-L01-03"
    result = find_next_bus(stop)
    print(json.dumps(result, ensure_ascii=False, indent=2))
