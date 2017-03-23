(ns lupapalvelu.ui.hub
  (:require [clojure.string :as string]))

(defn send
  ([event]
   (send event nil))
  ([event data]
   (.send js/hub (clj->js event) (clj->js data))))

(defn subscribe
  ([event listener-fn]
   (subscribe event listener-fn false))
  ([event listener-fn one-shot]
   (.subscribe js/hub (clj->js event) one-shot)))

(defn unsubscribe [hub-id]
  (.unsubscribe js/hub hub-id))
