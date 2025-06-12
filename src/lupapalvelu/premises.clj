(ns lupapalvelu.premises
  (:require [clojure.set :as set]
            [dk.ative.docjure.spreadsheet :as spreadsheet]
            [lupapalvelu.action :as action]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as log]
            [lupapalvelu.remote-excel-reader :as remote-excel-reader]
            [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$set]]
            [noir.response :as resp]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import (java.io OutputStream)))

(defn get-huoneistot-from-application [doc-id application]
  (-> (util/find-by-id doc-id (:documents application)) :data :huoneistot))

;;
;;  IFC-model apartment data from Excel file
;;

;; TODO: Does this also need to be updated to match the new ryhti spec??
(def ifc-labels ["Porras" "Huoneistonumero" "Huoneiston jakokirjain" "Sijaintikerros"
                 "Huoneiden lukum\u00e4\u00e4r\u00e4" "Keitti\u00f6tyyppi" "Huoneistoala" "Varusteena WC"
                 "Varusteena amme/suihku" "Varusteena parveke" "Varusteena Sauna"
                 "Varusteena l\u00e4mmin vesi"])

(def lp-huoneisto-keys [:huoneistoTyyppi :porras :huoneistonumero :jakokirjain :huoneluku :keittionTyyppi :huoneistoala
                        :WCKytkin :ammeTaiSuihkuKytkin :parvekeTaiTerassiKytkin :saunaKytkin :lamminvesiKytkin])

(def ^:ryhti lp-huoneisto-keys-ryhti
  [{:key :huoneistoTyyppi}
   {:key :porras}
   {:key :huoneistonumero}
   {:key :jakokirjain}
   {:key :sijaintikerros}
   {:key :sijaintikerroksen-tarkenne}
   {:key :huoneluku}
   {:key :keittionTyyppi}
   {:key :huoneistoala}
   {:key :wc          :i18nkey :suomi.fi.huoneiston-varuste.wc}
   {:key :suihku      :i18nkey :suomi.fi.huoneiston-varuste.suihku}
   {:key :sauna       :i18nkey :suomi.fi.huoneiston-varuste.sauna}
   {:key :parveke     :i18nkey :suomi.fi.huoneiston-varuste.parveke}
   {:key :lammin-vesi :i18nkey :suomi.fi.huoneiston-varuste.lammin-vesi}])

(def ifc->lp-val-map
  {:keittionTyyppi             {"1" "keittio"
                                "2" "keittokomero"
                                "3" "keittotila"
                                "4" "tupakeittio"
                                "5" "ei tiedossa"}
   :huoneistoTyyppi            {"1" "asuinhuoneisto"
                                "2" "toimitila"
                                "3" "ei tiedossa"}
   :WCKytkin                   {"1" true "0" false}
   :ammeTaiSuihkuKytkin        {"1" true "0" false}
   :parvekeTaiTerassiKytkin    {"1" true "0" false}
   :saunaKytkin                {"1" true "0" false}
   :lamminvesiKytkin           {"1" true "0" false}
   :sijaintikerroksen-tarkenne {"1" "kellari"
                                "2" "ullakko"}})

;; TODO: Does this need to include the new ryhti fields also?
(def ifc->lp-key-map
  {"Porras"                             :porras
   "Huoneistonumero"                    :huoneistonumero
   "Huoneiston jakokirjain"             :jakokirjain
   "Sijaintikerros"                     :sijaintikerros
   "Huoneiden lukum\u00e4\u00e4r\u00e4" :huoneluku
   "Keitti\u00f6tyyppi"                 :keittionTyyppi
   "Huoneistoala"                       :huoneistoala
   "Varusteena WC"                      :WCKytkin
   "Varusteena amme/suihku"             :ammeTaiSuihkuKytkin
   "Varusteena parveke"                 :parvekeTaiTerassiKytkin
   "Varusteena Sauna"                   :saunaKytkin
   "Varusteena l\u00e4mmin vesi"        :lamminvesiKytkin})

(defn localize-labels [lang]
  (map #(i18n/localize lang (util/kw-path :huoneistot %))
       lp-huoneisto-keys))

