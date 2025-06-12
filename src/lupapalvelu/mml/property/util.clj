(ns lupapalvelu.mml.property.util
  (:require [lupapalvelu.json :as json]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc])
  (:import [org.geotools.geojson.geom GeometryJSON]
           [org.geotools.geometry.jts WKTWriter2]))

(set! *warn-on-reflection* true)

(def registration-date-format
  "The same as `:basic-date` in `sade.date`, but defined here separately in order to be used
  in schemas as well."
 "yyyyMMdd")

(def rekisteriyksikkolaji
  "See http://xml.nls.fi/ktjkii/tietopalvelu/tietopalvelun_skeema/202302/KR-koodistomaarays-20230213.pdf"
  {"0"  {:fi "(Tuntematon)" :sv "(Okänd)"}
   "1"  {:fi "Tila" :sv "Lägenhet"}
   "3"  {:fi "Valtion metsämaa" :sv "Statens skogsmark"}
   "4"  {:fi "Lunastusyksikkö" :sv "Inlösningsenhet"}
   "5"  {:fi "Kruununkalastus" :sv "Kronofiske"}
   "6"  {:fi "Yleiseen tarpeeseen erotettu alue" :sv "Område avskilt för allmänt behov"}
   "7"  {:fi "Erillinen vesijättö" :sv "Fristående tillandning"}
   "8"  {:fi "Yleinen vesialue" :sv "Allmänt vattenområde"}
   "9"  {:fi "Yhteinen alue" :sv "Samfällt område"}
   "10" {:fi "Yhteismetsä" :sv "Samfälld skog"}
   "11" {:fi "Tie- tai liitännäisalue" :sv "Väg- eller biområde"}
   "12" {:fi "Lakkautettu tie- tai liitännäisalue" :sv "Indraget väg- eller biområde"}
   "13" {:fi "Tontti" :sv "Tomt"}
   "14" {:fi "Yleinen alue" :sv "Allmänt område"}
   "15" {:fi "Selvittämätön yhteinen alue" :sv "Outrett samfällt område"}
   "17" {:fi "Yhteinen vesialue" :sv "Samfällt vattenområde"}
   "18" {:fi "Yhteinen maa-alue" :sv "Samfällt jordområde"}
   "19" {:fi "Suojelualuekiinteistö" :sv "Skyddsområdesfastighet"}
   "21" {:fi "Tie- tai liitännäisalue tieoikeudella" :sv "Väg- eller biområde med vägrätt"}
   "22" {:fi "Tie- tai liitännäisalue omistusoikeudella" :sv "Väg- eller biområde med äganderätt"}
   "23" {:fi "Yleisen alueen lisäosa" :sv "Allmänna områdets tilläggsområde"}
   "24" {:fi "Tuntematon kunnan rekisteriyksikkö" :sv "Okänd kommunens registerenhet"}
   "25" {:fi "Yhteinen erityinen etuus" :sv "Gemensam särskild förmån"}
   "99" {:fi "Selvittämätön alue" :sv "Outrett område"}})

(defn geojson->wkt
  [geometry]
  (let [s      (json/encode geometry)
        gjson  (GeometryJSON.)
        writer (WKTWriter2.)]
    (.write writer (.read gjson s))))

(sc/defn coord->str :- sc/Str
  [coordinate :- (sc/cond-pre ssc/NonBlankStr sc/Num)]
  (if (number? coordinate)
    (ss/format-fixed "%.3f" (double coordinate))
    coordinate))
