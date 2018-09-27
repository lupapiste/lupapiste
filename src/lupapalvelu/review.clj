(ns lupapalvelu.review
  (:require [lupapalvelu.action :refer [update-application application->command]]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.backing-system.krysp.review-reader :as review-reader]
            [lupapalvelu.building :as building]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.verdict-review-util :as verdict-review-util]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]
            [taoensso.timbre :refer [debug debugf info infof warn warnf error errorf]]))

(defn- empty-review-task? [t]
  (let [katselmus-data (-> t :data :katselmus)
        top-keys [:tila :pitoPvm :pitaja]
        h-keys [:kuvaus :maaraAika :toteaja :toteamisHetki]]
    (and (every? empty? (map #(get-in katselmus-data [% :value]) top-keys))
         (every? empty? (map #(get-in katselmus-data [:huomautukset % :value]) h-keys)))))

(defn- katselmuksen-laji [t]
  (-> t tools/unwrapped :data :katselmuksenLaji))

(defn- reviews-have-same-type-other-than-muu-katselmus? [a b]
  (let [la (katselmuksen-laji a)
        lb (katselmuksen-laji b)]
    (and la (= la lb) (not= la "muu katselmus"))))

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

(defn- compare-pitaja
  "In order to accommodate for small inconsistensies with how backends
  store pitaja information, compare by removing non-letter characters
  and converting to lower case"
  [pitaja-a pitaja-b]
  (ss/=alpha-i pitaja-a pitaja-b))

(defn- matching-task
  "For a given mongo-task, return a matching task from the XML update"
  [mongo-task update-tasks]
  (or ;; 1. task with matching id, or
      (task-with-matching-background-id mongo-task update-tasks)

      ;; 2. task with same name and type WHEN mongo task is empty, or
      (when (empty-review-task? mongo-task)
        (task-with-same-name-and-type mongo-task update-tasks))

      ;; 3. task with same name, type and other data related to holding
      ;;    the review, given that mongo task does not have background id
      (when (ss/empty? (background-id mongo-task))
        (task-with-same-name-type-and-data [#_:tila ;; Temporarily disabled
                                            :pitoPvm
                                            [:pitaja compare-pitaja]]
                                           mongo-task
                                           update-tasks))))

(defn- merge-review-tasks
  "Returns a vector with two values:
   0: Existing tasks left unchanged,
   1: Completely new and updated existing review tasks,
   2: Reviews to be marked faulty"
  [tasks-from-update tasks-from-mongo & [overwrite-background-reviews?]]

  ;; As a postcondition, check that for every new faulty task there is
  ;; an matching updated task
  {:post [(let [[_ new-and-updated new-faulty] %]
            (every? #(matching-task % new-and-updated) new-faulty))]}

  (let [faulty-tasks     (filter #(util/=as-kw (:state %) :faulty_review_task)
                                 tasks-from-mongo)
        tasks-from-mongo (remove #(util/=as-kw (:state %) :faulty_review_task)
                                 tasks-from-mongo)]

    (debugf "merge-review-tasks loop starts: from-update %s from-mongo %s (+faulty: %s)"
            (count tasks-from-update) (count tasks-from-mongo) (count faulty-tasks))

    (loop [from-update tasks-from-update
           from-mongo  tasks-from-mongo
           unchanged []
           added-or-updated []
           new-faulty []]
      (let [current (first from-mongo)
            remaining (rest from-mongo)
            updated-match (matching-task current from-update)]
        (cond
          (= current nil)
          ;; Recursion end condition
          (let [added-or-updated (concat added-or-updated (filter non-empty-review-task? from-update))]
            (debugf "merge-review-tasks recursion ends: unchanged %s added-or-updated %s new faulty %s"
                    (count unchanged) (count added-or-updated) (count new-faulty))
            [(concat faulty-tasks unchanged)
             added-or-updated
             new-faulty])

          (and overwrite-background-reviews?
               (tasks/task-is-review? current)
               (= (-> current :source :type) "background")
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

(defn reviews-preprocessed
  "Review preprocessing: 1) Duplicate entry prevention (group-by review type, name, date and external id)
                         2) Collect all related building and attachment elements together
                         3) Merge into final results in which there are no duplicates by name or type but still
                            all building details and attachments are still there"
  [app-xml]
  (let [historical-timestamp-present? (fn [{pvm :pitoPvm}] (and (number? pvm)
                                                                (< pvm (now))))
        grouped-reviews (group-by
                          #(select-keys % [:katselmuksenLaji :tarkastuksenTaiKatselmuksenNimi :pitoPvm :muuTunnustieto])
                          (filter historical-timestamp-present? (review-reader/xml->reviews app-xml)))]
    (for [k (keys grouped-reviews)
          :let [values (get grouped-reviews k)
                rakennukset (->> (get grouped-reviews k)
                                 (mapcat :katselmuksenRakennustieto)
                                 (map :KatselmuksenRakennus)
                                 (merge-maps-that-are-distinct-by
                                   #(select-keys % [:kiinttun :rakennusnro :jarjestysnumero])))
                liitetiedot (->> (get grouped-reviews k)
                                 (map #(-> % :liitetieto :Liite))
                                 (remove nil?))]]
      (merge (first values)
             (util/strip-nils {:katselmuksenRakennustieto (when (seq rakennukset)
                                                            (map #(apply hash-map [:KatselmuksenRakennus %]) rakennukset))
                               :liitetieto (when (seq liitetiedot)
                                             (map #(apply hash-map [:liite %]) liitetiedot))})))))

(defn reviews->tasks [meta source buildings-summary reviews]
  (letfn [(review-to-task [review] (tasks/katselmus->task meta source {:buildings buildings-summary} review))
          (drop-reviews-with-lupapiste-muuTunnus [rs] (filter (util/fn-> :data :muuTunnusSovellus :value ss/lower-case (not= "lupapiste")) rs))]
    (->> reviews
         (map review-to-task)
         drop-reviews-with-lupapiste-muuTunnus)))

(defn- remove-repeating-background-ids
  "Remove repeating background ids from preprocessed review tasks."
  [reviews]
  (let [get-id        #(get-in % [:muuTunnustieto 0 :MuuTunnus :tunnus])
        repeating-ids (-<>> (map get-id reviews)
                            (remove nil?)
                            (group-by identity)
                            (filter (fn [[id xs]]
                                      (> (count xs) 1)))
                            (map first)
                            set)]
    (map (fn [review]
           (if (contains? repeating-ids (get-id review))
             (dissoc review :muuTunnustieto)
             review))
         reviews)))

(defn read-reviews-from-xml
  "Saves reviews from app-xml to application. Returns (ok) with updated verdicts and tasks"
  ;; adapted from save-verdicts-from-xml. called from do-check-for-review
  [user created application app-xml & [overwrite-background-reviews? do-not-include-state-updates?]]
  (let [reviews (-> (reviews-preprocessed app-xml)
                    remove-repeating-background-ids
                    vec )
        buildings-summary (building-reader/->buildings-summary app-xml)
        building-updates (building/building-updates (assoc application :buildings []) buildings-summary)
        source {:type "background"} ;; what should we put here? normally has :type verdict :id (verdict-id-from-application)
        review-tasks (reviews->tasks {:state :sent :created created} source buildings-summary reviews)
        validation-errors (doall (map #(tasks/task-doc-validation (-> % :schema-info :name) %) review-tasks))
        review-tasks (keep-indexed (fn [idx item]
                                     (if (empty? (get validation-errors idx))
                                       (assoc item :attachments (-> (get reviews idx) :liitetieto)))) review-tasks)
        attachments-by-task-id (apply hash-map
                                      (remove empty? (mapcat (fn [t]
                                                               (when (:attachments t) [(:id t) (:attachments t)])) review-tasks)))
        [unchanged-tasks
         added-and-updated-tasks
         new-faulty-tasks] (merge-review-tasks (map #(dissoc % :attachments) review-tasks)
                                               (:tasks application)
                                               overwrite-background-reviews?)
        updated-tasks (concat unchanged-tasks
                              new-faulty-tasks
                              added-and-updated-tasks)
        update-buildings-with-context (partial tasks/update-task-buildings buildings-summary)
        added-tasks-with-updated-buildings (map update-buildings-with-context added-and-updated-tasks) ;; for pdf generation
        updated-tasks-with-updated-buildings (map update-buildings-with-context updated-tasks)
        task-updates {$set {:tasks updated-tasks-with-updated-buildings}}
        state-updates (verdict/get-state-updates user created application app-xml)]

    ;; Check that the number of buildings in buildings summary matches
    ;; the number of buildings in the review data
    (doseq [task (filter #(and (tasks/task-is-review? %)
                               (-> % :data :rakennus)) updated-tasks-with-updated-buildings)]
      (if-not (= (count buildings-summary)
                 (-> task :data :rakennus count))
        (errorf "buildings count %s != task rakennus count %s for id %s"
                (count buildings-summary)
                (-> task :data :rakennus count) (:id application))))

    (assert (map? application) "application is map")
    (assert (every? not-empty updated-tasks) "tasks not empty")
    (assert (every? map? updated-tasks) "tasks are maps")
    (debugf "save-reviews-from-xml: post merge counts: %s review tasks from xml, %s pre-existing tasks in application, %s tasks after merge" (count review-tasks) (count (:tasks application)) (count updated-tasks))
    (assert (>= (count updated-tasks) (count (:tasks application))) "have fewer tasks after merge than before")
    ;; (assert (>= (count updated-tasks) (count review-tasks)) "have fewer post-merge tasks than xml had review tasks") ;; this is ok since id-less reviews from xml aren't used
    (assert (every? #(get-in % [:schema-info :name]) updated-tasks-with-updated-buildings))
    (when (some seq validation-errors)
      (doseq [error (remove empty? validation-errors)]
        (warnf "Backend task validation error, this was skipped: %s" (pr-str error))))
    (ok :review-count (count review-tasks)
        :updated-tasks (map :id updated-tasks)
        :updates (util/deep-merge task-updates building-updates (when-not do-not-include-state-updates? state-updates))
        :new-faulty-tasks (map :id new-faulty-tasks)
        :attachments-by-task-id attachments-by-task-id
        :added-tasks-with-updated-buildings added-tasks-with-updated-buildings)))

(defn save-review-updates [{user :user application :application :as command} updates added-tasks-with-updated-buildings attachments-by-task-id]
  (let [update-result (pos? (update-application command {:modified (:modified application)} updates :return-count? true))
        updated-application (domain/get-application-no-access-checking (:id application)) ;; TODO: mongo projection
        organization (organization/get-organization (:organization application))
        state-changed (not= (keyword (:state application)) (keyword (:state updated-application)))]
    (when update-result
      (doseq [{id :id :as added-task} added-tasks-with-updated-buildings]
        (let [attachments (get attachments-by-task-id id)]
          (if-not (empty? attachments)
            (doseq [att attachments]
              (try
                (verdict-review-util/get-poytakirja! updated-application user (now) {:type "task" :id id} att)
                (catch Exception e
                  (errorf "Error when getting review attachments: task=%s attachment=%s message=%s"
                          id
                          (:id att)
                          (.getMessage e)))))
            (when-not (true? (:only-use-inspection-from-backend organization))
              (tasks/generate-task-pdfa updated-application added-task (:user command) "fi")))))
      (when state-changed
        (attachment/maybe-generate-comments-attachment user updated-application (:state updated-application))))
    (cond-> {:ok update-result}
            (false? update-result) (assoc :desc (format "Application modified does not match (was: %d, now: %d)" (:modified application) (:modified updated-application))))))
