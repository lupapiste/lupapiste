(ns lupapalvelu.backing-system.allu.core-test
  "Unit tests for lupapalvelu.backing-system.allu. No side-effects."
  (:require [clojure.core.match :refer [match]]
            [clojure.data :refer [diff]]
            [clojure.test.check :refer [quick-check]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lupapalvelu.attachment :as attachment :refer [Attachment]]
            [lupapalvelu.backing-system.allu.conversion :as allu-emit :refer [lang]]
            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.allu.schemas :refer [ApplicationHistory ApplicationHistoryParams
                                                             ApplicationKind ApplicationType AttachmentMetadata
                                                             ContractMetadata DecisionMetadata PlacementContract
                                                             Promotion ShortTermRental StatusChange SupervisionEvent
                                                             ValidPlacementApplication ValidPromotion
                                                             ValidShortTermRental]]
            [lupapalvelu.document.allu-schemas :refer [application-kinds application-types]]
            [lupapalvelu.file-upload :refer [SavedFileData]]
            [lupapalvelu.generators.allu :as allu-gen]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.json :as json]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.test-util :refer [err in-text passing-quick-check]]
            [lupapalvelu.user :refer [User]]
            [midje.sweet :refer :all]
            [reitit.ring :as reitit-ring]
            [sade.core :refer [def- now]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.schema-generators :as ssg]
            [sade.shared-schemas :as sssc]
            [sade.util :as util]
            [schema.core :as sc])
  (:import [java.io InputStream]))

(set! *warn-on-reflection* true)

(allu-gen/register)

;;; TODO: Split the massive mock and test forms into pieces.

;;;; Refutation Utilities
;;;; ===================================================================================================================

(defn- schema-error? [exn] (= (:type (ex-data exn)) :schema.core/error))

(defn- http-error? [http-status] (fn [exn] (= (:status (ex-data exn)) http-status)))

