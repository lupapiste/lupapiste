(ns lupapalvelu.ui.biller.data
  (:require [clojure.set :as set]
            [goog.string :as goos]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.biller.util :as biller-util]
            [lupapalvelu.ui.common :as common]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [goog.Uri QueryData]))

(def ^:private batch-fetch-limit 5)
(def poller-id ::biller-poller)
(def poller-interval "Interval in ms" (* 30 1000))
(def fetch-more-invoices-limit 50)

(defn extra-info?
  "True if the given invoice row has extra information that should be shown."
  [{:keys [discount-percent comment product-constants]}]
  (or (not (or (zero? discount-percent) (ss/blank? comment)))
      (not-empty (util/strip-blanks product-constants))))

(defn- idmap [items]
  (some->> (seq items)
           (map (juxt :id identity))
           (into {})))

(defn- current-org [db]
  (or (::selected-organization db)
      (first (::organizations db))))

(defn- current-org-id [db]
  (:id (current-org db)))

(defn- invoice-rowcount [invoice]
  (or (some-> invoice :rows count) 0))

(defn- operation-rows [invoice operation-id]
  (filter #(= (:operation-id %) operation-id) (:rows invoice)))

(defn- transfer-batch-string [{:keys [name created]}]
  (goos/format "%s (%s)"
               (or name (common/loc :transfer-batch.no-name))
               (biller-util/datetime-format created :short-datetime)))

;; -------------------------
;; Events
;; -------------------------

(rf/reg-event-fx ::merge-batches
  (fn [{db :db} [_ {:keys [transfer-batches]} organization-id]]
    (when-let [batches (idmap transfer-batches)]
      {:db       (update db ::batches merge batches)
       :dispatch [::fetch-invoices organization-id
                  :batch-ids (set (keys batches))]})))

(rf/reg-event-fx ::fetch-transfer-batches
  (fn [{db :db} [_ organization-id & kvs]]
    (let [m       (apply hash-map kvs)
          states  (some-> (:states m) not-empty set)
          old-ids (when-not (:include m)
                    (->> (::batches db)
                         vals
                         (keep #(when (and (= (:organization-id %) organization-id)
                                           (or (nil? states)
                                               (states (:state %))))
                                  (:id %)))
                         seq))]
      {:action/command {:name    :biller-transfer-batches
                        :params  (util/strip-nils
                                   (merge m {:organizationId organization-id
                                             :exclude        old-ids}))
                        :success [::merge-batches organization-id]}})))

(rf/reg-event-fx ::merge-invoices
  (fn [{db :db} [_ {:keys [invoices draft-ids]} organization-id]]
    (let [invoice-map (idmap invoices)]
      (when (or invoice-map (seq draft-ids))
        (util/strip-nils {:db       (update db ::invoices (fn [m]
                                                            (apply dissoc (merge m invoice-map) draft-ids)))
                          :dispatch (when-let [batch-ids (not-empty (set/difference
                                                                      (set (keep :transfer-batch-id invoices))
                                                                      (set (keys (::batches db)))))]
                                      [::fetch-transfer-batches organization-id :include batch-ids])})))))

(rf/reg-event-fx ::fetch-invoices
  (fn [{db :db} [_ organization-id & kvs]]
    (let [m         (apply hash-map kvs)
          states    (some-> (:states m) not-empty set)
          batch-ids (some-> (:batch-ids m) not-empty set)
          old-ids   (when-not (:include m)
                      (->> (::invoices db)
                           vals
                           (keep #(when (and (= (:organization-id %) organization-id)
                                             (or (nil? batch-ids)
                                                 (batch-ids (:transfer-batch-id %)))
                                             (or (nil? states)
                                                 (states (:state %))))
                                    (:id %)))
                           seq))]
      {:action/command {:name    :biller-invoices
                        :params  (util/strip-nils
                                   (merge m {:organizationId organization-id
                                             :exclude        (or old-ids (:exclude m))}))
                        :success [::merge-invoices organization-id]}})))

