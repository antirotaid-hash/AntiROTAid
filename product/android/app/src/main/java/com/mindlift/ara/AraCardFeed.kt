package com.mindlift.ara

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.absoluteValue

/** AntiROTAid visual system.
 *
 * This is intentionally local/offline UI state only. Theme changes are stored
 * in SharedPreferences by AraStore and never require internet/accounts.
 */
enum class AraThemeStyle(val label: String) {
    DARK_GOLD("Dark Gold"),
    AMOLED_BLACK("AMOLED Black"),
    LIGHT_CLEAN("Light Clean"),
    NEON_FOCUS("Neon Focus"),
    DEEP_BLUE("Deep Blue");

    fun next(): AraThemeStyle = entries[(ordinal + 1) % entries.size]
}

data class AraPalette(
    val bgTop: Color,
    val bgBottom: Color,
    val green: Color,
    val greenDeep: Color,
    val teal: Color,
    val greenSoft: Color,
    val title: Color,
    val body: Color,
    val muted: Color,
    val danger: Color,
    val purple: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassEdge: Color,
    val glassEdgeGreen: Color,
    val glow: Color,
    val glowStrong: Color,
    val orbGreen: Color,
    val orbTeal: Color,
    val orbMint: Color,
)

object AraBrand {
    var activeTheme: AraThemeStyle by mutableStateOf(AraThemeStyle.DARK_GOLD)

    private val darkGold = AraPalette(
        bgTop = Color(0xFF050505), bgBottom = Color(0xFF111111),
        green = Color(0xFFFFC107), greenDeep = Color(0xFFFFD54F), teal = Color(0xFFFF8F00),
        greenSoft = Color(0xFF241B05), title = Color(0xFFF7F7F7), body = Color(0xFFD7D7DA),
        muted = Color(0xFF9A9AA0), danger = Color(0xFFFF6B5E), purple = Color(0xFFFFC107),
        glassTop = Color(0xF1141414), glassBottom = Color(0xF00A0A0A),
        glassEdge = Color(0x66FFFFFF), glassEdgeGreen = Color(0xAAFFC107),
        glow = Color(0x33FFC107), glowStrong = Color(0x66FFC107),
        orbGreen = Color(0x22FFC107), orbTeal = Color(0x18FF8F00), orbMint = Color(0x14333333),
    )

    private val amoled = AraPalette(
        bgTop = Color.Black, bgBottom = Color.Black,
        green = Color(0xFFFFD54F), greenDeep = Color(0xFFFFE082), teal = Color(0xFFB2FF59),
        greenSoft = Color(0xFF151309), title = Color(0xFFFFFFFF), body = Color(0xFFE5E7EB),
        muted = Color(0xFF9CA3AF), danger = Color(0xFFFF5252), purple = Color(0xFFFFD54F),
        glassTop = Color(0xFA080808), glassBottom = Color(0xF5000000),
        glassEdge = Color(0x44FFFFFF), glassEdgeGreen = Color(0x99FFD54F),
        glow = Color(0x22FFD54F), glowStrong = Color(0x55FFD54F),
        orbGreen = Color(0x14FFD54F), orbTeal = Color(0x10000000), orbMint = Color(0x10000000),
    )

    private val lightClean = AraPalette(
        bgTop = Color(0xFFF7FAFC), bgBottom = Color(0xFFEFF6FF),
        green = Color(0xFF2563EB), greenDeep = Color(0xFF1D4ED8), teal = Color(0xFF10B981),
        greenSoft = Color(0xFFEFF6FF), title = Color(0xFF111827), body = Color(0xFF374151),
        muted = Color(0xFF6B7280), danger = Color(0xFFDC2626), purple = Color(0xFF7C3AED),
        glassTop = Color(0xFFFFFFFF), glassBottom = Color(0xFFF8FAFC),
        glassEdge = Color(0xFFFFFFFF), glassEdgeGreen = Color(0x552563EB),
        glow = Color(0x222563EB), glowStrong = Color(0x442563EB),
        orbGreen = Color(0x332563EB), orbTeal = Color(0x3310B981), orbMint = Color(0x66FFFFFF),
    )

    private val neonFocus = AraPalette(
        bgTop = Color(0xFF030712), bgBottom = Color(0xFF0B1020),
        green = Color(0xFF22D3EE), greenDeep = Color(0xFF67E8F9), teal = Color(0xFFA855F7),
        greenSoft = Color(0xFF0C2630), title = Color(0xFFF8FAFC), body = Color(0xFFD1D5DB),
        muted = Color(0xFF94A3B8), danger = Color(0xFFFB7185), purple = Color(0xFFA855F7),
        glassTop = Color(0xF10B1020), glassBottom = Color(0xF0060A14),
        glassEdge = Color(0x44FFFFFF), glassEdgeGreen = Color(0xAA22D3EE),
        glow = Color(0x3322D3EE), glowStrong = Color(0x6622D3EE),
        orbGreen = Color(0x2222D3EE), orbTeal = Color(0x22A855F7), orbMint = Color(0x14333333),
    )

