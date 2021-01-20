package testquickresto.archiver;

import java.io.*;
import java.util.Objects;

/**
 * The type Archive input stream.
 */
public class ArchiveInputStream extends FilterInputStream {

    private static final int END_OF_FILE = -1;
    private static final int BUFFER_SIZE = 65535;

    private final byte[] buffer;
    private int bufferPosition;
    private int bufferLimit;
    private boolean streamDataFinished;

    /**
     * Instantiates a new Archive input stream.
     *
     * @param in the in
     */
    public ArchiveInputStream(InputStream in) {
        super(Objects.requireNonNull(in, "in"));
        this.buffer = new byte[BUFFER_SIZE];
        this.bufferPosition = 0;
        this.bufferLimit = 0;
    }

    @Override
    public int read() throws IOException {
        byte[] bytes = new byte[1];
        int bytesRead = read(bytes);
        return bytesRead == 1 ? bytes[0] & 0xFF : END_OF_FILE;
    }

    @Override
    public int read(byte[] bytes) throws IOException {
        return read(bytes, 0, bytes.length);
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        int bytesRead = 0;
        while (0 < length) {
            if (isBufferEmpty() && readBufferFromUnderlyingStream() <= 0) {
                break;
            }
            int bytesGotFromBuffer = getBytesFromBuffer(bytes, offset, length);
            bytesRead += bytesGotFromBuffer;
            offset += bytesGotFromBuffer;
            length -= bytesGotFromBuffer;
        }
        return bytesRead;
    }

    @Override
    public long skip(long bytesToSkip) throws IOException {
        for (long i = 0; i < bytesToSkip; ++i) {
            if (read() == END_OF_FILE) {
                return i;
            }
        }
        return bytesToSkip;
    }

    @Override
    public int available() {
        return bufferLimit - bufferPosition;
    }

    @Override
    public void close() throws IOException {
        while (!streamDataFinished) {
            readBufferFromUnderlyingStream();
        }
    }

    @Override
    public synchronized void mark(int limitRead) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean markSupported() {
        return false;
    }


    private boolean isBufferEmpty() {
        return bufferPosition == bufferLimit;
    }

    private int getBytesFromBuffer(byte[] bytes, int offset, int length) {
        int bytesAvailable = bufferLimit - bufferPosition;
        int bytesToRead = Math.min(length, bytesAvailable);
        System.arraycopy(buffer, bufferPosition, bytes, offset, bytesToRead);
        bufferPosition += bytesToRead;
        return bytesToRead;
    }

    private int readBufferFromUnderlyingStream() throws IOException {
        if (streamDataFinished) {
            return END_OF_FILE;
        }

        int chunkSizeHighByte = in.read();
        int chunkSizeLowByte = in.read();
        if (chunkSizeHighByte == END_OF_FILE || chunkSizeLowByte == END_OF_FILE
                || chunkSizeHighByte == 0 && chunkSizeLowByte == 0) {
            streamDataFinished = true;
            return END_OF_FILE;
        }

        int chunkSize = ((chunkSizeHighByte & 0xFF) << 8) | (chunkSizeLowByte & 0xFF);
        int bytesRead = readFullyFromUnderlyingStream(buffer, chunkSize);
        if (bytesRead != chunkSize) {
            throw new IOException("Expected " + chunkSize + " bytes, but got " + bytesRead);
        }

        bufferPosition = 0;
        bufferLimit = chunkSize;

        return chunkSize;
    }

    private int readFullyFromUnderlyingStream(byte[] bytes, int length) throws IOException {
        int offset = 0;
        int totalBytesRead = 0;
        while (0 < length) {
            int bytesRead = in.read(bytes, offset, length);
            if (bytesRead <= 0) {
                break;
            }
            totalBytesRead += bytesRead;
            offset += bytesRead;
            length -= bytesRead;
        }
        return totalBytesRead;
    }
}
