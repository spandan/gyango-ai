package ai.gyango.chatbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.gyango.core.SubjectMode
import java.time.LocalDate

data class LearningCheckInQuestion(
    val prompt: String,
    val options: List<String>,
)

private data class LearningCheckInCopy(
    val promptTitle: String,
    val promptBody: String,
    val whatThisDoesTitle: String,
    val whatThisDoesBody: String,
    val laterHint: String,
    val startButton: String,
    val skipButton: String,
    val screenTitle: String,
    val screenBody: String,
    val subjectPrompt: String,
    val saveButton: String,
    val footer: String,
    val subjectChoices: List<Pair<String, SubjectMode>>,
)

private fun ageBandFromBirthYear(birthYear: Int?): Int {
    val year = birthYear ?: return 2
    val age = (LocalDate.now().year - year).coerceAtLeast(0)
    return when {
        age <= 7 -> 0
        age <= 10 -> 1
        age <= 13 -> 2
        else -> 3
    }
}

fun learningCheckInQuestionsForAge(birthYear: Int?, localeTag: String): List<LearningCheckInQuestion> {
    val band = ageBandFromBirthYear(birthYear)
    return (when {
        localeTag.startsWith("te", ignoreCase = true) -> when (band) {
            0 -> listOf(
                LearningCheckInQuestion("మీరు కొత్త విషయం నేర్చుకునేప్పుడు ఏది బాగా నచ్చుతుంది?", listOf("ఒక చిత్రం చూపించండి", "ఒక చిన్న కథ చెప్పండి", "సులభమైన దశలు ఇవ్వండి", "చిన్న సవాలు ప్రయత్నించనివ్వండి")),
                LearningCheckInQuestion("ఏదైనా కాస్త కష్టం అనిపిస్తే GyanGo ఏమి చేయాలి?", listOf("చాలా సులభంగా చేయాలి", "మజాగా ఒక ఉదాహరణ ఇవ్వాలి", "దశలుగా విడగొట్టాలి", "ఒక చిన్న పజిల్ ఇవ్వాలి")),
                LearningCheckInQuestion("ఈరోజు ఏ విషయాలు ఆసక్తిగా అనిపిస్తున్నాయి?", listOf("జంతువులు మరియు ప్రకృతి", "అంతరిక్షం మరియు విజ్ఞానం", "సంఖ్యలు మరియు నమూనాలు", "చేయడం మరియు కట్టడం")),
                LearningCheckInQuestion("జవాబులు ఎంత పొడవుగా ఉండాలి?", listOf("చాలా చిన్నగా", "చిన్నగా, సరదాగా", "కొంచెం ఎక్కువగా", "ఇంకాస్త వివరంగా")),
                LearningCheckInQuestion("మీకు త్వరగా అర్థమయ్యేందుకు ఏది సహాయపడుతుంది?", listOf("ఒక్కో ఆలోచనగా", "నిజజీవిత ఉదాహరణ", "ఎలా చేయాలో జాబితా", "ఒక సవాలు ప్రశ్న")),
                LearningCheckInQuestion("మీరు 'ఎందుకు' అని అడిగితే తర్వాత ఏమి కావాలి?", listOf("చిన్న జవాబు", "సరదా కారణం", "దశల వారీ కారణం", "ఇంకా లోతైన కారణం")),
                LearningCheckInQuestion("మీకు ఎక్కువగా నచ్చే శైలి ఏది?", listOf("స్నేహపూర్వకంగా, సులభంగా", "ఆసక్తికరంగా, ఆటపాటగా", "స్పష్టంగా, దశల వారీగా", "తెలివిగా, సవాలుగా")),
                LearningCheckInQuestion("మీరు ఆగిపోతే నేను ఏమి చెప్పాలి?", listOf("పర్లేదు, సులభంగా కొనసాగిద్దాం", "ఒక సరదా ఉదాహరణ చూద్దాం", "దశల వారీగా చేద్దాం", "మీరు కాస్త కఠినమైన సూచనకు సిద్ధంగా ఉన్నారు")),
            )
            1 -> listOf(
                LearningCheckInQuestion("మీరు ప్రశ్న అడిగితే ముందుగా ఎలాంటి జవాబు నచ్చుతుంది?", listOf("చిన్నగా, సులభంగా", "ఒక సరదా ఉదాహరణ", "దశల వారీగా", "కొంచెం పెద్ద సవాలు")),
                LearningCheckInQuestion("మీరు బాగా నేర్చుకోవడానికి ఏది సహాయపడుతుంది?", listOf("సులభమైన పదాలు", "కథలు మరియు ఉదాహరణలు", "స్పష్టమైన దశలు", "పరిష్కరించాల్సిన కాస్త కఠిన ప్రశ్నలు")),
                LearningCheckInQuestion("ఏదైనా అర్థం కాకపోతే ఏమి జరగాలి?", listOf("ఇంకా సులభం చేయాలి", "కొత్త ఉదాహరణ ఇవ్వాలి", "చిన్న భాగాలుగా చేయాలి", "ఒక సవాలు సూచన ఇవ్వాలి")),
                LearningCheckInQuestion("ఇవాటిలో మీలాగా అనిపించేది ఏది?", listOf("నాకు సులభమైన వివరణలు ఇష్టం", "నాకు ఎందుకు అనేది తెలుసుకోవడం ఇష్టం", "నాకు సమస్యలు పరిష్కరించడం ఇష్టం", "నాకు పెద్ద సవాళ్లు ఇష్టం")),
                LearningCheckInQuestion("జవాబుల్లో ఎంత వివరాలు ఉండాలి?", listOf("ముఖ్య విషయం మాత్రమే", "కొంచెం ఎక్కువ", "అభ్యాసానికి సరిపడా", "ఇంకా లోతుగా")),
                LearningCheckInQuestion("ఏ విషయాలు మిమ్మల్ని ఎక్కువగా ఆకర్షిస్తాయి?", listOf("సాధారణ ఆశ్చర్య ప్రశ్నలు", "విజ్ఞానం మరియు ప్రకృతి", "గణితం మరియు నమూనాలు", "కోడింగ్ మరియు తయారీ")),
                LearningCheckInQuestion("GyanGo బోధించేప్పుడు ఏమి చేయాలి?", listOf("శాంతంగా, చిన్నగా ఉంచాలి", "నాకు తెలిసిన ఉదాహరణలు ఇవ్వాలి", "దశలలో నడిపించాలి", "కొంచెం ముందుకు నెట్టాలి")),
                LearningCheckInQuestion("జవాబు పూర్తయ్యాక తర్వాత ఏమి కావాలి?", listOf("అక్కడే ఆపు", "ఒక సరదా విషయం", "ఇంకో ఉదాహరణ", "ఒక సవాలు ప్రశ్న")),
            )
            2 -> listOf(
                LearningCheckInQuestion("సాధారణంగా మీకు ఏ రకమైన వివరణ ఎక్కువగా సహాయపడుతుంది?", listOf("చాలా స్పష్టమైన బేసిక్స్", "ఆలోచనలను కలిపే ఉదాహరణలు", "దశల వారీ తర్కం", "కొంచెం ఎక్కువ సవాలు")),
                LearningCheckInQuestion("మీరు 'ఎందుకు' అని అడిగితే నిజంగా ఏమి కావాలి?", listOf("సులభమైన కారణం", "నిజజీవిత అనుసంధానం", "దశలలో తర్కం", "లోతుగా ఆలోచించే ఆలోచన")),
                LearningCheckInQuestion("మీరు ఇరుక్కుపోతే GyanGo ఎలా స్పందించాలి?", listOf("వెంటనే సులభం చేయాలి", "వేరే ఉదాహరణ ప్రయత్నించాలి", "ఇంకా జాగ్రత్తగా విడదీయాలి", "ఇంకాస్త బలమైన సూచన ఇవ్వాలి")),
                LearningCheckInQuestion("ఎంత సవాలు బాగుంటుంది?", listOf("సులభంగా ఉంచండి", "చాలావరకు సౌకర్యంగా", "కొంచెం స్ట్రెచ్", "ఇంకా ముందుకు నెట్టండి")),
                LearningCheckInQuestion("ఇప్పుడు ఏ విభాగం ఎక్కువగా సరదాగా అనిపిస్తుంది?", listOf("ఆసక్తి ప్రశ్నలు", "విజ్ఞానం", "గణితం", "కోడింగ్ / నిర్మాణం")),
                LearningCheckInQuestion("చాలా జవాబులు ఎంత పొడవుగా ఉండాలి?", listOf("త్వరగా", "ఒక ఉదాహరణతో చిన్నగా", "దశలతో మధ్యస్థంగా", "లోతుతో కొంచెం పొడవుగా")),
                LearningCheckInQuestion("ఏ శైలి మీకు బాగా సరిపోతుంది?", listOf("మృదువుగా, సులభంగా", "ఆసక్తిగా, ఆలోచనాత్మకంగా", "క్రమబద్ధంగా, ఉపయోగకరంగా", "నా ఆలోచనను మరింత పెంచాలి")),
                LearningCheckInQuestion("ఒక మంచి జవాబు చివర ఏమి జరగాలి?", listOf("సులభంగా ముగించాలి", "ఒక చక్కని విషయం జోడించాలి", "చిన్న తదుపరి దశ ఇవ్వాలి", "ఒక సవాలు ప్రయత్నించమని చెప్పాలి")),
            )
            else -> listOf(
                LearningCheckInQuestion("కొత్త విషయం నేర్చుకునేప్పుడు ఏది ఎక్కువ సహాయపడుతుంది?", listOf("సులభమైన అవలోకనం", "స్పష్టమైన ఉదాహరణలు", "దశలలో తర్కం", "లోతైన సవాలు")),
                LearningCheckInQuestion("GyanGo ఏ విషయంలో మెరుగుపడాలని మీరు కోరుకుంటారు?", listOf("స్పష్టత", "ఆసక్తి", "సమస్య పరిష్కారం", "ఆలోచనను విస్తరించడం")),
                LearningCheckInQuestion("ఒక విషయం కష్టంగా మారితే ఏమి జరగాలి?", listOf("సులభం చేయాలి", "సంబంధిత ఉదాహరణ ఇవ్వాలి", "తర్కాన్ని విడగొట్టాలి", "సవాలును ఉంచి నడిపించాలి")),
                LearningCheckInQuestion("చాలా జవాబుల్లో ఎంత వివరాలు ఉండాలి?", listOf("అత్యవసరమైనవి మాత్రమే", "అత్యవసరమైనవి + ఉదాహరణలు", "అభ్యాసానికి సరిపడా", "ఇంకా లోతు మరియు విస్తరణ")),
                LearningCheckInQuestion("ఇవాటిలో మీలాగా అనిపించేది ఏది?", listOf("అన్వేషకుడు", "ఆలోచనాపరుడు", "నిర్మాత", "ఇంకాస్త సవాలుతో కలిపి")),
                LearningCheckInQuestion("ముందుగా ఏ విషయం వైపు వెళ్లాలని అనిపిస్తుంది?", listOf("సాధారణ ఆసక్తి", "విజ్ఞానం / ప్రకృతి", "గణితం / నమూనాలు", "కోడింగ్ / సృష్టి")),
                LearningCheckInQuestion("చివర ఏది ఎక్కువ ఉపయోగపడుతుంది?", listOf("చిన్న ముగింపు", "ఒక మంచి ఉదాహరణ", "తదుపరి అభ్యాస దశ", "ఒక స్ట్రెచ్ ప్రశ్న")),
                LearningCheckInQuestion("GyanGo కి ఏ శైలి బాగుంటుంది?", listOf("శాంతంగా, సులభంగా", "ఆసక్తిగా, లోతుగా", "స్పష్టంగా, ఆచరణాత్మకంగా", "ముక్కుసూటిగా, సవాలుగా")),
            )
        }
        localeTag.startsWith("hi", ignoreCase = true) -> when (band) {
            0 -> listOf(
                LearningCheckInQuestion("जब आप कुछ नया सीखते हैं, तो क्या सबसे अच्छा लगता है?", listOf("मुझे एक चित्र दिखाइए", "एक छोटी कहानी सुनाइए", "आसान कदम बताइए", "मुझे एक छोटा सा चैलेंज दीजिए")),
                LearningCheckInQuestion("अगर कुछ थोड़ा मुश्किल लगे, तो GyanGo क्या करे?", listOf("बहुत आसान बना दे", "मज़ेदार उदाहरण दे", "कदमों में तोड़ दे", "एक छोटा पज़ल दे")),
                LearningCheckInQuestion("आज कौन से विषय सबसे मज़ेदार लगते हैं?", listOf("जानवर और प्रकृति", "अंतरिक्ष और विज्ञान", "संख्याएँ और पैटर्न", "बनाना और रचना")),
                LearningCheckInQuestion("जवाब कितने लंबे हों?", listOf("बहुत छोटे", "छोटे और मज़ेदार", "थोड़ा और", "ज़्यादा विस्तार से")),
                LearningCheckInQuestion("आपको जल्दी समझने में क्या सबसे ज़्यादा मदद करता है?", listOf("एक बार में एक विचार", "असल जीवन का उदाहरण", "कैसे करें वाली सूची", "एक चैलेंज सवाल")),
                LearningCheckInQuestion("जब आप पूछते हैं कि कुछ क्यों होता है, तो आगे क्या चाहिए?", listOf("एक छोटा जवाब", "एक मज़ेदार कारण", "कदम-दर-कदम कारण", "थोड़ा और गहरा कारण")),
                LearningCheckInQuestion("आपको कौन-सी शैली सबसे ज़्यादा पसंद है?", listOf("दोस्ताना और आसान", "जिज्ञासु और खेल-खेल में", "साफ और कदम-दर-कदम", "स्मार्ट और चुनौतीपूर्ण")),
                LearningCheckInQuestion("अगर आप अटक जाएँ, तो मैं क्या कहूँ?", listOf("कोई बात नहीं, आसान रखते हैं", "चलो मज़ेदार उदाहरण लेते हैं", "चलो इसे कदम-दर-कदम हल करते हैं", "आप थोड़े कठिन संकेत के लिए तैयार हैं")),
            )
            1 -> listOf(
                LearningCheckInQuestion("जब आप कोई सवाल पूछते हैं, तो पहले किस तरह का जवाब पसंद है?", listOf("छोटा और आसान", "एक मज़ेदार उदाहरण", "कदम-दर-कदम", "थोड़ा बड़ा चैलेंज")),
                LearningCheckInQuestion("आपको सबसे अच्छा सीखने में क्या मदद करता है?", listOf("आसान शब्द", "कहानियाँ और उदाहरण", "साफ कदम", "हल करने वाले मुश्किल सवाल")),
                LearningCheckInQuestion("अगर कुछ समझ में न आए, तो क्या होना चाहिए?", listOf("उसे आसान बना दें", "नया उदाहरण दें", "छोटे हिस्सों में बाँट दें", "एक चैलेंज हिंट दें")),
                LearningCheckInQuestion("इनमें से कौन-सी बात आप जैसी लगती है?", listOf("मुझे आसान समझाइयाँ पसंद हैं", "मुझे 'क्यों' जानना पसंद है", "मुझे चीज़ें हल करना पसंद है", "मुझे बड़े चैलेंज पसंद हैं")),
                LearningCheckInQuestion("जवाबों में कितनी जानकारी होनी चाहिए?", listOf("बस मुख्य बात", "थोड़ी और", "अभ्यास के लिए जितनी चाहिए", "और गहराई")),
                LearningCheckInQuestion("कौन से विषय आपको सबसे ज़्यादा खींचते हैं?", listOf("सामान्य जिज्ञासा वाले सवाल", "विज्ञान और प्रकृति", "गणित और पैटर्न", "कोडिंग और बनाना")),
                LearningCheckInQuestion("जब GyanGo पढ़ाए, तो क्या करे?", listOf("शांत और छोटा रखे", "मेरे जाने-पहचाने उदाहरण दे", "मुझे कदमों में गाइड करे", "मुझे थोड़ा आगे बढ़ाए")),
                LearningCheckInQuestion("जवाब खत्म होने पर आगे क्या चाहिए?", listOf("वहीं रुक जाए", "एक मज़ेदार तथ्य", "एक और उदाहरण", "एक चैलेंज सवाल")),
            )
            2 -> listOf(
                LearningCheckInQuestion("आम तौर पर किस तरह की समझ आपको सबसे ज़्यादा मदद करती है?", listOf("बहुत साफ बुनियाद", "विचारों को जोड़ने वाले उदाहरण", "कदम-दर-कदम तर्क", "मुझे थोड़ा और चुनौती दें")),
                LearningCheckInQuestion("जब आप 'क्यों' पूछते हैं, तो आपको सच में क्या चाहिए?", listOf("एक आसान कारण", "असल जीवन से जुड़ाव", "तर्क के कदम", "सोचने के लिए एक गहरा विचार")),
                LearningCheckInQuestion("अगर आप अटक जाएँ, तो GyanGo कैसे जवाब दे?", listOf("तुरंत आसान बना दे", "दूसरा उदाहरण आज़माए", "और ध्यान से तोड़कर समझाए", "थोड़ा मज़बूत संकेत दे")),
                LearningCheckInQuestion("कितनी चुनौती अच्छी लगती है?", listOf("आसान रखो", "ज़्यादातर आरामदायक", "थोड़ा स्ट्रेच", "मुझे और आगे बढ़ाओ")),
                LearningCheckInQuestion("अभी कौन-सा क्षेत्र सबसे मज़ेदार लगता है?", listOf("जिज्ञासा वाले सवाल", "विज्ञान", "गणित", "कोडिंग / बनाना")),
                LearningCheckInQuestion("ज़्यादातर जवाब कितने लंबे हों?", listOf("जल्दी वाले", "एक उदाहरण के साथ छोटे", "कदमों के साथ मध्यम", "गहराई के साथ लंबे")),
                LearningCheckInQuestion("कौन-सी शैली आप पर सबसे अच्छी बैठती है?", listOf("नरम और आसान", "जिज्ञासु और सोचने वाली", "संगठित और काम की", "मेरी सोच को और फैलाए")),
                LearningCheckInQuestion("एक अच्छे जवाब के अंत में क्या होना चाहिए?", listOf("सीधे-सीधे समेट दे", "एक मज़ेदार तथ्य जोड़ दे", "एक छोटा अगला कदम दे", "कोई चैलेंज ट्राय करने को दे")),
            )
            else -> listOf(
                LearningCheckInQuestion("कुछ नया सीखते समय सबसे ज़्यादा क्या मदद करता है?", listOf("आसान ओवरव्यू", "साफ उदाहरण", "कदमों में तर्क", "गहरी चुनौती")),
                LearningCheckInQuestion("आप चाहते हैं कि GyanGo किस बात पर सबसे ज़्यादा ध्यान दे?", listOf("स्पष्टता", "जिज्ञासा", "समस्या समाधान", "सोच को और फैलाना")),
                LearningCheckInQuestion("अगर कोई विषय कठिन हो जाए, तो क्या होना चाहिए?", listOf("उसे आसान कर दे", "जुड़ा हुआ उदाहरण दे", "तर्क को तोड़ दे", "चुनौती रहे पर गाइड करे")),
                LearningCheckInQuestion("ज़्यादातर जवाबों में कितनी जानकारी होनी चाहिए?", listOf("सिर्फ ज़रूरी बातें", "ज़रूरी बातें और उदाहरण", "अभ्यास के लिए पर्याप्त", "और गहराई व विस्तार")),
                LearningCheckInQuestion("इनमें से कौन-सा आप जैसा लगता है?", listOf("Explorer", "Thinker", "Builder", "थोड़ी और चुनौती के साथ मिलाकर")),
                LearningCheckInQuestion("आप पहले किस विषय की तरफ़ बढ़ते हैं?", listOf("सामान्य जिज्ञासा", "विज्ञान / प्रकृति", "गणित / पैटर्न", "कोडिंग / बनाना")),
                LearningCheckInQuestion("अंत में क्या सबसे उपयोगी है?", listOf("छोटा समापन", "एक अच्छा उदाहरण", "अगला अभ्यास कदम", "एक स्ट्रेच सवाल")),
                LearningCheckInQuestion("GyanGo के लिए सबसे अच्छा अंदाज़ क्या है?", listOf("शांत और आसान", "जिज्ञासु और गहरा", "साफ और व्यावहारिक", "तेज़ और चुनौतीपूर्ण")),
            )
        }
        else -> when (band) {
            0 -> listOf(
                LearningCheckInQuestion("When you learn something new, what feels best?", listOf("Show me a picture", "Tell me a short story", "Give me easy steps", "Let me try a tiny challenge")),
                LearningCheckInQuestion("If something feels tricky, what should GyanGo do?", listOf("Make it super simple", "Use a fun example", "Break it into steps", "Give me a puzzle to try")),
                LearningCheckInQuestion("Which topics sound exciting today?", listOf("Animals and nature", "Space and science", "Numbers and patterns", "Making and building")),
                LearningCheckInQuestion("How long should answers be?", listOf("Very short", "Short and fun", "A little more", "Give me extra detail")),
                LearningCheckInQuestion("What helps you understand fastest?", listOf("One idea at a time", "A real-life example", "A how-to list", "A challenge question")),
                LearningCheckInQuestion("When you ask why something happens, what do you want next?", listOf("A tiny answer", "A fun reason", "A step-by-step reason", "A big deeper reason")),
                LearningCheckInQuestion("Pick the style you like most.", listOf("Friendly and simple", "Curious and playful", "Clear and step-by-step", "Smart and challenging")),
                LearningCheckInQuestion("If you get stuck, what should I say?", listOf("It's okay, let's keep it easy", "Let's try a fun example", "Let's solve it step by step", "You're ready for a harder clue")),
            )
            1 -> listOf(
                LearningCheckInQuestion("When you ask a question, what kind of answer do you like first?", listOf("Short and simple", "A fun example", "Step by step", "A bigger challenge")),
                LearningCheckInQuestion("What helps you learn best?", listOf("Simple words", "Stories and examples", "Clear steps", "Tricky questions to solve")),
                LearningCheckInQuestion("If something is confusing, what should happen?", listOf("Make it easier", "Use a new example", "Slow it down into parts", "Give me a challenge hint")),
                LearningCheckInQuestion("Which sounds most like you?", listOf("I like easy explanations", "I like knowing why", "I like solving things", "I like big challenges")),
                LearningCheckInQuestion("How much detail should answers have?", listOf("Just the main point", "A little more", "Enough to practice", "Extra depth")),
                LearningCheckInQuestion("What topics pull you in most?", listOf("General wonder questions", "Science and nature", "Math and patterns", "Coding and making")),
                LearningCheckInQuestion("When GyanGo teaches, what should it do?", listOf("Keep it calm and short", "Use examples I know", "Guide me in steps", "Push me a little more")),
                LearningCheckInQuestion("When you finish an answer, what do you want next?", listOf("Stop there", "One fun fact", "One extra example", "One challenge question")),
            )
            2 -> listOf(
                LearningCheckInQuestion("What kind of explanation usually helps you most?", listOf("Very clear basics", "Examples that connect ideas", "Step-by-step reasoning", "Challenge me a bit more")),
                LearningCheckInQuestion("When you ask 'why', what do you really want?", listOf("A simple reason", "A real-life connection", "The logic in steps", "A deeper idea to think about")),
                LearningCheckInQuestion("How should GyanGo respond if you're stuck?", listOf("Make it easier right away", "Try a different example", "Break it down more carefully", "Give me a stronger hint")),
                LearningCheckInQuestion("How much challenge feels good?", listOf("Keep it easy", "Mostly comfortable", "A little stretch", "Push me further")),
                LearningCheckInQuestion("Which area sounds most fun right now?", listOf("Curiosity questions", "Science", "Math", "Coding / building")),
                LearningCheckInQuestion("How long should most answers be?", listOf("Quick", "Short with one example", "Medium with steps", "Longer with depth")),
                LearningCheckInQuestion("What style fits you best?", listOf("Gentle and simple", "Curious and thoughtful", "Structured and practical", "Stretch my thinking")),
                LearningCheckInQuestion("What should happen at the end of a good answer?", listOf("Wrap up simply", "Add one cool fact", "Give me a small next step", "Give me a challenge to try")),
            )
            else -> listOf(
                LearningCheckInQuestion("When learning something new, what helps most?", listOf("Simple overview", "Clear examples", "Reasoning in steps", "Deeper challenge")),
                LearningCheckInQuestion("What do you want GyanGo to optimize for?", listOf("Clarity", "Curiosity", "Problem solving", "Stretch thinking")),
                LearningCheckInQuestion("If a topic gets hard, what should happen?", listOf("Simplify it", "Use a relatable example", "Break down the logic", "Keep the challenge but guide me")),
                LearningCheckInQuestion("How much detail should most answers include?", listOf("Only essentials", "Essentials plus examples", "Enough for practice", "More depth and extension")),
                LearningCheckInQuestion("Which area feels most like you?", listOf("Explorer", "Thinker", "Builder", "Mix it up with more challenge")),
                LearningCheckInQuestion("Which topic lane do you reach for first?", listOf("General curiosity", "Science / nature", "Math / patterns", "Coding / creating")),
                LearningCheckInQuestion("What ending is most useful?", listOf("Short wrap-up", "One good example", "A next practice step", "A stretch question")),
                LearningCheckInQuestion("What's the best tone for GyanGo?", listOf("Calm and simple", "Curious and insightful", "Clear and practical", "Sharp and challenging")),
            )
        }
    }).take(5)
}

