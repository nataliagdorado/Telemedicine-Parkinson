/// NAVEGACIÓN INICIAL
// Autenticación y entrada

// Alterna entre la tarjeta del login en modo Patietn y en modo Doctor
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
// Envío de credenciales a Bridge.login
function iniciarSesion() {
    const userInput = document.getElementById("usernameInput").value.trim();
    const passInput = document.getElementById("passwordInput").value;
    const role = document.getElementById("roleSelect").value;
    const statusBox = document.getElementById("statusMessage");

    if (!userInput || !passInput) {
        statusBox.style.color = "#e02424";
        statusBox.textContent = "Introduce ID and password";
        return;
    }

    statusBox.style.color = "var(--color-accent)";
    statusBox.textContent = "Verifying with the server...";

    if (window.javaBridge && typeof window.javaBridge.login === "function") {
        try {
            window.javaBridge.login(userInput, passInput, role);
        } catch (err) {
            statusBox.style.color = "#e02424";
            statusBox.textContent = "Error JS->Java: " + err.message;
        }
    } else {
        statusBox.style.color = "#e02424";
        statusBox.textContent = "Java Bridge not detected";
    }
}

let currentProfileData = null;
let isCapturingSignal = false;

//  @TODO si no la uso, quitarla
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
// @todo quitar si al final no usoooooo
function renderPatientDashboard(profile, reports, sessions) {
    // y aseguro que reports y sessions sean listas aunque vengan vacías (|| [])
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
    // calcula las estadísticas laterales (media de temblor, rigidez y % de medicación)
    // Recorre todos los reportes con .forEach() sumando los valores y divide entre el total
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
    // 4. Construye el historial combinado de síntomas + sesiones BITalino
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
        alert("Error, falta el html (pruebas)");
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

function toggleSignalCapture() {
    // RAMA 1: Si NO está grabando, arranca la captura
    // Lee del DOM la frecuencia (Hz), duración (seg) y notas que eligió el paciente
    if (!isCapturingSignal) {
        const rate = parseInt(document.getElementById("samplingRateSelect").value);
        const duration = parseInt(document.getElementById("durationSelect").value);
        const notes = document.getElementById("recordingNotesInput").value.trim();

        // Comprueba que el puente con Java existe y llama al metodo Java startSignalCapture()
        if (window.javaBridge && typeof window.javaBridge.startSignalCapture === "function") {
            document.getElementById("sensorStatusText").textContent = "Connecting to BITalino & Server...";
            window.javaBridge.startSignalCapture(rate, duration, notes);
        }
    } else {

        // RAMA 2: Si YA está grabando (else), pide detener la captura
        if (window.javaBridge && typeof window.javaBridge.stopSignalCapture === "function") {
            document.getElementById("sensorStatusText").textContent = "Stopping & closing CSV file...";
            window.javaBridge.stopSignalCapture();
        }
    }
}
