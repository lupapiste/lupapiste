(ns lupapalvelu.ui.notices.view
  "Notices section view where an authority can create a notice resolution (aka poor man's verdict)."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.quill :as quill]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.notices.data :as data]))

(defn delete-resolution []
  [components/icon-button {:text-loc "remove"
                           :icon     "lupicon-remove"
                           :class    :secondary.flex--self-end
                           :on-click (fn []
                                       (>evt [:dialog/show {:ltext    "notice.delete.confirmation"
                                                            :type     :yes-no
                                                            :callback #(>evt [::data/delete-resolution])}]))}])

(defn view-resolution []
  (let [state           (<sub [::data/resolution-state])
        notes           (<sub [::data/resolution-tags])
        user            (<sub [::data/resolution-name])
        date            (<sub [::data/resolution-date])
        {:keys [url filename
                error]} (<sub [::data/resolution-attachment])
        can-delete?     (and (<sub [:auth/application? :delete-published-notice-resolution])
                             (or url error))]
    [:div.flex--wrap.flex--row-gap2.flex--column-gap16
     [:div.grid.gap-2 {:class "grid-cols-[auto_auto]"}
      [:div.font-bold (common/loc :notice.state)] [:div (common/loc state)]
      [:div.font-bold (common/loc :notice.title)] notes
      [:div] [:div date]
      [:div.font-bold (common/loc :applications.authority)] user
      [:div.font-bold (common/loc :pdf.attachment)]
      (cond
        url   [:div [:a {:href url :target :_blank} filename]]
        error [:div.danger-note (common/loc error)]
        :else [:div.fg--gray [:i.spin.lupicon-refresh.dsp--inline-block]])]
     (when can-delete?
       [:div.flex--self-start [delete-resolution]])]))

(defn waiting? []
  (<sub [::data/waiting?]))

(defn- edit-state []
  [:div.flex--column
   [:label.lux {:for "resolution-state"}
     (common/loc :notice.state)]
   [components/dropdown (<sub [::data/editing-state])
    {:id        "resolution-state"
     :disabled? (waiting?)
     :items     [{:value "approved"
                  :text  (common/loc :approved)}
                 {:value "rejected"
                  :text  (common/loc :rejected)}]
     :sort-by   :text
     :callback  #(>evt [::data/edit :state %])}]])

(defn- edit-text []
  [:div.flex--column
   [:label.lux {:for "resolution-text"}
     (common/loc :notice.title)]
   [quill/quill-edit (<sub [::data/editing-text])
    {:id        "resolution-text"
     :disabled? (waiting?)
     :callback  #(>evt [::data/edit :text %])}]])

(defn- last-modified []
  (when-let [s (<sub [::data/editing-time])]
    [:div.flex--self-center.fg--gray
     (common/loc :pate.last-saved s)]))

(defn- edit-resolution []
  [:div.flex--column.flex--gap2
   [:div.flex--between.flex--wrap-reverse.flex--gap2
    [edit-state] [last-modified]]
   [edit-text]
   [:div.flex.flex--right.flex--wrap.flex--gap2
    [:button.secondary {:on-click #(>evt [::data/toggle-editing false])
                        :disabled (waiting?)}
     (common/loc :cancel)]
    [:button.primary {:disabled (or (waiting?)
                                    (not (<sub [::data/can-publish?])))
                      :on-click #(>evt [::data/publish-resolution])}
     (common/loc :done)]]])

(defn view []
  (when (<sub [::data/visible?])
    [:div.flex--column.flex--row-gap1
     [:h1 (common/loc :application.tabProcess)]
     (let [published? (<sub [::data/resolution-published?])
           editing?   (<sub [::data/editing?])
           can-edit?  (<sub [:auth/application? :publish-notice-resolution])]
       (cond
         published? [view-resolution]
         editing?   [edit-resolution]
         can-edit?  [:div
                     [:button.primary
                      {:on-click #(>evt [::data/toggle-editing true])}
                      (common/loc :notice.resolve)]]))]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
