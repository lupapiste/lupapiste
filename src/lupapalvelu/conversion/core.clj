(ns lupapalvelu.conversion.core
  "A namespace that provides an endpoint for the KuntaGML conversion pipeline."
  (:require [com.climate.claypoole :as cp]
            [lupapalvelu.backing-system.dmcity.client :as dm-cl]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.conversion.cleaner :as cleaner]
            [lupapalvelu.conversion.config :as conv-cfg]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.import.core :as conv-im]
            [lupapalvelu.conversion.kuntagml-converter :as conv]
            [lupapalvelu.conversion.link :as conv-link]
            [lupapalvelu.conversion.location :as conv-xy]
            [lupapalvelu.conversion.proxy :as conv-p]
            [lupapalvelu.conversion.schemas :refer [ConversionConfiguration
                                                    ConversionDocument
                                                    ResolvedTarget Target]
             :as conv-sc]
            [lupapalvelu.conversion.state :as conv-st]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.logging :as log :refer [error errorf info]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema.core :as sc]
            [swiss.arrows :refer :all]
            [sade.env :as env]))

(sc/defn  ^:private ^:always-validate process-conversion-doc :- (sc/maybe ConversionDocument)
  "Whether to proceed with conversion, if the document already exists, depends on the
  context. If the converted application exists the `overwrite?` option is required AND the
  application must be cleanable. Returns either updated `conversion-doc` or nil. Can throw, if
  cleaning fails."
  [{:keys [organization-id overwrite? municipality]} :- ConversionConfiguration
   {app-id :LP-id
    :keys  [backend-id]
    :as    conversion-doc} :- ConversionDocument]
  (let [application? (when app-id
                       (mongo/any? :applications {:_id app-id}))]
    (try
      (cond
        (and app-id application? overwrite?)
        (try
          (cleaner/clean-conversion! conversion-doc)
          (catch Exception e
            (conv-doc/set-conversion-error conversion-doc {:code      :clean-failed
                                                           :exception e})))

        (and app-id application?)
        (errorf "Backend id %s already converted to %s. No overwrite? option, no conversion."
                backend-id app-id)

        app-id conversion-doc

        :else
        (if-let [application-ids (conv-util/duplicate-candidate-ids organization-id
                                                                    backend-id)]
          (conv-doc/set-conversion-error conversion-doc
                                         {:code :duplicate-backend-id
                                          :text (format "Application(s) with backend-id %s already exist: %s. No conversion."
                                                        backend-id (ss/join ", " application-ids))})
          (conv-doc/resolve-application-id conversion-doc municipality)))
      (catch Exception e
        (conv-doc/set-conversion-error conversion-doc {:exception e})))))

(sc/defn ^:private resolve-conversion-target-documents :- [ConversionDocument]
  [{:keys [overwrite? retry] :as cfg} :- ConversionConfiguration]
  (->> (merge {:kuntagml.error   {$exists false}
               :kuntagml.xml     {$exists true}
               :conversion-error {$exists (boolean retry)}}
              (when-not overwrite?
                {:converted {$ne true}}))
       (conv-doc/find-documents cfg)
       (keep (partial process-conversion-doc cfg))))

(sc/defn conversion--convert
  [{:keys [organization-id threads] :as cfg} :- ConversionConfiguration]
  (let [batchrun-user (conv-util/conversion-user organization-id)
        created       (now)
        organization  (org/get-organization organization-id)]
    (log/with-logging-context {:userId (:id batchrun-user)}
      (cp/pdoseq (or threads 8)
                 [{backend-id :backend-id
                   app-id     :LP-id
                   :as        conversion-doc} (resolve-conversion-target-documents cfg)]
                 (try
                   (log/with-logging-context {:userId        (:id batchrun-user)
                                              :applicationId app-id
                                              :backendId     backend-id}
                     (conv/run-conversion! (assoc (select-keys cfg [:organization-id :municipality
                                                                    :force-terminal-state?
                                                                    :location-overrides :location-fallback
                                                                    :state-overrides :state-mapping
                                                                    :system])
                                                  :user batchrun-user
                                                  :created created
                                                  :organization organization
                                                  :conversion-doc conversion-doc)))
                   (catch Exception e
                     (conv-doc/set-conversion-error conversion-doc {:exception e})))))))

