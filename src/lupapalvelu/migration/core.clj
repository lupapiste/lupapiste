(ns lupapalvelu.migration.core)

(defonce migrations (atom []))

(defmacro defmigration [migration-name & body]
  `(swap! migrations conj {:name migration-name
                           :f (fn [] @~body)}))
