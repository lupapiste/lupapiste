(ns lupapalvelu.premises
  (:require [clojure.set :as set]
            [dk.ative.docjure.spreadsheet :as spreadsheet]
            [lupapalvelu.action :as action]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.remote-excel-reader :as remote-excel-reader]
            [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$set]]
            [noir.response :as resp]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :as timbre])
  (:import (java.io OutputStream)))

(defn get-huoneistot-from-application [doc-id application]
  (-> (util/find-by-id doc-id (:documents application)) :data :huoneistot))

;;
;;  IFC-model apartment data from Excel file
;;

(def ifc-labels ["Porras" "Huoneistonumero" "Huoneiston jakokirjain" "Sijaintikerros"
                 "Huoneiden lukum\u00e4\u00e4r\u00e4" "Keitti\u00f6tyyppi" "Huoneistoala" "Varusteena WC"
                 "Varusteena amme/suihku" "Varusteena parveke" "Varusteena Sauna"
                 "Varusteena l\u00e4mmin vesi"])

(def lp-huoneisto-keys [:huoneistoTyyppi :porras :huoneistonumero :jakokirjain :huoneluku :keittionTyyppi :huoneistoala
                        :WCKytkin :ammeTaiSuihkuKytkin :parvekeTaiTerassiKytkin :saunaKytkin :lamminvesiKytkin])

(def ifc->lp-val-map
  {:keittionTyyppi          {"1" "keittio"
                             "2" "keittokomero"
                             "3" "keittotila"
                             "4" "tupakeittio"
                             "5"  "ei tiedossa"}
   :huoneistoTyyppi         {"1" "asuinhuoneisto"
                             "2" "toimitila"
                             "3"  "ei tiedossa"}
   :WCKytkin                {"1" true "0" false}
   :ammeTaiSuihkuKytkin     {"1" true "0" false}
   :parvekeTaiTerassiKytkin {"1" true "0" false}
   :saunaKytkin             {"1" true "0" false}
   :lamminvesiKytkin        {"1" true "0" false}
   :sijaintikerros          nil})

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
  (->> lp-huoneisto-keys
       (map name)
       (map #(str "huoneistot." %))
       (map #(i18n/localize lang %))))

(defn pick-only-header-and-data-rows [rows]
  (let [ifc-labels-set (set ifc-labels)
        fi-labels      (set (localize-labels "fi"))
        sv-labels      (set (localize-labels "sv"))
        en-labels      (set (localize-labels "en"))
        ifc->lp-keys   (fn [header] (->> header (map #(get ifc->lp-key-map %))))]
    (loop [rows rows]
      (let [row (first rows)]
        (cond
          (empty? rows) nil
          (= (map ifc-labels-set row) row) {:header (ifc->lp-keys row) :data (rest rows)}
          (or (= (map fi-labels row) row)
              (= (map sv-labels row) row)
              (= (map en-labels row) row)) {:header lp-huoneisto-keys :data (rest rows)}
          :else (recur (rest rows)))))))

(defn item->update [premises-number [lp-key ifc-val]]
  (let [lp-val-keys #{:WCKytkin :keittionTyyppi :huoneistoTyyppi :ammeTaiSuihkuKytkin
                      :saunaKytkin :lamminvesiKytkin :parvekeTaiTerassiKytkin :sijaintikerros}
        lp-val (cond
                 (lp-val-keys lp-key) (-> lp-key ifc->lp-val-map (get ifc-val))
                 (= :huoneistonumero lp-key) (->> ifc-val ss/remove-leading-zeros util/->int (format "%03d"))
                 (= :huoneistoala lp-key) (ss/replace ifc-val #"," ".")
                 :else ifc-val)]
    (when-not (empty? ifc-val)
      [[:huoneistot (-> premises-number str keyword) lp-key] lp-val])))

(defn premise->updates [premise premises-number]
  (remove #(= nil (second %)) (map #(item->update premises-number %) premise)))

(defn data->model-updates [header-row data]
  (let [premise-data (map #(zipmap header-row %) data)]
    (reduce #(concat %1 (premise->updates (nth premise-data %2) %2))
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
        rows                      (-> files first (remote-excel-reader/xls-to-rows (:id user)))
        {:keys [header data]}     (when rows
                                    (pick-only-header-and-data-rows rows))
        premises-data             (when (and header data)
                                    (data->model-updates header data))
        file-updated?             (when-not (empty? premises-data)
                                    (-> premises-data (save-premises-data command doc) :ok))
        save-response             (when file-updated?
                                    (timbre/info "Premises updated by premises Excel file in application")
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
                              (sort-by #(-> % :huoneistonumero :value)))
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
