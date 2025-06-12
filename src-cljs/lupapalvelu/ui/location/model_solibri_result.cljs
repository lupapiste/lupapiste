(ns lupapalvelu.ui.location.model-solibri-result
  (:require [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.location.model-solibri-result-subs :as solibri-result-subs]
            [sade.strings :as ss]))

(def breadcrumb-item-class ["h-6" "flex" "items-center"])
(def button-classes ["min-h-min" "max-h-fit" "focus:ring-2"])
(def nav-box-base-classes ["rounded-sm" "p-2" "mb-2" "w-full" "border-solid"])
(def nav-box-base-flex-classes ["flex" "justify-between" "gap-2" "items-center"])
(def nav-box-back-button-classes ["inline-block" "border-solid" "border-r" "border-ui-black" "pr-2"])

(defn tooltip-icon-button [{:keys [icon on-click on-mouse-out tooltip-text]}]
  [:span.group
   [:button
    {:class        button-classes
     :on-mouse-out on-mouse-out
     :on-click     on-click}
    [:i.text-base {:class (name icon)}]]
   [:span.font-xs.py-1.px-2.ml-2.-mt-1.rounded-sm.absolute.text-nowrap.text-ui-white.bg-ui-black.invisible.group-hover:visible
    tooltip-text]])

(defn status->class [type status]
  (get-in {"PASSED"     {:border "border-success"
                         :bg     "bg-success"}
           "FAILED"     {:border "border-fail"
                         :bg     "bg-fail"}
           "ERROR"      {:border "border-fail"
                         :bg     "bg-fail"}
           "IRRELEVANT" {:border "border-ui-black"
                         :bg     "bg-ui-black"}}
          [status type]))

(defn status-block [status]
  [:span.inline-block.ml-2.mr-1.w-2.p-0
   {:class [(status->class :bg status)
            (if (= status "IRRELEVANT")
              ;; FIXME: Incorrect height value, likely didn't do anything previously?
              "h-[0.1875rem]"
              "h-2")]}])

(defn render-status-row [{:keys [type status id]}]
  (if (= type :rule)
    [:span.text-xs.flex.items-center.font-normal
     [:span.uppercase.p-0 (common/loc (str "model-details-page.result.status." status))]
     [status-block status]]
    (let [child-status-counts (<sub [::solibri-result-subs/child-status-counts id])]
      (into [:span.text-xs.flex.items-center.font-normal.mr-1
             (for [status ["PASSED" "FAILED" "IRRELEVANT"]]
               ^{:key (str id "-" status)}
               [:<>
                [status-block status]
                [:span (str (get child-status-counts status 0))]])]))))

