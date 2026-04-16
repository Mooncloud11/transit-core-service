import os
import json
import time
import random
import pandas as pd
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
from dotenv import load_dotenv
from google import genai
from google.genai import types
from datetime import datetime

# =====================================================================
# 1. GİZLİ AYARLAR VE API KURULUMU
# =====================================================================
load_dotenv()

API_KEY = os.getenv("GEMINI_API_KEY")
if not API_KEY:
    raise ValueError("KRİTİK HATA: .env dosyasında GEMINI_API_KEY bulunamadı!")

client = genai.Client(api_key=API_KEY)

# =====================================================================
# 2. FASTAPI SUNUCU AYARLARI
# =====================================================================
app = FastAPI(title="Sivas Predictive Transit AI")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# =====================================================================
# 3. OVERALL CACHING MECHANISM
# =====================================================================
_cache = {}
CACHE_TTL = 300

def get_cached(key):
    if key in _cache:
        entry = _cache[key]
        if time.time() - entry["ts"] < CACHE_TTL:
            return entry["data"]
        del _cache[key]
    return None

def set_cache(key, data):
    _cache[key] = {"data": data, "ts": time.time()}

# =====================================================================
# 4. CSV VERİ YÜKLEYİCİLER (DATA FETCH)
# =====================================================================
def fetch_all_data_for_line(line_code: str, hour: int, minute: int):
    try:
        stops = pd.read_csv("bus_stops.csv")
        flow = pd.read_csv("passenger_flow.csv")
        weather = pd.read_csv("weather_observations.csv")
        trips = pd.read_csv("bus_trips.csv")

        line_stops = stops[stops['line_id'] == line_code]
        if line_stops.empty:
            return {"error": f"{line_code} hattı verilere işlenmemiş."}

        line_trips = trips[trips['line_id'] == line_code]
        avg_occupancy = int(line_trips['avg_occupancy_pct'].mean()) if not line_trips.empty else 0
        avg_monthly_delay = round(line_trips['total_delay_min'].mean(), 1) if not line_trips.empty else 0

        current_weather = weather.iloc[0]['weather_condition']

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
        return {"error": f"Veri parse hatasi: {str(e)}"}


def fetch_next_buses_data(line_code: str, stop_id: str, hour: int):
    try:
        arrivals = pd.read_csv("stop_arrivals.csv")
        
        stop_data = arrivals[(arrivals['line_id'] == line_code) & (arrivals['stop_id'] == stop_id)]
        if stop_data.empty:
            stop_data = arrivals[arrivals['line_id'] == line_code]
        
        if stop_data.empty:
            return {"error": f"{line_code} için veri yok."}
        
        hour_data = stop_data[stop_data['hour_of_day'] == hour]
        if hour_data.empty:
            hour_data = stop_data 
        
        recent = hour_data.tail(10)
        
        avg_wait = round(recent['minutes_to_next_bus'].mean(), 1)
        avg_delay = round(recent['delay_min'].mean(), 1)
        avg_passengers = int(recent['passengers_waiting'].mean())
        weather = recent['weather_condition'].iloc[-1] if 'weather_condition' in recent.columns else "clear"
        traffic = recent['traffic_level'].iloc[-1] if 'traffic_level' in recent.columns else "normal"
        
        return {
            "line_id": line_code,
            "stop_id": stop_id,
            "hour": hour,
            "avg_minutes_to_next_bus": avg_wait,
            "avg_delay_min": avg_delay,
            "avg_passengers_waiting": avg_passengers,
            "weather": weather,
            "traffic_level": traffic,
            "sample_count": len(recent)
        }
    except Exception as e:
        return {"error": str(e)}


