package cc.nimbus.features.music.playback;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AudioEngine {
    private static final int SPECTRUM_WINDOW = 1024;
    private static final int SPECTRUM_BANDS = 256;
    private static final float[] SPECTRUM_HANN = createHann(SPECTRUM_WINDOW);
    private static final int[] SPECTRUM_BINS = createBins(SPECTRUM_WINDOW, SPECTRUM_BANDS);

    private final Object stateLock = new Object();
    private volatile File audioFile;
    private volatile float volume = 0.7f;
    private volatile float rate = 1.0f;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private volatile long playedFrames = 0;
    private volatile AudioFormat baseFormat;
    private volatile Thread thread;
    private volatile FloatControl gainControl;
    private volatile float lastAppliedVolume = -1.0f;
    private volatile SourceDataLine currentLine;
    private volatile long seekFrame = -1L;
    private final AtomicReference<float[]> spectrumRef = new AtomicReference<>(new float[0]);
    private final float[] spectrumSmooth = new float[SPECTRUM_BANDS];
    private final float[] spectrumRing = new float[SPECTRUM_WINDOW];
    private final float[] spectrumWindow = new float[SPECTRUM_WINDOW];
    private int spectrumRingPos;
    private int spectrumRingFill;
    private long lastSpectrumUpdateMs;

    public AudioEngine(File file) {
        setAudio(Objects.requireNonNull(file, "file"));
    }

    private void setAudio(File file) {
        Objects.requireNonNull(file, "file");
        synchronized (stateLock) {
            audioFile = file;
            finished.set(false);
            playedFrames = 0;
            resetSpectrumState();
        }
    }

    public void play() {
        synchronized (stateLock) {
            if (thread != null && thread.isAlive()) return;
            closed.set(false);
            paused.set(false);
            finished.set(false);
            thread = new Thread(this::runLoop, "NcmAudioPlayer");
            thread.setDaemon(true);
            try {
                thread.setPriority(Math.min(Thread.MAX_PRIORITY, Thread.NORM_PRIORITY + 1));
            } catch (Exception ignored) {
            }
            thread.start();
        }
    }

    public void close() {
        closed.set(true);
        paused.set(false);
        gainControl = null;
        resetSpectrumState();
        SourceDataLine line = currentLine;
        if (line != null) {
            try {
                line.stop();
            } catch (Exception ignored) {
            }
            try {
                line.flush();
            } catch (Exception ignored) {
            }
        }
        Thread t;
        synchronized (stateLock) {
            t = thread;
        }
        if (t != null) t.interrupt();
    }

    public boolean isFinished() {
        return finished.get();
    }

    public boolean isPausing() {
        return paused.get();
    }

    public void setPaused(boolean pause) {
        paused.set(pause);
        SourceDataLine line = currentLine;
        if (line == null) return;
        if (pause) {
            try {
                line.stop();
            } catch (Exception ignored) {
            }
        } else {
            try {
                line.start();
            } catch (Exception ignored) {
            }
        }
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        float v = Math.max(0.0f, Math.min(1.0f, volume));
        this.volume = v;
        applyGainIfAvailable(v);
    }

    public float getRate() {
        return rate;
    }

    public void setRate(float rate) {
        if (Float.isNaN(rate) || Float.isInfinite(rate)) return;
        float bounded = Math.max(0.5f, Math.min(2.0f, rate));
        if (Math.abs(this.rate - bounded) < 0.001f) return;
        this.rate = bounded;
        if (baseFormat != null) seekFrame = playedFrames;
    }

    public float getCurrentTimeSeconds() {
        AudioFormat fmt = baseFormat;
        if (fmt == null) return 0.0f;
        return (float) (playedFrames / fmt.getSampleRate());
    }

    public float[] getSpectrum() {
        return spectrumRef.get();
    }

    public void seekSeconds(double seconds) {
        AudioFormat fmt = baseFormat;
        if (fmt == null) return;
        double sr = fmt.getSampleRate();
        if (sr <= 1) return;
        long target = (long) Math.max(0d, seconds * sr);
        seekFrame = target;
    }

    private void runLoop() {
        File file = audioFile;
        if (file == null || !file.exists()) {
            finished.set(true);
            return;
        }

        long startFrame = 0L;
        try {
            while (!closed.get()) {
                try (AudioInputStream ais0 = AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                    AudioFormat in = ais0.getFormat();
                    AudioFormat decoded = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            in.getSampleRate(),
                            16,
                            in.getChannels(),
                            in.getChannels() * 2,
                            in.getSampleRate(),
                            false
                    );
                    try (AudioInputStream ais = AudioSystem.getAudioInputStream(decoded, ais0)) {
                        baseFormat = decoded;
                        if (startFrame > 0) {
                            skipToFrame(ais, decoded, startFrame);
                            playedFrames = startFrame;
                        }
                        long next = playStream(ais, decoded);
                        if (next >= 0) {
                            startFrame = next;
                            continue;
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            if (!closed.get()) e.printStackTrace();
        }
        finished.set(true);
    }

    private long playStream(AudioInputStream ais, AudioFormat decoded) throws Exception {
        float outputRate = Math.max(1f, decoded.getSampleRate() * rate);
        AudioFormat output = new AudioFormat(
                decoded.getEncoding(),
                outputRate,
                decoded.getSampleSizeInBits(),
                decoded.getChannels(),
                decoded.getFrameSize(),
                outputRate,
                decoded.isBigEndian()
        );
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, output);
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            int bufferBytes = Math.max(output.getFrameSize() * 16384, 65536);
            line.open(output, bufferBytes);
            currentLine = line;
            if (paused.get()) {
                try {
                    line.stop();
                } catch (Exception ignored) {
                }
            } else {
                line.start();
            }

            FloatControl gain = null;
            try {
                gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            } catch (Exception ignored) {
            }
            gainControl = gain;
            lastAppliedVolume = -1.0f;
            applyGainIfAvailable(volume);

            byte[] buffer = new byte[Math.max(decoded.getFrameSize() * 8192, 32768)];
            while (!closed.get()) {
                long sf = seekFrame;
                if (sf >= 0) {
                    seekFrame = -1L;
                    return sf;
                }
                if (paused.get()) {
                    Thread.sleep(20);
                    continue;
                }
                int read = ais.read(buffer, 0, buffer.length);
                if (read == -1) break;
                updateSpectrum(decoded, buffer, read);
                if (gainControl != null) {
                    float v = volume;
                    if (Math.abs(v - lastAppliedVolume) > 0.005f) {
                        applyGainIfAvailable(v);
                    }
                } else {
                    applyVolumePcm16le(buffer, read, volume);
                }
                line.write(buffer, 0, read);
                playedFrames += read / decoded.getFrameSize();
            }

            line.drain();
        } finally {
            currentLine = null;
            gainControl = null;
        }
        long sf = seekFrame;
        if (sf >= 0) {
            seekFrame = -1L;
            return sf;
        }
        return -1L;
    }

    private void updateSpectrum(AudioFormat format, byte[] pcm16le, int len) {
        if (format == null || pcm16le == null || len <= 0) return;
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        if (channels <= 0 || frameSize <= 0) return;

        for (int i = 0; i + frameSize - 1 < len; i += frameSize) {
            int sum = 0;
            for (int ch = 0; ch < channels; ch++) {
                int off = i + ch * 2;
                int lo = pcm16le[off] & 0xFF;
                int hi = pcm16le[off + 1];
                short s = (short) (lo | (hi << 8));
                sum += s;
            }
            float mono = (sum / (float) channels) * (1f / 32768f);
            spectrumRing[spectrumRingPos] = mono;
            spectrumRingPos = (spectrumRingPos + 1) & (SPECTRUM_WINDOW - 1);
            if (spectrumRingFill < SPECTRUM_WINDOW) spectrumRingFill++;
        }

        if (spectrumRingFill < SPECTRUM_WINDOW) return;
        long now = System.currentTimeMillis();
        if (now - lastSpectrumUpdateMs < 16L) return;
        lastSpectrumUpdateMs = now;

        int start = spectrumRingPos;
        for (int i = 0; i < SPECTRUM_WINDOW; i++) {
            spectrumWindow[i] = spectrumRing[(start + i) & (SPECTRUM_WINDOW - 1)] * SPECTRUM_HANN[i];
        }

        for (int i = 0; i < SPECTRUM_BANDS; i++) {
            int k = SPECTRUM_BINS[i];
            double w = (2.0 * Math.PI * k) / (double) SPECTRUM_WINDOW;
            double cos = Math.cos(w);
            double sin = Math.sin(w);
            double coeff = 2.0 * cos;

            double s0 = 0.0;
            double s1 = 0.0;
            double s2 = 0.0;
            for (int n = 0; n < SPECTRUM_WINDOW; n++) {
                s0 = spectrumWindow[n] + (coeff * s1) - s2;
                s2 = s1;
                s1 = s0;
            }
            double real = s1 - (s2 * cos);
            double imag = s2 * sin;
            double mag = Math.sqrt((real * real) + (imag * imag)) / (double) SPECTRUM_WINDOW;

            float v = (float) (Math.log10(1.0 + (mag * 1024.0)) / Math.log10(1025.0));
            if (v < 0f) v = 0f;
            if (v > 1f) v = 1f;
            spectrumSmooth[i] = (spectrumSmooth[i] * 0.45f) + (v * 0.55f);
        }

        spectrumRef.set(spectrumSmooth.clone());
    }

    private void resetSpectrumState() {
        spectrumRef.set(new float[0]);
        for (int i = 0; i < spectrumSmooth.length; i++) spectrumSmooth[i] = 0f;
        spectrumRingPos = 0;
        spectrumRingFill = 0;
        lastSpectrumUpdateMs = 0L;
    }

    private static float[] createHann(int n) {
        float[] w = new float[n];
        if (n <= 1) return w;
        for (int i = 0; i < n; i++) {
            w[i] = 0.5f - (0.5f * (float) Math.cos((2.0 * Math.PI * i) / (double) (n - 1)));
        }
        return w;
    }

    private static int[] createBins(int window, int bands) {
        int[] bins = new int[bands];
        int maxBin = (window / 2) - 1;
        if (maxBin < 1) maxBin = 1;
        if (bands <= 1) {
            bins[0] = 1;
            return bins;
        }
        for (int i = 0; i < bands; i++) {
            double t = i / (double) (bands - 1);
            double shaped = t;
            int k = 1 + (int) Math.round(shaped * (maxBin - 1));
            if (k < 1) k = 1;
            if (k > maxBin) k = maxBin;
            bins[i] = k;
        }
        return bins;
    }

    private static void skipToFrame(AudioInputStream stream, AudioFormat format, long frameIndex) throws Exception {
        long bytesToSkip = frameIndex * (long) format.getFrameSize();
        if (bytesToSkip <= 0) return;
        long skipped = 0L;
        byte[] buf = new byte[4096];
        while (skipped < bytesToSkip) {
            long left = bytesToSkip - skipped;
            long s = stream.skip(left);
            if (s > 0) {
                skipped += s;
                continue;
            }
            int read = stream.read(buf, 0, (int) Math.min(buf.length, left));
            if (read <= 0) break;
            skipped += read;
        }
    }

    private void applyGainIfAvailable(float volume) {
        FloatControl gain = gainControl;
        if (gain == null) return;
        float v = Math.max(0.0f, Math.min(1.0f, volume));
        float db;
        if (v <= 0.0001f) {
            db = gain.getMinimum();
        } else {
            db = (float) (20.0 * Math.log10(v));
            db = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db));
        }
        try {
            gain.setValue(db);
            lastAppliedVolume = v;
        } catch (Exception ignored) {
        }
    }

    private static void applyVolumePcm16le(byte[] buf, int len, float volume) {
        if (volume >= 0.999f) return;
        if (volume <= 0.001f) {
            for (int i = 0; i < len; i++) buf[i] = 0;
            return;
        }
        for (int i = 0; i + 1 < len; i += 2) {
            int lo = buf[i] & 0xFF;
            int hi = buf[i + 1];
            short s = (short) (lo | (hi << 8));
            int scaled = Math.round(s * volume);
            if (scaled > Short.MAX_VALUE) scaled = Short.MAX_VALUE;
            if (scaled < Short.MIN_VALUE) scaled = Short.MIN_VALUE;
            buf[i] = (byte) (scaled & 0xFF);
            buf[i + 1] = (byte) ((scaled >>> 8) & 0xFF);
        }
    }
}
