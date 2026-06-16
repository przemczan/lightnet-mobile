package com.lightnet.demo

import com.lightnet.api.http.model.AnimateTarget
import com.lightnet.api.http.model.AnimationType
import com.lightnet.api.http.model.BlendMode
import com.lightnet.api.http.model.ColorRef
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.api.http.model.PanelTarget
import com.lightnet.api.http.model.RunnerSourceToken
import com.lightnet.api.http.model.RunnerType
import com.lightnet.api.http.model.SceneColors
import com.lightnet.api.http.model.SceneJson
import com.lightnet.api.http.model.SceneLayer
import com.lightnet.api.http.model.SceneStep

internal object DemoDataInitializer {
    val defaultPalettes: List<PaletteJson> = listOf(
        PaletteJson(
            name = "userColors",
            stops = listOf(
                PaletteStop(0, "#FF0000"),
                PaletteStop(85, "#00FF00"),
                PaletteStop(170, "#0000FF"),
                PaletteStop(255, "#FF0000"),
            ),
        ),
        PaletteJson(
            name = "Warm Sunset",
            stops = listOf(
                PaletteStop(0, "#FF2200"),
                PaletteStop(100, "#FF6600"),
                PaletteStop(200, "#FFAA00"),
                PaletteStop(255, "#FFDD00"),
            ),
        ),
        PaletteJson(
            name = "Ocean Blue",
            stops = listOf(
                PaletteStop(0, "#001040"),
                PaletteStop(100, "#0044AA"),
                PaletteStop(200, "#0088FF"),
                PaletteStop(255, "#44CCFF"),
            ),
        ),
        PaletteJson(
            name = "Forest",
            stops = listOf(
                PaletteStop(0, "#001400"),
                PaletteStop(100, "#004400"),
                PaletteStop(200, "#008800"),
                PaletteStop(255, "#44CC44"),
            ),
        ),
        PaletteJson(
            name = "Aurora",
            stops = listOf(
                PaletteStop(0, "#00FFAA"),
                PaletteStop(85, "#0044FF"),
                PaletteStop(170, "#AA00FF"),
                PaletteStop(255, "#00FFAA"),
            ),
        ),
    )

