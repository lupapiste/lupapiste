(ns lupapalvelu.premises-api
  (:require [lupapalvelu.action :refer [defraw] :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.premises :as premises]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [lupapalvelu.xls-muuntaja-client :as xmc]
            [monger.operators :refer [$set]]
            [noir.response :as resp]
            [sade.core :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [swiss.arrows :refer :all]
            [taoensso.timbre :as timbre]
            [lupapalvelu.domain :as domain]))

;;
;;  Validators and pre-checks
;;

(defn- primary-operation-pre-check [{:keys [application]}]
  (when (not (= "kerrostalo-rivitalo" (-> application :primaryOperation :name)))
    (fail :error.illegal-primary-operation)))

(defn- file-size-positive [{{files :files} :data}]
  (when (not (pos? (-> files (first) :size)))
    (fail :error.select-file)))

(defn- validate-mime-type [{{files :files} :data}]
  (when-not (-> files (first) :filename (mime/allowed-file?))
    (fail :error.file-upload.illegal-file-type)))

(defraw upload-premises-data
  {:user-roles       #{:applicant :authority :oirAuthority}
   :parameters       [doc id files]
   :user-authz-roles (conj roles/default-authz-writer-roles :foreman)
   :pre-checks       [(action/some-pre-check att/allowed-only-for-authority-when-application-sent
                                             (permit/validate-permit-type-is :R))
                      primary-operation-pre-check
                      action/disallow-impersonation]
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      validate-mime-type
                      file-size-positive
                      file-upload/file-size-legal]
   :states           {:applicant    states/pre-verdict-states
                      :authority    states/pre-verdict-states
                      :oirAuthority states/pre-verdict-states}
   :feature          :premises-upload}
  [{user :user application :application created :created :as command}]
  (let [app-id        (:id application)
        premises-data (-> files (first) (xmc/xls-2-csv) :data (premises/csv-data->ifc-coll))
        file-updated? (when-not (empty? premises-data)
                        (-> premises-data (premises/save-premises-data command doc) :ok))
        save-response (when file-updated?
                        (timbre/info "Premises updated by premises Excel file in application")
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
      (let [old-ifc-fileId      (-> application :ifc-data :fileId)]
        (action/update-application command {$set {:ifc-data  {:fileId    (:fileId save-response)
                                                                      :filename  (:filename save-response)
                                                                      :modified  created
                                                                      :user      (usr/summary user)}}})
        (when old-ifc-fileId (mongo/delete-file-by-id old-ifc-fileId))))
    (->> return-map
         (resp/json)
         (resp/status 200))))
