(ns lupapalvelu.migration.main
  (:require [lupapalvelu.migration.migration :as migration]
            [lupapalvelu.system.system-util :as system-util]))

(defn -main [& [action & args]]
  (system-util/with-mongo
    (cond
      (nil? action)              (migration/show-help)
      (= action "list")          (migration/list-migrations)
      (= action "hist")          (migration/show-history (= "-l" (first args)))
      (= action "run")           (if (seq args)
                                   (migration/run-migrations! args)
                                   (migration/rtfm))
      (= action "update")        (migration/update!)
      (= action "count")         (migration/count!)
      (= action "update-or-die") (migration/update-or-die!)
      :else                      (migration/rtfm))))
