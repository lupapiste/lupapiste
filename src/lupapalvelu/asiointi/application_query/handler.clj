(ns lupapalvelu.asiointi.application-query.handler
  (:require [lupapalvelu.asiointi.http-util :as http-util]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.logging :as log]
            [lupapalvelu.ryhti.api.validation :as validation]
            [ring.util.response :as resp]))

(defn application-as-ryhti
  "Transform `application` into a Ryhti representation using `coercer`.
   Returns a HTTP response."
  [{:keys [application coercer]}]
  {:pre [(map? application) (fn? coercer)]}
  (let [options   {:bim-models (->> (linked-files/find-many-linked-files-with-metadata
                                      {:application-id (:id application)
                                       ;; Ignore inspection reports etc.
                                       :target-type    "bim-model-file"})
                                    (into []))}
        ryhti-app (validation/application->ryhti nil
                                                 application
                                                 options)]
    (try
      (resp/response (coercer ryhti-app))
      (catch Exception e
        (log/errorf "Failed to coerce %s: %s"
                    (:id application)
                    (-> e ex-data :data :explain :errors))
        http-util/internal-error))))
