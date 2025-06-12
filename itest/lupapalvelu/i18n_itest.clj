(ns lupapalvelu.i18n-itest
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [lupapalvelu.i18n :refer :all]
            [lupapiste-commons.i18n.resources :as commons-resources]
            [lupapiste-commons.i18n.txt-resources :as commons-txt-resources]
            [sade.files :as files])
  (:import [java.io File]
           [java.nio.file Path]))

(set! *warn-on-reflection* true)

(defn create-translation-file!
  ^File [temp-dir name translations]
  (let [txt-file (files/temp-file name ".txt" temp-dir)]
    (spit txt-file
          (apply str
                 (for [[k lang v] translations]
                   (#'commons-txt-resources/txt-line k lang v))))
    txt-file))

(defn create-translation-excel!
  ^File [temp-dir name loc-map]
  (let [excel-file (files/temp-file name ".xlsx" temp-dir)]
    (commons-resources/write-excel loc-map excel-file)
    excel-file))

(def unordered
  [["avain" "fi" "Avain"]
   ["arvo" "en" "Value"]
   ["arvo" "fi" "Arvo"]
   ["avain" "sv" "Nyckel"]])

(def more-translations
  [["vauhti" "fi" "vauhti"]
   ["vauhti" "sv" "fart"]])

(def english-translations
  {:languages [:fi :en]
   :translations {'avain  {:fi "Avain" :en "Key"}
                  'vauhti {:fi "vauhti" :en "speed"}}})

(def swedish-translations
  {:languages [:fi :sv]
   :translations {'arvo {:fi "Arvo" :sv "Värde"}}})

(def slash-keys-unordered
  [["ymparistoluvan-selventaminen.kuvaus" "fi" "Selvennys"]
   ["paloluokka.P1/P2" "fi" "P1/P2"]
   ["rakennelman-kayttotarkoitus.Katos/pergola" "fi" "Katos/pergola"]
   ["uusiRakennus.luokitus.energiatehokkuusluvunYksikko.kWh/brm2/vuosi" "fi" "kWh/brm²/vuosi"]])

(def slash-keys-english-translations
  {:languages    [:fi :en]
   :translations {'ymparistoluvan-selventaminen.kuvaus
                  {:fi "Selvennys" :en "Clarification"}
                  (symbol "paloluokka.P1/P2")
                  {:fi "P1/P2" :en "P1/P2"}
                  (symbol "rakennelman-kayttotarkoitus.Katos/pergola")
                  {:fi "Katos/pergola" :en "Canopy/pergola"}
                  (symbol "uusiRakennus.luokitus.energiatehokkuusluvunYksikko.kWh/brm2/vuosi")
                  {:fi "kWh/brm²/vuosi" :en "kWh/gross m²/year"}}})

(deftest excel-translation-files-properly-update-source-txt-files
  (fs/with-temp-dir [temp-dir {:prefix "translation-test"}]
    (let [temp-dir      (.toFile ^Path temp-dir)
          txt-file1     (create-translation-file! temp-dir
                                                  "unordered"
                                                  unordered)
          txt-file2     (create-translation-file! temp-dir
                                                  "moreTranslations"
                                                  more-translations)
          excel-english (create-translation-excel! temp-dir
                                                   "english"
                                                   english-translations)
          excel-swedish (create-translation-excel! temp-dir
                                                   "swedish"
                                                   swedish-translations)]
      (merge-translations-from-excels-into-source-files (.getAbsolutePath temp-dir)
                                                        [(.getAbsolutePath excel-english)
                                                         (.getAbsolutePath excel-swedish)])
      (let [loc-map-from-updated-files (#'lupapalvelu.i18n/txt-files->map [(io/file (.getAbsolutePath txt-file1))
                                                                           (io/file (.getAbsolutePath txt-file2))])]
        (is (= "Key" (-> loc-map-from-updated-files :translations (get 'avain) :en)))
        (is (= "speed" (-> loc-map-from-updated-files :translations (get 'vauhti) :en)))
        (is (= "Värde" (-> loc-map-from-updated-files :translations (get 'arvo) :sv)))))))

(deftest excel-merge-keeps-slash-containing-keys-in-lexical-string-order
  (fs/with-temp-dir [temp-dir {:prefix "translation-sort-test"}]
    (let [temp-dir   (.toFile ^Path temp-dir)
          txt-file   (create-translation-file! temp-dir
                                               "schemas"
                                               (shuffle slash-keys-unordered))
          excel-file (create-translation-excel! temp-dir
                                                "slash-english"
                                                slash-keys-english-translations)]
      (merge-translations-from-excels-into-source-files (.getAbsolutePath temp-dir)
                                                        [(.getAbsolutePath excel-file)])
      (let [content           (slurp txt-file)
            idx-paloluokka    (.indexOf content "\"paloluokka.P1/P2\" \"fi\" \"P1/P2\"")
            idx-rakennelman   (.indexOf content "\"rakennelman-kayttotarkoitus.Katos/pergola\" \"fi\" \"Katos/pergola\"")
            idx-uusi-rakennus (.indexOf content "\"uusiRakennus.luokitus.energiatehokkuusluvunYksikko.kWh/brm2/vuosi\" \"fi\" \"kWh/brm²/vuosi\"")
            idx-ymparistolupa (.indexOf content "\"ymparistoluvan-selventaminen.kuvaus\" \"fi\" \"Selvennys\"")]
        (is (not= -1 idx-paloluokka))
        (is (not= -1 idx-rakennelman))
        (is (not= -1 idx-uusi-rakennus))
        (is (not= -1 idx-ymparistolupa))
        (is (< idx-paloluokka idx-rakennelman idx-uusi-rakennus idx-ymparistolupa))))))