(rf/reg-event-fx ::fetch-more-invoices
  (fn [{db :db} _]
    {:dispatch [::fetch-invoices (current-org-id db)
                ;; All the checked and confirmed invoices are fetched automatically either
                ;; by poller or organization selection.
                :states ["transferred"]
                :limit fetch-more-invoices-limit]}))

(rf/reg-event-fx ::fetch-more-batches
  (fn [{db :db} _]
    {:dispatch [::fetch-transfer-batches (current-org-id db)
                ;; All the open batches have already been fetched.
                :states #{"closed"}
                :limit batch-fetch-limit]}))

(rf/reg-event-fx ::poll-invoices-success
  (fn [{db :db} [_ {:keys [new-ids gone-ids ok]} organization-id]]
    (let [include (seq new-ids)
          exclude (seq gone-ids)]
      (cond
        (not ok)
        {:dispatch [:poller/remove poller-id]}

        (and (= organization-id (current-org-id db))
             (or include exclude))
        {:dispatch [::fetch-invoices organization-id
                    :states #{"checked"}
                    :include include
                    :exclude exclude]}))))

(rf/reg-event-fx ::poll-invoices
  (fn [{db :db} _]
    (when-let [org-id (current-org-id db)]
      {:post/json {:path    "/api/biller-poller"
                   :params  {:organization-id org-id
                             :invoice-ids     (some->> (::invoices db)
                                                       vals
                                                       (keep (fn [{:keys [id state organization-id]}]
                                                               (when (and (= state "checked")
                                                                          (= organization-id org-id))
                                                                 id))))}
                   :success [::poll-invoices-success org-id]}})))

(rf/reg-event-fx ::store-organizations
  (fn [{db :db} [_ {:keys [organizations]}]]
    (let [{:keys [backend-codes client-number-required?]
           :as   organization} (first organizations)]
      {:db       (-> db
                     (assoc ::organizations organizations)
                     (update ::selected-columns
                             (fn [col-set]
                               (cond-> col-set
                                 (seq backend-codes)     (conj :backend-code)
                                 client-number-required? (conj :sap-number)))))
       :dispatch [::select-organization organization]})))

(rf/reg-event-fx ::initialize
  (fn [{db :db}]
    {:db           (assoc db
                          ::current-view :invoices
                          ::selected-columns #{:application-id :payer-name
                                               :sum :internal?})
     :action/query {:name    :biller-organizations
                    :success ::store-organizations}}))

(rf/reg-event-db ::clear
  (fn [db _]
    (dissoc db ::invoices ::batches ::organizations ::selected-organization)))

