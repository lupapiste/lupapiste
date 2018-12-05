(ns lupapalvelu.ui.auth-admin.prices.service
  (:require [lupapalvelu.invoices.shared.util :as util]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.auth-admin.prices.state :as state]))

(defn- sort-latest-first [catalogues]
  (reverse (sort-by :valid-from catalogues)))

(defn- auth? [action]
  (boolean (js/lupapisteApp.models.globalAuthModel.ok (name action))))

(defn fetch-price-catalogues [& [callback]]
  (when (auth? :organization-price-catalogues)
    (common/query :organization-price-catalogues
                  (fn [data]
                    (let [catalogues (:price-catalogues data)]
                      (reset! state/catalogues (sort-latest-first catalogues))
                      (when callback (callback))))
                  :organization-id @state/org-id)))

(defn publish-catalogue [catalogue]
  (when (auth? :publish-price-catalogue)
    (let [new-catalogue (select-keys catalogue [:valid-from-str :rows])]
      (common/command :publish-price-catalogue
                      (fn [data]
                        (let [id (:price-catalogue-id data)]
                          (fetch-price-catalogues
                          (fn []
                            (state/set-mode :show)
                            (state/set-selected-catalogue-id id)))))
                      :organization-id @state/org-id
                      :price-catalogue new-catalogue))))

(defn fetch-organization-operations [operation-categories]
  (common/query :all-operations-for-organization
                (fn [{:keys [operations] :as data}]
                  (reset! state/org-operations (util/get-operations-from-tree operations operation-categories)))
                :organizationId @state/org-id))
