(ns lupapalvelu.ui.checklist-summary
  (:require [rum.core :as rum]
            [clojure.string :as string]))

(enable-console-print!)

(def empty-state {:applicationId ""
                  :rows []
                  :input ""
                  :operations []})

(def state (atom empty-state))
(def rows  (rum/cursor-in state [:rows]))

(rum/defc summary-row [row-data]
  [:tr [:td "Cell"] [:td (:text row-data)] [:td "Faa"]])

(defn ko-subscription [value]
  (swap! state assoc :input value))

(defn update-operations [value]
  (swap! state assoc :operations value))

(defn id-subscription [value]
  (swap! state assoc :applicationId value)
  (when-not (empty? value)
    (-> (js/ajax.query "inspection-summaries-for-application"
                       (js-obj "id" value))
        (.success (fn [data]
                    (update-operations (js->clj (aget data "operations")
                                                :keywordize-keys true))))
        .call)))

(defn init
  [init-state props]
  (id-subscription ((-> (aget props ":rum/initial-state")
                                    :rum/args
                                    first
                                    (aget "id"))))
  #_(js/hub.send "XYZ" (js-obj :id (-> @state :applicationId)))
  init-state)

(rum/defc checklist-summary < rum/reactive
                              {:init init
                               :will-unmount (fn [& _] (reset! state empty-state))}
  [ko-app]
  (.subscribe (aget ko-app "id") id-subscription)
  [:div
   [:h1 "Tarkastusasiakirjan yhteenveto"]
   [:div
    [:label "Valitse tarkastusasiakirjan yhteenveto"]
    [:div
     {:class "left-buttons"}
     [:select.form-entry.is-middle
      {:style {:width "50%"}}
      (for [op (rum/react (rum/cursor-in state [:operations]))
            :let [op-name        (js/loc (str "operations." (:name op)))
                  op-description (string/join " - " (remove empty? [(:description op) (:op-identifier op)]))]]
        [:option
         {:value (:id op)}
         (str op-description " (" op-name ") ")])]
     [:button.positive
      {:on-click (fn [_] (swap! rows conj {:id (str "row" (rand-int 50)) :text "Faa"}))}
      [:i.lupicon-circle-plus]
      [:span "Luo uusi"]]]]
   [:table
    [:tbody
     (doall
       (for [row (rum/react rows)]
         (summary-row row)))]]
   [:button {:on-click (fn [_] (swap! rows conj {:id (str "row" (rand-int 50)) :text "Faa"}))}
    "Click"]])

(defn ^:export start [domId ko-app]
  (rum/mount (checklist-summary ko-app) (.getElementById js/document (name domId))))
