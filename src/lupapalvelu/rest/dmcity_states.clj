(ns lupapalvelu.rest.dmcity-states
  "Implements a states API for dmCity.

  The dmCity system keeps a record of various dates and times
  associated with each application. This API allows dmCity to report
  these \"states\" to Lupapiste. Each state message records the whole
  state of the application and replaces the earlier state (if any) in
  the database."
  (:require [clojure.set :as set]
            [lupapalvelu.logging :as log]
            [lupapalvelu.rest.schemas :as rest-schemas]
            [lupapalvelu.rest.util :as rest-util]
            [lupapalvelu.states :as states]
            [monger.operators :refer [$set]]
            [ring.swagger.json-schema :as rjs]
            [ring.util.http-status :as http-status]
            [sade.core :as sade]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [schema-tools.coerce :as stc]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(def states
  "Available states and their associated data.
   Note that these are listed according to the original excel spec."
  [;; Hakemus kirjattu
   {:id    :submitted-date
    :field :date}
   ;; Toimitusmääräys annettu
   {:id    :order-date
    :field :date}
   ;; Päätöksen päivämäärä
   {:id    :decision-date
    :field :date}
   ;; Vireilletulopäivä
   {:id    :pending-date
    :field :date}
   ;; Nähtäville asettamisen päiväys
   {:id    :display-decision-date
    :field :date}
   ;; Nähtävilläolon alkaminen
   {:id    :display-start-date
    :field :date}
   ;; Nähtävilläolon päättyminen
   {:id    :display-end-date
    :field :date}
   ;; Hyväksymispäivä
   {:id    :acceptance-date
    :field :date}
   ;; Voimaantulopäivä
   {:id    :apply-date
    :field :date}
   ;; Maastotyöt tehty
   {:id    :survey-finished
    :field :date}
   ;; Alkukokous
   {:id    :initial-meeting
    :field :datetime}
   ;; Loppukokous
   {:id    :final-meeting
    :field :date}
   ;; Päättymispäivä
   {:id    :end-date
    :field :date}
   ;; Valitusaika
   {:id    :appeal-period
    :field :enabled}
   ;; Tarkastettavaksi
   {:id    :validation-required
    :field :enabled}
   ;; Rekisteröitäväksi
   {:id    :registration-required
    :field :enabled}
   ;; Rekisteröity
   {:id    :registered
    :field :date}
   ;; Asemakaavan vireilletulo
   {:id    :plan-pending-date
    :field :date}
   ;; Mielipiteiden esittämisaika päättyy
   {:id    :plan-draft-comments-end-date
    :field :date}
   ;; Päätös kaavaluonnoksen nähtäville asettamisesta
   {:id    :plan-draft-display-decision-date
    :field :date}
   ;; Kaavaluonnoksen nähtävillä olo alkaa
   {:id    :plan-draft-display-start-date
    :field :date}
   ;; Kaavaluonnoksen nähtävillä olo päättyy
   {:id    :plan-draft-display-end-date
    :field :date}
   ;; Päätös kaavaehdotuksen nähtäville asettamisesta
   {:id    :plan-proposal-display-decision-date
    :field :date}
   ;; Kaavaehdotuksen nähtävillä olo alkaa
   {:id    :plan-proposal-display-start-date
    :field :date}
   ;; Kaavaehdotuksen nähtävillä olo päättyy
   {:id    :plan-proposal-display-end-date
    :field :date}
   ;; Lausuntojen ja muistutusten käsittely lautakunnassa
   {:id    :statements-board-date
    :field :date}
   ;; Lausuntojen ja muistutusten käsittely kaupunginhallituksessa
   {:id    :statements-city-date
    :field :date}
   ;; Asemakaavan hyväksyminen
   {:id    :plan-accepted-date
    :field :date}
   ;; Asemakaava on kokonaan tai osittain voimassa
   {:id    :plan-enforced-date
    :field :date}])

;;; Basic HTTP request validation

(sc/defschema StateName (->> states
                             (map (comp name :id))
                             (apply sc/enum)))

(sc/defschema StateUpdate
  {:state                      StateName
   (sc/optional-key :enabled)  sc/Bool
   (sc/optional-key :date)     (rjs/field ssc/NonBlankStr {:format  "date"
                                                           :example "2023-03-27"})
   (sc/optional-key :datetime) (rjs/field ssc/NonBlankStr {:format  "date-time"
                                                           :example "2023-03-27T13:29:10"})})

(sc/defschema RequestBody {:states [StateUpdate]})

(sc/defschema Request
  (st/merge
    {:application-id rest-schemas/ApplicationId}
    RequestBody))

;;; Data coercion

(sc/defschema State (->> states
                         (map :id)
                         (apply sc/enum)))
(sc/defschema Date (sc/named ssc/Timestamp "date"))
(sc/defschema DateTime (sc/named ssc/Timestamp "datetime"))

(sc/defschema StrictStateUpdate
  {:state                      State
   (sc/optional-key :enabled)  sc/Bool
   (sc/optional-key :date)     Date
   (sc/optional-key :datetime) DateTime})

(def ^:private coercions
  (some-fn stc/json-coercion-matcher
           {Date     (comp date/timestamp
                           (fn [s] (date/zoned-parse s :iso-date)))
            DateTime (comp date/timestamp
                           (fn [s] (date/zoned-parse s :iso-datetime)))}))

(def ^:private state->field (->> states
                                 (map (juxt :id :field))
                                 (into {})))

(defn- check-status-field
  [index status]
  (let [actual   (-> status keys set (disj :state))
        expected (-> status :state state->field hash-set)]
    (when (not= expected actual)
      (concat
        (->> (set/difference expected actual)
             (map (fn [missing]
                    (rest-util/make-error
                      {:error "missing-required-key"}
                      :states index missing))))
        (->> (set/difference actual expected)
             (map (fn [unexpected]
                    (rest-util/make-error
                      {:error "disallowed-key"}
                      :states index unexpected))))))))

(defn- check-status-fields
  [{::keys [states] :as ctx}]
  (let [add-errors (fnil (fn [m errors]
                           (update m :errors into errors))
                         {:title  "Status type and fields do not match"
                          :errors []})]
    (->> states
         (keep-indexed check-status-field)
         (reduce
           (fn [ctx errors] (update ctx :error add-errors errors))
           ctx))))

(defn- with-application-state-updates
  [{::keys [states]
    :keys  [request-timestamp]
    :as    ctx}]
  (assoc ctx ::updates {$set {:external-state {:received request-timestamp
                                               :source   "dmcity"
                                               :states   states}}}))

(def ^:private process
  (rest-util/do-all rest-util/check-authentication-step
                    rest-util/check-authorization-step
                    (rest-util/check-data-step
                      {:src-key   :request
                       :checker   (sc/checker Request)
                       :err-title "Invalid HTTP parameters"})
                    (rest-util/coerce-data-step
                      {:src-key   [:request :states]
                       :dst-key   ::states
                       :coercer   (stc/coercer [StrictStateUpdate] coercions)
                       :err-title "Failed to validate states data"
                       :err-path  [:states]})
                    check-status-fields
                    (rest-util/find-application-from-mongo-step
                      {:application-id-key  [:request :application-id]
                       :user-key            :user
                       :timestamp-key       :request-timestamp
                       :dst-application-key ::application
                       :dst-command-key     ::command})
                    (rest-util/check-application-authorization-step
                      {:application-id-key   [:request :application-id]
                       :command-key          ::command
                       :required-permissions #{:verdict/publish}})
                    (rest-util/check-application-state-step
                      {:command-key          ::command
                       :permitted-app-states states/post-submitted-states})
                    with-application-state-updates
                    (rest-util/commit-queued-updates-step!
                      {:command-key ::command
                       :updates-key ::updates})))

(defn- on-error
  [{:keys [title errors status]
    :or   {errors []
           status http-status/bad-request}}]
  (log/errorf "Failed to process status from dmCity: %d:'%s' %s"
              status title errors)
  (sade/fail title :status status :errors errors))

(defn- on-success
  [_ctx]
  (log/info "Successfully processed status from dmCity.")
  (sade/ok :status http-status/created))

(defn upsert
  "Inserts (or replaces) a status message into an application"
  [{{:keys [application-id]} :request
    {user-id :id}            :user
    :as                      ctx}]
  {:pre [(map? (:system ctx))]}
  (log/with-logging-context
    ;; Note that we haven't yet validated the application id
    {:applicationId application-id
     :userId        user-id}
    (log/info "Processing status from dmCity..")
    (let [ctx (process ctx)]
      (if-some [error (:error ctx)]
        (on-error error)
        (on-success ctx)))))
