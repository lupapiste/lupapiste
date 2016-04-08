(ns lupapalvelu.pdf.libreoffice-template-verdict
  (:require [taoensso.timbre :as log]
            [sade.util :as util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pdf.libreoffice-template :as template]
            [clojure.string :as s]
            [clojure.java.io :as io]))

(defn- verdict-dates [lang dates]
  (cond-> []
          (:anto dates) (conj [(i18n/localize lang "verdict.anto") (or (util/to-local-date (:anto dates)) "-")])
          (:lainvoimainen dates) (conj [(i18n/localize lang "verdict.lainvoimainen") (or (util/to-local-date (:lainvoimainen dates)) "-")])
          (:paatosdokumentinPvm dates) (conj [(i18n/localize lang "verdict.paatosdokumentinPvm") (or (util/to-local-date (:paatosdokumentinPvm dates)) "-")])
          (:paatosPvm dates) (conj [(i18n/localize lang "verdict.paatosPvm") (or (util/to-local-date (:paatosPvm dates)) "-")])
          (:julkipano dates) (conj [(i18n/localize lang "verdict.julkipano") (or (util/to-local-date (:julkipano dates)) "-")])
          (:viimeinenValitus dates) (conj [(i18n/localize lang "verdict.viimeinenValitus") (or (util/to-local-date (:viimeinenValitus dates)) "-")])
          (:aloitettava dates) (conj [(i18n/localize lang "verdict.aloitettava") (or (util/to-local-date (:aloitettava dates)) "-")])
          (:voimassaHetki dates) (conj [(i18n/localize lang "verdict.voimassaHetki") (or (util/to-local-date (:voimassaHetki dates)) "-")])
          (:raukeamis dates) (conj [(i18n/localize lang "verdict.raukeamis") (or (util/to-local-date (:raukeamis dates)) "-")])))

(defn- verdict-lupamaaraykset [lang lupamaaraykset]
  (cond-> []
          (:autopaikkojaEnintaan lupamaaraykset) (conj [(i18n/localize lang "verdict.autopaikkojaEnintaan") (:autopaikkojaEnintaan lupamaaraykset)])
          (:autopaikkojaVahintaan lupamaaraykset) (conj [(i18n/localize lang "verdict.autopaikkojaVahintaan") (:autopaikkojaVahintaan lupamaaraykset)])
          (:autopaikkojaRakennettava lupamaaraykset) (conj [(i18n/localize lang "verdict.autopaikkojaRakennettava") (:autopaikkojaRakennettava lupamaaraykset)])
          (:autopaikkojaRakennettu lupamaaraykset) (conj [(i18n/localize lang "verdict.autopaikkojaRakennettu") (:autopaikkojaRakennettu lupamaaraykset)])
          (:autopaikkojaKiinteistolla lupamaaraykset) (conj [(i18n/localize lang "verdict.autopaikkojaKiinteistolla") (:autopaikkojaKiinteistolla lupamaaraykset)])
          (:autopaikkojaUlkopuolella lupamaaraykset) (conj [(i18n/localize lang "verdict.autopaikkojaUlkopuolella") (:autopaikkojaUlkopuolella lupamaaraykset)])
          (:kerrosala lupamaaraykset) (conj [(i18n/localize lang "verdict.kerrosala") (:kerrosala lupamaaraykset)])
          (:rakennusoikeudellinenKerrosala lupamaaraykset) (conj [(i18n/localize lang "verdict.rakennusoikeudellinenKerrosala") (:rakennusoikeudellinenKerrosala lupamaaraykset)])
          (:kokonaisala lupamaaraykset) (conj [(i18n/localize lang "verdict.kokonaisala") (:kokonaisala lupamaaraykset)])))

(defn- verdict-attachments [application id]
  (s/join "\n" (map (fn [verdict-att] (str (get-in verdict-att [:latestVersion :filename]))) (filter (fn [att] (and (= :verdict (keyword (get-in att [:target :type]))) (= id (get-in att [:target :id])))) (:attachments application)))))

(defn write-verdict-libre-doc [application id paatos-idx lang file]
  (let [verdict (first (filter #(= id (:id %)) (:verdicts application)))
        paatos (nth (:paatokset verdict) paatos-idx)
        lupamaaraykset (:lupamaaraykset paatos)
        reviews (map (fn [val] [(str (:katselmuksenLaji val)) (str (:tarkastuksenTaiKatselmuksenNimi val))]) (:vaaditutKatselmukset lupamaaraykset))
        orders (map (fn [val] [(str (:sisalto val))]) (:maaraykset lupamaaraykset))]
    (when (nil? paatos) (throw (Exception. (str "verdict.paatos.id [" paatos-idx "] not found in verdict:\n" (with-out-str (clojure.pprint/pprint verdict))))))
    (template/create-libre-doc (io/resource "private/lupapiste-verdict-template.fodt")
                               file
                               (assoc (template/common-field-map application lang)
                                 "FIELD001" (if (:sopimus verdict) (i18n/localize lang "userInfo.company.contract") (i18n/localize lang "application.verdict.title"))
                                 "FIELD012" (str (i18n/localize lang "verdict.id") ": " (:kuntalupatunnus verdict))
                                 "FIELD013" (i18n/localize lang "date")
                                 "DATESTABLE" (verdict-dates lang (:paivamaarat paatos))

                                 "LUPAMAARAYKSETHEADER" (i18n/localize lang "verdict.lupamaaraukset")
                                 "LUPAMAARAYKSETTABLE" (verdict-lupamaaraykset lang lupamaaraykset)

                                 "FIELD015A" (i18n/localize lang "foreman.requiredForemen")
                                 "FIELD015B" (get-in paatos [:lupamaaraykset :vaaditutTyonjohtajat])

                                 "PLANSHEADER" (i18n/localize lang "verdict.vaaditutErityissuunnitelmat")
                                 ;;TODO: get real data to see the structure
                                 "PLANSTABLE" (map #(str %) (:vaaditutErityissuunnitelmat lupamaaraykset))

                                 "LP-HEADER-OTHER" (i18n/localize lang "verdict.muutMaaraykset")
                                 ;;TODO: get real data to see the structure
                                 "OTHERTABLE" (map #(str %) (:muutMaaraykset lupamaaraykset))

                                 "FIELD016" (i18n/localize lang "verdict.vaaditutKatselmukset")
                                 "REVIEWSTABLE" reviews

                                 "FIELD17" (i18n/localize lang "verdict.maaraykset")
                                 "ORDERSTABLE" orders

                                 "MINUTESHEADER" (i18n/localize lang "verdict.poytakirjat")

                                 "MINUTESCOL1" (i18n/localize lang "verdict.status")
                                 "MINUTESCOL2" (i18n/localize lang "verdict.pykala")
                                 "MINUTESCOL3" (i18n/localize lang "verdict.name")
                                 "MINUTESCOL4" (i18n/localize lang "verdict.paatospvm")
                                 "MINUTESCOL5" (i18n/localize lang "verdict.attachments")

                                 "MINUTESTABLE" (map (fn [val] [(str (if (:status val) (i18n/localize lang "verdict.status" (str (:status val))) "") " (" (:paatoskoodi val) ") " (:paatos val))
                                                                (str (:pykala val))
                                                                (str (:paatoksentekija val))
                                                                (or (util/to-local-date (:paatospvm val)) "-")
                                                                (verdict-attachments application id)]) (:poytakirjat paatos))))))