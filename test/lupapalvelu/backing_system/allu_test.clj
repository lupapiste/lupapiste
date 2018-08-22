(ns lupapalvelu.backing-system.allu-test
  "Unit tests for lupapalvelu.backing-system.allu. No side-effects."
  (:require [cheshire.core :as json]
            [schema.core :as sc :refer [defschema Bool]]
            [sade.core :refer [def- now]]
            [sade.env :as env]
            [sade.schemas :refer [NonBlankStr Kiinteistotunnus ApplicationId]]
            [sade.schema-generators :as sg]
            [sade.municipality :refer [municipality-codes]]
            [lupapalvelu.attachment :refer [Attachment]]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-schema-name-key]]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.integrations.geojson-2008-schemas :as geo]
            [lupapalvelu.organization :refer [PermitType]]
            [lupapalvelu.user :refer [User]]

            [midje.sweet :refer [facts fact => contains provided]]
            [midje.util :refer [testable-privates]]
            [clojure.test.check :refer [quick-check]]
            [com.gfredericks.test.chuck.properties :refer [for-all]]
            [com.gfredericks.test.chuck.generators :refer [string-from-regex]]
            [lupapalvelu.test-util :refer [passing-quick-check catch-all]]

            [lupapalvelu.backing-system.allu :as allu :refer [PlacementContract]])
  (:import [lupapalvelu.backing_system.allu IntegrationMessagesMockALLU RemoteALLU]))

(testable-privates lupapalvelu.backing-system.allu application->allu-placement-contract
                   application-cancel-request placement-creation-request placement-update-request attachment-send
                   request-integration-message response-integration-message)

;;; TODO: Instead of testing internals, just use mocks.

;;;; Refutation Utilities
;;;; ===================================================================================================================

