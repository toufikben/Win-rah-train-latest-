package com.example.data

import com.example.model.AlertSeverity
import com.example.model.LineAlert
import com.example.model.NearbyStationInfo
import com.example.model.Station
import com.example.model.StationInterchange
import com.example.model.SuburbLine
import com.example.model.TransitConnection
import com.example.model.TransitType
import com.example.utils.GeoUtils

object TrainRepository {

    val suburbLines = listOf(
        SuburbLine(
            id = "thnia_algiers",
            name = "ضاحية الثنية - الجزائر العاصمة",
            description = "خط الضاحية الشرقية: الثنية، بومرداس، الرغاية، الرويبة، الحراش، آغا والجزائر (16 محطة)",
            inboundTerminus = "الجزائر (آغا)",
            outboundTerminus = "الثنية",
            stations = listOf(
                Station("st_thn", "الثنية", "THN", 36.7261, 3.5564, 1, "رصيف 1"),
                Station("st_tjd", "تيجلابين", "TJD", 36.7380, 3.5042, 2, "رصيف 2"),
                Station("st_bmr", "بومرداس", "BMR", 36.7594, 3.4731, 3, "رصيف 1"),
                Station("st_crs", "قورصو", "CRS", 36.7512, 3.4285, 4, "رصيف 2"),
                Station("st_bdw", "بودواو", "BDW", 36.7289, 3.3987, 5, "رصيف 1"),
                Station("st_rgh", "الرغاية", "RGH", 36.7358, 3.3402, 6, "رصيف 2"),
                Station("st_rwb", "الرويبة", "RWB", 36.7371, 3.2844, 7, "رصيف 1"),
                Station("st_rwz", "الرويبة الصناعية", "RWZ", 36.7250, 3.2610, 8, "رصيف 2"),
                Station("st_deb", "الدار البيضاء", "DEB", 36.7118, 3.2127, 9, "رصيف 1"),
                Station("st_bbz", "باب الزوار", "BBZ", 36.7160, 3.1850, 10, "رصيف 1"),
                Station("st_osm", "واد سمار", "OSM", 36.7088, 3.1691, 11, "رصيف 2"),
                Station("st_hrh", "الحراش", "HRH", 36.7214, 3.1328, 12, "رصيف 2"),
                Station("st_crb", "الخروبة", "CRB", 36.7380, 3.1020, 13, "رصيف 1"),
                Station("st_hsd", "حسين داي", "HSD", 36.7456, 3.0886, 14, "رصيف 1"),
                Station("st_agh", "آغا", "AGH", 36.7647, 3.0569, 15, "رصيف 3"),
                Station("st_alg", "الجزائر (المحطة المركزية)", "ALG", 36.7772, 3.0601, 16, "رصيف 1")
            )
        ),
        SuburbLine(
            id = "zeralda_algiers",
            name = "ضاحية زرالدة - الجزائر العاصمة",
            description = "خط الضاحية الغربية: زرالدة، سيدي عبد الله، بئر توتة، عين النعجة، الحراش، الجزائر (12 محطة)",
            inboundTerminus = "الجزائر (آغا)",
            outboundTerminus = "زرالدة",
            stations = listOf(
                Station("st_zrd", "زرالدة", "ZRD", 36.7122, 2.8423, 1, "رصيف 1"),
                Station("st_sab", "سيدي عبد الله", "SAB", 36.6855, 2.8712, 2, "رصيف 2"),
                Station("st_sau", "جامعة سيدي عبد الله", "SAU", 36.6710, 2.8890, 3, "رصيف 1"),
                Station("st_tsl", "تسالة المرجة", "TSL", 36.6540, 2.9460, 4, "رصيف 2"),
                Station("st_brt_z", "بئر توتة", "BRT", 36.6456, 3.0012, 5, "رصيف 1"),
                Station("st_bal_z", "بابا علي", "BAL", 36.6738, 3.0381, 6, "رصيف 2"),
                Station("st_gqc_z", "جسر قسنطينة", "GQC", 36.7052, 3.0851, 7, "رصيف 1"),
                Station("st_ann_z", "عين النعجة", "ANN", 36.7165, 3.0784, 8, "رصيف 2"),
                Station("st_hrh_z", "الحراش", "HRH", 36.7214, 3.1328, 9, "رصيف 1"),
                Station("st_hsd_z", "حسين داي", "HSD", 36.7456, 3.0886, 10, "رصيف 2"),
                Station("st_agh_z", "آغا", "AGH", 36.7647, 3.0569, 11, "رصيف 2"),
                Station("st_alg_z", "الجزائر (المحطة المركزية)", "ALG", 36.7772, 3.0601, 12, "رصيف 1")
            )
        ),
        SuburbLine(
            id = "algiers_affroun",
            name = "ضاحية الجزائر - البليدة - العفرون",
            description = "خط الضاحية الجنوبية: الجزائر، الحراش، بوفاريك، بني مراد، البليدة، الشفة، موزاية والعفرون (14 محطة)",
            inboundTerminus = "الجزائر (آغا)",
            outboundTerminus = "العفرون",
            stations = listOf(
                Station("st_alg_a", "الجزائر (المحطة المركزية)", "ALG", 36.7772, 3.0601, 1, "رصيف 1"),
                Station("st_agh_a", "آغا", "AGH", 36.7647, 3.0569, 2, "رصيف 4"),
                Station("st_hsd_a", "حسين داي", "HSD", 36.7456, 3.0886, 3, "رصيف 2"),
                Station("st_hrh_a", "الحراش", "HRH", 36.7214, 3.1328, 4, "رصيف 1"),
                Station("st_ann_a", "عين النعجة", "ANN", 36.7165, 3.0784, 5, "رصيف 1"),
                Station("st_gqc_a", "جسر قسنطينة", "GQC", 36.7052, 3.0851, 6, "رصيف 2"),
                Station("st_bal_a", "بابا علي", "BAL", 36.6738, 3.0381, 7, "رصيف 1"),
                Station("st_brt_a", "بئر توتة", "BRT", 36.6456, 3.0012, 8, "رصيف 2"),
                Station("st_bfr_a", "بوفاريك", "BFR", 36.5982, 2.9126, 9, "رصيف 1"),
                Station("st_bmr_a", "بني مراد", "BMR", 36.5204, 2.8598, 10, "رصيف 2"),
                Station("st_bld_a", "البليدة", "BLD", 36.4789, 2.8285, 11, "رصيف 1"),
                Station("st_chf_a", "الشفة", "CHF", 36.4623, 2.7381, 12, "رصيف 2"),
                Station("st_moz_a", "موزاية", "MOZ", 36.4645, 2.6841, 13, "رصيف 1"),
                Station("st_afr_a", "العفرون", "AFR", 36.4719, 2.6245, 14, "رصيف 1")
            )
        ),
        SuburbLine(
            id = "airport_algiers",
            name = "خط مطار الجزائر هواري بومدين",
            description = "الخط السريع للمطار الدولي: آغا، الحراش، باب الزوار، محطة مطار الجزائر (4 محطات)",
            inboundTerminus = "الجزائر (آغا)",
            outboundTerminus = "المطار الدولي",
            stations = listOf(
                Station("st_agh_ap", "آغا", "AGH", 36.7647, 3.0569, 1, "رصيف 5"),
                Station("st_hrh_ap", "الحراش", "HRH", 36.7214, 3.1328, 2, "رصيف 3"),
                Station("st_bbz_ap", "باب الزوار", "BBZ", 36.7160, 3.1850, 3, "رصيف 2"),
                Station("st_air_ap", "مطار هواري بومدين الدولي ✈️", "AIR", 36.6975, 3.2185, 4, "رصيف 1")
            )
        ),
        SuburbLine(
            id = "thenia_tizi",
            name = "خط الثنية - تيزي وزو - واد عيسي",
            description = "خط ولايتي بومرداس وتيزي وزو: الثنية، يسر، برج منايل، دراع بن خدة، تيزي وزو، وادي عيسي (10 محطات)",
            inboundTerminus = "الثنية",
            outboundTerminus = "وادي عيسي",
            stations = listOf(
                Station("st_thn_t", "الثنية", "THN", 36.7261, 3.5564, 1, "رصيف 2"),
                Station("st_sms_t", "سي مصطفى", "SMS", 36.7210, 3.6120, 2, "رصيف 1"),
                Station("st_iss_t", "يسر", "ISS", 36.7205, 3.6680, 3, "رصيف 2"),
                Station("st_brm_t", "برج منايل", "BRM", 36.7420, 3.7250, 4, "رصيف 1"),
                Station("st_nac_t", "الناصرية", "NAC", 36.7490, 3.8290, 5, "رصيف 2"),
                Station("st_tdm_t", "تادمايت", "TDM", 36.7460, 3.9010, 6, "رصيف 1"),
                Station("st_dbk_t", "ذراع بن خدة", "DBK", 36.7320, 4.0080, 7, "رصيف 2"),
                Station("st_bkh_t", "بوخالفة", "BKH", 36.7280, 4.0290, 8, "رصيف 1"),
                Station("st_tzo_t", "تيزي وزو (المحطة الرئيسية)", "TZO", 36.7120, 4.0480, 9, "رصيف 1"),
                Station("st_oai_t", "وادي عيسي (القطب الجامعي)", "OAI", 36.6980, 4.0950, 10, "رصيف 2")
            )
        )
    )