private fun learningCheckInCopy(localeTag: String): LearningCheckInCopy = when {
    localeTag.startsWith("te", ignoreCase = true) -> LearningCheckInCopy(
        promptTitle = "మీకు బాగా అర్థమయ్యేలా నేర్చుకోవాలా?",
        promptBody = "ఒక చిన్న, సరదా check-in తీసుకుంటే GyanGo మీకు సరిగ్గా సరిపోయేలా వివరిస్తుంది. ఇది స్కూల్ IQ టెస్ట్ కాదు. మీకు నచ్చినట్టు మాత్రమే ఎంచుకోండి. మార్కులు లేవు, ఒత్తిడి లేదు.",
        whatThisDoesTitle = "ఇది ఏమి చేస్తుంది",
        whatThisDoesBody = "ప్రతి పిల్లవాడికి సరైన వేగం, ఉదాహరణలు, మరియు సవాలును GyanGo ఎంచుకోవడానికి ఇది సహాయపడుతుంది.",
        laterHint = "ఇది తర్వాత Settings నుంచీ కూడా చేయవచ్చు.",
        startButton = "ప్రయత్నిద్దాం",
        skipButton = "ఇప్పటికైతే వద్దు",
        screenTitle = "త్వరిత learning check-in",
        screenBody = "మీకు ఎక్కువగా సరిపడేదాన్ని ఎంచుకోండి. సరైనది లేదా తప్పు అనే జవాబులు లేవు.",
        subjectPrompt = "ముందుగా ఏ విషయంలో ఎక్కువ సహాయం కావాలి?",
        saveButton = "నా శైలిని సేవ్ చేయి",
        footer = "భవిష్యత్ ప్రశ్నల ఆధారంగా GyanGo ఇంకా సర్దుబాటు అవుతూనే ఉంటుంది.",
        subjectChoices = listOf(
            "ఆసక్తి" to SubjectMode.CURIOSITY,
            "గణితం" to SubjectMode.MATH,
            "విజ్ఞానం" to SubjectMode.SCIENCE,
            "కోడింగ్" to SubjectMode.CODING,
            "రచన" to SubjectMode.WRITING,
        ),
    )
    localeTag.startsWith("hi", ignoreCase = true) -> LearningCheckInCopy(
        promptTitle = "क्या मैं सीखूँ कि आपको सबसे अच्छे तरीके से कैसे समझाना है?",
        promptBody = "एक छोटा, मज़ेदार check-in लेने से GyanGo आपको आपकी पसंद के हिसाब से समझा पाएगा। यह स्कूल का IQ test नहीं है। बस वही चुनिए जो आपको अपने जैसा लगे। न अंक, न दबाव.",
        whatThisDoesTitle = "यह क्या करता है",
        whatThisDoesBody = "यह GyanGo को हर बच्चे के लिए सही गति, उदाहरण और चुनौती स्तर चुनने में मदद करता है।",
        laterHint = "आप इसे बाद में Settings से भी कर सकते हैं।",
        startButton = "चलो शुरू करें",
        skipButton = "अभी छोड़ें",
        screenTitle = "झटपट learning check-in",
        screenBody = "जो आपको सबसे ज़्यादा सही लगे, वही चुनें। इसमें सही या गलत जवाब नहीं हैं।",
        subjectPrompt = "सबसे पहले किस विषय में ज़्यादा मदद चाहिए?",
        saveButton = "मेरी शैली सेव करें",
        footer = "आने वाले सवालों के आधार पर GyanGo आगे भी अपने जवाब समायोजित करता रहेगा।",
        subjectChoices = listOf(
            "जिज्ञासा" to SubjectMode.CURIOSITY,
            "गणित" to SubjectMode.MATH,
            "विज्ञान" to SubjectMode.SCIENCE,
            "कोडिंग" to SubjectMode.CODING,
            "लेखन" to SubjectMode.WRITING,
        ),
    )
    else -> LearningCheckInCopy(
        promptTitle = "Want answers that fit you better?",
        promptBody = "Take a quick check-in. No grades, no pressure.",
        whatThisDoesTitle = "What this does",
        whatThisDoesBody = "Helps GyanGo match pace, examples, and challenge.",
        laterHint = "You can do this later in Settings.",
        startButton = "Let's try it",
        skipButton = "Skip for now",
        screenTitle = "Quick learning check-in",
        screenBody = "Choose what feels most like you. There are no right or wrong answers.",
        subjectPrompt = "What would you like more help with first?",
        saveButton = "Save my style",
        footer = "GyanGo will keep adjusting from future questions too.",
        subjectChoices = listOf(
            "Curiosity" to SubjectMode.CURIOSITY,
            "Math" to SubjectMode.MATH,
            "Science" to SubjectMode.SCIENCE,
            "Coding" to SubjectMode.CODING,
            "Writing" to SubjectMode.WRITING,
        ),
    )
}

