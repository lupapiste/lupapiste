(ns lupapalvelu.conversion.other
  "Conversion-adjacent functionality that targets non-converted applications."
  (:require [clojure.set :as set]
            [lupapalvelu.backing-system.dmcity.client :as dm-cl]
            [lupapalvelu.backing-system.dmcity.schemas :as dm-sc]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.conversion.building :as build]
            [lupapalvelu.conversion.cli :as conv-cli]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.review-fix :as review-fix]
            [lupapalvelu.conversion.schemas :refer [other-ts-fields
                                                    OtherConfig]]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.verdict :as verdict]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [swiss.arrows :refer :all]))

(defn- make-query
  ([{:keys [organization-id permit-type application-ids include-converted?]
     :as   cfg} extras]
   (->> (select-keys cfg other-ts-fields)
        (map (fn [[k v]]
               (let [v (flatten [v])
                     k (if (= k :verdict-date) :verdictDate k)
                     m {$gte (date/timestamp (date/start-of-day (first v)))
                        $lte (date/timestamp (date/end-of-day (last v)))}]
                 (if (= :sent k)
                   ;; The application has been sent to the backing system before or after
                   ;; the verdict.
                   [$or [{:sent m}
                         {:transfers {$elemMatch {:type      :exported-to-backing-system
                                                  :timestamp m}}}]]
                   [k m]))))
        (into (cond-> {:organization   organization-id
                       :permitType     permit-type
                       :infoRequest    {$ne true}}
                (seq application-ids)    (assoc :_id {$in application-ids})
                (not include-converted?) (assoc :facta-imported {$ne true})))
        (merge extras)))
  ([cfg]
   (make-query cfg nil)))

