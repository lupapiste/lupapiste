(ns lupapalvelu.pdf.libreoffice-template-verdict-test
  (:require
    [clojure.string :as s]
    [taoensso.timbre :refer [trace debug]]
    [midje.sweet :refer :all]
    [midje.util :refer [testable-privates]]
    [lupapalvelu.organization :refer :all]
    [lupapalvelu.i18n :refer [with-lang loc localize] :as i18n]
    [lupapalvelu.pdf.libreoffice-template-verdict :as verdict]
    [lupapalvelu.pdf.libreoffice-template :as template]
    [lupapalvelu.pdf.libreoffice-template-base-test :refer :all])
  (:import (java.io File)))

(def applicant-index #'lupapalvelu.pdf.libreoffice-template/applicant-index)
(fact "Applicant index"
      (applicant-index application2) => '[["Testaaja Testi"]])

(facts "Verdict lupamaaraykset "
       (def verdict-lupamaaraykset #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-lupamaaraykset)
       (fact {:midje/description (str " krysp")}
             (verdict-lupamaaraykset application1 "a1" 0 :fi) => '[["Kerrosala" "100m2"]])
       (fact {:midje/description (str " non-krysp")}
             (verdict-lupamaaraykset application2 "a1" 0 :fi) => '[]))

(facts "Verdict foremen "
       (def verdict-foremen #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-foremen)
       (fact {:midje/description (str " krysp")}
             (verdict-foremen application1 "a1" 0) => '[["Vastaava ty\u00f6njohtaja"] ["Toinen ty\u00f6njohtaja"]])
       (fact {:midje/description (str " non-krysp")}
             (verdict-foremen application2 "a1" 0) => '[["Joku ty\u00f6hohtaja"] ["Joku toinen ty\u00f6hohtaja"]]))

(facts "Verdict vaaditutErityissuunnitelmat "
       (def verdict-vaaditutErityissuunnitelmat #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-vaaditutErityissuunnitelmat)
       (fact {:midje/description (str " krysp")}
             (verdict-vaaditutErityissuunnitelmat application1 "a1" 0) => '[["Joku erityissuunnitelma"] ["Joku toinen erityissuunnitelma"]])
       (fact {:midje/description (str " non-krysp")}
             (verdict-vaaditutErityissuunnitelmat application2 "a1" 0) => '[]))

(facts "Verdict other muutMaaraykset "
       (def verdict-muutMaaraykset #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-muutMaaraykset)
       (fact {:midje/description (str " krysp")}
             (verdict-muutMaaraykset application1 "a1" 0) => '[])
       (fact {:midje/description (str " non-krysp")}
             (verdict-muutMaaraykset application2 "a1" 0) => '[["Jotain pitais tehda (Joku lupam\u00e4\u00e4r\u00e4ys)"]]))

(facts "Verdict vaaditutKatselmukset "
       (def verdict-vaaditutKatselmukset #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-vaaditutKatselmukset)
       (fact {:midje/description (str "verdict-reviews krysp")}
             (verdict-vaaditutKatselmukset application1 "a1" 0 :fi) => '[[" muu katselmus " "* KVV-tarkastus"] [" muu katselmus " " * S\u00e4hk\u00f6tarkastus "] [" muu katselmus " " * Rakennetarkastus "] [" loppukatselmus " ""] [" muu katselmus " " Aloitusilmoitus "]])
       (fact {:midje/description (str "verdict-reviews non-krysp")}
             (verdict-vaaditutKatselmukset application2 "a1" 0 :fi) => '[["Muu katselmus (YA paikan tarkastaminen)"] ["Muu katselmus (rakennuksen paikan tarkastaminen)"]]))

(facts "Verdict signatures "
       (def verdict-signatures #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-signatures)
       (fact
         (let [verdict (first (filter #(= "a1" (:id %)) (:verdicts application2)))
               paatos (nth (:paatokset verdict) 0)]
           (verdict-signatures verdict paatos) => '[["Tytti M\u00e4ntyoja" "04.02.2016"] ["Matti Mallikas" "23.02.2015"] ["Minna Mallikas" "23.02.2015"]])))

(facts "Verdict vastuuhenkilö "
       (def get-vastuuhenkilo #'lupapalvelu.pdf.libreoffice-template-verdict/get-vastuuhenkilo)
       (fact {:midje/description " yritys "}
             (get-vastuuhenkilo (assoc application2 :documents [{:schema-info {:name :tyomaastaVastaava}
                                                                 :data        {:henkilo {:henkilotiedot {:etunimi  {:value ""}
                                                                                                         :sukunimi {:value ""}}}
                                                                               :yritys  {:yritysnimi    {:value "Yritys Oy Ab"}
                                                                                         :yhteyshenkilo {:henkilotiedot {:etunimi  {:value "Mikko"}
                                                                                                                         :sukunimi {:value "Mallikas"}}}}}}])) => "Yritys Oy Ab")
       (fact {:midje/description " henkilo "}
             (get-vastuuhenkilo (assoc application2 :documents [{:schema-info {:name :tyomaastaVastaava}
                                                                 :data        {:henkilo {:henkilotiedot {:etunimi  {:value "Veikko"}
                                                                                                         :sukunimi {:value "Vastaava"}}}
                                                                               :yritys  {:yritysnimi    {:value "Yritys Oy Ab"}
                                                                                         :yhteyshenkilo {:henkilotiedot {:etunimi  {:value "Mikko"}
                                                                                                                         :sukunimi {:value "Mallikas"}}}}}}])) => "Veikko Vastaava"))
(facts "Verdict yhteyshenkilo "
       (def get-yhteyshenkilo #'lupapalvelu.pdf.libreoffice-template-verdict/get-yhteyshenkilo)
       (fact (get-yhteyshenkilo (assoc application2 :documents [{:schema-info {:name :tyomaastaVastaava}
                                                                 :data        {:henkilo {:henkilotiedot {:etunimi  {:value ""}
                                                                                                         :sukunimi {:value ""}}}
                                                                               :yritys  {:yritysnimi    {:value "Yritys Oy Ab"}
                                                                                         :yhteyshenkilo {:henkilotiedot {:etunimi  {:value "Mikko"}
                                                                                                                         :sukunimi {:value "Mallikas"}}}}}}])) => "Mikko Mallikas"))

(background
  (get-organization "753-R") => {:name {:fi "org-name-fi"}})

(defn get-user-field [fields key]
  (first (filter #(s/ends-with? % (str "text:name=\"" key "\"/>")) fields)))

(facts "YA Verdict publish export "
       (doseq [lang i18n/languages]
         (let [tmp-file (File/createTempFile (str "verdict-" (name lang) "-") ".fodt")
               data (assoc application2 :documents [{:schema-info {:name :tyomaastaVastaava}
                                                     :data        {:henkilo {:henkilotiedot {:etunimi  {:value ""}
                                                                                             :sukunimi {:value ""}}}
                                                                   :yritys  {:yritysnimi    {:value "Yritys Oy Ab"}
                                                                             :yhteyshenkilo {:henkilotiedot {:etunimi  {:value "Mikko"}
                                                                                                             :sukunimi {:value "Mallikas"}}}}}}
                                                    {:schema-info {:name :tyoaika}
                                                     :data        {:tyoaika-alkaa-pvm {:value "01.12.2016"}
                                                                   :tyoaika-paattyy-pvm {:value "02.12.2016"}}}])]
           (verdict/write-verdict-libre-doc data "a1" 0 lang tmp-file)
           (let [res (s/split (slurp tmp-file) #"\r?\n")
                 user-fields (filter #(s/includes? % "<text:user-field-decl ") res)]
             #_(.delete tmp-file)
             (fact {:midje/description (str " verdict title id (" (name lang) ")")} (get-user-field user-fields "LPATITLE_ID") => (template/build-user-field (localize lang "verdict-attachment-prints-order.order-dialog.lupapisteId") "LPATITLE_ID"))
             (fact {:midje/description (str " verdict id (" (name lang) ")")} (get-user-field user-fields "LPAVALUE_ID") => (template/build-user-field "LP-000-0000-0000" "LPAVALUE_ID"))
             (fact {:midje/description (str " verdict title municipality (" (name lang) ")")} (get-user-field user-fields "LPATITLE_MUNICIPALITY") => (template/build-user-field (localize lang "application.muncipality") "LPATITLE_MUNICIPALITY"))
             (fact {:midje/description (str " verdict municipality (" (name lang) ")")} (get-user-field user-fields "LPAVALUE_MUNICIPALITY") => (template/build-user-field (localize lang (str "municipality." (:municipality application2))) "LPAVALUE_MUNICIPALITY"))
             ;;TODO: test rest of common "LPA" application fields

             (fact {:midje/description (str " verdict title vastuuhenkilo (" (name lang) ")")} (get-user-field user-fields "LPTITLE_VASTUU") => (template/build-user-field (localize lang "verdict.vastuuhenkilo") "LPTITLE_VASTUU"))
             (fact {:midje/description (str " verdict vastuuhenkilo (" (name lang) ")")} (get-user-field user-fields "LPVALUE_VASTUU") => (template/build-user-field "Yritys Oy Ab" "LPVALUE_VASTUU"))
             (fact {:midje/description (str " verdict title yhteyshenkilo (" (name lang) ")")} (get-user-field user-fields "LPTITLE_YHTEYSHENKILO") => (template/build-user-field (localize lang "verdict.yhteyshenkilo") "LPTITLE_YHTEYSHENKILO"))
             (fact {:midje/description (str " verdict yhteyshenkilo (" (name lang) ")")} (get-user-field user-fields "LPVALUE_YHTEYSHENKILO") => (template/build-user-field "Mikko Mallikas" "LPVALUE_YHTEYSHENKILO"))
             (fact {:midje/description (str " verdict alkaa (" (name lang) ")")} (get-user-field user-fields "LPVALUE_LUPA_AIKA_ALKAA") => (template/build-user-field "01.12.2016" "LPVALUE_LUPA_AIKA_ALKAA"))
             (fact {:midje/description (str " verdict alkaa (" (name lang) ")")} (get-user-field user-fields "LPVALUE_LUPA_AIKA_PAATTYY") => (template/build-user-field "02.12.2016" "LPVALUE_LUPA_AIKA_PAATTYY"))

             ))))

#_(facts "Verdict-contract publish export "
         (doseq [lang i18n/languages]
           (let [tmp-file (File/createTempFile (str "verdict-contract-" (name lang) "-") ".fodt")]
             (verdict/write-verdict-libre-doc (assoc application2 :verdicts (map #(assoc % :sopimus true) (:verdicts application2))) "a1" 0 lang tmp-file)
             (let [res (s/split (slurp tmp-file) #"\r?\n")
                   doc-start-row (start-pos res)]
               #_(.delete tmp-file)
               (fact {:midje/description (str " verdict libre document title (" (name lang) ")")} (nth res doc-start-row) => #(s/includes? % (localize lang "userInfo.company.contract")))
               (fact {:midje/description (str " verdict libre document id ")} (nth res (+ doc-start-row 1)) => #(s/includes? % "LP-000-0000-0000"))
               (fact {:midje/description (str " verdict libre document kuntalupatunnus ")} (nth res (+ doc-start-row 2)) => #(s/includes? % "20160043"))
               (fact {:midje/description (str " verdict libre document sijainti ")} (nth res (+ doc-start-row 4)) => #(s/includes? % "Korpikuusen kannon alla 6"))
               (fact {:midje/description (str " verdict libre document osapuolet 1 ")} (nth res (+ doc-start-row 18)) => #(s/includes? % "org-name-fi / Tytti M\u00e4ntyoja"))
               (fact {:midje/description (str " verdict libre document osapuolet 2 ")} (nth res (+ doc-start-row 26)) => #(s/includes? % "Testaaja Testi"))
               (fact {:midje/description (str " verdict libre document sis\u00e4lt\u00f6 ")} (nth res (+ doc-start-row 34)) => #(s/includes? % "Lorem ipsum dolor sit amet"))
               (fact {:midje/description (str " verdict libre document signature 1 ")} (nth res (+ doc-start-row 69)) => #(s/includes? % "Tytti M\u00e4ntyoja"))
               (fact {:midje/description (str " verdict libre document signature 2 ")} (nth res (+ doc-start-row 82)) => #(s/includes? % "Matti Mallikas"))
               (fact {:midje/description (str " verdict libre document last signature ")} (nth res (+ doc-start-row 95)) => #(s/includes? % "Minna Mallikas"))
               ))))
