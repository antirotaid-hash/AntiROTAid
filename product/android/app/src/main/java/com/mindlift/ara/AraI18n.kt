package com.mindlift.ara


/**
 * App language. The user picks one on first launch (before consent) and can
 * change it anytime in Settings. The choice is persisted in [AraStore] and
 * provided down the tree through [LocalTr] so every screen re-composes when it
 * changes — no Activity restart needed.
 *
 * NOTE: this translates the app's OWN interface (chrome). The study cards are
 * generated from the user's imported material and stay in that material's
 * language — we don't machine-translate the user's content.
 */
enum class Lang(val code: String, val display: String, val badge: String) {
    RO("ro", "Română", "RO"),
    EN("en", "English", "EN"),
    DE("de", "Deutsch", "DE"),
    FR("fr", "Français", "FR"),
    ES("es", "Español", "ES");

    companion object {
        fun from(code: String?): Lang = entries.firstOrNull { it.code == code } ?: EN
    }
}

/** Every piece of UI text, in one place. One [Tr] instance per language. */
data class Tr(
    // top menu
    val menuSettings: String,
    val menuGuide: String,
    val menuInfo: String,
    // home
    val homeTagline: String,
    val chipOfflineT: String, val chipOfflineS: String,
    val chipStudyT: String, val chipStudyS: String,
    val chipLocalT: String, val chipLocalS: String,
    val heroTitle: String, val heroBody: String,
    val overlayOnTitle: String, val overlayOnSub: String,
    val overlayOffTitle: String, val overlayOffSub: String,
    val examsTitle: String, val examsSub: String,
    val coursesTitle: String, val coursesSub: String,
    val homeFooter: String,
    // language picker (first launch)
    val langPickTitle: String, val langPickLead: String, val langPickContinue: String,
    // consent
    val privacyBrandSub: String,
    val privacyTitle: String, val privacyLead: String,
    val pol1T: String, val pol1B: String,
    val pol2T: String, val pol2B: String,
    val pol3T: String, val pol3B: String,
    val pol4T: String, val pol4B: String,
    val pol5T: String, val pol5B: String,
    val consentAgree: String, val consentContinue: String,
    // honesty warning: shown before consent and again on the Info screen, so the
    // owner can't miss it — the app is a study aid, not a cure for phone habits.
    val warnTitle: String, val warnBody: String,
    // info screen
    val infoTitle: String, val infoLead: String, val infoFooter: String,
    // tutorial (quick guide)
    val guideTitle: String, val guideSkip: String, val guideBack: String,
    val guideNext: String, val guideDone: String,
    val tut0T: String, val tut0B: String,
    val tut1T: String, val tut1B: String,
    val tut2T: String, val tut2B: String,
    val tut3T: String, val tut3B: String,
    val tut4T: String, val tut4B: String,
    // settings
    val settingsLanguage: String, val settingsLanguageSub: String,
    // live coach marks
    val coachNext: String, val coachBack: String, val coachDone: String, val coachStartGuide: String,
    val coachSettings: String, val coachGuide: String, val coachInfo: String,
    val coachOverlay: String, val coachExams: String, val coachCourses: String,
    // overlay prompts + toasts
    val overlayPermPrompt: String, val overlayStarted: String, val overlayStopped: String,
    val toastCourseAdded: String, val toastReadFail: String,
    val cardPreparing: String, val close: String,
    // bottom nav
    val navTheme: String, val navPause: String, val navExit: String,
    val cardRemove: String, val cardRemoveConfirm: String, val resumeHint: String, val passesFmt: String,
    // courses + dates
    val coursesEmpty1: String, val coursesEmpty2: String, val importCourse: String, val importShort: String,
    val learned: String, val learnedPace: String, val learning: String,
    val todayTomorrow: String, val noDate: String, val noDeadline: String,
    val examTomorrowToday: String, val daysLeftFmt: String, val noDatePressChange: String,
    // course detail
    val changeDeadline: String, val confirmAgain: String, val deleteCourse: String,
    val confirmDeletePermanent: String, val privacy100: String, val filesStayPhone: String, val localStorageShort: String,
    // material settings card
    val howCardsAppear: String, val repeats: String, val repeatsSub: String,
    val chTextPlusImage: String, val chTextOnly: String, val chImageOnly: String, val chImagePlusCaption: String,
    val chAuto: String, val chSmall: String, val chMedium: String, val chLarge: String,
    // MOD + GRANULARITATE choice labels and one-line explanations (instructaj).
    val chPdfPages: String, val chPdfParagraphs: String, val chPptxAsIs: String,
    val chDocxParagraphs: String, val chImageAsIs: String,
    val descPdfPages: String, val descPdfParagraphs: String, val descPptxAsIs: String,
    val descDocxParagraphs: String, val descImageAsIs: String,
    val coachModesTitle: String, val coachModesBody: String,
    // exams
    val examsEstimate: String, val examsEmpty: String, val examName: String, val cancel: String,
    val addMaterials: String, val noMaterialsImported: String, val openMaterialsHint: String,
    val timeWarning: String, val deleteExam: String,
    // settings
    val settingsTitle: String, val planLabel: String, val weeklyPhoneTime: String, val weeklyPhoneSub: String,
    val hoursPerWeek: String, val moreExams: String, val combine: String, val oneAtTime: String,
    val combineDesc: String, val oneAtTimeDesc: String,
    val textSize: String, val fontSmall: String, val fontMedium: String, val fontLarge: String, val fontXLarge: String,
    val cardPreview: String, val appTheme: String,
    val privacyInfoTitle: String, val infoFilesT: String, val infoFilesB: String,
    val infoStorageT: String, val infoStorageB: String, val infoNoNetT: String, val infoNoNetB: String,
    val infoStudyT: String, val infoStudyB: String, val appVersionLocal: String,
    val notifTitle: String, val notifText: String,
    // feed
    val feedNoLesson: String, val feedUnderstood: String, val feedScrollNext: String, val feedVisualSlide: String,
    val feedComeBack: String,
    // selection
    val selected: String,
    // courses list + course card
    val cardsSlidesUnit: String, val restoreDemos: String, val libraryCountFmt: String,
    val tagMaterial: String, val tagUrgent: String, val tagImportant: String,
    val courseDetails: String, val details: String, val localMaterial: String,
    val unitCards: String, val unitSlides: String, val daysShortFmt: String,
    val openCards: String, val previewCards: String, val seeAll: String,
    val coursesSavedDevice: String, val coursesNav: String, val cardsLabel: String,
    val courseCardsFmt: String, val visualSlideTag: String,
    // exam card labels
    val examNew: String, val examDefault: String, val pickDateArrow: String,
    val labelData: String, val labelMaterials: String, val labelPlan: String,
    val planFmt: String, val fromCourses: String,
    // course detail
    val detailDeadline: String, val detailProgress: String, val detailFullPasses: String,
    val detailRecommendedDay: String, val cardsPerDayFmt: String, val learnedPaceFmt: String, val cardFallback: String,
    val tap: String, val previewTag: String, val previewTitle: String, val previewBody: String,
    // tutorial mock sample cards
    val mTagAlgebra: String, val mEqTitle: String, val mIdea: String, val mEqIdea1: String, val mEqIdea2: String,
    val mExample: String, val mExamTitle: String, val mDateVal: String, val mPlanVal: String,
    val mCourse1: String, val mCourse2: String, val mNeural: String, val mBalanceTitle: String, val mBalanceBody: String,
    // per-screen coach callouts
    val cCoursesImport: String, val cCoursesCard: String,
    val cExamsNew: String, val cExamsCard: String,
    val cSetLang: String, val cSetPlan: String, val cSetLook: String,
    // import errors
    val errTitle: String, val errGenerate: String, val errTransform: String,
    // NOTE: file-validation messages (errInvalidFile, errCorruptFile, …) and
    // noticeDeckReset are NOT constructor params — the Tr primary constructor is at
    // the JVM/Dalvik argument limit. They live as byLocale extension properties
    // near the bottom of this file (Android Update 1, Phase 8).
    // ISO code used for locale-aware date formatting (month names etc.)
    val localeCode: String,
    // guide chooser (Guide button)
    val guideAskTitle: String, val guideAskLead: String,
    val guideQuick: String, val guideQuickSub: String,
    val guideLive: String, val guideLiveSub: String,
    // tutorial welcome
    val tutWelcomeT: String, val tutWelcomeB: String, val tutStart: String,
    // material-settings option labels (engine sends RO; we localize by key)
    val optAppearance: String, val optCardSize: String,
    val unitSlideW: String, val unitImageW: String, val unitParagraphW: String,
    // playful nag shown with an overlay card, picked at random each appearance —
    // reinforces that the card just interrupted a scroll, not a normal study open.
    val nagMessages: List<String>,
)

