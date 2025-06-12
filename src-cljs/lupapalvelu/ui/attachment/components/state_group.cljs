(ns lupapalvelu.ui.attachment.components.state-group
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.attachment.filters :as att-filters]
            [lupapalvelu.ui.attachment.grouping :as att-grouping]
            [lupapalvelu.ui.attachment.shared :as shared]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as components]))

(defn- download-button [attachments]
  (let [with-versions (filter (fn [{:keys [versions]}] (-> versions empty? not)) attachments)
        ac            (count with-versions)]
    (when (> ac 0)
      [:div [components/icon-button {:class    :tertiary
                                     :on-click #(.downloadAttachments js/lupapisteApp.services.attachmentsService
                                                                      (clj->js (map :id with-versions)))
                                     :icon     :lupicon-download
                                     :text     (str (loc "download") " "
                                                    ac " "
                                                    (loc (if (= ac 1) "file" "file-plural-partitive")))}]])))

(defn- attachment-group
  "Renders given attachments in separate tables according to their main group (yleiset
  hankkeen liitteet, operation etc.)  and sub groups (pääpiirustukset, muut suunnitelmat
  etc.) Sub groups don't always exist and in those cases attachments are listed under the
  main group."
  [{:keys [group-display download? filter-set-key]} attachment-ids]
  (let [attachments (<sub [::shared/attachments-for-ids attachment-ids])
        tag-groups  (-> (.tagGroups js/lupapisteApp.services.attachmentsService)
                        (js->clj :keywordize-keys true)
                        att-grouping/convert-tag-groups)]
    [:div
     (doall
       (for [[main-group & sub-groups] tag-groups]
         (let [main-group-attachments    (when (empty? sub-groups)
                                           (att-grouping/attachments-by-group attachments
                                                                              [main-group]))
               all-sub-group-attachments (if (empty? main-group-attachments)
                                           (reduce (fn [acc [sg]]
                                                     (let [atts (att-grouping/attachments-by-group attachments
                                                                                                   [main-group sg])]
                                                       (if (empty? atts)
                                                         acc
                                                         (assoc acc sg atts))))
                                                   {}
                                                   sub-groups)
                                           {})]
           (when (or (not-empty main-group-attachments)
                     (not-empty all-sub-group-attachments))
             [:div
              {:key main-group}
              [:div.dsp--flex.flex--between.flex--align-center.gap--b1.flex--wrap
               [:h2.gap--b0.gap--t1 (att-grouping/group-loc main-group)]
               [att-filters/refresh-button filter-set-key]
               (when download?
                 [download-button main-group-attachments])]
              (when (not-empty main-group-attachments)
                [group-display main-group-attachments])
              (doall
                (for [[sub-group] (->> sub-groups
                                       (sort-by (comp (partial att-grouping/type-sort
                                                               att-grouping/type-group-order)
                                                      first)))
                      :let        [sub-group-attachments (->> (all-sub-group-attachments sub-group)
                                                              (sort-by (comp (partial att-grouping/type-sort
                                                                                      att-grouping/type-order)
                                                                             :type-id
                                                                             :type)))]]
                  (when (not-empty sub-group-attachments)
                    [:div
                     {:key sub-group}
                     [:div.dsp--flex.flex--between.flex--align-center.gap--b1.flex--wrap
                      [:h3.gap--b0 (att-grouping/group-loc sub-group)]
                      (when download?
                        [download-button sub-group-attachments])]
                     [group-display sub-group-attachments]])))]))))]))

(defn attachment-state-group [{:keys [title-loc] :as options} attachment-ids]
  (when (not-empty attachment-ids)
    [:div.attachment-state-group
     [:h1 (str (loc title-loc) " (" (count attachment-ids) ")")]
     [attachment-group options attachment-ids]]))
