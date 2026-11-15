// ==========================================================================
// DOCTOR PORTAL LOGIC
// ==========================================================================
// @todo quitar si no uso
let doctorCohortCache = [];
let selectedDoctorPatientId = null;

function renderDoctorCohort(patients) {
    doctorCohortCache = patients || [];
    paintCohortList(doctorCohortCache);

    if (doctorCohortCache.length > 0 && !selectedDoctorPatientId) {
        selectDoctorPatient(doctorCohortCache[0].patientId);
    }
}

function paintCohortList(list) {
    const container = document.getElementById("doctorPatientList");
    if (!container) return;

    if (!list || list.length === 0) {
        container.innerHTML = '<div class="cohort-loading" style="color:#94a3b8; font-size:12px; padding:10px;">No patients found.</div>';
        return;
    }

    container.innerHTML = list.map(p => {
        const isActive = p.patientId === selectedDoctorPatientId ? "active" : "";
        return `
            <button type="button" class="cohort-item ${isActive}" onclick="selectDoctorPatient('${p.patientId}')">
                <div>
                    <span class="cohort-item-name">${p.name}</span>
                    <span class="cohort-item-sub">Diag. ${p.diagnosisYear || 'N/A'}</span>
                </div>
                <span class="cohort-id-tag">${p.patientId}</span>
            </button>
        `;
    }).join("");
}

function filterDoctorCohort() {
    const query = document.getElementById("cohortSearchInput").value.toLowerCase().trim();
    const filtered = doctorCohortCache.filter(p =>
        (p.name && p.name.toLowerCase().includes(query)) ||
        (p.patientId && p.patientId.toLowerCase().includes(query))
    );
    paintCohortList(filtered);
}

