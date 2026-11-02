package server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modulo de autenticacion y seguridad criptografica.
 * Proporciona hashing unidireccional SHA-256 con salt para credenciales de usuarios.
 */
public class AdminAuth {
    private static final Logger LOGGER = Logger.getLogger(AdminAuth.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Genera un salt criptografico aleatorio codificado en Base64.
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Calcula el hash SHA-256 combinando la contrasena en texto plano y el salt.
     */
    public static String hashPassword(String password, String salt) {
        if (password == null || salt == null) {
            throw new IllegalArgumentException("Password and salt cannot be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(salt));
            byte[] hashedBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, "SHA-256 algorithm not available", e);
            throw new RuntimeException("Cryptographic error", e);
        }
    }

    /**
     * Valida si una contrasena proporcionada coincide con el hash almacenado.
     */
    public static boolean verifyPassword(String enteredPassword, String storedHash, String storedSalt) {
        if (enteredPassword == null || storedHash == null || storedSalt == null) {
            return false;
        }
        String calculatedHash = hashPassword(enteredPassword, storedSalt);
        return MessageDigest.isEqual(
                calculatedHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}