private val RO = Tr(
    menuSettings = "Setări", menuGuide = "Ghid", menuInfo = "Info",
    homeTagline = "Transformă. Învață. Recâștigă-ți timpul.",
    chipOfflineT = "Offline", chipOfflineS = "Fără internet",
    chipStudyT = "Study Mode", chipStudyS = "Fără distrageri",
    chipLocalT = "Local only", chipLocalS = "Totul rămâne pe telefon",
    heroTitle = "Transformă lecțiile/cursurile\nîn carduri de studiu scrollabile.",
    heroBody = "Importă lecțiile/cursurile tale, iar AntiROTAid le transformă în carduri clare, concise și ușor de parcurs oricând.",
    overlayOnTitle = "Oprește AntiROTAid Mode", overlayOnSub = "Mod activ — cardurile apar peste alte aplicații",
    overlayOffTitle = "Pornește AntiROTAid Mode", overlayOffSub = "Apasă ca să pornești — apoi îți arată carduri peste alte aplicații cât stai pe telefon",
    examsTitle = "Examenele mele", examsSub = "Examene cu dată, materiale și plan automat de studiu",
    coursesTitle = "Cursurile mele", coursesSub = "Toate materialele tale, ca o bibliotecă",
    homeFooter = "Totul rămâne pe telefon. Detalii la Info.",
    langPickTitle = "Alege limba", langPickLead = "În ce limbă vrei aplicația? O poți schimba oricând din Setări.",
    langPickContinue = "Continuă",
    privacyBrandSub = "AntiROTAid",
    privacyTitle = "Confidențialitate", privacyLead = "Pe scurt: nimic nu pleacă de pe telefonul tău.",
    pol1T = "Ce prelucrează", pol1B = "Doar fișierele de curs pe care le adaugi tu (PDF, PPTX, Word, text, poze) și progresul tău de studiu.",
    pol2T = "Unde rămâne", pol2B = "Totul stă pe acest telefon. Fișierele tale NU sunt trimise nicăieri.",
    pol3T = "Fără internet", pol3B = "Aplicația funcționează complet offline. Nu trimite și nu primește date.",
    pol4T = "Fără cont, fără reclame", pol4B = "Nu cere cont, e-mail sau parolă. Fără reclame. Nu se colectează nimic despre tine.",
    pol5T = "Control total", pol5B = "Când ștergi un curs (sau tot) din aplicație, copia din aplicație și progresul dispar pe loc. Fișierul pe care l-ai descărcat rămâne neatins în telefon — cele două sunt separate.",
    consentAgree = "Am citit și am înțeles.", consentContinue = "Continuă",
    warnTitle = "Disclaimer", warnBody = "AntiROTAid este doar un posibil ajutor — nu înlocuiește școala/facultatea, nu vindecă dependența de telefon și nu garantează note mari. Pentru rezultate reale și note bune, cel mai bine rămâne să mergi la școală/facultate, să înveți, să-ți iei notițe și să participi la activități. Aplicația doar te ajută să recuperezi câteva minute din timpul petrecut pe telefon.",
    infoTitle = "Informații și confidențialitate",
    infoLead = "AntiROTAid îți transformă lecțiile/cursurile în carduri scurte și ți le arată peste alte aplicații cât stai pe telefon — local, offline.",
    infoFooter = "AntiROTAid · versiune locală, fără cont, fără internet.",
    guideTitle = "Ghid rapid", guideSkip = "Sari peste", guideBack = "Înapoi",
    guideNext = "Înainte", guideDone = "Am înțeles",
    tut0T = "Cardurile tale", tut0B = "Materialul devine carduri scurte și clare — un singur concept pe card.",
    tut1T = "Examenele", tut1B = "Creezi un examen cu dată, îi adaugi materiale, iar ritmul de studiu se calculează singur.",
    tut2T = "Biblioteca", tut2B = "Toate lecțiile/cursurile tale stau ordonate, ca o bază de date. Fără termene aici.",
    tut3T = "Peste alte aplicații", tut3B = "Cât stai pe telefon, un card îți apare peste aplicația folosită — înveți în loc să pierzi timpul.",
    tut4T = "Cum pornești cardurile", tut4B = "Apeși butonul mare din ecranul principal ca să pornești AntiROTAid Mode. De atunci, cardurile apar peste ORICE aplicație folosești, cât stai pe telefon — nu doar când dai scroll. Apeși din nou pe același buton și se oprește oricând. Cardul îți arată doar materialul tău: NU citește aplicația de dedesubt și nimic nu pleacă de pe telefon (totul e local). Nu poți alege peste ce aplicații apare — intenționat, din motive de confidențialitate: AntiROTAid nu vede ce faci și nici pe ce site-uri ești.",
    settingsLanguage = "Limbă", settingsLanguageSub = "Limba aplicației",
    coachNext = "Înainte", coachBack = "Înapoi", coachDone = "Gata", coachStartGuide = "Pornește instructajul live",
    coachSettings = "Setări — temă, font, limbă și confidențialitate.",
    coachGuide = "Ghid — redeschide acest instructaj oricând.",
    coachInfo = "Info — ce date folosește aplicația. Nimic nu pleacă de pe telefon.",
    coachOverlay = "Butonul principal — pornește modul care îți arată carduri peste alte aplicații cât stai pe telefon. Apeși din nou și se oprește; totul e local.",
    coachExams = "Examenele mele — adaugi un examen cu dată, pui materiale, iar planul de studiu se face singur.",
    coachCourses = "Cursurile mele — biblioteca ta cu toate materialele, transformate în carduri.",
    overlayPermPrompt = "Permite afișarea peste alte aplicații, apoi apasă din nou Pornește.",
    overlayStarted = "Mod AntiROTAid pornit — cardurile apar peste alte aplicații. Rămâne pornit până apeși Oprește.",
    overlayStopped = "Mod AntiROTAid oprit.",
    toastCourseAdded = "Curs adăugat local.", toastReadFail = "Nu am putut citi fișierul.",
    cardPreparing = "Se pregătește cardul…", close = "Închide",
    navTheme = "Temă", navPause = "Pauză", navExit = "Ieșire",
    cardRemove = "Scoate cardul", cardRemoveConfirm = "Sigur?", resumeHint = "Continuă de unde ai rămas", passesFmt = "Parcurs: %d×",
    coursesEmpty1 = "Nicio lecție/curs încă",
    coursesEmpty2 = "Adaugă o lecție/un curs cu butonul ＋ sau readu cursurile demo ca să vezi cum arată.",
    importCourse = "＋  Adaugă curs", importShort = "＋ Adaugă",
    learned = "învățat", learnedPace = "Ritm învățat", learning = "se învață…",
    todayTomorrow = "azi/mâine", noDate = "fără dată", noDeadline = "Fără deadline",
    examTomorrowToday = "Examen mâine/azi", daysLeftFmt = "%d zile rămase",
    noDatePressChange = "fără dată (apasă Schimbă data)",
    changeDeadline = "Schimbă deadline", confirmAgain = "Sigur? Apasă din nou", deleteCourse = "Șterge cursul",
    confirmDeletePermanent = "Confirmă ștergerea definitivă",
    privacy100 = "Confidențialitate 100%", filesStayPhone = "Fișierele nu părăsesc telefonul.",
    localStorageShort = "Stocare locală",
    howCardsAppear = "⚙  Cum apar cardurile", repeats = "Repetări",
    repeatsSub = "De câte ori să parcurgi tot materialul până la examen.",
    chTextPlusImage = "Text + poză", chTextOnly = "Doar text", chImageOnly = "Doar poză", chImagePlusCaption = "Poză + text",
    chAuto = "Auto", chSmall = "Mic", chMedium = "Mediu", chLarge = "Mare",
    chPdfPages = "Pagini", chPdfParagraphs = "Paragrafe", chPptxAsIs = "Slide-uri",
    chDocxParagraphs = "Paragrafe", chImageAsIs = "Imagini",
    descPdfPages = "Fiecare pagină = un card cu pagina întreagă, derulabilă pe verticală.",
    descPdfParagraphs = "Fiecare paragraf = un card; paragrafele tăiate între pagini se reunesc.",
    descPptxAsIs = "Fiecare slide = un card.",
    descDocxParagraphs = "Fiecare paragraf = un card.",
    descImageAsIs = "Fiecare imagine = un card.",
    coachModesTitle = "Cum se fac cardurile",
    coachModesBody = "Cardurile se fac automat după tipul fișierului. Doar la PDF alegi: Pagini (pagina întreagă, derulabilă) sau Paragrafe. PPTX = un card/slide; Word = un card/paragraf; imagini = un card/imagine.",
    examsEstimate = "Folosesc 10h/săptămână ca estimare. Pune-ți timpul real în Setări pentru un plan exact.",
    examsEmpty = "Niciun examen încă. Creează unul, apoi adaugă-i materiale (PDF/PPTX/Word/text/poze).",
    examName = "Nume examen", cancel = "Anulează", addMaterials = "Adaugă materiale",
    noMaterialsImported = "Niciun material importat încă.",
    openMaterialsHint = "Deschide materialele o dată (Detalii) ca să le număr unitățile și să calculez planul.",
    timeWarning = "⚠ Nu ai timp să parcurgi toată materia de atâtea ori până la examen. Scade numărul de parcurgeri sau mută data mai târziu.",
    deleteExam = "Șterge examenul",
    settingsTitle = "Setări și personalizare", planLabel = "Plan de studiu",
    weeklyPhoneTime = "Cât stai pe telefon, în medie / săptămână",
    weeklyPhoneSub = "Din asta calculez cât de des să apară o unitate, ca să acoperi materia până la examen.",
    hoursPerWeek = "ore / săptămână", moreExams = "Mai multe examene",
    combine = "Îmbinat", oneAtTime = "Un examen odată",
    combineDesc = "Îmbinat: studiezi pentru toate examenele în paralel (mai întâi cele cu deadline apropiat).",
    oneAtTimeDesc = "Un examen odată: termini examenul cel mai apropiat, apoi treci la următorul.",
    textSize = "Dimensiune text", fontSmall = "Mic", fontMedium = "Mediu", fontLarge = "Mare", fontXLarge = "Foarte mare",
    cardPreview = "Previzualizare card", appTheme = "Temă aplicație",
    privacyInfoTitle = "Confidențialitate și informații",
    infoFilesT = "Fișierele rămân pe dispozitiv",
    infoFilesB = "Tot ce imporți este procesat local. Nimic nu pleacă de pe telefon — fără cont, fără cloud, fără server.",
    infoStorageT = "Stocare locală",
    infoStorageB = "Cursurile și progresul sunt salvate doar pe acest telefon și pot fi șterse oricând din Detalii curs.",
    infoNoNetT = "Fără internet",
    infoNoNetB = "Aplicația funcționează 100% offline. Nu cere și nu folosește permisiuni de rețea.",
    infoStudyT = "Study-only",
    infoStudyB = "AntiROTAid îți livrează materialul în carduri scrollabile. Nu promite note — doar te ajută să parcurgi materia mai bine.",
    appVersionLocal = "AntiROTAid · versiune locală",
    notifTitle = "Mod AntiROTAid activ", notifText = "Cardurile pot apărea peste ecran. Apasă Stop ca să oprești.",
    feedNoLesson = "Nicio lecție încărcată.", feedUnderstood = "✓  Am înțeles",
    feedScrollNext = "Scroll pentru cardul următor", feedVisualSlide = "Slide vizual — fără text extractibil.",
    feedComeBack = "↻  Revin la el",
    selected = "selectată",
    cardsSlidesUnit = "carduri / slide-uri", restoreDemos = "Readu cursurile demo", libraryCountFmt = "Biblioteca ta · %d materiale",
    tagMaterial = "MATERIAL", tagUrgent = "CURS URGENT", tagImportant = "CURS IMPORTANT",
    courseDetails = "Detalii curs", details = "Detalii", localMaterial = "material local",
    unitCards = "carduri", unitSlides = "slide-uri", daysShortFmt = "%d zile",
    openCards = "Deschide cardurile", previewCards = "PREVIEW CARDURI", seeAll = "Vezi toate ›",
    coursesSavedDevice = "Cursuri salvate pe device.", coursesNav = "Cursuri", cardsLabel = "Carduri",
    courseCardsFmt = "Cardurile cursului (%d)", visualSlideTag = "▣ Slide vizual",
    examNew = "Examen nou", examDefault = "Examen", pickDateArrow = "Alege data →",
    labelData = "Data", labelMaterials = "Materiale", labelPlan = "Plan",
    planFmt = "o unitate la ~%d min · %d/zi", fromCourses = "＋ Din cursuri",
    detailDeadline = "Termen", detailProgress = "Progres", detailFullPasses = "Parcurgeri complete",
    detailRecommendedDay = "Recomandat / zi", cardsPerDayFmt = "%d carduri", learnedPaceFmt = "~%d carduri/sesiune", cardFallback = "Card",
    tap = "atinge", previewTag = "SLIDE 3 · EXEMPLU", previewTitle = "Rețele neuronale convoluționale", previewBody = "Un strat convoluțional aplică filtre peste imaginea de intrare ca să detecteze trăsături locale. Așa arată un card real cu fontul și tema alese.",
    mTagAlgebra = "ALGEBRĂ", mEqTitle = "Ce este o ecuație", mIdea = "Idee",
    mEqIdea1 = "E ca o balanță: stânga valorează cât dreapta.", mEqIdea2 = "Rezolvi aflând numărul ascuns.",
    mExample = "Exemplu", mExamTitle = "Examen: Algebră", mDateVal = "12 iunie, 10:00", mPlanVal = "o unitate la ~3 min",
    mCourse1 = "Cursul 1 — Ecuații", mCourse2 = "Cursul 2 — Inecuații", mNeural = "Rețele neuronale",
    mBalanceTitle = "Regula echilibrului", mBalanceBody = "Ce faci pe-o parte, faci pe cealaltă.",
    cCoursesImport = "Importă un fișier (PDF/PPTX/Word/text/poze) — devine carduri automat.",
    cCoursesCard = "Fiecare material: Deschide cardurile ca să studiezi, sau Detalii pentru progres și ștergere.",
    cExamsNew = "Creează un examen: îi pui un nume și o dată.",
    cExamsCard = "Adaugi materiale examenului, iar planul de studiu (cât de des să apară o unitate) se calculează singur.",
    cSetLang = "Schimbă limba aplicației oricând — se aplică pe loc.",
    cSetPlan = "Spune-mi câte ore stai pe telefon / săptămână, ca planul să fie exact.",
    cSetLook = "Alege dimensiunea textului și tema — vezi un card de probă mai jos.",
    errTitle = "Nu am putut citi materialul", errGenerate = "Nu am putut genera carduri din material.", errTransform = "Fișierul nu a putut fi transformat în carduri locale.",
    localeCode = "ro",
    guideAskTitle = "Cum vrei ghidul?", guideAskLead = "Alege cum să-ți arăt aplicația.",
    guideQuick = "Ghid rapid", guideQuickSub = "Câteva ecrane cu exemple — pe scurt.",
    guideLive = "Ghid live", guideLiveSub = "Îți arăt fiecare buton chiar pe ecran, pas cu pas.",
    tutWelcomeT = "Bun venit la AntiROTAid", tutWelcomeB = "În câteva ecrane îți arăt ce face aplicația și cum o folosești. Durează 20 de secunde.", tutStart = "Începe",
    optAppearance = "Mod", optCardSize = "Granularitate", unitSlideW = "slide", unitImageW = "poză", unitParagraphW = "paragraf",
    nagMessages = listOf(
        "Salut! Un card rapid și gata.", "Hai să învățăm ceva mic acum.",
        "Un minut pentru tine și mintea ta.", "Pauză bună de scroll — hai la un card.",
        "Doar un card, apoi continui liniștit.", "Mintea ta îți mulțumește pentru asta.",
        "Hai să prindem o idee nouă.", "Un pas mic azi, un zâmbet la examen.",
        "Te-ai descurcat — încă un card?", "Învățăm puțin, fără stres.",
        "Cardul ăsta e ușor, promitem.", "Hai, doar atât. Ești pe drumul bun.",
        "O idee acum, mai mult timp liber mai târziu.", "Respiră și citește un card.",
        "Ești mai aproape de obiectiv ca ieri.", "Un card mic, un creier fericit.",
        "Hai să facem scrollul util.", "Câteva secunde de învățat, merită.",
        "Ușor-ușor, ajungem departe.", "Te susținem — hai la un card.",
        "Un card acum, mândrie mai târziu.", "Minte curioasă? Hai să o hrănim.",
        "Pas cu pas, devii tot mai bun.", "Doar un card și ești liber.",
        "Hai să transformăm minutele în progres.", "Ești capabil de mai mult decât crezi.",
        "Un strop de învățat, dulce ca pauza.", "Hai la treabă ușoară, fără presiune.",
        "Cardul te așteaptă, prietenos ca întotdeauna.", "Încă puțin și prinzi ideea.",
        "Micile reluări fac minuni.", "Hai să bifăm un card împreună.",
        "Creierul tău iubește lucrurile mici și dese.", "Un moment de focus, apoi relaxare.",
        "Ești pe val — continuă cu un card.", "Hai, te ajut să ții minte.",
        "Un card acum face diferența mâine.", "Mergi în ritmul tău, dar hai un pic.",
        "Învățatul scurt e cel mai prietenos.", "Te descurci grozav. Încă unul?",
        "Un card mic, un pas mare.", "Hai să facem mintea să zâmbească.",
        "Doar citește, fără să memorezi forțat.", "Progresul tău se adună, card cu card.",
        "Hai la o gură de cunoaștere.", "Ești aici, deci deja câștigi.",
        "Un card blând, exact cât trebuie.", "Reluăm puțin, ca să rămână.",
        "Hai să-ți faci viitorul mai ușor.", "Mulțumim că revii. Un card?",
    ),
)