    val initialLineAlerts = listOf(
        LineAlert(
            id = "alt_1",
            lineId = "thnia_algiers",
            lineName = "خط الثنية - الجزائر",
            title = "حركة قطارات عادية ومنتظمة 🟢",
            description = "كافة القطارات الكهربائية والحرارية تعمل بانسيابية تامة عبر جميع المحطات.",
            severity = AlertSeverity.NORMAL,
            timeAgo = "منذ 10 دقائق",
            weatherTemperature = "25°C",
            weatherCondition = "مشمس ولطيف"
        ),
        LineAlert(
            id = "alt_2",
            lineId = "algiers_affroun",
            lineName = "خط الجزائر - البليدة - العفرون",
            title = "أشغال صيانة وإبطاء سرعة مؤقت ⚠️",
            description = "أشغال تجديد قضبان السكة بين محطتي بوفاريك والبليدة مع تأخير متوقع بين 5 إلى 10 دقائق.",
            severity = AlertSeverity.WARNING,
            timeAgo = "منذ 25 دقيقة",
            weatherTemperature = "27°C",
            weatherCondition = "غيوم خفيفة"
        ),
        LineAlert(
            id = "alt_3",
            lineId = "airport_algiers",
            lineName = "خط مطار هواري بومدين",
            title = "تواتر سريع كل 30 دقيقة ✈️",
            description = "القطار المكوكي للمطار يعمل بدقة متناهية مع مكيفات الهواء وخدمة نقل الأمتعة.",
            severity = AlertSeverity.NORMAL,
            timeAgo = "منذ 5 دقائق",
            weatherTemperature = "24°C",
            weatherCondition = "جو مثالي"
        ),
        LineAlert(
            id = "alt_4",
            lineId = "thenia_tizi",
            lineName = "خط الثنية - تيزي وزو",
            title = "انسيابية كاملة عبر الأنفاق الجبلية ℹ️",
            description = "نظام Dead-Reckoning مفعل تلقائياً لتعويض تغطية GPS أثناء عبور أنفاق ذراع بن خدة.",
            severity = AlertSeverity.INFO,
            timeAgo = "منذ 40 دقيقة",
            weatherTemperature = "22°C",
            weatherCondition = "نسيم منعش"
        )
    )

