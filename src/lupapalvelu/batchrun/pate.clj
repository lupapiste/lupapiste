(ns lupapalvelu.batchrun.pate
  "Pate-related batchrun functionality"
  (:require [com.climate.claypoole :as cp]
            [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict :as verdict]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v])
  (:import [java.time Duration]))

(set! *warn-on-reflection* true)

(defn duration->str
  "String representing `duration` in a friendly format.
  The format is '12h 34m 56s' where each zero part is omitted. Returns nil if the duration
  is less than a second."
  [^Duration duration]
  (let [h   (.toHours duration)
        m   (.toMinutesPart duration)
        s   (.toSecondsPart duration)
        fmt (fn [v u] (when (pos? v)
                        (str v u)))]
    (->> (map fmt [h m s] ["h" "m" "s"])
         (ss/join-non-blanks " ")
         ss/blank-as-nil)))

(defn runtime-string [start]
  (or (duration->str (Duration/between start (date/now)))
      "0s"))

(defn- field-query
  "This might not actually optimize anything, but let's given mongo all the help we can."
  [k vs]
  (case (count vs)
    0 nil
    1 {k (first vs)}
    {k {$in vs}}))

(defn- backend-ids--application
  "Returns true if the `application` is updated."
  [{app-id :id :as application}]
  (log/with-logging-context {:applicationId app-id}
    (when-let [{backend-id :kuntalupatunnus
                vrk-id     :vrk-lupatunnus} (some->> application
                                                     krysp/get-application-xml-by-application-id
                                                     krysp-reader/->backend-ids
                                                     (util/find-first :kuntalupatunnus))]
      (mongo/update-by-id :applications
                          app-id
                          {$push {:verdicts (verdict/backend-id->verdict backend-id vrk-id true)}})
      (log/infof "Backend id update: %s, %s." backend-id vrk-id)
      true)))

(defn- integration-error? [e]
  (boolean (some-> (ex-data e) :text name
                   (ss/starts-with? "error.integration."))))

(defn- build-query
  [{:keys [org-id now permit-types application-ids]}]
  (let [;; Case 1 & 2: The application has been sent to the backing system
        ;; before or after the verdict.
        case-1 {:sent {$ne nil}}
        case-2 {:transfers.type :exported-to-backing-system}
        ;; Case 3 & 4: The application has the correct state
        case-3 {:state {$in states/post-verdict-but-terminal}}
        ;; The `states/r-jatkoaika-state-graph` doesn't have a state
        ;; which matches `states/post-verdict-but-terminal`. The best
        ;; we can do is start polling when the (Pate) verdict is given
        ;; and give the backend some time (90 days) to generate the backend id
        case-4 {:primaryOperation.name {$in [:jatkoaika
                                             :raktyo-aloit-loppuunsaat]}
                :state                 :ready
                ;; The `:ready` state is terminal. Set a time limit for the polling
                ;; to make sure we don't try to process the same application forever
                ;; if no backend is available.
                :verdictDate           {$ne nil
                                        $gt (-> now
                                                (date/minus :days 90)
                                                date/timestamp)}}]
    (merge
      (field-query :_id application-ids)
      (field-query :permitType permit-types)
      {:organization   org-id
       :facta-imported {$ne true}
       :infoRequest    {$ne true}
       :verdicts.0     {$exists false}
       $or             (->> (for [sent-condition  [case-1 case-2]
                                  state-condition [case-3 case-4]]
                              (merge sent-condition
                                     state-condition))
                            (into []))})))

(defn- backend-ids--organization
  "Fetches KuntaGML and adds (pseudo) backing system verdict draft with `kuntalupatunnus`
  and `vrk-lupatunnus`. An application is included if it is in the Pate scope and in
  post-verdict state without any backing system verdicts. The applications can be
  explicitly limited with `application-ids`."
  ([{org-id :id :keys [krysp scope] :as organization} application-ids]
   (let [permit-types  (some->> scope
                                (keep (fn [{pt :permitType pate :pate}]
                                        (when (and (:enabled pate)
                                                   (not (dm-cfg/target-enabled? organization pt))
                                                   (ss/not-blank? (get-in krysp [(keyword pt) :url])))
                                          pt)))
                                set
                                not-empty)
         {user-id :id} (usr/batchrun-user [org-id])
         q             (build-query (util/mmap {:now (date/now)} org-id permit-types application-ids))
         start-time    (date/now)]
     (log/with-logging-context {:userId user-id}
       (if permit-types
         (let [{n :n} (reduce (fn [{:keys [ignored] :as acc}
                                   {pt :permitType app-id :id :as application}]
                                (log/with-logging-context {:applicationId app-id}
                                  (try
                                    (cond-> acc
                                      (and (not (contains? ignored pt))
                                           (backend-ids--application application))
                                      (update :n inc))
                                    (catch Exception e
                                      (log/error "Backend id update failed:" (ex-message e))
                                      (cond-> acc
                                        (integration-error? e)
                                        (update :ignored conj pt))))))
                              {:ignored #{} :n 0}
                              (mongo/select :applications q [:organization :permitType]))]
           (log/infof "Backend id update for %s: Updated %s applications in %s."
                      org-id n (runtime-string start-time)))
         (log/infof "Backend id update for %s: No suitable permit types."
                    org-id)))))
  ([organization]
   (backend-ids--organization organization nil)))

(defn- organization-id?
  "Just to make sure that LX-applications are not mistaken for organizations."
  [s]
  (not (or (ss/blank? s) (v/application-id? s))))

(defn- parse-args! [args]
  (let [args (some->> (seq args)
                      (keep (comp ss/blank-as-nil ss/upper-case ss/trim))
                      distinct)]
    (cond
      (empty? args) nil

      (every? v/lp-application-id? args)
      (or (some->> (mongo/select :applications
                                 (field-query :_id args)
                                 [:organization])
                   seq
                   (group-by :organization)
                   (map (fn [[org-id apps]]
                          {:organization-id org-id
                           :application-ids (map :id apps)})))
          (throw (ex-info "Applications not found." {:application-ids args})))

      (every? organization-id? args)
      (map #(hash-map :organization-id %) args)

      :else
      (throw (ex-info "Bad arguments. The supported arguments are either organization or application ids."
                      {:args args})))))

(defn backend-ids-update
  "Fetches and updates suitable applications. After a successful update, the application
  will have a draft backing system verdict with backend ids.

  `args` can be either empty, organization ids or application ids. If ids are given they
  all must be of the same type. When `args` is empty, every suitable organization is
  processed."
  [& args]
  (try
    (let [start   (date/now)
          targets (parse-args! args)
          orgs    (mongo/select :organizations
                                (assoc (field-query :_id (map :organization-id targets))
                                       :scope.pate.enabled true
                                       :deactivated {$ne true})
                                [:krysp :scope :dmcity])]
      (if (seq orgs)
        (do
          (cp/pdoseq 8 [organization orgs]
                     (->> targets
                          (util/find-by-key :organization-id (:id organization))
                          :application-ids
                          (backend-ids--organization organization)))
          (log/infof "Backend ids updated for %s organizations in %s."
                     (count orgs) (runtime-string start)))
        (log/info "Backend ids update: Organizations not found.")))
    (catch Exception e
      (log/error "Backend ids update failed:" (ex-message e)))))
