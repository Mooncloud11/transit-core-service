# 🚌 Transit Core Service
**Transit Core Service** is a high-performance, AI-powered public transit tracking and prediction system built with modern web technologies. The project features a **Premium Dark Glassmorphism** user interface, a robust **Spring Boot** bridge server, and a physics-based prediction engine powered by **Groq AI (Llama 3.1)**.
---
## ✨ Features
* **AI-Powered Predictions (AI ETA):** Utilizes the Groq API (Llama 3.1) to predict real-time bus delays and line crowding (green, yellow, red) based on historical CSV data.
* **Physics-Based Simulation:** Calculates realistic arrival times in O(1) complexity using the AI's deviation factor combined with real distances between stops and historical speed metrics.
* **"Next-Bus" Algorithm:** Intelligently detects if a bus has already passed your current stop and automatically calculates the precise ETA for the *next* scheduled bus departing from the terminal.
* **Fail-Safe & Fallback Architecture:** If the Python AI Engine crashes or API limits are reached, the Java Backend automatically provides "Fallback" data (historical averages) to ensure a seamless and uninterrupted user experience.
* **Premium Interface:** Offers a unique user experience with a modern "Dark Mode" color palette, micro-animations, Glassmorphism elements, and animated origin/destination indicators.
---
## 🏗️ Architecture
The project consists of 3 main components:
1. **Frontend:** Built with Vanilla HTML, CSS, and JavaScript (`map.js`, `route-detail.js`). Features background API polling for live updates and is 100% localized in English.
2. **Backend:** Powered by Spring Boot (Java). Acts as a secure proxy bridge between the frontend and the AI Engine. Includes integration with an H2 Database and implements caching mechanisms for performance optimization.
3. **AI Engine:** A FastAPI-based Python microservice. It indexes `bus_trips.csv` and `bus_stops.csv` in memory for O(1) physics calculations and handles LLM predictions via the Async Groq API.
---
## 🚀 Getting Started
### 1. Python AI Engine Setup
The AI engine is the heart of the prediction system and must be started first.
```bash
cd ai-engine
# Install required dependencies
pip install -r requirements.txt
# Create a .env file and add your Groq API key
echo "GROQ_API_KEY=your_api_key_here" > .env
# Start the server (Default: http://localhost:8000)
python main.py
2. Spring Boot Backend Setup
The Java Backend proxies requests to the AI service, providing security and fallback capabilities.

bash
cd backend
# Build and run the project using Maven
mvn clean install
mvn spring-boot:run
Note: The backend runs on port http://localhost:8080 by default.

3. Frontend Setup
No build step or configuration is required. Simply launch the frontend directory using a Live Server (e.g., the VS Code extension).

bash
cd frontend
# If using VS Code, right-click index.html and select "Open with Live Server".
🛠️ API Endpoints
AI Engine (Python - 8000)
GET /health : Service health status.
GET /predict?line_code=L01 : Dynamic delay prediction and ETA array for the entire line.
GET /next-buses?line_code=L01&stop_id=S01 : Estimated arrival times for the next 3 buses following the active bus.
Backend (Java - 8080)
GET /actuator/health : Backend health status (Polled by the frontend every 30 seconds).
GET /api/transit/predict/{lineId} : Cached and formatted line prediction tailored for the frontend client.
👨‍💻 Technologies Used
Frontend: HTML5, CSS3 (Glassmorphism), Vanilla JavaScript ES6+
Backend: Java 17, Spring Boot, Spring Cache, H2 Database
AI Engine: Python 3.10+, FastAPI, Pandas, Groq API (Llama 3.1)
