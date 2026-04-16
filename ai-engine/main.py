import os
import json
import time
import pandas as pd
from datetime import datetime
from fastapi import FastAPI, HTTPException
from fastapi.responses import ORJSONResponse
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
from dotenv import load_dotenv
from google import genai
from google.genai import types

# =====================================================================
# 1. GİZLİ AYARLAR VE ÇOKLU API
# =====================================================================
load_dotenv()
api_keys_str = os.getenv("GEMINI_API_KEY")
if not api_keys_str:
    raise ValueError("KRİTİK HATA: .env dosyasında GEMINI_API_KEY bulunamadı!")
API_KEYS_LIST = api_keys_str.split(",")

# =====================================================================
# 2. FASTAPI (ORJSON İLE RUST HIZINDA YANITLAR)
# =====================================================================
# default_response_class=ORJSONResponse ile JSON oluşturma süresini minimize ediyoruz
app = FastAPI(title="Sivas Transit AI - ULTRA FAST", default_response_class=ORJSONResponse)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# =====================================================================
# 3. RAM CACHE VE O(1) HASH-MAP İNDEKSLEME (NANOSANİYE HIZI)
# =====================================================================
print("[BILGI] Veriler RAM'e Hash-Map (Index) olarak yükleniyor...")
try:
    # Verileri okuyup doğrudan arama yapılacak sütunları "index" yapıyoruz (O(1) hız için)
    STOPS_DF = pd.read_csv("bus_stops.csv")
    FLOW_DF = pd.read_csv("passenger_flow.csv")
    WEATHER_DF = pd.read_csv("weather_observations.csv")
    TRIPS_DF = pd.read_csv("bus_trips.csv")
    ARRIVALS_DF = pd.read_csv("stop_arrivals.csv")

    # İndeksleme işlemleri (Filtrelemeyi devreden çıkarır, nokta atışı bulur)
    STOPS_IDX = STOPS_DF.set_index('stop_id')
    TRIPS_IDX = TRIPS_DF.set_index('line_id')
    print("[BILGI] O(1) İndeksleme başarılı! Motor uçuşa hazır.")
except Exception as e:
    print(f"[KRİTİK HATA] CSV okunamadı: {e}")

_cache = {}
CACHE_TTL = 300


def get_cached(key):
    entry = _cache.get(key)
    if entry and time.time() - entry["ts"] < CACHE_TTL:
        return entry["data"]
    return None


def set_cache(key, data):
    _cache[key] = {"data": data, "ts": time.time()}


# =====================================================================
# 4. GERÇEK ASENKRON YAPAY ZEKA (AIO CLIENT)
# =====================================================================
async def generate_ai_content_with_fallback(prompt: str):
    for index, current_key in enumerate(API_KEYS_LIST):
        try:
            temp_client = genai.Client(api_key=current_key)
            # DİKKAT: .aio.models kullanıldı. API yanıt beklerken işlemci kilitlenmez!
            response = await temp_client.aio.models.generate_content(
                model="gemini-flash-latest",
                config=types.GenerateContentConfig(response_mime_type="application/json"),
                contents=prompt
            )
            return orjson.loads(response.text)  # orjson ile ışık hızında parse
        except Exception as e:
            if index == len(API_KEYS_LIST) - 1:
                raise Exception("API Keys exhausted.")


