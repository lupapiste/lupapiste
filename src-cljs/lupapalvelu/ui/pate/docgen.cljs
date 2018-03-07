(ns lupapalvelu.ui.pate.docgen
  "Rudimentary support for docgen subset in the Pate context."
  (:require [clojure.string :as s]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.path :as path]
            [rum.core :as rum]
            [sade.shared_util :as util]))

(defn docgen-loc [options & extra]
  (path/loc options extra))

(defn docgen-type [{schema :schema}]
  (-> schema :body first :type keyword))

(defmulti docgen-label-wrap (fn [options & _]
                              (docgen-type options)))

(defmethod docgen-label-wrap :default
  [{:keys [schema path] :as options} component]
  [:div.col--vertical
   (if (-> schema :body first :label false?)
     (common/empty-label)
     [:label.pate-label {:for (path/id path)
                         :class (common/css-flags :required
                                                  (path/required? options))}
      (docgen-loc options)])
   component])

(defn docgen-attr [{:keys [path state] :as options} & kv]
  (let [id (path/id path)]
    (merge {:key id
            :id  id
            :disabled (path/disabled? options)
            :class (common/css-flags :warning (path/error? options))}
           (apply hash-map kv))))

(defn- state-change
  "Updates state according to event."
  [{:keys [state path] :as options}]
  (let [handler (common/event->state (path/state path state))]
    (fn [event]
      (when (handler event)
        (path/meta-updated options)))))

(defn- state-change-callback
  "Updates state according to value."
  [{:keys [state path] :as options}]
  (fn [value]
    (when (common/reset-if-needed! (path/state path state) value)
      (path/meta-updated options))))

;; ---------------------------------------
;; Components
;; ---------------------------------------

(rum/defc docgen-select < rum/reactive
  [{:keys [schema state path] :as options}]
  (let [local-state (path/state path state)
        sort-fn (if (some->> schema :body first
                             :sortBy (util/=as-kw :displayName))
                  (partial sort-by :text js/util.localeComparator)
                  identity)]
    [:select.dropdown
     (common/update-css (docgen-attr options
                                     :value     (rum/react local-state)
                                     :on-change (state-change options))
                        :required (and
                                   (s/blank? (rum/react local-state))
                                   (path/required? options)))
     (->> schema :body first
          :body
          (map (fn [{n :name}]
                 {:value n
                  :text  (if-let [item-loc (:item-loc-prefix schema)]
                           (path/loc [item-loc n])
                           (docgen-loc options n))}))
          sort-fn
          (cons {:value ""
                 :text  (common/loc "selectone")})
          (map (fn [{:keys [value text]}]
                 [:option {:key value :value value} text])))]))

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
                   [:div.pate-radio-wrapper
                    (docgen-attr {:path radio-path})
                    [:input {:type    "radio"
                             :checked  (= n checked)
                             :value    n
                             :name     (path/id path)
                             :id       radio-id}]
                   [:label.pate-radio-label
                    {:for      radio-id
                     :on-click (fn [_]
                                 (when (common/reset-if-needed! state n)
                                   (path/meta-updated options)))}
                    (docgen-loc options n)]]))))]))


;; ---------------------------------------
;; Component dispatch
;; ---------------------------------------

(defn docgen-component [options]
  (case (docgen-type options)
    :select     (docgen-select options)
    ;;:checkbox   (docgen-checkbox options)
    :radioGroup (docgen-radio-group options)))

;; ---------------------------------------
;; Docgen view components
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
    [:span.pate-checkbox (docgen-attr options)
     (docgen-loc options)]))
