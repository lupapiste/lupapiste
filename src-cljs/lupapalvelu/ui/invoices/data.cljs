(ns lupapalvelu.ui.invoices.data
  (:require [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.biller.util :as biller-util]
            [lupapalvelu.ui.common :as common]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [goog.Uri QueryData]))

(defn remove-by-key [k v coll]
  (remove #(= (get % k) v) coll))

(defn- update-invoice [db invoice-id update-fn]
  (update-in db [::invoices invoice-id] update-fn))

(defn- update-modified [db invoice-id m]
  (update-invoice db invoice-id #(merge % (select-keys m [:modified :modified-by]))))

(defn operation-string [{:keys [name description]}]
  (ss/trim-join " - "
                (path/loc :operations name)
                description))

(defn row-extra-info?
  [{:keys [discount-percent comment product-constants]}]
  (or (and (some-> discount-percent str ss/trim ss/blank-as-nil (not= "0"))
           (ss/not-blank? comment))
      (some ss/not-blank? (vals product-constants))))

(defn- get-row [db invoice-id row-id & ks]
  (cond-> (util/find-by-id row-id (get-in db [::invoices invoice-id :rows]))
    (seq ks) (get-in (flatten ks))))

(defn- update-row [db invoice-id row-id update-fn]
  (update-in db
             [::invoices invoice-id :rows]
             (fn [rows]
               (loop [[row & row-xs] rows
                      passed         []]
                 (if (= (:id row) row-id)
                   (concat passed [(update-fn row)] row-xs)
                   (recur row-xs (conj passed row)))))))

(defn- set-row-field [db invoice-id row-id k-or-path v]
  (update-row db invoice-id row-id #(assoc-in % (flatten [k-or-path]) v)))

(defn =str [ & xs]
  (apply = (map (comp ss/trim str) xs)))

;; -------------------------
;; Events
;; -------------------------

(rf/reg-event-db
  ::initialize-success
  (fn [db [_ response]]
    (assoc db ::config (select-keys response [:catalogues :worktime? :backend-codes
                                              :operations]))))

(rf/reg-event-fx
  ::initialize
  (fn [& _]
    {:action/query {:name    :application-invoicing-configuration
                    :params {:id (common/application-id)}
                    :success ::initialize-success}}))

(rf/reg-event-db
  ::clear
  (fn [db _]
    (dissoc db ::invoices ::config)))

(rf/reg-event-db
  ::fetch-invoices-success
  (fn [db [_ {:keys [invoices]} & [invoice-id]]]
    (if invoice-id
      (update-invoice db invoice-id  #(merge % (first invoices)))
      (some->> invoices
               (map (juxt :id identity))
               (into {})
               (assoc db ::invoices)))))

(rf/reg-event-fx
  ::fetch-invoices
  (fn [_ [_ & [invoice-id]]]
    {:action/query {:name    :application-invoices
                    :params  (util/strip-nils
                               {:id         (common/application-id)
                                :invoice-id invoice-id})
                    :success [::fetch-invoices-success invoice-id]}}))

(rf/reg-event-db
  ::new-invoice-success
  (fn [db [_ {:keys [invoice]}]]
    (assoc-in db
              [::invoices (:id invoice)]
              (assoc-in invoice [::meta :expanded?] true))))

(rf/reg-event-fx
  ::new-invoice
  (fn [& _]
    {:action/command {:name :new-invoice-draft
                      :params {:id (common/application-id)}
                      :success ::new-invoice-success}}))

(rf/reg-event-db
  ::set-invoice-meta-value
  (fn [db [_ invoice-id k v]]
    (assoc-in db [::invoices invoice-id ::meta k] v)))

(rf/reg-event-fx
  ::set-invoice-field-success
  (fn [{db :db} [_ {:keys [in out]} invoice-id field]]
    (cond-> {:db (update-in db [::invoices invoice-id]
                            (fn [invoice]
                              (apply dissoc
                                     (merge invoice in)
                                     (map keyword out))))}
      (= field :state)
      (assoc :dispatch [::fetch-invoices invoice-id]))))

(rf/reg-event-fx
  ::set-invoice-field
  (fn [{db :db} [_ invoice-id field value]]
    (let [params {:id (common/application-id)
                  :invoice-id invoice-id}]
      {:db             (assoc-in db [::invoices invoice-id field] value)
       :action/command {:name    :set-invoice-field
                        :params  (assoc params
                                        :field field
                                        :value (ss/blank-as-nil value))
                        :success [::set-invoice-field-success invoice-id field]}})))

(rf/reg-event-db
  ::add-operation-success
  (fn [db [_ {:keys [operation rows] :as response} invoice-id]]
    (update-in (update-modified db invoice-id response)
               [::invoices invoice-id]
               (fn [invoice]
                 (-> invoice
                     (update :operations conj operation)
                     (update :rows concat rows))))))

(rf/reg-event-fx
  ::add-operation
  (fn [_ [_ invoice-id operation-id]]
    {:action/command {:name    :add-invoice-operation
                      :params  {:id           (common/application-id)
                                :invoice-id   invoice-id
                                :operation-id operation-id}
                      :success [::add-operation-success invoice-id]}}))

(rf/reg-event-db
  ::set-row-field-success
  (fn [db [_ {:keys [in row-in row-out row-error]} invoice-id row-id path]]
    (cond-> db
      in              (update-invoice invoice-id #(merge % in))
      row-in          (update-row invoice-id row-id #(reduce-kv (fn [acc k v]
                                                                  (assoc-in acc (util/split-kw-path k) v))
                                                                %
                                                                row-in))
      row-out         (update-row invoice-id row-id #(reduce (fn [acc k]
                                                               (util/dissoc-in acc (util/split-kw-path k)))
                                                             %
                                                             row-out))
      row-error       (update-row invoice-id row-id #(update % ::errors merge row-error))
      (not row-error) (update-row invoice-id row-id #(update % ::errors dissoc (last path))))))

(rf/reg-event-fx
  ::set-row-field
  (fn [{db :db} [_ invoice-id row-id kwp v]]
    (let [path (util/split-kw-path kwp)
          old-value (get-row db invoice-id row-id path)]
      (when-not (=str old-value v)
        (cond-> {:db             (set-row-field db invoice-id row-id path v)
                 :action/command {:name    :set-invoice-row-field
                                  :params  {:id         (common/application-id)
                                            :invoice-id invoice-id
                                            :row-id     row-id
                                            :path       path
                                            :value      v}
                                  :success [::set-row-field-success invoice-id row-id path]}}
          (and (= kwp :discount-percent)
               (ss/not-blank? (str v))
               (ss/blank? (get-row db invoice-id row-id :comment)))
          (assoc :dispatch [::toggle-extra-info invoice-id :id row-id true]))))))

(rf/reg-event-db
  ::add-row-success
  (fn [db [_ {:keys [row] :as response} invoice-id]]
    (-> db
        (update-modified invoice-id response)
        (update-invoice invoice-id #(update % :rows concat [row])))))

(rf/reg-event-fx
  ::add-row
  (fn [_ [_ invoice-id operation-id row-id]]
    {:action/command {:name   :add-invoice-row
                      :params {:id           (common/application-id)
                               :invoice-id   invoice-id
                               :operation-id operation-id
                               :row-id       row-id}
                      :success [::add-row-success invoice-id]}}))

;; -----------------
;; Remove operation
;; -----------------

(rf/reg-event-db
  ::remove-operation-success
  (fn [db [_ response invoice-id op-id]]
    (-> db
        (update-modified invoice-id response)
        (update-invoice invoice-id (fn [invoice]
                                     (-> invoice
                                         (update :operations (partial remove-by-key :id op-id ))
                                         (update :rows (partial remove-by-key :operation-id op-id))))))))

(rf/reg-event-fx
  ::remove-operation
  (fn [_ [_ invoice-id op-id]]
    {:action/command {:name   :delete-invoice-operation
                      :params {:id           (common/application-id)
                               :invoice-id   invoice-id
                               :operation-id op-id}
                      :success [::remove-operation-success invoice-id op-id]}}))

(rf/reg-event-fx
  ::remove-operation-confirm
  (fn [_ [_ invoice-id {:keys [id text]}]]
    {:dialog/show {:type  :yes-no
                   :text  (common/loc :invoice.remove-operation.confirm (common/escape-html text))
                   :event [::remove-operation invoice-id id]}}))

;; -----------------
;; Remove row
;; -----------------

(rf/reg-event-db
  ::delete-row-success
  (fn [db [_ response invoice-id row-id]]
    (-> db
        (update-modified invoice-id response)
        (update-invoice invoice-id #(update % :rows (partial remove-by-key :id row-id))))))

(rf/reg-event-fx
  ::delete-row
  (fn [_ [_ invoice-id row-id]]
    {:action/command {:name    :delete-invoice-row
                      :params  {:id         (common/application-id)
                                :invoice-id invoice-id
                                :row-id     row-id}
                      :success [::delete-row-success invoice-id row-id]}}))

(rf/reg-event-fx
  ::delete-row-confirm
  (fn [_ [_ invoice-id row-id]]
    {:dialog/show {:type  :yes-no
                   :text-loc  :invoice.delete-row.confirm
                   :event [::delete-row invoice-id row-id]}}))

;; -----------------
;; Remove invoice
;; -----------------

(rf/reg-event-db
  ::delete-invoice-success
  (fn [db [_ _ invoice-id]]
    (update db ::invoices dissoc invoice-id)))

(rf/reg-event-fx
  ::delete-invoice
  (fn [_ [_ invoice-id]]
    {:action/command {:name    :delete-invoice
                      :params  {:id         (common/application-id)
                                :invoice-id invoice-id}
                      :success [::delete-invoice-success invoice-id]}}))

(rf/reg-event-fx
  ::delete-invoice-confirm
  (fn [_ [_ invoice-id]]
    {:dialog/show {:type     :yes-no
                   :text-loc :invoice.delete.confirm
                   :event    [::delete-invoice invoice-id]}}))

(rf/reg-event-db
  ::toggle-extra-info
  (fn [db [_ invoice-id ctx-k ctx-id & [flag?]]]
    (let [rows    (filter #(= (ctx-k %) ctx-id)
                          (get-in db [::invoices invoice-id :rows]))
          open?   (if (boolean? flag?)
                    (not flag?)
                    (some #(get-in % [::meta :expanded?]) rows))]
      (reduce (fn [acc {row-id :id}]
                (update-row acc invoice-id row-id
                            #(assoc-in % [::meta :expanded?] (not open?))))
              db
              rows))))

(rf/reg-event-db
  ::set-worktime-result
  (fn [db [_ {:keys [ok text] :as response} invoice-id]]
    (update-invoice db invoice-id
                    (if ok
                      #(merge % (dissoc response :ok))
                      #(assoc % :workdays {:error text})))))

(rf/reg-event-fx
  ::set-worktime
  (fn [{db :db} [_ invoice-id time-key ts]]
    (let [db                    (update-invoice db invoice-id
                                                #(assoc % time-key ts))
          {:keys [work-start-ms
                  work-end-ms]} (get-in db [::invoices invoice-id])]
      (if (or (and work-start-ms work-end-ms)
              (not (or work-start-ms work-end-ms)))
        {:db             db
         ;; Command is sent if BOTH timestamps or NEITHER is given
         :action/command {:name    :update-invoice-worktime
                          :params  (util/strip-nils {:id         (common/application-id)
                                                     :invoice-id invoice-id
                                                     :start-ts   work-start-ms
                                                     :end-ts     work-end-ms})
                          :error   [::set-worktime-result invoice-id]
                          :success [::set-worktime-result invoice-id]}}
        {:db (update-invoice db invoice-id #(dissoc % :workdays))}))))

;; -------------------------
;; Subs
;; -------------------------

(rf/reg-sub
  ::config
  (fn [db [_ k]]
    (get-in db [::config k])))

(def invoice-order {"draft"       0
                    "checked"     1
                    "confirmed"   2
                    "transferred" 3})

(rf/reg-sub
  ::invoices
  (fn [{:keys [::invoices]}]
    (->> (vals invoices)
         (map #(select-keys % [:id :state :created]))
         (sort-by (fn [{:keys [created state]}]
                    [(get invoice-order state) (* -1 created)])))))

(rf/reg-sub
  ::invoice
  (fn [{:keys [::invoices]} [_ invoice-id & ks]]
    (cond-> (get invoices invoice-id)
      (seq ks) (get-in ks))))

(rf/reg-sub
  ::invoice-meta-value
  (fn [[_ invoice-id & ks]]
    (rf/subscribe (apply conj [::invoice invoice-id ::meta] ks)))
  identity)

(rf/reg-sub
  ::catalogue-items
  :<- [::config :catalogues]
  (fn [catalogues]
    (some->> catalogues
             (map (fn [{:keys [id name valid-from valid-until]}]
                    {:text  (ss/trim-join " "
                                         name
                                         (when (or valid-from valid-until)
                                           (str (biller-util/datetime-format valid-from :short-date)
                                                " - "
                                                (biller-util/datetime-format valid-until :short-date))))
                     :value id}))
             seq)))

(rf/reg-sub
  ::pdf-link
  ;; The key is either :sap-excel? or :download?
  (fn [_ [_ invoice-id]]
    (let [params (doto (QueryData.)
                   (.set "id" (common/application-id))
                   (.set "invoice-id" invoice-id)
                   (.set "lang" (common/get-current-language)))]
      (str "/api/raw/invoice-pdf?" (.toString params)))))

(rf/reg-sub
  ::backend-code-items
  :<- [::config :backend-codes]
  (fn [codes _]
    (some->> codes
             (map (fn [{:keys [code] :as m}]
                    {:value code
                     :text (biller-util/backend-text m)})))))

(rf/reg-sub
  ::backend-text
  (fn [[_ invoice-id]]
    [(rf/subscribe [::config :backend-codes])
     (rf/subscribe [::invoice invoice-id])])
  (fn [[codes {:keys [backend-code backend-id]}]]
    (some-> (util/find-by-key :code backend-code codes)
            (biller-util/backend-text backend-id))))

(rf/reg-sub
  ::operations
  (fn [[_ invoice-id]]
    [(rf/subscribe [::config :operations])
     (rf/subscribe [::invoice invoice-id])])
  (fn [[ops {:keys [operations]}]]
    (some->> (seq operations)
             (map (fn [op]
                    {:id (:id op)
                     ;; It is possible that the operation is no longer in the application.
                     :text (operation-string (merge op (util/find-by-id (:id op) ops)))})))))

(rf/reg-sub
  ::operation-row-ids
  (fn [[_ invoice-id _]]
    (rf/subscribe [::invoice invoice-id]))
  (fn [invoice [_ _ op-id]]
    (keep (fn [{:keys [id operation-id]}]
            (when (= op-id operation-id)
              id))
          (:rows invoice))))

(rf/reg-sub
  ::operation-items
  (fn [[_ invoice-id]]
    [(rf/subscribe [::config :operations])
     (rf/subscribe [::operations invoice-id])])
  (fn [[ops operations]]
    (let [used-ids (set (map :id operations))]
      (some->> ops
               (remove (comp used-ids :id))
               seq
               (map (fn [op]
                      {:value (:id op)
                       :text (operation-string op)}))))))

(rf/reg-sub
  ::editing?
  (fn [[_ invoice-id]]
    [(rf/subscribe [::invoice invoice-id])
     (rf/subscribe [:auth/application? :set-invoice-field])])
  (fn [[{:keys [state]} authed?]]
    (and (= state "draft") authed?)))

(rf/reg-sub
  ::invoice-row
  (fn [[_ invoice-id _]]
    (rf/subscribe [::invoice invoice-id]))
  (fn [{:keys [rows]} [_ _ row-id]]
    (util/find-by-id row-id rows)))

(rf/reg-sub
  ::row-field
  (fn [[_ invoice-id row-id _]]
    (rf/subscribe [::invoice-row invoice-id row-id]))
  (fn [row [_ _ _ & field-keys]]
    (if (= field-keys [:text])
      (ss/trim-join " " (:code row) (:text row))
      (get-in row field-keys))))

(rf/reg-sub
  ::field-editable?
  (fn [[_ invoice-id row-id _]]
    [(rf/subscribe [::editing? invoice-id])
     (rf/subscribe [::invoice-row invoice-id row-id])])
  (fn [[edit? {:keys [custom? min-unit-price max-unit-price]}] [_ _ _ field-key]]
    (and edit?
         (case field-key
           (:unit :text)   custom?
           :price-per-unit (or custom? min-unit-price max-unit-price)
           true))))

(rf/reg-sub
  ::row-extra-info?
  (fn [[_ invoice-id row-id]]
    [(rf/subscribe [::editing? invoice-id])
     (rf/subscribe [::invoice-row invoice-id row-id])])
  (fn [[edit? row]]
    (or edit?
        (row-extra-info? row))))

(rf/reg-sub
  ::operation-extra-info?
  (fn [[_ invoice-id _]]
    [(rf/subscribe [::editing? invoice-id])
     (rf/subscribe [::invoice invoice-id])])
  (fn [[edit? {:keys [rows]}] [_ _ operation-id]]
    (or edit?
        (some #(and (= (:operation-id %) operation-id)
                    (row-extra-info? %))
              rows))))

(rf/reg-sub
  ::product-items
  (fn [[_ invoice-id]]
    [(rf/subscribe [::config :catalogues])
     (rf/subscribe [::invoice invoice-id :price-catalogue-id])])
  (fn [[catalogs cat-id]]
    (let [cat-items (some->> (util/find-by-id cat-id catalogs)
                             :rows
                             ;; Don't sort rows, retain original catalog order
                             (map (fn [{:keys [id code text]}]
                                    {:value id
                                     :text  (ss/trim-join " " code text)})))]
      (cons {:value :custom
             :text  (common/loc :invoices.rows.customrow)
             :flags [:divide]}
            cat-items))))

(rf/reg-sub
  ::invoice-total-cents
  (fn [[_ invoice-id]]
    (rf/subscribe [::invoice invoice-id :rows]))
  (fn [rows]
    (apply + (map :total-cents rows))))

(rf/reg-sub
  ::invoice-ok?
  ;; Invoice is OK (fully filled) when there are operations, every operation has rows and
  ;; each row is properly filled. Worktime errors do not matter.
  (fn [[_ invoice-id]]
    (rf/subscribe [::invoice invoice-id]))
  (fn [{:keys [operations rows]}]
    (and (seq operations)
         (every? #(util/find-by-key :operation-id (:id %) rows) operations)
         (every? (fn [{:keys [code text price-per-unit units] :as row}]
                   (and (every? ss/not-blank? [(ss/trim-join "" code text)
                                               (str price-per-unit)
                                               (str units)])
                        (empty? (::errors row))))
                 rows))))

(rf/reg-sub
  ::show-discounts?
  (fn [[_ invoice-id _]]
    [(rf/subscribe [::editing? invoice-id])
     (rf/subscribe [::invoice invoice-id :rows])])
  (fn [[edit? rows] [_ _ op-id]]
    (or edit?
        (some (fn [{:keys [operation-id discount-percent]}]
                (let [dp (some-> discount-percent str ss/trim ss/blank-as-nil)]
                  (and (= operation-id op-id)
                       dp
                       (not= dp "0"))))
              rows))))

(rf/reg-sub
  ::show-vat-info?
  (fn [[_ invoice-id]]
    (rf/subscribe [::invoice invoice-id]))
  (fn [{:keys [sum-vat-info state]}]
    (and sum-vat-info (not= state "draft"))))
