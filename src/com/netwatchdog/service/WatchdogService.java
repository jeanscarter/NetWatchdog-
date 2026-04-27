/*
 * ============================================================================
 *  NetWatchdog - Core Service (Headless Daemon)
 *  Ejecuta bajo NT AUTHORITY\SYSTEM en Sesión 0.
 *  NO importa java.awt ni javax.swing.
 * ============================================================================
 */
package com.netwatchdog.service;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servicio principal de monitoreo (daemon).
 * <p>
 * Ciclo de vida:
 *  1. Lee configuración desde {@code C:\ProgramData\NetWatchdog\config.properties}
 *  2. Ejecuta validaciones de Red y Hora en cada ciclo.
 *  3. Delega la recuperación a {@link RecoveryEngine}.
 *  4. Duerme el intervalo configurado y repite.
 */
public class WatchdogService {

    // ─── Constantes ─────────────────────────────────────────────────────────────
    private static final Path BASE_DIR    = Paths.get("C:\\ProgramData\\NetWatchdog");
    private static final Path CONFIG_FILE = BASE_DIR.resolve("config.properties");
    private static final Path LOG_FILE    = BASE_DIR.resolve("watchdog.log");

    private static final String DEFAULT_DOMAIN   = "activa.local";
    private static final String DEFAULT_ADAPTER  = "Ethernet";
    private static final int    DEFAULT_INTERVAL  = 30;  // segundos
    private static final long   TIME_THRESHOLD_MS = 5000; // 5 segundos

    private static final DateTimeFormatter LOG_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // ─── Estado ─────────────────────────────────────────────────────────────────
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final RecoveryEngine recovery = new RecoveryEngine();

    // Configuración (recargada en cada ciclo)
    private String targetDomain  = DEFAULT_DOMAIN;
    private String adapterName   = DEFAULT_ADAPTER;
    private int    intervalSecs  = DEFAULT_INTERVAL;
    private boolean recoveryEnabled = true;

