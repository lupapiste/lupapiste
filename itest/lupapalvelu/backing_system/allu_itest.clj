  (ns lupapalvelu.backing-system.allu-itest
  "Integration tests for ALLU integration. Using local (i.e. not over HTTP) testing style."
    (:require [clj-time.core :as t]
              [clj-time.format :as tf]
              [clojure.string :as s]
              [lupapalvelu.allu]
              [lupapalvelu.backing-system.allu.conversion :as allu-emit]
              [lupapalvelu.backing-system.allu.core :as allu]
              [lupapalvelu.backing-system.allu.schemas :refer [SijoituslupaOperation ValidPlacementApplication
                                                               ValidShortTermRental ValidPromotion
                                                               PlacementContract ShortTermRental
                                                               Promotion AttachmentMetadata]]
              [lupapalvelu.domain :as domain]
              [lupapalvelu.integrations.pubsub :as lip]
              [lupapalvelu.itest-util :as itu :refer [pena pena-id raktark-helsinki]]
              [lupapalvelu.itest-util.model-based :refer [state-graph->transitions traverse-state-transitions]]
              [lupapalvelu.json :as json]
              [lupapalvelu.mongo :as mongo]
              [lupapalvelu.states :as states]
              [lupapalvelu.user :as usr]
              [midje.sweet :refer [facts fact => =not=> contains just with-state-changes]]
              [monger.operators :refer [$set]]
              [mount.core :as mount]
              [reitit.ring :as reitit-ring]
              [sade.core :refer [def- ok? fail?]]
              [sade.env :as env]
              [sade.schema-generators :as ssg]
              [sade.shared-schemas :as sssc]
              [sade.util :as util :refer [file->byte-array]]
              [schema.core :as sc]
              [taoensso.timbre :as timbre])
  (:import [java.io InputStream]))

(def- date-parser (tf/formatter "dd.MM.yyyy"))


;;;; # Constant Data
;;;; ===================================================================================================================

(def- drawings
  [{:id       1,
    :name     "A",
    :desc     "A desc",
    :category "123",
    :geometry "POLYGON((438952 6666883.25,441420 6666039.25,441920 6667359.25,439508 6667543.25,438952 6666883.25))",
    :area     "2686992",
    :height   "1"}
   {:id       2,
    :name     "B",
    :desc     "B desc",
    :category "123",
    :geometry "POLYGON((440652 6667459.25,442520 6668435.25,441912 6667359.25,440652 6667459.25))",
    :area     "708280",
    :height   "12"}])

(def- proposal {:category   "allu-contract"
                :giver      "Hannu Helsinki"
                :legacy?    true
                :proposal?  false
                :title      "Sopimusehdotus"
                :signatures (just [(contains {:name "Pena Panaani"})])})

(def- agreement {:category   "allu-contract"
                 :giver      "Hannu Helsinki"
                 :legacy?    true
                 :proposal?  false
                 :title      "Sopimus"
                 :signatures (just [(contains {:name "Pena Panaani"})
                                    (contains {:name "Uber-driver Decider Dave"})])})

(def- decision {:category   "allu-verdict"
                :giver      "Hannu Helsinki"
                :legacy?    true
                :proposal?  false
                :title      ""
                :signatures (just [(contains {:name "Uber-driver Decider Dave"})])})

;; Agreement-template when agreement is made without proposal
(def- fast-agreement {:category   "allu-contract"
                      :giver      "Hannu Helsinki"
                      :legacy?    true
                      :proposal?  false
                      :title      "Sopimus"
                      :signatures (just [(contains {:name "Uber-driver Decider Dave"})])})

;;;; # Helper Functions
;;;; ===================================================================================================================

(defn- nullify-doc-ids
  "Make some foreign keys nil so that the tests don't try to load nonexistent users with generated id:s."
  [doc]
  (-> doc
      (assoc-in [:data :henkilo :userId :value] nil)
      (assoc-in [:data :yritys :companyId :value] nil)))

