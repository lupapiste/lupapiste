(ns lupapalvelu.imports.core
  "Excel import functionality. Typically, the given excel file is processed in the following steps:

  1. Upload. The file is uploaded and processed in Laundry.

  2. Configuration. The results from Laundry are stored and a corresponding
  configuration (title, columns, ...) is created (into `:import-configs` collection). The
  user can edit the configuration before the actual import.

  3. Import. The stored Laundry data is processed according to the configuration and for
  each row a corresponding document is created into `:import-rows` collection.

  4. Edit. After import, the user can edit the resulting rows and the
  configuration (within limits)."
  (:require [babashka.fs :as fs]
            [clojure.core.match :as match]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [lupapalvelu.imports.schemas :as im-sc]
            [lupapalvelu.imports.util :as im-util]
            [lupapalvelu.job :as job]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.remote-excel-reader :as excel-reader]
            [lupapalvelu.storage.file-storage :as file-storage]
            [monger.operators :refer :all]
            [sade.core :refer [fail fail! ok]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.nippy :as nippy])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(set! *warn-on-reflection* true)

(def ^:private preview-count "Maximum number of preview rows" 50)
(def ^:private batch-size "Mongo write batch size for imported rows" 10)

(defn- upload-import-excel
  "Upload excel file and create import configuration draft. The whole process consists of three separate steps:

  1. The excel file is parsed in Laundry.
  2. The (string) rows are cleaned and trimmed. The resulting rows are are serialized into
     on organization import bucket.
  3. A draft import configuration is created in mongo."
  [organization-id user created {:keys [content-type tempfile filename]}]
  (let [filename     (mime/sanitize-filename filename)
        {:keys [window1904 rows error]
         :as   m}    (excel-reader/read-xlsx-rows-fast (:id user)
                                                       tempfile
                                                       filename
                                                       content-type)
        _            (when error (throw+ m))
        [hdr & rows] (->> rows
                          ;; Remove carriage return garbage and trim.
                          (ss/stringwalk (util/fn-> (ss/replace "_x000D_" "") ss/trim))
                          (remove #(every? ss/blank? %)))
        _            (when (empty? rows)
                       (throw+ {:error :error.imports.no-rows}))
        mongo-id     (mongo/create-id)
        file-id      (format "%s/import-%s" organization-id mongo-id)
        frozen       (nippy/freeze rows)
        columns      (map-indexed (fn [i s]
                                    {:id      (mongo/create-id)
                                     :title   (im-util/make-title s)
                                     :type    {:type :text}
                                     :index   i
                                     :include true})
                                  hdr)
        column-ids   (map :id columns)
        cfg          (im-sc/valid-config!
                       {:id           mongo-id
                        :organization organization-id
                        :title        (-> filename fs/strip-ext
                                          ss/capitalize im-util/make-title)
                        :rowdata      (cond-> {:filename   filename
                                               :object-key file-id
                                               :n          (count rows)}
                                        window1904 (assoc :window1904 true))
                        :modified     (im-util/make-user-ts user created)
                        :columns      columns
                        :previews     (mapv (util/fn->> (zipmap column-ids)
                                                        (util/filter-map-by-val ss/not-blank?))
                                            (take preview-count rows))})]
    (with-open [is (ByteArrayInputStream. frozen)]
      (file-storage/upload-import-data file-id
                                       filename
                                       "application/octet-stream"
                                       is))
    (mongo/insert :import-configs cfg)
    cfg))

(defn start-upload-job
  "uploads excel via `upload-import-excel`. Since the uploading process can take some time,
  it is done in its own thread that updates the corresponding organization job. Returns
  the job id."
  [{:keys [user created data]}]
  (let [{org-id   :organizationId
         filedata :file} data
        {job-id :id
         :as    job}     (job/store-organization-job org-id user :upload-import-excel {}
                                                     :filename (:filename filedata))]
    (future (try+
              (some->> (upload-import-excel org-id user created filedata)
                       :id
                       (hash-map :config-id)
                       (job/update-organization-job job-id :status :done :value))
              (catch map? m
                (log/error "Import excel upload job failed:" job "due to" m)
                (job/update-organization-job job-id
                                             :status :done
                                             :value m))
              (catch Exception e
                (log/error e "Import excel upload job failed:" job)
                (job/update-organization-job job-id
                                             :status :done
                                             :value {:error :error.imports.upload
                                                     :text  (ex-message e)}))))
    job-id))

(defn config-exists
  "Pre-checker for checking configuration or column existence. Additional check keywords:

  `:imported` The configuration must have been imported.
  `:not-deleted` The configuration must _not_ have been marked deleted.
  `:previews` The configuration must have previews
  `:draft` Opposite of `:imported`: the configuration must not been imported or currently
  importing.
  `:key-column` The configuration must have an included key column (backend-id or application-id).
  `:other-columns` The configuration must have an included regular (not key) column. This
  kind of check is needed, since key columns are not shown in the application section."
  [& ks]
  (fn [{{:keys [organizationId configId column
                columnId]} :data
        :keys              [application]}]
    (let [org-id (or organizationId (:organization application))]
      (when (and org-id configId)
        (let [cfg              (some->> (util/assoc-when {:_id          configId
                                                          :organization org-id}
                                                         :columns.id   (or (:id column)
                                                                           columnId))
                                        (mongo/select-one :import-configs)
                                        im-sc/->config)
              included-columns (filter :include (:columns cfg))]
          (when (not-every? (fn [k]
                              (case k
                                nil            cfg
                                :imported      (:imported cfg)
                                :not-deleted   (not (:deleted cfg))
                                :previews      (seq (:previews cfg))
                                :draft         (not (or (:importing-job cfg)
                                                        (:imported cfg)))
                                :key-column    (some im-sc/key-column? included-columns)
                                :other-columns (some (complement im-sc/key-column?)
                                                     included-columns)))
                            (cons nil ks))
            (fail :error.not-found)))))))

(defn get-configurations
  "Every import configuration for organization `org-id`, regardless of their states. If
  `frontend?` is true (default false), the results are passed through
  `im-sc/->frontend-config`, otherwise through `im-sc/->config`. Returns nil if no
  configurations found."
  ([org-id frontend?]
   (let [cfg-fn (if frontend?
                  im-sc/->frontend-config
                  im-sc/->config)]
     (some->> (mongo/select :import-configs {:organization org-id})
              seq
              (mapv cfg-fn))))
  ([org-id]
   (get-configurations org-id false)))

(defn get-configuration
  "Import configuration for organization `org-id` with id `config-id`. If `frontend?` is
  true (default false), the result is passed through `im-sc/->frontend-config`, otherwise
  through `im-sc/->config`. Returns nil the configuration is not found."
  ([org-id config-id frontend?]
   (when-let [m (mongo/select-one :import-configs {:_id config-id :organization org-id})]
     (if frontend?
       (im-sc/->frontend-config m)
       (im-sc/->config m))))
  ([org-id config-id]
   (get-configuration org-id config-id false)))

(defn- command->organization-id [{:keys [application data]}]
  (or (:organization application)
      (:organizationId data)))

(defn command->row
  ([{{:keys [rowId cellId]} :data :as command} projection]
   (cond-> (mongo/select-one :import-rows
                             (cond-> {:organization (command->organization-id command)
                                      :_id          rowId}
                               cellId (assoc :cells.id cellId))
                             projection)
     (empty? projection) im-sc/->row))
  ([command] (command->row command [])))

(defn command->config
  [{{:keys [configId rowId]} :data :as command}]
  (let [org-id (command->organization-id command)]
    (->> {:_id          (or configId
                            (:config-id (mongo/select-one :import-rows
                                                          {:_id          rowId
                                                           :organization org-id}
                                                          [:config-id])))
          :organization org-id}
        (mongo/select-one :import-configs)
        im-sc/->config)))

(defn- make-title-in-outs
  "`:in` - `:out` map for the given `title`. In/outs are convenience mapping that is later
  converted to mongo updates (in -> $set, out -> $unset)."
  [title]
  (some->> title
           (reduce-kv (fn [acc k v]
                        (let [k (util/kw-path :title k)]
                          (if (ss/blank? v)
                            (update acc :out conj k)
                            (assoc-in acc [:in k] (ss/trim v)))))
                      {:out #{}})
           util/strip-empty-collections
           not-empty))

(defn- make-column-in-outs
  "In/out map for for an individual configuration column. The mapping resolves 'delta'
  between new `column` and the old one."
  [{:keys [imported columns]} column]
  (when-let [old (util/find-by-id (:id column) columns)]
    (let [new-type (some-> column :type keyword)
          in-outs  (for [[k v] (dissoc column :id)]
                     (match/match
                       [(boolean imported)
                        (or new-type (-> old :type :type))
                        k]
                       [_ _ :title] (make-title-in-outs v)
                       [_ :text :lines] (let [v (util/->int v)]
                                          (if (> v 1)
                                            {:in {:type.lines v}}
                                            {:out #{:type.lines}}))
                       [_ :number :unit] (if (ss/blank? v)
                                           {:out #{:type.unit}}
                                           {:in {:type.unit v}})
                       [false _ :type] (cond-> {:in {:type.type new-type}
                                                :out #{}}
                                         (not= :text new-type)
                                         (update :out conj :type.lines)
                                         (not= :number new-type)
                                         (update :out conj :type.unit))
                       [false _ :include] {:in {:include v}}
                       :else nil))
          prefix   (partial util/kw-path :columns.$)
          outs     (some->> in-outs (keep :out) (apply set/union))
          ins      (some->> in-outs (keep :in) (apply merge)
                            (remove (fn [[k _]] (contains? outs k)))
                            (into {}))]
      (some-> {:in  (util/map-keys prefix ins)
               :out (mapv prefix outs)}
              util/strip-empty-collections
              not-empty))))

(defn- edit-config-mongo-updates
  "Returns `query`, `updates` map for `mongo/update-by-query` purposes. A separate function
  makes testing easier."
  [cfg {:keys [data]}]
  (let [{:keys [title column]} data
        title-io               (some-> title make-title-in-outs)
        column-io              (some->> column (make-column-in-outs cfg))
        sets                   (some->> (merge (:in title-io) (:in column-io))
                                        not-empty)
        unsets                 (some->> (set/union (:out title-io) (:out column-io))
                                        not-empty (mapv #(hash-map % true))
                                        (into {}))]
    (when (or sets unsets)
      {:query   (util/assoc-when {:_id (:id cfg)}
                                 :columns (some->> (:id column)
                                                   (assoc-in nil [$elemMatch :id])))
       :updates (util/strip-nils {$set   sets
                                  $unset unsets})})))

(defn edit-config
  "Edit configuration according to given `command`."
  [command]
  (when-let [{:keys [query updates]} (edit-config-mongo-updates (command->config command)
                                                                command)]
    (mongo/update-by-query :import-configs query (im-util/with-modified command updates))))

(defn reorder-columns
  "Updates configuration column indeces according to `command`."
  [command]
  (let [{:keys [columns]
         :as   cfg} (command->config command)
        new-order   (-> command :data :columnIds (zipmap (range)))
        _           (when-not (= (set (map :id columns)) (set (keys new-order)))
                      (fail! :error.columns-do-not-match))
        updates     (->> columns
                         (map-indexed (fn [i {id :id}]
                                        [(util/kw-path :columns i :index) (get new-order id)]))
                         (into {})
                         (hash-map $set)
                         (im-util/with-modified command))]
    (mongo/update-by-id :import-configs (:id cfg) updates)))

(sc/defn previews :- (sc/maybe [{ssc/ObjectIdStr im-sc/Preview}])
  "Previews for included columns. Either every included column or the one specified in the
  command parameters."
  [command]
  (let [{:keys [previews columns]
         :as   cfg} (command->config command)
        column-id   (some-> command :data :columnId)
        columns     (cond->> (filter :include columns)
                      column-id (util/filter-by-key :id column-id))]
    (when (seq columns)
      (into []
            (for [p previews]
              (into {}
                    (for [{col-id   :id
                           col-type :type} columns
                          :let             [s (get p col-id)]
                          :when            (ss/not-blank? s)
                          :let             [col-type (:type col-type)
                                            {:keys [value error]} (im-util/parse-cell cfg col-type s :excel)]]
                      [col-id (util/assoc-when
                                {:raw   s
                                 :state (if error :error :ok)}
                                :value value)])))))))

(defn- make-cell-data
  "Returns a map with `cell` and (optional) `error` keys."
  [{:keys [cfg] :as m} column raw]
  (when (:include column)
    (let [col-type              (-> column :type :type)
          {:keys [value error]} (im-util/parse-cell cfg col-type raw :excel)
          cell-id               (mongo/create-id)]
      (-> {:cell  (util/strip-nils
                    {:id        cell-id
                     :column-id (:id column)
                     :type      col-type
                     :value     value
                     :modified  (im-util/make-user-ts m)
                     :original  {:raw   raw
                                 :value value}})
           :error (when error cell-id)}
          util/strip-blanks
          util/strip-empty-maps))))

(defn- key-missing?
  "True if _every_ key cell is blank."
  [cells]
  (->> cells
       (filter im-sc/key-cell?)
       (every? (comp ss/blank? :value))))

(sc/defn ^:private ^:always-validate make-row-document :- im-sc/Row
  [{:keys [cfg] :as m} row]
  (let [columns    (:columns cfg)
        ;; Make sure that every column has a cell.
        cell-datas (->> (concat row (take (count columns) (repeat "")))
                        (zipmap columns)
                        (keep (partial apply make-cell-data m)))
        cells      (mapv :cell cell-datas)
        errors     (set (keep :error cell-datas))]
    (util/strip-nils {:id           (mongo/create-id)
                      :organization (:organization cfg)
                      :config-id    (:id cfg)
                      :cells        cells
                      :errors       (cond-> errors
                                      (key-missing? cells) (conj "key")
                                      true                 not-empty)})))

(defn- import-rows
  "Reads the rowdata from the import bucket and transforms each row into a row document. The
  progress is reported via given job. The import is rolled back (the already imported rows
  deleted) on exception."
  [{:keys [cfg] :as m} job-id]
  (try
    (let [{fun :content} (-> cfg :rowdata :object-key
                            file-storage/download-import-data)
         rows           (with-open [os (ByteArrayOutputStream.)
                                    is (io/input-stream (fun))]
                          (io/copy is os)
                          (nippy/thaw (.toByteArray os)))]
      (doseq [[i batch] (->> rows
                             (map #(make-row-document m %))
                             (partition-all batch-size)
                             (map-indexed (fn [i batch]
                                            [i batch])))
              :let [n (+ (* i batch-size) (count batch))]]
        (mongo/insert-batch :import-rows batch)
        (job/update-organization-job job-id :value {:n n})))
    (catch Exception e
      (log/error e "Import rows failed. Rolling back the changes.")
      (mongo/remove-many :import-rows {:config-id (:id cfg)})
      (throw e))))

(defn start-import-job
  "The actual importing is done in a separate thread and the progress is reported via
  job. After a successful import, the rowdata is deleted from the import bucket. Returns
  job."
  [{:keys [user] :as command}]
  (let [{cfg-id :id
         :as    cfg} (command->config command)
        {job-id :id
         :as    job} (job/store-organization-job (:organization cfg) user :import {}
                                                 :configId (:id cfg))]
    (future
      (try
        (mongo/update-by-id :import-configs cfg-id {$set {:importing-job job-id}})
        (import-rows {:cfg cfg :user user} job-id)
        (let [user-ts (im-util/make-user-ts user)]
          (mongo/update-by-id :import-configs cfg-id
                              {$unset {:importing-job      true
                                       :rowdata.window1904 true
                                       :rowdata.object-key true
                                       :rowdata.n          true
                                       :previews           true}
                               $set   {:columns  (keep #(when (:include %)
                                                          (dissoc % :include))
                                                       (:columns cfg))
                                       :modified user-ts
                                       :imported user-ts}}))
        (file-storage/delete-import-data (-> cfg :rowdata :object-key))
        (job/update-organization-job job-id :status :done)
        (catch Exception e
          (log/error e "Import job failed. Resetting config." job)
          (job/update-organization-job job-id
                                       :status :done
                                       :value (util/strip-blanks
                                                {:error :error.imports.import
                                                 :text  (ex-message e)}))
          (mongo/update-by-id :import-configs cfg-id {$unset {:importing-job true}}))))
    job))

(defn- tri-state-query [param]
  (case (keyword param)
    :only    {$exists true}
    :exclude {$exists false}
    nil))

(defn row-ids
  "Queries row ids from mongo according to `command`."
  [command]
  (let [{cfg-id :id}                (command->config command)
        {:keys [deleted errors added]
         :or   {deleted "exclude" errors "include"
                added   "include"}} (:data command)
        q                           (util/strip-nils {:config-id cfg-id
                                                      :deleted   (tri-state-query deleted)
                                                      :errors    (tri-state-query errors)
                                                      :added     (tri-state-query added)})]
    (mapv :id (mongo/select-options :import-rows q
                                    {:projection [:_id]
                                     :order-by   {:_id 1}}))))

(defn rows-for-ids
  "Rows (`:import-rows` documents) for ids listed in `command`."
  [command]
  (let [{cfg-id :id} (command->config command)
        row-ids      (some-> command :data :rowIds set not-empty)]
    (when row-ids
      (->> (mongo/select-options :import-rows {:config-id cfg-id
                                               :_id       {$in row-ids}}
                                 {:order-by {:_id 1}})
           (into [])))))

(defn row-cell [row cell-id]
  (util/find-by-id cell-id (:cells row)))

(defn edit-cell
  "Edits an individual cell. Either fails with validation error or updates the row. On
  success, returns processed value and modified info."
  [{{row-id :rowId cell-id :cellId v :value} :data :as command}]
  (let [cfg                   (command->config command)
        {:keys [cells errors]
         :as   row}           (command->row command)
        cell                  (row-cell row cell-id)
        {:keys [value error]} (im-util/parse-cell cfg (:type cell) (str v))]
    (if error
      (fail :error.invalid-value :validation-error error)
      (let [key-error? (when (im-sc/key-cell? cell)
                         (key-missing? (util/update-by-id cell-id assoc cells :value value)))
            new-errors (cond-> (disj errors cell-id)
                         key-error?          (conj "key")
                         (false? key-error?) (disj "key")
                         true                not-empty)]
        (mongo/update-by-query :import-rows
                               {:_id   row-id
                                :cells {$elemMatch {:id cell-id}}}
                            (im-util/with-modified command :cells.$.modified
                              (cond-> (cond
                                        (= new-errors errors) {}
                                        new-errors {$set {:errors new-errors}}
                                        :else      {$unset {:errors true}})
                                value        (assoc-in [$set :cells.$.value] value)
                                (nil? value) (assoc-in [$unset :cells.$.value] true))))
        (ok :value value :modified (im-util/make-user-ts command))))))

(defn delete-config
  "Deletes config. Should be called only for draft configs (checked in pre-checks)."
  [command]
  (let [{cfg-id :id rowdata :rowdata} (command->config command)]
    (some->> rowdata :object-key file-storage/delete-import-data)
    (mongo/remove-by-id :import-configs cfg-id)))

(defn- mark-deleted-if-needed
  "Toggles deletion status (`:deleted` property) via `mongo-fn`. `deleted` is the current
  property and `delete?` the toggled flag."
  [command mongo-fn deleted delete?]
  (cond
    (= delete? (boolean deleted)) nil
    delete?                       (let [user-ts (im-util/make-user-ts command)]
                                    (mongo-fn {$set {:deleted user-ts}})
                                    user-ts)
    :else                         (mongo-fn {$unset {:deleted true}})))

(defn mark-config-deleted
  [{:keys [data] :as command}]
  (let [delete?            (:deleted data)
        {cfg-id  :id
         deleted :deleted} (command->config command)
        mongo-fn           #(mongo/update-by-id :import-configs cfg-id %)]
    (mark-deleted-if-needed command mongo-fn deleted delete?)))

(defn mark-row-deleted [{:keys [data] :as command}]
  (let [delete?            (:deleted data)
        {row-id  :id
         deleted :deleted} (command->row command)
        mongo-fn           #(mongo/update-by-id :import-rows row-id %)]
    (mark-deleted-if-needed command mongo-fn deleted delete?)))

(defn- application-configs-query [command]
  {:organization (command->organization-id command)
   :deleted      {$exists false}
   :imported     {$exists true}})

(defn application-has-configurations
  "Pre-checker that fails if there are no active import configurations for the application."
  [command]
  (when-not (mongo/any? :import-configs (application-configs-query command))
    (fail :error.not-found)))

(defn- just-or-in [xs]
  (if (= 1 (count xs)) (first xs) {$in xs}))

(defn- verdict-backend-ids [application]
  (some->>  (:verdicts application)
            (keep vc/verdict-municipality-permit-id)
            seq set))

(defn- application-rows-query
  [{:keys [application] :as command} cfg-ids]
  (when (seq cfg-ids)
    (let [app-id   (:id application)
          ;; We only consider backend-ids received from the backing system.
          bids     (verdict-backend-ids application)
          app-id-q {:type :application-id :value app-id}]
      {:organization (command->organization-id command)
       :config-id    (just-or-in cfg-ids)
       $or           [{:cells {$elemMatch (if bids
                                            {$or [{:type  :backend-id
                                                   :value (just-or-in bids)}
                                                  app-id-q]}
                                            app-id-q)}}
                      {:added.id app-id}]})))

(defn application-configurations-and-rows [command]
  (let [cfgs (some->> (application-configs-query command)
                      (mongo/select :import-configs)
                      seq
                      (mapv im-sc/->application-config))]
    (some->> {:configs cfgs
              :rows    (some->> cfgs
                                (mapv :id)
                                (application-rows-query command)
                                (mongo/select :import-rows)
                                seq
                                (mapv im-sc/->application-row))}
             util/strip-nils
             not-empty)))

(defn- make-application-import-row
  "New row for `application` and configurtion `cfg`. Separate function for easier testing."
  [{:keys [application] :as command} cfg]
  (let [cfg (update cfg :columns (fn [columns]
                                   (map #(assoc % :include true)
                                        columns)))
        bid (first (verdict-backend-ids application))
        row (-> (make-row-document (assoc command :cfg cfg) nil)
                (dissoc :errors)
                (assoc :added (assoc (im-util/make-user-ts command)
                                     :id (:id application)))
                (update :cells #(util/update-by-key :type :application-id
                                                    assoc % :value (:id application))))]
    (cond-> row
      bid (update :cells #(util/update-by-key :type :backend-id assoc % :value bid)))))

(defn add-aplication-import-row [command]
  (let [row (make-application-import-row command (command->config command))]
    (mongo/insert :import-rows row)
    (im-sc/->application-row row)))
