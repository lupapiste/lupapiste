(ns lupapalvelu.premises-api
  (:require [lupapalvelu.action :refer [defraw] :as action]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.premises :as premises]
            [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.util :as util]))

;;
;;  Validators and pre-checks
;;

(defn- uusiRakennus-pre-check [{:keys [application]}]
  (when-not (some #(#{"uusiRakennus" "uusiRakennuskohde"} (-> % :schema-info :name)) (:documents application))
    (fail :error.illegal-primary-operation)))

(defn- file-size-positive [{{files :files} :data}]
  (when (not (pos? (-> files (first) :size)))
    (fail :error.select-file)))

(defn- validate-mime-type [{{files :files} :data}]
  (when-not (-> files (first) :filename (mime/allowed-file?))
    (fail :error.file-upload.illegal-file-type)))

(defn- document-exists [{application :application {doc-id :doc} :data}]
  (when doc-id
    (let [document (util/find-by-id doc-id (:documents application))]
      (when-not document (fail :error.document-not-found)))))

;;
;;  Upload
;;

(defraw upload-premises-data
  {:user-roles           #{:applicant :authority :oirAuthority}
   :parameters           [doc id files]
   :user-authz-roles     (conj roles/default-authz-writer-roles :foreman)
   :pre-checks           [(permit/validate-permit-type-is :R)
                          uusiRakennus-pre-check
                          document-exists]
   :allow-impersonation? false
   :input-validators     [(partial action/non-blank-parameters [:id :doc])
                          validate-mime-type
                          file-size-positive
                          file-upload/file-size-legal]
   :states               {:applicant    states/pre-sent-application-states
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
  (let [{:keys [ryhti-data-2026]} (mongo/by-id :applications application-id [:ryhti-data-2026])
        filename                  (str (i18n/localize lang "huoneistot.excel-file-name")
                                       "-"
                                       (date/zoned-format (date/now) :basic-date)
                                       ".xlsx")
        error-message             "Exception while compiling premises excel file: "]
    (excel/excel-response
      filename
      (if ryhti-data-2026
       (premises/download-premises-template-ryhti-data-2026 user application-id document-id lang)
       (premises/download-premises-template user application-id document-id lang))
      error-message)))
