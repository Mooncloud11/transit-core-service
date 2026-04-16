// =========================================
//  DATA.JS – Tam Sürüm v2 (Hat Kilitleme + Backend Proxy)
//  Frontend SADECE Java Backend'e (:8080) bağlanır.
//  AI verisi Backend üzerinden gelir.
// =========================================

const TransitData = {
    hatlar: {
        L01: { id: "L01", ad: "Merkez - Üniversite", renk: "#4A90D9", duraklar: ["Merkez Terminal", "Belediye Meydanı", "Cumhuriyet Caddesi", "Atatürk Bulvarı", "Hürriyet Parkı", "Gül Mahallesi", "Çamlık Durağı", "Yeni Mahalle", "Sağlık Ocağı", "Kültür Merkezi", "Stadyum", "Rektörlük", "Mühendislik Fakültesi", "Üniversite Kampüsü"], aktifOtobus: { mevcutDurakIndex: 4, yogunluk: "red" }, tahminiSure: 3 },
        L02: { id: "L02", ad: "Sanayi - Hastane", renk: "#FF9500", duraklar: ["Sanayi Sitesi", "Fabrikalar Bölgesi", "İş Merkezi", "Organize Sanayi", "Köprübaşı", "Pazar Yeri", "Adliye", "Emniyet Müdürlüğü", "Devlet Hastanesi", "Acil Servis", "Hastane Ana Giriş"], aktifOtobus: { mevcutDurakIndex: 6, yogunluk: "yellow" }, tahminiSure: 5 },
        L03: { id: "L03", ad: "Bağlar - Çarşı", renk: "#34C759", duraklar: ["Bağlar Mahallesi", "Bağlar Parkı", "Kooperatif", "Otogar", "PTT", "Çarşı Girişi", "Kapalı Çarşı", "Büyük Cami", "Çarşı Merkez"], aktifOtobus: { mevcutDurakIndex: 3, yogunluk: "green" }, tahminiSure: 8 },
        L04: { id: "L04", ad: "Esentepe - Meydan", renk: "#AF52DE", duraklar: ["Esentepe Terminal", "Esentepe Parkı", "Yıldız Mahallesi", "Güneş Sokak", "Bahçelievler", "Zafer Caddesi", "Kışla", "Spor Salonu", "AVM", "Postane", "Hükümet Konağı", "Meydan"], aktifOtobus: { mevcutDurakIndex: 5, yogunluk: "yellow" }, tahminiSure: 6 },
        L05: { id: "L05", ad: "Terminal - Kampüs", renk: "#FF3B30", duraklar: ["Şehirlerarası Terminal", "Terminal Çıkışı", "Yeni Yol", "Kavşak", "Sanayi Kavşağı", "Demir Çelik", "Lojmanlar", "İlkokul", "Ortaokul", "Lise", "Dershane Sokak", "Yurt", "Spor Tesisleri", "Kütüphane", "Kampüs Girişi", "Kampüs Merkez"], aktifOtobus: { mevcutDurakIndex: 7, yogunluk: "red" }, tahminiSure: 4 }
    },

    // Hat – StopID eşleştirme tablosu (CSV'deki stop_id'ler ile eşleşir)
    durakStopIdMap: {
        L01: ["STP-L01-01", "STP-L01-02", "STP-L01-03", "STP-L01-04", "STP-L01-05", "STP-L01-06", "STP-L01-07", "STP-L01-08", "STP-L01-09", "STP-L01-10", "STP-L01-11", "STP-L01-12", "STP-L01-13", "STP-L01-14"],
        L02: ["STP-L02-01", "STP-L02-02", "STP-L02-03", "STP-L02-04", "STP-L02-05", "STP-L02-06", "STP-L02-07", "STP-L02-08", "STP-L02-09", "STP-L02-10", "STP-L02-11"],
        L03: ["STP-L03-01", "STP-L03-02", "STP-L03-03", "STP-L03-04", "STP-L03-05", "STP-L03-06", "STP-L03-07", "STP-L03-08", "STP-L03-09"],
        L04: ["STP-L04-01", "STP-L04-02", "STP-L04-03", "STP-L04-04", "STP-L04-05", "STP-L04-06", "STP-L04-07", "STP-L04-08", "STP-L04-09", "STP-L04-10", "STP-L04-11", "STP-L04-12"],
        L05: ["STP-L05-01", "STP-L05-02", "STP-L05-03", "STP-L05-04", "STP-L05-05", "STP-L05-06", "STP-L05-07", "STP-L05-08", "STP-L05-09", "STP-L05-10", "STP-L05-11", "STP-L05-12", "STP-L05-13", "STP-L05-14", "STP-L05-15", "STP-L05-16"]
    }
};

