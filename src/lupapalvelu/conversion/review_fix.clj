(ns lupapalvelu.conversion.review-fix
  "Various fix batchrun functionality. Not necessarily related to conversions but called via
  `other` batchrun."
  (:require [babashka.fs :as fs]
            [clojure.set :as set]
            [lupapalvelu.action :as action]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp]
            [lupapalvelu.backing-system.krysp.review-reader :as rr]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.review :as review]
            [lupapalvelu.task-util :as task-util]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema-tools.core :as st]
            [schema.core :as sc]
            [swiss.arrows :refer :all])
  (:import [java.time ZonedDateTime]))

(def ^:private ReviewScope
  "`pidetty` can be found in older applications."
  (sc/enum "osittainen" "lopullinen" "pidetty"))

(sc/defschema ^:private EndReview
  {:date                       ZonedDateTime
   :name                       ssc/NonBlankStr
   :scope                      ReviewScope
   :buildings                  [(st/optional-keys
                                  {;; Mongo object field key
                                   :index-key sc/Keyword
                                   :in-use?   sc/Bool
                                   :scope     ReviewScope
                                   :vtj-prt   ssc/NonBlankStr
                                   :kspr      ssc/NonBlankStr})]
   (sc/optional-key :other-id) ssc/NonBlankStr
   (sc/optional-key :mongo-id) ssc/ObjectIdStr})

(def ^:private end-review-validator (sc/validator EndReview))

(sc/defschema ^:private NoteReview
  {:date                       ZonedDateTime
   :name                       ssc/NonBlankStr
   :scope                      ReviewScope
   :type                       ssc/NonBlankStr
   ;; Application task note can be missing.
   (sc/optional-key :note)     ssc/NonBlankStr
   (sc/optional-key :other-id) ssc/NonBlankStr
   (sc/optional-key :mongo-id) ssc/ObjectIdStr})

(def ^:private note-review-validator (sc/validator NoteReview))

