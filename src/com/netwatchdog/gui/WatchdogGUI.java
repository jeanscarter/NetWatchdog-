package com.netwatchdog.gui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * Configurator GUI (Java Swing) - Se ejecuta en la sesión del usuario.
 * <ul>
 * <li>Autodescubrimiento de adaptadores de red via PowerShell</li>
 * <li>Lee/Escribe config.properties</li>
 * <li>Monitor de log en tiempo real</li>
 * </ul>
 */
public class WatchdogGUI extends JFrame {

    private static final Path BASE = Paths.get("C:\\ProgramData\\NetWatchdog");
    private static final Path CONFIG = BASE.resolve("config.properties");
    private static final Path LOGFILE = BASE.resolve("watchdog.log");

    // ─── Colores del tema ───────────────────────────────────────────────────────
    private static final Color BG_DARK = new Color(24, 24, 32);
    private static final Color BG_PANEL = new Color(32, 34, 48);
    private static final Color BG_INPUT = new Color(42, 44, 60);
    private static final Color ACCENT = new Color(80, 140, 255);
    private static final Color SUCCESS = new Color(80, 200, 120);
    private static final Color TXT_MAIN = new Color(225, 228, 240);
    private static final Color TXT_DIM = new Color(140, 145, 170);
    private static final Color BORDER_CLR = new Color(55, 58, 78);

    // ─── Componentes ────────────────────────────────────────────────────────────
    private JTextField domainField;
    private JComboBox<String> adapterCombo;
    private JSpinner intervalSpinner;
    private JCheckBox recoveryCheck;
    private JTextArea logArea;
    private JLabel statusLabel;
    private JLabel serviceStatusLabel;
    private JButton btnStartSvc;
    private JButton btnStopSvc;
    private JButton btnInstallSvc;
    private JButton btnUninstallSvc;
    private JLabel lblMemGUI;
    private JLabel lblMemSvc;
    private javax.swing.Timer logTimer;

