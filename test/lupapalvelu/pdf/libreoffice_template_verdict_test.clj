(ns lupapalvelu.pdf.libreoffice-template-verdict-test
  (:require [clojure.string :as s]
            [taoensso.timbre :refer [trace debug]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.files :as files]
            [lupapalvelu.test-util :as test-util]
            [lupapalvelu.organization :refer :all]
            [lupapalvelu.i18n :refer [with-lang loc localize] :as i18n]
            [lupapalvelu.pdf.libreoffice-template-verdict :as verdict]
            [lupapalvelu.pdf.libreoffice-template :as template]
            [lupapalvelu.pdf.libreoffice-template-base-test :refer :all]))

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
             (verdict-vaaditutKatselmukset application2 "a1" 0 :fi) => '[["YA paikan tarkastaminen"] ["rakennuksen paikan tarkastaminen"]]))

(facts "Verdict signatures "
       (def verdict-signatures #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-signatures)
       (fact
         (let [verdict (first (filter #(= "a1" (:id %)) (:verdicts application2)))
               paatos (nth (:paatokset verdict) 0)]
           (verdict-signatures verdict paatos) => '[["Tytti M\u00e4ntyoja" "04.02.2016"] ["Matti Mallikas" "23.02.2015"] ["Minna Mallikas" "23.02.2015"]])))

(facts "Verdict vastuuhenkil\u00f6 "
       (def get-vastuuhenkilo #'lupapalvelu.pdf.libreoffice-template-verdict/get-vastuuhenkilo)
       (fact {:midje/description " yritys "}
             (get-vastuuhenkilo (assoc application2 :documents [{:schema-info {:name :tyomaastaVastaava}
                                                                 :data        {:_selected {:value "yritys"}
                                                                               :henkilo   {:henkilotiedot {:etunimi  {:value ""}
                                                                                                           :sukunimi {:value ""}}}
                                                                               :yritys    {:yritysnimi    {:value "Yritys Oy Ab"}
                                                                                           :yhteyshenkilo {:henkilotiedot {:etunimi  {:value "Mikko"}
                                                                                                                           :sukunimi {:value "Mallikas"}}}}}}])) => "Yritys Oy Ab / Mikko Mallikas")
       (fact {:midje/description " henkilo "}
             (get-vastuuhenkilo (assoc application2 :documents [{:schema-info {:name :tyomaastaVastaava}
                                                                 :data        {:henkilo {:henkilotiedot {:etunimi  {:value "Veikko"}
                                                                                                         :sukunimi {:value "Vastaava"}}}
                                                                               :yritys  {:yritysnimi    {:value "Yritys Oy Ab"}
                                                                                         :yhteyshenkilo {:henkilotiedot {:etunimi  {:value "Mikko"}
                                                                                                                         :sukunimi {:value "Mallikas"}}}}}}])) => "Veikko Vastaava"))

(background
  (get-organization "753-R") => {:name {:fi "org-name-fi"}})

(defn get-user-field [fields key]
  (first (filter #(s/ends-with? % (str "text:name=\"" key "\"/>")) fields)))

(facts "YA Verdict publish export "
  (doseq [lang test-util/test-languages]
    (files/with-temp-file tmp-file
      (let [data (assoc application2 :documents [{:schema-info {:name :tyomaastaVastaava}
                                                  :data        {:_selected {:value "yritys"}
                                                                :henkilo   {:henkilotiedot {:etunimi  {:value ""}
                                                                                            :sukunimi {:value ""}}}
                                                                :yritys    {:yritysnimi    {:value "Yritys Oy Ab"}
                                                                            :yhteyshenkilo {:henkilotiedot {:etunimi  {:value "Mikko"}
                                                                                                            :sukunimi {:value "Mallikas"}}}}}}
                                                 {:schema-info {:name :tyoaika}
                                                  :data        {:tyoaika-alkaa-pvm   {:value "01.12.2016"}
                                                                :tyoaika-paattyy-pvm {:value "02.12.2016"}}}

                                                 {:schema-info {:subtype "hakija"}
                                                  :data        {:_selected {:value "yritys"}
                                                                :yritys    {:yhteyshenkilo {:henkilotiedot {:sukunimi {:value "Lohikaarme"}
                                                                                                            :etunimi  {:value "Puff"}}}
                                                                            :yritysnimi    {:value "Vantaan kaupunki / Rakennus Oy"}}
                                                                :henkilo   {:henkilotiedot {:etunimi  {:value "aa"}
                                                                                            :sukunimi {:value "bb"}}}}}
                                                 {:schema-info {:subtype "hakija"}
                                                  :data        {:_selected {:value "henkilo"}
                                                                :henkilo   {:henkilotiedot {:etunimi  {:value "Heikki"}
                                                                                            :sukunimi {:value "Hakija"}}}}}])]
        (verdict/write-verdict-libre-doc data "a1" 0 lang tmp-file)
        (let [res (s/split (slurp tmp-file) #"\r?\n")
              user-fields (filter #(s/includes? % "<text:user-field-decl ") res)]
          (fact {:midje/description (str " verdict title id (" (name lang) ")")} (get-user-field user-fields "LPATITLE_ID") => (build-user-field (localize lang "verdict-attachment-prints-order.order-dialog.lupapisteId") "LPATITLE_ID"))
          (fact {:midje/description (str " verdict id (" (name lang) ")")} (get-user-field user-fields "LPAVALUE_ID") => (build-user-field "LP-000-0000-0000" "LPAVALUE_ID"))
          (fact {:midje/description (str " verdict title kuntalupa (" (name lang) ")")} (get-user-field user-fields "LPATITLE_ID") => (build-user-field (localize lang "verdict-attachment-prints-order.order-dialog.lupapisteId") "LPATITLE_ID"))
          (fact {:midje/description (str " verdict kuntalupa (" (name lang) ")")} (get-user-field user-fields "LPVALUE_KUNTALUPA") => (build-user-field "20160043" "LPVALUE_KUNTALUPA"))
          (fact {:midje/description (str " verdict title municipality (" (name lang) ")")} (get-user-field user-fields "LPTITLE_KUNTALUPA") => (build-user-field (localize lang "linkPermit.dialog.kuntalupatunnus") "LPTITLE_KUNTALUPA"))
          (fact {:midje/description (str " verdict municipality (" (name lang) ")")} (get-user-field user-fields "LPVALUE_KUNTALUPA") => (build-user-field "20160043" "LPVALUE_KUNTALUPA"))
          ;;TODO: test rest of common "LPA" application fields

          (fact {:midje/description (str " verdict title vastuuhenkilo (" (name lang) ")")} (get-user-field user-fields "LPTITLE_VASTUU") => (build-user-field (localize lang "verdict.vastuuhenkilo") "LPTITLE_VASTUU"))
          (fact {:midje/description (str " verdict vastuuhenkilo (" (name lang) ")")} (get-user-field user-fields "LPVALUE_VASTUU") => (build-user-field "Yritys Oy Ab / Mikko Mallikas" "LPVALUE_VASTUU"))
          (fact {:midje/description (str " verdict title alkaa (" (name lang) ")")} (get-user-field user-fields "LPTITLE_LUPA_AIKA") => (build-user-field (i18n/localize lang "tyoaika._group_label") "LPTITLE_LUPA_AIKA"))
          (fact {:midje/description (str " verdict alkaa (" (name lang) ")")} (get-user-field user-fields "LPVALUE_LUPA_AIKA") => (build-user-field "01.12.2016 - 02.12.2016" "LPVALUE_LUPA_AIKA"))
          )))))

(facts "R Verdict publish export "
  (doseq [lang test-util/test-languages]
    (files/with-temp-file tmp-file
      (let [data (assoc application2 :documents [{:schema-info {:name :tyomaastaVastaava}
                                                  :data        {:_selected {:value "yritys"}
                                                                :henkilo   {:henkilotiedot {:etunimi  {:value ""}
                                                                                            :sukunimi {:value ""}}}
                                                                :yritys    {:yritysnimi    {:value "Yritys Oy Ab"}
                                                                            :yhteyshenkilo {:henkilotiedot {:etunimi  {:value "Mikko"}
                                                                                                            :sukunimi {:value "Mallikas"}}}}}}])]
        (verdict/write-verdict-libre-doc data "a1" 0 lang tmp-file)
        (let [res (s/split (slurp tmp-file) #"\r?\n")
              user-fields (filter #(s/includes? % "<text:user-field-decl ") res)]
          (fact {:midje/description (str " verdict title id (" (name lang) ")")} (get-user-field user-fields "LPATITLE_ID") => (build-user-field (localize lang "verdict-attachment-prints-order.order-dialog.lupapisteId") "LPATITLE_ID"))
          (fact {:midje/description (str " verdict id (" (name lang) ")")} (get-user-field user-fields "LPAVALUE_ID") => (build-user-field "LP-000-0000-0000" "LPAVALUE_ID"))
          (fact {:midje/description (str " verdict title kuntalupa (" (name lang) ")")} (get-user-field user-fields "LPATITLE_ID") => (build-user-field (localize lang "verdict-attachment-prints-order.order-dialog.lupapisteId") "LPATITLE_ID"))
          (fact {:midje/description (str " verdict kuntalupa (" (name lang) ")")} (get-user-field user-fields "LPVALUE_KUNTALUPA") => (build-user-field "20160043" "LPVALUE_KUNTALUPA"))
          (fact {:midje/description (str " verdict title municipality (" (name lang) ")")} (get-user-field user-fields "LPTITLE_KUNTALUPA") => (build-user-field (localize lang "linkPermit.dialog.kuntalupatunnus") "LPTITLE_KUNTALUPA"))
          (fact {:midje/description (str " verdict municipality (" (name lang) ")")} (get-user-field user-fields "LPVALUE_KUNTALUPA") => (build-user-field "20160043" "LPVALUE_KUNTALUPA"))
          ;;TODO: test rest of common "LPA" application fields

          (fact {:midje/description (str " verdict title vastuuhenkilo (" (name lang) ")")} (get-user-field user-fields "LPTITLE_VASTUU") => (build-user-field (localize lang "verdict.vastuuhenkilo") "LPTITLE_VASTUU"))
          (fact {:midje/description (str " verdict vastuuhenkilo (" (name lang) ")")} (get-user-field user-fields "LPVALUE_VASTUU") => (build-user-field "Yritys Oy Ab / Mikko Mallikas" "LPVALUE_VASTUU"))
          (fact {:midje/description (str " verdict title alkaa (" (name lang) ")")} (get-user-field user-fields "LPTITLE_LUPA_AIKA") => (build-user-field "" "LPTITLE_LUPA_AIKA"))
          (fact {:midje/description (str " verdict alkaa (" (name lang) ")")} (get-user-field user-fields "LPVALUE_LUPA_AIKA") => (build-user-field "" "LPVALUE_LUPA_AIKA"))
          )))))

(facts "YA contract publish export "
  (doseq [lang test-util/test-languages]
    (files/with-temp-file tmp-file
      (verdict/write-verdict-libre-doc (assoc application2 :verdicts (map #(assoc % :sopimus true) (:verdicts application2))) "a1" 0 lang tmp-file)
      (let [res (s/split (slurp tmp-file) #"\r?\n")
            user-fields (filter #(s/includes? % "<text:user-field-decl ") res)]
        (fact {:midje/description (str " verdict title  date(" (name lang) ")")} (get-user-field user-fields "LPTITLE_CONTRACT_DATE") => (build-user-field (localize lang "verdict.contract.date") "LPTITLE_CONTRACT_DATE"))
        ;;TODO test signatures
        ))))
