// =========================================
//  SCRIPT.JS – Ana Sayfa: Hat Kilitleme + Bağımlı Dropdown
//  Başlangıç durağı seçildiğinde bitiş listesi aynı hattaki
//  durakları gösterir. 62 durak, 5 hat.
// =========================================

// Saat güncelle
function updateClock() {
    const now = new Date();
    const h = now.getHours().toString().padStart(2, '0');
    const m = now.getMinutes().toString().padStart(2, '0');
    const el = document.getElementById('statusTime');
    if (el) el.textContent = `${h}:${m}`;
}

// ── Hat kilitleme durumu ──
let kilitliHatId = null;
let kilitliHatAdi = null;

// ── Autocomplete ──
async function setupAutocomplete(inputId, dropdownId, containerId, mode) {
    const input = document.getElementById(inputId);
    const dropdown = document.getElementById(dropdownId);
    const container = document.getElementById(containerId);

    // DİKKAT: Verileri Java'dan alıyoruz
    let tumDuraklar = [];
    try {
        const response = await fetch('http://localhost:8080/api/stops'); // Kendi API yoluna göre değiştir
        if(response.ok) {
           tumDuraklar = await response.json();
        }
    } catch(e) {
        console.error("Java Backend bağlantı hatası!", e);
    }

    input.addEventListener('input', function() {
        const q = this.value.toLowerCase();
        if (q.length < 2 || tumDuraklar.length === 0) {
            dropdown.style.display = 'none';
            dropdown.innerHTML = '';
            return;
        }
        dropdown.style.display = 'block';

        let durakListesi = [];
        if (mode === 'bitis' && kilitliHatId) {
            // ═══ HAT KİLİTLEME: Sadece aynı hattaki (lineId) durakları göster ═══
            durakListesi = tumDuraklar.filter(d => d.lineId === kilitliHatId);
        } else {
            durakListesi = tumDuraklar;
        }

        // İsme göre filtrele (stopName)
        const filtreli = durakListesi.filter(d => d.stopName.toLowerCase().includes(q));
        
        // Tekrar eden isimleri temizle (Sadece benzersiz durak isimleri kalsın)
        const benzersizFiltreli = [];
        const map = new Map();
        for (const item of filtreli) {
            if(!map.has(item.stopName)){
                map.set(item.stopName, true);
                benzersizFiltreli.push(item);
            }
        }

        dropdown.innerHTML = '';

        if (benzersizFiltreli.length > 0) {
            benzersizFiltreli.forEach(durak => {
                const li = document.createElement('li');
                li.textContent = durak.stopName; // <--- stopName KULLANILDI

                // Hangi hatlarda geçtiğini bul ve göster (Aynı isimli durakların hatlarını birleştir)
                const hatlar = tumDuraklar.filter(d => d.stopName === durak.stopName).map(d => d.lineId);
                
                if (hatlar.length > 0) {
                    const badge = document.createElement('span');
                    badge.style.cssText = 'font-size:10px;color:#888;margin-left:8px;';
                    // Benzersiz hatları virgülle ayırarak yazdır
                    badge.textContent = [...new Set(hatlar)].join(', '); 
                    li.appendChild(badge);
                }

                li.addEventListener('click', function() {
                    input.value = durak.stopName; // <--- stopName KULLANILDI
                    dropdown.style.display = 'none';

                    if (mode === 'baslangic') {
                        onBaslangicSecildi(durak.stopName, hatlar[0]); // İlk hattı gönder
                    }
                });
                dropdown.appendChild(li);
            });
        } else {
            if (mode === 'bitis' && kilitliHatId) {
                dropdown.innerHTML = `<li style="color:#888;cursor:default;">Bu hatta "${q}" içeren durak yok</li>`;
            } else {
                dropdown.innerHTML = '<li style="color:#888;cursor:default;">Durak bulunamadı</li>';
            }
        }
    });

    document.addEventListener('click', function(e) {
        if (!container.contains(e.target)) dropdown.style.display = 'none';
    });
}

