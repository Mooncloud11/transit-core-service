// =========================================
//  MAP.JS – AI Entegreli Rota Paneli
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

    let seciliHatId = 'L01';
    let kullaniciDurakIndex = 6; 
    const selection = TransitAPI.getSelection();

    if (selection.baslangic) {
        const hatlar = TransitAPI.getDurakHatlari(selection.baslangic);
        if (hatlar.length > 0) {
            seciliHatId = hatlar[0].hatId;
            kullaniciDurakIndex = hatlar[0].durakIndex;
        }
    }

    function updateClock() {
        const now = new Date();
        const el = document.getElementById('statusTime');
        if (el) el.textContent = `${now.getHours().toString().padStart(2,'0')}:${now.getMinutes().toString().padStart(2,'0')}`;
    }
    updateClock();
    setInterval(updateClock, 30000);

    setTimeout(() => {
        skeletonOverlay.classList.add('hidden');
        setTimeout(() => skeletonOverlay.remove(), 500);
    }, 1200);

    function renderRoute(hatId) {
        const hat = TransitData.hatlar[hatId];
        if (!hat) return;

        const busIdx = hat.aktifOtobus.mevcutDurakIndex;
        const yogunluk = hat.aktifOtobus.yogunluk;
        const renk = hat.renk;

        hatBadge.textContent = hat.id;
        hatBadge.style.background = renk;
        etaTime.innerHTML = `${hat.tahminiSure}<span class="min">Dk</span>`;
        routeTimeline.style.setProperty('--hat-renk', renk);
        routeTimeline.innerHTML = '';

        hat.duraklar.forEach((durakAdi, i) => {
            const isPassed = i <= busIdx;
            const isBus = i === busIdx;
            const isUser = i === kullaniciDurakIndex;

            const node = document.createElement('div');
            node.className = 'stop-node';
            const marker = document.createElement('div');
            marker.className = 'stop-marker';

            if (isBus) { marker.classList.add('bus-loc'); marker.innerHTML = '🚌'; } 
            else if (isUser) { marker.classList.add('user-loc', `crowd-${yogunluk}`); } 
            else if (isPassed) { marker.classList.add('passed'); } 
            else { marker.classList.add('remaining'); }

            const label = document.createElement('span');
            label.className = 'stop-label';
            label.textContent = durakAdi;
            if (isBus || isUser) label.classList.add('highlight');

            node.appendChild(marker);
            node.appendChild(label);
            routeTimeline.appendChild(node);

            if (i < hat.duraklar.length - 1) {
                const seg = document.createElement('div');
                seg.className = 'route-segment';
                seg.classList.add(i < busIdx ? 'passed' : 'remaining');
                routeTimeline.appendChild(seg);
            }
        });
    }

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

    // ══════════════════════════════════════
    //  YAPAY ZEKAYI ÇALIŞTIR VE EKRANI GÜNCELLE
    // ══════════════════════════════════════
    async function loadAITahminAndRender(hatId) {
        etaTime.innerHTML = `<span style="font-size: 20px;">AI...</span>`;
        
        const aiData = await TransitAPI.getAITahmin(hatId);
        
        if (aiData) {
            TransitData.hatlar[hatId].tahminiSure = aiData.real_time_delay_min;
            TransitData.hatlar[hatId].aktifOtobus.yogunluk = aiData.status_color.toLowerCase();
            
            const subtitleEl = document.querySelector('.subtitle');
            if (subtitleEl) {
                subtitleEl.innerHTML = `🤖 <b>AI:</b> ${aiData.passenger_advice}`;
                subtitleEl.style.color = '#FFD700'; // Altın sarısı ile dikkat çeksin
            }
        }
        
        renderRoute(hatId);
        renderBusCards();
    }

    // İLK YÜKLEMEDE AI ÇAĞIR
    loadAITahminAndRender(seciliHatId);

    busList.addEventListener('click', (e) => {
        const card = e.target.closest('.bus-card');
        if (!card || card.classList.contains('active')) return;

        const hatId = card.dataset.hatId;
        seciliHatId = hatId;

        if (selection.baslangic) {
            const hatBilgi = TransitAPI.getDurakHatlari(selection.baslangic);
            const match = hatBilgi.find(h => h.hatId === hatId);
            kullaniciDurakIndex = match ? match.durakIndex : Math.min(6, TransitData.hatlar[hatId].duraklar.length - 1);
        } else {
            kullaniciDurakIndex = Math.min(6, TransitData.hatlar[hatId].duraklar.length - 1);
        }

        if (navigator.vibrate) navigator.vibrate(30);

        card.classList.add('selecting');
        setTimeout(() => {
            card.classList.remove('selecting');
            loadAITahminAndRender(seciliHatId);
        }, 350);

        TransitAPI.saveSelection({ ...selection, seciliHat: hatId });
    });

    fabBtn.addEventListener('click', () => {
        if (navigator.vibrate) navigator.vibrate(20);
        TransitAPI.saveSelection({ ...TransitAPI.getSelection(), seciliHat: seciliHatId, kullaniciDurakIndex });
        const container = document.getElementById('appContainer');
        document.body.classList.add('transitioning');
        container.classList.add('page-transition-out');
        setTimeout(() => { window.location.href = 'route-detail.html'; }, 400);
    });

    // ── Bottom Sheet Kodları ── (Aynı kaldı)
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

    dragArea.addEventListener('touchstart', (e) => startDrag(e.touches[0].clientY), { passive: true });
    document.addEventListener('touchmove', (e) => moveDrag(e.touches[0].clientY), { passive: true });
    document.addEventListener('touchend', endDrag);

    dragArea.addEventListener('mousedown', (e) => {
        e.preventDefault();
        startDrag(e.clientY);
    });
    document.addEventListener('mousemove', (e) => moveDrag(e.clientY));
    document.addEventListener('mouseup', endDrag);

    mapOverlay.addEventListener('click', closeSheet);
});