(defn- fetch-backend-ids
  "Returns `:kuntalupatunnus`, `:vrk-lupatunnus` map or nil."
  [{:keys [id verdicts] :as application}]
  (try
    (some->> (if-let [backend-id (some :kuntalupatunnus verdicts)]
               (krysp/get-application-xml-by-backend-id application
                                                        backend-id
                                                        false)
               (krysp/get-application-xml-by-application-id application
                                                            false))
             krysp-reader/->backend-ids
             ;; Both ids must be present in the result.
             (util/find-first #(= 2 (count %))))
    (catch Exception e
      (log/errorf "Backend ids fetch for %s failed: %s"
                  id (ex-message e)))))

(defn  backend--vrk-ids
  "Add `vrk-lupatunnus` to applications. If an application does not have `kuntalupatunnus`
  it is ignored."
  [cfg]
  (doseq [{app-id :id
           :as    application} (mongo/select :applications
                                             (make-query cfg
                                                         {:verdicts.kuntalupatunnus {$exists true}
                                                          :verdicts.vrk-lupatunnus  {$exists false}})
                                             [:permitType :verdicts :organization])
          :let                 [{backend-id :kuntalupatunnus
                                 vrk-id     :vrk-lupatunnus} (fetch-backend-ids application)]
          :when                vrk-id]
    (mongo/update-by-query :applications
                           {:_id      app-id
                            :verdicts {$elemMatch {:kuntalupatunnus backend-id}}}
                           (conv-util/with-sheriff-note
                             (str "Other backend-ids: VRK-lupatunnus " vrk-id)
                             {$set {:verdicts.$.vrk-lupatunnus vrk-id}}))))
(defn  backend--both-ids
  "Add `kuntalupatunnus` and `vrk-lupatunnus` to applications that have `sent` timestamp
  but no existing `kuntalupatunnus`."
  [cfg]
  (doseq [{app-id :id
           :as    application} (mongo/select :applications
                                             (cond-> (make-query cfg
                                                                 {:verdicts.kuntalupatunnus {$exists false}})
                                               (nil? (:sent cfg))
                                               (assoc $or [{:sent {$ne nil}}
                                                           {:transfers.type :exported-to-backing-system}]))
                                             [:permitType :verdicts :organization])
          :let                 [{backend-id :kuntalupatunnus
                                 vrk-id     :vrk-lupatunnus} (fetch-backend-ids application)]
          :when                vrk-id]
    (mongo/update-by-id :applications app-id
                        (conv-util/with-sheriff-note
                          (format "Other backend-ids: %s, vrk: %s" backend-id vrk-id)
                          {$push {:verdicts (verdict/backend-id->verdict backend-id vrk-id true)}}))))

(sc/defn ^:always-validate other--backend-ids
  [cfg  :- OtherConfig]
  (backend--vrk-ids cfg)
  (backend--both-ids cfg))

(defn- applications-to-send
  "Returns a lazy sequence of applications selected by `cfg`. Drafts, open and foreman
  applications are excluded."
  [{:keys [application-ids] :as cfg}]
  (let [query (make-query (dissoc cfg :application-ids)
                          {:state                 {$nin dm-sc/skipped-states}
                           :primaryOperation.name {$ne :tyonjohtajan-nimeaminen-v2}})]
    (if-let [application-ids (seq (distinct application-ids))]
      ;; Order is important, let's get the apps one-by-one
      (keep (fn [app-id]
              (mongo/select-one :applications (assoc query :_id app-id)))
            application-ids)
      (mongo/select-ordered :applications query {:_id 1}))))

(defn- other-cfg->client-cfg
  [{:keys [system] :as cfg}]
  {:pre [(map? system)]}
  (dm-cl/client-config system
                       (:organization-id cfg)
                       (select-keys cfg [:force-path])))

(defn process-options
  "Exactly one of the given `options` must be set in `cfg`. Returns `client-cfg` (`Client-Cfg`),
  `option` map or throws."
  [cfg & options]
  {:pre [(map? (:system cfg))]}
  (let [kws (->> (select-keys cfg options)
                 (filter second)
                 (map first))]
    (case (count kws)
      0 (throw (ex-info "No option given." {}))
      1 {:option     (first kws)
         :client-cfg (other-cfg->client-cfg cfg)}
      (throw (ex-info (str "Mutually exclusive options: " (ss/join ", " (map name kws))) {})))))

(sc/defn ^:always-validate other--send-states
  "Sends state-change messages to dmcity based on the target application history. Mutually
  exclusive, mandatory options:

  `all`  Send every state change.
  `last` Send only the last state change."
  [cfg :- OtherConfig]
  (let [{:keys [client-cfg option]} (process-options cfg :all :last)]
    (doseq [application (applications-to-send cfg)]
      (dm-cl/send-messages client-cfg application [(case option
                                                     :all  :all-states
                                                     :last :last-state)]))))

(defn- send-kuntagml--application
  [client-cfg {:keys [state] :as application}]
  (when-not (util/includes-as-kw? states/post-submitted-states state)
    (throw (ex-info (str "Unsupported state: " state) {:state state})))
  (dm-cl/send-messages client-cfg application [:application]))

(defn- send-kuntagml--verdict [client-cfg application]
  (dm-cl/send-messages client-cfg application [:verdicts]))

(defn- send-kuntagml--review
  [client-cfg application]
  (dm-cl/send-messages client-cfg application [:reviews]))

(defn- send-kuntagml--auto
  [client-cfg application]
  (dm-cl/send-messages client-cfg application [:application/verdicts :reviews]))

(sc/defn ^:always-validate other--send-kuntagml
  "Sends KuntaGML messages to dmcity based on the target application history. The message
  types are defined by mutually exclusive, mandatory options: `application`, `verdict`,
  `reviews.` or `auto`."
  [cfg :- OtherConfig]
  (let [{:keys [client-cfg
                option]} (process-options cfg :application :verdict :review :auto)]
    (doseq [application (applications-to-send cfg)
            :let        [send-fn (case option
                                   :application send-kuntagml--application
                                   :verdict     send-kuntagml--verdict
                                   :review      send-kuntagml--review
                                   :auto        send-kuntagml--auto
                                   nil)]
            :when       send-fn]
      (log/with-logging-context {:userId        "batchrun-user"
                                 :applicationId (:id application)}
        (try
          (send-fn client-cfg application)
          (catch Exception e
            (log/errorf "Send %s failed: %s"
                        (name option) (ex-message e))))))))

(sc/defn ^:always-validate other--fix-end-reviews
  "Updates end review (loppukatselmus) building states (in-use, käyttöönottava) according
  to the fetched backing system KuntaGML messages."
  [cfg :- OtherConfig]
  (let [q (make-query cfg
                      {:state {$in [:closed :inUse]}
                       :tasks {$elemMatch {:schema-info.subtype         {$in [:review :review-backend]}
                                           :state                       :sent
                                           :data.katselmuksenLaji.value #"(?i)loppukatselmus"
                                           :data.rakennus.0             {$exists true}}}})]
    (doseq [application (mongo/select :applications q)]
      (review-fix/process-application-review-in-use-fixes application))))

(defn- conversion-document-xml [{:keys [verdicts organization permitType]}]
  (let [bid (some :kuntalupatunnus verdicts)]
    (some-> (conv-doc/find-documents {:organization-id organization
                                      :permit-type     permitType}
                                     {:backend-id bid})
            first
            (conv-doc/read-xml :force? true))))

(defn- fetch-fn-factory [cfg]
  (if (:fetch cfg)
    krysp/get-application-xml-by-application-id
    conversion-document-xml))

(sc/defn ^:always-validate other--fix-review-notes
  "Updates end review (loppukatselmus) building states (in-use, käyttöönottava) according
  to the fetched backing system KuntaGML messages."
  [cfg :- OtherConfig]
  (let [q        (make-query cfg
                             {:verdicts.kuntalupatunnus {$exists true}
                              :tasks                    {$elemMatch {:schema-info.subtype {$in [:review :review-backend]}
                                                                     :state               :sent}}})
        fetch-fn (fetch-fn-factory cfg)]
    (doseq [application (mongo/select :applications q)
            :let        [xml (fetch-fn application)]
            :when       xml]
      (review-fix/process-application-review-note-fixes application xml))))

(defn- make-states [{:keys [states] :or {states [:closed]}}]
  (if (= 1 (count states))
    (first states)
    {$in states}))

(sc/defn ^:always-validate other--fix-missing-reviews
  "Executes review batchrun against closed applications with conversion document or fetched XML."
  [cfg :- OtherConfig]
  (let [q        (make-query cfg
                             {:state                    (make-states cfg)
                              :verdicts.kuntalupatunnus {$exists true}
                              :primaryOperation.name    {$nin ["tyonjohtajan-nimeaminen-v2"
                                                               "suunnittelijan-nimeaminen"]}})
        fetch-fn (fetch-fn-factory cfg)]
    (doseq [application (mongo/select :applications q)
            :let        [xml (fetch-fn application)]
            :when       xml]
      (review-fix/process-application-missing-reviews (:system cfg) application xml))))

(sc/defn ^:always-validate other--vtj-prts
  "Updates missing building ids (`data.valtakunnallinenNumero.value`) by matching
  documents to application `buildings` array."
  [cfg :- OtherConfig]
  (let [vtj-prt-re #"\w+"
        q          (make-query
                     cfg
                     {:state                {$nin [:draft :open :canceled]}
                      ;; At least one building must have a vtj-prt value.
                      :buildings.nationalId vtj-prt-re
                      :documents            {$elemMatch {:data.valtakunnallinenNumero.value {$not vtj-prt-re}
                                                         :schema-info.name                  {$in (build/vtj-prt-schema-names)}}}})]
    (log/with-logging-context {:userId "batchrun-user"}
      (->> (mongo/select :applications q [:buildings :documents])
           (reduce (fn [acc application]
                     (cond-> acc
                       (build/update-document-vtj-prts application)
                       inc))
                   0)
           (log/infof "Other vtj-prts: %s applications updated.")))))

(sc/defn ^:private ^:always-validate other--config :- OtherConfig
  [{:keys [cmd cfgfile system] :as command}]
  {:pre [(map? system)]}
  (-> (merge {:permit-type "R"}
             (some-> cfgfile util/read-edn-file)
             (->> (util/find-by-key :cmd cmd
                                    conv-cli/other-commands)
                  :opts
                  (select-keys command))
             {:system system})

      (set/rename-keys {:include-converted :include-converted?})
      ss/trimwalk
      (util/pcond->
        :application-ids (update :application-ids distinct)
        :force-path (update :force-path #(case %
                                           :a :application
                                           :c :conversion
                                           %)))))

(defn execute-other-command [{:keys [cmd cmd-help] :as command}]
  {:pre [(map? (:system command))]}
  (log/with-logging-context {:other-batchrun cmd}
    (try
      (let [cfg (other--config command)]
        (case cmd
          :backend-ids         (other--backend-ids cfg)
          :send-states         (other--send-states cfg)
          :send-kuntagml       (other--send-kuntagml cfg)
          :fix-end-reviews     (other--fix-end-reviews cfg)
          :fix-review-notes    (other--fix-review-notes cfg)
          :fix-missing-reviews (other--fix-missing-reviews cfg)
          :vtj-prts            (other--vtj-prts cfg)))
      (catch Exception e
        (log/error e)
        (if cmd-help
          (cmd-help command (ex-message e))
          (throw e))))))
