(ns lupapalvelu.backing-system.allu-itest
  "Integration tests for ALLU integration. Using local (i.e. not over HTTP) testing style."
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [monger.operators :refer [$set]]
            [reitit.ring :as reitit-ring]
            [schema.core :as sc]
            [taoensso.timbre :refer [warn]]
            [sade.core :refer [def- ok?]]
            [sade.env :as env]
            [sade.files :refer [with-temp-file]]
            [sade.schema-generators :as ssg]
            [lupapalvelu.attachment :refer [get-attachment-file!]]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.document.tools :refer [doc-name]]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]

            [midje.sweet :refer [facts fact => contains]]
            [lupapalvelu.itest-util :as itu :refer [pena pena-id raktark-helsinki]]

            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.allu.schemas :refer [SijoituslupaOperation PlacementContract FileMetadata]]
            [lupapalvelu.domain :as domain])
  (:import [java.io InputStream]))

;;; TODO: Ensure that errors from ALLU don't break the application process
;;; TODO: Sijoituslupa

;;;; Nano-framework :P for Model-Based Testing
;;;; ===================================================================================================================

(defn- state-graph->transitions [states]
  (mapcat (fn [[state succs]] (map #(vector state %) succs)) states))

(defn- transitions-todo [visited-total soured visit-goal]
  (->> visited-total
       (filter (fn [[transition visit-count]] (and (not (soured transition)) (< visit-count visit-goal))))
       (map key)))

(defn- probe-next-transition [states visit-goal visited-total soured current-state]
  ;; This code is not so great but does the job for now.
  (letfn [(useful [current visited]
            (let [[usefuls visited]
                  (reduce (fn [[transitions visited] succ]
                            (let [transition [current succ]
                                  visited* (conj visited transition)]
                              (cond
                                (soured transition) [transitions visited*]
                                (< (get visited-total transition) visit-goal) [(conj transitions transition) visited*]
                                (not (visited transition)) (let [[transition* visited*] (useful succ visited*)]
                                                             (if transition*
                                                               [(conj transitions transition) visited*]
                                                               [transitions visited*]))
                                :else [transitions visited*])))
                          [[] visited] (get states current))]
              [(if (seq usefuls) (rand-nth usefuls) nil)
               visited]))]
    (first (useful current-state #{}))))

(defn- traverse-state-transitions [& {:keys [states initial-state init! transition-adapters visit-goal]}]
  (let [initial-visited (zipmap (state-graph->transitions states) (repeat 0))]
    (loop [visited-total initial-visited
           visited visited-total
           soured #{}
           current-state initial-state
           user-state (init!)]
      (if-let [[_ next-state :as transition] (probe-next-transition states visit-goal visited-total soured
                                                                    current-state)]
        (if-let [transition-adapter (get transition-adapters transition)]
          (let [next-state* (transition-adapter transition user-state)]
            (if (or (not= next-state* current-state)        ; Led somewhere else?
                    (= next-state* next-state))             ; Wasn't supposed to!
              (recur (update visited-total transition inc) (update visited transition inc) soured
                     next-state* user-state)
              (recur (update visited-total transition inc)
                     (update visited transition inc)
                     (conj soured transition)               ; Let's not try that again.
                     next-state*
                     user-state)))
          (do (warn "No adapter provided for" transition)
              (recur (update visited-total transition inc) (update visited transition inc) soured
                     next-state user-state)))
        (when (seq (transitions-todo visited-total soured visit-goal))
          (recur visited-total initial-visited soured initial-state (init!)))))))

;;;; Refutation Utilities
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

(defn- nullify-doc-ids [doc]
  (-> doc
      (assoc-in [:data :henkilo :userId :value] nil)
      (assoc-in [:data :yritys :companyId :value] nil)))

;; HACK: undo-cancellation bypasses the state graph so we add [:cancel *] arcs here:
(defn- add-canceled->* [state-graph]
  (update state-graph :canceled
          into
          (comp (filter (fn [[_ succs]] (some (partial = :canceled) succs)))
                (map key))
          state-graph))

(defn- create-and-fill-placement-app [apikey permitSubtype]
  (let [{:keys [id] :as response} (itu/create-local-app apikey
                                                        :operation (ssg/generate SijoituslupaOperation)
                                                        :x "385770.46" :y "6672188.964"
                                                        :address "Kaivokatu 1"
                                                        :propertyId "09143200010023")
        _ (fact "placement application created succesfully" response => ok?)
        documents [(nullify-doc-ids (ssg/generate (dds/doc-data-schema "hakija-ya" true)))
                   (ssg/generate (dds/doc-data-schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa" true))
                   (ssg/generate (dds/doc-data-schema "yleiset-alueet-maksaja" true))]]
    (mongo/update-by-id :applications id {$set {:permitSubtype permitSubtype, :documents documents}})
    id))

(defn- upload-attachment
  ([apikey app-id] (upload-attachment apikey app-id nil))
  ([apikey app-id attachment]
   (let [filename "dev-resources/test-attachment.txt"]
     ;; HACK: Have to use a temp file as :upload-attachment expects to get one and deletes it in the end.
     (with-temp-file file
       (io/copy (io/file filename) file)
       (let [description "Test file"
             {:keys [attachmentId] :as upload-res} (itu/local-command apikey :upload-attachment :id app-id
                                                                      :attachmentId (:id attachment)
                                                                      :attachmentType {:type-group "muut"
                                                                                       :type-id "muu"}
                                                                      :locked false
                                                                      :group {} :filename filename :tempfile file
                                                                      :size (.length file))
             _ (fact "upload attachment" upload-res => ok?)
             _ (fact "set-attachment-meta"
                 (itu/local-command apikey :set-attachment-meta :id app-id :attachmentId attachmentId
                                    :meta {:contents description}) => ok?)]
         (fact "add-comment"
           (itu/local-command apikey :add-comment :id app-id :text "Added my test text file."
                              :target {:type "application"} :roles ["applicant" "authority"]) => ok?)
         attachmentId)))))

(defn- open [apikey app-id msg]
  (fact ":draft -> :open"
    (itu/local-command apikey :add-comment :id app-id :text msg
                       :target {:type "application"} :roles ["applicant" "authority"]) => ok?))

(defn- submit [apikey app-id]
  (fact "submit application"
    (itu/local-command apikey :submit-application :id app-id) => ok?))

(defn- fill [apikey app-id]
  (let [{[attachment] :attachments :keys [documents]} (domain/get-application-no-access-checking app-id)
        {descr-id :id} (first (filter #(= (doc-name %) "yleiset-alueet-hankkeen-kuvaus-sijoituslupa")
                                      documents))
        {applicant-id :id} (first (filter #(= (doc-name %) "hakija-ya") documents))]
    (fact "fill application"
      (itu/local-command apikey :update-doc :id app-id :doc descr-id :updates [["kayttotarkoitus" "tuijottelu"]]) => ok?
      (itu/local-command apikey :save-application-drawings :id app-id :drawings drawings) => ok?

      (itu/local-command apikey :set-user-to-document :id app-id :documentId applicant-id
                         :userId pena-id :path "henkilo") => ok?

      (itu/local-command apikey :set-current-user-to-document :id app-id :documentId applicant-id :path "henkilo") => ok?

      (itu/local-command apikey :set-company-to-document :id app-id :documentId applicant-id
                         :companyId "esimerkki" :path "yritys") => ok?
      (let [user (usr/get-user-by-id pena-id)]
        (itu/local-command apikey :update-doc :id app-id :doc applicant-id
                           :updates [["yritys.yhteyshenkilo.henkilotiedot.etunimi" (:firstName user)]
                                     ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" (:lastName user)]
                                     ["yritys.yhteyshenkilo.yhteystiedot.email" (:email user)]
                                     ["yritys.yhteyshenkilo.yhteystiedot.puhelin" (:phone user)]])))

    (upload-attachment apikey app-id attachment)))

(defn- approve [apikey app-id]
  (fact "approve application"
    (itu/local-command apikey :approve-application :id app-id :lang "fi") => ok?

    ;; HACK: Testing move-attachments-to-backing-system here for lack of a better place:
    (itu/local-command apikey :move-attachments-to-backing-system :id app-id :lang "fi"
                       :attachmentIds [(upload-attachment apikey app-id)]) => ok?))

(defn- return-to-draft [apikey app-id msg]
  (fact "return to draft"
    (itu/local-command apikey :return-to-draft :id app-id :text msg :lang "fi") => ok?))

(defn- request-for-complement [apikey id]
  (fact "request for complement"
    (itu/local-command apikey :request-for-complement :id id)
    => (contains {:ok     false
                  :text   "error.integration.unsupported-action"
                  :action "request-for-complement"})))

(defn- cancel [apikey app-id msg]
  (fact "cancel application"
    (itu/local-command apikey :cancel-application :id app-id :text msg :lang "fi") => ok?))

(defn- undo-cancellation [apikey app-id]
  (fact "undo cancellation"
    (itu/local-command apikey :undo-cancellation :id app-id)
    => (contains {:ok     false
                  :text   "error.integration.unsupported-action"
                  :action "undo-cancellation"})))

;;;; Mock Handler
;;;; ===================================================================================================================

(defn- check-response-ok-middleware [handler]
  (fn [request]
    (let [response (handler request)]
      (fact "response is successful" response => (comp #{200 201} :status))
      response)))

(defn- check-imessages-middleware [handler]
  (fn [{{:keys [application]} ::allu/command :as request}]
    (let [imsg-query (fn [direction]
                       {:partner        "allu"
                        :messageType    (s/join \. (map name (-> request reitit-ring/get-match :data :name)))
                        :direction      direction
                        :status         "done"
                        :application.id (:id application)})
          res (handler request)]
      (fact "integration messages are saved"
        (mongo/any? :integration-messages (imsg-query "out")) => true
        (mongo/any? :integration-messages (imsg-query "in")) => true)
      res)))

(def- mock-jwt "foo.bar.baz")

(defn- mock-routes [allu-state]
  [["/login" {:post {:handler (fn [_] {:status 200, :body (json/encode mock-jwt)})}}]

   ["/"
    ["applications"
     ["/:id/cancelled"
      {:put {:handler (fn [{{:keys [id]} :path-params :keys [headers]}]
                        (if (= (get headers "authorization") (str "Bearer " mock-jwt))
                          (if (contains? (:applications @allu-state) id)
                            (do (swap! allu-state update :applications dissoc id)
                                {:status 200, :body ""})
                            {:status 404, :body (str "Not Found: " id)})
                          {:status 401, :body "Unauthorized"}))}}]

     ["/:id/attachments"
      {:post {:handler (fn [{{:keys [id]} :path-params :keys [headers multipart]}]
                         (if (= (get headers "authorization") (str "Bearer " mock-jwt))
                           (let [metadata-error (sc/check {:name      (sc/eq "metadata")
                                                           :mime-type (sc/eq "application/json")
                                                           :encoding  (sc/eq "UTF-8")
                                                           :content   FileMetadata}
                                                          (update (first multipart) :content json/decode true))
                                 file-error (sc/check {:name      (sc/eq "file")
                                                       :mime-type sc/Str
                                                       :content   InputStream}
                                                      (second multipart))]
                             (if-let [validation-error (or metadata-error file-error)]
                               {:status 400, :body validation-error}
                               (if (contains? (:applications @allu-state) id)
                                 (let [attachment {:metadata (-> multipart (get-in [0 :content]) (json/decode true))}]
                                   (swap! allu-state update-in [:applications id :attachments] (fnil conj []) attachment)
                                   {:status 200, :body ""})
                                 {:status 404, :body (str "Not Found: " id)})))
                           {:status 401, :body "Unauthorized"}))}}]]


    ["placementcontracts"
     [""
      {:post {:handler (fn [{:keys [headers body]}]
                         (if (= (get headers "authorization") (str "Bearer " mock-jwt))
                           (let [placement-contract (json/decode body true)]
                             (if-let [validation-error (sc/check PlacementContract placement-contract)]
                               {:status 400, :body validation-error}
                               (let [{:keys [id-counter]}
                                     (swap! allu-state (fn [{:keys [id-counter] :as state}]
                                                         (-> state
                                                             (update :id-counter inc)
                                                             (update :applications assoc (str id-counter)
                                                                     placement-contract))))]
                                 {:status 200, :body (str (dec id-counter))})))
                           {:status 401, :body "Unauthorized"}))}}]

     ["/:id"
      {:put {:handler (fn [{{:keys [id]} :path-params :keys [headers body]}]
                        (if (= (get headers "authorization") (str "Bearer " mock-jwt))
                          (let [placement-contract (json/decode body true)]
                            (if-let [validation-error (sc/check PlacementContract placement-contract)]
                              {:status 400, :body validation-error}
                              (if (contains? (:applications @allu-state) id)
                                (if (get-in @allu-state [:applications id :pendingOnClient])
                                  (do (swap! allu-state assoc-in [:applications id] placement-contract)
                                      {:status 200, :body id})
                                  {:status 403, :body (str id " is not pendingOnClient")})
                                {:status 404, :body (str "Not Found: " id)})))
                          {:status 401, :body "Unauthorized"}))}}]]]])

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
  (reitit-ring/router (#'allu/routes false (mock-allu allu-state))
                      {:reitit.middleware/transform (fn [middlewares]
                                                      (-> [check-imessages-middleware]
                                                          (into middlewares)
                                                          (conj check-response-ok-middleware)))}))

;;;; Actual Tests
;;;; ===================================================================================================================

;;; TODO: (sc/with-fn-validation ...)

(env/with-feature-value :allu true
  (mongo/connect!)

  (facts "Usage of ALLU integration in commands"
    (mongo/with-db itu/test-db-name
      (lupapalvelu.fixture.core/apply-fixture "minimal")

      (let [initial-allu-state {:id-counter 0, :applications {}}
            allu-state (atom initial-allu-state)
            full-sijoitussopimus-state-graph (->> states/ya-sijoitussopimus-state-graph
                                                  add-canceled->*
                                                  ;; :complementNeeded should be unreachable:
                                                  (into {} (remove (fn [[src _]] (= src :complementNeeded)))))
            router (make-test-router allu-state)]
        (with-redefs [allu/allu-router router
                      allu/allu-request-handler (reitit-ring/ring-handler router)]
          (facts "state transitions"
            (traverse-state-transitions
              :states full-sijoitussopimus-state-graph
              :initial-state :draft
              :init! (fn [] (create-and-fill-placement-app pena "sijoitussopimus"))
              :transition-adapters (into {} (map (fn [[src dest :as transition]]
                                                   [transition
                                                    (if (= src :canceled)
                                                      (fn [[current _] id]
                                                        (undo-cancellation raktark-helsinki id)
                                                        current)
                                                      (case dest
                                                        :draft (fn [[_ dest] id]
                                                                 (return-to-draft raktark-helsinki id "Nolo!")
                                                                 dest)
                                                        :open (fn [[_ dest] id] (open pena id "YOLO") dest)
                                                        :submitted (fn [[_ dest] id]
                                                                     (fill pena id)
                                                                     (submit pena id)
                                                                     dest)
                                                        :sent (fn [[_ dest] id] (approve raktark-helsinki id) dest)
                                                        :canceled (fn [[current dest] id]
                                                                    (if (contains? #{:draft :open} current)
                                                                      (cancel pena id "Alkoi nolottaa.")
                                                                      (cancel raktark-helsinki id "Nolo!"))
                                                                    dest)
                                                        :complementNeeded (fn [[current _] id]
                                                                            (request-for-complement raktark-helsinki id)
                                                                            current)
                                                        (fn [[_ dest :as transition] _]
                                                          (warn "TODO:" transition)
                                                          dest)))]))
                                         (state-graph->transitions full-sijoitussopimus-state-graph))
              :visit-goal 1))

          ;;; TODO: agreementPrepared/Signed (LPK-3888)

          (let [old-id-counter (:id-counter @allu-state)]
            (fact "ALLU integration disabled for"
              (fact "Non-Helsinki sijoituslupa"
                (let [{:keys [id]} (itu/create-local-app pena :operation (ssg/generate SijoituslupaOperation)) => ok?]
                  (itu/local-command pena :submit-application :id id) => ok?))

              (fact "Helsinki non-sijoituslupa"
                (let [{:keys [id]} (itu/create-local-app pena
                                                         :operation "pientalo"
                                                         :x "385770.46" :y "6672188.964"
                                                         :address "Kaivokatu 1"
                                                         :propertyId "09143200010023") => ok?]
                  (itu/local-command pena :submit-application :id id) => ok?
                  (itu/local-command raktark-helsinki :approve-application :id id :lang "fi") => ok?))

              (:id-counter @allu-state) => (partial = old-id-counter))))))))
