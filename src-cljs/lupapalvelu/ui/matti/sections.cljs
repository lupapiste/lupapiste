(ns lupapalvelu.ui.matti.sections
  (:require [rum.core :as rum]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.state :as state]))

(defmulti schema-component #(-> % :schema :body first :type keyword))

(defmethod schema-component :default
  [options]
  (println "default options:" options))

(defn thread-log [v]
  (println v)
  v)

(defmethod schema-component :select
  [{:keys [schema state]}]
  [:select.dropdown
   {:value     (rum/react state)
    :on-change #(reset! state (.. % -target -value))}
   (->> schema :body first
        :body
        (map (fn [{n :name}]
               {:value n
                :text  (common/loc (str "matti.verdict." n))}))
        (sort-by :text)
        (cons {:value ""
               :text  (common/loc "selectone")})
        (map (fn [{:keys [value text]}]
               [:option {:key value :value value} text])))])

(defmulti section-component :id)

(defmethod section-component :default
  [{:keys [state schema data] :as options}]
  (schema-component (assoc options
                           :state (state/data-cursor state
                                                     (-> schema :info :name)
                                                     data))))

(rum/defc section < rum/reactive
  [{:keys [title schema] :as options}]
  (println "section state" (:state options))
  [:div [:h4 (common/loc title)]
   (section-component (assoc options
                             :schema (service/schema schema)))])
