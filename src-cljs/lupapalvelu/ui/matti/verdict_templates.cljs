(ns lupapalvelu.ui.matti.verdict-templates
  (:require [rum.core :as rum]
            [lupapalvelu.matti.shared :as matti]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.matti.sections :as sections]
            [lupapalvelu.ui.matti.state :as state]))

(defonce current-template (atom {}))


(defn verdict-template
  [{:keys [name sections state]}]
  [:div
   [:h3 name]
   (for [s sections]
     [:div {:key (:id s)}
      (sections/section (assoc s :state (state/data-cursor state
                                                           (:id s)
                                                           {(-> s :id keyword) (:data s)})))])])

(rum/defc verdict-templates < rum/reactive
  [_]
  (when-not (empty? (rum/react service/schemas))
    [:div (verdict-template (assoc matti/default-verdict-template
                                   :name "Unnamed"
                                   :state current-template))
     [:p (str (rum/react current-template))]]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (verdict-templates (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (service/fetch-schemas)
  (mount-component))
