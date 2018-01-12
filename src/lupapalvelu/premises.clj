(ns lupapalvelu.premises
  (:require [dk.ative.docjure.spreadsheet :as spreadsheet]
            [lupapalvelu.action :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.user :as usr]
            [lupapalvelu.xls-muuntaja-client :as xmc]
            [monger.operators :refer [$set]]
            [noir.response :as resp]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [slingshot.slingshot :refer [throw+]]
            [taoensso.timbre :as timbre])
  (:import (java.io OutputStream)))

(defn get-huoneistot-from-application [doc-id application]
  (-> (util/find-by-id doc-id (:documents application)) :data :huoneistot))

;;
;;  IFC-model apartment data from Excel file
;;

(def ifc-labels ["Porras" "Huoneistonumero" "Huoneiston jakokirjain" "Sijaintikerros"
                 "Huoneiden lukumäärä" "Keittiötyyppi" "Huoneistoala" "Varusteena WC"
                 "Varusteena amme/suihku" "Varusteena parveke" "Varusteena Sauna"
                 "Varusteena lämmin vesi"])

(def ifc->lp-val-map
  {:keittionTyyppi          {"1" "keittio"
                             "2" "keittokomero"
                             "3" "keittotila"
                             "4" "tupakeittio"
                             ""  "ei tiedossa"}
   :huoneistoTyyppi         {"1" "asuinhuoneisto"
                             "2" "toimitila"
                             ""  "ei tiedossa"}
   :WCKytkin                {"1" true "0" false}
   :ammeTaiSuihkuKytkin     {"1" true "0" false}
   :parvekeTaiTerassiKytkin {"1" true "0" false}
   :saunaKytkin             {"1" true "0" false}
   :lamminvesiKytkin        {"1" true "0" false}})

(def localized-ifc-keys
  {"huoneiston tyyppi"      :huoneistoTyyppi
   "lägenhetstyp"           :huoneistoTyyppi
   "dwelling type"          :huoneistoTyyppi
   "porras"                 :porras
   "trappa"                 :porras
   "stairway"               :porras
   "huoneistonumero"        :huoneistonumero
   "huoneiston numero"      :huoneistonumero
   "lägenhetsnummer"        :huoneistonumero
   "flat number"            :huoneistonumero
   "huoneiston jakokirjain" :jakokirjain
   "jakokirjain"            :jakokirjain
   "delningsbokstav"        :jakokirjain
   "splitting letter"       :jakokirjain
   "huoneiden lukumäärä"    :huoneluku
   "huoneluku"              :huoneluku
   "antal rum"              :huoneluku
   "number of rooms"        :huoneluku
   "keittiötyyppi"          :keittionTyyppi
   "keittiön tyyppi"        :keittionTyyppi
   "typ av kök"             :keittionTyyppi
   "kitchen type"           :keittionTyyppi
   "huoneistoala"           :huoneistoala
   "huoneistoala m2"        :huoneistoala
   "lägenhetsyta"           :huoneistoala
   "floor area"             :huoneistoala
   "varusteena wc"          :WCKytkin
   "wc"                     :WCKytkin
   "toilet"                 :WCKytkin
   "varusteena amme/suihku" :ammeTaiSuihkuKytkin
   "amme/ suihku"           :ammeTaiSuihkuKytkin
   "badkar/dusch"           :ammeTaiSuihkuKytkin
   "bath tub/shower"        :ammeTaiSuihkuKytkin
   "varusteena parveke"     :parvekeTaiTerassiKytkin
   "parveke/ terassi"       :parvekeTaiTerassiKytkin
   "balkong/ terass"        :parvekeTaiTerassiKytkin
   "balcony/terrace"        :parvekeTaiTerassiKytkin
   "varusteena sauna"       :saunaKytkin
   "sauna"                  :saunaKytkin
   "bastu"                  :saunaKytkin
   "varusteena lämmin vesi" :lamminvesiKytkin
   "lämminvesi"             :lamminvesiKytkin
   "lämmin vesi"            :lamminvesiKytkin
   "varmvatten"             :lamminvesiKytkin
   "warm water"             :lamminvesiKytkin})

