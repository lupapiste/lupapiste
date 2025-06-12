(ns lupapalvelu.ui.admin.sso-keys.core
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.admin.sso-keys.subs :as subs]
            [lupapalvelu.ui.components :as ui]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.dom :as ui-dom]))

(defn- ip-field
  [id]
  [ui-input/text-input
   {:class        :form-input
    :data-test-id (str "sso-ip-" id)
    :on-change    (fn [v] (>evt [::subs/change-ip {:id id :ip v}]))
    :value        (<sub [::subs/ip id])}])

(defn- sso-key-field
  [id]
  [ui-input/text-input
   {:class        :form-input
    :placeholder  (when-not (= :sso/new-entry id) "Ei näytetä")
    :data-test-id (str "sso-key-" id)
    :on-change    (fn [v] (>evt [::subs/change-sso-key {:id id :sso-key v}]))
    :value        (<sub [::subs/sso-key id])}])

(defn- comment-field
  [id]
  [ui-input/text-input
   {:class        :form-input
    :data-test-id (str "sso-comment-" id)
    :on-change    (fn [v] (>evt [::subs/change-comment {:id id :comment v}]))
    :value        (<sub [::subs/comment id])}])

(defn- update-button
  [id]
  (let [entry (<sub [::subs/current-entry id])]
    [:button.primary
     {:disabled (not (<sub [::subs/can-save? id]))
      :on-click (fn [_] (>evt [::subs/save-changes {:id id :entry entry}]))}
     (if (= :sso/new-entry id)
       "Lisää"
       "Päivitä")]))

(defn- remove-icon
  [id]
  [ui/icon-button
   {:icon     :lupicon-remove
    :class    :secondary
    :text-loc :remove
    :on-click (fn [_] (>evt [::subs/show-remove-entry-dialog {:id id}]))}])

(defn- row
  [id]
  [:tr
   {:key id}
   [:td [ip-field id]]
   [:td [sso-key-field id]]
   [:td [comment-field id]]
   [:td [update-button id]]
   [:td.action-column
    (when-not (= :sso/new-entry id)
      [remove-icon id])]])

(defn- rows
  []
  (->> (<sub [::subs/row-ids])
       (map (fn [id] [row id]))
       (into [:tbody])))

(defn- add-row
  []
  [:tbody
   [row :sso/new-entry]])

(defn- table
  []
  [:table {:data-test-id "sso-table"}
   [:thead
    [:tr
     [:th "IP"]
     [:th "Avain"]
     [:th "Kommentti"]
     [:th "Tallenna"]
     [:th "Poista"]]]
   [rows]
   [add-row]])

(defn- view
  []
  (when (<sub [::subs/visible?])
    [:<>
     [:h2 "Single Sign On -avaimet"]
     [:div
      [table]]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id _]
  (ui-dom/mount! mounter dom-id))
