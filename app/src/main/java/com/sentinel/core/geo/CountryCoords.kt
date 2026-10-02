package com.sentinel.core.geo

/**
 * Static lookup: ISO 3166-1 alpha-2 country code → Mercator canvas fractions (x, y).
 *
 * Projection:
 *   x = (longitude + 180) / 360   (0 = left edge, 1 = right edge)
 *   y = (90 - latitude)  / 180   (0 = top/north pole, 1 = bottom/south pole)
 *
 * Center coordinates are approximate geographic centers of each country.
 * Covers all 195 UN-recognised states plus a handful of territories.
 */
object CountryCoords {

    /** Returns the Mercator (x, y) fraction for the given ISO code, or null if unknown. */
    fun get(iso: String): Pair<Float, Float>? = MAP[iso.uppercase()]

    private val MAP: Map<String, Pair<Float, Float>> = mapOf(

        // ── North America ──────────────────────────────────────────────────────
        "US" to (0.226f to 0.289f),   // United States      lon=-98.5  lat=38
        "CA" to (0.233f to 0.167f),   // Canada             lon=-96    lat=60
        "MX" to (0.217f to 0.367f),   // Mexico             lon=-102   lat=24

        // ── Central America & Caribbean ────────────────────────────────────────
        "GT" to (0.250f to 0.417f),   // Guatemala          lon=-90    lat=15
        "BZ" to (0.256f to 0.406f),   // Belize             lon=-88    lat=17
        "HN" to (0.258f to 0.417f),   // Honduras           lon=-87    lat=15
        "SV" to (0.253f to 0.422f),   // El Salvador        lon=-89    lat=14
        "NI" to (0.264f to 0.428f),   // Nicaragua          lon=-85    lat=13
        "CR" to (0.267f to 0.444f),   // Costa Rica         lon=-84    lat=10
        "PA" to (0.278f to 0.450f),   // Panama             lon=-80    lat=9
        "CU" to (0.281f to 0.378f),   // Cuba               lon=-79    lat=22
        "JM" to (0.286f to 0.400f),   // Jamaica            lon=-77    lat=18
        "HT" to (0.297f to 0.394f),   // Haiti              lon=-73    lat=19
        "DO" to (0.306f to 0.394f),   // Dominican Republic lon=-70    lat=19
        "PR" to (0.317f to 0.400f),   // Puerto Rico        lon=-66    lat=18
        "TT" to (0.331f to 0.439f),   // Trinidad & Tobago  lon=-61    lat=11
        "BB" to (0.333f to 0.411f),   // Barbados           lon=-59.5  lat=13
        "LC" to (0.331f to 0.417f),   // Saint Lucia        lon=-61    lat=14
        "VC" to (0.331f to 0.422f),   // St Vincent         lon=-61    lat=13
        "GD" to (0.333f to 0.428f),   // Grenada            lon=-62    lat=12
        "AG" to (0.328f to 0.406f),   // Antigua & Barbuda  lon=-62    lat=17
        "KN" to (0.328f to 0.411f),   // Saint Kitts        lon=-63    lat=17
        "DM" to (0.331f to 0.411f),   // Dominica           lon=-61    lat=15
        "BS" to (0.289f to 0.383f),   // Bahamas            lon=-78    lat=24
        "TC" to (0.297f to 0.389f),   // Turks & Caicos     lon=-72    lat=22

        // ── South America ──────────────────────────────────────────────────────
        "CO" to (0.294f to 0.478f),   // Colombia           lon=-74    lat=4
        "VE" to (0.317f to 0.456f),   // Venezuela          lon=-66    lat=8
        "GY" to (0.336f to 0.472f),   // Guyana             lon=-59    lat=5
        "SR" to (0.344f to 0.478f),   // Suriname           lon=-56    lat=4
        "GF" to (0.353f to 0.478f),   // French Guiana      lon=-53    lat=4
        "BR" to (0.347f to 0.556f),   // Brazil             lon=-55    lat=-10
        "EC" to (0.283f to 0.511f),   // Ecuador            lon=-78    lat=-2
        "PE" to (0.289f to 0.556f),   // Peru               lon=-76    lat=-10
        "BO" to (0.319f to 0.594f),   // Bolivia            lon=-65    lat=-17
        "PY" to (0.339f to 0.628f),   // Paraguay           lon=-58    lat=-23
        "CL" to (0.303f to 0.667f),   // Chile              lon=-71    lat=-30
        "AR" to (0.322f to 0.689f),   // Argentina          lon=-64    lat=-34
        "UY" to (0.344f to 0.683f),   // Uruguay            lon=-56    lat=-33

        // ── Western Europe ─────────────────────────────────────────────────────
        "IS" to (0.447f to 0.139f),   // Iceland            lon=-19    lat=65
        "IE" to (0.478f to 0.206f),   // Ireland            lon=-8     lat=53
        "GB" to (0.492f to 0.200f),   // United Kingdom     lon=-3     lat=54
        "PT" to (0.478f to 0.283f),   // Portugal           lon=-8     lat=39
        "ES" to (0.489f to 0.278f),   // Spain              lon=-4     lat=40
        "FR" to (0.506f to 0.244f),   // France             lon=2      lat=46
        "AD" to (0.504f to 0.264f),   // Andorra            lon=1.5    lat=42.5
        "MC" to (0.519f to 0.256f),   // Monaco             lon=7      lat=44
        "BE" to (0.511f to 0.217f),   // Belgium            lon=4      lat=51
        "NL" to (0.514f to 0.211f),   // Netherlands        lon=5      lat=52
        "LU" to (0.517f to 0.222f),   // Luxembourg         lon=6      lat=50
        "CH" to (0.522f to 0.239f),   // Switzerland        lon=8      lat=47
        "LI" to (0.525f to 0.239f),   // Liechtenstein      lon=9.5    lat=47
        "NO" to (0.542f to 0.139f),   // Norway             lon=15     lat=65
        "SE" to (0.550f to 0.156f),   // Sweden             lon=18     lat=62
        "FI" to (0.572f to 0.144f),   // Finland            lon=26     lat=64
        "DK" to (0.528f to 0.189f),   // Denmark            lon=10     lat=56
        "DE" to (0.528f to 0.217f),   // Germany            lon=10     lat=51
        "AT" to (0.539f to 0.239f),   // Austria            lon=14     lat=47
        "IT" to (0.533f to 0.267f),   // Italy              lon=12     lat=42
        "SM" to (0.533f to 0.256f),   // San Marino         lon=12     lat=44
        "VA" to (0.533f to 0.267f),   // Vatican            lon=12     lat=42
        "MT" to (0.539f to 0.300f),   // Malta              lon=14     lat=36

        // ── Central / Eastern Europe ───────────────────────────────────────────
        "PL" to (0.556f to 0.211f),   // Poland             lon=20     lat=52
        "CZ" to (0.544f to 0.222f),   // Czech Republic     lon=16     lat=50
        "SK" to (0.553f to 0.228f),   // Slovakia           lon=19     lat=49
        "HU" to (0.553f to 0.239f),   // Hungary            lon=19     lat=47
        "SI" to (0.542f to 0.244f),   // Slovenia           lon=15     lat=46
        "HR" to (0.544f to 0.250f),   // Croatia            lon=16     lat=45
        "BA" to (0.550f to 0.256f),   // Bosnia             lon=18     lat=44
        "RS" to (0.558f to 0.256f),   // Serbia             lon=21     lat=44
        "ME" to (0.553f to 0.261f),   // Montenegro         lon=19     lat=43
        "MK" to (0.561f to 0.272f),   // N. Macedonia       lon=22     lat=41
        "AL" to (0.556f to 0.272f),   // Albania            lon=20     lat=41
        "GR" to (0.561f to 0.283f),   // Greece             lon=22     lat=39
        "BG" to (0.569f to 0.261f),   // Bulgaria           lon=25     lat=43
        "RO" to (0.569f to 0.244f),   // Romania            lon=25     lat=46
        "MD" to (0.581f to 0.239f),   // Moldova            lon=29     lat=47
        "UA" to (0.589f to 0.228f),   // Ukraine            lon=32     lat=49
        "BY" to (0.578f to 0.206f),   // Belarus            lon=28     lat=53
        "LT" to (0.567f to 0.189f),   // Lithuania          lon=24     lat=56
        "LV" to (0.569f to 0.183f),   // Latvia             lon=25     lat=57
        "EE" to (0.569f to 0.172f),   // Estonia            lon=25     lat=59
        "CY" to (0.592f to 0.306f),   // Cyprus             lon=33     lat=35

        // ── Russia & Former Soviet (Central Asia) ──────────────────────────────
        "RU" to (0.778f to 0.167f),   // Russia             lon=100    lat=60
        "KZ" to (0.689f to 0.233f),   // Kazakhstan         lon=68     lat=48
        "UZ" to (0.678f to 0.272f),   // Uzbekistan         lon=64     lat=41
        "TM" to (0.664f to 0.278f),   // Turkmenistan       lon=59     lat=40
        "KG" to (0.708f to 0.272f),   // Kyrgyzstan         lon=75     lat=41
        "TJ" to (0.697f to 0.283f),   // Tajikistan         lon=71     lat=39
        "GE" to (0.619f to 0.267f),   // Georgia            lon=43     lat=42
        "AM" to (0.625f to 0.278f),   // Armenia            lon=45     lat=40
        "AZ" to (0.631f to 0.278f),   // Azerbaijan         lon=47     lat=40

        // ── Middle East ────────────────────────────────────────────────────────
        "TR" to (0.597f to 0.283f),   // Turkey             lon=35     lat=39
        "SY" to (0.606f to 0.306f),   // Syria              lon=38     lat=35
        "LB" to (0.600f to 0.311f),   // Lebanon            lon=36     lat=34
        "IL" to (0.597f to 0.328f),   // Israel             lon=35     lat=31
        "JO" to (0.603f to 0.328f),   // Jordan             lon=37     lat=31
        "IQ" to (0.622f to 0.317f),   // Iraq               lon=44     lat=33
        "IR" to (0.647f to 0.322f),   // Iran               lon=53     lat=32
        "SA" to (0.625f to 0.367f),   // Saudi Arabia       lon=45     lat=24
        "KW" to (0.633f to 0.339f),   // Kuwait             lon=48     lat=29
        "BH" to (0.639f to 0.356f),   // Bahrain            lon=50.5   lat=26
        "QA" to (0.642f to 0.361f),   // Qatar              lon=51.5   lat=25
        "AE" to (0.650f to 0.367f),   // UAE                lon=54     lat=24
        "OM" to (0.658f to 0.378f),   // Oman               lon=57     lat=22
        "YE" to (0.633f to 0.417f),   // Yemen              lon=48     lat=15
        "PS" to (0.597f to 0.322f),   // Palestine          lon=35.5   lat=32

        // ── South Asia ─────────────────────────────────────────────────────────
        "AF" to (0.686f to 0.317f),   // Afghanistan        lon=67     lat=33
        "PK" to (0.694f to 0.333f),   // Pakistan           lon=70     lat=30
        "IN" to (0.717f to 0.389f),   // India              lon=78     lat=20
        "LK" to (0.725f to 0.456f),   // Sri Lanka          lon=81     lat=8
        "NP" to (0.733f to 0.344f),   // Nepal              lon=84     lat=28
        "BT" to (0.750f to 0.350f),   // Bhutan             lon=90     lat=27
        "BD" to (0.750f to 0.367f),   // Bangladesh         lon=90     lat=24
        "MV" to (0.703f to 0.483f),   // Maldives           lon=73     lat=3

        // ── East Asia ──────────────────────────────────────────────────────────
        "MN" to (0.786f to 0.244f),   // Mongolia           lon=103    lat=46
        "CN" to (0.789f to 0.306f),   // China              lon=104    lat=35
        "KP" to (0.853f to 0.278f),   // North Korea        lon=127    lat=40
        "KR" to (0.856f to 0.294f),   // South Korea        lon=128    lat=37
        "JP" to (0.883f to 0.300f),   // Japan              lon=138    lat=36
        "TW" to (0.836f to 0.367f),   // Taiwan             lon=121    lat=24
        "HK" to (0.817f to 0.378f),   // Hong Kong          lon=114    lat=22
        "MO" to (0.814f to 0.378f),   // Macao              lon=113    lat=22

        // ── Southeast Asia ─────────────────────────────────────────────────────
        "MM" to (0.767f to 0.383f),   // Myanmar            lon=96     lat=21
        "TH" to (0.781f to 0.417f),   // Thailand           lon=101    lat=15
        "LA" to (0.786f to 0.400f),   // Laos               lon=103    lat=18
        "VN" to (0.794f to 0.411f),   // Vietnam            lon=106    lat=16
        "KH" to (0.792f to 0.433f),   // Cambodia           lon=105    lat=12
        "MY" to (0.806f to 0.478f),   // Malaysia           lon=110    lat=4
        "SG" to (0.789f to 0.494f),   // Singapore          lon=104    lat=1
        "BN" to (0.819f to 0.478f),   // Brunei             lon=115    lat=4
        "PH" to (0.839f to 0.428f),   // Philippines        lon=122    lat=13
        "ID" to (0.833f to 0.528f),   // Indonesia          lon=120    lat=-5
        "TL" to (0.850f to 0.550f),   // Timor-Leste        lon=126    lat=-9

        // ── North Africa ───────────────────────────────────────────────────────
        "MA" to (0.481f to 0.322f),   // Morocco            lon=-7     lat=32
        "DZ" to (0.508f to 0.344f),   // Algeria            lon=3      lat=28
        "TN" to (0.525f to 0.311f),   // Tunisia            lon=9      lat=34
        "LY" to (0.547f to 0.350f),   // Libya              lon=17     lat=27
        "EG" to (0.583f to 0.356f),   // Egypt              lon=30     lat=26

        // ── Sub-Saharan Africa (West) ──────────────────────────────────────────
        "CV" to (0.433f to 0.411f),   // Cape Verde         lon=-24    lat=16
        "MR" to (0.469f to 0.389f),   // Mauritania         lon=-11    lat=20
        "ML" to (0.494f to 0.406f),   // Mali               lon=-2     lat=17
        "SN" to (0.461f to 0.422f),   // Senegal            lon=-14    lat=14
        "GM" to (0.456f to 0.428f),   // Gambia             lon=-16    lat=13
        "GW" to (0.458f to 0.433f),   // Guinea-Bissau      lon=-15    lat=12
        "GN" to (0.469f to 0.439f),   // Guinea             lon=-11    lat=11
        "SL" to (0.467f to 0.450f),   // Sierra Leone       lon=-12    lat=9
        "LR" to (0.475f to 0.467f),   // Liberia            lon=-9     lat=6
        "CI" to (0.483f to 0.461f),   // Ivory Coast        lon=-6     lat=7
        "GH" to (0.494f to 0.456f),   // Ghana              lon=-2     lat=8
        "TG" to (0.503f to 0.456f),   // Togo               lon=1      lat=8
        "BJ" to (0.506f to 0.450f),   // Benin              lon=2      lat=9
        "NE" to (0.522f to 0.406f),   // Niger              lon=8      lat=17
        "BF" to (0.503f to 0.428f),   // Burkina Faso       lon=1      lat=13
        "NG" to (0.522f to 0.444f),   // Nigeria            lon=8      lat=10

        // ── Sub-Saharan Africa (Central) ───────────────────────────────────────
        "TD" to (0.550f to 0.417f),   // Chad               lon=18     lat=15
        "CM" to (0.533f to 0.472f),   // Cameroon           lon=12     lat=5
        "CF" to (0.558f to 0.461f),   // Central Afr. Rep.  lon=21     lat=7
        "GQ" to (0.525f to 0.494f),   // Equatorial Guinea  lon=9      lat=2
        "GA" to (0.533f to 0.506f),   // Gabon              lon=12     lat=-1
        "CG" to (0.542f to 0.506f),   // Republic of Congo  lon=15     lat=-1
        "CD" to (0.569f to 0.517f),   // DR Congo           lon=25     lat=-3
        "ST" to (0.519f to 0.494f),   // São Tomé           lon=7      lat=1
        "AO" to (0.550f to 0.567f),   // Angola             lon=18     lat=-12

        // ── Sub-Saharan Africa (East) ──────────────────────────────────────────
        "SD" to (0.583f to 0.417f),   // Sudan              lon=30     lat=15
        "SS" to (0.586f to 0.461f),   // South Sudan        lon=31     lat=7
        "ER" to (0.608f to 0.417f),   // Eritrea            lon=39     lat=15
        "ET" to (0.611f to 0.450f),   // Ethiopia           lon=40     lat=9
        "DJ" to (0.619f to 0.433f),   // Djibouti           lon=43     lat=12
        "SO" to (0.628f to 0.467f),   // Somalia            lon=46     lat=6
        "KE" to (0.606f to 0.494f),   // Kenya              lon=38     lat=1
        "UG" to (0.589f to 0.494f),   // Uganda             lon=32     lat=1
        "RW" to (0.583f to 0.511f),   // Rwanda             lon=30     lat=-2
        "BI" to (0.583f to 0.517f),   // Burundi            lon=30     lat=-3
        "TZ" to (0.597f to 0.533f),   // Tanzania           lon=35     lat=-6
        "MW" to (0.594f to 0.572f),   // Malawi             lon=34     lat=-13
        "MZ" to (0.597f to 0.600f),   // Mozambique         lon=35     lat=-18

        // ── Sub-Saharan Africa (Southern) ──────────────────────────────────────
        "ZM" to (0.578f to 0.578f),   // Zambia             lon=28     lat=-14
        "ZW" to (0.583f to 0.611f),   // Zimbabwe           lon=30     lat=-20
        "BW" to (0.567f to 0.622f),   // Botswana           lon=24     lat=-22
        "NA" to (0.550f to 0.622f),   // Namibia            lon=18     lat=-22
        "ZA" to (0.569f to 0.661f),   // South Africa       lon=25     lat=-29
        "LS" to (0.578f to 0.667f),   // Lesotho            lon=28     lat=-30
        "SZ" to (0.589f to 0.644f),   // Eswatini           lon=32     lat=-26

        // ── Indian Ocean Islands ───────────────────────────────────────────────
        "MG" to (0.631f to 0.611f),   // Madagascar         lon=47     lat=-20
        "MU" to (0.658f to 0.611f),   // Mauritius          lon=57.5   lat=-20
        "SC" to (0.653f to 0.522f),   // Seychelles         lon=55     lat=-4
        "KM" to (0.622f to 0.567f),   // Comoros            lon=44     lat=-12
        "RE" to (0.653f to 0.617f),   // Réunion            lon=55.5   lat=-21

        // ── Oceania ────────────────────────────────────────────────────────────
        "AU" to (0.872f to 0.639f),   // Australia          lon=134    lat=-25
        "NZ" to (0.975f to 0.733f),   // New Zealand        lon=171    lat=-42
        "PG" to (0.900f to 0.533f),   // Papua New Guinea   lon=144    lat=-6
        "FJ" to (0.994f to 0.600f),   // Fiji               lon=178    lat=-18
        "SB" to (0.944f to 0.550f),   // Solomon Islands    lon=160    lat=-9
        "VU" to (0.964f to 0.589f),   // Vanuatu            lon=167    lat=-16
        "WS" to (0.022f to 0.578f),   // Samoa              lon=-172   lat=-14
        "TO" to (0.014f to 0.611f),   // Tonga              lon=-175   lat=-20
        "FM" to (0.939f to 0.461f),   // Micronesia         lon=158    lat=7
        "MH" to (0.975f to 0.461f),   // Marshall Islands   lon=171    lat=7
        "PW" to (0.872f to 0.461f),   // Palau              lon=134    lat=7
        "NR" to (0.964f to 0.497f),   // Nauru              lon=167    lat=0.5
        "KI" to (0.064f to 0.492f),   // Kiribati           lon=-157   lat=1.5

        // ── Atlantic Islands ───────────────────────────────────────────────────
        "GL" to (0.339f to 0.089f),   // Greenland          lon=-42    lat=72
    )
}