@Composable
fun LearningCheckInPromptScreen(
    localeTag: String,
    statusHint: String? = null,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    val copy = remember(localeTag) { learningCheckInCopy(localeTag) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .displayCutoutPadding()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = copy.promptTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = copy.promptBody,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            statusHint?.let { hint ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = EntryScreenCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = hint,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = EntryScreenCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = copy.whatThisDoesTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = copy.whatThisDoesBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = copy.laterHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                shape = EntryScreenFieldShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(copy.startButton)
            }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                shape = EntryScreenFieldShape,
            ) {
                Text(copy.skipButton)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun LearningCheckInScreen(
    localeTag: String,
    birthYear: Int?,
    statusHint: String? = null,
    onComplete: (answers: List<Int>, subjectPreferences: List<SubjectMode>) -> Unit,
    onSkip: () -> Unit,
) {
    val copy = remember(localeTag) { learningCheckInCopy(localeTag) }
    val questions = remember(birthYear, localeTag) { learningCheckInQuestionsForAge(birthYear, localeTag) }
    val selectedAnswers = remember(questions) { mutableStateListOf(*IntArray(questions.size) { -1 }.toTypedArray()) }
    val selectedSubjects = remember { mutableStateListOf<SubjectMode>() }
    val subjectChoices = copy.subjectChoices
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .displayCutoutPadding()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = copy.screenTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = copy.screenBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            statusHint?.let { hint ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = EntryScreenCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = hint,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            questions.forEachIndexed { index, question ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = EntryScreenCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "${index + 1}. ${question.prompt}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        question.options.forEachIndexed { optionIndex, option ->
                            FilterChip(
                                selected = selectedAnswers[index] == optionIndex,
                                onClick = { selectedAnswers[index] = optionIndex },
                                label = { Text(option) },
                            )
                        }
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = EntryScreenCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = copy.subjectPrompt,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        subjectChoices.forEach { (label, mode) ->
                            FilterChip(
                                selected = selectedSubjects.contains(mode),
                                onClick = {
                                    if (selectedSubjects.contains(mode)) {
                                        selectedSubjects.remove(mode)
                                    } else {
                                        selectedSubjects.add(mode)
                                    }
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                    shape = EntryScreenFieldShape,
                ) {
                    Text(copy.skipButton)
                }
                Button(
                    onClick = {
                        onComplete(
                            selectedAnswers.map { it.coerceAtLeast(0) },
                            selectedSubjects.ifEmpty { listOf(SubjectMode.CURIOSITY) },
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = EntryScreenFieldShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(copy.saveButton)
                }
            }
            Text(
                text = copy.footer,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
