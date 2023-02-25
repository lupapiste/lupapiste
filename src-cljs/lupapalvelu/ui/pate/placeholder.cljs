(ns lupapalvelu.ui.pate.placeholder
  "In Pate, a placeholder is a component that displays data differently
  than a 'traditional' model-based (internal state) based
  component. The name refers to the scenario, where as a placeholder
  the displayed data is initially calculated and only fixed after
  verdict (e.g., application id). Recently, this namespace is a
  collection of various components that are needed for the
  implmentation of :placeholder schemas (see pate/shared_schemas.cljc)."
  (:require [clojure.string :as s]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defmulti placeholder (fn [options & _] (-> options :schema :type)))

;; Neighbors value is a list of property-id, timestamp, state maps. It is received from the backend and only
;; shown in the verdict drafts (not in pdf).
(defmethod placeholder :neighbors [{:keys [state path]}]
  [:div.tabby.neighbor-states
   (map (fn [{:keys [property-id created state]}]
          [:div.tabby__row.neighbor
           [:div.tabby__cell.property-id (js/util.prop.toHumanFormat property-id)]
           [:div.tabby__cell
            (common/loc (util/kw-path :neighbor.state state))
            " "
            (when-not (util/=as-kw state :open)
              (js/util.finnishDateAndTime created "D.M.YYYY HH:mm"))]])
        (path/value path state))])

(defmethod placeholder :application-id [_] [:span.formatted (rum/react state/application-id)])

(defmethod placeholder :building [{:keys [state path]}]
  (let [{:keys [operation building-id tag description]} (path/value (butlast path) state)]
    [:span.formatted (->> [(path/loc :operations operation)
                           (s/join ": " (remove s/blank? [tag description]))
                           building-id]
                          (remove s/blank?)
                          (s/join " \u2013 "))]))

(defmethod placeholder :statements [{:keys [state path]}]
  (if-let [statements (seq (path/value path state))]
    [:div.tabby.statements
     (map (fn [{:keys [given text status]}]
            [:div.tabby__row.statement
             [:div.tabby__cell text]
             [:div.tabby__cell (when given
                                 (js/util.finnishDate given))]
            [:div.tabby__cell (if status
                                (path/loc :statement status)
                                (path/loc :application.statement.requested))]])
          statements)]
    [:span (path/loc :pate.no-statements)]))
