(ns lupapalvelu.premises
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [lupapalvelu.xls-muuntaja-client :as xmc]
            [monger.operators :refer [$set]]
            [noir.response :as resp]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [slingshot.slingshot :refer [throw+]]
            [swiss.arrows :refer :all]
            [taoensso.timbre :as timbre]))

(defn get-huoneistot-from-application [doc-id application]
  (-> (util/find-by-id doc-id (:documents application)) :data :huoneistot))

;;
;;  IFC-model apartment data from Excel file
;;

(def ifc-to-lupapiste-keys
  {"porras"                             {:key "porras"}
   "huoneistonumero"                    {:key "huoneistonumero"}
   "huoneiston jakokirjain"             {:key "jakokirjain"}
;  "sijaintikerros"                     {:key "sijaintikerros"} ;; ei skeemassa
   "huoneiden lukum\u00e4\u00e4r\u00e4" {:key "huoneluku"}
   "keitti\u00f6tyyppi"                 {:key    "keittionTyyppi"
                                         :values {"1" "keittio"
                                                  "2" "keittokomero"
                                                  "3" "keittotila"
                                                  "4" "tupakaittio"
                                                  ""  "ei tiedossa"}}
   "huoneistoala"                       {:key "huoneistoala"}
   "varusteena wc"                      {:key    "WCKytkin"
                                         :values {"1" true
                                                  "0" false}}
   "varusteena amme/suihku"             {:key    "ammeTaiSuihkuKytkin"
                                         :values {"1" true
                                                  "0" false}}
   "varusteena parveke"                 {:key    "parvekeTaiTerassiKytkin"
                                         :values {"1" true
                                                  "0" false}}
   "varusteena sauna"                   {:key    "saunaKytkin"
                                         :values {"1" true
                                                  "0" false}}
   "varusteena l\u00e4mmin vesi"        {:key    "lamminvesiKytkin"
                                         :values {"1" true
                                                  "0" false}}})

(defn validate-header [header]
  (let [ifc-headers (set (cons "sijaintikerros" (keys ifc-to-lupapiste-keys)))]
    (= (set header) ifc-headers)))

(defn- header-data-map [vecs]
  {:header-row (mapv #(.toLowerCase %) (first vecs))
   :data       (rest vecs)})

(defn split-with-semicolon [row]
  (map #(ss/split % #";") row))

(defn- pick-only-header-and-data-rows [rows]
  (drop-while #(not (ss/starts-with % "Porras")) rows))

(defn csv-data->ifc-coll [csv]
  (-> csv
      (ss/split #"\n")
      (pick-only-header-and-data-rows)
      (split-with-semicolon)
      (header-data-map)))

(defn item->update [premises-number [ifc-key ifc-val]]
  (let [lp-key (-> ifc-key ifc-to-lupapiste-keys :key)
        lp-val (-> ifc-key ifc-to-lupapiste-keys :values)]
    (when (and (not (empty? ifc-val)) lp-key)
      [(mapv keyword ["huoneistot" (str premises-number) lp-key])
       (if (map? lp-val) (-> ifc-val lp-val) (ss/replace ifc-val #"," "."))])))

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
    (doc-persistence/validate-against-whitelist! document update-paths role)
    (doc-persistence/validate-readonly-updates! document update-paths)
    (remove-old-premises command doc-id)
    (action/update-application command mongo-query mongo-updates)
    (ok :results post-results)))

(defn upload-premises-data [{user :user application :application created :created :as command} files doc]
  (let [app-id                    (:id application)
        {:keys [header-row data]} (-> files (first) (xmc/xls-2-csv) :data (csv-data->ifc-coll))
        premises-data             (when (validate-header header-row)
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
                                    (empty? premises-data) {:ok false :text "error.illegal-premises-excel"}
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
