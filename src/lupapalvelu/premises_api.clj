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
            [lupapalvelu.action :refer [defraw] :as action]
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
  [{:keys [applicationId doc user]}]
        (println "PLINK PLONK")
        (println id file filename applicationId doc user)
        (resp/status 200 (resp/json {:plink "plonk"}))
  #_(let [timestamp     (sc/now)
        premises-data (-> file (xmc/xls-2-csv) :data (csv-data->ifc-coll))
        file-updated? (some-> premises-data
                              (save-premises-data applicationId timestamp user doc)
                              :ok)
        save-response (when file-updated? (-> {:filename filename :content file}
                                              (file-upload/save-file)))]
    (when save-response (att/link-files-to-application applicationId [(:file-id save-response)]))
    (when file-updated? (mongo/update-by-id :applications
                                            applicationId
                                            {:ifc-data {:file-id (:file-id save-response)
                                                        :filename (:filename save-response)
                                                        :timestamp timestamp}}))
    (->> {:filename (:filename save-response)}
         (resp/json)
         (resp/status (if file-updated? 200 500)))))

(def headers ["Porras" "Huoneistonumero" "Huoneiston jakokirjain" "Sijaintikerros"
              "Huoneiden lukumäärä" "Keittiötyyppi" "Huoneistoala" "Varusteena WC"
              "Varusteena amme/suihku" "Varusteena parveke" "Varusteena Sauna"
              "Varusteena lämmin vesi"])

(def lupapiste-to-ifc-keys
  {"porras"                 {:key "Porras"}
   "huoneistonumero"        {:key "Huoneistonumero"}
   "jakokirjain"            {:key "Huoneiston jakokirjain"}
   "sijaintikerros"         {:key "Sijaintikerros"}
   "huoneluku"              {:key "Huoneiden lukumäärä"}
   "keittionTyyppi"         {:key "Keittiötyyppi"
                             :values {"keittio"      "1"
                                      "keittokomero" "2"
                                      "keittotila"   "3"
                                      "tupakaittio"  "4"
                                      "ei tiedossa"  ""}}
   "huoneistoala"           {:key "Huoneistoala"}
   "WCKytkin"               {:key "Varusteena WC"
                             :values {true  "1"
                                      false "0"}}
   "ammeTaiSuihkuKytkin"    {:key "Varusteena amme/suihku"
                             :values {true  "1"
                                      false "0"}}
   "parvekeTaiTerassiKytkin"{:key "Varusteena parveke"
                             :values {true  "1"
                                      false "0"}}
   "saunaKytkin"             {:key "Varusteena Sauna"
                             :values {true  "1"
                                      false "0"}}
   "lamminvesiKytkin"       {:key "Varusteena lämmin vesi"
                             :values {true  "1"
                                      false "0"}}})

(defn ifc->lp-key [ifc-key]
  (-> ifc-key ifc-to-lupapiste-keys :key (keyword)))

(defn resolve-ifc-val [item]
  (let [values (-> item lupapiste-to-ifc-keys :values)]
    (if (map? values)
      (-> item values)
      item)))

(defn premise-to-row [premise]
  (let [ifc-value (fn [ifc-key] (-> ifc-key
                                    ifc->lp-key
                                    premise
                                    #(if (-> ifc-key ifc-to-lupapiste-keys :values)
                                       )))]

(defraw download-premises-data
  {:parameters [application-id]
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles #{:applicant}}
  [{user :user}]
  (when-not user (throw+ {:status 401 :body "forbidden"}))
  (let [premises (->> (:documents (mongo/by-id :applications application-id))
                      (filter #(= "uusiRakennus" (-> % :schema-info :name)))
                      :data
                      :huoneistot)
        ]
    {:status 200
     :body ((:content attachment))
     :headers {"Content-Type" (:contentType attachment)
               "Content-Length" (str (:size attachment))
               "Content-Disposition" (format "attachment;filename=\"%s\"" (ss/encode-filename (:filename attachment)))}}
    {:status 404
     :body (str "Attachment not found: id=" attachment-id)}))