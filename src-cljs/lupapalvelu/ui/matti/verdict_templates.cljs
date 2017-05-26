(ns lupapalvelu.ui.matti.verdict-templates
  (:require [rum.core :as rum]
            [lupapalvelu.matti.shared :as matti]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.matti.sections :as sections]
            [lupapalvelu.ui.matti.path :as path]))

(defonce current-template (atom {}))


(defn verdict-template
  [{:keys [name sections state data]}]
  (when (empty? @state)
    (reset! state data))
  [:div
   [:h3 name]
   (for [sec sections]
     [:div {:key (:id sec)}
      (sections/section (assoc sec
                               :path (path/extend (:id sec))
                               :state state))])])

(rum/defc verdict-templates < rum/reactive
  [_]
  (when-not (empty? (rum/react service/schemas))
    [:div (verdict-template (assoc matti/default-verdict-template
                                   :state current-template
                                   :data matti/default-data))
     [:p (str (rum/react current-template))]]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (verdict-templates (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (service/fetch-schemas)
  (mount-component))
