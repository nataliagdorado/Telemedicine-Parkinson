function switchRole(role) {
    document.getElementById("roleSelect").value = role;
    const btnPat = document.getElementById("btnPat");
    const btnDoc = document.getElementById("btnDoc");
    const userInput = document.getElementById("usernameInput");

    if (role === "PATIENT") {
        btnPat.classList.add("active");
        btnDoc.classList.remove("active");
        userInput.placeholder = "ID (ej. P001)";
    } else {
        btnDoc.classList.add("active");
        btnPat.classList.remove("active");
        userInput.placeholder = "ID (ej. doctor)";
    }
}

function iniciarSesion() {
    const userInput = document.getElementById("usernameInput").value.trim();
    const passInput = document.getElementById("passwordInput").value;
    const role = document.getElementById("roleSelect").value;
    const statusBox = document.getElementById("statusMessage");

    if (!userInput || !passInput) {
        statusBox.style.color = "#e02424";
        statusBox.textContent = "Introduce ID y contraseña.";
        return;
    }

    statusBox.style.color = "var(--color-accent)";
    statusBox.textContent = "Verificando con el servidor...";

    if (window.javaBridge && typeof window.javaBridge.login === "function") {
        try {
            window.javaBridge.login(userInput, passInput, role);
        } catch (err) {
            statusBox.style.color = "#e02424";
            statusBox.textContent = "Error JS->Java: " + err.message;
        }
    } else {
        statusBox.style.color = "#e02424";
        statusBox.textContent = "Java Bridge no detectado.";
    }
}

let currentProfileData = null;
let isCapturingSignal = false;

function onLoginSuccess(userId, role, token) {
    document.getElementById("landingPortal").classList.add("hidden");

    if (role === "PATIENT") {
        document.getElementById("patientDashboard").classList.remove("hidden");
        document.getElementById("topPatientBadge").textContent = "Patient ID: " + userId;
        if (window.javaBridge && typeof window.javaBridge.loadPatientDashboardData === "function") {
            window.javaBridge.loadPatientDashboardData();
        }
    } else {
        document.getElementById("doctorDashboard").classList.remove("hidden");
        if (window.javaBridge && typeof window.javaBridge.loadDoctorCohort === "function") {
            window.javaBridge.loadDoctorCohort();
        }
    }
}

// Renderiza perfil, estadísticas e historial desde Java
function renderPatientDashboard(profile, reports, sessions) {
    currentProfileData = profile;
    reports = reports || [];
    sessions = sessions || [];

    document.getElementById("profName").textContent = profile.name || profile.patientId;
    document.getElementById("profDiagYear").textContent = profile.diagnosisYear || "N/A";
    document.getElementById("profMeds").textContent = profile.medicationSchedule || "No schedule assigned";

    if (document.getElementById("sideName")) {
        document.getElementById("sideName").textContent = profile.name || profile.patientId;
    }
    if (document.getElementById("sideAvatar")) {
        document.getElementById("sideAvatar").textContent = profile.patientId;
    }

    if (profile.birthDate) {
        const birthYear = Array.isArray(profile.birthDate) ? profile.birthDate[0] : parseInt(String(profile.birthDate).substring(0, 4));
        const age = new Date().getFullYear() - birthYear;
        document.getElementById("profAge").textContent = age + " yrs";
    }

    // Estadísticas de síntomas
    if (reports.length === 0) {
        document.getElementById("statAvgTremor").textContent = "-";
        document.getElementById("statAvgRigidity").textContent = "-";
        document.getElementById("statAdherence").textContent = "--%";
    } else {
        let sumTremor = 0, sumRigidity = 0, medsCount = 0;
        reports.forEach(r => {
            sumTremor += r.tremorLevel || 0;
            sumRigidity += r.rigidityLevel || 0;
            if (r.medicationTaken) medsCount++;
        });
        document.getElementById("statAvgTremor").textContent = (sumTremor / reports.length).toFixed(1);
        document.getElementById("statAvgRigidity").textContent = (sumRigidity / reports.length).toFixed(1);
        document.getElementById("statAdherence").textContent = Math.round((medsCount / reports.length) * 100) + "%";
    }

    // Combinar reportes de síntomas + sesiones de BITalino en el historial
    const listEl = document.getElementById("reportsHistoryList");
    const totalCount = reports.length + sessions.length;
    document.getElementById("reportsCountBadge").textContent = totalCount + " records";

    if (totalCount === 0) {
        listEl.innerHTML = '<p class="empty-msg">No symptom logs or telemetry sessions recorded yet.</p>';
        return;
    }

    const symptomItems = reports.map(r => {
        const rawTime = r.timeStamp ? String(r.timeStamp) : "";
        const dateStr = rawTime.replace("T", " ").substring(0, 16);
        const medBadge = r.medicationTaken ? "Meds ✓" : "Missed";
        return {
            sortKey: rawTime,
            html: `
                <div class="history-item">
                    <span class="history-date">${dateStr} · <strong>Symptom Log</strong></span>
                    <div class="history-metrics">
                        <span>Tremor: ${r.tremorLevel}/5</span>
                        <span>Rigidity: ${r.rigidityLevel}/5</span>
                        <span class="pill-tag">${medBadge}</span>
                    </div>
                </div>
            `
        };
    });

    const sessionItems = sessions.map(s => {
        const rawTime = s.startTime ? String(s.startTime) : "";
        const dateStr = rawTime.replace("T", " ").substring(0, 16);
        return {
            sortKey: rawTime,
            html: `
                <div class="history-item">
                    <span class="history-date">${dateStr} · <strong>BITalino Signal (#${s.sessionId})</strong></span>
                    <div class="history-metrics">
                        <span>Rate: ${s.samplingRateHz} Hz</span>
                        <span class="pill-tag">${s.status}</span>
                    </div>
                </div>
            `
        };
    });

    const combined = [...symptomItems, ...sessionItems]
        .sort((a, b) => b.sortKey.localeCompare(a.sortKey))
        .slice(0, 10);

    listEl.innerHTML = combined.map(item => item.html).join("");
}

