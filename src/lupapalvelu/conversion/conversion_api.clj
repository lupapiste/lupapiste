(ns lupapalvelu.conversion.conversion-api
  "Conversion-related action endpoints."
  (:require [lupapalvelu.action :refer [defraw defquery defcommand] :as action]
            [lupapalvelu.conversion.proxy :as conv-p]
            [lupapalvelu.conversion.section :as conv-sec]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.states :as states]
            [monger.operators :refer :all]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [sade.core :refer [ok fail]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc]))

(defn kuntagml-available
  "Pre-check that succeeds only if application is converted AND its KuntaGML message has
  been stored."
  [{:keys [application]}]
  (when-not (and (:facta-imported application)
                 (mongo/any? :conversion {:LP-id        (:id application)
                                          :kuntagml.xml {$exists true}}))
    (fail :error.not-found)))

(defraw conversion-message
  {:description      "Downloads the original conversion source KuntaGML, if available."
   :permissions      [{:required [:application/read-conversion-section]}]
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [kuntagml-available]
   :states           states/all-states}
  [_]
  {:status  200
   :headers {"Content-Type" "text/xml; charset=utf-8"}
   :body    (-> (mongo/select-one :conversion {:LP-id id})
                :kuntagml :xml)})

(defn sections-exist
  "Pre-check that succeeds only if application is converted AND its conversion document includes sections."
  [{:keys [application]}]
  (when-not (and (:facta-imported application)
                 (mongo/any? :conversion {:LP-id      (:id application)
                                          :sections.0 {$exists true}}))
    (fail :error.not-found)))

(defquery conversion-sections
  {:description      "The conversion section UI consists of multiple schema/data sections."
   :permissions      [{:required [:application/read-conversion-section]}]
   :parameters       [id]
   :feature          :conversion-section
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [sections-exist]}
  [_]
  (ok :sections (conv-sec/application-sections id)))

(defn section-exists
  "Pre-check that succeeds only if application is converted AND its conversion document
  includes the given section."
  [{:keys [application] :as command}]
  (let [q (conv-sec/section-query-or-nil command)]
    (cond
      (not (:facta-imported application))
      (fail :error.not-converted)

      (and q (not (mongo/any? :conversion q)))
      (fail :error.not-found))))

(defcommand edit-conversion-section
  {:description      "Updates section data."
   :permissions      [{:required [:application/edit-converted-application]}]
   :feature          :conversion-section
   :parameters       [id section-id path value]
   :input-validators [{:id         ssc/NonBlankStr
                       :section-id ssc/NonBlankStr
                       :path       [sc/Any]
                       :value      sc/Any}]
   :pre-checks       [section-exists]}
  [command]
  (let [result (conv-sec/edit-section (update command :data ss/trimwalk))]
    (if (:modified result)
      (ok result)
      (template/error-response result))))


;; Proxy endpoint for fetching a KuntaGML message for an arbitrary organization/backend-id
;; pair.
(defpage [:get "/conversion-proxy"] params
  (log/with-logging-context
    {:endpoint "conversion-proxy"}
    (conv-p/proxy-response (request/ring-request) params)))
