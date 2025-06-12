(ns lupapalvelu.ui.auth-admin.imports.data.config
  "Model support for an individual configuration (creation, import, editing). The following
  keys can be found under the datapath:

  `:visible?` True if the view should be rendered.

  `:org-id` Current organization id.

  UPLOAD

  `:file` File selected for upload.

  `:mode` UI state during upload. Initially unset, but can later be either `:upload` or
  `:error`. Used only when uploading the excel file.

  `:job` Upload job.

  `:error` Upload error. `:error` code and `:text` map.

  CONFIGURATION (before and after import)

  `:edit` Map hierarchy for active editors (main title or a column). Subkey is either
  column-id or `:title`.

  `:config` Current configuration.

  `:previews` List of column-id - preview info maps used to render the cell previews. Only
  before import.

  `:sorted-column-ids` List of column-ids in the manually (drag'n'drop) sorted order.

  `:import-job` Importing job

  AFTER IMPORT

  `:row-id-count` Total number of row-ids matching the latest query

  `:row-windows-ids` Ids of every row that matched the latest query. Partitioned into
  windows (pages). Since an import can have huge amount of lines, only the full rows for
  the current window/page are stored into db (see `::idata/rows`). When a window changes,
  its full rows are fetched.

  `:row-window` Current window index.

  `:include-deleted-rows` and `include-added-rows` Values of the corresponding tri-state
  buttons.

  `:include-error-rows` Value of the corresponding tri-state button. Error rows include
  only import-errors, since the subsequent bad edits are not stored into mongo."
  (:require [lupapalvelu.data :as data]
            [lupapalvelu.imports.schemas :as im-sc]
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.ui.auth-admin.imports.data.core :as dcore]
            [lupapalvelu.ui.auth-admin.org-subs :as org-subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.date :as date]
            [lupapalvelu.ui.imports.data.core :as idata]
            [lupapalvelu.ui.misc]
            [lupapalvelu.ui.user :as usr]
            [re-frame-fx.dispatch]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(def row-window-size "Maximum number of rows shown when paging." 100)

(def datapath (partial data/datapath ::data))
(def set-d    (partial data/set-d ::data))
(def get-d    (partial data/get-d ::data))
(def unset-d  (partial data/unset-d ::data))
(def ctx-d    (partial data/ctx-d ::data))

(defn- set-mode
  ([db mode submode]
   (-> db
       (set-d :mode (cond-> [mode]
                      submode (conj submode)))
       (unset-d :job)))
  ([db mode]
   (set-mode db mode nil)))

(defn- get-column [db-or-data column-id & ks]
  (cond-> (util/find-by-id column-id (ctx-d db-or-data :config :columns))
    (seq ks) (get-in ks)))

(defn- set-column [db column]
  (update-in db (datapath :config :columns)
             #(util/replace-by-id column %)))

(defn- update-column [db column-id update-fn & args]
  (update-in db (datapath :config :columns)
             #(apply util/update-by-id column-id update-fn % args)))

(defn- set-error
  ([db error mode]
   (cond-> (set-d db :error (select-keys error [:error :text]))
     mode (set-mode mode :error)))
  ([db error] (set-error db error (first (get-d db :mode)))))

(def clear-error
  (rf/->interceptor
    :id      ::clear-error
    :before  (fn [context]
               (update-in context [:coeffects :db] unset-d :error))))

(defn- tri-state-param [param tri-state]
  (some->> (cond
             (true? tri-state)  "only"
             (false? tri-state) "exclude"
             :else              "include")
           (hash-map param)))

(defn- set-tri-state [db k tri-state]
  (if (nil? tri-state)
    (unset-d db k)
    (set-d db k tri-state)))

;; -----------------------------
;; Events
;; -----------------------------

(rf/reg-event-fx ::fetch-config.success
  (fn [{db :db} [_ {:keys [config]}]]
    (let [{:keys [imported] :as cfg} (im-sc/->config config)
          col-ids                    (->> cfg :columns (sort-by :index) (mapv :id))]
      {:db        (cond-> (-> db
                              (set-d :config cfg)
                              (set-d :column-ids col-ids)
                              ;; These are updated when the columns are (manually) sorted
                              (set-d :sorted-column-ids col-ids))
                    imported (set-d :include-deleted-rows false))
       :fx        [[:dispatch [(if imported
                                 ::fetch-row-ids
                                 ::fetch-previews)]]]
       :scroll/to 0})))

(rf/reg-event-fx ::fetch-config
  (fn [{db :db} [_ config-id]]
    (let [org-id (get-d db :org-id)
          cfg-id (or config-id (get-d db :config :id))]
      {:action/query {:name    :get-import-config
                      :success ::fetch-config.success
                      :params  {:organizationId org-id
                                :configId       cfg-id}}})))

(rf/reg-event-fx ::toggle-view
  [(rf/inject-cofx ::org-subs/url-org-id)]
  (fn [{:keys [db] :as cofx} [_ visible?]]
    (let [org-id (::org-subs/url-org-id cofx)
          cfg-id (last (common/page-path))]
      (if (and cfg-id org-id visible?)
        {:db (set-d db {:visible? true
                        :org-id   org-id})
         :fx [[:dispatch [:auth/refresh]]
              [:dispatch [::fetch-config cfg-id]]]}
        {:db (unset-d db)}))))

(rf/reg-event-db ::poll-upload-job.error
  (fn [db [_ res]]
    (set-error db res)))

(rf/reg-event-fx ::poll-upload-job.success
  (fn [{db :db} [_ {:keys [jobs]}]]
    (let [{:keys [id status
                  value]} (first jobs)
          [mode job-id]   (first (get-d db :job))]
      (cond
        ;; No suitable results, keep polling.
        (or (not= job-id id) (util/=as-kw status :running))
        {:fx [[:dispatch [::poll-job id]]]}

        (:error value)
        {:db (set-error db value mode)}

        (= mode :upload)
        {:fx [[:dispatch [::dcore/goto-config (:config-id value)]]]}))))

(rf/reg-event-fx ::poll-upload-job
  (fn [{db :db} [_ job-id]]
    (let [[_ v]  (first (get-d db :job))
          org-id (get-d db :org-id)]
      (when (= v job-id)
        {:action/query {:name    :organization-jobs
                        :success ::poll-upload-job.success
                        :error   ::poll-upload-job.error
                        :params  {:organizationId org-id
                                  :jobIds         [job-id]
                                  :poll           true}}}))))

(rf/reg-event-fx ::upload-file.success
  (fn [{db :db} [_ {:keys [job-id]}]]
    {:db (set-d db [:job :upload] job-id)
     :fx [[:dispatch [::poll-upload-job job-id]]] }))

(rf/reg-event-fx ::upload-file
  [clear-error
   (rf/inject-cofx ::org-subs/url-org-id)]
  (fn [{db :db :as cofx}]
    (let [file   (get-d db :file)
          ;; Since upload is called outside of the config view
          org-id (::org-subs/url-org-id cofx)]
      {:db        (-> db
                      (set-d :org-id org-id)
                      (set-mode :upload))
       :post/form {:raw-name :upload-import-excel
                   :params   {:organizationId org-id
                              :file           file}
                   :success  ::upload-file.success}})))

(rf/reg-event-db ::edit-title
  (fn [db [_ lang title-id value]]
    (set-d db :edit title-id :title lang value)))

(rf/reg-event-fx ::edit-main-title
  (fn [{db :db} [_ op]]
    (case op
      :start  {:db (set-d db :edit :title (get-d db :config :title))}
      :cancel {:db (unset-d db :edit :title)}
      :save   (let [title (get-d db :edit :title)]
                {:db (-> db
                         (set-d :config :title title)
                         (unset-d :edit :title))
                 :fx [[:dispatch [::update-import-config {:title title}]]]}))))

(rf/reg-event-db ::toggle-edit-column
  (fn [db [_ column-id edit?]]
    (if edit?
      (set-d db :edit column-id (get-column db column-id))
      (unset-d db :edit column-id))))

(rf/reg-event-fx ::save-edit-column
  (fn [{db :db} [_ column-id]]
    (let [{:keys [id title type]
           :as   column}                (get-d db :edit column-id)
          {:keys [imported] cfg-id :id} (get-d db :config)]
      {:db (-> db
               (set-column column)
               (unset-d :edit column-id))
       :fx (cond-> [[:dispatch [::update-import-config
                                {:column (-> type
                                             (select-keys [:type :unit :lines])
                                             (assoc :id id :title title))}]]]
             imported (conj [:dispatch [::idata/update-column-type cfg-id id
                                        (select-keys type [:unit :lines])]]))})))

(rf/reg-event-db ::edit-column
  (fn [db [_ column-id & kvs]]
    (set-d db :edit column-id (butlast kvs) (last kvs))))

(rf/reg-event-db ::select-file
  [clear-error]
  (fn [db [_ file]]
    (set-d db :file file)))

(rf/reg-event-db ::remove-file
  [clear-error]
  (fn [db]
    (unset-d db :file)))

(rf/reg-event-db ::fetch-previews.success
  (fn [db [_ {:keys [previews]}]]
    (let [new-previews (map (partial util/map-keys name) previews)
          old-previews (get-d db :previews)]
      (set-d db :previews (if old-previews
                            (->> (zipmap old-previews new-previews)
                                 (mapv (fn [[old new]]
                                         (merge old new))))
                            new-previews)))))

(rf/reg-event-fx ::fetch-previews
  (fn [{db :db} [_ column-id]]
    {:action/query {:name   :import-config-previews
                    :params (util/assoc-when
                              {:organizationId (get-d db :org-id)
                               :configId       (get-d db :config :id)}
                              :columnId column-id)
                    :success ::fetch-previews.success}}))

(rf/reg-event-fx ::update-modified
  (fn [{db :db} [_ {:keys [modified imported]} fetch-column-id]]
    (cond-> {:db (set-d db :config :modified modified)}
      (and fetch-column-id (not imported))
      (assoc :fx [[:dispatch [::fetch-previews fetch-column-id]]]))))

(rf/reg-event-fx ::update-import-config
  (fn [{db :db} [_ params fetch-previews?]]
    (let [org-id   (get-d db :org-id)
          cfg-id   (get-d db :config :id)
          imported (get-d db :config :imported)
          fetch-id (when-not (or imported (false? fetch-previews?))
                      (some-> params :column :id))]
      {:action/command {:name    :update-import-config
                        :params  (merge {:organizationId org-id
                                         :configId       cfg-id}
                                        params)
                        :success [::update-modified fetch-id]}})))

(rf/reg-event-fx ::toggle-include
  (fn [{db :db} [_ column-id included?]]
    {:db (update-column db column-id assoc :include included?)
     :fx [[:dispatch [::update-import-config
                      {:column {:id      column-id
                                :include included?}}
                      included?]]]}))

(rf/reg-event-fx ::reorder-columns
  (fn [{db :db} [_ column-ids]]
    (let [org-id (get-d db :org-id)
          cfg-id (get-d db :config :id)]
      {:db             (set-d db :sorted-column-ids column-ids)
       :action/command {:name    :reorder-import-config-columns
                        :params  {:organizationId org-id
                                  :configId       cfg-id
                                  :columnIds      column-ids}
                        :success ::update-modified}
       :fx [[:dispatch [::idata/set-sorted-column-order cfg-id column-ids]]]})))

(rf/reg-event-fx ::poll-import-job.result
  (fn [{db :db} [_ {:keys [ok jobs]}]]
    (let [old-job              (get-d db :import-job)
          new-job              (some-> jobs first)
          done?                (util/=as-kw :done (:status new-job))
          {:keys [error text]} (:value new-job)
          repoll               {:dispatch-debounce {:id      :poll-import-again
                                                    :action  :dispatch
                                                    :event   [::poll-import-job]
                                                    :timeout 250}}]
      (if (and ok (= (:id old-job) (:id new-job)))
        (cond-> {:db (set-d db :import-job new-job)}
          done?       (assoc :fx [[:dispatch [::fetch-config]]])
          error       (assoc :dialog/show
                             {:type             :integration-error
                              :title-loc        :error.imports.import.title
                              :size             :medium
                              :component-params {:text    (common/loc error)
                                                 :details text}})
          (not done?) (merge repoll))

        ;; We assume/hope that the query failure was temporary. Let's try again.
        repoll))))

(rf/reg-event-fx ::poll-import-job
  (fn [{db :db}]
    (let [job    (get-d db :import-job)
          org-id (get-d db :org-id)]
      {:action/query {:name    :organization-jobs
                      :params  {:organizationId org-id
                                :jobIds         [(:id job)]
                                :types          [:import]
                                :modified       (:modified job)
                                :poll           true}
                      :success ::poll-import-job.result
                      :error   ::poll-import-job.result}})))

(rf/reg-event-fx ::execute-import.result
  (fn [{db :db} [_ {:keys [ok job text]}]]
    (if ok
      {:db (set-d db :import-job job)
       :fx [[:dispatch [::poll-import-job]]]}
      {:db                 (unset-d db :config :importing?)
       :indicator/negative {:text-loc text}})))

(rf/reg-event-fx ::execute-import
  (fn [{db :db}]
    (let [org-id (get-d db :org-id)
          cfg-id (get-d db :config :id)]
      {:db (set-d db :config :importing? true)
       :action/command {:name    :execute-import-config
                        :params  {:organizationId org-id
                                  :configId       cfg-id}
                        :success ::execute-import.result
                        :error   ::execute-import.result}})))

(rf/reg-event-fx ::move-row-window
  (fn [{db :db} [_ window-n]]
    (let [org-id (get-d db :org-id)
          cfg-id (get-d db :config :id)
          window (seq (nth (get-d db :row-windows-ids) window-n))]
      (cond-> {:db           (-> db
                                 (set-d :row-window window-n)
                                 (unset-d :row-open))}
        window        (assoc :action/query {:name    :import-config-rows
                                            :params  {:organizationId org-id
                                                      :configId       cfg-id
                                                      :rowIds         window}
                                            :success #(>evt [::idata/set-rows (:rows %)])})
        (nil? window) (assoc :fx [[:dispatch [::idata/set-rows []]]])))))

(rf/reg-event-fx ::fetch-row-ids.success
  (fn [{db :db} [_ {:keys [row-ids] :or {row-ids []}}]]
    (let [windows (or (seq (partition-all row-window-size row-ids))
                      [[]])]
      {:db (-> db
               (set-d :row-id-count (count row-ids))
               (set-d :row-windows-ids windows))
       :fx [[:dispatch [::move-row-window 0]]]})))

(rf/reg-event-fx ::fetch-row-ids
  (fn [{db :db}]
    (let [org-id  (get-d db :org-id)
          cfg     (get-d db :config)
          deleted (get-d db :include-deleted-rows)
          errors  (get-d db :include-error-rows)
          added   (get-d db :include-added-rows)]
      {:action/query {:name    :import-config-row-ids
                      :params  (merge {:organizationId org-id
                                       :configId       (:id cfg)}
                                      (tri-state-param :deleted deleted)
                                      (tri-state-param :errors errors)
                                      (tri-state-param :added added))
                      :success ::fetch-row-ids.success}
       :fx           [[:dispatch [::idata/set-configs [cfg]]]]})))

(rf/reg-event-fx ::include-deleted-rows
  (fn [{db :db} [_ tri-state]]
    {:db (set-tri-state db :include-deleted-rows tri-state)
     :fx [[:dispatch [::fetch-row-ids]]]}))

(rf/reg-event-fx ::include-added-rows
  (fn [{db :db} [_ tri-state]]
    {:db (set-tri-state db :include-added-rows tri-state)
     :fx [[:dispatch [::fetch-row-ids]]]}))

(rf/reg-event-fx ::include-error-rows
  (fn [{db :db} [_ tri-state]]
    {:db (set-tri-state db :include-error-rows tri-state)
     :fx [[:dispatch [::fetch-row-ids]]]}))

(rf/reg-event-fx ::delete-config
  (fn [{db :db}]
    (let [org-id (get-d db :org-id)
          cfg-id (get-d db :config :id)]
      {:action/command {:name    :delete-import-config
                        :params  {:organizationId org-id
                                  :configId       cfg-id}
                        :success ::dcore/goto-imports}})))

(rf/reg-event-db ::mark-config-deleted.success
  (fn [db [_ {:keys [deleted]}]]
    (if deleted
      (set-d db :config :deleted deleted)
      (unset-d db :config :deleted))))

(rf/reg-event-fx ::mark-config-deleted
  (fn [{db :db} [_ deleted?]]
    (let [org-id (get-d db :org-id)
          cfg-id (get-d db :config :id)]
      {:action/command {:name    :mark-import-config-deleted
                        :params  {:organizationId org-id
                                  :configId       cfg-id
                                  :deleted        deleted?}
                        :success ::mark-config-deleted.success}})))

(rf/reg-event-fx ::toggle-row-open
  (fn [{db :db} [_ row-id open?]]
    (cond-> {:db (set-d db :row-open row-id open?)}
      (not open?) (assoc :fx [[:dispatch [::idata/toggle-edit row-id false]]]))))

;; -----------------------------
;; Subs
;; -----------------------------

(rf/reg-sub ::data
  :-> get-d)

(rf/reg-sub ::visible?
  :<- [::data]
  :-> :visible?)

(rf/reg-sub ::fileinfo
  :<- [::data]
  :-> (fn [{file :file}]
        (when file
          {:filename (.-name file)
           :filesize (.-size file)})))

(rf/reg-sub ::mode
  :<- [::data]
  :-> :mode)

(rf/reg-sub ::config
  :<- [::data]
  :-> :config)

(rf/reg-sub ::config-id
  :<- [::config]
  :-> :id)

(rf/reg-sub ::column-ids
  :<- [::data]
  :-> :column-ids)

(rf/reg-sub ::sorted-column-ids
  :<- [::data]
  (fn [data]
    (->> (ctx-d data :sorted-column-ids)
         (remove #(false? (get-column data % :include)))
         seq)))

(rf/reg-sub ::column
  :<- [::config]
  (fn [cfg [_ column-id & ks]]
    (cond-> (util/find-by-id column-id (:columns cfg))
      (seq ks) (get-in ks))))

(rf/reg-sub ::column-edit
  :<- [::data]
  (fn [data [_ column-id]]
    (ctx-d data :edit column-id)))

(rf/reg-sub ::column-editing?
  (fn [[_ column-id]]
    [(rf/subscribe [::column-edit column-id])])
  :-> (comp boolean first))

(rf/reg-sub ::sortable?
  :<- [::data]
  :<- [::column-ids]
  :<- [::disabled?]
  (fn [[data col-ids disabled?]]
    (and (not disabled?)
         (not-any? #(ctx-d data :edit %) col-ids))))

(rf/reg-sub ::column-edit-type
  (fn [[_ column-id]]
    [(rf/subscribe [::column-edit column-id])])
  (fn [[column] [_ _ type-key]]
    (cond-> (:type column)
      type-key type-key)))

(rf/reg-sub ::error
  :<- [::data]
  :-> :error)

(rf/reg-sub ::title
  :<- [::data]
  (fn [data [_ lang column-id]]
    (if column-id
      (get-in (get-column data column-id) [:title lang])
      (ctx-d data :config :title lang))))

(rf/reg-sub ::titlestring
  (fn [[_ column-id]]
    (rf/subscribe [::title (keyword (js/loc.getCurrentLanguage)) column-id]))
  :-> identity)

(rf/reg-sub ::can-save-column?
  (fn [[_ column-id]]
    [(rf/subscribe [::column-edit column-id])])
  :-> (util/fn-> first :title :fi ss/not-blank?))

(rf/reg-sub ::title-editing?
  :<- [::data]
  :-> #(boolean (ctx-d % :edit :title)))

(rf/reg-sub ::can-save-title?
  :<- [::data]
  :-> (util/fn-> (ctx-d :edit :title :fi) ss/not-blank?))

(rf/reg-sub ::timestamp
  :<- [::config]
  (fn [cfg [_ k]]
    (ctx-d cfg k :ts)))

(rf/reg-sub ::previews
  :<- [::data]
  (fn [data]
    (when-not (ctx-d data :config :imported)
      (:previews data))))

(rf/reg-sub ::importing?
  :<- [::config]
  :-> (comp boolean :importing?))

(rf/reg-sub ::imported?
  :<- [::config]
  :-> (comp boolean :imported))

(rf/reg-sub ::can-start-import?
  :<- [::data]
  :<- [::config]
  (fn [[data cfg]]
    (let [included    (filter :include (:columns cfg))
          key-column? (util/fn->> :type :type
                                  (util/includes-as-kw? #{:backend-id
                                                          :application-id}))]
      (boolean (and (not (:importing? cfg))
                    (not (usr/virtual?))
                    (not (:imported cfg))
                    ;; No active edits
                    (empty? (:edit data))
                    ;; Key column
                    (some key-column? included)
                    ;; Other columns
                    (some (comp not key-column?) included))))))

(rf/reg-sub ::import-job
  :<- [::data]
  :-> :import-job)

(rf/reg-sub ::total-rows
  :<- [::config]
  :-> (comp :n :rowdata))

(rf/reg-sub ::import-progress
  :<- [::total-rows]
  :<- [::import-job]
  (fn [[n job]]
    (if-let [v (some-> job :value :n)]
      (int (* 100 (/ v n)))
      0)))

(rf/reg-sub ::disabled?
  :<- [::importing?]
  :<- [::deleted?]
  (fn [[importing? deleted?]]
    (or importing? deleted? (usr/virtual?))))

(rf/reg-sub ::row-total
  :<- [::data]
  :-> :row-id-count)

(rf/reg-sub ::window-index
  :<- [::data]
  :-> :row-window)

(rf/reg-sub ::last-window-index
  :<- [::data]
  :-> (comp dec count :row-windows-ids))

(rf/reg-sub ::first-window?
  :<- [::window-index]
  :-> zero?)

(rf/reg-sub ::last-window?
  :<- [::window-index]
  :<- [::last-window-index]
  (fn [[a b]]
    (= a b)))

(rf/reg-sub ::deleted-date
  :<- [::config]
  :-> (comp date/finnish-datetime :ts :deleted))

(rf/reg-sub ::deleted?
  :<- [::config]
  :-> (comp boolean :deleted))

(rf/reg-sub ::delete-active?
  :<- [::importing?]
  :-> (util/fn-> (or (usr/virtual?)) not))

(rf/reg-sub ::include-deleted-rows
  :<- [::data]
  :-> :include-deleted-rows)

(rf/reg-sub ::include-error-rows
  :<- [::data]
  :-> :include-error-rows)

(rf/reg-sub ::include-added-rows
  :<- [::data]
  :-> :include-added-rows)

(rf/reg-sub ::row-open?
  (fn [[_ row-id]]
    [(rf/subscribe [::data])
     (rf/subscribe [::idata/editing? row-id])])
  (fn [[data editing?] [_ row-id]]
    (or (ctx-d data :row-open row-id) editing?)))
