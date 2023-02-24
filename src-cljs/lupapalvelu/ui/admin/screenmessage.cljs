(ns lupapalvelu.ui.admin.screenmessage
  "Managing screen notes for Lupapiste, Docstore, Terminal and Departmental."
  (:require [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [sade.shared-strings :as ss]))

(rf/reg-event-fx
  ::fetch-messages
  (fn [_]
    {:action/query {:name    :admin-screenmessages
                    :success (fn [{:keys [screenmessages]}]
                               (>evt [:value/set ::messages screenmessages])
                               (js/LUPAPISTE.Screenmessage.refresh))}}))

(rf/reg-event-fx
  ::remove-message
  (fn [_ [_ msg-id]]
    {:action/command {:name    :remove-screenmessage
                      :params  {:message-id msg-id}
                      :success ::fetch-messages}}))

(rf/reg-event-fx
  ::save-message
  (fn [{db :db} [_ editing]]
    {:action/command {:name    :add-screenmessage
                      :params  editing
                      :success (fn []
                                 (>evt [:value/set ::editing nil])
                                 (>evt [::fetch-messages]))}}))

(defn product-loc [product]
  (case (keyword product)
    :store        (loc :auth-admin.docstore.title-short)
    :terminal     (loc :auth-admin.docterminal.title)
    :departmental (loc :auth-admin.docdepartmental.title)
    "Lupapiste"))

(defn message-table []
  (when-let [messages (seq (<sub [:value/get ::messages]))]
    [:table
     [:thead
      [:tr
       [:th.pad--h1 (loc :admin.screenmessages.added)]
       [:th.pad--h1 (loc :admin.screenmessages.products)]
       [:th.pad--h1.w--50 (loc :admin.screenmessages.fi)]
       [:th.pad--h1.w--50 (loc :admin.screenmessages.sv)]
       [:th (loc :remove)]]]
     (into [:tbody]
           (for [{:keys [added products fi sv id]} messages]
             [:tr
              [:td.pad--h1.ws--nowrap (js/util.finnishDateAndTime added)]
              (into [:td.pad--h1] (map #(vector :span.dsp--block.ws--nowrap (product-loc %)) products))
              [:td.pad--h1 fi]
              [:td.pad--h1 sv]
              [:td.txt--center
               [components/icon-button {:class      :tertiary
                                        :icon       :lupicon-remove
                                        :icon-only? true
                                        :text-loc   :remove
                                        :on-click   #(>evt [::remove-message id])}]]]))]))

(defn message-editor []
  (let [{:keys [products fi sv]
         :as   editing} (<sub [:value/get ::editing])]
    (if editing
      [:div.gap--v1.flex--column.flex--row-gap1
       [:div
        [:label.lux.txt--bold (loc :admin.screenmessages.products)]
        [components/toggle-group products
         {:items    (map #(hash-map :value % :text (product-loc %))
                         ["lupapiste" "store" "terminal" "departmental"])
          :callback #(>evt [:value/set ::editing :products %])}]]
       [:div
        [:label.lux.txt--bold
         {:for "screenmessage-fi"} (loc :admin.screenmessages.fi)]
        [components/text-edit fi
         {:lines     4
          :id        "screenmessage-fi"
          :required? true
          :class     :w--100
          :callback  #(>evt [:value/set ::editing :fi %])}]]
       [:div
        [:label.lux.txt--bold
         {:for "screenmessage-sv"}
         (loc :admin.screenmessages.sv)]
        [components/text-edit sv
         {:lines       4
          :id          "screenmessage-sv"
          :placeholder (loc :admin.screenmessages.ifNoSwedishGiven)
          :class       :w--100
          :callback    #(>evt [:value/set ::editing :sv %])}]]
       [:div.flex--wrap.flex--gap2
        [components/icon-button {:icon      :lupicon-save
                                 :text-loc  :save
                                 :class     :primary
                                 :disabled? (or (empty? products)
                                                (ss/blank? fi))
                                 :on-click  #(>evt [::save-message editing])}]
        [:button.tertiary {:on-click #(>evt [:value/set ::editing nil])}
         (loc :cancel)]]]
      [components/icon-button {:icon     :lupicon-circle-plus
                               :text-loc :admin.screenmessages.new
                               :class    :primary
                               :on-click #(>evt [:value/set ::editing
                                                 :products #{"lupapiste"}])}])))

(defn view []
  (r/with-let [_ (>evt [::fetch-messages])]
    [:div
     [:h2 (loc :admin.screenmessages.list-title)]
     [message-table]
     [message-editor]]))


(defn mount-component [dom-id]
  (rd/render [view] (.getElementById js/document dom-id)))

(defn ^:export start [dom-id]
  (mount-component dom-id))