// Selector de puntitos 1-5
function selectDot(type, value) {
    const containerId = type === "tremor" ? "tremorDots" : "rigidityDots";
    const inputId = type === "tremor" ? "tremorInput" : "rigidityInput";

    document.getElementById(inputId).value = value;
    const buttons = document.getElementById(containerId).querySelectorAll(".dot-btn");
    buttons.forEach(btn => {
        if (parseInt(btn.getAttribute("data-val")) === value) {
            btn.classList.add("active");
        } else {
            btn.classList.remove("active");
        }
    });
}

// Modal Editar Perfil
function openEditProfileModal() {
    const modal = document.getElementById("editProfileModal");
    if (!modal) {
        alert("Falta el bloque HTML #editProfileModal en index.html");
        return;
    }

    const currentName = (currentProfileData && currentProfileData.name)
        ? currentProfileData.name
        : document.getElementById("profName").textContent;

    const currentMeds = (currentProfileData && currentProfileData.medicationSchedule)
        ? currentProfileData.medicationSchedule
        : document.getElementById("profMeds").textContent;

    document.getElementById("editNameInput").value = currentName !== "Patient" ? currentName : "";
    document.getElementById("editMedsInput").value = currentMeds !== "--" ? currentMeds : "";
    modal.classList.remove("hidden");
}

function closeEditProfileModal() {
    const modal = document.getElementById("editProfileModal");
    if (modal) modal.classList.add("hidden");
}

function saveProfileChanges() {
    const newName = document.getElementById("editNameInput").value.trim();
    const newMeds = document.getElementById("editMedsInput").value.trim();
    if (!newName) return;

    // Actualización visual inmediata
    document.getElementById("profName").textContent = newName;
    document.getElementById("profMeds").textContent = newMeds || "No schedule assigned";
    if (document.getElementById("sideName")) {
        document.getElementById("sideName").textContent = newName;
    }

    // Guardar en el servidor/archivo mediante Java Bridge
    if (window.javaBridge && typeof window.javaBridge.updatePatientProfile === "function") {
        window.javaBridge.updatePatientProfile(newName, newMeds);
    }
    closeEditProfileModal();
}

// Toggle Signal Acquisition
function toggleSignalCapture() {
    if (!isCapturingSignal) {
        const rate = parseInt(document.getElementById("samplingRateSelect").value);
        const duration = parseInt(document.getElementById("durationSelect").value);
        const notes = document.getElementById("recordingNotesInput").value.trim();

        if (window.javaBridge && typeof window.javaBridge.startSignalCapture === "function") {
            document.getElementById("sensorStatusText").textContent = "Connecting to BITalino & Server...";
            window.javaBridge.startSignalCapture(rate, duration, notes);
        }
    } else {
        if (window.javaBridge && typeof window.javaBridge.stopSignalCapture === "function") {
            document.getElementById("sensorStatusText").textContent = "Stopping & closing CSV file...";
            window.javaBridge.stopSignalCapture();
        }
    }
}
function switchPatientTab(tab) {
    const overviewEl = document.getElementById("tabOverviewContent");
    const telemetryEl = document.getElementById("tabTelemetryContent");
    const navOver = document.getElementById("navOverview");
    const navTel = document.getElementById("navTelemetry");

    if (tab === "overview") {
        overviewEl.classList.remove("hidden");
        telemetryEl.classList.add("hidden");
        navOver.classList.add("active");
        navTel.classList.remove("active");
    } else {
        overviewEl.classList.add("hidden");
        telemetryEl.classList.remove("hidden");
        navTel.classList.add("active");
        navOver.classList.remove("active");
    }
}

