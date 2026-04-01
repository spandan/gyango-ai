package ai.pocket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.pocket.audio.AudioAnalysisPhase
import ai.pocket.audio.AudioAnalysisUiState
import ai.pocket.audio.JsonPretty
import ai.pocket.audio.TranscriptionStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeAudioScreen(
    state: AudioAnalysisUiState,
    onBack: () -> Unit,
    onPickAudio: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAnalyze: () -> Unit,
    onClear: () -> Unit
) {
    val busy = state.phase == AudioAnalysisPhase.Transcribing ||
        state.phase == AudioAnalysisPhase.Analyzing
    val canAnalyze = state.selectedLabel != null &&
        !state.isRecording &&
        !busy
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Record or choose a file, then analyze. Transcription and summary run on device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.isRecording) {
                    Button(
                        onClick = onStopRecording,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop")
                    }
                } else {
                    OutlinedButton(
                        onClick = onStartRecording,
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Record")
                    }
                }
                OutlinedButton(
                    onClick = onPickAudio,
                    enabled = !state.isRecording && !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.selectedLabel == null) "File" else "Change")
                }
            }
            Button(
                onClick = onAnalyze,
                enabled = canAnalyze,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Analyze")
            }
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear")
            }
            when (state.phase) {
                AudioAnalysisPhase.Transcribing -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val stepLabel = when (state.transcriptionStep) {
                            TranscriptionStep.PreparingSpeechModel ->
                                "Preparing Whisper tiny model (first run copies from assets)…"
                            TranscriptionStep.DecodingAudio ->
                                "Decoding audio to 16 kHz…"
                            TranscriptionStep.RunningWhisper ->
                                "Transcribing with Whisper tiny (on-device)…"
                            TranscriptionStep.None ->
                                "Preparing…"
                        }
                        Text(stepLabel, style = MaterialTheme.typography.labelLarge)
                        if (state.transcriptionStep == TranscriptionStep.PreparingSpeechModel) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add models/ggml-tiny-q8_0.bin under app assets (see models/README.txt).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                AudioAnalysisPhase.Analyzing -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Analyzing with on-device model…", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator()
                    }
                }
                else -> { }
            }
            state.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            if (state.speechModelMissing) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = "Whisper model missing: place ggml-tiny-q8_0.bin in app/src/main/assets/models/ (Hugging Face ggml-org/whisper.cpp).",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            state.transcript?.let { t ->
                ResultSection(title = "Transcript (Whisper tiny)") {
                    Text(t, style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.analysis?.let { a ->
                ResultSection(title = "Sentiment & insights") {
                    StatLine("Overall", a.sentimentOverall.ifBlank { "—" })
                    StatLine("Score", String.format("%.2f", a.sentimentScore))
                    StatLine("Suggested tone", a.suggestedTone.ifBlank { "—" })
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Summary", fontWeight = FontWeight.SemiBold)
                    Text(a.summary.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
                    if (a.keyPhrases.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Key phrases", fontWeight = FontWeight.SemiBold)
                        Text(a.keyPhrases.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (a.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Notes", fontWeight = FontWeight.SemiBold)
                        Text(a.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.rawAnalysisText?.let { raw ->
                ResultSection(title = "Model output (unparsed)") {
                    Text(
                        JsonPretty.formatForDisplay(raw),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
