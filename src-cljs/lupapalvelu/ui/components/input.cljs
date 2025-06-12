(ns lupapalvelu.ui.components.input
  "Collection of simple, low-level hiccup/reagent input components.
  Ideally these should not be opinionated wrt. styling etc."
  (:require [clojure.set :as set]
            [goog.functions :as gfunc]
            [goog.object]
            [lupapalvelu.ui.misc]
            [reagent.core :as r]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]]))

(defn- has-type?
  [expected-type]
  (fn [options]
    (= (:input-type options) expected-type)))

(defschema ClassName (sc/cond-pre sc/Str sc/Keyword))

(defschema InputCommonOptions
  (st/merge
    {;; There are a bunch of others
     :input-type (sc/enum :checkbox :radio :password :text :textarea)}
    (st/optional-keys
      {:id                sc/Str
       :data-test-id      sc/Str
       :class             (sc/cond-pre (sc/maybe ClassName) [(sc/maybe ClassName)])
       :title             sc/Str
       :disabled?         sc/Bool
       :read-only?        sc/Bool
       :required?         sc/Bool
       :autofocus?        sc/Bool
       :on-change         (sc/pred fn?)
       :on-blur           (sc/pred fn?)
       :aria-required?    sc/Bool
       :aria-label        sc/Str
       :aria-labelledby   sc/Str
       :aria-invalid?     sc/Bool
       :aria-errormessage sc/Str
       :ref               sc/Any})))

(defschema InputCheckboxOptions
  (st/merge
    (st/dissoc InputCommonOptions :on-change)
    {:checked?      sc/Bool
     :raw-on-change (sc/pred fn?)}))

(defschema InputRadioButtonOptions
  (st/merge
    InputCommonOptions
    {:value    (sc/maybe sc/Str)
     :checked? sc/Bool}))

(defschema InputTextOptions
  (st/merge
    InputCommonOptions
    (st/optional-keys
      {:max-length  sc/Int
       :value       (sc/maybe sc/Str)
       :placeholder (sc/maybe sc/Str)
       :on-key-down (sc/pred fn?)})))

(defschema InputTextAreaOptions
  (st/merge
    InputTextOptions
    (st/optional-keys
      {:rows sc/Int
       :cols sc/Int})))

(defschema InputOptions
  (sc/conditional
    (has-type? :checkbox) InputCheckboxOptions
    (has-type? :radio) InputRadioButtonOptions
    (has-type? :textarea) InputTextAreaOptions
    (some-fn (has-type? :text)
             (has-type? :password)) InputTextOptions
    :else InputCommonOptions))

(sc/defn common-input
  "Simple hiccup input.
  Available options depends on the type of the input."
  [{:keys [input-type on-change raw-on-change on-key-down on-blur]
    :as   opts} :- InputOptions]
  [(if (= :textarea input-type)
     :textarea
     :input)
   (merge {:on-change (or raw-on-change
                          (fn [e] (on-change (util/tval e))))}
          (when (not= :text-area input-type)
            {:type input-type})
          (-> opts
              (dissoc :input-type :on-change :raw-on-change)
              (set/rename-keys {:aria-invalid?  :aria-invalid
                                :aria-required? :aria-required
                                :autofocus?     :autoFocus
                                :checked?       :checked
                                :disabled?      :disabled
                                :max-length     :maxLength
                                :read-only?     :readOnly
                                :required?      :required}))
          (when on-blur
            {:on-blur (fn [e] (on-blur (util/tval e)))})
          (when on-key-down
            {:on-key-down (fn [e] (on-key-down (.-keyCode e)))}))])

(defn- with-debounce
  "Wrapper for an input element, which debounces `on-change` calls.

  Takes a map of options, including `debounce-ms`; the debounce time in milliseconds.

  Input state is kept in an atom. Updating it is much quicker than a full round-trip
  through re-frame, which may discard keystrokes if browser performance is poor.

  Tries to detect if the input `value` changes due to some other component,
  e.g. if the user clicks a 'clear form' button. In such a case the internal state
  is updated to match the external and any pending changes are discarded."
  [inner-component]
  (fn [{:keys [debounce-ms value]}]
    (let [value     (or value "")
          *state    (r/atom {;; Previous `value` from outside
                             :last-value    value
                             ;; Previous value we sent out
                             :out-value     value
                             ;; Current value on the UI
                             :current-value value})
          debounced (gfunc/debounce (fn [callback value]
                                      (swap! *state assoc :out-value value)
                                      (callback value)) debounce-ms)]
      (fn [{:keys [value on-change on-blur] :as opts}]
        (let [value                   (or value "")
              {:keys [last-value
                      out-value
                      current-value]} (deref *state)]
          ;; Check if `value` was changed outside, e.g. user pressed
          ;; a "clear input" button or something in another component.
          (when (and
                  ;; The outside value is changing?
                  (not= last-value value)
                  ;; It does not match what's on the UI currently?
                  (not= current-value value)
                  ;; And it's not the value we previously reported out?
                  ;; E.g. user might start typing just after the `callback`
                  ;; was invoked but before `value` changed and we don't
                  ;; want to revert those changes on the UI.
                  (not= out-value value))
            ;; Assume the outside value has precedence
            (swap! *state assoc :current-value value)
            ;; If there's some earlier outgoing input waiting in the debouncer
            ;; -> clear it.
            (debounced (constantly nil) value))
          (when (not= value last-value)
            (swap! *state assoc :last-value value))
          [inner-component (-> opts
                               (dissoc :debounce-ms)
                               (assoc :value current-value
                                      :on-change (fn [v]
                                                   ;; Immediately update the UI
                                                   (swap! *state assoc :current-value v)
                                                   (when on-change
                                                     (debounced on-change v)))
                                      :on-blur (fn [_]
                                                 (when on-change
                                                   (let [{:keys [current-value out-value]} (deref *state)]
                                                     ;; Flush any pending changes
                                                     (when (not= current-value out-value)
                                                       (swap! *state assoc :out-value value)
                                                       (debounced (constantly nil) current-value)
                                                       (on-change current-value))))
                                                 (when on-blur
                                                   (on-blur current-value)))))])))))