(defn- add-canceled->*
  "HACK: undo-cancellation bypasses the state graph so we add [:cancel *] arcs to the state graph with this."
  [state-graph]
  (update state-graph :canceled
          into
          (comp (filter (fn [[_ succs]] (some (partial = :canceled) succs)))
                (map key))
          state-graph))

(def- full-sijoitussopimus-state-graph
  (->> states/ya-sijoitussopimus-state-graph
       add-canceled->*
       (into {} (remove (fn [[src _]] (= src :complementNeeded)))))) ; :complementNeeded should be unreachable

(def- full-allu-state-graph
  (->> states/allu-state-graph
       add-canceled->*
       (into {} (remove (fn [[src _]] (= src :complementNeeded)))))) ; :complementNeeded should be unreachable

(def- initial-allu-state {:id-counter 0, :applications {}})

(defn- create [apikey app-type]
  (let [{:keys [id] :as response} (itu/create-local-app apikey
                                                        :operation (if (= app-type "sijoitussopimus")
                                                                     (ssg/generate SijoituslupaOperation)
                                                                     app-type)
                                                        :x "385770.46" :y "6672188.964"
                                                        :address "Kaivokatu 1"
                                                        :propertyId "09143200010023")]
    (fact (str app-type " created succesfully") response => ok?)
    (when (= app-type "sijoitussopimus")
      (mongo/update-by-id :applications id {$set {:permitSubtype app-type}}))
    id))

(defn- initialize-documents [id app-type]
  (let [ValidApplicationSubtype (case app-type
                                  "sijoitussopimus" ValidPlacementApplication
                                  "lyhytaikainen-maanvuokraus" ValidShortTermRental
                                  "promootio" ValidPromotion)]
    (mongo/update-by-id :applications id {$set {:documents (-> (ssg/generate (:documents ValidApplicationSubtype))
                                                               (update 0 nullify-doc-ids))}})))

(defn create-and-fill [apikey app-type]
  (doto (create apikey app-type)
    (initialize-documents app-type)))

(defn- upload-attachment
  ([apikey app-id] (upload-attachment apikey app-id nil))
  ([apikey app-id attachment]
   (let [attachmentId (:id attachment)
         description "Test file"
         filedata {:type     {:type-group "muut", :type-id "muu"}
                   :contents description}]
     (fact "upload attachment file and bind"
       (itu/upload-file-and-bind apikey app-id filedata :attachment-id attachmentId)
       => (comp nil? (partial sc/check sssc/FileId)))
     (fact "add-comment"
       (itu/command apikey :add-comment :id app-id :text "Added my test text file." :target {:type "application"}
                    :roles ["applicant" "authority"]) => ok?)
     attachmentId)))

(defn- open [apikey app-id msg]
  (fact ":draft -> :open"
    (itu/command apikey :add-comment :id app-id :text msg :target {:type "application"}
                 :roles ["applicant" "authority"]) => ok?))

(defn- id->allu-id [{:keys [applications] :as _allu-state} id]
  (some->> applications
           (util/find-first (fn [[_ {:keys [identificationNumber]}]] (= identificationNumber id)))
           key))

(defn check-allu-id-exists [app-id allu-state]
  ;; This relies on the fact that these are local applications, so in our local db
  (loop [app (mongo/by-id :applications app-id)
         n   0]
    (when (and (or (not (get-in app [:integrationKeys :ALLU :id]))
                   (not (id->allu-id @allu-state app-id)))
               (< n 30))
      ;; This can actually take quite long sometimes if Pub/Sub stalls
      (timbre/debug "ALLU ID not yet found for" app-id)
      (Thread/sleep 1000)
      (recur (mongo/by-id :applications app-id) (inc n)))))

(defn- submit [apikey app-id allu-state]
  (fact "submit application"
    (itu/command apikey :submit-application :id app-id) => ok?)
  (check-allu-id-exists app-id allu-state))

