(ns lupapalvelu.job-api
  (:require [lupapalvelu.action :refer [defquery]]
            [lupapalvelu.job :as job]
            [lupapalvelu.logging :refer [warnf]]
            [sade.core :refer [fail ok]]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [slingshot.slingshot :refer [try+]]))

(def timeout 5000)

(defn job-status
  "Utility for checking the status of job"
  [job-id version]
  (job/status job-id (util/->long version) timeout))

(defquery job-status
  {:description      "Long poll for job updates. At this point for a job to be polled from front,
                      it is required to be associated with an application via :data.application-id."
   :parameters       [id job-id version]
   :input-validators [{:id      ssc/NonBlankStr
                       :job-id  ssc/NonBlankStr
                       :version ssc/IntString}]
   :permissions      [{:required [:application/read]}]}
  [{:keys [user] {:keys [id job-id version]} :data}]
  (try+
    (let [;; TODO: Fix this. This is a hack due to sade.strings/numeric? not allowing strings representing negative numbers
          version (if (= version "-1") -1 version)
          job     (job/find-job job-id)]
      (if (-> job :data :application-id (= id))
        (ok (job-status job-id version))
        (fail :error.unauthorized)))
    (catch [:error :not-found] {:keys [message]}
      (warnf "Job status poll failed: %s" message)
      (fail :error.unauthorized))))

(defquery organization-jobs
  {:description      "Organization jobs that match the parameters. Either returns
  immediately (default) or keeps polling a while depending on the parameters."
   :parameters       [:organizationId]
   :input-validators [job/OrgJobsQuery]
   :permissions      [{:required [:organization/admin]}]}
  [command]
  (ok :jobs (job/query-organization-jobs command)))
