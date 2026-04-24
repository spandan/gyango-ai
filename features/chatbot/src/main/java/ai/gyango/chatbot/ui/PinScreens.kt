package ai.gyango.chatbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.gyango.chatbot.security.AppPinStore
import ai.gyango.core.InferenceSettings

private const val PinMaxDigits = 6

private enum class PinUnlockMode {
    Unlock,
    RecoveryIdentity,
    RecoveryNewPin,
}

@Composable
private fun PinCenteredColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun PinMaskedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { proposed ->
            onValueChange(proposed.filter { it.isDigit() }.take(PinMaxDigits))
        },
        label = label,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.fillMaxWidth(),
        shape = EntryScreenFieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        ),
    )
}

@Composable
fun PinSetupScreen(
    settings: InferenceSettings,
    strings: ChatDisplayStrings,
    pinStore: AppPinStore,
    onPinSaved: (InferenceSettings) -> Unit,
    /** When true, [OnboardingStepShell] supplies title/body; this screen only shows fields. */
    embeddedInOnboardingShell: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val enterPinLabel = strings.pinSetupEnterLabel.ifBlank { strings.pinPinLabel }
    val reenterPinLabel = strings.pinSetupReenterLabel.ifBlank { strings.pinConfirmPinLabel }
    val savePinLabel = strings.pinSetupSaveButton.ifBlank { strings.pinContinue }

    @Composable
    fun PinFieldsColumn(columnModifier: Modifier) {
        Column(
            modifier = columnModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!embeddedInOnboardingShell) {
                Text(
                    text = strings.pinSetupTitle,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = strings.pinSetupBody,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PinCenteredColumn {
                PinMaskedTextField(
                    value = pin,
                    onValueChange = { pin = it; error = null },
                    label = { Text(enterPinLabel) },
                )
                PinMaskedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text(reenterPinLabel) },
                )
                error?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Button(
                onClick = {
                    if (pin.length !in 4..6 || confirm.length !in 4..6) {
                        error = strings.pinErrorFormat
                        return@Button
                    }
                    if (pin != confirm) {
                        error = strings.pinErrorMismatch
                        return@Button
                    }
                    if (!pinStore.savePin(pin, settings)) {
                        error = strings.pinErrorFormat
                        return@Button
                    }
                    onPinSaved(
                        settings.copy(
                            pinSetupComplete = true,
                            voiceOnboardingComplete = true,
                        ),
                    )
                },
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(),
                shape = EntryScreenFieldShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(savePinLabel)
            }
        }
    }

    if (embeddedInOnboardingShell) {
        PinFieldsColumn(
            columnModifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            PinFieldsColumn(
                columnModifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
fun PinUnlockScreen(
    strings: ChatDisplayStrings,
    pinStore: AppPinStore,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var modeName by rememberSaveable { mutableStateOf(PinUnlockMode.Unlock.name) }
    val mode = runCatching { PinUnlockMode.valueOf(modeName) }.getOrDefault(PinUnlockMode.Unlock)

    var pin by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf<String?>(null) }

    var lastName by remember { mutableStateOf("") }
    var birthMonth by remember { mutableStateOf<Int?>(null) }
    var birthYear by remember { mutableStateOf<Int?>(null) }
    var recoveryIdentityError by remember { mutableStateOf<String?>(null) }

    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var recoveryPinError by remember { mutableStateOf<String?>(null) }

    val newPinLabel = strings.pinSetupEnterLabel.ifBlank { strings.pinPinLabel }
    val reenterNewPinLabel = strings.pinSetupReenterLabel.ifBlank { strings.pinConfirmPinLabel }

    fun goUnlock() {
        modeName = PinUnlockMode.Unlock.name
        pin = ""
        newPin = ""
        confirmPin = ""
        unlockError = null
        recoveryIdentityError = null
        recoveryPinError = null
    }

    fun goRecoveryIdentity() {
        modeName = PinUnlockMode.RecoveryIdentity.name
        lastName = ""
        birthMonth = null
        birthYear = null
        recoveryIdentityError = null
    }

    fun goRecoveryNewPin() {
        modeName = PinUnlockMode.RecoveryNewPin.name
        newPin = ""
        confirmPin = ""
        recoveryPinError = null
    }

    // Opaque full-bleed layer: do not show underlying chat or other UI until the user unlocks (PIN privacy).
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .displayCutoutPadding()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = EntryScreenCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (mode) {
                            PinUnlockMode.Unlock -> {
                                Text(
                                    text = strings.pinLockTitle,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = strings.pinLockBody,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                PinMaskedTextField(
                                    value = pin,
                                    onValueChange = { pin = it; unlockError = null },
                                    label = { Text(strings.pinPinLabel) },
                                )
                                unlockError?.let {
                                    Text(
                                        text = it,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (!pinStore.verifyPin(pin)) {
                                            unlockError = strings.pinUnlockErrorWrongPin
                                            return@Button
                                        }
                                        pin = ""
                                        onUnlocked()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = EntryScreenFieldShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Text(strings.pinUnlockSubmit)
                                }
                                TextButton(
                                    onClick = { goRecoveryIdentity() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(strings.settingsPinResetTitle.ifBlank { strings.pinUnlockForgotResetButton })
                                }
                            }

                            PinUnlockMode.RecoveryIdentity -> {
                                Text(
                                    text = strings.pinResetTitle,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = strings.pinResetBody,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedTextField(
                                    value = lastName,
                                    onValueChange = { lastName = it; recoveryIdentityError = null },
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
                                Text(
                                    text = strings.onboardingBirthOptionalSection,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                BirthMonthYearFields(
                                    month = birthMonth,
                                    onMonthChange = { birthMonth = it; recoveryIdentityError = null },
                                    year = birthYear,
                                    onYearChange = { birthYear = it; recoveryIdentityError = null },
                                    monthSectionLabel = {},
                                    monthDropdownLabel = strings.onboardingBirthMonthLabel,
                                    yearDropdownLabel = strings.onboardingBirthYearLabel,
                                    notSetLabel = strings.onboardingBirthMonthNotSet,
                                )
                                recoveryIdentityError?.let {
                                    Text(
                                        text = it,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (lastName.isBlank()) {
                                            recoveryIdentityError = strings.pinResetIdentityError
                                            return@Button
                                        }
                                        if (!pinStore.verifyRecoveryIdentity(lastName, birthMonth, birthYear)) {
                                            recoveryIdentityError = strings.pinResetIdentityError
                                            return@Button
                                        }
                                        goRecoveryNewPin()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = EntryScreenFieldShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Text(strings.pinValidateProfileButton)
                                }
                                TextButton(onClick = { goUnlock() }, modifier = Modifier.fillMaxWidth()) {
                                    Text(strings.pinFlowBack)
                                }
                            }

                            PinUnlockMode.RecoveryNewPin -> {
                                Text(
                                    text = strings.pinRecoveryNewPinTitle,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = strings.pinRecoveryNewPinBody,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                PinMaskedTextField(
                                    value = newPin,
                                    onValueChange = { newPin = it; recoveryPinError = null },
                                    label = { Text(newPinLabel) },
                                )
                                PinMaskedTextField(
                                    value = confirmPin,
                                    onValueChange = { confirmPin = it; recoveryPinError = null },
                                    label = { Text(reenterNewPinLabel) },
                                )
                                recoveryPinError?.let {
                                    Text(
                                        text = it,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (newPin.length !in 4..6 || confirmPin.length !in 4..6) {
                                            recoveryPinError = strings.pinErrorFormat
                                            return@Button
                                        }
                                        if (newPin != confirmPin) {
                                            recoveryPinError = strings.pinErrorMismatch
                                            return@Button
                                        }
                                        if (!pinStore.verifyRecoveryAndSetNewPin(lastName, birthMonth, birthYear, newPin)) {
                                            recoveryPinError = strings.pinResetIdentityError
                                            return@Button
                                        }
                                        onUnlocked()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = EntryScreenFieldShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Text(strings.pinSetupSaveButton.ifBlank { strings.pinResetSubmit })
                                }
                                TextButton(
                                    onClick = { goRecoveryIdentity() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(strings.pinFlowBack)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Settings flow: verify current PIN, then enter and re-enter the new PIN on one screen and save.
 */
@Composable
fun PinChangeScreen(
    strings: ChatDisplayStrings,
    pinStore: AppPinStore,
    onChangeComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    var step by rememberSaveable("pin_change_two_step") { mutableIntStateOf(0) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val enterCurrentTitle = strings.pinChangeEnterCurrentTitle.ifBlank { strings.pinLockTitle }
    val enterCurrentBody = strings.pinChangeEnterCurrentBody.ifBlank { strings.pinLockBody }
    val newPinLabel = strings.pinSetupEnterLabel.ifBlank { strings.pinPinLabel }
    val reenterNewPinLabel = strings.pinSetupReenterLabel.ifBlank { strings.pinConfirmPinLabel }
    val saveLabel = strings.pinSetupSaveButton.ifBlank { strings.pinResetSubmit }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .displayCutoutPadding()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                0 -> {
                    Text(
                        text = enterCurrentTitle,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = enterCurrentBody,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PinCenteredColumn {
                        PinMaskedTextField(
                            value = currentPin,
                            onValueChange = { currentPin = it; error = null },
                            label = {
                                Text(
                                    strings.pinChangeEnterCurrentTitle.ifBlank { strings.pinPinLabel },
                                )
                            },
                        )
                    }
                }
                else -> {
                    Text(
                        text = strings.pinRecoveryNewPinTitle,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = strings.pinRecoveryNewPinBody,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PinCenteredColumn {
                        PinMaskedTextField(
                            value = newPin,
                            onValueChange = { newPin = it; error = null },
                            label = { Text(newPinLabel) },
                        )
                        PinMaskedTextField(
                            value = confirm,
                            onValueChange = { confirm = it; error = null },
                            label = { Text(reenterNewPinLabel) },
                        )
                    }
                }
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            when (step) {
                0 -> {
                    Button(
                        onClick = {
                            if (currentPin.length !in 4..6) {
                                error = strings.pinErrorFormat
                                return@Button
                            }
                            if (!pinStore.verifyPin(currentPin)) {
                                error = strings.pinChangeWrongCurrent.ifBlank { strings.pinUnlockErrorWrongPin }
                                return@Button
                            }
                            error = null
                            newPin = ""
                            confirm = ""
                            step = 1
                        },
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .fillMaxWidth(),
                        shape = EntryScreenFieldShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text(strings.pinContinue)
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            if (newPin.length !in 4..6 || confirm.length !in 4..6) {
                                error = strings.pinErrorFormat
                                return@Button
                            }
                            if (newPin != confirm) {
                                error = strings.pinErrorMismatch
                                return@Button
                            }
                            if (!pinStore.changePin(currentPin, newPin)) {
                                error = strings.pinChangeWrongCurrent.ifBlank { strings.pinUnlockErrorWrongPin }
                                return@Button
                            }
                            onChangeComplete()
                        },
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .fillMaxWidth(),
                        shape = EntryScreenFieldShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text(saveLabel)
                    }
                    TextButton(
                        onClick = {
                            step = 0
                            currentPin = ""
                            newPin = ""
                            confirm = ""
                            error = null
                        },
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .fillMaxWidth(),
                    ) {
                        Text(strings.pinFlowBack)
                    }
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(strings.settingsCancel)
            }
        }
    }
}
