(ns lupapalvelu.admin.elasticsearch-api
  (:require [lupapalvelu.action :refer [defcommand defquery]]
            [lupapalvelu.elasticsearch.api :as es-api]
            [lupapalvelu.elasticsearch.query :as es-query]
            [lupapalvelu.elasticsearch.util :as es-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.util-components :as components]
            [monger.operators :refer [$set]]
            [sade.core :refer [fail ok]]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc])
  (:import [clojure.lang ExceptionInfo]))

(defquery get-es-config
  {:description "Get Elasticsearch configuration"
   :user-roles  #{:admin}}
  [{:keys [system]}]
  (ok :config (components/system->elastic-config system)))

(defquery get-es-index-info
  {:parameters       [index]
   :input-validators [{:index ssc/NonBlankStr}]
   :description      "Get Elasticsearch info for index"
   :user-roles       #{:admin}}
  [command]
  (let [es           (components/command->elastic command)
        index        (es-util/elevate-index-name index)
        full-version (-> (components/command->elastic-config command)
                         (es-util/full-version index))]
    (ok :data {:lupapiste-index-name (str index)
               :aliases              (es-util/get-aliases (:rest-client es) index)
               :full-version         full-version
               :settings             (try
                                       (es-api/get-index-settings es index)
                                       (catch Exception e
                                         (log/errorf e "Failed to get index settings for %s." index)))
               :old-document-count   (es-api/count-documents es index
                                                             {:query (es-query/version-older-than full-version)})
               :document-count       (es-api/count-documents es index {})})))

(defquery get-es-document-by-id
  {:parameters       [index search-id]
   :input-validators [{:index     ssc/NonBlankStr
                       :search-id ssc/NonBlankStr}]
   :description      "Find document by id from elasticsearch index"
   :user-roles       #{:admin}}
  [command]
  (try
    (ok :result (es-api/get-by-id (components/command->elastic command) (es-util/elevate-index-name index) search-id nil))
    (catch ExceptionInfo e
      (let [data (ex-data e)]
        (if (= 404 (:status data))
          (ok :result nil :error "Not found")
          (throw e))))
    (catch Exception e
      (log/error e "Unknown error in query lupapalvelu.admin.elasticsearch-api/get-by-id")
      (fail :error.unknown))))

(defcommand update-es-document-by-mongo-id
  {:parameters       [index doc-id]
   :input-validators [{:index  ssc/NonBlankStr
                       :doc-id ssc/NonBlankStr}]
   :description      "Update elastic document by it's corresponding Mongo document ID"
   :user-roles       #{:admin}}
  [command]
  (es-api/re-index-by-query! (components/command->elastic command) (es-util/elevate-index-name index) {:_id doc-id})
  (ok))

(defcommand full-es-index-update
  {:parameters       [index]
   :input-validators [{:index ssc/NonBlankStr}]
   :description      "Trigger a full update of all documents in the specified Elasticsearch index"
   :user-roles       #{:admin}}
  [command]
  (es-api/full-index-update! (components/command->elastic command) (es-util/elevate-index-name index))
  (ok))

(defcommand es-index-cleanup
  {:parameters       [index]
   :input-validators [{:index ssc/NonBlankStr}]
   :description      "Delete outdated documents from the specified Elasticsearch index. A document is considered
                      outdated if the index-specific implementation of es-util/elastic-documents does not return its ID."
   :user-roles       #{:admin}}
  [command]
  (es-api/delete-outdated-documents! (components/command->elastic command) (es-util/elevate-index-name index))
  (ok))

(defcommand set-es-index-aliases-to-current-version
  {:parameters       [index]
   :input-validators [{:index ssc/NonBlankStr}]
   :description      "Updates the read and write aliases of the index to point to the current version, resolving issues
                      caused by application crashes during index creation."
   :user-roles       #{:admin}}
  [command]
  (let [client                     (:rest-client (components/command->elastic command))
        elevated-index-name        (es-util/elevate-index-name index)
        {index-kw        :name
         {:keys [major]} :version} (->> (components/command->elastic-config command)
                                        :indices
                                        (util/find-by-key :name elevated-index-name))
        {:keys [read write]}       (es-util/aliases index-kw "")
        current-index              (es-util/version->index index-kw major "")]
    (es-util/remove-aliases client index-kw read)
    (es-util/update-aliases client [{:add {:index current-index :alias read}}])
    (es-util/remove-aliases client index-kw write)
    (es-util/update-aliases client [{:add {:index current-index :alias write}}]))
  (ok))

(defcommand set-es-index-refresh-interval
  {:parameters       [index]
   :input-validators [{:index ssc/NonBlankStr}]
   :description      "Sets the index refresh interval to 1 second, resolving issues caused by application crashes during
                      index creation."
   :user-roles       #{:admin}}
  [command]
  (let [es    (components/command->elastic command)
        index (es-util/elevate-index-name index)]
    (es-api/update-index-settings! es index {:refresh_interval "1s"}))
  (ok))

(defquery dashboard-search-settings
  {:description "Get dashboard search elastic settings"
   :user-roles  #{:admin}}
  [_]
  (ok :settings (dissoc (mongo/by-id :admin-config "dashboard-search-elastic") :id)))

(defcommand toggle-dashboard-search-setting
  {:parameters       [setting value]
   :input-validators [{:setting (sc/enum "application-search" "foreman-search")
                       :value   sc/Bool}]
   :description      "Set dashboard search elastic settings"
   :user-roles       #{:admin}}
  [_]
  (ok :settings (dissoc (mongo/update-one-and-return :admin-config {:_id "dashboard-search-elastic"}
                                                     {$set {setting value}} :upsert true)
                        :_id)))
