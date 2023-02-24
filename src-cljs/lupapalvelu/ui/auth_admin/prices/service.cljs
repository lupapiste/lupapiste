(ns lupapalvelu.ui.auth-admin.prices.service
  (:require [lupapalvelu.invoices.shared.util :as iutil]
            [lupapalvelu.ui.auth-admin.prices.state :as state]
            [lupapalvelu.ui.common :as common]))

(defn fetch-price-catalogues [& [callback]]
  (when (state/auth? :organization-price-catalogues)
    (common/query :organization-price-catalogues
                  (fn [{:keys [price-catalogues]}]
                    (reset! state/catalogues price-catalogues)
                    (when callback
                      (callback)))
                  :organizationId @state/org-id)))

(defn fetch-price-catalogue [catalogue-id callback]
  (when (state/auth? :organization-price-catalogue)
    (common/query :organization-price-catalogue
                  (comp callback :price-catalogue)
                  :organizationId @state/org-id
                  :price-catalogue-id catalogue-id)))

(defn new-price-catalogue-draft
  ([catalogue-id]
   (common/command {:command :new-price-catalogue-draft
                    :success (fn [{:keys [draft]}]
                               (state/refresh-auth)
                               (fetch-price-catalogues)
                               (state/set-selected draft true))}
                   :organizationId @state/org-id
                   :price-catalogue-id catalogue-id))
  ([]
   (new-price-catalogue-draft nil)))

(defn edit-price-catalogue-draft [changes]
  (common/command {:command :edit-price-catalogue-draft
                   :success (fn [{:keys [draft]}]
                              (state/update-edit-state draft))}
                  :organizationId @state/org-id
                  :price-catalogue-id (state/selected-catalogue-id)
                  :edit changes))

(defn publish-price-catalogue []
  (when (state/auth? :publish-price-catalogue)
    (let [catalog-id (state/selected-catalogue-id)]
      (common/command :publish-price-catalogue
                      (fn []
                        (state/refresh-auth)
                        (fetch-price-catalogues (partial fetch-price-catalogue catalog-id
                                                         state/set-selected)))
                      :organization-id @state/org-id
                      :price-catalogue-id catalog-id))))

(defn fetch-organization-operations [operation-categories]
  (common/query :all-operations-for-organization
                (fn [{:keys [operations] :as data}]
                  (reset! state/org-operations (iutil/get-operations-from-tree operations operation-categories)))
                :organizationId @state/org-id))

(defn move-row [row-id direction]
  (common/command {:command :move-price-catalogue-row
                   :success (fn [{:keys [updated-catalogue]}]
                              (if @state/edit-state
                                (state/update-edit-state updated-catalogue)
                                (do
                                  (state/set-selected updated-catalogue)
                                  (js/util.showSavedIndicator (clj->js {:ok true})))))}
                  :organizationId @state/org-id
                  :price-catalogue-id (state/selected-catalogue-id)
                  :row-id row-id
                  :direction direction))

(defn- get-proper-no-billing-periods [no-billing-periods]
  (apply dissoc no-billing-periods (reduce-kv
                                    (fn [memo k {:keys [start end]}]
                                      (if (or (nil? start) (nil? end))
                                        (conj memo k)
                                        memo)) [] no-billing-periods)))

(defn save-no-billing-periods [no-billing-periods]
  (common/command {:command :save-no-billing-periods
                   :success (fn [{:keys [updated-catalogue]}]
                              (if @state/edit-state
                                (state/update-edit-state updated-catalogue)
                                (do
                                  (state/set-selected updated-catalogue)
                                  (js/util.showSavedIndicator (clj->js {:ok true})))))
                   :error   (fn [{:keys [text period]}]
                              (js/hub.send "indicator"
                                           (clj->js {:style   "negative"
                                                     :rawMessage (js/sprintf "%s: %s - %s"
                                                                             (common/loc text)
                                                                             (:start period "")
                                                                             (:end period ""))}) ))}
                  :organizationId @state/org-id
                  :price-catalogue-id (state/selected-catalogue-id)
                  :no-billing-periods (get-proper-no-billing-periods no-billing-periods)))

(defn delete-catalogue []
  (common/command {:command :delete-price-catalogue
                   :success (fn []
                              (state/set-selected nil)
                              (state/refresh-auth)
                              (fetch-price-catalogues))}
                  :organizationId @state/org-id
                  :price-catalogue-id (state/selected-catalogue-id)))

(defn revert-catalogue []
  (let [catalog-id (state/selected-catalogue-id)]
    (common/command {:command :revert-price-catalogue-to-draft
                     :success (fn []
                                (fetch-price-catalogue catalog-id
                                                       #(state/set-selected % true)))}
                    :organizationId @state/org-id
                    :price-catalogue-id catalog-id)))