    private val deepBlue = AraPalette(
        bgTop = Color(0xFF06111F), bgBottom = Color(0xFF0B172A),
        green = Color(0xFF38BDF8), greenDeep = Color(0xFF7DD3FC), teal = Color(0xFF60A5FA),
        greenSoft = Color(0xFF0E2A44), title = Color(0xFFF1F5F9), body = Color(0xFFD4DEE9),
        muted = Color(0xFF94A3B8), danger = Color(0xFFFF6B6B), purple = Color(0xFF818CF8),
        glassTop = Color(0xF10F1D33), glassBottom = Color(0xF0081324),
        glassEdge = Color(0x44FFFFFF), glassEdgeGreen = Color(0xAA38BDF8),
        glow = Color(0x3338BDF8), glowStrong = Color(0x6638BDF8),
        orbGreen = Color(0x2238BDF8), orbTeal = Color(0x2260A5FA), orbMint = Color(0x141E293B),
    )

    private val palette: AraPalette
        get() = when (activeTheme) {
            AraThemeStyle.DARK_GOLD -> darkGold
            AraThemeStyle.AMOLED_BLACK -> amoled
            AraThemeStyle.LIGHT_CLEAN -> lightClean
            AraThemeStyle.NEON_FOCUS -> neonFocus
            AraThemeStyle.DEEP_BLUE -> deepBlue
        }

    val bgTop: Color get() = palette.bgTop
    val bgBottom: Color get() = palette.bgBottom
    val green: Color get() = palette.green
    val greenDeep: Color get() = palette.greenDeep
    val teal: Color get() = palette.teal
    val greenSoft: Color get() = palette.greenSoft
    val title: Color get() = palette.title
    val body: Color get() = palette.body
    val muted: Color get() = palette.muted
    val danger: Color get() = palette.danger
    val purple: Color get() = palette.purple
    val glassTop: Color get() = palette.glassTop
    val glassBottom: Color get() = palette.glassBottom
    val glassEdge: Color get() = palette.glassEdge
    val glassEdgeGreen: Color get() = palette.glassEdgeGreen
    val glow: Color get() = palette.glow
    val glowStrong: Color get() = palette.glowStrong
    val orbGreen: Color get() = palette.orbGreen
    val orbTeal: Color get() = palette.orbTeal
    val orbMint: Color get() = palette.orbMint

    val araBrush: Brush get() = Brush.linearGradient(listOf(green, teal))

    /** A fixed representative accent for a theme, independent of the active one
     *  (used by the settings theme picker swatches). */
    fun swatchFor(t: AraThemeStyle): Color = when (t) {
        AraThemeStyle.DARK_GOLD -> Color(0xFFFFC107)
        AraThemeStyle.AMOLED_BLACK -> Color(0xFFFFD54F)
        AraThemeStyle.LIGHT_CLEAN -> Color(0xFF2563EB)
        AraThemeStyle.NEON_FOCUS -> Color(0xFF22D3EE)
        AraThemeStyle.DEEP_BLUE -> Color(0xFF38BDF8)
    }
}

@Composable
fun AraGlassScrim(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Living aurora: the glow orbs drift slowly so the backdrop feels alive.
    // The orbs are soft radial gradients feathered to Transparent, so a light
    // blur (not the previous heavy 80dp) keeps it dreamy while staying smooth.
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(16000, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "auroraPhase",
    )
    fun s(x: Float) = kotlin.math.sin(x)
    fun c(x: Float) = kotlin.math.cos(x)
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AraBrand.bgTop, AraBrand.bgBottom)))) {
        Box(Modifier.fillMaxSize().blur(64.dp)) {
            Box(Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(AraBrand.orbGreen, Color.Transparent),
                    center = Offset(120f + 90f * s(phase), 220f + 70f * c(phase)), radius = 620f)
            ))
            Box(Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(AraBrand.orbTeal, Color.Transparent),
                    center = Offset(950f + 110f * c(phase * 0.8f), 720f + 90f * s(phase * 0.8f)), radius = 760f)
            ))
            Box(Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(AraBrand.orbMint, Color.Transparent),
                    center = Offset(520f + 130f * s(phase * 0.6f + 1f), 1700f + 90f * c(phase * 0.6f)), radius = 840f)
            ))
        }
        content()
    }
}

