(ns lupapalvelu.batchrun.repair
  (:require [babashka.fs :as fs]
            [clojure.pprint :refer [pprint]]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-date :as verdict-date]
            [lupapalvelu.review :as review]
            [lupapalvelu.verdict :as verdict]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema.core :as sc]
            [taoensso.timbre :as timbre]))

(def DateRange (sc/pred (fn [[start end]]
                          (date/before? (date/start-of-day start)
                                        (date/end-of-day end)))
                        "Valid date range"))

(sc/defschema RepairConfiguration
  "For the review cleanup, only reviews whose `:tila` property has been modified within
  `:date-range` are considered.

   For verdict date fix, the date range is matched against a verdict timestamp property."
  (sc/conditional
    :application-ids {:date-range      DateRange
                      :application-ids [ssc/NonBlankStr]}
    :else {:organization-ids [ssc/NonBlankStr]
           :date-range       DateRange}))

(sc/defn ^:always-validate config->str :- ssc/NonBlankStr
  [cfg :- RepairConfiguration]
  (with-out-str (pprint cfg)))

(defn background-id [unwrapped-review]
  (some-> unwrapped-review :data :muuTunnus ss/lower-case ss/blank-as-nil))

(defn task-attachment-archived? [attachments task-id]
  (some (fn [{:keys [source target metadata]}]
          (and (or (= task-id (:id source))
                   (= task-id (:id target)))
               (= (:tila metadata) "arkistoitu")))
        attachments))

(defn pack-review [task]
  {:name      (:taskname task)
   :kind      (-> task :data :katselmuksenLaji)
   :authority (util/pcond-> (-> task :data :katselmus :pitaja)
                map? :code)
   :timestamp (-> task :data :katselmus :pitoPvm date/timestamp)
   :state     (-> task :data :katselmus :tila)})

(defn task-match?
  "Tasks match if _either_ their background-ids match or enough of the details match."
  [mongo-task xml-task]
  (let [bid (ss/blank-as-nil (background-id mongo-task))]
    (or (and bid (= bid (background-id xml-task)))
        (= (pack-review mongo-task) (pack-review xml-task)))))

(defn bad-reviews [cfg application kuntagml]
  (let [{m-tasks :application
         x-tasks :kuntagml} (review/application-vs-kuntagml-bad-review-candidates cfg
                                                                                  application
                                                                                  kuntagml)]
    (->> m-tasks
         (remove (fn [mongo-task]
                   (or (some (partial task-match? mongo-task) x-tasks)
                       (task-attachment-archived? (:attachments application)
                                                  (:id mongo-task)))))
         seq)))