private val EN = Tr(
    menuSettings = "Settings", menuGuide = "Guide", menuInfo = "Info",
    homeTagline = "Transform. Learn. Reclaim your time.",
    chipOfflineT = "Offline", chipOfflineS = "No internet",
    chipStudyT = "Study Mode", chipStudyS = "No distractions",
    chipLocalT = "Local only", chipLocalS = "Everything stays on your phone",
    heroTitle = "Turn your course material\ninto scrollable study cards.",
    heroBody = "Import your course files and AntiROTAid turns them into clear, concise cards you can go through anytime.",
    overlayOnTitle = "Stop AntiROTAid Mode", overlayOnSub = "Active — cards appear over other apps",
    overlayOffTitle = "Start AntiROTAid Mode", overlayOffSub = "Tap to start — cards then appear over other apps while you're on your phone",
    examsTitle = "My exams", examsSub = "Exams with a date, materials and an automatic study plan",
    coursesTitle = "My courses", coursesSub = "All your materials, like a library",
    homeFooter = "Everything stays on your phone. Details under Info.",
    langPickTitle = "Choose your language", langPickLead = "Which language do you want the app in? You can change it anytime in Settings.",
    langPickContinue = "Continue",
    privacyBrandSub = "AntiROTAid",
    privacyTitle = "Privacy", privacyLead = "In short: nothing leaves your phone.",
    pol1T = "What it processes", pol1B = "Only the course files you add (PDF, PPTX, Word, text, images) and your study progress.",
    pol2T = "Where it stays", pol2B = "Everything stays on this phone. Your files are NOT sent anywhere.",
    pol3T = "No internet", pol3B = "The app works fully offline. It sends and receives no data.",
    pol4T = "No account, no ads", pol4B = "No account, email or password. No ads. Nothing about you is collected.",
    pol5T = "Full control", pol5B = "When you delete a course (or everything) in the app, the app's copy and your progress are erased instantly. The file you downloaded stays untouched on your phone — the two are separate.",
    consentAgree = "I have read and understood.", consentContinue = "Continue",
    warnTitle = "Disclaimer", warnBody = "AntiROTAid is only a possible helper — it does not replace school, it does not cure phone addiction, and it does not guarantee good grades. For real results and good marks, the best path is still going to school, studying, taking notes and joining activities. The app only helps you reclaim a few minutes of your phone time.",
    infoTitle = "Info and privacy",
    infoLead = "AntiROTAid turns your courses into short cards and shows them over other apps while you're on your phone — local, offline.",
    infoFooter = "AntiROTAid · local version, no account, no internet.",
    guideTitle = "Quick guide", guideSkip = "Skip", guideBack = "Back",
    guideNext = "Next", guideDone = "Got it",
    tut0T = "Your cards", tut0B = "Your material becomes short, clear cards — one concept per card.",
    tut1T = "Exams", tut1B = "Create an exam with a date, add materials, and the study pace is computed for you.",
    tut2T = "Library", tut2B = "All your courses sit neatly, like a database. No deadlines here.",
    tut3T = "Over other apps", tut3B = "While you're on your phone, a card appears over the app you're using — you learn instead of wasting time.",
    tut4T = "How to turn cards on", tut4B = "Tap the big button on the home screen to start AntiROTAid Mode. From then on, cards appear over ANY app you use while you're on your phone — not only when scrolling. Tap the same button again to turn it off anytime. The card only shows YOUR material: it does NOT read the app underneath, and nothing leaves your phone (everything is local). You can't choose which apps it shows over — on purpose, for privacy: AntiROTAid doesn't see what you do or which sites you're on.",
    settingsLanguage = "Language", settingsLanguageSub = "App language",
    coachNext = "Next", coachBack = "Back", coachDone = "Done", coachStartGuide = "Start the live walkthrough",
    coachSettings = "Settings — theme, font, language and privacy.",
    coachGuide = "Guide — reopen this walkthrough anytime.",
    coachInfo = "Info — what data the app uses. Nothing leaves your phone.",
    coachOverlay = "The main button — starts the mode that shows you cards over other apps while you're on your phone. Tap again to stop; everything is local.",
    coachExams = "My exams — add an exam with a date, attach materials, and the study plan builds itself.",
    coachCourses = "My courses — your library of all materials, turned into cards.",
    overlayPermPrompt = "Allow display over other apps, then tap Start again.",
    overlayStarted = "AntiROTAid mode on — cards appear over other apps. It stays on until you tap Stop.",
    overlayStopped = "AntiROTAid mode stopped.",
    toastCourseAdded = "Course added locally.", toastReadFail = "Couldn't read the file.",
    cardPreparing = "Preparing the card…", close = "Close",
    navTheme = "Theme", navPause = "Pause", navExit = "Exit",
    cardRemove = "Remove card", cardRemoveConfirm = "Sure?", resumeHint = "Continue where you left off", passesFmt = "Done: %d×",
    coursesEmpty1 = "No courses yet",
    coursesEmpty2 = "Add a course with the ＋ button, or bring back the demo courses to see how it looks.",
    importCourse = "＋  Add course", importShort = "＋ Add",
    learned = "learned", learnedPace = "Learned pace", learning = "learning…",
    todayTomorrow = "today/tomorrow", noDate = "no date", noDeadline = "No deadline",
    examTomorrowToday = "Exam tomorrow/today", daysLeftFmt = "%d days left",
    noDatePressChange = "no date (tap Change date)",
    changeDeadline = "Change deadline", confirmAgain = "Sure? Tap again", deleteCourse = "Delete course",
    confirmDeletePermanent = "Confirm permanent deletion",
    privacy100 = "100% private", filesStayPhone = "Files never leave the phone.",
    localStorageShort = "Local storage",
    howCardsAppear = "⚙  How cards appear", repeats = "Repeats",
    repeatsSub = "How many times to go through all the material before the exam.",
    chTextPlusImage = "Text + image", chTextOnly = "Text only", chImageOnly = "Image only", chImagePlusCaption = "Image + text",
    chAuto = "Auto", chSmall = "Small", chMedium = "Medium", chLarge = "Large",
    chPdfPages = "Pages", chPdfParagraphs = "Paragraphs", chPptxAsIs = "Slides",
    chDocxParagraphs = "Paragraphs", chImageAsIs = "Images",
    descPdfPages = "Each page = one card with the whole page, scrolled vertically.",
    descPdfParagraphs = "Each paragraph = one card; paragraphs split across pages are rejoined.",
    descPptxAsIs = "Each slide = one card.",
    descDocxParagraphs = "Each paragraph = one card.",
    descImageAsIs = "Each image = one card.",
    coachModesTitle = "How cards are made",
    coachModesBody = "Cards are made automatically by file type. Only PDF lets you pick: Pages (whole page, scrollable) or Paragraphs. PPTX = one card/slide; Word = one card/paragraph; images = one card/image.",
    examsEstimate = "Using 10h/week as an estimate. Set your real time in Settings for an exact plan.",
    examsEmpty = "No exams yet. Create one, then add materials to it (PDF/PPTX/Word/text/images).",
    examName = "Exam name", cancel = "Cancel", addMaterials = "Add materials",
    noMaterialsImported = "No materials imported yet.",
    openMaterialsHint = "Open the materials once (Details) so I can count their units and compute the plan.",
    timeWarning = "⚠ There isn't time to cover the whole material that many times before the exam. Lower the number of passes or move the date later.",
    deleteExam = "Delete exam",
    settingsTitle = "Settings and personalization", planLabel = "Study plan",
    weeklyPhoneTime = "How long you're on the phone, on average / week",
    weeklyPhoneSub = "From this I work out how often a unit should appear, so you cover the material by the exam.",
    hoursPerWeek = "hours / week", moreExams = "Multiple exams",
    combine = "Combined", oneAtTime = "One exam at a time",
    combineDesc = "Combined: you study for all exams in parallel (the nearest deadlines first).",
    oneAtTimeDesc = "One at a time: you finish the nearest exam, then move to the next.",
    textSize = "Text size", fontSmall = "Small", fontMedium = "Medium", fontLarge = "Large", fontXLarge = "Extra large",
    cardPreview = "Card preview", appTheme = "App theme",
    privacyInfoTitle = "Privacy and information",
    infoFilesT = "Files stay on the device",
    infoFilesB = "Everything you import is processed locally. Nothing leaves the phone — no account, no cloud, no server.",
    infoStorageT = "Local storage",
    infoStorageB = "Courses and progress are saved only on this phone and can be deleted anytime from Course details.",
    infoNoNetT = "No internet",
    infoNoNetB = "The app works 100% offline. It requests and uses no network permissions.",
    infoStudyT = "Study-only",
    infoStudyB = "AntiROTAid delivers your material as scrollable cards. It promises no grades — it just helps you get through the material better.",
    appVersionLocal = "AntiROTAid · local version",
    notifTitle = "AntiROTAid mode active", notifText = "Cards may appear over the screen. Tap Stop to stop.",
    feedNoLesson = "No lesson loaded.", feedUnderstood = "✓  Got it",
    feedScrollNext = "Scroll for the next card", feedVisualSlide = "Visual slide — no extractable text.",
    feedComeBack = "↻  Come back to it",
    selected = "selected",
    cardsSlidesUnit = "cards / slides", restoreDemos = "Bring back demo courses", libraryCountFmt = "Your library · %d materials",
    tagMaterial = "MATERIAL", tagUrgent = "URGENT COURSE", tagImportant = "IMPORTANT COURSE",
    courseDetails = "Course details", details = "Details", localMaterial = "local material",
    unitCards = "cards", unitSlides = "slides", daysShortFmt = "%d days",
    openCards = "Open the cards", previewCards = "CARD PREVIEW", seeAll = "See all ›",
    coursesSavedDevice = "Courses saved on device.", coursesNav = "Courses", cardsLabel = "Cards",
    courseCardsFmt = "Course cards (%d)", visualSlideTag = "▣ Visual slide",
    examNew = "New exam", examDefault = "Exam", pickDateArrow = "Pick the date →",
    labelData = "Date", labelMaterials = "Materials", labelPlan = "Plan",
    planFmt = "one unit every ~%d min · %d/day", fromCourses = "＋ From courses",
    detailDeadline = "Deadline", detailProgress = "Progress", detailFullPasses = "Full passes",
    detailRecommendedDay = "Recommended / day", cardsPerDayFmt = "%d cards", learnedPaceFmt = "~%d cards/session", cardFallback = "Card",
    tap = "tap", previewTag = "SLIDE 3 · EXAMPLE", previewTitle = "Convolutional neural networks", previewBody = "A convolutional layer applies filters over the input image to detect local features. This is what a real card looks like with the chosen font and theme.",
    mTagAlgebra = "ALGEBRA", mEqTitle = "What is an equation", mIdea = "Idea",
    mEqIdea1 = "It's like a balance: the left equals the right.", mEqIdea2 = "You solve it by finding the hidden number.",
    mExample = "Example", mExamTitle = "Exam: Algebra", mDateVal = "June 12, 10:00", mPlanVal = "one unit every ~3 min",
    mCourse1 = "Course 1 — Equations", mCourse2 = "Course 2 — Inequalities", mNeural = "Neural networks",
    mBalanceTitle = "The balance rule", mBalanceBody = "What you do to one side, you do to the other.",
    cCoursesImport = "Import a file (PDF/PPTX/Word/text/images) — it becomes cards automatically.",
    cCoursesCard = "Each material: Open the cards to study, or Details for progress and deletion.",
    cExamsNew = "Create an exam: give it a name and a date.",
    cExamsCard = "Add materials to the exam, and the study plan (how often a unit appears) is computed for you.",
    cSetLang = "Change the app language anytime — it applies instantly.",
    cSetPlan = "Tell me how many hours you're on the phone / week, so the plan is exact.",
    cSetLook = "Choose the text size and theme — see a sample card below.",
    errTitle = "Couldn't read the material", errGenerate = "Couldn't generate cards from the material.", errTransform = "The file couldn't be turned into local cards.",
    localeCode = "en",
    guideAskTitle = "How do you want the guide?", guideAskLead = "Choose how I show you the app.",
    guideQuick = "Quick guide", guideQuickSub = "A few screens with examples — short.",
    guideLive = "Live walkthrough", guideLiveSub = "I point at each button right on the screen, step by step.",
    tutWelcomeT = "Welcome to AntiROTAid", tutWelcomeB = "In a few screens I show you what the app does and how to use it. Takes 20 seconds.", tutStart = "Start",
    optAppearance = "Mode", optCardSize = "Granularity", unitSlideW = "slide", unitImageW = "image", unitParagraphW = "paragraph",
    nagMessages = listOf(
        "Hey! Quick card, then back to it.", "Let's learn one small thing now.",
        "A minute for you and your mind.", "Nice scroll break — here's a card.",
        "Just one card, then carry on.", "Your brain says thanks for this.",
        "Let's catch one fresh idea.", "Small step today, big smile at the exam.",
        "You've got this — one more card?", "A little learning, zero stress.",
        "This card's an easy one, promise.", "Just this. You're on the right track.",
        "One idea now, free time later.", "Breathe, and read a card.",
        "You're closer to your goal than yesterday.", "Small card, happy brain.",
        "Let's make this scroll count.", "A few seconds to learn — worth it.",
        "Little by little, we go far.", "We're rooting for you — here's a card.",
        "One card now, pride later.", "Curious mind? Let's feed it.",
        "Step by step, you're getting sharper.", "Just one card and you're free.",
        "Let's turn minutes into progress.", "You're capable of more than you think.",
        "A drop of learning, sweet as a break.", "Easy does it — no pressure here.",
        "The card's waiting, friendly as ever.", "A bit more and the idea clicks.",
        "Tiny reviews work wonders.", "Let's tick off one card together.",
        "Your brain loves small and often.", "A moment of focus, then relax.",
        "You're on a roll — one card.", "Here to help you remember.",
        "A card now makes tomorrow easier.", "Go at your pace, but let's go a little.",
        "Short study is the friendliest study.", "You're doing great. One more?",
        "Small card, big step.", "Let's make your mind smile.",
        "Just read — no forced memorizing.", "Your progress adds up, card by card.",
        "Here's a sip of knowledge.", "You showed up, so you're already winning.",
        "A gentle card, just the right size.", "A little review so it sticks.",
        "Let's make your future easier.", "Thanks for coming back. One card?",
    ),
)

