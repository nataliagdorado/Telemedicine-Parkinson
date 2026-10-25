package patient;

import common.Config;
import common.Message;
import common.MessageCodec;
import common.ProtocolException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Manages the TCP socket connection between the patient application and the hospital server.
 */
public class ServerConnection {

    private final String host;
    private final int port;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    public ServerConnection() {
        this.host = Config.get("server.ip", "127.0.0.1");
        this.port = Config.getInt("server.port", 9000);
    }

    public void connect() throws IOException {
        this.socket = new Socket(host, port);
        this.writer = new PrintWriter(socket.getOutputStream(), true);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public Message send(Message message) throws IOException, ProtocolException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Cannot send message: socket is not connected.");
        }

        // Encode and send message
        String jsonPayload = MessageCodec.encode(message);
        writer.println(jsonPayload);

        // Await response from server
        String jsonResponse = reader.readLine();
        if (jsonResponse == null) {
            throw new IOException("Server closed the connection unexpectedly.");
        }

        return MessageCodec.decode(jsonResponse);
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }
}