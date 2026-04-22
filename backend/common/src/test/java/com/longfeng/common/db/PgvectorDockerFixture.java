package com.longfeng.common.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Minimal replacement for Testcontainers Docker 29.x socket-probe incompatibility
 * (Testcontainers 1.20.4 still returns 400 BadRequest against Docker Desktop 4.64.0 /
 * Engine 29.2.1 — upstream issue). Spawns pgvector/pgvector:pg16 via docker run,
 * exposes JDBC URL, and cleans up on close.
 * refs plan §5.7 steps 5-6 · §5.8 V-S1-04.
 */
final class PgvectorDockerFixture implements AutoCloseable {

    private static final String IMAGE = "pgvector/pgvector:pg16";
    private static final String NAME_PREFIX = "pg-wb-s1-it-";
    private static final String DB = "wrongbook";
    private static final String USER = "postgres";
    private static final String PWD = "wb";

    private final String containerName;
    private final int hostPort;

    PgvectorDockerFixture() {
        this.containerName = NAME_PREFIX + Long.toHexString(System.nanoTime());
        this.hostPort = 54323 + (int) (System.nanoTime() % 1000);
    }

    void start() throws IOException, InterruptedException {
        // best-effort prior cleanup
        runDocker("rm", "-f", containerName).waitFor();

        Process run = runDocker("run", "-d", "--rm",
                "--name", containerName,
                "-e", "POSTGRES_PASSWORD=" + PWD,
                "-e", "POSTGRES_DB=" + DB,
                "-p", hostPort + ":5432",
                IMAGE);
        if (run.waitFor() != 0) {
            throw new IllegalStateException("docker run failed for " + containerName
                    + " · stderr=" + readAll(run.getErrorStream()));
        }

        // Wait for pg_isready (max 30s)
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            Process ready = runDocker("exec", containerName,
                    "pg_isready", "-U", USER, "-d", DB);
            if (ready.waitFor() == 0) {
                return;
            }
            Thread.sleep(500);
        }
        stopAndDump();
        throw new IllegalStateException("Postgres container did not become ready in 30s · " + containerName);
    }

    String jdbcUrl() {
        return "jdbc:postgresql://localhost:" + hostPort + "/" + DB;
    }

    Connection connect() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USER);
        props.setProperty("password", PWD);
        return DriverManager.getConnection(jdbcUrl(), props);
    }

    @Override
    public void close() {
        try {
            runDocker("rm", "-f", containerName).waitFor();
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void stopAndDump() {
        try {
            runDocker("logs", containerName).waitFor();
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            close();
        }
    }

    private static Process runDocker(String... args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        for (String a : args) {
            cmd.add(a);
        }
        return new ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start();
    }

    private static String readAll(java.io.InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    String user() {
        return USER;
    }

    String password() {
        return PWD;
    }
}
