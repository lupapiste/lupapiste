(ns lupapalvelu.backing-system.dynasty.api.client
  (:require [camel-snake-kebab.core :as csk]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [lupapalvelu.backing-system.dynasty.api.client-proto :as proto]
            [lupapalvelu.json :as json]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc]))

(def ^:private api-path "/dynasty.externalinterface.oauth/api/v2")
(def ^:private token-path "/dynasty.identityserver/connect/token")

(sc/defschema Organization
  "> Target database selection from configuration
   Typically a number (default is `\"0\"`), but can also be alphanumeric"
  ssc/NonBlankStr)

(sc/defschema ServerAuthConfig
  {:host          ssc/NonBlankStr
   :client-id     ssc/NonBlankStr
   :client-secret ssc/NonBlankStr})

(sc/defschema ServerConfig
  {:host         ssc/NonBlankStr
   :organization Organization
   :auth         ServerAuthConfig})

(def Client proto/Client)

(def ^:private special-keys
  "Although most of the keys are in camelCase, there are a few exceptions."
  {:schemaId    :schemaID
   :metaJson    :metaJSON
   :eInvEdiCode :eInvEDICode})

(defn- convert-keys-to-dynasty
  [m meta-json?]
  (walk/postwalk
    (fn [form]
      (cond
        (keyword? form) (if meta-json?
                          (csk/->PascalCaseKeyword form)
                          (csk/->camelCaseKeyword form))
        (map? form)     (set/rename-keys form special-keys)
        :else           form))
    m))

(defn- convert-parameters
  [{:keys [meta-json] :as parameters}]
  (cond-> parameters
    (some? meta-json) (assoc :meta-json (-> meta-json
                                            (convert-keys-to-dynasty true)
                                            json/encode))
    true              (convert-keys-to-dynasty false)))

(defn- prepare-command
  ([path server-config]
   (prepare-command path server-config {}))
  ([path {:keys [host auth organization]} {:keys [form-params multipart]}]
   (merge {:url (str host api-path path)}
          (when auth
            {:auth    (merge {:url (str (:host auth) token-path)}
                             (select-keys auth [:client-id :client-secret]))
             :headers {:organization organization}})
          (when form-params
            {:form-params (convert-parameters form-params)})
          (when multipart
            {:multipart (reduce (fn [m {:keys [content] :as entry}]
                                  (conj m (if (map? content)
                                            (-> entry
                                                (update :content (comp json/encode convert-parameters))
                                                ;; Make sure to specify type and ecoding, as clj-http
                                                ;; defaults to "text/plain" + ASCII
                                                (assoc :mime-type "application/json"
                                                       :encoding  "UTF-8"))
                                            entry)))
                                []
                                multipart)}))))

(defn- convert-keys-from-dynasty
  [m]
  (util/postwalk-map
    (partial util/map-keys csk/->kebab-case-keyword)
    m))

(defn- process-response
  "Cleanup Dynasty responses with empty, blank, and nil values.
   Note that responses never contain the `:metaJSON` field."
  [body]
  (when (seq body)
    (->> body
         util/strip-blanks
         util/strip-empty-collections
         convert-keys-from-dynasty)))

(defn get!
  [client path server-config]
  (let [command  (prepare-command path server-config)
        response (proto/get! client command)]
    (process-response response)))

(defn post!
  ([client path server-config]
   (post! client path server-config {}))
  ([client path server-config opts]
   (let [command  (prepare-command path server-config opts)
         response (proto/post! client command)]
    (process-response response))))
