(ns lupapalvelu.ui.rum-util
  (:require [rum.core :as rum]
            [lupapalvelu.common.hub :as hub]))

(defonce unique-id (atom 0))
(defn derived-atom
  "Wraps rum.core/derived-atom. Generates key-unique key if not given"
  ([refs f]
   (let [key (keyword *ns* (str "lupaderived" (swap! unique-id inc)))]
     (derived-atom refs key f)))
  ([refs key f]
   (rum/derived-atom refs key f)))

(defn hubscribe
  ([eventName callback]
   (hubscribe eventName {} callback))
  ([eventName params callback]
   {:init         (fn [state _]
                    (let [result (if (fn? params)
                                   (params state)
                                   params)]
                      (update state
                              ::hubscriptions
                              conj
                              (hub/subscribe (assoc result
                                               :eventType eventName)
                                             (partial callback state)))))
    :will-unmount (fn [state] (doseq [subscription (::hubscriptions state)]
                                (hub/unsubscribe subscription)))}))

(defn map-with-key [f coll]
  (map-indexed (fn [idx elem] (rum/with-key (f elem) idx)) coll))