(defn result-breadcrumb []
  (let [{:keys [path]} (<sub [::solibri-result-subs/navigation-items])]
    [:div.py-3.flex.flex-wrap.items-center.gap-y-1.border-b.border-ui-gray-221
     [:span {:class breadcrumb-item-class}
      (into
        (if path
          [:button.text-ui-blue.flex.items-center
           {:class     button-classes
            :tab-index 0
            :on-click  #(>evt [::solibri-result-subs/set-navigation-root nil])}]
          [:span.flex.items-center])
        [[:i.lupicon-house.text-sm.pr-2_5.text-ui-black.m-0
          {:class "pr-[0.625rem]"}]
         [:span (common/loc :model-details-page.results.breadcrumb.start)]])]
     (when path
       (for [[i item] (map-indexed vector path)]
         ^{:key (str "breadcrumb-item-" (:id item))}
         [:span.text-nowrap
          {:class breadcrumb-item-class}
          [:span.px-3 "/"]
          (if (= (inc i) (count path))
            [:span.flex.items-center (:name item)]
            [:button.text-ui-blue.flex.items-center
             {:class     button-classes
              :tab-index 0
              :on-click  #(>evt [::solibri-result-subs/set-navigation-root item])}
             (:name item)])]))]))

(defn navigation-header [{:keys [name id] :as item} parent]
  (let [ui-mode-wide? (<sub [::solibri-result-subs/ui-mode-wide?])
        rule-set?     (= (:type item) :rule-set)
        rule?         (not rule-set?)]
    [:span {:class (concat
                     nav-box-base-classes
                     nav-box-base-flex-classes
                     ["bg-ui-blue-10"])}
     [:span {:class nav-box-base-flex-classes}
      [:button
       {:class     (concat button-classes nav-box-back-button-classes)
        :tab-index 0
        :on-click  #(>evt [::solibri-result-subs/set-navigation-root parent])}
       [:i.lupicon-arrow-left.text-sm.pr-2]]
      [:span.font-bold
       (if rule?
         [:button.inline-block
          {:class     button-classes
           :tab-index 0
           :on-click  #(do (>evt [::solibri-result-subs/open-result-topics id])
                           (when-not ui-mode-wide?
                             (>evt [::solibri-result-subs/show-results])))}
          [:span.text-wrap name]]
         [:span.text-wrap name])]]
     [render-status-row item]]))

(defn navigation-item [{:keys [name id has-children?] :as item}]
  (let [ui-mode-wide?         (<sub [::solibri-result-subs/ui-mode-wide?])
        opened-topics-rule-id (<sub [::solibri-result-subs/opened-topics-rule-id])
        selected?             (= id opened-topics-rule-id)
        rule-set?             (= (:type item) :rule-set)
        rule?                 (not rule-set?)]
    [:button
     {:class     (concat button-classes
                         nav-box-base-classes
                         nav-box-base-flex-classes
                         (cond
                           (or rule-set? has-children?) ["bg-ui-light-gray"]
                           rule? (if selected?
                                   ["border-2" "border-ui-blue"]
                                   ["border" "border-ui-gray-51"])))
      :tab-index 0
      :on-click  #(do (when has-children?
                        (>evt [::solibri-result-subs/set-navigation-root item]))
                      (>evt [::solibri-result-subs/open-result-topics (when rule? id)])
                      (when-not (or ui-mode-wide? has-children?)
                        (>evt [::solibri-result-subs/show-results])))}
     [:span {:class nav-box-base-flex-classes}
      (when has-children?
        [:span.inline-block
         [:i.lupicon-chevron-right.text-sm.pr-2]])
      [:span.text-wrap name]]
     [render-status-row item]]))

(defn result-navigation []
  (let [{:keys [path items]} (<sub [::solibri-result-subs/navigation-items])
        [selected-item parent & _] (reverse path)
        child-navigation (into [:ul] (for [item items]
                                       [:li [navigation-item item]]))]
    (if selected-item
      [:ul
       [:li
        [navigation-header selected-item parent]
        child-navigation]]
      child-navigation)))

(defn topic-list []
  (let [ui-mode-wide? (<sub [::solibri-result-subs/ui-mode-wide?])
        {:keys [rule topics]} (<sub [::solibri-result-subs/topics])]
    [:<>
     [:span.bg-ui-blue-10 {:class (concat nav-box-base-classes
                                          nav-box-base-flex-classes)}
      [:span {:class nav-box-base-flex-classes}
       (when-not ui-mode-wide?
         [:button
          {:class     (concat button-classes nav-box-back-button-classes)
           :tab-index 0
           :on-click  #(>evt [::solibri-result-subs/hide-results])}
          [:i.lupicon-arrow-left.text-sm.pr-2]])
       [:span
        [:span.text-wrap (:name rule)]]]
      [render-status-row rule]]
     [:div.mb-4
      [:div.mb-1.font-bold (common/loc :model-details-page.results.passed-checked)]
      [:div.mb-1 (->> rule
                      ((juxt (fn [rule] (- (:checkedComponentsAmount rule)
                                           (:failedComponentsAmount rule))) :checkedComponentsAmount))
                      (ss/join " / "))]]
     (into
       [:div]
       (for [{:keys [message components]} topics]
         [:<>
          [:div.mb-4
           [:div.mb-1.font-bold "Kuvaus"]
           [:div message]]
          ;; TODO: border-radius for table - https://www.htmltables.io/blog/html-table-borders
          [:table.reset.mb-4.text-xs.border.border-ui-gray-221.w-full
           [:thead
            [:tr.border.border-ui-gray-221.bg-ui-gray-238
             [:th.p-2.text-left (common/loc :model-details-page.results.component-name)]
             [:th.p-2.text-left "GUID"]]]
           (into
             [:tbody]
             (for [{:keys [guid name]} components]
               (let [copy-guid-text (<sub [::solibri-result-subs/copy-guid-text])]
                 [:tr.border.border-ui-gray-221
                  [:td.p-2.text-ui-blue (or name "-")]
                  [:td.p-2.min-w-20
                   [tooltip-icon-button
                    {:icon         :lupicon-copy
                     :on-mouse-out #(>evt [::solibri-result-subs/reset-copy-guid-text])
                     :on-click     #(>evt [::solibri-result-subs/copy-guid guid])
                     :tooltip-text copy-guid-text}]]])))]]))]))

(defn result-view []
  (if (<sub [::solibri-result-subs/opened-topics-rule-id])
    [:div.p-3.border.border-ui-gray-51
     [topic-list]]
    [:div.text-center (common/loc :model-details-page.results.choose-rule-first)]))

(defn result-table []
  (let [ui-mode-wide?    (<sub [::solibri-result-subs/ui-mode-wide?])
        responsive-width (if ui-mode-wide? "w-1/2" "w-full")
        results-visible? (<sub [::solibri-result-subs/results-visible])]
    ;; NOTE: Could use nobase class to drop old base element styles from Sass styles,
    ;; but the view was written with those enabled so it would break some styles currently.
    [:div.twp.text-sm
     (when ui-mode-wide?
       [result-breadcrumb])
     [:div.flex.mt-3
      (when (or ui-mode-wide? (not results-visible?))
        [:div {:class (cond-> [responsive-width]
                        ui-mode-wide? (into ["pr-2" "border-ui-gray-221" "border-r"]))}
         [result-navigation]])
      (when (or ui-mode-wide? results-visible?)
        [:div {:class (cond-> [responsive-width]
                        ui-mode-wide? (conj "pl-4"))}
         [result-view]])]]))
