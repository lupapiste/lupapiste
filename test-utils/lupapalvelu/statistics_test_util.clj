(ns lupapalvelu.statistics-test-util
  (:require [sade.date :as date]
            [sade.env :as env]
            [sade.util :as util]
            [schema.core :as sc]))

(defn test-date [year month day]
  (date/zoned-date-time (util/mmap year month day)))

(defn test-datetime [year month day hour minute]
  (date/zoned-date-time (util/mmap year month day hour minute)))

(defn test-ts [year month day hour minute]
  (date/timestamp (util/mmap year month day hour minute)))

(defn test-fixture [f]
  (with-redefs [env/get-config (constantly (-> (env/get-config)
                                               (assoc-in [:feature :statistics-processing] true)
                                               (dissoc :statistics-processing)))]
    (sc/with-fn-validation
      (f))))