# =====================================================================
# 5. DİNAMİK YEDEKLEME ALGORİTMASI (GEMINI KOTA AŞIMI İÇİN)
# =====================================================================
def generate_mock_prediction(raw_data, is_simulation=False, sim_hour=12):
    occupancy = raw_data.get("monthly_stats", {}).get("avg_occupancy_pct", 50)
    avg_delay = raw_data.get("monthly_stats", {}).get("avg_delay_min", 3.0)
    weather = raw_data.get("weather", "clear")
    crowding = raw_data.get("real_time_factors", {}).get("crowding_level", "Normal")
    
    weather_factor = {"rain": 1.5, "snow": 2.0, "fog": 1.3, "wind": 1.2, "cloudy": 1.1, "clear": 1.0}
    delay_multiplier = weather_factor.get(weather, 1.0)
    
    # Saate dayalı sentetik yoğunluk
    crowd_extra = 0
    if 7 <= sim_hour <= 9 or 17 <= sim_hour <= 19:
        crowd_extra = 4.5
        crowding = "busy"
    
    real_delay = round(max(1, avg_delay * delay_multiplier + crowd_extra + random.uniform(-1, 1)), 1)
    
    if real_delay > 8: color = "RED"
    elif real_delay > 4: color = "YELLOW"
    else: color = "GREEN"
    
    advices = {
        "RED": f"Hava {weather} ve saatten dolayı trafik yoğun, {real_delay:.0f} dk gecikme var.",
        "YELLOW": f"Trafik orta yoğunlukta, tahmini {real_delay:.0f} dk gecikme bekleniyor.",
        "GREEN": f"Hat şu an akıcı durumda, tahmini gecikme {real_delay:.0f} dk."
    }
    
    return {
        "real_time_delay_min": real_delay,
        "status_color": color,
        "passenger_advice": advices[color],
        "route_details": {"line": raw_data.get("line_name", "Hat"), "monthly_occupancy": f"%{occupancy}", "crowding_status": crowding},
        "is_fallback": True
    }


def generate_mock_next_buses(bus_data, sim_hour=12):
    weather = bus_data.get("weather", "clear")
    traffic = bus_data.get("traffic_level", "normal")
    
    base_interval = 8 if (7 <= sim_hour <= 9 or 17 <= sim_hour <= 19) else 15
    
    buses = []
    eta = round(random.uniform(1, 4), 1)
    for i in range(1, 4):
        step = round(base_interval * random.uniform(0.8, 1.3), 1)
        eta += step if i > 1 else step / 2
        crowd = random.choice(["quiet", "normal", "busy"])
        buses.append({
            "bus_order": i,
            "estimated_arrival_min": round(eta, 1),
            "crowding_forecast": crowd,
            "confidence": round(0.9 - (i * 0.1), 2)
        })
        
    return {
        "line_id": bus_data.get("line_id", "?"),
        "stop_id": bus_data.get("stop_id", "?"),
        "next_buses": buses,
        "weather": weather,
        "traffic_level": traffic,
        "is_fallback": True
    }


# =====================================================================
# 6. ROUTING 1 (DELAY PREDICTION)
# =====================================================================
@app.get("/predict")
def predict_delay(line_code: str, hour: int = None, minute: int = None):
    now = datetime.now()
    hour = hour if hour is not None else now.hour
    minute = minute if minute is not None else now.minute
    
    cache_key = f"predict_{line_code}_{hour}_{minute // 5}"
    cached = get_cached(cache_key)
    if cached:
        return cached

    raw_data = fetch_all_data_for_line(line_code, hour, minute)
    if "error" in raw_data:
        raise HTTPException(status_code=404, detail=raw_data["error"])

    prompt = f"""
    Sivas Smart City projesi için otobüs gecikmesini analiz et.
    Veri: {json.dumps(raw_data)}
    DİKKAT: Kullanıcı Saat {hour:02d}:{minute:02d} SİMÜLASYONU aramaktadır. Gecikmeyi ve tavsiyeyi o saate göre uyarla.
    ÇIKTI FORMU: SADECE JSON. BAŞKA METİN KULLANMA.
    {{
      "real_time_delay_min": 7,
      "status_color": "YELLOW",
      "passenger_advice": "Saat {hour:02d}:{minute:02d} mesai saati olduğu için yoğun trafikten ... dk etkileniyor",
      "route_details": {{"line": "{raw_data.get('line_name', '')}", "monthly_occupancy": "%55", "crowding_status": "busy"}}
    }}
    """
    try:
        response = client.models.generate_content(
            model="gemini-flash-latest",
            config=types.GenerateContentConfig(response_mime_type="application/json"),
            contents=prompt
        )
        result = json.loads(response.text)
        result["is_fallback"] = False
        set_cache(cache_key, result)
        return result
    except Exception as e:
        print(f"[UYARI] Gemini gecikme hatası: {e}")
        mock = generate_mock_prediction(raw_data, True, hour)
        set_cache(cache_key, mock)
        return mock


