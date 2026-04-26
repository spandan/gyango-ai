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

data class LearningCheckInCopy(
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
    val lang = learningCheckInContentLanguage(localeTag)
    return (when (lang) {
        "te" -> when (band) {
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
                LearningCheckInQuestion("ఏ విషయాలు మిమ్మల్ని ఎక్కువగా ఆకర్షిస్తాయి?", listOf("సాధారణ లేదా మిశ్రమ ప్రశ్నలు", "విజ్ఞానం మరియు ప్రకృతి", "గణితం మరియు నమూనాలు", "కోడింగ్ మరియు తయారీ")),
                LearningCheckInQuestion("GyanGo బోధించేప్పుడు ఏమి చేయాలి?", listOf("శాంతంగా, చిన్నగా ఉంచాలి", "నాకు తెలిసిన ఉదాహరణలు ఇవ్వాలి", "దశలలో నడిపించాలి", "కొంచెం ముందుకు నెట్టాలి")),
                LearningCheckInQuestion("జవాబు పూర్తయ్యాక తర్వాత ఏమి కావాలి?", listOf("అక్కడే ఆపు", "ఒక సరదా విషయం", "ఇంకో ఉదాహరణ", "ఒక సవాలు ప్రశ్న")),
            )
            2 -> listOf(
                LearningCheckInQuestion("సాధారణంగా మీకు ఏ రకమైన వివరణ ఎక్కువగా సహాయపడుతుంది?", listOf("చాలా స్పష్టమైన బేసిక్స్", "ఆలోచనలను కలిపే ఉదాహరణలు", "దశల వారీ తర్కం", "కొంచెం ఎక్కువ సవాలు")),
                LearningCheckInQuestion("మీరు 'ఎందుకు' అని అడిగితే నిజంగా ఏమి కావాలి?", listOf("సులభమైన కారణం", "నిజజీవిత అనుసంధానం", "దశలలో తర్కం", "లోతుగా ఆలోచించే ఆలోచన")),
                LearningCheckInQuestion("మీరు ఇరుక్కుపోతే GyanGo ఎలా స్పందించాలి?", listOf("వెంటనే సులభం చేయాలి", "వేరే ఉదాహరణ ప్రయత్నించాలి", "ఇంకా జాగ్రత్తగా విడదీయాలి", "ఇంకాస్త బలమైన సూచన ఇవ్వాలి")),
                LearningCheckInQuestion("ఎంత సవాలు బాగుంటుంది?", listOf("సులభంగా ఉంచండి", "చాలావరకు సౌకర్యంగా", "కొంచెం స్ట్రెచ్", "ఇంకా ముందుకు నెట్టండి")),
                LearningCheckInQuestion("ఇప్పుడు ఏ విభాగం ఎక్కువగా సరదాగా అనిపిస్తుంది?", listOf("సాధారణ విషయాలు", "విజ్ఞానం", "గణితం", "కోడింగ్ / నిర్మాణం")),
                LearningCheckInQuestion("చాలా జవాబులు ఎంత పొడవుగా ఉండాలి?", listOf("త్వరగా", "ఒక ఉదాహరణతో చిన్నగా", "దశలతో మధ్యస్థంగా", "లోతుతో కొంచెం పొడవుగా")),
                LearningCheckInQuestion("ఏ శైలి మీకు బాగా సరిపోతుంది?", listOf("మృదువుగా, సులభంగా", "ఆసక్తిగా, ఆలోచనాత్మకంగా", "క్రమబద్ధంగా, ఉపయోగకరంగా", "నా ఆలోచనను మరింత పెంచాలి")),
                LearningCheckInQuestion("ఒక మంచి జవాబు చివర ఏమి జరగాలి?", listOf("సులభంగా ముగించాలి", "ఒక చక్కని విషయం జోడించాలి", "చిన్న తదుపరి దశ ఇవ్వాలి", "ఒక సవాలు ప్రయత్నించమని చెప్పాలి")),
            )
            else -> listOf(
                LearningCheckInQuestion("కొత్త విషయం నేర్చుకునేప్పుడు ఏది ఎక్కువ సహాయపడుతుంది?", listOf("సులభమైన అవలోకనం", "స్పష్టమైన ఉదాహరణలు", "దశలలో తర్కం", "లోతైన సవాలు")),
                LearningCheckInQuestion("GyanGo ఏ విషయంలో మెరుగుపడాలని మీరు కోరుకుంటారు?", listOf("స్పష్టత", "అన్వేషణ", "సమస్య పరిష్కారం", "ఆలోచనను విస్తరించడం")),
                LearningCheckInQuestion("ఒక విషయం కష్టంగా మారితే ఏమి జరగాలి?", listOf("సులభం చేయాలి", "సంబంధిత ఉదాహరణ ఇవ్వాలి", "తర్కాన్ని విడగొట్టాలి", "సవాలును ఉంచి నడిపించాలి")),
                LearningCheckInQuestion("చాలా జవాబుల్లో ఎంత వివరాలు ఉండాలి?", listOf("అత్యవసరమైనవి మాత్రమే", "అత్యవసరమైనవి + ఉదాహరణలు", "అభ్యాసానికి సరిపడా", "ఇంకా లోతు మరియు విస్తరణ")),
                LearningCheckInQuestion("ఇవాటిలో మీలాగా అనిపించేది ఏది?", listOf("అన్వేషకుడు", "ఆలోచనాపరుడు", "నిర్మాత", "ఇంకాస్త సవాలుతో కలిపి")),
                LearningCheckInQuestion("ముందుగా ఏ విషయం వైపు వెళ్లాలని అనిపిస్తుంది?", listOf("సాధారణ విషయాలు", "విజ్ఞానం / ప్రకృతి", "గణితం / నమూనాలు", "కోడింగ్ / సృష్టి")),
                LearningCheckInQuestion("చివర ఏది ఎక్కువ ఉపయోగపడుతుంది?", listOf("చిన్న ముగింపు", "ఒక మంచి ఉదాహరణ", "తదుపరి అభ్యాస దశ", "ఒక స్ట్రెచ్ ప్రశ్న")),
                LearningCheckInQuestion("GyanGo కి ఏ శైలి బాగుంటుంది?", listOf("శాంతంగా, సులభంగా", "ఆసక్తిగా, లోతుగా", "స్పష్టంగా, ఆచరణాత్మకంగా", "ముక్కుసూటిగా, సవాలుగా")),
            )
        }
        "hi" -> when (band) {
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
                LearningCheckInQuestion("कौन से विषय आपको सबसे ज़्यादा खींचते हैं?", listOf("सामान्य या मिश्रित सवाल", "विज्ञान और प्रकृति", "गणित और पैटर्न", "कोडिंग और बनाना")),
                LearningCheckInQuestion("जब GyanGo पढ़ाए, तो क्या करे?", listOf("शांत और छोटा रखे", "मेरे जाने-पहचाने उदाहरण दे", "मुझे कदमों में गाइड करे", "मुझे थोड़ा आगे बढ़ाए")),
                LearningCheckInQuestion("जवाब खत्म होने पर आगे क्या चाहिए?", listOf("वहीं रुक जाए", "एक मज़ेदार तथ्य", "एक और उदाहरण", "एक चैलेंज सवाल")),
            )
            2 -> listOf(
                LearningCheckInQuestion("आम तौर पर किस तरह की समझ आपको सबसे ज़्यादा मदद करती है?", listOf("बहुत साफ बुनियाद", "विचारों को जोड़ने वाले उदाहरण", "कदम-दर-कदम तर्क", "मुझे थोड़ा और चुनौती दें")),
                LearningCheckInQuestion("जब आप 'क्यों' पूछते हैं, तो आपको सच में क्या चाहिए?", listOf("एक आसान कारण", "असल जीवन से जुड़ाव", "तर्क के कदम", "सोचने के लिए एक गहरा विचार")),
                LearningCheckInQuestion("अगर आप अटक जाएँ, तो GyanGo कैसे जवाब दे?", listOf("तुरंत आसान बना दे", "दूसरा उदाहरण आज़माए", "और ध्यान से तोड़कर समझाए", "थोड़ा मज़बूत संकेत दे")),
                LearningCheckInQuestion("कितनी चुनौती अच्छी लगती है?", listOf("आसान रखो", "ज़्यादातर आरामदायक", "थोड़ा स्ट्रेच", "मुझे और आगे बढ़ाओ")),
                LearningCheckInQuestion("अभी कौन-सा क्षेत्र सबसे मज़ेदार लगता है?", listOf("सामान्य विषय", "विज्ञान", "गणित", "कोडिंग / बनाना")),
                LearningCheckInQuestion("ज़्यादातर जवाब कितने लंबे हों?", listOf("जल्दी वाले", "एक उदाहरण के साथ छोटे", "कदमों के साथ मध्यम", "गहराई के साथ लंबे")),
                LearningCheckInQuestion("कौन-सी शैली आप पर सबसे अच्छी बैठती है?", listOf("नरम और आसान", "जिज्ञासु और सोचने वाली", "संगठित और काम की", "मेरी सोच को और फैलाए")),
                LearningCheckInQuestion("एक अच्छे जवाब के अंत में क्या होना चाहिए?", listOf("सीधे-सीधे समेट दे", "एक मज़ेदार तथ्य जोड़ दे", "एक छोटा अगला कदम दे", "कोई चैलेंज ट्राय करने को दे")),
            )
            else -> listOf(
                LearningCheckInQuestion("कुछ नया सीखते समय सबसे ज़्यादा क्या मदद करता है?", listOf("आसान ओवरव्यू", "साफ उदाहरण", "कदमों में तर्क", "गहरी चुनौती")),
                LearningCheckInQuestion("आप चाहते हैं कि GyanGo किस बात पर सबसे ज़्यादा ध्यान दे?", listOf("स्पष्टता", "अन्वेषण", "समस्या समाधान", "सोच को और फैलाना")),
                LearningCheckInQuestion("अगर कोई विषय कठिन हो जाए, तो क्या होना चाहिए?", listOf("उसे आसान कर दे", "जुड़ा हुआ उदाहरण दे", "तर्क को तोड़ दे", "चुनौती रहे पर गाइड करे")),
                LearningCheckInQuestion("ज़्यादातर जवाबों में कितनी जानकारी होनी चाहिए?", listOf("सिर्फ ज़रूरी बातें", "ज़रूरी बातें और उदाहरण", "अभ्यास के लिए पर्याप्त", "और गहराई व विस्तार")),
                LearningCheckInQuestion("इनमें से कौन-सा आप जैसा लगता है?", listOf("Explorer", "Thinker", "Builder", "थोड़ी और चुनौती के साथ मिलाकर")),
                LearningCheckInQuestion("आप पहले किस विषय की तरफ़ बढ़ते हैं?", listOf("सामान्य विषय", "विज्ञान / प्रकृति", "गणित / पैटर्न", "कोडिंग / बनाना")),
                LearningCheckInQuestion("अंत में क्या सबसे उपयोगी है?", listOf("छोटा समापन", "एक अच्छा उदाहरण", "अगला अभ्यास कदम", "एक स्ट्रेच सवाल")),
                LearningCheckInQuestion("GyanGo के लिए सबसे अच्छा अंदाज़ क्या है?", listOf("शांत और आसान", "जिज्ञासु और गहरा", "साफ और व्यावहारिक", "तेज़ और चुनौतीपूर्ण")),
            )
        }
        "mr" -> LearningCheckIndicExtras.marathiForBand(band)
        "ta" -> LearningCheckIndicExtras.tamilForBand(band)
        "bn" -> listOf(
            LearningCheckInQuestion("নতুন কিছু শিখতে গেলে কোনভাবে বুঝলে তোমার সবচেয়ে ভালো লাগে?", listOf("সহজ করে ছোট করে", "উদাহরণ দিয়ে", "ধাপে ধাপে", "একটু চ্যালেঞ্জসহ")),
            LearningCheckInQuestion("কিছু কঠিন লাগলে GyanGo কী করবে?", listOf("আরও সহজ করবে", "নতুন উদাহরণ দেবে", "খণ্ডে ভাগ করে বুঝাবে", "ইঙ্গিত দিয়ে চেষ্টা করাবে")),
            LearningCheckInQuestion("উত্তর কত বড় হলে ভালো?", listOf("খুব ছোট", "ছোট কিন্তু পরিষ্কার", "মাঝারি, ধাপে ধাপে", "বিস্তারিত")),
            LearningCheckInQuestion("কোন ধরনের বিষয় এখন বেশি পছন্দ?", listOf("সাধারণ", "বিজ্ঞান", "গণিত", "কোডিং/বানানো")),
            LearningCheckInQuestion("ভালো উত্তরের শেষে কী চাই?", listOf("ছোট সারাংশ", "একটি মজার তথ্য", "পরের ছোট ধাপ", "একটি চ্যালেঞ্জ প্রশ্ন")),
        )
        "gu" -> listOf(
            LearningCheckInQuestion("નવું શીખતા વખતે તમને કઈ રીત સૌથી વધુ ગમે છે?", listOf("ખૂબ સરળ રીતે", "ઉદાહરણ સાથે", "પગલું-દર-પગલું", "થોડા પડકાર સાથે")),
            LearningCheckInQuestion("કંઈક મુશ્કેલ લાગે તો GyanGo શું કરવું?", listOf("વધારે સરળ કરવું", "બીજું ઉદાહરણ આપવું", "ભાગોમાં સમજાવવું", "ઈશારો આપી પ્રયત્ન કરાવવો")),
            LearningCheckInQuestion("જવાબ કેટલા લાંબા હોવા જોઈએ?", listOf("ખૂબ ટૂંકા", "ટૂંકા અને સ્પષ્ટ", "મધ્યમ, પગલાં સાથે", "વિગતવાર")),
            LearningCheckInQuestion("હમણાં કયા વિષયો વધુ ગમે છે?", listOf("સામાન્ય", "વિજ્ઞાન", "ગણિત", "કોડિંગ/બનાવટ")),
            LearningCheckInQuestion("સારો જવાબ પૂરો થયા પછી શું જોઈએ?", listOf("ટૂંકો સારાંશ", "એક રસપ્રદ માહિતી", "આગલું નાનું પગલું", "એક પડકાર પ્રશ્ન")),
        )
        "kn" -> listOf(
            LearningCheckInQuestion("ಹೊಸ ವಿಷಯ ಕಲಿಯುವಾಗ ನಿಮಗೆ ಯಾವ ರೀತಿಯಲ್ಲಿ ಕಲಿಸಿದರೆ ಹೆಚ್ಚು ಇಷ್ಟ?", listOf("ಬಹಳ ಸರಳವಾಗಿ", "ಉದಾಹರಣೆಯೊಂದಿಗೆ", "ಹಂತ ಹಂತವಾಗಿ", "ಸ್ವಲ್ಪ ಸವಾಲಿನೊಂದಿಗೆ")),
            LearningCheckInQuestion("ಏನಾದರೂ ಕಷ್ಟವಾದರೆ GyanGo ಏನು ಮಾಡಬೇಕು?", listOf("ಇನ್ನಷ್ಟು ಸರಳಗೊಳಿಸಬೇಕು", "ಹೊಸ ಉದಾಹರಣೆ ಕೊಡಬೇಕು", "ಭಾಗಗಳಾಗಿ ಬಿಡಿಸಿ ಹೇಳಬೇಕು", "ಸೂಚನೆ ನೀಡಿ ಪ್ರಯತ್ನಿಸಲಿ")),
            LearningCheckInQuestion("ಉತ್ತರಗಳು ಎಷ್ಟು ಉದ್ದವಾಗಿರಲಿ?", listOf("ತುಂಬ ಚಿಕ್ಕದು", "ಚಿಕ್ಕದು ಆದರೆ ಸ್ಪಷ್ಟ", "ಮಧ್ಯಮ, ಹಂತಗಳೊಂದಿಗೆ", "ವಿಸ್ತಾರವಾಗಿ")),
            LearningCheckInQuestion("ಈಗ ಯಾವ ವಿಷಯಗಳು ಹೆಚ್ಚು ಇಷ್ಟ?", listOf("ಸಾಮಾನ್ಯ", "ವಿಜ್ಞಾನ", "ಗಣಿತ", "ಕೋಡಿಂಗ್/ಮೇಕಿಂಗ್")),
            LearningCheckInQuestion("ಒಳ್ಳೆಯ ಉತ್ತರದ ಕೊನೆಯಲ್ಲಿ ಏನು ಬೇಕು?", listOf("ಚಿಕ್ಕ ಸಾರಾಂಶ", "ಒಂದು ಕುತೂಹಲಕರ ವಿಷಯ", "ಮುಂದಿನ ಸಣ್ಣ ಹಂತ", "ಒಂದು ಸವಾಲಿನ ಪ್ರಶ್ನೆ")),
        )
        "ml" -> listOf(
            LearningCheckInQuestion("പുതിയത് പഠിക്കുമ്പോൾ ഏത് രീതിയിൽ പഠിപ്പിച്ചാൽ നിങ്ങള്‍ക്ക് ഏറ്റവും നന്നായി മനസ്സിലാകും?", listOf("വളരെ ലളിതമായി", "ഉദാഹരണത്തോടൊപ്പം", "പടിപടിയായി", "ചെറിയ വെല്ലുവിളിയോടെ")),
            LearningCheckInQuestion("ഒന്നുകിൽ ബുദ്ധിമുട്ടായി തോന്നിയാൽ GyanGo എന്ത് ചെയ്യണം?", listOf("ഇനിയും ലളിതമാക്കണം", "പുതിയ ഉദാഹരണം നൽകണം", "ഭാഗങ്ങളാക്കി വിശദീകരിക്കണം", "ഹിന്റ് നൽകി ശ്രമിപ്പിക്കണം")),
            LearningCheckInQuestion("ഉത്തരങ്ങൾ എത്ര നീളമുള്ളതാകണം?", listOf("വളരെ ചെറുത്", "ചെറുത് പക്ഷേ വ്യക്തം", "മധ്യമം, ഘട്ടങ്ങളോടെ", "വിശദമായി")),
            LearningCheckInQuestion("ഇപ്പോൾ ഏത് വിഷയം കൂടുതലായി ഇഷ്ടമാണ്?", listOf("സാധാരണ", "ശാസ്ത്രം", "ഗണിതം", "കോഡിംഗ്/നിർമ്മാണം")),
            LearningCheckInQuestion("നല്ല ഉത്തരത്തിന്റെ അവസാനം എന്ത് വേണം?", listOf("ചുരുക്കം", "ഒരു രസകരമായ വിവരം", "അടുത്ത ചെറിയ പടി", "ഒരു വെല്ലുവിളി ചോദ്യം")),
        )
        "pa" -> listOf(
            LearningCheckInQuestion("ਨਵਾਂ ਸਿੱਖਦੇ ਸਮੇਂ ਤੁਹਾਨੂੰ ਕਿਹੜਾ ਢੰਗ ਸਭ ਤੋਂ ਵਧੀਆ ਲੱਗਦਾ ਹੈ?", listOf("ਬਹੁਤ ਆਸਾਨ ਤਰੀਕੇ ਨਾਲ", "ਉਦਾਹਰਨ ਨਾਲ", "ਕਦਮ-ਦਰ-ਕਦਮ", "ਥੋੜ੍ਹੇ ਚੈਲੈਂਜ ਨਾਲ")),
            LearningCheckInQuestion("ਜੇ ਕੁਝ ਥੋੜ੍ਹਾ ਔਖਾ ਲੱਗੇ ਤਾਂ GyanGo ਕੀ ਕਰੇ?", listOf("ਹੋਰ ਆਸਾਨ ਕਰੇ", "ਨਵੀਂ ਉਦਾਹਰਨ ਦੇਵੇ", "ਹਿੱਸਿਆਂ ਵਿੱਚ ਸਮਝਾਏ", "ਇਸ਼ਾਰਾ ਦੇ ਕੇ ਕੋਸ਼ਿਸ਼ ਕਰਵਾਏ")),
            LearningCheckInQuestion("ਜਵਾਬ ਕਿੰਨੇ ਲੰਮੇ ਹੋਣ?", listOf("ਬਹੁਤ ਛੋਟੇ", "ਛੋਟੇ ਪਰ ਸਾਫ", "ਦਰਮਿਆਨੇ, ਕਦਮਾਂ ਨਾਲ", "ਵਿਸਥਾਰ ਨਾਲ")),
            LearningCheckInQuestion("ਇਸ ਵੇਲੇ ਕਿਹੜੇ ਵਿਸ਼ੇ ਵੱਧ ਪਸੰਦ ਹਨ?", listOf("ਆਮ", "ਵਿਗਿਆਨ", "ਗਣਿਤ", "ਕੋਡਿੰਗ/ਬਣਾਉਣਾ")),
            LearningCheckInQuestion("ਚੰਗੇ ਜਵਾਬ ਦੇ ਅੰਤ 'ਤੇ ਕੀ ਚਾਹੀਦਾ?", listOf("ਛੋਟਾ ਸਾਰ", "ਇੱਕ ਮਜ਼ੇਦਾਰ ਤੱਥ", "ਅਗਲਾ ਛੋਟਾ ਕਦਮ", "ਇੱਕ ਚੈਲੈਂਜ ਸਵਾਲ")),
        )
        "fr" -> listOf(
            LearningCheckInQuestion("Quand tu apprends quelque chose de nouveau, quelle facon t'aide le plus ?", listOf("Tres simple", "Avec un exemple", "Etape par etape", "Avec un petit defi")),
            LearningCheckInQuestion("Si c'est difficile, que doit faire GyanGo ?", listOf("Simplifier davantage", "Donner un autre exemple", "Decouper en petites parties", "Donner un indice")),
            LearningCheckInQuestion("Quelle longueur de reponse preferez-vous ?", listOf("Tres courte", "Courte et claire", "Moyenne avec etapes", "Detaillee")),
            LearningCheckInQuestion("Quel domaine vous interesse le plus maintenant ?", listOf("General", "Science", "Mathematiques", "Codage/creation")),
            LearningCheckInQuestion("A la fin d'une bonne reponse, que voulez-vous ?", listOf("Un court resume", "Un fait interessant", "Une petite etape suivante", "Une question defi")),
        )
        "es" -> listOf(
            LearningCheckInQuestion("Cuando aprendes algo nuevo, que forma te ayuda mas?", listOf("Muy simple", "Con ejemplo", "Paso a paso", "Con un pequeno reto")),
            LearningCheckInQuestion("Si algo se vuelve dificil, que debe hacer GyanGo?", listOf("Simplificar mas", "Dar otro ejemplo", "Dividir en partes", "Dar una pista")),
            LearningCheckInQuestion("Que longitud de respuesta prefieres?", listOf("Muy corta", "Corta y clara", "Media con pasos", "Detallada")),
            LearningCheckInQuestion("Que tema te interesa mas ahora?", listOf("General", "Ciencia", "Matematicas", "Codigo/creacion")),
            LearningCheckInQuestion("Al final de una buena respuesta, que quieres?", listOf("Resumen corto", "Un dato interesante", "Un siguiente paso pequeno", "Una pregunta reto")),
        )
        else -> LearningCheckInEnglishPools.forBand(band)
    }).take(5)
}

