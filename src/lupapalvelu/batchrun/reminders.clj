(ns lupapalvelu.batchrun.reminders
  "Email reminders sent by batchrun."
  (:require [lupapalvelu.action :refer :all]
            [lupapalvelu.application :as app]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.neighbors-api :as neighbors]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.states :as states]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.dummy-email-server]
            [sade.util :as util]
            [sade.validators :as v]))

(defn- older-than [timestamp] {$lt timestamp})

(defn- newer-than [timestamp] {$gt timestamp})

;; Email definition for the "open info request reminder"

(defn- oir-reminder-base-email-model [{{token :token-id created-date :created-date} :data
                                       :keys                                        [system]
                                       :as                                          command}
                                      _ recipient]
  (merge (notifications/create-app-model command nil recipient)
         {:link                (fn [lang] (str (components/system->host system) "/api/raw/openinforequest?token-id=" token "&lang=" (name lang)))
          :inforequest-created created-date}))

(def- oir-reminder-email-conf
  {:recipients-fn  notifications/from-data
   :subject-key    "open-inforequest-reminder"
   :model-fn       oir-reminder-base-email-model
   :application-fn (fn [{id :id}] (mongo/by-id :applications id))})

(notifications/defemail :reminder-open-inforequest oir-reminder-email-conf)

;; Email definition for the "Neighbor reminder"

(notifications/defemail :reminder-neighbor (assoc neighbors/email-conf :subject-key "neighbor-reminder"))

;; Email definition for the "Request statement reminder"

(defn- statement-reminders-email-model [{{:keys [created-date statement]} :data
                                         :keys                            [application system]
                                         :as                              command} _ recipient]
  (merge (notifications/create-app-model command nil recipient)
         {:link                      #(notifications/get-application-link system application "/statement" % recipient)
          :statement-request-created created-date
          :due-date                  #(some->> (:dueDate statement)
                                               date/finnish-date
                                               (i18n/localize-and-fill % :email.statement.due-date))
          :message                   (:saateText statement)}))

(notifications/defemail :reminder-request-statement
  {:recipients-fn :recipients
   :subject-key   "statement-request-reminder"
   :model-fn      statement-reminders-email-model})

;; Email definition for the "Statement due date reminder"

(notifications/defemail :reminder-statement-due-date
  {:recipients-fn :recipients
   :subject-key   "reminder-statement-due-date"
   :model-fn      statement-reminders-email-model})

;; Email definition for the "Application state reminder"

(notifications/defemail :reminder-application-state
  {:subject-key   "active-application-reminder"
   :recipients-fn notifications/application-state-reminder-recipients-fn})

;; Email definition for the "YA work time is expiring"

(defn- ya-work-time-is-expiring-reminder-email-model [{{work-time-expires-date :work-time-expires-date} :data :as command} _ recipient]
  (assoc
    (notifications/create-app-model command nil recipient)
    :work-time-expires-date work-time-expires-date))

(notifications/defemail :reminder-ya-work-time-is-expiring
  {:subject-key "ya-work-time-is-expiring-reminder"
   :model-fn    ya-work-time-is-expiring-reminder-email-model})

(defn- ts-change [op-fn]
  (-> (date/now) (op-fn) (date/timestamp)))

