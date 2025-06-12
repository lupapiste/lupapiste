(ns lupapalvelu.ui.docgen.field.common
  "Common helpers used by several docgen fields"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.approvals :as approvals]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.command :as command]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.feature.whitelist :as whitelist]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.strings :as ss]
            [sade.util :as util]))

(def OTHER "other")

(defn other-selected?
  "Has the user chosen the `Muu` option?"
  [v]
  (= OTHER v))

(defn unknown-selected?
  [value options]
  ;; NOTE: Fix compared to the JS version:
  ;; if the currently selected option isn't one of the valid options,
  ;; display an option "Unknown: ID"
  (and value
       (not (or (other-selected? value)
                (->> options
                     ;; unwrap any grouped options
                     (mapcat (fn [{:keys [items] :as option}]
                               (or items [option])))
                     (map :value)
                     (some (fn [v] (= v value))))))))

(defn unknown-value
  "Produce a 'unknown value' select option"
  [value]
  {:value value
   :text  (str (common/loc "select-missing") ": " value)})

(defn other-value
  "Produce a 'other value' (`Muu`) select option"
  []
  {:value OTHER
   :text  (common/loc "select-other")})

(defn empty-option
  ([]
   (empty-option nil))
  ([i18npath]
   (let [custom-term (when i18npath (str i18npath ".select"))
         label       (if (and custom-term (common/loc-has-term? custom-term))
                       custom-term
                       :selectone)]
     {:value ""
      :text  (common/loc label)})))

(defn is-read-only?
  "See `isInputReadOnly()` in `docmodel.js`."
  [{:keys [doc-id path schema]}]
  (boolean
    (or (:readonly schema)
        (= :disabled (<sub [::docs/field-whitelist-action doc-id path]))
        (and (:readonly-after-sent schema)
             (= :sent (<sub [::docs/document-state doc-id]))))))

(defn- at-least-one-permitted?
  [doc-id override-auth-source actions]
  (some (fn [action]
          (<sub [::auth/auth? doc-id (keyword action) override-auth-source]))
        actions))

(defn- disabled-due-to-action?
  "Schemas may specify e.g. `:auth {:enabled [:foo :bar]}` so that particular
  actions dictate whether the field is disabled or not."
  [doc-id {{:keys [enabled disabled]} :auth} override-auth-source]
  (or (and (seq disabled)
           ;; If any of the actions is permitted => disable field!
           (at-least-one-permitted? doc-id override-auth-source disabled))
      (and (seq enabled)
           ;; If none of the actions are permitted => disable field!
           (not (at-least-one-permitted? doc-id override-auth-source enabled)))))

(defn is-disabled?
  [{:keys [doc-id override-auth-source disabled? schema]}]
  (boolean
    (or disabled? ;; Whole view (or document) is disabled?
        ;; Whole document disabled or update forbidden?
        (<sub [::docs/doc-disabled? doc-id override-auth-source])
        (whitelist/schema-disabled? schema (<sub [::app/permit-type]))
        (disabled-due-to-action? doc-id schema override-auth-source))))

(def default-input-debounce-ms
  "The default debounce time in milliseconds used on (text) inputs."
  300)

(def sizes {"t"  :tiny
            "s"  :short
            "m"  :medium
            "l"  :long
            "xl" :really-long})

(defn schema->css
  "Extract the `css` property from the schema.
  Returns a (possibly empty) vector of keywords, or `nil`."
  [{:keys [css]}]
  (when css
    (cond
      (coll? css)               (mapv keyword css)
      (and (string? css)
           (ss/not-blank? css)) [(keyword css)]
      (keyword? css)            [css])))

(defn schema->group-help-css
  "Extract the `group-help-css` property from the schema.
  Returns a (possibly empty) vector of keywords, or `nil`."
  [{:keys [group-help-css]}]
  (schema->css {:css group-help-css}))

(defn aria-flags
  [validation-result-level & {:keys [error-message]}]
  (let [required? (= :tip validation-result-level)
        invalid?  (and (not required?)
                       validation-result-level
                       (not= :tip validation-result-level))]
    (cond-> {}
      required?                    (assoc :aria-required? true :required? true)
      invalid?                     (assoc :aria-invalid? true)
      (and invalid? error-message) (assoc :aria-errormessage error-message))))

(defn entry-span-data
  "Produce hiccup options for an entry span.
  Separate function, because sometimes the callers might want to read some of the properties."
  [{:keys [schema secondary-field-hidden? radio-group? extra-classes]}]
  (let [{schema-type :type
         :keys       [size
                      layout
                      aria-label
                      vertical
                      show-when
                      hide-when]} schema
        size-class                (sizes size)
        text?                     (= "text" schema-type)
        show-or-hide?             (or show-when hide-when)
        css                       (schema->css schema)]
    (merge
      {:class (cond-> (cond
                        text?               ["form-entry" "form-full-width"]
                        layout              ["form-entry" (str "form-" layout) size-class]
                        :else               ["form-entry" size-class])
                (seq css)               (into css)
                secondary-field-hidden? (conj "docgen-element-hidden")
                show-or-hide?           (conj "toggle-when")
                (and radio-group?
                     vertical)          (conj "verticalRadioGroup")
                (and radio-group?
                     (not vertical))    (conj "radioGroup")
                (seq extra-classes)     (into extra-classes))}
      (when radio-group?
        {:role "radiogroup"})
      (when aria-label
        {:aria-label (common/loc aria-label)}))))

