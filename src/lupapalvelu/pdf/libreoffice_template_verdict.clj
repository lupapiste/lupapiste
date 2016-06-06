(ns lupapalvelu.pdf.libreoffice-template-verdict
  (:require [taoensso.timbre :as timbre]
            [sade.util :as util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pdf.libreoffice-template :refer [get-organization-name applicant-index] :as template]
            [clojure.string :as s]
            [lupapalvelu.domain :as domain]
            [clojure.java.io :as io]))

(defn- get-lupamaaraykset [application verdict-id paatos-idx]
  (:lupamaaraykset (nth (:paatokset (first (filter #(= verdict-id (:id %)) (:verdicts application)))) paatos-idx)))

(defn- filter-tasks [application verdict-id type]
  (filter (fn [task]
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
          (map (fn [row] [(str (get-in row [:data :maarays :value]) " (" (:taskname row) ")")]) (filter-tasks application verdict-id "task-lupamaarays")))))

;;(sc/optional-key :vaaditutKatselmukset)           [Katselmus]
(defn- verdict-vaaditutKatselmukset [application verdict-id paatos-idx lang]
  (if-let [krysp-other-requirements (:vaaditutKatselmukset (get-lupamaaraykset application verdict-id paatos-idx))]
    (map (fn [val] [(str (:katselmuksenLaji val)) (str (:tarkastuksenTaiKatselmuksenNimi val))])
         krysp-other-requirements)
    (map (fn [child] [(str (:taskname child))])
         (concat (filter-tasks application verdict-id "task-katselmus-ya") (filter-tasks application verdict-id "task-katselmus")))))

;(sc/optional-key :maaraykset)                     [Maarays]
(defn- verdict-maaraykset [application verdict-id paatos-idx]
  (into [] (map (fn [val] [(str (:sisalto val))]) (:maaraykset (get-lupamaaraykset application verdict-id paatos-idx)))))

(defn- verdict-attachments [application id]
  (map (fn [verdict-att] [(str (get-in verdict-att [:latestVersion :filename]))]) (filter (fn [att] (and (= :verdict (keyword (get-in att [:target :type]))) (= id (get-in att [:target :id])))) (:attachments application))))

(defn- verdict-status [paatos lang]
  (s/join "\n" (map (fn [val] (str (if (:status val) (i18n/localize lang "verdict.status" (str (:status val))) (:paatoskoodi val)))) (:poytakirjat paatos))))

(defn- verdict-paatos-key [paatos key]
  (s/join "\n" (map (fn [val] (str (key val))) (:poytakirjat paatos))))

(defn- verdict-signatures [verdict paatos]
  (into [[(verdict-paatos-key paatos :paatoksentekija) (or (util/to-local-date (get-in paatos [:paivamaarat :anto])) "-")]]
        (map (fn [sig] [(str (get-in sig [:user :firstName]) " " (get-in sig [:user :lastName])) (or (util/to-local-date (:created sig)) "-")]) (:signatures verdict))))

(defn- get-vastuuhenkilo [application]
    (let [data (template/get-document application :tyomaastaVastaava)
          henkilo-nimi (template/name-from-doc data)]
      henkilo-nimi))

(defn write-verdict-libre-doc [application id paatos-idx lang file]
  (let [applicants (map (fn [val] [val]) (map template/name-from-doc (template/get-applicant-docs application)))
        _ (timbre/debug "aplicants: " (with-out-str (clojure.pprint/pprint applicants)))
        verdict (first (filter #(= id (:id %)) (:verdicts application)))
        paatos (nth (:paatokset verdict) paatos-idx)
        start-time (template/get-in-document-data application :tyoaika [:tyoaika-alkaa-pvm :value])
        end-time (template/get-in-document-data application :tyoaika [:tyoaika-paattyy-pvm :value])]
    (when (nil? paatos) (throw (Exception. (str "verdict.paatos.id [" paatos-idx "] not found in verdict:\n" (with-out-str (clojure.pprint/pprint verdict))))))
    (template/create-libre-doc (io/resource (if (:sopimus verdict) "private/lupapiste-ya-contract-template.fodt" "private/lupapiste-ya-verdict-template.fodt"))
                               file
                               (assoc (template/common-field-map application lang)

                                 "LPTABLE_APPLICANT" applicants

                                 "LPATITLE_OPERATIONS" (i18n/localize lang "verdict.export.operations")

                                 "LPTITLE" (i18n/localize lang (if (:sopimus verdict) "userInfo.company.contract" "application.verdict.title"))

                                 "LPTITLE_CONTRACT_DATE" (i18n/localize lang "verdict.contract.date")

                                 "LPTITLE_KUNTA" (i18n/localize lang "verdict.id")
                                 "LPTITLE_KUNTALUPA" (i18n/localize lang "linkPermit.dialog.kuntalupatunnus")
                                 "LPVALUE_KUNTALUPA" (:kuntalupatunnus verdict)

                                 "LPTITLE_SIJAINTI" (i18n/localize lang "applications.location")

                                 "LPTITLE_VERDICT_GIVEN" (i18n/localize lang "verdict.anto")
                                 "LPVALUE_VERDICT_GIVEN" (or (util/to-local-date (get-in paatos [:paivamaarat :anto])) "-")

                                 "LPTITLE_VERDICT_LEGAL" (i18n/localize lang "verdict.lainvoimainen")
                                 "LPVALUE_VERDICT_LEGAL" (or (util/to-local-date (get-in paatos [:paivamaarat :lainvoimainen])) "-")

                                 "LPTITLE_VERDICT_STATE" (i18n/localize lang "verdict.status")
                                 "LPVALUE_VERDICT_STATE" (verdict-status paatos lang)

                                 "LPTITLE_TEKSTI" (i18n/localize lang (if (:sopimus verdict) "verdict.contract.text" "verdict.text"))
                                 "LPTABLE_TEKSTI" (map (fn [line] ["" line]) (clojure.string/split (verdict-paatos-key paatos :paatos) #"\n"))

                                 "LPTITLE_VASTUU" (i18n/localize lang "verdict.vastuuhenkilo")
                                 "LPVALUE_VASTUU" (get-vastuuhenkilo application)

                                 "LPTITLE_LUPA_AIKA" (if (s/blank? start-time) "" (i18n/localize lang "tyoaika._group_label"))
                                 "LPVALUE_LUPA_AIKA" (if (s/blank? start-time) ""  (str start-time " - " end-time))


                                 ;;                                 "LUPAMAARAYKSETHEADER" (i18n/localize lang "verdict.lupamaaraukset")
                                 ;;                                 "LUPAMAARAYKSETTABLE" (verdict-lupamaaraykset application id paatos-idx lang)

                                 ;;                                 "FOREMENHEADER" (i18n/localize lang "foreman.requiredForemen")
                                 ;;                                 "FOREMENTABLE" (verdict-foremen application id paatos-idx)

                                 ;;                                 "PLANSHEADER" (i18n/localize lang "verdict.vaaditutErityissuunnitelmat")
                                 ;;                                 "PLANSTABLE" (verdict-vaaditutErityissuunnitelmat application id paatos-idx)

                                 "REVIEWHEADER" (i18n/localize lang "verdict.vaaditutKatselmukset")
                                 "REVIEWSTABLE" (verdict-vaaditutKatselmukset application id paatos-idx lang)

                                 "OTHERHEADER" (i18n/localize lang "verdict.muutMaaraykset")
                                 "OTHERTABLE" (verdict-muutMaaraykset application id paatos-idx)

                                 ;;                                 "FIELD17" (i18n/localize lang "verdict.maaraykset")
                                 ;;                                 "ORDERSTABLE" (verdict-maaraykset application id paatos-idx)

                                 "LPTITLE_ATTACHMENTS" (i18n/localize lang (if (:sopimus verdict) "verdict.contract.attachments" "application.verdict-attachments"))
                                 "ATTACHMENTTABLE" (verdict-attachments application id)

                                 "LPTITLE_OSAPUOLET" (i18n/localize lang "verdict.osapuolet.ja.yhteyshenkilot")
                                 "LPTABLE-OSAPUOLET" (into [[(str (get-organization-name application lang) " / " (verdict-paatos-key paatos :paatoksentekija))]] (applicant-index application))



                                 "LPTITLE_SISALTO" (i18n/localize lang "verdict.contract.content")

                                 "LPTITLE_SIGNED" (i18n/localize lang "verdict.contract.signed.by")

                                 "LPTABLE-SIGNATURES" (verdict-signatures verdict paatos)
                                 ))))