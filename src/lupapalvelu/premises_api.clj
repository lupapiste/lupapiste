(ns lupapalvelu.premises-api
  (:require [sade.core :as sc]
            [sade.strings :as ss]
            [lupapalvelu.file-upload-api :as file-upload-api]
            [lupapalvelu.xls-muuntaja-client :as xmc]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.states :as states]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.action :refer [defraw] :as action]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.mongo :as mongo]
            [swiss.arrows :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [lupapalvelu.document.persistence :as doc-persistence]
            [noir.response :as resp]
            [taoensso.timbre :as timbre]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$set]]))

(defn get-huoneistot-doc [application]
  (->> (:documents application)
       (filter #(= "uusiRakennus" (-> % :schema-info :name)))
       (first)))

;;
;;  Validators and pre-checks
;;

(defn- primary-operation-pre-check [{:keys [application]}]
  (when (not (= "kerrostalo-rivitalo" (-> application :primaryOperation :name)))
    (sc/fail :error.illegal-primary-operation)))

(defn- file-size-positive [{{files :files} :data}]
  (when (not (pos? (-> files (first) :size)))
    (sc/fail :error.select-file)))

(defn- validate-mime-type [{{files :files} :data}]
  (when-not (-> files (first) :filename (mime/allowed-file?))
    (sc/fail :error.file-upload.illegal-file-type)))

;;
;;  IFC-model apartment data from Excel file
;;

(def ifc-to-lupapiste-keys
  {"porras"                 {:key "porras"}
   "huoneistonumero"        {:key "huoneistonumero"}
   "huoneiston jakokirjain" {:key "jakokirjain"}
;  "sijaintikerros"         {:key "sijaintikerros"} ;; ei skeemassa, ei käytetä
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

(defn- header-pairing-with-cells [vecs]
  (let [headers (map #(.toLowerCase %) (first vecs))
        data (rest vecs)]
    (map #(zipmap headers %) data)))

(defn- split-with-semicolon [row]
  (map #(ss/split % #";") row))

(defn- pick-only-header-and-data-rows [rows]
  (drop-while #(not (clojure.string/starts-with? % "Porras")) rows))

(defn- csv-data->ifc-coll [csv]
  (-> csv
      (ss/split #"\n")
      (pick-only-header-and-data-rows)
      (split-with-semicolon)
      (header-pairing-with-cells)))

(defn- item->update [premises-number [ifc-key ifc-val]]
  (let [lp-key (-> ifc-key ifc-to-lupapiste-keys :key)
        lp-val (-> ifc-key ifc-to-lupapiste-keys :values)]
    (when (and (not (empty? ifc-val)) lp-key)
      [(ss/join "." ["huoneistot" premises-number lp-key])
       (if (map? lp-val) (-> ifc-val lp-val) (ss/replace ifc-val #"," "."))])))

(defn- premise->updates [premise premises-number]
  (remove nil? (map #(item->update premises-number %) premise)))

(defn- remove-old-premises [pseudo-command doc]
  (let [app-id (-> pseudo-command :application :id)
        paths (->> (mongo/by-id :applications app-id)
                   (get-huoneistot-doc)
                   :data
                   :huoneistot
                   (keys)
                   (map (fn [huoneisto-key] [:huoneistot huoneisto-key])))]
    (doc-persistence/remove-document-data pseudo-command doc paths "documents")))

(defn- save-premises-data [premise-data applicationId timestamp user doc]
  (let [application    (mongo/by-id :applications applicationId)
        pseudo-command {:application application
                        :data        {:id applicationId}
                        :created     timestamp
                        :user        user}
        updates        (reduce #(concat %1 (premise->updates (nth premise-data %2) %2))
                               []
                               (range (count premise-data)))]
    (remove-old-premises pseudo-command doc)
    (doc-persistence/update! pseudo-command doc updates "documents")))

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
  (let [timestamp (sc/now)
        app-id        (:id application)
        premises-data (-> files (first) (xmc/xls-2-csv) :data (csv-data->ifc-coll))
        file-updated? (when-not (empty? premises-data)
                        (-> premises-data (save-premises-data app-id timestamp user doc) :ok))
        save-response (when file-updated?
                        (timbre/info "Premises updated by premises Excel file in application" app-id)
                        (->> (first files)
                             ((fn [file] {:filename (:filename file) :content (:tempfile file)}))
                             (file-upload/save-file)))
        file-linked?  (when (:fileId save-response)
                        (= 1 (att/link-files-to-application app-id [(:fileId save-response)])))
        return-map    (cond
                        file-updated? {:ok true}
                        (empty? premises-data) {:ok false :text "error.illegal-premises-excel"}
                        :else {:ok false})]
    (when file-linked?
      (mongo/update-by-id :applications
                          app-id
                          {$set {:ifc-data {:fileId    (:fileId save-response)
                                            :filename  (:filename save-response)
                                            :timestamp timestamp
                                            :user      (usr/summary user)}}}))
    (->> return-map
         (resp/json)
         (resp/status 200))))
