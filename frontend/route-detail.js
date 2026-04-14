// =========================================
//  ROUTE-DETAIL.JS – Güzergah Detay Sayfası
//  Hat duraklarını timeline olarak gösterir
// =========================================

document.addEventListener('DOMContentLoaded', () => {
    const timeline = document.getElementById('detailTimeline');
    const header = document.getElementById('detailHeader');
    const hatNameEl = document.getElementById('detailHatName');
    const hatMetaEl = document.getElementById('detailHatMeta');
    const searchInput = document.getElementById('searchStopsInput');
    const btnBack = document.getElementById('btnBack');
    const pullRefresh = document.getElementById('pullRefresh');
    const timelineContainer = document.getElementById('timelineContainer');

    // ── Saat ──
    function updateClock() {
        const now = new Date();
        const el = document.getElementById('statusTime');
        if (el) el.textContent = `${now.getHours().toString().padStart(2,'0')}:${now.getMinutes().toString().padStart(2,'0')}`;
    }
    updateClock();
    setInterval(updateClock, 30000);

    // ── Seçimi oku ──
    const selection = TransitAPI.getSelection();
    const hatId = selection.seciliHat || 'L01';
    const hat = TransitData.hatlar[hatId];
    const kullaniciDurakIdx = selection.kullaniciDurakIndex ?? 6;

    if (!hat) {
        timeline.innerHTML = '<p style="text-align:center;color:#888;padding:40px;">Hat bulunamadı</p>';
        return;
    }

    const busIdx = hat.aktifOtobus.mevcutDurakIndex;
    const yogunluk = hat.aktifOtobus.yogunluk;
    const renk = hat.renk;

    // ── Header ──
    hatNameEl.textContent = `${hat.id} – ${hat.ad}`;
    hatMetaEl.textContent = `${hat.duraklar.length} durak • Tahmini ${hat.tahminiSure} dk`;
    hatNameEl.style.color = 'white';

    // CSS custom property
    timeline.style.setProperty('--hat-renk', renk);

    // ══════════════════════════════════════
    //  TIMELINE RENDER
    // ══════════════════════════════════════
    function renderTimeline(filter = '') {
        timeline.innerHTML = '';

        hat.duraklar.forEach((durakAdi, i) => {
            const isPassed = i <= busIdx;
            const isBus = i === busIdx;
            const isUser = i === kullaniciDurakIdx;
            const isLast = i === hat.duraklar.length - 1;

            // Filtreleme
            const matchesFilter = !filter || durakAdi.toLowerCase().includes(filter.toLowerCase());

            const row = document.createElement('div');
            row.className = 'detail-stop-row';
            if (!matchesFilter) row.classList.add('hidden-by-search');

            // Sol visual (dot + line)
            const visual = document.createElement('div');
            visual.className = 'detail-stop-visual';

            const dot = document.createElement('div');
            dot.className = 'detail-stop-dot';

            if (isBus) {
                dot.classList.add('bus');
                dot.innerHTML = '🚌';
            } else if (isUser) {
                dot.classList.add('user', `crowd-${yogunluk}`);
            } else if (isPassed) {
                dot.classList.add('passed');
            }

            visual.appendChild(dot);

            // Çizgi (son durak hariç)
            if (!isLast) {
                const line = document.createElement('div');
                line.className = 'detail-stop-line';
                if (isPassed && !isBus) line.classList.add('passed');
                visual.appendChild(line);
            }

            // Sağ bilgi
            const info = document.createElement('div');
            info.className = 'detail-stop-info';

            const name = document.createElement('span');
            name.className = 'detail-stop-name';
            name.textContent = durakAdi;

            if (isBus || isUser) name.classList.add('highlight');

            info.appendChild(name);

            // Etiketler
            if (isBus) {
                const tag = document.createElement('span');
                tag.className = 'detail-stop-tag bus-tag';
                tag.textContent = '🚌 Otobüs burada';
                info.appendChild(tag);
            }

            if (isUser) {
                const tag = document.createElement('span');
                tag.className = `detail-stop-tag user-tag ${yogunluk}`;
                const crowdText = { red: 'Çok Yoğun', yellow: 'Orta', green: 'Uygun' };
                tag.textContent = `📍 Sizin durağınız • ${crowdText[yogunluk]}`;
                info.appendChild(tag);
            }

            if (isPassed && !isBus && !isUser) {
                const passedText = document.createElement('span');
                passedText.style.cssText = 'font-size:11px;color:#999;';
                passedText.textContent = '✓ Geçildi';
                info.appendChild(passedText);
            }

            row.appendChild(visual);
            row.appendChild(info);
            timeline.appendChild(row);
        });
    }

    renderTimeline();

    // ══════════════════════════════════════
    //  DURAK ARAMA (Özellik B)
    // ══════════════════════════════════════
    searchInput.addEventListener('input', function() {
        renderTimeline(this.value);
    });

    // ══════════════════════════════════════
    //  GERİ BUTONU
    // ══════════════════════════════════════
    btnBack.addEventListener('click', () => {
        if (navigator.vibrate) navigator.vibrate(20);
        const container = document.getElementById('appContainer');
        container.classList.remove('page-transition-in');
        container.classList.add('page-transition-out');
        setTimeout(() => { window.location.href = 'map.html'; }, 400);
    });

    // ══════════════════════════════════════
    //  PULL TO REFRESH (Özellik F)
    // ══════════════════════════════════════
    let pullStartY = 0;
    let isPulling = false;

    timelineContainer.addEventListener('touchstart', (e) => {
        if (timelineContainer.scrollTop === 0) {
            pullStartY = e.touches[0].clientY;
            isPulling = true;
        }
    }, { passive: true });

    timelineContainer.addEventListener('touchmove', (e) => {
        if (!isPulling) return;
        const delta = e.touches[0].clientY - pullStartY;
        if (delta > 0 && delta < 100) {
            pullRefresh.style.opacity = Math.min(1, delta / 60);
            pullRefresh.style.transform = `translateY(${delta * 0.3}px)`;
        }
    }, { passive: true });

    timelineContainer.addEventListener('touchend', () => {
        if (!isPulling) return;
        isPulling = false;
        pullRefresh.style.opacity = '';
        pullRefresh.style.transform = '';

        // Backend bağlandığında burada veri yenileme çağrısı yapılacak
        // TransitAPI.getOtobusKonum(hatId).then(data => { ... });

        if (navigator.vibrate) navigator.vibrate(30);
        pullRefresh.textContent = '✓ Veriler güncel';
        setTimeout(() => {
            pullRefresh.textContent = '↓ Verileri yenilemek için aşağı çekin';
        }, 2000);
    });
});