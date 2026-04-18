// =========================================
//  SCRIPT.JS – Home Page
//  Line locking + dependent dropdown + simulation
//  Backend fallback to local TransitData
// =========================================

function updateClock() {
    const now = new Date();
    const h = now.getHours().toString().padStart(2, '0');
    const m = now.getMinutes().toString().padStart(2, '0');
    const el = document.getElementById('statusTime');
    if (el) el.textContent = `${h}:${m}`;
}

// ── Line lock state ──
let kilitliHatId = null;
let kilitliHatAdi = null;

// ── Autocomplete ──
function setupAutocomplete(inputId, dropdownId, containerId, mode) {
    const input = document.getElementById(inputId);
    const dropdown = document.getElementById(dropdownId);
    const container = document.getElementById(containerId);

    let tumDuraklar = [];
    
    function populateDuraklar() {
        if (tumDuraklar.length > 0) return;
        if (typeof TransitAPI !== 'undefined' && TransitAPI.gercekDuraklar && TransitAPI.gercekDuraklar.length > 0) {
            tumDuraklar = TransitAPI.gercekDuraklar.map(d => ({
                stopName: d.stopName || d.stop_name || d.id,
                lineId: d.lineId || "L01",
                lineName: (typeof TransitData !== 'undefined' && TransitData.hatlar[d.lineId]) ? TransitData.hatlar[d.lineId].ad : (d.lineId || "L01"),
                stopId: d.stopId || d.id
            }));
        } else if (typeof TransitData !== 'undefined') {
        Object.entries(TransitData.hatlar).forEach(([hatId, hat]) => {
            hat.duraklar.forEach((durakAdi, idx) => {
                const stopIds = TransitData.durakStopIdMap[hatId];
                tumDuraklar.push({
                    stopName: durakAdi,
                    lineId: hatId,
                    lineName: hat.ad,
                    stopId: stopIds ? stopIds[idx] : `${hatId}-${idx}`
                });
            });
        });
        console.log("✅ Loaded", tumDuraklar.length, "stops for autocomplete");
    }

    input.addEventListener('input', function () {
        populateDuraklar();
        const q = this.value.toLowerCase();
        if (q.length < 1 || tumDuraklar.length === 0) {
            dropdown.style.display = 'none';
            dropdown.innerHTML = '';
            return;
        }
        dropdown.style.display = 'block';

        let durakListesi = [];
        if (mode === 'bitis' && kilitliHatId) {
            durakListesi = tumDuraklar.filter(d => d.lineId === kilitliHatId);
        } else {
            durakListesi = tumDuraklar;
        }

        const filtreli = durakListesi.filter(d =>
            d.stopName && d.stopName.toLowerCase().includes(q)
        );

        // Unique stop names
        const benzersiz = [];
        const seen = new Map();
        for (const item of filtreli) {
            if (!seen.has(item.stopName)) {
                seen.set(item.stopName, true);
                benzersiz.push(item);
            }
        }

        dropdown.innerHTML = '';

        if (benzersiz.length > 0) {
            benzersiz.forEach(durak => {
                const li = document.createElement('li');
                li.textContent = durak.stopName;

                const hatlar = tumDuraklar.filter(d => d.stopName === durak.stopName).map(d => d.lineId);

                if (hatlar.length > 0) {
                    const badge = document.createElement('span');
                    badge.style.cssText = 'font-size:10px;color:rgba(255,255,255,0.35);margin-left:8px;';
                    badge.textContent = [...new Set(hatlar)].join(', ');
                    li.appendChild(badge);
                }

                li.addEventListener('click', function () {
                    input.value = durak.stopName;
                    dropdown.style.display = 'none';
                    if (mode === 'baslangic') {
                        onBaslangicSecildi(durak.stopName, hatlar[0]);
                    }
                });
                dropdown.appendChild(li);
            });
        } else {
            if (mode === 'bitis' && kilitliHatId) {
                dropdown.innerHTML = `<li style="color:rgba(255,255,255,0.35);cursor:default;">No stop matching "${q}" on this line</li>`;
            } else {
                dropdown.innerHTML = '<li style="color:rgba(255,255,255,0.35);cursor:default;">Stop not found</li>';
            }
        }
    });

    document.addEventListener('click', function (e) {
        if (!container.contains(e.target)) dropdown.style.display = 'none';
    });
}

// ═══ LINE LOCK ═══
function onBaslangicSecildi(durakAdi, hatId) {
    const endInput = document.getElementById('end-input');
    const hatLockBanner = document.getElementById('hatLockBanner');

    if (hatId) {
        kilitliHatId = hatId;
        const hat = (typeof TransitData !== 'undefined' && TransitData.hatlar) ? TransitData.hatlar[hatId] : null;
        kilitliHatAdi = hat ? hat.ad : hatId;

        endInput.value = '';
        endInput.disabled = false;
        endInput.placeholder = `Search on ${kilitliHatAdi}...`;

        if (hatLockBanner) {
            hatLockBanner.innerHTML = `
                <span class="lock-icon">🔒</span>
                <span class="lock-text"><strong>${hatId}</strong> – ${kilitliHatAdi}</span>
                <button class="lock-clear" id="clearLock">✕</button>
            `;
            hatLockBanner.style.display = 'flex';
            if (hat) hatLockBanner.style.borderLeftColor = hat.renk;

            document.getElementById('clearLock').addEventListener('click', (e) => {
                e.stopPropagation();
                clearHatLock();
            });
        }
    } else {
        kilitliHatId = null;
        endInput.placeholder = 'Search...';
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
    endInput.placeholder = 'Search...';
    if (hatLockBanner) hatLockBanner.style.display = 'none';
}

function navigateWithTransition(url) {
    const container = document.getElementById('appContainer');
    document.body.classList.add('transitioning');
    container.classList.add('page-transition-out');
    setTimeout(() => { window.location.href = url; }, 400);
}

// ═══ INIT ═══
document.addEventListener('DOMContentLoaded', () => {
    setupAutocomplete('start-input', 'start-dropdown', 'start-container', 'baslangic');
    setupAutocomplete('end-input', 'end-dropdown', 'end-container', 'bitis');
    updateClock();
    setInterval(updateClock, 30000);

    // ── Simulation ──
    const simToggleMain = document.getElementById('simToggleMain');
    const simControls = document.getElementById('simControls');
    const simTimeSlider = document.getElementById('simTimeSlider');
    const simTimeDisplay = document.getElementById('simTimeDisplay');

    let isSimulationActive = false;
    let simHour = 12;
    let simMinute = 0;

    if (simToggleMain) {
        simToggleMain.addEventListener('change', function () {
            isSimulationActive = this.checked;
            if (simControls) simControls.style.display = isSimulationActive ? 'flex' : 'none';
            if (isSimulationActive && simTimeSlider) {
                updateSimDisplay(simTimeSlider.value);
            }
        });
    }

    if (simTimeSlider) {
        simTimeSlider.addEventListener('input', function () {
            updateSimDisplay(this.value);
        });
    }

    function updateSimDisplay(val) {
        const totalMinutes = parseInt(val, 10);
        simHour = Math.floor(totalMinutes / 60);
        simMinute = totalMinutes % 60;
        if (simTimeDisplay) {
            simTimeDisplay.textContent = `${simHour.toString().padStart(2, '0')}:${simMinute.toString().padStart(2, '0')}`;
        }
    }

    // ── Find Route Button ──
    const btnNext = document.getElementById('btnNext');
    if (btnNext) {
        btnNext.addEventListener('click', async (e) => {
            e.preventDefault();
            const baslangic = document.getElementById('start-input').value;
            const varis = document.getElementById('end-input').value;

            if (!baslangic || !varis) {
                if (!baslangic) {
                    const box = document.querySelector('#start-container .glass-input-box');
                    if (box) box.classList.add('shake');
                }
                if (!varis) {
                    const box = document.querySelector('#end-container .glass-input-box');
                    if (box) box.classList.add('shake');
                }
                setTimeout(() => {
                    document.querySelectorAll('.shake').forEach(el => el.classList.remove('shake'));
                }, 600);
                return;
            }

            // --- Auto-Lock Line if manually typed ---
            if (!kilitliHatId && baslangic) {
                if (typeof TransitAPI !== 'undefined') {
                    const hatlar = TransitAPI.getDurakHatlari(baslangic.trim());
                    if (hatlar.length > 0) {
                        kilitliHatId = hatlar[0].hatId;
                    }
                }
            }

            // --- Invalid Input Validation ---
            let validStops = [];
            if (typeof TransitAPI !== 'undefined') {
                validStops = TransitAPI.getTumDuraklar().map(d => d.toLowerCase());
            } else if (typeof TransitData !== 'undefined') {
                const set = new Set();
                Object.values(TransitData.hatlar).forEach(hat => hat.duraklar.forEach(d => set.add(d.toLowerCase())));
                validStops = [...set];
            }

            const isStartValid = validStops.includes(baslangic.trim().toLowerCase());
            
            let isEndValid = false;
            if (kilitliHatId && typeof TransitData !== 'undefined' && TransitData.hatlar[kilitliHatId]) {
                const lockedLineStops = TransitData.hatlar[kilitliHatId].duraklar.map(d => d.toLowerCase());
                isEndValid = lockedLineStops.includes(varis.trim().toLowerCase());
            } else {
                isEndValid = validStops.includes(varis.trim().toLowerCase());
            }

            if (!isStartValid || !isEndValid) {
                if (!isStartValid) {
                    const box = document.querySelector('#start-container .glass-input-box');
                    if (box) box.classList.add('shake');
                }
                if (!isEndValid) {
                    const box = document.querySelector('#end-container .glass-input-box');
                    if (box) box.classList.add('shake');
                }
                setTimeout(() => {
                    document.querySelectorAll('.shake').forEach(el => el.classList.remove('shake'));
                }, 600);
                return;
            }

            // Save selection
            if (typeof TransitAPI !== 'undefined') {
                let finalStopId = TransitAPI.getStopId(baslangic, kilitliHatId);
                let targetStopId = TransitAPI.getStopId(varis, kilitliHatId);

                TransitAPI.saveSelection({
                    baslangic,
                    varis,
                    hatId: kilitliHatId,
                    stopId: finalStopId,
                    destinationId: targetStopId,
                    simHour: isSimulationActive ? simHour : null,
                    simMinute: isSimulationActive ? simMinute : null
                });
            }
            navigateWithTransition('map.html');
        });
    }
});