(defn text-input
  "Text input."
  {:arglists '([{:keys [aria-errormessage aria-invalid? autofocus? class data-test-id debounce-ms disabled?
                        id on-change on-key-down placeholder ref required? value]}])}
  [{:keys [debounce-ms]}]
  (let [input (cond-> common-input
                debounce-ms with-debounce)]
    (fn [args]
      [input (assoc args :input-type :text)])))

(defn password-input
  "Password input."
  {:arglists '([{:keys [class data-test-id id on-change placeholder value on-key-down debounce-ms aria-invalid? required? disabled?]}])}
  [{:keys [debounce-ms]}]
  (let [input (cond-> common-input
                debounce-ms with-debounce)]
    (fn [args]
      [input (assoc args :input-type :password)])))

(defn checkbox
  "Checkbox"
  {:arglists '([{:keys [aria-labelledby checked? class data-test-id disabled? id on-change]}])}
  [{:keys [checked? on-change] :as args}]
  [common-input (-> args
                    (dissoc :on-change)
                    (assoc :input-type :checkbox
                           :checked? (boolean checked?)
                           :raw-on-change (fn [e] (on-change (.-checked (.-target e))))))])

(defn radio-button
  "Radio button"
  [args]
  [common-input (assoc args :input-type :radio)])

(defn textarea
  "Textarea input"
  {:arglists '([{:keys [aria-invalid? class cols debounce-ms disabled? id max-length on-blur on-change placeholder read-only? required? rows title value]}])}
  [{:keys [debounce-ms]}]
  (let [input (cond-> common-input
                debounce-ms with-debounce)]
    (fn [args]
      [input (assoc args :input-type :textarea)])))

(defn- group-options-by-optgroup
  "Groups a flat list of options by `:optgroup` flag.
  Items with `:optgroup true` become group headers, subsequent items become their children.
  Items before the first optgroup are standalone options."
  [options]
  (loop [remaining     options
         result        []
         current-group nil]
    (if (empty? remaining)
      (if current-group
        (conj result current-group)
        result)
      (let [{:keys [optgroup] :as opt} (first remaining)
            rest-opts                  (rest remaining)]
        (if optgroup
          ;; Start a new optgroup
          (let [result (if current-group
                         (conj result current-group)
                         result)]
            (recur rest-opts
                   result
                   {:optgroup-label (:label opt) :options []}))
          ;; Regular option
          (if current-group
            ;; Add to current group
            (recur rest-opts
                   result
                   (update current-group :options conj opt))
            ;; Standalone option (no current group)
            (recur rest-opts
                   (conj result opt)
                   nil)))))))

(defn- select-option
  "Renders a single option or an optgroup."
  [{:keys [disabled? label value optgroup-label options]}]
  (if optgroup-label
    [:optgroup {:label optgroup-label :title optgroup-label}
     (for [opt options]
       ^{:key (:value opt)} [select-option opt])]
    [:option (cond-> {:value value
                      :title label}
               disabled? (assoc :disabled true))
     label]))

(defn select
  "Select.

  Arguments:
  * `options` -- Sequence of maps. Each map can be either:
    - An option: `{:value \"val\" :label \"Label\" :disabled? false}`
    - An optgroup header: `{:label \"Group\" :optgroup true}`

  Options following an optgroup header (until the next header) are grouped under it."
  {:arglists '([{:keys [aria-errormessage aria-invalid? aria-label aria-required?
                        class disabled? id on-change ref required? title value]} options])}
  [{:keys [on-change] :as args} options]
  (let [grouped-options (if (some :optgroup options)
                          (group-options-by-optgroup options)
                          options)]
    [:select
     (cond-> (-> args
                 (set/rename-keys {:aria-invalid?  :aria-invalid
                                   :aria-required? :aria-required
                                   :disabled?      :disabled
                                   :required?      :required}))
       on-change (assoc :on-change (comp on-change util/tval)))
     (->> grouped-options
          (map (fn [opt]
                 ^{:key (or (:value opt)
                            (:optgroup-label opt)
                            ;; also handle the "- Valitse -" option
                            (when (nil? (:value opt)) "nil"))}
                 [select-option opt]))
          doall)]))
