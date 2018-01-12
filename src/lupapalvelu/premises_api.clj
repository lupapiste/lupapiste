(ns lupapalvelu.premises-api
  (:require [lupapalvelu.action :refer [defraw defquery] :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.premises :as premises]
            [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [monger.operators :refer [$set]]
            [sade.core :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [swiss.arrows :refer :all]))

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
                      action/disallow-impersonation]
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      validate-mime-type
                      file-size-positive
                      file-upload/file-size-legal]
   :states           {:applicant    states/pre-verdict-states
                      :authority    states/pre-verdict-states
                      :oirAuthority states/pre-verdict-states}
   :feature          :premises-upload}
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
   :input-validators [(partial action/non-blank-parameters [:application-id :document-id])]
   :feature          :premises-upload}
  [{{:keys [application-id document-id lang]} :data user :user :as command}]
  (let [filename "huoneistotietotaulukko.xlsx"]
    (excel/excel-response
      filename
      (premises/download-premises-template user application-id document-id lang))))
