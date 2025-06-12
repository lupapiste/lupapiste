(ns lupapalvelu.imports.imports-api
  "Importing external form data via Excel files."
  (:require [babashka.fs :as fs]
            [lupapalvelu.action :refer [defcommand defquery defraw] :as action]
            [lupapalvelu.imports.core :as im]
            [lupapalvelu.imports.schemas :as im-sc]
            [lupapalvelu.imports.util :as im-util]
            [lupapalvelu.json :as json]
            [monger.operators :refer :all]
            [sade.core :refer [fail ok]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc]))

;; ----------------------
;; Authority admin
;; ----------------------

(defraw upload-import-excel
  {:description      "Uploads import excel and creates the initial import
  configuration. Returns job-id, since the actual upload can take some time."
   :permissions      [{:required [:organization/import]}]
   :parameters       [organizationId file]
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (fn [{data :data}]
                        (let [{:keys [filename size tempfile]} (:file data)]
                          (cond
                            (nil? tempfile)
                            (fail :error.file-not-found)
                            (not (some-> filename fs/extension (ss/=trim-i "xlsx")))
                            (fail :error.invalid-filename)
                            (not (some-> size (< (env/value :file-upload :max-size :logged-in))))
                            (fail :error.invalid-filesize))))]}
  [command]
  {:status  200
   :body    (json/encode {:ok true :job-id (im/start-upload-job command)})
   :headers (assoc http/no-cache-headers "Content-Type" "application/json")})

(defquery get-import-config
  {:description      "Fetches an individual import configuration"
   :permissions      [{:required [:organization/import]}]
   :parameters       [organizationId configId]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :configId       ssc/ObjectIdStr}]}
  [_]
  (if-let [cfg (im/get-configuration organizationId configId true)]
    (ok :config cfg)
    (fail :error.not-found)))

(defcommand update-import-config
  {:description      "Modifies import configuration. Some properties (e.g. types) can only be
   modified before importing."
   :permissions      [{:required [:organization/import]}]
   :input-validators [{:organizationId           ssc/NonBlankStr
                       :configId                 ssc/ObjectIdStr
                       (sc/optional-key :title)  im-sc/Title
                       (sc/optional-key :column) {:id                        ssc/ObjectIdStr
                                                  (sc/optional-key :type)    (sc/enum "text" "number" "backend-id"
                                                                                      "application-id" "date")
                                                  (sc/optional-key :include) sc/Bool
                                                  (sc/optional-key :title)   im-sc/Title
                                                  (sc/optional-key :unit)    (->> (map name im-sc/units)
                                                                                  (cons "")
                                                                                  (apply sc/enum))
                                                  (sc/optional-key :lines)   (->> [5 10 15 20]
                                                                                  (mapcat (juxt identity str))
                                                                                  (apply sc/enum ""))}}]
   :pre-checks       [(im/config-exists :not-deleted)]}
  [command]
  (im/edit-config command)
  (ok :modified (im-util/make-user-ts command)))

(defcommand reorder-import-config-columns
  {:description      "Updates columns' index fields according to the columnIds order."
   :permissions      [{:required [:organization/import]}]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :configId       ssc/ObjectIdStr
                       :columnIds      [ssc/ObjectIdStr]}]
   :pre-checks       [(im/config-exists :not-deleted)]}
  [command]
  (im/reorder-columns command)
  (ok :modified (im-util/make-user-ts command)))

(defquery organization-import-configs
  {:description      "All the organization import configs"
   :permissions      [{:required [:organization/import]}]
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]}
  [_]
  (ok :configs (im/get-configurations organizationId true)))

(defquery import-config-previews
  {:description      "Config previews. List of column-id  - `im-sc/Preview` maps."
   :permissions      [{:required [:organization/import]}]
   :input-validators [{:organizationId             ssc/NonBlankStr
                       :configId                   ssc/ObjectIdStr
                       ;; If not given, returns every (included) column.
                       (sc/optional-key :columnId) ssc/ObjectIdStr}]
   :pre-checks [(im/config-exists :previews)]}
  [command]
  (ok :previews (im/previews command)))

(defcommand execute-import-config
  {:description      "Does the actual importing of rowdata into section documents. Returns the
  created job instance."
   :permissions      [{:required [:organization/import]}]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :configId       ssc/ObjectIdStr}]
   :pre-checks       [(im/config-exists :draft :key-column :other-columns)]}
  [command]
  (ok :job (im/start-import-job command)))

(def tri-state-param (sc/enum "exclude" "include" "only"))

(defquery import-config-row-ids
  {:description "Imported row-ids for the given config. The idea is to first fetch _every
  id_ that matches the query. The actual rows are fetched separately with
  `import-config-rows`. In other words, we trust that the frontend handles paging. The ids
  are returned in their natural, id order. This ensures that the later added rows are
  always last. Optional parameters:

  `:deleted` (exclude/include/only) Whether deleted rows are excluded/included in the result
  set or if _only_ the deleted rows are included. Default is `exclude`.

  `:errors` (exclude/include/only) Similar to `:deleted` but for rows with import
  errors. Default is `include`.

  `:added` (exclude/include/only) Similar to `:deleted` but for (manually) added
  rows. Default is `include`."
   :permissions      [{:required [:organization/import]}]
   :input-validators [{:organizationId            ssc/NonBlankStr
                       :configId                  ssc/ObjectIdStr
                       (sc/optional-key :deleted) tri-state-param
                       (sc/optional-key :errors)  tri-state-param
                       (sc/optional-key :added)   tri-state-param}]
   :pre-checks       [(im/config-exists :imported :not-deleted)]}
  [command]
  (ok :row-ids (im/row-ids command)))

