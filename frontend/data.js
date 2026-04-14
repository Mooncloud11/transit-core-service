// =========================================
//  DATA.JS – Transit Core Service Veri Katmanı
//  Backend API bağlanana kadar mock data kullanılır
//  API bağlandığında TransitAPI metodları güncellenir
// =========================================

const TransitData = {
    hatlar: {
        L01: {
            id: "L01",
            ad: "Merkez - Üniversite",
            renk: "#4A90D9",
            duraklar: [
                "Merkez Terminal",
                "Belediye Meydanı",
                "Cumhuriyet Caddesi",
                "Atatürk Bulvarı",
                "Hürriyet Parkı",
                "Gül Mahallesi",
                "Çamlık Durağı",
                "Yeni Mahalle",
                "Sağlık Ocağı",
                "Kültür Merkezi",
                "Stadyum",
                "Rektörlük",
                "Mühendislik Fakültesi",
                "Üniversite Kampüsü"
            ],
            aktifOtobus: {
                mevcutDurakIndex: 4,
                yogunluk: "red"
            },
            tahminiSure: 3
        },
        L02: {
            id: "L02",
            ad: "Sanayi - Hastane",
            renk: "#FF9500",
            duraklar: [
                "Sanayi Sitesi",
                "Fabrikalar Bölgesi",
                "İş Merkezi",
                "Organize Sanayi",
                "Köprübaşı",
                "Pazar Yeri",
                "Adliye",
                "Emniyet Müdürlüğü",
                "Devlet Hastanesi",
                "Acil Servis",
                "Hastane Ana Giriş"
            ],
            aktifOtobus: {
                mevcutDurakIndex: 6,
                yogunluk: "yellow"
            },
            tahminiSure: 5
        },
        L03: {
            id: "L03",
            ad: "Bağlar - Çarşı",
            renk: "#34C759",
            duraklar: [
                "Bağlar Mahallesi",
                "Bağlar Parkı",
                "Kooperatif",
                "Otogar",
                "PTT",
                "Çarşı Girişi",
                "Kapalı Çarşı",
                "Büyük Cami",
                "Çarşı Merkez"
            ],
            aktifOtobus: {
                mevcutDurakIndex: 3,
                yogunluk: "green"
            },
            tahminiSure: 8
        },
        L04: {
            id: "L04",
            ad: "Esentepe - Meydan",
            renk: "#AF52DE",
            duraklar: [
                "Esentepe Terminal",
                "Esentepe Parkı",
                "Yıldız Mahallesi",
                "Güneş Sokak",
                "Bahçelievler",
                "Zafer Caddesi",
                "Kışla",
                "Spor Salonu",
                "AVM",
                "Postane",
                "Hükümet Konağı",
                "Meydan"
            ],
            aktifOtobus: {
                mevcutDurakIndex: 5,
                yogunluk: "yellow"
            },
            tahminiSure: 6
        },
        L05: {
            id: "L05",
            ad: "Terminal - Kampüs",
            renk: "#FF3B30",
            duraklar: [
                "Şehirlerarası Terminal",
                "Terminal Çıkışı",
                "Yeni Yol",
                "Kavşak",
                "Sanayi Kavşağı",
                "Demir Çelik",
                "Lojmanlar",
                "İlkokul",
                "Ortaokul",
                "Lise",
                "Dershane Sokak",
                "Yurt",
                "Spor Tesisleri",
                "Kütüphane",
                "Kampüs Girişi",
                "Kampüs Merkez"
            ],
            aktifOtobus: {
                mevcutDurakIndex: 7,
                yogunluk: "red"
            },
            tahminiSure: 4
        }
    }
};

// =========================================
//  API KATMANI – Backend Bağlantısı İçin Hazır
// =========================================
const TransitAPI = {
    baseUrl: '',  // Backend URL buraya gelecek
    
    endpoints: {
        hatlar:      '/api/hatlar',
        duraklar:    '/api/hatlar/{hatId}/duraklar',
        otobusKonum: '/api/hatlar/{hatId}/otobus',
        yogunluk:    '/api/hatlar/{hatId}/yogunluk',
        eta:         '/api/eta'
    },

    // ── Tüm hatları getir ──
    async getHatlar() {
        // Backend bağlandığında:
        // return fetch(`${this.baseUrl}${this.endpoints.hatlar}`).then(r => r.json());
        return Object.values(TransitData.hatlar);
    },

    // ── Belirli hat bilgisi ──
    async getHat(hatId) {
        // Backend: return fetch(`${this.baseUrl}/api/hatlar/${hatId}`).then(r => r.json());
        return TransitData.hatlar[hatId] || null;
    },

    // ── Belirli hattın durakları ──
    async getDuraklar(hatId) {
        // Backend: const url = this.endpoints.duraklar.replace('{hatId}', hatId);
        // return fetch(`${this.baseUrl}${url}`).then(r => r.json());
        return TransitData.hatlar[hatId]?.duraklar || [];
    },

    // ── Otobüs konumu ──
    async getOtobusKonum(hatId) {
        // Backend: const url = this.endpoints.otobusKonum.replace('{hatId}', hatId);
        // return fetch(`${this.baseUrl}${url}`).then(r => r.json());
        return TransitData.hatlar[hatId]?.aktifOtobus || null;
    },

    // ── Yoğunluk bilgisi ──
    async getYogunluk(hatId) {
        // Backend: const url = this.endpoints.yogunluk.replace('{hatId}', hatId);
        // return fetch(`${this.baseUrl}${url}`).then(r => r.json());
        return TransitData.hatlar[hatId]?.aktifOtobus?.yogunluk || "green";
    },

    // ── Tahmini varış süresi (AI backend hesaplayacak) ──
    async getETA(hatId, durakAdi) {
        // Backend: return fetch(`${this.baseUrl}${this.endpoints.eta}`, {
        //     method: 'POST',
        //     headers: { 'Content-Type': 'application/json' },
        //     body: JSON.stringify({ hatId, durakAdi })
        // }).then(r => r.json());
        return TransitData.hatlar[hatId]?.tahminiSure || 0;
    },

    // ── Tüm durakları düz liste (arama için) ──
    getTumDuraklar() {
        const set = new Set();
        Object.values(TransitData.hatlar).forEach(hat => {
            hat.duraklar.forEach(d => set.add(d));
        });
        return [...set].sort((a, b) => a.localeCompare(b, 'tr'));
    },

    // ── Durağı içeren hatları bul ──
    getDurakHatlari(durakAdi) {
        const sonuc = [];
        Object.entries(TransitData.hatlar).forEach(([hatId, hat]) => {
            const idx = hat.duraklar.indexOf(durakAdi);
            if (idx !== -1) {
                sonuc.push({ hatId, hat, durakIndex: idx });
            }
        });
        return sonuc;
    },

    // ── Kullanıcı seçimini kaydet/oku ──
    saveSelection(data) {
        localStorage.setItem('transitSelection', JSON.stringify(data));
    },

    getSelection() {
        try {
            return JSON.parse(localStorage.getItem('transitSelection')) || {};
        } catch { return {}; }
    }
};