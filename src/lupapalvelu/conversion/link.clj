(ns lupapalvelu.conversion.link
  "Code for the batchrun that establishes the app-links after the conversions
  themselves are done."
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.user :as user]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.validators :as validators]
            [taoensso.timbre :refer [infof warn warnf]]))

(defn- get-converted-apps [organization-id re-link?]
  (mongo/select :conversion (cond-> {:converted    true
                                     :organization organization-id}
                              (not re-link?)
                              (assoc :linked {$ne true}))))

(defn- get-proper-link-ids
  "Tries to find an LP-id (an application already in Lupapiste) but falls back to the permit id as is.
  Returns nil if none found"
  [organization-id app-links]
  (some->> app-links
           (map #(or (conv-util/get-lp-id-for-permit-id organization-id %)
                     %))
           (filter mongo/valid-key?)
           seq))

(defn- clean-up-links
  "Removes the kuntalupatunnus app-links from the application in preparation
  for re-linking the converted app so no incorrect links linger"
  [app-id]
  (mongo/remove-many
    :app-links
    {$and [{:link app-id}
           {:link {$elemMatch {$not validators/application-id-pattern}}}]}))

(defn- link-apps
  "Links the two Lupapiste apps if able, falls back to adding a link to the kuntalupatunnus if not"
  [link-id LP-id]
  (when-not (app/are-linked? LP-id link-id)
    (if-let [link-app (mongo/by-id :applications link-id)]
      (app/do-add-link-permit link-app LP-id)
      (app/do-add-link-permit (mongo/by-id :applications LP-id) link-id))))

(defn- linkable-type?
  "Do not link both the foreman app to the base permit and vice versa"
  [backend-id]
  (try
    (->> backend-id conv-util/destructure-permit-id :tyyppi #{"TJO" "AJ" "BJ" "CJ" "DJ"} nil?)
    (catch Exception e
      (warnf "Failed to check for backend-id type: %s %s" backend-id (.getMessage e)))))

(defn link-converted-files
  "The top level link batchrun function.
  If `re-link?` is true, the batchrun will ignore the `linked` attribute on the conversion and
  replace any existing links with the new ones. This is used to fix the previous incorrect link
  batchruns.
  Any app-links between two LP-ids are not removed!
  These can be (and mostly are) linked by humans and so cannot be found in conversion data."
  [organization-id re-link?]
  (let [conversions   (get-converted-apps organization-id re-link?)
        batchrun-user (user/batchrun-user [organization-id])]
    (doseq [{:keys [id LP-id app-links backend-id]} conversions]
      (logging/with-logging-context {:applicationId LP-id :userId (:id batchrun-user)}
        (when (linkable-type? backend-id)
          (when-let [proper-link-ids (get-proper-link-ids organization-id app-links)]
            (infof "Linking %s -> %d applications (%s)" LP-id (count proper-link-ids) (ss/join ", " proper-link-ids))
            (when re-link?
              (clean-up-links LP-id))
            (doseq [link-id proper-link-ids]
              (when-not (= link-id LP-id)
                (try
                  (link-apps link-id LP-id)
                  (mongo/update-by-id :conversion id {$set {:linked true}})
                  (catch Exception e
                    (warn (.getMessage e))))))))))))