function selectDoctorPatient(patientId) {
    selectedDoctorPatientId = patientId;
    paintCohortList(doctorCohortCache);

    if (window.javaBridge && typeof window.javaBridge.loadPatientRecordForDoctor === "function") {
        window.javaBridge.loadPatientRecordForDoctor(patientId);
    }
}
//@todo quitar si no uso
function renderDoctorPatientDetail(profile, reports, sessions, notes) {
    document.getElementById("doctorEmptyState").classList.add("hidden");
    document.getElementById("doctorPatientWorkspace").classList.remove("hidden");

    // 1. Cabecera del Perfil
    document.getElementById("docPatName").textContent = profile.name || profile.patientId;
    document.getElementById("docPatId").textContent = profile.patientId;
    document.getElementById("docPatDiag").textContent = profile.diagnosisYear || "N/A";
    document.getElementById("docPatMeds").textContent = profile.medicationSchedule || "None";

    if (profile.birthDate) {
        const birthYear = Array.isArray(profile.birthDate) ? profile.birthDate[0] : parseInt(String(profile.birthDate).substring(0, 4));
        document.getElementById("docPatAge").textContent = (new Date().getFullYear() - birthYear) + " yrs";
    }

    // 2. Métricas Resumen
    document.getElementById("docSympCount").textContent = reports.length;
    document.getElementById("docSessCount").textContent = sessions.length;

    if (reports.length > 0) {
        let sumT = 0, sumR = 0, medsOk = 0;
        reports.forEach(r => {
            sumT += r.tremorLevel || 0;
            sumR += r.rigidityLevel || 0;
            if (r.medicationTaken) medsOk++;
        });
        document.getElementById("docAvgTremor").textContent = (sumT / reports.length).toFixed(1) + " / 5";
        document.getElementById("docAvgRigidity").textContent = (sumR / reports.length).toFixed(1) + " / 5";
        document.getElementById("docAdherence").textContent = Math.round((medsOk / reports.length) * 100) + "%";
    } else {
        document.getElementById("docAvgTremor").textContent = "-";
        document.getElementById("docAvgRigidity").textContent = "-";
        document.getElementById("docAdherence").textContent = "--%";
    }

    // 3. Renderizar Symptom Logs
    const sympContainer = document.getElementById("docSymptomsContainer");
    if (reports.length === 0) {
        sympContainer.innerHTML = '<p class="empty-msg">No symptom logs submitted by this patient.</p>';
    } else {
        const orderedReports = [...reports].reverse();
        sympContainer.innerHTML = orderedReports.map(r => {
            const dateStr = r.timeStamp ? String(r.timeStamp).replace("T", " ").substring(0, 16) : "";
            const fogText = r.freezingEpisodes ? "FoG: Yes" : "FoG: No";
            const medText = r.medicationTaken ? "Meds Taken" : "Meds Missed";
            const refLabel = `Symptom Log #${r.reportId} (${dateStr})`;

            return `
                <div class="record-row-card">
                    <div class="record-main-info">
                        <div class="record-top-line">
                            <span class="record-id-code">#${r.reportId}</span>
                            <span class="record-timestamp">${dateStr}</span>
                        </div>
                        <div class="record-scores-line">
                            <span>Tremor: <strong>${r.tremorLevel}/5</strong></span>
                            <span>Rigidity: <strong>${r.rigidityLevel}/5</strong></span>
                            <span>${medText}</span>
                            <span>${fogText}</span>
                        </div>
                    </div>
                    <button type="button" class="record-obs-btn" onclick="openObservationModal('${refLabel}')">
                        + Add Observation
                    </button>
                </div>
            `;
        }).join("");
    }

    // 4. Renderizar BITalino Sessions (con botón "Inspect Waveform" + "+ Add Observation")
    const sessContainer = document.getElementById("docSessionsContainer");
    if (sessions.length === 0) {
        sessContainer.innerHTML = '<p class="empty-msg">No BITalino telemetry sessions recorded yet.</p>';
    } else {
        const orderedSessions = [...sessions].reverse();
        sessContainer.innerHTML = orderedSessions.map(s => {
            const startStr = s.startTime ? String(s.startTime).replace("T", " ").substring(0, 16) : "";
            const refLabel = `BITalino Session #${s.sessionId} (${s.samplingRateHz} Hz)`;

            return `
                <div class="record-row-card">
                    <div class="record-main-info">
                        <div class="record-top-line">
                            <span class="record-id-code">#${s.sessionId}</span>
                            <span class="record-timestamp">${startStr}</span>
                        </div>
                        <div class="record-scores-line">
                            <span>Rate: <strong>${s.samplingRateHz} Hz</strong></span>
                            <span>Status: <strong>${s.status}</strong></span>
                        </div>
                    </div>
                    <div style="display:flex; gap:8px;">
                        <button type="button" class="record-obs-btn" style="border-color: var(--color-accent); color: var(--color-accent);" onclick="inspectSessionWaveform('${s.sessionId}')">
                            Inspect Waveform
                        </button>
                        <button type="button" class="record-obs-btn" onclick="openObservationModal('${refLabel}')">
                            + Add Observation
                        </button>
                    </div>
                </div>
            `;
        }).join("");
    }

    // 5. Renderizar Observaciones Clínicas (usando n.observation de ClinicalNote.java)
    const notesFeed = document.getElementById("docNotesFeed");
    document.getElementById("docNotesCount").textContent = notes.length;

    if (notes.length === 0) {
        notesFeed.innerHTML = '<p class="empty-msg">No observations recorded for this patient.</p>';
    } else {
        const orderedNotes = [...notes].reverse();
        notesFeed.innerHTML = orderedNotes.map(n => {
            const timeStr = n.timestamp ? String(n.timestamp).replace("T", " ").substring(0, 16) : "";
            return `
                <div class="note-entry">
                    <div class="note-entry-meta">
                        <strong>${n.doctorId || 'Specialist'}</strong>
                        <span>${timeStr}</span>
                    </div>
                    <div class="note-entry-text">${n.observation}</div>
                </div>
            `;
        }).join("");
    }
}

function switchDoctorRecordTab(tab) {
    const sympEl = document.getElementById("docSymptomsContainer");
    const sessEl = document.getElementById("docSessionsContainer");
    const btnSymp = document.getElementById("tabDocSymptoms");
    const btnSess = document.getElementById("tabDocSessions");

    if (tab === "symptoms") {
        sympEl.classList.remove("hidden");
        sessEl.classList.add("hidden");
        btnSymp.classList.add("active");
        btnSess.classList.remove("active");
    } else {
        sympEl.classList.add("hidden");
        sessEl.classList.remove("hidden");
        btnSess.classList.add("active");
        btnSymp.classList.remove("active");
    }
}

function openObservationModal(recordRef) {
    document.getElementById("obsRecordRefInput").value = recordRef;
    document.getElementById("obsTargetLabel").textContent = recordRef;
    document.getElementById("obsTextArea").value = "";
    document.getElementById("observationModal").classList.remove("hidden");
}

function closeObservationModal() {
    document.getElementById("observationModal").classList.add("hidden");
}

