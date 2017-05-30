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

(defn- bg-id [task]
  (get-in task [:data :muuTunnus :value]))

(defn- non-empty-review-task? [task]
  (and (tasks/task-is-review? task) (not (empty-review-task? task))))

(defn- merge-review-tasks
  "Returns a vector with two values: 0: Existing tasks left unchanged, 1: Completely new and updated existing review tasks."
  [from-update from-mongo]
  (debugf "merge-review-tasks loop starts: from-update %s from-mongo %s" (count from-update) (count from-mongo))
  (loop [from-update from-update
         from-mongo from-mongo
         unchanged []
         added-or-updated []]
    (let [current (first from-mongo)
          bg-id-current (bg-id current)
          remaining (rest from-mongo)
          updated-id-match (when-not (ss/empty? bg-id-current)
                             (util/find-first #(= bg-id-current (bg-id %)) from-update))
          updated-match (or updated-id-match
                            (first (filter #(or (reviews-have-same-name-and-type? current %)
                                                (reviews-have-same-type-other-than-muu-katselmus? current %)) from-update)))]
      (cond
        (= current nil)
        ; Recursion end condition
        (let [added-or-updated (concat added-or-updated (filter non-empty-review-task? from-update))]
          (debugf "merge-review-tasks recursion ends: unchanged %s added-or-updated %s" (count unchanged) (count added-or-updated))
          [unchanged added-or-updated])

        (or (not (tasks/task-is-review? current)) (not (empty-review-task? current)))
        ;non-empty review tasks and all non-review tasks are left unchanged
        (do
          (recur (remove #{updated-match} from-update) remaining (conj unchanged current) added-or-updated))

        (and updated-match (not (empty-review-task? updated-match)))
        ; matching review found from backend system update => it replaces the existing one
        (recur (remove #{updated-match} from-update) remaining unchanged (conj added-or-updated updated-match))

        :else
        ; this existing review is left unchanged
        (recur from-update remaining (conj unchanged current) added-or-updated)))))

(defn merge-maps-that-are-distinct-by [pred map-coll]
  (->> (group-by pred map-coll)
       vals
       (map #(apply merge %))))

(defn reviews-preprocessed [app-xml]
  "Review preprocessing: 1) Duplicate entry prevention (group-by review type, name, date and external id)
                         2) Collect all related building and attachment elements together
                         3) Merge into final results in which there are no duplicates by name or type but still
                            all building details and attachments are still there"
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

(defn read-reviews-from-xml
  "Saves reviews from app-xml to application. Returns (ok) with updated verdicts and tasks"
  ;; adapted from save-verdicts-from-xml. called from do-check-for-review
  [user created application app-xml]

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
        updated-existing-and-added-tasks  (merge-review-tasks (map #(dissoc % :attachments) review-tasks) (:tasks application))
        updated-tasks (apply concat updated-existing-and-added-tasks)
        update-buildings-with-context (partial tasks/update-task-buildings buildings-summary)
        added-tasks-with-updated-buildings (map update-buildings-with-context (second updated-existing-and-added-tasks)) ;; for pdf generation
        updated-tasks-with-updated-buildings (map update-buildings-with-context updated-tasks)
        task-updates {$set {:tasks updated-tasks-with-updated-buildings}}
        state-updates (verdict/get-state-updates user created application app-xml)]
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