private val DE = Tr(
    menuSettings = "Einstellungen", menuGuide = "Anleitung", menuInfo = "Info",
    homeTagline = "Umwandeln. Lernen. Zeit zurückholen.",
    chipOfflineT = "Offline", chipOfflineS = "Kein Internet",
    chipStudyT = "Lernmodus", chipStudyS = "Keine Ablenkung",
    chipLocalT = "Nur lokal", chipLocalS = "Alles bleibt auf dem Handy",
    heroTitle = "Verwandle dein Kursmaterial\nin scrollbare Lernkarten.",
    heroBody = "Importiere deine Kursdateien und AntiROTAid macht daraus klare, knappe Karten, die du jederzeit durchgehen kannst.",
    overlayOnTitle = "AntiROTAid-Modus stoppen", overlayOnSub = "Aktiv — Karten erscheinen über anderen Apps",
    overlayOffTitle = "AntiROTAid-Modus starten", overlayOffSub = "Zum Starten tippen — dann erscheinen Karten über anderen Apps, während du am Handy bist",
    examsTitle = "Meine Prüfungen", examsSub = "Prüfungen mit Datum, Materialien und automatischem Lernplan",
    coursesTitle = "Meine Kurse", coursesSub = "All deine Materialien, wie eine Bibliothek",
    homeFooter = "Alles bleibt auf deinem Handy. Details unter Info.",
    langPickTitle = "Sprache wählen", langPickLead = "In welcher Sprache willst du die App? Du kannst sie jederzeit in den Einstellungen ändern.",
    langPickContinue = "Weiter",
    privacyBrandSub = "AntiROTAid",
    privacyTitle = "Datenschutz", privacyLead = "Kurz gesagt: nichts verlässt dein Handy.",
    pol1T = "Was verarbeitet wird", pol1B = "Nur die Kursdateien, die du hinzufügst (PDF, PPTX, Word, Text, Bilder), und dein Lernfortschritt.",
    pol2T = "Wo es bleibt", pol2B = "Alles bleibt auf diesem Handy. Deine Dateien werden NIRGENDWO hingesendet.",
    pol3T = "Kein Internet", pol3B = "Die App funktioniert komplett offline. Sie sendet und empfängt keine Daten.",
    pol4T = "Kein Konto, keine Werbung", pol4B = "Kein Konto, keine E-Mail, kein Passwort. Keine Werbung. Es wird nichts über dich gesammelt.",
    pol5T = "Volle Kontrolle", pol5B = "Wenn du einen Kurs (oder alles) in der App löschst, werden die Kopie der App und dein Fortschritt sofort gelöscht. Die heruntergeladene Datei bleibt unberührt auf deinem Handy — beide sind getrennt.",
    consentAgree = "Ich habe es gelesen und verstanden.", consentContinue = "Weiter",
    warnTitle = "Disclaimer", warnBody = "AntiROTAid ist nur eine mögliche Hilfe — sie ersetzt nicht die Schule, heilt keine Handysucht und garantiert keine guten Noten. Für echte Ergebnisse und gute Noten bleibt es am besten, zur Schule zu gehen, zu lernen, Notizen zu machen und an Aktivitäten teilzunehmen. Die App hilft dir nur, ein paar Minuten deiner Handyzeit zurückzuholen.",
    infoTitle = "Info und Datenschutz",
    infoLead = "AntiROTAid macht aus deinen Kursen kurze Karten und zeigt sie über anderen Apps, während du am Handy bist — lokal, offline.",
    infoFooter = "AntiROTAid · lokale Version, kein Konto, kein Internet.",
    guideTitle = "Schnellanleitung", guideSkip = "Überspringen", guideBack = "Zurück",
    guideNext = "Weiter", guideDone = "Verstanden",
    tut0T = "Deine Karten", tut0B = "Dein Material wird zu kurzen, klaren Karten — ein Konzept pro Karte.",
    tut1T = "Prüfungen", tut1B = "Erstelle eine Prüfung mit Datum, füge Materialien hinzu, und das Lerntempo wird für dich berechnet.",
    tut2T = "Bibliothek", tut2B = "All deine Kurse stehen ordentlich da, wie eine Datenbank. Keine Fristen hier.",
    tut3T = "Über anderen Apps", tut3B = "Während du am Handy bist, erscheint eine Karte über der genutzten App — du lernst, statt Zeit zu verschwenden.",
    tut4T = "So schaltest du Karten ein", tut4B = "Tippe auf den großen Knopf im Startbildschirm, um den AntiROTAid-Modus zu starten. Ab dann erscheinen Karten über JEDER App, die du nutzt, während du am Handy bist — nicht nur beim Scrollen. Tippe denselben Knopf erneut, um ihn jederzeit auszuschalten. Die Karte zeigt nur DEIN Material: Sie liest NICHT die App darunter, und nichts verlässt dein Handy (alles ist lokal). Du kannst nicht wählen, über welchen Apps sie erscheint — absichtlich, aus Datenschutzgründen: AntiROTAid sieht nicht, was du tust oder auf welchen Seiten du bist.",
    settingsLanguage = "Sprache", settingsLanguageSub = "App-Sprache",
    coachNext = "Weiter", coachBack = "Zurück", coachDone = "Fertig", coachStartGuide = "Live-Anleitung starten",
    coachSettings = "Einstellungen — Thema, Schrift, Sprache und Datenschutz.",
    coachGuide = "Anleitung — öffne diese Einführung jederzeit erneut.",
    coachInfo = "Info — welche Daten die App nutzt. Nichts verlässt dein Handy.",
    coachOverlay = "Die Haupttaste — startet den Modus, der dir Karten über anderen Apps zeigt, während du am Handy bist. Erneut tippen zum Stoppen; alles ist lokal.",
    coachExams = "Meine Prüfungen — füge eine Prüfung mit Datum hinzu, hänge Materialien an, der Lernplan baut sich selbst.",
    coachCourses = "Meine Kurse — deine Bibliothek mit allen Materialien, in Karten verwandelt.",
    overlayPermPrompt = "Erlaube die Anzeige über anderen Apps und tippe dann erneut auf Starten.",
    overlayStarted = "AntiROTAid-Modus an — Karten erscheinen über anderen Apps. Bleibt an, bis du auf Stoppen tippst.",
    overlayStopped = "AntiROTAid-Modus gestoppt.",
    toastCourseAdded = "Kurs lokal hinzugefügt.", toastReadFail = "Datei konnte nicht gelesen werden.",
    cardPreparing = "Karte wird vorbereitet…", close = "Schließen",
    navTheme = "Thema", navPause = "Pause", navExit = "Beenden",
    cardRemove = "Karte entfernen", cardRemoveConfirm = "Sicher?", resumeHint = "Weiter, wo du aufgehört hast", passesFmt = "Durchgänge: %d×",
    coursesEmpty1 = "Noch keine Kurse",
    coursesEmpty2 = "Füge mit ＋ einen Kurs hinzu oder hole die Demo-Kurse zurück, um zu sehen, wie es aussieht.",
    importCourse = "＋  Kurs hinzufügen", importShort = "＋ Hinzu",
    learned = "gelernt", learnedPace = "Gelerntes Tempo", learning = "wird gelernt…",
    todayTomorrow = "heute/morgen", noDate = "kein Datum", noDeadline = "Keine Frist",
    examTomorrowToday = "Prüfung morgen/heute", daysLeftFmt = "%d Tage übrig",
    noDatePressChange = "kein Datum (tippe auf Datum ändern)",
    changeDeadline = "Frist ändern", confirmAgain = "Sicher? Nochmal tippen", deleteCourse = "Kurs löschen",
    confirmDeletePermanent = "Endgültiges Löschen bestätigen",
    privacy100 = "100% privat", filesStayPhone = "Dateien verlassen das Handy nie.",
    localStorageShort = "Lokaler Speicher",
    howCardsAppear = "⚙  Wie Karten erscheinen", repeats = "Wiederholungen",
    repeatsSub = "Wie oft du den gesamten Stoff bis zur Prüfung durchgehst.",
    chTextPlusImage = "Text + Bild", chTextOnly = "Nur Text", chImageOnly = "Nur Bild", chImagePlusCaption = "Bild + Text",
    chAuto = "Auto", chSmall = "Klein", chMedium = "Mittel", chLarge = "Groß",
    chPdfPages = "Seiten", chPdfParagraphs = "Absätze", chPptxAsIs = "Folien",
    chDocxParagraphs = "Absätze", chImageAsIs = "Bilder",
    descPdfPages = "Jede Seite = eine Karte mit der ganzen Seite, vertikal scrollbar.",
    descPdfParagraphs = "Jeder Absatz = eine Karte; über Seiten getrennte Absätze werden zusammengefügt.",
    descPptxAsIs = "Jede Folie = eine Karte.",
    descDocxParagraphs = "Jeder Absatz = eine Karte.",
    descImageAsIs = "Jedes Bild = eine Karte.",
    coachModesTitle = "Wie Karten erstellt werden",
    coachModesBody = "Karten werden automatisch nach Dateityp erstellt. Nur bei PDF wählst du: Seiten (ganze Seite, scrollbar) oder Absätze. PPTX = eine Karte/Folie; Word = eine Karte/Absatz; Bilder = eine Karte/Bild.",
    examsEstimate = "Schätze mit 10 Std./Woche. Trage deine echte Zeit in den Einstellungen ein für einen genauen Plan.",
    examsEmpty = "Noch keine Prüfungen. Erstelle eine und füge ihr Materialien hinzu (PDF/PPTX/Word/Text/Bilder).",
    examName = "Prüfungsname", cancel = "Abbrechen", addMaterials = "Materialien hinzufügen",
    noMaterialsImported = "Noch keine Materialien importiert.",
    openMaterialsHint = "Öffne die Materialien einmal (Details), damit ich die Einheiten zählen und den Plan berechnen kann.",
    timeWarning = "⚠ Es reicht die Zeit nicht, den ganzen Stoff so oft bis zur Prüfung durchzugehen. Verringere die Durchgänge oder verschiebe das Datum nach hinten.",
    deleteExam = "Prüfung löschen",
    settingsTitle = "Einstellungen und Personalisierung", planLabel = "Lernplan",
    weeklyPhoneTime = "Wie lange du im Schnitt am Handy bist / Woche",
    weeklyPhoneSub = "Daraus berechne ich, wie oft eine Einheit erscheinen soll, damit du den Stoff bis zur Prüfung schaffst.",
    hoursPerWeek = "Std. / Woche", moreExams = "Mehrere Prüfungen",
    combine = "Kombiniert", oneAtTime = "Eine Prüfung nach der anderen",
    combineDesc = "Kombiniert: du lernst für alle Prüfungen parallel (die nächstgelegenen Fristen zuerst).",
    oneAtTimeDesc = "Nacheinander: du schließt die nächste Prüfung ab, dann die nächste.",
    textSize = "Textgröße", fontSmall = "Klein", fontMedium = "Mittel", fontLarge = "Groß", fontXLarge = "Sehr groß",
    cardPreview = "Kartenvorschau", appTheme = "App-Thema",
    privacyInfoTitle = "Datenschutz und Informationen",
    infoFilesT = "Dateien bleiben auf dem Gerät",
    infoFilesB = "Alles, was du importierst, wird lokal verarbeitet. Nichts verlässt das Handy — kein Konto, keine Cloud, kein Server.",
    infoStorageT = "Lokaler Speicher",
    infoStorageB = "Kurse und Fortschritt werden nur auf diesem Handy gespeichert und können jederzeit in den Kursdetails gelöscht werden.",
    infoNoNetT = "Kein Internet",
    infoNoNetB = "Die App funktioniert 100% offline. Sie fordert und nutzt keine Netzwerkberechtigungen.",
    infoStudyT = "Nur Lernen",
    infoStudyB = "AntiROTAid liefert deinen Stoff als scrollbare Karten. Es verspricht keine Noten — es hilft dir nur, den Stoff besser durchzugehen.",
    appVersionLocal = "AntiROTAid · lokale Version",
    notifTitle = "AntiROTAid-Modus aktiv", notifText = "Karten können über dem Bildschirm erscheinen. Tippe auf Stopp zum Beenden.",
    feedNoLesson = "Keine Lektion geladen.", feedUnderstood = "✓  Verstanden",
    feedScrollNext = "Scrolle für die nächste Karte", feedVisualSlide = "Visuelle Folie — kein extrahierbarer Text.",
    feedComeBack = "↻  Später nochmal",
    selected = "ausgewählt",
    cardsSlidesUnit = "Karten / Folien", restoreDemos = "Demo-Kurse zurückholen", libraryCountFmt = "Deine Bibliothek · %d Materialien",
    tagMaterial = "MATERIAL", tagUrgent = "DRINGENDER KURS", tagImportant = "WICHTIGER KURS",
    courseDetails = "Kursdetails", details = "Details", localMaterial = "lokales Material",
    unitCards = "Karten", unitSlides = "Folien", daysShortFmt = "%d Tage",
    openCards = "Karten öffnen", previewCards = "KARTENVORSCHAU", seeAll = "Alle ansehen ›",
    coursesSavedDevice = "Kurse auf dem Gerät gespeichert.", coursesNav = "Kurse", cardsLabel = "Karten",
    courseCardsFmt = "Kurskarten (%d)", visualSlideTag = "▣ Visuelle Folie",
    examNew = "Neue Prüfung", examDefault = "Prüfung", pickDateArrow = "Datum wählen →",
    labelData = "Datum", labelMaterials = "Materialien", labelPlan = "Plan",
    planFmt = "eine Einheit alle ~%d Min · %d/Tag", fromCourses = "＋ Aus Kursen",
    detailDeadline = "Frist", detailProgress = "Fortschritt", detailFullPasses = "Vollständige Durchgänge",
    detailRecommendedDay = "Empfohlen / Tag", cardsPerDayFmt = "%d Karten", learnedPaceFmt = "~%d Karten/Sitzung", cardFallback = "Karte",
    tap = "tippen", previewTag = "FOLIE 3 · BEISPIEL", previewTitle = "Konvolutionale neuronale Netze", previewBody = "Eine Konvolutionsschicht legt Filter über das Eingabebild, um lokale Merkmale zu erkennen. So sieht eine echte Karte mit der gewählten Schrift und dem Thema aus.",
    mTagAlgebra = "ALGEBRA", mEqTitle = "Was ist eine Gleichung", mIdea = "Idee",
    mEqIdea1 = "Wie eine Waage: links ist gleich rechts.", mEqIdea2 = "Du löst sie, indem du die unbekannte Zahl findest.",
    mExample = "Beispiel", mExamTitle = "Prüfung: Algebra", mDateVal = "12. Juni, 10:00", mPlanVal = "eine Einheit alle ~3 Min",
    mCourse1 = "Kurs 1 — Gleichungen", mCourse2 = "Kurs 2 — Ungleichungen", mNeural = "Neuronale Netze",
    mBalanceTitle = "Die Gleichgewichtsregel", mBalanceBody = "Was du auf einer Seite machst, machst du auf der anderen.",
    cCoursesImport = "Importiere eine Datei (PDF/PPTX/Word/Text/Bilder) — sie wird automatisch zu Karten.",
    cCoursesCard = "Jedes Material: Karten öffnen zum Lernen, oder Details für Fortschritt und Löschen.",
    cExamsNew = "Erstelle eine Prüfung: gib ihr einen Namen und ein Datum.",
    cExamsCard = "Füge der Prüfung Materialien hinzu, und der Lernplan (wie oft eine Einheit erscheint) wird für dich berechnet.",
    cSetLang = "Ändere die App-Sprache jederzeit — sie wird sofort übernommen.",
    cSetPlan = "Sag mir, wie viele Stunden du pro Woche am Handy bist, damit der Plan genau ist.",
    cSetLook = "Wähle Textgröße und Thema — sieh unten eine Beispielkarte.",
    errTitle = "Material konnte nicht gelesen werden", errGenerate = "Aus dem Material konnten keine Karten erzeugt werden.", errTransform = "Die Datei konnte nicht in lokale Karten umgewandelt werden.",
    localeCode = "de",
    guideAskTitle = "Wie möchtest du die Anleitung?", guideAskLead = "Wähle, wie ich dir die App zeige.",
    guideQuick = "Schnellanleitung", guideQuickSub = "Ein paar Screens mit Beispielen — kurz.",
    guideLive = "Live-Anleitung", guideLiveSub = "Ich zeige dir jeden Button direkt auf dem Bildschirm, Schritt für Schritt.",
    tutWelcomeT = "Willkommen bei AntiROTAid", tutWelcomeB = "In wenigen Screens zeige ich dir, was die App macht und wie du sie nutzt. Dauert 20 Sekunden.", tutStart = "Los geht's",
    optAppearance = "Modus", optCardSize = "Granularität", unitSlideW = "Folie", unitImageW = "Bild", unitParagraphW = "Absatz",
    nagMessages = listOf(
        "Hi! Eine schnelle Karte, dann weiter.", "Lass uns kurz etwas Kleines lernen.",
        "Eine Minute für dich und deinen Kopf.", "Schöne Scroll-Pause — hier eine Karte.",
        "Nur eine Karte, dann mach weiter.", "Dein Gehirn sagt danke.",
        "Fangen wir eine frische Idee ein.", "Kleiner Schritt heute, Lächeln in der Prüfung.",
        "Du schaffst das — noch eine Karte?", "Ein bisschen lernen, null Stress.",
        "Diese Karte ist leicht, versprochen.", "Nur das. Du bist auf dem richtigen Weg.",
        "Eine Idee jetzt, Freizeit später.", "Atme durch und lies eine Karte.",
        "Du bist deinem Ziel näher als gestern.", "Kleine Karte, glückliches Gehirn.",
        "Machen wir das Scrollen sinnvoll.", "Ein paar Sekunden lernen — lohnt sich.",
        "Stück für Stück kommen wir weit.", "Wir drücken dir die Daumen — eine Karte.",
        "Eine Karte jetzt, Stolz später.", "Neugieriger Kopf? Füttern wir ihn.",
        "Schritt für Schritt wirst du besser.", "Nur eine Karte und du bist frei.",
        "Machen wir Minuten zu Fortschritt.", "Du kannst mehr, als du denkst.",
        "Ein Tropfen Wissen, süß wie eine Pause.", "Ganz entspannt — kein Druck.",
        "Die Karte wartet, freundlich wie immer.", "Noch ein bisschen und es klickt.",
        "Kleine Wiederholungen wirken Wunder.", "Haken wir eine Karte zusammen ab.",
        "Dein Gehirn liebt klein und oft.", "Ein Moment Fokus, dann entspannen.",
        "Du bist im Flow — eine Karte.", "Wir helfen dir beim Merken.",
        "Eine Karte jetzt macht morgen leichter.", "Geh dein Tempo, aber lass uns kurz.",
        "Kurzes Lernen ist das freundlichste.", "Du machst das super. Noch eine?",
        "Kleine Karte, großer Schritt.", "Bringen wir deinen Kopf zum Lächeln.",
        "Einfach lesen — kein Auswendig-Zwang.", "Dein Fortschritt summiert sich, Karte für Karte.",
        "Hier ein Schluck Wissen.", "Du bist da, also gewinnst du schon.",
        "Eine sanfte Karte, genau richtig.", "Eine kleine Wiederholung, damit's bleibt.",
        "Machen wir deine Zukunft leichter.", "Danke, dass du zurück bist. Eine Karte?",
    ),
)

