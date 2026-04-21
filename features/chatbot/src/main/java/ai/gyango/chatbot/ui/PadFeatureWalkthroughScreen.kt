package ai.gyango.chatbot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class WalkthroughShot {
    Toolbar,
    UserSettings,
    ChatSettings,
    ComposerAndMic,
    SubjectTiles,
}

private data class WalkthroughSlide(
    val title: String,
    val body: String,
    val shot: WalkthroughShot,
)

private data class WalkthroughCopy(
    val heading: String,
    val subheading: String,
    val next: String,
    val back: String,
    val skip: String,
    val done: String,
    val slides: List<WalkthroughSlide>,
)

private fun walkthroughCopy(localeTag: String): WalkthroughCopy = when {
    localeTag.startsWith("te", ignoreCase = true) -> WalkthroughCopy(
        heading = "డౌన్‌లోడ్ జరుగుతుండగా చిన్న టూర్",
        subheading = "మోడల్ బ్యాక్‌గ్రౌండ్‌లో సిద్ధమవుతోంది. అప్పటివరకు ఈ ఫీచర్‌లను చూసేయండి.",
        next = "తర్వాత",
        back = "వెనక్కి",
        skip = "దాటవేయి",
        done = "చాట్‌కు సిద్ధం",
        slides = listOf(
            WalkthroughSlide(
                title = "చాట్ టాప్ బార్",
                body = "మైక్‌తో మాట్లాడవచ్చు, చెత్తకుండి ఐకాన్‌తో క్లియర్ చాట్ చేయవచ్చు, సెట్టింగ్స్/ఫీడ్‌బ్యాక్ ఇక్కడే ఉంటాయి.",
                shot = WalkthroughShot.Toolbar,
            ),
            WalkthroughSlide(
                title = "యూజర్ సెట్టింగ్స్",
                body = "పేరు, భాష, చదివి వినిపించాలా అనే ఎంపికలను ప్రొఫైల్ సెక్షన్‌లో ఎప్పుడైనా మార్చవచ్చు.",
                shot = WalkthroughShot.UserSettings,
            ),
            WalkthroughSlide(
                title = "చాట్ సెట్టింగ్స్",
                body = "జవాబు పొడవు, క్రియేటివిటీ, మరియు ఇతర మోడల్ సెట్టింగ్స్‌ను మీకు నచ్చినట్టు ట్యూన్ చేయండి.",
                shot = WalkthroughShot.ChatSettings,
            ),
            WalkthroughSlide(
                title = "మెసేజ్ & మైక్",
                body = "టైప్ చేయండి లేదా మైక్ నొక్కి మాట్లాడండి. + బటన్‌తో చిత్రం జోడించి OCR సహాయం పొందవచ్చు.",
                shot = WalkthroughShot.ComposerAndMic,
            ),
            WalkthroughSlide(
                title = "సబ్జెక్ట్ చిప్స్",
                body = "Math, Science, Coding వంటి చిప్స్‌తో టాపిక్ ఫోకస్ మారుతుంది — మీ లెర్నింగ్ ఫ్లో మరింత స్పష్టంగా ఉంటుంది.",
                shot = WalkthroughShot.SubjectTiles,
            ),
        ),
    )
    localeTag.startsWith("hi", ignoreCase = true) -> WalkthroughCopy(
        heading = "डाउनलोड के दौरान छोटा टूर",
        subheading = "मॉडल बैकग्राउंड में तैयार हो रहा है। तब तक इन फीचर्स को देख लें।",
        next = "आगे",
        back = "पीछे",
        skip = "छोड़ें",
        done = "चैट के लिए तैयार",
        slides = listOf(
            WalkthroughSlide(
                title = "चैट टॉप बार",
                body = "माइक से बोलें, डिलीट आइकन से चैट साफ़ करें, और सेटिंग्स/फीडबैक यहीं मिलेंगे।",
                shot = WalkthroughShot.Toolbar,
            ),
            WalkthroughSlide(
                title = "यूज़र सेटिंग्स",
                body = "नाम, भाषा, और read-aloud जैसी प्रोफ़ाइल सेटिंग्स कभी भी बदल सकते हैं।",
                shot = WalkthroughShot.UserSettings,
            ),
            WalkthroughSlide(
                title = "चैट सेटिंग्स",
                body = "जवाब की लंबाई, creativity और अन्य मॉडल विकल्प अपनी पसंद के अनुसार सेट करें।",
                shot = WalkthroughShot.ChatSettings,
            ),
            WalkthroughSlide(
                title = "मैसेज और माइक",
                body = "टाइप करें या माइक से बोलें। + बटन से इमेज जोड़कर OCR मदद ले सकते हैं।",
                shot = WalkthroughShot.ComposerAndMic,
            ),
            WalkthroughSlide(
                title = "सब्जेक्ट चिप्स",
                body = "Math, Science, Coding जैसे चिप्स से जवाब का फोकस बदलता है और सीखना अधिक स्पष्ट होता है।",
                shot = WalkthroughShot.SubjectTiles,
            ),
        ),
    )
    else -> WalkthroughCopy(
        heading = "Quick app tour while downloading",
        subheading = "The model is preparing in the background. Explore key features while you wait.",
        next = "Next",
        back = "Back",
        skip = "Skip",
        done = "Go to chat",
        slides = listOf(
            WalkthroughSlide(
                title = "Chat top bar",
                body = "Use the microphone, clear chat with the delete icon, and open settings or feedback from here.",
                shot = WalkthroughShot.Toolbar,
            ),
            WalkthroughSlide(
                title = "User settings",
                body = "Update your profile, language, and read-aloud preferences anytime in User Settings.",
                shot = WalkthroughShot.UserSettings,
            ),
            WalkthroughSlide(
                title = "Chat settings",
                body = "Tune response length, creativity, and model behavior to match your learning style.",
                shot = WalkthroughShot.ChatSettings,
            ),
            WalkthroughSlide(
                title = "Message + microphone",
                body = "Type or speak. Use the + button to attach an image and get OCR-assisted context.",
                shot = WalkthroughShot.ComposerAndMic,
            ),
            WalkthroughSlide(
                title = "Subject chips",
                body = "Switch focus quickly with topic chips like Math, Science, and Coding.",
                shot = WalkthroughShot.SubjectTiles,
            ),
        ),
    )
}

