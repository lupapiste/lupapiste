(ns lupapalvelu.review
  (:require [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.backing-system.krysp.review-reader :as review-reader]
            [lupapalvelu.building :as building]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.task-util :refer [faulty?]]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.verdict-review-util :as verdict-review-util]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [debugf warnf errorf]]))

(defn- empty-review-task? [t]
  (let [katselmus-data (-> t :data :katselmus)
        top-keys [:tila :pitoPvm :pitaja]
        h-keys [:kuvaus :maaraAika :toteaja :toteamisHetki]]
    (and (every? empty? (map #(get-in katselmus-data [% :value]) top-keys))
         (every? empty? (map #(get-in katselmus-data [:huomautukset % :value]) h-keys)))))

(defn- katselmuksen-laji [t]
  (-> t tools/unwrapped :data :katselmuksenLaji))

(defn- reviews-have-same-name-and-type? [a b]
  (let [name-a (:taskname a)
        name-b (:taskname b)
        la (katselmuksen-laji a)
        lb (katselmuksen-laji a)]
    (and name-a (= name-a (or name-b lb)) la (= la lb))))

(defn- background-id [task]
  (get-in task [:data :muuTunnus :value]))

(defn- non-empty-review-task? [task]
  (and (tasks/task-is-review? task) (not (empty-review-task? task))))

(defn- task-with-matching-background-id
  [mongo-task tasks]
  (let [mongo-task-bg-id (background-id mongo-task)]
    (when-not (ss/empty? mongo-task-bg-id)
      (util/find-first #(= mongo-task-bg-id
                           (background-id %))
                       tasks))))

(defn- task-with-same-name-and-type [mongo-task tasks]
  (util/find-first #(reviews-have-same-name-and-type? mongo-task %)
                   tasks))

(defn- katselmus-data [t]
  (-> t :data :katselmus tools/unwrapped))

(defn- matching-data?
  "Do the two tasks have matching review data for the given keys?"
  [data-keys a b]
  (let [key->comparator-fn (->> data-keys
                                ;; If no comparator function is provided, use =
                                (map #(if (vector? %) % [% =]))
                                (into {}))
        compared-keys (keys key->comparator-fn)]
    (every? true? (map (fn [key]
                         ((key->comparator-fn key) (get (katselmus-data a) key)
                                                   (get (katselmus-data b) key)))
                       compared-keys))))

(defn- task-with-same-name-type-and-data [data-keys mongo-task tasks]
  (util/find-first #(and (reviews-have-same-name-and-type? mongo-task %)
                         (matching-data? data-keys mongo-task %))
                   tasks))

(defn- get-pitaja-strings
  "Ease comparison by mining the actual string value for the review officer field"
  [pitaja]
  (if (map? pitaja) [(:code pitaja) (:name pitaja)] [pitaja nil]))

(defn- compare-pitaja
  "In order to accommodate for small inconsistensies with how backends
  store pitaja information, compare by removing non-letter characters
  and converting to lower case"
  [pitaja-a pitaja-b]
  (let [[a-code a-name] (get-pitaja-strings pitaja-a)
        [b-code b-name] (get-pitaja-strings pitaja-b)]
    (or (ss/=alpha-i a-code b-code)
        (and (some? a-name) (some? b-name) (ss/=alpha-i a-name b-name))
        (and (some? a-code) (nil? a-name) (ss/=alpha-i a-code b-name))
        (and (some? b-code) (nil? b-name) (ss/=alpha-i a-name b-code)))))

(defn- matching-task
  "For a given mongo-task, return a matching task from the XML update"
  [mongo-task update-tasks]
  (or ;; 1. task with matching non-empty id, or
      (task-with-matching-background-id mongo-task update-tasks)

      ;; 2. task with same name and type WHEN mongo task is empty, or
      (when (empty-review-task? mongo-task)
        (task-with-same-name-and-type mongo-task update-tasks))

      ;; 3. task with same name, type and other data related to holding
      ;;    the review. Note that background id is ignored (LPK-4254).
      (task-with-same-name-type-and-data [#_:tila ;; Temporarily disabled
                                          [:pitoPvm date/eq?]
                                          [:pitaja compare-pitaja]]
                                         mongo-task
                                         update-tasks)))

(defn tasks-match? [task-a task-b]
  (some? (matching-task task-a [task-b])))

(defn transmute-review-officer [review-officers task]
  (let [value (-> task :data :katselmus :pitaja :value)]
    (if (and review-officers
             ;; not yet transmuted - review created before enabling the mapping
             ;; see TT-18788
             (-> value map? not))
      (let [officer (get review-officers value value)]
        (assoc-in task [:data :katselmus :pitaja :value] officer))
      task)))

(defn- merge-review-tasks
  "Returns a vector with three values:
   0: Existing tasks left unchanged,
   1: Completely new and updated existing review tasks,
   2: Reviews to be marked faulty"
  [review-officers tasks-from-update tasks-from-mongo & [overwrite-background-reviews?]]

  ;; As a postcondition, check that for every new faulty task there is
  ;; a matching updated task
  {:post [(let [[_ new-and-updated new-faulty] %]
            (every? #(matching-task % new-and-updated) new-faulty))]}
  (let [faulty-tasks     (filter faulty? tasks-from-mongo)
        tasks-from-mongo (remove faulty? tasks-from-mongo)]

    (debugf "merge-review-tasks loop starts: from-update %s from-mongo %s (+faulty: %s)"
            (count tasks-from-update) (count tasks-from-mongo) (count faulty-tasks))

    (loop [from-update tasks-from-update
           ;; Make sure that background id matches are processed first.
           from-mongo  (sort-by (util/fn-> (task-with-matching-background-id tasks-from-update) not)
                                tasks-from-mongo)
           unchanged []
           added-or-updated []
           new-faulty []]
      (let [current (first from-mongo)
            remaining (rest from-mongo)
            updated-match (matching-task current from-update)]
        (cond
          (= current nil)
          ;; Recursion end condition
          (let [added-or-updated (->> from-update
                                      (filter non-empty-review-task?)
                                      (concat added-or-updated)
                                      (map (partial transmute-review-officer review-officers)))]
            (debugf "merge-review-tasks recursion ends: unchanged %s added-or-updated %s new faulty %s"
                    (count unchanged) (count added-or-updated) (count new-faulty))
            [(concat faulty-tasks unchanged)
             added-or-updated
             new-faulty])

          (and overwrite-background-reviews?
               (tasks/task-is-review? current)
               (tasks/background-source? current)
               updated-match
               (not (= (katselmus-data updated-match)
                       (katselmus-data current))))
          ;; if existing background reviews can be overwritten by more
          ;; recent ones, the existing one needs to be marked faulty
          (recur (remove #{updated-match} from-update)
                 remaining
                 unchanged
                 (conj added-or-updated (util/assoc-when updated-match :created (:created current)))
                 (conj new-faulty current))

          (or (not (tasks/task-is-review? current))
              (not (empty-review-task?    current)))
          ;; non-empty review tasks and all non-review tasks are left unchanged
          (recur (remove #{updated-match} from-update)
                 remaining
                 (conj unchanged current)
                 added-or-updated
                 new-faulty)

          (and updated-match
               (not (empty-review-task? updated-match)))
          ;; matching review found from backend system update => it replaces the existing one
          (recur (remove #{updated-match} from-update)
                 remaining
                 unchanged
                 (conj added-or-updated (util/assoc-when updated-match :created (:created current)))
                 new-faulty)

          :else
          ;; this existing review is left unchanged
          (recur from-update
                 remaining
                 (conj unchanged current)
                 added-or-updated
                 new-faulty))))))

(defn merge-maps-that-are-distinct-by [pred map-coll]
  (->> (group-by pred map-coll)
       vals
       (map #(apply merge %))))

(sc/defschema PreProcessedLiitetieto
  {:liite review-reader/Liite})

(def PreProcessedReview (assoc review-reader/KuntaGMLReview
                          (sc/optional-key :liitetieto)
                          (sc/maybe [PreProcessedLiitetieto])))

(sc/defn reviews-preprocessed :- [PreProcessedReview]
  "Review preprocessing: 1) Duplicate entry prevention (group-by review type, name, date and external id)
                         2) Collect all related building and attachment elements together
                         3) Merge into final results in which there are no duplicates by name or type but
                            all building details and attachments are still there"
  [asia-xml]
  (let [historical-timestamp-present? (fn [{pvm :pitoPvm}] (and (number? pvm)
                                                                (< pvm (now))))
        grouped-reviews (group-by
                          #(select-keys % [:katselmuksenLaji :tarkastuksenTaiKatselmuksenNimi :pitoPvm :muuTunnustieto])
                          (filter historical-timestamp-present? (review-reader/xml->reviews asia-xml)))]
    (for [k (keys grouped-reviews)
          :let [values (get grouped-reviews k)
                rakennukset (->> values
                                 (mapcat :katselmuksenRakennustieto)
                                 (map :KatselmuksenRakennus)
                                 (merge-maps-that-are-distinct-by
                                   #(select-keys % [:kiinttun :rakennusnro :jarjestysnumero])))
                liitetiedot (->> values
                                 (map #(-> % :liitetieto :Liite))
                                 (remove nil?)
                                 (remove (comp util/empty-or-nil? :linkkiliitteeseen)))]]
      (merge (first values)
             (util/strip-nils {:katselmuksenRakennustieto (when (seq rakennukset)
                                                            (map #(apply hash-map [:KatselmuksenRakennus %]) rakennukset))
                               :liitetieto (when (seq liitetiedot)
                                             (map #(apply hash-map [:liite %]) liitetiedot))})))))

(defn- review->task
  "Task definition for the given review. If the task passes validation,
  the review attachments are also stored under :attachments."
  [created buildings-summary review]
  (let [task  (tasks/katselmus->task {:state :sent :created created}
                                     {:type "background"}
                                     {:buildings buildings-summary}
                                     review)
        error (seq (tasks/task-doc-validation (get-in task [:schema-info :name])
                                              task))]
    (if error
      (do
        (warnf "Invalid backing system task data. Attachment not added: %s"
               (ss/serialize error))
        task)
      (assoc task :attachments (:liitetieto review)))))

(def- muuTunnustieto-path [:muuTunnustieto 0 :MuuTunnus :tunnus])

(defn- get-muuTunnustieto-from-review [review]
  (get-in review muuTunnustieto-path))

(defn- repeating-review-ids
  "Returns a set of those `muuTunnustieto` ids that occur in more than
  one review. Application id is included if it's the id of even one
  review."
  [reviews app-id]
  (->> reviews
       (map get-muuTunnustieto-from-review)
       (remove (comp nil? not-empty))
       (cons app-id)
       (group-by identity)
       (filter (fn [[id xs]]
                 (> (count xs) 1)))
       (map first)
       set))

(defn- attachments-by-id-from-task-candidates
  "From given tasks, selects those who have attachments under :attachments.
  Returns a map, where selected attachments containing tasks are keyed by both
  task's :id and its muuTunnus value.
  nil and blank muuTunnus values are stripped from the result."
  [review-tasks]
  (->> (filter (comp not-empty :attachments) review-tasks)
       (reduce
         (fn [acc {:keys [attachments] :as task-candidate}]
           (cond-> (assoc acc (:id task-candidate) attachments)
             ; associate by background ID if it's not empty or nil
             (util/not-empty-or-nil? (background-id task-candidate))
             (assoc (background-id task-candidate) attachments)))
         {})))

(defn- remove-repeating-background-ids
  "Remove (or rather set to `\"\"`)repeating background ids from
  preprocessed review tasks. If a background id is the application id,
  it is removed, too."
  [app-id reviews]
  (let [repeating-ids (repeating-review-ids reviews app-id)]
    (map (fn [review]
           (cond-> review
             (contains? repeating-ids
                        (get-muuTunnustieto-from-review review))
             (assoc-in  muuTunnustieto-path "")))
        reviews)))

(defn- lupapiste-review?
  "True if the review has originated from Lupapiste (according
  to :muuTunnus.sovellus)."
  [review]
  (ss/=trim-i (some-> review :muuTunnustieto first :MuuTunnus :sovellus)
              "Lupapiste"))

(defn- process-reviews
  "Return reviews from XML as tasks (through review->task).
  Review tasks contain key :attachment, which has liite (PreProcessedLiitetieto) elements found from xml for each review."
  [app asia-xml created buildings-summary]
  (->> asia-xml
       reviews-preprocessed
       (remove-repeating-background-ids (:id app))
       (remove lupapiste-review?)
       (map #(review->task created buildings-summary %))))

(defn- preprocess-tasks
  "Tasks for the application after processing. The processing removes
  muuTunnus values that are application ids."
  [{app-id :id tasks :tasks}]
  (map (fn [task]
         (cond-> task
           (= (background-id task) app-id) (assoc-in [:data :muuTunnus :value] "")))
       tasks))

(defn- validate-new-task
  [{:keys [created]} {{{:keys [pitoPvm] :as review-data} :katselmus} :data id :id :as unwrapped-task}]
  (cond
    (< (date/timestamp pitoPvm) created)
    (merge
      (select-keys review-data [:pitoPvm :katselmuksenLaji])
      {:id id
       :error (format "pitoPvm %s is before application was created %s"
                      pitoPvm
                      (date/xml-date created))})))

(defn- validate-changed-tasks
  "Checks that every task going to be saved looks sane.
  Returns nil if all are valid. Returns sequence of validation errors of there are problems."
  [application added-and-updated-tasks]
  (reduce
    (fn [errors task]
      (if-let [error-report (validate-new-task application (tools/unwrapped task))]
        (conj errors error-report)
        errors))
    nil
    added-and-updated-tasks))

(defn read-reviews-from-xml
  "Saves reviews from app-xml to application. Returns (ok) with updated verdicts and tasks.
  Takes optional options map as last parameters."
  ;; adapted from save-verdicts-from-xml. called from do-check-for-review
  [user created application asia-xml & [{:keys [overwrite-background-reviews? do-not-include-state-updates? skip-task-validation?]
                                         :or   {overwrite-background-reviews? false
                                                do-not-include-state-updates? false
                                                skip-task-validation?         false}}]]
  (let [buildings-summary (building-reader/->buildings-summary asia-xml)
        building-updates  (building/building-updates (assoc application :buildings []) created buildings-summary)
        organizationId    (:organization application)
        review-officers   (when (:data (organization/fetch-organization-review-officers-list-enabled organizationId))
                            (->> (organization/fetch-organization-review-officers organizationId)
                                 :data
                                 (reduce #(assoc %1 (:code %2) %2) {})))
        review-tasks      (process-reviews application asia-xml created buildings-summary)
        [unchanged-tasks
         added-and-updated-tasks
         new-faulty-tasks] (merge-review-tasks review-officers
                                               (map #(dissoc % :attachments) review-tasks)
                                               (preprocess-tasks application)
                                               overwrite-background-reviews?)
        all-tasks         (concat unchanged-tasks
                                  new-faulty-tasks
                                  added-and-updated-tasks)
        update-buildings-with-context (partial tasks/update-task-buildings buildings-summary)
        added-tasks-with-updated-buildings (map update-buildings-with-context added-and-updated-tasks) ;; for pdf generation
        updated-tasks-with-updated-buildings (map update-buildings-with-context all-tasks)
        task-updates (when (seq updated-tasks-with-updated-buildings) {$set {:tasks updated-tasks-with-updated-buildings}})
        state-updates (verdict/get-state-updates user created application asia-xml)]

    ;; Check that the number of buildings in buildings summary matches
    ;; the number of buildings in the review data
    (doseq [task (filter #(and (tasks/task-is-review? %)
                               (-> % :data :rakennus)) updated-tasks-with-updated-buildings)]
      (if-not (= (count buildings-summary)
                 (-> task :data :rakennus count))
        (errorf "buildings count %s != task rakennus count %s for id %s"
                (count buildings-summary)
                (-> task :data :rakennus count) (:id application))))

    (assert (or (seq all-tasks) (empty? (:tasks application))) "there should be tasks, or else this makes no sense at all")
    (assert (map? application) "application is map")
    (assert (every? not-empty all-tasks) "tasks not empty")
    (assert (every? map? all-tasks) "tasks are maps")
    (debugf "save-reviews-from-xml: post merge counts: %s review tasks from xml, %s pre-existing tasks in application, %s tasks after merge" (count review-tasks) (count (:tasks application)) (count all-tasks))
    (assert (>= (count all-tasks) (count (:tasks application))) "have fewer tasks after merge than before")
    (assert (every? #(get-in % [:schema-info :name]) updated-tasks-with-updated-buildings))
    (if-let [errors (when-not skip-task-validation? (seq (validate-changed-tasks application added-and-updated-tasks)))]
      (fail :error.invalid-reviews :validation-errors errors)
      (ok :review-count (count review-tasks)
          :all-tasks (map :id all-tasks)
          :updates (util/deep-merge task-updates building-updates (when-not do-not-include-state-updates? state-updates))
          :new-faulty-tasks (map :id new-faulty-tasks)
          :attachments-by-ids (attachments-by-id-from-task-candidates review-tasks)
          :added-tasks-with-updated-buildings added-tasks-with-updated-buildings))))

(defn- try-download-review-attachment [application user timestamp target attachment]
  (try
    (verdict-review-util/get-poytakirja! application user timestamp target attachment)
    (catch Exception e
      (logging/log-event :error {:run-by        "Automatic review checking"
                                 :event         (format "Reviews attachment error: task=%s attachment=%s message=%s"
                                                        (:id target)
                                                        (:id attachment)
                                                        (.getMessage e))})
      (errorf "Error when getting review attachments: task=%s attachment=%s message=%s"
              (:id target)
              (:id attachment)
              (.getMessage e)))))

(defn- find-attachment-by-url-hash [url-hash attachments]
  (->> attachments
       (util/find-first (fn [{:keys [target]}] (= url-hash (:urlHash target))))))

(defn save-review-updates [{user :user application :application :as command}
                           updates
                           added-tasks-with-updated-buildings
                           attachments-by-ids
                           only-use-inspection-from-backend?]
  (let [update-result       (pos? (update-application command {:modified (:modified application)} updates :return-count? true))
        updated-application (domain/get-application-no-access-checking (:id application)) ;; TODO: mongo projection
        state-changed       (not= (keyword (:state application)) (keyword (:state updated-application)))]
    (when update-result
      (doseq [{id :id :as added-task} added-tasks-with-updated-buildings]
        (let [attachments (get attachments-by-ids id)]
          (if-not (empty? attachments)
            (doseq [att attachments]
              (try-download-review-attachment application user (or (:created command) (now)) {:type "task" :id id} att))
            (when-not only-use-inspection-from-backend?
              (tasks/generate-task-pdfa updated-application added-task (:user command) "fi")))))
      (when state-changed
        (att/maybe-generate-comments-attachment user updated-application (:state updated-application)))
      (when (seq attachments-by-ids)
        ;; let's check if old tasks from backend have new attachments available from XML
        (doseq [review-task         (->> (:tasks application)
                                         (filter (every-pred tasks/background-source? tasks/task-is-review?))
                                         (remove faulty?))
                :let                [task-id              (:id review-task)
                                     muuTunnus            (background-id review-task)
                                     kuntagml-attachments (or (get attachments-by-ids task-id)
                                                              ;; LPK-4857 workaround for
                                                              ;; process-reviews, which
                                                              ;; always creates new
                                                              ;; task-ids, thus we try to
                                                              ;; find old task's
                                                              ;; attachments by muuTunnus
                                                              (get attachments-by-ids muuTunnus))
                                     task-attachments     (att/get-attachments-by-target-type-and-id application
                                                                                                     {:type "task"
                                                                                                      :id   task-id})]
                :when               (seq kuntagml-attachments)
                kuntagml-attachment kuntagml-attachments
                :let                [liite               (:liite kuntagml-attachment)
                                     url-hash            (verdict-review-util/urlHash liite)
                                     matching-attachment (find-attachment-by-url-hash url-hash task-attachments)]
                :when               (not matching-attachment)]
          ;; we did not find the kuntagml-attachment, so let's download it
          (logging/log-event :debug
                             {:run-by "Automatic review checking"
                              :event  (format "Missing a review attachment for task %s, downloading %s"
                                              task-id
                                              (:linkkiliitteeseen liite))})
          (debugf "Missing a review attachment for task %s, downloading %s"
                  task-id
                  (:linkkiliitteeseen liite))
          ;; yes, we looped through old application, but we want to update against latest available data
          (try-download-review-attachment updated-application
                                          user
                                          (:created command)
                                          {:type        "task"
                                           :id          task-id
                                           :review-type (katselmuksen-laji review-task)}
                                          kuntagml-attachment))))
    (cond-> {:ok update-result}
      (false? update-result) (assoc :desc (format "Application modified does not match (was: %d, now: %d)"
                                                  (:modified application)
                                                  (:modified updated-application))))))

(defn application-vs-kuntagml-bad-review-candidates
  "Used by `review-cleanup` batchrun."
  [{:keys [start-ts end-ts]} application kuntagml]
  (tools/unwrapped {:kuntagml    (process-reviews application kuntagml (now) {})
                    :application (filter #(when-let [modified-ts (some-> % :data :katselmus :tila :modified)]
                                            (and (tasks/background-source? %)
                                                 (= (:state %) "sent")
                                                 (tasks/task-is-review? %)
                                                 (< start-ts modified-ts end-ts)))
                                         (preprocess-tasks application))}))
