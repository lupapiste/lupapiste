(ns lupapalvelu.ui.biller.view
  "Biller user interface."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.biller.data :as data]
            [lupapalvelu.ui.biller.invoice :as invoice]
            [lupapalvelu.ui.biller.transfer-batch :as batch]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [reagent.core :as r]
            [sade.util :as util]))

(defn organization []
  (let [orgs    (<sub [:value/get ::data/organizations])
        current (<sub [::data/current-organization])
        text-fn (fn [{:keys [id name]}]
                  (js/sprintf "%s (%s)" (get name (keyword (common/get-current-language))) id))]
    (if (= (count orgs) 1)
      [:div.txt--bold (text-fn current)]
      [components/dropdown
       (:id current)
       {:items      (map #(hash-map :value (:id %) :text (text-fn %)) orgs)
        :sort-by    :text
        :aria-label (loc :stamp.organization)
        :choose?    false
        :class      :w--min-30em
        :callback   #(>evt [::data/select-organization (util/find-by-id % orgs)])}])))

(defn header []
  (let [view (<sub [:value/get ::data/current-view])]
    [:div.flex--between.flex--wrap-reverse.flex--gap2.flex--align-center
     [:div [components/toggle-group
            #{view}
            {:items    [{:text-loc :invoices
                         :value    :invoices}
                        {:text-loc :invoices.transfer-batches
                         :value    :transfer-batches}]
             :callback #(>evt [:value/set ::data/current-view %])
             :radio?   true
             :class    :gap--r1
             :prefix   :plain-bold-tag}]]
     [organization]]))

(defn view  []
  (r/with-let [_ (>evt [::data/initialize])
               _ (>evt [:auth/refresh])
               _ (>evt [:poller/add data/poller-id data/poller-interval
                        [::data/poll-invoices]])]
    (let [v (<sub [::data/current-view])]
      [:div.pad--4.flex--column.flex--row-gap4
       [header]
       (case v
         :invoices         [invoice/view]
         :transfer-batches [batch/view]
         nil)])
    (finally
      (>evt [:poller/remove data/poller-id])
      (>evt [::data/clear]))))

(defonce mounter (ui-dom/init :reagent (constantly view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