const TransitAPI = {
    // ══════════════════════════════════════════
    //  TEK BAĞLANTI NOKTASI: JAVA BACKEND
    //  Frontend ASLA doğrudan Python AI'a bağlanmaz!
    // Java Backend kapalı olduğu için doğrudan Python FastAPI (AI Engine) portuna bağlandı.
    baseUrl: 'http://localhost:8080',
    gercekDuraklar: [],
    AI_TIMEOUT: 8000, // 8 saniye timeout

    // ── Backend'den Durakları Çek ──
    initStops: async function () {
        try {
            const controller = new AbortController();
            const timeout = setTimeout(() => controller.abort(), 5000);
            const response = await fetch(`${this.baseUrl}/api/stops`, { signal: controller.signal });
            clearTimeout(timeout);
            if (response.ok) {
                this.gercekDuraklar = await response.json();
            }
        } catch (error) {
            console.warn("Backend kapalı, yerel veriler kullanılacak.");
        }
    },

    getTumDuraklar: function () {
        if (this.gercekDuraklar && this.gercekDuraklar.length > 0) {
            const apiDuraklar = this.gercekDuraklar.map(d => d.stopName || d.stop_name || d.id);
            if (apiDuraklar.length > 0 && apiDuraklar[0] !== "Bilinmeyen Durak") {
                return [...new Set(apiDuraklar)].sort((a, b) => a.localeCompare(b, 'tr'));
            }
        }
        const set = new Set();
        Object.values(TransitData.hatlar).forEach(hat => hat.duraklar.forEach(d => set.add(d)));
        return [...set].sort((a, b) => a.localeCompare(b, 'tr'));
    },

    // ── Durak Hangi Hatlarda ──
    getDurakHatlari: function (durakAdi) {
        const sonuc = [];
        Object.entries(TransitData.hatlar).forEach(([hatId, hat]) => {
            const idx = hat.duraklar.indexOf(durakAdi);
            if (idx !== -1) sonuc.push({ hatId, hat, durakIndex: idx });
        });
        return sonuc;
    },

    // ── Hat Kilitleme: Belirli hattın duraklarını getir ──
    getHattinDuraklari: function (hatId) {
        const hat = TransitData.hatlar[hatId];
        return hat ? hat.duraklar : [];
    },

    // ── Duraktan Hat ID Bul ──
    getDurakHatId: function (durakAdi) {
        for (const [hatId, hat] of Object.entries(TransitData.hatlar)) {
            if (hat.duraklar.includes(durakAdi)) return hatId;
        }
        return null;
    },

    // ── Durak İsminden Stop ID Bul ──
    getStopId: function (durakAdi, hatId) {
        const hat = TransitData.hatlar[hatId];
        if (!hat) return null;
        const idx = hat.duraklar.indexOf(durakAdi);
        if (idx === -1) return null;
        const stopIds = TransitData.durakStopIdMap[hatId];
        return stopIds ? stopIds[idx] : null;
    },

    // ══════════════════════════════════════════
    //  AI TAHMİN VERİSİ – JAVA BACKEND ÜZERİNDEN
    //  Endpoint: GET /api/predict/{lineCode}
    // ══════════════════════════════════════════
    getAITahmin: async function (hatId, simHour, simMinute) {
        try {
            const controller = new AbortController();
            const timeout = setTimeout(() => controller.abort(), this.AI_TIMEOUT);

            let url = `${this.baseUrl}/api/predict/${hatId}`;
            if (simHour !== undefined && simMinute !== undefined && simHour !== null) {
                url += `?hour=${simHour}&minute=${simMinute}`;
            }

            const response = await fetch(url, { signal: controller.signal });
            clearTimeout(timeout);

            if (response.ok) {
                const aiData = await response.json();
                console.log("✅ AI Verisi (Backend üzerinden):", aiData);
                return aiData;
            } else {
                console.warn(`⚠️ Backend AI yanıtı ${response.status}:`, await response.text());
            }
        } catch (e) {
            if (e.name === 'AbortError') {
                console.warn("⏱ AI isteği zaman aşımına uğradı (8sn)");
            } else {
                console.error("❌ AI Tahmini alınamadı:", e);
            }
        }
        return null;
    },

    // ══════════════════════════════════════════
    //  SIRADAKİ OTOBÜSLER – JAVA BACKEND ÜZERİNDEN
    //  Endpoint: GET /api/predict/next-buses?lineCode=X&stopId=Y
    // ══════════════════════════════════════════
    getNextBuses: async function (hatId, stopId, simHour, simMinute) {
        try {
            const controller = new AbortController();
            const timeout = setTimeout(() => controller.abort(), this.AI_TIMEOUT);

            let url = `${this.baseUrl}/api/predict/next-buses?lineCode=${hatId}&stopId=${stopId}`;
            if (simHour !== undefined && simMinute !== undefined && simHour !== null) {
                url += `&hour=${simHour}&minute=${simMinute}`;
            }

            const response = await fetch(url, { signal: controller.signal });
            clearTimeout(timeout);

            if (response.ok) {
                const data = await response.json();
                console.log("✅ Sıradaki Otobüs Verisi:", data);
                return data;
            }
        } catch (e) {
            if (e.name === 'AbortError') {
                console.warn("⏱ Sıradaki otobüs isteği zaman aşımına uğradı");
            } else {
                console.error("❌ Sıradaki otobüs verisi alınamadı:", e);
            }
        }
        return null;
    },

    // ── Seçim Kaydetme/Okuma ──
    saveSelection(data) { localStorage.setItem('transitSelection', JSON.stringify(data)); },
    getSelection() {
        try { return JSON.parse(localStorage.getItem('transitSelection')) || {}; }
        catch { return {}; }
    }
};

TransitAPI.initStops();