(defn- purge-other-ids
  "Other ids must be unique. If not, we do not trust any of them."
  [reviews]
  (let [oids (keep :other-id reviews)]
    (cond->> reviews
      (not= (count oids) (count (distinct oids)))
      (map #(dissoc % :other-id)))))

(defn- make-raw-common [m]
  (-> (set/rename-keys m {:tarkastuksenTaiKatselmuksenNimi :name
                          :osittainen                      :scope
                          :katselmuksenLaji                :type})
      (assoc :other-id (some #(when-let [{:keys [sovellus tunnus]} (:MuuTunnus %)]
                                (when (and (ss/not-blank? sovellus)
                                           (ss/not-blank? tunnus))
                                  tunnus))
                             (:muuTunnustieto m))
             :date (date/zoned-date-time (:pitoPvm m)))
      util/strip-nils))

(defn- make-task-common [app-id task data]
  (let [other-id (:muuTunnus data)]
    (-> (:katselmus data)
        (set/rename-keys {:tila :scope :pitoPvm :date})
        (assoc :other-id (when-not (= other-id app-id)
                           other-id)
               :name (:taskname task)
               :type (:katselmuksenLaji data)
               :mongo-id (:id task))
        (update :date date/zoned-date-time))))

(defn- review-matcher [{:keys [other-id mongo-id] :as review} & ks]
  (letfn [(str-eq? [a b]
            (when-not (and (ss/blank? a) (ss/blank? b))
              ;; Arguments are trimmed and lower-cased earlier.
              (= a b)))]
    (fn [{xml-other-id :other-id :as target}]
      (every? (fn [k]
                (case k
                  :other-id
                  ;; XML other id (muu tunnus) can originate either from the backing system
                  ;; or Lupapiste. If latter then it matches task's mongo id.
                  (or (str-eq? xml-other-id other-id)
                      (str-eq? xml-other-id mongo-id))

                  :date  (some->> (:date target) (date/eq? (:date review)))
                  :scope (str-eq? (:scope review) (:scope target))
                  :type  (str-eq? (:type review) (:type target))
                  :name  (str-eq? (:name review) (:name target))
                  false))
              ks))))

(defn- find-matches-by-fields [tasks targets & ks]
  (loop [[t & ts] tasks
         targets  (set targets)
         matches  []]
    (if (and t (seq targets))
      (if-let [target (util/find-first (apply review-matcher t ks) targets)]
        (recur ts (disj targets target) (conj matches [t target]))
        (recur ts targets matches))
      matches)))

(defn- find-matches [fix-type tasks targets]
  (let [matches1 (find-matches-by-fields tasks targets :other-id)
        fields   (cond-> [:name :date :scope]
                   (= fix-type :note) (conj :type))
        matches2 (apply find-matches-by-fields
                        (apply disj (set tasks) (map first matches1))
                        (apply disj (set targets) (map second matches1))
                        fields)]
    (seq (concat matches1 matches2))))

(defn- application-updates [fix-type task-ids]
  (let [prefix (case fix-type
                 :end-review "End review"
                 :note       "Note"
                 :missing    "Review"
                 "Task")
        s      (ss/join ", " (sort (map name task-ids)))
        ts     (date/timestamp (date/now))
        note   (format "%s fix: Task%s %s."
                       prefix
                       (if (= 1 (count task-ids))
                         "" "s")
                       s)]
    (log/info note)
    (conv-util/with-sheriff-note note ts
      {$set {:reportingDbOverrideTs ts}})))

(defmulti ^:private -xml-keeper
  "Keeper function for filtering the source KuntaGML reviews."
  (fn [fix-type _review] fix-type))

(defmulti ^:private -task-keeper
  "Keeper function for filtering the target application reviews."
  (fn [fix-type _application _task] fix-type))

(defmulti ^:private -review-updates
  "Returns a mongo updates map for `_task-review`. The map consists only of keyword paths
  and values, no mongo operators. Nil if no update."
  (fn [fix-type _task-review _xml-review] fix-type))

(defn-  source-reviews
  "KuntaGML reviews processor. Extracts reviews from `xml` and processes them through
  `-xml-keeper`."
  [fix-type xml]
  (some->> xml
           rr/xml->reviews
           (keep (partial -xml-keeper fix-type))
           distinct ; Duplicates are possible
           seq
           purge-other-ids))

(defn- target-tasks
  "Application reviews processor. Processes `application` tasks through `-task-keeper`."
  [fix-type application]
  (some->> (:tasks application)
           (keep (partial -task-keeper fix-type application))
           seq
           purge-other-ids))

;; ----------------------------------
;; Fix end reviews
;; ----------------------------------

(defn- make-building [{:keys [kunnanSisainenPysyvaRakennusnumero
                              valtakunnallinenNumero
                              index-key in-use? scope]}]
  (-> {:index-key index-key
       :vtj-prt   valtakunnallinenNumero
       :kspr      kunnanSisainenPysyvaRakennusnumero
       :in-use?   in-use?
       :scope     scope}
      util/strip-blanks
      not-empty))

(defn- make-end-review [raw buildings]
  (try
    (some-<>
      ;; `:type` is not needed, since we are only interested in end reviews.
      (select-keys raw [:name :scope :other-id :mongo-id :date])
      (assoc :buildings (some->> buildings seq (keep make-building)))
      util/strip-blanks
      (ss/stringwalk (comp ss/lower-case ss/trim) <>)
      not-empty
      end-review-validator)
    (catch Exception e
      (log/warn "Ignore review:" raw (ex-message e)))))

(defn- end-review? [m]
  (boolean (some->> (:katselmuksenLaji m)
                    (re-matches #"(?i)(osittainen )?loppukatselmus"))))

(defn- building-state-updates
  "Nil if no change needed."
  [building target-buildings]
  (when-let [{:keys [in-use?
                     scope]} (some #(when-let [v (get building %)]
                                      (util/find-by-key % v target-buildings))
                                   [:vtj-prt :kspr])]
    (let [basepath (util/kw-path :tasks.$.data.rakennus
                                 (:index-key building)
                                 :tila)]
      (merge (when (and (some? in-use?) (not= in-use? (:in-use? building)))
               {(util/kw-path basepath :kayttoonottava.value) in-use?})
             (when (and (ss/not-blank? scope) (not= scope (:scope building)))
               {(util/kw-path basepath :tila.value) scope})))))

(defmethod -xml-keeper :end-review
  [_ review]
  (when (end-review? review)
    (when-let [buildings (some->> (:katselmuksenRakennustieto review)
                                  (keep :KatselmuksenRakennus)
                                  (map #(set/rename-keys % {:kayttoonottoKytkin  :in-use?
                                                            :katselmusOsittainen :scope}))
                                  seq)]
      (make-end-review (make-raw-common review) buildings))))

(defmethod -task-keeper :end-review
  [_ application-id {:keys [state data] :as task}]
  (let [data (tools/unwrapped data)]
    (when (and (task-util/task-is-review? task)
               (= state "sent")
               (end-review? data))
      (when-let [buildings (some->> (:rakennus data)
                                    (map (fn [[k v]]
                                           (-> (:rakennus v)
                                               (select-keys [:kunnanSisainenPysyvaRakennusnumero
                                                             :valtakunnallinenNumero])
                                               (assoc :index-key k
                                                      :in-use? (get-in v [:tila :kayttoonottava])
                                                      :scope (get-in v [:tila :tila])))))
                                    seq)]
        (make-end-review (make-task-common application-id task data) buildings)))))

(defmethod -review-updates :end-review
  [_ task-review xml-review]
  (some->> (:buildings task-review)
           (keep #(building-state-updates % (:buildings xml-review)))
           seq
           (into {})))

;; ----------------------------------
;; Fix review notes
;; ----------------------------------

(defn- make-note-review  [raw]
  (try
    (-<> (select-keys raw [:name :date :other-id :mongo-id
                           :scope :type])
         (ss/stringwalk (comp ss/lower-case ss/trim) <>)
         (assoc :note (ss/trim (:note raw)))
         util/strip-blanks
         note-review-validator)
    (catch Exception e
      (log/warn "Ignore review:" raw (ex-message e)))))

(defn- resolve-note [old new]
  (let [oldlow (-> old ss/trim ss/lower-case ss/blank-as-nil)
        newlow (-> new ss/trim ss/lower-case ss/blank-as-nil)]
    (cond
      (= newlow oldlow)            nil
      (ss/includes? oldlow newlow) nil
      (ss/includes? newlow oldlow) (ss/trim new)
      :else                        (ss/trim-join "\n\n------------------------\n\n"
                                             old new))))

(defmethod -xml-keeper :note
  [_ review]
  (when-let [note (some-> review :huomautukset first :huomautus
                          :kuvaus ss/trim ss/blank-as-nil)]
    (-> (make-raw-common review)
        (assoc :note note)
        make-note-review)))

(defmethod -task-keeper :note
  [_ {application-id :id} {:keys [state data] :as task}]
  (let [data (tools/unwrapped data)
        note (some-> data :katselmus :huomautukset :kuvaus)]
    (when (and (task-util/task-is-review? task)
               (= state "sent"))
      (-> (make-task-common application-id task data)
          (assoc :note note)
          make-note-review))))

(defmethod -review-updates :note
  [_ task-review xml-review]
  (when-let [note (resolve-note (:note task-review) (:note xml-review))]
    {:tasks.$.data.katselmus.huomautukset.kuvaus.value note}))

;; ----------------------------------
;; Process template
;; ----------------------------------

(defn- reviews-updates [fix-type application xml]
  (let [xml-reviews (source-reviews fix-type xml)
        app-reviews (target-tasks fix-type application)]
    (when (and xml-reviews app-reviews)
      (some->> (for [[task target] (find-matches fix-type app-reviews xml-reviews)
                     :let          [m (-review-updates fix-type task target)]
                     :when         m]
                 [(:mongo-id task) m])
               seq
               (into {})))))

(defn- process-application-reviews
  [fix-type {app-id :id :as application} xml]
  {:pre [app-id]}
  (log/with-logging-context {:applicationId app-id
                             :fix-type      fix-type}
    (when-let [updates (reviews-updates fix-type application xml)]
      (doseq [[task-id setters] updates]
        (mongo/update-by-query :applications
                               {:_id   app-id
                                :tasks {$elemMatch {:id task-id}}}
                               {$set setters}))
      (mongo/update-by-id :applications
                          app-id
                          (application-updates fix-type (keys updates))))))

(defn process-application-review-in-use-fixes
  ([application xml]
   (process-application-reviews :end-review application xml))
  ([application]
   (process-application-review-in-use-fixes application
                                            (krysp/get-application-xml-by-application-id application))))

(defn process-application-review-note-fixes
  [application xml]
  (process-application-reviews :note application xml))

;; ----------------------------------
;; Process missing reviews
;; ----------------------------------

(defn- update-reviews [system application xml]
  {:pre [(map? system)]}
  (let [user                         (usr/batchrun-user [(:organization application)])
        created                      (date/timestamp (date/now))
        {:keys [updates added-tasks-with-updated-buildings
                attachments-by-ids]} (review/read-reviews-from-xml conv-util/conversion-user-data
                                                                   created application xml
                                                                   {:do-not-include-state-updates? true
                                                                    :skip-task-validation?         true})
        review-command               (assoc (action/application->command application user)
                                            :action "fix-review-updates"
                                            :system system)
        task-ids                     (some->> added-tasks-with-updated-buildings seq (mapv :id))]
    (when (and task-ids
               (:ok (review/save-review-updates review-command
                                                updates
                                                added-tasks-with-updated-buildings
                                                attachments-by-ids true)))
      task-ids)))

(defn process-application-missing-reviews
  [system application xml]
  {:pre [(map? system)]}
  (when-let [task-ids (update-reviews system application xml)]
    (mongo/update-by-id :applications
                        (:id application)
                        (application-updates :missing task-ids))))

(env/in-dev
  ;; Convenience functions to dry-run (part of) the fix updates.
  (defn- testrun-file [filename]
    (let [xml    (-> filename slurp (xml/parse-string "utf8"))
          app-id (:application-id (conv-util/find-xml-ids "R" filename))]
      (log/with-logging-context {:applicationId app-id}
        (if (and xml app-id)
          (try
            (when-let [updates (reviews-updates :end-review (mongo/by-id :applications app-id) xml)]
              {:app-id   app-id
               :filename (fs/file-name filename)
               :updates  updates})
            (catch Exception e
              (log/error e)))
          (log/warn "Filename:" (fs/file-name filename))))))

  #_{:clj-kondo/ignore [:unused-private-var]}
  (defn- testrun [dir]
    (->> (fs/glob dir "*.xml")
         (keep (comp testrun-file str))))

  #_{:clj-kondo/ignore [:unused-private-var]}
  (defn- testprocess [dir]
    (doseq [filename (map str (fs/glob dir "*.xml"))
            :let     [xml (-> filename str slurp (xml/parse-string "utf8"))
                      app-id (:application-id (conv-util/find-xml-ids "R" filename))]
            :when    (and xml app-id)]
      (process-application-review-in-use-fixes (mongo/by-id :applications app-id)
                                               xml))))
