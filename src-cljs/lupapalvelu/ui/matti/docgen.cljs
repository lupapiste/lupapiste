(ns lupapalvelu.ui.matti.docgen
  "Rudimentary support for docgen subset in the Matti context."
  (:require [rum.core :as rum]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.common :as common]))

(defn docgen-loc [{:keys [schema path]} & extra]
  (let [{:keys [i18nkey locPrefix]} (-> schema :body first)]
    (-> (cond
          i18nkey [i18nkey]
          locPrefix (cons locPrefix path)
          :else path)
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
     [:label {:for (path/id path)} (docgen-loc options)]
     component]))

(defmethod docgen-label-wrap :checkbox
  [_ component]
  component)

(defn docgen-attr [{:keys [path]} & kv]
  (let [id (path/id path)]
    (assoc (apply hash-map kv)
           :key id
           :id id)))

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
                  :on-change  #(reset! state (.. % -target -value)))
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

(defmethod docgen-component :string
  [{:keys [schema state path] :as options}]
  (let [state (path/state path state)]
    [:input.grid-style-input
     (docgen-attr options
                  :type "text"
                  :value (rum/react state)
                  :on-change #(reset! state (.. % -target -value)))]))

(defmethod docgen-component :text
  [{:keys [schema state path] :as options}]
  (let [state (path/state path state)]
    [:textarea.grid-style-input
     (docgen-attr options
                  :value (rum/react state)
                  :on-change #(reset! state (.. % -target -value)))]))
