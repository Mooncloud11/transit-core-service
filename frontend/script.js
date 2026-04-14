// =========================================
//  SCRIPT.JS – Ana Sayfa: Autocomplete + Sayfa Geçişi
//  data.js'den 62 durak verisini kullanır
// =========================================

// Saat güncelle
function updateClock() {
    const now = new Date();
    const h = now.getHours().toString().padStart(2, '0');
    const m = now.getMinutes().toString().padStart(2, '0');
    const el = document.getElementById('statusTime');
    if (el) el.textContent = `${h}:${m}`;
}

// Autocomplete
function setupAutocomplete(inputId, dropdownId, containerId) {
    const input = document.getElementById(inputId);
    const dropdown = document.getElementById(dropdownId);
    const container = document.getElementById(containerId);
    const tumDuraklar = TransitAPI.getTumDuraklar(); // data.js'den 62 durak

    input.addEventListener('input', function() {
        const q = this.value.toLowerCase();
        if (q.length < 2) {
            dropdown.style.display = 'none';
            dropdown.innerHTML = '';
            return;
        }
        dropdown.style.display = 'block';
        const filtreli = tumDuraklar.filter(d => d.toLowerCase().includes(q));
        dropdown.innerHTML = '';

        if (filtreli.length > 0) {
            filtreli.forEach(durak => {
                const li = document.createElement('li');
                li.textContent = durak;
                // Hangi hatlarda geçtiğini göster
                const hatlar = TransitAPI.getDurakHatlari(durak);
                if (hatlar.length > 0) {
                    const badge = document.createElement('span');
                    badge.style.cssText = 'font-size:10px;color:#888;margin-left:8px;';
                    badge.textContent = hatlar.map(h => h.hatId).join(', ');
                    li.appendChild(badge);
                }
                li.addEventListener('click', function() {
                    input.value = durak;
                    dropdown.style.display = 'none';
                });
                dropdown.appendChild(li);
            });
        } else {
            dropdown.innerHTML = '<li style="color:#888;cursor:default;">Durak bulunamadı</li>';
        }
    });

    document.addEventListener('click', function(e) {
        if (!container.contains(e.target)) dropdown.style.display = 'none';
    });
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
    setupAutocomplete('start-input', 'start-dropdown', 'start-container');
    setupAutocomplete('end-input', 'end-dropdown', 'end-container');
    updateClock();
    setInterval(updateClock, 30000);

    const btnNext = document.getElementById('btnNext');
    if (btnNext) {
        btnNext.addEventListener('click', (e) => {
            e.preventDefault();
            const baslangic = document.getElementById('start-input').value;
            const varis = document.getElementById('end-input').value;

            // Seçimi kaydet (map sayfası kullanacak)
            TransitAPI.saveSelection({ baslangic, varis });
            navigateWithTransition('map.html');
        });
    }
});