(sc/defn conversion--send
  "Sends messages to dmcity backing system.

  Message type selection options:

  `state-changes?` if true, the state-change messages are sent. Some early states (e.g.,
  draft) are skipped.

  `last-state?` if true, only the state-change message for the LAST application history
  state is sent.

  Note: the two options are mutually exclusive.

  `kuntagml?` if true, the files listed in the `cfgfile` are augmented with
  the (conversion result) application id and sent."
  [{:keys [organization-id system]
    :as   cfg}    :- ConversionConfiguration
   state-changes? :- sc/Bool
   last-state?    :- sc/Bool
   kuntagml?      :- sc/Bool]
  {:pre [(map? system)]}
  (let [state-flag-n (count (filter identity [state-changes? last-state?]))]
    (cond
      (> state-flag-n 1)
      (throw (ex-info "State-change options are mutually exclusive." {}))

      (not (or kuntagml? (pos? state-flag-n)))
      (throw (ex-info "No options given, nothing to send." {}))

      :else
      (let [client-cfg (dm-cl/client-config system organization-id)
            ops        (cond-> []
                         state-changes? (conj :all-states)
                         last-state?    (conj :last-state)
                         kuntagml?      (conj :application))]
        (doseq [{app-id :LP-id
                 :as    conversion-doc} (conv-doc/find-converted-documents
                                          cfg
                                          nil
                                          :exclude-foremen
                                          :target-order
                                          :lp-id-order)
                :let                    [application (mongo/by-id :applications app-id)]
                :when                   application]
          (dm-cl/send-messages (cond-> {:cfg client-cfg :application application}
                                 kuntagml? (assoc :xml (conv-doc/xml-with-application-id conversion-doc)))
                               ops)
          (conv-doc/upsert-document :send conversion-doc))))))

(sc/defn ^:always-validate make-bad-conversion-removal-target
  :- (sc/maybe ResolvedTarget)
  "Conversion is deemed erroneous and thus a removal candidate if it either already had an
  Lupapiste application id in its KuntaGML message OR its municipality id is a duplicate
  within mongo for the same organization."
  [{:keys [organization-id] :as cfg} :- ConversionConfiguration
   target :- Target]
  (try
    (when-let [{:keys [id xml-application-id conversion-doc]
                :as   target} (apply conv-cfg/resolve-target cfg (first target))]
      (when conversion-doc
        (when (or xml-application-id
                  ;; If no applications for the backend-id is found, we assume that the
                  ;; conversion has failed earlier and thus is not a bad one.
                  (conv-util/duplicate-candidate-ids organization-id
                                                     id
                                                     (:LP-id conversion-doc)))
          target)))
    (catch Exception e
      (error e "Bad target"))))

(sc/defn ^:always-validate conversion--clean
  [cfg      :- ConversionConfiguration
   dry-run? :- sc/Bool
   force?   :- sc/Bool]
  (let [rm-opts (util/strip-nils {:delete-conversion-document? true
                                  :force?                      force?})]
    (doseq [{:keys [conversion-doc]} (keep (partial make-bad-conversion-removal-target cfg)
                                           (:targets cfg))]
      (info (when dry-run? "Dry run, no changes.")
            "Remove bad conversion"  (:id conversion-doc))
      (if dry-run?
        (info "Can be deleted:" (cleaner/can-be-deleted? conversion-doc rm-opts))
        (try
          (cleaner/clean-conversion! conversion-doc rm-opts)
          (catch Exception e
            (info (ex-message e))))))))

