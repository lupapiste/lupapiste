(ns lupapalvelu.review
  (:require [taoensso.timbre :as timbre :refer [debug debugf info infof warn warnf error errorf]]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.util :as util]
            [lupapalvelu.tasks :as tasks]
            [plumbing.core :as pc]
            [lupapalvelu.action :refer [update-application application->command] :as action]
            [lupapalvelu.building :as building]
            [lupapalvelu.xml.krysp.building-reader :as building-reader]
            [lupapalvelu.xml.krysp.review-reader :as review-reader]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.document.tools :as tools]
            [sade.strings :as ss]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.verdict-review-util :as verdict-review-util]))

(defn- empty-review-task? [t]
  (let [katselmus-data (-> t :data :katselmus)
        top-keys [:tila :pitoPvm :pitaja]
        h-keys [:kuvaus :maaraAika :toteaja :toteamisHetki]
        ]
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

(defn- task-with-matching-background-id [mongo-task tasks]
  (let [mongo-task-bg-id (background-id mongo-task)]
    (when-not (ss/empty? mongo-task-bg-id)
      (util/find-first #(= mongo-task-bg-id
                           (background-id %))
                       tasks))))

(defn- task-with-same-name-and-type [mongo-task tasks]
  (->> tasks
       (filter #(reviews-have-same-name-and-type? mongo-task %))
       (first)))

(defn- matching-data?
  "Do the two tasks have matching review data for the given keys?"
  [data-keys a b]
  (->> [a b]
       (map (comp #(select-keys % data-keys)
                  :katselmus
                  :data))
       (apply =)))

(defn- task-with-same-name-type-and-data [data-keys mongo-task tasks]
  (->> tasks
       (filter #(and (reviews-have-same-name-and-type? mongo-task %)
                     (matching-data? data-keys mongo-task %)))
       (first)))

(defn- matching-task
  "For a given mongo-task, return a matching task from the XML update"
  [mongo-task update-tasks]
  (let [mongo-task-bg-id (background-id mongo-task)
        updated-id-match (when-not (ss/empty? mongo-task-bg-id)
                           (util/find-first #(= mongo-task-bg-id
                                                (background-id %))
                                            update-tasks))]
    (or ;; 1. task with matching id, or
        (task-with-matching-background-id mongo-task update-tasks)

        ;; 2. task with same name and type WHEN mongo task is empty, or
        (and (empty-review-task? mongo-task)
             (task-with-same-name-and-type mongo-task update-tasks))

        ;; 3. task with same name, type and other data related to
        ;;    holding the review
        (task-with-same-name-type-and-data [:tila :pitoPvm :pitaja]
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
               (not (= (-> updated-match :data :katselmus)
                       (-> current       :data :katselmus))))
          ;; if existing background reviews can be overwritten by more
          ;; recent ones, the existing one needs to be marked faulty
          (recur (remove #{updated-match} from-update)
                 remaining
                 unchanged
                 (conj added-or-updated updated-match)
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
                 (conj added-or-updated updated-match)
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
                          #(select-keys % [:katselmuksenLaji :tarkastuksenTaiKatselmuksenNimi :pitoPvm])
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

(defn read-reviews-from-xml
  "Saves reviews from app-xml to application. Returns (ok) with updated verdicts and tasks"
  ;; adapted from save-verdicts-from-xml. called from do-check-for-review
  [user created application app-xml & [overwrite-background-reviews?]]

  (let [reviews (vec (reviews-preprocessed app-xml))
        buildings-summary (building-reader/->buildings-summary app-xml)
        building-updates (building/building-updates (assoc application :buildings []) buildings-summary)
        source {:type "background"} ;; what should we put here? normally has :type verdict :id (verdict-id-from-application)
        review-to-task #(tasks/katselmus->task {:state :sent} source {:buildings buildings-summary} %)
        review-tasks (map review-to-task reviews)
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
        updated-tasks (concat unchanged-tasks added-and-updated-tasks)
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
        :updates (util/deep-merge task-updates building-updates state-updates)
        :new-faulty-tasks (map :id new-faulty-tasks)
        :attachments-by-task-id attachments-by-task-id
        :added-tasks-with-updated-buildings added-tasks-with-updated-buildings)))

(defn save-review-updates [user application updates added-tasks-with-updated-buildings attachments-by-task-id]
  (let [update-result (update-application (application->command application) updates)
        updated-application (domain/get-application-no-access-checking (:id application))] ;; TODO: mongo projection
    (doseq [{id :id :as added-task} added-tasks-with-updated-buildings]
      (let [attachments (get attachments-by-task-id id)]
        (if-not (empty? attachments)
          (doseq [att attachments]
            (verdict-review-util/get-poytakirja application user (now) {:type "task" :id id} att))
          (tasks/generate-task-pdfa updated-application added-task user "fi"))))))