fun Modifier.araGlass(
    shape: Shape,
    glow: Color = AraBrand.glow,
    glowRadius: androidx.compose.ui.unit.Dp = 22.dp,
): Modifier = this
    .shadow(glowRadius, shape, ambientColor = glow, spotColor = glow)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(AraBrand.glassTop, AraBrand.glassBottom)))
    .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(AraBrand.glassEdge, AraBrand.glassEdgeGreen))), shape)

/** Card title that "decodes" — characters resolve from a scramble into the real
 *  text when the card appears. Sci-fi flavour; settles in well under a second so
 *  it never gets in the way of reading. */
@Composable
private fun DecodeText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    lineHeight: androidx.compose.ui.unit.TextUnit,
) {
    val glyphs = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&/<>*+="
    val anim = remember(text) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(text) {
        anim.snapTo(0f)
        anim.animateTo(1f, androidx.compose.animation.core.tween(540,
            easing = androidx.compose.animation.core.LinearEasing))
    }
    val p = anim.value
    val reveal = (p * text.length).toInt()
    val tick = (p * 1000).toInt()
    val display = buildString {
        text.forEachIndexed { i, ch ->
            if (i < reveal || ch == ' ' || ch == '\n' || ch == '\t') append(ch)
            else append(glyphs[kotlin.math.abs(i * 31 + tick * 7) % glyphs.length])
        }
    }
    Text(
        display,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = color,
        lineHeight = lineHeight,
    )
}

data class AraCard(
    val cardId: String,
    val cardType: String,
    val sourceLabel: String,
    val title: String,
    val body: String,
    val media: JSONObject? = null,
) {
    val isImage: Boolean get() = cardType == "slide_image"

    val imagePath: String
        get() = media?.optString("image_path", "")
            ?.ifBlank { media?.optString("local_path", "") }
            ?.ifBlank { media?.optString("path", "") }
            ?: ""

    val imageBase64: String
        get() = media?.optString("base64", "")
            ?.ifBlank { media?.optString("data_base64", "") }
            ?: ""

    /** For slide-deck PDFs kept on-device: render the exact page with PdfRenderer. */
    val pdfDocumentPath: String
        get() = if (media?.optString("document_type", "") == "pdf")
            media.optString("document_path", "") else ""

    /** 1-based physical PDF page to render for this card (0 when not a PDF slide). */
    val pdfPhysicalPage: Int
        get() = media?.optInt("physical_page", 0) ?: 0
}

enum class AraFont(val label: String, val title: Int, val body: Int) {
    SMALL("Mic", 19, 15),
    MEDIUM("Mediu", 23, 17),
    LARGE("Mare", 27, 20),
    XLARGE("Foarte mare", 31, 23),
}

private fun araCardFrom(c: JSONObject, fallbackIdx: String): AraCard {
    val src = c.optJSONObject("source")
    val label = if (src != null) {
        val l = src.optString("label", "")
        val n = src.optInt("number", -1)
        if (l.isNotBlank() && n >= 0) "$l $n" else l
    } else ""
    val rawTitle = if (src != null && !src.isNull("title")) src.optString("title", "") else ""
    val answer = c.optString("answer", "")
    return AraCard(
        cardId = c.optString("card_id", "card_$fallbackIdx"),
        cardType = c.optString("card_type", "lesson_chunk"),
        // Blank when the engine gives no label/question — the display layer then
        // localizes it (araLocalizeSourceLabel / tr.cardFallback), so no hardcoded
        // English "Card" ever leaks onto a non-English card.
        sourceLabel = label.ifBlank { c.optString("question", "") },
        title = if (rawTitle == "null") "" else rawTitle,
        body = answer,
        media = c.optJSONObject("media"),
    )
}

fun parseAraFeed(feed: JSONObject): List<AraCard> {
    val out = ArrayList<AraCard>()
    val pages = feed.optJSONArray("pages") ?: return out
    for (i in 0 until pages.length()) {
        val cards = pages.optJSONObject(i)?.optJSONArray("cards") ?: continue
        for (j in 0 until cards.length()) {
            val c = cards.optJSONObject(j) ?: continue
            out.add(araCardFrom(c, "$i$j"))
        }
    }
    return out
}

/** Cards grouped per logical unit (one slide / paragraph / image = one page).
 *  The overlay surfaces ONE unit per appearance, not the whole deck. */
fun parseAraFeedByUnit(feed: JSONObject): List<List<AraCard>> {
    val units = ArrayList<List<AraCard>>()
    val pages = feed.optJSONArray("pages") ?: return units
    for (i in 0 until pages.length()) {
        val cardsArr = pages.optJSONObject(i)?.optJSONArray("cards") ?: continue
        val unit = ArrayList<AraCard>()
        for (j in 0 until cardsArr.length()) {
            cardsArr.optJSONObject(j)?.let { unit.add(araCardFrom(it, "$i$j")) }
        }
        if (unit.isNotEmpty()) units.add(unit)
    }
    return units
}

