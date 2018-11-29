(ns lupapalvelu.premises-api
  (:require [clj-time.local :as local]
            [lupapalvelu.action :refer [defraw] :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.premises :as premises]
            [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [sade.shared-util :as util]
            [slingshot.slingshot :refer [throw+]]))

;;
;;  Validators and pre-checks
;;

(defn- uusiRakennus-pre-check [{:keys [application]}]
  (when-not (some #(= "uusiRakennus" (-> % :schema-info :name)) (:documents application))
    (fail :error.illegal-primary-operation)))

(defn- file-size-positive [{{files :files} :data}]
  (when (not (pos? (-> files (first) :size)))
    (fail :error.select-file)))

(defn- validate-mime-type [{{files :files} :data}]
  (when-not (-> files (first) :filename (mime/allowed-file?))
    (fail :error.file-upload.illegal-file-type)))

(defn- document-exists [{application :application {doc-id :doc} :data}]
  (let [document (util/find-by-id doc-id (:documents application))]
    (when-not document (fail :error.document-not-found))))

;;
;;  Upload
;;

(defraw upload-premises-data
  {:user-roles       #{:applicant :authority :oirAuthority}
   :parameters       [doc id files]
   :user-authz-roles (conj roles/default-authz-writer-roles :foreman)
   :pre-checks       [(action/some-pre-check att/allowed-only-for-authority-when-application-sent
                                             (permit/validate-permit-type-is :R))
                      uusiRakennus-pre-check
                      action/disallow-impersonation
                      document-exists]
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      validate-mime-type
                      file-size-positive
                      file-upload/file-size-legal]
   :states           {:applicant    states/pre-verdict-states
                      :authority    states/pre-verdict-states
                      :oirAuthority states/pre-verdict-states}}
  [command]
  (premises/upload-premises-data command files doc))

;;
;;  Template download
;;

(defraw download-premises-template
  {:user-roles       #{:applicant :authority :oirAuthority}
   :parameters       [application-id document-id lang]
   :user-authz-roles (conj roles/default-authz-writer-roles :foreman)
   :pre-checks       []
   :input-validators [(partial action/non-blank-parameters [:application-id :document-id])]}
  [{{:keys [application-id document-id lang]} :data user :user}]
  (let [filename (str (i18n/localize lang "huoneistot.excel-file-name")
                      "-"
                      (local/format-local-time (local/local-now) :basic-date)
                      ".xlsx")
        error-message "Exception while compiling premises excel file: "]
    (excel/excel-response
      filename
      (premises/download-premises-template user application-id document-id lang)
      error-message)))
