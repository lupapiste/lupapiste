(ns lupapalvelu.conversion.conversion-runner
  "A namespace that provides an endpoint for the KuntaGML conversion pipeline."
  (:require [taoensso.timbre :refer [info infof warn error errorf]]
            [clojure.java.io :as io]
            [clojure.walk :refer [keywordize-keys]]
            [sade.core :refer [now]]
            [sade.strings :as ss]
            [lupapalvelu.application :as app]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.conversion.kuntagml-converter :as conv]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.user :as usr]))

(defn- vakuustieto? [kuntalupatunnus]
  (-> kuntalupatunnus conv-util/destructure-permit-id :tyyppi (= "VAK")))

(defn update-links!
  "This is a separate function so that it can be run as a separate process if needed.
  This step must be run only after all the applications have been imported."
  [& kuntalupa-ids]
  (when kuntalupa-ids
    (info (str "Updating links from kuntalupatunnus -> Lupapiste-id for " (count kuntalupa-ids) " applications."))
    (doseq [id kuntalupa-ids]
      (app/update-app-links! id))))

(defn convert!
  "Takes a list of kuntalupatunnus-ids."
  [& kuntalupa-ids]
  (when kuntalupa-ids
    (let [vakuus-ids (filter vakuustieto? kuntalupa-ids)
          others (filter (complement vakuustieto?) kuntalupa-ids)
          running-user (usr/get-user-by-email "sonja.sibbo@sipoo.fi")] ;; TODO: Change!
      ;; Phase 1. Convert applications and save to db.
      (info (str "Converting " (count others) " applications from Krysp -> Lupapiste."))
      (doseq [id others]
        (try
          (conv/fetch-prev-application! {:created (now)
                                         :data {:kuntalupatunnus id}
                                         :user running-user}
                                        true) ;; The last flag determines if we fetch local applications or not. TODO: Switch to false for production!
          (catch Exception e
            (info (.getMessage e)))))
      ;; Phase 2. Add vakuustieto notification to the verdict of their main application.
      (info (str "Adding vakuustieto notifications to " (count vakuus-ids) " applications."))
      (doseq [id vakuus-ids]
        (try
          (->> {:id id :organization "092-R" :permitType "R"}
               krysp-fetch/get-application-xml-by-application-id
               conv-util/add-vakuustieto!)
          (catch Exception e
            (info (.getMessage e)))))
      ;; Phase 3. Update app-links - they should be linked by LP id, not kuntalupatunnus
      (update-links! kuntalupa-ids))))

(defn- take-testset
  "Development time helper function that returns a list of random kuntalupa-ids from the batch available locally."
  [amount]
  (let [files (->> conv-util/config
                   :resource-path
                   io/file
                   file-seq
                   (filter (memfn isFile))
                   (map (comp #(first (ss/split % #"\.")) (memfn getName)))
                   (filter #(not= "---" (conv-util/normalize-permit-id %))))]
    (take amount (shuffle files))))