(defquery import-config-rows
  {:description      "Rows for the given ids within the config. The rows are returned in their
  id order."
   :permissions      [{:required [:organization/import]}]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :configId       ssc/ObjectIdStr
                       :rowIds         [ssc/ObjectIdStr]}]
   :pre-checks       [(im/config-exists :imported :not-deleted)]}
  [command]
  (ok :rows (im/rows-for-ids command)))

(defn- row-exists
  "Pre-checker that fails if no matching row is not found in mongo. If `cellId` parameter is
  given, checks its existence as well."
  [{{:keys [rowId cellId]} :data :as command}]
  (when rowId
    (let [row (im/command->row command [:cells])]
      (when (or (nil? row)
                (and cellId (not (im/row-cell row cellId))))
        (fail :error.not-found)))))

(defcommand edit-import-row
  {:description      "Edits an individual row cell. Fails if the new value does not pass
  validation. Returns processed `:value` and `:modified` information. While the command
  allows the editing of (implicitly or explicitly) deleted rows, the user interface
  typically disallows it. "
   :permissions      [{:required [:organization/import]}]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :rowId          ssc/ObjectIdStr
                       :cellId         ssc/ObjectIdStr
                       :value          sc/Any}]
   :pre-checks       [row-exists]}
  [command]
  (im/edit-cell command))

(defcommand delete-import-config
  {:description      "Deletes the (not yet imported!)  configuration and its rowdata file."
   :permissions      [{:required [:organization/import]}]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :configId       ssc/ObjectIdStr}]
   :pre-checks       [(im/config-exists :draft)]}
  [command]
  (im/delete-config command)
  (ok))

(defcommand mark-import-config-deleted
  {:description      "Marks imported configuration either deleted or not. If a config is marked
  deleted, its rows are (implicitly) treated as deleted too (but not marked deleted in
  mongo). When marked deleted, returns `:deleted` property."
   :permissions      [{:required [:organization/import]}]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :configId       ssc/ObjectIdStr
                       :deleted        sc/Bool}]
   :pre-checks       [(im/config-exists :imported)]}
  [command]
  (if-let [deleted (im/mark-config-deleted command)]
    (ok :deleted deleted)
    (ok)))

(defcommand mark-import-config-row-deleted
  {:description      "Marks an individual imported row either deleted or not. If a config is marked
  deleted, its rows are (implicitly) treated as deleted too. When marked deleted, returns
  `:deleted` property."
   :permissions      [{:required [:organization/import]}]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :rowId          ssc/ObjectIdStr
                       :deleted        sc/Bool}]
   :pre-checks       [row-exists]}
  [command]
  (if-let [deleted (im/mark-row-deleted command)]
    (ok :deleted deleted)
    (ok)))

;; ----------------------
;; Application authority
;; ----------------------

(defcommand edit-application-import-row
  {:description      "Edits an individual row cell. Fails if the new value does not pass
  validation. Returns processed `:value` and `:modified` information. While the command
  allows the editing of (implicitly or explicitly) deleted rows, the user interface
  typically disallows it. Application counterpart to `edit-import-row`."
   :permissions      [{:required [:application/edit-imports]}]
   :input-validators [{:id     ssc/ApplicationId
                       :rowId  ssc/ObjectIdStr
                       :cellId ssc/ObjectIdStr
                       :value  sc/Any}]
   :pre-checks       [row-exists]}
  [command]
  (im/edit-cell command))

(defcommand mark-application-import-row-deleted
  {:description      "Marks an individual imported row either deleted or not. When marked
  deleted, returns `:deleted` property."
   :permissions      [{:required [:application/edit-imports]}]
   :input-validators [{:id      ssc/ApplicationId
                       :rowId   ssc/ObjectIdStr
                       :deleted sc/Bool}]
   :pre-checks       [row-exists]}
  [command]
  (if-let [deleted (im/mark-row-deleted command)]
    (ok :deleted deleted)
    (ok)))

(defquery application-imports
  {:description      "Active (non-deleted) import configurations and their rows that match the
  application. A row is match if its `application-id` or `backend-id` cell matches the
  application or its added property matches the application."
   :permissions      [{:required [:application/read-imports]}]
   :input-validators [{:id ssc/ApplicationId}]
   :pre-checks       [im/application-has-configurations]}
  [command]
  (ok (im/application-configurations-and-rows command)))

(defcommand add-application-import-row
  {:description      "Adds new row with `application-id` and `backend-id` cells filled with the
  corresponding application data. The new row has added property as well. Returns the
  created row."
   :permissions      [{:required [:application/edit-imports]}]
   :input-validators [{:id       ssc/ApplicationId
                       :configId ssc/ObjectIdStr}]
   :pre-checks       [(im/config-exists :imported :not-deleted)]}
  [command]
  (ok :row (im/add-aplication-import-row command)))