# =====================================================================
# 7. ROUTING 2 (NEXT BUSES)
# =====================================================================
@app.get("/next-buses")
def get_next_buses(line_code: str, stop_id: str, hour: int = None, minute: int = None):
    now = datetime.now()
    hour = hour if hour is not None else now.hour
    minute = minute if minute is not None else now.minute
    
    cache_key = f"nextbuses_{line_code}_{stop_id}_{hour}"
    cached = get_cached(cache_key)
    if cached:
        return cached

    bus_data = fetch_next_buses_data(line_code, stop_id, hour)
    if "error" in bus_data:
        raise HTTPException(status_code=404, detail=bus_data["error"])

    prompt = f"""
    Saat {hour:02d}:{minute:02d} simülasyonu yapan kullanıcıya sıradaki 3 otobüsün Sivas {stop_id} durağı varışını tahmin et.
    Durak verisi: {json.dumps(bus_data)}
    ÇIKTI FORMU (JSON ONLY):
    {{
      "line_id": "{line_code}", "stop_id": "{stop_id}",
      "next_buses": [
        {{"bus_order": 1, "estimated_arrival_min": 5.5, "crowding_forecast": "normal", "confidence": 0.88}}
      ],
      "weather": "clear", "traffic_level": "moderate"
    }}
    """
    try:
        response = client.models.generate_content(
            model="gemini-flash-latest",
            config=types.GenerateContentConfig(response_mime_type="application/json"),
            contents=prompt
        )
        result = json.loads(response.text)
        result["is_fallback"] = False
        set_cache(cache_key, result)
        return result
    except Exception as e:
        print(f"[UYARI] Gemini next-buses hatası: {e}")
        mock = generate_mock_next_buses(bus_data, hour)
        set_cache(cache_key, mock)
        return mock


# =====================================================================
# 8. MOCK JAVA LAYER - (STOPS)
# =====================================================================
@app.get("/api/stops")
def get_all_stops():
    try:
        stops_df = pd.read_csv("bus_stops.csv")
        
        # Orijinal Java Backend veritabanından çekilmişçesine maskele
        stopNamesMap = {
            "L01": ["Merkez Terminal", "Belediye Meydanı", "Cumhuriyet Caddesi", "Atatürk Bulvarı", "Hürriyet Parkı", "Gül Mahallesi", "Çamlık Durağı", "Yeni Mahalle", "Sağlık Ocağı", "Kültür Merkezi", "Stadyum", "Rektörlük", "Mühendislik Fakültesi", "Üniversite Kampüsü"],
            "L02": ["Sanayi Sitesi", "Fabrikalar Bölgesi", "İş Merkezi", "Organize Sanayi", "Köprübaşı", "Pazar Yeri", "Adliye", "Emniyet Müdürlüğü", "Devlet Hastanesi", "Acil Servis", "Hastane Ana Giriş"],
            "L03": ["Bağlar Mahallesi", "Bağlar Parkı", "Kooperatif", "Otogar", "PTT", "Çarşı Girişi", "Kapalı Çarşı", "Büyük Cami", "Çarşı Merkez"],
            "L04": ["Esentepe Terminal", "Esentepe Parkı", "Yıldız Mahallesi", "Güneş Sokak", "Bahçelievler", "Zafer Caddesi", "Kışla", "Spor Salonu", "AVM", "Postane", "Hükümet Konağı", "Meydan"],
            "L05": ["Şehirlerarası Terminal", "Terminal Çıkışı", "Yeni Yol", "Kavşak", "Sanayi Kavşağı", "Demir Çelik", "Lojmanlar", "İlkokul", "Ortaokul", "Lise", "Dershane Sokak", "Yurt", "Spor Tesisleri", "Kütüphane", "Kampüs Girişi", "Kampüs Merkez"]
        }
        
        result = []
        added_stops = set()
        
        for _, row in stops_df.iterrows():
            stop_id = row['stop_id']
            if stop_id in added_stops: continue
            
            line_id = row['line_id']
            seq = int(row['stop_sequence'])
            stop_name = stopNamesMap.get(line_id, [])[seq - 1] if line_id in stopNamesMap and 0 < seq <= len(stopNamesMap[line_id]) else "Bilinmeyen Durak"
                    
            result.append({
                "stopId": stop_id,
                "stopName": stop_name,
                "stopLat": float(row['latitude']),
                "stopLon": float(row['longitude']),
                "lineId": line_id
            })
            added_stops.add(stop_id)
            
        return result
    except Exception as e:
        return []


if __name__ == "__main__":
    print("\n[BILGI] Yapay Zeka Servisi Başlatılıyor...")
    print("[BILGI] Java Bypass Aktif -> Alternatif Frontend Port: 8001")
    uvicorn.run(app, host="0.0.0.0", port=8001)