(def org-gen (gen/let [prefix (gen/choose 0 999)
                       suffix (gen/elements #{"R" "YA" "YMP"})]
               (format "%03d-%s" prefix suffix)))

(def- organizations org-gen)

(def- invalid-placement-application? (comp not nil? (partial sc/check ValidPlacementApplication)))

(def- allu-id "23")
(def- mock-jwt "foo.bar.baz")

(def- ^:dynamic sent-attachment
  "A side channel for providing original attachment data to `test-handler`."
  nil)

(def- ^:dynamic selected-kind
  "A side channel for providing expected kind to `test-handler`."
  nil)

(def- permit-type-schema (->> (permit/permit-types)
                              keys
                              (apply sc/enum)))

(defn- test-router
  "Make a mock Reitit router that does some test checks and usually returns `default-response`."
  [default-response]
  (reitit-ring/router
    (#'allu/routes
      (fn [request]
        (let [interface-path (-> request reitit-ring/get-match :data :name)
              http-request (-> (into {} (remove (comp namespace key)) request)
                               (update :body (fn [body]
                                               (cond
                                                 (string? body) (json/decode body true)
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
            (let [response* (match interface-path
                              [:applications :allu-data] (facts "application.allu-data request"
                                                           http-request
                                                           => (contains {:uri            (str "/applications/" allu-id)
                                                                         :request-method :get
                                                                         :headers        headers}))

                              [:applications :cancel]
                              (facts "applications.cancel request"
                                http-request
                                => (contains {:uri            (str "/applications/" allu-id "/cancelled")
                                              :request-method :put
                                              :headers        headers}))

                              [:applicationhistory]
                              (do (facts "applicationhistory request"
                                    http-request => (contains {:uri            "/applicationhistory"
                                                               :request-method :post
                                                               :headers        headers})
                                    (sc/check ApplicationHistoryParams (:body http-request)) => nil)
                                  {:status 200
                                   :body   (json/encode (map (fn [id]
                                                               {:applicationId     id
                                                                :events            (ssg/generate [StatusChange])
                                                                :supervisionEvents (ssg/generate [SupervisionEvent])})
                                                             (-> http-request :body :applicationIds)))})

                              [:placementcontracts :create] (facts "placementcontracts.create request"
                                                              (dissoc http-request :body)
                                                              => (contains {:uri            "/placementcontracts"
                                                                            :request-method :post
                                                                            :headers        headers
                                                                            :content-type   :json})
                                                              (sc/check PlacementContract (:body http-request)) => nil)

                              [:placementcontracts :update]
                              (facts "placementcontracts.update request"
                                (dissoc http-request :body)
                                => (contains {:uri            (str "/placementcontracts/" allu-id)
                                              :request-method :put
                                              :headers        headers
                                              :content-type   :json})
                                (sc/check PlacementContract (:body http-request)) => nil)

                              [:placementcontracts :decision]
                              (do (facts "placementcontracts.decision request"
                                    http-request
                                    => (contains {:uri            (str "/placementcontracts/" allu-id "/decision")
                                                  :request-method :get
                                                  :headers        headers}))
                                  {:status  200
                                   :headers {"Content-Type" "application/pdf"}
                                   :body    (byte-array 0)})

                              [:placementcontracts :decision :metadata]
                              (do (facts "placementcontracts.decision.metadata request"
                                    http-request => (contains {:uri            (str "/placementcontracts/" allu-id
                                                                                    "/decision/metadata")
                                                               :request-method :get
                                                               :headers        headers}))
                                  {:status 200, :body (json/encode (ssg/generate DecisionMetadata))})

                              [:placementcontracts :contract :proposal]
                              (do (facts "placementcontracts.contract.proposal request"
                                    http-request
                                    => (contains {:uri            (str "/placementcontracts/" allu-id "/contract/proposal")
                                                  :request-method :get
                                                  :headers        headers}))
                                  {:status  200
                                   :headers {"Content-Type" "application/pdf"}
                                   :body    (byte-array 0)})

                              [:placementcontracts :contract :approved]
                              (facts "placementcontracts.contract.approved request"
                                http-request
                                => (contains {:uri            (str "/placementcontracts/" allu-id "/contract/approved")
                                              :request-method :post
                                              :headers        headers}))

                              [:placementcontracts :contract :final]
                              (do (facts "placementcontracts.contract.final request"
                                    http-request
                                    => (contains {:uri            (str "/placementcontracts/" allu-id "/contract/final")
                                                  :request-method :get
                                                  :headers        headers}))
                                  {:status  200
                                   :headers {"Content-Type" "application/pdf"}
                                   :body    (byte-array 0)})

                              [:placementcontracts :contract :metadata]
                              (do (facts "placementcontracts.contract.metadata request"
                                    http-request => (contains {:uri            (str "/placementcontracts/" allu-id
                                                                                    "/contract/metadata")
                                                               :request-method :get
                                                               :headers        headers}))
                                  {:status 200, :body (json/encode (ssg/generate ContractMetadata))})

                              [:short-term-rental :create]
                              (facts "short-term-rental.create request"
                                (dissoc http-request :body) => (contains {:uri            "/shorttermrentals"
                                                                          :request-method :post
                                                                          :headers        headers
                                                                          :content-type   :json})
                                (sc/check ShortTermRental (:body http-request)) => nil)

                              [:short-term-rental :update]
                              (facts "short-term-rental.update request"
                                (dissoc http-request :body)
                                => (contains {:uri            (str "/shorttermrentals/" allu-id)
                                              :request-method :put
                                              :headers        headers
                                              :content-type   :json})
                                (sc/check ShortTermRental (:body http-request)) => nil)

                              [:short-term-rental :decision]
                              (do (facts "short-term-rental.decision request"
                                    http-request
                                    => (contains {:uri            (str "/shorttermrentals/" allu-id "/decision")
                                                  :request-method :get
                                                  :headers        headers}))
                                  {:status  200
                                   :headers {"Content-Type" "application/pdf"}
                                   :body    (byte-array 0)})

                              [:short-term-rental :decision :metadata]
                              (do (facts "short-term-rental.decision.metadata request"
                                    http-request => (contains {:uri            (str "/shorttermrentals/" allu-id
                                                                                    "/decision/metadata")
                                                               :request-method :get
                                                               :headers        headers}))
                                  {:status 200, :body (json/encode (ssg/generate DecisionMetadata))})

                              [:promotion :create]
                              (facts "promotion.create request"
                                (dissoc http-request :body) => (contains {:uri            "/events"
                                                                          :request-method :post
                                                                          :headers        headers
                                                                          :content-type   :json})
                                (sc/check Promotion (:body http-request)) => nil)

                              [:promotion :update]
                              (facts "promotion.update request"
                                (dissoc http-request :body) => (contains {:uri            (str "/events/" allu-id)
                                                                          :request-method :put
                                                                          :headers        headers
                                                                          :content-type   :json})
                                (sc/check Promotion (:body http-request)) => nil)

                              [:promotion :decision :metadata]
                              (do (facts "promotion.decision.metadata request"
                                    http-request => (contains {:uri            (str "/events/" allu-id
                                                                                    "/decision/metadata")
                                                               :request-method :get
                                                               :headers        headers}))
                                  {:status 200, :body (json/encode (ssg/generate DecisionMetadata))})

                              [:promotion :decision]
                              (do (facts "promotion.decision request"
                                    http-request => (contains {:uri            (str "/events/" allu-id "/decision")
                                                               :request-method :get
                                                               :headers        headers}))
                                  {:status  200
                                   :headers {"Content-Type" "application/pdf"}
                                   :body    (byte-array 0)})

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
                                  metadata-content
                                  => {:name        (-> sent-attachment :latestVersion :filename)
                                      :description (let [{{:keys [type-group type-id]} :type} sent-attachment
                                                         type (localize lang :attachmentType type-group type-id)
                                                         description (:contents sent-attachment)]
                                                     (if (or (not description) (= type description))
                                                       type
                                                       (str type ": " description)))
                                      :mimeType    (-> sent-attachment :latestVersion :contentType)}
                                  (if (-> file :content :attach-self)
                                    (fact "pdf-exported Lupapiste application"
                                      (dissoc file :mime-type) => {:name    "file"
                                                                   :content {:attach-self true}})
                                    (fact "normal attachment"
                                      (dissoc file :mime-type)
                                      => (just {:name    "file"
                                                :content (partial instance? InputStream)})))
                                  ;; Could be improved but generators produce junk for this anyway:
                                  (:mime-type file) => string?))

                              [:applicationkinds]
                              (facts "applicationkinds request"
                                http-request => (contains {:uri            "/applicationkinds"
                                                           :query-params   {:applicationType (get application-types
                                                                                                  selected-kind)}
                                                           :request-method :get
                                                           :headers        headers}))

                              [:fixedlocations]
                              (facts "fixedlocations request"
                                http-request => (contains {:uri            "/fixedlocations"
                                                           :query-params   {:applicationKind (get application-kinds
                                                                                                  selected-kind)}
                                                           :request-method :get
                                                           :headers        headers})))]
              (if (map? response*) response* default-response)))))
      {:save-responses? true, :disable-io? true})))

;;;; Actual Tests
;;;; ==================================================================================================================

(defn invalidate-application [application]
  (update application :documents (fn [[doc & xs]]
                                   (-> doc
                                       (assoc-in [:data :henkilo :yhteystiedot :email] "bad-email")
                                       (assoc-in [:data :yritys :yhteyshenkilo :yhteystiedot :email] "bad-email")
                                       (cons xs)))))

(env/with-feature-value :allu true
  (sc/with-fn-validation
    (let [user                                    (ssg/generate (select-keys User [:id :username :firstName :lastName]))

          placement-app                           (ssg/generate ValidPlacementApplication)
          submitted-placement-app                 (-> placement-app
                                                      (assoc :state "submitted")
                                                      (assoc-in [:integrationKeys :ALLU :id] allu-id))
          sent-placement-app                      (assoc submitted-placement-app :state "sent")
          invalid-placement-app                   (invalidate-application placement-app)
          invalid-submitted-placement-app         (invalidate-application submitted-placement-app)

          short-term-rental-app                   (ssg/generate ValidShortTermRental)
          submitted-short-term-rental-app         (-> short-term-rental-app
                                                      (assoc :state "submitted")
                                                      (assoc-in [:integrationKeys :ALLU :id] allu-id))
          sent-short-term-rental-app              (assoc submitted-short-term-rental-app :state "sent")
          invalid-short-term-rental-app           (invalidate-application short-term-rental-app)
          invalid-submitted-short-term-rental-app (invalidate-application submitted-short-term-rental-app)

          promotion-app                           (ssg/generate ValidPromotion)
          submitted-promotion-app                 (-> promotion-app
                                                      (assoc :state "submitted")
                                                      (assoc-in [:integrationKeys :ALLU :id] allu-id))
          sent-promotion-app                      (assoc submitted-promotion-app :state "sent")
          invalid-promotion-app                   (invalidate-application promotion-app)
          invalid-submitted-promotion-app         (invalidate-application submitted-promotion-app)]
      (facts "integration message generation"
        (let [appy-request    (#'allu/application-creation-request {:application placement-app
                                                                    :user        user
                                                                    :action      "submit-application"})
              appless-request (#'allu/application-histories-request {:user user :action "fetch-verdicts"}
                                                                    [] (date/now))]
          (fact "request-integration-message"
            (-> (#'allu/request-integration-message (::allu/command appy-request) appy-request "placementcontracts.create")
                (diff {:direction    "out"
                       :messageType  "placementcontracts.create"
                       :transferType "http"
                       :partner      "allu"
                       :format       "json"
                       :created      5
                       :status       "processing"
                       :application  (select-keys placement-app [:id :organization :state])
                       :initiator    (select-keys user [:id :username])
                       :action       "submit-application"
                       :data         appy-request}))
            => (just [anything nil anything])
            (provided (now) => 5)

            (-> (#'allu/request-integration-message (::allu/command appless-request) appless-request "applicationhistory")
                (diff {:direction    "out"
                       :messageType  "applicationhistory"
                       :transferType "http"
                       :partner      "allu"
                       :format       "json"
                       :created      5
                       :status       "processing"
                       :initiator    (select-keys user [:id :username])
                       :action       "fetch-verdicts"
                       :data         appless-request}))
            => (just [anything nil anything])
            (provided (now) => 5))

          (let [response {:status 200, :body allu-id}]
            (fact "response-integration-message"
              (-> (#'allu/response-integration-message (::allu/command appy-request) (:uri appy-request)
                                                       response "placementcontracts.create")
                  (diff {:direction    "in"
                         :messageType  "placementcontracts.create"
                         :transferType "http"
                         :partner      "allu"
                         :format       "json"
                         :created      5
                         :status       "done"
                         :application  (select-keys placement-app [:id :organization :state])
                         :initiator    (select-keys user [:id :username])
                         :action       "submit-application"
                         :data         {:endpoint (:uri appy-request)
                                        :response response}}))
              => (just [anything nil anything])
              (provided (now) => 5)

              (-> (#'allu/response-integration-message (::allu/command appless-request) (:uri appless-request)
                                                       response "applicationhistory")
                  (diff {:direction    "in"
                         :messageType  "applicationhistory"
                         :transferType "http"
                         :partner      "allu"
                         :format       "json"
                         :created      5
                         :status       "done"
                         :initiator    (select-keys user [:id :username])
                         :action       "fetch-verdicts"
                         :data         {:endpoint (:uri appless-request)
                                        :response response}}))
              => (just [anything nil anything])
              (provided (now) => 5)))))

      (facts "allu-application?"
        (fact "Use ALLU integration for Helsinki YA."
          (allu/allu-application? "091-YA" "YA") => true
          (allu/allu-application? {:organization "091-YA"
                                   :permitType   "YA"}) => true)
        (fact "Permit type A is always Allu application"
          (allu/allu-application? "FOO" "A") => true
          (allu/allu-application? {:permitType "A"}) => true)

        (fact "Do not use ALLU integration for anything else."
          (let [org+permit-gen (gen/let [org-id      organizations
                                         permit-type (ssg/generator permit-type-schema)]
                                 [org-id permit-type])
                gen            (gen/such-that (fn [[org-id permit-type]]
                                                (not (or (= permit-type "A")
                                                         (and (= org-id "091-YA") (= permit-type "YA")))))
                                              org+permit-gen)]
            (quick-check 10
                         (prop/for-all [[org-id permit-type] gen]
                           (not (or (allu/allu-application? org-id permit-type)
                                    (allu/allu-application? {:organization org-id
                                                             :permitType   permit-type})))))
            => passing-quick-check)))

      (facts "uses-contract-documents?"
        (allu/uses-contract-documents? placement-app) => true
        (allu/uses-contract-documents? short-term-rental-app) => false
        (allu/uses-contract-documents? promotion-app) => false)

      (facts "application-history-fetchable?"
        (let [applicationId (Long/parseLong allu-id)]
          (allu/application-history-fetchable? {:applicationId     applicationId
                                                :events            []
                                                :supervisionEvents []}) => false

          (allu/application-history-fetchable?
            {:applicationId     applicationId
             :events            [{:applicationIdentifier "Fire Walk with Me"
                                  :eventTime             (allu-emit/format-date-time (date/now))
                                  :newStatus             "PENDING"}]
             :supervisionEvents []}) => false

          (doseq [newStatus ["DECISION" "WAITING_CONTRACT_APPROVAL"]]
            (allu/application-history-fetchable?
              {:applicationId     applicationId
               :events            [{:applicationIdentifier "Fire Walk with Me"
                                    :eventTime             (allu-emit/format-date-time (date/now))
                                    :newStatus             newStatus}]
               :supervisionEvents []}) => true)))

      (let [router  (test-router {:status 200, :body allu-id})
            handler (comp (reitit-ring/ring-handler router) @#'allu/try-reload-allu-id)]
        (with-redefs [allu/allu-router          router
                      allu/allu-request-handler handler
                      allu/send-allu-request!   (fn [_cmd req]
                                                  (handler req))]      ; Since these are unit tests we bypass the message queue.
          (facts "login!" (#'allu/login!) => {:status 200, :body (json/encode mock-jwt)})

          (facts "submit-application!"
            (fact "valid"
              (doseq [app [placement-app short-term-rental-app promotion-app]]
                (allu/submit-application! {:application app
                                           :user        user
                                           :system      {}
                                           :action      "submit-application"}) => {:status 200 :body allu-id}))

            (fact "invalid"
              (doseq [invalid-app [invalid-placement-app invalid-short-term-rental-app invalid-promotion-app]]
                (try
                  (allu/submit-application! {:application invalid-app
                                             :user        user
                                             :system      {}
                                             :action      "submit-application" })
                  (catch Exception e
                    (let [res (ex-data e)]
                      res => (err :error.integration.create-message)
                      (in-text (:details res) "bad-email")))))))

          (facts "update-application!"
            (fact "valid"
              (doseq [submitted-app [submitted-placement-app submitted-short-term-rental-app submitted-promotion-app]]
                (allu/update-application! {:application submitted-app
                                           :user        user
                                           :system      {}
                                           :action      "update-document"}) => {:status 200, :body allu-id}))

            (facts "invalid"
              (doseq [[app-name invalid-app] (util/mmap invalid-submitted-placement-app
                                                        invalid-submitted-short-term-rental-app
                                                        invalid-submitted-promotion-app)]
                (fact {:midje/description (name app-name)}
                  (allu/update-application! {:application invalid-app
                                             :user        user
                                             :system      {}
                                             :action      "update-document"}) => (throws schema-error?)))))

          (facts "lock-application!"
            (fact "valid"
              (doseq [submitted-app [submitted-placement-app submitted-short-term-rental-app submitted-promotion-app]]
                (allu/lock-application! {:application submitted-app
                                         :user        user
                                         :system      {}
                                         :action      "approve-application"}) => {:status 200, :body allu-id}))

            (fact "invalid"
              (doseq [invalid-app [invalid-submitted-placement-app invalid-submitted-short-term-rental-app
                                   invalid-submitted-promotion-app]]
                (allu/lock-application! {:application invalid-app
                                         :user        user
                                         :system      {}
                                         :action      "approve-application"}) => (throws schema-error?))))

          (facts "cancel-application!"
            (allu/cancel-application! {:application submitted-placement-app
                                       :user        user
                                       :system      {}
                                       :action      "cancel-application"}) => {:status 200, :body allu-id})

          (facts "application-histories"
            (let [apps      [sent-placement-app sent-short-term-rental-app submitted-promotion-app]
                  histories (allu/application-histories {:user   user
                                                         :action "fetch-allu-contracts"}
                                                        apps
                                                        (date/zoned-date-time (transduce (map :created)
                                                                                         min (now) apps)))]
              (count histories) => (count apps)
              (sc/check [ApplicationHistory] histories) => nil))

          (facts "application-history"
            (let [app      sent-placement-app
                  response (allu/application-history {:application app, :user user, :action "check-for-verdict"}
                                                     (date/zoned-date-time (:created app)))]
              (sc/check ApplicationHistory response) => nil))

          (facts "load-decision-document!"
            (doseq [sent-app [sent-placement-app sent-short-term-rental-app sent-promotion-app]]
              (sc/check SavedFileData
                        (allu/load-decision-document! {:application sent-app, :user user, :action "check-for-verdict"}))
              => nil))

          (facts "load-decision-metadata"
            (doseq [sent-app [sent-placement-app sent-short-term-rental-app sent-promotion-app]]
              (sc/check DecisionMetadata
                        (allu/load-decision-metadata {:application sent-app, :user user, :action "check-for-verdict"}))
              => nil))

          (facts "load-contract-document!"
            (doseq [agreement-state [:proposal :final]]
              (sc/check SavedFileData (allu/load-contract-document! {:application sent-placement-app,
                                                                     :user        user,
                                                                     :action      "check-for-verdict"}
                                                                    agreement-state)) => nil))

          (facts "load-contract-metadata"
            (sc/check ContractMetadata
                      (allu/load-contract-metadata {:application sent-placement-app
                                                    :user        user,
                                                    :action      "check-for-verdict"})) => nil)

          (facts "approve-placementcontract!"
            (allu/approve-placementcontract! {:application sent-placement-app
                                              :user        user
                                              :action      "check-for-verdict"}) => {:status 200, :body allu-id})

          (facts "send-attachments!"
            (let [fileId     (ssg/generate sssc/FileId)
                  version    (assoc (ssg/generate attachment/Version) :fileId fileId)
                  attachment (assoc (ssg/generate Attachment) :latestVersion version :versions [version])]
              (binding [sent-attachment attachment]
                (allu/send-attachments! {:application submitted-placement-app
                                         :user        user
                                         :action      "move-attachments-to-backing-system"}
                                        [attachment]) => [fileId])))

          (facts "send-application-as-attachment!"
            (binding [sent-attachment {:type          {:type-group "muut", :type-id "muu"}
                                       :contents      "Hakemus"
                                       :latestVersion {:filename    "Hakemus.pdf"
                                                       :contentType "application/pdf"}}]
              (allu/send-application-as-attachment! {:application submitted-placement-app
                                                     :user        user
                                                     :action      "approve-application"})
              => {:status 200 :body allu-id}))

          (facts "load-application-kinds!"
            (binding [selected-kind (ssg/generate ApplicationType)]
              (allu/load-application-kinds! selected-kind)) => (json/decode allu-id true))

          (facts "load-fixed-locations!"
            (binding [selected-kind (ssg/generate ApplicationKind)]
              (allu/load-fixed-locations! selected-kind)) => (json/decode allu-id true))))

      (doseq [status (conj (range 400 405) 500)]
        (facts (str "ALLU HTTP error " status)
          (let [router  (test-router {:status status, :body allu-id})
                handler (reitit-ring/ring-handler router)]
            (with-redefs [allu/allu-router          router
                          allu/allu-request-handler handler
                          allu/send-allu-request!   (fn [_cmd req]
                                                      (handler req))]  ; Since these are unit tests we bypass the message queue.
              (allu/submit-application! {:application placement-app
                                         :user        user
                                         :system      {}
                                         :action      "submit-application"}) => (throws (http-error? status)))))))))

(let [fun       #'lupapalvelu.backing-system.allu.core/relogin-and-retry-on-auth-errors!
      good      (constantly {:status 200 :body "Great!"})
      not-found (constantly {:status 404 :body "Where?"})
      bad       (fn [& _] {:status (rand-nth [401 403 "401" "403"]) :body "Bad!"})
      kaboom    (fn [_] (throw (ex-info "Kaboom!" {:status (rand-nth [nil 500])})))
      reboom    (fn [_] (throw (ex-info "Reboom!" (bad))))
      test-fn   #((fun %) {:my "request"})]
  (facts "Relogin and retry on auth errors"
    (fact "No error"
      (test-fn good) => (good)
      (provided (allu/login!) => nil :times 0)
      (test-fn not-found) => (not-found)
      (provided (allu/login!) => nil :times 0))
    (fact "Error, no retry"
      (test-fn kaboom) => (throws #"Kaboom!")
      (provided (allu/login!) => nil :times 0))
    (fact "Response, retry"
      (test-fn bad) => (contains {:body "Bad!"})
      (provided (allu/login!) => nil :times 1))
    (fact "Error, retry"
      (test-fn reboom) => (throws #"Reboom!")
      (provided (allu/login!) => nil :times 1))))