(sc/defn ^:always-validate conversion--nuke
  [cfg      :- ConversionConfiguration
   dry-run? :- sc/Bool]
  (when-not (env/feature? :conversion-nuke)
    (throw (ex-info "Nuke command not supported." cfg)))
  (let [rm-opts {:delete-conversion-document? true
                 :nuke?                       true}]
    (doseq [{:keys [converted]
             :as   conversion-doc} (conv-doc/find-documents cfg)]
      (log/with-logging-context {:applicationId (:LP-id conversion-doc)}
        (info (when dry-run? "Dry run, no changes.")
              "Nuke conversion"  (:id conversion-doc))
        (cond
          (and dry-run?
               (not converted)) (info "No application, delete the document only.")
          dry-run?              (info "Application can be deleted:"
                                      (cleaner/can-be-deleted? conversion-doc rm-opts))
          (not converted)       (cleaner/delete-conversion-document! (:id conversion-doc))
          :else                 (try
                                  (cleaner/clean-conversion! conversion-doc rm-opts)
                                  (catch Exception e
                                    (info (ex-message e)))))))))

(defn conversion--backend-ids [cfg]
  (when-let [m (some->> (conv-doc/find-converted-documents
                          cfg {:kuntagml.xml {$exists true}})
                        seq
                        (map #(vector (:LP-id %) (select-keys % [:kuntagml])))
                        (into {}))]
    (let [target-ids (mongo/select :applications
                                   {:_id                     {$in (keys m)}
                                    :organization            (:organization-id cfg)
                                    :facta-imported          true
                                    ;; If verdicts exist, we assume that
                                    ;; `kuntalupatunnus` exists as well.
                                    :verdicts.vrk-lupatunnus {$exists false}}
                                   [:verdicts])]
      (info "Found" (count target-ids) "converted applications with missing backend ids.")
      (doseq [{app-id   :id
               verdicts :verdicts} target-ids
              :let                 [{:keys [kuntalupatunnus vrk-lupatunnus]
                                     :as   backend-ids} (some->> (conv-doc/read-xml (get m app-id))
                                                                 krysp-reader/->backend-ids
                                                                 first)]
              :when                backend-ids]
        (log/with-logging-context {:applicationId app-id}
          (info "Update application" app-id "with backend ids" backend-ids)
          (if (and (seq verdicts) vrk-lupatunnus)
            (mongo/update-by-query :applications
                                   {:_id      app-id
                                    :verdicts {$elemMatch {:kuntalupatunnus kuntalupatunnus}}}
                                   {$set {:verdicts.$.vrk-lupatunnus vrk-lupatunnus}})
            (conv/store-backend-ids app-id backend-ids)))))))

(defn- conversion--store-kuntagml
  "Upserts conversion document with KuntaGML message. The message is either read from a
  file or fetched from the backing system. If the document already has the message, it
  can be overwritten with `overwrite?` configuration option. Note that legacy documents
  might have been fully converted without having the message."
  [{:keys [organization-id targets overwrite? threads] :as cfg}]
  (cp/pdoseq (or threads 2)
             [{:keys [filename conversion-doc
                      id n]} (map-indexed (fn [i target]
                                            (assoc (apply conv-cfg/resolve-target cfg (first target))
                                                   :n (inc i)))
                                          targets)
              :let           [xml (some-> conversion-doc :kuntagml :xml)]
              :when          (or (not xml) overwrite?)]
             (try
               (if-not id
                 (error "No backend-id found in" filename)
                 (let [{:keys [xml]
                        :as   kuntagml}       (or (some->> filename slurp (hash-map :xml))
                                                  (if (conv-p/use-proxy?)
                                                    (conv-p/fetch-xml-via-proxy organization-id id)
                                                    (conv-util/fetch-xml organization-id id)))
                       {x-error  :error
                        x-app-id :application-id
                        x-id     :backend-id} (some->> (ss/blank-as-nil xml)
                                                       ss/->inputstream
                                                       conv-util/find-xml-ids)
                       make-error             (fn [code text]
                                                (cond-> {:conversion-error {:code code :text text}
                                                         :kuntagml         {:error text}}
                                                  (ss/not-blank? xml) (assoc-in [:kuntagml :xml] xml)))
                       fields                 (cond
                                                (ss/blank? xml)
                                                (make-error :no-kuntagml "No KuntaGML message found")

                                                x-error
                                                (make-error :bad-kuntagml x-error)

                                                (not= x-id id)
                                                (make-error :bad-kuntagml
                                                            "No matching backend-id found in the KuntaGML message.")

                                                (not (conv-sc/supported-backend-id? id))
                                                (make-error :unsupported-backend-id
                                                            "Unsupported backend-id")

                                                x-app-id
                                                (make-error :xml-application-id
                                                            (str "KuntaGML already refers to " x-app-id))

                                                :else {:kuntagml kuntagml}) ]

                   (conv-doc/upsert-document :store
                                             (util/assoc-when (merge fields
                                                                     (select-keys cfg [:organization-id :tags]))
                                                              :backend-id id
                                                              :n n
                                                              :document conversion-doc))))
               (catch Exception e
                 (errorf "Storing backend-id %s document failed: %s"
                         id (ex-message e))))))

(defn- conversion--store-location
  "Updates an existing conversion document with the location information from the
  configuration `:location-overrides`. Overwrites existing location with `overwrite?`
  option."
  [{:keys [organization-id overwrite? location-overrides]}]
  (when-let [ids (some-> location-overrides keys seq)]
    (doseq [{:keys [backend-id]
             :as   document} (mongo/select :conversion
                                           (cond-> {:organization organization-id
                                                    :backend-id   {$in ids}}
                                             (not overwrite?)
                                             (assoc :location {$exists false})))]
      (conv-doc/upsert-document :location
                                {:document document
                                 :location (get location-overrides backend-id)}))))

(sc/defn conversion--store
  "Upserts conversion document with KuntaGML message and/or location. The message is either read from a
  file or fetched from the backing system. If the document already has the message, it
  can be overwritten with `overwrite?` configuration option. Note that legacy documents
  might have been fully converted without having the message. Also the application
  location is stored if provided by `:location-overrides` in the configuration"
  [cfg :- ConversionConfiguration]
  (conversion--store-kuntagml cfg)
  (conversion--store-location cfg))

(sc/defn ^:always-validate conversion--tag
  "Updates tags and renumbers conversion documents. Specific options (all optional):

   `add` New tags.
   `remove` Tags to be removed.
   `number` Renumbers documents. The numbering starts from `number`."
  [cfg :- ConversionConfiguration
   {:keys [add remove number]
    :as   options} :- {(sc/optional-key :add)    [ssc/NonBlankStr]
                       (sc/optional-key :remove) [ssc/NonBlankStr]
                       (sc/optional-key :number) ssc/PosInt}]
  (when (or (not-empty add) (not-empty remove) number)
    (doall
      (map-indexed (fn [i doc]
                     (conv-doc/upsert-document :tag
                                               (cond-> (assoc (select-keys options [:add :remove])
                                                              :document doc)
                                                 number (assoc :n (+ number i)))))
                   (conv-doc/find-documents cfg)))))

(sc/defn ^:always-validate conversion--fix-verdicts
  "Adds verdicts (and reviews) from KuntaGML if those were missed in the original
  conversion."
  [cfg :- ConversionConfiguration]
  (let [user         (conv-util/conversion-user (:organization-id cfg))
        organization (org/get-organization (:organization-id cfg))
        cfg          (assoc cfg :user user :organization organization)]
    (cp/pdoseq 8
               [{app-id :LP-id
                 :as    doc} (conv-doc/find-converted-documents cfg)
                :let         [application (mongo/select-one :applications
                                                            {:_id            app-id
                                                             ;; Just in case
                                                             :facta-imported true
                                                             :verdictDate    nil})]
                :when        application
                :let         [xml         (conv-doc/read-xml doc)
                              cfg (assoc cfg :created (now))]]
               (log/with-logging-context {:applicationId app-id
                                          :userId        (:id user)}
                 (when (conv/update-verdicts cfg xml application)
                   (conv/update-reviews cfg xml app-id)
                   (conv-doc/upsert-document :fix-verdicts doc))))))

(sc/defn ^:always-validate conversion--fix-reviews
  "Adds reviews from KuntaGML if those were missed in the original conversion."
  [cfg :- ConversionConfiguration]
  (let [user         (conv-util/conversion-user (:organization-id cfg))
        organization (org/get-organization (:organization-id cfg))
        cfg          (assoc cfg :user user :organization organization)]
    (cp/pdoseq 8
               [{app-id :LP-id
                 :as    doc} (conv-doc/find-converted-documents cfg
                                                                {:kuntagml.xml #"katselmustieto"}
                                                                :exclude-foremen)
                :let         [application (mongo/select-one :applications
                                                            {:_id            app-id
                                                             ;; Just in case
                                                             :facta-imported true
                                                             :verdictDate    {$exists true}})]
                :when        application
                :let         [xml         (conv-doc/read-xml doc)
                              cfg (assoc cfg :created (now))]]
               (log/with-logging-context {:applicationId app-id
                                          :userId        (:id user)}
                 (when-let [task-ids (conv/update-reviews cfg xml app-id)]
                   (log/info "Fixed reviews:" task-ids)
                   (conv-doc/upsert-document :fix-reviews doc))))))

(sc/defn ^:always-validate conversion--fetch-review-attachments
  "Fetches missing review attachments for the converted applications. If no `backend-ids`
  are given, then every suitable application in the organization is processed."
  [{:keys [organization-id permit-type threads targets]
    :as   cfg} :- ConversionConfiguration]
  (let [user      (conv-util/conversion-user (:organization-id cfg))
        cfg       (assoc cfg :user user :created (now))
        doc-query {:kuntagml.xml #"(?i)linkkiliitteeseen"}
        q         (if (seq targets)
                    doc-query
                    (some-<>> {:organization   organization-id
                               :facta-imported true
                               :tasks          {$elemMatch {:state       :sent
                                                            :source.type :background}}}
                              (util/assoc-when <> :permitType permit-type)
                              ;; We do not limit mongo query, but the documents in order to use
                              ;; other delimiters as well (e.g., years, tags).
                              (mongo/select :applications <> [:id])
                              (map :id)
                              seq
                              (assoc-in doc-query [:LP-id $in])))]
    (when q
      (cp/pdoseq (or threads 8)
                 [doc (conv-doc/find-converted-documents cfg q)]
                 (log/with-logging-context {:userId             (:id user)
                                            :conversion-command "fetch-review-attachments"
                                            :applicationId      (:LP-id doc)}
                   (try
                     (conv/fetch-missing-review-attachments cfg doc)
                     (catch Exception e
                       (error e "Review attachments fetch failed."))))))))

(sc/defn ^:always-validate conversion--fix-foremen
  "Adds foreman termination from KuntaGML if those were missed in the original
  conversion."
  [cfg :- ConversionConfiguration]
  (doseq [{app-id :LP-id
           :as    doc} (conv-doc/find-converted-documents
                         cfg {:suffix       :tjo
                              :kuntagml.xml #"(?i)alkamisPvm|paattymisPvm"})
          :when        (mongo/any? :applications
                                   {:_id                   app-id
                                    ;; Just in case
                                    :facta-imported        true
                                    :primaryOperation.name :tyonjohtajan-nimeaminen-v2
                                    :foremanTermination    nil})]
    (log/with-logging-context {:applicationId app-id
                               :userId        conv-util/conversion-user-id}
      (try
        (let [xml         (conv-doc/read-xml doc)
              termination (->> [:alkamisPvm :paattymisPvm]
                               (map #(xml/get-text xml [:Tyonjohtaja %]))
                               (apply conv/foreman-termination))]
          (when termination
            (mongo/update-by-id :applications app-id {$set {:foremanTermination termination}})
            (conv-doc/upsert-document :fix-foremen doc)))
        (catch Exception e
          (error e "Fixing foreman failed."))))))

(sc/defn ^:always-validate conversion--fix-locations
  "Update location information for conversion documents AND converted applications, if
  needed. The new locations are read from `:location-overrides` in the `cfg.`"
  [{:keys [location-overrides threads] :or {threads 8}
    :as   cfg} :- ConversionConfiguration]
  (cp/pdoseq threads
             [{:keys [backend-id]
               :as   doc} (conv-doc/find-documents cfg {:backend-id {$in (keys location-overrides)}})]
             (log/with-logging-context {:backend-id backend-id
                                        :userId     conv-util/conversion-user-id}
               (try
                 (conv-xy/fix-location cfg doc (get location-overrides backend-id))
                 (catch Exception e
                   (error e "Fixing location failed."))))))

(sc/defn ^:always-validate conversion--fix-states
  "Recalculate the current states of the converted applications and update if necessary."
  [{:keys [threads] :or {threads 8} :as cfg} :- ConversionConfiguration]
  (cp/pdoseq threads
             ;; We maintain the target order, so we know where to continue on crash etc.
             [doc (conv-doc/find-converted-documents cfg nil :target-order)]
             (log/with-logging-context {:applicationId (:LP-id doc)
                                        :userId        conv-util/conversion-user-id}
               (try
                 (log/debugf "Check state fix for %s (%s)"
                             (:backend-id doc) (:LP-id doc))
                 (conv-st/fix-state cfg doc)
                 (catch Exception e
                   (error e "Fixing state failed."))
                 (catch AssertionError e
                   (error e "Fixing state failed badly."))))))

(defn- fixable-history?
  "True if the application is converted AND every history state is set by
  batchrun/conversion user AND the current state matches the last history state."
  [{:keys [history facta-imported state]}]
  (and facta-imported
       (let [items (filterv :state history)]
         (and (every? (comp conv-util/conversion-or-batchrun-user? :user) items)
              (-> items peek :state (util/=as-kw state))))))

(sc/defn ^:always-validate conversion--fix-history
  "Reinitializes target application history, state and related timestamp properties. Only
  applications with pure batchrun _state history_ are reset (there can be other
  non-batchrun non-state history entries)."
  [{:keys [threads] :or {threads 8} :as cfg} :- ConversionConfiguration]
  (cp/pdoseq threads
             [{app-id :LP-id
               :as    doc} (conv-doc/find-converted-documents cfg)
              :let         [application (mongo/by-id :applications app-id)]
              :when        (fixable-history? application)]
             (log/with-logging-context {:applicationId app-id
                                        :userId        conv-util/conversion-user-id}
               (try
                 (conv-st/fix-history cfg doc application)
                 (catch Exception e
                   (error e "Fixing history failed."))
                 (catch AssertionError e
                   (error e "Fixing history failed badly."))))))

(defn execute-conversion-command [{:keys [cmd cfgfile cmd-help system] :as command}]
  {:pre [(map? system)]}
  (log/with-logging-context {:conversion-batchrun cmd}
    (try
      (if (= cmd :import)
        (conv-im/conversion--import cfgfile)
        (let [cfg     (sc/validate ConversionConfiguration
                                   (merge (some-> cfgfile conv-cfg/read-configuration)
                                          (select-keys command [:organization-id :tags :range
                                                                :years :limit :threads :retry
                                                                :system])))
              command (util/assoc-when command :cfg cfg)
              k?      #(boolean (% command))
              k-kw    #(when (k? %) %)]
          (case cmd
            :store         (conversion--store cfg)
            :convert       (conversion--convert cfg)
            :link          (conv-link/conversion--link cfg (or (k-kw :relink)
                                                               (k-kw :repair)))
            :send          (conversion--send cfg (k? :state-changes)
                                             (k? :last-state) (k? :kuntagml))
            :clean         (conversion--clean cfg (k? :dry-run) (k? :force))
            :nuke          (conversion--nuke cfg (k? :dry-run))
            :backend-ids   (conversion--backend-ids cfg)
            :tag           (conversion--tag cfg (select-keys command [:add :remove :number]))
            :fix-history   (conversion--fix-history cfg)
            :fix-verdicts  (conversion--fix-verdicts cfg)
            :fix-reviews   (conversion--fix-reviews cfg)

            :fetch-review-attachments
            (conversion--fetch-review-attachments cfg)
            :fix-foremen   (conversion--fix-foremen cfg)
            :fix-locations (conversion--fix-locations
                             (merge cfg (select-keys command [:location-relax])))
            :fix-states    (conversion--fix-states cfg))))
      (catch Exception e
        (error e)
        (cmd-help command (ex-message e))))))
