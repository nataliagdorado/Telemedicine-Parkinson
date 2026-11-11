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