function selectSamplingRate(rate) {
    document.getElementById("samplingRateSelect").value = rate;
    document.querySelectorAll("#rateSegmented .seg-btn").forEach(btn => {
        if (parseInt(btn.getAttribute("data-rate")) === rate) {
            btn.classList.add("active");
        } else {
            btn.classList.remove("active");
        }
    });
}
function onLoginError(errorMsg) {
    const statusBox = document.getElementById("statusMessage");
    statusBox.style.color = "#e02424";
    statusBox.textContent = errorMsg;
}

function enviarReporte() {
    const tremor = parseInt(document.getElementById("tremorInput").value);
    const rigidity = parseInt(document.getElementById("rigidityInput").value);
    const meds = document.getElementById("medsCheck").checked;
    const freezing = document.getElementById("freezingCheck").checked;
    const statusBox = document.getElementById("reportStatus");

    statusBox.style.color = "var(--color-accent)";
    statusBox.textContent = "Guardando...";

    if (window.javaBridge) {
        window.javaBridge.sendSymptomReport(tremor, rigidity, meds, freezing);
    }
}

function onReportResult(success) {
    const statusBox = document.getElementById("reportStatus");
    if (success) {
        statusBox.style.color = "var(--color-accent)";
        statusBox.textContent = "✓ Reporte guardado en la base de datos.";
    } else {
        statusBox.style.color = "#e02424";
        statusBox.textContent = "Error al persistir el reporte.";
    }
}

// MODALES SUPERIORES
const MODAL_DATA = {
    specs: {
        category: "SYSTEM ARCHITECTURE & HARDWARE",
        title: "TremorLink Biomedical Specifications",
        meta: "Engineered for Movement Disorder Clinics · Revision 2026.2",
        html: `
            <p>TremorLink provides continuous neuromuscular telemedicine infrastructure designed for real-time tremor characterization and motor fluctuation tracking.</p>
            <ul>
                <li><strong>Biosensing Integration:</strong> Native acquisition support for BITalino (r)evolution multichannel biosignals via Bluetooth Low Energy (BLE) and virtual RFCOMM streams.</li>
                <li><strong>Acquisition Channels:</strong> Single-lead Electromyography (EMG, 10-bit analog conversion) and triaxial acceleration (ACC, ±3g dynamic range) at scalable sampling rates (10, 100, 1000 Hz).</li>
                <li><strong>Communication Protocol:</strong> Asynchronous full-duplex TCP stream over port 9000 with atomic JSON packet routing and sub-10ms pipeline latency.</li>
                <li><strong>Security & Storage:</strong> Zero-knowledge SHA-256 password salting, tokenized session validation, and ACID-compliant local repository synchronization.</li>
            </ul>
            <div class="info-highlight-box">
                Certified for ambulatory clinical research and digital biomarkers analysis in neurodegenerative disorders.
            </div>
        `
    },
    protocol: {
        category: "NEUROLOGICAL PROTOCOL",
        title: "Clinical Monitoring Protocol (UPDRS-III)",
        meta: "Parkinson's Disease Assessment Standard · MDS Guidelines",
        html: `
            <p>Standard operating procedure for ambulatory patient monitoring and levodopa therapy calibration:</p>
            <ul>
                <li><strong>Daily Subjective Log:</strong> Patients record tremor severity, muscular rigidity, and sudden motor blockages (Freezing of Gait) twice daily relative to medication intake.</li>
                <li><strong>Kinematic Sessions:</strong> Sensor placement on dominant forearms for resting and postural tremor trials, scheduled during anticipated ON/OFF pharmacological transitions.</li>
                <li><strong>Algorithmic Extraction:</strong> Fast Fourier Transform (FFT) analysis centered within the Parkinsonian diagnostic tremor spectrum (3.5 Hz to 7.0 Hz).</li>
            </ul>
            <div class="info-highlight-box">
                All patient logs are linked to electronic medical records (EMR) accessible to designated neurologists.
            </div>
        `
    },
    support: {
        category: "CLINICAL TECHNICAL DESK",
        title: "Hospital Telemedicine Support",
        meta: "Dedicated Engineering Helpdesk · Ext: 4402",
        html: `
            <p>If you experience communication discrepancies or synchronization delays between client interfaces and the hospital server:</p>
            <ul>
                <li><strong>Local Daemon Verification:</strong> Verify that <code>HospitalServer</code> is running on host <code>127.0.0.1</code> and accepting incoming TCP sockets.</li>
                <li><strong>Hardware Pairing:</strong> Ensure the BITalino acquisition board has active battery levels (>3.7V LiPo) and is paired to the system serial channel.</li>
                <li><strong>Technical Inquiries:</strong> Contact the Department of Medical Engineering at <code>telemed-support@hospital-neuro.org</code> or via emergency hotline.</li>
            </ul>
            <div class="info-highlight-box">
                Logs and diagnostic traces are stored locally in the <code>/data/signals</code> runtime repository.
            </div>
        `
    }
};