(defn- ts-week-ago []
  (ts-change #(date/minus % :week)))

(defn- ts-week-from-now []
  (ts-change #(date/plus % :week)))

(defn- ts-months-ago [n]
  (ts-change #(date/minus % :months n)))

(defn- ts-months-from-now [n]
  (ts-change #(date/plus % :months n)))

;; "Lausuntopyynto: Pyyntoon ei ole vastattu viikon kuluessa ja hakemuksen tila on nakyy viranomaiselle tai hakemys jatetty. Lahetetaan viikoittain uudelleen muttei enää määräpäivän jälkeen."
(defn statement-request-reminder [system]
  (let [timestamp-now        (now)
        timestamp-1-week-ago (ts-week-ago)
        apps                 (mongo/snapshot :applications
                                             {:state      {$in ["open" "submitted"]}
                                              :readOnly   {$ne true}
                                              :permitType {$nin ["ARK"]}
                                              :statements {$elemMatch {:requested (older-than timestamp-1-week-ago)
                                                                       :given     nil
                                                                       $or        [{:reminder-sent nil}
                                                                                   {:reminder-sent (older-than timestamp-1-week-ago)}]}}}
                                             [:statements :state :modified :infoRequest :title :address
                                              :municipality :primaryOperation :readOnly])]
    (doseq [app       apps
            statement (:statements app)
            :let      [requested     (:requested statement)
                       due-date      (:dueDate statement)
                       reminder-sent (:reminder-sent statement)]
            :when     (and
                        (nil? (:given statement))
                        (some-> requested (< timestamp-1-week-ago))
                        (or (nil? reminder-sent) (< reminder-sent timestamp-1-week-ago))
                        (or (nil? due-date) (> due-date timestamp-now)))]
      (log/with-logging-context {:applicationId (:id app)
                                 :userId        "batchrun-user"}
        (notifications/notify! :reminder-request-statement
                               {:application app
                                :system      system
                                :recipients  [(usr/get-user-by-email (get-in statement [:person :email]))]
                                :data        {:created-date (date/finnish-date requested :zero-pad)
                                              :statement    statement}})
        (update-application (application->command app)
                            {:statements {$elemMatch {:id (:id statement)}}}
                            {$set {:statements.$.reminder-sent timestamp-now}})))))



;; "Lausuntopyynnon maaraika umpeutunut, mutta lausuntoa ei ole
;; annettu. Muistutus vain kerran."
(defn statement-reminder-due-date [system]
  (let [timestamp-now (now)
        apps          (mongo/snapshot :applications
                                      ;; give-statement states with statement-in-sent-state-allowed pre-check.
                                      {$or         [{:state {$in ["open" "submitted" "complementNeeded"]}}
                                                    {:permitType {$in ["YI" "YL" "YM" "VVVL" "MAL"]}
                                                     :state      "sent"}]
                                       :permitType {$nin ["ARK"]}
                                       :readOnly   {$ne true}
                                       :statements {$elemMatch {:given nil
                                                                $and   [{:dueDate {$exists true}}
                                                                        {:dueDate (older-than timestamp-now)}]
                                                                $or    [{:duedate-reminder-sent {$exists false}}
                                                                        {:duedate-reminder-sent nil}]}}}
                                      [:statements :state :modified :infoRequest :title
                                       :address :municipality :primaryOperation :readOnly])]
    (doseq [app       apps
            statement (:statements app)
            :let      [due-date              (:dueDate statement)
                       duedate-reminder-sent (:duedate-reminder-sent statement)]
            :when     (and
                        (nil? (:given statement))
                        (number? due-date)
                        (< due-date timestamp-now)
                        (nil? duedate-reminder-sent))]
      (log/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :reminder-statement-due-date {:application app
                                                             :system      system
                                                             :recipients  [(usr/get-user-by-email (get-in statement [:person :email]))]
                                                             :data        {:due-date  (date/finnish-date due-date :zero-pad)
                                                                           :statement statement}})
        (update-application (application->command app)
                            {:statements {$elemMatch {:id (:id statement)}}}
                            {$set {:statements.$.duedate-reminder-sent (now)}})))))

(defn neighbor-reminder
  "If the neighbor status is still `email-sent` after a week, a reminder is sent, but only
  once."
  [system]
  (let [timestamp-1-week-ago (ts-week-ago)
        apps                 (mongo/snapshot :applications
                                             {:state            {$in [:open :submitted :sent :complementNeeded]}
                                              :readOnly         {$ne true}
                                              :permitType       {$nin ["ARK"]}
                                              :neighbors.status {$elemMatch {$and [{:state {$in ["email-sent"]}}
                                                                                   {:created (older-than timestamp-1-week-ago)}]}}}
                                             [:neighbors :state :modified :title :address
                                              :municipality :primaryOperation :readOnly])]
    (doseq [app      apps
            neighbor (:neighbors app)
            :let     [statuses (:status neighbor)]
            :when    (not-any? #(or
                                  (= "reminder-sent" (:state %))
                                  (= "response-given-ok" (:state %))
                                  (= "response-given-comments" (:state %))
                                  (= "mark-done" (:state %)))
                               statuses)]
      (log/with-logging-context {:applicationId (:id app)}
        (doseq [status statuses
                :when  (and
                         (= "email-sent" (:state status))
                         (< (:created status) timestamp-1-week-ago))]
          (notifications/notify! :reminder-neighbor {:application app
                                                     :system      system
                                                     :user        {:email (:email status)}
                                                     :data        {:token      (:token status)
                                                                   :expires    (date/finnish-datetime (+ ttl/neighbor-token-ttl (:created status))
                                                                                                      :zero-pad)
                                                                   :neighborId (:id neighbor)}})
          (update-application (application->command app)
                              {:neighbors {$elemMatch {:id (:id neighbor)}}}
                              {$push {:neighbors.$.status {:state   "reminder-sent"
                                                           :token   (:token status)
                                                           :created (now)}}}))))))

;; "Neuvontapyynto: Neuvontapyyntoon ei ole vastattu viikon kuluessa eli neuvontapyynnon tila on avoin. Lahetetaan viikoittain uudelleen."
(defn open-inforequest-reminder [system]
  (let [timestamp-1-week-ago (ts-week-ago)
        oirs                 (mongo/snapshot :open-inforequest-token {:created   (older-than timestamp-1-week-ago)
                                                                      :last-used nil
                                                                      $or        [{:reminder-sent {$exists false}}
                                                                                  {:reminder-sent nil}
                                                                                  {:reminder-sent (older-than timestamp-1-week-ago)}]})]
    (doseq [oir oirs]
      (let [application (mongo/by-id :applications (:application-id oir) [:state :modified :title :address
                                                                          :municipality :primaryOperation :readOnly])]
        (when-not (:readOnly application)
          (log/with-logging-context {:applicationId (:id application)}
            (when (= "info" (:state application))
              (notifications/notify! :reminder-open-inforequest {:application application
                                                                 :system      system
                                                                 :data        {:email        (:email oir)
                                                                               :token-id     (:id oir)
                                                                               :created-date (date/finnish-date (:created oir)
                                                                                                                :zero-pad)}})
              (mongo/update-by-id :open-inforequest-token (:id oir) {$set {:reminder-sent (now)}}))))))))


;; ----------------------------------------------------------------------------
;; The following reminders store their status in the `reminder-sent` object.
;; ----------------------------------------------------------------------------

(def supported-reminders #{:work-time-expiring :application-state
                           :no-final-review :construction-not-started})

(defn- reminder-sent-update [k]
  {:pre [(supported-reminders k)]}
  {$set {(util/kw-path :reminder-sent k) (now)}})

(defn- reminder-sent-check
  "Returns mongo query part that is true if the reminder for `k` has never been sent or `condition` is
  true. Falsey condition is ignored."
  ([k condition]
   {:pre [(supported-reminders k)]}
   (let [k (util/kw-path :reminder-sent k)]
     {$or (cond-> [{k {$exists false}} {k nil}]
            condition (conj {k condition}))}))
  ([k]
   (reminder-sent-check k nil)))

(defn no-linked-continuation-applications-with-verdict?
  "Returns true if there is no linked continuation application (jatkoaika) that:
  1. Was submitted after the original application
  2. Has a published verdict (is in post-verdict state)"
  [{:keys [id submitted]}]
  (let [linked-app-ids (->> (mongo/select :app-links {:link id})
                            (filter #(= (:type ((keyword id) %)) "linkpermit"))
                            (mapcat :link)
                            (filter v/lp-application-id?)
                            (remove #{id})
                            (into #{}))]
    (not (and submitted
              (seq linked-app-ids)
              (mongo/any? :applications
                          {:_id                   {$in linked-app-ids}
                           :primaryOperation.name {$in app/jatkoaika-operation-names}
                           :state                 {$in states/post-verdict-states}
                           :submitted             {$gt submitted}})))))

(defn ya-work-time-is-expiring-reminder
  "Send reminder to the default recipients if the YA permit work time is about to expire. The
  reminder is sent when all the following conditions are true:

  1. the application permit type is YA
  2. the application state is `verdictGiven` or `constructionStarted`
  3. work time expires within one week but has not passed yet
  4. the application is not read-only
  5. the reminder has not been sent earlier
  6. the application has no linked continuation application with a verdict"
  [system]
  (let [timestamp-1-week-in-future (ts-week-from-now)
        apps                       (mongo/snapshot :applications
                                                   {:permitType                       "YA"
                                                    :readOnly                         {$ne true}
                                                    :state                            {$in ["verdictGiven" "constructionStarted"]}
                                                    ;; Cannot compare timestamp directly against date string here (e.g against "08.10.2015"). Must do it in function body.
                                                    :documents                        {$elemMatch {:schema-info.name "tyoaika"}}
                                                    :reminder-sent.work-time-expiring {$exists false}}
                                                   [:documents :auth :state :modified :title :address :municipality
                                                    :infoRequest :primaryOperation :readOnly :submitted])]
    (doseq [app   apps
            :let  [tyoaika-doc                 (some
                                                 (fn [doc]
                                                   (when (= "tyoaika" (-> doc :schema-info :name)) doc))
                                                 (:documents app))
                   work-time-expires-timestamp (-> tyoaika-doc :data :tyoaika-paattyy-ms :value)]
            :when (and
                    work-time-expires-timestamp
                    (> work-time-expires-timestamp (now))
                    (< work-time-expires-timestamp timestamp-1-week-in-future)
                    (no-linked-continuation-applications-with-verdict? app))]
      (log/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :reminder-ya-work-time-is-expiring {:application app
                                                                   :system      system
                                                                   :user        (usr/batchrun-user [])
                                                                   :data        {:work-time-expires-date (date/finnish-date work-time-expires-timestamp
                                                                                                                            :zero-pad)}})
        (update-application (application->command app)
                            (reminder-sent-update :work-time-expiring))))))

(defn application-state-reminder
  "Hakemus: Hakemuksen tila on luonnos tai näkyy viranomaiselle, mutta edellisestä päivityksestä on
  aikaa yli kuukausi ja alle puoli vuotta. Lähetetään kuukausittain uudelleen. Ei lähetetä
  digitoiduille luville."
  [system]
  (let [apps (mongo/snapshot :applications
                             (merge {:state      {$in ["draft" "open"]}
                                     :readOnly   {$ne true}
                                     :permitType {$nin ["ARK"]}
                                     $and        [{:modified (older-than (ts-months-ago 1))}
                                                  {:modified (newer-than (ts-months-ago 6))}]}
                                    (reminder-sent-check :application-state
                                                         (older-than (ts-months-ago 1))))
                             [:auth :state :modified :title :address :municipality
                              :infoRequest :primaryOperation :readOnly])]
    (doseq [app apps]
      (log/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :reminder-application-state {:application app
                                                            :system      system
                                                            :user        (usr/batchrun-user [])})
        (update-application (application->command app)
                            (reminder-sent-update :application-state))))))

(notifications/defemail :no-final-review-reminder
  {:subject-key   "no-final-review-reminder"
   :recipients-fn notifications/application-state-reminder-recipients-fn})

(defn deadline-check
  "Query part for the given deadlne key. Deadline must exist and must not be passed yet, but will be
  passed within three months."
  [k]
  {:pre [(#{:aloitettava :voimassa} k)]}
  (let [three-months-from-now (ts-months-from-now 3)
        path                  (util/kw-path :deadlines k)]
    {$and [{path {$gt (now)}}
           {path {$lt three-months-from-now}}]}))

(defn no-final-review-reminder
  "Send reminder to the default recipients if the permit is about to expire. The expiration date is
  denoted by `voimassa` deadline. The reminder is sent when all the following conditions are true:

  1. the application state is `constructionStarted`
  2. permit type is R
  3. `voimassa` deadline has not been passed yet, but will be passed in three months.
  4. no final review has been completed.
  5. the application is neither digitized nor read-only.
  6. the reminder has not been sent earlier
  7. the application has no linked continuation application with a verdict"
  [system]
  (let [apps (mongo/snapshot :applications
                             (merge {:state      "constructionStarted"
                                     :readOnly   {$ne true}
                                     :permitType "R"
                                     :tasks      {$not {$elemMatch {;; Can be partial (osittainen loppukatselmus)
                                                                    :data.katselmuksenLaji.value #"loppukatselmus"
                                                                    :data.katselmus.tila.value   "lopullinen"
                                                                    :state                       "sent"}}}}
                                    (deadline-check :voimassa)
                                    (reminder-sent-check :no-final-review))
                             [:auth :state :modified :address :municipality :primaryOperation :submitted])]
    (doseq [app   apps
            :when (no-linked-continuation-applications-with-verdict? app)]
      (log/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :no-final-review-reminder {:application app
                                                          :system      system
                                                          :user        (usr/batchrun-user [])})
        (update-application (application->command app)
                            (reminder-sent-update :no-final-review))))))

(notifications/defemail :construction-not-started-reminder
  {:subject-key   "construction-not-started-reminder"
   :recipients-fn notifications/application-state-reminder-recipients-fn})

(defn construction-not-started-reminder
  "Send reminder to the default recipients if the construction has not been timely started. The
  reminder is sent when `aloitettava` deadline is closing. The reminder is sent when all the
  following conditions are true:

  1. the application state is `verdictGiven`
  2. permit type is R
  3. `aloitettava` deadline has not been passed yet, but will be passed in three months.
  4. the application is neither digitized nor read-only.
  5. the reminder has not been sent earlier
  6. the application has no linked continuation application with a verdict"
  [system]
  (let [apps (mongo/snapshot :applications
                             (merge {:state      "verdictGiven"
                                     :readOnly   {$ne true}
                                     :permitType "R"}
                                    (deadline-check :aloitettava)
                                    (reminder-sent-check :construction-not-started))
                             [:auth :address :municipality :primaryOperation :state :modified :submitted])]
    (doseq [app   apps
            :when (no-linked-continuation-applications-with-verdict? app)]
      (log/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :construction-not-started-reminder {:application app
                                                                   :system      system
                                                                   :user        (usr/batchrun-user [])})
        (update-application (application->command app)
                            (reminder-sent-update :construction-not-started))))))

(defn- supplement-request-reminder-email-model [{{:keys [due-date]} :data :as command} _ recipient]
  (merge (notifications/create-app-model command {:tab "process"} recipient)
         {:due-date (date/finnish-date due-date :zero-pad)}))

(notifications/defemail :reminder-supplement-request-due-date
  {:recipients-fn notifications/application-state-reminder-recipients-fn
   :subject-key   "reminder-supplement-request-due-date"
   :model-fn      supplement-request-reminder-email-model})

(defn supplement-request-reminder-due-date [system]
  (let [timestamp-now (now)
        apps          (mongo/snapshot :applications
                                      {:permitType          {$in ["YI" "YL" "YM" "VVVL" "MAL"]}
                                       :state               {$in ["submitted" "sent" "complementNeeded"]}
                                       :supplement-requests {$elemMatch {$and [{:due-date {$exists true}}
                                                                               {:due-date (older-than timestamp-now)}
                                                                               {:state "requested"}]
                                                                         $or  [{:due-date-reminder-sent {$exists false}}
                                                                               {:due-date-reminder-sent nil}]}}}
                                      [:supplement-requests :address :municipality :primaryOperation :auth :state :modified])]
    (doseq [app     apps
            request (:supplement-requests app)
            :let    [due-date               (:due-date request)
                     due-date-reminder-sent (:due-date-reminder-sent request)]
            :when   (and
                      (-> request :state (= "requested"))
                      (number? due-date)
                      (< due-date timestamp-now)
                      (nil? due-date-reminder-sent))]
      (log/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :reminder-supplement-request-due-date {:application app
                                                                      :system      system
                                                                      :data        {:due-date due-date}})
        (update-application (application->command app)
                            {:supplement-requests {$elemMatch {:id (:id request)}}}
                            {$set {:supplement-requests.$.due-date-reminder-sent (now)}})))))
