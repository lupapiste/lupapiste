(ns lupapalvelu.ui.matti.service
  (:require [lupapalvelu.ui.common :as common]))

(defonce schemas (atom {}))

(defn fetch-schemas []
  (common/query "schemas" (fn [res]
                            (reset! schemas (:schemas res)))))
(defn schema
  ([schema-name version]
   (get-in @schemas [(-> version str keyword) (keyword schema-name)]))
  ([schema-name]
   (schema schema-name 1)))
