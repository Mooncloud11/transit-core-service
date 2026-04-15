// =========================================
//  DATA.JS – Tam Sürüm (UI + Backend API + AI Engine)
// =========================================

const TransitData = {
    hatlar: {
        L01: { id: "L01", ad: "Merkez - Üniversite", renk: "#4A90D9", duraklar: ["Merkez Terminal", "Belediye Meydanı", "Cumhuriyet Caddesi", "Atatürk Bulvarı", "Hürriyet Parkı", "Gül Mahallesi", "Çamlık Durağı", "Yeni Mahalle", "Sağlık Ocağı", "Kültür Merkezi", "Stadyum", "Rektörlük", "Mühendislik Fakültesi", "Üniversite Kampüsü"], aktifOtobus: { mevcutDurakIndex: 4, yogunluk: "red" }, tahminiSure: 3 },
        L02: { id: "L02", ad: "Sanayi - Hastane", renk: "#FF9500", duraklar: ["Sanayi Sitesi", "Fabrikalar Bölgesi", "İş Merkezi", "Organize Sanayi", "Köprübaşı", "Pazar Yeri", "Adliye", "Emniyet Müdürlüğü", "Devlet Hastanesi", "Acil Servis", "Hastane Ana Giriş"], aktifOtobus: { mevcutDurakIndex: 6, yogunluk: "yellow" }, tahminiSure: 5 },
        L03: { id: "L03", ad: "Bağlar - Çarşı", renk: "#34C759", duraklar: ["Bağlar Mahallesi", "Bağlar Parkı", "Kooperatif", "Otogar", "PTT", "Çarşı Girişi", "Kapalı Çarşı", "Büyük Cami", "Çarşı Merkez"], aktifOtobus: { mevcutDurakIndex: 3, yogunluk: "green" }, tahminiSure: 8 },
        L04: { id: "L04", ad: "Esentepe - Meydan", renk: "#AF52DE", duraklar: ["Esentepe Terminal", "Esentepe Parkı", "Yıldız Mahallesi", "Güneş Sokak", "Bahçelievler", "Zafer Caddesi", "Kışla", "Spor Salonu", "AVM", "Postane", "Hükümet Konağı", "Meydan"], aktifOtobus: { mevcutDurakIndex: 5, yogunluk: "yellow" }, tahminiSure: 6 },
        L05: { id: "L05", ad: "Terminal - Kampüs", renk: "#FF3B30", duraklar: ["Şehirlerarası Terminal", "Terminal Çıkışı", "Yeni Yol", "Kavşak", "Sanayi Kavşağı", "Demir Çelik", "Lojmanlar", "İlkokul", "Ortaokul", "Lise", "Dershane Sokak", "Yurt", "Spor Tesisleri", "Kütüphane", "Kampüs Girişi", "Kampüs Merkez"], aktifOtobus: { mevcutDurakIndex: 7, yogunluk: "red" }, tahminiSure: 4 }
    }
};

const TransitAPI = {
    baseUrl: 'http://localhost:8080',    // Java Backend
    aiBaseUrl: 'http://localhost:8000',  // Python AI Engine
    gercekDuraklar: [], 

    initStops: async function() {
        try {
            const response = await fetch(`${this.baseUrl}/api/stops`);
            if (response.ok) {
                this.gercekDuraklar = await response.json();
            }
        } catch (error) {
            console.warn("Backend kapalı, yerel veriler kullanılacak.");
        }
    },

    getTumDuraklar: function() {
        if (this.gercekDuraklar && this.gercekDuraklar.length > 0) {
            return [...new Set(this.gercekDuraklar.map(d => d.stop_name || d.stopName || d.id))];
        }
        const set = new Set();
        Object.values(TransitData.hatlar).forEach(hat => hat.duraklar.forEach(d => set.add(d)));
        return [...set].sort((a, b) => a.localeCompare(b, 'tr'));
    },

    getDurakHatlari: function(durakAdi) {
        const sonuc = [];
        Object.entries(TransitData.hatlar).forEach(([hatId, hat]) => {
            const idx = hat.duraklar.indexOf(durakAdi);
            if (idx !== -1) sonuc.push({ hatId, hat, durakIndex: idx });
        });
        return sonuc;
    },

    // YENİ: YAPAY ZEKA MOTORUNDAN CANLI VERİ ÇEKME FONKSİYONU
    getAITahmin: async function(hatId) {
        try {
            const now = new Date();
            const h = now.getHours();
            const m = now.getMinutes();
            
            const response = await fetch(`${this.aiBaseUrl}/predict?line_code=${hatId}&hour=${h}&minute=${m}`);
            
            if (response.ok) {
                const aiData = await response.json();
                console.log("AI Verisi Geldi:", aiData);
                return aiData;
            }
        } catch(e) {
            console.error("AI Tahmini alınamadı:", e);
        }
        return null;
    },

    saveSelection(data) { localStorage.setItem('transitSelection', JSON.stringify(data)); },
    getSelection() {
        try { return JSON.parse(localStorage.getItem('transitSelection')) || {}; }
        catch { return {}; }
    }
};

TransitAPI.initStops();