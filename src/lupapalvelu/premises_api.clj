(ns lupapalvelu.premises-api
  (:require [sade.core :as sc]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.file-upload-api :as file-upload-api]
            [lupapalvelu.xls-muuntaja-client :as xmc]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.states :as states]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.action :refer [defraw defcommand] :as action]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.mongo :as mongo]
            [swiss.arrows :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [lupapalvelu.document.persistence :as doc-persistence]
            [noir.response :as resp]
            [dk.ative.docjure.spreadsheet :as spreadsheet]
            [lupapalvelu.reports.excel :as excel]
            [taoensso.timbre :as timbre]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$set]]))

(defn get-huoneistot-doc [application]
  (->> (:documents application)
       (filter #(= "uusiRakennus" (-> % :schema-info :name)))
       (first)))

;;
;;  IFC-model apartment data from Excel file
;;

(def ifc-to-lupapiste-keys
  {"porras"                 {:key "porras"}
   "huoneistonumero"        {:key "huoneistonumero"}
   "huoneiston jakokirjain" {:key "jakokirjain"}
;  "sijaintikerros"         {:key "sijaintikerros"} ;; ei skeemassa
   "huoneiden lukumäärä"    {:key "huoneluku"}
   "keittiötyyppi"          {:key    "keittionTyyppi"
                             :values {"1" "keittio"
                                      "2" "keittokomero"
                                      "3" "keittotila"
                                      "4" "tupakaittio"
                                      ""  "ei tiedossa"}}
   "huoneistoala"           {:key "huoneistoala"}
   "varusteena wc"          {:key    "WCKytkin"
                             :values {"1" true
                                      "0" false}}
   "varusteena amme/suihku" {:key    "ammeTaiSuihkuKytkin"
                             :values {"1" true
                                      "0" false}}
   "varusteena parveke"     {:key    "parvekeTaiTerassiKytkin"
                             :values {"1" true
                                      "0" false}}
   "varusteena sauna"       {:key    "saunaKytkin"
                             :values {"1" true
                                      "0" false}}
   "varusteena lämmin vesi" {:key    "lamminvesiKytkin"
                             :values {"1" true
                                      "0" false}}})

(defn header-pairing-with-cells [vecs]
  (let [headers (map #(.toLowerCase %) (first vecs))
        data (rest vecs)]
    (map #(zipmap headers %) data)))

(defn split-with-semicolon [row]
  (map #(ss/split % #";") row))

(defn csv-data->ifc-coll [csv]
  (-> csv
      (ss/split #"\n")
      (split-with-semicolon)
      (header-pairing-with-cells)))

(defn item->update [premises-number [ifc-key ifc-val]]
  (let [lp-key (-> ifc-key ifc-to-lupapiste-keys :key)
        lp-val (-> ifc-key ifc-to-lupapiste-keys :values)]
    (when (and (not (empty? ifc-val)) lp-key)
      [(ss/join "." ["huoneistot" premises-number lp-key])
       (if (map? lp-val) (-> ifc-val lp-val) (ss/replace ifc-val #"," "."))])))

(defn premise->updates [premise premises-number]
  (remove nil? (map #(item->update premises-number %) premise)))

(defn remove-old-premises [pseudo-command doc]
  (let [app-id (-> pseudo-command :application :id)
        paths (->> (mongo/by-id :applications app-id)
                   :documents
                   (filter #(= "uusiRakennus" (-> % :schema-info :name)))
                   (first)
                   :data
                   :huoneistot
                   (keys)
                   (map (fn [huoneisto-key] [:huoneistot huoneisto-key])))]
    (doc-persistence/remove-document-data pseudo-command doc paths "documents")))

(defn save-premises-data [premise-data applicationId timestamp user doc]
  (let [application    (mongo/by-id :applications applicationId)
        pseudo-command {:application application
                        :data        {:id applicationId}
                        :created     timestamp
                        :user        user}
        updates         (reduce #(concat %1 (premise->updates (nth premise-data %2) %2)) [] (range (count premise-data)))
        remove-old?     (> (count (-> application (get-huoneistot-doc) :data :huoneistot)) 1)
        remove-result   (when remove-old? (remove-old-premises pseudo-command doc))]
    (doc-persistence/update! pseudo-command doc updates "documents")))

(defn- primary-operation-pre-check [{:keys [application]}]
  (when (not (= "kerrostalo-rivitalo" (-> application :primaryOperation :name)))
    (sc/fail :error.illegal-primary-operation)))

(defn- file-size-positive [{{files :files} :data}]
  (when (not (pos? (-> files (first) :size)))
    (sc/fail :error.select-file)))

(defn- validate-mime-type [{{files :files} :data}]
  (when-not (-> files (first) :filename (mime/allowed-file?))
    (sc/fail :error.file-upload.illegal-file-type)))

(defraw upload-premises-data
  {:user-roles       #{:applicant :authority :oirAuthority}
   :parameters       [doc id files]
   :user-authz-roles (conj roles/all-authz-writer-roles :foreman)
   :pre-checks       [(action/some-pre-check att/allowed-only-for-authority-when-application-sent
                                             (permit/validate-permit-type-is :R))
                      primary-operation-pre-check
                      action/disallow-impersonation]
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      validate-mime-type
                      file-size-positive
                      file-upload-api/file-size-legal]
   :states           {:applicant    states/pre-verdict-states
                      :authority    states/pre-verdict-states
                      :oirAuthority states/pre-verdict-states}}
  [{user :user application :application}]
  (let [timestamp           (sc/now)
        app-id              (:id application)
        premises-csv        (-> files (first) (xmc/xls-2-csv))
        premises-data       (when-not (empty? premises-csv) (-> premises-csv :data csv-data->ifc-coll))
        file-updated?       (when-not (nil? premises-data)
                              (-> premises-data
                                  (save-premises-data app-id timestamp user doc)
                                  :ok))
        save-response       (when file-updated? (->> (first files)
                                                     ((fn [file] {:filename (:filename file) :content (:tempfile file)}))
                                                     (file-upload/save-file)))
        file-linked?        (= 1 (att/link-files-to-application app-id [(:fileId save-response)]))
        return-map          (cond
                              file-updated? {:ok true :filename (:filename save-response)}
                              (empty? premises-csv) {:ok false :text "error.illegal-premises-excel"}
                              :else {:ok false})]
    (when file-linked?
      (do
        (timbre/info "Successfully linked premises xlsx" (:filename save-response) "to application" app-id)
        (mongo/update-by-id :applications
                            app-id
                            {$set {:ifc-data {:fileId   (:fileId save-response)
                                              :filename  (:filename save-response)
                                              :timestamp timestamp
                                              :user      (usr/summary user)}}})))
    (->> return-map
         (resp/json)
         (resp/status 200))))

;;
;;
;;

(def headers ["Porras" "Huoneistonumero" "Huoneiston jakokirjain" ;"Sijaintikerros"
              "Huoneiden lukumäärä" "Keittiötyyppi" "Huoneistoala" "Varusteena WC"
              "Varusteena amme/suihku" "Varusteena parveke" "Varusteena Sauna"
              "Varusteena lämmin vesi"])

(def lupapiste-to-ifc-keys
  {:porras                  {:key "Porras"}
   :huoneistonumero         {:key "Huoneistonumero"}
   :jakokirjain             {:key "Huoneiston jakokirjain"}
   :sijaintikerros          {:key "Sijaintikerros"}
   :huoneluku               {:key "Huoneiden lukumäärä"}
   :keittionTyyppi          {:key    "Keittiötyyppi"
                              :values {"keittio"      "1"
                                       "keittokomero" "2"
                                       "keittotila"   "3"
                                       "tupakaittio"  "4"
                                       "ei tiedossa"  ""}}
   :huoneistoala            {:key "Huoneistoala"}
   :WCKytkin                {:key    "Varusteena WC"
                              :values {true  "1"
                                       false "0"}}
   :ammeTaiSuihkuKytkin     {:key    "Varusteena amme/suihku"
                              :values {true  "1"
                                       false "0"}}
   :parvekeTaiTerassiKytkin {:key    "Varusteena parveke"
                              :values {true  "1"
                                       false "0"}}
   :saunaKytkin             {:key    "Varusteena Sauna"
                              :values {true  "1"
                                       false "0"}}
   :lamminvesiKytkin        {:key    "Varusteena lämmin vesi"
                              :values {true  "1"
                                       false "0"}}})

(defn ifc->lp-key [ifc-key]
  (-> ifc-key ifc-to-lupapiste-keys :key (keyword)))

(defn resolve-ifc-val [item]
  (let [values (-> item lupapiste-to-ifc-keys :values)]
    (if (map? values)
      (-> item values)
      item)))

(defn premises-to-row [premise]
  (let [ifc-value (fn [ifc-key] (-> ifc-key ifc->lp-key premise resolve-ifc-val))]
    (mapv ifc-value headers)))

(defn excel-response [filename body]
  (try
    {:status  200
     :headers {"Content-Type"        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
               "Content-Disposition" (str "attachment;filename=\"" filename "\"")}
     :body    body}
    (catch Exception e#
      (timbre/error "Exception while compiling premises data excel:" e#)
      {:status 500})))

(defraw download-premises-data
  {:parameters       [applicationId user]
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles       #{:applicant}}
  []
  (println user applicationId)
  (when-not user (throw+ {:status 401 :body "forbidden"}))
  (let [premises (->> (:documents (mongo/by-id :applications applicationId))
                      (filter #(= "uusiRakennus" (-> % :schema-info :name)))
                      :data
                      :huoneistot)
        premises-data (cons headers (map premises-to-row premises))
        workbook (spreadsheet/create-workbook "huoneistot" premises-data)]
    (excel-response (str applicationId "-huoneistot.xlsx")
                    (excel/xlsx-stream workbook))))
