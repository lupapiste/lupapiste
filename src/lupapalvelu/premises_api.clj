(ns lupapalvelu.premises-api
  (:require [sade.core :as sc]
            [sade.strings :as ss]
            [lupapalvelu.file-upload-api :as file-upload-api]
            [lupapalvelu.attachment.muuntaja-client :as muuntaja]
            [lupapalvelu.xls-muuntaja-client :as xmc]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.application :as app]
            [lupapalvelu.states :as states]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.action :as action]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.mongo :as mongo]
            [swiss.arrows :refer :all]
            [lupapalvelu.document.persistence :as doc-persistence]
            [noir.response :as resp]))

;;
;;  IFC-model apartment data from Excel file
;;

(def ifc-to-lupapiste-keys
  {"porras"                 {:key "porras"}
   "huoneistonumero"        {:key "huoneistonumero"}
   "huoneiston jakokirjain" {:key "jakokirjain"}
   "sijaintikerros"         {:key "sijaintikerros"}
   "huoneiden lukumäärä"    {:key "huoneluku"}
   "keittiötyyppi"          {:key "keittionTyyppi"
                             :values {"1" "keittio"
                                      "2" "keittokomero"
                                      "3" "keittotila"
                                      "4" "tupakaittio"
                                      "" "ei tiedossa"}}
   "huoneistoala"           {:key "huoneistoala"}
   "varusteena WC"          {:key "WCKytkin"
                             :values {"1" true
                                      "0" false}}
   "varusteena amme/suihku" {:key "ammeTaiSuihkuKytkin"
                             :values {"1" true
                                      "0" false}}
   "varusteena parveke"     {:key "parvekeTaiTerassiKytkin"
                             :values {"1" true
                                      "0" false}}
   "varusteena Sauna"       {:key "saunaKytkin"
                             :values {"1" true
                                      "0" false}}
   "varusteena lämmin vesi" {:key "lamminvesiKytkin"
                             :values {"1" true
                                      "0" false}}})

(defn- csv-data->ifc-coll [csv]
  (-<> csv
       (ss/split <> #"\n")
       (map #(ss/split % #";") <>)
       (apply (fn [x] (map #(zipmap (first x) %) (rest x))) <>)))

(defn- item->update [premises-number [ifc-key ifc-val]]
  (let [lp-key (-> ifc-key ifc-to-lupapiste-keys :key)
        lp-val (-> ifc-key ifc-to-lupapiste-keys :values)]
    [(ss/join "." "huoneistot" premises-number lp-key)
     (if (map? lp-val) (-> ifc-val lp-val) ifc-val)]))

(defn- premise->updates [premise]
  (let [premises-number (-> "huoneistonumero" premise (Integer/parseInt))]
    (map #(item->update premises-number %) premise)))

(defn- save-premises-data [ifc-coll applicationId timestamp user doc]
  (let [pseudo-command {:application (mongo/by-id :applications applicationId)
                        :created     timestamp
                        :user        user}
        updates        (reduce #(concat %1 (premise->updates %2)) [] ifc-coll)]
    (doc-persistence/update! pseudo-command doc updates "documents")))

(defraw upload-premises-data
  {:parameters       [id file filename]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles (conj roles/all-authz-writer-roles :foreman)
   :pre-checks       [att/attachment-matches-application
                      att/upload-to-target-allowed
                      (action/some-pre-check att/allowed-only-for-authority-when-application-sent
                                             (permit/validate-permit-type-is :YI :YL :YM :VVVL :MAL))
                      app/validate-authority-in-drafts
                      att/validate-group]
   :input-validators [(partial action/non-blank-parameters [:id :filename])
                      (fn [{{size :size} :data}] (when-not (pos? size) (sc/fail :error.select-file)))
                      (fn [{{filename :filename} :data}] (when-not (mime/allowed-file? filename) (sc/fail :error.file-upload.illegal-file-type)))]
   :states           {:applicant    (conj (states/all-states-but states/terminal-states) :answered) ;; tsekkaa tämä
                      :authority    (states/all-states-but :canceled) ;; ja tämä
                      :oirAuthority (states/all-states-but :canceled)} ;; ja tämä
   :notified         true
   :on-success       []}
  [{:keys [applicationId documentId user]}]
  (let [timestamp     (sc/now)
        premises-data (-> file (xmc/xls-2-csv) :data (csv-data->ifc-coll))
        file-updated? (some-> premises-data
                              (save-premises-data applicationId timestamp user doc)
                              :ok)
        save-response (when file-updated? (-> {:filename filename :content file}
                                              (file-upload/save-file)))]
    (when file-updated? (mongo/update-by-id :applications
                                            applicationId
                                            {:ifc-data {:file-id (:file-id save-response)
                                                        :filename (:filename save-response)
                                                        :timestamp timestamp}}))
    (->> {:filename (:filename save-response)}
         (resp/json)
         (resp/status (if file-updated? 200 500)))))

