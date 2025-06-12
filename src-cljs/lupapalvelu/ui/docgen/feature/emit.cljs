(ns lupapalvelu.ui.docgen.feature.emit
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.docgen.feature.documents :as docs]))

(def excluded-events
  "Some emitted events have dedicated processing elsewhere in this new docgen implementation."
  #{:filterByCode ;; See `lupapalvelu.ui.docgen.feature.filter-by-code`
    :accordionUpdate ;; See `lupapalvelu.ui.docgen.component.accordion-toolbar`
    })

(defn schema->emit-data
  "If the given `schema` emits signals, produce a map describing what to emit.

  Note: Historically, only certain fields in `docmodel.js` supported emitting signals."
  [{:keys [schema app-id doc-id]}]
  (when-let [emits (->> schema
                        :emit
                        (map keyword)
                        (remove excluded-events)
                        (into #{})
                        not-empty)]
    {:emits  emits
     :app-id app-id
     :doc    {:id   doc-id
              :name (<sub [::docs/doc-name doc-id])}
     :schema {:name (:name schema)}}))

;; NOTE: Each emitted signal has either 0 or 1 recipients because
;; at the moment each signal has a single use case. Alternatively we could
;; implement a registration mechanism to store listener(s) in an atom.

(defmulti do-emit
  "Return an effect handler function, or nil."
  (fn [emit-data] (:emit emit-data)))

(defmethod do-emit :default
  [{:keys [emit]}]
  (println "Unknown emit:" emit))

(defmethod do-emit :buildingDemolition
  [{:keys [value]}]
  (fn [_cofx _]
    ;; Notify `application-actions-model.js`
    {:fx [[:hub/send {:event :buildingDemolition
                      :data  {:checked (boolean value)}}]]}))

(defmethod do-emit :hetuChanged
  [{:keys [doc]}]
  (fn [_cofx _]
    ;; Notify `foreman-other-applications
    ;; Use different name as the JS version, so this event
    ;; won't be seen by the JS version when dev is running Cljs
    ;; docgen.
    {:fx [[:hub/send {:event :hetuChangedCljs
                      :data  {:docId (:id doc)}}]]}))

(defmethod do-emit :emailChanged
  [_]
  ;; Not used currently
  ;; Used in js docgen for consent component
  nil)

(defn produce-emit-fx
  [cofx [_ {:keys [emit-data value]}]]
  (let [{:keys [emits]} emit-data]
    (nutil/merge-every-fn cofx (->> emits
                                    (keep (fn [emit]
                                            (some-> (do-emit (assoc emit-data :emit emit :value value))
                                                    vector)))
                                    (into [])))))
