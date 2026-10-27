package server;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Gestor de sesiones activas concurrentes (pacientes y doctores autenticados).
 * Asigna tokens de sesion y previene sesiones duplicadas para un mismo usuario.
 */
public class SessionManager {
    private static final Logger LOGGER = Logger.getLogger(SessionManager.class.getName());

    // Almacena: token -> SesionActiva
    private final Map<String, ActiveSession> sessionsByToken = new ConcurrentHashMap<>();

    // Almacena: userId -> token (para evitar logins simultaneos del mismo paciente)
    private final Map<String, String> tokensByUserId = new ConcurrentHashMap<>();

    public static class ActiveSession {
        private final String token;
        private final String userId;
        private final String role;
        private final LocalDateTime loginTime;
        private volatile LocalDateTime lastAccess;

        public ActiveSession(String token, String userId, String role) {
            this.token = token;
            this.userId = userId;
            this.role = role;
            this.loginTime = LocalDateTime.now();
            this.lastAccess = LocalDateTime.now();
        }

        public String getToken() { return token; }
        public String getUserId() { return userId; }
        public String getRole() { return role; }
        public LocalDateTime getLoginTime() { return loginTime; }
        public LocalDateTime getLastAccess() { return lastAccess; }

        public void touch() {
            this.lastAccess = LocalDateTime.now();
        }
    }


    /**
     * Inicia una sesion. Si el usuario ya tenia una sesion previa, se invalida la anterior.
     */
    public synchronized String createSession(String userId, String role) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be empty");
        }

        // Si ya tenia un token previo, invalidamos la sesion vieja
        String existingToken = tokensByUserId.get(userId);
        if (existingToken != null) {
            LOGGER.warning(() -> "User " + userId + " re-logged in. Invalidating previous session: " + existingToken);
            sessionsByToken.remove(existingToken);
        }

        // Generar un nuevo token seguro
        String token = "TOKEN-" + UUID.randomUUID().toString().substring(0, 8);
        ActiveSession session = new ActiveSession(token, userId, role);

        sessionsByToken.put(token, session);
        tokensByUserId.put(userId, token);

        LOGGER.info(() -> "Session created for user [" + userId + "] (Role: " + role + "). Active sessions: " + sessionsByToken.size());
        return token;
    }

    /**
     * Valida si un token es valido y actualiza el ultimo acceso.
     */
    public boolean isValidSession(String token) {
        if (token == null) return false;
        ActiveSession session = sessionsByToken.get(token);
        if (session != null) {
            session.touch();
            return true;
        }
        return false;
    }

    public ActiveSession getSession(String token) {
        if (token == null) return null;
        return sessionsByToken.get(token);
    }

    /**
     * Cierra la sesion (Logout).
     */
    public synchronized void invalidateSession(String token) {
        if (token != null) {
            ActiveSession removed = sessionsByToken.remove(token);
            if (removed != null) {
                tokensByUserId.remove(removed.getUserId());
                LOGGER.info(() -> "Session terminated for user: " + removed.getUserId());
            }
        }
    }

    public int getActiveSessionCount() {
        return sessionsByToken.size();
    }



}