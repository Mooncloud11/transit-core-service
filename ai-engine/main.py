import os
import json
import pandas as pd
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
from dotenv import load_dotenv
from google import genai
from google.genai import types

# =====================================================================
# 1. GİZLİ AYARLAR VE API KURULUMU
# =====================================================================
# .env dosyasını yükle
load_dotenv()

# Kasa içinden API key'i çek
API_KEY = os.getenv("GEMINI_API_KEY")
if not API_KEY:
    raise ValueError("KRİTİK HATA: .env dosyasında GEMINI_API_KEY bulunamadı!")

# Gemini 2.0 İstemcisini (Client) Başlat
client = genai.Client(api_key=API_KEY)

# =====================================================================
# 2. FASTAPI SUNUCU AYARLARI
# =====================================================================
app = FastAPI(title="Sivas Predictive Transit AI")

# Tarayıcı üzerinden doğrudan test edilebilmesi için CORS izni
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# =====================================================================
# 3. VERİ TOPLAMA MOTORU
# =====================================================================
def fetch_all_data_for_line(line_code: str, hour: int, minute: int):
    try:
        # CSV Dosyalarını Oku
        stops = pd.read_csv("bus_stops.csv")
        flow = pd.read_csv("passenger_flow.csv")
        weather = pd.read_csv("weather_observations.csv")
        trips = pd.read_csv("bus_trips.csv")

        # Hattın Duraklarını Bul
        line_stops = stops[stops['line_id'] == line_code]
        if line_stops.empty:
            return {"error": f"{line_code} hattı veritabanında bulunamadı."}

        # 1 Aylık İstatistiklerin Ortalaması (Doluluk ve Gecikme)
        line_trips = trips[trips['line_id'] == line_code]
        avg_occupancy = int(line_trips['avg_occupancy_pct'].mean()) if not line_trips.empty else 0
        avg_monthly_delay = round(line_trips['total_delay_min'].mean(), 1) if not line_trips.empty else 0

        # Hava Durumu (En güncel kayıt)
        current_weather = weather.iloc[0]['weather_condition']

        # Hattın Başlangıç Durağındaki Anlık Kalabalık
        first_stop_id = line_stops['stop_id'].iloc[0]
        stop_flow = flow[(flow['stop_id'] == first_stop_id) & (flow['hour_of_day'] == hour)]

        crowding = stop_flow['crowding_level'].iloc[0] if not stop_flow.empty else "Normal"
        passengers = int(stop_flow['avg_passengers_waiting'].iloc[0]) if not stop_flow.empty else 0

        # Toplanan Veriyi Paketle
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
    except FileNotFoundError as e:
        return {"error": f"CSV dosyası eksik: {str(e)}"}
    except Exception as e:
        return {"error": f"Veri işleme hatası: {str(e)}"}


# =====================================================================
# 4. API ENDPOINT (JAVA'NIN BAĞLANACAĞI YER)
# =====================================================================
@app.get("/predict")
def predict_delay(line_code: str, hour: int, minute: int):
    # 1. Ham Veriyi Topla
    raw_data = fetch_all_data_for_line(line_code, hour, minute)

    # Veri toplanırken hata olduysa Java'ya 404 döndür
    if "error" in raw_data:
        raise HTTPException(status_code=404, detail=raw_data["error"])

    # 2. Yapay Zeka Promptu (Sert Kurallı JSON İstiyoruz)
    prompt = f"""
    Sivas Smart City projesi için aşağıdaki otobüs transit verisini analiz et.
    Veri: {json.dumps(raw_data)}

    Görevler:
    - Hava durumu ve kalabalığa (real_time_factors) bakarak gerçek zamanlı gecikmeyi (dakika) tahmin et.
    - Duruma göre bir renk kodu belirle: 'GREEN' (akıcı), 'YELLOW' (orta yoğunluk), 'RED' (gecikmeli/kalabalık).
    - Yolcuya Türkçe, kısa, samimi ve yardımcı bir tavsiye cümlesi yaz (passenger_advice).

    ÇIKTI KURALLARI:
    AŞAĞIDAKİ FORMATTA SADECE JSON DÖN. BAŞKA HİÇBİR METİN VEYA MARKDOWN (```json) KULLANMA.

    {{
      "real_time_delay_min": 5,
      "status_color": "GREEN",
      "passenger_advice": "Hava yağmurlu olduğu için durak kalabalık, otobüsünüz 5 dakika gecikebilir. Şemsiyenizi unutmayın!",
      "route_details": {{
          "line": "{raw_data['line_name']}",
          "monthly_occupancy": "%{raw_data['monthly_stats']['avg_occupancy_pct']}",
          "crowding_status": "{raw_data['real_time_factors']['crowding_level']}"
      }}
    }}
    """

    try:
        # 3. Gemini 2.0 Flash'a İstek At
        response = client.models.generate_content(
            model="gemini-flash-latest",
            config=types.GenerateContentConfig(
                response_mime_type="application/json"  # Gemini'yi doğrudan JSON dönmeye zorlar
            ),
            contents=prompt
        )

        # 4. Gelen yanıtı Python sözlüğüne çevirip Java'ya gönder
        return json.loads(response.text)

    except json.JSONDecodeError:
        raise HTTPException(status_code=500, detail="Yapay Zeka düzgün bir JSON üretemedi.")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"AI Bağlantı Hatası: {str(e)}")


# =====================================================================
# 5. SUNUCUYU AYAĞA KALDIRMA BAŞLATICI
# =====================================================================
if __name__ == "__main__":
    print("\n[BILGI] Yapay Zeka Servisi Başlatılıyor...")
    print("[BILGI] Java Backend için dinlenen port: 8000")
    uvicorn.run(app, host="0.0.0.0", port=8000)