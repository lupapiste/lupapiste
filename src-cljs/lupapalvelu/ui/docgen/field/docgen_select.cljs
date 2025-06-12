(ns lupapalvelu.ui.docgen.field.docgen-select
  "New `select` component utilizing `reselect`."
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.reselect :as reselect]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.feature.flags :as flags]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
            [lupapalvelu.ui.docgen.feature.select-options :as select-options]
            [lupapalvelu.ui.docgen.feature.source-value :as source-value]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

;; TODO: The styling of this component is a horrible mess because we try to serve
;; both legacy `docmodel.js` -styled containers (`:group`, `:table`..) and
;; the marginally newer `Knockout` -styled containers. Ideally the styling could
;; be simplified once we only have a single container type to support.

(defn- other-option
  [other options-map]
  (let [value (or (:name other) other)]
    {:value value
     :text  (common/loc (get options-map value))}))

(defn- group-items
  [items]
  (->> items
       (reduce (fn [{:keys [grouping?] :as acc} {:keys [header?] :as element}]
                 (cond
                   ;; Starting a new group?
                   header?
                   (-> acc
                       (assoc :grouping? true)
                       (update :result conj (dissoc element :header?)))
                   ;; Continuing a previous group?
                   grouping?
                   (let [n (-> acc :result count dec)]
                     (update-in acc [:result n :items] conj element))
                   :else
                   ;; A plain list of options
                   (update acc :result conj element)))
               {:grouping? false
                :result    []})
       :result))

(defn- sort-items
  [items]
  (->> items
       ;; Sort top-level items
       (sort-by :text)
       ;; Check if we are dealing with grouped items
       (map (fn [{:keys [items] :as element}]
              (if items
                (update element :items (partial sort-by :text))
                element)))))

(defn- options-data
  [{:keys [body other-key sortBy options-map flags]}]
  (let [other-element (and other-key
                           (util/find-by-key :name other-key body))
        options       (cond->> body
                        ;; Sometimes (very rarely) the `body` of the `select` may include
                        ;; the "other" element explicitly -> filter it out.
                        other-element            (filter (fn [element] (not= other-element element)))
                        ;; As per `lupapalvelu.document.schema-validation/Option`, `body` options
                        ;; may include `FlagConditions` but no other visibility control
                        ;; => use `flags/include-schema?` to conditionally hide options
                        true                     (filter (fn [element] (flags/include-schema? element flags)))
                        ;; Pre-process elements and find any option group headers
                        true                     (map (fn [element]
                                                        (let [header? (:optgroup element)
                                                              loc-key (get options-map (:name element))]
                                                          (cond-> {:text (common/loc loc-key)}
                                                            header?       (assoc :header? true :items [])
                                                            (not header?) (assoc :value (:name element))))))
                        ;; Group up options, if necessary
                        true                     group-items
                        (= "displayname" sortBy) sort-items
                        true                     (into []))]
    (if other-key
      ;; Add the "other" option as the last one
      (conj options (other-option (or other-element docgen-common/OTHER) options-map))
      options)))

(rf/reg-sub ::options
  (fn [[_ doc-id path _schema]]
    {:pre [(string? doc-id)]}
    [(rf/subscribe [::select-options/options-map doc-id (path/path-without-indexes path)])
     (rf/subscribe [::flags/schema-flags])])
  (fn [[options-map flags] [_ _doc-id _path {:keys [body other-key sortBy]}]]
    (options-data (util/mmap body other-key sortBy options-map flags))))

(rf/reg-sub ::all-options
  (fn [[_ doc-id path schema]]
    {:pre [(string? doc-id)]}
    [(rf/subscribe [::value/current-field-value doc-id path])
     (rf/subscribe [::docs/i18n-name doc-id])
     (rf/subscribe [::options doc-id path schema])])
  (fn [[value doc-i18n-name options]
       [_ _doc-id path {:keys [valueAllowUnset] :as schema}]]
    (let [i18npath (docgen-loc/loc-key doc-i18n-name path schema)]
      (->> (concat
             (when (docgen-common/unknown-selected? value options)
               [(docgen-common/unknown-value value)])
             (when (not (false? valueAllowUnset))
               [(docgen-common/empty-option i18npath)])
             options)
           (into [])))))

(defn- get-size
  [{:keys [size]}]
  ;; Try to mimic what the various `.form-input.<size>` classes do
  (case size
    "t"  :w-32
    "s"  :w-36
    "m"  :w-96
    "l"  :w-full
    "xl" :w-full
    nil))

(defn- select
  [{:keys [app-id doc-id schema path disabled?] :as args}]
  (let [validation-result-level    (<sub [::validation/field-data-validation-result-level doc-id path])
        can-modify?                (and (not disabled?)
                                        (not (docgen-common/is-read-only? (util/mmap doc-id path schema))))
        input-id                   (path/path->id path doc-id)
        {:keys [label aria-label]} (di/args->label args)
        aria-label                 (when (ss/not-blank? aria-label)
                                     (common/loc aria-label))
        flags                      (docgen-common/aria-flags validation-result-level)
        all-options                (<sub [::all-options doc-id path schema])
        focus-ref                  (focus/use-focus doc-id path)
        source-value-modified?     (<sub [::source-value/source-value-modified? doc-id path])]
    ;; Note that we don't utilize the `(:css schema)` here at all for now
    ;; because the existing classes in the schemas most likely don't apply
    (docgen-common/check-unique path all-options)
    [:div
     ;; TODO: would be better to focus on the select itself
     {:ref      focus-ref
      :tabIndex -1}
     [di/docgen-label {:id                      input-id
                       :label                   label
                       :size                    (:size schema)
                       :validation-result-level validation-result-level}]
     [reselect/reselect
      (<sub [::value/current-field-value doc-id path])
      (cond-> {:class  (->> (concat
                              (di/input-classes schema :select)
                              (when source-value-modified?
                                ["cljs-source-value-changed"])
                              [(get-size schema)])
                            (into []))
               :id     input-id
               :items  all-options
               :multi? false
               :sort?  false}
        ;; React select has a `title` property, but it doesn't seem
        ;; to do anything so we can't add a source value title
        (seq flags)                (merge flags)
        (ss/not-blank? aria-label) (assoc :aria-label aria-label)
        (not can-modify?)          (assoc :disabled? true)
        can-modify?                (assoc :callback (docgen-common/make-on-change-immediate
                                                      (util/mmap {:emit-data (emit/schema->emit-data (util/mmap app-id doc-id schema))}
                                                                 app-id doc-id path))))]
     [ping/ping {:doc-id doc-id :field-path path :mode :icon}]]))

(defn- wrapper
  "Wrapper shim to support both `docmodel.js` and Knockout styled `:group` parents"
  [{:keys [schema prefer-ko-component?]} & children]
  (let [size-class (docgen-common/sizes (:size schema))]
    (into (if prefer-ko-component?
            [(di/container schema)]
            [:span.form-entry {:class [size-class]}])
          children)))

(sc/defn render
  [args :- schemas/DocgenFieldArguments]
  [wrapper args
   [:f> select args]])
