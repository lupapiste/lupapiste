(ns lupapalvelu.ui.matti.docgen
  "Rudimentary support for docgen subset in the Matti context."
  (:require [rum.core :as rum]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.matti.shared :as shared]))

(defn docgen-loc [{:keys [schema path state]} & extra]
  (let [{:keys [i18nkey locPrefix]} (-> schema :body first)
        loc-prefix                  (or locPrefix
                                        (shared/parent-value schema :loc-prefix))]
    (-> (cond
          i18nkey    [i18nkey]
          loc-prefix (flatten (concat [loc-prefix] [(last path)]))
          :else      path)
        (concat extra)
        path/loc)))

(defn docgen-type [{schema :schema}]
  (-> schema :body first :type keyword))

(defmulti docgen-label-wrap (fn [options & _]
                              (docgen-type options)))

(defmethod docgen-label-wrap :default
  [{:keys [schema path] :as options} component]
  (if (-> schema :body first :label false?)
    component
    [:div.col--vertical
     [:label.matti-label {:for (path/id path)}
      (docgen-loc options)]
     component]))

(defmethod docgen-label-wrap :checkbox
  [_ component]
  component)

(defn docgen-attr [{:keys [path]} & kv]
  (let [id (path/id path)]
    (assoc (apply hash-map kv)
           :key id
           :id  id)))

(defn- state-change [{:keys [state path] :as options}]
  (let [handler (common/event->state (path/state path state))]
    (fn [event]
      (when (handler event)
        (path/meta-updated options)))))

;; ---------------------------------------
;; Components
;; ---------------------------------------

(rum/defc docgen-select < rum/reactive
  [{:keys [schema state path] :as options}]
  (let [local-state (path/state path state)]
    [:select.dropdown
     (docgen-attr options
                  :value     (rum/react local-state)
                  :on-change (state-change options))
     (->> schema :body first
          :body
          (map (fn [{n :name}]
                 {:value n
                  :text  (docgen-loc options n)}))
          (sort-by :text)
          (cons {:value ""
                 :text  (common/loc "selectone")})
          (map (fn [{:keys [value text]}]
                 [:option {:key value :value value} text])))]))

(rum/defc docgen-checkbox < rum/reactive
  [{:keys [schema state path] :as options}]
  (let [state    (path/state path state)
        input-id (str (path/id path) "input")]
    [:div.matti-checkbox-wrapper (docgen-attr options)
     [:input {:type    "checkbox"
              :checked (rum/react state)
              :id      input-id}]
     [:label.matti-checkbox-label
      {:for      input-id
       :on-click (fn [_]
                   (swap! state not)
                   (path/meta-updated options))}
      (when-not (false? (:label schema))
        (docgen-loc options))]]))

(rum/defc docgen-radio-group < rum/reactive
  [{:keys [schema state path] :as options}]
  (let [state (path/state path state)
        checked (rum/react state)]
    [:div
     (->> schema
          :body first :body
          (map (fn [{n :name}]
                 (let [radio-path (path/extend path n)
                       radio-id   (str (path/id radio-path) "radio")]
                   [:div.matti-radio-wrapper
                    (docgen-attr {:path radio-path})
                    [:input {:type    "radio"
                             :checked  (= n checked)
                             :value    n
                             :name     (path/id path)
                             :id       radio-id}]
                   [:label.matti-radio-label
                    {:for      radio-id
                     :on-click (fn [_]
                                 (when (common/reset-if-needed! state n)
                                   (path/meta-updated options)))}
                    (docgen-loc options n)]]))))]))


(rum/defcs text-edit < (rum/local "" ::text)
  {:key-fn (fn [_ {path :path} _ & _] (path/id path))}
  "Update the options model state only on blur. Immediate update does
  not work reliably."
  [local-state {:keys [schema state path] :as options} tag & [attr]]
  (let [text* (::text local-state)
        state (path/state path state)]
    (common/reset-if-needed! text* @state)
    [tag
     (merge (docgen-attr options
                         :value     @text*
                         :on-change identity ;; A function is needed
                         :on-blur   (state-change options))
            attr)]))


;; ---------------------------------------
;; Component dispatch
;; ---------------------------------------

(defn docgen-component [options]
  (case (docgen-type options)
    :select     (docgen-select options)
    :checkbox   (docgen-checkbox options)
    :radioGroup (docgen-radio-group options)
    :string     (text-edit options :input.grid-style-input {:type "text"})
    :text       (text-edit options :textarea.grid-style-input)))

;; ---------------------------------------
;; Multimethods
;; ---------------------------------------


(defmulti docgen-view docgen-type)

(defmethod docgen-view :default
  [{:keys [schema state path] :as options}]
  [:span.formatted (docgen-attr options)
   (rum/react (path/state path state))])

(defmethod docgen-view :select
  [{:keys [schema state path] :as options}]
  [:span (docgen-attr options)
   (when-let [v (not-empty (rum/react (path/state path state)))]
     (docgen-loc options v))])

(defmethod docgen-view :checkbox
  [{:keys [schema state path] :as options}]
  (when (rum/react (path/state path state))
    [:span.matti-checkbox (docgen-attr options)
     (docgen-loc options)]))
