package com.netwatchdog.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Motor de recuperación - ejecuta acciones correctivas via ProcessBuilder.
 */
public class RecoveryEngine {

    private static final int TIMEOUT_SECS = 30;

    public void restartNetworkAdapter(String adapterName) {
        WatchdogService.log("ACTION", "Iniciando reinicio de adaptador: " + adapterName);
        String psCmd = String.format(
            "Restart-NetAdapter -Name '%s' -Confirm:$false",
            adapterName.replace("'", "''")
        );
        ExecResult r = runPowerShell(psCmd);
        if (r.ok) {
            WatchdogService.log("OK", "Adaptador '" + adapterName + "' reiniciado.");
            safeSleep(10_000);
        } else {
            WatchdogService.log("ERROR", "Fallo reinicio adaptador: " + r.out);
        }
    }

    public void resyncTime() {
        WatchdogService.log("ACTION", "Iniciando resincronización de hora (w32tm)");
        ExecResult r = run("cmd.exe", "/c", "w32tm", "/resync", "/force");
        if (r.ok) {
            WatchdogService.log("OK", "Hora resincronizada exitosamente.");
        } else {
            WatchdogService.log("ERROR", "Fallo resync hora: " + r.out);
            WatchdogService.log("INFO", "Plan B: reiniciando W32Time...");
            run("cmd.exe", "/c", "net", "stop", "w32time");
            safeSleep(2000);
            run("cmd.exe", "/c", "net", "start", "w32time");
            safeSleep(2000);
            ExecResult retry = run("cmd.exe", "/c", "w32tm", "/resync", "/force");
            if (retry.ok) {
                WatchdogService.log("OK", "Resync exitosa tras reinicio W32Time.");
            } else {
                WatchdogService.log("ERROR", "Plan B falló: " + retry.out);
            }
        }
    }

    private ExecResult runPowerShell(String script) {
        return run("powershell.exe", "-NoProfile", "-NonInteractive",
                   "-WindowStyle", "Hidden", "-Command", script);
    }

    private ExecResult run(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                out = sb.toString();
            }
            boolean done = p.waitFor(TIMEOUT_SECS, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return new ExecResult(false, "Timeout"); }
            return new ExecResult(p.exitValue() == 0, out);
        } catch (Exception e) {
            return new ExecResult(false, e.getMessage());
        }
    }

    private void safeSleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static class ExecResult {
        final boolean ok;
        final String out;
        ExecResult(boolean ok, String out) { this.ok = ok; this.out = out != null ? out : ""; }
    }
}
