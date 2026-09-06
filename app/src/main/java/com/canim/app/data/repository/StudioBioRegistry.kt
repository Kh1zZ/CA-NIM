package com.canim.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.canim.app.data.model.StudioBioInfo
import org.json.JSONObject

/**
 * Factual registry and persistent cache for Anime Studio details (bio, founded year, country, official site).
 * Includes curated metadata for 30+ major studios with 0ms instant display,
 * backed by persistent storage with 30-day TTL for dynamic lookups.
 */
object StudioBioRegistry {

    private const val PREFS_NAME = "canim_studio_cache"
    private const val KEY_PREFIX = "studio_bio_"
    private const val TTL_MS = 30L * 24 * 60 * 60 * 1000L // 30 days

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    // Curated factual registry for top animation studios
    private val curatedStudios: Map<String, StudioBioInfo> = listOf(
        StudioBioInfo(
            studioId = 569,
            name = "MAPPA",
            foundedYear = 2011,
            country = "Jepang",
            officialSite = "http://www.mappa.co.jp/",
            bio = "Didirikan oleh Masao Maruyama (salah satu pendiri Madhouse) pada tahun 2011. MAPPA dikenal dengan kualitas animasi aksi berkecepatan tinggi dan adaptasi karya-karya ikonik seperti Jujutsu Kaisen, Attack on Titan The Final Season, Chainsaw Man, dan Vinland Saga Season 2."
        ),
        StudioBioInfo(
            studioId = 43,
            name = "ufotable",
            foundedYear = 2000,
            country = "Jepang",
            officialSite = "http://www.ufotable.com/",
            bio = "Studio yang berbasis di Suginami, Tokyo, terkenal di dunia dengan integrasi visual 2D dan efek 3D CGI photorealistic yang memukau. Mahakarya mereka mencakup serial Demon Slayer: Kimetsu no Yaiba serta trilogi Fate/stay night: Heaven's Feel dan Unlimited Blade Works."
        ),
        StudioBioInfo(
            studioId = 2,
            name = "Kyoto Animation",
            foundedYear = 1981,
            country = "Jepang",
            officialSite = "http://www.kyotoanimation.co.jp/",
            bio = "Sering dijuluki 'KyoAni', studio legendaris asal Uji, Kyoto yang terkenal dengan dedikasi luar biasa terhadap kesejahteraan animator in-house. Terkenal dengan detail animasi halus dan emosional seperti Violet Evergarden, A Silent Voice, Hyouka, K-On!, dan Clannad."
        ),
        StudioBioInfo(
            studioId = 4,
            name = "Bones",
            foundedYear = 1998,
            country = "Jepang",
            officialSite = "http://www.bones.co.jp/",
            bio = "Didirikan oleh mantan staf Sunrise (Masahiko Minami dkk). Bones memegang reputasi emas untuk koreografi aksi tanpa kompromi, animasi sakuga dinamis, dan konsistensi tinggi dalam Fullmetal Alchemist: Brotherhood, My Hero Academia, Mob Psycho 100, dan Bungo Stray Dogs."
        ),
        StudioBioInfo(
            studioId = 858,
            name = "Wit Studio",
            foundedYear = 2012,
            country = "Jepang",
            officialSite = "http://www.witstudio.co.jp/",
            bio = "Awalnya didirikan sebagai anak perusahaan IG Port oleh George Wada. Wit Studio menggemparkan industri anime melalui 3 musim pertama Attack on Titan, Spy x Family (kolaborasi dengan CloverWorks), Vinland Saga Season 1, dan Ranking of Kings."
        ),
        StudioBioInfo(
            studioId = 11,
            name = "Madhouse",
            foundedYear = 1972,
            country = "Jepang",
            officialSite = "http://www.madhouse.co.jp/",
            bio = "Salah satu studio animasi paling bersejarah di Jepang yang melahirkan karya legendaris selama beberapa dekade, termasuk Death Note, Hunter x Hunter (2011), Monster, One Punch Man Season 1, dan kesuksesan modern Frieren: Beyond Journey's End."
        ),
        StudioBioInfo(
            studioId = 6214,
            name = "CloverWorks",
            foundedYear = 2018,
            country = "Jepang",
            officialSite = "https://cloverworks.co.jp/",
            bio = "Studio modern yang berkembang pesat dari bekas A-1 Pictures Koenji Studio. Terkenal dengan karakter ekspresif dan visual trendi seperti Bocchi the Rock!, The Promised Neverland, Spy x Family, My Dress-Up Darling, dan Horimiya."
        ),
        StudioBioInfo(
            studioId = 56,
            name = "A-1 Pictures",
            foundedYear = 2005,
            country = "Jepang",
            officialSite = "http://www.a1p.jp/",
            bio = "Anak perusahaan animasi dari Aniplex (Sony Music Entertainment Japan). A-1 Pictures memiliki kapasitas produksi besar yang menghasilkan deretan waralaba terpopuler seperti Solo Leveling, Sword Art Online, Kaguya-sama: Love Is War, 86 Eighty-Six, dan Fate/Apocrypha."
        ),
        StudioBioInfo(
            studioId = 10,
            name = "Production I.G",
            foundedYear = 1987,
            country = "Jepang",
            officialSite = "http://www.production-ig.co.jp/",
            bio = "Pelopor animasi sci-fi dan olahraga beranggaran tinggi. Dikenal secara global melalui Ghost in the Shell, serial olahraga fenomenal Haikyu!!, Kuroko no Basket, Psycho-Pass, dan Heavenly Delusion."
        ),
        StudioBioInfo(
            studioId = 44,
            name = "Shaft",
            foundedYear = 1975,
            country = "Jepang",
            officialSite = "http://www.shaft-web.co.jp/",
            bio = "Terkenal dengan gaya visual avant-garde, sudut kamera eksentrik, dan 'head tilt' yang menjadi ciri khas sutradara Akiyuki Shinbo. Karya terkenalnya meliputi Monogatari Series, Puella Magi Madoka Magica, Nisekoi, dan March Comes in Like a Lion."
        ),
        StudioBioInfo(
            studioId = 290,
            name = "CoMix Wave Films",
            foundedYear = 2007,
            country = "Jepang",
            officialSite = "http://www.cwfilms.jp/",
            bio = "Studio film animasi yang memproduksi karya-karya sinematik megah Makoto Shinkai dengan visual pencahayaan pemandangan spektakuler seperti Kimi no Na wa (Your Name), Weathering With You, Suzume, dan 5 Centimeters per Second."
        ),
        StudioBioInfo(
            studioId = 18,
            name = "Toei Animation",
            foundedYear = 1948,
            country = "Jepang",
            officialSite = "http://www.toei-anim.co.jp/",
            bio = "Raksasa animasi tertua di Jepang yang membawa anime ke panggung dunia. Pembuat waralaba anime terlama dan tersukses sepanjang masa seperti One Piece, Dragon Ball, Sailor Moon, Slam Dunk, dan Digimon."
        ),
        StudioBioInfo(
            studioId = 14,
            name = "Sunrise",
            foundedYear = 1972,
            country = "Jepang",
            officialSite = "http://www.sunrise-inc.co.jp/",
            bio = "Kini bagian dari Bandai Namco Filmworks, Sunrise adalah raja mecha animasi Jepang dengan waralaba legendaris Mobile Suit Gundam, Code Geass, Cowboy Bebop, Gintama, dan seri idol Love Live!."
        ),
        StudioBioInfo(
            studioId = 21,
            name = "Studio Ghibli",
            foundedYear = 1985,
            country = "Jepang",
            officialSite = "http://www.ghibli.jp/",
            bio = "Studio film animasi kelas dunia yang didirikan oleh Hayao Miyazaki dan Isao Takahata. Pemenang Academy Award dengan mahakarya sepanjang masa seperti Spirited Away, My Neighbor Totoro, Princess Mononoke, dan Howl\'s Moving Castle."
        ),
        StudioBioInfo(
            studioId = 287,
            name = "David Production",
            foundedYear = 2007,
            country = "Jepang",
            officialSite = "http://davidproduction.jp/",
            bio = "Studio yang diakui atas adaptasi setia karya manga ikonik Hirohiko Araki, JoJo\'s Bizarre Adventure, serta visual api spektakuler di Fire Force dan serial edukatif Cells at Work!."
        ),
        StudioBioInfo(
            studioId = 803,
            name = "Trigger",
            foundedYear = 2011,
            country = "Jepang",
            officialSite = "http://www.st-trigger.co.jp/",
            bio = "Didirikan oleh mantan animator Gainax (Hiroyuki Imaishi dkk). Terkenal dengan gaya animasi ekspresif, eksplosif, dan over-the-top seperti Kill la Kill, Cyberpunk: Edgerunners, Tengen Toppa Gurren Lagann spiritual successors, dan Delicious in Dungeon."
        ),
        StudioBioInfo(
            studioId = 95,
            name = "Doga Kobo",
            foundedYear = 1973,
            country = "Jepang",
            officialSite = "http://www.dogakobo.com/",
            bio = "Awalnya terkenal dengan anime komedi slice-of-life bernuansa ceria dan ekspresi imut, Doga Kobo mencapai puncak popularitas global melalui produksi fenomenal Oshi no Ko, Plastic Memories, dan Monthly Girls\' Nozaki-kun."
        ),
        StudioBioInfo(
            studioId = 132,
            name = "P.A. Works",
            foundedYear = 2000,
            country = "Jepang",
            officialSite = "http://www.pa-works.jp/",
            bio = "Studio asal Prefektur Toyama yang dikenal dengan pemandangan latar belakang hiper-realistis dan drama kehidupan kerja serta kehidupan remaja seperti Angel Beats!, Charlotte, Shirobako, dan Ya Boy Kongming!."
        ),
        StudioBioInfo(
            studioId = 7,
            name = "J.C.Staff",
            foundedYear = 1986,
            country = "Jepang",
            officialSite = "http://www.jcstaff.co.jp/",
            bio = "Studio produktif yang telah memproduksi ratusan anime populer lintas genre, terkenal melalui Toradora!, DanMachi (Is It Wrong to Try to Pick Up Girls in a Dungeon?), Shokugeki no Souma (Food Wars!), dan seri A Certain Magical Index."
        ),
        StudioBioInfo(
            studioId = 300,
            name = "Silver Link.",
            foundedYear = 2007,
            country = "Jepang",
            officialSite = "http://silverlink.co.jp/",
            bio = "Dipimpin oleh Shin Oonuma, studio ini menghasilkan banyak serial komedi dan fantasi populer seperti Non Non Biyori, Kokoro Connect, Fate/kaleid liner Prisma Illya, dan The Misfit of Demon King Academy."
        ),
        StudioBioInfo(
            studioId = 456,
            name = "Lerche",
            foundedYear = 2011,
            country = "Jepang",
            officialSite = "http://www.lerche.jp/",
            bio = "Unit animasi Studio Hibari yang memproduksi Classroom of the Elite, Assassination Classroom, Danganronpa, Astra Lost in Space, dan serial drama musik Given."
        ),
        StudioBioInfo(
            studioId = 314,
            name = "White Fox",
            foundedYear = 2007,
            country = "Jepang",
            officialSite = "http://w-fox.co.jp/",
            bio = "Studio yang disegani atas adaptasi cerita mendalam penuh ketegangan, termasuk serial legendaris Steins;Gate, Re:Zero - Starting Life in Another World, Akame ga Kill!, Katanagatari, dan Goblin Slayer."
        ),
        StudioBioInfo(
            studioId = 6734,
            name = "Studio Bind",
            foundedYear = 2018,
            country = "Jepang",
            officialSite = "https://studiobind.jp/",
            bio = "Studio joint-venture antara White Fox dan Egg Firm yang secara khusus didirikan untuk memproduksi adaptasi anime bertaraf tinggi Mushoku Tensei: Jobless Reincarnation."
        ),
        StudioBioInfo(
            studioId = 296,
            name = "Kinema Citrus",
            foundedYear = 2008,
            country = "Jepang",
            officialSite = "http://kinemacitrus.biz/",
            bio = "Studio yang dikenal dengan desain dunia fantasi yang kaya dan sinematik seperti Made in Abyss, The Rising of the Shield Hero, dan Revue Starlight."
        ),
        StudioBioInfo(
            studioId = 91,
            name = "feel.",
            foundedYear = 2002,
            country = "Jepang",
            officialSite = "http://www.feel-ing.com/",
            bio = "Terkenal dengan animasi slice-of-life dan drama emosional bertekstur lembut seperti My Teen Romantic Comedy SNAFU (Oregairu Season 2 & 3), Hinamatsuri, Tsuki ga Kirei, dan Dagashi Kashi."
        ),
        StudioBioInfo(
            studioId = 68,
            name = "TMS Entertainment",
            foundedYear = 1946,
            country = "Jepang",
            officialSite = "http://www.tms-e.co.jp/",
            bio = "Salah satu studio tertua dan terbesar di Jepang, memproduksi Detective Conan (Case Closed), Dr. STONE, Fruits Basket (2019), Megalo Box, dan Lupin the Third."
        ),
        StudioBioInfo(
            studioId = 28,
            name = "OLM",
            foundedYear = 1990,
            country = "Jepang",
            officialSite = "http://www.olm.co.jp/",
            bio = "Studio di balik serial raksasa waralaba Pokémon, yang juga sukses menggarap adaptasi modern berkelas tinggi seperti The Apothecary Diaries, Komi Can\'t Communicate, dan Summertime Rendering."
        ),
        StudioBioInfo(
            studioId = 1,
            name = "Pierrot",
            foundedYear = 1979,
            country = "Jepang",
            officialSite = "http://pierrot.jp/",
            bio = "Studio terkemuka pembuat serial anime Shonen melegenda di seluruh dunia: Naruto & Naruto Shippuden, Bleach & Thousand-Year Blood War, Tokyo Ghoul, Black Clover, dan Yu Yu Hakusho."
        ),
        StudioBioInfo(
            studioId = 1591,
            name = "Science SARU",
            foundedYear = 2013,
            country = "Jepang",
            officialSite = "https://www.sciencesaru.com/",
            bio = "Didirikan oleh Masaaki Yuasa dan Eunyoung Choi. Terkenal dengan eksperimentasi animasi 2D/Flash ekspresif dan inovatif seperti Dandadan, Devilman Crybaby, Keep Your Hands Off Eizouken!, dan The Heike Story."
        ),
        StudioBioInfo(
            studioId = 418,
            name = "LIDENFILMS",
            foundedYear = 2012,
            country = "Jepang",
            officialSite = "http://lidenfilms.jp/",
            bio = "Studio produktif anggota Ultra Super Pictures yang memproduksi Tokyo Revengers, Rurouni Kenshin (2023), Call of the Night, dan Yamada-kun and the Seven Witches."
        )
    ).associateBy { it.name.lowercase().trim() }

