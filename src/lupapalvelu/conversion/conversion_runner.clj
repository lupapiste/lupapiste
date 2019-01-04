(ns lupapalvelu.conversion.conversion-runner
  "A namespace that provides an endpoint for the KuntaGML conversion pipeline."
  (:require [taoensso.timbre :refer [info infof warn error errorf]]
            [clojure.walk :refer [keywordize-keys]]
            [sade.core :refer [now]]
            [lupapalvelu.conversion.kuntagml-converter :as conv]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.user :as usr]))

(defn- vakuustieto? [kuntalupatunnus]
  (-> kuntalupatunnus conv-util/destructure-permit-id :tyyppi (= "VAK")))

#_(defn convert!
  "Takes a list of kuntalupatunnus-ids."
  [kuntalupa-ids]
  (let [vakuus-ids (filter vakuustieto? kuntalupa-ids)
        others (filter (complement vakuustieto?) kuntalupa-ids)
        running-user (usr/get-user-by-email "sonja.sibbo@sipoo.fi")] ;; TODO: Change!
    ;; Phase 1. Convert applications and save to db.
    (do
      (doseq [id others]
        (try
          (conv/fetch-prev-local-application! {:created (now)
                                               :data {:kuntalupatunnus id}
                                               :user running-user})
          (catch Exception e
            (info (.getMessage e)))))
    ;; Phase 2. Add vakuustieto notification to the verdict of their main application.
    ;; Phase 3. Update app-links - they should be linked by LP id, not kuntalupatunnus