private val FR = Tr(
    menuSettings = "Réglages", menuGuide = "Guide", menuInfo = "Infos",
    homeTagline = "Transforme. Apprends. Récupère ton temps.",
    chipOfflineT = "Hors ligne", chipOfflineS = "Sans internet",
    chipStudyT = "Mode étude", chipStudyS = "Sans distractions",
    chipLocalT = "Local uniquement", chipLocalS = "Tout reste sur le téléphone",
    heroTitle = "Transforme ton support de cours\nen cartes d'étude défilables.",
    heroBody = "Importe tes fichiers de cours et AntiROTAid les transforme en cartes claires et concises, à parcourir à tout moment.",
    overlayOnTitle = "Arrêter le mode AntiROTAid", overlayOnSub = "Actif — les cartes apparaissent par-dessus les autres applis",
    overlayOffTitle = "Lancer le mode AntiROTAid", overlayOffSub = "Appuie pour lancer — des cartes apparaîtront alors par-dessus les autres applis pendant que tu es sur ton téléphone",
    examsTitle = "Mes examens", examsSub = "Examens avec date, supports et plan d'étude automatique",
    coursesTitle = "Mes cours", coursesSub = "Tous tes supports, comme une bibliothèque",
    homeFooter = "Tout reste sur ton téléphone. Détails dans Infos.",
    langPickTitle = "Choisis ta langue", langPickLead = "Dans quelle langue veux-tu l'appli ? Tu peux la changer à tout moment dans les Réglages.",
    langPickContinue = "Continuer",
    privacyBrandSub = "AntiROTAid",
    privacyTitle = "Confidentialité", privacyLead = "En bref : rien ne quitte ton téléphone.",
    pol1T = "Ce qui est traité", pol1B = "Seulement les fichiers de cours que tu ajoutes (PDF, PPTX, Word, texte, images) et ta progression.",
    pol2T = "Où ça reste", pol2B = "Tout reste sur ce téléphone. Tes fichiers ne sont envoyés NULLE PART.",
    pol3T = "Sans internet", pol3B = "L'appli fonctionne entièrement hors ligne. Elle n'envoie ni ne reçoit aucune donnée.",
    pol4T = "Sans compte, sans pub", pol4B = "Aucun compte, e-mail ou mot de passe. Pas de pub. Rien n'est collecté sur toi.",
    pol5T = "Contrôle total", pol5B = "Quand tu supprimes un cours (ou tout) dans l'appli, la copie de l'appli et ta progression sont effacées aussitôt. Le fichier que tu as téléchargé reste intact sur ton téléphone — les deux sont séparés.",
    consentAgree = "J'ai lu et compris.", consentContinue = "Continuer",
    warnTitle = "Disclaimer", warnBody = "AntiROTAid n'est qu'une aide possible — elle ne remplace pas l'école, ne guérit pas la dépendance au téléphone et ne garantit pas de bonnes notes. Pour de vrais résultats et de bonnes notes, le mieux reste d'aller en cours, d'étudier, de prendre des notes et de participer aux activités. L'appli t'aide seulement à récupérer quelques minutes de ton temps passé sur le téléphone.",
    infoTitle = "Infos et confidentialité",
    infoLead = "AntiROTAid transforme tes cours en cartes courtes et te les montre par-dessus les autres applis pendant que tu es sur ton téléphone — local, hors ligne.",
    infoFooter = "AntiROTAid · version locale, sans compte, sans internet.",
    guideTitle = "Guide rapide", guideSkip = "Passer", guideBack = "Retour",
    guideNext = "Suivant", guideDone = "Compris",
    tut0T = "Tes cartes", tut0B = "Ton support devient des cartes courtes et claires — un seul concept par carte.",
    tut1T = "Les examens", tut1B = "Tu crées un examen avec une date, tu ajoutes des supports, et le rythme d'étude se calcule tout seul.",
    tut2T = "La bibliothèque", tut2B = "Tous tes cours sont rangés, comme une base de données. Pas d'échéances ici.",
    tut3T = "Par-dessus les applis", tut3B = "Pendant que tu es sur ton téléphone, une carte apparaît par-dessus l'appli utilisée — tu apprends au lieu de perdre ton temps.",
    tut4T = "Comment activer les cartes", tut4B = "Appuie sur le grand bouton de l'écran d'accueil pour lancer le mode AntiROTAid. Dès lors, les cartes apparaissent par-dessus N'IMPORTE QUELLE appli que tu utilises pendant que tu es sur ton téléphone — pas seulement en scrollant. Appuie de nouveau sur le même bouton pour l'arrêter à tout moment. La carte ne montre que TON matériel : elle NE lit PAS l'appli en dessous, et rien ne quitte ton téléphone (tout est local). Tu ne peux pas choisir sur quelles applis elle apparaît — exprès, pour la confidentialité : AntiROTAid ne voit pas ce que tu fais ni sur quels sites tu es.",
    settingsLanguage = "Langue", settingsLanguageSub = "Langue de l'appli",
    coachNext = "Suivant", coachBack = "Retour", coachDone = "Terminé", coachStartGuide = "Lancer la visite guidée",
    coachSettings = "Réglages — thème, police, langue et confidentialité.",
    coachGuide = "Guide — rouvre cette visite à tout moment.",
    coachInfo = "Infos — quelles données l'appli utilise. Rien ne quitte ton téléphone.",
    coachOverlay = "Le bouton principal — lance le mode qui t'affiche des cartes par-dessus les autres applis pendant que tu es sur ton téléphone. Réappuie pour arrêter ; tout est local.",
    coachExams = "Mes examens — ajoute un examen avec une date, attache des supports, le plan d'étude se construit seul.",
    coachCourses = "Mes cours — ta bibliothèque de tous les supports, transformés en cartes.",
    overlayPermPrompt = "Autorise l'affichage par-dessus les autres applis, puis appuie de nouveau sur Lancer.",
    overlayStarted = "Mode AntiROTAid activé — les cartes apparaissent par-dessus les autres applis. Reste actif jusqu'à ce que tu appuies sur Arrêter.",
    overlayStopped = "Mode AntiROTAid arrêté.",
    toastCourseAdded = "Cours ajouté en local.", toastReadFail = "Impossible de lire le fichier.",
    cardPreparing = "Préparation de la carte…", close = "Fermer",
    navTheme = "Thème", navPause = "Pause", navExit = "Quitter",
    cardRemove = "Retirer la carte", cardRemoveConfirm = "Sûr ?", resumeHint = "Reprends où tu t'es arrêté", passesFmt = "Parcouru : %d×",
    coursesEmpty1 = "Aucun cours pour l'instant",
    coursesEmpty2 = "Ajoute un cours avec le bouton ＋, ou ramène les cours de démo pour voir le rendu.",
    importCourse = "＋  Ajouter un cours", importShort = "＋ Ajouter",
    learned = "appris", learnedPace = "Rythme appris", learning = "en cours…",
    todayTomorrow = "aujourd'hui/demain", noDate = "sans date", noDeadline = "Sans échéance",
    examTomorrowToday = "Examen demain/aujourd'hui", daysLeftFmt = "%d jours restants",
    noDatePressChange = "sans date (appuie sur Changer la date)",
    changeDeadline = "Changer l'échéance", confirmAgain = "Sûr ? Appuie encore", deleteCourse = "Supprimer le cours",
    confirmDeletePermanent = "Confirmer la suppression définitive",
    privacy100 = "100% privé", filesStayPhone = "Les fichiers ne quittent jamais le téléphone.",
    localStorageShort = "Stockage local",
    howCardsAppear = "⚙  Comment apparaissent les cartes", repeats = "Répétitions",
    repeatsSub = "Combien de fois parcourir tout le support avant l'examen.",
    chTextPlusImage = "Texte + image", chTextOnly = "Texte seul", chImageOnly = "Image seule", chImagePlusCaption = "Image + texte",
    chAuto = "Auto", chSmall = "Petit", chMedium = "Moyen", chLarge = "Grand",
    chPdfPages = "Pages", chPdfParagraphs = "Paragraphes", chPptxAsIs = "Diapos",
    chDocxParagraphs = "Paragraphes", chImageAsIs = "Images",
    descPdfPages = "Chaque page = une carte avec la page entière, défilement vertical.",
    descPdfParagraphs = "Chaque paragraphe = une carte ; les paragraphes coupés entre pages sont réunis.",
    descPptxAsIs = "Chaque diapo = une carte.",
    descDocxParagraphs = "Chaque paragraphe = une carte.",
    descImageAsIs = "Chaque image = une carte.",
    coachModesTitle = "Comment les cartes sont créées",
    coachModesBody = "Les cartes sont créées automatiquement selon le type de fichier. Seul le PDF te laisse choisir : Pages (page entière, défilante) ou Paragraphes. PPTX = une carte/diapo ; Word = une carte/paragraphe ; images = une carte/image.",
    examsEstimate = "J'utilise 10h/semaine comme estimation. Mets ton temps réel dans les Réglages pour un plan exact.",
    examsEmpty = "Aucun examen pour l'instant. Crées-en un, puis ajoute-lui des supports (PDF/PPTX/Word/texte/images).",
    examName = "Nom de l'examen", cancel = "Annuler", addMaterials = "Ajouter des supports",
    noMaterialsImported = "Aucun support importé pour l'instant.",
    openMaterialsHint = "Ouvre les supports une fois (Détails) pour que je compte leurs unités et calcule le plan.",
    timeWarning = "⚠ Pas assez de temps pour couvrir toute la matière autant de fois avant l'examen. Réduis le nombre de passages ou repousse la date.",
    deleteExam = "Supprimer l'examen",
    settingsTitle = "Réglages et personnalisation", planLabel = "Plan d'étude",
    weeklyPhoneTime = "Temps passé sur le téléphone, en moyenne / semaine",
    weeklyPhoneSub = "À partir de ça je calcule à quelle fréquence une unité doit apparaître, pour couvrir le support avant l'examen.",
    hoursPerWeek = "heures / semaine", moreExams = "Plusieurs examens",
    combine = "Combiné", oneAtTime = "Un examen à la fois",
    combineDesc = "Combiné : tu étudies pour tous les examens en parallèle (les échéances les plus proches d'abord).",
    oneAtTimeDesc = "Un à la fois : tu finis l'examen le plus proche, puis tu passes au suivant.",
    textSize = "Taille du texte", fontSmall = "Petit", fontMedium = "Moyen", fontLarge = "Grand", fontXLarge = "Très grand",
    cardPreview = "Aperçu de carte", appTheme = "Thème de l'appli",
    privacyInfoTitle = "Confidentialité et informations",
    infoFilesT = "Les fichiers restent sur l'appareil",
    infoFilesB = "Tout ce que tu importes est traité en local. Rien ne quitte le téléphone — pas de compte, pas de cloud, pas de serveur.",
    infoStorageT = "Stockage local",
    infoStorageB = "Les cours et la progression sont enregistrés uniquement sur ce téléphone et peuvent être supprimés à tout moment depuis les Détails du cours.",
    infoNoNetT = "Sans internet",
    infoNoNetB = "L'appli fonctionne 100% hors ligne. Elle ne demande et n'utilise aucune permission réseau.",
    infoStudyT = "Étude uniquement",
    infoStudyB = "AntiROTAid te livre ton support sous forme de cartes défilables. Elle ne promet pas de notes — elle t'aide juste à mieux parcourir le support.",
    appVersionLocal = "AntiROTAid · version locale",
    notifTitle = "Mode AntiROTAid actif", notifText = "Des cartes peuvent apparaître par-dessus l'écran. Appuie sur Stop pour arrêter.",
    feedNoLesson = "Aucune leçon chargée.", feedUnderstood = "✓  Compris",
    feedScrollNext = "Scrolle pour la carte suivante", feedVisualSlide = "Diapo visuelle — pas de texte extractible.",
    feedComeBack = "↻  Y revenir",
    selected = "sélectionné",
    cardsSlidesUnit = "cartes / diapos", restoreDemos = "Ramener les cours de démo", libraryCountFmt = "Ta bibliothèque · %d supports",
    tagMaterial = "SUPPORT", tagUrgent = "COURS URGENT", tagImportant = "COURS IMPORTANT",
    courseDetails = "Détails du cours", details = "Détails", localMaterial = "support local",
    unitCards = "cartes", unitSlides = "diapos", daysShortFmt = "%d jours",
    openCards = "Ouvrir les cartes", previewCards = "APERÇU DES CARTES", seeAll = "Tout voir ›",
    coursesSavedDevice = "Cours enregistrés sur l'appareil.", coursesNav = "Cours", cardsLabel = "Cartes",
    courseCardsFmt = "Cartes du cours (%d)", visualSlideTag = "▣ Diapo visuelle",
    examNew = "Nouvel examen", examDefault = "Examen", pickDateArrow = "Choisir la date →",
    labelData = "Date", labelMaterials = "Supports", labelPlan = "Plan",
    planFmt = "une unité toutes les ~%d min · %d/jour", fromCourses = "＋ Depuis les cours",
    detailDeadline = "Échéance", detailProgress = "Progression", detailFullPasses = "Passages complets",
    detailRecommendedDay = "Recommandé / jour", cardsPerDayFmt = "%d cartes", learnedPaceFmt = "~%d cartes/session", cardFallback = "Carte",
    tap = "appuyer", previewTag = "DIAPO 3 · EXEMPLE", previewTitle = "Réseaux de neurones convolutifs", previewBody = "Une couche convolutive applique des filtres sur l'image d'entrée pour détecter des caractéristiques locales. Voilà à quoi ressemble une vraie carte avec la police et le thème choisis.",
    mTagAlgebra = "ALGÈBRE", mEqTitle = "Qu'est-ce qu'une équation", mIdea = "Idée",
    mEqIdea1 = "C'est comme une balance : la gauche vaut la droite.", mEqIdea2 = "Tu la résous en trouvant le nombre caché.",
    mExample = "Exemple", mExamTitle = "Examen : Algèbre", mDateVal = "12 juin, 10:00", mPlanVal = "une unité toutes les ~3 min",
    mCourse1 = "Cours 1 — Équations", mCourse2 = "Cours 2 — Inéquations", mNeural = "Réseaux de neurones",
    mBalanceTitle = "La règle de l'équilibre", mBalanceBody = "Ce que tu fais d'un côté, tu le fais de l'autre.",
    cCoursesImport = "Importe un fichier (PDF/PPTX/Word/texte/images) — il devient des cartes automatiquement.",
    cCoursesCard = "Chaque support : Ouvrir les cartes pour étudier, ou Détails pour la progression et la suppression.",
    cExamsNew = "Crée un examen : donne-lui un nom et une date.",
    cExamsCard = "Ajoute des supports à l'examen, et le plan d'étude (à quelle fréquence une unité apparaît) se calcule tout seul.",
    cSetLang = "Change la langue de l'appli à tout moment — appliqué immédiatement.",
    cSetPlan = "Dis-moi combien d'heures tu passes sur le téléphone / semaine, pour un plan exact.",
    cSetLook = "Choisis la taille du texte et le thème — vois une carte d'exemple en bas.",
    errTitle = "Impossible de lire le support", errGenerate = "Impossible de générer des cartes à partir du support.", errTransform = "Le fichier n'a pas pu être transformé en cartes locales.",
    localeCode = "fr",
    guideAskTitle = "Comment veux-tu le guide ?", guideAskLead = "Choisis comment je te montre l'appli.",
    guideQuick = "Guide rapide", guideQuickSub = "Quelques écrans avec des exemples — court.",
    guideLive = "Visite guidée", guideLiveSub = "Je pointe chaque bouton directement à l'écran, étape par étape.",
    tutWelcomeT = "Bienvenue sur AntiROTAid", tutWelcomeB = "En quelques écrans je te montre ce que fait l'appli et comment l'utiliser. Ça prend 20 secondes.", tutStart = "Commencer",
    optAppearance = "Mode", optCardSize = "Granularité", unitSlideW = "diapo", unitImageW = "image", unitParagraphW = "paragraphe",
    nagMessages = listOf(
        "Coucou ! Une carte rapide, puis continue.", "Apprenons une petite chose maintenant.",
        "Une minute pour toi et ton esprit.", "Belle pause scroll — voici une carte.",
        "Juste une carte, puis continue.", "Ton cerveau te dit merci.",
        "Attrapons une idée fraîche.", "Petit pas aujourd'hui, sourire à l'examen.",
        "Tu gères — une carte de plus ?", "Un peu d'apprentissage, zéro stress.",
        "Cette carte est facile, promis.", "Juste ça. Tu es sur la bonne voie.",
        "Une idée maintenant, du temps libre après.", "Respire et lis une carte.",
        "Tu es plus près du but qu'hier.", "Petite carte, cerveau content.",
        "Rendons ce scroll utile.", "Quelques secondes pour apprendre — ça vaut le coup.",
        "Petit à petit, on va loin.", "On te soutient — voici une carte.",
        "Une carte maintenant, fierté plus tard.", "Esprit curieux ? Nourrissons-le.",
        "Pas à pas, tu deviens plus affûté.", "Juste une carte et tu es libre.",
        "Transformons les minutes en progrès.", "Tu es capable de plus que tu crois.",
        "Une goutte de savoir, douce comme une pause.", "Tout en douceur — aucune pression.",
        "La carte t'attend, sympa comme toujours.", "Encore un peu et l'idée s'éclaire.",
        "Les petites révisions font des merveilles.", "Cochons une carte ensemble.",
        "Ton cerveau aime petit et souvent.", "Un moment de focus, puis détente.",
        "Tu es lancé — une carte.", "On est là pour t'aider à retenir.",
        "Une carte maintenant rend demain plus facile.", "Va à ton rythme, mais avançons un peu.",
        "Apprendre court, c'est le plus sympa.", "Tu t'en sors très bien. Encore une ?",
        "Petite carte, grand pas.", "Faisons sourire ton esprit.",
        "Lis simplement — pas de par-cœur forcé.", "Tes progrès s'accumulent, carte après carte.",
        "Voici une gorgée de savoir.", "Tu es là, donc tu gagnes déjà.",
        "Une carte douce, juste ce qu'il faut.", "Une petite révision pour que ça reste.",
        "Rendons ton avenir plus facile.", "Merci d'être revenu. Une carte ?",
    ),
)

