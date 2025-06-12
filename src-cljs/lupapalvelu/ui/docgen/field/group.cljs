(ns lupapalvelu.ui.docgen.field.group
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.group-approval :as group-approval]
            [lupapalvelu.ui.docgen.component.group-info :as group-info]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.edit :as edit]
            [lupapalvelu.ui.docgen.feature.filter-by-code :as filter-by-code]
            [lupapalvelu.ui.docgen.feature.visibility :as visibility]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [sade.util :as util]))

(defn- group-help-text
  [schema]
  ;; In `docmodel.js` groups the help text maps directly to the translation
  (when-let [help (-> schema :group-help)]
    (if-let [css (-> schema :group-help-css)]
      [common/loc-html-reagent (util/kw-path :span css) help]
      [common/loc-html-reagent :span.group-help-text help])))

(defn- can-remove?
  [disabled? schema doc-id override-auth-source]
  (boolean (and (:repeating schema)
                (not disabled?)
                (<sub [::auth/auth? doc-id :remove-document-data override-auth-source]))))

(defn- render-group
  "See `buildGroup` in `docmodel.js`."
  [{:keys [doc-id schema path render-fn override-auth-source disabled?] :as args}]
  (let [id               (path/path->group-id path doc-id)
        {:keys [layout]} schema
        body             (->> (filter-by-code/filter-body (util/mmap schema path doc-id))
                              (visibility/keep-visible-bodies args))
        approvable?      (boolean (:approvable schema))
        removable?       (can-remove? disabled? schema doc-id override-auth-source)
        css              (docgen-common/schema->css schema)]
    [:div
     {:id    id
      :class (cond-> [(if (= :vertical (keyword layout))
                        "form-choice" "form-group")]
               (seq css) (into css))}
     (when (or approvable? removable?)
       [group-approval/render (util/mmap doc-id path override-auth-source approvable? removable?)])
     [group-info/group-info (util/mmap doc-id path body schema)]
     [group-help-text schema]
     [:div
      ;; our `schema` also contains all our children and their schemas
      [render-fn body (dissoc args :schema)]]]))

(rf/reg-event-fx ::append-group-button-clicked
  (fn [{:keys [db]} [_ args]]
    (when-let [new-db (docs/add-group-child db args)]
      {:db new-db})))

(defn- append-button
  [{:keys [doc-id path schema disabled?]}]
  (let [doc-i18n-name         (<sub [::docs/i18n-name doc-id])
        next-idx              (<sub [::docs/next-child-id doc-id path])
        label-loc             (docgen-loc/loc-key-append-label doc-i18n-name path schema)
        ignore-missing-parent (:repeating-init-empty schema)]
    [:button.secondary
     {:id       (path/path->append-id path doc-id)
      :disabled disabled?
      :on-click (fn [_] (>evt [::append-group-button-clicked
                               (util/mmap doc-id path next-idx ignore-missing-parent)]))}
     [:i.lupicon-circle-plus {:aria-hidden true}]
     [:span (common/loc label-loc)]]))

(defn- append-button-group
  [args]
  [:div.button-group
   [append-button args]])

(defn- disable-when-editing?
  [{:keys [schema doc-id]}]
  ;; Handle special case to keep rakennuksenOmistajat (using this schema flag)
  ;; disabled (including the `append` button) on post-verdict edit.
  ;; Setting disabled? flag on docgen args overrides disabled so that ::docs/doc-disabled? isn't checked.
  (and (:post-verdict-editable-disabled? schema)
       (<sub [::edit/editing-doc? doc-id])))

(defn render
  "See `buildGroup` in `docmodel.js`."
  [{:keys [doc-id schema path] :as args}]
  (let [{:keys [repeating]} schema
        args                (cond-> args
                              (disable-when-editing? args) (assoc :disabled? true))]
    (if repeating
      ;; For repeating groups we grab the child indexes and create
      ;; a whole new group element for each with the correct path
      (let [child-indexes (<sub [::docs/field-child-keys doc-id path])]
        [:<> (doall (concat
                      (->> child-indexes
                           (map (fn [child-index] (update args :path conj child-index)))
                           (map (fn [args]
                                  ^{:key (path/path->str (:path args))}
                                  [render-group args])))
                      [^{:key (path/path->str (conj path :append))}
                       [append-button-group args]]))])
      [render-group args])))
