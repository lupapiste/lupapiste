(ns lupapalvelu.backing-system.allu-test
  "Unit tests for lupapalvelu.backing-system.allu. No side-effects."
  (:require [clojure.core.match :refer [match]]
            [clojure.walk :refer [postwalk]]
            [lupapalvelu.json :as json]
            [mount.core :as mount]
            [reitit.ring :as reitit-ring]
            [schema.core :as sc :refer [defschema]]
            [sade.core :refer [def- now]]
            [sade.env :as env]
            [sade.schema-generators :as sg]
            [sade.shared-schemas :as sssc]
            [lupapalvelu.attachment :as attachment :refer [Attachment]]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.organization :refer [PermitType]]
            [lupapalvelu.user :refer [User]]

            [midje.sweet :refer [facts fact => contains provided throws]]
            [clojure.test.check :refer [quick-check]]
            [com.gfredericks.test.chuck.properties :refer [for-all]]
            [com.gfredericks.test.chuck.generators :refer [string-from-regex]]
            [lupapalvelu.test-util :refer [passing-quick-check]]

            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.allu.schemas
             :refer [ValidPlacementApplication PlacementContract AttachmentMetadata LoginCredentials]]
            [lupapalvelu.backing-system.allu.conversion :refer [lang]]))

;;;; Refutation Utilities
;;;; ===================================================================================================================

(defn- schema-error? [exn] (= (:type (ex-data exn)) :schema.core/error))

(defn- http-error? [http-status] (fn [exn] (= (:status (ex-data exn)) http-status)))

(def- organizations (string-from-regex #"\d{3}-(R|YA|YMP)"))

(def- invalid-placement-application? (comp not nil? (partial sc/check ValidPlacementApplication)))

(def- allu-id "23")
(def- mock-jwt "foo.bar.baz")

(def- ^:dynamic sent-attachment
  "A side channel for providing original attachment data to `test-handler`."
  nil)

(defn- test-router
  "Make a mock Reitit router that does some test checks and returns `response`."
  [response]
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
              headers {"authorization" (str "Bearer " mock-jwt)}]
          (if (= interface-path [:login])
            (do (facts "login request"
                  http-request => (contains {:uri            "/login"
                                             :request-method :post
                                             :content-type   :json
                                             :body           {:username (env/value :allu :username)
                                                              :password (env/value :allu :password)}}))
                {:status 200 :body (json/encode mock-jwt)})
            (do (match interface-path
                  [:applications :cancel] (facts "applications.cancel request"
                                            http-request
                                            => (contains {:uri            (str "/applications/" allu-id "/cancelled")
                                                          :request-method :put
                                                          :headers        headers}))

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

                  [:placementcontracts :contract :proposal]
                  (facts "placementcontracts.contract.proposal request"
                    http-request => (contains {:uri            (str "/placementcontracts/" allu-id "/contract/proposal")
                                               :request-method :get
                                               :headers        headers}))

                  [:placementcontracts :contract :approved]
                  (facts "placementcontracts.contract.approved request"
                    http-request => (contains {:uri            (str "/placementcontracts/" allu-id "/contract/approved")
                                               :request-method :post
                                               :headers        headers})
                    (sc/check LoginCredentials (:body http-request)))

                  [:placementcontracts :contract :final]
                  (facts "placementcontracts.contract.proposal request"
                    http-request => (contains {:uri            (str "/placementcontracts/" allu-id "/contract/final")
                                               :request-method :get
                                               :headers        headers}))

                  [:attachments :create]
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
                      (sc/check AttachmentMetadata metadata-content) => nil
                      metadata-content => {:name        (-> sent-attachment :latestVersion :filename)
                                           :description (let [{{:keys [type-group type-id]} :type} sent-attachment
                                                              type (localize lang :attachmentType type-group type-id)
                                                              description (:contents sent-attachment)]
                                                          (if (or (not description) (= type description))
                                                            type
                                                            (str type ": " description)))
                                           :mimeType    (-> sent-attachment :latestVersion :contentType)}
                      (dissoc file :mime-type) => {:name    "file"
                                                   :content (-> sent-attachment :latestVersion
                                                                (select-keys [:fileId :storageSystem]))}
                      ;; Could be improved but generators produce junk for this anyway:
                      (:mime-type file) => string?)))
                response)))))))

;;;; Actual Tests
;;;; ==================================================================================================================

