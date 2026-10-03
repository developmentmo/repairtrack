package com.repairtrack.document.infrastructure;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal clamd client (TCP, {@code INSTREAM} and {@code PING}); no extra library. The content is sent in chunks
 * of {@value #CHUNK_SIZE} bytes, each prefixed with its length as a 4-byte big-endian integer, ended by a zero
 * length. clamd answers {@code stream: OK}, {@code stream: <signature> FOUND} or an {@code ERROR}.
 * <p>
 * clamd's {@code StreamMaxLength} must be at least the upload limit (20 MB); the default is 25 MB.
 */
public class ClamAvScanner implements MalwareScanner {

    static final int CHUNK_SIZE = 64 * 1024;
    private static final byte[] INSTREAM = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PING = "zPING\0".getBytes(StandardCharsets.US_ASCII);

    private final MalwareScanProperties properties;

    public ClamAvScanner(MalwareScanProperties properties) {
        this.properties = properties;
    }

    @Override
    public ScanResult scan(InputStream content) {
        String reply;
        try (Socket socket = connect()) {
            OutputStream out = new BufferedOutputStream(socket.getOutputStream(), CHUNK_SIZE + 4);
            out.write(INSTREAM);
            byte[] buffer = new byte[CHUNK_SIZE];
            int read;
            while ((read = content.read(buffer)) > 0) {
                out.write(lengthPrefix(read));
                out.write(buffer, 0, read);
            }
            out.write(lengthPrefix(0));
            out.flush();
            reply = readReply(socket);
        } catch (IOException e) {
            throw new MalwareScannerUnavailableException(e);
        }
        return interpret(reply);
    }

    /** True when clamd answers {@code PONG}. Used by the health indicator. */
    public boolean ping() {
        try (Socket socket = connect()) {
            socket.getOutputStream().write(PING);
            socket.getOutputStream().flush();
            return "PONG".equals(readReply(socket));
        } catch (IOException e) {
            return false;
        }
    }

    static ScanResult interpret(String reply) {
        if (reply.endsWith(" OK") || reply.equals("OK")) {
            return ScanResult.CLEAN;
        }
        if (reply.endsWith(" FOUND")) {
            String withoutSuffix = reply.substring(0, reply.length() - " FOUND".length());
            int colon = withoutSuffix.indexOf(": ");
            return ScanResult.infected(colon >= 0 ? withoutSuffix.substring(colon + 2) : withoutSuffix);
        }
        // e.g. "INSTREAM size limit exceeded. ERROR": no verdict, so fail closed
        throw new MalwareScannerUnavailableException(new IOException("clamd: " + reply));
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(properties.host(), properties.port()),
                (int) properties.connectTimeout().toMillis());
        socket.setSoTimeout((int) properties.readTimeout().toMillis());
        return socket;
    }

    private static String readReply(Socket socket) throws IOException {
        byte[] bytes = socket.getInputStream().readAllBytes();
        return new String(bytes, StandardCharsets.US_ASCII).replace("\0", "").trim();
    }

    private static byte[] lengthPrefix(int length) {
        return new byte[] {(byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length};
    }
}