    // ═════════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new WatchdogGUI().setVisible(true));
    }

    public WatchdogGUI() {
        super("NetWatchdog — Configurador");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(720, 680);
        setMinimumSize(new Dimension(600, 550));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        initComponents();
        loadConfig();
        startLogMonitor();
    }

    // ─── Layout ─────────────────────────────────────────────────────────────────
    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        JLabel title = new JLabel("NetWatchdog Configurador");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(TXT_MAIN);

        statusLabel = new JLabel("Estado: desconocido");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(TXT_DIM);

        p.add(title, BorderLayout.WEST);
        p.add(statusLabel, BorderLayout.EAST);
        return p;
    }

    private JComponent buildCenter() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabbedPane.setBackground(BG_PANEL);
        tabbedPane.setForeground(new Color(30, 30, 30)); // Texto oscuro para contrastar con las pestañas claras
        
        // Pestaña 1: Configuración & Logs
        JPanel pConfig = new JPanel(new BorderLayout(0, 12));
        pConfig.setOpaque(false);
        pConfig.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        
        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(buildServiceControlPanel(), BorderLayout.NORTH);
        top.add(buildConfigPanel(), BorderLayout.CENTER);
        
        pConfig.add(top, BorderLayout.NORTH);
        pConfig.add(buildLogPanel(), BorderLayout.CENTER);
        
        tabbedPane.addTab("Configuración & Logs", pConfig);
        
        // Pestaña 2: Rendimiento
        tabbedPane.addTab("Rendimiento", buildPerformancePanel());
        
        return tabbedPane;
    }

    // ─── Panel de Rendimiento ───────────────────────────────────────────────────
    private JPanel buildPerformancePanel() {
        JPanel card = createCard("MÉTRICAS DE RECURSOS");
        card.setLayout(new GridLayout(3, 1, 10, 10));
        
        lblMemGUI = makeLabel("Calculando...");
        lblMemGUI.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblMemGUI.setForeground(ACCENT);
        
        lblMemSvc = makeLabel("Calculando...");
        lblMemSvc.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblMemSvc.setForeground(ACCENT);
        
        JPanel pGUI = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pGUI.setOpaque(false);
        pGUI.add(makeLabel("Consumo de RAM de esta interfaz (GUI):"));
        pGUI.add(lblMemGUI);
        
        JPanel pSvc = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pSvc.setOpaque(false);
        pSvc.add(makeLabel("Consumo de RAM del Servicio en 2do Plano:"));
        pSvc.add(lblMemSvc);

        JPanel pTips = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pTips.setOpaque(false);
        JLabel tipLabel = makeLabel("<html><b>¿Cómo disminuir el consumo?</b><br>Edita el archivo <i>deploy/watchdog-service.xml</i> y añade los parámetros de memoria a los argumentos:<br><code>&lt;arguments&gt;-Xmx64m -Xms16m -jar \"%BASE%\\watchdog-service.jar\"&lt;/arguments&gt;</code><br>Luego, reinstala el servicio para aplicar los cambios.</html>");
        tipLabel.setForeground(TXT_DIM);
        pTips.add(tipLabel);
        
        card.add(pGUI);
        card.add(pSvc);
        card.add(pTips);
        
        // Actualizar métricas
        javax.swing.Timer t = new javax.swing.Timer(2000, e -> updatePerformanceMetrics());
        t.start();
        updatePerformanceMetrics();
        
        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        container.add(card, BorderLayout.NORTH);
        
        return container;
    }

    private void updatePerformanceMetrics() {
        // GUI Memoria
        long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        lblMemGUI.setText(String.format("%.2f MB", usedMem / (1024.0 * 1024.0)));
        
        // Servicio Memoria
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                try {
                    String cmd = "(Get-WmiObject Win32_Process | Where-Object { $_.Name -match 'watchdog-service' -or $_.CommandLine -match 'watchdog-service.jar' } | Measure-Object -Property WorkingSetSize -Sum).Sum";
                    Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", cmd).start();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line = br.readLine();
                        if (line != null && !line.trim().isEmpty()) {
                            long bytes = Long.parseLong(line.trim());
                            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
                        }
                    }
                } catch (Exception e) {}
                return "0.00 MB (Detenido / No encontrado)";
            }
            @Override
            protected void done() {
                try {
                    lblMemSvc.setText(get());
                } catch(Exception e){}
            }
        };
        worker.execute();
    }

    // ─── Panel de Control de Servicio ───────────────────────────────────────────
    private JPanel buildServiceControlPanel() {
        JPanel card = createCard("ESTADO DEL SERVICIO CORE");
        card.setLayout(new FlowLayout(FlowLayout.LEFT, 16, 8));
        
        serviceStatusLabel = makeLabel("Comprobando...");
        serviceStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        
        btnStartSvc = makeSmallButton("▶ Iniciar");
        btnStartSvc.addActionListener(e -> controlService("Start"));
        
        btnStopSvc = makeSmallButton("■ Detener");
        btnStopSvc.addActionListener(e -> controlService("Stop"));

        btnInstallSvc = makeSmallButton("⚙ Instalar Servicio");
        btnInstallSvc.addActionListener(e -> installService());

        btnUninstallSvc = makeSmallButton("🗑 Desinstalar");
        btnUninstallSvc.addActionListener(e -> uninstallService());
        
        card.add(makeLabel("NetWatchdogService:"));
        card.add(serviceStatusLabel);
        card.add(btnStartSvc);
        card.add(btnStopSvc);
        card.add(btnInstallSvc);
        card.add(btnUninstallSvc);
        
        // Timer to check status periodically
        javax.swing.Timer t = new javax.swing.Timer(3000, e -> checkServiceStatus());
        t.start();
        checkServiceStatus();
        
        return card;
    }

    private void checkServiceStatus() {
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                try {
                    Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "(Get-Service 'NetWatchdogService' -ErrorAction SilentlyContinue).Status").start();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String status = br.readLine();
                        if (status != null && !status.trim().isEmpty()) return status.trim();
                    }
                } catch (Exception e) {}
                return "NotInstalled";
            }
            @Override
            protected void done() {
                try {
                    String st = get();
                    if ("Running".equalsIgnoreCase(st)) {
                        serviceStatusLabel.setText("EN EJECUCIÓN");
                        serviceStatusLabel.setForeground(SUCCESS);
                        btnStartSvc.setVisible(false);
                        btnStopSvc.setVisible(true);
                        btnInstallSvc.setVisible(false);
                        btnUninstallSvc.setVisible(true);
                    } else if ("Stopped".equalsIgnoreCase(st)) {
                        serviceStatusLabel.setText("DETENIDO");
                        serviceStatusLabel.setForeground(Color.ORANGE);
                        btnStartSvc.setVisible(true);
                        btnStopSvc.setVisible(false);
                        btnInstallSvc.setVisible(false);
                        btnUninstallSvc.setVisible(true);
                    } else {
                        serviceStatusLabel.setText("NO INSTALADO / DESCONOCIDO");
                        serviceStatusLabel.setForeground(Color.RED);
                        btnStartSvc.setVisible(false);
                        btnStopSvc.setVisible(false);
                        btnInstallSvc.setVisible(true);
                        btnUninstallSvc.setVisible(false);
                    }
                } catch (Exception e) {}
            }
        };
        worker.execute();
    }

    /**
     * Resuelve la ruta de install-service.bat buscando en múltiples ubicaciones:
     * 1. Relativo a la ubicación del JAR en ejecución (mismo directorio)
     * 2. user.dir/deploy/
     * 3. C:\ProgramData\NetWatchdog\
     */
    private Path resolveInstallBat() {
        // 1. Relativo al JAR en ejecución (más confiable)
        try {
            Path jarDir = Paths.get(
                WatchdogGUI.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParent();
            // Si el JAR está en deploy/, el .bat está al lado
            Path candidate = jarDir.resolve("install-service.bat");
            if (Files.exists(candidate)) return candidate;
            // Si el JAR está en la raíz del proyecto, buscar en deploy/
            candidate = jarDir.resolve("deploy").resolve("install-service.bat");
            if (Files.exists(candidate)) return candidate;
        } catch (Exception ignored) {}

        // 2. Relativo al directorio de trabajo actual
        Path fromCwd = Paths.get(System.getProperty("user.dir"), "deploy", "install-service.bat");
        if (Files.exists(fromCwd)) return fromCwd;

        // 3. Ubicación de instalación conocida
        Path fromProgramData = Paths.get("C:\\ProgramData\\NetWatchdog\\install-service.bat");
        if (Files.exists(fromProgramData)) return fromProgramData;

        return null; // No encontrado
    }

    private void installService() {
        try {
            Path batPath = resolveInstallBat();
            if (batPath == null) {
                LightweightToast.show(this, "No se encontró install-service.bat — verifique la carpeta deploy/", Color.RED);
                return;
            }

            btnInstallSvc.setEnabled(false);
            btnInstallSvc.setText("Instalando...");

            // Crear un script PowerShell temporal que:
            // 1. Ejecuta el .bat como administrador con -Wait
            // 2. Captura la salida a un archivo temporal para diagnóstico
            Path logFile = Files.createTempFile("netwatchdog_install_", ".log");
            Path ps1File = Files.createTempFile("netwatchdog_install_", ".ps1");


            String batDir = batPath.toAbsolutePath().getParent().toString();

            // Workaround para unidades de red mapeadas (ej. Z:\):
            // La sesión de Administrador elevada no ve las unidades mapeadas del usuario.
            // Copiamos los archivos a un directorio local temporal en C:\
            Path tempDeployDir = Files.createTempDirectory("netwatchdog_deploy_");
            String[] filesToCopy = {"install-service.bat", "watchdog-service.jar", "watchdog-service.exe", "watchdog-service.xml"};
            for (String f : filesToCopy) {
                Path source = Paths.get(batDir, f);
                if (Files.exists(source)) {
                    Files.copy(source, tempDeployDir.resolve(f), StandardCopyOption.REPLACE_EXISTING);
                }
            }

            String localBatAbsolute = tempDeployDir.resolve("install-service.bat").toAbsolutePath().toString();
            String localBatDir = tempDeployDir.toAbsolutePath().toString();

            // Script PS1 que se ejecutará ELEVADO:
            // Redirige la salida del .bat a un archivo de log para diagnóstico
            String ps1Content =
                "Set-Location '" + localBatDir + "'\n" +
                "try {\n" +
                "    & '" + localBatAbsolute + "' /silent *> '" + logFile.toAbsolutePath() + "' 2>&1\n" +
                "    $exitCode = $LASTEXITCODE\n" +
                "    Add-Content -Path '" + logFile.toAbsolutePath() + "' -Value \"`nEXIT_CODE=$exitCode\"\n" +
                "} catch {\n" +
                "    Add-Content -Path '" + logFile.toAbsolutePath() + "' -Value \"ERROR: $_\"\n" +
                "}\n";

            Files.writeString(ps1File, ps1Content, StandardCharsets.UTF_8);

            // Lanzar el script .ps1 elevado con -Wait para que no retorne hasta completar
            String launchCmd = "Start-Process powershell -Verb runAs -Wait -WindowStyle Hidden " +
                "-ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', '\"" +
                ps1File.toAbsolutePath() + "\"'";

            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    try {
                        Process proc = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", launchCmd).start();
                        // Esperar a que el proceso elevado termine (el -Wait en Start-Process hace que
                        // el powershell padre no retorne hasta que el hijo termine)
                        proc.waitFor();

                        // Pequeña pausa para asegurar que el archivo de log se haya escrito
                        Thread.sleep(1500);

                        // Leer el log de salida
                        if (Files.exists(logFile)) {
                            byte[] bytes = Files.readAllBytes(logFile);
                            String output;
                            if (bytes.length >= 2 && bytes[0] == (byte)0xFF && bytes[1] == (byte)0xFE) {
                                output = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
                            } else {
                                output = new String(bytes, java.nio.charset.Charset.defaultCharset());
                            }
                            // Limpiar archivos temporales
                            Files.deleteIfExists(logFile);
                            Files.deleteIfExists(ps1File);
                            return output;
                        }
                        Files.deleteIfExists(ps1File);
                        return "NO_LOG";
                    } catch (Exception e) {
                        return "EXCEPTION: " + e.getMessage();
                    }
                }

                @Override
                protected void done() {
                    btnInstallSvc.setEnabled(true);
                    btnInstallSvc.setText("⚙ Instalar Servicio");
                    try {
                        String result = get();
                        if (result.equals("NO_LOG")) {
                            // El usuario probablemente canceló el UAC
                            LightweightToast.show(WatchdogGUI.this,
                                "Instalación cancelada o sin respuesta del UAC", Color.ORANGE);
                        } else if (result.startsWith("EXCEPTION:")) {
                            LightweightToast.show(WatchdogGUI.this,
                                "Error: " + result, Color.RED);
                        } else if (result.contains("EXIT_CODE=0") || result.contains("INSTALACIÓN COMPLETADA")) {
                            LightweightToast.show(WatchdogGUI.this,
                                "Servicio instalado correctamente ✓", SUCCESS);
                        } else {
                            // Mostrar las últimas líneas del log para diagnóstico
                            String[] lines = result.split("\n");
                            StringBuilder summary = new StringBuilder();
                            int start = Math.max(0, lines.length - 5);
                            for (int i = start; i < lines.length; i++) {
                                String line = lines[i].trim();
                                if (!line.isEmpty()) {
                                    summary.append(line).append(" | ");
                                }
                            }
                            LightweightToast.show(WatchdogGUI.this,
                                "Posible error: " + summary, Color.ORANGE);
                            // También escribir al log area para diagnóstico completo
                            logArea.append("\n--- INSTALL LOG ---\n" + result + "\n--- END ---\n");
                        }
                    } catch (Exception ex) {
                        LightweightToast.show(WatchdogGUI.this,
                            "Error verificando instalación: " + ex.getMessage(), Color.RED);
                    }
                    checkServiceStatus();
                }
            };
            worker.execute();
        } catch (Exception ex) {
            btnInstallSvc.setEnabled(true);
            btnInstallSvc.setText("⚙ Instalar Servicio");
            LightweightToast.show(this, "Error instalando: " + ex.getMessage(), Color.RED);
        }
    }

    private void controlService(String action) {
        try {
            String script = "Start-Process powershell -Verb runAs -WindowStyle Hidden -ArgumentList '-NoProfile', '-Command', '" + action + "-Service', 'NetWatchdogService'";
            new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", script).start();
            // wait a bit and refresh
            javax.swing.Timer t = new javax.swing.Timer(2500, e -> checkServiceStatus());
            t.setRepeats(false);
            t.start();
        } catch (Exception ex) {
            LightweightToast.show(this, "Error: " + ex.getMessage(), Color.RED);
        }
    }

    private void uninstallService() {
        int confirm = JOptionPane.showConfirmDialog(this, 
            "¿Está seguro de que desea desinstalar COMPLETAMENTE el sistema?\n\n" +
            "Esto hará lo siguiente:\n" +
            "1. Detener el servicio y matar procesos residuales\n" +
            "2. Eliminar el servicio de Windows\n" +
            "3. BORRAR permanentemente la carpeta C:\\ProgramData\\NetWatchdog", 
            "Desinstalación Completa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            // Creamos un script temporal en PowerShell para una desinstalación más robusta
            Path tempScript = Files.createTempFile("netwatchdog_uninstall_", ".ps1");
            String baseDir = "C:\\ProgramData\\NetWatchdog";
            
            String ps1Content = 
                "Stop-Service NetWatchdogService -ErrorAction SilentlyContinue\n" +
                "if (Test-Path '" + baseDir + "\\watchdog-service.exe') {\n" +
                "    & '" + baseDir + "\\watchdog-service.exe' uninstall\n" +
                "}\n" +
                "sc.exe delete NetWatchdogService\n" +
                "Get-WmiObject Win32_Process | Where-Object { $_.CommandLine -match 'watchdog-service.jar' } | ForEach-Object { $_.Terminate() }\n" +
                "Start-Sleep -Seconds 2\n" +
                "Remove-Item -Path '" + baseDir + "' -Recurse -Force -ErrorAction SilentlyContinue\n" +
                "Remove-Item -Path $PSCommandPath -Force -ErrorAction SilentlyContinue\n";
            
            Files.writeString(tempScript, ps1Content, StandardCharsets.UTF_8);

            String scriptCmd = "Start-Process powershell -Verb runAs -WindowStyle Hidden -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', '\"" + tempScript.toAbsolutePath() + "\"'";
            new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", scriptCmd).start();
            
            javax.swing.Timer t = new javax.swing.Timer(4000, e -> {
                checkServiceStatus();
                LightweightToast.show(this, "Sistema desinstalado y archivos eliminados", Color.ORANGE);
            });
            t.setRepeats(false);
            t.start();
            
        } catch (Exception ex) {
            LightweightToast.show(this, "Error desinstalando: " + ex.getMessage(), Color.RED);
        }
    }

    // ─── Panel de Configuración ─────────────────────────────────────────────────
    private JPanel buildConfigPanel() {
        JPanel card = createCard("CONFIGURACIÓN DEL SERVICIO");
        card.setLayout(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0 — Dominio
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 0;
        card.add(makeLabel("Dominio Objetivo:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        domainField = makeTextField("activa.local");
        card.add(domainField, gc);

        // Row 1 — Adaptador
        gc.gridx = 0;
        gc.gridy = 1;
        gc.weightx = 0;
        card.add(makeLabel("Adaptador de Red:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        adapterCombo = new JComboBox<>();
        styleCombo(adapterCombo);
        card.add(adapterCombo, gc);
        gc.gridx = 2;
        gc.weightx = 0;
        JButton refreshBtn = makeSmallButton("Actualizar");
        refreshBtn.setToolTipText("Redescubrir adaptadores");
        refreshBtn.addActionListener(e -> discoverAdapters());
        card.add(refreshBtn, gc);

        // Row 2 — Intervalo
        gc.gridx = 0;
        gc.gridy = 2;
        gc.weightx = 0;
        card.add(makeLabel("Intervalo (seg):"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        intervalSpinner = new JSpinner(new SpinnerNumberModel(30, 5, 3600, 5));
        styleSpinner(intervalSpinner);
        card.add(intervalSpinner, gc);

        // Row 3 - RecoveryEngine
        gc.gridx = 0; gc.gridy = 3; gc.weightx = 0;
        card.add(makeLabel("RecoveryEngine:"), gc);
        gc.gridx = 1; gc.weightx = 1; gc.gridwidth = 2;
        recoveryCheck = new JCheckBox("Activo (Permitir aplicar cambios automáticos al sistema)");
        recoveryCheck.setOpaque(false);
        recoveryCheck.setForeground(TXT_MAIN);
        recoveryCheck.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        card.add(recoveryCheck, gc);

        // Row 4 — Botones
        gc.gridx = 0;
        gc.gridy = 4;
        gc.gridwidth = 3;
        gc.fill = GridBagConstraints.NONE;
        gc.anchor = GridBagConstraints.CENTER;
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btns.setOpaque(false);
        JButton saveBtn = makeButton("✔  Guardar Configuración", ACCENT);
        saveBtn.addActionListener(e -> saveConfig());
        JButton reloadBtn = makeButton("↻  Recargar", new Color(100, 100, 130));
        reloadBtn.addActionListener(e -> loadConfig());
        btns.add(saveBtn);
        btns.add(reloadBtn);
        card.add(btns, gc);

        // Descubrir adaptadores al iniciar
        discoverAdapters();
        return card;
    }

    // ─── Panel de Log ───────────────────────────────────────────────────────────
    private JPanel buildLogPanel() {
        JPanel card = createCard("MONITOR DE LOG EN TIEMPO REAL");
        card.setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(18, 18, 24));
        logArea.setForeground(new Color(180, 220, 180));
        logArea.setCaretColor(ACCENT);
        logArea.setMargin(new Insets(8, 8, 8, 8));

        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_CLR));
        sp.getViewport().setBackground(new Color(18, 18, 24));
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFooter() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JLabel foot = new JLabel("NetWatchdog v1.0 — Solo edita la configuración. El Servicio ejecuta las acciones.");
        foot.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        foot.setForeground(TXT_DIM);
        p.add(foot);
        return p;
    }

    // ─── Autodescubrimiento de Adaptadores ──────────────────────────────────────
    private void discoverAdapters() {
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                List<String> adapters = new ArrayList<>();
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                            "powershell.exe", "-NoProfile", "-Command",
                            "(Get-NetAdapter).Name");
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            line = line.trim();
                            if (!line.isEmpty())
                                adapters.add(line);
                        }
                    }
                    p.waitFor();
                } catch (Exception e) {
                    adapters.add("Ethernet");
                    adapters.add("Wi-Fi");
                }
                if (adapters.isEmpty()) {
                    adapters.add("Ethernet");
                }
                return adapters;
            }

            @Override
            protected void done() {
                try {
                    String current = (String) adapterCombo.getSelectedItem();
                    List<String> list = get();
                    adapterCombo.removeAllItems();
                    for (String a : list)
                        adapterCombo.addItem(a);
                    if (current != null && list.contains(current)) {
                        adapterCombo.setSelectedItem(current);
                    }
                    statusLabel.setText("Adaptadores: " + list.size() + " encontrados");
                    statusLabel.setForeground(SUCCESS);
                } catch (Exception e) {
                    statusLabel.setText("Error descubriendo adaptadores");
                    statusLabel.setForeground(Color.RED);
                }
            }
        };
        worker.execute();
    }

    // ─── Config I/O ─────────────────────────────────────────────────────────────
    private void loadConfig() {
        if (!Files.exists(CONFIG)) {
            statusLabel.setText("Sin config — usando defaults");
            statusLabel.setForeground(TXT_DIM);
            return;
        }
        try (InputStream is = Files.newInputStream(CONFIG)) {
            Properties p = new Properties();
            p.load(is);
            domainField.setText(p.getProperty("TargetDomain", "activa.local"));
            String adapter = p.getProperty("AdapterName", "Ethernet");
            adapterCombo.setSelectedItem(adapter);
            if (adapterCombo.getSelectedItem() == null || !adapterCombo.getSelectedItem().equals(adapter)) {
                adapterCombo.addItem(adapter);
                adapterCombo.setSelectedItem(adapter);
            }
            intervalSpinner.setValue(
                    Integer.parseInt(p.getProperty("CheckIntervalSeconds", "30")));
            recoveryCheck.setSelected(Boolean.parseBoolean(p.getProperty("RecoveryEnabled", "true")));
            statusLabel.setText("Configuración cargada ✓");
            statusLabel.setForeground(SUCCESS);
        } catch (Exception e) {
            statusLabel.setText("Error cargando config: " + e.getMessage());
            statusLabel.setForeground(Color.RED);
        }
    }

    private void saveConfig() {
        try {
            Files.createDirectories(BASE);
            Properties p = new Properties();
            p.setProperty("TargetDomain", domainField.getText().trim());
            p.setProperty("AdapterName", (String) adapterCombo.getSelectedItem());
            p.setProperty("CheckIntervalSeconds", intervalSpinner.getValue().toString());
            p.setProperty("RecoveryEnabled", String.valueOf(recoveryCheck.isSelected()));
            try (OutputStream os = Files.newOutputStream(CONFIG)) {
                p.store(os, "NetWatchdog - Editado desde GUI");
            }
            statusLabel.setText("Configuración guardada ✓");
            statusLabel.setForeground(SUCCESS);
            LightweightToast.show(this, "Configuración guardada correctamente", SUCCESS);
        } catch (Exception e) {
            LightweightToast.show(this, "Error guardando: " + e.getMessage(), new Color(220, 80, 80));
        }
    }

    // ─── Log Monitor ────────────────────────────────────────────────────────────
    private void startLogMonitor() {
        logTimer = new javax.swing.Timer(3000, e -> refreshLog());
        logTimer.setInitialDelay(500);
        logTimer.start();
    }

    private void refreshLog() {
        if (!Files.exists(LOGFILE)) {
            logArea.setText("[Esperando archivo de log...]\n");
            return;
        }
        try {
            java.util.List<String> all = Files.readAllLines(LOGFILE, StandardCharsets.UTF_8);
            int start = Math.max(0, all.size() - 100);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < all.size(); i++) {
                sb.append(all.get(i)).append('\n');
            }
            logArea.setText(sb.toString());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        } catch (Exception ignored) {
        }
    }

    // ─── Factory de componentes estilizados ─────────────────────────────────────
    private JPanel createCard(String title) {
        JPanel card = new JPanel();
        card.setBackground(BG_PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_CLR),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(), title);
        tb.setTitleFont(new Font("Segoe UI", Font.BOLD, 11));
        tb.setTitleColor(ACCENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_CLR),
                BorderFactory.createCompoundBorder(
                        tb, BorderFactory.createEmptyBorder(8, 10, 8, 10))));
        return card;
    }

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        l.setForeground(TXT_MAIN);
        return l;
    }

    private JTextField makeTextField(String defaultVal) {
        JTextField tf = new JTextField(defaultVal, 20);
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tf.setBackground(BG_INPUT);
        tf.setForeground(TXT_MAIN);
        tf.setCaretColor(TXT_MAIN);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_CLR),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        return tf;
    }

    private void styleCombo(JComboBox<String> cb) {
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cb.setBackground(Color.WHITE);
        cb.setForeground(Color.BLACK);
    }

    private void styleSpinner(JSpinner sp) {
        sp.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sp.getEditor().getComponent(0).setBackground(Color.WHITE);
        sp.getEditor().getComponent(0).setForeground(Color.BLACK);
    }

    private JButton makeButton(String text, Color bg) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? bg.brighter() : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        b.setContentAreaFilled(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton makeSmallButton(String text) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? BG_INPUT.brighter() : BG_INPUT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        b.setContentAreaFilled(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setForeground(ACCENT);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ─── Custom Lightweight Toast ───────────────────────────────────────────────
    private static class LightweightToast extends JWindow {
        private String message;
        private Color color;
        private float opacity = 0f;

        public static void show(JFrame parent, String message, Color color) {
            SwingUtilities.invokeLater(() -> {
                LightweightToast toast = new LightweightToast(parent, message, color);
                toast.setVisible(true);
                toast.animateIn();
            });
        }

        private LightweightToast(JFrame parent, String message, Color color) {
            super(parent);
            this.message = message;
            this.color = color;
            initComponents();
            pack();
            setLocationRelativeTo(parent);
            // Position near the top
            setLocation(getX(), parent.getY() + 60);
        }

        private void initComponents() {
            JPanel panel = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(color);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                    g2.setColor(color.darker());
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                    g2.dispose();
                }
            };
            panel.setOpaque(false);
            panel.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));

            JLabel label = new JLabel(message);
            label.setFont(new Font("Segoe UI", Font.BOLD, 13));
            label.setForeground(Color.WHITE);
            panel.add(label, BorderLayout.CENTER);

            setBackground(new Color(0, 0, 0, 0));
            setContentPane(panel);
            setOpacity(0f);
            setAlwaysOnTop(true);
        }

        private void animateIn() {
            javax.swing.Timer timer = new javax.swing.Timer(30, null);
            timer.addActionListener(e -> {
                opacity += 0.1f;
                if (opacity >= 1.0f) {
                    opacity = 1.0f;
                    timer.stop();
                    scheduleOut();
                }
                setOpacity(opacity);
            });
            timer.start();
        }

        private void scheduleOut() {
            javax.swing.Timer delay = new javax.swing.Timer(2500, e -> animateOut());
            delay.setRepeats(false);
            delay.start();
        }

        private void animateOut() {
            javax.swing.Timer timer = new javax.swing.Timer(30, null);
            timer.addActionListener(e -> {
                opacity -= 0.1f;
                if (opacity <= 0.0f) {
                    opacity = 0.0f;
                    timer.stop();
                    dispose();
                }
                setOpacity(Math.max(0f, opacity));
            });
            timer.start();
        }
    }
}
