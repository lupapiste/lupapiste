(ns lupapalvelu.organization-integrations
  (:require [camel-snake-kebab.core :as csk]
            [clojure.set :as set]
            [lupapalvelu.backing-system.schema :as schemas]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer [$elemMatch $push $set]]
            [sade.core :refer [fail!]]
            [sade.shared-schemas :as ssc]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(sc/defschema HasIntegrations
  (st/open-schema
    (st/optional-keys
      {:integrations [sc/Any]})))

(defn- mongo->clj
  [m]
  (-> (util/postwalk-map (partial util/map-keys csk/->kebab-case-keyword) m)
      (set/rename-keys {:enabled :enabled?
                        :type    :integration-type})
      (update :integration-type keyword)
      (update :targets set)))

(defn- clj->mongo
  [m]
  (->> (set/rename-keys m {:enabled?         :enabled
                           :integration-type :type})
       (util/postwalk-map (partial util/map-keys csk/->camelCaseKeyword))))

(defn- has-type-fn
  [expected-type]
  (fn [{:keys [type]}]
    (= expected-type (keyword type))))

(sc/defn find-by-type :- [schemas/CommonIntegrationConfig]
  {:always-validate true}
  [{:keys [integrations]} :- HasIntegrations
   integration-type :- sc/Keyword]
  (->> integrations
       (filter (has-type-fn integration-type))
       (map mongo->clj)))

(sc/defn current-targets-by-type :- #{schemas/Target}
  "Returns the set of targets covered by any integration of type `integration-type`."
  {:always-validate true}
  [organization :- HasIntegrations
   integration-type :- sc/Keyword]
  (->> (find-by-type organization integration-type)
       (map (comp set :targets))
       (apply set/union)))

(sc/defschema Args
  "Arguments when inserting or updating integrations"
  {:config          schemas/CommonIntegrationConfig
   :organization-id org/OrgId
   :now             ssc/Timestamp})

(sc/defn insert!
  "Insert a new integration config"
  {:always-validate true}
  [{:keys [config organization-id now]} :- Args]
  (mongo/update-by-query
    :organizations
    {:_id organization-id}
    {$push {:integrations (-> (merge {:id         (mongo/create-id)
                                      :created-at now}
                                     (select-keys config [:integration-type :enabled? :targets :options]))
                              clj->mongo)}}))

(defn- with-kw-prefix
  "Add `prefix` to all keys in the map `m`."
  [m prefix]
  (reduce-kv
    (fn [m k v] (assoc m (util/kw-path prefix k) v))
    (empty m)
    m))

(defn- lift-field
  [m k prefix]
  (-> m
      (dissoc k)
      (merge (with-kw-prefix (get m k) prefix))))

;; TODO: It would be nice if we could remove existing fields, e.g. if they are optional
(sc/defn update!
  "Updating an integration config.
   To allow partial updates, the `:options` are merged with the existing ones instead of overwriting them.
   The downside is that we cannot remove existing fields."
  {:always-validate true}
  [{{:keys [id] :as config} :config
    :keys                   [organization-id]} :- Args]
  (when (nil? id)
    (fail! :error))
  (mongo/update-by-query
    :organizations
    {:_id          organization-id
     :integrations {$elemMatch {:id id}}}
    {$set (-> (select-keys config [:enabled? :targets :options])
              clj->mongo
              ;; Rename and lift the options fields so we get keys like
              ;; "integrations.$.enabled", "integrations.$.options.someField"..
              (lift-field :options "options")
              (with-kw-prefix "integrations.$"))}))
