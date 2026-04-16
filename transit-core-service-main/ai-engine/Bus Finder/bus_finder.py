import sys
import subprocess
import json

# =====================================================================
# 0. AUTOMATIC LIBRARY INSTALLATION
# =====================================================================
try:
    from google import genai
    import pandas as pd
except ImportError:
    print("[INFO] Required libraries not found. Installing...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "google-genai", "pandas"])
    from google import genai
    import pandas as pd

    print("[INFO] Installation successful!\n")


# =====================================================================
# 1. BIG DATA: DETERMINISTIC TIME-BASED FETCHING (NEXT TRIP ALGORITHM)
# =====================================================================
def fetch_all_data_for_line(line_code, target_hour, target_minute):
    """
    Fetches data from databases filtered by the line_code.
    Calculates the exact closest upcoming trip based on the inputted time.
    """
    raw_data = {
        "line_code": line_code,
        "input_time": f"{target_hour:02d}:{target_minute:02d}"
    }

    target_mins_since_midnight = target_hour * 60 + target_minute

    try:
        # 1. BUS STOPS
        df_stops = pd.read_csv("bus_stops.csv")
        line_stops = df_stops[df_stops['line_id'] == line_code]
        raw_data["total_stops"] = len(line_stops)
        if len(line_stops) > 0:
            raw_data["line_name"] = line_stops.iloc[0]['line_name']

        # 2. BUS TRIPS (Find the EXACT next trip deterministically)
        df_trips = pd.read_csv("bus_trips.csv")
        df_trips['planned_departure'] = pd.to_datetime(df_trips['planned_departure'], errors='coerce')
        line_trips = df_trips[df_trips['line_id'] == line_code].copy()

        if line_trips.empty:
            return {"error": f"No trip data found for line {line_code}"}

        # Calculate minutes since midnight for all trips
        line_trips['mins_since_midnight'] = line_trips['planned_departure'].dt.hour * 60 + line_trips[
            'planned_departure'].dt.minute

        # Filter upcoming trips
        upcoming_trips = line_trips[line_trips['mins_since_midnight'] >= target_mins_since_midnight]

        if not upcoming_trips.empty:
            # Sort by time and date to ensure absolute determinism (always the exact same row)
            closest_trip = upcoming_trips.sort_values(by=['mins_since_midnight', 'date']).iloc[0]
        else:
            # Fallback: If time is too late (e.g., 23:50), wrap around and find the first trip of the next day
            closest_trip = line_trips.sort_values(by=['mins_since_midnight', 'date']).iloc[0]

        # Extract precise data from the found trip
        analyzed_hour = closest_trip['planned_departure'].hour
        analyzed_minute = closest_trip['planned_departure'].minute

        raw_data["tour_number"] = str(closest_trip['trip_id'])
        raw_data["closest_trip_time"] = f"{analyzed_hour:02d}:{analyzed_minute:02d}"
        raw_data["avg_historical_delay_min"] = round(line_trips['total_delay_min'].mean(), 2)
        raw_data["common_traffic_level"] = closest_trip['traffic_level']

        # 3. PASSENGER FLOW (Filtered by the exact hour of the closest trip)
        df_flow = pd.read_csv("passenger_flow.csv")
        line_flow = df_flow[(df_flow['line_id'] == line_code) & (df_flow['hour_of_day'] == analyzed_hour)]
        if line_flow.empty: line_flow = df_flow[df_flow['line_id'] == line_code]

        if not line_flow.empty:
            raw_data["avg_passengers_waiting"] = round(line_flow['avg_passengers_waiting'].mean(), 1)
            raw_data["most_common_crowding"] = line_flow['crowding_level'].mode()[0]

        # 4. STOP ARRIVALS (Filtered by the exact hour)
        df_arrivals = pd.read_csv("stop_arrivals.csv")
        line_arrivals = df_arrivals[
            (df_arrivals['line_id'] == line_code) & (df_arrivals['hour_of_day'] == analyzed_hour)]
        if line_arrivals.empty: line_arrivals = df_arrivals[df_arrivals['line_id'] == line_code]

        if not line_arrivals.empty:
            raw_data["avg_minutes_to_next_bus"] = round(line_arrivals['minutes_to_next_bus'].mean(), 1)

        # 5. WEATHER OBSERVATIONS (Filtered by the exact hour)
        df_weather = pd.read_csv("weather_observations.csv")
        df_weather['timestamp'] = pd.to_datetime(df_weather['timestamp'], errors='coerce')
        hour_weather = df_weather[df_weather['timestamp'].dt.hour == analyzed_hour]
        if hour_weather.empty: hour_weather = df_weather

        if not hour_weather.empty:
            raw_data["current_weather"] = hour_weather['weather_condition'].mode()[0]
            raw_data["transit_delay_risk"] = round(float(hour_weather['transit_delay_risk'].mean()), 3)
            raw_data["passenger_demand_multiplier"] = round(float(hour_weather['passenger_demand_multiplier'].mean()),
                                                            2)

        return raw_data
    except Exception as e:
        return {"error": f"Error reading databases: {str(e)}"}


# =====================================================================
# 2. AI CALCULATION ENGINE (STRICT JSON OUTPUT)
# =====================================================================
class GeminiCalculator:
    def __init__(self, api_keys, models):
        self.api_keys = api_keys
        self.models = models
        self.current_key_idx = 0
        self.current_model_idx = 0

    def analyze_and_calculate(self, raw_data):
        prompt = f"""
        Using the raw data below, perform 2 mathematical calculations and return the result ONLY IN A VALID JSON FORMAT. 
        Do not use any greetings, markdown, explanations, or extra text.
        ALL generated text MUST be in English.
        Take into consideration the 'closest_trip_time' and 'current_weather' when generating advices.

        RAW DATA:
        {json.dumps(raw_data, ensure_ascii=False)}

        CALCULATIONS:
        1. real_time_delay_min = (avg_historical_delay_min * (1 + transit_delay_risk)) -> Round to 2 decimal places.
        2. estimated_passengers = (avg_passengers_waiting * passenger_demand_multiplier) -> Round to the nearest integer.

        PLEASE RETURN ONLY A JSON MATCHING THE EXACT STRUCTURE BELOW:
        {{
            "tour_number": "{raw_data.get('tour_number', 'Unknown')}",
            "line_code": "{raw_data.get('line_code', 'Unknown')}",
            "closest_trip_time": "{raw_data.get('closest_trip_time', 'Unknown')}",
            "current_weather": "{raw_data.get('current_weather', 'Unknown')}",
            "real_time_delay_min": 0.00,
            "estimated_passengers": 0,
            "passenger_advice": "One short sentence of advice for passengers.",
            "driver_advice": "One short sentence of advice for the driver."
        }}
        """

        while self.current_key_idx < len(self.api_keys):
            try:
                client = genai.Client(api_key=self.api_keys[self.current_key_idx])
            except Exception as e:
                self.current_key_idx += 1
                continue

            while self.current_model_idx < len(self.models):
                model_name = self.models[self.current_model_idx]
                try:
                    response = client.models.generate_content(
                        model=model_name,
                        contents=prompt
                    )

                    response_text = response.text.strip()
                    if response_text.startswith("```json"):
                        response_text = response_text[7:]
                    if response_text.endswith("```"):
                        response_text = response_text[:-3]

                    ai_json = json.loads(response_text.strip())
                    return ai_json

                except Exception as e:
                    print(f"[WARNING] Model {model_name} failed. Error: {e}")
                    self.current_model_idx += 1

            self.current_key_idx += 1
            self.current_model_idx = 0

        return {"error": "AI systems are unreachable. Please use local data."}


# =====================================================================
# 3. MAIN EXECUTION LOGIC
# =====================================================================
def main():
    API_KEYS = [
        "AIzaSyCBKX0Fs1l9-Pb6p8szWBpLfHBE2XattaI"  # Kendi API anahtarını eklemeyi unutma
    ]

    MODELS = ["gemini-flash-latest"]

    print("=" * 50)
    print("🚀 SMART BUS PREDICTION SYSTEM 🚀")
    print("=" * 50)

    # 1. Get Line Code
    bus_code = input("Enter the bus code to analyze (e.g., L01, L02): ").strip().upper()
    if not bus_code:
        print("Invalid bus code!")
        return

    # 2. Get Time with Decimal Parsing Support
    time_input = input("Enter the time (e.g., 7.30, 08:15, or simply 8): ").strip()

    # Parse time logic (supports '7.30', '7:30', '7,30', '8')
    time_input_cleaned = time_input.replace(':', '.').replace(',', '.')

    try:
        if '.' in time_input_cleaned:
            parts = time_input_cleaned.split('.')
            target_hour = int(parts[0])
            # Handle user typing "7.3" as 7:30
            if len(parts[1]) == 1:
                target_minute = int(parts[1] + "0")
            else:
                target_minute = int(parts[1][:2])
        else:
            target_hour = int(time_input_cleaned)
            target_minute = 0

        if not (0 <= target_hour <= 23) or not (0 <= target_minute <= 59):
            raise ValueError

    except ValueError:
        print("[WARNING] Invalid time format entered. Defaulting to 08:00.")
        target_hour = 8
        target_minute = 0

    print(f"\n[INFO] Finding the closest upcoming trip for {bus_code} after {target_hour:02d}:{target_minute:02d}...")

    raw_data = fetch_all_data_for_line(bus_code, target_hour, target_minute)

    if "error" in raw_data:
        print(raw_data["error"])
        return

    print("\n" + "=" * 50)
    print("📊 STAGE 1: RAW DATA EXTRACTED BY SYSTEM")
    print("=" * 50)
    print(json.dumps(raw_data, indent=4, ensure_ascii=False))

    print("\n" + "=" * 50)
    print("🤖 STAGE 2: AI CALCULATION (SIMPLIFIED JSON)")
    print("=" * 50)
    print("Calculating, please wait...\n")

    ai_calculator = GeminiCalculator(API_KEYS, MODELS)
    ai_analysis_json = ai_calculator.analyze_and_calculate(raw_data)

    print(json.dumps(ai_analysis_json, indent=4, ensure_ascii=False))

    print("\n" + "=" * 50)
    print("✅ PROCESS COMPLETED")


if __name__ == "__main__":
    main()