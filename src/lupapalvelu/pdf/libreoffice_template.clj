(ns
  ^{:doc    "For creating PDF/A documents from Libre Office Flat Open Document Format files used as templates.
             Read resources/private/lupapiste-template.fodt with Libre Office for more information."}
  lupapalvelu.pdf.libreoffice-template
  (:require [taoensso.timbre :refer [trace debug debugf info infof warn warnf error fatal]]
            [sade.util :as util]
            [sade.property :as p]
            [sade.xml :as sx]
            [lupapalvelu.organization :as org]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [clojure.xml :as xml]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(defn xml-escape [text]
  (sx/escape-xml (str text)))
;;(s/escape (str text) {\< "&lt;", \> "&gt;", \& "&amp;"}))

(defn build-user-field
  ([space type value-type value name]
   (str space "<text:user-field-decl office:value-type=\"" type "\" office:" value-type "value=\"" (xml-escape value) "\" text:name=\"" name "\"/>"))
  ([value name] (build-user-field "    " "string" "string-" value name)))

(defn replace-user-field [line data]
  (let [match (re-find (re-matcher #"(\s*?)<text:user-field-decl office:value-type=\"(.*?)\" office:(.*?)value=\"(.*?)\" text:name=\"(.*?)\"\/>" line))
        key (nth match 5)]
    (if (and match (contains? data key))
      (build-user-field (nth match 1) (nth match 2) (nth match 3) (get data key) key)
      line)))

(defn xml-table-row [& cols]
  (with-out-str (xml/emit-element {:tag     :table:table-row
                                   :content (map (fn [val] {:tag     :table:table-cell
                                                            :attrs   {:office:value-type "string"}
                                                            :content (map (fn [p] {:tag     :text:p
                                                                                   :content [(xml-escape p)]}) (s/split val #"\n"))}) cols)})))
;; Deprecated, use User Fields in templates
(defn- replace-text [line field value]
  (s/replace line field (str value)))

(defn- localized-text [lang value]
  (if (nil? value) "" (xml-escape (i18n/localize lang value))))

(defn- get-authority [lang {authority :authority :as application}]
  (if (and (:authority application)
           (domain/assigned? application))
    (str (:lastName authority) " " (:firstName authority))
    (i18n/localize lang "application.export.empty")))

(defn- get-operations [{:keys [primaryOperation secondaryOperations]}]
  (s/join ", " (map (fn [[op c]] (str (if (> c 1) (str c " \u00D7 ")) (i18n/loc "operations" op)))
                    (frequencies (map :name (remove nil? (conj (seq secondaryOperations) primaryOperation)))))))

;TODO: Give more descriptive names like "LPATITLE_ADDRESS" and "LPAVALUE_ADDRESS" and re-do templates
(defn common-field-map [application lang]
  {"FOOTER_PAGE"           (localized-text lang "application.export.page")
   "FOOTER_DATE"           (util/to-local-datetime (System/currentTimeMillis))

   "LPATITLE_ID"           (localized-text lang "verdict-attachment-prints-order.order-dialog.lupapisteId")
   "LPAVALUE_ID"           (xml-escape (:id application))

   "LPATITLE_MUNICIPALITY" (localized-text lang "application.muncipality")
   "LPAVALUE_MUNICIPALITY" (localized-text lang (str "municipality." (:municipality application)))

   "LPATITLE_ADDRESS"      (localized-text lang "application.address")
   "LPAVALUE_ADDRESS"      (xml-escape (:address application))

   "LPATITLE_PROPERTYID"   (localized-text lang "kiinteisto.kiinteisto.kiinteistotunnus")
   "LPAVALUE_PROPERTYID"   (xml-escape (if (nil? (:propertyId application)) (i18n/localize lang "application.export.empty") (p/to-human-readable-property-id (:propertyId application))))

   "LPATITLE_SUBMITTED"    (localized-text lang "submitted")
   "LPAVALUE_SUBMITTED"    (xml-escape (or (util/to-local-date (:submitted application)) "-"))

   "LPATITLE_AUTHORITY"    (localized-text lang "applications.authority")
   "LPAVALUE_AUTHORITY"    (xml-escape (get-authority lang application))

   "LPATITLE_APPLICANT"    (localized-text lang "applicant")
   "LPAVALUE_APPLICANT"    (xml-escape (s/join ", " (:_applicantIndex application)))

   "LPATITLE_OPERATIONS"   (localized-text lang "selectm.source.label.edit-selected-operations")
   "LPAVALUE_OPERATIONS"   (xml-escape (get-operations application))

   ;;TODO: remove these old common fields below, once all templates have been updated
   "FIELD002"              (xml-escape (:address application))

   "FIELD003A"             (localized-text lang "application.muncipality")
   "FIELD003B"             (localized-text lang (str "municipality." (:municipality application)))

   "FIELD004A"             (localized-text lang "application.export.state")
   "FIELD004B"             (localized-text lang (:state application))

   "FIELD005A"             (localized-text lang "kiinteisto.kiinteisto.kiinteistotunnus")
   "FIELD005B"             (xml-escape (if (nil? (:propertyId application)) (i18n/localize lang "application.export.empty") (p/to-human-readable-property-id (:propertyId application))))

   "FIELD006A"             (localized-text lang "submitted")
   "FIELD006B"             (xml-escape (or (util/to-local-date (:submitted application)) "-"))

   "FIELD007A"             (localized-text lang "verdict-attachment-prints-order.order-dialog.lupapisteId")
   "FIELD007B"             (xml-escape (:id application))

   "FIELD008A"             (localized-text lang "applications.authority")
   "FIELD008B"             (xml-escape (get-authority lang application))

   "FIELD009A"             (localized-text lang "application.address")
   "FIELD009B"             (xml-escape (:address application))

   "FIELD010A"             (localized-text lang "applicant")
   "FIELD010B"             (xml-escape (s/join ", " (:_applicantIndex application)))

   "FIELD011A"             (localized-text lang "selectm.source.label.edit-selected-operations")
   "FIELD011B"             (xml-escape (get-operations application))})

(defn- write-line [line data wrtr]
  (.write wrtr (str (reduce (fn [s [k v]] (if (s/includes? s (str ">" k "<")) (replace-text s k v) (replace-user-field s data))) line data) "\n")))

(defn- get-table-name [line] (nth (re-find #"<table:table table:name=\"(.*?)\"" line) 1))

(defn- write-table! [rdr wrtr table-rows fields]
  (doseq [line (take-while (fn [line] (not (s/includes? line "</table:table-header-rows>"))) (line-seq rdr))]
    (write-line line fields wrtr)
    (when-let [table-rows2 (get fields (get-table-name line))]
      (write-table! rdr wrtr table-rows2 fields)))
  (write-line "      </table:table-header-rows>" fields wrtr)
  (doseq [row table-rows]
    (.write wrtr (str (apply xml-table-row row) "\n")))

  ;; advance reader past rows we want to skip
  (doseq [_ (take-while (fn [line] (not (s/includes? line "</table:table>"))) (line-seq rdr))])
  (write-line "</table:table>" fields wrtr))

(defn- applicant-name-from-doc [document]
  (when-let [body (:data document)]
    (if (= (get-in body [:_selected :value]) "yritys")
      (let [name (get-in body [:yritys :yritysnimi :value])
            y-tunnus (get-in body [:yritys :liikeJaYhteisoTunnus :value])]
        (str name " / (" y-tunnus ")"))
      (let [{first-name :etunimi last-name :sukunimi} (get-in body [:henkilo :henkilotiedot])]
        (s/trim (str (:value last-name) \space (:value first-name)))))))

(defn applicant-index [application]
  (let [applicants (remove s/blank? (map applicant-name-from-doc (domain/get-applicant-documents (:documents application))))
        applicant (:applicant application)]
    [(if (seq applicants) applicants [applicant])]))

(defn child-attachments [application child-type id]
  (let [type (case child-type
               :statements :statement
               :verdicts :verdict)]
    (filter (fn [att] (and (= type (keyword (get-in att [:target :type]))) (= id (get-in att [:target :id])))) (:attachments application))))

(defn get-organization-name [application lang]
  (org/get-organization-name (:organization application) lang))

(defn get-document-data [application type data]
  (-> (domain/get-document-by-name application type) (get :data) (get-in data)))

(defn create-libre-doc [template file fields]
  (with-open [wrtr (io/writer file :encoding "UTF-8" :append true)]
    (with-open [rdr (io/reader template)]
      (doseq [line (line-seq rdr)]
        (write-line line fields wrtr)
        (when-let [table-rows (get fields (get-table-name line))]
          (write-table! rdr wrtr table-rows fields))))))