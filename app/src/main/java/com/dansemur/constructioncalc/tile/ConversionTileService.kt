package com.dansemur.constructioncalc.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.dansemur.constructioncalc.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

/**
 * ConversionTileService
 *
 * Wear OS Tile displaying an instant high-visibility reference table of common
 * fractional tape measure conversions (Imperial Fraction <-> Decimal Inches <-> Metric mm).
 *
 * Mathematically Calibrated Circular Layout:
 * - Top Toggle: 184dp span (88dp left wing + 88dp right wing) safely inside the 200dp chord at y=15dp
 * - Circular Chords: Expanding row widths (216dp pole -> 240dp equator) tracing the circular bezel
 * - Proportional Columns: Frac (32%) | Dec (34%) | Metric (34%) with interior padding
 * - Zero clipping on all text, labels, and units across all Wear OS round displays
 */
class ConversionTileService : TileService() {

    companion object {
        @Volatile
        private var currentPage = 0 // 0 = 0 to 1/2", 1 = 1/2" to 1"

        private data class ConvRow(
            val frac: String,
            val dec: String,
            val mm: String,
            val isMajor: Boolean
        )

        // Trimmed trailing zeros: clean, direct, carpenter-friendly
        private val PAGE_0_ROWS = listOf(
            ConvRow("1/16″", ".0625″", "1.6 mm", false),
            ConvRow("1/8″",  ".125″",  "3.2 mm", true),
            ConvRow("3/16″", ".1875″", "4.8 mm", false),
            ConvRow("1/4″",  ".25″",   "6.4 mm", true),
            ConvRow("5/16″", ".3125″", "7.9 mm", false),
            ConvRow("3/8″",  ".375″",  "9.5 mm", true),
            ConvRow("7/16″", ".4375″", "11.1 mm", false),
            ConvRow("1/2″",  ".5″",    "12.7 mm", true)
        )

        private val PAGE_1_ROWS = listOf(
            ConvRow("9/16″",  ".5625″", "14.3 mm", false),
            ConvRow("5/8″",   ".625″",  "15.9 mm", true),
            ConvRow("11/16″", ".6875″", "17.5 mm", false),
            ConvRow("3/4″",   ".75″",   "19.1 mm", true),
            ConvRow("13/16″", ".8125″", "20.6 mm", false),
            ConvRow("7/8″",   ".875″",  "22.2 mm", true),
            ConvRow("15/16″", ".9375″", "23.8 mm", false),
            ConvRow("1″",     "1″",     "25.4 mm", true)
        )

        // Calibrated row widths: comfortably within the circular display
        // Row 0 (1/16"): W=210dp
        // Row 1 (1/8"):  W=220dp
        // Row 2 (3/16"): W=228dp
        // Row 3 (1/4"):  W=230dp
        // Row 4 (5/16"): W=224dp
        // Row 5 (3/8"):  W=206dp
        // Row 6 (7/16"): W=186dp
        // Row 7 (1/2"):  W=166dp (comfortably fits fraction, decimal, and full 'mm' unit)
        // Calibrated row widths: following circular chords from y=56dp to y=210dp
        private val CIRCLE_ROW_WIDTHS = floatArrayOf(
            190f, // Row 0 (1/16")
            206f, // Row 1 (1/8")
            216f, // Row 2 (3/16")
            218f, // Row 3 (1/4") - Near equator
            214f, // Row 4 (5/16")
            202f, // Row 5 (3/8")
            182f, // Row 6 (7/16")
            158f  // Row 7 (1/2" and 1") - fits full "12.7 mm" and "25.4 mm"
        )
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val lastClicked = requestParams.currentState.lastClickableId
        if (lastClicked == "toggle_page_0") {
            currentPage = 0
        } else if (lastClicked == "toggle_page_1") {
            currentPage = 1
        } else if (lastClicked == "toggle_btn") {
            currentPage = if (currentPage == 0) 1 else 0
        }

        val rows = if (currentPage == 0) PAGE_0_ROWS else PAGE_1_ROWS

        // Background Silent OTA check (rate-limited / non-blocking)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.dansemur.constructioncalc.ota.SilentSelfUpdater.checkForUpdate(applicationContext)
            } catch (_: Exception) {}
        }

        // Launch Action to open ConstructionCalcActivity if table is tapped
        val launchIntent = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName("com.dansemur.constructioncalc")
                    .setClassName("com.dansemur.constructioncalc.ConstructionCalcActivity")
                    .build()
            )
            .build()

        val calcClickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(launchIntent)
            .setId("open_calc_activity")
            .build()

        // Page toggles
        val click0 = ModifiersBuilders.Clickable.Builder()
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .setId("toggle_page_0")
            .build()

        val click1 = ModifiersBuilders.Clickable.Builder()
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .setId("toggle_page_1")
            .build()

        // Root container (Black Wear OS background)
        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF000000.toInt()))
                            .build()
                    )
                    .build()
            )
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

        // ── 1. TOP HALF-SPACE QUADRANT WINGS CONFORMING TO BEZEL ─────────────
        // Non-concentric flat-bottom bezel domes (exact R_outer=184dp circle intersection)
        // Each wing is 110dp wide x 38dp tall, with 4dp center split at 12 o'clock
        val isP0 = (currentPage == 0)
        val leftResId = if (isP0) "wing_left_active" else "wing_left_inactive"
        val rightResId = if (!isP0) "wing_right_active" else "wing_right_inactive"

        // Left Wing Button: 0 à 1/2" (93dp wide x 48dp tall, pure circular arc to 12 o'clock)
        val chip0Box = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(93f))
            .setHeight(dp(48f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(click0)
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(leftResId)
                    .setWidth(dp(93f))
                    .setHeight(dp(48f))
                    .build()
            )

        // Right Wing Button: 1/2 à 1" (93dp wide x 48dp tall, pure circular arc to 12 o'clock)
        val chip1Box = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(93f))
            .setHeight(dp(48f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(click1)
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(rightResId)
                    .setWidth(dp(93f))
                    .setHeight(dp(48f))
                    .build()
            )

        // Top Header Row with 2dp center split at 12 o'clock (total span 188dp, pure circle arc)
        val topHeaderRow = LayoutElementBuilders.Row.Builder()
            .setWidth(dp(188f))
            .setHeight(dp(48f))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(chip0Box.build())
            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(2f)).build())
            .addContent(chip1Box.build())

        // Top Container anchored at upper bezel y=4dp (circle peak)
        val topContainer = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setTop(dp(4f))
                            .build()
                    )
                    .build()
            )
            .addContent(topHeaderRow.build())

        root.addContent(topContainer.build())

        // ── 2. CIRCLE-CONFORMING TABLE ROWS ───────────────────────────────────
        val col = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        // Top clearance below the flat-bottom wings (spacer to Row 0):
        col.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(46f)).build())

        for (i in rows.indices) {
            val r = rows[i]
            val rowW = CIRCLE_ROW_WIDTHS.getOrElse(i) { 200f }
            val col1W = if (i == 7) (rowW * 0.27f) else (rowW * 0.30f)
            val col2W = if (i == 7) (rowW * 0.31f) else (rowW * 0.34f)
            val col3W = if (i == 7) (rowW * 0.42f) else (rowW * 0.36f)

            val isEven = (i % 2 == 0)
            val rowBg = if (isEven) 0x18FFFFFF else 0x00000000

            val fracCol = if (r.isMajor) 0xFFFFFFFF.toInt() else 0xFFCBD5E1.toInt()
            val decCol = if (r.isMajor) 0xFFFEF08A.toInt() else 0xFFE2E8F0.toInt()
            val mmCol = if (r.isMajor) 0xFF86EFAC.toInt() else 0xFFA7F3D0.toInt()

            // Scale down font slightly on lowest 2 rows to fit narrower safe chords
            val fracSize = when (i) {
                7 -> 12.0f
                6 -> 13.0f
                else -> if (r.isMajor) 14.0f else 12.5f
            }
            val decSize = when (i) {
                7 -> 12.0f
                6 -> 13.0f
                else -> if (r.isMajor) 14.0f else 12.5f
            }
            val mmSize = when (i) {
                7 -> 12.0f
                6 -> 13.0f
                else -> if (r.isMajor) 14.0f else 12.5f
            }

            val row = LayoutElementBuilders.Row.Builder()
                .setWidth(dp(rowW))
                .setHeight(dp(17.5f))
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(argb(rowBg))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(8f)).build())
                                .build()
                        )
                        .setClickable(calcClickable)
                        .build()
                )

            // Dynamic side inset: pull columns inward from row 5 down
            val sideInset = when (i) {
                5 -> 10f // 3/8"
                6 -> 8f  // 7/16"
                7 -> 4f  // 1/2" (allows full "12.7 mm" to render without clipping)
                else -> 8f
            }

            // Col 1: Fraction (e.g. 1/16″) - 30% width, Left aligned
            row.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(col1W))
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(sideInset)).build())
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(r.frac)
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(fracSize))
                                    .setWeight(if (r.isMajor) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                                    .setColor(argb(fracCol))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )

            // Col 2: Decimal (e.g. .125″, .25″) - 34% width, Center aligned
            row.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(col2W))
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(r.dec)
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(decSize))
                                    .setWeight(if (r.isMajor) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                                    .setColor(argb(decCol))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )

            // Col 3: Metric (e.g. 1.6 mm) - 36% width, Right aligned
            row.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(col3W))
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_END)
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setPadding(ModifiersBuilders.Padding.Builder().setEnd(dp(sideInset)).build())
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(r.mm)
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(mmSize))
                                    .setWeight(if (r.isMajor) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                                    .setColor(argb(mmCol))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )

            col.addContent(row.build())
        }

        root.addContent(col.build())

        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(root.build())
            .build()

        val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(layout)
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(timelineEntry)
            .build()

        // Dynamic resources version forces ProtoLayout to invalidate cache and redraw active chip
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("page_${currentPage}_${System.currentTimeMillis()}")
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(0)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(requestParams.version)
            .addIdToImageMapping(
                "wing_left_active",
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.wing_left_active)
                            .build()
                    )
                    .build()
            )
            .addIdToImageMapping(
                "wing_left_inactive",
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.wing_left_inactive)
                            .build()
                    )
                    .build()
            )
            .addIdToImageMapping(
                "wing_right_active",
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.wing_right_active)
                            .build()
                    )
                    .build()
            )
            .addIdToImageMapping(
                "wing_right_inactive",
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.wing_right_inactive)
                            .build()
                    )
                    .build()
            )
            .build()
        return Futures.immediateFuture(resources)
    }
}