    fun findStationById(stationId: String): Station? {
        suburbLines.forEach { line ->
            line.stations.find { it.id == stationId }?.let { return it }
        }
        return null
    }

    val totalStationsCount: Int
        get() = suburbLines.sumOf { it.stations.size }

    val stationInterchanges: Map<String, StationInterchange> = mapOf(
        "ALG" to StationInterchange(
            stationCode = "ALG",
            stationName = "الجزائر (المحطة المركزية)",
            mainHubTitle = "القطب المركزي للنقل بالعاصمة • تافورة وساحة الشهداء",
            connections = listOf(
                TransitConnection(TransitType.METRO, "محطة مترو ساحة الشهداء / علي بومنجل", "الخط 1 (Place des Martyrs ⇄ El Harrach / Ain Naadja)", 350, 4, "تواتر كل 3.5 إلى 5 دقائق"),
                TransitConnection(TransitType.ETUSA_BUS, "محطة حافلات تافورة المركزية (Tafourah)", "خطوط إيتوزا 31, 32, 33, 99 (نحو الأبيار، بن عكنون، القبة، باب الواد)", 250, 3, "حافلات متوفرة كل 10 دقائق"),
                TransitConnection(TransitType.TAXI_STATION, "موقف سيارات الأجرة الحضرية (Tafourah)", "طاكسي فردي وجماعي لجميع بلديات العاصمة ورياض الفتح", 180, 2, "متوفر على مدار 24 ساعة"),
                TransitConnection(TransitType.CABLE_CAR, "تيليفيريك قصر الثقافة / المدنية", "محطة التيليفيريك الرابطة بين الواجهة البحرية والمدنية", 800, 10, "يعمل حتى 19:00 مساءً")
            ),
            walkingTip = "اخرج من الباب الشمالي مباشرة نحو شارع حسيبة بن بوعلي وساحة تافورة (دقيقتين مشياً).",
            landmark = "البريد المركزي، ميناء الجزائر، ساحة الشهداء"
        ),
        "AGH" to StationInterchange(
            stationCode = "AGH",
            stationName = "آغا (Gare de l'Agha)",
            mainHubTitle = "القطب المحوري لقلب العاصمة وشارع ديدوش مراد",
            connections = listOf(
                TransitConnection(TransitType.METRO, "محطة مترو أول ماي (1er Mai) / خليفة بوخالفة", "الخط 1 للمترو", 400, 5, "تواتر كل 4 دقائق"),
                TransitConnection(TransitType.ETUSA_BUS, "محطة حافلات ساحة أول ماي (Place du 1er Mai)", "أكبر محطة حافلات إيتوزا: خطوط نحو بئر مراد رايس، دالي براهيم، شوفالي، بئر خادم", 350, 4, "خطوط مكثفة كل 5-8 دقائق"),
                TransitConnection(TransitType.TAXI_STATION, "محطة سيارات الأجرة (ديدوش مراد / أول ماي)", "سيارات أجرة صفراء وسيارات نقل جماعي نحو مختلف الوجهات", 200, 3, "متوفرة باستمرار"),
                TransitConnection(TransitType.AIRPORT_SHUTTLE, "حافلة المطار السريعة (Aérobus 1er Mai)", "حافلات إيتوزا المباشرة نحو المطار الدولي", 350, 4, "انطلاق كل 30 دقيقة")
            ),
            walkingTip = "استخدم الممشى العلوي أو النفق الأرضي للوصول مباشرة إلى ساحة أول ماي ومستشفى مصطفى باشا.",
            landmark = "شارع ديدوش مراد، مستشفى مصطفى باشا، ساحة أول ماي"
        ),
        "HRH" to StationInterchange(
            stationCode = "HRH",
            stationName = "الحراش (Gare d'El Harrach)",
            mainHubTitle = "أكبر قطب تبادلي متعدد الوسائط بالجزائر العاصمة 🚆🚇🚌🚕",
            connections = listOf(
                TransitConnection(TransitType.METRO, "محطة مترو الحراش محطة (El Harrach Gare)", "الخط 1: ربط مباشر إلى وسط العاصمة، عين النعجة، وباب الزوار قريباً", 50, 1, "ملاصقة لرصيف القطار مباشرة!"),
                TransitConnection(TransitType.ETUSA_BUS, "محطة حافلات الحراش للنقل الحضري", "خطوط إيتوزا والنقل الخاص نحو براقي، الكاليتوس، الأربعاء، وباب الزوار", 150, 2, "تواتر مرتفع جداً"),
                TransitConnection(TransitType.TAXI_STATION, "محطة سيارات الأجرة الجماعية الحراش", "طاكسيات جماعية نحو الكاليتوس، سيدي موسى، بومعطي، وبراقي", 200, 3, "خدمة مستمرة طوال اليوم"),
                TransitConnection(TransitType.AIRPORT_SHUTTLE, "ربط سريع إلى مطار هواري بومدين", "القطار المكوكي أو سيارات الأجرة المباشرة (10 دقائق)", 0, 0, "قطارات كل 30 دقيقة")
            ),
            walkingTip = "محطة المترو متصلة مباشرة بمحطة القطار من خلال المصاعد والسلالم الكهربائية الداخلية دون الحاجة لقطع الطريق.",
            landmark = "سوق بومعطي، جامعة بلكين، وادي الحراش"
        ),
        "CRB" to StationInterchange(
            stationCode = "CRB",
            stationName = "الخروبة (Caroubier)",
            mainHubTitle = "المحطة البرية المركزية للنقل بين الولايات والترامواي",
            connections = listOf(
                TransitConnection(TransitType.TRAMWAY, "محطة ترامواي الخروبة (Station Tramway Caroubier)", "خط الترامواي الرابط بين رويسو وبرج الكيفان ودرقانة", 120, 2, "تواتر كل 6 دقائق"),
                TransitConnection(TransitType.TAXI_STATION, "المحطة البرية الكبرى لنقل المسافرين (خروبة)", "حافلات وسيارات أجرة بين الولايات لجميع أنحاء الوطن (48 ولاية)", 200, 3, "انطلاق رحلات على مدار 24 ساعة"),
                TransitConnection(TransitType.ETUSA_BUS, "محطة حافلات إيتوزا خروبة", "حافلات النقل الحضري نحو بيلكور، ساحة الشهداء، وحسين داي", 180, 2, "تواتر منتظم")
            ),
            walkingTip = "ممشى الراجلين المحمي يقودك مباشرة إلى مدخل المحطة البرية لنقل المسافرين ورصيف الترامواي.",
            landmark = "المحطة البرية خروبة، جامعة الحقوق الخروبة، الواجهة البحرية"
        ),
        "HSD" to StationInterchange(
            stationCode = "HSD",
            stationName = "حسين داي (Hussein Dey)",
            mainHubTitle = "قطب الربط مع الترامواي والمترو في رويسو (Les Fusillés)",
            connections = listOf(
                TransitConnection(TransitType.TRAMWAY, "محطة ترامواي طرابلس / رويسو", "خط ترامواي الجزائر (رويسو ⇄ درقانة)", 250, 3, "تواتر كل 5 دقائق"),
                TransitConnection(TransitType.METRO, "محطة مترو العناصر / رويسو (Les Fusillés)", "الخط 1 للمترو والمصعد الهوائي لرياض الفتح", 650, 8, "ربط سريع بالمترو"),
                TransitConnection(TransitType.ETUSA_BUS, "محطة حافلات حسين داي وشارع طرابلس", "حافلات إيتوزا نحو القبة، كوكليكو، رويسو وباش جراح", 150, 2, "حافلات متوفرة بانتظام"),
                TransitConnection(TransitType.TAXI_STATION, "موقف طاكسي حسين داي / شارع طرابلس", "سيارات أجرة حضرية نحو القبة وجسر قسنطينة", 120, 2, "متوفرة نهاراً وليلاً")
            ),
            walkingTip = "اعبر نحو شارع طرابلس الرئيسي للوصول إلى رصيف الترامواي ومواقف الحافلات في دقيقتين.",
            landmark = "شارع طرابلس، مستشفى بارني، دار الثقافة حسين داي"
        ),
        "BBZ" to StationInterchange(
            stationCode = "BBZ",
            stationName = "باب الزوار (Bab Ezzouar)",
            mainHubTitle = "القطب الجامعي والتجاري لباب الزوار والمنطقة الصناعية",
            connections = listOf(
                TransitConnection(TransitType.TRAMWAY, "محطة ترامواي جامعة باب الزوار (USTHB)", "خط الترامواي نحو الحراش، برج الكيفان والمطار قريباً", 450, 6, "تواتر كل 6 دقائق"),
                TransitConnection(TransitType.ETUSA_BUS, "حافلات إيتوزا والنقل الجامعي", "خطوط إيتوزا نحو الدار البيضاء، سوريكال، والكاليتوس", 200, 3, "خدمة منتظمة للطلبة والركاب"),
                TransitConnection(TransitType.TAXI_STATION, "محطة سيارات الأجرة باب الزوار / حي إسماعيل يفصح", "طاكسيات جماعية نحو الدار البيضاء، برج البحري، وعين طاية", 250, 3, "خدمة مستمرة"),
                TransitConnection(TransitType.AIRPORT_SHUTTLE, "مكوك المطار والمنطقة التجارية (Centre Commercial)", "طاكسي وحافلات مكوكية نحو المركز التجاري والمطار", 500, 6, "5 دقائق بالسيارة")
            ),
            walkingTip = "مخرج المحطة يفتح مباشرة على جسر المشاة نحو جامعة هواري بومدين للعلوم والتكنولوجيا (USTHB) وحي 5 جويلية.",
            landmark = "جامعة USTHB، المركز التجاري باب الزوار، فندق إيبيس"
        ),
        "AIR" to StationInterchange(
            stationCode = "AIR",
            stationName = "مطار هواري بومدين الدولي ✈️",
            mainHubTitle = "المحطة الجوية والمطار الدولي هواري بومدين",
            connections = listOf(
                TransitConnection(TransitType.AIRPORT_SHUTTLE, "المكوك الداخلي للمطار (Navette Terminaux)", "مكوك مجاني بين المحطة الدولية غرب، المحطة 1، والمحطة 2 الداخلية", 50, 1, "ينطلق كل 10 دقائق مجاناً"),
                TransitConnection(TransitType.ETUSA_BUS, "حافلات إيتوزا Aérobus", "حافلات مكيفة تنطلق نحو ساحة أول ماي ومحطة 8 ماي 1945", 100, 1, "انطلاق كل 30 دقيقة"),
                TransitConnection(TransitType.TAXI_STATION, "محطة سيارات أجرة المطار المعتمدة (Taxi Aéroport)", "طاكسيات رسمية بعداد لنقل المسافرين لكافة الولايات والعاصمة", 80, 1, "خدمة 24/24 ساعة")
            ),
            walkingTip = "المحطة تقع تحت الأرض مع ممرات كهربائية مكيفة تؤدي مباشرة إلى بهو تسجيل الأمتعة بالمحطة الغربية الجديدة (Terminal Ouest).",
            landmark = "المحطة الغربية الجديدة، فندق حياة ريجنسي المطار، صالات الرحلات الدولية"
        ),
        "ANN" to StationInterchange(
            stationCode = "ANN",
            stationName = "عين النعجة (Ain Naadja)",
            mainHubTitle = "قطب عين النعجة للربط مع المترو والضاحية الجنوبية",
            connections = listOf(
                TransitConnection(TransitType.METRO, "محطة مترو عين النعجة (Station Métro Ain Naadja)", "الخط 1 للمترو مباشرة نحو تافورة والحراش", 300, 4, "تواتر كل 4 دقائق"),
                TransitConnection(TransitType.ETUSA_BUS, "محطة حافلات عين النعجة وجسر قسنطينة", "خطوط نحو حي البدر، القبة، وبئر خادم", 200, 3, "حافلات متوفرة"),
                TransitConnection(TransitType.TAXI_STATION, "موقف طاكسيات عين النعجة", "سيارات أجرة جماعية وفردية", 150, 2, "متوفرة باستمرار")
            ),
            walkingTip = "ممشى معبد ومضاء يربط محطة القطار بمحطة المترو والشارع التجاري.",
            landmark = "سوق عين النعجة، المستشفى العسكري عين النعجة"
        ),
        "BMR" to StationInterchange(
            stationCode = "BMR",
            stationName = "بومرداس (Boumerdès)",
            mainHubTitle = "القطب المركزي لولاية بومرداس والجامعة",
            connections = listOf(
                TransitConnection(TransitType.ETUSA_BUS, "محطة الحافلات الحضرية والجامعية بومرداس", "حافلات النقل الحضري نحو الكرمة، قورصو، تيجلابين والقطب الجامعي", 150, 2, "حافلات متوفرة كل 10 دقائق"),
                TransitConnection(TransitType.TAXI_STATION, "محطة سيارات الأجرة ما بين البلديات", "طاكسيات جماعية نحو زموري، دلس، الثنية، وبودواو", 120, 2, "خدمة ممتازة"),
                TransitConnection(TransitType.ETUSA_BUS, "حافلات الواجهة البحرية والمدينة", "نقل خفيف نحو الكورنيش وشاطئ بومرداس", 200, 3, "كل 15 دقيقة")
            ),
            walkingTip = "مخرج المحطة يقع بقلب وسط المدينة، على بعد 5 دقائق سيراً من كلية العلوم والواجهة البحرية.",
            landmark = "جامعة محمد بوقرة، الواجهة البحرية الكورنيش، مقر الولاية"
        ),
        "BLD" to StationInterchange(
            stationCode = "BLD",
            stationName = "البليدة (Blida)",
            mainHubTitle = "المحطة المركزية لمدينة الورود والربط الجبلي بالشريعة",
            connections = listOf(
                TransitConnection(TransitType.ETUSA_BUS, "محطة حافلات باب الرحبة وباب السبت", "حافلات النقل الحضري لجميع أحياء البليدة، بني مراد وأولاد يعيش", 250, 3, "حافلات مكثفة"),
                TransitConnection(TransitType.TAXI_STATION, "محطة سيارات الأجرة المركزية البليدة", "طاكسيات نحو بوفاريك، الشفة، العفرون، والمدية", 150, 2, "متوفرة 24 ساعة"),
                TransitConnection(TransitType.CABLE_CAR, "تيليفيريك البليدة - الشريعة (Télécabine Chréa)", "المصعد الهوائي السياحي للصعود إلى قمة جبال الشريعة وحظيرة الثلوج", 900, 12, "يعمل في عطلات نهاية الأسبوع والأيام المشمسة")
            ),
            walkingTip = "مخرج المحطة الرئيسي يقودك مباشرة إلى ساحة الشهداء وباب السبت في وسط مدينة البليدة.",
            landmark = "ساحة باب السبت، حديقة باتريس لومومبا، جبال الشريعة"
        ),
        "TZO" to StationInterchange(
            stationCode = "TZO",
            stationName = "تيزي وزو (Tizi Ouzou)",
            mainHubTitle = "القطب المركزي لولاية تيزي وزو وتيليفيريك سيدي بالوة",
            connections = listOf(
                TransitConnection(TransitType.CABLE_CAR, "تيليفيريك تيزي وزو (Télécabine Tizi Ouzou)", "المصعد الهوائي الرابط بين المحطة المتعددة الأنماط، مستشفى بلoua، ورجاوة", 100, 1, "انطلاق عربات معلقة كل دقيقة!"),
                TransitConnection(TransitType.ETUSA_BUS, "المحطة الحضرية المتعددة الأنماط (Kaf Naadja)", "حافلات النقل الحضري نحو وسط المدينة، حسناوة والقطب الجامعي تامدة", 120, 2, "متوفرة بانتظام"),
                TransitConnection(TransitType.TAXI_STATION, "محطة سيارات الأجرة ما بين الدوائر والقرى", "طاكسيات نحو عزازقة، تيقزيرت، عين الحمام وذراع الميزان", 150, 2, "خدمة متواصلة")
            ),
            walkingTip = "المحطة مدمجة ضمن القطب متعدد الأنماط كاف النعجة مع التيليفيريك والحافلات في مجمع واحد متكامل.",
            landmark = "تيليفيريك سيدي بالوة، ملعب 1 نوفمبر، محطة كاف النعجة"
        ),
        "ZRD" to StationInterchange(
            stationCode = "ZRD",
            stationName = "زرالدة (Zéralda)",
            mainHubTitle = "القطب الساحلي للضاحية الغربية والمدينة الجديدة سيدي عبد الله",
            connections = listOf(
                TransitConnection(TransitType.ETUSA_BUS, "محطة حافلات إيتوزا والنقل الحضري زرالدة", "حافلات نحو سطاوالي، الشراقة، تيبازة، وبوسماعيل", 200, 3, "حافلات متوفرة"),
                TransitConnection(TransitType.TAXI_STATION, "محطة سيارات الأجرة زرالدة الساحل", "طاكسيات جماعية نحو سطاوالي، عين البنيان، والمعالمة", 150, 2, "خدمة مستمرة")
            ),
            walkingTip = "اخرج نحو الشارع الرئيسي لتجد مواقف الحافلات وسيارات الأجرة المتجهة نحو شواطئ زرالدة وسطاوالي.",
            landmark = "شاطئ الرمال الذهبية زرالدة، غابة زرالدة، المستشفى المتخصص"
        ),
        "BRT" to StationInterchange(
            stationCode = "BRT",
            stationName = "بئر توتة (Birtouta)",
            mainHubTitle = "محطة التحويل والتقاطع بين خطوط البليدة وزرالدة والجزائر",
            connections = listOf(
                TransitConnection(TransitType.ETUSA_BUS, "حافلات النقل الحضري بئر توتة", "حافلات نحو تسالة المرجة، الدويرة، وخرايسية", 150, 2, "تواتر كل 15 دقيقة"),
                TransitConnection(TransitType.TAXI_STATION, "موقف سيارات الأجرة بئر توتة المركز", "طاكسيات نحو بوفاريك، الدويرة، وأولاد فايت", 120, 2, "متوفرة نهاراً")
            ),
            walkingTip = "تعد المحطة مركز تقاطع وتغيير القطارات، حيث تتوفر أرصفة مريحة للانتقال بين قطارات العفرون وزرالدة والعاصمة.",
            landmark = "المدينة الجديدة بئر توتة، تقاطع الطريق السريع شرق-غرب"
        )
    )

