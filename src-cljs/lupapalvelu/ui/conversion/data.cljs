(ns lupapalvelu.ui.conversion.data
  (:require [lupapalvelu.conversion.section-schemas :as sec-sc]
            [lupapalvelu.ui.common :as common]
            [re-frame.core :as rf]
            [sade.util :as util]))

(defn- pathify
  "Converts `xs` into keyword vector or nil (for nil and empty)."
  [& xs]
  (some->> xs flatten (keep keyword) seq))

(defn- access
  "If `path` is emptyish, `m` is returned as such."
  [m & path]
  (if-let [xs (pathify path)]
    (get-in m xs)
    m))

(defn section-loc [l10n & path]
  (when-let [path (util/kw-path path)]
    (or (get-in l10n (pathify path (common/get-current-language)))
        (common/loc path))))

;; --------------------
;; Events
;; --------------------

(rf/reg-event-db ::initialize.success
  (fn [db [_ {:keys [sections]}]]
    (let [section-ids (map (comp keyword :id) sections)]
      (update db ::data assoc
              ;; We store ids separately to maintain ordering
              :section-ids section-ids
              :sections (zipmap section-ids sections)))))

;; Called by the router when the view is shown or hidden.
(rf/reg-event-fx ::toggle-view
  (fn [{db :db} [_ visible?]]
    (if visible?
      {:db           (assoc db ::data {})
       :action/query {:name    :conversion-sections
                      :params  {:id (common/application-id)}
                      :success ::initialize.success}
       :dispatch     [:auth/refresh]}
      {:db (dissoc db ::data)})))

(rf/reg-event-db ::set-editing?
  (fn [db [_ editing?]]
    (assoc-in db [::data :editing?] editing?)))

(rf/reg-event-db ::edit-section.success
  (fn [db [_ {:keys [modified errors]} section-id path]]
    (cond
      modified
      (-> db
          (assoc-in (pathify ::data :sections section-id :modified) modified)
          (util/dissoc-in (pathify ::data :errors section-id path)))
      errors
      (reduce (fn [acc [path error]]
                (assoc-in acc (pathify ::data :errors section-id path) error))
              db
              errors)
      ;; Just in case
      :else db)))

(rf/reg-event-fx ::edit-section
  (fn [{db :db} [_ section-id path value]]
    {:db             (assoc-in db
                               (pathify ::data :sections section-id :data path)
                               value)
     :action/command {:name    :edit-conversion-section
                      :params  {:id         (common/application-id)
                                :section-id section-id
                                :path       (pathify path)
                                :value      value}
                      :success [::edit-section.success section-id path]}}))

;; --------------------
;; Subs
;; --------------------

(rf/reg-sub ::editing?
  :-> (util/fn-> ::data :editing?))

(rf/reg-sub ::errors
  :-> (util/fn-> ::data :errors))

(rf/reg-sub ::sections
  :-> (util/fn-> ::data :sections))

;; Section ids in the correct order
(rf/reg-sub ::section-ids
  :-> (util/fn-> ::data :section-ids))

(rf/reg-sub ::section
  :<- [::sections]
  :=> (fn [sections section-id & path]
        (access sections section-id path)))

(rf/reg-sub ::section-l10n
  (fn [[_ section-id] _]
    (rf/subscribe [::section section-id :l10n]))
  :-> identity)

(rf/reg-sub ::dict-value
  (fn [[_ section-id & _] _]
    (rf/subscribe [::section section-id]))
  :=> (fn [section _ & path]
        (access section :data path)))

(rf/reg-sub ::schema
  (fn [[_ section-id & _] _]
    (rf/subscribe [::section section-id]))
  :=> (fn [{:keys [schema]} _ & path]
        (access schema path)))

(rf/reg-sub ::dict-schema
  (fn [[_ section-id & path] _]
    (rf/subscribe [::schema section-id :dictionary path]))
  :-> identity)

(rf/reg-sub ::dict-schema-type
  (fn [[_ section-id & path]]
    (rf/subscribe [::dict-schema section-id path]))
  :-> sec-sc/pate-schema-type)

(rf/reg-sub ::dict-inner-schema
  (fn [[_ section-id & path]]
    [(rf/subscribe [::dict-schema section-id path])
     (rf/subscribe [::dict-schema-type section-id path])])
  :=> (fn [[schema schema-type]]
        (get schema schema-type)))

(rf/reg-sub ::last-modified
  :<- [::sections]
  :-> (util/fn->> vals
                  (map :modified)
                  (apply max)))

(rf/reg-sub ::dict-editing?
  (fn [[_ section-id & path]]
    [(rf/subscribe [:auth/application? :edit-conversion-section])
     (rf/subscribe [::editing?])
     (rf/subscribe [::dict-inner-schema section-id path])])
  (fn [[authed? editing? {:keys [read-only? always-editing?]}] _]
    (when (and authed? (not read-only?))
      (or editing? always-editing?))))

(rf/reg-sub ::dict-visible?
  (fn [[_ section-id & path]]
    [(rf/subscribe [::dict-editing? section-id path])
     (rf/subscribe [::dict-value section-id path])])
  (fn [[editing? value]]
    (or editing? (util/fullish? value))))

(rf/reg-sub ::dict-error
  :<- [::errors]
  :=> (fn [errors section-id & path]
        (access errors section-id path)))


;; The l10n key is either the explicit `i18nkey` or `dict`.
(rf/reg-sub ::dict-loc
  (fn [[_ section-id & path] _]
    [(rf/subscribe [::dict-inner-schema section-id path])
     (rf/subscribe [::section-l10n section-id])])
  :=> (fn [[{:keys [i18nkey]} l10n] _ & path]
        (section-loc l10n (or i18nkey path))))

(rf/reg-sub ::section-loc
  (fn [[_ section-id _] _]
    (rf/subscribe [::section-l10n section-id]))
  :=> (fn [l10n _ & path]
        (section-loc l10n path)))

(rf/reg-sub ::dict-items
  (fn [[_ section-id & path] _]
    [(rf/subscribe [::dict-schema section-id path :select])
     (rf/subscribe [::section-l10n section-id])])
  :=> (fn [[{:keys [item-loc-prefix items]
             sby   :sort-by} l10n] & _]
        (cond->> (map (fn [v]
                        {:value v
                         :text (section-loc l10n (pathify item-loc-prefix v) )})
                      items)
          sby (sort-by (keyword sby)))))
