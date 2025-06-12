(ns lupapalvelu.imports.schemas
  (:require [clojure.set :as set]
            [sade.shared-schemas :as ssc]
            [sade.util :as util]
            [schema-tools.coerce :as stc]
            [schema.core :as sc]))

(def user-ts {:ts ssc/Timestamp :user-id ssc/NonBlankStr})
(def units [:days :years :ha :cm :m :m2 :m3 :kpl :section :eur])
(def ^:private key-columns #{:backend-id :application-id})

(sc/defschema ColumnType
  (sc/conditional
    (comp #{:text} :type)   {:type                    (sc/eq :text)
                             (sc/optional-key :lines) ssc/PosInt}
    (comp #{:number} :type) {:type                   (sc/eq :number)
                             (sc/optional-key :unit) (apply sc/enum units)}
    (comp #{:date} :type)   {:type (sc/eq :date)}
    :else                   {:type (apply sc/enum key-columns)}))

(sc/defschema Title
  {:fi                   ssc/NonBlankStr
   (sc/optional-key :sv) sc/Str
   (sc/optional-key :en) sc/Str})

(sc/defschema Column
  {:id                        ssc/ObjectIdStr
   :title                     Title
   :type                      ColumnType
   :index                     ssc/Nat
   ;; Only during configuration. After import, every remaining column is included.
   (sc/optional-key :include) sc/Bool})

(defn key-column? [column]
  (util/includes-as-kw? key-columns (-> column :type :type)))

(sc/defschema Cell
  {:id                         ssc/ObjectIdStr
   :column-id                  ssc/ObjectIdStr
   :type                       (apply sc/enum :text :number :date key-columns)
   (sc/optional-key :value)    sc/Any
   :modified                   user-ts
   ;; Missing if `raw` is blank.
   (sc/optional-key :original) {:raw                     sc/Str
                                ;; Fallbacks to `raw`.
                                (sc/optional-key :value) sc/Any}})

(defn key-cell?
  [cell]
  (util/includes-as-kw? key-columns (:type cell)))

(sc/defschema Preview
  {(sc/optional-key :value) sc/Any
   :raw                     sc/Str
   :state                   (sc/enum :ok :error)})

(def error-source
  "`key` means that _every_ key column is blank. Otherwise the value is interpreted as a
  cell-id."
  (sc/cond-pre (sc/eq "key") ssc/ObjectIdStr))

(def added
  "Application id is needed to make sure that the row can be matched to the application. For
  example, if the configuration does not have `:application-id` column and the application
  is without backend-id."
  (assoc user-ts :id ssc/ApplicationId))

(sc/defschema Row
  {:id                        ssc/ObjectIdStr
   :organization              ssc/NonBlankStr
   :config-id                 ssc/ObjectIdStr
   :cells                     [Cell]
   (sc/optional-key :errors)  #{error-source}
   (sc/optional-key :deleted) user-ts
   ;; When the row was _manually_ added
   (sc/optional-key :added)   added})

(sc/defschema ImportConfig
  {:id                              ssc/ObjectIdStr
   :organization                    ssc/NonBlankStr
   :title                           Title
   :rowdata                         {:filename                     ssc/NonBlankStr
                                     (sc/optional-key :window1904) sc/Bool
                                     (sc/optional-key :object-key) ssc/NonBlankStr
                                     (sc/optional-key :n)          ssc/PosInt}
   :modified                        user-ts
   (sc/optional-key :imported)      user-ts
   ;; Temporary guard during the actual importing. The value is the job-id in order to
   ;; make sure that we always have access to the current job (failed earlier attempts can
   ;; leave jobs linger)
   (sc/optional-key :importing-job) ssc/ObjectIdStr
   :columns                         [Column]
   (sc/optional-key :previews)      [;; Column-id - raw string
                                     {ssc/ObjectIdStr sc/Str}]
   (sc/optional-key :deleted)       user-ts})

(def valid-config! (sc/validator ImportConfig))

(def ^:private coerce-config
  (stc/coercer ImportConfig
               (some-fn stc/json-coercion-matcher
                        {ssc/ObjectIdStr #(util/pcond-> %
                                            keyword? name)
                         ColumnType      #(update % :type keyword)})))

(sc/defn ->config :- (sc/maybe ImportConfig)
  [m]
  (some-> m (set/rename-keys {:_id :id}) coerce-config))

(defn ->frontend-config
  "Filter out some unnecessary fields."
  [m]
  (some-> (->config m)
          (update :rowdata dissoc :window1904 :object-key)
          (dissoc :previews)))

(defn ->application-config
  "Key columns are not needed in the application imports section."
  [m]
  (some-> (->config m)
          (dissoc :rowdata :organization :modified :imported)
          (update :columns #(remove key-column? %))))

(def ^:private coerce-row
  (stc/coercer Row stc/json-coercion-matcher))

(sc/defn ->row :- (sc/maybe Row)
  [m]
  (some-> m coerce-row))

(defn ->application-row
  "Key cells are not shown in the application imports section."
  [m]
  (some-> (->row m)
          (dissoc :organization :errors)
          (update :cells (fn [cells]
                           (->> cells
                                (remove key-cell?)
                                (into []))))))