/**
 * Main feed behavior:
 * - outer scroll = vertical snap between cards, no outer scrollbar;
 * - next card is visible below, so it feels like a serious card feed, not one static slab;
 * - inner scroll = only inside long card text, with a thin scrollbar.
 */
@Composable
fun AraCardFeed(
    cards: List<AraCard>,
    font: AraFont,
    theme: AraThemeStyle = AraThemeStyle.DARK_GOLD,
    subject: String = "",
    onExit: () -> Unit,
    onCardShown: (AraCard, Int) -> Unit = { _, _ -> },
    onCardHidden: (AraCard) -> Unit = {},
    onInnerScroll: (AraCard, Float) -> Unit = { _, _ -> },
    onSwipe: (from: Int, to: Int) -> Unit = { _, _ -> },
    onUnderstood: (AraCard, Boolean) -> Unit = { _, _ -> },
    onCycleFont: () -> Unit = {},
    onCycleTheme: () -> Unit = {},
    onDeleteCard: (AraCard) -> Unit = {},
    initialScrollFor: (AraCard) -> Int = { 0 },
    onScroll: (AraCard, Int, Int) -> Unit = { _, _, _ -> },
    onCardComplete: (AraCard) -> Unit = {},
    // "Am rămas la cardul ăsta": bookmark this card as the resume point and leave.
    onStayHere: (AraCard) -> Unit = {},
    // Pressing "Gata" on the LAST card = the deck is finished. Fired so the caller
    // can reset the resume point to the top; otherwise it would reopen on the last
    // card forever (the resume index tracks whatever card was shown last).
    onFinishedDeck: () -> Unit = {},
    // The card to open on: resume where the user last left off (0 = from the top).
    startIndex: Int = 0,
    // Set only by the overlay (never the in-app study feed): a short, playful
    // line acknowledging the card just interrupted a scroll.
    nagMessage: String? = null,
    // Show a "←" back arrow in the header. Only the in-app course feed sets this;
    // the doomscroll overlay must NOT offer a back-out (it's a forced card).
    showBack: Boolean = false,
) {
    val completedThisSession = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    AraBrand.activeTheme = theme
    // Live "?" walkthrough for the study feed — points at each control in turn.
    val tr = LocalTr.current
    val coach = rememberCoachState()
    val feedSteps = remember(tr) {
        listOf(
            CoachStep("feedFont", tr.cFeedFont),
            CoachStep("feedTheme", tr.cFeedTheme),
            CoachStep("feedDelete", tr.cFeedDelete),
            CoachStep("feedProgress", tr.cFeedProgress),
            CoachStep("", tr.cFeedScroll),
            CoachStep("", tr.cFeedCta),
        )
    }
    AraGlassScrim {
      CoachHost(coach) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            val pagerState = rememberPagerState(
                initialPage = startIndex.coerceIn(0, (cards.size - 1).coerceAtLeast(0)),
                pageCount = { cards.size.coerceAtLeast(1) },
            )
            if (!nagMessage.isNullOrBlank()) {
                NagBanner(nagMessage)
                Spacer(Modifier.height(8.dp))
            }
            AraHeader(
                index = pagerState.currentPage,
                total = cards.size,
                fontLabel = font.label,
                themeLabel = theme.label,
                onCycleFont = onCycleFont,
                onCycleTheme = onCycleTheme,
                onExit = onExit,
                onDelete = {
                    cards.getOrNull(pagerState.currentPage)?.let(onDeleteCard)
                },
                coach = coach,
                onHelp = { coach.start(feedSteps) },
                showBack = showBack,
            )
            Spacer(Modifier.height(8.dp))

            // How far through the whole course you are — fills as you scroll cards.
            if (cards.isNotEmpty()) {
                val frac = ((pagerState.currentPage + 1).toFloat() / cards.size).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(5.dp).coachAnchor(coach, "feedProgress")
                        .clip(RoundedCornerShape(3.dp)).background(Color(0x22FFFFFF)),
                ) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp)).background(AraBrand.araBrush))
                }
                Spacer(Modifier.height(8.dp))
            }

            if (cards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(LocalTr.current.feedNoLesson, color = AraBrand.body)
                }
                return@Column
            }

            // remember the derived state so it isn't recreated every recomposition
            // (UnrememberedMutableState): keeps the current-card tracking stable.
            val current by remember { derivedStateOf { pagerState.currentPage } }
            LaunchedEffect(current) { onCardShown(cards[current], current) }

            // Cascade entrance: the deck rises + fades in when it opens.
            val enter = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(Unit) {
                enter.animateTo(1f, androidx.compose.animation.core.tween(480,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
            BoxWithConstraints(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 70f
                }
            ) {
                val cardPageHeight = maxHeight * 0.82f
                VerticalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(cardPageHeight),
                    beyondViewportPageCount = 1,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    pageSpacing = 14.dp,
                    // Lighter paging: a short drag (~10% of a card) already commits
                    // to the next/previous card, so scrolling between cards is easy.
                    flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapPositionalThreshold = 0.1f,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                    val scale = (1f - offset * 0.035f).coerceIn(0.94f, 1f)
                    val alpha = (1f - offset * 0.13f).coerceIn(0.70f, 1f)
                    Box(
                        Modifier.fillMaxWidth().height(cardPageHeight)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            },
                    ) {
                        AraCardView(
                            card = cards[page],
                            font = font,
                            subject = subject,
                            index = page,
                            total = cards.size,
                            isLast = page == cards.size - 1,
                            modifier = Modifier.fillMaxSize(),
                            onInnerScroll = { d -> onInnerScroll(cards[page], d) },
                            // "Am înțeles, cardul următor": acknowledge + go to the next
                            // card; on the last one there is nowhere to go, so leave.
                            onNext = {
                                onUnderstood(cards[page], true)
                                if (page < cards.size - 1) scope.launch { pagerState.animateScrollToPage(page + 1) }
                                else { onFinishedDeck(); onExit() }
                            },
                            // "Am rămas la cardul ăsta": bookmark here and leave.
                            onStayHere = { onStayHere(cards[page]); onExit() },
                            onExit = onExit,
                            initialScrollPx = initialScrollFor(cards[page]),
                            onScroll = { off, pct ->
                                onScroll(cards[page], off, pct)
                                val id = cards[page].cardId
                                if (pct >= 85 && id !in completedThisSession) {
                                    completedThisSession.add(id)
                                    onCardComplete(cards[page])
                                }
                            },
                        )
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun AraHeader(
    index: Int,
    total: Int,
    fontLabel: String,
    themeLabel: String,
    onCycleFont: () -> Unit,
    onCycleTheme: () -> Unit,
    onExit: () -> Unit,
    onDelete: () -> Unit = {},
    coach: CoachState,
    onHelp: () -> Unit = {},
    showBack: Boolean = false,
) {
    val tr = LocalTr.current
    var confirmDelete by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Back to the course detail. Shown only for the in-app course feed;
            // the doomscroll overlay never sets showBack (no escape from a card).
            if (showBack) {
                Box(
                    Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0x1FFFFFFF))
                        .border(1.dp, AraBrand.glassEdgeGreen, androidx.compose.foundation.shape.CircleShape)
                        .clickable { onExit() },
                    contentAlignment = Alignment.Center,
                ) { Text("←", color = AraBrand.green, fontWeight = FontWeight.Black, fontSize = 18.sp) }
                Spacer(Modifier.width(10.dp))
            }
            AraLogoMark()
            Spacer(Modifier.width(12.dp))
            // Flexible so the tagline ellipsizes instead of squeezing the counter
            // onto a second line ("43/10" + "9").
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = AraBrand.title)) { append("AntiROTA") }
                        withStyle(SpanStyle(color = AraBrand.green, fontWeight = FontWeight.Bold)) { append("id") }
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                )
                Text(tr.homeTagline, color = AraBrand.muted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            // "?" — opens the live, point-at-each-control walkthrough for the feed.
            Box(
                Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0x1FFFFFFF))
                    .border(1.dp, AraBrand.glassEdgeGreen, androidx.compose.foundation.shape.CircleShape)
                    .clickable { onHelp() },
                contentAlignment = Alignment.Center,
            ) { Text("?", color = AraBrand.green, fontWeight = FontWeight.Black, fontSize = 15.sp) }
            Spacer(Modifier.width(10.dp))
            // Never wrap: the running count stays on one line.
            Text("${index + 1}/$total", color = AraBrand.muted, fontSize = 12.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HeaderPill("Aa", tr.textSize, onCycleFont, Modifier.weight(1f).coachAnchor(coach, "feedFont"))
            HeaderPill("◌", tr.navTheme, onCycleTheme, Modifier.weight(1f).coachAnchor(coach, "feedTheme"))
            // No "Pauză" pill: stopping the study mode lives only on the AntiROTAid
            // on/off button (home), so there is one clear place that owns on/off.
            // Remove THIS card from the deck (tap once to arm, again to confirm).
            HeaderPill(if (confirmDelete) "✓" else "✕", if (confirmDelete) tr.cardRemoveConfirm else tr.cardRemove,
                {
                    if (confirmDelete) { confirmDelete = false; onDelete() } else confirmDelete = true
                }, Modifier.weight(1f).coachAnchor(coach, "feedDelete"))
            // No standalone "Ieșire" pill: leaving the deck is the "Am rămas la
            // cardul ăsta" CTA on every card (it bookmarks the spot AND exits).
        }
    }
}

