-- Pacientes. Los IDs (P001...) los asigna SeedData o el servidor
CREATE TABLE IF NOT EXISTS patients (
    patient_id          TEXT PRIMARY KEY,
    name                TEXT NOT NULL,
    birth_date          TEXT,
    diagnosis_year      INTEGER,
    medication_schedule TEXT,
    password_hash       TEXT,
    password_salt       TEXT);

-- Metadatos de cada sesión. La señal sigue en CSV, aquí solo va la ruta
CREATE TABLE IF NOT EXISTS sessions (
    session_id       TEXT PRIMARY KEY,
    patient_id       TEXT NOT NULL REFERENCES patients(patient_id),
    start_time       TEXT NOT NULL,
    end_time         TEXT,
    sampling_rate_hz INTEGER NOT NULL CHECK (sampling_rate_hz > 0),
    channels         TEXT NOT NULL,
    signal_file      TEXT,
    status           TEXT NOT NULL CHECK (status IN ('RECORDING', 'COMPLETED', 'INCOMPLETE'))
    );

-- Reportes de síntomas. session_id es opcional (C4): NULL si se hizo fuera de una sesión
CREATE TABLE IF NOT EXISTS symptom_reports (
    report_id         TEXT PRIMARY KEY,
    patient_id        TEXT NOT NULL REFERENCES patients(patient_id),
    session_id        TEXT REFERENCES sessions(session_id),
    time_stamp        TEXT NOT NULL,
    rigidity_level    INTEGER NOT NULL CHECK (rigidity_level BETWEEN 1 AND 5),
    tremor_level      INTEGER NOT NULL CHECK (tremor_level BETWEEN 1 AND 5),
    medication_taken  INTEGER NOT NULL CHECK (medication_taken IN (0, 1)),
    last_dose_time    TEXT,
    freezing_episodes INTEGER NOT NULL CHECK (freezing_episodes IN (0, 1)),
    notes             TEXT CHECK (notes IS NULL OR length(notes) <= 500));

-- Notas clínicas del médico sobre un paciente
CREATE TABLE IF NOT EXISTS clinical_notes (
    note_id     TEXT PRIMARY KEY,
    patient_id  TEXT NOT NULL REFERENCES patients(patient_id),
    doctor_id   TEXT NOT NULL,
    timestamp   TEXT NOT NULL,
    observation TEXT NOT NULL);

-- Índices para las búsquedas por paciente (findSessions, findSymptomReports, findClinicalNotes)
CREATE INDEX IF NOT EXISTS idx_sessions_patient ON sessions(patient_id);
CREATE INDEX IF NOT EXISTS idx_reports_patient  ON symptom_reports(patient_id);
CREATE INDEX IF NOT EXISTS idx_notes_patient    ON clinical_notes(patient_id);