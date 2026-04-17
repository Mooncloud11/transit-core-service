import os
import pandas as pd
from datetime import datetime
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
from dotenv import load_dotenv
from google import genai
from google.genai import types
import orjson
from cachetools import TTLCache

# =====================================================================
# 1. SECRET SETTINGS AND MULTIPLE APIs
# =====================================================================
env_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
load_dotenv(dotenv_path=env_path)

api_keys_str = os.getenv("GEMINI_API_KEY")
if not api_keys_str:
    raise ValueError("CRITICAL ERROR: GEMINI_API_KEY not found in .env file!")
API_KEYS_LIST = api_keys_str.split(",")

# =====================================================================
# 2. FASTAPI (NATIVE HIGH-SPEED SERIALIZATION)
# =====================================================================
app = FastAPI(title="Sivas Transit AI Engine")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# =====================================================================
# 3. RAM CACHE AND O(1) HASH-MAP INDEXING
# =====================================================================
print("[INFO] Loading data into RAM as Hash-Map (Index)...")
try:
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    STOPS_DF = pd.read_csv(os.path.join(BASE_DIR, "bus_stops.csv"))
    FLOW_DF = pd.read_csv(os.path.join(BASE_DIR, "passenger_flow.csv"))
    WEATHER_DF = pd.read_csv(os.path.join(BASE_DIR, "weather_observations.csv"))
    TRIPS_DF = pd.read_csv(os.path.join(BASE_DIR, "bus_trips.csv"))
    ARRIVALS_DF = pd.read_csv(os.path.join(BASE_DIR, "stop_arrivals.csv"))

    STOPS_IDX = STOPS_DF.set_index('stop_id')
    TRIPS_IDX = TRIPS_DF.set_index('line_id')
    print("[INFO] O(1) Indexing successful! Engine is ready.")
except Exception as init_err:
    print(f"[CRITICAL ERROR] Failed to initialize data: {init_err}")

_cache = TTLCache(maxsize=2000, ttl=300)


def get_cached(key):
    return _cache.get(key)


def set_cache(key, data):
    _cache[key] = data


# =====================================================================
# 4. TRUE ASYNCHRONOUS AI (AIO CLIENT) WITH BULLETPROOF PARSING
# =====================================================================
async def generate_ai_content_with_fallback(prompt: str):
    for index, current_key in enumerate(API_KEYS_LIST):
        try:
            temp_client = genai.Client(api_key=current_key)
            response = await temp_client.aio.models.generate_content(
                model="gemini-flash-latest",
                config=types.GenerateContentConfig(response_mime_type="application/json"),
                contents=prompt
            )

            raw_text = response.text if response.text else "{}"

            # --- THE BULLETPROOF MARKDOWN STRIPPER ---
            clean_text = raw_text.strip()
            if clean_text.startswith("```json"):
                clean_text = clean_text[7:]
            elif clean_text.startswith("```"):
                clean_text = clean_text[3:]

            if clean_text.endswith("```"):
                clean_text = clean_text[:-3]

            clean_text = clean_text.strip()
            # -----------------------------------------

            return orjson.loads(clean_text.encode('utf-8'))

        except Exception as api_err:
            if index == len(API_KEYS_LIST) - 1:
                raise RuntimeError(f"API Keys exhausted or parsing failed: {api_err}")
    return {}


# =====================================================================
# 5. O(1) DATA FETCHING FUNCTIONS
# =====================================================================
def fetch_all_data(line_code: str, hour: int, minute: int):
    try:
        avg_occupancy = 0
        avg_delay = 0.0
        if line_code in TRIPS_IDX.index:
            line_trips = TRIPS_IDX.loc[line_code]
            if isinstance(line_trips, pd.DataFrame):
                avg_occupancy = int(line_trips['avg_occupancy_pct'].mean())
                avg_delay = float(round(line_trips['total_delay_min'].mean(), 1))
            else:
                avg_occupancy = int(line_trips['avg_occupancy_pct'])
                avg_delay = float(round(line_trips['total_delay_min'], 1))

        current_weather = str(WEATHER_DF.iloc[0]['weather_condition']) if not WEATHER_DF.empty else "Unknown"

        line_stops = STOPS_DF[STOPS_DF['line_id'] == line_code]
        line_name = str(line_stops.iloc[0]['line_name']) if not line_stops.empty else line_code

        return {
            "line_id": line_code, "line_name": line_name, "time": f"{hour:02d}:{minute:02d}",
            "weather": current_weather, "occ": avg_occupancy, "delay": avg_delay,
            "stop": "All", "crowd": "Unknown", "pass": 0
        }
    except Exception as data_err:
        return {"system_error": str(data_err)}