(defn find-bad-reviews
  "`kuntagmls` are alternative (via application id or municipality id) WFS results. We
  continue ONLY IF ONLY one of them contains relevant data."
  [cfg application kuntagmls]
  (logging/with-logging-context {:applicationId (:id application)}
   (let [kuntagmls (filter #(xml/select1 % [:RakennusvalvontaAsia]) kuntagmls)
         n         (count kuntagmls)]
     (if-not (= n 1)
       (timbre/warn "Bad # of KuntaGML messages:" n)
       (doall (for [{:keys [id taskname]} (bad-reviews cfg application (first kuntagmls))
                    :let [_ (timbre/info "Bad review:" id taskname)]]
                id))))))

(sc/defn ^:private ^:always-validate date-range-timestamps :- [ssc/Timestamp]
  [{:keys [date-range]} :- RepairConfiguration]
  [(-> date-range
       first
       date/start-of-day
       date/timestamp)
   (-> date-range
       second
       date/end-of-day
       date/timestamp)])

(sc/defn ^:private ^:always-validate select-cleanup-targets
  [{:keys [application-ids organization-ids]
    :as   cfg} :- RepairConfiguration]
  (let [[start-ts end-ts] (date-range-timestamps cfg)]
    {:targets  (->> (if application-ids
                      {:_id {$in application-ids}}
                      {:organization {$in organization-ids}
                       :permitType   :R
                       :tasks        {$elemMatch {:source.type                  :background
                                                  :state                        :sent
                                                  :data.katselmus.tila.modified {$gt start-ts
                                                                                 $lt end-ts}}}})
                    (mongo/select :applications)
                    (group-by :organization))
     :start-ts start-ts
     :end-ts   end-ts}))

(sc/defn ^:private ^:always-validate parse-arguments :- {:config   RepairConfiguration
                                                         :dry-run? sc/Bool}
  "Arguments are `filename.edn` and (optional) `-dry-run` in any order. The file must
  exist. Throws on bad arguments"
  [args]
  (let [{dry-runs  true
         filenames false} (group-by #(= % "-dry-run") args)
        filename          (first filenames)]
    (cond
      (or (> (count dry-runs) 1)
          (not= (count filenames) 1))
      (throw (ex-info "Bad arguments, should be filename.edn [-dry-run]."
                      {:args args}))

      (fs/exists? filename)
      {:config   (util/read-edn-file filename)
       :dry-run? (boolean (seq dry-runs))}

      :else
      (throw (ex-info (str "File not found: " filename)
                      {:args args})))))

(sc/defn ^:always-validate initialize-cleanup :- {:start-ts ssc/Timestamp
                                                  :end-ts   ssc/Timestamp
                                                  :targets  {ssc/NonBlankStr [{sc/Keyword sc/Any}]}
                                                  :dry-run? sc/Bool}
  ([& args :- [ssc/NonBlankStr]]
   (let [{:keys [dry-run? config]} (parse-arguments args)]
     (assoc (select-cleanup-targets config)
            :dry-run? dry-run?))))


;; ---------------------------
;; Verdict dates
;; ---------------------------

(def timestamp-keys "Verdict keys whose value is a timestamp. See `verdict/Paatos` for details"
  #{;; Maarays
    :maaraysPvm :maaraysaika :toteutusHetki
    ;; Poytakirja
    :paatospvm
    ;; Paatos
    :anto :lainvoimainen :aloitettava :voimassaHetki :viimeinenValitus
    :raukeamis :paatosdokumentinPvm :julkipano})

(defn- pack-dates
  "Packs the given map `m` into keyword - timestamp map, where keys are keyword
  paths (starting with `prefix`)."
  ([prefix m]
   (->> (for [[k v] m]
          (cond
            (contains? timestamp-keys k)
            ;; Start of day since older verdicts may have UTC/EET offset.
            (hash-map (util/kw-path prefix k) (date/timestamp (date/start-of-day v)))

            (map? v)
            (pack-dates (util/kw-path prefix k) v)

            (sequential? v)
            (map-indexed (fn [i m]
                           (pack-dates (util/kw-path prefix k i) m))
                         v)))
        flatten
        (apply merge)
        util/strip-nils))
  ([m]
   (pack-dates nil m)))

(defn- date-changes [prefix mongo-verdict xml-verdict]
  (let [mongo-dates (pack-dates prefix mongo-verdict)]
    (some->> (pack-dates prefix xml-verdict)
             (filter (fn [[k v]]
                       (not= (get mongo-dates k) v)))
             seq
             (into {}))))

(defn- verdict-dates-updates
  [[start-ts end-ts] {:keys [verdicts] :as application}]
  (let [xml-verdicts (verdict/get-xml-verdicts application)]
    (some->> verdicts
             (map-indexed (fn [i {:keys [kuntalupatunnus draft timestamp] :as mongo-verdict}]
                            (when (and (not draft) timestamp (< start-ts timestamp end-ts))
                              (when-let [xml-verdict (util/find-by-key :kuntalupatunnus
                                                                       kuntalupatunnus
                                                                       xml-verdicts)]
                                (date-changes (util/kw-path :verdicts i)
                                              mongo-verdict
                                              xml-verdict)))))
             (apply merge)
             not-empty
             (hash-map $set))))

(defn fix-verdict-dates
  [args]
  (let [{:keys [config dry-run?]} (parse-arguments args)
        in-fn                     #(some->> config % (hash-map $in))
        [start-ts end-ts
         :as ts-range]            (date-range-timestamps config)
        note-ts                   (date/timestamp (date/now))]
    (doseq [{app-id :id
             :as    application} (mongo/select :applications
                                               (util/strip-nils
                                                 {:_id          (in-fn :application-ids)
                                                  :organization (in-fn :organization-ids)
                                                  :verdicts     {$elemMatch {:draft     {$ne true}
                                                                             :timestamp {$gt start-ts
                                                                                         $lt end-ts}}}}))
            :let                 [updates (verdict-dates-updates ts-range application)]
            :when                updates]
      (logging/with-logging-context {:applicationId app-id}
        (timbre/info "Fix verdict dates" (if dry-run? "(dry-run, mongo not updated)" ""))
        (when-not dry-run?
          (mongo/update-by-id :applications
                              app-id
                              (assoc updates
                                     $push {:_sheriff-notes {:note    "Batchrun: fix-verdict-dates"
                                                             :created note-ts}}))
          (verdict-date/update-verdict-date app-id))))))

(defn neighbors-with-missing-attachments
  "Params:
  - organizations: only consider given organizations"
  [organizations]
  (->> (mongo/aggregate
         :applications
         ;; 1662422400000 is 06.09.2022 when the problematic code was merged to master
         [{"$match" (merge {:neighbors {$elemMatch {:status.created {$gt 1662422400000}
                                                    :status.state   "response-given-ok"}}}
                           (when (seq organizations)
                             {:organization {$in organizations}}))}
          {"$lookup" {:from         :organizations
                      :localField   "organization"
                      :foreignField "_id"
                      :as           "organizationDetails"}}
          {"$match" {:organizationDetails.no-comment-neighbor-attachment-enabled {$ne true}}}
          {"$project" {:attachments  "$attachments"
                       :neighbors    "$neighbors"
                       :organization "$organization"}}])
       (reduce
         (fn [acc app]
           (concat acc
                   (mapcat
                     (fn [neighbor]
                       (keep (fn [status]
                               (when (and (= (:state status) "response-given-ok")
                                          (not-any? #(-> % :source :id (= (:id neighbor))) (:attachments app)))
                                 {:app-id (:_id app) :neighbor-id (:id neighbor) :org (:organization app)}))
                             (:status neighbor)))
                     (:neighbors app))))
         [])
       (remove nil?)
       doall))