(rf/reg-event-fx ::select-organization
  (fn [{db :db} [_ {org-id :id :as organization}]]
    {:db (-> db
             (assoc ::selected-organization organization)
             (dissoc ::invoices ::batches))
     :fx [[:dispatch [::fetch-invoices org-id :states #{"checked" "confirmed"}]]
          [:dispatch [::fetch-transfer-batches org-id :states #{"open"}]]
          [:dispatch [::fetch-transfer-batches org-id :states #{"closed"}
                      :limit batch-fetch-limit]]]}))

(rf/reg-event-fx ::set-invoice-field-success
  (fn [{db :db} [_ {:keys [in out errors]} invoice-id]]
    (let [batch-id (:transfer-batch-id in)]
      (cond-> {:db (update-in db [::invoices invoice-id]
                              (fn [invoice]
                                (assoc (apply dissoc
                                              (merge invoice in)
                                              (map keyword out))
                                       :errors errors)))}
        ;; We'll only fetched the changed transfer batch if it has not been fetched earlier.
        (and batch-id (nil? (get-in db [::batches batch-id])))
        (assoc :dispatch [::fetch-transfer-batches (current-org-id db)
                          :include [batch-id]])))))

(rf/reg-event-fx ::set-invoice-field
  (fn [{db :db} [_ invoice-id field value]]
    (let [params {:id         (get-in db [::invoices invoice-id :application-id])
                  :invoice-id invoice-id
                  :field      field
                  :value      value}]
      {:db             (assoc-in db [::invoices invoice-id field] value)
       :action/command {:name    :set-biller-invoice-field
                        :params  params
                        :success [::set-invoice-field-success invoice-id]}})))

;; Merge key - value pairs into meta
(rf/reg-event-db ::set-invoice-meta
  (fn [db [_ invoice-id & kvs]]
    (update-in db [::invoices invoice-id ::meta] merge (apply hash-map kvs))))

;; Change value for a meta path
(rf/reg-event-db ::set-in-invoice-meta
  (fn [db [_ invoice-id & xs]]
    (assoc-in db (concat [::invoices invoice-id ::meta] (butlast xs)) (last xs))))

(rf/reg-event-db ::toggle-operation-extra-info
  (fn [db [_ invoice-id operation-id]]
    (update-in db [::invoices invoice-id ::meta :show-extra?]
               (fn [m]
                 (let [invoice (get-in db [::invoices invoice-id])
                       op-rows (filter extra-info? (operation-rows invoice operation-id))
                       expand? (not-any? #(get m (:id %)) op-rows)]
                   (->> (map #(vector (:id %) expand?) op-rows)
                        (into {})
                        (merge m)))))))

(rf/reg-event-db ::toggle-batch-invoice
  (fn [db [_ batch-id k flag]] ;; Flag can be true, false or nil
    (let [path        [::batches batch-id ::meta :expanded]
          all?        (= k :all)
          invoice-ids (fn []
                        (->> (::invoices db)
                             vals
                             (keep #(when (= (:transfer-batch-id %) batch-id)
                                      (:id %)))))
          set-meta    (fn [v]
                        (assoc-in db path v))]
      (cond
        (and all? (false? flag))
        (set-meta {})

        (and all? (true? flag))
        (set-meta (zipmap (invoice-ids) (cycle [true])))

        all?
        (let [ids (invoice-ids)
              m   (get-in db path)]
          (set-meta (if (some #(get m %) ids)
                      {}
                      (zipmap ids (cycle [true])))))

        :else
        (update-in db (conj path k) (fn [v]
                                      (if (nil? flag)
                                        (not v)
                                        flag)))))))

(rf/reg-event-fx ::change-invoice-state
  (fn [{db :db} [_ invoice-id new-state]]
    (let [state  (get-in db [::invoices invoice-id :state])
          change (util/kw-path state new-state)]
      (when (#{:checked.confirmed :checked.draft :confirmed.checked} change)
        {:dialog/show {:ltext (util/kw-path :invoice.confirmation change)
                       :type  :yes-no
                       :event [::set-invoice-field invoice-id :state new-state]}}))))

(rf/reg-event-db ::set-view
  (fn [db [_ view]]
    (assoc db ::current-view view)))

(rf/reg-event-db ::delete-transfer-batch-success
  (fn [db [_ _ id]]
    (util/dissoc-in db [::batches id])))

(rf/reg-event-fx ::delete-transfer-batch
  (fn [_ [_ {:keys [id organization-id]}]]
    {:action/command {:name    :delete-transfer-batch
                      :params  {:transfer-batch-id id
                                :organizationId    organization-id}
                      :success [::delete-transfer-batch-success id]}}))

(rf/reg-event-fx ::delete-transfer-batch-confirm
  (fn [_ [_ {:keys [created name] :as batch}]]
    {:dialog/show {:text  (common/loc :transfer-batch.remove.confirmation
                                      (biller-util/datetime-format created :medium-datetime)
                                      (or (ss/blank-as-nil name)
                                          (common/loc :transfer-batch.no-name)))
                   :type  :yes-no
                   :event [::delete-transfer-batch batch]}}))

(rf/reg-event-fx ::new-transfer-batch-success
  (fn [_ [_ response organization-id]]
    {:dispatch [::fetch-transfer-batches organization-id
                :include [(:transfer-batch-id response)]]}))

(rf/reg-event-fx ::new-transfer-batch
  (fn [{db :db} _]
    (let [org-id (current-org-id db)]
      {:action/command {:name    :new-transfer-batch
                        :params  {:organizationId org-id}
                        :success [::new-transfer-batch-success org-id]}})))

(rf/reg-event-fx ::rename-transfer-batch
  (fn [{db :db} [_ {:keys [id organization-id]} txt]]
    {:db             (assoc-in db [::batches id :name] (ss/trim txt))
     :action/command {:name   :rename-transfer-batch
                      :params {:transfer-batch-id id
                               :organizationId    organization-id
                               :name              txt}}}))

(rf/reg-event-fx ::transfer-batch-status-change
  (fn [{db :db} [_ {:keys [closed opened sent]} batch-id & events]]
    {:db (update-in db [::batches batch-id]
                    (fn [batch]
                      (cond-> batch
                        closed (assoc :closed closed
                                      :state "closed")
                        opened (-> (dissoc :closed) (assoc :state "open"))
                        sent   (assoc :sent sent))))
     :fx (mapv (fn [event] [:dispatch event]) events)}))

(rf/reg-event-fx ::set-transfer-batch-state
  (fn [_ [_ {:keys [organization-id id invoice-ids]} state]]
    {:action/command {:name    (if (util/=as-kw state :open)
                                 :open-transfer-batch
                                 :close-transfer-batch)
                      :params  {:organizationId    organization-id
                                :transfer-batch-id id}
                      :success [::transfer-batch-status-change
                                id
                                [::fetch-invoices organization-id
                                 :include invoice-ids
                                 :states #{"confirmed" "transferred"}]]}}))

(rf/reg-event-fx ::set-transfer-batch-state-confirm
  (fn [_ [_ batch state]]
    {:dialog/show {:text-loc (util/kw-path :transfer-batch.to state :confirmation)
                   :type     :yes-no
                   :event    [::set-transfer-batch-state batch state]}}))

(rf/reg-event-fx ::send-transfer-batch
  (fn [{db :db} [_ batch-id]]
    (let [{:keys [id]} (current-org db)]
      {:action/command {:name    :process-transfer-batch-integration
                        :params  {:organizationId    (current-org-id db)
                                  :transfer-batch-id batch-id}
                        :success [::transfer-batch-status-change batch-id
                                  [::fetch-transfer-batches id :include #{batch-id}]]}})))

(rf/reg-event-fx ::send-transfer-batch-confirmation
  (fn [{db :db} [_ batch-id]]
    (let [sent-ts (get-in db [::batches batch-id :sent])]
      {:dialog/show {:text  (if sent-ts
                              (common/loc :transfer-batch.send-again.confirmation
                                          (biller-util/datetime-format sent-ts :medium-datetime))
                              (common/loc :transfer-batch.send.confirmation))
                     :type  :yes-no
                     :event [::send-transfer-batch batch-id]}})))

;; -------------------------
;; Subs
;; -------------------------

(rf/reg-sub ::current-organization
  :<- [:value/get ::organizations]
  :<- [:value/get ::selected-organization]
  (fn [[orgs selected]]
    (or selected (first orgs))))

(defn- value-ids [values state]
  (some->> values
           (filter #(util/=as-kw (:state %) state))
           (sort-by :created)
           reverse
           (map :id)
           seq))

(rf/reg-sub ::invoice-ids
  :<- [:value/get ::invoices]
  (fn [invoices [_ state]]
    (value-ids (vals invoices) state)))

(rf/reg-sub ::batch-ids
  :<- [:value/get ::batches]
  (fn [batches [_ state]]
    (value-ids (vals batches) state)))

(rf/reg-sub ::transfer-batch
  :<- [:value/get ::batches]
  :<- [:value/get ::invoices]
  (fn [[batches invoices] [_ batch-id]]
    (let [transfer-batch (get batches batch-id)
          invoices       (some->> (vals invoices)
                                  seq
                                  (filter #(= (:transfer-batch-id %) batch-id))
                                  (sort-by :created))
          rowcount       (some->> invoices
                                  (map invoice-rowcount)
                                  (apply +))
          total-cents    (some->> invoices
                                  (map :sum-total-cents)
                                  (apply +))]
      (assoc transfer-batch
             :invoice-ids (map :id invoices)
             :rowcount (or rowcount 0)
             :total-cents (or total-cents 0)))))

(rf/reg-sub ::invoice
  :<- [:value/get ::invoices]
  (fn [invoices [_ invoice-id & ks]]
    (get-in (get invoices invoice-id) ks)))

(rf/reg-sub ::invoice-rows
  (fn [[_ invoice-id _]]
    (rf/subscribe [::invoice invoice-id]))
  (fn [invoice [_ _ operation-id]]
    (operation-rows invoice operation-id)))

(rf/reg-sub ::invoice-field
  (fn [[_ invoice-id]]
    (rf/subscribe [::invoice invoice-id]))
  (fn [{:keys [errors] :as invoice} [_ _ field-key]]
    {:value (get invoice field-key)
     :error (get errors field-key)}))

(rf/reg-sub ::invoice-meta-value
  (fn [[_ invoice-id]]
    (rf/subscribe [::invoice invoice-id]))
  (fn [invoice [_ _ & ks]]
    (get-in invoice (cond-> [::meta]
                      (seq ks) (concat ks)))))

(rf/reg-sub ::batch-meta-value
  (fn [[_ batch-id]]
    (rf/subscribe [::transfer-batch batch-id]))
  (fn [batch [_ _ & ks]]
    (get-in batch (cond-> [::meta]
                    (seq ks) (concat ks)))))

(rf/reg-sub ::invoice-fields-ok?
  (fn [[_ invoice-id]]
    [(rf/subscribe [::current-organization])
     (rf/subscribe [::invoice invoice-id])])
  (fn [[{:keys [client-number-required?]} {:keys [sap-number errors]}]]
    (not (or (and (ss/blank? sap-number) client-number-required?)
             (not-empty errors)))))

(rf/reg-sub ::invoice-created-title
  (fn [[_ invoice-id]]
    (rf/subscribe [::invoice invoice-id]))
  (fn [{:keys [created created-by]}]
    (goos/format "%s, %s %s"
                 (biller-util/datetime-format created)
                 (:firstName created-by)
                 (:lastName created-by))))

(rf/reg-sub ::payer-name
  (fn [[_ invoice-id]]
    (rf/subscribe [::invoice invoice-id]))
  (fn [{:keys [payer-type first-name last-name entity-name company-id]}]
    (if (= payer-type "person")
      (ss/trim-join " " first-name last-name)
      (ss/trim-join " " entity-name (biller-util/id-string company-id)))))

(rf/reg-sub ::current-view
  :-> ::current-view)

(rf/reg-sub ::batch-column-keys
  :<- [::current-organization]
  :<- [:value/get ::selected-columns]
  (fn [[{:keys [backend-codes]} columns] [_ mode]]
    (let [columns (set columns)]
      (cond->> [:application-id :address :payer-name :payer-id :payer-address :payer-contact
                :payer-netbill :sum :payer-reference :sap-number :internal? :partner-code
                :case-reference :letter :description :backend-code]
        (not backend-codes)
        butlast

        (and (= mode :selected) (not-empty columns))
        (filter columns)))))

(defn payer-summary [{:keys [payer-type entity-name company-id person-id first-name last-name
                             entity-address city postal-code country netbill ovt operator
                             billing-reference]}]
  (let [person-name (ss/join-non-blanks " " [first-name last-name])]
    (-> (if (= payer-type "company")
          {:payer-name    entity-name
           :payer-id      company-id
           :payer-netbill (ss/join-non-blanks ", " [netbill ovt (biller-util/operator-string operator)])
           :payer-contact person-name}
          {:payer-name person-name
           :payer-id   person-id})
        (assoc :payer-reference billing-reference
               :payer-address (ss/join-non-blanks ", " [entity-address
                                                        (ss/join-non-blanks " " [postal-code city])
                                                        (when-not (= country "FIN")
                                                          (biller-util/country-string country))])))))

(rf/reg-sub ::batch-column-values
  (fn [[_ invoice-id]]
    [(rf/subscribe [::current-organization])
     (rf/subscribe [::invoice invoice-id])
     (rf/subscribe [::batch-column-keys :selected])])
  (fn [[{:keys [backend-codes]} invoice column-keys]]
    (let [payer (payer-summary invoice)]
      (for [k column-keys]
        {:key   k
         :value (case k
                  (:application-id :sap-number :partner-code
                   :case-reference :description :letter)
                  (get invoice k)

                  :internal?
                  (path/loc (if (:organization-internal-invoice? invoice)
                              :yes :no))

                  :address
                  (get-in invoice [:enriched-data :application :address])

                  (:payer-name ;; Lonesome due to indentation
                   :payer-id :payer-address :payer-netbill
                   :payer-contact :payer-reference)
                  (get payer k)

                  :sum
                  (-> invoice :sum-total-cents biller-util/cents->eur)

                  :backend-code
                  (when-let [code (ss/blank-as-nil (:backend-code invoice))]
                    (some-> (util/find-by-key :code code backend-codes)
                            biller-util/backend-text))

                  nil)}))))

(rf/reg-sub ::batch-candidates
  ;; Transfer batches that are open and have enough space for the invoice.
  ;; Returns list of text - value maps, where values are batch ids.
  (fn [db [_ invoice-id]]
    (let [{:keys [organization-id transfer-batch-id]
           :as   invoice}        (get-in db [::invoices invoice-id])
          {:keys [max-rowcount]} (util/find-by-id organization-id (::organizations db))
          max-rowcount           (when max-rowcount
                                   (- max-rowcount (invoice-rowcount invoice)))]
      (->> (::batches db)
           vals
           (filter (fn [{:keys [id state] :as batch}]
                     (or (= transfer-batch-id id)
                         (and (= state "open")
                              (= (:organization-id batch) organization-id)
                              (or (nil? max-rowcount)
                                  (>= max-rowcount (->> (::invoices db)
                                                        vals
                                                        (filter #(= (:transfer-batch-id %) id))
                                                        (map invoice-rowcount)
                                                        (apply +))))))))
           (sort-by :created >)
           (map (fn [batch]
                  {:text  (transfer-batch-string batch)
                   :value (:id batch)}))))))

(rf/reg-sub ::transfer-batch-string
  (fn [[_ batch-id]]
    (rf/subscribe [::transfer-batch batch-id]))
  (fn [batch _]
    (transfer-batch-string batch)))

(rf/reg-sub ::transfer-batch-download-link
  :<- [::current-organization]
  ;; The key is either :sap-excel? or :download?
  (fn [{:keys [id] :as organization} [_ batch-id download-key]]
    (when (get organization download-key)
      (let [params (doto (QueryData.)
                     (.set "organizationId" id)
                     (.set "transfer-batch-id" batch-id)
                     (.set "lang" (common/get-current-language)))]
        (goos/format "/api/raw/download-transfer-batch-%s?%s"
                     (case download-key
                       :sap-excel? "excel"
                       :download?  "xml")
                     (.toString params))))))

(rf/reg-sub ::non-empty-batches?
  :<- [:value/get ::invoices]
  (fn [invoices _]
    (->> invoices
         vals
         (some :transfer-batch-id)
         boolean)))
