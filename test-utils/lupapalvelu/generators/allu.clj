(ns lupapalvelu.generators.allu
  (:require [clojure.test.check.generators :as gen]
            [lupapalvelu.backing-system.allu.schemas :refer [allu-date-string]]
            [sade.date :as date]
            [sade.schema-generators :as ssg]))

(def iso-datetime
  (let [start (date/timestamp "1.1.1920")
        end   (date/timestamp "1.1.2030")]
    (gen/fmap (fn [ts]
                (apply date/iso-datetime
                       ts
                       (case (rem ts 4)
                         1 [:utc]
                         2 [:ms]
                         3 [:utc :ms]
                         [])))
              (gen/choose start end))))

(defn register []
  (ssg/register-generator allu-date-string iso-datetime))
