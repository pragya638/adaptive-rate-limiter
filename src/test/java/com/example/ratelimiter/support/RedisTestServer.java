package com.example.ratelimiter.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts Redis for integration tests.
 *
 * <p>Preference order:
 * <ol>
 *   <li>Testcontainers (when Docker is available)</li>
 *   <li>an already-running Redis at {@code redis.host}/{@code redis.port}
 *       (defaults to {@code localhost:6379})</li>
 *   <li>return {@code null} so the test can skip with a clear message</li>
 * </ol>
 */
public final class RedisTestServer implements AutoCloseable {

    public static final String UNAVAILABLE_REASON =
            "Redis integration tests skipped: Docker is unavailable and no Redis is listening. "
                    + "Start Docker Desktop or a local Redis, or set -Dredis.host/-Dredis.port.";

    private static final Logger log = LoggerFactory.getLogger(RedisTestServer.class);

    private final GenericContainer<?> container;
    private final LettuceConnectionFactory connectionFactory;
    private final StringRedisTemplate template;

    private RedisTestServer(GenericContainer<?> container, String host, int port) {
        this.container = container;
        this.connectionFactory = new LettuceConnectionFactory(host, port);
        this.connectionFactory.afterPropertiesSet();
        this.template = new StringRedisTemplate(connectionFactory);
        this.template.afterPropertiesSet();
    }

    public static RedisTestServer startOrNull() {
        String host = System.getProperty("redis.host",
                System.getenv().getOrDefault("REDIS_HOST", "localhost"));
        int port = Integer.parseInt(System.getProperty("redis.port",
                System.getenv().getOrDefault("REDIS_PORT", "6379")));

        if (isDockerAvailable()) {
            try {
                GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                        .withExposedPorts(6379)
                        .waitingFor(Wait.forListeningPort());
                container.start();
                log.info("Started Redis test container on {}:{}", container.getHost(),
                        container.getMappedPort(6379));
                return new RedisTestServer(container, container.getHost(), container.getMappedPort(6379));
            } catch (RuntimeException ex) {
                log.warn("Could not start Redis test container; trying {}:{}", host, port, ex);
            }
        }

        if (canConnect(host, port)) {
            log.info("Using local Redis at {}:{}", host, port);
            return new RedisTestServer(null, host, port);
        }

        log.info("No Redis available at {}:{}; integration tests will be skipped", host, port);
        return null;
    }

    public StringRedisTemplate template() {
        return template;
    }

    public void flush() {
        try (RedisConnection connection = template.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Override
    public void close() {
        try {
            connectionFactory.destroy();
        } finally {
            if (container != null) {
                container.stop();
            }
        }
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            log.debug("Docker is not available: {}", ex.toString());
            return false;
        }
    }

    private static boolean canConnect(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
