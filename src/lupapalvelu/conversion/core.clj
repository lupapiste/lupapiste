(ns lupapalvelu.conversion.core
  "A namespace that provides an endpoint for the KuntaGML conversion pipeline."
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.conversion.cleaner :as cleaner]
            [lupapalvelu.conversion.config :as conv-cfg]
            [lupapalvelu.conversion.kuntagml-converter :as conv]
            [lupapalvelu.conversion.schemas :refer [ConversionConfiguration
                                                    Target ResolvedTarget
                                                    ConversionDocument]]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.matti :as matti]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [net.cgrand.enlive-html :as enlive]
            [sade.core :refer [now]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema.core :as sc]
            [taoensso.timbre :refer [info error errorf]]))

(sc/defn ^:always-validate ^:private new-conversion-document :- ConversionDocument
  "Creates and inserts new conversion document."
  [{:keys [organization-id municipality]} :- ConversionConfiguration
   backend-id :- ssc/NonBlankStr]
  (let [doc {:id                    (mongo/create-id)
             :backend-id            backend-id
             :organization          organization-id
             :LP-id                 (conv-util/make-converted-application-id municipality backend-id)
             :converted             false
             :linked                false
             :conversion-timestamps []
             :app-links             []}]
    (mongo/insert :conversion doc)
    doc))

(defn- process-existing-conversion-doc
  "Whether to proceed with conversion, if the document already exists, dependends on the
  context. If the converted application exists the `overwrite?` option is required AND the
  application must be cleanable. Returns either updated `target` or nil. Can throw, if
  cleaning fails."
  [{:keys [overwrite?]} {:keys [id conversion-doc] :as target}]
  (let [application? (mongo/any? :applications {:_id (:LP-id conversion-doc)})]
    (cond
      (not application?) target

      overwrite?
      (update target :conversion-doc cleaner/clean-conversion!)

      :else
      (errorf "Backend id %s already converted to %s. No overwrite? option, no conversion."
              id (:LP-id conversion-doc)))))

(sc/defn ^:always-validate make-conversion-target :- (sc/maybe ResolvedTarget)
  [{:keys [organization-id] :as cfg} :- ConversionConfiguration
   target :- Target]
  (try
    (when-let [{:keys [id xml-application-id conversion-doc filename]
                :as   target} (apply conv-cfg/resolve-target cfg (first target))]
      (cond

        (nil? filename)
        (errorf "KuntaGML file not given for %s. Not converted." id)

        xml-application-id
        (errorf "Application id %s found in %s for %s. Not converted."
                xml-application-id filename id)

        conversion-doc
        (process-existing-conversion-doc cfg target)

        :else
        (if-let [application-ids (seq (app/get-lp-ids-by-kuntalupatunnus organization-id id))]
          (errorf "Applications with municipality id %s already exists: %s. No conversion."
                  id application-ids)
          (assoc target :conversion-doc (new-conversion-document cfg id)))))
    (catch Exception e
      (errorf "No conversion for %s: %s" target (ex-message e)))))

(defn convert!
  "The conversion configuration file `filename` format is defined by
  `ConversionEdn` schema."
  [filename]
  (let [{:keys [organization-id permit-type targets]
         :as   cfg}     (conv-cfg/read-configuration filename)
        batchrun-user (usr/batchrun-user [organization-id])
        created       (now)]
    (logging/with-logging-context {:userId (:id batchrun-user)}
      (info (str "Converting " (count targets) " permits from KuntaGML -> Lupapiste."))
      (doseq [{:keys [conversion-doc]
               :as   target} (keep (partial make-conversion-target cfg) targets)
              :let           [{backend-id :backend-id
                               app-id     :LP-id} conversion-doc]]
        (try
          (logging/with-logging-context {:userId        (:id batchrun-user)
                                         :applicationId app-id
                                         :backendId     backend-id}
            (->> {:created        created
                  :application-id app-id
                  :data           {:target         target
                                   :permitType     permit-type
                                   :organizationId organization-id}
                  :user           batchrun-user}
                 (util/strip-nils)
                 (conv/run-conversion! (assoc (select-keys cfg [:organization-id :permit-type
                                                                :municipality :force-terminal-state?
                                                                :location-overrides :location-fallback])
                                              :conversion-doc conversion-doc))))
          (catch Exception e
            (errorf e "Conversion for %s (%s) failed." backend-id (:filename target))))))))

(defn- enrich-with-LupaTunnus [application-id node]
  (update node :content
          (partial mapcat (fn [element]
                            (cond-> [element]
                              (= (:tag element) :yht:kuntalupatunnus)
                              (conj {:tag :yht:muuTunnustieto
                                     :content [{:tag :yht:MuuTunnus
                                                :content [{:tag :yht:tunnus
                                                           :content [application-id]}
                                                          {:tag :yht:sovellus
                                                           :content ["Lupapiste"]}]}]}))))))

(defn xml-with-application-id
  "Adds `application-id` to XML file `filename`. Returns the augmented XML as string."
  [filename application-id]
  (-> (slurp filename)
      (xml/parse-string "utf8")
      (enlive/at [:rakval:luvanTunnisteTiedot :yht:LupaTunnus]
                 (partial enrich-with-LupaTunnus application-id))
      xml/element-to-string))