private val ES = Tr(
    menuSettings = "Ajustes", menuGuide = "Guía", menuInfo = "Info",
    homeTagline = "Transforma. Aprende. Recupera tu tiempo.",
    chipOfflineT = "Sin conexión", chipOfflineS = "Sin internet",
    chipStudyT = "Modo estudio", chipStudyS = "Sin distracciones",
    chipLocalT = "Solo local", chipLocalS = "Todo se queda en el teléfono",
    heroTitle = "Convierte el material del curso\nen tarjetas de estudio deslizables.",
    heroBody = "Importa los archivos de tu curso y AntiROTAid los convierte en tarjetas claras y concisas que puedes repasar cuando quieras.",
    overlayOnTitle = "Detener el modo AntiROTAid", overlayOnSub = "Activo — las tarjetas aparecen sobre otras apps",
    overlayOffTitle = "Iniciar el modo AntiROTAid", overlayOffSub = "Toca para iniciar — luego te muestra tarjetas sobre otras apps mientras estás en el teléfono",
    examsTitle = "Mis exámenes", examsSub = "Exámenes con fecha, materiales y plan de estudio automático",
    coursesTitle = "Mis cursos", coursesSub = "Todos tus materiales, como una biblioteca",
    homeFooter = "Todo se queda en tu teléfono. Detalles en Info.",
    langPickTitle = "Elige tu idioma", langPickLead = "¿En qué idioma quieres la app? Puedes cambiarlo cuando quieras en Ajustes.",
    langPickContinue = "Continuar",
    privacyBrandSub = "AntiROTAid",
    privacyTitle = "Privacidad", privacyLead = "En resumen: nada sale de tu teléfono.",
    pol1T = "Qué procesa", pol1B = "Solo los archivos del curso que añadas (PDF, PPTX, Word, texto, imágenes) y tu progreso de estudio.",
    pol2T = "Dónde se queda", pol2B = "Todo se queda en este teléfono. Tus archivos NO se envían a ningún sitio.",
    pol3T = "Sin internet", pol3B = "La app funciona totalmente sin conexión. No envía ni recibe datos.",
    pol4T = "Sin cuenta, sin anuncios", pol4B = "Sin cuenta, correo ni contraseña. Sin anuncios. No se recopila nada sobre ti.",
    pol5T = "Control total", pol5B = "Cuando borras un curso (o todo) en la app, la copia de la app y tu progreso se eliminan al instante. El archivo que descargaste queda intacto en tu teléfono — son cosas separadas.",
    consentAgree = "He leído y entendido.", consentContinue = "Continuar",
    warnTitle = "Disclaimer", warnBody = "AntiROTAid es solo una posible ayuda — no sustituye a la escuela, no cura la adicción al móvil ni garantiza buenas notas. Para resultados reales y buenas notas, lo mejor sigue siendo ir a clase, estudiar, tomar apuntes y participar en las actividades. La app solo te ayuda a recuperar unos minutos del tiempo que pasas en el teléfono.",
    infoTitle = "Información y privacidad",
    infoLead = "AntiROTAid convierte tus cursos en tarjetas cortas y te las muestra sobre otras apps mientras estás en el teléfono — local, sin conexión.",
    infoFooter = "AntiROTAid · versión local, sin cuenta, sin internet.",
    guideTitle = "Guía rápida", guideSkip = "Saltar", guideBack = "Atrás",
    guideNext = "Siguiente", guideDone = "Entendido",
    tut0T = "Tus tarjetas", tut0B = "Tu material se vuelve tarjetas cortas y claras — un concepto por tarjeta.",
    tut1T = "Los exámenes", tut1B = "Creas un examen con fecha, añades materiales, y el ritmo de estudio se calcula solo.",
    tut2T = "La biblioteca", tut2B = "Todos tus cursos están ordenados, como una base de datos. Sin fechas límite aquí.",
    tut3T = "Sobre otras apps", tut3B = "Mientras estás en el teléfono, una tarjeta aparece sobre la app que usas — aprendes en vez de perder el tiempo.",
    tut4T = "Cómo activar las tarjetas", tut4B = "Pulsa el botón grande de la pantalla principal para iniciar el modo AntiROTAid. A partir de ahí, las tarjetas aparecen sobre CUALQUIER app que uses mientras estás en el teléfono — no solo al hacer scroll. Pulsa el mismo botón otra vez para apagarlo cuando quieras. La tarjeta solo muestra TU material: NO lee la app de debajo, y nada sale de tu teléfono (todo es local). No puedes elegir sobre qué apps aparece — a propósito, por privacidad: AntiROTAid no ve lo que haces ni en qué sitios estás.",
    settingsLanguage = "Idioma", settingsLanguageSub = "Idioma de la app",
    coachNext = "Siguiente", coachBack = "Atrás", coachDone = "Listo", coachStartGuide = "Iniciar el recorrido guiado",
    coachSettings = "Ajustes — tema, fuente, idioma y privacidad.",
    coachGuide = "Guía — vuelve a abrir este recorrido cuando quieras.",
    coachInfo = "Info — qué datos usa la app. Nada sale de tu teléfono.",
    coachOverlay = "El botón principal — inicia el modo que te muestra tarjetas sobre otras apps mientras estás en el teléfono. Púlsalo otra vez para parar; todo es local.",
    coachExams = "Mis exámenes — añade un examen con fecha, adjunta materiales, y el plan de estudio se arma solo.",
    coachCourses = "Mis cursos — tu biblioteca con todos los materiales, convertidos en tarjetas.",
    overlayPermPrompt = "Permite mostrar sobre otras apps y luego pulsa Iniciar de nuevo.",
    overlayStarted = "Modo AntiROTAid activado — las tarjetas aparecen sobre otras apps. Sigue activo hasta que pulses Detener.",
    overlayStopped = "Modo AntiROTAid detenido.",
    toastCourseAdded = "Curso añadido localmente.", toastReadFail = "No se pudo leer el archivo.",
    cardPreparing = "Preparando la tarjeta…", close = "Cerrar",
    navTheme = "Tema", navPause = "Pausa", navExit = "Salir",
    cardRemove = "Quitar tarjeta", cardRemoveConfirm = "¿Seguro?", resumeHint = "Continúa donde lo dejaste", passesFmt = "Recorrido: %d×",
    coursesEmpty1 = "Aún no hay cursos",
    coursesEmpty2 = "Añade un curso con el botón ＋, o recupera los cursos de demo para ver cómo se ve.",
    importCourse = "＋  Añadir curso", importShort = "＋ Añadir",
    learned = "aprendido", learnedPace = "Ritmo aprendido", learning = "aprendiendo…",
    todayTomorrow = "hoy/mañana", noDate = "sin fecha", noDeadline = "Sin fecha límite",
    examTomorrowToday = "Examen mañana/hoy", daysLeftFmt = "%d días restantes",
    noDatePressChange = "sin fecha (pulsa Cambiar fecha)",
    changeDeadline = "Cambiar fecha límite", confirmAgain = "¿Seguro? Pulsa otra vez", deleteCourse = "Eliminar curso",
    confirmDeletePermanent = "Confirmar eliminación definitiva",
    privacy100 = "100% privado", filesStayPhone = "Los archivos nunca salen del teléfono.",
    localStorageShort = "Almacenamiento local",
    howCardsAppear = "⚙  Cómo aparecen las tarjetas", repeats = "Repeticiones",
    repeatsSub = "Cuántas veces repasar todo el material antes del examen.",
    chTextPlusImage = "Texto + imagen", chTextOnly = "Solo texto", chImageOnly = "Solo imagen", chImagePlusCaption = "Imagen + texto",
    chAuto = "Auto", chSmall = "Pequeño", chMedium = "Mediano", chLarge = "Grande",
    chPdfPages = "Páginas", chPdfParagraphs = "Párrafos", chPptxAsIs = "Diapositivas",
    chDocxParagraphs = "Párrafos", chImageAsIs = "Imágenes",
    descPdfPages = "Cada página = una tarjeta con la página entera, desplazable vertical.",
    descPdfParagraphs = "Cada párrafo = una tarjeta; los párrafos cortados entre páginas se reúnen.",
    descPptxAsIs = "Cada diapositiva = una tarjeta.",
    descDocxParagraphs = "Cada párrafo = una tarjeta.",
    descImageAsIs = "Cada imagen = una tarjeta.",
    coachModesTitle = "Cómo se crean las tarjetas",
    coachModesBody = "Las tarjetas se crean automáticamente según el tipo de archivo. Solo el PDF te deja elegir: Páginas (página entera, desplazable) o Párrafos. PPTX = una tarjeta/diapositiva; Word = una tarjeta/párrafo; imágenes = una tarjeta/imagen.",
    examsEstimate = "Uso 10h/semana como estimación. Pon tu tiempo real en Ajustes para un plan exacto.",
    examsEmpty = "Aún no hay exámenes. Crea uno y añádele materiales (PDF/PPTX/Word/texto/imágenes).",
    examName = "Nombre del examen", cancel = "Cancelar", addMaterials = "Añadir materiales",
    noMaterialsImported = "Aún no hay materiales importados.",
    openMaterialsHint = "Abre los materiales una vez (Detalles) para que cuente sus unidades y calcule el plan.",
    timeWarning = "⚠ No hay tiempo para cubrir toda la materia tantas veces antes del examen. Baja el número de pasadas o mueve la fecha más adelante.",
    deleteExam = "Eliminar examen",
    settingsTitle = "Ajustes y personalización", planLabel = "Plan de estudio",
    weeklyPhoneTime = "Cuánto tiempo pasas en el teléfono, de media / semana",
    weeklyPhoneSub = "Con eso calculo cada cuánto debe aparecer una unidad, para que cubras el material antes del examen.",
    hoursPerWeek = "horas / semana", moreExams = "Varios exámenes",
    combine = "Combinado", oneAtTime = "Un examen a la vez",
    combineDesc = "Combinado: estudias para todos los exámenes en paralelo (primero los de fecha más cercana).",
    oneAtTimeDesc = "Uno a la vez: terminas el examen más cercano y pasas al siguiente.",
    textSize = "Tamaño del texto", fontSmall = "Pequeño", fontMedium = "Mediano", fontLarge = "Grande", fontXLarge = "Muy grande",
    cardPreview = "Vista previa de tarjeta", appTheme = "Tema de la app",
    privacyInfoTitle = "Privacidad e información",
    infoFilesT = "Los archivos se quedan en el dispositivo",
    infoFilesB = "Todo lo que importas se procesa localmente. Nada sale del teléfono — sin cuenta, sin nube, sin servidor.",
    infoStorageT = "Almacenamiento local",
    infoStorageB = "Los cursos y el progreso se guardan solo en este teléfono y se pueden borrar en cualquier momento desde Detalles del curso.",
    infoNoNetT = "Sin internet",
    infoNoNetB = "La app funciona 100% sin conexión. No pide ni usa permisos de red.",
    infoStudyT = "Solo estudio",
    infoStudyB = "AntiROTAid te entrega el material en tarjetas deslizables. No promete notas — solo te ayuda a repasar el material mejor.",
    appVersionLocal = "AntiROTAid · versión local",
    notifTitle = "Modo AntiROTAid activo", notifText = "Pueden aparecer tarjetas sobre la pantalla. Pulsa Stop para detener.",
    feedNoLesson = "Ninguna lección cargada.", feedUnderstood = "✓  Entendido",
    feedScrollNext = "Desliza para la siguiente tarjeta", feedVisualSlide = "Diapositiva visual — sin texto extraíble.",
    feedComeBack = "↻  Volver a ella",
    selected = "seleccionado",
    cardsSlidesUnit = "tarjetas / diapositivas", restoreDemos = "Recuperar cursos de demo", libraryCountFmt = "Tu biblioteca · %d materiales",
    tagMaterial = "MATERIAL", tagUrgent = "CURSO URGENTE", tagImportant = "CURSO IMPORTANTE",
    courseDetails = "Detalles del curso", details = "Detalles", localMaterial = "material local",
    unitCards = "tarjetas", unitSlides = "diapositivas", daysShortFmt = "%d días",
    openCards = "Abrir las tarjetas", previewCards = "VISTA PREVIA", seeAll = "Ver todo ›",
    coursesSavedDevice = "Cursos guardados en el dispositivo.", coursesNav = "Cursos", cardsLabel = "Tarjetas",
    courseCardsFmt = "Tarjetas del curso (%d)", visualSlideTag = "▣ Diapositiva visual",
    examNew = "Examen nuevo", examDefault = "Examen", pickDateArrow = "Elegir la fecha →",
    labelData = "Fecha", labelMaterials = "Materiales", labelPlan = "Plan",
    planFmt = "una unidad cada ~%d min · %d/día", fromCourses = "＋ De cursos",
    detailDeadline = "Fecha límite", detailProgress = "Progreso", detailFullPasses = "Pasadas completas",
    detailRecommendedDay = "Recomendado / día", cardsPerDayFmt = "%d tarjetas", learnedPaceFmt = "~%d tarjetas/sesión", cardFallback = "Tarjeta",
    tap = "toca", previewTag = "DIAPOSITIVA 3 · EJEMPLO", previewTitle = "Redes neuronales convolucionales", previewBody = "Una capa convolucional aplica filtros sobre la imagen de entrada para detectar características locales. Así se ve una tarjeta real con la fuente y el tema elegidos.",
    mTagAlgebra = "ÁLGEBRA", mEqTitle = "Qué es una ecuación", mIdea = "Idea",
    mEqIdea1 = "Es como una balanza: la izquierda vale lo mismo que la derecha.", mEqIdea2 = "La resuelves hallando el número oculto.",
    mExample = "Ejemplo", mExamTitle = "Examen: Álgebra", mDateVal = "12 de junio, 10:00", mPlanVal = "una unidad cada ~3 min",
    mCourse1 = "Curso 1 — Ecuaciones", mCourse2 = "Curso 2 — Inecuaciones", mNeural = "Redes neuronales",
    mBalanceTitle = "La regla del equilibrio", mBalanceBody = "Lo que haces de un lado, lo haces del otro.",
    cCoursesImport = "Importa un archivo (PDF/PPTX/Word/texto/imágenes) — se convierte en tarjetas automáticamente.",
    cCoursesCard = "Cada material: Abre las tarjetas para estudiar, o Detalles para progreso y borrado.",
    cExamsNew = "Crea un examen: ponle un nombre y una fecha.",
    cExamsCard = "Añade materiales al examen, y el plan de estudio (cada cuánto aparece una unidad) se calcula solo.",
    cSetLang = "Cambia el idioma de la app cuando quieras — se aplica al instante.",
    cSetPlan = "Dime cuántas horas pasas en el teléfono / semana, para que el plan sea exacto.",
    cSetLook = "Elige el tamaño del texto y el tema — mira una tarjeta de ejemplo abajo.",
    errTitle = "No se pudo leer el material", errGenerate = "No se pudieron generar tarjetas del material.", errTransform = "El archivo no se pudo convertir en tarjetas locales.",
    localeCode = "es",
    guideAskTitle = "¿Cómo quieres la guía?", guideAskLead = "Elige cómo te muestro la app.",
    guideQuick = "Guía rápida", guideQuickSub = "Unas pantallas con ejemplos — breve.",
    guideLive = "Recorrido en vivo", guideLiveSub = "Te señalo cada botón en la pantalla, paso a paso.",
    tutWelcomeT = "Bienvenido a AntiROTAid", tutWelcomeB = "En unas pantallas te muestro qué hace la app y cómo usarla. Tarda 20 segundos.", tutStart = "Empezar",
    optAppearance = "Modo", optCardSize = "Granularidad", unitSlideW = "diapositiva", unitImageW = "imagen", unitParagraphW = "párrafo",
    nagMessages = listOf(
        "¡Hola! Una tarjeta rápida y sigues.", "Aprendamos algo pequeño ahora.",
        "Un minuto para ti y tu mente.", "Buena pausa del scroll — aquí una tarjeta.",
        "Solo una tarjeta, y continúas.", "Tu cerebro te lo agradece.",
        "Atrapemos una idea fresca.", "Pequeño paso hoy, sonrisa en el examen.",
        "Tú puedes — ¿una tarjeta más?", "Un poco de estudio, cero estrés.",
        "Esta tarjeta es fácil, lo prometo.", "Solo esto. Vas por buen camino.",
        "Una idea ahora, tiempo libre después.", "Respira y lee una tarjeta.",
        "Estás más cerca de tu meta que ayer.", "Tarjeta pequeña, cerebro feliz.",
        "Hagamos que este scroll valga.", "Unos segundos para aprender — vale la pena.",
        "Poco a poco, llegamos lejos.", "Te apoyamos — aquí una tarjeta.",
        "Una tarjeta ahora, orgullo después.", "¿Mente curiosa? Alimentémosla.",
        "Paso a paso, te vuelves más agudo.", "Solo una tarjeta y eres libre.",
        "Convirtamos minutos en progreso.", "Eres capaz de más de lo que crees.",
        "Una gota de saber, dulce como un descanso.", "Con calma — sin presión.",
        "La tarjeta te espera, amable como siempre.", "Un poco más y la idea encaja.",
        "Los repasos pequeños hacen maravillas.", "Marquemos una tarjeta juntos.",
        "Tu cerebro ama lo pequeño y frecuente.", "Un momento de foco, luego a relajar.",
        "Estás en racha — una tarjeta.", "Aquí para ayudarte a recordar.",
        "Una tarjeta hoy hace el mañana más fácil.", "Ve a tu ritmo, pero avancemos un poco.",
        "Estudiar corto es lo más amable.", "Lo haces genial. ¿Una más?",
        "Tarjeta pequeña, gran paso.", "Hagamos sonreír a tu mente.",
        "Solo lee — sin memorizar a la fuerza.", "Tu progreso suma, tarjeta a tarjeta.",
        "Aquí un sorbo de conocimiento.", "Apareciste, así que ya ganas.",
        "Una tarjeta suave, del tamaño justo.", "Un repaso pequeño para que se quede.",
        "Hagamos tu futuro más fácil.", "Gracias por volver. ¿Una tarjeta?",
    ),
)