function submitDoctorObservation() {
    const recordRef = document.getElementById("obsRecordRefInput").value;
    const text = document.getElementById("obsTextArea").value.trim();
    if (!text || !selectedDoctorPatientId) return;

    if (window.javaBridge && typeof window.javaBridge.saveDoctorObservation === "function") {
        window.javaBridge.saveDoctorObservation(selectedDoctorPatientId, recordRef, text);
    }
    closeObservationModal();
}

let captureTimerInterval = null;
let waveAnimFrame = null;
let elapsedSeconds = 0;

function selectDuration(sec) {
    document.getElementById("durationSelect").value = sec;
    document.querySelectorAll("#durationSegmented .seg-btn").forEach(btn => {
        if (parseInt(btn.getAttribute("data-dur")) === sec) {
            btn.classList.add("active");
        } else {
            btn.classList.remove("active");
        }
    });
}



function onCaptureStarted(sessionId, rate, duration) {
    isCapturingSignal = true;
    elapsedSeconds = 0;

    const btn = document.getElementById("btnStartCapture");
    btn.textContent = "Stop Recording";
    btn.style.background = "#e11d48";

    document.getElementById("sensorStatusText").textContent = `Recording Session #${sessionId} (${rate} Hz)`;
    document.getElementById("sensorLed").style.background = "#10b981";
    document.getElementById("sessionStorageBadge").innerHTML = `CSV Stream: <strong>data/signals/${sessionId}.csv</strong>`;

    startRecordingClock(duration);
    startLiveWaveformAnimation();
}



// ==========================================================================
// DOCTOR WAVEFORM INSPECTOR (CSV TELEMETRY RENDERER)
// Esto es para que el docto rvisualice lo que ya hemos grabado
// ==========================================================================
let wfCurrentSessionId = null;
let wfEmgData = [];
let wfAccData = [];
let wfActiveChannel = "EMG";
// Variables de estado. wLoquesea. Abre el modal, quitándole la clase hidden.P one unmensaje de carga y llama a LoadSignalWaveFormFromDoctor
function inspectSessionWaveform(sessionId) {
    wfCurrentSessionId = sessionId;
    document.getElementById("wfSessionBadge").textContent = "SESSION #" + sessionId;
    document.getElementById("wfModalTitle").textContent = "Loading Telemetry Stream...";
    document.getElementById("wfSampleCount").textContent = "Reading CSV repository...";
    document.getElementById("waveformModal").classList.remove("hidden");

    if (window.javaBridge && typeof window.javaBridge.loadSignalWaveformForDoctor === "function") {
        window.javaBridge.loadSignalWaveformForDoctor(sessionId);
    }
}

function closeWaveformModal() {
    document.getElementById("waveformModal").classList.add("hidden");
}

