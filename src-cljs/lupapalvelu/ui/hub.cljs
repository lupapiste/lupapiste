(ns lupapalvelu.ui.hub)

(defn send
  ([event]
   (send event nil))
  ([event data]
   (.send js/hub (clj->js event) (clj->js data))))

(defn subscribe
  ([event listener-fn]
   (subscribe event listener-fn false))
  ([event listener-fn one-shot]
   (.subscribe js/hub (clj->js event) listener-fn one-shot)))

(defn unsubscribe [hub-id]
  (.unsubscribe js/hub hub-id))
