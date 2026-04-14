// =========================================
//  MAP.JS – Rota Paneli + Drag Sheet + Kart Yönetimi
//  data.js ve TransitAPI ile çalışır
// =========================================

document.addEventListener('DOMContentLoaded', async () => {
    const bottomSheet = document.getElementById('bottomSheet');
    const dragArea = document.getElementById('dragArea');
    const mapOverlay = document.getElementById('mapOverlay');
    const skeletonOverlay = document.getElementById('skeletonOverlay');
    const busList = document.getElementById('busList');
    const routeTimeline = document.getElementById('routeTimeline');
    const hatBadge = document.getElementById('hatBadge');
    const etaTime = document.getElementById('etaTime');
    const fabBtn = document.getElementById('fabRouteDetail');

    // ── Durumlar ──
    let seciliHatId = 'L01';
    let kullaniciDurakIndex = 6; // Varsayılan kullanıcı durağı
    const selection = TransitAPI.getSelection();

    // Kullanıcı seçimine göre uygun hattı bul
    if (selection.baslangic) {
        const hatlar = TransitAPI.getDurakHatlari(selection.baslangic);
        if (hatlar.length > 0) {
            seciliHatId = hatlar[0].hatId;
            kullaniciDurakIndex = hatlar[0].durakIndex;
        }
    }

    // ── Saat ──
    function updateClock() {
        const now = new Date();
        const el = document.getElementById('statusTime');
        if (el) el.textContent = `${now.getHours().toString().padStart(2,'0')}:${now.getMinutes().toString().padStart(2,'0')}`;
    }
    updateClock();
    setInterval(updateClock, 30000);

    // ── Skeleton ──
    setTimeout(() => {
        skeletonOverlay.classList.add('hidden');
        setTimeout(() => skeletonOverlay.remove(), 500);
    }, 1200);

    // ══════════════════════════════════════
    //  ROTA PANELİ RENDER
    // ══════════════════════════════════════
    function renderRoute(hatId) {
        const hat = TransitData.hatlar[hatId];
        if (!hat) return;

        const busIdx = hat.aktifOtobus.mevcutDurakIndex;
        const yogunluk = hat.aktifOtobus.yogunluk;
        const renk = hat.renk;

        // Header güncelle
        hatBadge.textContent = hat.id;
        hatBadge.style.background = renk;
        etaTime.innerHTML = `${hat.tahminiSure}<span class="min">Dk</span>`;

        // CSS custom property
        routeTimeline.style.setProperty('--hat-renk', renk);

        routeTimeline.innerHTML = '';

        hat.duraklar.forEach((durakAdi, i) => {
            const isPassed = i <= busIdx;
            const isBus = i === busIdx;
            const isUser = i === kullaniciDurakIndex;

            // Durak noktası
            const node = document.createElement('div');
            node.className = 'stop-node';

            const marker = document.createElement('div');
            marker.className = 'stop-marker';

            if (isBus) {
                marker.classList.add('bus-loc');
                marker.innerHTML = '🚌';
            } else if (isUser) {
                marker.classList.add('user-loc', `crowd-${yogunluk}`);
            } else if (isPassed) {
                marker.classList.add('passed');
            } else {
                marker.classList.add('remaining');
            }

            const label = document.createElement('span');
            label.className = 'stop-label';
            label.textContent = durakAdi;
            if (isBus || isUser) label.classList.add('highlight');

            node.appendChild(marker);
            node.appendChild(label);
            routeTimeline.appendChild(node);

            // Segment çizgisi (son durak hariç)
            if (i < hat.duraklar.length - 1) {
                const seg = document.createElement('div');
                seg.className = 'route-segment';
                seg.classList.add(i < busIdx ? 'passed' : 'remaining');
                routeTimeline.appendChild(seg);
            }
        });
    }

    // ══════════════════════════════════════
    //  OTOBÜS KARTLARINI RENDER
    // ══════════════════════════════════════
    function renderBusCards() {
        busList.innerHTML = '';
        const hatlarArr = Object.entries(TransitData.hatlar);

        hatlarArr.forEach(([hatId, hat]) => {
            const isActive = hatId === seciliHatId;
            const yogunlukText = { red: 'Çok Yoğun', yellow: 'Orta Yoğun', green: 'Az Yoğun' };
            const yogunlukEmoji = { red: '👥', yellow: '👥', green: '👥' };

            const li = document.createElement('li');
            li.className = `bus-card${isActive ? ' active' : ''}`;
            li.dataset.hatId = hatId;
            li.style.setProperty('--hat-renk', hat.renk);

            li.innerHTML = `
                <div class="hat-color-band" style="background:${hat.renk}"></div>
                <div class="bus-icon-box" style="background:${hat.renk}20;border:2px solid ${hat.renk}">🚌</div>
                <div class="bus-details">
                    <span class="bus-hat-id">${hat.id}</span>
                    <span class="bus-route">${hat.ad}</span>
                    <span class="bus-crowd ${hat.aktifOtobus.yogunluk}">${yogunlukEmoji[hat.aktifOtobus.yogunluk]} ${yogunlukText[hat.aktifOtobus.yogunluk]}</span>
                </div>
                <div class="bus-status">
                    <span class="status-label" style="visibility:${isActive ? 'visible' : 'hidden'}">Seçildi</span>
                    <span class="status-time">⏱ ${hat.tahminiSure}Dk</span>
                    <span class="bus-stops-count">${hat.duraklar.length} durak</span>
                </div>
            `;

            busList.appendChild(li);
        });
    }

    // İlk render
    renderRoute(seciliHatId);
    renderBusCards();

    // ══════════════════════════════════════
    //  KART SEÇİMİ (Özellik B: parlama)
    // ══════════════════════════════════════
    busList.addEventListener('click', (e) => {
        const card = e.target.closest('.bus-card');
        if (!card || card.classList.contains('active')) return;

        const hatId = card.dataset.hatId;
        seciliHatId = hatId;

        // Kullanıcı durağını bu hatta göre güncelle
        if (selection.baslangic) {
            const hatBilgi = TransitAPI.getDurakHatlari(selection.baslangic);
            const match = hatBilgi.find(h => h.hatId === hatId);
            kullaniciDurakIndex = match ? match.durakIndex : Math.min(6, TransitData.hatlar[hatId].duraklar.length - 1);
        } else {
            kullaniciDurakIndex = Math.min(6, TransitData.hatlar[hatId].duraklar.length - 1);
        }

        // Haptic feedback (Özellik C)
        if (navigator.vibrate) navigator.vibrate(30);

        // Animasyonlu seçim
        card.classList.add('selecting');
        setTimeout(() => {
            card.classList.remove('selecting');
            renderBusCards();
            renderRoute(seciliHatId);
        }, 350);

        // Seçimi kaydet
        TransitAPI.saveSelection({ ...selection, seciliHat: hatId });
    });

    // ══════════════════════════════════════
    //  FAB BUTON – Güzergah Detay
    // ══════════════════════════════════════
    fabBtn.addEventListener('click', () => {
        if (navigator.vibrate) navigator.vibrate(20);
        TransitAPI.saveSelection({ ...TransitAPI.getSelection(), seciliHat: seciliHatId, kullaniciDurakIndex });
        const container = document.getElementById('appContainer');
        document.body.classList.add('transitioning');
        container.classList.add('page-transition-out');
        setTimeout(() => { window.location.href = 'route-detail.html'; }, 400);
    });

    // ══════════════════════════════════════
    //  BOTTOM SHEET – DRAG ONLY (mouse + touch)
    // ══════════════════════════════════════
    const SHEET_HEIGHT = 540;
    const COLLAPSED_Y = 420;
    let isDragging = false;
    let dragStartY = 0;
    let dragStartTranslateY = 0;

    function getTranslateY() {
        const m = new DOMMatrix(getComputedStyle(bottomSheet).transform);
        return m.m42;
    }

    function openSheet() {
        bottomSheet.classList.remove('collapsed');
        mapOverlay.classList.add('active');
        if (navigator.vibrate) navigator.vibrate(15);
    }

    function closeSheet() {
        bottomSheet.classList.add('collapsed');
        mapOverlay.classList.remove('active');
    }

    function startDrag(clientY) {
        isDragging = true;
        dragStartY = clientY;
        dragStartTranslateY = getTranslateY();
        bottomSheet.style.transition = 'none';
    }

    function moveDrag(clientY) {
        if (!isDragging) return;
        const delta = clientY - dragStartY;
        let newY = dragStartTranslateY + delta;
        newY = Math.max(0, Math.min(COLLAPSED_Y, newY));
        bottomSheet.style.transform = `translateY(${newY}px)`;

        const progress = 1 - (newY / COLLAPSED_Y);
        mapOverlay.style.background = `rgba(0,0,0,${0.35 * progress})`;
        mapOverlay.style.pointerEvents = progress > 0.1 ? 'auto' : 'none';
    }

    function endDrag() {
        if (!isDragging) return;
        isDragging = false;
        bottomSheet.style.transition = 'transform 0.45s cubic-bezier(0.22,0.68,0.0,1.0)';

        const currentY = getTranslateY();
        if (currentY > COLLAPSED_Y * 0.45) {
            closeSheet();
        } else {
            openSheet();
        }

        setTimeout(() => {
            bottomSheet.style.transform = '';
            mapOverlay.style.background = '';
        }, 50);
    }

    // Touch events
    dragArea.addEventListener('touchstart', (e) => startDrag(e.touches[0].clientY), { passive: true });
    document.addEventListener('touchmove', (e) => moveDrag(e.touches[0].clientY), { passive: true });
    document.addEventListener('touchend', endDrag);

    // Mouse events (web tarayıcı desteği)
    dragArea.addEventListener('mousedown', (e) => {
        e.preventDefault();
        startDrag(e.clientY);
    });
    document.addEventListener('mousemove', (e) => moveDrag(e.clientY));
    document.addEventListener('mouseup', endDrag);

    // Overlay'e tıkla → kapat
    mapOverlay.addEventListener('click', closeSheet);
});