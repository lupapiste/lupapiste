(ns lupapalvelu.backing-system.dmcity.schemas
  "Schemas for dmcity configuration."
  (:require [lupapalvelu.conversion.schemas :as conv-sc]
            [lupapalvelu.system.util-components-schema :as components-schema]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(defn- supported-permit-types
  "Set of permit-types (keywords) that are supported by dmcity. By default, only R, P
  and YA are supported. However, feature flag `dmcity-extra-permit-types` provides
  support for KT and MM as well."
  []
  (cond-> #{:R :P :YA}
    (env/feature? :dmcity-extra-permit-types)
    (conj :KT :MM :YI :YM :MAL :VVVL :YL)))

(defn supported-permit-type?
  "True if `permit-type` (string or keyword) is supported by dmcity."
  [permit-type]
  (contains? (supported-permit-types) (keyword permit-type)))

(def ^:private Path #"^[\w\-]+$")

(def ^:private all-paths
  (merge {:R  {:application "Rakennusvalvontapaatos"
               :review      "Katselmustieto"}
          :P  {:application "Poikkeamispaatos"
               :review      "Katselmustieto"}
          :YA {:application "YleistenAlueidenPaatos"}}
         (zipmap [:KT :MM] (repeat {:application "KiinteistonmuodostusHakemus"}))
         (zipmap [:YI :YM :MAL :VVVL :YL] (repeat {:application "YmparistoHakemus"}))))

(defn- permit-type-paths []
  (select-keys all-paths (supported-permit-types)))

(defn default-path
  "Default path or throws."
  [target path]
  (let [target (keyword target)
        path   (keyword path)]
    (or (get-in (permit-type-paths) [target path])
        (case target
          :state      (case path
                        :state "HakemuksenTilapaivitys")
          ;; Permit types without conversion paths fallback to :application path in the client
          ;; code.
          :conversion (case path
                        :R     "RakennuslupienKonversio"
                        :state "HakemuksenTilapaivitysKonversio")
          :link       (env/value :dmcity :link-format)
          (throw (ex-info (format "Unsupported dmcity target (%s) and/or path (%s)"
                                  target path)
                          {:target target :path path}))))))

(defn- make-dmcity-target-schema
  "MySchema true :foo :bar
  -> {(sc/optional-key :enabled) boolean
      (sc/optional-key :foo)     MySchema
      (sc/optional-key :bar)     MySchema}"
  [schema optional? & ks]
  (->> ks
       (map #(hash-map (cond-> % optional? sc/optional-key) schema))
       (apply st/merge {(sc/optional-key :enabled) sc/Bool})))

(defn- make-paths-schema [& ks]
  (apply make-dmcity-target-schema Path true ks))

(defn- make-permit-type-schemas []
  (util/map-values #(apply make-paths-schema (keys %))
                   (permit-type-paths)))

(sc/defschema DmcityIntegration
  "Full integration details (credentials, urls, paths, ...) for dmcity integration. "
  {;; Url without the use case specific path parth.
   :url                       (sc/constrained
                                ssc/HttpUrl #(not= \/ (last %))
                                "Base URL cannot end in a slash.")
   :vault                     ssc/NonBlankStr
   :username                  ssc/NonBlankStr
   :password                  ssc/NonBlankStr
   :crypto-iv                 ssc/NonBlankStr
   (sc/optional-key :targets) (st/optional-keys
                                (st/merge (make-permit-type-schemas)
                                          {:state      (make-paths-schema :state)
                                           ;; If not enabled, the conversion use cases (e.g.,
                                           ;; applications with backend-id) fallback to the regular
                                           ;; paths.
                                           :conversion (make-paths-schema :R :state)
                                           :building   (make-dmcity-target-schema
                                                         ssc/HttpUrl false :url)
                                           ;; Not authenticated
                                           :address    (make-dmcity-target-schema
                                                         ssc/HttpUrl false :url)
                                           ;; Linking/redirecting to dmcity enabled. The
                                           ;; fallback for the blank `:fmt` is the
                                           ;; `dmcity.link-format` property.
                                           :link       {:enabled               sc/Bool
                                                        (sc/optional-key :fmt) sc/Str}}))})

(defn schema-path?
  "True if given path is found in `DmcityIntegration`."
  [& ks]
  (some->> (flatten ks)
           seq
           (map keyword)
           (st/get-in DmcityIntegration)
           boolean))

(def TopTargets (->> (st/get-in DmcityIntegration [:targets])
                     keys (map :k) (apply sc/enum)))

(defn target-key? [k]
  (schema-path? :targets k))

(defn review-support? [permit-type]
  (schema-path? :targets permit-type :review))

(def TargetKey (sc/pred target-key? "Dmcity configuration target"))

(sc/defschema SimpleConfig
  "UI configurator view of `DmcityIntegration`."
  (-> DmcityIntegration
      (st/dissoc :crypto-iv)
      (st/assoc :url ssc/HttpUrl)
      (st/update :targets
                 st/merge
                 (st/optional-keys
                   (zipmap (conj (supported-permit-types) :state :conversion :link)
                           (repeat sc/Bool)))
                 (st/optional-keys
                   {:building (make-dmcity-target-schema
                                sc/Str false :url)
                    :address  (make-dmcity-target-schema
                                sc/Str false :url)}))))

(sc/defschema ClientConfig
  "Runtime configuration for a batchrun."
  {:organization sc/Any
   :user         sc/Any
   :system       components-schema/CommonComponents
   :options      (st/merge {(sc/optional-key :type-suffix) sc/Keyword
                            sc/Keyword                     sc/Bool}
                           conv-sc/ForcePath)})

(sc/defschema ClientResult
  "Result of a batchrun message sending step."
  (st/merge {:application-id ssc/NonBlankStr
             :ts             ssc/Timestamp}
            (st/optional-keys
              {:state      ssc/NonBlankStr
               :verdict-id ssc/NonBlankStr
               :review     {:id   ssc/NonBlankStr
                            :name ssc/NonBlankStr}
               :error      ssc/NonBlankStr
               :skipped    (sc/conditional
                             :state       {:state ssc/NonBlankStr}
                             :operation   {:operation ssc/NonBlankStr}
                             :permitType  {:permitType ssc/NonBlankStr}
                             :infoRequest {:infoRequest (sc/eq true)})})))

(def ClientOp (sc/enum :last-state :all-states :application
                       :verdicts :application/verdicts :reviews))

(def skipped-states
  "States that are not necessarily sent to dmcity. Even the skipped states can be sent if
  they are not in the beginning of the application state history (returned to draft, for
  example). Also, the whole application (states, KuntaGML) is ignored if its active state
  is skipped."
  #{:draft :open})

(defn skipped-state?
  "True if state (string or keyword) belongs to `skipped-states`."
  [state]
  (util/includes-as-kw? skipped-states state))
