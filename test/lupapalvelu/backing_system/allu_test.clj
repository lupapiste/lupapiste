(ns lupapalvelu.backing-system.allu-test
  "Unit tests for lupapalvelu.backing-system.allu. No side-effects."
  (:require [clojure.core.match :refer [match]]
            [clojure.walk :refer [postwalk]]
            [cheshire.core :as json]
            [reitit.ring :as reitit-ring]
            [schema.core :as sc :refer [defschema]]
            [sade.core :refer [def- now]]
            [sade.env :as env]
            [sade.municipality :refer [municipality-codes]]
            [sade.schemas :refer [NonBlankStr Kiinteistotunnus ApplicationId]]
            [sade.schema-generators :as sg]
            [sade.shared-schemas :as sssc]
            [lupapalvelu.attachment :as attachment :refer [Attachment]]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.integrations.geojson-2008-schemas :as geo]
            [lupapalvelu.organization :refer [PermitType]]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :refer [User]]

            [midje.sweet :refer [facts fact => contains provided throws]]
            [midje.util :refer [testable-privates]]
            [clojure.test.check :refer [quick-check]]
            [com.gfredericks.test.chuck.properties :refer [for-all]]
            [com.gfredericks.test.chuck.generators :refer [string-from-regex]]
            [lupapalvelu.test-util :refer [passing-quick-check]]

            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.allu.schemas :refer [SijoituslupaOperation PlacementContract FileMetadata]]
            [lupapalvelu.backing-system.allu.conversion :refer [lang]]))

(testable-privates lupapalvelu.backing-system.allu.core
                   placement-creation-request
                   request-integration-message response-integration-message
                   interface-path->string allu-fail!)

;;;; Refutation Utilities
;;;; ===================================================================================================================

(defn- schema-error? [exn] (= (:type (ex-data exn)) :schema.core/error))

(defn- http-error? [http-status] (fn [exn] (= (:status (ex-data exn)) http-status)))