# =====================================================================
# 5. O(1) VERİ OKUMA FONKSİYONLARI
# =====================================================================
def fetch_all_data(start_stop_id: str, hour: int, minute: int):
    try:
        # .loc kullanarak O(1) hızında buluyoruz (Eski df[df == x] mantığı silindi)
        if start_stop_id not in STOPS_IDX.index:
            return {"system_error": "Origin not found."}

        stop_row = STOPS_IDX.loc[start_stop_id]

        # Eğer aynı ID'den birden fazla varsa ilkini al
        if isinstance(stop_row, pd.DataFrame): stop_row = stop_row.iloc[0]

        line_code = stop_row['line_id']

        avg_occupancy = 0
        avg_delay = 0.0
        if line_code in TRIPS_IDX.index:
            line_trips = TRIPS_IDX.loc[line_code]
            if isinstance(line_trips, pd.DataFrame):
                avg_occupancy = int(line_trips['avg_occupancy_pct'].mean())
                avg_delay = round(line_trips['total_delay_min'].mean(), 1)
            else:
                avg_occupancy = int(line_trips['avg_occupancy_pct'])
                avg_delay = round(line_trips['total_delay_min'], 1)

        current_weather = WEATHER_DF.iloc[0]['weather_condition']

        # Karmaşık sorgularda mecburi pandas filter kullanıyoruz ama data çok küçük
        stop_flow = FLOW_DF[(FLOW_DF['stop_id'] == start_stop_id) & (FLOW_DF['hour_of_day'] == hour)]
        crowding = stop_flow['crowding_level'].iloc[0] if not stop_flow.empty else "Unknown"
        passengers = int(stop_flow['avg_passengers_waiting'].iloc[0]) if not stop_flow.empty else 0

        return {
            "line_id": line_code, "line_name": stop_row['line_name'], "time": f"{hour:02d}:{minute:02d}",
            "weather": current_weather, "occ": avg_occupancy, "delay": avg_delay,
            "stop": start_stop_id, "crowd": crowding, "pass": passengers
        }
    except Exception as e:
        return {"system_error": str(e)}


def fetch_next_buses_data(line_code: str, stop_id: str, hour: int):
    try:
        sd = ARRIVALS_DF[(ARRIVALS_DF['line_id'] == line_code) & (ARRIVALS_DF['stop_id'] == stop_id)]
        if sd.empty: sd = ARRIVALS_DF[ARRIVALS_DF['line_id'] == line_code]
        if sd.empty: return {"error": "No data"}

        hd = sd[sd['hour_of_day'] == hour]
        if hd.empty: hd = sd
        rc = hd.tail(5)  # İhtiyacımız olan son 5 kayıt (daha az veri, daha hızlı işlem)

        return {
            "line_id": line_code, "stop_id": stop_id, "hour": hour,
            "wait": round(rc['minutes_to_next_bus'].mean(), 1),
            "weather": rc['weather_condition'].iloc[-1] if 'weather_condition' in rc.columns else "clear"
        }
    except Exception as e:
        return {"error": str(e)}


# =====================================================================
# 6. DÜRÜST VERİTABANI YEDEKLEMESİ
# =====================================================================
def generate_database_fallback_prediction(raw_data):
    return {
        "real_time_delay_min": 0, "status_color": "YELLOW",
        "passenger_advice": f"AI offline. Scheduled data: Route delay is {raw_data.get('delay', 0)} mins. Stop is '{raw_data.get('crowd', 'Unknown')}'.",
        "route_details": {"line": raw_data.get("line_name", ""), "monthly_occupancy": f"%{raw_data.get('occ', 0)}",
                          "crowding_status": raw_data.get("crowd", "")},
        "is_fallback": True
    }


def generate_database_fallback_next_buses(bus_data):
    buses = [{"bus_order": i, "estimated_arrival_min": round(bus_data.get("wait", 15.0) * i, 1),
              "crowding_forecast": "AI Offline", "confidence": 0.0} for i in range(1, 4)]
    return {"line_id": bus_data.get("line_id", "?"), "stop_id": bus_data.get("stop_id", "?"), "next_buses": buses,
            "weather": bus_data.get("weather", ""), "traffic_level": "Unknown", "is_fallback": True}


# =====================================================================
# 7. ROUTING 1 (SIKIŞTIRILMIŞ PROMPT İLE MAX HIZ)
# =====================================================================
import orjson  # Hızlı JSON kütüphanesini içe aktar


@app.get("/predict")
async def predict_delay(start_stop_id: str, end_stop_id: str, hour: int = None, minute: int = None):
    now = datetime.now()
    h = hour if hour is not None else now.hour
    m = minute if minute is not None else now.minute

    cache_key = f"p_{start_stop_id}_{h}_{m // 5}"  # Cache stringi kısaltıldı
    cached = get_cached(cache_key)
    if cached: return cached

    raw_data = fetch_all_data(start_stop_id, h, m)
    if "system_error" in raw_data: raise HTTPException(status_code=500, detail=raw_data["system_error"])

    # PROMPT MİNİFİKASYONU: LLM en hızlı kısa metinleri işler.
    prompt = f"Transit JSON. Data:{orjson.dumps(raw_data).decode()} Return strictly JSON:\n{{\"real_time_delay_min\":7,\"status_color\":\"YELLOW\",\"passenger_advice\":\"Short English advice.\",\"route_details\":{{\"line\":\"{raw_data.get('line_name', '')}\",\"monthly_occupancy\":\"%55\",\"crowding_status\":\"busy\"}}}}"

    try:
        result = await generate_ai_content_with_fallback(prompt)
        result["is_fallback"] = False
        set_cache(cache_key, result)
        return result
    except Exception as e:
        db_fb = generate_database_fallback_prediction(raw_data)
        set_cache(cache_key, db_fb)
        return db_fb


