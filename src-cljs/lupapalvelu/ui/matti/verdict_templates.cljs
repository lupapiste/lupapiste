(ns lupapalvelu.ui.matti.verdict-templates
  (:require [rum.core :as rum]
            [lupapalvelu.matti.shared :as matti]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.matti.sections :as sections]
            [lupapalvelu.ui.matti.path :as path]))

(defonce current-template (atom {}))

(rum/defcs add-section < (rum/local "" ::selected)
  [local-state {:keys [sections state] :as options}]
  (let [removed (filter #(path/meta? (assoc options
                                            :path [%]
                                            :state state)
                                     :removed?)
                        (map :id sections))
        selected* (::selected local-state)]
    (when-not (contains? (set removed) @selected*)
     (common/reset-if-needed! selected* (first removed)))
    (when-not (empty? removed)
      [:span
       [:select.dropdown
        {:value @selected*
         :on-change (common/event->state selected*)}
        (map (fn [n]
               [:option {:value n :key n} (common/loc n)])
             removed)]
       [:button.primary
        {:on-click #(path/flip-meta {:path [@selected*]
                                     :state state}
                                    :removed?)}
        [:i.lupicon-circle-plus]
        [:span (common/loc "add")]]])))

(defn verdict-template
  [{:keys [name sections state data] :as options}]
  (when (empty? @state)
    (reset! state data))
  [:div
   [:h3 name]
   (for [sec sections]
     [:div {:key (:id sec)}
      (sections/section (assoc sec
                               :path (path/extend (:id sec))
                               :state state))])
   (add-section options)])

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