(defn entry-span
  "A wrapper span for input fields"
  [options & children]
  (into [:span (entry-span-data options)]
        children))

(defn make-label-data
  "Produce hiccup options for a label.
  Separate function, because sometimes the callers might want to read some of the properties."
  [{:keys [class doc-id hidden? input-id object-i18n-key parent-path parent-type
           schema title use-aria-label? validation-result-level]}]
  (let [{:keys [i18nkey labelclass]} schema
        path                         (cond-> parent-path
                                       (#{:table :group :select} parent-type) (conj :_group_label))
        loc-key                      (or object-i18n-key
                                         i18nkey
                                         (->> (concat [(<sub [::docs/i18n-name doc-id])]
                                                      path)
                                              path/path-without-indexes))
        label-text                   (common/loc (util/kw-path loc-key))]
    {:class       (or class ;; Override everything else
                      (cond-> ["lux" (str "form-label-" (name parent-type)) validation-result-level]
                        labelclass (conj labelclass)))
     :id          (path/path->label-id parent-path doc-id)
     :for         input-id
     :title       title
     :label-text  label-text
     :aria-hidden hidden?
     :aria-label  (when use-aria-label?
                    label-text)}))

(defn make-label
  "A label for input fields"
  {:arglists '([{:keys [class doc-id hidden? input-id object-i18n-key parent-path parent-type
                        schema title use-aria-label? validation-result-level]}])}
  [options]
  (let [{:keys [label-text] :as data} (make-label-data options)]
    [:label (-> data
                (dissoc :label-text)
                ;; In rare cases the L10N label contains HTML tags
                (assoc :dangerouslySetInnerHTML (r/unsafe-html label-text)))]))

(defn max-length
  "Maximum length for text (area) input fields."
  [schema]
  (int (or (:max-len schema) js/LUPAPISTE.config.inputMaxLength)))

(defn make-wrapper-data
  [{:keys [class-prefix classes label-opts]}]
  (let [class-prefix (or class-prefix "blockbox")]
    (cond-> {:class (into [(str class-prefix "-wrapper")]
                          classes)}
      (seq label-opts) (assoc :label-opts (assoc label-opts
                                                 :hidden? true
                                                 :use-aria-label? true
                                                 :class (str class-prefix "-label"))))))

