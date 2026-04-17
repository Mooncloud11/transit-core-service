// =========================================
//  ROUTE-DETAIL.JS – Route Detail Page
//  Shows line stops as a frosted glass timeline
// =========================================

document.addEventListener('DOMContentLoaded', () => {
    const timeline = document.getElementById('detailTimeline');
    const header = document.getElementById('detailHeader');
    const hatNameEl = document.getElementById('detailHatName');
    const hatMetaEl = document.getElementById('detailHatMeta');
    const searchInput = document.getElementById('searchStopsInput');
    const btnBack = document.getElementById('btnBack');
    const btnBackToMap = document.getElementById('btnBackToMap');
    const searchToggleBtn = document.getElementById('searchToggleBtn');
    const searchBarContainer = document.getElementById('searchBarContainer');
    const timelineContainer = document.getElementById('timelineContainer');

    // ── Clock ──
    function updateClock() {
        const now = new Date();
        const el = document.getElementById('statusTime');
        if (el) el.textContent = `${now.getHours().toString().padStart(2,'0')}:${now.getMinutes().toString().padStart(2,'0')}`;
    }
    updateClock();
    setInterval(updateClock, 30000);

    // ── Read selection ──
    const selection = typeof TransitAPI !== 'undefined' ? TransitAPI.getSelection() : {};
    const hatId = selection.seciliHat || 'L01';
    const hat = (typeof TransitData !== 'undefined' && TransitData.hatlar) ? TransitData.hatlar[hatId] : null;
    const kullaniciDurakIdx = selection.kullaniciDurakIndex !== undefined ? selection.kullaniciDurakIndex : 6;

    if (!hat) {
        if(timeline) timeline.innerHTML = '<p style="text-align:center;color:rgba(255,255,255,0.4);padding:40px;">Line not found</p>';
        return;
    }

    const busIdx = hat.aktifOtobus ? hat.aktifOtobus.mevcutDurakIndex : 0;
    const yogunluk = hat.aktifOtobus ? hat.aktifOtobus.yogunluk : 'green';
    const renk = hat.renk || '#FFFFFF';

    // ── Header ──
    if (hatNameEl) hatNameEl.textContent = `${hat.id} ${hat.ad}`;
    if (hatMetaEl) hatMetaEl.textContent = `${hat.duraklar.length} stops • Est. ${hat.tahminiSure || 0} min`;
    if (timeline) timeline.style.setProperty('--hat-renk', renk);

    // ── Toggle Search ──
    if (searchToggleBtn && searchBarContainer) {
        searchToggleBtn.addEventListener('click', () => {
            if (searchBarContainer.style.display === 'none') {
                searchBarContainer.style.display = 'block';
                if(searchInput) searchInput.focus();
            } else {
                searchBarContainer.style.display = 'none';
                if(searchInput) searchInput.value = '';
                renderTimeline();
            }
        });
    }

    // ══════════════════════════════════════
    //  TIMELINE RENDER (Frosted Glass Layout)
    // ══════════════════════════════════════
    function renderTimeline(filter = '') {
        if(!timeline) return;
        timeline.innerHTML = '';

        hat.duraklar.forEach((durakAdi, i) => {
            const isPassed = i <= busIdx;
            const isBus = i === busIdx;
            const isUser = i === kullaniciDurakIdx;
            const isLast = i === hat.duraklar.length - 1;

            // Filter
            const matchesFilter = !filter || durakAdi.toLowerCase().includes(filter.toLowerCase());

            const row = document.createElement('div');
            row.className = 'detail-stop-row';
            if (!matchesFilter) row.classList.add('hidden-by-search');

            // --- Left: Stop Name ---
            const leftDiv = document.createElement('div');
            leftDiv.className = 'detail-stop-name-left';
            if (isBus || isUser) leftDiv.classList.add('highlight');
            leftDiv.innerHTML = durakAdi.replace(' ', '<br>'); // Mockup has line breaks for long words
            row.appendChild(leftDiv);

            // --- Center: Visuals (Dot & Line) ---
            const centerDiv = document.createElement('div');
            centerDiv.className = 'detail-stop-center';

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
            centerDiv.appendChild(dot);

            // Line
            if (!isLast) {
                const line = document.createElement('div');
                line.className = 'detail-stop-line';
                if (isPassed && !isBus) line.classList.add('passed');
                centerDiv.appendChild(line);
            }
            row.appendChild(centerDiv);

            // --- Right: Info (Time / Tags) ---
            const rightDiv = document.createElement('div');
            rightDiv.className = 'detail-stop-info-right';

            // Fake time calculation based on index difference
            let timeText = '';
            if(isBus) timeText = '0 min';
            else if (i > busIdx) {
                timeText = `${(i - busIdx) * 7} min`;
            } else {
                 timeText = '✓ Passed';
            }

            const timeSpan = document.createElement('span');
            timeSpan.textContent = timeText;
            rightDiv.appendChild(timeSpan);

            if (isUser) {
                const tag = document.createElement('span');
                tag.className = `detail-stop-tag user-tag ${yogunluk}`;
                const crowdText = { red: 'Crowded', yellow: 'Moderate', green: 'Empty' };
                tag.textContent = crowdText[yogunluk];
                rightDiv.appendChild(tag);
            }
            
            row.appendChild(rightDiv);

            // Append row to timeline
            timeline.appendChild(row);
        });
    }

    renderTimeline();

    // ══════════════════════════════════════
    //  STOP SEARCH
    // ══════════════════════════════════════
    if(searchInput) {
        searchInput.addEventListener('input', function() {
            renderTimeline(this.value);
        });
    }

    // ══════════════════════════════════════
    //  BACK BUTTONS
    // ══════════════════════════════════════
    if(btnBack) {
        btnBack.addEventListener('click', () => {
            if (navigator.vibrate) navigator.vibrate(20);
            const container = document.getElementById('appContainer');
            container.classList.remove('page-transition-in');
            container.classList.add('page-transition-out');
            setTimeout(() => { window.location.href = 'map.html'; }, 400);
        });
    }

    if (btnBackToMap) {
        btnBackToMap.addEventListener('click', () => {
            if (navigator.vibrate) navigator.vibrate(20);
            const container = document.getElementById('appContainer');
            container.classList.remove('page-transition-in');
            container.classList.add('page-transition-out');
            setTimeout(() => { window.location.href = 'map.html'; }, 400);
        });
    }
});