(env/with-feature-value :allu true
  (mount/start #'allu/current-jwt)

  (sc/with-fn-validation
    (let [user (sg/generate (select-keys User [:id :username :firstName :lastName]))
          app (sg/generate ValidPlacementApplication)
          submitted-app (-> app
                            (assoc :state "submitted")
                            (assoc-in [:integrationKeys :ALLU :id] allu-id))
          sent-app (assoc submitted-app :state "sent")
          invalid-app (assoc app :address "")
          invalid-submitted-app (assoc submitted-app :address " ")]
      (facts "integration message generation"
        (let [request (#'allu/application-creation-request {:application app
                                                            :user user
                                                            :action "submit-application"})]
          (fact "request-integration-message"
            (#'allu/request-integration-message (::allu/command request) request "placementcontracts.create")
            => (contains {:direction "out"
                          :messageType "placementcontracts.create"
                          :transferType "http"
                          :partner "allu"
                          :format "json"
                          :created 5
                          :status "processing"
                          :application (select-keys app [:id :organization :state])
                          :initator (select-keys user [:id :username])
                          :action "submit-application"
                          :data request})
            (provided (now) => 5))

          (let [response {:status 200, :body allu-id}]
            (fact "response-integration-message"
              (#'allu/response-integration-message (::allu/command request) (:uri request)
                response "placementcontracts.create")
              => (contains {:direction "in"
                            :messageType "placementcontracts.create"
                            :transferType "http"
                            :partner "allu"
                            :format "json"
                            :created 5
                            :status "done"
                            :application (select-keys app [:id :organization :state])
                            :initator (select-keys user [:id :username])
                            :action "submit-application"
                            :data {:endpoint (:uri request)
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
            handler (comp (reitit-ring/ring-handler router) @#'allu/try-reload-allu-id)]
        (with-redefs [allu/allu-router router
                      allu/allu-request-handler handler
                      allu/send-allu-request! handler]      ; Since these are unit tests we bypass JMS.
          (facts "login!" (#'allu/login!) => {:status 200, :body (json/encode mock-jwt)})

          (facts "submit-application!"
            (allu/submit-application! {:application app
                                       :user user
                                       :action "submit-application"}) => {:status 200, :body allu-id}
            (allu/submit-application! {:application invalid-app, :user user, :action "submit-application"})
            => (throws schema-error?))

          (facts "update-application!"
            (allu/update-application! {:application submitted-app
                                       :user user
                                       :action "update-document"}) => {:status 200, :body allu-id}
            (allu/update-application! {:application invalid-submitted-app
                                       :user user
                                       :action "update-document"}) => (throws schema-error?))

          (facts "lock-application!"
            (allu/lock-application! {:application submitted-app
                                     :user user
                                     :action "approve-application"}) => {:status 200, :body allu-id}
            (allu/lock-application! {:application invalid-submitted-app
                                     :user user
                                     :action "approve-application"}) => (throws schema-error?))

          (facts "cancel-application!"
            (allu/cancel-application! {:application submitted-app
                                       :user user
                                       :action "cancel-application"}) => {:status 200, :body allu-id})

          (facts "load-placementcontract-proposal!"
            (allu/load-placementcontract-proposal! {:application sent-app
                                                    :user user
                                                    :action "TODO"}) => {:status 200, :body allu-id})

          (facts "approve-placementcontract!"
            (allu/approve-placementcontract! {:application sent-app
                                              :user user
                                              :action "TODO"}) => {:status 200, :body allu-id})

          (facts "load-placementcontract-final!"
            (allu/load-placementcontract-final! {:application sent-app
                                                 :user user
                                                 :action "TODO"}) => {:status 200, :body allu-id})

          (facts "load-contract-document!"
            (allu/load-contract-document! {:application sent-app
                                           :user user
                                           :action "check-for-verdict"}) => allu-id)


          (facts "send-attachments!"
            (let [fileId (sg/generate sssc/FileId)
                  version (assoc (sg/generate attachment/Version) :fileId fileId)
                  attachment (assoc (sg/generate Attachment) :latestVersion version :versions [version])]
              (binding [sent-attachment attachment]
                (allu/send-attachments! {:application submitted-app
                                         :user user
                                         :action "move-attachments-to-backing-system"}
                                        [attachment]) => [fileId])))))

      (doseq [status (conj (range 400 405) 500)]
        (facts (str "ALLU HTTP error " status)
          (let [router (test-router {:status status, :body allu-id})
                handler (reitit-ring/ring-handler router)]
            (with-redefs [allu/allu-router router
                          allu/allu-request-handler handler
                          allu/send-allu-request! handler]  ; Since these are unit tests we bypass JMS.
              (allu/submit-application! {:application app
                                         :user user
                                         :action "submit-application"}) => (throws (http-error? status)))))))))
