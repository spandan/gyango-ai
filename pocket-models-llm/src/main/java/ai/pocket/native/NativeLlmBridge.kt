package ai.pocket.native

object NativeLlmBridge {
    init {
        System.loadLibrary("omp")
        System.loadLibrary("ggml-base")
        System.loadLibrary("ggml-cpu")
        System.loadLibrary("ggml")
        System.loadLibrary("llama")
        System.loadLibrary("pocket_llm")
    }

    external fun init(
        modelPath: String,
        nThreads: Int,
        contextLength: Int
    ): Boolean

    external fun release()

    external fun generateStreaming(
        prompt: String,
        temperature: Float,
        topP: Float,
        maxTokens: Int,
        callback: TokenCallback
    )

    fun interface TokenCallback {
        fun onToken(token: String)
    }
}