(defn fill-applicant-doc [apikey app-id applicant-id]
  (itu/command apikey :set-user-to-document :id app-id :documentId applicant-id :userId pena-id :path "henkilo") => ok?
  (itu/command apikey :set-current-user-to-document :id app-id :documentId applicant-id :path "henkilo") => ok?
  (itu/command apikey :set-company-to-document :id app-id :documentId applicant-id :companyId "esimerkki" :path "yritys") => ok?
  (let [user (usr/get-user-by-id pena-id)]
    (itu/command apikey :update-doc :id app-id :doc applicant-id
                 :updates [["yritys.yhteyshenkilo.henkilotiedot.etunimi" (:firstName user)]
                           ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" (:lastName user)]
                           ["yritys.yhteyshenkilo.yhteystiedot.email" (:email user)]
                           ["yritys.yhteyshenkilo.yhteystiedot.puhelin" (:phone user)]]) => ok?))

(defmulti fill-application (fn [_apikey application] (#'allu/application-type application)))

(defmethod fill-application "sijoitussopimus" [apikey {app-id :id :keys [documents] :as _application}]
  (let [{descr-id :id} (domain/get-document-by-name documents "yleiset-alueet-hankkeen-kuvaus-sijoituslupa")]
    (fact "fill sijoitussopimus"
      (itu/command apikey :update-doc :id app-id :doc descr-id
                   :updates [["kayttotarkoitus" "tuijottelu"]]) => ok?
      (itu/command apikey :save-application-drawings :id app-id :drawings drawings) => ok?
      (fill-applicant-doc apikey app-id (:id (domain/get-applicant-document documents))))

    #_(upload-attachment apikey app-id attachment)))

(defmethod fill-application "lyhytaikainen-maanvuokraus" [apikey {app-id :id :keys [documents] :as _application}]
  (let [kind "dog-training-event"
        {descr-id :id} (domain/get-document-by-name documents "lyhytaikainen-maanvuokraus")
        {location-id :id} (domain/get-document-by-name documents "lmv-location")
        {time-id :id} (domain/get-document-by-name documents "lmv-time")]
    (fact "fill lyhytaikainen-maanvuokraus"
      (itu/command apikey :update-doc :id app-id :doc descr-id
                   :updates [["lyhytaikainen-maanvuokraus.kind" kind]
                             ["lyhytaikainen-maanvuokraus.description" "How to hau hau BIG."]]) => ok?

      (itu/command apikey :save-application-drawings :id app-id :drawings drawings) => ok?
      (itu/command apikey :add-allu-drawing :id app-id :kind kind :siteId 102) => ok?
      (itu/command apikey :update-doc :id app-id :doc location-id
                   :updates [["lmv-location.area" "230"]]) => ok?

      (let [start-date (t/plus (t/today) (t/days 5))
            end-date (t/plus start-date (t/days 2))]
        (itu/command apikey :update-doc :id app-id :doc time-id
                     :updates [["lmv-time.start-date" (tf/unparse-local-date @#'date-parser start-date)]
                               ["lmv-time.end-date" (tf/unparse-local-date @#'date-parser end-date)]])) => ok?

      (fill-applicant-doc apikey app-id (:id (domain/get-applicant-document documents))))))

(defmethod fill-application "promootio" [apikey {app-id :id :keys [documents] :as _application}]
  (let [{descr-id :id} (domain/get-document-by-name documents "promootio")
        {time-id :id} (domain/get-document-by-name documents "promootio-time")]
    (itu/command apikey :update-doc :id app-id :doc descr-id
                 :updates [["promootio.promootio-name" "Ostakaa makkaraa"]
                           ["promootio.promootio-description" "Nokkahuilua"]]) => ok?

    (let [promotion-date (t/plus (t/today-at 12 0) (t/days 5))]
      (itu/command apikey :update-doc :id app-id :doc time-id
                   :updates [["promootio-time.start-date" (tf/unparse @#'date-parser promotion-date)]
                             ["promootio-time.start-time" "12:00"]
                             ["promootio-time.end-date" (tf/unparse @#'date-parser promotion-date)]
                             ["promootio-time.end-time" "17:15"]])) => ok?

    (itu/command apikey :save-application-drawings :id app-id :drawings drawings) => ok?
    (itu/command apikey :add-allu-drawing :id app-id :kind "promotion" :siteId 8) => ok?

    (fill-applicant-doc apikey app-id (:id (domain/get-applicant-document documents)))))

(defn- fill [apikey app-id]
  (fill-application apikey (domain/get-application-no-access-checking app-id)))

(defn- approve [apikey app-id]
  (fact "approve application"
    (itu/command apikey :approve-application :id app-id :lang "fi") => ok?))

(defn- return-to-draft [apikey app-id msg]
  (fact "return to draft"
    (itu/command apikey :return-to-draft :id app-id :text msg :lang "fi") => ok?))

(defn- request-for-complement [apikey id]
  (fact "request for complement"
    (itu/command apikey :request-for-complement :id id)
    => (contains {:ok     false
                  :text   "error.integration.unsupported-action"
                  :action "request-for-complement"})))

(defn- cancel [apikey app-id msg]
  (fact "cancel application"
    (itu/command apikey :cancel-application :id app-id :text msg :lang "fi") => ok?))

(defn- undo-cancellation [apikey app-id]
  (fact "undo cancellation"
    (itu/command apikey :undo-cancellation :id app-id)
    => (contains {:ok     false
                  :text   "error.integration.unsupported-action"
                  :action "undo-cancellation"})))

(defn- fetch-contract [apikey app-id & [expect-fail?]]
  ;; HACK: Since check-for-verdict can fail due to a race condition with submit-application, we retry for 20 secs.
  (loop [{:keys [ok] :as result} (itu/command apikey :check-for-verdict :id app-id)
         n      0]
    (cond
      (or (and expect-fail? (false? ok)) ok) (do (timbre/debug "Verdict found") result)
      (>= n 20) (do (timbre/debug "Verdict not found") result)
      :else
      (do (timbre/debug "Verdict not yet found on application" app-id "- retrying")
          (Thread/sleep 1000)
          (recur (itu/command apikey :check-for-verdict :id app-id) (inc n))))))

(defn- sign-contract [apikey app-id]
  (fact "sign contract"
    (let [verdict-id (-> (lupapalvelu.domain/get-application-no-access-checking app-id)
                         :pate-verdicts
                         first
                         :id)]
      (itu/command apikey :sign-allu-contract :id app-id :verdict-id verdict-id :password "pena") => ok?)))

(defn- verdict-list [apikey app-id]
  (let [response (itu/query apikey :pate-verdicts :id app-id)]
    (fact response => ok?)
    (:verdicts response)))

;;;; # Mock Handler
;;;; ===================================================================================================================

(defn- check-imessages-middleware
  "Middleware that checks that `integration-messages` is updated with the request and response."
  [handler]
  (fn [{{:keys [application]} ::allu/command :as request}]
    (let [imsg-query (fn [direction]
                       {:partner        "allu"
                        :messageType    (s/join \. (map name (-> request reitit-ring/get-match :data :name)))
                        :direction      direction
                        :status         "done"
                        :application.id (:id application)})
          res (handler request)]
      (when-not (= (:uri request) "/login")                 ; HACK
        (fact "integration messages are saved"
          (mongo/any? :integration-messages (imsg-query "out")) => true
          (mongo/any? :integration-messages (imsg-query "in")) => true))
      res)))

(def- mock-jwt "foo.bar.baz")

(defn- json-request
  "Decode request JSON body."
  [handler]
  (fn [request]
    (handler (update request :body json/decode true))))

(defn- json-response
  "Encode response JSON body and add Content-Type header."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (case (:status response)
        200 (-> response
                (update :body json/encode)
                (assoc-in [:headers "Content-Type"] "application/json"))
        response))))

(defn- check-auth
  "Check JWT."
  [handler]
  (fn [request]
    (if (= (get-in request [:headers "authorization"] (str "Bearer " mock-jwt)))
      (handler request)
      {:status 401, :body "Unauthorized"})))

(defn- check-allu-id
  "Check ALLU id format and app existence."
  [allu-state]
  (fn [handler]
    (fn [{{:keys [id]} :path-params :as request}]
      (if-let [validation-error (sc/check sc/Str id)]
        {:status 400, :body validation-error}
        (if (loop [n 0]
              (cond
                (contains? (:applications @allu-state) id) true
                (> n 10) false
                :else (do (Thread/sleep 1000)
                          (recur (inc n)))))
          (handler request)
          {:status 404, :body (str "Not Found: " id ", current application ids: " (keys (:applications @allu-state)))})))))

(defn wait-until-in-state-or-timeout [allu-state allu-id attr target-state?]
  (let [get-state #(get-in @allu-state [:applications allu-id attr])]
    (loop [current-state (get-state)
           n 0]
      (cond
        (= current-state target-state?) true
        (>= n 10) false
        :else (do (Thread/sleep 1000)
                  (recur (get-state) (inc n)))))))

(defn- check-pending-on-client
  "Check that application is (not) pendingOnClient"
  [allu-state should-be-pending]
  (fn [handler]
    (fn [{{:keys [id]} :path-params :as request}]
      (let [route-name (allu/route-name->string (-> request reitit-ring/get-match :data :name))]
        (if (wait-until-in-state-or-timeout allu-state id :pendingOnClient should-be-pending)
          (handler request)
          {:status 403, :body (str id " is" (if-not should-be-pending " " " not ") "pendingOnClient") :route route-name})))))

(defn- create-application-route [allu-state ApplicationSubtype]
  ["" {:middleware [json-request]
       :post       {:handler (fn [{allu-application :body}]
                               (if-let [validation-error (sc/check ApplicationSubtype allu-application)]
                                 {:status 400, :body validation-error}
                                 (let [allu-application (assoc allu-application :status-changes [])
                                       {:keys [id-counter]}
                                       (swap! allu-state (fn [{:keys [id-counter] :as state}]
                                                           (-> state
                                                               (update :id-counter inc)
                                                               (update :applications assoc (str id-counter)
                                                                       allu-application))))]
                                   {:status 200, :body (str (dec id-counter))})))}}])

(defn- update-application-route [allu-state ApplicationSubtype]
  ["" {:middleware [(check-pending-on-client allu-state true) json-request]
       :put        {:handler (fn [{{:keys [id]} :path-params allu-application :body}]
                               (if-let [validation-error (sc/check ApplicationSubtype allu-application)]
                                 {:status 400, :body validation-error}
                                 (do (swap! allu-state assoc-in [:applications id] allu-application)
                                     {:status 200, :body id})))}}])

(defn- metadata-route [allu-state metadata-body]
  ["/metadata"
   {:get {:middleware [json-response]
          :handler    (fn [{{:keys [id]} :path-params}]
                        {:status 200
                         :body   (metadata-body allu-state id)})}}])

(defn- pdf-route [fragment]
  [fragment {:get {:handler (fn [_request]
                              {:status  200
                               :headers {"Content-Type" "application/pdf"}
                               :body    (file->byte-array "dev-resources/test-pdf.pdf")})}}])

(defn- decision-routes [fragment allu-state]
  [fragment {:middleware [(check-pending-on-client allu-state false)]}
   (pdf-route "")
   (metadata-route allu-state (fn [_ _]
                                {:handler       {:name  "Hannu Helsinki"
                                                 :title "Director"}
                                 :decisionMaker {:name  "Decider Dave"
                                                 :title "Uber-driver"}}))])

(defn- mock-routes
  "Create mock Reitit routes whose handlers use `allu-state` as the ALLU DB."
  [allu-state]
  [["/login" {:post {:handler (fn [_] {:status 200, :body (json/encode mock-jwt)})}}]

   ["/" {:middleware [check-auth]}
    ["applications"
     ["/:id" {:middleware [(check-allu-id allu-state)]}
      ["" {:get {:handler (fn [{{:keys [id]} :path-params}]
                            {:status 200, :body (str "{\"applicationId\": \"SL00000" id "\"}")})}}]

      ["/cancelled" {:put {:handler (fn [{{:keys [id]} :path-params}]
                                      (do (swap! allu-state update :applications dissoc id)
                                          {:status 200, :body ""}))}}]

      ["/attachments"
       {:post {:handler (fn [{{:keys [id]} :path-params :keys [multipart]}]
                          (let [metadata-error (sc/check {:name      (sc/eq "metadata")
                                                          :mime-type (sc/eq "application/json")
                                                          :encoding  (sc/eq "UTF-8")
                                                          :content   AttachmentMetadata}
                                                         (update (first multipart) :content json/decode true))
                                file-error (sc/check {:name      (sc/eq "file")
                                                      :mime-type sc/Str
                                                      :content   InputStream}
                                                     (second multipart))]
                            (if-let [validation-error (or metadata-error file-error)]
                              {:status 400, :body validation-error}
                              (let [attachment {:metadata (-> multipart (get-in [0 :content]) (json/decode true))}]
                                (swap! allu-state update-in [:applications id :attachments] (fnil conj []) attachment)
                                {:status 200, :body ""}))))}}]]]

    ["applicationhistory"
     {:middleware [json-request json-response]
      :post       {:handler (fn [{{:keys [applicationIds]} :body}]
                              {:status 200
                               :body   (map (fn [id]
                                              {:applicationId     id
                                               :events            (get-in @allu-state
                                                                          [:applications (str id) :status-changes])
                                               :supervisionEvents []})
                                            applicationIds)})}}]

    ["placementcontracts"
     (create-application-route allu-state PlacementContract)
     ["/:id" {:middleware [(check-allu-id allu-state)]}
      (update-application-route allu-state PlacementContract)
      (decision-routes "/decision" allu-state)
      ["/contract" {:middleware [(check-pending-on-client allu-state false)]}
       ["/approved" {:post {:handler (fn [{{:keys [id]} :path-params}]
                                       (if-not (get-in @allu-state [:applications id :approved])
                                         (do (swap! allu-state assoc-in [:applications id :approved] true)
                                             {:status 200, :body ""})
                                         {:status 400, :body "Already signed"}))}}]
       (pdf-route "/proposal")
       ["/final" {:get {:handler (fn [{{:keys [id]} :path-params}]
                                   (if (wait-until-in-state-or-timeout allu-state id :approved true)
                                     {:status  200, :body (file->byte-array "dev-resources/test-pdf.pdf"),
                                      :headers {"Content-Type" "application/pdf"}}
                                     {:status 400, :body "Not signed"}))}}]
       (metadata-route allu-state (fn [allu-state id]
                                    {:creationTime  (allu-emit/format-date-time (t/now))
                                     :handler       {:name  "Hannu Helsinki"
                                                     :title "Director"}
                                     :decisionMaker {:name  "Decider Dave"
                                                     :title "Uber-driver"}
                                     :status        (get-in @allu-state [:applications id :status]
                                                            "PROPOSAL")}))]]]

    ["shorttermrentals"
     (create-application-route allu-state ShortTermRental)
     ["/:id" {:middleware [(check-allu-id allu-state)]}
      (update-application-route allu-state ShortTermRental)
      (decision-routes "/decision" allu-state)]]

    ["events"
     (create-application-route allu-state Promotion)
     ["/:id" {:middleware [(check-allu-id allu-state)]}
      (update-application-route allu-state Promotion)
      (decision-routes "/decision" allu-state)]]]])

(defn- mock-router [allu-id]
  (reitit-ring/router (mock-routes allu-id)))

(defn- mock-allu
  "This pretends to be ALLU or more specifically, clj-http talking to ALLU."
  [allu-id]
  (reitit-ring/ring-handler (mock-router allu-id)))

(defn- make-test-router
  "A replacement for `allu/allu-router` that talks to `mock-allu` instead of clj-http and adds some middleware to do
  Midje checks."
  [allu-state]
  (reitit-ring/router (#'allu/routes (mock-allu allu-state) {:save-responses? true, :disable-io? false})
                      {:reitit.middleware/transform #(cons check-imessages-middleware %)}))

;;;; # Actual Tests
;;;; ===================================================================================================================

(defn- transition-adapter [allu-state applicant authority [src dest :as transition]]
  [transition
   (if (= src :canceled)
     (fn [[current _] id]
       (undo-cancellation authority id)
       current)
     (case dest
       :draft (fn [[_ dest] id]
                (return-to-draft authority id "Nolo!")
                dest)
       :open (fn [[_ dest] id] (open applicant id "YOLO") dest)
       :submitted (fn [[_ dest] id]
                    (fill applicant id)
                    (submit applicant id allu-state)
                    dest)
       :sent (fn [[_ dest] id]
               (approve authority id)
               (wait-until-in-state-or-timeout allu-state (id->allu-id @allu-state id) :approved true)
               (let [{:keys [drawings] :as application} (mongo/by-id :applications id)]
                 (fact "Drawings have been cleaned up"
                   drawings => seq
                   drawings => (case (#'lupapalvelu.allu/location-type application)
                                 "fixed" (partial every? :allu-id)
                                 "custom" (partial not-any? :allu-id))))
               dest)
       :canceled (fn [[current dest] id]
                   (if (contains? #{:draft :open} current)
                     (cancel applicant id "Alkoi nolottaa.")
                     (cancel authority id "Nolo!"))
                   dest)
       :complementNeeded (fn [[current _] id]
                           (request-for-complement authority id)
                           current)
       :agreementPrepared (fn [[current dest] id]
                            (cond
                              (= current dest)
                              dest

                              (= current :submitted)
                              (do (fact "fetch contract (dest :agreementPrepared, current :submitted)"
                                    (fetch-contract authority id true) => fail?)
                                  current)

                              :else
                              (let [allu-id (id->allu-id @allu-state id)]
                                (swap! allu-state update-in [:applications allu-id :status-changes] conj
                                       {:applicationIdentifier id
                                        :eventTime             (allu-emit/format-date-time (t/now))
                                        :newStatus             "WAITING_CONTRACT_APPROVAL"})
                                (fact "fetch contract (dest :agreementPrepared, should return ok)"
                                  (fetch-contract authority id) => ok?
                                  (verdict-list authority id)
                                  => (just [(just {:category  "allu-contract"
                                                   :giver     "Hannu Helsinki"
                                                   :legacy?       true
                                                   :proposal?     false
                                                   :replaced?     false
                                                   :published     pos?
                                                   :title         "Sopimusehdotus"
                                                   :modified      pos?
                                                   :id            string?
                                                   :verdict-state :published})]))
                                dest)))
       :agreementSigned (fn [[src dest] id]
                          (let [allu-id (id->allu-id @allu-state id)]
                            (when (= src :agreementPrepared)
                              (sign-contract applicant id)
                              (wait-until-in-state-or-timeout allu-state allu-id :approved true))
                            ;; If we came here from :sent -state this means that the normal proposal-stage has been omitted
                            ;; and we need to approve this manually
                            (when (= src :sent)
                              (swap! allu-state assoc-in [:applications allu-id :approved] true))
                            ;; Let metadata know that we expect metadata for the final contract
                            (swap! allu-state assoc-in [:applications allu-id :status] "FINAL")
                            (swap! allu-state update-in [:applications allu-id :status-changes] conj
                                   {:applicationIdentifier id
                                    :eventTime             (allu-emit/format-date-time (t/now))
                                    :newStatus             "DECISION"})
                            (fact "fetch contract (dest :agreementSigned, should return ok)"
                              (fetch-contract authority id) => ok?
                              (verdict-list authority id) => (just (if (= src :agreementPrepared)
                                                                     [(contains agreement)
                                                                      (contains decision)
                                                                      (contains proposal)]
                                                                     [(contains fast-agreement)
                                                                      (contains decision)])))
                            dest))
       :verdictGiven (fn [[_ dest] id]
                       (let [allu-id (id->allu-id @allu-state id)]
                         (swap! allu-state update-in [:applications allu-id :status-changes] conj
                                {:applicationIdentifier id
                                 :eventTime             (allu-emit/format-date-time (t/now))
                                 :newStatus             "DECISION"})
                         (fact "fetch decision (dest :verdictGiven)"
                           (fetch-contract authority id) => ok?
                           (verdict-list authority id) => (just [(contains decision)]))
                         dest))))])

(defn- test-transitions [state-graph allu-state applicant authority app-type]
  (facts {:midje/description (str app-type " state transitions")}
    (traverse-state-transitions
      :states state-graph
      :initial-state :draft
      :init! (fn [] (create-and-fill applicant app-type))
      :transition-adapters (into {}
                                 (map (partial transition-adapter allu-state applicant authority))
                                 (state-graph->transitions state-graph))
      :visit-goal 1)))

(sc/with-fn-validation
  (env/with-feature-value :allu true
    (itu/with-local-actions
      ;; Purge everything from possibly existing subscription before this test to have a better chance of the test
      ;; succeeding
      (with-state-changes [(before :facts (do (mount/stop #'allu/allu-pubsub-consumer)
                                              (lip/remove-subscription! allu/allu-jms-queue-name)
                                              (mount/start #'mongo/connection
                                                           #'allu/allu-pubsub-consumer)))]
        (facts "Usage of ALLU integration in commands"
          (mongo/with-db itu/test-db-name
            (lupapalvelu.fixture.core/apply-fixture "minimal")

            (let [allu-state  (atom initial-allu-state)
                  router      (make-test-router allu-state)
                  handler     (comp (reitit-ring/ring-handler router) @#'allu/try-reload-allu-id)
                  login-count (atom 0)
                  login!      (let [actual-login! allu/login!] ; Calling `allu/login!` directly from the fn would diverge.
                                (fn []
                                  (swap! login-count inc)
                                  (actual-login!)))]
              (with-redefs [allu/allu-router          router
                            allu/allu-request-handler handler
                            allu/login!               login!]
                (test-transitions full-sijoitussopimus-state-graph allu-state pena raktark-helsinki "sijoitussopimus")
                (test-transitions full-allu-state-graph allu-state pena raktark-helsinki "lyhytaikainen-maanvuokraus")
                (test-transitions full-allu-state-graph allu-state pena raktark-helsinki "promootio")
                (fact "Login has been done a reasonable number of times"
                  @#'allu/current-jwt =not=> (env/value :allu :jwt)
                  @login-count => (partial >= 2)) ; One for sync, one for JMS queue handler.

                (let [old-id-counter (:id-counter @allu-state)]
                  (fact "ALLU integration disabled for"
                    (fact "Non-Helsinki sijoituslupa"
                      (let [{:keys [id]} (itu/create-local-app pena :operation (ssg/generate SijoituslupaOperation)) => ok?]
                        (itu/command pena :submit-application :id id) => ok?))

                    (fact "Helsinki non-sijoituslupa"
                      (let [{:keys [id]} (itu/create-local-app pena
                                                               :operation "pientalo"
                                                               :x "385770.46" :y "6672188.964"
                                                               :address "Kaivokatu 1"
                                                               :propertyId "09143200010023") => ok?]
                        (itu/command pena :submit-application :id id) => ok?
                        (itu/command raktark-helsinki :approve-application :id id :lang "fi") => ok?))

                    (:id-counter @allu-state) => (partial = old-id-counter)))))))))))