/** One-line, playful acknowledgement that this card just pulled the user off a
 *  scroll — distinct from the header, no controls, purely tone. */
@Composable
private fun NagBanner(text: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(AraBrand.araBrush)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, color = Color(0xFF16110A), fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun HeaderPill(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0x1CFFFFFF), Color(0x08000000))))
            .border(BorderStroke(1.dp, AraBrand.glassEdgeGreen), RoundedCornerShape(18.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(icon, color = AraBrand.greenDeep, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text(label, color = AraBrand.body, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** The slide title is shown as the card heading already; drop it if the body
 *  repeats it as its first line, so the card isn't redundant. */
internal fun bodyWithoutLeadingTitle(body: String, title: String): String {
    if (title.isBlank()) return body
    val lines = body.split("\n")
    val idx = lines.indexOfFirst { it.isNotBlank() }
    if (idx >= 0 && lines[idx].trim().equals(title.trim(), ignoreCase = true)) {
        return lines.toMutableList().apply { removeAt(idx) }.joinToString("\n").trim()
    }
    return body
}

/** Renders a card body with real structure (like the design mockups): section
 *  headings in gold, bulleted and numbered lists, and clean paragraphs — instead
 *  of one flat wall of text. Plain prose (no markers) still renders as paragraphs. */
@Composable
private fun FormattedBody(body: String, font: AraFont) {
    val lines = remember(body) { body.replace("\r", "").split("\n") }
    val bulletRe = remember { Regex("^\\s*[\\u2022\\u25AA\\u2023\\u2013\\u00B7*\\-]\\s+(.*)") }
    val numRe = remember { Regex("^\\s*(\\d{1,2}[.)])\\s+(.*)") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed
            val b = bulletRe.find(line)
            val nm = numRe.find(line)
            val next = lines.getOrNull(i + 1)?.trim().orEmpty()
            val nextBullet = bulletRe.containsMatchIn(next)
            val header = b == null && nm == null &&
                (line.endsWith(":") ||
                    (line.length in 1..46 && !line.endsWith(".") && !line.endsWith(",") && nextBullet))
            when {
                b != null -> BulletRow(b.groupValues[1], font)
                nm != null -> NumberRow(nm.groupValues[1], nm.groupValues[2], font)
                header -> SectionHeader(line.trimEnd(':').trim(), font)
                else -> Text(
                    line,
                    color = AraBrand.body,
                    fontSize = font.body.sp,
                    lineHeight = (font.body * 1.45).sp,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, font: AraFont) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(
            Modifier.width(3.dp).height((font.body + 5).dp)
                .clip(RoundedCornerShape(2.dp)).background(AraBrand.araBrush)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = AraBrand.greenDeep,
            fontSize = (font.body + 1).sp,
            fontWeight = FontWeight.Black,
            lineHeight = ((font.body + 1) * 1.25).sp,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun BulletRow(text: String, font: AraFont) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = (font.body * 0.42f).dp).size(6.dp)
            .clip(CircleShape).background(AraBrand.green))
        Spacer(Modifier.width(11.dp))
        Text(text, color = AraBrand.body, fontSize = font.body.sp, lineHeight = (font.body * 1.42).sp)
    }
}

@Composable
private fun NumberRow(num: String, text: String, font: AraFont) {
    Row(verticalAlignment = Alignment.Top) {
        Text(num, color = AraBrand.greenDeep, fontWeight = FontWeight.Bold, fontSize = font.body.sp)
        Spacer(Modifier.width(9.dp))
        Text(text, color = AraBrand.body, fontSize = font.body.sp, lineHeight = (font.body * 1.42).sp)
    }
}

@Composable
private fun AraCardView(
    card: AraCard,
    font: AraFont,
    subject: String = "",
    index: Int,
    total: Int,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    onInnerScroll: (Float) -> Unit,
    onNext: () -> Unit = {},
    onStayHere: () -> Unit = {},
    onExit: () -> Unit,
    initialScrollPx: Int = 0,
    onScroll: (offsetPx: Int, percent: Int) -> Unit = { _, _ -> },
) {
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.fillMaxWidth().araGlass(shape, glow = AraBrand.glowStrong, glowRadius = 26.dp)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            val tr = LocalTr.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The chip names the SUBJECT (course topic) so that during a
                // doomscroll the overlay tells you at a glance what you're reviewing;
                // falls back to the slide label when no subject is supplied.
                SourceChip(subject.ifBlank { araLocalizeSourceLabel(tr, card.sourceLabel).ifBlank { "${tr.cardFallback} ${index + 1}" } })
                Spacer(Modifier.weight(1f))
                Text("${index + 1} / $total", color = AraBrand.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(14.dp))

            val scroll = rememberScrollState(initial = initialScrollPx)
            LaunchedEffect(scroll.value, scroll.maxValue) {
                val depth = if (scroll.maxValue > 0) scroll.value.toFloat() / scroll.maxValue else 0f
                onInnerScroll(depth)
                onScroll(scroll.value, (depth * 100).toInt())
            }
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (card.title.isNotBlank()) {
                        DecodeText(
                            text = card.title,
                            fontSize = font.title.sp,
                            color = AraBrand.title,
                            lineHeight = (font.title * 1.18).sp,
                        )
                        Box(Modifier.width(52.dp).height(4.dp).clip(CircleShape).background(AraBrand.araBrush))
                    }
                    if (card.isImage) {
                        VisualCardPanel(card = card, font = font)
                    } else {
                        FormattedBody(bodyWithoutLeadingTitle(card.body, card.title), font)
                    }
                }
                if (scroll.maxValue > 0) {
                    SlimScrollbar(
                        value = scroll.value,
                        max = scroll.maxValue,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(5.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            // The whole course is scrollable — swipe to keep going. Two explicit
            // actions on every card: advance, or bookmark here and leave (so the
            // next time the deck opens it resumes from this card).
            if (!isLast) {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(tr.feedScrollNext, color = AraBrand.greenDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text("↑", color = AraBrand.green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            val ctaShape = RoundedCornerShape(16.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).heightIn(min = 56.dp)
                        .clip(ctaShape)
                        .border(BorderStroke(1.dp, AraBrand.glassEdgeGreen), ctaShape)
                        .clickable { onStayHere() }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr.feedStayHere, color = AraBrand.greenDeep, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 17.sp)
                }
                Box(
                    Modifier.weight(1f).heightIn(min = 56.dp)
                        .shadow(16.dp, ctaShape, spotColor = AraBrand.glowStrong, ambientColor = AraBrand.glowStrong)
                        .clip(ctaShape).background(AraBrand.araBrush)
                        .clickable { onNext() }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // On the last card there is no "next" — say so plainly.
                    Text(if (isLast) tr.feedDone else tr.feedUnderstoodNext,
                        color = Color(0xFF16110A), fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun SourceChip(label: String) {
    Box(Modifier.clip(CircleShape).background(AraBrand.greenSoft).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(AraBrand.green))
            Spacer(Modifier.width(7.dp))
            Text(label.uppercase(), color = AraBrand.greenDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.45.sp)
        }
    }
}

@Composable
private fun VisualCardPanel(card: AraCard, font: AraFont) {
    val hasBody = card.body.isNotBlank() && card.body != card.title

    // PDF AS-IS (spec P0): render the page at FULL WIDTH and let it be tall — the
    // card's own vertical scroll then walks the whole page at readable size,
    // instead of shrinking the page into an illegible thumbnail.
    val isPdfPage = card.pdfDocumentPath.isNotBlank() && card.pdfPhysicalPage >= 1
    if (isPdfPage) {
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val widthPx = with(density) { maxWidth.toPx() }.toInt()
            val pageBmp = remember(card.pdfDocumentPath, card.pdfPhysicalPage, widthPx) {
                renderPdfPageWide(card.pdfDocumentPath, card.pdfPhysicalPage, widthPx)
            }
            if (pageBmp != null) {
                val hDp = with(density) { (pageBmp.height.toFloat() * maxWidth.toPx() / pageBmp.width).toDp() }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        bitmap = pageBmp.asImageBitmap(),
                        contentDescription = card.title.ifBlank { card.sourceLabel },
                        modifier = Modifier.fillMaxWidth().height(hDp)
                            .clip(RoundedCornerShape(16.dp)).background(Color.White),
                        contentScale = ContentScale.FillWidth,
                    )
                    if (hasBody) FormattedBody(bodyWithoutLeadingTitle(card.body, card.title), font)
                }
            } else if (hasBody) {
                FormattedBody(bodyWithoutLeadingTitle(card.body, card.title), font)
            }
        }
        return
    }

    val bitmap = remember(card.imagePath, card.imageBase64) {
        runCatching {
            when {
                card.imagePath.isNotBlank() -> BitmapFactory.decodeFile(card.imagePath)
                card.imageBase64.isNotBlank() -> {
                    val bytes = Base64.decode(card.imageBase64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                else -> null
            }
        }.getOrNull()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (bitmap != null) {
            // Typeset slide (perfect LaTeX math). Double-tap to zoom in/out; once
            // zoomed, drag to pan / pinch to fine-tune. While at 1× the image does
            // NOT capture drags, so a normal swipe still scrolls to the next card.
            var scale by remember(card.cardId) { mutableStateOf(1f) }
            var offset by remember(card.cardId) { mutableStateOf(Offset.Zero) }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 520.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFFFFFFF))
                    .border(1.dp, AraBrand.glassEdgeGreen, RoundedCornerShape(20.dp))
                    .pointerInput(card.cardId) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.6f
                        })
                    }
                    .then(if (scale > 1f) Modifier.pointerInput(card.cardId) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = card.title.ifBlank { card.sourceLabel.ifBlank { "Slide vizual" } },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
                    contentScale = ContentScale.Fit,
                )
            }
            if (hasBody) FormattedBody(bodyWithoutLeadingTitle(card.body, card.title), font)
        } else if (hasBody) {
            // No bundled image, but there IS real text — show it cleanly (no empty
            // placeholder box, which looked terrible on text-only PDFs).
            FormattedBody(bodyWithoutLeadingTitle(card.body, card.title), font)
        } else {
            // Genuinely nothing to render: a compact, tidy hint — not a giant box.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0x22FFC107)),
                    contentAlignment = Alignment.Center) {
                    Text("▧", color = AraBrand.greenDeep, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(LocalTr.current.feedVisualSlide,
                    color = AraBrand.muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

/**
 * Render one page of an on-device PDF to a bitmap using the platform PdfRenderer.
 * Fully offline. `physicalPage` is 1-based. Returns null on any failure so the
 * caller falls back to the clean text card.
 */
private val pdfRenderLock = Any()

private fun renderPdfPage(path: String, physicalPage: Int): android.graphics.Bitmap? =
    synchronized(pdfRenderLock) { renderPdfPageLocked(path, physicalPage) }

/**
 * PDF AS-IS (spec P0): render a page at a fixed WIDTH so the text is full-size and
 * readable, letting the height grow — the card then scrolls vertically instead of
 * shrinking the whole page to fit. `targetWidthPx` is the card's pixel width.
 */
private fun renderPdfPageWide(path: String, physicalPage: Int, targetWidthPx: Int): android.graphics.Bitmap? =
    synchronized(pdfRenderLock) {
        val file = java.io.File(path)
        if (!file.exists()) return@synchronized null
        runCatching {
            android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                    val index = (physicalPage - 1).coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(index).use { page ->
                        val w = targetWidthPx.coerceIn(360, 1400)
                        // Keep the page aspect ratio; cap height so a freak tall page
                        // can't allocate an enormous bitmap (it stays scrollable anyway).
                        val h = (page.height.toFloat() / page.width * w).toInt().coerceIn(1, 6000)
                        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrNull()
    }

private fun renderPdfPageLocked(path: String, physicalPage: Int): android.graphics.Bitmap? {
    val file = java.io.File(path)
    if (!file.exists()) return null
    return android.os.ParcelFileDescriptor.open(
        file, android.os.ParcelFileDescriptor.MODE_READ_ONLY
    ).use { pfd ->
        android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
            val index = (physicalPage - 1).coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(index).use { page ->
                // Render at ~2x slide point size for a crisp image, capped for memory.
                val scale = 2f
                val w = (page.width * scale).toInt().coerceIn(1, 2200)
                val h = (page.height * scale).toInt().coerceIn(1, 2200)
                val bmp = android.graphics.Bitmap.createBitmap(
                    w, h, android.graphics.Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(android.graphics.Color.WHITE)  // slides assume white paper
                page.render(bmp, null, null,
                    android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }
}

@Composable
private fun SlimScrollbar(value: Int, max: Int, modifier: Modifier) {
    val frac = if (max > 0) (value.toFloat() / max).coerceIn(0f, 1f) else 0f
    val thumb = 0.28f
    val travel = 1f - thumb
    val top = frac * travel
    val bottom = (1f - frac) * travel
    Box(modifier.clip(RoundedCornerShape(2.dp)).background(Color(0x14000000))) {
        Column(Modifier.fillMaxSize()) {
            if (top > 0f) Spacer(Modifier.weight(top))
            Box(Modifier.fillMaxWidth().weight(thumb).clip(RoundedCornerShape(2.dp)).background(AraBrand.green))
            if (bottom > 0f) Spacer(Modifier.weight(bottom))
        }
    }
}