def fetch_next_buses_data(line_code: str, stop_id: str, hour: int):
    try:
        sd = ARRIVALS_DF[(ARRIVALS_DF['line_id'] == line_code) & (ARRIVALS_DF['stop_id'] == stop_id)]
        if sd.empty:
            sd = ARRIVALS_DF[ARRIVALS_DF['line_id'] == line_code]
        if sd.empty:
            return {"error": "No data"}

        hd = sd[sd['hour_of_day'] == hour]
        if hd.empty:
            hd = sd
        rc = hd.tail(5)

        return {
            "line_id": line_code, "stop_id": stop_id, "hour": hour,
            "wait": float(round(rc['minutes_to_next_bus'].mean(), 1)),
            "weather": str(rc['weather_condition'].iloc[-1]) if 'weather_condition' in rc.columns else "clear"
        }
    except Exception as fetch_err:
        return {"error": str(fetch_err)}


# =====================================================================
# 6. HONEST DATABASE FALLBACK
# =====================================================================
def generate_database_fallback_prediction(raw_data):
    return {
        "real_time_delay_min": raw_data.get('delay', 0),
        "status_color": "YELLOW",
        "passenger_advice": f"AI offline. Scheduled data: Route delay is {raw_data.get('delay', 0)} mins.",
        "route_details": {
            "line": raw_data.get("line_name", ""),
            "monthly_occupancy": f"%{raw_data.get('occ', 0)}",
            "crowding_status": raw_data.get("crowd", "")
        },
        "is_fallback": True
    }


def generate_database_fallback_next_buses(bus_data):
    buses = [
        {
            "bus_order": i,
            "estimated_arrival_min": round(bus_data.get("wait", 15.0) * i, 1),
            "crowding_forecast": "AI Offline",
            "confidence": 0.0
        } for i in range(1, 4)
    ]
    return {
        "line_id": bus_data.get("line_id", "?"),
        "stop_id": bus_data.get("stop_id", "?"),
        "next_buses": buses,
        "weather": bus_data.get("weather", ""),
        "traffic_level": "Unknown",
        "is_fallback": True
    }


# =====================================================================
# 7. HEALTH CHECK (FOR JAVA BACKEND PING)
# =====================================================================
@app.get("/")
async def health_check():
    """Java backend expects a 200 OK response from http://localhost:8000"""
    return {"status": "ok"}


# =====================================================================
# 8. ROUTING 1
# =====================================================================
@app.get("/predict")
async def predict_delay(line_code: str, hour: int = None, minute: int = None):
    now = datetime.now()
    h = hour if hour is not None else now.hour
    m = minute if minute is not None else now.minute

    cache_key = f"p_{line_code}_{h}_{m // 5}"
    cached = get_cached(cache_key)
    if cached:
        return cached

    raw_data = fetch_all_data(line_code, h, m)
    if "system_error" in raw_data:
        raise HTTPException(status_code=500, detail=raw_data["system_error"])

    expected_output = {
        "real_time_delay_min": 7.5,
        "status_color": "YELLOW",
        "passenger_advice": "Short English advice.",
        "route_details": {
            "line": raw_data.get('line_name', ''),
            "monthly_occupancy": "%55",
            "crowding_status": "busy"
        }
    }

    prompt = f"Transit JSON. Data:{orjson.dumps(raw_data).decode('utf-8')} Return strictly JSON:\n{orjson.dumps(expected_output).decode('utf-8')}"

    try:
        result = await generate_ai_content_with_fallback(prompt)
        result["is_fallback"] = False
        set_cache(cache_key, result)
        return result
    except Exception:
        db_fb = generate_database_fallback_prediction(raw_data)
        set_cache(cache_key, db_fb)
        return db_fb


# =====================================================================
# 9. ROUTING 2
# =====================================================================
@app.get("/next-buses")
async def get_next_buses(line_code: str, stop_id: str, hour: int = None, minute: int = None):
    now = datetime.now()
    h = hour if hour is not None else now.hour
    m = minute if minute is not None else now.minute

    cache_key = f"nb_{line_code}_{stop_id}_{h}_{m // 5}"
    cached = get_cached(cache_key)
    if cached:
        return cached

    bus_data = fetch_next_buses_data(line_code, stop_id, h)
    if "error" in bus_data:
        raise HTTPException(status_code=404, detail=bus_data["error"])

    expected_output = {
        "line_id": line_code,
        "stop_id": stop_id,
        "next_buses": [
            {
                "bus_order": 1,
                "estimated_arrival_min": 5.5,
                "crowding_forecast": "normal",
                "confidence": 0.88
            }
        ],
        "weather": "clear",
        "traffic_level": "moderate"
    }

    prompt = f"Next 3 buses JSON. Stop:{stop_id}. Data:{orjson.dumps(bus_data).decode('utf-8')} Return strictly JSON:\n{orjson.dumps(expected_output).decode('utf-8')}"

    try:
        result = await generate_ai_content_with_fallback(prompt)
        result["is_fallback"] = False
        set_cache(cache_key, result)
        return result
    except Exception:
        db_fb = generate_database_fallback_next_buses(bus_data)
        set_cache(cache_key, db_fb)
        return db_fb


if __name__ == "__main__":
    print(f"\n[INFO] {len(API_KEYS_LIST)} API Keys active.")
    print("[INFO] AI Engine running on port 8000.")
    uvicorn.run(app, host="0.0.0.0", port=8000)