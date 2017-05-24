(ns lupapalvelu.ui.matti.sections
  (:require [rum.core :as rum]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.state :as state]
            [clojure.string :as s]))

(defn extend-path [path id]
  (if id
    (conj path id)
    path))

(defn path-id [path]
  (s/join "-" path))

(defn docgen-loc [{:keys [schema path]} & extra]
  (let [{:keys [i18nkey locPrefix]} (-> schema :body first)
        xs (-> (cond
                 i18nkey [i18nkey]
                 locPrefix (cons locPrefx path)
                 :else path)
               (concat extra))]
    (common/loc (s/join "." (filter identity xs)))))

(defn docgen-label-wrap [{:keys [schema path] :as options} component]
  (if (-> schema :body first :label false?)
    component
    [:div.col--vertical
     [:label {:for (path-id path)} (docgen-loc options)]
     component]))

(defmulti docgen-component #(-> % :schema :body first :type keyword))

(defmethod docgen-component :default
  [options]
  (println "default options:" options))

(defn thread-log [v]
  (println v)
  v)

(defmethod docgen-component :select
  [{:keys [schema state path] :as options}]
  (->> [:select.dropdown
        {:value     (rum/react state)
         :id (path-id path)
         :on-change #(reset! state (.. % -target -value))}
        (->> schema :body first
             :body
             (map (fn [{n :name}]
                    {:value n
                     :text  (common/loc (str "matti.verdict." n))}))
             (sort-by :text)
             (cons {:value ""
                    :text  (common/loc "selectone")})
             (map (fn [{:keys [value text]}]
                    [:option {:key value :value value} text])))]
       (docgen-label-wrap options)))


(defn grid [{:keys [grid state path data] :as options}]
  (letfn [(child-options [col-id schema-name full-schema]
            (state/state-schema-options {:state state
                                         :data data
                                         :path (extend-path path col-id)
                                         :schema schema-name}
                                        full-schema))]
      [:div {:class (str "matti-grid-" (:columns grid))}
    (for [row (:rows grid)]
      [:div.row
       (for [{:keys [col align schema id]} row]
         [:div {:class [(str "col-" (or col 1))
                        (when align (str "col--" (name align)))]}
          (cond
            (string? schema) (->> (service/schema schema)
                                  (child-options id schema)
                                  docgen-component)
            (keyword? schema) "Not supported.")])])]))

(rum/defc section < rum/reactive
  [{:keys [schema id] :as options}]
  [:div [:h4 (common/loc id)]
   (grid (assoc options :path [id]))])
