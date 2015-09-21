(ns lupapalvelu.statistics
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all])
  (:import [java.time LocalDate]))

(defn store-pdf-conversion-page-count [count-key count]
  (let [year (.getYear (LocalDate/now))
        count-path (str "years." year "." (name count-key))]
    (mongo/update "statistics" 
                  {:type "pdfa-conversion"} 
                  {$set {:type "pdfa-conversion"}
                   $inc {count-path count}}
                  :upsert true)))
