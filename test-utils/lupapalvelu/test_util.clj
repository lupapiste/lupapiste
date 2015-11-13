(ns lupapalvelu.test-util
  (:require [midje.sweet :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]))

(defn xml-datetime-is-roughly? [^String date1 ^String date2 & interval]
  "Check if two xml datetimes are roughly the same. Default interval 60 000ms (1 min)."
  (let [dt1 (tf/parse date1)
        dt2 (tf/parse date2)
        i (if (< (.getMillis dt1) (.getMillis dt2))
            (t/interval dt1 dt2)
            (t/interval dt2 dt1))]
    (<= (.toDurationMillis i) (or interval 60000))))


(defn dummy-doc [schema-name]
  (let [schema (schemas/get-schema (schemas/get-latest-schema-version) schema-name)
        data   (tools/create-document-data schema (partial tools/dummy-values nil))]
    {:schema-info (:info schema)
     :data        data}))
