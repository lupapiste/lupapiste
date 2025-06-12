(ns lupapalvelu.http.noir.util
  (:require [lupapalvelu.system.util-components :as components]
            [noir.request :as request]))

(defn get-system
  "Get current system (components) when within a noir handler."
  []
  {:pre  [(map? (request/ring-request))]
   :post [(map? %)]}
  (-> (request/ring-request)
      :system))

(defn hostname
  "Get current hostname when within a noir handler."
  []
  (-> (get-system)
      components/system->host))
