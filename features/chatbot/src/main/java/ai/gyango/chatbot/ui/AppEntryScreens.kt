package ai.gyango.chatbot.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.gyango.core.InferenceSettings
import java.util.Locale
import ai.gyango.core.hardware.ModelHardwareSupport
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
private fun rememberEntryLogo(): ImageBitmap? {
    val context = LocalContext.current
    return remember(context) {
        try {
            context.assets.open("images/gyangoAI.webp").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) { null }
    }
}

@Composable
fun OnboardingWelcomeScreen(
    strings: ChatDisplayStrings,
    onContinue: () -> Unit,
) {
    val logo = rememberEntryLogo()
    val appVersion = rememberAppVersionInfo()
    val versionFooter = appVersion?.let { info ->
        String.format(
            Locale.ROOT,
            strings.settingsVersionFormat,
            info.versionName.ifEmpty { "—" },
            info.versionCode.toString(),
        )
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .displayCutoutPadding()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(bottom = if (versionFooter != null) 56.dp else 0.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (logo != null) {
                        Image(
                            bitmap = logo,
                            contentDescription = strings.topBarTitle,
                        )
                    } else {
                        Text(
                            text = strings.topBarTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    text = strings.topBarCaption,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.onboardingIntroTitle,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = strings.onboardingIntroBody,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    shape = EntryScreenFieldShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(strings.onboardingIntroContinue)
                }
            }
            if (versionFooter != null) {
                Text(
                    text = versionFooter,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * First-run profile: given name, family name, and conversation language. Shown after the device
 * passes the GPU/NPU gate and before PAD download (if applicable) or chat.
 *
 * @param onProfileDraft Called after typing pauses so names and language persist before "Continue".
 */
@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileOnboardingScreen(
    settings: InferenceSettings,
    strings: ChatDisplayStrings,
    onProfileDraft: (InferenceSettings) -> Unit = {},
    onComplete: (InferenceSettings) -> Unit,
    statusHint: String? = null,
    onReadAloudYesSelected: (preferredLocaleTag: String) -> Unit = {},
    /**
     * When true, this screen is placed under [OnboardingStepShell]: no outer [Surface], no duplicate
     * welcome title block, no bottom disclaimer (shell provides header / optional footer).
     */
    embeddedInOnboardingShell: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var first by remember(settings.userFirstName) { mutableStateOf(settings.userFirstName) }
    var last by remember(settings.userLastName) { mutableStateOf(settings.userLastName) }
    var email by remember(settings.userEmail) { mutableStateOf(settings.userEmail) }
    var birthMonth by remember(settings.birthMonth) { mutableStateOf(settings.birthMonth) }
    var birthYear by remember(settings.birthYear) { mutableStateOf(settings.birthYear) }
    var speechLocaleTag by remember(settings.speechInputLocaleTag) { mutableStateOf(settings.speechInputLocaleTag) }
    var assistantSpeechEnabled by remember(settings.assistantSpeechEnabled) {
        mutableStateOf(settings.assistantSpeechEnabled)
    }
    LaunchedEffect(settings.speechInputLocaleTag) {
        speechLocaleTag = settings.speechInputLocaleTag
    }
    LaunchedEffect(settings.birthMonth) {
        birthMonth = settings.birthMonth
    }
    LaunchedEffect(settings.birthYear) {
        birthYear = settings.birthYear
    }
    LaunchedEffect(settings.userEmail) {
        email = settings.userEmail
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            listOf(
                first,
                last,
                email,
                speechLocaleTag,
                birthMonth?.toString().orEmpty(),
                birthYear?.toString().orEmpty(),
                assistantSpeechEnabled.toString(),
            )
        }
            .debounce(450L)
            .distinctUntilChanged()
            .collect {
                onProfileDraft(
                    settings.copy(
                        userFirstName = first.trim(),
                        userLastName = last.trim(),
                        userEmail = email.trim(),
                        speechInputLocaleTag = speechLocaleTag,
                        birthMonth = birthMonth,
                        birthYear = birthYear,
                        assistantSpeechEnabled = assistantSpeechEnabled,
                        voiceOnboardingComplete = false,
                        pinSetupComplete = settings.pinSetupComplete,
                        profileOnboardingSubmitted = settings.profileOnboardingSubmitted,
                    ),
                )
            }
    }

    @Composable
    fun ProfileFormColumn(scrollModifier: Modifier, showWelcomeHeader: Boolean) {
        Column(
            modifier = scrollModifier,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (showWelcomeHeader) {
                Text(
                    text = strings.onboardingWelcomeTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = strings.onboardingProgressSavedHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                )
            }
            statusHint?.let { hint ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = EntryScreenCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = hint,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = EntryScreenCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = first,
                        onValueChange = { first = it },
                        label = { Text(strings.onboardingFirstNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = EntryScreenFieldShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        ),
                    )
                    OutlinedTextField(
                        value = last,
                        onValueChange = { last = it },
                        label = { Text(strings.onboardingLastNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = EntryScreenFieldShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        ),
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(strings.profileEmailLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = EntryScreenFieldShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        ),
                    )
                    Text(
                        text = strings.onboardingBirthOptionalSection,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    BirthMonthYearFields(
                        month = birthMonth,
                        onMonthChange = { birthMonth = it },
                        year = birthYear,
                        onYearChange = { birthYear = it },
                        monthSectionLabel = {},
                        monthDropdownLabel = strings.onboardingBirthMonthLabel,
                        yearDropdownLabel = strings.onboardingBirthYearLabel,
                        notSetLabel = strings.onboardingBirthMonthNotSet,
                    )
                    Text(
                        text = strings.onboardingLanguageSection,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    var languageMenuExpanded by remember { mutableStateOf(false) }
                    val selectedLanguageLabel = SpeechInputLocales.OPTIONS
                        .find { (tag, _) -> tag == speechLocaleTag }
                        ?.second
                        ?: speechLocaleTag
                    ExposedDropdownMenuBox(
                        expanded = languageMenuExpanded,
                        onExpandedChange = { languageMenuExpanded = !languageMenuExpanded },
                    ) {
                        OutlinedTextField(
                            value = selectedLanguageLabel,
                            onValueChange = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            readOnly = true,
                            singleLine = true,
                            label = { Text(strings.onboardingLanguageSection) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded)
                            },
                            shape = EntryScreenFieldShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            ),
                        )
                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false },
                        ) {
                            SpeechInputLocales.OPTIONS.forEach { (tag, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        speechLocaleTag = tag
                                        languageMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        text = strings.onboardingReadAloudSection,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = !assistantSpeechEnabled,
                            onClick = { assistantSpeechEnabled = false },
                            label = { Text(strings.onboardingReadAloudNoLabel) },
                            shape = EntryScreenChipShape,
                        )
                        FilterChip(
                            selected = assistantSpeechEnabled,
                            onClick = {
                                assistantSpeechEnabled = true
                                onReadAloudYesSelected(speechLocaleTag)
                            },
                            label = { Text(strings.onboardingReadAloudYesLabel) },
                            shape = EntryScreenChipShape,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            val canContinue = first.trim().isNotEmpty() && last.trim().isNotEmpty()
            Button(
                onClick = {
                    onComplete(
                        settings.copy(
                            userFirstName = first.trim(),
                            userLastName = last.trim(),
                            userEmail = email.trim(),
                            speechInputLocaleTag = speechLocaleTag,
                            birthMonth = birthMonth,
                            birthYear = birthYear,
                            assistantSpeechEnabled = assistantSpeechEnabled,
                            voiceOnboardingComplete = false,
                            pinSetupComplete = false,
                            profileOnboardingSubmitted = true,
                        ),
                    )
                },
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth(),
                shape = EntryScreenFieldShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(strings.onboardingContinue)
            }
        }
    }

    if (embeddedInOnboardingShell) {
        ProfileFormColumn(
            scrollModifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp),
            showWelcomeHeader = false,
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ProfileFormColumn(
                    scrollModifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .displayCutoutPadding()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 24.dp, bottom = 20.dp),
                    showWelcomeHeader = true,
                )
                AiContentDisclaimerBanner(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun TtsLanguageSetupScreen(
    strings: ChatDisplayStrings,
    onInstallLanguagePack: () -> Unit,
    onContinueWithoutReadAloud: () -> Unit,
    statusMessage: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
            Text(
                text = strings.ttsSetupTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = EntryScreenCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = strings.ttsSetupBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!statusMessage.isNullOrBlank()) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Button(
                onClick = onInstallLanguagePack,
                modifier = Modifier.fillMaxWidth(),
                shape = EntryScreenFieldShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(strings.ttsSetupInstallButton)
            }
            Button(
                onClick = onContinueWithoutReadAloud,
                modifier = Modifier.fillMaxWidth(),
                shape = EntryScreenFieldShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(strings.ttsSetupSkipButton)
            }
            }
            AiContentDisclaimerBanner(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Blocking screen when the device cannot run the on-device model (before profile and PAD). */
@Composable
fun ModelHardwareUnsupportedScreen(
    strings: ChatDisplayStrings,
    support: ModelHardwareSupport,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
            Text(
                text = strings.onboardingHardwareUnsupportedTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = EntryScreenCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = strings.onboardingHardwareUnsupportedBody,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = EntryScreenCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = strings.onboardingHardwareUnsupportedPerformanceNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
            Text(
                text = strings.onboardingHardwareChecklistTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (!support.hasGpu && !support.hasNpu) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = EntryScreenCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = strings.onboardingHardwareNoAccelCard,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
            if (!support.hasEnoughRam) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = EntryScreenCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = strings.onboardingHardwareLowRamCard,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
            if (!support.hasEnoughFreeDisk) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = EntryScreenCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = strings.onboardingHardwareLowDiskCard,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
            }
            AiContentDisclaimerBanner(modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Fixed header (title + optional subtitle + divider) with a weighted body area for onboarding steps.
 */
@Composable
fun OnboardingStepShell(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                content(Modifier.fillMaxSize())
            }
            bottomBar()
        }
    }
}
