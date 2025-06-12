(ns lupapalvelu.conversion.import.itest-util
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.date :as date]))

(set! *warn-on-reflection* true)

(defn make-conversion-document
  ([app-id backend-id converted?]
   (mongo/insert :conversion
                 (cond-> {:_id (mongo/create-id)
                          :backend-id backend-id
                          :organization "753-R"
                          :LP-id app-id
                          :permit-type :R
                          :year (.getYear (date/today))}
                   converted? (assoc :converted true))))
  ([app-id backend-id]
   (make-conversion-document app-id backend-id true)))
