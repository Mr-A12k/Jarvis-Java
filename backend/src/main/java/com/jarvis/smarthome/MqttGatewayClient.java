package com.jarvis.smarthome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight, zero-dependency pure Java MQTT v3.1.1 Client.
 * Connects directly to local MQTT brokers (Mosquitto, EMQX, HiveMQ, Home Assistant Mosquitto add-on)
 * to control smart plugs, power strips, and lights via Tasmota / ESPHome / Zigbee2MQTT topics.
 */
@Component
public class MqttGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(MqttGatewayClient.class);

    @FunctionalInterface
    public interface MessageListener {
        void onMessage(String topic, String payload);
    }

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicInteger packetIdGenerator = new AtomicInteger(1);
    private final Map<String, List<MessageListener>> subscribers = new ConcurrentHashMap<>();

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private Thread receiverThread;
    private ScheduledExecutorService pingExecutor;

    private String currentHost;
    private int currentPort;
    private String currentClientId;

    public synchronized boolean connect(String brokerUrl, String clientId, String username, String password) {
        if (connected.get()) {
            disconnect();
        }

        try {
            ParsedUri uri = parseBrokerUrl(brokerUrl);
            this.currentHost = uri.host;
            this.currentPort = uri.port;
            this.currentClientId = (clientId != null && !clientId.isBlank()) ? clientId : "jarvis-" + UUID.randomUUID().toString().substring(0, 8);

            socket = new Socket();
            socket.connect(new InetSocketAddress(currentHost, currentPort), 2500);
            socket.setSoTimeout(15000);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            // Build MQTT 3.1.1 CONNECT packet
            sendConnectPacket(currentClientId, username, password);

            // Read CONNACK (0x20 0x02 [flags] [return_code])
            int header = in.readUnsignedByte();
            if (header != 0x20) {
                log.warn("MQTT unexpected header on connect: 0x{}", Integer.toHexString(header));
                cleanup();
                return false;
            }
            int remainingLength = readRemainingLength(in);
            int flags = in.readUnsignedByte();
            int returnCode = in.readUnsignedByte();

            if (returnCode != 0) {
                log.warn("MQTT connection rejected with code: {}", returnCode);
                cleanup();
                return false;
            }

            connected.set(true);
            log.info("Successfully connected to MQTT broker at {}:{} (Client ID: {})", currentHost, currentPort, currentClientId);

            // Start background receiver
            startReceiver();

            // Start periodic PINGREQ keepalive (every 30 seconds)
            startKeepAlive();

            return true;
        } catch (Exception e) {
            log.debug("MQTT broker unreachable at {}: {}", brokerUrl, e.getMessage());
            cleanup();
            return false;
        }
    }

    public synchronized void disconnect() {
        if (!connected.get()) return;
        try {
            if (out != null) {
                out.write(new byte[]{(byte) 0xE0, 0x00}); // DISCONNECT packet
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            cleanup();
        }
    }

    public boolean isConnected() {
        return connected.get() && socket != null && !socket.isClosed();
    }

    public synchronized boolean publish(String topic, String payload) {
        if (!isConnected()) {
            log.debug("MQTT not connected; dropping publish to topic: {}", topic);
            return false;
        }
        try {
            byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
            byte[] payloadBytes = payload != null ? payload.getBytes(StandardCharsets.UTF_8) : new byte[0];

            ByteArrayOutputStream varHeader = new ByteArrayOutputStream();
            varHeader.write((topicBytes.length >> 8) & 0xFF);
            varHeader.write(topicBytes.length & 0xFF);
            varHeader.write(topicBytes);
            varHeader.write(payloadBytes);

            byte[] body = varHeader.toByteArray();
            out.write(0x30); // PUBLISH QoS 0
            writeRemainingLength(out, body.length);
            out.write(body);
            out.flush();
            return true;
        } catch (Exception e) {
            log.warn("MQTT publish to {} failed: {}", topic, e.getMessage());
            handleDisconnect();
            return false;
        }
    }

    public synchronized boolean subscribe(String topicFilter, MessageListener listener) {
        subscribers.computeIfAbsent(topicFilter, k -> new CopyOnWriteArrayList<>()).add(listener);

        if (!isConnected()) {
            return false;
        }
        try {
            int packetId = packetIdGenerator.incrementAndGet() & 0xFFFF;
            byte[] filterBytes = topicFilter.getBytes(StandardCharsets.UTF_8);

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.write((packetId >> 8) & 0xFF);
            buf.write(packetId & 0xFF);
            buf.write((filterBytes.length >> 8) & 0xFF);
            buf.write(filterBytes.length & 0xFF);
            buf.write(filterBytes);
            buf.write(0x00); // Requested QoS 0

            byte[] body = buf.toByteArray();
            out.write(0x82); // SUBSCRIBE header
            writeRemainingLength(out, body.length);
            out.write(body);
            out.flush();
            return true;
        } catch (Exception e) {
            log.warn("MQTT subscribe to {} failed: {}", topicFilter, e.getMessage());
            return false;
        }
    }

    private void sendConnectPacket(String clientId, String username, String password) throws IOException {
        ByteArrayOutputStream variableHeader = new ByteArrayOutputStream();

        // Protocol Name: "MQTT"
        variableHeader.write(0x00);
        variableHeader.write(0x04);
        variableHeader.write("MQTT".getBytes(StandardCharsets.UTF_8));
        variableHeader.write(0x04); // Protocol Level: 4 (MQTT 3.1.1)

        // Connect Flags
        int flags = 0x02; // Clean Session
        boolean hasUser = username != null && !username.isBlank();
        boolean hasPass = password != null && !password.isBlank();
        if (hasUser) flags |= 0x80;
        if (hasPass) flags |= 0x40;
        variableHeader.write(flags);

        // Keep Alive (60s)
        variableHeader.write(0x00);
        variableHeader.write(0x3C);

        // Payload
        byte[] clientIdBytes = clientId.getBytes(StandardCharsets.UTF_8);
        variableHeader.write((clientIdBytes.length >> 8) & 0xFF);
        variableHeader.write(clientIdBytes.length & 0xFF);
        variableHeader.write(clientIdBytes);

        if (hasUser) {
            byte[] userBytes = username.getBytes(StandardCharsets.UTF_8);
            variableHeader.write((userBytes.length >> 8) & 0xFF);
            variableHeader.write(userBytes.length & 0xFF);
            variableHeader.write(userBytes);
        }
        if (hasPass) {
            byte[] passBytes = password.getBytes(StandardCharsets.UTF_8);
            variableHeader.write((passBytes.length >> 8) & 0xFF);
            variableHeader.write(passBytes.length & 0xFF);
            variableHeader.write(passBytes);
        }

        byte[] body = variableHeader.toByteArray();
        out.write(0x10); // CONNECT packet type
        writeRemainingLength(out, body.length);
        out.write(body);
        out.flush();
    }

    private void startReceiver() {
        receiverThread = new Thread(() -> {
            try {
                while (connected.get() && socket != null && !socket.isClosed()) {
                    int header = in.readUnsignedByte();
                    int packetType = (header >> 4) & 0x0F;
                    int remainingLength = readRemainingLength(in);
                    byte[] packetData = new byte[remainingLength];
                    in.readFully(packetData);

                    if (packetType == 3) { // PUBLISH
                        parseAndDispatchPublish(packetData);
                    } else if (packetType == 13) { // PINGRESP
                        log.trace("MQTT PINGRESP received");
                    }
                }
            } catch (Exception e) {
                if (connected.get()) {
                    log.info("MQTT receiver disconnected: {}", e.getMessage());
                    handleDisconnect();
                }
            }
        }, "jarvis-mqtt-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    private void parseAndDispatchPublish(byte[] data) {
        try {
            if (data.length < 2) return;
            int topicLen = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            if (data.length < 2 + topicLen) return;

            String topic = new String(data, 2, topicLen, StandardCharsets.UTF_8);
            int payloadOffset = 2 + topicLen;
            String payload = (data.length > payloadOffset)
                    ? new String(data, payloadOffset, data.length - payloadOffset, StandardCharsets.UTF_8)
                    : "";

            for (Map.Entry<String, List<MessageListener>> entry : subscribers.entrySet()) {
                if (topicMatches(entry.getKey(), topic)) {
                    for (MessageListener listener : entry.getValue()) {
                        try {
                            listener.onMessage(topic, payload);
                        } catch (Exception ex) {
                            log.error("Error in MQTT subscriber callback: {}", ex.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse incoming MQTT publish: {}", e.getMessage());
        }
    }

    private boolean topicMatches(String filter, String topic) {
        if (filter.equals("#") || filter.equals(topic)) return true;
        if (filter.endsWith("/#")) {
            String prefix = filter.substring(0, filter.length() - 2);
            return topic.startsWith(prefix);
        }
        return false;
    }

    private void startKeepAlive() {
        if (pingExecutor != null && !pingExecutor.isShutdown()) {
            pingExecutor.shutdownNow();
        }
        pingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jarvis-mqtt-ping");
            t.setDaemon(true);
            return t;
        });
        pingExecutor.scheduleAtFixedRate(() -> {
            if (isConnected()) {
                try {
                    synchronized (this) {
                        if (out != null) {
                            out.write(new byte[]{(byte) 0xC0, 0x00}); // PINGREQ
                            out.flush();
                        }
                    }
                } catch (Exception e) {
                    handleDisconnect();
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void handleDisconnect() {
        connected.set(false);
        cleanup();
    }

    private synchronized void cleanup() {
        connected.set(false);
        if (pingExecutor != null) {
            pingExecutor.shutdownNow();
            pingExecutor = null;
        }
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        in = null;
        out = null;
        socket = null;
    }

    private int readRemainingLength(DataInputStream in) throws IOException {
        int multiplier = 1;
        int value = 0;
        int digit;
        do {
            digit = in.readUnsignedByte();
            value += (digit & 127) * multiplier;
            multiplier *= 128;
            if (multiplier > 128 * 128 * 128) {
                throw new IOException("Malformed MQTT remaining length");
            }
        } while ((digit & 128) != 0);
        return value;
    }

    private void writeRemainingLength(OutputStream out, int length) throws IOException {
        do {
            int digit = length % 128;
            length /= 128;
            if (length > 0) {
                digit |= 0x80;
            }
            out.write(digit);
        } while (length > 0);
    }

    private record ParsedUri(String host, int port) {}

    private ParsedUri parseBrokerUrl(String url) {
        String clean = url.trim();
        if (clean.startsWith("tcp://") || clean.startsWith("mqtt://")) {
            clean = clean.substring(clean.indexOf("://") + 3);
        }
        String[] parts = clean.split(":");
        String host = parts[0];
        int port = 1883;
        if (parts.length > 1) {
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        return new ParsedUri(host, port);
    }
}
