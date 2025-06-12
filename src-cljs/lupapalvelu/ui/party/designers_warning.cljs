(ns lupapalvelu.ui.party.designers-warning
  "Displays the 'non-approved designers' warning."
  (:require [clojure.set :as set]
            [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.accordion-toolbar :as at]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.approvals :as approvals]
            [lupapalvelu.ui.docgen.feature.document-types :as doc-types]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-sub ::non-approved-designers-doc-ids
  :<- [::doc-types/designer-doc-ids]
  :<- [::docs/enabled-doc-ids]
  :<- [::approvals/approved-doc-ids]
  :<- [::doc-types/post-verdict-party-doc-ids]
  :<- [::app/in-post-verdict-state?]
  (fn [[designer-ids enabled approved post-verdict-party post-verdict?]]
    (let [visible (cond-> (set/intersection (set designer-ids)
                                            (set enabled))
                    true          (set/difference (set approved))
                    post-verdict? (set/intersection (set post-verdict-party)))]
      ;; retain the original order of documents
      (->> designer-ids
           (keep visible)
           (into [])))))

(rf/reg-sub ::non-approved-designers-texts
  :<- [::non-approved-designers-doc-ids]
  (fn [doc-ids]
    (->> doc-ids
         (mapv (fn [doc-id]
                 (let [schema-name (<sub [::docs/schema-name doc-id])
                       header      (<sub [::at/header-description doc-id])]
                   (str (common/loc (util/kw-path schema-name :_group_label))
                        header))))
         not-empty)))

(defn- view
  []
  (when-let [non-approved-designers (<sub [::non-approved-designers-texts])]
    [:div.clear
     [:h2.missing-info-title
      (common/loc :application.designers-not-approved)]
     [:div.info-line
      [:div.like-btn
       [:i.lupicon-circle-attention
        {:aria-hidden true}]
       [:span.non-approved-designers
        (common/loc :application.designers-not-approved-help)]]
      (->> non-approved-designers
           (map (fn [text]
                  [:li text]))
           (into [:ul.non-approved-designers]))]
     [:div.clear.spacerM]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id _params]
  (ui-dom/mount! mounter dom-id {:dom-id dom-id}))