(defn send-conversion-messages
  "Called after a successful conversion with the same EDN configuration file. If
  `state-changes?` is true, the state-change messages are sent. If `kuntagml?` is true, the
  files listed in the `cfgfile` are augmented with the (conversion result) application id
  and sent."
  [cfgfile state-changes? kuntagml?]
  (let [{:keys [organization-id targets]
         :as cfg} (conv-cfg/read-configuration cfgfile)
        organization                      (mongo/by-id :organizations organization-id)
        user                              (usr/batchrun-user [organization-id])]
    (doseq [{:keys [filename xml-application-id
                    conversion-doc]} (some->> targets
                                              (filter :filename)
                                              (keep #(apply conv-cfg/resolve-target cfg (first %))))
            :let               [application (when-not xml-application-id
                                              (some->> conversion-doc
                                                       :LP-id
                                                       (mongo/by-id :applications)))]
            :when              application]
      (when state-changes?
        (doseq [{err    :error
                 app-id :applicationId
                 state  :state} (matti/send-state-changes user organization application nil)
                :when           err]
          (logging/with-logging-context {:applicationId app-id
                                         :userId        (:id user)
                                         :state         state}
            (error "Send state change failed:" err))))
      (when kuntagml?
        (matti/send-kuntagml user organization application :conversion
                             (xml-with-application-id filename (:id application)))))))

(defn assoc-once!
  "Throws if `k` already in `m`."
  [m k v]
  (if (contains? m k)
    (throw (ex-info (str "Option already set: " k) {:k k}))
    (assoc m k v)))

(defn parse-command-line
  "`option-defs` is a pred - option map. If pred is a string, it means an exact trimmed
  case-insensitive match. Returns an option - arg map. For example,

  > (parse-command-line {'-hello' :hello?} ['-hello'])
  {:hello? '-hello'}

  Returns a  `:parse-error` - error message string map on errors. If no options found, returns nil."
  [option-defs args]
  (try
    (let [option-defs (util/map-keys (fn [k]
                                       (if (string? k)
                                         #(ss/=trim-i % k)
                                         k))
                                     option-defs)]
      (reduce (fn [acc a]
                (if-let [opt (some (fn [[pred? opt]]
                                     (when (pred? a)
                                       opt))
                                   option-defs)]
                  (assoc-once! acc opt a)
                  (throw (ex-info (str "Unknown option: " a) {:arg a}))))
              nil
              args))
    (catch Exception e
      {:parse-error (ex-message e)})))

(defn edn-file-arg? [a]
  (ss/ends-with-i a ".edn"))

(defn send-conversion-messages-wrapper
  "Parses args and calls `send-conversion-messages`. Args are filename , (optional)
  `-state-changes` and (optional) `-kuntagml` in any order."
  [& args]
  (let [{:keys [filename state-changes? kuntagml?
                parse-error]} (parse-command-line {edn-file-arg?    :filename
                                                   "-state-changes" :state-changes?
                                                   "-kuntagml"      :kuntagml?}
                                                  args)
        help                  "The supported parameters are filename with -state-changes and -kuntagml options"]
    (cond
      parse-error (error parse-error help)
      filename    (send-conversion-messages filename
                                            (boolean state-changes?)
                                            (boolean kuntagml?))
      :else       (error "Filename is mandatory." help))))

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
                  ;; conversion has failed earlier and thus not a bad one.
                  (some->> (app/get-lp-ids-by-kuntalupatunnus organization-id id)
                           seq
                           (not= [(:LP-id conversion-doc)])))
          target)))
    (catch Exception e
      (error e "Bad target"))))


(sc/defn ^:always-validate remove-bad-conversions
  [cfg :- ConversionConfiguration
   {:keys [force? dry-run?]} :- {:force?   sc/Bool
                                 :dry-run? sc/Bool}]
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

(defn remove-bad-conversions-wrapper
  [& args]
  (let [{:keys [filename force? dry-run?
                parse-error]} (parse-command-line {edn-file-arg? :filename
                                                   "-force"      :force?
                                                   "-dry-run"    :dry-run?}
                                                  args)
        help                  "The supported parameters are mandatory filename with optional -dry-run and -force options"]
    (cond
      parse-error (error parse-error help)
      filename    (remove-bad-conversions (conv-cfg/read-configuration filename)
                                          {:force?   (boolean force?)
                                           :dry-run? (boolean dry-run?)})
      :else       (error "Mandatory filename is missing." help))))

(defn add-missing-backend-ids [organization-id]
  (let [m          (->> (mongo/select :conversion
                                      {:organization organization-id :converted true}
                                      [:backend-id :LP-id])
                        (map (juxt :LP-id :backend-id))
                        (into {}))
        target-ids (map :id
                        (mongo/select :applications
                                      {:_id            {$in (keys m)}
                                       :organization   organization-id
                                       :facta-imported true
                                       :verdicts.kuntalupatunnus {$exists false}}
                                      [:id]))]
    (info "Found" (count target-ids) "converted applications without verdicts.")
    (doseq [app-id target-ids
            :let [backend-id (get m app-id)]]
      (logging/with-logging-context {:applicationId app-id}
        (info "Update application" app-id "with backend-id" backend-id)
        (conv/store-backend-id app-id backend-id)))))
