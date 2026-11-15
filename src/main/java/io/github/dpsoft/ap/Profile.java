package io.github.dpsoft.ap;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A recorded and converted profile, ready to be sent as an HTTP response body. It is backed by a temporary file:
 * always {@link #close()} it (e.g. with try-with-resources).
 */
public final class Profile implements AutoCloseable {
    private final Path body;
    private final String contentType;
    private final String contentEncoding;

    Profile(Path body, String contentType, String contentEncoding) {
        this.body = body;
        this.contentType = contentType;
        this.contentEncoding = contentEncoding;
    }

    /** e.g. {@code text/html; charset=utf-8} for flame graphs or {@code application/octet-stream} for JFR/pprof. */
    public String contentType() {
        return contentType;
    }

    /** {@code gzip} for the JFR and pprof outputs, which are compressed. */
    public Optional<String> contentEncoding() {
        return Optional.ofNullable(contentEncoding);
    }

    /** Size of the body in bytes. */
    public long size() {
        try {
            return Files.size(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Copies the body to {@code out}, which is left open. */
    public void writeTo(OutputStream out) throws IOException {
        Files.copy(body, out);
    }

    /** Deletes the temporary file. */
    @Override
    public void close() throws IOException {
        Files.deleteIfExists(body);
    }
}
