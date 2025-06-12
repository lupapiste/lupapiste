(ns lupapalvelu.ui.docgen.field.docgen-group
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.warnings :as warnings]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.visibility :as visibility]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.group-grid :as gg]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- group-label
  [{:keys [doc-id schema path]}]
  (let [doc-i18n-name (<sub [::docs/i18n-name doc-id])
        loc-key       (docgen-loc/loc-key-group-label doc-i18n-name path schema)]
    [:label.form-label.form-label-group
     (common/loc loc-key)]))

(defn- group-help-text
  [schema path]
  ;; In KO groups the help text is a suffix added to the group path
  (when-let [help (-> schema :group-help)]
    (let [help-path (-> (path/path-without-indexes path)
                        (conj help)
                        path/path->str)]
      (if-let [css (docgen-common/schema->group-help-css schema)]
        [common/loc-html-reagent (util/kw-path :div css) help-path]
        [common/loc-html-reagent :span.group-help-text help-path]))))

;;; Default template

(defn- default-docgen-group-template
  [{:keys [doc-id schema path render-fn] :as args}]
  (let [id             (path/path->group-id path doc-id)
        {:keys [body]} schema]
    [:div.form-group
     {:id id}
     [group-label (util/mmap doc-id schema path)]
     [group-help-text schema path]
     [:div
      (->> body
           (visibility/keep-visible-bodies args)
           (map (fn [subschema] (docgen-loc/apply-parent-i18nkey subschema schema)))
           (map (fn [subschema]
                  ^{:key (path/path->str (conj path (:name subschema)))}
                  [:span.form-entry
                   [render-fn [subschema] (dissoc args :schema)]]))
           doall)]]))

;;; Form Grid template

(def children-shows-warnings
  "Sometimes the direct children of the group can display their own warnings"
  ;; NOTE: technically `:group` could also be included here, but I'm not sure
  ;; if any warnings ever directly target a group (instead of its children).
  #{:table})

(defn- render-row
  [row {:keys [render-fn doc-id doc-i18n-name] :as args}]
  [:<>
   [:div
    (let [warns (->> (:columns row)
                     (keep (fn [{:keys [path schema]}]
                             (when-not (util/includes-as-kw? children-shows-warnings (:type schema))
                               {:schema schema
                                :path   (conj path (keyword (:name schema)))})))
                     (warnings/resolve-warnings (util/mmap doc-id doc-i18n-name)))]
      [warnings/warnings {:warnings warns}])]
   [:div.row
    {:class (:row-css row)}
    (if-let [ltext (:ltext row)]
      [:div {:id    ltext
             :class (:class row)}
       (common/loc ltext)]
      [:div
       (->> (:columns row)
            (map (fn [column]
                   (let [field-name (-> column :schema :name keyword)
                         field-path (conj (:path column) field-name)]
                     ^{:key (path/path->str field-path)}
                     [:div.col--vertical
                      {:class (:col-class column)}
                      [render-fn [(:schema column)] (-> args
                                                        (assoc :path (:path column))
                                                        (dissoc :schema :doc-i18n-name))]])))
            doall)])]])

(defn- form-grid-docgen-group-template
  "Represent the group contents using rows.
  The schema specifies
  * `body` -- The individual child field schemas
  * `rows` -- Which child fields to show and how, organized as rows."
  [{:keys [doc-id schema path] :as args}]
  (let [id            (path/path->group-id path doc-id)
        css           (docgen-common/schema->css schema)
        doc-i18n-name (<sub [::docs/i18n-name doc-id])
        ;; We display rows, which source data from within the `body`.
        ;; Unfortunately, rows might reference fields several layers
        ;; deep (a field within a group within a group..).
        ;; At the moment we don't have the tools to reliably recursive filter
        ;; the `body` so for now just check the top-level body. This should be enough
        ;; for now as we don't have any existing schemas which refer to such fields.
        schema        (update schema :body (fn [body] (visibility/keep-visible-bodies args body)))]
    ;; `form-grid--no-border` also exists, but it also removes padding
    [:div.form-grid.border-0
     {:id    id
      :class css}
     [group-help-text schema path]
     [:div
      (->> (gg/process-rows (util/mmap path schema))
           (map (fn [row]
                  (let [id (or (some->> (:ltext row)
                                        (str "label_"))
                               (->> (:columns row)
                                    (map (fn [{:keys [schema path]}]
                                           (path/path->str (conj path (:name schema)))))
                                    (ss/join "_")))]
                    ^{:key id}
                    [render-row row (assoc args :doc-i18n-name doc-i18n-name)])))
           doall)]]))

;;; Simple group template

(defn- simple-docgen-group-template
  [{:keys [schema render-fn] :as args}]
  (let [{:keys [body]} schema
        body           (mapv (fn [subschema] (docgen-loc/apply-parent-i18nkey subschema schema)) body)]
    [:div
     [render-fn body (dissoc args :schema)]]))

;;; Basic group template

(defn- basic-docgen-group-template
  "Like `simple-docgen-group-template`, but displays the group label & help text."
  [{:keys [doc-id path render-fn schema] :as args}]
  (let [{:keys [body]} schema
        body           (mapv (fn [subschema] (docgen-loc/apply-parent-i18nkey subschema schema)) body)
        css            (docgen-common/schema->css schema)]
    [:div {:class css}
     [:div.form-group
      [group-label (util/mmap doc-id schema path)]
      [group-help-text schema path]
      [render-fn body (dissoc args :schema)]]]))

(sc/defn render
  "See `docgen-group-[template.html,model.js]`."
  [{:keys [schema] :as args} :- schemas/DocgenFieldArguments]
  (let [args (assoc args :prefer-ko-component? true)]
    [:div.inline
     [(case (keyword (:template schema))
        :simple-docgen-group-template           simple-docgen-group-template
        :basic-docgen-group-template            basic-docgen-group-template
        :form-grid-docgen-group-template        form-grid-docgen-group-template
        default-docgen-group-template)
      args]]))
