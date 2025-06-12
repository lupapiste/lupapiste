(ns lupapalvelu.ui.docgen.feature.allu-application-kind
  "Utilities for fetching allu application kinds options for various
  application types."
  (:require [lupapalvelu.ui.authorization :as auth]
            [re-frame.core :as rf]))

(rf/reg-sub
  ::allu-application-kind-options
  (fn [db [_ application-type]]
    (get-in db [::allu-application-kind-options application-type])))

(rf/reg-event-fx
  ::allu-application-kind-options.success
  (fn [{:keys [db]} [_ response application-type]]
    (let [kinds (:kinds response)]
      {:db (assoc-in db [::allu-application-kind-options application-type] kinds)})))

(defn make-application-kind-fetch-effect [application-id application-type]
  [:action/query {:name    :application-kinds
                  :params  {:id   application-id
                            :type application-type}
                  :success [::allu-application-kind-options.success application-type]}])

(defn find-fields-matching-type
  "Finds all instances of matching field type from the schema tree."
  [field-type {:keys [schema]}]
  (let [schemas (tree-seq :body :body schema)
        allu-application-kind? (fn [field]
                                 (= (:type field) field-type))]
    (filter allu-application-kind? schemas)))

(defn allu-application-types
  "Produces set of all applicationType values found in the document
  schemas."
  [documents]
  (let [application-kind-fields (mapcat (partial find-fields-matching-type "allu-application-kind")
                                        documents)]
    (set (map :applicationType application-kind-fields))))

(defn on-app-loaded [_ [_ {:keys [app]}]]
  (when (auth/application-auth? :application-kinds)
    (let [application-types         (allu-application-types (:documents app))
          application-type-fetch-fx (mapv (partial make-application-kind-fetch-effect (:id app))
                                          application-types)]
      {:fx application-type-fetch-fx})))

(defn on-unload [{:keys [db]}]
  {:db (dissoc db ::allu-application-kind-options)})