(defn make-wrapper
  "See `makeWrapper`."
  {:arglists '([{:keys [class-prefix classes label-opts]}])}
  [options & children]
  (let [wrapper-data    (make-wrapper-data options)
        wrapper-options (dissoc wrapper-data :label-opts)
        label-opts      (:label-opts wrapper-data)]
    (into [:div wrapper-options]
          (cond-> (vec children)
            (seq label-opts) (conj (into [make-label label-opts]
                                         (:children label-opts)))))))

(rf/reg-event-fx ::on-save-success
  (fn on-save-success-fn [cofx [_ resp {:keys [doc-id field-paths values emit-data extra-effects]}]]
    {:pre [(string? doc-id) (vector? field-paths) (vector? values) (map? resp)]}
    (let [[resp-update-paths resp-update-values] (apply mapv vector (:updates resp))
          resp-update-paths                      (mapv path/integerize-path resp-update-paths)
          fns (cond-> [;; Commit the pending changes
                       [committed/commit-changed-fields doc-id resp-update-paths resp-update-values]
                       ;; Even if the update was ok, the backend might report problems with other fields
                       [validation/commit-field-errors doc-id resp]
                       ;; Document (and group) approval state may change when fields are modified
                       [approvals/update-approvals-fx resp (util/mmap doc-id)]]
                ;; Show positive indicator on form after a successful save
                ;; NOTE: Not all fields show the `ping` indicator; perhaps we could omit
                ;; this effect if we know that?
                (= 1 (count field-paths))
                (conj [ping/show-ping-fx {:doc-id     doc-id
                                          :field-path (first field-paths)
                                          :status     :ok}])
                ;; Some fields emit signals when values change
                emit-data
                (conj [emit/produce-emit-fx (util/mmap {:value (first values)}
                                                       emit-data)])
                ;; Optional extra effects for special cases
                extra-effects
                (into extra-effects))]
      (nutil/merge-every-fn cofx fns))))

(rf/reg-event-fx ::on-save-error
  (fn [cofx [_ resp {:keys [doc-id field-paths values]}]]
    (nutil/merge-every-fn cofx [;; Commit the pending changes, even though the value did not go through
                                [committed/commit-changed-fields doc-id field-paths values]
                                [validation/commit-field-errors doc-id resp]
                                ;; Show error ping
                                (when (= 1 (count field-paths))
                                  [ping/show-ping-fx {:doc-id     doc-id
                                                      :field-path (first field-paths)
                                                      :status     :err}])])))

(defn make-on-change
  "Helper for constructing generic `on-change` handlers for inputs.
  Writes down that a field was modified, but does not send the update to the server."
  [{:keys [doc-id path]}]
  (fn [value]
    (>evt [::value/fields-edited {:doc-id      doc-id
                                  :field-paths [path]
                                  :values      [value]}])))

(defn make-on-blur
  "Helper for constructing generic `on-blur` handlers for inputs.
  Sends the modified value to the sever using the `:update-doc` command."
  [{:keys [app-id doc-id path backend-value emit-data]}]
  (fn [value]
    ;; Save the field only if we think our current value does not
    ;; match the value in the backend.
    (when (not= (ss/blank-as-nil value)
                (ss/blank-as-nil backend-value))
      (let [callback-args (util/assoc-when {:app-id      app-id
                                            :doc-id      doc-id
                                            :field-paths [path]
                                            :values      [value]}
                                           :emit-data emit-data)]
        (>evt [::command/update-doc (assoc callback-args
                                           :on-success [::on-save-success callback-args]
                                           :on-error [::on-save-error callback-args])])))))

(defn change-immediate-fx
  [cofx [_ {:keys [app-id doc-id field-paths on-error on-success values]}]]
  (nutil/merge-every-fn cofx
                        [;; Also update the field so that the UI updates immediately
                         [value/fields-edited-fx
                          (util/mmap doc-id field-paths values)]
                         ;; Send update command to backend
                         [command/update-doc-fx
                          (util/mmap app-id doc-id field-paths values on-success on-error)]]))

(rf/reg-event-fx ::change-immediate
  change-immediate-fx)

(defn make-on-change-immediate
  "Helper for constructing generic immediate `on-change` handlers for checkboxes, selects."
  [{:keys [app-id doc-id path emit-data extra-effects]}]
  (fn [value]
    (let [callback-args (util/assoc-when {:app-id      app-id
                                          :doc-id      doc-id
                                          :field-paths [path]
                                          :values      [value]}
                                         :emit-data emit-data
                                         :extra-effects extra-effects)]
      (>evt [::change-immediate (assoc callback-args
                                       :on-success [::on-save-success callback-args]
                                       :on-error [::on-save-error callback-args])]))))

(defn check-unique
  "Warn if `options` to a `select` are non-unique."
  [path options]
  (when ^boolean js/goog.DEBUG
    (let [labels (->> options
                      (map :text)
                      (into #{}))]
      (when (not= (count labels) (count options))
        (.warn js/console "Warning:" (path/path->str path) "has non-unique options:" (pr-str labels))))))

(rf/reg-event-fx ::copy-last-row
  (fn [{:keys [db] :as cofx} [_ {:keys [app-id doc-id path next-idx ignore-paths extra-data]}]]
    {:pre [(string? app-id) (string? doc-id) (vector? path) (int? next-idx)]}
    ;; Locally create a new destination row
    (when-let [new-db (docs/add-group-child db (util/mmap doc-id path next-idx))]
      (let [;; Original data received from the backend for the source row, if any
            data-paths      (docs/get-field-paths db {:doc-id doc-id
                                                      :path   (conj path (dec next-idx))})
            ;; Local changes to the source row, if any
            committed-paths (committed/get-committed db {:doc-id doc-id
                                                         :path   (conj path (dec next-idx))})
            row-idx         (count path)
            ;; The resulting new destination row, pointing to the new path
            total-paths     (cond->> (merge data-paths committed-paths extra-data)
                              ;; Sometimes we want to restrict what we copy, e.g. ignore readonly fields
                              (seq ignore-paths) (remove (fn [[k _v]]
                                                           (contains? ignore-paths (path/path-without-indexes k))))
                              true               (map (fn [[k v]]
                                                        [(update k row-idx inc) v]))
                              true               (into {}))
            field-paths     (into [] (keys total-paths))
            values          (into [] (vals total-paths))
            callback-args   {:app-id      app-id
                             :doc-id      doc-id
                             :field-paths field-paths
                             :values      values}]
        (nutil/merge-every-fn
          (assoc cofx :db new-db)
          [;; The source row might be empty
           (when (seq values)
             [command/update-doc-fx
              (assoc callback-args
                     :on-success [::on-save-success callback-args]
                     :on-error [::on-save-error callback-args])])])))))

(rf/reg-event-fx ::remove-document-data.success
  (fn [_ [_ _resp app-id]]
    {:fx [[:application/load app-id]]}))

(rf/reg-event-fx ::remove-button-clicked
  (fn [_ [_ {:keys [app-id doc-id path]}]]
    {:fx [[:dialog/show {:ltitle   :document.delete.header
                         :ltext    :document.delete.message
                         :size     :medium
                         :type     :yes-no
                         :callback (fn [_]
                                     (>evt [::command/remove-document-data
                                            (util/mmap {:path       path
                                                        :on-success [::remove-document-data.success app-id]}
                                                       app-id doc-id)]))}]]}))