(defn ^:ryhti localize-labels-ryhti [lang]
  (->> lp-huoneisto-keys-ryhti
       (map #(if (:i18nkey %)
               (i18n/localize lang (:i18nkey %))
               (i18n/localize lang (util/kw-path :huoneistot (:key %)))))))

(def ^:ryhti lp-huoneisto-keys-ryhti-headers
 (mapv :key lp-huoneisto-keys-ryhti))

(defn pick-only-header-and-data-rows [rows ryhti-data-2026]
  (let [ifc-labels-set  (set ifc-labels)
        fi-labels       (set (localize-labels "fi"))
        sv-labels       (set (localize-labels "sv"))
        en-labels       (set (localize-labels "en"))
        fi-labels-ryhti (set (localize-labels-ryhti "fi"))
        sv-labels-ryhti (set (localize-labels-ryhti "sv"))
        en-labels-ryhti (set (localize-labels-ryhti "en"))
        ifc->lp-keys    (fn [header] (->> header (map #(get ifc->lp-key-map %))))]
    (loop [rows rows]
      (let [row (first rows)]
        (cond
          (empty? rows)
          nil

          (= (map ifc-labels-set row) row)
          {:header (ifc->lp-keys row) :data (rest rows)}

          (and (not ryhti-data-2026)
               (or (= (map fi-labels row) row)
                   (= (map sv-labels row) row)
                   (= (map en-labels row) row)))
          {:header lp-huoneisto-keys :data (rest rows)}

          (and ryhti-data-2026
               (or (= (map fi-labels-ryhti row) row)
                   (= (map sv-labels-ryhti row) row)
                   (= (map en-labels-ryhti row) row)))
          {:header lp-huoneisto-keys-ryhti-headers :data (rest rows)}

          :else (recur (rest rows)))))))

(def ^:private valid-keys-legacy
  "Valid huoneisto keys for non-ryhti mode."
  (set lp-huoneisto-keys))

(def ^:private valid-keys-ryhti
  "Valid huoneisto keys for ryhti mode."
  (set lp-huoneisto-keys-ryhti-headers))

(defn item->update [premises-number [lp-key ifc-val] ryhti-data-2026]
  (let [valid-keys (if ryhti-data-2026 valid-keys-ryhti valid-keys-legacy)
        valid-key? (valid-keys lp-key)]
    (when (and valid-key? (not (empty? ifc-val)))
      (let [lp-val-keys (set (keys ifc->lp-val-map))
            lp-val      (cond
                          (lp-val-keys lp-key)        (-> lp-key ifc->lp-val-map (get ifc-val))
                          (= :huoneistonumero lp-key) (->> ifc-val ss/remove-leading-zeros util/->int (format "%03d"))
                          (= :huoneistoala lp-key)    (ss/replace ifc-val #"," ".")
                          :else                       ifc-val)]
        (if ryhti-data-2026
          [[:rakennuskohteen-osa :huoneistot (-> premises-number str keyword) lp-key] lp-val]
          [[:huoneistot (-> premises-number str keyword) lp-key] lp-val])))))

(defn premise->updates [premise premises-number ryhti-data-2026]
  (remove #(= nil (second %)) (map #(item->update premises-number % ryhti-data-2026) premise)))

(defn data->model-updates [header-row data ryhti-data-2026]
  (let [premise-data (map #(zipmap header-row %) data)]
    (reduce #(concat %1 (premise->updates (nth premise-data %2) %2 ryhti-data-2026))
            []
            (range (count premise-data)))))

(defn remove-old-premises [command doc]
  (let [paths (->> command
                   :application
                   (get-huoneistot-from-application doc)
                   (keys)
                   (map (fn [huoneisto-key] [:huoneistot huoneisto-key])))]
    (when (seq paths)
      (doc-persistence/remove-document-data command doc paths :documents))))

(defn save-premises-data [model-updates {application :application timestamp :created {role :role} :user :as command} doc-id]
  (let [document                                         (util/find-by-id doc-id (:documents application))
        update-paths                                     (map first model-updates)
        {:keys [mongo-query mongo-updates post-results]} (apply doc-persistence/validated-model-updates application "documents" document
                                                                (doc-persistence/transform document model-updates) timestamp nil)]
    (doc-persistence/validate-against-whitelist! document update-paths role application)
    (doc-persistence/validate-readonly-updates! document update-paths)
    (remove-old-premises command doc-id)
    (action/update-application command mongo-query mongo-updates)
    (ok :results post-results)))

(defn upload-premises-data [{user :user application :application created :created :as command} files doc]
  (let [app-id                    (:id application)
        ryhti-data-2026           (:ryhti-data-2026 application)
        rows                      (-> files first (remote-excel-reader/xls-to-rows (:id user)))
        {:keys [header data]}     (when rows
                                    (pick-only-header-and-data-rows rows ryhti-data-2026))
        premises-data             (when (and header data)
                                    (data->model-updates header data ryhti-data-2026))
        file-updated?             (when-not (empty? premises-data)
                                    (-> premises-data (save-premises-data command doc) :ok))
        save-response             (when file-updated?
                                    (log/info "Premises updated by premises Excel file in application")
                                    (-> (first files)
                                        (select-keys [:filename :tempfile])
                                        (set/rename-keys {:tempfile :content})
                                        (file-upload/save-file :application app-id :linked true)))
        return-map                (cond
                                    file-updated? {:ok true}
                                    (nil? rows) {:ok false :text "error.illegal-premises-excel-file"}
                                    (empty? premises-data) {:ok false :text "error.illegal-premises-excel-data"}
                                    :else {:ok false})]
    (when file-updated?
      (let [old-ifc-fileId (-> application :ifc-data :fileId)]
        (action/update-application command {$set {:ifc-data {:fileId   (:fileId save-response)
                                                             :filename (:filename save-response)
                                                             :modified created
                                                             :user     (usr/summary user)}}})
        (when old-ifc-fileId
          (storage/delete-from-any-system (:id application) old-ifc-fileId))))
    (->> return-map
         (resp/json)
         (resp/status 200))))

;;
;;  Premises download
;;

(defn- get-keittio [tyyppi]
  (let [keittio-values {"keittio"      "1"
                        "keittokomero" "2"
                        "keittotila"   "3"
                        "tupakeittio"  "4"
                        "ei tiedossa"  "5"}]
    (get keittio-values tyyppi)))

(defn- get-huoneisto [tyyppi]
  (let [huoneisto-values {"asuinhuoneisto" "1"
                          "toimitila"      "2"
                          "ei tiedossa"    "3"}]
    (get huoneisto-values tyyppi)))

(defn- get-sijaintikerroksen-tarkenne [tyyppi]
  (let [sijaintikerroksen-tarkenne-values {"kellari" "1"
                                           "ullakko" "2"}]
    (get sijaintikerroksen-tarkenne-values tyyppi)))

(defn- get-huoneisto-ryhti [tyyppi]
  (let [huoneisto-values {"asuinhuoneisto" "1"
                          "toimitila"      "2"}]
    (get huoneisto-values tyyppi)))

(defn- get-keittio-ryhti [tyyppi]
  (let [keittio-values {"keittio"      "1"
                        "keittokomero" "2"
                        "keittotila"   "3"
                        "tupakeittio"  "4"}]
    (get keittio-values tyyppi)))

(defn- premises-workbook [sheet-name sheet-data]
  (let [wb            (spreadsheet/create-workbook sheet-name sheet-data)
        rows          (-> wb spreadsheet/sheet-seq first spreadsheet/row-seq)
        kitchen-info  (first rows)
        premises-info (second rows)
        header        (nth rows 2)
        header-style  (spreadsheet/create-cell-style! wb {:font {:bold true}})]
    (spreadsheet/set-row-style! kitchen-info header-style)
    (spreadsheet/set-row-style! premises-info header-style)
    (spreadsheet/set-row-style! header header-style)
    wb))

(defn ^OutputStream download-premises-template [user app-id doc-id lang]
  (let [application      (domain/get-application-as app-id user :include-canceled-apps? false)
        data             (->> doc-id
                              (domain/get-document-by-id application)
                              :data
                              :huoneistot
                              (vals)
                              (sort-by (juxt #(or (-> % :porras :value) "")
                                             #(-> % :huoneistonumero :value))))
        sheet-name       (i18n/localize lang "huoneistot.excel-sheet-name")
        kitchen-info     [(i18n/localize lang "huoneistot.excel-kitchen-info-box")]
        premises-info    [(i18n/localize lang "huoneistot.excel-premises-info-box")]
        localized-labels (localize-labels lang)
        header-info      [kitchen-info premises-info localized-labels]
        row-fn           (juxt #(-> % :huoneistoTyyppi :value (get-huoneisto))
                               #(-> % :porras :value (str))
                               #(-> % :huoneistonumero :value (str))
                               #(-> % :jakokirjain :value (str))
                               #(-> % :huoneluku :value (str))
                               #(-> % :keittionTyyppi :value (get-keittio))
                               #(-> % :huoneistoala :value (str))
                               #(if (-> % :WCKytkin :value) "1" "0")
                               #(if (-> % :ammeTaiSuihkuKytkin :value) "1" "0")
                               #(if (-> % :parvekeTaiTerassiKytkin :value) "1" "0")
                               #(if (-> % :saunaKytkin :value) "1" "0")
                               #(if (-> % :lamminvesiKytkin :value) "1" "0"))]
    (excel/xlsx-stream (premises-workbook sheet-name (concat header-info (map row-fn data))))))

(defn ^OutputStream ^:ryhti download-premises-template-ryhti-data-2026 [user app-id doc-id lang]
  (let [application      (domain/get-application-as app-id user :include-canceled-apps? false)
        data             (->> doc-id
                              (domain/get-document-by-id application)
                              :data
                              :rakennuskohteen-osa
                              :huoneistot
                              (vals)
                              (sort-by (juxt #(or (-> % :porras :value) "")
                                             #(-> % :huoneistonumero :value))))
        sheet-name       (i18n/localize lang "huoneistot.excel-sheet-name")
        kitchen-info     [(i18n/localize lang "huoneistot.excel-kitchen-info-box")]
        premises-info    [(i18n/localize lang "huoneistot.excel-premises-info-box")]
        floor-spec-info  [(i18n/localize lang "huoneistot.excel-sijaintikerroksen-tarkenne-info-box")]
        localized-labels (localize-labels-ryhti lang)
        header-info      [kitchen-info premises-info floor-spec-info localized-labels]
        row-fn           (juxt #(-> % :huoneistoTyyppi :value (get-huoneisto-ryhti))
                               #(-> % :porras :value (str))
                               #(-> % :huoneistonumero :value (str))
                               #(-> % :jakokirjain :value (str))
                               #(-> % :sijaintikerros :value (str))
                               #(-> % :sijaintikerroksen-tarkenne :value (get-sijaintikerroksen-tarkenne))
                               #(-> % :huoneluku :value (str))
                               #(-> % :keittionTyyppi :value (get-keittio-ryhti))
                               #(-> % :huoneistoala :value (str))
                               #(-> % :wc :value (str))
                               #(-> % :suihku :value (str))
                               #(-> % :sauna :value (str))
                               #(-> % :parveke :value (str))
                               #(-> % :lammin-vesi :value (str)))]
    (excel/xlsx-stream (premises-workbook sheet-name (concat header-info (map row-fn data))))))

(defn add-source-values [source premises]
  (->> premises
       (map (fn [[premise-index premise]]
              (let [premise-with-source-values (->> premise
                                                   (map (fn [[property {:keys [value] :as property-data}]]
                                                          [property (merge property-data {:source source :sourceValue value})]))
                                                   (into {}))]
                [premise-index premise-with-source-values])))
       (into {})))

(defn set-premises-source-values [source document]
  (let [premises (get-in document [:data :huoneistot])]
    (if premises
      (update-in document [:data :huoneistot] (partial add-source-values source))
      document)))

(defn set-premises-source-values-for-documents [source documents]
  (map (partial set-premises-source-values source) documents))