// ═══════════════════════════════════════════
//  BAŞLANGIÇ DURAĞ SEÇİLDİĞİNDE HAT KİLİTLE
// ═══════════════════════════════════════════
function onBaslangicSecildi(durakAdi, hatId) {
    const endInput = document.getElementById('end-input');
    const hatLockBanner = document.getElementById('hatLockBanner');

    if (hatId) {
        kilitliHatId = hatId;
        // Eğer TransitData.hatlar hala data.js'den geliyorsa adını oradan bul
        const hat = (typeof TransitData !== 'undefined' && TransitData.hatlar) ? TransitData.hatlar[hatId] : null;
        kilitliHatAdi = hat ? hat.ad : hatId;

        // Bitiş inputunu sıfırla ve aktifleştir
        endInput.value = '';
        endInput.disabled = false;
        endInput.placeholder = `${kilitliHatAdi} hattında varış durağı seçin`;

        // Hat kilidi banner
        if (hatLockBanner) {
            hatLockBanner.innerHTML = `
                <span class="lock-icon">🔒</span>
                <span class="lock-text"><strong>${hatId}</strong> – ${kilitliHatAdi}</span>
                <button class="lock-clear" id="clearLock">✕</button>
            `;
            hatLockBanner.style.display = 'flex';
            if(hat) hatLockBanner.style.borderLeftColor = hat.renk;

            document.getElementById('clearLock').addEventListener('click', (e) => {
                e.stopPropagation();
                clearHatLock();
            });
        }
    } else {
        kilitliHatId = null;
        endInput.placeholder = 'Varış Noktası';
    }
}

function clearHatLock() {
    kilitliHatId = null;
    kilitliHatAdi = null;
    const endInput = document.getElementById('end-input');
    const startInput = document.getElementById('start-input');
    const hatLockBanner = document.getElementById('hatLockBanner');

    startInput.value = '';
    endInput.value = '';
    endInput.placeholder = 'Varış Noktası';
    if (hatLockBanner) hatLockBanner.style.display = 'none';
}

// Sayfa geçiş animasyonu
function navigateWithTransition(url) {
    const container = document.getElementById('appContainer');
    document.body.classList.add('transitioning');
    container.classList.add('page-transition-out');
    setTimeout(() => { window.location.href = url; }, 400);
}

// Başlat
document.addEventListener('DOMContentLoaded', () => {
    // DİKKAT: Artık async olduğu için fonksiyon içindeki API isteğini bekler
    setupAutocomplete('start-input', 'start-dropdown', 'start-container', 'baslangic');
    setupAutocomplete('end-input', 'end-dropdown', 'end-container', 'bitis');
    updateClock();
    setInterval(updateClock, 30000);

    // ── Simülasyon UI Eventleri ──
    const simToggle = document.getElementById('simToggle');
    const simSliderContainer = document.getElementById('simSliderContainer');
    const simTimeSlider = document.getElementById('simTimeSlider');
    const simTimeDisplay = document.getElementById('simTimeDisplay');

    let isSimulationActive = false;
    let simHour = 12;
    let simMinute = 0;

    if (simToggle) {
        simToggle.addEventListener('change', function() {
            isSimulationActive = this.checked;
            simSliderContainer.style.display = isSimulationActive ? 'block' : 'none';
            if (isSimulationActive) {
                updateSimDisplay(simTimeSlider.value);
            }
        });
    }

    if (simTimeSlider) {
        simTimeSlider.addEventListener('input', function() {
            updateSimDisplay(this.value);
        });
    }

    function updateSimDisplay(val) {
        const totalMinutes = parseInt(val, 10);
        simHour = Math.floor(totalMinutes / 60);
        simMinute = totalMinutes % 60;
        simTimeDisplay.textContent = `${simHour.toString().padStart(2, '0')}:${simMinute.toString().padStart(2, '0')}`;
    }

    const btnNext = document.getElementById('btnNext');
    if (btnNext) {
        btnNext.addEventListener('click', async (e) => {
            e.preventDefault();
            const baslangic = document.getElementById('start-input').value;
            const varis = document.getElementById('end-input').value;

            if (!baslangic || !varis) {
                if (!baslangic) document.getElementById('start-input').parentElement.classList.add('shake');
                if (!varis) document.getElementById('end-input').parentElement.classList.add('shake');
                setTimeout(() => {
                    document.querySelectorAll('.shake').forEach(el => el.classList.remove('shake'));
                }, 600);
                return;
            }

            // Seçimi kaydet (map sayfası kullanacak)
            // TransitAPI hala varsa oraya kaydet, yoksa localStorage kullan
            if(typeof TransitAPI !== 'undefined') {
                 // Stop Id'yi bulmak için güncellenmiş mantık (stopName ve lineId'ye göre)
                 let finalStopId = null;
                 try {
                     const response = await fetch('http://localhost:8080/api/stops'); 
                     if(response.ok) {
                        const duraklar = await response.json();
                        const bulunanDurak = duraklar.find(d => d.stopName === baslangic && d.lineId === kilitliHatId);
                        if(bulunanDurak) finalStopId = bulunanDurak.stopId;
                     }
                 } catch(e) {}

                TransitAPI.saveSelection({
                    baslangic,
                    varis,
                    hatId: kilitliHatId,
                    stopId: finalStopId, 
                    simHour: isSimulationActive ? simHour : null,
                    simMinute: isSimulationActive ? simMinute : null
                });
            }
            navigateWithTransition('map.html');
        });
    }
});