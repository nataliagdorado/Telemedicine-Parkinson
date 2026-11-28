package server;

import common.SessionInfo;
import server.data.Patient;
import server.data.Repository;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class ServerAdminFrame extends JFrame {

    private final int port;
    private final ClientRegistry clientRegistry;
    private final Repository repository;
    private final ShutdownCoordinator shutdownCoordinator;
    private final String adminHash;
    private final String adminSalt;

    private final DefaultTableModel connectedModel;
    private final DefaultTableModel sessionsModel;
    private final JLabel statusLabel;

    public ServerAdminFrame(int port,
                            ClientRegistry clientRegistry,
                            Repository repository,
                            ShutdownCoordinator shutdownCoordinator,
                            String adminHash,
                            String adminSalt) {
        this.port = port;
        this.clientRegistry = clientRegistry;
        this.repository = repository;
        this.shutdownCoordinator = shutdownCoordinator;
        this.adminHash = adminHash;
        this.adminSalt = adminSalt;

        setTitle("TremorLink · Hospital Server Admin Console (Port " + port + ")");
        setSize(880, 540);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        // Barra superior
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(24, 24, 27));
        topBar.setBorder(new EmptyBorder(10, 14, 10, 14));

        statusLabel = new JLabel("● SERVER ONLINE · Listening on TCP :" + port);
        statusLabel.setForeground(new Color(74, 222, 128));
        statusLabel.setFont(new Font("Monospaced", Font.BOLD, 13));

        JButton shutdownBtn = new JButton("Safe Server Shutdown");
        shutdownBtn.setBackground(new Color(220, 38, 38));
        shutdownBtn.setForeground(Color.WHITE);
        shutdownBtn.setFocusPainted(false);
        shutdownBtn.addActionListener(e -> handleSafeShutdown());

        topBar.add(statusLabel, BorderLayout.WEST);
        topBar.add(shutdownBtn, BorderLayout.EAST);

        // Tabla 1: Sockets conectados actualmente
        connectedModel = new DefaultTableModel(new String[]{"#", "Remote Socket Address", "Connection State"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable connectedTable = new JTable(connectedModel);
        JPanel connectedPanel = new JPanel(new BorderLayout());
        connectedPanel.setBorder(BorderFactory.createTitledBorder("Active Client Sockets (Live via ClientRegistry)"));
        connectedPanel.add(new JScrollPane(connectedTable), BorderLayout.CENTER);

        // Tabla 2: Historial de sesiones de monitorización
        sessionsModel = new DefaultTableModel(new String[]{"Session ID", "Patient ID", "Sampling Rate", "Start Time", "End Time", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable sessionsTable = new JTable(sessionsModel);
        JPanel sessionsPanel = new JPanel(new BorderLayout());
        sessionsPanel.setBorder(BorderFactory.createTitledBorder("Recorded Monitoring Sessions (Repository)"));
        sessionsPanel.add(new JScrollPane(sessionsTable), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, connectedPanel, sessionsPanel);
        splitPane.setDividerLocation(190);
        splitPane.setBorder(new EmptyBorder(10, 10, 10, 10));

        add(topBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        // Refresco automático cada 2 segundos
        Timer refreshTimer = new Timer(2000, e -> refreshData());
        refreshTimer.start();
        refreshData();
    }

    private void refreshData() {
        // Clientes conectados en tiempo real
        connectedModel.setRowCount(0);
        if (clientRegistry != null) {
            int count = clientRegistry.getActiveClientCount();
            statusLabel.setText("● SERVER ONLINE (Port " + port + ") · Active Sockets: " + count);

            int idx = 1;
            for (ClientHandler client : clientRegistry.getConnectedClients()) {
                connectedModel.addRow(new Object[]{
                        idx++,
                        client.getRemoteAddress(),
                        "CONNECTED"
                });
            }
        }

        // Sesiones registradas en el Repository
        sessionsModel.setRowCount(0);
        if (repository != null) {
            try {
                List<Patient> patients = repository.findAllPatients();
                for (Patient p : patients) {
                    List<SessionInfo> sessions = repository.findSessions(p.getPatientId());
                    for (SessionInfo s : sessions) {
                        sessionsModel.addRow(new Object[]{
                                s.getSessionId(),
                                s.getPatientId(),
                                s.getSamplingRateHz() + " Hz",
                                s.getStartTime(),
                                s.getEndTime() != null ? s.getEndTime() : "In progress...",
                                s.getStatus()
                        });
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void handleSafeShutdown() {
        int active = clientRegistry != null ? clientRegistry.getActiveClientCount() : 0;
        JPasswordField pwdField = new JPasswordField();
        Object[] message = {
                "Active connected clients: " + active,
                "Enter Admin Password to stop server cleanly:",
                pwdField
        };

        int option = JOptionPane.showConfirmDialog(
                this,
                message,
                "Safe Server Shutdown",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (option == JOptionPane.OK_OPTION) {
            String entered = new String(pwdField.getPassword());
            if (AdminAuth.verifyPassword(entered, adminHash, adminSalt)) {
                int currentActive = clientRegistry != null ? clientRegistry.getActiveClientCount() : 0;
                if (currentActive > 0) {
                    int confirm = JOptionPane.showConfirmDialog(
                            this,
                            "Hay " + currentActive + " cliente(s) conectado(s). ¿Apagar el servidor igualmente?",
                            "Confirmar desconexión de clientes",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );
                    if (confirm != JOptionPane.YES_OPTION) {
                        return;
                    }
                }

                dispose();
                if (shutdownCoordinator != null) {
                    shutdownCoordinator.stopServer();
                }
                System.exit(0);
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "Invalid administrator password.",
                        "Authentication Failed",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }
}