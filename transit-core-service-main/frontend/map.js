// =========================================
//  MAP.JS – v2: AI Loading Overlay + Next Buses
//  All data comes through Java Backend (:8080).
// =========================================

document.addEventListener('DOMContentLoaded', async () => {
    const bottomSheet = document.getElementById('bottomSheet');
    const dragArea = document.getElementById('dragArea');
    const mapOverlay = document.getElementById('mapOverlay');
    const skeletonOverlay = document.getElementById('skeletonOverlay');
    const aiLoadingOverlay = document.getElementById('aiLoadingOverlay');
    const aiLoadingText = document.getElementById('aiLoadingText');
    const fallbackBanner = document.getElementById('fallbackBanner');
    const busList = document.getElementById('busList');
    const routeTimeline = document.getElementById('routeTimeline');
    const hatBadge = document.getElementById('hatBadge');
    const etaTime = document.getElementById('etaTime');
    const fabBtn = document.getElementById('fabRouteDetail');
    const btnBack = document.getElementById('btnBackToHome');

    let seciliHatId = 'L01';
    let kullaniciDurakIndex = 6;
    let kullaniciStopId = null;
    const selection = TransitAPI.getSelection();

    if (selection.hatId) {
        seciliHatId = selection.hatId;
    }
    if (selection.baslangic) {
        const hatlar = TransitAPI.getDurakHatlari(selection.baslangic);
        if (hatlar.length > 0) {
            seciliHatId = hatlar[0].hatId;
            kullaniciDurakIndex = hatlar[0].durakIndex;
        }
    }
    if (selection.stopId) {
        kullaniciStopId = selection.stopId;
    } else {
        kullaniciStopId = TransitAPI.getStopId(selection.baslangic, seciliHatId);
    }

    // ── Route Info Bar (Start → Destination) ──
    const routeInfoBar = document.getElementById('routeInfoBar');
    const routeStartName = document.getElementById('routeStartName');
    const routeEndName = document.getElementById('routeEndName');
    if (selection.baslangic || selection.varis) {
        if (routeInfoBar) routeInfoBar.style.display = 'flex';
        if (routeStartName) routeStartName.textContent = selection.baslangic || '—';
        if (routeEndName) routeEndName.textContent = selection.varis || '—';
    }

    // ── Simulation Control ──
    const isSimMode = selection.simHour !== undefined && selection.simHour !== null;
    if (isSimMode) {
        const simBanner = document.createElement('div');
        simBanner.style.cssText = 'position:absolute; top:42px; left:50%; transform:translateX(-50%); background:rgba(255,255,255,0.1); backdrop-filter:blur(12px); color:rgba(255,255,255,0.9); padding:4px 12px; border-radius:12px; font-size:12px; font-weight:700; z-index:100; white-space:nowrap; box-shadow:0 4px 12px rgba(0,0,0,0.4); border:1px solid rgba(255,255,255,0.1);';
        simBanner.innerHTML = `Simulation Mode: ${selection.simHour.toString().padStart(2,'0')}:${selection.simMinute.toString().padStart(2,'0')}`;
        document.getElementById('appContainer').appendChild(simBanner);
    }

    // ── Clock ──
    function updateClock() {
        const now = new Date();
        const el = document.getElementById('statusTime');
        const h = isSimMode ? selection.simHour : now.getHours();
        const m = isSimMode ? selection.simMinute : now.getMinutes();
        if (el) el.textContent = `${h.toString().padStart(2,'0')}:${m.toString().padStart(2,'0')}`;
    }
    updateClock();
    setInterval(updateClock, 30000);

    // ── Back Button → Index ──
    btnBack.addEventListener('click', () => {
        if (navigator.vibrate) navigator.vibrate(20);
        const container = document.getElementById('appContainer');
        container.classList.remove('page-transition-in');
        container.classList.add('page-transition-out');
        setTimeout(() => { window.location.href = 'index.html'; }, 400);
    });

    // ── Remove skeleton ──
    setTimeout(() => {
        skeletonOverlay.classList.add('hidden');
        setTimeout(() => skeletonOverlay.remove(), 500);
    }, 1200);

    // ══════════════════════════════════════
    //  AI LOADING OVERLAY
    // ══════════════════════════════════════
    const loadingMessages = [
        "Inspecting line data...",
        "Analyzing weather conditions...",
        "Calculating traffic density...",
        "Running AI model...",
        "Generating delay prediction..."
    ];

    function showAILoading() {
        aiLoadingOverlay.classList.add('visible');
        let msgIdx = 0;
        const interval = setInterval(() => {
            msgIdx = (msgIdx + 1) % loadingMessages.length;
            aiLoadingText.textContent = loadingMessages[msgIdx];
        }, 1500);
        aiLoadingOverlay._interval = interval;
    }

    function hideAILoading() {
        if (aiLoadingOverlay._interval) {
            clearInterval(aiLoadingOverlay._interval);
        }
        aiLoadingOverlay.classList.add('fade-out');
        setTimeout(() => {
            aiLoadingOverlay.classList.remove('visible', 'fade-out');
        }, 500);
    }

    // ══════════════════════════════════════
    //  ROUTE RENDER
    // ══════════════════════════════════════
    function renderRoute(hatId) {
        const hat = TransitData.hatlar[hatId];
        if (!hat) return;

        const busIdx = hat.aktifOtobus.mevcutDurakIndex;
        const yogunluk = hat.aktifOtobus.yogunluk;
        const renk = hat.renk;

        hatBadge.textContent = hat.id;
        hatBadge.style.background = renk;
        const sure = hat.tahminiSure ?? '—';
        etaTime.innerHTML = `${sure}<span class="min">min</span>`;
        routeTimeline.style.setProperty('--hat-renk', renk);
        routeTimeline.innerHTML = '';

        let animDelay = 0;

        hat.duraklar.forEach((durakAdi, i) => {
            const isPassed = i <= busIdx;
            const isBus = i === busIdx;
            const isUser = i === kullaniciDurakIndex;

            const node = document.createElement('div');
            node.className = 'stop-node animated';
            node.style.animationDelay = `${animDelay}ms`;
            animDelay += 60;

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

            // ── Tooltip Popup (on bus stop) ──
            if (isBus) {
                node.style.position = 'relative';
                const nextDurak = hat.duraklar[i + 1] || 'Son Durak';
                const tooltip = document.createElement('div');
                tooltip.className = 'stop-tooltip';
                tooltip.innerHTML = `
                    <div class="stop-tooltip-row">
                        <span class="stop-tooltip-label">Anlık Durak:</span>
                        <span class="stop-tooltip-value">${durakAdi.toUpperCase()}</span>
                    </div>
                    <div class="stop-tooltip-row">
                        <span class="stop-tooltip-label">Sonraki Durak:</span>
                        <span class="stop-tooltip-value">${nextDurak}</span>
                    </div>
                `;
                tooltip.style.animationDelay = `${animDelay + 200}ms`;
                node.appendChild(tooltip);
            }

            routeTimeline.appendChild(node);

            if (i < hat.duraklar.length - 1) {
                const seg = document.createElement('div');
                seg.className = 'route-segment animated';
                seg.classList.add(i < busIdx ? 'passed' : 'remaining');
                seg.style.animationDelay = `${animDelay}ms`;
                animDelay += 30;
                routeTimeline.appendChild(seg);
            }
        });
    }

    // ══════════════════════════════════════
    //  NEXT BUSES RENDER
    // ══════════════════════════════════════
    function renderNextBuses(nextBusData) {
        busList.innerHTML = '';
        const hat = TransitData.hatlar[seciliHatId];
        if (!hat) return;

        if (nextBusData && nextBusData.next_buses) {
            const buses = nextBusData.next_buses;
            const isFallback = nextBusData.is_fallback;

            buses.forEach((bus) => {
                const li = document.createElement('li');
                li.className = 'bus-card next-bus-card';
                li.style.setProperty('--hat-renk', hat.renk);

                const crowdText = { busy: 'Crowded', normal: 'Available', quiet: 'Empty' };
                const crowdColor = { busy: '#FF9500', normal: '#34C759', quiet: '#4A90D9' };
                const crowdEmoji = { busy: '👥', normal: '🧑', quiet: '💺' };
                const crowd = bus.crowding_forecast || 'normal';
                const confidence = Math.round((bus.confidence || 0.5) * 100);

                li.innerHTML = `
                    <div class="hat-color-band" style="background:${hat.renk}"></div>
                    <div class="next-bus-order" style="background:${hat.renk}20;color:${hat.renk}">
                        Bus #${bus.bus_order}
                    </div>
                    <div class="bus-details">
                        <span class="bus-route">${hat.ad}</span>
                        <span class="bus-crowd" style="color:${crowdColor[crowd]}">${crowdEmoji[crowd]} ${crowdText[crowd]}</span>
                    </div>
                    <div class="bus-status">
                        <span class="next-bus-eta">${Math.round(bus.estimated_arrival_min)}<small>min</small></span>
                        <span class="next-bus-confidence" title="Prediction confidence">${isFallback ? '📊' : '🤖'} ${confidence}%</span>
                    </div>
                `;
                busList.appendChild(li);
            });

            // Weather and traffic info footer
            if (nextBusData.weather || nextBusData.traffic_level) {
                const footer = document.createElement('li');
                footer.className = 'next-bus-footer';
                const weatherEmoji = { rain: '🌧️', snow: '❄️', fog: '🌫️', wind: '💨', cloudy: '☁️', clear: '☀️' };
                const trafficEmoji = { heavy: '🔴', moderate: '🟡', normal: '🟢', light: '🟢' };
                footer.innerHTML = `
                    <span>${weatherEmoji[nextBusData.weather] || '🌤️'} ${nextBusData.weather || 'Unknown'}</span>
                    <span>${trafficEmoji[nextBusData.traffic_level] || '⚪'} Traffic: ${nextBusData.traffic_level || 'Unknown'}</span>
                `;
                busList.appendChild(footer);
            }
        } else {
            busList.innerHTML = `
                <li class="next-bus-empty">
                    <span class="empty-icon">🚌</span>
                    <span class="empty-text">Could not load next bus data</span>
                    <span class="empty-sub">Try again when data is updated</span>
                </li>
            `;
        }
    }

    // ══════════════════════════════════════
    //  AI PREDICTION + NEXT BUS LOAD
    // ══════════════════════════════════════
    async function loadAIDataAndRender(hatId) {
        showAILoading();

        // Fire both requests in parallel
        const [aiData, nextBusData] = await Promise.all([
            TransitAPI.getAITahmin(hatId, selection.simHour, selection.simMinute),
            kullaniciStopId ? TransitAPI.getNextBuses(hatId, kullaniciStopId, selection.simHour, selection.simMinute) : Promise.resolve(null)
        ]);

        // Apply AI prediction data
        if (aiData && aiData.real_time_delay_min !== undefined) {
            TransitData.hatlar[hatId].tahminiSure = aiData.real_time_delay_min;

            const colorMap = { 'red': 'red', 'yellow': 'yellow', 'green': 'green' };
            const statusColor = (aiData.status_color || '').toLowerCase();
            TransitData.hatlar[hatId].aktifOtobus.yogunluk = colorMap[statusColor] || 'yellow';

            // AI advice message
            const subtitleEl = document.querySelector('.subtitle');
            if (subtitleEl && aiData.passenger_advice) {
                subtitleEl.innerHTML = `🤖 <b>AI:</b> ${aiData.passenger_advice}`;
                subtitleEl.style.color = '#FFD700';
            }

            // Show fallback status
            if (aiData.is_fallback) {
                fallbackBanner.style.display = 'flex';
            } else {
                fallbackBanner.style.display = 'none';
            }
        }

        hideAILoading();
        renderRoute(hatId);
        renderNextBuses(nextBusData);
    }

    // ── Initial load ──
    loadAIDataAndRender(seciliHatId);

    // ── FAB → Route Detail ──
    fabBtn.addEventListener('click', () => {
        if (navigator.vibrate) navigator.vibrate(20);
        TransitAPI.saveSelection({ ...TransitAPI.getSelection(), seciliHat: seciliHatId, kullaniciDurakIndex });
        const container = document.getElementById('appContainer');
        document.body.classList.add('transitioning');
        container.classList.add('page-transition-out');
        setTimeout(() => { window.location.href = 'route-detail.html'; }, 400);
    });

    // ══════════════════════════════════════
    //  BOTTOM SHEET – Drag Only
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
        mapOverlay.style.background = `rgba(0,0,0,${0.5 * progress})`;
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