fun learningCheckInCopy(localeTag: String): LearningCheckInCopy = when (learningCheckInContentLanguage(localeTag)) {
    "te" -> LearningCheckInCopy(
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
            "సాధారణం" to SubjectMode.GENERAL,
            "గణితం" to SubjectMode.MATH,
            "విజ్ఞానం" to SubjectMode.SCIENCE,
            "కోడింగ్" to SubjectMode.CODING,
            "రచన" to SubjectMode.WRITING,
        ),
    )
    "hi" -> LearningCheckInCopy(
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
            "सामान्य" to SubjectMode.GENERAL,
            "गणित" to SubjectMode.MATH,
            "विज्ञान" to SubjectMode.SCIENCE,
            "कोडिंग" to SubjectMode.CODING,
            "लेखन" to SubjectMode.WRITING,
        ),
    )
    "mr" -> LearningCheckInCopy(
        promptTitle = "तुम्हाला तुमच्याशी जुळणारी उत्तरं हवी आहेत का?",
        promptBody = "एक लहान, मजेशीर check-in घेतल्यास GyanGo तुमच्या पद्धतीशी जुळवतो. हा शाळेचा IQ चाचणी नाही. जे जुळते ते निवडा — गुण नाहीत, दडपण नाही.",
        whatThisDoesTitle = "हे काय करते",
        whatThisDoesBody = "प्रत्येक मुलासाठी योग्य वेग, उदाहरणं आणि आव्हान निवडण्यास GyanGo ला मदत करते.",
        laterHint = "हे नंतर Settings मधूनही करू शकता.",
        startButton = "चला सुरू करूया",
        skipButton = "आत्ता नको",
        screenTitle = "जलद learning check-in",
        screenBody = "तुम्हाला सर्वात योग्य वाटते ते निवडा — चूक किंवा बरोबर नाही.",
        subjectPrompt = "प्रथम कोणत्या विषयात जास्त मदत हवी?",
        saveButton = "माझी शैली जतन करा",
        footer = "पुढील प्रश्नांनुसार GyanGo समायोजित राहील.",
        subjectChoices = listOf(
            "सामान्य" to SubjectMode.GENERAL,
            "गणित" to SubjectMode.MATH,
            "विज्ञान" to SubjectMode.SCIENCE,
            "कोडिंग" to SubjectMode.CODING,
            "लेखन" to SubjectMode.WRITING,
        ),
    )
    "ta" -> LearningCheckInCopy(
        promptTitle = "உங்களுக்குப் பொருந்தும் பதில்கள் வேண்டுமா?",
        promptBody = "ஒரு சிறிய check-in: GyanGo உங்கள் பாணிக்கு ஏற்ப மாறும். இது பள்ளி IQ தேர்வு அல்ல. பொருந்துவதைத் தேர்ந்தெடுங்கள் — மதிப்பெண் இல்லை, அழுத்தம் இல்லை.",
        whatThisDoesTitle = "இது என்ன செய்கிறது",
        whatThisDoesBody = "வேகம், உதாரணங்கள் மற்றும் சவாலை GyanGo தேர்வு செய்ய உதவுகிறது.",
        laterHint = "இதை பின்னர் Settings இலும் செய்யலாம்.",
        startButton = "சரி, தொடங்குவோம்",
        skipButton = "இப்போது வேண்டாம்",
        screenTitle = "விரைவு learning check-in",
        screenBody = "உங்களுக்கு மிகப் பொருத்தமானதைத் தேர்ந்தெடுங்கள் — சரி/தவறு இல்லை.",
        subjectPrompt = "முதலில் எந்தத் தலைப்பில் அதிக உதவி வேண்டும்?",
        saveButton = "என் பாணியைச் சேமி",
        footer = "எதிர்கால கேள்விகளின் அடிப்படையில் GyanGo தொடர்ந்து மாறும்.",
        subjectChoices = listOf(
            "பொது" to SubjectMode.GENERAL,
            "கணிதம்" to SubjectMode.MATH,
            "அறிவியல்" to SubjectMode.SCIENCE,
            "குறியீடு" to SubjectMode.CODING,
            "எழுத்து" to SubjectMode.WRITING,
        ),
    )
    "bn" -> LearningCheckInCopy(
        promptTitle = "আপনার জন্য মানানসই উত্তর চান?",
        promptBody = "একটি ছোট check-in নিলে GyanGo আপনার শৈলী বুঝবে। এটি স্কুলের IQ পরীক্ষা নয়। যা মানায় তাই বেছে নিন — নম্বর নেই, চাপ নেই।",
        whatThisDoesTitle = "এটা কী করে",
        whatThisDoesBody = "গতি, উদাহরণ ও চ্যালেঞ্জ বেছে নিতে GyanGo-কে সাহায্য করে।",
        laterHint = "পরে Settings থেকেও করতে পারেন।",
        startButton = "চলুন শুরু করি",
        skipButton = "এখন নয়",
        screenTitle = "দ্রুত learning check-in",
        screenBody = "আপনার জন্য সবচেয়ে মানানসই বেছে নিন — সঠিক/ভুল নেই।",
        subjectPrompt = "প্রথমে কোন বিষয়ে বেশি সাহায্য চান?",
        saveButton = "আমার শৈলী সংরক্ষণ",
        footer = "ভবিষ্যৎ প্রশ্ন অনুযায়ী GyanGo মানিয়ে নেবে।",
        subjectChoices = listOf(
            "সাধারণ" to SubjectMode.GENERAL,
            "গণিত" to SubjectMode.MATH,
            "বিজ্ঞান" to SubjectMode.SCIENCE,
            "কোডিং" to SubjectMode.CODING,
            "লেখা" to SubjectMode.WRITING,
        ),
    )
    "gu" -> LearningCheckInCopy(
        promptTitle = "તમારા માટે યોગ્ય જવાબો જોઈએ છે?",
        promptBody = "એક નાની check-in લઈએ તો GyanGo તમારી શૈલી સાથે મેળ ખાશે. આ સ્કૂલનો IQ ટેસ્ટ નથી. જે ગમે તે પસંદ કરો — ગ્રેડ નહીં, દબાણ નહીં.",
        whatThisDoesTitle = "આ શું કરે છે",
        whatThisDoesBody = "ગતિ, ઉદાહરણો અને પડકાર પસંદ કરવામાં GyanGo ને મદદ કરે છે.",
        laterHint = "પછી Settings માંથી પણ કરી શકાય છે.",
        startButton = "ચાલો શરૂ કરીએ",
        skipButton = "હમણાં નહીં",
        screenTitle = "ઝડપી learning check-in",
        screenBody = "તમને સૌથી સાચું લાગે તે પસંદ કરો — સાચું/ખોટું નથી.",
        subjectPrompt = "પહેલા કયા વિષયમાં વધુ મદદ જોઈએ?",
        saveButton = "મારી શૈલી સાચવો",
        footer = "આગળના પ્રશ્નો પ્રમાણે GyanGo ઢળતું રહેશે.",
        subjectChoices = listOf(
            "સામાન્ય" to SubjectMode.GENERAL,
            "ગણિત" to SubjectMode.MATH,
            "વિજ્ઞાન" to SubjectMode.SCIENCE,
            "કોડિંગ" to SubjectMode.CODING,
            "લેખન" to SubjectMode.WRITING,
        ),
    )
    "kn" -> LearningCheckInCopy(
        promptTitle = "ನಿಮಗೆ ಹೊಂದಿಕೆಯಾಗುವ ಉತ್ತರಗಳು ಬೇಕೇ?",
        promptBody = "ಸಣ್ಣ check-in ಮಾಡಿದರೆ GyanGo ನಿಮ್ಮ ಶೈಲಿಗೆ ಹೊಂದಿಸುತ್ತದೆ. ಇದು ಶಾಲೆಯ IQ ಪರೀಕ್ಷೆ ಅಲ್ಲ. ಹೊಂದುವುದನ್ನು ಆರಿಸಿ — ಅಂಕಗಳಿಲ್ಲ, ಒತ್ತಡವಿಲ್ಲ.",
        whatThisDoesTitle = "ಇದು ಏನು ಮಾಡುತ್ತದೆ",
        whatThisDoesBody = "ವೇಗ, ಉದಾಹರಣೆಗಳು ಮತ್ತು ಸವಾಲನ್ನು ಆರಿಸಲು GyanGo ಗೆ ಸಹಾಯ ಮಾಡುತ್ತದೆ.",
        laterHint = "ನಂತರ Settings ನಿಂದಲೂ ಮಾಡಬಹುದು.",
        startButton = "ಆರಂಭಿಸೋಣ",
        skipButton = "ಈಗ ಬೇಡ",
        screenTitle = "ವೇಗದ learning check-in",
        screenBody = "ನಿಮಗೆ ಹೆಚ್ಚು ಸರಿಯಾಗಿ ಅನಿಸುವುದನ್ನು ಆರಿಸಿ — ಸರಿ/ತಪ್ಪು ಇಲ್ಲ.",
        subjectPrompt = "ಮೊದಲು ಯಾವ ವಿಷಯದಲ್ಲಿ ಹೆಚ್ಚು ಸಹಾಯ ಬೇಕು?",
        saveButton = "ನನ್ನ ಶೈಲಿ ಉಳಿಸಿ",
        footer = "ಮುಂದಿನ ಪ್ರಶ್ನೆಗಳ ಆಧಾರದ ಮೇಲೆ GyanGo ಹೊಂದಿಕೊಳ್ಳುತ್ತದೆ.",
        subjectChoices = listOf(
            "ಸಾಮಾನ್ಯ" to SubjectMode.GENERAL,
            "ಗಣಿತ" to SubjectMode.MATH,
            "ವಿಜ್ಞಾನ" to SubjectMode.SCIENCE,
            "ಕೋಡಿಂಗ್" to SubjectMode.CODING,
            "ಬರವಣಿಗೆ" to SubjectMode.WRITING,
        ),
    )
    "ml" -> LearningCheckInCopy(
        promptTitle = "നിങ്ങൾക്ക് പൊരുത്തപ്പെടുന്ന ഉത്തരങ്ങൾ വേണോ?",
        promptBody = "ഒരു ചെറിയ check-in എടുത്താൽ GyanGo നിങ്ങളുടെ ശൈലിയുമായി പൊരുത്തപ്പെടും. ഇത് സ്കൂളിലെ IQ പരീക്ഷയല്ല. പൊരുത്തപ്പെടുന്നത് തിരഞ്ഞെടുക്കുക — മാർക്കില്ല, സമ്മർദ്ദമില്ല.",
        whatThisDoesTitle = "ഇത് എന്താണ് ചെയ്യുന്നത്",
        whatThisDoesBody = "വേഗത, ഉദാഹരണങ്ങൾ, വെല്ലുവിളി തിരഞ്ഞെടുക്കാൻ GyanGo ക്ക് സഹായിക്കുന്നു.",
        laterHint = "പിന്നീട് Settings ൽ നിന്നും ചെയ്യാം.",
        startButton = "തുടങ്ങാം",
        skipButton = "ഇപ്പോൾ വേണ്ട",
        screenTitle = "വേഗത്തിലുള്ള learning check-in",
        screenBody = "നിങ്ങൾക്ക് ഏറ്റവും ചേരുന്നത് തിരഞ്ഞെടുക്കുക — ശരി/തെറ്റില്ല.",
        subjectPrompt = "ആദ്യം ഏത് വിഷയത്തിൽ കൂടുതൽ സഹായം വേണം?",
        saveButton = "എന്റെ ശൈലി സേവ് ചെയ്യുക",
        footer = "വരുന്ന ചോദ്യങ്ങളുടെ അടിസ്ഥാനത്തിൽ GyanGo ക്രമീകരിക്കും.",
        subjectChoices = listOf(
            "പൊതു" to SubjectMode.GENERAL,
            "ഗണിതം" to SubjectMode.MATH,
            "ശാസ്ത്രം" to SubjectMode.SCIENCE,
            "കോഡിംഗ്" to SubjectMode.CODING,
            "എഴുത്ത്" to SubjectMode.WRITING,
        ),
    )
    "pa" -> LearningCheckInCopy(
        promptTitle = "ਤੁਹਾਨੂੰ ਢੁਕਵੇ ਜਵਾਬ ਚਾਹੀਦੇ ਹਨ?",
        promptBody = "ਇੱਕ ਛੋਟੀ check-in ਨਾਲ GyanGo ਤੁਹਾਡੀ ਸ਼ੈਲੀ ਨਾਲ ਮੇਲ ਖਾਂਦਾ ਹੈ। ਇਹ ਸਕੂਲ ਦਾ IQ ਟੈਸਟ ਨਹੀਂ। ਜੋ ਢੁਕਦਾ ਹੈ ਚੁਣੋ — ਨੰਬਰ ਨਹੀਂ, ਦਬਾਅ ਨਹੀਂ।",
        whatThisDoesTitle = "ਇਹ ਕੀ ਕਰਦਾ ਹੈ",
        whatThisDoesBody = "ਗਤੀ, ਉਦਾਹਰਣਾਂ ਅਤੇ ਚੁਣੌਤੀ ਚੁਣਨ ਵਿੱਚ GyanGo ਦੀ ਮਦਦ ਕਰਦਾ ਹੈ।",
        laterHint = "ਬਾਅਦ ਵਿੱਚ Settings ਤੋਂ ਵੀ ਕਰ ਸਕਦੇ ਹੋ।",
        startButton = "ਚਲੋ ਸ਼ੁਰੂ ਕਰੀਏ",
        skipButton = "ਹੁਣ ਨਹੀਂ",
        screenTitle = "ਤੇਜ਼ learning check-in",
        screenBody = "ਜੋ ਤੁਹਾਨੂੰ ਸਭ ਤੋਂ ਠੀਕ ਲੱਗੇ ਚੁਣੋ — ਸਹੀ/ਗਲਤ ਨਹੀਂ।",
        subjectPrompt = "ਪਹਿਲਾਂ ਕਿਸ ਵਿਸ਼ੇ ਵਿੱਚ ਵੱਧ ਮਦਦ ਚਾਹੀਦੀ ਹੈ?",
        saveButton = "ਮੇਰੀ ਸ਼ੈਲੀ ਸੇਵ ਕਰੋ",
        footer = "ਆਉਣ ਵਾਲੇ ਸਵਾਲਾਂ ਅਨੁਸਾਰ GyanGo ਢਲਦਾ ਰਹੇਗਾ।",
        subjectChoices = listOf(
            "ਆਮ" to SubjectMode.GENERAL,
            "ਗਣਿਤ" to SubjectMode.MATH,
            "ਵਿਗਿਆਨ" to SubjectMode.SCIENCE,
            "ਕੋਡਿੰਗ" to SubjectMode.CODING,
            "ਲਿਖਤ" to SubjectMode.WRITING,
        ),
    )
    "fr" -> LearningCheckInCopy(
        promptTitle = "Vous voulez des reponses mieux adaptees a vous ?",
        promptBody = "Faites un petit check-in rapide. Pas de notes, pas de pression.",
        whatThisDoesTitle = "A quoi cela sert",
        whatThisDoesBody = "Aide GyanGo a ajuster le rythme, les exemples et le niveau de challenge.",
        laterHint = "Vous pouvez aussi le faire plus tard dans les Parametres.",
        startButton = "Commencer",
        skipButton = "Plus tard",
        screenTitle = "Check-in d'apprentissage rapide",
        screenBody = "Choisissez ce qui vous ressemble le plus. Il n'y a pas de bonne ou mauvaise reponse.",
        subjectPrompt = "Sur quel sujet voulez-vous d'abord plus d'aide ?",
        saveButton = "Enregistrer mon style",
        footer = "GyanGo continuera de s'ajuster avec vos prochaines questions.",
        subjectChoices = listOf(
            "General" to SubjectMode.GENERAL,
            "Mathematiques" to SubjectMode.MATH,
            "Science" to SubjectMode.SCIENCE,
            "Codage" to SubjectMode.CODING,
            "Redaction" to SubjectMode.WRITING,
        ),
    )
    "es" -> LearningCheckInCopy(
        promptTitle = "Quieres respuestas que se adapten mejor a ti?",
        promptBody = "Haz un check-in rapido. Sin notas, sin presion.",
        whatThisDoesTitle = "Para que sirve",
        whatThisDoesBody = "Ayuda a GyanGo a ajustar ritmo, ejemplos y nivel de reto.",
        laterHint = "Tambien puedes hacerlo mas tarde desde Ajustes.",
        startButton = "Empezar",
        skipButton = "Ahora no",
        screenTitle = "Check-in rapido de aprendizaje",
        screenBody = "Elige lo que mas se parece a ti. No hay respuestas correctas o incorrectas.",
        subjectPrompt = "En que tema quieres mas ayuda primero?",
        saveButton = "Guardar mi estilo",
        footer = "GyanGo seguira ajustandose con tus futuras preguntas.",
        subjectChoices = listOf(
            "General" to SubjectMode.GENERAL,
            "Matematicas" to SubjectMode.MATH,
            "Ciencia" to SubjectMode.SCIENCE,
            "Codigo" to SubjectMode.CODING,
            "Redaccion" to SubjectMode.WRITING,
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
            "General" to SubjectMode.GENERAL,
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
    /** When true, [OnboardingStepShell] shows title/body; omit outer surface and duplicate headings. */
    embedInShell: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val copy = remember(localeTag) { learningCheckInCopy(localeTag) }
    @Composable
    fun PromptBody(columnModifier: Modifier, showIntroHeadings: Boolean) {
        Column(
            modifier = columnModifier,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (showIntroHeadings) {
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
            }
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

    if (embedInShell) {
        PromptBody(
            columnModifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            showIntroHeadings = false,
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            PromptBody(
                columnModifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 28.dp),
                showIntroHeadings = true,
            )
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
    embedInShell: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val copy = remember(localeTag) { learningCheckInCopy(localeTag) }
    val questions = remember(birthYear, localeTag) { learningCheckInQuestionsForAge(birthYear, localeTag) }
    val selectedAnswers = remember(questions) { mutableStateListOf(*IntArray(questions.size) { -1 }.toTypedArray()) }
    val selectedSubjects = remember { mutableStateListOf<SubjectMode>() }
    val subjectChoices = copy.subjectChoices
    @Composable
    fun CheckInBody(columnModifier: Modifier, showScreenIntro: Boolean) {
        Column(
            modifier = columnModifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showScreenIntro) {
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
            }
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
                            selectedSubjects.ifEmpty { listOf(SubjectMode.GENERAL) },
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

    if (embedInShell) {
        CheckInBody(
            columnModifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            showScreenIntro = false,
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            CheckInBody(
                columnModifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 24.dp),
                showScreenIntro = true,
            )
        }
    }
}