# =====================================================================
# 8. ROUTING 2
# =====================================================================
@app.get("/next-buses")
async def get_next_buses(line_code: str, stop_id: str, hour: int = None, minute: int = None):
    now = datetime.now()
    h = hour if hour is not None else now.hour

    cache_key = f"nb_{line_code}_{stop_id}_{h}"
    cached = get_cached(cache_key)
    if cached: return cached

    bus_data = fetch_next_buses_data(line_code, stop_id, h)
    if "error" in bus_data: raise HTTPException(status_code=404, detail=bus_data["error"])

    prompt = f"Next 3 buses JSON. Stop:{stop_id}. Data:{orjson.dumps(bus_data).decode()} Return strictly JSON:\n{{\"line_id\":\"{line_code}\",\"stop_id\":\"{stop_id}\",\"next_buses\":[{{\"bus_order\":1,\"estimated_arrival_min\":5.5,\"crowding_forecast\":\"normal\",\"confidence\":0.88}}],\"weather\":\"clear\",\"traffic_level\":\"moderate\"}}"
    try:
        result = await generate_ai_content_with_fallback(prompt)
        result["is_fallback"] = False
        set_cache(cache_key, result)
        return result
    except Exception as e:
        db_fb = generate_database_fallback_next_buses(bus_data)
        set_cache(cache_key, db_fb)
        return db_fb


# =====================================================================
# 9. JAVA BYPASS LAYER (DURAKLAR)
# =====================================================================
@app.get("/api/stops")
async def get_all_stops():
    # Frontend her açılışta çektiği için bu rotanın O(1) hafızadan dönmesi şart
    cache_key = "all_stops"
    cached = get_cached(cache_key)
    if cached: return cached

    try:
        stopNamesMap = {
            "L01": ["Merkez Terminal", "Belediye Meydanı", "Cumhuriyet Caddesi", "Atatürk Bulvarı", "Hürriyet Parkı",
                    "Gül Mahallesi", "Çamlık Durağı", "Yeni Mahalle", "Sağlık Ocağı", "Kültür Merkezi", "Stadyum",
                    "Rektörlük", "Mühendislik Fakültesi", "Üniversite Kampüsü"],
            "L02": ["Sanayi Sitesi", "Fabrikalar Bölgesi", "İş Merkezi", "Organize Sanayi", "Köprübaşı", "Pazar Yeri",
                    "Adliye", "Emniyet Müdürlüğü", "Devlet Hastanesi", "Acil Servis", "Hastane Ana Giriş"]
        }
        result = []
        added_stops = set()

        for _, row in STOPS_DF.iterrows():
            stop_id = row['stop_id']
            if stop_id in added_stops: continue

            line_id = row['line_id']
            seq = int(row['stop_sequence'])
            stop_name = stopNamesMap.get(line_id, [])[seq - 1] if line_id in stopNamesMap and 0 < seq <= len(
                stopNamesMap[line_id]) else "Durak"

            result.append({"stopId": stop_id, "stopName": stop_name, "stopLat": float(row['latitude']),
                           "stopLon": float(row['longitude']), "lineId": line_id})
            added_stops.add(stop_id)

        set_cache(cache_key, result)  # API/stops de cache'lendi!
        return result
    except Exception:
        return []


if __name__ == "__main__":
    print(f"\n[BILGI] {len(API_KEYS_LIST)} API Key aktif.")
    print("[BILGI] AIO, O(1) İndeksleme ve ORJSON devrede. Max Hız.")
    uvicorn.run(app, host="0.0.0.0", port=8000)