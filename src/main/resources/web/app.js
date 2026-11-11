function iniciarSesion() {
    const userInput = document.getElementById("usernameInput").value.trim();
    const role = document.getElementById("roleSelect").value;
    const statusBox = document.getElementById("statusMessage");

    if (!userInput) {
        statusBox.style.color = "#ff7b72";
        statusBox.textContent = "Por favor, introduce tu identificador.";
        return;
    }

    statusBox.style.color = "#8b949e";
    statusBox.textContent = "Conectando...";

    if (window.javaBridge) {
        window.javaBridge.login(userInput, role);
    } else {
        statusBox.style.color = "#ff7b72";
        statusBox.textContent = "Java Bridge no detectado.";
    }
}

function onLoginSuccess(userId, role, token) {
    document.getElementById("loginCard").classList.add("hidden");

    if (role === "PATIENT") {
        document.getElementById("patientDashboard").classList.remove("hidden");
        document.getElementById("patientGreeting").textContent = "Paciente: " + userId;
    } else {
        document.getElementById("doctorDashboard").classList.remove("hidden");
    }
}

function onLoginError(errorMsg) {
    const statusBox = document.getElementById("statusMessage");
    statusBox.style.color = "#ff7b72";
    statusBox.textContent = "Error: " + errorMsg;
}

function enviarReporte() {
    const tremor = parseInt(document.getElementById("tremorInput").value);
    const rigidity = parseInt(document.getElementById("rigidityInput").value);
    const meds = document.getElementById("medsCheck").checked;
    const freezing = document.getElementById("freezingCheck").checked;
    const statusBox = document.getElementById("reportStatus");

    statusBox.style.color = "#8b949e";
    statusBox.textContent = "Guardando reporte en el servidor...";

    if (window.javaBridge) {
        window.javaBridge.sendSymptomReport(tremor, rigidity, meds, freezing);
    }
}

function onReportResult(success) {
    const statusBox = document.getElementById("reportStatus");
    if (success) {
        statusBox.style.color = "#ccff00";
        statusBox.textContent = "✓ Reporte registrado en base de datos.";
    } else {
        statusBox.style.color = "#ff7b72";
        statusBox.textContent = "Error al persistir el reporte.";
    }
}
function selectRole(role) {
    document.getElementById("roleSelect").value = role;
    const tabPatient = document.getElementById("tabPatient");
    const tabDoctor = document.getElementById("tabDoctor");
    const userLabel = document.getElementById("userLabel");
    const userInput = document.getElementById("usernameInput");

    if (role === "PATIENT") {
        tabPatient.classList.add("active");
        tabDoctor.classList.remove("active");
        userLabel.textContent = "ID de Paciente";
        userInput.placeholder = "Ej. P001";
    } else {
        tabDoctor.classList.add("active");
        tabPatient.classList.remove("active");
        userLabel.textContent = "Identificador Médico";
        userInput.placeholder = "Ej. doctor_diego";
    }
}

function iniciarSesion() {
    const userInput = document.getElementById("usernameInput").value.trim();
    const role = document.getElementById("roleSelect").value;
    const statusBox = document.getElementById("statusMessage");

    if (!userInput) {
        statusBox.style.color = "#ff7b72";
        statusBox.textContent = "Por favor, introduce tu identificador.";
        return;
    }

    statusBox.style.color = "#8b949e";
    statusBox.textContent = "Verificando con el servidor...";

    if (window.javaBridge) {
        window.javaBridge.login(userInput, role);
    } else {
        statusBox.style.color = "#ff7b72";
        statusBox.textContent = "Java Bridge no detectado.";
    }
}

function onLoginSuccess(userId, role, token) {
    document.getElementById("heroPortal").classList.add("hidden");

    if (role === "PATIENT") {
        document.getElementById("patientDashboard").classList.remove("hidden");
        document.getElementById("patientGreeting").textContent = "Paciente: " + userId;
    } else {
        document.getElementById("doctorDashboard").classList.remove("hidden");
    }
}

function onLoginError(errorMsg) {
    const statusBox = document.getElementById("statusMessage");
    statusBox.style.color = "#ff7b72";
    statusBox.textContent = "Error: " + errorMsg;
}

function enviarReporte() {
    const tremor = parseInt(document.getElementById("tremorInput").value);
    const rigidity = parseInt(document.getElementById("rigidityInput").value);
    const meds = document.getElementById("medsCheck").checked;
    const freezing = document.getElementById("freezingCheck").checked;
    const statusBox = document.getElementById("reportStatus");

    statusBox.style.color = "#8b949e";
    statusBox.textContent = "Guardando reporte en el servidor...";

    if (window.javaBridge) {
        window.javaBridge.sendSymptomReport(tremor, rigidity, meds, freezing);
    }
}

function onReportResult(success) {
    const statusBox = document.getElementById("reportStatus");
    if (success) {
        statusBox.style.color = "#ccff00";
        statusBox.textContent = "✓ Reporte registrado en base de datos.";
    } else {
        statusBox.style.color = "#ff7b72";
        statusBox.textContent = "Error al persistir el reporte.";
    }
}

// CONTENIDO ÍNDICE
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

// Atajo: cerrar modal al pulsar la tecla Escape
document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
        closeModal();
    }
});