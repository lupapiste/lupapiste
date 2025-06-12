(ns lupapalvelu.pdf.pdf-export-api
  (:require [lupapalvelu.action :as action :refer [defraw]]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.document.rakennuslupa-canonical :refer [fix-legacy-apartments]]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.pageutil :refer [try-again-page]]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.pdf.html-templates.foreman-info-template :as foreman-info-template]
            [lupapalvelu.pdf.libreoffice-template-history :as history]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [ring.util.response :as response]
            [sade.files :as files]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

(defn ok [body]
  {:status  200
   :headers {"Content-Type" "application/pdf"}
   :body    body})

(def not-found
  {:status 404
   :headers {"Content-Type" "text/plain"}
   :body "404"})

(defn foreman-pdf-response [{:keys [pdf-file-stream text]} command api-endpoint]
  (if pdf-file-stream
        (ok pdf-file-stream)
        (try-again-page command {:raw    api-endpoint
                                 :status 503
                                 :error  text})))

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
            app/fix-default-rakennusoikeusValinta-app
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
  (let [{:keys [category] :as version} (bulletins/bulletin-version-for-user command (bulletins/get-bulletin bulletinId {}))]
    (cond
      (nil? version)        not-found
      (not= "ymp" category) not-found
      :else                 (ok (-> version (app-utils/with-masked-person-ids user) (pdf-export/generate lang))))))

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

(defraw pdf-foreman
  {:description    "Generates a pdf of one foreman document, the foremans person-id will always be masked"
   :categories     #{:documents}
   :request-schema {:id ssc/ApplicationId :foreman-doc-id ssc/ObjectIdStr}
   :permissions    [{:required [:application/read]}]
   :pre-checks     [foreman/foreman-doc-id-is-foreman-document
                    foreman/foreman-document-is-initiated
                    (action/some-pre-check usr/validate-authority
                                           foreman/is-foreman-user?)]}
  [{:keys [application lang data system] :as command}]
  (let [html->pdf (partial html-pdf/html->pdf system)]
    (if-let [foreman-doc (domain/get-document-by-id application (:foreman-doc-id data))]
      (-> (app-utils/mask-person-id-partially foreman-doc)
          (foreman-info-template/single-foreman-html application lang)
          html->pdf
          (foreman-pdf-response command :pdf-foreman))
      not-found)))

(defraw pdf-foreman-list
  {:description    "Generates a pdf of all initiated foreman documents. Here we do not need to mask any person-ids,
   since this document is not visible in the attachment list of the app, it is only for archiving purposes."
   :categories     #{:documents}
   :request-schema {:id ssc/ApplicationId :lang sc/Str}
   :permissions    [{:required [:application/archive]}]
   :pre-checks     [foreman/application-has-initiated-foreman]}
  [{:keys [application lang system] :as command}]
  (let [html->pdf (partial html-pdf/html->pdf system)]
    (-> (foreman/get-all-initiated-foreman-documents application)
        (foreman-info-template/foreman-list-html application lang)
        html->pdf
        (foreman-pdf-response command :pdf-foreman-list))))
