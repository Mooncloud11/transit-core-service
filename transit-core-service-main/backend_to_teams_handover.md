# 🚀 Backend Release Notes & Team Handover

Merhaba takım! Backend modülünde yaptığımız yenilikleri, optimizasyonları ve bu değişiklikler doğrultusunda **Frontend** ve **Python AI Engine** ekiplerinin dikkate alması gereken teknik maddeleri aşağıda özetledim.

---

## 🛠️ 1. Backend'de Neler Değişti? (The Change Log)

1. **Bağımlılık ve Sürüm Düzeltmeleri:**
   - Spring Boot sürümü geçersiz bir versiyondan (`4.0.5`), kararlı ve modern olan `3.2.4`'e çekildi.
   - Hatalı test ve veritabanı kütüphaneleri temizlendi, yerine doğru ve standart olan `spring-boot-starter-test` eklendi.
2. **Kod Temizliği ve Klasör Yapısı:**
   - Eski tasarım olan ve kodu çiftleyen `TransitController` ile `TransitResponse` sınıfları **tamamen silindi**. Artık tüm trafik `PredictionController` üzerinden dönüyor.
   - Sistem başlatılırken okunan CSV verileri artık tehlikeli bir path üzerinden değil, güvenli `src/main/resources/data` klasöründen okunacak şekilde yapılandırıldı.
3. **Performans Optimizasyonu (Caffeine Cache):**
   - Python yapay zeka modeline sürekli yük binmemesi adına `PredictionService` içerisindeki tüm API isteklerine (`/api/predict`) **Caffeine Cache** eklendi. Aynı otobüs veya hatta gelen istekler ilk çekimden sonra **30 saniye boyunca (Time-To-Live)** doğrudan RAM'den 0 milisaniye ile çevrilecek. Python tarafına hiçbir istek düşmeyecek.
   - Ağ çağrıları eski nesil `RestTemplate` yerine Spring 3.2'nin getirdiği modern, asenkron ve yüksek performanslı `RestClient` yapısına göç ettirildi. 
4. **Veritabanı Kalıcılığı (Persistence):**
   - Yeniden başlatmalarda (restart) veya elektrik kesintilerinde veritabanı uçmasın diye In-Memory RAM kurulumundan vazgeçildi. Uygulama ayağa kalktığı anda proje kök dizininde `/data/transitdb.mv.db` isminde bir kalıcı (file-based) H2 veritabanı oluşacak.
5. **Swagger Dokümantasyonu ve Actuator:**
   - Uç noktalara Swagger UI destekli `@Operation` ve `@Tag` anatasyonları giydirildi.
   - Mikroservis iletişimlerinin sağlığı (Health Check) ve sistem metrikleri için projeye `spring-boot-starter-actuator` implemente edildi.

---

## 🎨 2. Frontend Sorumlularına Notlar (UI/UX)

Sevgili frontend ekibi, backend tarafı ciddi bir performans güncellemesi aldı. Sizin tarafınızda uygulamanız gereken noktalar:

* **Yeni Dokümantasyon Arayüzü (Swagger):** Backend'i başlattığınızda artık manuel istek atmak veya "Acaba bu Endpoint ne JSON dönüyordu?" diye düşünmek zorunda değilsiniz. Tarayıcıdan `http://localhost:8080/swagger-ui/index.html` linkine girip tüm uç noktaların yapısını, nasıl çalıştırılacağını ve açıklamalarını görebilirsiniz.
* **Endpoint Değişikliği:** Eskiden var olan `/api/transit` endpoint'i **kaldırılmıştır**. Lütfen kodunuzda herhangi bir yerde buraya istek atıyorsanız kaldırın ve standart olarak `PredictionController`'a ait olan `/api/predict/{lineCode}` adresini kullanmaya devam edin.
* **Health Check API (Hayatta Kalma Kontrolü):** Arayüz yüklenmeden evvel sunucunun çalışıp çalışmadığını test etmek için ping atmak yerine `http://localhost:8080/actuator/health` adresine bir HTTP GET isteği atın. Cevap `{"status": "UP"}` gelirse backend ve veritabanı %100 sağlıklı demektir.
* **Cache Limiti ve Polling:** Eskiden API'ye saniyede 1 kere sorsak bile her saniye arka plandan yapay zekaya ulaşıyordu. Şu anda 30 saniyelik bir Cache yerleştirdik. Bu nedenle frontend tarafındaki harita verilerini (veya sıradaki otobüsler listesini) her saniye (setInterval) güncellemeye çalışmayın, veriler 30 saniyeden önce değişmeyecektir. İdeal frontend `fetch` aralığınızı **15-30 saniye arası** bir değere sabitleyin.

---

## 🧠 3. AI-Engine (Python) Sorumlularına Notlar

Sevgili AI ekibi, modelinizi ve donanımınızı rahatlatacak bazı mimari önlemler aldık:

* **Daha Az CPU Yükü:** Backend tarafında uyguladığımız In-Memory Caching sayesinde, Java aynı durağa veya hatta dair tahmin isteklerini saniyelerce size iletmeyecek (TTL: 30s). Bu, Python tarafındaki Thread birikmelerini ve Gemini API'si kullanıyorsanız Rate Limit (Quota) 429 çakılmalarını yüksek oranda düşürecektir.
* **Data Dosyalarının Yeri Değişti:** Eskiden CSV dosyalarınız ve Java'nın okuduğu CSV dosyaları aynı `backend/predictive_transit_data` klasörü ile karışıyordu. Java'nın verilerini izole ederek `src/main/resources/data` içerisine kalıcılaştırdık. Eğer Python tarafı kendi içerisinde `backend/predictive_transit_data` klasörünün içindeki dosyalara bağımlıysa, kendi tarafınızdaki `/ai-engine` kök dizinine (zaten mevcut gözüküyor) odaklanın.
* **Bağlantı Linki Kontrolü:** Java Backend, sizi `AI_ENGINE_URL` ortam değişkeniyle, aksi halde varsayılan olarak `http://localhost:8000` üzerinden aramaktadır. Model prediction uç noktalarınızı (`/predict` ve `/next-buses`) uyanık ve bu port üzerinden yayın yapıyor (FastAPI/Uvicorn vb.) halde tutmayı unutmayın. Sağlık kontrolünüz Java üzerinden sizin bu portunuza ping atılarak değerlendirilecektir.

Hepinize iyi entegrasyonlar dilerim! 🤝