    // ─── Entry Point ────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        WatchdogService service = new WatchdogService();
        service.start();
    }

    // ─── Ciclo Principal ────────────────────────────────────────────────────────
    public void start() {
        log("INFO", "═══════════════════════════════════════════════════════════");
        log("INFO", "  NetWatchdog Core Service v1.0 - Iniciando...");
        log("INFO", "═══════════════════════════════════════════════════════════");

        ensureBaseDirectory();
        ensureDefaultConfig();

        // Shutdown hook para detención limpia
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            log("INFO", "Señal de apagado recibida. Deteniendo servicio...");
        }));

        while (running.get()) {
            try {
                reloadConfig();
                runChecks();
                Thread.sleep(intervalSecs * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("WARN", "Hilo principal interrumpido.");
                break;
            } catch (Exception e) {
                log("ERROR", "Error inesperado en ciclo principal: " + e.getMessage());
                safeSleep(10_000); // espera de gracia ante errores
            }
        }

        log("INFO", "Servicio detenido correctamente.");
    }

    // ─── Validaciones ───────────────────────────────────────────────────────────

    private void runChecks() {
        boolean networkOk = checkNetwork();
        if (!networkOk) {
            log("FAIL", "VALIDACIÓN DE RED FALLIDA para dominio: " + targetDomain);
            if (recoveryEnabled) {
                recovery.restartNetworkAdapter(adapterName);
            } else {
                log("INFO", "RecoveryEngine está APAGADO. No se tomó acción.");
            }
            return; // No checar hora si la red está caída
        }

        log("OK", "Validación de red exitosa → " + targetDomain);

        boolean timeOk = checkTime();
        if (!timeOk) {
            log("FAIL", "VALIDACIÓN DE HORA FALLIDA (desviación > " + TIME_THRESHOLD_MS + " ms)");
            if (recoveryEnabled) {
                recovery.resyncTime();
            } else {
                log("INFO", "RecoveryEngine está APAGADO. No se tomó acción.");
            }
        } else {
            log("OK", "Validación de hora exitosa (dentro del umbral).");
        }
    }

    /**
     * Valida conectividad de red resolviendo el dominio objetivo
     * y ejecutando un ping ICMP.
     */
    private boolean checkNetwork() {
        try {
            // Paso 1: Resolución DNS
            InetAddress address = InetAddress.getByName(targetDomain);
            log("DEBUG", "DNS resuelto: " + targetDomain + " → " + address.getHostAddress());

            // Paso 2: Ping ICMP (timeout 4 segundos)
            boolean reachable = address.isReachable(4000);
            if (!reachable) {
                // Fallback: intentar con ping del sistema
                log("DEBUG", "ICMP Java falló, intentando ping del sistema...");
                return pingViaSystem(targetDomain);
            }
            return true;
        } catch (UnknownHostException e) {
            log("ERROR", "No se pudo resolver DNS para: " + targetDomain + " → " + e.getMessage());
            return false;
        } catch (IOException e) {
            log("ERROR", "Error de red al verificar: " + targetDomain + " → " + e.getMessage());
            return false;
        }
    }

    /**
     * Fallback: ping usando el comando del sistema.
     */
    private boolean pingViaSystem(String host) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ping", "-n", "1", "-w", "3000", host);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log("ERROR", "Ping del sistema falló: " + e.getMessage());
            return false;
        }
    }

    /**
     * Valida la hora del sistema comparando con la cabecera HTTP Date de Google.
     * Si la diferencia excede {@link #TIME_THRESHOLD_MS}, retorna false.
     */
    private boolean checkTime() {
        try {
            URL url = URI.create("https://www.google.com").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(false);
            conn.connect();

            String dateHeader = conn.getHeaderField("Date");
            conn.disconnect();

            if (dateHeader == null || dateHeader.isEmpty()) {
                log("WARN", "No se recibió cabecera Date de Google.");
                return true; // No marcar como fallo si no hay cabecera
            }

            // Parsear la cabecera HTTP Date (formato RFC 1123)
            ZonedDateTime remoteTime = ZonedDateTime.parse(dateHeader,
                    DateTimeFormatter.RFC_1123_DATE_TIME);
            ZonedDateTime localTime  = ZonedDateTime.now(ZoneOffset.UTC);

            long diffMs = Math.abs(Duration.between(remoteTime, localTime).toMillis());
            log("DEBUG", String.format("Hora remota: %s | Hora local: %s | Δ: %d ms",
                    remoteTime, localTime, diffMs));

            return diffMs <= TIME_THRESHOLD_MS;

        } catch (Exception e) {
            log("ERROR", "Error al validar hora: " + e.getMessage());
            return true; // No penalizar si el check falla por error de conexión
        }
    }

    // ─── Configuración ──────────────────────────────────────────────────────────

    /**
     * Recarga la configuración desde el archivo .properties.
     * Se invoca en cada ciclo para detectar cambios hechos desde la GUI.
     */
    private void reloadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            log("WARN", "Archivo de configuración no encontrado. Usando valores por defecto.");
            return;
        }
        try (InputStream is = Files.newInputStream(CONFIG_FILE)) {
            Properties props = new Properties();
            props.load(is);

            targetDomain = props.getProperty("TargetDomain", DEFAULT_DOMAIN).trim();
            adapterName  = props.getProperty("AdapterName", DEFAULT_ADAPTER).trim();

            String intervalStr = props.getProperty("CheckIntervalSeconds",
                    String.valueOf(DEFAULT_INTERVAL)).trim();
            int parsed = Integer.parseInt(intervalStr);
            intervalSecs = Math.max(5, Math.min(parsed, 3600)); // clamp [5, 3600]

            recoveryEnabled = Boolean.parseBoolean(props.getProperty("RecoveryEnabled", "true"));

        } catch (NumberFormatException e) {
            log("WARN", "Intervalo inválido en config. Usando " + DEFAULT_INTERVAL + "s.");
            intervalSecs = DEFAULT_INTERVAL;
        } catch (IOException e) {
            log("ERROR", "Error leyendo configuración: " + e.getMessage());
        }
    }

    private void ensureBaseDirectory() {
        try {
            Files.createDirectories(BASE_DIR);
        } catch (IOException e) {
            System.err.println("FATAL: No se pudo crear directorio base: " + e.getMessage());
        }
    }

    private void ensureDefaultConfig() {
        if (Files.exists(CONFIG_FILE)) return;
        try {
            Properties defaults = new Properties();
            defaults.setProperty("TargetDomain", DEFAULT_DOMAIN);
            defaults.setProperty("AdapterName", DEFAULT_ADAPTER);
            defaults.setProperty("CheckIntervalSeconds", String.valueOf(DEFAULT_INTERVAL));
            defaults.setProperty("RecoveryEnabled", "true");
            try (OutputStream os = Files.newOutputStream(CONFIG_FILE)) {
                defaults.store(os, "NetWatchdog - Configuración por defecto");
            }
            log("INFO", "Archivo de configuración creado con valores por defecto.");
        } catch (IOException e) {
            log("ERROR", "No se pudo crear configuración por defecto: " + e.getMessage());
        }
    }

    // ─── Logging ────────────────────────────────────────────────────────────────

    /**
     * Escribe una línea de log al archivo y a la consola.
     * Formato: [TIMESTAMP] [LEVEL] mensaje
     */
    static void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(LOG_FMT);
        String line = String.format("[%s] [%-5s] %s", timestamp, level, message);
        System.out.println(line);

        try {
            Files.createDirectories(BASE_DIR);
            Files.write(LOG_FILE,
                    (line + System.lineSeparator()).getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error escribiendo log: " + e.getMessage());
        }
    }

    private void safeSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