@Composable
private fun ShotAction(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WalkthroughScreenshotCard(shot: WalkthroughShot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "GyanGo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }

            when (shot) {
                WalkthroughShot.Toolbar -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        ShotAction(Icons.Default.Mic, "Mic")
                        ShotAction(Icons.Default.Delete, "Clear")
                        ShotAction(Icons.Default.Settings, "Settings")
                        ShotAction(Icons.Default.Email, "Feedback")
                    }
                }
                WalkthroughShot.UserSettings -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Profile", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Name • Language • Read aloud", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ShotAction(Icons.Default.Person, "User")
                            ShotAction(Icons.Default.Settings, "Language")
                        }
                    }
                }
                WalkthroughShot.ChatSettings -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Chat & model", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Long answers • Creativity • Low power", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ShotAction(Icons.Default.Tune, "Tuning")
                    }
                }
                WalkthroughShot.ComposerAndMic -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        ShotAction(Icons.Default.Keyboard, "Type")
                        ShotAction(Icons.Default.Mic, "Speak")
                        ShotAction(Icons.Default.Add, "Image")
                    }
                }
                WalkthroughShot.SubjectTiles -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("Math", "Science", "Coding").forEach { chip ->
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(chip, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PadFeatureWalkthroughScreen(
    localeTag: String,
    statusHint: String? = null,
    onDone: () -> Unit,
    onSkip: () -> Unit,
) {
    val copy = remember(localeTag) { walkthroughCopy(localeTag) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val slide = copy.slides[index]
    val isLast = index == copy.slides.lastIndex

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = copy.heading,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSkip, modifier = Modifier.padding(start = 12.dp)) {
                    Text(copy.skip)
                }
            }

            Text(
                text = copy.subheading,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            statusHint?.let { hint ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            WalkthroughScreenshotCard(shot = slide.shot)

            Text(
                text = slide.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = slide.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(copy.slides.size) { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == index) 10.dp else 8.dp)
                            .background(
                                color = if (i == index) MaterialTheme.colorScheme.primary else Color.LightGray,
                                shape = CircleShape,
                            ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (index == 0) onSkip() else index -= 1
                    },
                    modifier = Modifier.weight(1f),
                    shape = EntryScreenFieldShape,
                ) {
                    Text(copy.back)
                }
                Button(
                    onClick = {
                        if (isLast) onDone() else index += 1
                    },
                    modifier = Modifier.weight(1f),
                    shape = EntryScreenFieldShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(if (isLast) copy.done else copy.next)
                }
            }
        }
    }
}