    /**
     * Resolves studio factual bio. First checks curated in-memory registry (0 ms),
     * then persistent SharedPreferences cache, and returns a merged model.
     */
    fun getStudioInfo(studioId: Int, studioName: String): StudioBioInfo {
        val normalizedName = studioName.lowercase().trim()

        // 1. Check curated in-memory registry first
        val curated = curatedStudios[normalizedName]
            ?: curatedStudios.values.firstOrNull { it.studioId == studioId }
            ?: curatedStudios.entries.firstOrNull { normalizedName.contains(it.key) || it.key.contains(normalizedName) }?.value

        if (curated != null) {
            return curated.copy(studioId = studioId, name = studioName)
        }

        // 2. Check persistent SharedPreferences cache
        val cached = getFromPersistentCache(studioId)
        if (cached != null) {
            return cached
        }

        // 3. Fallback default
        val fallback = StudioBioInfo(
            studioId = studioId,
            name = studioName,
            country = "Jepang",
            bio = "Studio animasi anime asal Jepang yang aktif memproduksi serial dan film animasi berkualitas untuk penonton di seluruh dunia."
        )
        saveToPersistentCache(fallback)
        return fallback
    }

    /**
     * Saves or updates studio info in persistent cache with 30-day TTL.
     */
    fun saveToPersistentCache(info: StudioBioInfo) {
        val p = prefs ?: return
        try {
            val json = JSONObject().apply {
                put("studioId", info.studioId)
                put("name", info.name)
                put("foundedYear", info.foundedYear ?: JSONObject.NULL)
                put("country", info.country)
                put("officialSite", info.officialSite ?: JSONObject.NULL)
                put("bio", info.bio ?: JSONObject.NULL)
                put("totalAnime", info.totalAnime ?: JSONObject.NULL)
                put("favourites", info.favourites ?: JSONObject.NULL)
                put("timestamp", System.currentTimeMillis())
            }
            p.edit().putString(KEY_PREFIX + info.studioId, json.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun getFromPersistentCache(studioId: Int): StudioBioInfo? {
        val p = prefs ?: return null
        val raw = p.getString(KEY_PREFIX + studioId, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val ts = json.optLong("timestamp", 0L)
            if (System.currentTimeMillis() - ts > TTL_MS) {
                p.edit().remove(KEY_PREFIX + studioId).apply()
                return null
            }
            StudioBioInfo(
                studioId = json.optInt("studioId", studioId),
                name = json.optString("name", "Studio"),
                foundedYear = if (json.has("foundedYear") && !json.isNull("foundedYear")) json.optInt("foundedYear") else null,
                country = json.optString("country", "Jepang"),
                officialSite = if (json.has("officialSite") && !json.isNull("officialSite")) json.optString("officialSite") else null,
                bio = if (json.has("bio") && !json.isNull("bio")) json.optString("bio") else null,
                totalAnime = if (json.has("totalAnime") && !json.isNull("totalAnime")) json.optInt("totalAnime") else null,
                favourites = if (json.has("favourites") && !json.isNull("favourites")) json.optInt("favourites") else null
            )
        } catch (_: Exception) {
            null
        }
    }
}