fun trFor(lang: Lang): Tr = when (lang) {
    Lang.RO -> RO
    Lang.EN -> EN
    Lang.DE -> DE
    Lang.FR -> FR
    Lang.ES -> ES
}

/** Localized message for a failed import [Envelope]. Never shows the engine's
 *  raw (Romanian-only) fallback to a non-Romanian user: an unknown code maps to
 *  the generic localized "import failed". */
fun importErrorMessage(env: com.mindlift.bridge.Envelope, tr: Tr): String {
    val code = (env as? com.mindlift.bridge.Envelope.Error)?.code
    val lang = Lang.from(tr.localeCode)
    return AraImportMessages.forCode(code, lang) ?: AraImportMessages.importFailed(lang)
}


// ---------------------------------------------------------------------------
// Additional UI strings, added as extension properties keyed by [Tr.localeCode]
// instead of new constructor parameters: the Tr primary constructor is already
// near Dalvik's per-invoke argument-register cap, and pushing it over triggers a
// VerifyError at class init. Keep new copy here.
// ---------------------------------------------------------------------------
private fun <T> Tr.byLocale(ro: T, en: T, de: T, fr: T, es: T): T = when (localeCode) {
    "ro" -> ro; "de" -> de; "fr" -> fr; "es" -> es; else -> en
}

// Home: the "open" affordance on the big section tiles.
val Tr.open: String get() = byLocale("Deschide", "Open", "Öffnen", "Ouvrir", "Abrir")

// File-validation & deck-change messages live in [AraImportMessages] (keyed by
// Lang, independent of the large Tr table) — see that file. Convenience accessors:
val Tr.errInvalidFile: String get() = AraImportMessages.invalidFile(Lang.from(localeCode))
val Tr.errCorruptFile: String get() = AraImportMessages.corruptFile(Lang.from(localeCode))
val Tr.errEmptyDoc: String get() = AraImportMessages.emptyDoc(Lang.from(localeCode))
val Tr.errNoReadableContent: String get() = AraImportMessages.noReadableContent(Lang.from(localeCode))
val Tr.errUnsupportedFormat: String get() = AraImportMessages.unsupportedFormat(Lang.from(localeCode))
val Tr.errFileTooLarge: String get() = AraImportMessages.fileTooLarge(Lang.from(localeCode))
val Tr.errImportFailed: String get() = AraImportMessages.importFailed(Lang.from(localeCode))
val Tr.noticeDeckReset: String get() = AraImportMessages.deckReset(Lang.from(localeCode))

// Card feed bottom actions: advance to the next card, or bookmark this card and
// leave so the deck resumes here next time.
val Tr.feedUnderstoodNext: String get() =
    byLocale("Următorul card", "Next card",
        "Nächste Karte", "Carte suivante", "Siguiente")
// Shown on the LAST card, where the same button finishes instead of advancing.
val Tr.feedDone: String get() =
    byLocale("Am terminat", "Done",
        "Fertig", "Terminé", "Listo")
val Tr.feedStayHere: String get() =
    byLocale("Rămân aici și ies", "Save spot & exit",
        "Merken & beenden", "Garder & quitter", "Guardar y salir")

// Exams: pointer from the "doesn't fit the time" warning to where to fix it.
val Tr.openPlanSettings: String get() =
    byLocale("→ Setări · Plan de studiu", "→ Settings · Study plan",
        "→ Einstellungen · Lernplan", "→ Réglages · Plan d'étude", "→ Ajustes · Plan de estudio")

// Courses: which exams a material belongs to (or that it's unassigned).
val Tr.inExamsLabel: String get() =
    byLocale("În examen:", "In exam:", "In Prüfung:", "Dans l'examen :", "En examen:")
val Tr.notInAnyExam: String get() =
    byLocale("Neatribuit niciunui examen", "Not assigned to any exam",
        "Keiner Prüfung zugeordnet", "Assigné à aucun examen", "Sin examen asignado")

// Sorting controls (courses + exams).
val Tr.sortBy: String get() = byLocale("Sortează", "Sort", "Sortieren", "Trier", "Ordenar")
val Tr.sortName: String get() = byLocale("Nume", "Name", "Name", "Nom", "Nombre")
val Tr.sortRecent: String get() = byLocale("Recente", "Recent", "Neueste", "Récents", "Recientes")
val Tr.sortProgress: String get() = byLocale("Progres", "Progress", "Fortschritt", "Progrès", "Progreso")
val Tr.sortExam: String get() = byLocale("Examen", "Exam", "Prüfung", "Examen", "Examen")
val Tr.sortDate: String get() = byLocale("Dată", "Date", "Datum", "Date", "Fecha")

// Settings: auto-delete-after-deadline.
val Tr.autoDeleteSection: String get() =
    byLocale("Ștergere automată", "Auto-delete", "Automatisch löschen", "Suppression auto", "Borrado automático")
val Tr.autoDeleteTitle: String get() =
    byLocale("Șterge automat după termen", "Auto-delete after deadline",
        "Nach Frist automatisch löschen", "Supprimer après l'échéance", "Borrar tras la fecha límite")
val Tr.autoDeleteSub: String get() = byLocale(
    "Când un examen trece, examenul se șterge; când un curs rămâne fără examene, se șterge și el. Un curs aflat în mai multe examene rămâne până trec toate. Cursurile fără examen nu se ating.",
    "When an exam's date passes it's deleted; a course left with no exams is deleted too. A course in several exams stays until they all pass. Courses with no exam are never touched.",
    "Wenn das Prüfungsdatum vorbei ist, wird die Prüfung gelöscht; ein Kurs ohne Prüfungen wird ebenfalls gelöscht. Ein Kurs in mehreren Prüfungen bleibt, bis alle vorbei sind. Kurse ohne Prüfung bleiben unangetastet.",
    "Quand la date d'un examen est passée, il est supprimé ; un cours sans examen l'est aussi. Un cours dans plusieurs examens reste jusqu'à ce qu'ils soient tous passés. Les cours sans examen ne sont jamais touchés.",
    "Cuando pasa la fecha de un examen, se borra; un curso sin exámenes también se borra. Un curso en varios exámenes permanece hasta que todos pasen. Los cursos sin examen no se tocan.")
val Tr.onWord: String get() = byLocale("Pornit", "On", "An", "Activé", "Activado")
val Tr.offWord: String get() = byLocale("Oprit", "Off", "Aus", "Désactivé", "Desactivado")
val Tr.autoDeleteDialogBody: String get() = byLocale(
    "Examenele cu data trecută și cursurile rămase fără examene vor fi șterse acum. Continui?",
    "Past exams and courses left with no exams will be deleted now. Continue?",
    "Vergangene Prüfungen und Kurse ohne Prüfungen werden jetzt gelöscht. Fortfahren?",
    "Les examens passés et les cours sans examen seront supprimés maintenant. Continuer ?",
    "Los exámenes pasados y los cursos sin exámenes se borrarán ahora. ¿Continuar?")
val Tr.autoDeleteDeleteNow: String get() =
    byLocale("Șterge acum", "Delete now", "Jetzt löschen", "Supprimer", "Borrar ahora")

// Settings · Legal & store links. Labels localized in all five languages;
// the URLs themselves are fixed constants (see AraStudyActivity).
val Tr.legalSection: String get() =
    byLocale("Legal și linkuri", "Legal & links", "Rechtliches & Links", "Légal et liens", "Legal y enlaces")
val Tr.privacyPolicyLabel: String get() =
    byLocale("Politica de confidențialitate", "Privacy Policy",
        "Datenschutzerklärung", "Politique de confidentialité", "Política de privacidad")
val Tr.termsLabel: String get() =
    byLocale("Termeni și condiții", "Terms of Service",
        "Nutzungsbedingungen", "Conditions d’utilisation", "Términos de servicio")
val Tr.rateLabel: String get() =
    byLocale("Evaluează AntiROTAid", "Rate AntiROTAid",
        "AntiROTAid bewerten", "Évaluer AntiROTAid", "Valorar AntiROTAid")
val Tr.linkOpenError: String get() =
    byLocale("Linkul nu a putut fi deschis.", "Could not open the link.",
        "Der Link konnte nicht geöffnet werden.", "Impossible d’ouvrir le lien.", "No se pudo abrir el enlace.")

