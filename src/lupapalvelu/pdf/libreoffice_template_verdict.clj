(ns lupapalvelu.pdf.libreoffice-template-verdict
  (:require [taoensso.timbre :as log]
            [sade.util :as util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pdf.libreoffice-template :as template]
            [clojure.string :as s]
            [clojure.java.io :as io]))


(defn- verdict-attachments [application id]
  (s/join "\n" (map (fn [verdict-att] (str (get-in verdict-att [:latestVersion :filename]))) (filter (fn [att] (and (= :verdict (keyword (get-in att [:target :type]))) (= id (get-in att [:target :id])))) (:attachments application)))))

(defn- get-lupamaaraykset [application verdict-id paatos-idx]
  (:lupamaaraykset (nth (:paatokset (first (filter #(= verdict-id (:id %)) (:verdicts application)))) paatos-idx)))

(defn- filter-tasks [application verdict-id type]
  (filter (fn [task]
            (log/debug "task: " task "=" (= (get-in task [:schema-info :name]) type))
            (and (= (get-in task [:schema-info :name]) type)
                 (= (get-in task [:source :type]) "verdict")
                 (= (get-in task [:source :id]) verdict-id))) (:tasks application)))

(defn- verdict-dates [lang dates]
  (cond-> []
          (:anto dates) (conj [(i18n/localize lang "verdict.anto") (or (util/to-local-date (:anto dates)) "-")])
          (:lainvoimainen dates) (conj [(i18n/localize lang "verdict.lainvoimainen") (or (util/to-local-date (:lainvoimainen dates)) "-")])
          (:aloitettava dates) (conj [(i18n/localize lang "verdict.aloitettava") (or (util/to-local-date (:aloitettava dates)) "-")])
          (:voimassaHetki dates) (conj [(i18n/localize lang "verdict.voimassaHetki") (or (util/to-local-date (:voimassaHetki dates)) "-")])
          (:viimeinenValitus dates) (conj [(i18n/localize lang "verdict.viimeinenValitus") (or (util/to-local-date (:viimeinenValitus dates)) "-")])
          (:raukeamis dates) (conj [(i18n/localize lang "verdict.raukeamis") (or (util/to-local-date (:raukeamis dates)) "-")])
          (:paatosdokumentinPvm dates) (conj [(i18n/localize lang "verdict.paatosdokumentinPvm") (or (util/to-local-date (:paatosdokumentinPvm dates)) "-")])
          (:julkipano dates) (conj [(i18n/localize lang "verdict.julkipano") (or (util/to-local-date (:julkipano dates)) "-")])
          (:paatosPvm dates) (conj [(i18n/localize lang "verdict.paatosPvm") (or (util/to-local-date (:paatosPvm dates)) "-")])))

(defn- verdict-lupamaaraykset [application verdict-id paatos-idx lang]
  (let [lupamaaraykset (get-lupamaaraykset application verdict-id paatos-idx)]
    (cond-> []
            (:autopaikkojaEnintaan lupamaaraykset) (conj [(i18n/localize lang "verdict.autopaikkojaEnintaan") (:autopaikkojaEnintaan lupamaaraykset)])
            (:autopaikkojaVahintaan lupamaaraykset) (conj [(i18n/localize lang "verdict.autopaikkojaVahintaan") (:autopaikkojaVahintaan lupamaaraykset)])
            (:autopaikkojaRakennettava lupamaaraykset) (conj [(i18n/localize lang "verdict.autopaikkojaRakennettava") (:autopaikkojaRakennettava lupamaaraykset)])
            (:autopaikkojaRakennettu lupamaaraykset) (conj [(i18n/localize lang "verdict.autopaikkojaRakennettu") (:autopaikkojaRakennettu lupamaaraykset)])
            (:autopaikkojaKiinteistolla lupamaaraykset) (conj [(i18n/localize lang "verdict.autopaikkojaKiinteistolla") (:autopaikkojaKiinteistolla lupamaaraykset)])
            (:autopaikkojaUlkopuolella lupamaaraykset) (conj [(i18n/localize lang "verdict.autopaikkojaUlkopuolella") (:autopaikkojaUlkopuolella lupamaaraykset)])
            (:takuuaikaPaivat lupamaaraykset) (conj [(i18n/localize lang "verdict.takuuaikaPaivat") (:takuuaikaPaivat lupamaaraykset)])
            (:kerrosala lupamaaraykset) (conj [(i18n/localize lang "verdict.kerrosala") (:kerrosala lupamaaraykset)])
            (:rakennusoikeudellinenKerrosala lupamaaraykset) (conj [(i18n/localize lang "verdict.rakennusoikeudellinenKerrosala") (:rakennusoikeudellinenKerrosala lupamaaraykset)])
            (:kokonaisala lupamaaraykset) (conj [(i18n/localize lang "verdict.kokonaisala") (:kokonaisala lupamaaraykset)]))))

;; (sc/optional-key :vaadittuTyonjohtajatieto)       [sc/Str]
(defn- verdict-foremen [application verdict-id paatos-idx]
  (into []
  (if-let [krysp-foremen (:vaadittuTyonjohtajatieto (get-lupamaaraykset application verdict-id paatos-idx))]
    (map (fn [row] [(str row)]) krysp-foremen)
    (map (fn [row] [(str (:taskname row))]) (filter-tasks application verdict-id "task-vaadittu-tyonjohtaja")))))

;(sc/optional-key :vaaditutErityissuunnitelmat)    [sc/Str]
(defn- verdict-vaaditutErityissuunnitelmat [application verdict-id paatos-idx]
  (into [] (map (fn [row] [(str row)]) (:vaaditutErityissuunnitelmat (get-lupamaaraykset application verdict-id paatos-idx)))))

;;(sc/optional-key :muutMaaraykset)                 [(sc/maybe sc/Str)]
(defn- verdict-muutMaaraykset [application verdict-id paatos-idx]
  (into []
  (if-let [krysp-other-requirements (:muutMaaraykset (get-lupamaaraykset application verdict-id paatos-idx))]
    (map (fn [row] [(str row)]) krysp-other-requirements)
    (map (fn [row] [(str (:taskname row))]) (filter-tasks application verdict-id "task-lupamaarays")))))

;;(sc/optional-key :vaaditutKatselmukset)           [Katselmus]
(defn- verdict-vaaditutKatselmukset [application verdict-id paatos-idx lang]
  (if-let [krysp-other-requirements (:vaaditutKatselmukset (get-lupamaaraykset application verdict-id paatos-idx))]
    (map (fn [val] [(str (:katselmuksenLaji val)) (str (:tarkastuksenTaiKatselmuksenNimi val))])
         krysp-other-requirements)
    (map (fn [child] [(i18n/localize (name lang) (str (get-in child [:schema-info :i18nprefix]) "." (get-in child [:data :katselmuksenLaji :value])))])
         (filter-tasks application verdict-id "task-katselmus"))))

;(sc/optional-key :maaraykset)                     [Maarays]
(defn- verdict-maaraykset [application verdict-id paatos-idx]
  (into [] (map (fn [val] [(str (:sisalto val))]) (:maaraykset (get-lupamaaraykset application verdict-id paatos-idx)))))

(defn write-verdict-libre-doc [application id paatos-idx lang file]
  (let [verdict (first (filter #(= id (:id %)) (:verdicts application)))
        paatos (nth (:paatokset verdict) paatos-idx)]
    (when (nil? paatos) (throw (Exception. (str "verdict.paatos.id [" paatos-idx "] not found in verdict:\n" (with-out-str (clojure.pprint/pprint verdict))))))
    (template/create-libre-doc (io/resource "private/lupapiste-verdict-template.fodt")
                               file
                               (assoc (template/common-field-map application lang)
                                 "FIELD001" (if (:sopimus verdict) (i18n/localize lang "userInfo.company.contract") (i18n/localize lang "application.verdict.title"))
                                 "FIELD012" (str (i18n/localize lang "verdict.id") ": " (:kuntalupatunnus verdict))
                                 "FIELD013" (i18n/localize lang "date")
                                 "DATESTABLE" (verdict-dates lang (:paivamaarat paatos))

                                 "LUPAMAARAYKSETHEADER" (i18n/localize lang "verdict.lupamaaraukset")
                                 "LUPAMAARAYKSETTABLE" (verdict-lupamaaraykset application id paatos-idx lang)

                                 "FOREMENHEADER" (i18n/localize lang "foreman.requiredForemen")
                                 "FOREMENTABLE" (verdict-foremen application id paatos-idx)

                                 "PLANSHEADER" (i18n/localize lang "verdict.vaaditutErityissuunnitelmat")
                                 "PLANSTABLE" (verdict-vaaditutErityissuunnitelmat application id paatos-idx)

                                 "OTHERHEADER" (i18n/localize lang "verdict.muutMaaraykset")
                                 "OTHERTABLE" (verdict-muutMaaraykset application id paatos-idx)

                                 "REVIEWHEADER" (i18n/localize lang "verdict.vaaditutKatselmukset")
                                 "REVIEWSTABLE" (verdict-vaaditutKatselmukset application id paatos-idx lang)

                                 "FIELD17" (i18n/localize lang "verdict.maaraykset")
                                 "ORDERSTABLE" (verdict-maaraykset application id paatos-idx)

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