(defschema ValidPlacementApplication
  {:id               ApplicationId
   :state            (apply sc/enum (map name states/all-states))
   :permitSubtype    (sc/enum "sijoituslupa" "sijoitussopimus")
   :organization     NonBlankStr
   :propertyId       Kiinteistotunnus
   :municipality     (apply sc/enum municipality-codes)
   :address          NonBlankStr
   :primaryOperation {:name SijoituslupaOperation}
   :documents        [(sc/one (dds/doc-data-schema "hakija-ya" true) "applicant")
                      (sc/one (dds/doc-data-schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa" true) "description")
                      (sc/one (dds/doc-data-schema "yleiset-alueet-maksaja" true) "payee")]
   :location-wgs84   [(sc/one sc/Num "longitude") (sc/one sc/Num "latitude")]
   :drawings         [{:geometry-wgs84 geo/SingleGeometry}]})

(def- organizations (string-from-regex #"\d{3}-(R|YA|YMP)"))

(def- invalid-placement-application? (comp not nil? (partial sc/check ValidPlacementApplication)))

(def- allu-id "23")

(def- ^:dynamic sent-attachment
  "A side channel for providing original attachment data to `test-handler`."
  nil)

(defn- test-router [response]
  (reitit-ring/router
    (#'allu/routes
      true
      (fn [request]
        (let [interface-path (-> request reitit-ring/get-match :data :name)
              http-request (-> (into {} (remove (comp namespace key)) request)
                               (update :body (fn [body]
                                               (cond
                                                 (string? body) (->> (json/decode body true)
                                                                     (postwalk (fn [v] ; HACK
                                                                                 (case v
                                                                                   "NaN" ##NaN
                                                                                   "Infinity" ##Inf
                                                                                   "-Infinity" ##-Inf
                                                                                   v))))
                                                 (vector? body) (update-in body [0 :content] json/decode true)))))
              headers {"authorization" (str "Bearer " (env/value :allu :jwt))}]
          (match interface-path
            [:applications :cancel] (facts "applications.cancel request"
                                      http-request
                                      => (contains {:uri            (str "/applications/" allu-id "/cancelled")
                                                    :request-method :put
                                                    :headers        headers
                                                    :body           nil}))

            [:placementcontracts :create] (facts "placementcontracts.create request"
                                            (dissoc http-request :body)
                                            => (contains {:uri            "/placementcontracts"
                                                          :request-method :post
                                                          :headers        headers
                                                          :content-type   :json})
                                            (sc/check PlacementContract (:body http-request)) => nil)

            [:placementcontracts :update]
            (facts "placementcontracts.update request"
              (dissoc http-request :body) => (contains {:uri            (str "/placementcontracts/" allu-id)
                                                        :request-method :put
                                                        :headers        headers
                                                        :content-type   :json})
              (sc/check PlacementContract (:body http-request)) => nil)

            [:attachments :create]
            (let [fileId (get-in request [::allu/command :latestAttachmentVersion :fileId])]
              (facts "attachments.create request"
                (dissoc http-request :multipart)
                => (contains {:uri            (str "/applications/" allu-id "/attachments")
                              :request-method :post
                              :headers        headers})
                (let [[metadata file] (:multipart http-request)
                      metadata-content (json/decode (:content metadata) true)]
                  (dissoc metadata :content) => {:name      "metadata"
                                                 :mime-type "application/json"
                                                 :encoding  "UTF-8"}
                  (sc/check FileMetadata metadata-content) => nil
                  metadata-content => {:name        (-> sent-attachment :latestVersion :filename)
                                       :description (let [{{:keys [type-group type-id]} :type} sent-attachment
                                                          type (localize lang :attachmentType type-group type-id)
                                                          description (:contents sent-attachment)]
                                                      (if (or (not description) (= type description))
                                                        type
                                                        (str type ": " description)))
                                       :mimeType    (-> sent-attachment :latestVersion :contentType)}
                  (dissoc file :mime-type) => {:name    "file"
                                               :content fileId}
                  ;; Could be improved but generators produce junk for this anyway:
                  (:mime-type file) => string?))))

          response)))))

;;;; Actual Tests
;;;; ==================================================================================================================

(env/with-feature-value :allu true
  (sc/with-fn-validation
    (let [user (sg/generate (select-keys User [:id :username]))
          app (sg/generate ValidPlacementApplication)
          submitted-app (assoc-in app [:integrationKeys :ALLU :id] allu-id)
          invalid-app (assoc app :address "")
          invalid-submitted-app (assoc submitted-app :address "")]
      (facts "integration message generation"
        (let [request (placement-creation-request {:application app
                                                   :user        user
                                                   :action      "submit-application"})]
          (fact "request-integration-message"
            (request-integration-message (::allu/command request) request
                                         "placementcontracts.create")
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

          (let [response {:status 200, :body allu-id}]
            (fact "response-integration-message"
              (response-integration-message (::allu/command request) (:uri request)
                                            response "placementcontracts.create")
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

      (let [router (test-router {:status 200, :body allu-id})
            handler (reitit-ring/ring-handler router)]
        (with-redefs [allu/allu-router router
                      allu/allu-request-handler handler
                      allu/send-allu-request! handler]      ; Since these are unit tests we bypass JMS.
          (facts "submit-application!"
            (allu/submit-application! {:application app
                                       :user        user
                                       :action      "submit-application"}) => nil
            (allu/submit-application! {:application invalid-app, :user user, :action "submit-application"})
            => (throws schema-error?))

          (facts "update-application!"
            (allu/update-application! {:application submitted-app
                                       :user        user
                                       :action      "update-document"}) => nil
            (allu/update-application! {:application invalid-submitted-app
                                       :user        user
                                       :action      "update-document"}) => (throws schema-error?))

          (facts "lock-application!"
            (allu/lock-application! {:application submitted-app
                                     :user        user
                                     :action      "approve-application"}) => nil
            (allu/lock-application! {:application invalid-submitted-app
                                     :user        user
                                     :action      "approve-application"}) => (throws schema-error?))

          (facts "cancel-application!"
            (allu/cancel-application! {:application submitted-app
                                       :user        user
                                       :action      "cancel-application"}) => nil)

          (facts "send-attachments!"
            (let [fileId (sg/generate sssc/FileId)
                  version (assoc (sg/generate attachment/Version) :fileId fileId)
                  attachment (assoc (sg/generate Attachment) :latestVersion version :versions [version])]
              (binding [sent-attachment attachment]
                (allu/send-attachments! {:application submitted-app
                                         :user        user
                                         :action      "move-attachments-to-backing-system"}
                                        [attachment]) => [fileId])))))

      (doseq [status (range 400 405)]
        (facts (str "ALLU HTTP error " status)
          (let [router (test-router {:status status, :body allu-id})
                handler (reitit-ring/ring-handler router)]
            (with-redefs [allu/allu-router router
                          allu/allu-request-handler handler
                          allu/send-allu-request! handler]  ; Since these are unit tests we bypass JMS.
              (allu/submit-application! {:application app
                                         :user        user
                                         :action      "submit-application"}) => (throws (http-error? status)))))))))