function onSignalWaveformError(msg) {
    document.getElementById("wfModalTitle").textContent = "Signal Unavailable";
    document.getElementById("wfSampleCount").textContent = msg;
}
// Cuando Java termina de leer el CSV, llama a esta función pasándole los arrays reales, el total de muestras y el análisis de TremorAnalyzer
function renderDoctorWaveformModal(sessionId, emgArray, accArray, totalSamples, metrics) {
    wfCurrentSessionId = sessionId;
    wfEmgData = emgArray || [];
    wfAccData = accArray || [];
    wfActiveChannel = "EMG";

    document.getElementById("wfModalTitle").textContent = "Biosignal Recording · #" + sessionId;
    document.getElementById("wfSampleCount").textContent = `${totalSamples.toLocaleString()} samples archived in CSV`;

    // Inyectar los resultados biomédicos de TremorAnalyzer y SignalProcessor justo encima del canvas
    const canvasEl = document.getElementById("doctorWaveformCanvas");
    if (canvasEl && canvasEl.parentElement) {
        let kpiBar = document.getElementById("tremor-analysis-kpis");
        if (!kpiBar) {
            kpiBar = document.createElement("div");
            kpiBar.id = "tremor-analysis-kpis";
            kpiBar.style.cssText = "display:grid; grid-template-columns:repeat(3,1fr); gap:10px; margin-bottom:14px;";
            canvasEl.parentElement.insertBefore(kpiBar, canvasEl);
        }

        if (metrics) {
            const freqText = metrics.dominantHz !== null ? `${metrics.dominantHz} Hz` : "N/A (< 2s)";
            const bandBadge = metrics.dominantHz !== null
                ? (metrics.inRestBand
                    ? '<span style="color:#dc2626; font-weight:700;">● Rest Tremor (4–6 Hz)</span>'
                    : '<span style="color:#16a34a; font-weight:700;">● Outside 4–6 Hz</span>')
                : '<span style="color:#64748b;">Short recording</span>';

            const accText = metrics.accRmsG !== null ? `${metrics.accRmsG} g` : `${metrics.accRmsAdc} ADC`;
            const emgText = metrics.emgRmsMv !== null ? `${metrics.emgRmsMv} mV` : "—";

            kpiBar.innerHTML = `
                <div style="background:#f8fafc; border:1px solid #e2e8f0; border-radius:10px; padding:10px 12px;">
                    <div style="font-size:10px; font-weight:700; color:#64748b; text-transform:uppercase;">Dominant Tremor (DFT)</div>
                    <div style="font-size:18px; font-weight:800; color:#0f172a; margin:2px 0;">${freqText}</div>
                    <div style="font-size:11px;">${bandBadge}</div>
                </div>
                <div style="background:#f8fafc; border:1px solid #e2e8f0; border-radius:10px; padding:10px 12px;">
                    <div style="font-size:10px; font-weight:700; color:#64748b; text-transform:uppercase;">Tremor Intensity (RMS)</div>
                    <div style="font-size:18px; font-weight:800; color:#0f172a; margin:2px 0;">${accText}</div>
                    <div style="font-size:11px; color:#64748b;">3-Axis Accel (${metrics.analysedSeconds}s)</div>
                </div>
                <div style="background:#f8fafc; border:1px solid #e2e8f0; border-radius:10px; padding:10px 12px;">
                    <div style="font-size:10px; font-weight:700; color:#64748b; text-transform:uppercase;">Muscle Rigidity (EMG)</div>
                    <div style="font-size:18px; font-weight:800; color:#0f172a; margin:2px 0;">${emgText}</div>
                    <div style="font-size:11px; color:#64748b;">RMS via SignalProcessor</div>
                </div>
            `;
        } else {
            kpiBar.innerHTML = "";
        }
    }

    // Enlazar el botón directo para añadir observación tras ver la onda
    const obsBtn = document.getElementById("wfAddObsBtn");
    if (obsBtn) {
        obsBtn.onclick = () => {
            closeWaveformModal();
            openObservationModal(`BITalino Session #${sessionId} (Waveform Review)`);
        };
    }

    switchWaveformChannel("EMG");
}

// cambia el estilo del btón cuando está activo
function switchWaveformChannel(channel) {
    wfActiveChannel = channel;
    const btnEmg = document.getElementById("btnWfEmg");
    const btnAcc = document.getElementById("btnWfAcc");
    const descEl = document.getElementById("wfChannelDesc");

    if (channel === "EMG") {
        btnEmg.classList.add("active");
        btnAcc.classList.remove("active");
        descEl.textContent = "Channel A1: Surface Electromyography (10-bit ADC)";
        drawSignalArrayOnCanvas(wfEmgData, "rgb(198, 124, 211)");
    } else {
        btnAcc.classList.add("active");
        btnEmg.classList.remove("active");
        descEl.textContent = "Channel A2: Triaxial Accelerometer X-Axis (Tremor Kinematic)";
        drawSignalArrayOnCanvas(wfAccData, "#38bdf8");
    }
}
// dibujo el lienzo oscuro, calculo el valor min y max, y recorro cada muestra con forEach
function drawSignalArrayOnCanvas(samples, strokeColor) {
    const canvas = document.getElementById("doctorWaveformCanvas");
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    const w = canvas.width;
    const h = canvas.height;

    ctx.clearRect(0, 0, w, h);

    // Rejilla clínica sutil
    ctx.strokeStyle = "rgba(255, 255, 255, 0.05)";
    ctx.lineWidth = 1;
    for (let y = 44; y < h; y += 44) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(w, y);
        ctx.stroke();
    }

    if (!samples || samples.length === 0) {
        document.getElementById("wfPeakStat").textContent = "Amplitude Range: No data";
        return;
    }

    let minVal = Math.min(...samples);
    let maxVal = Math.max(...samples);
    if (maxVal === minVal) {
        maxVal += 10;
        minVal -= 10;
    }

    document.getElementById("wfPeakStat").textContent = `ADC Range: ${minVal} – ${maxVal} units`;

    const padY = 24;
    const drawH = h - padY * 2;
    const stepX = w / Math.max(1, samples.length - 1);

    ctx.strokeStyle = strokeColor;
    ctx.lineWidth = 1.8;
    ctx.beginPath();

    samples.forEach((val, idx) => {
        const norm = (val - minVal) / (maxVal - minVal);
        const x = idx * stepX;
        const y = (h - padY) - (norm * drawH);
        if (idx === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
    });

    ctx.stroke();
}