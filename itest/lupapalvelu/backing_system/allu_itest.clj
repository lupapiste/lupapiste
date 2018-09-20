(ns lupapalvelu.backing-system.allu-itest
  "Integration tests for ALLU integration. Using local (i.e. not over HTTP) testing style."
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [compojure.core :refer [routes POST PUT]]
            [compojure.route :refer [not-found]]
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

            [lupapalvelu.backing-system.allu :as allu :refer [PlacementContract]]
            [lupapalvelu.domain :as domain])
  (:import [java.io InputStream]))

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
                                                        :operation (ssg/generate allu/SijoituslupaOperation)
                                                        :x "385770.46" :y "6672188.964"
                                                        :address "Kaivokatu 1"
                                                        :propertyId "09143200010023")
        _ (fact "placement application created succesfully" response => ok?)
        documents [(nullify-doc-ids (ssg/generate (dds/doc-data-schema "hakija-ya" true)))
                   (ssg/generate (dds/doc-data-schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa" true))
                   (ssg/generate (dds/doc-data-schema "yleiset-alueet-maksaja" true))]]
    (mongo/update-by-id :applications id {$set {:permitSubtype permitSubtype, :documents documents}})
    id))

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

    (fact "upload attachments"
      (let [filename "dev-resources/test-attachment.txt"]
        ;; HACK: Have to use a temp file as :upload-attachment expects to get one and deletes it in the end.
        (with-temp-file file
          (io/copy (io/file filename) file)
          (let [description "Test file"
                _ (itu/local-command apikey :upload-attachment :id app-id :attachmentId (:id attachment)
                                     :attachmentType {:type-group "muut", :type-id "muu"} :group {}
                                     :filename filename :tempfile file :size (.length file)) => ok?
                _ (itu/local-command apikey :set-attachment-meta :id app-id :attachmentId (:id attachment)
                                     :meta {:contents description}) => ok?]
            (itu/local-command apikey :add-comment :id app-id :text "Added my test text file."
                               :target {:type "application"} :roles ["applicant" "authority"]) => ok?))))))

(defn- approve [apikey app-id]
  (fact "approve application"
    (itu/local-command apikey :approve-application :id app-id :lang "fi")) => ok?)

(defn- return-to-draft [apikey app-id msg]
  (fact "return to draft"
    (itu/local-command apikey :return-to-draft :id app-id :text msg :lang "fi") => ok?))

(defn- request-for-complement [apikey id]
  (fact "request for complement"
    (itu/local-command apikey :request-for-complement :id id)
    => (contains {:ok   false
                  :text "error.integration.unsupported-action"
                  :action "request-for-complement"})))

(defn- cancel [apikey app-id msg]
  (fact "cancel application"
    (itu/local-command apikey :cancel-application :id app-id :text msg :lang "fi") => ok?))

