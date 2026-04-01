package ai.pocket.audio

import android.app.Application
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.pocket.PocketAIApplication
import ai.pocket.core.InferenceSettings
import ai.pocket.core.LlmDefaults
import ai.pocket.api.PromptBuilder
import ai.pocket.orchestration.OrchestrationRequest
import ai.pocket.orchestration.Orchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

data class AudioAnalysisUiState(
    val selectedLabel: String? = null,
    val transcript: String? = null,
    val analysis: AudioSentimentResult? = null,
    val rawAnalysisText: String? = null,
    val phase: AudioAnalysisPhase = AudioAnalysisPhase.Idle,
    val transcriptionStep: TranscriptionStep = TranscriptionStep.None,
    val error: String? = null,
    val speechModelMissing: Boolean = false,
    val isRecording: Boolean = false
)

enum class TranscriptionStep {
    None, PreparingSpeechModel, DecodingAudio, RunningWhisper
}

enum class AudioAnalysisPhase {
    Idle, Transcribing, Analyzing, Done
}

class AudioAnalysisViewModel(
    application: Application,
    private val orchestrator: Orchestrator
) : AndroidViewModel(application) {

    private var pendingUri: Uri? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var lastPeakAmp: Float = 0f

    private val _ui = MutableStateFlow(AudioAnalysisUiState())
    val ui: StateFlow<AudioAnalysisUiState> = _ui.asStateFlow()

    private fun cancelRecordingSilently() {
        mediaRecorder?.let { r ->
            try { r.stop() } catch (_: Exception) { }
            r.release()
        }
        mediaRecorder = null
        recordingFile?.delete()
        recordingFile = null
        _ui.value = _ui.value.copy(isRecording = false)
    }

    fun clearSelection() {
        cancelRecordingSilently()
        pendingUri = null
        _ui.value = AudioAnalysisUiState()
    }

    fun startRecording(): Boolean {
        if (mediaRecorder != null) return true
        val app = getApplication<Application>()
        val file = File(app.cacheDir, "pocket_rec_${System.currentTimeMillis()}.m4a")
        recordingFile = file

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(app)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(64000)
            recorder.setAudioChannels(1)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
        } catch (e: Exception) {
            try { recorder.release() } catch (_: Exception) {}
            recordingFile = null
            _ui.value = _ui.value.copy(error = "Mic failed: ${e.message}", isRecording = false)
            return false
        }

        mediaRecorder = recorder
        _ui.value = _ui.value.copy(isRecording = true, error = null, transcript = null, analysis = null, phase = AudioAnalysisPhase.Idle)
        return true
    }

    fun stopRecording() {
        val recorder = mediaRecorder ?: return
        val file = recordingFile
        try {
            Thread.sleep(100)
            recorder.stop()
        } catch (_: Exception) {
        }
        recorder.release()
        mediaRecorder = null
        recordingFile = null
        _ui.value = _ui.value.copy(isRecording = false)

        if (file == null || !file.exists() || file.length() < 500) {
            file?.delete()
            _ui.value = _ui.value.copy(error = "Recording was too short or silent")
            return
        }

        pendingUri = Uri.fromFile(file)
        _ui.value = _ui.value.copy(selectedLabel = "Recording", transcript = null, error = null)
    }

    fun onAudioPicked(uri: Uri, displayName: String?) {
        cancelRecordingSilently()
        pendingUri = uri
        _ui.value = _ui.value.copy(selectedLabel = displayName ?: uri.lastPathSegment, transcript = null, error = null)
    }

    fun runPipeline() {
        val uri = pendingUri ?: return
        viewModelScope.launch {
            val pocketApp = getApplication<PocketAIApplication>()
            val whisperEngine = pocketApp.sharedWhisperEngine
            pocketApp.beginInference()
            try {
                _ui.value = _ui.value.copy(phase = AudioAnalysisPhase.Transcribing, transcriptionStep = TranscriptionStep.PreparingSpeechModel, error = null)
                val modelPath = withContext(Dispatchers.IO) { whisperEngine.resolveModelPathOrNull() }
                if (modelPath == null) {
                    _ui.value = _ui.value.copy(phase = AudioAnalysisPhase.Idle, speechModelMissing = true, error = "Speech model missing")
                    return@launch
                }

                val transcript = try {
                    val app = getApplication<Application>()
                    _ui.value = _ui.value.copy(transcriptionStep = TranscriptionStep.DecodingAudio)
                    coroutineScope {
                        val floatsDeferred = async(Dispatchers.IO) { AudioFloatDecoder.decodeUriToMono16kFloat(app, uri) }
                        val prepDeferred = async(Dispatchers.IO) { whisperEngine.prepareModelIfPresent() }
                        val floats = floatsDeferred.await()
                        prepDeferred.await()

                        if (floats.isEmpty()) error("No audio samples decoded")

                        val peak = floats.maxOfOrNull { abs(it) } ?: 0f
                        lastPeakAmp = peak

                        if (peak > 0 && peak < 0.2f) {
                            val gain = 0.7f / peak
                            for (i in floats.indices) {
                                floats[i] = (floats[i] * gain).coerceIn(-1f, 1f)
                            }
                        }

                        _ui.value = _ui.value.copy(transcriptionStep = TranscriptionStep.RunningWhisper)
                        whisperEngine.transcribe16kMono(floats).trim()
                    }
                } catch (e: Exception) {
                    _ui.value = _ui.value.copy(phase = AudioAnalysisPhase.Idle, error = e.message ?: "Failed to process audio")
                    return@launch
                }

                if (transcript.isBlank() || transcript.length < 2) {
                    val ampInfo = String.format("%.3f", lastPeakAmp)
                    _ui.value = _ui.value.copy(phase = AudioAnalysisPhase.Idle, error = "Transcription empty (Peak: $ampInfo). Try speaking louder/longer.")
                    return@launch
                }

                _ui.value = _ui.value.copy(transcript = transcript, phase = AudioAnalysisPhase.Analyzing)
                val sentimentTask = """
Analyze sentiment for this speech transcript. Output only one valid JSON object (no markdown fences), keys exactly:
{"sentiment_overall":"","sentiment_score":0,"suggested_tone":"","summary":"","key_phrases":[],"notes":""}
sentiment_score is float from -1 to 1. key_phrases is an array of short strings.

Transcript:
"$transcript"
""".trimIndent()
                val rawOutput = StringBuilder()
                orchestrator.generate(
                    request = OrchestrationRequest(
                        prompt = PromptBuilder.formatRwkvG1eSingleTurn(sentimentTask),
                        settings = InferenceSettings(temperature = 0.3f, maxTokens = LlmDefaults.MAX_NEW_TOKENS_CAP),
                        toolId = "audio_analyze"
                    ),
                    onToken = { token: String ->
                        synchronized(rawOutput) { rawOutput.append(token) }
                    },
                    onPhaseChange = null
                )
                val rawText = rawOutput.toString()
                val parsed = AudioSentimentResult.parseOrNull(rawText)
                _ui.value = _ui.value.copy(phase = AudioAnalysisPhase.Done, analysis = parsed, rawAnalysisText = if (parsed == null) rawText else null)
            } finally {
                pocketApp.endInference()
            }
        }
    }
}
