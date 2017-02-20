 (ns lupapalvelu.ui.rum-util
   (:require [rum.core :as rum]))

(defonce unique-id (atom 0))
(defn derived-atom
   "Wraps rum.core/derived-atom. Generates key-unique key if not given"
   ([refs f]
     (let [key (keyword *ns* (str "lupaderived" (swap! unique-id inc)))]
       (derived-atom refs key f)))
   ([refs key f]
     (rum/derived-atom refs key f)))

 (defn hubscribe
   [eventName params callback]
   {:init (fn [state _]
            (let [result (if (fn? params) (params state) params)]
              (update state
                      :hubscriptions
                      conj
                      (.subscribe js/hub
                                  (clj->js (assoc result :eventType eventName))
                                  (partial callback state)))))
    :will-unmount (fn [state] (doseq [subscription (:hubscriptions state)] (.unsubscribe js/hub subscription)))})