(defschema ValidPlacementApplication
  {:id               ApplicationId
   :permitSubtype    NonBlankStr
   :organization     NonBlankStr
   :propertyId       Kiinteistotunnus
   :municipality     (apply sc/enum municipality-codes)
   :address          NonBlankStr
   :primaryOperation {:name allu/SijoituslupaOperation}
   :documents        [(sc/one (dds/doc-data-schema "hakija-ya" true) "applicant")
                      (sc/one (dds/doc-data-schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa" true) "description")
                      (sc/one (dds/doc-data-schema "yleiset-alueet-maksaja" true) "payee")]
   :location-wgs84   [(sc/one sc/Num "longitude") (sc/one sc/Num "latitude")]
   :drawings         [{:geometry-wgs84 geo/SingleGeometry}]})

(def- organizations (string-from-regex #"\d{3}-(R|YA|YMP)"))

(def- invalid-placement-application? (comp not nil? (partial sc/check ValidPlacementApplication)))

;;;; Actual Tests
;;;; ==================================================================================================================

(env/with-feature-value :allu true
  (sc/with-fn-validation
    (facts "allu-application?"
      (fact "Use ALLU integration for Helsinki YA."
        (allu/allu-application? "091-YA" "YA") => true)

      (fact "Do not use ALLU integration for anything else."
        (quick-check 10
                     (for-all [org-id organizations
                               permit-type (sg/generator PermitType)
                               :when (not (and (= org-id "091-YA") (= permit-type "YA")))]
                       (not (allu/allu-application? org-id permit-type))))
        => passing-quick-check))

    (facts "application->allu-placement-contract"
      (fact "Valid applications produce valid inputs for ALLU."
        (quick-check 10
                     (for-all [application (sg/generator ValidPlacementApplication)]
                       (nil? (sc/check PlacementContract
                                       (application->allu-placement-contract (sg/generate Bool) application)))))
        => passing-quick-check))

    (facts "application-cancel-request"
      (let [allu-id "23"
            app (assoc-in (sg/generate ValidPlacementApplication) [:integrationKeys :ALLU :id] allu-id)]
        (application-cancel-request {:application app})
        => {:request-method :put
            :uri            (str "https://example.com/api/v1/applications/" allu-id "/cancelled")
            :headers        {:authorization "Bearer foo.bar.baz"}}))

    (facts "placement-creation-request"
      (let [app (sg/generate ValidPlacementApplication)
            {:keys [params http-request]} (placement-creation-request {:application app})]
        http-request => (contains {:request-method :post
                                   :uri            "https://example.com/api/v1/placementcontracts"
                                   :headers        {:authorization "Bearer foo.bar.baz"}})
        (:body http-request) => string?
        params => {:application (application->allu-placement-contract true {:application app})}))

    (facts "placement-update-request"
      (let [allu-id "23"
            app (assoc-in (sg/generate ValidPlacementApplication) [:integrationKeys :ALLU :id] allu-id)]
        (doseq [pending-on-client [true false]
                :let [{:keys [params http-request]}
                      (placement-update-request pending-on-client {:application app})]]
          http-request => (contains {:request-method :put
                                     :uri            (str "https://example.com/api/v1/placementcontracts/" allu-id)
                                     :headers        {:authorization "Bearer foo.bar.baz"}})
          (:body http-request) => string?
          params => {:id          allu-id
                     :application (application->allu-placement-contract pending-on-client {:application app})})))

    (facts "attachment-send"
      (let [allu-id "23"
            application (-> (sg/generate ValidPlacementApplication) (assoc-in [:integrationKeys :ALLU :id] allu-id))
            {{:keys [type-group type-id]} :type :keys [latestVersion] :as attachment} (sg/generate Attachment)
            contents "You will be assimilated."
            {:keys [http-request]} (attachment-send {:application application} attachment contents)]
        http-request => (contains {:request-method :post
                                   :uri            (str "https://example.com/api/v1/applications/" allu-id
                                                        "/attachments")
                                   :headers        {:authorization "Bearer foo.bar.baz"}})
        (-> http-request (get-in [:body 0]) (update :content #(json/decode % true))) =>
        {:name      "metadata"
         :content   {:name        (or (:contents attachment) "")
                     :description (localize "fi" :attachmentType type-group type-id)
                     :mimeType    (:contentType latestVersion)}
         :mime-type "application/json"}
        (get-in http-request [:body 1]) => {:name      "file"
                                            :content   contents
                                            :mime-type (:contentType latestVersion)}))

    (facts "integration message generation"
      (let [user (sg/generate (select-keys User [:id :username]))
            app (sg/generate ValidPlacementApplication)
            request (select-keys (placement-creation-request {:application app}) [:uri :request-method :body])]
        (fact "request-integration-message"
          (request-integration-message {:user        user
                                        :application app
                                        :action      "submit-application"}
                                       request "placementcontracts.create")
          => (contains {:direction    "out"
                        :messageType  "placementcontracts.create"
                        :transferType "http"
                        :partner      "allu"
                        :format       "json"
                        :created      5
                        :status       "processing"
                        :application  (select-keys app [:id :organization :state])
                        :initator     user
                        :action       "submit-application"
                        :data         request})
          (provided (now) => 5))

        (let [response {:status 200, :body "23"}]
          (fact "response-integration-message"
            (response-integration-message {:user        user
                                           :application app
                                           :action      "submit-application"}
                                          (:uri request) response "placementcontracts.create")
            => (contains {:direction    "in"
                          :messageType  "placementcontracts.create"
                          :transferType "http"
                          :partner      "allu"
                          :format       "json"
                          :created      5
                          :status       "done"
                          :application  (select-keys app [:id :organization :state])
                          :initator     user
                          :action       "submit-application"
                          :data         {:endpoint (:uri request)
                                         :response response}})
            (provided (now) => 5)))))

    (facts "make-allu"
      (fact "dev mock"
        (.. (allu/make-allu) inner inner) => (partial instance? IntegrationMessagesMockALLU)
        (provided (env/dev-mode?) => true))

      (fact "prod HTTP client"
        (.. (allu/make-allu) inner inner) => (partial instance? RemoteALLU)
        (provided (env/dev-mode?) => false)))))
