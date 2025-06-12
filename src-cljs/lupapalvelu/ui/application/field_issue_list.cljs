(ns lupapalvelu.ui.application.field-issue-list
  "Display a list of issues in the application documents.
   Each issue includes a link to the corresponding field on the target document."
  (:require ["react" :as react]
            [goog.object :as googo]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.document-types :as doc-types]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.field-schemas :as field-schemas]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as docgen-schemas]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [reitit.frontend.easy :as rfe]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]))

(rf/reg-sub ::app-id
  :<- [:route/path-parameters]
  :-> :app-id)

(rf/reg-event-fx ::issues-updated
  (fn [{:keys [db]} [_ issues-per-doc]]
    {:db (assoc db ::issues (reduce (fn [acc {:keys [docId issues]}]
                                      (if (seq issues)
                                        (assoc acc docId (validation/field-errors issues))
                                        acc))
                                    {}
                                    issues-per-doc))}))

(rf/reg-sub ::issues :-> ::issues)

(defschema IssueView (sc/enum :parties :info))

(defschema FieldIssue
  {:error   sc/Str
   :level   sc/Keyword
   :loc-key sc/Str})

(defschema DocIssue
  {:doc-id sc/Str
   :view   IssueView
   :issues {docgen-schemas/Path [FieldIssue]}})

(defschema DocIssues [DocIssue])

(sc/defn merge-issues :- DocIssues
  [doc-ids
   classifier
   fresh-issues
   old-issues]
  (->> doc-ids
       ;; retain original order
       (keep (fn [doc-id]
               (let [issues (or
                              ;; prefer fresh data from `:fetch-validation-errors`
                              ;; if available. Only fetched when user moves between tabs.
                              (get fresh-issues doc-id)
                              ;; and fall back to the latest validation data from docgen
                              (get old-issues doc-id))]
                 (when (seq issues)
                   (util/mmap doc-id issues)))))
       (map (fn [doc-issue]
              (assoc doc-issue :view (classifier (:doc-id doc-issue)))))
       (into [])))

(rf/reg-sub ::classifier
  :<- [::doc-types/party-doc-ids]
  (fn [party-doc-ids]
    (let [party (set party-doc-ids)]
      (fn [doc-id]
        (if (contains? party doc-id)
          :parties
          :info)))))

(rf/reg-sub ::current-issues
  :<- [::doc-types/validated-doc-ids]
  :<- [::classifier]
  :<- [::issues]
  :<- [::validation/validation]
  (fn [[doc-ids classifier fresh-issues old-issues]]
    (merge-issues doc-ids classifier fresh-issues old-issues)))

(defschema PrunedIssue
  {:doc-id sc/Str
   :view   IssueView
   :paths  [{:path    docgen-schemas/Path
             :loc-key sc/Str}]})

(defschema PrunedIssues [PrunedIssue])

(sc/defn prune-by-error :- PrunedIssues
  [doc-issues :- DocIssues
   error-predicate :- (sc/pred fn?)]
  (->> doc-issues
       (keep (fn [{:keys [doc-id view issues]}]
               (let [paths (->> issues
                                (keep (fn [[path field-issues]]
                                        (when (->> field-issues
                                                   (filter error-predicate)
                                                   ;; We only care if there's at least one
                                                   ;; suitable error
                                                   first)
                                          ;; `field-issues` contains the loc key from the backend.
                                          ;; But the backend doesn't understand that the UI labels are
                                          ;; sometimes built using the inherited `i18nkey` from the parent.
                                          ;; -> Consult the schemas as it knows the inherited `i18nkey`
                                          (let [schema  (<sub [::field-schemas/field-schema doc-id path])
                                                loc-key (di/label-loc (util/mmap schema path doc-id))]
                                            (util/mmap path loc-key)))))
                                (into []))]
                 (when (seq paths)
                   (util/mmap doc-id view paths)))))
       (into [])))

(rf/reg-sub ::current-errors
  :<- [::current-issues]
  (fn [m]
    (prune-by-error m (fn [{:keys [error]}]
                        (= "illegal-value:required" error)))))

(rf/reg-sub ::current-warnings
  :<- [::current-issues]
  (fn [m]
    (prune-by-error m (fn [{:keys [level]}]
                        (= :warn level)))))

(defn- subscribe!
  [observable on-change]
  (react/useEffect
    (fn []
      (let [^js sub (.subscribe observable (fn [value]
                                             (on-change (js->clj value :keywordize-keys true))))]
        (on-change (js->clj (.peek observable) :keywordize-keys true))
        (fn []
          (.dispose sub))))
    #js [observable]))

(defn- render-issues
  [{:keys [issues warning?]}]
  (let [app-id (<sub [::app-id])]
    [:<>
     (->> issues
          (map (fn [{:keys [doc-id view paths]}]
                 (let [text (util/kw-path (<sub [::docs/doc-name doc-id])
                                          :_group_label)]
                   ^{:key doc-id}
                   [:<>
                    [:h3.required-field-error-doc
                     (common/loc text)]
                    [:<>
                     (->> paths
                          (map (fn [{:keys [path loc-key]}]
                                 ^{:key (path/path->str path)}
                                 [:div.info-line
                                  {:class (when warning? "warning")}
                                  [:div.like-btn
                                   [:i.lupicon-circle-attention
                                    {:aria-hidden true}]
                                   [:span.required-field-error-element-name
                                    (common/loc loc-key)]]
                                  [:a {:href (rfe/href view
                                                       {:app-id app-id}
                                                       {:focus-doc  doc-id
                                                        :focus-path (path/path->str path)})}
                                   (common/loc :view)]]))
                          doall)]])))
          doall)]))

(defn- render-warnings
  []
  (when-let [issues (not-empty (<sub [::current-warnings]))]
    [:div#applicationWarnings
     [:h2.missing-info-title (common/loc :application.warningsInDocument)]
     [render-issues {:issues   issues
                     :warning? true}]]))

(defn- render-errors
  []
  (when-let [errors (not-empty (<sub [::current-errors]))]
    [:div#applicationRequiredFields
     [:h2.missing-info-title (common/loc :application.requiredDataMissing)]
     [render-issues {:issues   errors
                     :warning? false}]]))

(defn- view*
  [{:keys [issues-obs]}]
  (subscribe! issues-obs (fn [issues-per-doc] (>evt [::issues-updated issues-per-doc])))
  [:<>
   [render-warnings]
   [render-errors]])

(defn- view [args]
  [:f> view* args])

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id params]
  (ui-dom/mount! mounter dom-id {:dom-id     dom-id
                                 :issues-obs (googo/get params "issuesObs")}))
