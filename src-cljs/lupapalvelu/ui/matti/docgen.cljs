(ns lupapalvelu.ui.matti.docgen
  "Rudimentary support for docgen subset in the Matti context."
  (:require [rum.core :as rum]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.common :as common]))

(defn docgen-loc [{:keys [schema path]} & extra]
  (let [{:keys [i18nkey locPrefix]} (-> schema :body first)]
    (-> (cond
          i18nkey   [i18nkey]
          locPrefix (cons locPrefix path)
          :else     path)
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

(defmulti docgen-component docgen-type)

(defmethod docgen-component :default
  [options]
  (println "default options:" options))

(defn thread-log [v]
  (println v)
  v)

(defmethod docgen-component :select
  [{:keys [schema state path] :as options}]
  (let [state (path/state path state)]
    [:select.dropdown
     (docgen-attr options
                  :value     (rum/react state)
                  :on-change (common/event->state state))
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

(defmethod docgen-component :checkbox
  [{:keys [schema state path] :as options}]
  (let [state    (path/state path state)
        input-id (str (path/id path) "input")]
    [:div.matti-checkbox-wrapper (docgen-attr options)
     [:input {:type    "checkbox"
              :checked (rum/react state)
              :id      input-id}]
     [:label.matti-checkbox-label
      {:for      input-id
       :on-click #(swap! state not)}
      (docgen-loc options)]]))

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
                         :on-change (common/event->state text*)
                         :on-blur   (common/event->state state))
            attr)]))

(defmethod docgen-component :string
  [{:keys [schema state path] :as options}]
  (text-edit options :input.grid-style-input {:type "text"}))

(defmethod docgen-component :text
  [{:keys [schema state path] :as options}]
  (text-edit options :textarea.grid-style-input))

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