function openModal(key) {
    const data = MODAL_DATA[key];
    if (!data) return;

    document.getElementById("modalCategory").textContent = data.category;
    document.getElementById("modalTitle").textContent = data.title;
    document.getElementById("modalContent").innerHTML = data.html;
    document.getElementById("modalMeta").textContent = data.meta;

    document.getElementById("infoModal").classList.remove("hidden");
}

function closeModal() {
    document.getElementById("infoModal").classList.add("hidden");
}

function closeModalOnBackdrop(event) {
    if (event.target.id === "infoModal") {
        closeModal();
    }
}

document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
        closeModal();
    }
});

// ==========================================================================
// DOCTOR PORTAL LOGIC
// ==========================================================================
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

    // 4. Renderizar BITalino Sessions
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
                    <button type="button" class="record-obs-btn" onclick="openObservationModal('${refLabel}')">
                        + Add Observation
                    </button>
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

function toggleSignalCapture() {
    if (!isCapturingSignal) {
        const rate = parseInt(document.getElementById("samplingRateSelect").value);
        const duration = parseInt(document.getElementById("durationSelect").value);

        if (window.javaBridge && typeof window.javaBridge.startSignalCapture === "function") {
            document.getElementById("sensorStatusText").textContent = "Connecting to BITalino & Server...";
            window.javaBridge.startSignalCapture(rate, duration);
        }
    } else {
        if (window.javaBridge && typeof window.javaBridge.stopSignalCapture === "function") {
            document.getElementById("sensorStatusText").textContent = "Stopping & closing CSV file...";
            window.javaBridge.stopSignalCapture();
        }
    }
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

function onCaptureFinished(sessionId, status) {
    isCapturingSignal = false;
    stopRecordingVisuals();

    const btn = document.getElementById("btnStartCapture");
    btn.textContent = "Start Signal Capture";
    btn.style.background = "var(--color-accent)";

    document.getElementById("sensorLed").style.background = "var(--color-accent)";
    document.getElementById("sensorStatusText").textContent = `Session #${sessionId} saved (${status})`;
    document.getElementById("sessionStorageBadge").innerHTML = `Saved: <strong>${sessionId}.csv (${status})</strong>`;
}

function onCaptureError(errMsg) {
    isCapturingSignal = false;
    stopRecordingVisuals();

    const btn = document.getElementById("btnStartCapture");
    btn.textContent = "Start Signal Capture";
    btn.style.background = "var(--color-accent)";
    document.getElementById("sensorStatusText").textContent = "Error: " + errMsg;
}

function startRecordingClock(maxDuration) {
    clearInterval(captureTimerInterval);
    const timerEl = document.getElementById("recordingTimer");
    timerEl.textContent = "00:00";

    captureTimerInterval = setInterval(() => {
        elapsedSeconds++;
        const mins = String(Math.floor(elapsedSeconds / 60)).padStart(2, "0");
        const secs = String(elapsedSeconds % 60).padStart(2, "0");
        timerEl.textContent = `${mins}:${secs}`;
    }, 1000);
}

function stopRecordingVisuals() {
    clearInterval(captureTimerInterval);
    cancelAnimationFrame(waveAnimFrame);
}

function startLiveWaveformAnimation() {
    const canvas = document.getElementById("liveSignalCanvas");
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    let phase = 0;

    function draw() {
        if (!isCapturingSignal) return;
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        // Línea base sutil
        ctx.strokeStyle = "rgba(255,255,255,0.06)";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(0, canvas.height / 2);
        ctx.lineTo(canvas.width, canvas.height / 2);
        ctx.stroke();

        // Onda EMG / Temblor Parkinsónico (5 Hz) en color acento lila
        ctx.strokeStyle = "rgb(198, 124, 211)";
        ctx.lineWidth = 2;
        ctx.beginPath();

        for (let x = 0; x < canvas.width; x++) {
            const tremorWave = Math.sin((x + phase) * 0.08) * 28;
            const emgBurst = Math.sin((x + phase * 1.6) * 0.35) * 14;
            const y = (canvas.height / 2) + tremorWave + emgBurst;
            if (x === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
        }
        ctx.stroke();

        phase += 4;
        waveAnimFrame = requestAnimationFrame(draw);
    }
    draw();
}
