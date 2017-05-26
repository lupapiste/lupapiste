(ns lupapalvelu.ui.matti.sections
  (:require [rum.core :as rum]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.path :as path]
            [clojure.string :as s]))

(defn docgen-loc [{:keys [schema path]} & extra]
  (let [{:keys [i18nkey locPrefix]} (-> schema :body first)
        xs (-> (cond
                 i18nkey [i18nkey]
                 locPrefix (cons locPrefix path)
                 :else path)
               (concat extra))]
    (common/loc (->> xs
                     (map name)
                     (filter identity)
                     (s/join ".")))))

(defn docgen-label-wrap [{:keys [schema path] :as options} component]
  (if (-> schema :body first :label false?)
    component
    [:div.col--vertical
     [:label {:for (path/id path)} (docgen-loc options)]
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
  (let [state (path/state path state)]
    (->> [:select.dropdown
         {:value     (rum/react state)
          :id (path/id path)
          :on-change  #(reset! state (.. % -target -value))}
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
        (docgen-label-wrap options))))


(defn grid [{:keys [grid state path] :as options}]
  [:div {:class (str "matti-grid-" (:columns grid))}
   (for [row (:rows grid)]
     [:div.row
      (for [{:keys [col align schema id]} row]
        [:div {:class [(str "col-" (or col 1))
                       (when align (str "col--" (name align)))]}
         (cond
           (string? schema) (docgen-component {:state state
                                               :path (path/extend path id schema)
                                               :schema (service/schema schema)})
           (keyword? schema) "Not supported.")])])])

(rum/defc section < rum/reactive
  [{:keys [state path id] :as options}]
  [:div [:h4 (common/loc id)]
   (grid options)])