    // ── Aurora Storm ────────────────────────────────────────────────────────
    // 28 s scene. Eight layers: one conductor that anchors timing with named
    // step ids, one free-running atmosphere, five runners/animations that gate
    // on conductor steps, and two modifier layers (hue-drift + dim-out).
    //
    // Timeline
    //  0s        5s       10s      15s  18s       23s 27s  28s
    //  |         |         |        |    |          |   |    |
    //  conductor [emerge─][ripple─][storm─────────][peak][fade]
    //  ambient   [BREATHE continuous ─────────────────────────]
    //  first-rip           [rip→──][rip←───][chase]
    //  wave-storm          [wave45°][wave135°][bounce──────]
    //  rain-chaos          [rain──────────][sparkle────────]
    //  peak                                   [sparkle][matrix]
    //  hue-drift           [hue shift 18 s ──────────────]
    //  dim-out                                         [dim──]
    private val auroraStorm = SceneJson(
        schemaVersion = 8,
        name = "AuroraStorm",
        loop = true,
        palette = "Aurora",
        colors = SceneColors(
            primary   = "#00FFC8",
            secondary = "#9900FF",
            tertiary  = "#001A30",
        ),
        layers = listOf(
            // 1. conductor — background colour evolution; its named step ids gate all other layers.
            SceneLayer(
                group = "conductor",
                sequence = listOf(
                    SceneStep(id = "emerge", type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#000000"), colorTo = ColorRef.Hex("#001428"),
                        duration = 5000),
                    SceneStep(id = "ripple-phase", type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#001428"), colorTo = ColorRef.Hex("#001C35"),
                        duration = 5000),
                    SceneStep(id = "storm-phase", type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#001C35"), colorTo = ColorRef.Hex("#000C20"),
                        duration = 8000),
                    SceneStep(id = "peak-phase", type = AnimationType.SOLID,
                        color = ColorRef.Hex("#000C20"), duration = 5000),
                    SceneStep(type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#000C20"), colorTo = ColorRef.Hex("#000000"),
                        duration = 5000),
                ),
            ),
            // 2. ambient — continuous atmospheric breathe layered under everything (async free so
            //    it never blocks the scene) with ADD blend so it lifts without washing out runners.
            SceneLayer(
                group = "ambient",
                async = "free",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(type = AnimationType.BREATHE,
                        colorFrom = ColorRef.Hex("#000820"),
                        colorTo   = ColorRef.PalettePosition(60),
                        duration  = 9000, loop = true),
                ),
            ),
            // 3. first-ripple — warm-up: a single clean ripple expands from root, then a denser
            //    convergence from leaves, capped with a quick chase. Starts at t=5 s (after emerge).
            SceneLayer(
                group     = "first-ripple",
                startAfter = "conductor:emerge",
                blend     = BlendMode.ADD,
                sequence  = listOf(
                    SceneStep(runner = RunnerType.RIPPLE, source = RunnerSourceToken.ROOT,
                        color = ColorRef.PalettePosition(0),
                        rippleWidth = 2, duration = 3000),
                    SceneStep(runner = RunnerType.RIPPLE, source = RunnerSourceToken.LEAVES,
                        color = ColorRef.PalettePosition(200),
                        rippleWidth = 2, count = 3, duration = 4500),
                    SceneStep(runner = RunnerType.CHASE, source = RunnerSourceToken.ROOT,
                        color = ColorRef.BaseColorSlot(0), duration = 1500),
                ),
            ),
            // 4. wave-storm — two crossing geometric sweeps then a topology bounce.
            //    Starts at t=10 s (after ripple-phase).
            SceneLayer(
                group     = "wave-storm",
                startAfter = "conductor:ripple-phase",
                blend     = BlendMode.MAX,
                sequence  = listOf(
                    SceneStep(runner = RunnerType.WAVE,
                        directionality = "geometric", angle = 45,
                        color = ColorRef.PalettePosition(220),
                        waveWidth = 3, count = 4, duration = 4000),
                    SceneStep(runner = RunnerType.WAVE,
                        directionality = "geometric", angle = 135,
                        color = ColorRef.PalettePosition(40),
                        waveWidth = 2, count = 3, reverse = true, duration = 3000),
                    SceneStep(runner = RunnerType.BOUNCE, source = RunnerSourceToken.ROOT,
                        color = ColorRef.PalettePosition(160),
                        width = 2, duration = 4000),
                ),
            ),
            // 5. rain-chaos — topology rain builds intensity then transitions into sparkle.
            //    Starts at t=10 s alongside wave-storm; blending with MAX keeps the brightest wins.
            SceneLayer(
                group     = "rain-chaos",
                startAfter = "conductor:ripple-phase",
                blend     = BlendMode.MAX,
                sequence  = listOf(
                    SceneStep(runner = RunnerType.RAIN,
                        color = ColorRef.BaseColorSlot(1),
                        waves = 6, speed = 1500, width = 2, duration = 6000),
                    SceneStep(runner = RunnerType.SPARKLE,
                        color = ColorRef.PalettePosition(10),
                        waves = 8, width = 80, duration = 5000),
                ),
            ),
            // 6. peak — brief white sparkle eruption followed by geometric digital rain at the climax.
            //    Starts at t=18 s (after storm-phase). SCREEN blend makes whites bloom.
            SceneLayer(
                group     = "peak",
                startAfter = "conductor:storm-phase",
                blend     = BlendMode.SCREEN,
                sequence  = listOf(
                    SceneStep(runner = RunnerType.SPARKLE,
                        color = ColorRef.Hex("#FFFFFF"),
                        waves = 12, width = 40, duration = 3500),
                    SceneStep(runner = RunnerType.MATRIX,
                        color = ColorRef.PalettePosition(10),
                        directionality = "geometric", angle = 90,
                        waves = 6, speed = 500, width = 2, duration = 2000),
                ),
            ),
            // 7. hue-drift — modifier that slowly rotates the hue of all layers below it,
            //    giving the whole storm a living colour shift over 18 s.
            SceneLayer(
                group     = "hue-drift",
                startAfter = "conductor:emerge",
                sequence  = listOf(
                    SceneStep(type = AnimationType.FADE,
                        animates = AnimateTarget.HUE,
                        from = 0, to = 80, duration = 18000),
                ),
            ),
            // 8. dim-out — modifier that fades everything to black in the final 4 s before loop.
            SceneLayer(
                group     = "dim-out",
                startAfter = "conductor:peak-phase",
                sequence  = listOf(
                    SceneStep(type = AnimationType.FADE,
                        animates = AnimateTarget.DIM,
                        from = 255, to = 0, duration = 4000),
                ),
            ),
        ),
    )

    // ── Heart Pulse ──────────────────────────────────────────────────────────
    // 8 s cycle (one heartbeat = 800 ms; scene-level loop restarts it).
    // Showcases: PULSE with asymmetric rise/fall, ADD-blended BREATHE ambient,
    // SCREEN-blended SPARKLE shimmer.
    //
    // glow  [BREATHE 2.4 s continuous free ───────────────]
    // beat  [PULSE 800 ms loop ─────────────────────────── → barrier fires]
    // blush [SPARKLE infinite free ─────────────────────────────────────────]
    private val heartPulse = SceneJson(
        schemaVersion = 8,
        name = "HeartPulse",
        loop = true,
        palette = "Warm Sunset",
        colors = SceneColors(
            primary   = "#FF1A3A",
            secondary = "#4A0018",
            tertiary  = "#FF9040",
        ),
        layers = listOf(
            // 1. glow — deep crimson breathe beneath everything
            SceneLayer(
                group = "glow",
                async = "free",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(type = AnimationType.BREATHE,
                        colorFrom = ColorRef.Hex("#1A0005"),
                        colorTo   = ColorRef.PalettePosition(20),
                        duration  = 2400, loop = true),
                ),
            ),
            // 2. beat — sharp PULSE: fast rise (24%), long fall (47%), brief hold
            SceneLayer(
                group = "beat",
                sequence = listOf(
                    SceneStep(type = AnimationType.PULSE,
                        colorFrom = ColorRef.Hex("#000000"),
                        colorTo   = ColorRef.Hex("#FF1A3A"),
                        duration  = 800, loop = true,
                        params    = listOf(60, 120)),
                ),
            ),
            // 3. blush — soft sparkle shimmer, free-running
            SceneLayer(
                group = "blush",
                async = "free",
                blend = BlendMode.SCREEN,
                sequence = listOf(
                    SceneStep(runner = RunnerType.SPARKLE,
                        color  = ColorRef.PalettePosition(40),
                        waves  = 3, width = 120, duration = 0),
                ),
            ),
        ),
    )

    // ── Digital Rain ─────────────────────────────────────────────────────────
    // 12 s scene. Showcases: MATRIX geometric columns, topology RAIN for depth,
    // BLINK glitch burst. Conductor gates matrix and glitch on separate phases.
    //
    // Timeline
    //  0s     2s              9s     12s
    //  |      |               |       |
    //  conductor [boot─][active──────][fade─]
    //  matrix-rain    [MATRIX──────────────]
    //  code-drip      [RAIN────────────────]
    //  glitch  [gap 9 s──────────][BLINK─3s]
    private val digitalRain = SceneJson(
        schemaVersion = 8,
        name = "DigitalRain",
        loop = true,
        palette = "Forest",
        colors = SceneColors(
            primary   = "#00FF44",
            secondary = "#001400",
            tertiary  = "#44FF00",
        ),
        layers = listOf(
            // 1. conductor — boot → active → fade
            SceneLayer(
                group = "conductor",
                sequence = listOf(
                    SceneStep(id = "boot", type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#000000"), colorTo = ColorRef.Hex("#001400"),
                        duration = 2000),
                    SceneStep(id = "active", type = AnimationType.SOLID,
                        color = ColorRef.Hex("#001400"), duration = 7000),
                    SceneStep(type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#001400"), colorTo = ColorRef.Hex("#000000"),
                        duration = 3000),
                ),
            ),
            // 2. matrix-rain — geometric columns (boot → fade): dense, then thinning
            SceneLayer(
                group      = "matrix-rain",
                startAfter = "conductor:boot",
                blend      = BlendMode.ADD,
                sequence   = listOf(
                    SceneStep(runner = RunnerType.MATRIX,
                        color = ColorRef.PalettePosition(200),
                        directionality = "geometric", angle = 90,
                        waves = 8, speed = 400, width = 2, duration = 7000),
                    SceneStep(runner = RunnerType.MATRIX,
                        color = ColorRef.PalettePosition(150),
                        directionality = "geometric", angle = 90,
                        waves = 4, speed = 700, width = 1, duration = 3000),
                ),
            ),
            // 3. code-drip — topology RAIN alongside geometric columns for organic depth
            SceneLayer(
                group      = "code-drip",
                startAfter = "conductor:boot",
                blend      = BlendMode.MAX,
                sequence   = listOf(
                    SceneStep(runner = RunnerType.RAIN,
                        color = ColorRef.PalettePosition(100),
                        waves = 5, speed = 1200, width = 1, duration = 10000),
                ),
            ),
            // 4. glitch — gap-delayed BLINK burst at the active peak
            SceneLayer(
                group = "glitch",
                blend = BlendMode.SCREEN,
                sequence = listOf(
                    SceneStep(duration = 9000),
                    SceneStep(type = AnimationType.BLINK,
                        colorFrom = ColorRef.Hex("#001400"),
                        colorTo   = ColorRef.Hex("#CCFFCC"),
                        duration  = 3000, params = listOf(50)),
                ),
            ),
        ),
    )

    // ── Wheel of Fire ────────────────────────────────────────────────────────
    // 10 s loop. Showcases: WHEEL runner (multi-blade, counter-rotation),
    // ADD-layered BREATHE backdrop, SCREEN-blended SPARKLE embers.
    private val wheelOfFire = SceneJson(
        schemaVersion = 8,
        name = "WheelOfFire",
        loop = true,
        palette = "Warm Sunset",
        colors = SceneColors(
            primary   = "#FF4400",
            secondary = "#FF8800",
            tertiary  = "#FFCC00",
        ),
        layers = listOf(
            // 1. ember-glow — deep amber breathe backdrop, free-running
            SceneLayer(
                group = "ember-glow",
                async = "free",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(type = AnimationType.BREATHE,
                        colorFrom = ColorRef.Hex("#1A0500"),
                        colorTo   = ColorRef.Hex("#3A1000"),
                        duration  = 3000, loop = true),
                ),
            ),
            // 2. fire-wheel — three blades forward (one full rotation over 10 s)
            SceneLayer(
                group = "fire-wheel",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(runner = RunnerType.WHEEL,
                        color     = ColorRef.PalettePosition(60),
                        thickness = 40, lines = 3, duration = 10000),
                ),
            ),
            // 3. ember-blade — single counter-blade adds depth
            SceneLayer(
                group = "ember-blade",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(runner = RunnerType.WHEEL,
                        color     = ColorRef.PalettePosition(200),
                        thickness = 20, lines = 1, reverse = true, duration = 10000),
                ),
            ),
            // 4. sparks — scattered ember sparkle, free-running
            SceneLayer(
                group = "sparks",
                async = "free",
                blend = BlendMode.SCREEN,
                sequence = listOf(
                    SceneStep(runner = RunnerType.SPARKLE,
                        color  = ColorRef.PalettePosition(240),
                        waves  = 5, width = 80, duration = 0),
                ),
            ),
        ),
    )

    // ── Ocean Swell ──────────────────────────────────────────────────────────
    // 18 s scene. Showcases: slow topology WAVE, BOUNCE (pendulum), geometric
    // crossing swell, DESATURATE modifier arc, SPARKLE seafoam.
    //
    // Timeline
    //  0s       6s          12s        18s
    //  |        |            |          |
    //  conductor [calm──────][swell─────][crest─────]
    //  deep-current [BREATHE continuous free ────────]
    //  surface-wave [WAVE topo 12 s─────][WAVE 6 s──]
    //  geo-swell            [wave315][bounce][bounce R]
    //  seafoam                        [SPARKLE][DIM]
    //  depth-cool           [desat→→→→→→→resat───────]
    private val oceanSwell = SceneJson(
        schemaVersion = 8,
        name = "OceanSwell",
        loop = true,
        palette = "Ocean Blue",
        colors = SceneColors(
            primary   = "#0066AA",
            secondary = "#001040",
            tertiary  = "#44CCFF",
        ),
        layers = listOf(
            // 1. conductor — calm → swell → crest
            SceneLayer(
                group = "conductor",
                sequence = listOf(
                    SceneStep(id = "calm", type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#000820"), colorTo = ColorRef.Hex("#001840"),
                        duration = 6000),
                    SceneStep(id = "swell", type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#001840"), colorTo = ColorRef.Hex("#001A50"),
                        duration = 6000),
                    SceneStep(id = "crest", type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#001A50"), colorTo = ColorRef.Hex("#000820"),
                        duration = 6000),
                ),
            ),
            // 2. deep-current — continuous slow breathe, ocean floor depth
            SceneLayer(
                group = "deep-current",
                async = "free",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(type = AnimationType.BREATHE,
                        colorFrom = ColorRef.Hex("#000820"),
                        colorTo   = ColorRef.PalettePosition(40),
                        duration  = 8000, loop = true),
                ),
            ),
            // 3. surface-wave — slow topology wave builds then strengthens at crest
            SceneLayer(
                group = "surface-wave",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(runner = RunnerType.WAVE,
                        color = ColorRef.PalettePosition(150),
                        waveWidth = 3, count = 2, duration = 12000),
                    SceneStep(runner = RunnerType.WAVE,
                        color = ColorRef.PalettePosition(180),
                        waveWidth = 2, count = 3, duration = 6000),
                ),
            ),
            // 4. geo-swell — crossing geometric wave → bounce pendulum → seafoam sparkle
            SceneLayer(
                group      = "geo-swell",
                startAfter = "conductor:calm",
                blend      = BlendMode.MAX,
                sequence   = listOf(
                    SceneStep(runner = RunnerType.WAVE,
                        directionality = "geometric", angle = 315,
                        color = ColorRef.PalettePosition(200),
                        waveWidth = 2, count = 3, duration = 4000),
                    SceneStep(runner = RunnerType.BOUNCE,
                        source = RunnerSourceToken.ROOT,
                        color  = ColorRef.PalettePosition(220),
                        width  = 2, duration = 2500),
                    SceneStep(runner = RunnerType.SPARKLE,
                        color  = ColorRef.Hex("#AADDFF"),
                        waves  = 8, width = 100, duration = 5500),
                ),
            ),
            // 5. depth-cool — DESATURATE arc (identity=255→100 then back) for watery feel
            SceneLayer(
                group      = "depth-cool",
                startAfter = "conductor:calm",
                sequence   = listOf(
                    SceneStep(type = AnimationType.FADE,
                        animates = AnimateTarget.DESATURATE,
                        from = 255, to = 100, duration = 6000),
                    SceneStep(type = AnimationType.FADE,
                        animates = AnimateTarget.DESATURATE,
                        from = 100, to = 255, duration = 6000),
                ),
            ),
        ),
    )

    // ── Spectrum Spin ─────────────────────────────────────────────────────────
    // 12 s loop. Showcases: HUE_CYCLE full rainbow base, WHEEL double-blade
    // spotlight (ADD), CHASE traveling accent from leaves.
    private val spectrumSpin = SceneJson(
        schemaVersion = 8,
        name = "SpectrumSpin",
        loop = true,
        colors = SceneColors(
            primary   = "#FF0000",
            secondary = "#00FF00",
            tertiary  = "#0000FF",
        ),
        layers = listOf(
            // 1. spectrum — HUE_CYCLE: full rainbow rotation over the scene window
            SceneLayer(
                group = "spectrum",
                sequence = listOf(
                    SceneStep(type = AnimationType.HUE_CYCLE,
                        duration = 12000, params = listOf(8)),
                ),
            ),
            // 2. wheel-cut — double-blade spotlight over the hue base (6 s/rotation × 2)
            SceneLayer(
                group = "wheel-cut",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(runner = RunnerType.WHEEL,
                        color = ColorRef.Hex("#FFFFFF"),
                        thickness = 60, lines = 2, duration = 6000),
                    SceneStep(runner = RunnerType.WHEEL,
                        color = ColorRef.Hex("#FFFFFF"),
                        thickness = 60, lines = 2, duration = 6000),
                ),
            ),
            // 3. chase-spark — traveling white highlight from leaves, free-running
            SceneLayer(
                group = "chase-spark",
                async = "free",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(runner = RunnerType.CHASE,
                        source  = RunnerSourceToken.LEAVES,
                        color   = ColorRef.Hex("#FFFFFF"),
                        count = 3, duration = 3000),
                ),
            ),
        ),
    )

    // ── Comet Cross ──────────────────────────────────────────────────────────
    // 10 s loop. Showcases: opposing CHASE directions (root→leaves vs
    // leaves→root) crossing through the topology, with SPARKLE particle dust.
    private val cometCross = SceneJson(
        schemaVersion = 8,
        name = "CometCross",
        loop = true,
        palette = "Aurora",
        colors = SceneColors(
            primary   = "#00CCFF",
            secondary = "#FF6600",
            tertiary  = "#110022",
        ),
        layers = listOf(
            // 1. void — deep space ambient breathe, free-running
            SceneLayer(
                group = "void",
                async = "free",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(type = AnimationType.BREATHE,
                        colorFrom = ColorRef.Hex("#030008"),
                        colorTo   = ColorRef.Hex("#08001A"),
                        duration  = 5000, loop = true),
                ),
            ),
            // 2. comet-inward — icy blue CHASE root→leaves
            SceneLayer(
                group = "comet-inward",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(runner = RunnerType.CHASE,
                        source  = RunnerSourceToken.ROOT,
                        color   = ColorRef.PalettePosition(20),
                        count = 4, duration = 10000),
                ),
            ),
            // 3. comet-outward — warm orange CHASE leaves→root (reverse)
            SceneLayer(
                group = "comet-outward",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(runner = RunnerType.CHASE,
                        source  = RunnerSourceToken.LEAVES,
                        color   = ColorRef.BaseColorSlot(1),
                        count = 4, duration = 10000),
                ),
            ),
            // 4. comet-dust — ambient sparkle where comets cross, free-running
            SceneLayer(
                group = "comet-dust",
                async = "free",
                blend = BlendMode.SCREEN,
                sequence = listOf(
                    SceneStep(runner = RunnerType.SPARKLE,
                        color  = ColorRef.Hex("#FFFFFF"),
                        waves  = 4, width = 80, duration = 0),
                ),
            ),
        ),
    )

    // ── Thunderbolt ──────────────────────────────────────────────────────────
    // 15 s scene. Showcases: building RAIN intensity, SPARKLE lightning burst,
    // BOUNCE aftershock, BRIGHTEN flash spike + DIM aftermath — all gated on
    // conductor phases.
    //
    // Timeline
    //  0s     4s         10s      15s
    //  |      |           |        |
    //  conductor [distant─][building─][strike──]
    //  storm-cloud [BREATHE continuous free ────]
    //  drizzle  [RAIN×2 low→heavy 15 s─────────]
    //  lightning              [SPARKLE][BOUNCE][SPARKLE]
    //  flash                  [BRIGHTEN↑][BRIGHTEN↓][DIM↓][DIM↑]
    private val thunderbolt = SceneJson(
        schemaVersion = 8,
        name = "Thunderbolt",
        loop = true,
        palette = "Ocean Blue",
        colors = SceneColors(
            primary   = "#FFFFFF",
            secondary = "#0033AA",
            tertiary  = "#001020",
        ),
        layers = listOf(
            // 1. conductor — distant → building → strike
            SceneLayer(
                group = "conductor",
                sequence = listOf(
                    SceneStep(id = "distant", type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#000810"), colorTo = ColorRef.Hex("#001428"),
                        duration = 4000),
                    SceneStep(id = "building", type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#001428"), colorTo = ColorRef.Hex("#002040"),
                        duration = 6000),
                    SceneStep(id = "strike", type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#002040"), colorTo = ColorRef.Hex("#000810"),
                        duration = 5000),
                ),
            ),
            // 2. storm-cloud — dark blue breathe, continuous pressure
            SceneLayer(
                group = "storm-cloud",
                async = "free",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(type = AnimationType.BREATHE,
                        colorFrom = ColorRef.Hex("#000A18"),
                        colorTo   = ColorRef.Hex("#00183A"),
                        duration  = 7000, loop = true),
                ),
            ),
            // 3. drizzle — low rain builds to heavy; timing matches conductor end
            SceneLayer(
                group = "drizzle",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(runner = RunnerType.RAIN,
                        color = ColorRef.PalettePosition(100),
                        waves = 3, speed = 2000, width = 1, duration = 10000),
                    SceneStep(runner = RunnerType.RAIN,
                        color = ColorRef.PalettePosition(120),
                        waves = 6, speed = 1200, width = 2, duration = 5000),
                ),
            ),
            // 4. lightning — SPARKLE eruption + BOUNCE shockwave + aftershock sparkle
            SceneLayer(
                group      = "lightning",
                startAfter = "conductor:building",
                blend      = BlendMode.SCREEN,
                sequence   = listOf(
                    SceneStep(runner = RunnerType.SPARKLE,
                        color  = ColorRef.Hex("#FFFFFF"),
                        waves  = 15, width = 40, duration = 2000),
                    SceneStep(runner = RunnerType.BOUNCE,
                        source = RunnerSourceToken.ROOT,
                        color  = ColorRef.Hex("#AACCFF"),
                        width  = 3, duration = 1500),
                    SceneStep(runner = RunnerType.SPARKLE,
                        color  = ColorRef.Hex("#FFFFFF"),
                        waves  = 6, width = 80, duration = 1500),
                ),
            ),
            // 5. flash — BRIGHTEN spike then DIM aftermath; starts with lightning
            SceneLayer(
                group      = "flash",
                startAfter = "conductor:building",
                sequence   = listOf(
                    SceneStep(type = AnimationType.FADE,
                        animates = AnimateTarget.BRIGHTEN,
                        from = 0, to = 200, duration = 500),
                    SceneStep(type = AnimationType.FADE,
                        animates = AnimateTarget.BRIGHTEN,
                        from = 200, to = 0, duration = 1500),
                    SceneStep(type = AnimationType.FADE,
                        animates = AnimateTarget.DIM,
                        from = 255, to = 40, duration = 2000),
                    SceneStep(type = AnimationType.FADE,
                        animates = AnimateTarget.DIM,
                        from = 40, to = 255, duration = 1000),
                ),
            ),
        ),
    )

    // ── Candle Flicker ────────────────────────────────────────────────────────
    // 14 s scene. Showcases: BREATHE + PULSE layered for organic flame, topology
    // RAIN for gust effect, DESATURATE modifier for heat distortion.
    //
    // Timeline
    //  0s       5s          10s      14s
    //  |        |            |        |
    //  conductor [still──────][gust───][recovery─]
    //  flame     [BREATHE continuous free ─────────]
    //  flicker   [PULSE continuous free ────────────]
    //  gust-effect         [RAIN×2][gap─────────]
    //  heat-blur           [desat→100][→255──────]
    private val candleFlicker = SceneJson(
        schemaVersion = 8,
        name = "CandleFlicker",
        loop = true,
        palette = "Warm Sunset",
        colors = SceneColors(
            primary   = "#FF8800",
            secondary = "#FF3300",
            tertiary  = "#1A0800",
        ),
        layers = listOf(
            // 1. conductor — still flame → wind gust → recovery
            SceneLayer(
                group = "conductor",
                sequence = listOf(
                    SceneStep(id = "still", type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#1A0800"), colorTo = ColorRef.Hex("#3A1400"),
                        duration = 5000),
                    SceneStep(id = "gust", type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#3A1400"), colorTo = ColorRef.Hex("#0A0400"),
                        duration = 5000),
                    SceneStep(type = AnimationType.FADE,
                        colorFrom = ColorRef.Hex("#0A0400"), colorTo = ColorRef.Hex("#1A0800"),
                        duration = 4000),
                ),
            ),
            // 2. flame — warm amber BREATHE, the steady candle body
            SceneLayer(
                group = "flame",
                async = "free",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(type = AnimationType.BREATHE,
                        colorFrom = ColorRef.Hex("#FF4400"),
                        colorTo   = ColorRef.Hex("#FF8800"),
                        duration  = 1800, loop = true),
                ),
            ),
            // 3. flicker — fast PULSE layered above for organic micro-variation
            //    rise=16%, fall=55%, hold=29% — creates a quick pop that trails off
            SceneLayer(
                group = "flicker",
                async = "free",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(type = AnimationType.PULSE,
                        colorFrom = ColorRef.Hex("#1A0800"),
                        colorTo   = ColorRef.Hex("#FF6600"),
                        duration  = 600, loop = true,
                        params    = listOf(40, 140)),
                ),
            ),
            // 4. gust-effect — sudden RAIN when gust hits, then gap to match conductor
            SceneLayer(
                group      = "gust-effect",
                startAfter = "conductor:still",
                blend      = BlendMode.MAX,
                sequence   = listOf(
                    SceneStep(runner = RunnerType.RAIN,
                        color = ColorRef.BaseColorSlot(1),
                        waves = 8, speed = 600, width = 2, duration = 3000),
                    SceneStep(runner = RunnerType.RAIN,
                        color = ColorRef.BaseColorSlot(0),
                        waves = 4, speed = 1000, width = 1, duration = 2000),
                    SceneStep(duration = 4000),
                ),
            ),
            // 5. heat-blur — DESATURATE rises during gust (identity=255→100), restores during recovery
            SceneLayer(
                group      = "heat-blur",
                startAfter = "conductor:still",
                sequence   = listOf(
                    SceneStep(type = AnimationType.FADE,
                        animates = AnimateTarget.DESATURATE,
                        from = 255, to = 100, duration = 3000),
                    SceneStep(type = AnimationType.FADE,
                        animates = AnimateTarget.DESATURATE,
                        from = 100, to = 255, duration = 6000),
                ),
            ),
        ),
    )

    // ── Strobe Party ─────────────────────────────────────────────────────────
    // 7 s loop. Showcases: HUE_CYCLE full-spectrum base, WHEEL 4-blade disco
    // geometry (ADD), STROBE high-frequency flash overlay (SCREEN).
    private val strobeParty = SceneJson(
        schemaVersion = 8,
        name = "StrobeParty",
        loop = true,
        colors = SceneColors(
            primary   = "#FF0000",
            secondary = "#00FF00",
            tertiary  = "#0000FF",
        ),
        layers = listOf(
            // 1. color-engine — HUE_CYCLE: fast full rainbow rotation
            SceneLayer(
                group = "color-engine",
                sequence = listOf(
                    SceneStep(type = AnimationType.HUE_CYCLE,
                        duration = 7000, params = listOf(12)),
                ),
            ),
            // 2. disco-wheel — 4-blade spinning spotlight over the hue base
            SceneLayer(
                group = "disco-wheel",
                blend = BlendMode.ADD,
                sequence = listOf(
                    SceneStep(runner = RunnerType.WHEEL,
                        color     = ColorRef.Hex("#FFFFFF"),
                        thickness = 45, lines = 4, duration = 7000),
                ),
            ),
            // 3. strobe — 12 Hz flash overlay
            SceneLayer(
                group = "strobe",
                blend = BlendMode.SCREEN,
                sequence = listOf(
                    SceneStep(type = AnimationType.STROBE,
                        color    = ColorRef.Hex("#FFFFFF"),
                        duration = 7000, params = listOf(12)),
                ),
            ),
        ),
    )

    val defaultScenes: List<SceneJson> = listOf(
        auroraStorm,
        heartPulse,
        digitalRain,
        wheelOfFire,
        oceanSwell,
        spectrumSpin,
        cometCross,
        thunderbolt,
        candleFlicker,
        strobeParty,
    )
}
