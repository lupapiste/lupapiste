(ns lupapalvelu.ui.docgen.field.docgen-input-common
  "Common helpers used by several docgen fields, specifically those
  fields which are based on the original Knockout components."
  (:require [cljs.math :as math]
            [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [sade.util :as util]))

(defn container [{:keys [container-css]}]
  (if container-css
    (util/kw-path :div container-css)
    :div.inline))

(defn docgen-label
  [{:keys [id label label-text class size validation-result-level]}]
  (when (or label-text label)
    [:label
     {:for   id
      :class (->> class
                  (cons validation-result-level)
                  (keep identity)
                  (into ["lux" "form-label" (docgen-common/sizes size)]))}
     (or label-text (common/loc label))]))

(defn docgen-label-empty
  [{:keys [label size]}]
  (when label
    [:label
     {:class ["empty-label" "lux" "form-label" (docgen-common/sizes size)]}]))

(defn label-loc
  [{:keys [schema path doc-id]}]
  (let [doc-i18n-name (<sub [::docs/i18n-name doc-id])
        path-label    (docgen-loc/loc-key doc-i18n-name path schema)
        ;; Some fields (mostly legacy docmodel.js based) have a "<path to field>._group_label"
        ;; translation, even if the field is not a group.
        group-label   (docgen-loc/loc-key-group-label doc-i18n-name path schema)]
    (or (and (common/loc-has-term? path-label) path-label)
        (and (common/loc-has-term? group-label) group-label))))

(defn args->label
  [{:keys [schema path ariaLabel doc-id]}]
  ;; There's also `:i18npath` but it's not used anywhere?
  (let [{:keys [label]} schema
        label           (if (= "false" label) false label)
        real-label      (label-loc (util/mmap schema path doc-id))]
    {:label      (when label real-label)
     :aria-label (or ariaLabel
                     (when (and (string? label) (not= "true" label))
                       label)
                     real-label)}))

(defn schema->input-options
  [{:keys   [max-len placeholder]
    max-val :max
    min-val :min}]
  (cond-> {:max-length  (or max-len 255)
           :placeholder (common/loc (or placeholder :empty))}
    max-val (assoc :max max-val)
    min-val (assoc :min min-val)))

(defn- default-classes
  [input-type]
  (case input-type
    :check-string         ["check-string"]
    :checkbox-wrapper     ["form-input"]
    :date                 []
    :paragraph            ["form-paragraph block"]
    :checkbox             ["inline"]
    :inline-string        ["form-input" "inline"]
    :localized-string     ["form-string"]
    :link-permit-selector ["form-input"
                           ;; Use `lux` (unlike original KO component) for highlighting
                           ;; field errors
                           "lux"]
    :select               []
    :string               ["form-input"]
    :text                 ["form-input" "textarea"]
    :time                 ["lux" "timepicker"]))

(defn input-classes
  ([schema input-type]
   (input-classes schema input-type nil))
  ([{:keys [css layout] :as _schema} input-type validation-result-level]
   (cond-> (or (not-empty css)
               (default-classes input-type))
     validation-result-level (conj validation-result-level)
     layout                  (conj (str "layout--" layout)))))

(defn group-removable?
  "See `docgen-repeating-group-model.js`/`groupsRemovable`."
  [{:keys [disabled? doc-id schema override-auth-source]} & {:keys [ignore-read-only?]}]
  (and (or ignore-read-only?
           (not (some :readonly (:body schema))))
       (not disabled?)
       (let [{remove-cmd :remove} (<sub [::docs/commands doc-id])]
         (<sub [::auth/auth? doc-id remove-cmd override-auth-source]))))

(defn truncate
  "Truncate number to two decimals, if necessary."
  [val]
  (/ (math/round (* 100 val)) 100))
