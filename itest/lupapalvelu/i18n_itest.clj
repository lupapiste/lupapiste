(ns lupapalvelu.i18n-itest
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [sade.files :as files]
            [lupapiste-commons.i18n.resources :as commons-resources]
            [lupapalvelu.i18n :refer :all]))

(defn create-translation-file! [name translations]
  (let [txt-file (files/temp-file name ".txt" (io/file "."))]
    (spit txt-file
          (apply str
                 (for [[k lang v] translations]
                   (#'commons-resources/txt-line k lang v))))
    txt-file))

(defn create-translation-excel! [name loc-map]
  (let [excel-file (files/temp-file name ".xlsx" (io/file "."))]
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
   :translations {'arvo {:fi "Arvo" :sv "V\u00e4rde"}}})

(fact "Excel translation files properly update the source txt files"
      (let [txt-file1 (create-translation-file! "unordered"
                                                unordered)
            txt-file2 (create-translation-file! "moreTranslations"
                                                more-translations)
            excel-english (create-translation-excel! "english"
                                                     english-translations)
            excel-swedish (create-translation-excel! "swedish"
                                                     swedish-translations)]
        (merge-translations-from-excels-into-source-files (.getAbsolutePath (io/file "."))
                                                          [(.getAbsolutePath excel-english)
                                                           (.getAbsolutePath excel-swedish)])
        (let [loc-map-from-updated-files (#'lupapalvelu.i18n/txt-files->map [(io/file (.getAbsolutePath txt-file1))
                                                                             (io/file (.getAbsolutePath txt-file2))])]
          (-> loc-map-from-updated-files :translations (get 'avain) :en) => "Key"
          (-> loc-map-from-updated-files :translations (get 'vauhti) :en) => "speed"
          (-> loc-map-from-updated-files :translations (get 'arvo) :sv) => "V\u00e4rde")
        (.delete txt-file1)
        (.delete txt-file2)
        (.delete excel-english)
        (.delete excel-swedish)))
