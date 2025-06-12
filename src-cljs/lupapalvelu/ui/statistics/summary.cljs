(ns lupapalvelu.ui.statistics.summary
  "Application processing days summary."
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.statistics.schemas :as stats-sc]
            [lupapalvelu.statistics.tags :as stats-tags]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.date :as date]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.statistics.data.summary :as data]
            [reagent.core :as r]
            [sade.util :as util]))

(defn editor []
  (let [complexity (<sub [::data/editing-complexity])
        id         "edit-complexity"
        items      (for [{kw :kw} stats-sc/complexities]
                     {:value (name kw)
                      :text  (stats-tags/complexity->text loc kw)})]
    [:div.dsp--inline-block.pad--2.bd--black
     [:div.flex--column.flex--gap2
      [:div.flex.flex--gap1.flex--align-center
       [:label.lux.txt--bold {:for id}
        (loc :statistics.processing.complexity)]
       [components/dropdown complexity {:id       id
                                        :class    :w--min-20rem
                                        :choose?  false
                                        :items    items
                                        :callback #(>evt [::data/edit-complexity %])}]]
      [:div.flex.flex--right.flex--gap1
       [:button.primary {:on-click #(>evt [::data/save-edit])}
        (loc :save)]
       [:button.secondary {:on-click #(>evt [::data/toggle-edit false])}
        (loc :cancel)]]]]))

(def ^:private s-loc   (partial path/loc :statistics.processing))
(def ^:private day-loc (partial loc :statistics.period.d))
(def ^:private kpl-loc (partial loc :statistics.processing.kpl))

(defn labeled [text id content]
  [:div.flex--column.flex--align-center
   [:label.lux.ws--nowrap.txt--bold {:for id} text]
   (if (string? content)
     [:span.ws--nowrap {:id id} content]
     content)])

(defn summary-row []
  (let [{:keys [status used-period delays remaining remaining-period
                deadline verdict-ts requests complexity
                unseen?]} (<sub [::data/processing-days])
        can-edit?         (<sub [:auth/application? :set-processing-complexity])
        make-id           (util/fn->> name (str "processing-summary-"))
        complexity-id     (make-id :complexity)
        requests-id       (make-id :requests)
        remaining-id      (make-id :remaining)]
    [:div.flex--wrap.flex--row-gap1.flex--column-gap2
     [labeled (s-loc :status) (make-id :status) (s-loc :status status)]
     [labeled (s-loc :complexity) complexity-id
      (let [complexity (stats-tags/complexity->text loc complexity)]
        (cond->> complexity
          can-edit?
          (conj [:a {:id       complexity-id
                     :on-click #(>evt [::data/toggle-edit true])}])))]
     [labeled (s-loc :used) (make-id :used)
      (stats-tags/period->text loc used-period)]
     [labeled (s-loc :requests) requests-id
      (cond->> (kpl-loc requests)
        unseen? (conj [:span.lux-new {:id requests-id}]))]
     [labeled (s-loc :delays) (make-id :delays) (day-loc delays)]
     [labeled
      (s-loc (if verdict-ts :verdict :remaining))
      remaining-id
      (cond->> (if verdict-ts
                 (date/finnish-date verdict-ts)
                 (cond-> (stats-tags/period->text loc remaining-period)
                   deadline (str " (" (date/finnish-date deadline) ")")))
        (neg? remaining)
        (conj [:span.txt--bold.fg--negative {:id remaining-id}]))]]))

(defn no-complexity []
  [:div.primary-note
   [:div.flex--wrap.flex--gap2.flex--align-center
    [:div (s-loc :no-complexity.info)]
    [:button.primary {:on-click #(>evt [::data/toggle-edit true])}
     (s-loc :no-complexity.button)]]])

(defn viewer []
  (let [complexity? (<sub [::data/complexity?])
        can-edit?   (<sub [:auth/application? :set-processing-complexity])]
    (cond
      complexity? [summary-row]
      can-edit?   [no-complexity])))

(defn view []
  (r/with-let [_ (>evt [::data/toggle-visible true])
               _ (>evt [::hub/subscribe :application-loaded
                        #(>evt [::data/toggle-visible true])
                        ::application-loaded-hub-id])]
    (when (<sub [::data/processing-days])
      [:div.pad--1
       (if (<sub [::data/editing?])
         [editor]
         [viewer])])
    (finally
      (>evt [::data/toggle-visible false])
      (>evt [::hub/unsubscribe ::application-loaded-hub-id]))))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