(defn- undo-cancellation [apikey app-id]
  (fact "undo cancellation"
    (itu/local-command apikey :undo-cancellation :id app-id)
    => (contains {:ok   false
                  :text "error.integration.unsupported-action"
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

(defn- make-test-handler [allu-state]
  (reitit-ring/ring-handler
    (reitit-ring/router
      (#'allu/routes
        false
        (routes
          (PUT "/applications/:id/cancelled" [id :as {:keys [headers]}]
            (if (= (get headers "authorization") (str "Bearer " (env/value :allu :jwt)))
              (if (contains? (:applications @allu-state) id)
                (do (swap! allu-state update :applications dissoc id)
                    {:status 200, :body ""})
                {:status 404, :body (str "Not Found: " id)})
              {:status 401, :body "Unauthorized"}))

          (POST "/placementcontracts" {:keys [headers body]}
            (if (= (get headers "authorization") (str "Bearer " (env/value :allu :jwt)))
              (let [placement-contract (json/decode body true)]
                (if-let [validation-error (sc/check PlacementContract placement-contract)]
                  {:status 400, :body validation-error}
                  (let [{:keys [id-counter]}
                        (swap! allu-state (fn [{:keys [id-counter] :as state}]
                                            (-> state
                                                (update :id-counter inc)
                                                (update :applications assoc (str id-counter) placement-contract))))]
                    {:status 200, :body (str (dec id-counter))})))
              {:status 401, :body "Unauthorized"}))

          (PUT "/placementcontracts/:id" [id :as {:keys [headers body]}]
            (if (= (get headers "authorization") (str "Bearer " (env/value :allu :jwt)))
              (let [placement-contract (json/decode body true)]
                (if-let [validation-error (sc/check PlacementContract placement-contract)]
                  {:status 400, :body validation-error}
                  (if (contains? (:applications @allu-state) id)
                    (if (get-in @allu-state [:applications id :pendingOnClient])
                      (do (swap! allu-state assoc-in [:applications id] placement-contract)
                          {:status 200, :body id})
                      {:status 403, :body (str id " is not pendingOnClient")})
                    {:status 404, :body (str "Not Found: " id)})))
              {:status 401, :body "Unauthorized"}))

          (POST "/applications/:id/attachments" [id :as {:keys [headers body]}]
            (if (= (get headers "authorization") (str "Bearer " (env/value :allu :jwt)))
              (let [metadata-error (sc/check {:name      (sc/eq "metadata")
                                              :mime-type (sc/eq "application/json")
                                              :encoding  (sc/eq "UTF-8")
                                              :content   @#'allu/FileMetadata}
                                             (update (first body) :content json/decode true))
                    file-error (sc/check {:name      (sc/eq "file")
                                          :mime-type sc/Str
                                          :content   InputStream}
                                         (second body))]
                (if-let [validation-error (or metadata-error file-error)]
                  {:status 400, :body validation-error}
                  (if (contains? (:applications @allu-state) id)
                    (let [attachment {:metadata (-> body (get-in [0 :content]) (json/decode true))}]
                      (swap! allu-state update-in [:applications id :attachments] (fnil conj []) attachment)
                      {:status 200, :body ""})
                    {:status 404, :body (str "Not Found: " id)})))
              {:status 401, :body "Unauthorized"}))

          (not-found "No such route.")))
      {:reitit.middleware/transform (fn [middlewares]
                                      (-> [check-imessages-middleware]
                                          (into middlewares)
                                          (conj check-response-ok-middleware)))})))

;(deftype ConstALLU [cancel-response attach-response creation-response update-response]
;  ALLUApplications
;  (-cancel-application! [_ _ _] cancel-response)
;  ALLUPlacementContracts
;  (-create-placement-contract! [_ _ _] creation-response)
;  (-update-placement-contract! [_ _ _ ] update-response)
;  ALLUAttachments
;  (-send-attachment! [_ _ _] attach-response))

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
                                                  (into {} (remove (fn [[src _]] (= src :complementNeeded)))))]
        (with-redefs [allu/allu-request-handler (make-test-handler allu-state)]
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

          ;;; TODO: move-attachments-to-backing-system
          ;;; TODO: agreementPrepared/Signed
          ;;; TODO: Ensure that errors from ALLU don't break the application process

          (let [old-id-counter (:id-counter @allu-state)]
            (fact "ALLU integration disabled for"
              (fact "Non-Helsinki sijoituslupa"
                (let [{:keys [id]} (itu/create-local-app pena :operation (ssg/generate allu/SijoituslupaOperation)) => ok?]
                  (itu/local-command pena :submit-application :id id) => ok?))

              (fact "Helsinki non-sijoituslupa"
                (let [{:keys [id]} (itu/create-local-app pena
                                                         :operation "pientalo"
                                                         :x "385770.46" :y "6672188.964"
                                                         :address "Kaivokatu 1"
                                                         :propertyId "09143200010023") => ok?]
                  (itu/local-command pena :submit-application :id id) => ok?
                  (itu/local-command raktark-helsinki :approve-application :id id :lang "fi") => ok?))

              (:id-counter @allu-state) => (partial = old-id-counter)))))

      #_(let [initial-allu-state {:id-counter 0, :applications {}}
              allu-state (atom initial-allu-state)
              failure-counter (atom 0)]
          (mount/start-with {#'allu/allu-instance
                             (->CheckingALLU (->MessageSavingALLU (->GetAttachmentFiles (->AtomMockALLU allu-state))))})

          (binding [allu-fail! (fn [text info-map]
                                 (fact "error text" text => :error.allu.http)
                                 (fact "response is 4**" info-map => http/client-error?)
                                 (swap! failure-counter inc))]
            (fact "enabled and sending correctly to ALLU for Helsinki YA sijoituslupa and sijoitussopimus."
              (let [{:keys [id]} (create-and-fill-placement-app apikey "sijoituslupa") => ok?
                    {[attachment] :attachments :keys [documents]} (domain/get-application-no-access-checking id)
                    {descr-id :id} (first (filter #(= (doc-name %) "yleiset-alueet-hankkeen-kuvaus-sijoituslupa")
                                                  documents))
                    {applicant-id :id} (first (filter #(= (doc-name %) "hakija-ya") documents))]
                (let [filename "dev-resources/test-attachment.txt"]
                  ;; HACK: Have to use a temp file as :upload-attachment expects to get one and deletes it in the end.
                  (with-temp-file file
                    (io/copy (io/file filename) file)
                    (let [description "Test file"
                          description* "The best file"
                          {[attachment] :attachments} (domain/get-application-no-access-checking id)
                          expected-attachments [{:metadata {:name        description
                                                            :description (localize "fi" :attachmentType
                                                                                   (-> attachment :type :type-group)
                                                                                   (-> attachment :type :type-id))
                                                            :mimeType    (-> attachment :latestVersion :contentType)}}
                                                {:metadata {:name        ""
                                                            :description (localize "fi" :attachmentType :muut
                                                                                   :keskustelu)
                                                            :mimeType    "application/pdf"}}]
                          expected-attachments* (conj expected-attachments
                                                      (assoc-in (first expected-attachments)
                                                                [:metadata :name] description*))]

                      (io/copy (io/file filename) file)
                      ;; Upload another attachment for :move-attachments-to-backing-system to send:
                      (itu/local-command raktark-helsinki :upload-attachment :id id :attachmentId (:id attachment)
                                         :attachmentType {:type-group "muut", :type-id "muu"} :group {}
                                         :filename filename :tempfile file :size (.length file)) => ok?
                      (itu/local-command raktark-helsinki :set-attachment-meta :id id :attachmentId (:id attachment)
                                         :meta {:contents description*}) => ok?
                      (itu/local-command raktark-helsinki :move-attachments-to-backing-system :id id :lang "fi"
                                         :attachmentIds [(:id attachment)]) => ok?

                      (-> (:applications @allu-state) first val :attachments) => expected-attachments*)))))

            (fact "error responses from ALLU produce `fail!`ures"
              (mount/start-with {#'allu/allu-instance
                                 (->MessageSavingALLU (->GetAttachmentFiles
                                                        (->ConstALLU {:status 200} {:status 200}
                                                                     {:status 400, :body "Your data was bad."} nil)))})
              (let [{:keys [id]} (create-and-fill-placement-app apikey "sijoituslupa") => ok?]
                (itu/local-command apikey :submit-application :id id)
                @failure-counter => 1)

              (reset! failure-counter 0)

              (mount/start-with {#'allu/allu-instance
                                 (->MessageSavingALLU (->GetAttachmentFiles
                                                        (->ConstALLU {:status 200} {:status 200}
                                                                     {:status 401, :body "You are unauthorized."} nil)))})
              (let [{:keys [id]} (create-and-fill-placement-app apikey "sijoitussopimus") => ok?]
                (itu/local-command apikey :submit-application :id id)
                @failure-counter => 1)))))))