(defn- header-data-map [vecs]
  {:header-row (mapv #(.toLowerCase %) (first vecs))
   :data       (rest vecs)})

(defn split-with-semicolon [row]
  (map #(ss/split % #";") row))

(defn- pick-only-header-and-data-rows [rows]
  (drop-while #(not (some #{"Porras"} %)) rows))

(defn csv-data->ifc-coll [csv]
  (-> csv
      (ss/split #"\n")
      (split-with-semicolon)
      (pick-only-header-and-data-rows)
      (header-data-map)))

(defn item->update [premises-number [ifc-key ifc-val]]
  (let [lp-val-keys #{:WCKytkin :huoneistoTyyppi :keittionTyyppi :ammeTaiSuihkuKytkin
                      :saunaKytkin :lamminvesiKytkin :parvekeTaiTerassiKytkin}
        lp-key (-> ifc-key localized-ifc-keys)
        lp-val (cond
                 (lp-val-keys lp-key) (-> lp-key ifc->lp-val-map (get ifc-val))
                 (= :huoneistonumero lp-key) (->> ifc-val ss/remove-leading-zeros util/->int (format "%03d"))
                 (= :huoneistoala lp-key) (ss/replace ifc-val #"," ".")
                 :else ifc-val)]
    (when (and (not (empty? ifc-val)) lp-key)
      [[:huoneistot (-> premises-number str keyword) lp-key] lp-val])))

(defn premise->updates [premise premises-number]
  (remove nil? (map #(item->update premises-number %) premise)))

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
    (doc-persistence/remove-document-data command doc paths "documents")))

(defn save-premises-data [model-updates {application :application timestamp :created {role :role} :user :as command} doc-id]
  (let [document                                         (doc-persistence/by-id application "documents" doc-id)
        update-paths                                     (map first model-updates)
        {:keys [mongo-query mongo-updates post-results]} (apply doc-persistence/validated-model-updates application "documents" document
                                                                (doc-persistence/transform document model-updates) timestamp nil)]
    (when-not document (fail! :error.document-not-found))
    (doc-persistence/validate-against-whitelist! document update-paths role application)
    (doc-persistence/validate-readonly-updates! document update-paths)
    (remove-old-premises command doc-id)
    (action/update-application command mongo-query mongo-updates)
    (ok :results post-results)))

(defn upload-premises-data [{user :user application :application created :created :as command} files doc]
  (let [app-id                    (:id application)
        csv-data                  (-> files (first) (xmc/xls-2-csv))
        {:keys [header-row data]} (when (:data csv-data) (-> csv-data :data (csv-data->ifc-coll)))
        premises-data             (when (and header-row data)
                                    (data->model-updates header-row data))
        file-updated?             (when-not (empty? premises-data)
                                    (-> premises-data (save-premises-data command doc) :ok))
        save-response             (when file-updated?
                                    (timbre/info "Premises updated by premises Excel file in application")
                                    (->> (first files)
                                         ((fn [file] {:filename (:filename file) :content (:tempfile file)}))
                                         (file-upload/save-file)))
        file-linked?              (when (:fileId save-response)
                                    (= 1 (att/link-files-to-application app-id [(:fileId save-response)])))
        return-map                (cond
                                    file-updated? {:ok true}
                                    (nil? csv-data) {:ok false :text "error.illegal-premises-excel-file"}
                                    (empty? premises-data) {:ok false :text "error.illegal-premises-excel-data"}
                                    :else {:ok false})]
    (when file-linked?
      (let [old-ifc-fileId      (-> application :ifc-data :fileId)]
        (action/update-application command {$set {:ifc-data {:fileId   (:fileId save-response)
                                                             :filename (:filename save-response)
                                                             :modified created
                                                             :user     (usr/summary user)}}})
        (when old-ifc-fileId (mongo/delete-file-by-id old-ifc-fileId))))
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
                        "ei tiedossa"  ""}]
    (get keittio-values tyyppi)))

(defn- get-huoneisto [tyyppi]
  (let [huoneisto-values {"asuinhuoneisto" "1"
                          "toimitila"      "2"
                          "ei tiedossa"    ""}]
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
  (let [application        (domain/get-application-as app-id user :include-canceled-apps? false)
        data               (->> doc-id (domain/get-document-by-id application) :data :huoneistot (vals))
        sheet-name         (i18n/localize lang "huoneistot.excel-sheet-name")
        kitchen-info       [(i18n/localize lang "huoneistot.excel-kitchen-info-box")]
        premises-info      [(i18n/localize lang "huoneistot.excel-premises-info-box")]
        localized-labels   (->> (map #(.toLowerCase %) (cons "huoneiston tyyppi" ifc-labels))
                                (map #(get localized-ifc-keys %))
                                (remove nil?)
                                (map #(i18n/localize lang (str "huoneistot." (name %)))))
        header-info        [kitchen-info premises-info localized-labels]
        row-fn             (juxt #(-> % :huoneistoTyyppi :value (get-huoneisto))
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