// ---------------------------------------------------------------------------
// Bundled demo-course titles, keyed by the STABLE demo id (demo_<name>) — NEVER
// derived from the English asset filename. Each of the five demos therefore shows
// a natural title in the current language, and the title switches with the app
// language exactly like the demo body does (see AraDemo.read). An unknown id (a
// future demo not yet listed here) falls back to a readable de-underscored id so a
// card is never blank; the five shipped demos always hit an explicit mapping.
fun Tr.demoTitle(id: String): String = when (id) {
    "demo_algebra"          -> byLocale("Algebră", "Algebra", "Algebra", "Algèbre", "Álgebra")
    "demo_history_original" -> byLocale("Istorie", "History", "Geschichte", "Histoire", "Historia")
    "demo_neural_networks"  -> byLocale("Rețele neuronale", "Neural networks", "Neuronale Netze", "Réseaux de neurones", "Redes neuronales")
    "demo_photosynthesis"   -> byLocale("Fotosinteză", "Photosynthesis", "Photosynthese", "Photosynthèse", "Fotosíntesis")
    "demo_programming"      -> byLocale("Programare", "Programming", "Programmierung", "Programmation", "Programación")
    else -> id.removePrefix("demo_").replace('_', ' ').replaceFirstChar { it.uppercase() }
}

// Localized noun for an engine-built source label such as "Slide 3" / "Page 2".
// The word inside the label is ALWAYS the English marker the demo parser splits on
// ("Slide N:" — kept identical across all five languages so cardification works),
// so we localize only what the user sees on the card chip, never the asset markers.
val Tr.labelSlide: String get() = byLocale("Slide", "Slide", "Folie", "Diapo", "Diapositiva")
val Tr.labelPage: String get() = byLocale("Pagina", "Page", "Seite", "Page", "Página")
val Tr.labelParagraph: String get() = byLocale("Paragraf", "Paragraph", "Absatz", "Paragraphe", "Párrafo")
val Tr.labelImage: String get() = byLocale("Imagine", "Image", "Bild", "Image", "Imagen")

private val UNIT_LABEL_NUM_RE = Regex(
    "^\\s*(slide|page|pagina|pagină|pg|paragraph|paragraf|para|image|imagine|photo|poza|poză)\\s+(\\d+)\\s*$",
    RegexOption.IGNORE_CASE,
)

/** Localize an engine source label ("Slide 3", "Page 2", …) into the current app
 *  language, keeping the number. A real slide/course TITLE — anything that is not a
 *  bare "<marker> <n>" — is returned unchanged, so localized demo titles pass
 *  through untouched. */
fun araLocalizeSourceLabel(tr: Tr, raw: String): String {
    val m = UNIT_LABEL_NUM_RE.find(raw) ?: return raw
    val word = when (m.groupValues[1].lowercase()) {
        "slide" -> tr.labelSlide
        "page", "pagina", "pagină", "pg" -> tr.labelPage
        "paragraph", "paragraf", "para" -> tr.labelParagraph
        else -> tr.labelImage
    }
    return "$word ${m.groupValues[2]}"
}

// ---------------------------------------------------------------------------
// Model (worked-example) exam, seeded once on first run.
// ---------------------------------------------------------------------------
val Tr.examModelName: String get() =
    byLocale("Examen model", "Model exam", "Beispielprüfung", "Examen modèle", "Examen modelo")

// ---------------------------------------------------------------------------
// "?" live walkthrough — extra instructions added everywhere.
// ---------------------------------------------------------------------------
val Tr.feedHelp: String get() = byLocale("Ghid", "Guide", "Hilfe", "Guide", "Guía")

// Card feed (in-study + over other apps)
val Tr.cFeedFont: String get() = byLocale(
    "Mărimea textului — apasă „Aa” ca să faci literele mai mari sau mai mici, pe gustul tău.",
    "Text size — tap “Aa” to make the letters bigger or smaller, to your taste.",
    "Textgröße — tippe „Aa“, um die Schrift größer oder kleiner zu machen.",
    "Taille du texte — appuie sur « Aa » pour agrandir ou réduire les lettres.",
    "Tamaño del texto — toca «Aa» para agrandar o reducir las letras.")
val Tr.cFeedTheme: String get() = byLocale(
    "Tema — apasă „◌” ca să schimbi culorile (închis/deschis). Alege ce-ți odihnește ochii.",
    "Theme — tap “◌” to switch the colours (dark/light). Pick what's easy on your eyes.",
    "Thema — tippe „◌“, um die Farben zu wechseln (dunkel/hell).",
    "Thème — appuie sur « ◌ » pour changer les couleurs (sombre/clair).",
    "Tema — toca «◌» para cambiar los colores (oscuro/claro).")
val Tr.cFeedDelete: String get() = byLocale(
    "Șterge cardul — dacă un card e inutil (o poză, ceva off-topic), apasă „✕” o dată ca să-l armezi și încă o dată ca să-l scoți definitiv din teanc.",
    "Delete card — if a card is useless (an image, something off-topic), tap “✕” once to arm it and again to remove it from the deck for good.",
    "Karte löschen — tippe „✕“ einmal zum Aktivieren und erneut, um sie dauerhaft zu entfernen.",
    "Supprimer la carte — appuie sur « ✕ » une fois pour armer, puis encore pour la retirer définitivement.",
    "Eliminar tarjeta — toca «✕» una vez para armar y otra para quitarla del mazo para siempre.")
val Tr.cFeedProgress: String get() = byLocale(
    "Bara de progres — se umple pe măsură ce înaintezi prin curs. Numărul din dreapta sus îți spune la ce card ești din total.",
    "Progress bar — it fills as you move through the course. The number at the top right tells you which card you're on out of the total.",
    "Fortschrittsbalken — füllt sich, während du den Kurs durchgehst. Die Zahl oben rechts zeigt die aktuelle Karte.",
    "Barre de progression — elle se remplit à mesure que tu avances. Le chiffre en haut à droite indique la carte en cours.",
    "Barra de progreso — se llena a medida que avanzas. El número arriba a la derecha indica la tarjeta actual.")
val Tr.cFeedScroll: String get() = byLocale(
    "Treci de la un card la altul printr-o glisare scurtă în sus sau în jos. Următorul card se vede deja dedesubt, ca să curgă firesc.",
    "Move between cards with a short swipe up or down. The next card already peeks below, so it flows naturally.",
    "Wechsle zwischen Karten mit einem kurzen Wischen nach oben oder unten.",
    "Passe d'une carte à l'autre par un petit glissement vers le haut ou le bas.",
    "Cambia entre tarjetas con un breve deslizamiento arriba o abajo.")
val Tr.cFeedCta: String get() = byLocale(
    "Pe fiecare card ai două butoane: „Am înțeles, următorul” te duce mai departe, iar „Am rămas aici” îți pune semn de carte și închide — revii fix de unde ai rămas.",
    "Each card has two buttons: “Got it, next” moves you on, and “I stayed here” bookmarks the spot and closes — you'll come back exactly where you left off.",
    "Jede Karte hat zwei Tasten: „Verstanden, weiter“ und „Hier geblieben“ (setzt ein Lesezeichen und schließt).",
    "Chaque carte a deux boutons : « Compris, suivant » et « Je suis resté ici » (marque la page et ferme).",
    "Cada tarjeta tiene dos botones: «Entendido, siguiente» y «Me quedé aquí» (marca el punto y cierra).")

// Course detail
val Tr.cDetailStudy: String get() = byLocale(
    "Deschide cardurile — apasă aici ca să începi să studiezi materialul, transformat în carduri scurte.",
    "Open cards — tap here to start studying this material, turned into short cards.",
    "Karten öffnen — tippe hier, um das Material als kurze Karten zu lernen.",
    "Ouvrir les cartes — appuie ici pour étudier ce support sous forme de cartes courtes.",
    "Abrir tarjetas — toca aquí para estudiar este material en tarjetas cortas.")
val Tr.cDetailProgress: String get() = byLocale(
    "Aici vezi cât ai parcurs: câte carduri are materialul, procentul terminat, de câte ori l-ai trecut complet și ritmul tău de învățare.",
    "Here you see your progress: how many cards, percent done, how many full passes you've made, and your learning pace.",
    "Hier siehst du deinen Fortschritt: Kartenanzahl, Prozent, volle Durchgänge und dein Lerntempo.",
    "Tu vois ta progression : nombre de cartes, pourcentage, passages complets et ton rythme.",
    "Aquí ves tu progreso: número de tarjetas, porcentaje, pasadas completas y tu ritmo.")
val Tr.cDetailSettings: String get() = byLocale(
    "Setările materialului — alegi doar cum se taie fișierul în carduri. De câte ori se parcurge se stabilește la examen, nu aici.",
    "Material settings — only choose how the file is split into cards. How many times it's covered is set on the exam, not here.",
    "Material-Einstellungen — nur die Aufteilung der Datei in Karten. Wie oft wiederholt wird, legst du bei der Prüfung fest.",
    "Réglages du support — choisis seulement le découpage en cartes. Le nombre de passages se règle sur l'examen.",
    "Ajustes del material — solo eliges cómo se divide en tarjetas. Cuántas veces se repasa se fija en el examen.")

// ---------------------------------------------------------------------------
// Exam-level coverage knob (replaces per-material repeats) + live readout.
// ---------------------------------------------------------------------------
val Tr.coverageTitle: String get() = byLocale(
    "De câte ori parcurgi materia", "How many times to cover the material",
    "Wie oft den Stoff durchgehen", "Combien de fois couvrir la matière",
    "Cuántas veces cubrir la materia")
val Tr.coverageSub: String get() = byLocale(
    "Pentru tot examenul. Alege de câte ori să apară toată materia până la examen, sau NEDEFINIT și spui tu la câte minute apare un card.",
    "For the whole exam. Pick how many times the whole material appears before the exam, or UNDEFINED and you set how many minutes between cards.",
    "Für die ganze Prüfung. Wähle, wie oft der gesamte Stoff bis zur Prüfung erscheint, oder UNBESTIMMT und du legst die Minuten pro Karte fest.",
    "Pour tout l'examen. Choisis combien de fois toute la matière apparaît avant l'examen, ou INDÉFINI et tu fixes les minutes entre les cartes.",
    "Para todo el examen. Elige cuántas veces aparece toda la materia antes del examen, o INDEFINIDO y tú fijas los minutos entre tarjetas.")
val Tr.coverageUndefined: String get() = byLocale(
    "NEDEFINIT", "UNDEFINED", "UNBESTIMMT", "INDÉFINI", "INDEFINIDO")
val Tr.coverageCountMode: String get() = byLocale(
    "De câte ori", "How many times", "Wie oft", "Combien de fois", "Cuántas veces")
val Tr.coverageCountLabel: String get() = byLocale(
    "de câte ori", "how many times", "wie oft", "combien de fois", "cuántas veces")
// Dropdown menu items + closed-state labels.
val Tr.coverageOnce: String get() = byLocale("O dată", "Once", "Einmal", "Une fois", "Una vez")
fun Tr.coverageNTimes(n: Int): String = byLocale(
    "De $n ori", "$n times", "${n}-mal", "$n fois", "$n veces")
val Tr.coverageOtherNumber: String get() = byLocale(
    "Alt număr…", "Other number…", "Andere Zahl…", "Autre nombre…", "Otro número…")
val Tr.coverageMenuInterval: String get() = byLocale(
    "NEDEFINIT — pun eu minutele", "UNDEFINED — I set the minutes",
    "UNBESTIMMT — ich setze die Minuten", "INDÉFINI — je règle les minutes",
    "INDEFINIDO — yo pongo los minutos")
fun Tr.coverageIntervalClosed(min: Int): String = byLocale(
    "NEDEFINIT · un card la $min min", "UNDEFINED · a card every $min min",
    "UNBESTIMMT · eine Karte alle $min Min", "INDÉFINI · une carte toutes les $min min",
    "INDEFINIDO · una tarjeta cada $min min")
val Tr.coverageIntervalUnset: String get() = byLocale(
    "NEDEFINIT · alege minutele", "UNDEFINED · pick the minutes",
    "UNBESTIMMT · Minuten wählen", "INDÉFINI · choisis les minutes",
    "INDEFINIDO · elige los minutos")
val Tr.coveragePickHint: String get() = byLocale(
    "Apasă ca să alegi", "Tap to choose", "Zum Wählen tippen", "Appuie pour choisir", "Toca para elegir")
// Generic "commit + lock" controls for every numeric field.
val Tr.setBtn: String get() = byLocale("Setează", "Set", "Festlegen", "Définir", "Fijar")
val Tr.editBtn: String get() = byLocale("Schimbă", "Change", "Ändern", "Modifier", "Cambiar")
val Tr.coverageMinLabel: String get() = byLocale(
    "Minute între carduri", "Minutes between cards", "Minuten zwischen Karten",
    "Minutes entre les cartes", "Minutos entre tarjetas")
val Tr.coverageMinHint: String get() = byLocale(
    "Un card nou apare la fiecare câteva minute petrecute pe telefon.",
    "A new card appears every few minutes you spend on the phone.",
    "Eine neue Karte erscheint alle paar Minuten am Handy.",
    "Une nouvelle carte apparaît toutes les quelques minutes passées au téléphone.",
    "Aparece una tarjeta nueva cada pocos minutos que pasas en el móvil.")
val Tr.coverageSetMinutes: String get() = byLocale(
    "Completează la câte minute apare un card.",
    "Fill in how many minutes between cards.",
    "Trag ein, wie viele Minuten zwischen Karten.",
    "Indique combien de minutes entre les cartes.",
    "Indica cuántos minutos entre tarjetas.")
val Tr.coveragePassesLive: String get() = byLocale(
    "La ritmul actual mai poți parcurge toată materia de ~%d ori până la examen.",
    "At the current pace you can still cover the whole material ~%d times before the exam.",
    "Beim aktuellen Tempo schaffst du den ganzen Stoff noch ~%d-mal bis zur Prüfung.",
    "À ce rythme tu peux encore couvrir toute la matière ~%d fois avant l'examen.",
    "A este ritmo aún puedes cubrir toda la materia ~%d veces antes del examen.")
val Tr.coverageNoTimeWhole: String get() = byLocale(
    "Nu mai e timp pentru toată lecția la ritmul ăsta — mărește orele/săptămână sau micșorează intervalul.",
    "No time left for a full pass at this pace — raise your hours/week or shorten the interval.",
    "Keine Zeit mehr für einen ganzen Durchgang — erhöhe Stunden/Woche oder verkürze das Intervall.",
    "Plus le temps pour un passage complet à ce rythme — augmente tes heures/semaine ou réduis l'intervalle.",
    "No queda tiempo para una pasada completa a este ritmo — sube tus horas/semana o acorta el intervalo.")
val Tr.coverageCountLive: String get() = byLocale(
    "Toată materia va fi parcursă de %d ori până la examen.",
    "The whole material will be covered %d times before the exam.",
    "Der ganze Stoff wird bis zur Prüfung %d-mal durchgegangen.",
    "Toute la matière sera couverte %d fois avant l'examen.",
    "Toda la materia se cubrirá %d veces antes del examen.")
