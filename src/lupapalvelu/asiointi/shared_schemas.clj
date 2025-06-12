(ns lupapalvelu.asiointi.shared-schemas
  "Like `sade.shared-schemas` but for `malli`."
  (:require [clojure.test.check.generators :as gen]
            [malli.core :as m]
            [sade.date :as date]
            [sade.validators :as validators])
  (:import [java.util Date]
           [org.bson.types ObjectId]))

(set! *warn-on-reflection* true)

(def NonBlankStr [:string {:min 1}])

(def DateString [:string {:json-schema/example "2026-04-29T08:48:54+03:00"
                          :json-schema/format  :date-time}])

(defn- constant-gen
  "Construct a simple generator that produces a non-shrinking value using `source-fn`.

  NOTE: Only use when you just need a value, but don't intend to do anything interesting
  with it. If the actual value matters then you should likely create a proper generator."
  [source-fn]
  (-> (fn [_] (source-fn))
      (gen/fmap (gen/return :ignored))
      gen/no-shrink))

(defn- coerce-date
  [obj]
  (cond
    (instance? Date obj) obj
    (string? obj)        (let [^long ts (-> obj
                                            (date/zoned-parse :iso-datetime)
                                            date/timestamp)]
                           (Date. ts))
    :else                (throw (ex-info "Unexpected type"
                                         {:type  (type obj)
                                          :value obj}))))

(def DateField
  "Malli schema for a `java.util/Date`"
  (m/-simple-schema
    {:type            ::date
     :pred            (fn [v] (instance? Date v))
     :type-properties {:error/message    "should be a Date"
                       :decode/json      coerce-date
                       :decode/string    coerce-date
                       :json-schema/type "string"
                       ;; At the moment the dates are just bookkeeping and we don't do any calculations
                       ;; on them so we can use a simple generator.
                       :gen/gen          (constant-gen (fn [] (Date.)))}}))

(def ObjectIdGen (constant-gen (fn [] (ObjectId.))))

(defn- coerce-object-id
  [obj]
  (cond
    (instance? ObjectId obj) obj
    (string? obj)            (ObjectId. ^String obj)
    :else                    (throw (ex-info "Unexpected type"
                                             {:type  (type obj)
                                              :value obj}))))

(def ObjectIdField
  "Malli schema for a `org.bson.types/ObjectId`"
  (m/-simple-schema
    {:type            ::object-id
     :pred            (fn [v] (instance? ObjectId v))
     :type-properties {:error/message    "should be a ObjectId"
                       :decode/json      coerce-object-id
                       :decode/string    coerce-object-id
                       :json-schema/type "string"
                       :gen/gen          ObjectIdGen}}))

(def ObjectIdString
  "Malli schema for a string representing a ObjectId"
  (m/-simple-schema
    {:type            ::object-id-string
     :pred            (fn [v] (and (string? v)
                                   (ObjectId/isValid v)))
     :type-properties {:error/message       "should be a ObjectId"
                       :decode/string       identity
                       :json-schema/type    "string"
                       :json-schema/example "69f19a7cd7598c608901f162"
                       :gen/gen             (gen/fmap str ObjectIdGen)}}))

(def ApplicationId
  "Malli schema for a string representing Lupapiste application ID"
  (m/-simple-schema
    {:type            ::application-id
     :pred            (fn [v] (and (string? v)
                                   (validators/application-id? v)))
     :type-properties {:error/message       "should be a Lupapiste application id"
                       :decode/string       identity
                       :json-schema/type    "string"
                       :json-schema/example "LP-123-2026-00001"
                       :gen/gen             (gen/let [org-id  (gen/large-integer* {:min 1 :max 999})
                                                      year    (gen/large-integer* {:min 2020 :max 2030})
                                                      counter (gen/large-integer* {:min 0 :max 100})]
                                              (format "LP-%03d-%04d-%05d" org-id year counter))}}))
