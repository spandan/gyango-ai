package ai.pocket.whisper

/**
 * JNI to whisper.cpp. Load a single GGML/GGUF Whisper model from a filesystem path, then run full-file decode.
 */
object WhisperBridge {
    init {
        System.loadLibrary("pocket_whisper_jni")
    }

    /** Load or replace the Whisper model at [modelPath]. */
    external fun init(modelPath: String, nThreads: Int): Boolean

    external fun release()

    /** PCM mono float32 @ 16 kHz, roughly [-1, 1]. */
    external fun transcribe(samples: FloatArray, nThreads: Int): String
}
