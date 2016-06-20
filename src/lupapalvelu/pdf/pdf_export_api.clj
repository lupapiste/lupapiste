(ns lupapalvelu.pdf.pdf-export-api
  (:require [lupapalvelu.action :refer [defraw]]
            [lupapalvelu.application :as a]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.states :as states]
            [lupapalvelu.application-bulletins-api :as bulletins-api]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pdf.libreoffice-conversion-client :as libre]
            [lupapalvelu.action :as action]))

(defraw pdf-export
  {:parameters [:id]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/all-org-authz-roles
   :states     states/all-states}
  [{:keys [user application lang]}]
  (if application
    {:status 200
     :headers {"Content-Type" "application/pdf"}
     :body (-> application (a/with-masked-person-ids user) (pdf-export/generate lang))}
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "404"}))

(defraw bulletin-pdf-export
  {:parameters [bulletinId]
   :user-roles #{:anonymous}
   :input-validators [bulletins-api/bulletin-exists]
   :states     states/all-states}
  [{:keys [lang user]}]
  (if-let [bulletin (bulletins/get-bulletin bulletinId {})]
    {:status 200
     :headers {"Content-Type" "application/pdf"}
     :body (-> (last (:versions bulletin)) (a/with-masked-person-ids user) (pdf-export/generate lang))}
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "404"}))

(defraw pdfa-casefile
  {:parameters       [:id]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/all-org-authz-roles
   :input-validators [(partial action/non-blank-parameters [:lang])]
   :states           states/all-states}
  [{:keys [user application lang]}]
  (if application
    {:status  200
     :headers {"Content-Type" "application/pdf"}
           :body (libre/generate-casefile-pdfa application lang)}
    {:status  404
     :headers {"Content-Type" "text/plain"}
     :body    "404"}))
