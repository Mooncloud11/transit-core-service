import sys
import json
import urllib.request
from datetime import datetime
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from fastapi.middleware.cors import CORSMiddleware

# =====================================================================
# AYARLAR & API KEY YÖNETİMİ
# =====================================================================
GEMINI_API_KEYS = [
    "AIzaSyAtUtdlcYEBACkezzAv5P065F_ciCDobkU"
]


class BusAI:
    def __init__(self, api_keys):
        self.api_keys = api_keys
        self.current_key_idx = 0

    def call_gemini(self, prompt):
        while self.current_key_idx < len(self.api_keys):
            api_key = self.api_keys[self.current_key_idx]

            if not api_key:
                return {"error": "Lütfen geçerli bir API Key girin."}

            url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key={api_key}"
            payload = {"contents": [{"parts": [{"text": prompt}]}]}
            headers = {'Content-Type': 'application/json'}

            try:
                req = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'), headers=headers)
                with urllib.request.urlopen(req) as response:
                    result = json.loads(response.read().decode())
                    text_response = result['candidates'][0]['content']['parts'][0]['text']

                    if text_response.startswith("```"):
                        text_response = text_response.strip("`").replace("json\n", "", 1).replace("json", "", 1)

                    # Gemini'den gelen metni Python Dict (JSON) objesine çeviriyoruz
                    return json.loads(text_response)

            except Exception as e:
                self.current_key_idx += 1
                if self.current_key_idx >= len(self.api_keys):
                    return {"error": f"Tüm API Keyler tükendi. Son Hata: {str(e)}"}


# =====================================================================
# VERİ İŞLEME
# =====================================================================
def get_stop_name(stop_id, stops_df):
    df = stops_df[stops_df['stop_id'] == stop_id]
    if df.empty: return stop_id
    return f"{df['line_name'].iloc[0]} ({str(df['stop_type'].iloc[0]).capitalize()})"


def prepare_ai_prompt(start_stop_id, end_stop_id):
    now = datetime.now()
    current_time = now.strftime("%H:%M")

    try:
        stops = pd.read_csv("bus_stops.csv")
        flow = pd.read_csv("passenger_flow.csv")
        weather = pd.read_csv("weather_observations.csv")
    except Exception as e:
        raise Exception(f"CSV dosyası okunamadı: {e}")

    start_data = stops[stops['stop_id'] == start_stop_id]
    end_data = stops[stops['stop_id'] == end_stop_id]

    start_line = start_data['line_id'].iloc[0] if not start_data.empty else "Bilinmiyor"
    end_line = end_data['line_id'].iloc[0] if not end_data.empty else "Bilinmiyor"

    start_name = get_stop_name(start_stop_id, stops)
    end_name = get_stop_name(end_stop_id, stops)

    weather_cond = weather.iloc[0]['weather_condition']
    is_transfer = (start_line != end_line)
    transfer_stop_id = "STP-L04-12" if is_transfer else "None"
    transfer_name = get_stop_name(transfer_stop_id, stops) if is_transfer else "None"

    # AI PROMPT: Frontend için katı JSON kuralları ve Renk Sistemi
    prompt = f"""
    Route: {start_name} -> {transfer_name if is_transfer else ''} -> {end_name}
    Time: {current_time} | Weather: {weather_cond}
    Transfer Needed: {is_transfer}

    Output ONLY a strict JSON. NO markdown format.
    Use 'status_color' strictly as 'GREEN' (low crowding), 'YELLOW' (moderate), or 'RED' (high crowding).
    Format exactly like this:
    {{
      "route_steps": [
        {{
          "step_type": "Baslangic",
          "stop_name": "...",
          "planned_time": "HH:MM",
          "real_time": "HH:MM",
          "status_color": "GREEN|YELLOW|RED",
          "status_text": "Sakin|Orta|Kalabalık"
        }}
      ],
      "ai_comment": "Yolculuk özeti."
    }}
    """
    return prompt


# =====================================================================
# FASTAPI KURULUMU
# =====================================================================
app = FastAPI(title="AI Bus Route API", version="1.0")

# Frontend'den (React, Vue vb.) gelen istekleri engellememesi için CORS ayarı
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

ai_service = BusAI(GEMINI_API_KEYS)


# Frontend'in bize göndereceği veri modeli
class RouteRequest(BaseModel):
    start_stop_id: str
    end_stop_id: str


@app.post("/api/v1/predict-route")
async def predict_route(request: RouteRequest):
    try:
        # 1. Prompt'u hazırla
        ai_prompt = prepare_ai_prompt(request.start_stop_id, request.end_stop_id)

        # 2. Gemini'ye sor ve JSON yanıtı al
        ai_response = ai_service.call_gemini(ai_prompt)

        # Eğer yapay zeka veya bağlantı hatası varsa
        if "error" in ai_response:
            raise HTTPException(status_code=500, detail=ai_response["error"])

        return ai_response

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))