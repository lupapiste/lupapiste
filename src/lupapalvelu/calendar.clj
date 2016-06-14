(ns lupapalvelu.calendar
  (:require [sade.env :as env]
            [sade.strings :as str]))

(defn ui-params []
  (let [m (get (env/get-config) :calendar)]
    ; convert keys to camel-case-keywords
    (zipmap (map (comp keyword str/to-camel-case name) (keys m)) (vals m))))