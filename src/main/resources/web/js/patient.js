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
            <p>TremorLink provides continuous tremor characterization and motor fluctuation tracking.</p>
            <ul>
                <li><strong>Biosensing Integration:</strong> Native acquisition support for BITalino (r)evolution multichannel biosignals via Bluetooth Low Energy (BLE).</li>
                <li><strong>Acquisition Channels:</strong> Electromyography  and acceleration at different sampling rates (10, 100, 1000 Hz).</li>
                <li><strong>Communication Protocol:</strong> TCP stream over port 9000 with atomic JSON packet routing.</li>
                <li><strong>Security & Storage:</strong> Zero-knowledge SHA-256 password salting, tokenized session validation.</li>
            </ul>
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
        `
    },
    support: {
        category: "TECHNICAL SUPPORT",
        title: "Contact Technical Service",
        meta: "Engineering Support Team",
        html: `
    <p>If you experience any technical issues, connection drops, or sensor problems, please contact our technical support team directly:</p>
    <ul>
        <li><strong>Sarah</strong> (Hardware & BITalino Acquisition): <code>sarah.support@tremorlink.org</code></li>
        <li><strong>Diego</strong> (Server & Network Infrastructure): <code>diego.support@tremorlink.org</code></li>
        <li><strong>Natalia</strong> (Client Interface & Clinical Portal): <code>natalia.support@tremorlink.org</code></li>
    </ul>
    <div class="info-highlight-box">
        Please include your <strong>Patient or Specialist ID</strong> and the approximate time of the issue so our team can review the session logs.
    </div>
`
    }
};

function openModal(key) {
    const data = MODAL_DATA[key];
    const modal = document.getElementById("infoModal");
    if (!data || !modal) return;

    document.getElementById("modalCategory").textContent = data.category;
    document.getElementById("modalTitle").textContent = data.title;
    document.getElementById("modalMeta").textContent = data.meta;
    document.getElementById("modalBody").innerHTML = data.html;

    modal.classList.remove("hidden");
}

function closeModal() {
    const modal = document.getElementById("infoModal");
    if (modal) modal.classList.add("hidden");
}

document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
        closeModal();
    }
});

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