    fun getInterchangeForStation(stationCode: String): StationInterchange? {
        val cleanCode = stationCode.replace("_z", "").replace("_a", "").replace("_ap", "").replace("_t", "")
        return stationInterchanges[cleanCode] ?: stationInterchanges[stationCode]
    }

    fun findNearestStations(userLat: Double, userLon: Double, limit: Int = 4): List<NearbyStationInfo> {
        val allStationEntries = mutableListOf<NearbyStationInfo>()
        val seenStationCodes = mutableSetOf<String>()

        for (line in suburbLines) {
            for (st in line.stations) {
                val baseCode = st.code.replace("_z", "").replace("_a", "").replace("_ap", "").replace("_t", "")
                if (!seenStationCodes.contains(baseCode)) {
                    seenStationCodes.add(baseCode)
                    val distKm = GeoUtils.calculateDistanceKm(userLat, userLon, st.latitude, st.longitude)
                    val walkingMinutes = (distKm / 0.08).toInt().coerceAtLeast(1) // ~4.8 km/h walking speed
                    val drivingMinutes = (distKm / 0.6).toInt().coerceAtLeast(1)  // ~36 km/h driving speed
                    val connections = getInterchangeForStation(st.code)?.connections?.size ?: 0

                    allStationEntries.add(
                        NearbyStationInfo(
                            station = st,
                            suburbLine = line,
                            distanceKm = distKm,
                            walkingMinutes = walkingMinutes,
                            drivingMinutes = drivingMinutes,
                            connectionsCount = connections
                        )
                    )
                }
            }
        }

        return allStationEntries.sortedBy { it.distanceKm }.take(limit)
    }
}

