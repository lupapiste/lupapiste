(ns lupapalvelu.pdf.pdf-export-api
  (:require [lupapalvelu.action :as action :refer [defraw]]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.document.rakennuslupa-canonical :refer [fix-legacy-apartments]]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.pdf.libreoffice-template-history :as history]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [ring.util.response :as response]
            [sade.files :as files]))

(defn ok [body]
  {:status  200
   :headers {"Content-Type" "application/pdf"}
   :body    body})

(def not-found
  {:status 404
   :headers {"Content-Type" "text/plain"}
   :body "404"})

(defraw pdf-export
  {:parameters       [:id]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/all-org-authz-roles
   :states           states/all-states
   :pre-checks       [permit/is-not-archiving-project]}
  [{:keys [user application lang] :as command}]
  (if application
    (ok (-> application
            (app-utils/with-masked-person-ids user)
            fix-legacy-apartments
            (assoc ::pdf-export/schema-flags (tools/resolve-schema-flags command))
            (pdf-export/generate lang)))
    not-found))

(defraw submitted-application-pdf-export
  {:parameters       [id]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/all-org-authz-roles
   :states           states/all-states}
  [{:keys [user lang]}]
  (if-let [application-pdf (pdf-export/export-submitted-application user lang id)]
    (ok application-pdf)
    not-found))

(defraw bulletin-pdf-export
  {:parameters       [bulletinId]
   :user-roles       #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:bulletinId])]
   :pre-checks       [bulletins/bulletin-is-accessible]
   :states           states/all-states}
  [{:keys [lang user] :as command}]
  (if-let [version (bulletins/bulletin-version-for-user command (bulletins/get-bulletin bulletinId {}))]
    (ok (-> version (app-utils/with-masked-person-ids user) (pdf-export/generate lang)))
    not-found))

(defraw pdfa-casefile
  {:parameters       [:id]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/all-org-authz-roles
   :input-validators [(partial action/non-blank-parameters [:lang])]
   :states           states/all-states}
  [{:keys [application lang]}]
  (if application
    (files/with-temp-file libre-file
      (history/write-history-libre-doc application lang libre-file)
      (-> (laundry-client/convert-libre-template-to-pdfa-stream libre-file)
          response/response
          (response/content-type "application/pdf")))
    not-found))
