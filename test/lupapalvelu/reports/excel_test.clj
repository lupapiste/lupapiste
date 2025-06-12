(ns lupapalvelu.reports.excel-test
  (:require [dk.ative.docjure.spreadsheet :as spreadsheet]
            [clojure.test :refer [is]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [lupapalvelu.reports.excel :refer :all]
            [sade.strings :as ss]
            [sade.schemas :as ssc]))


(defspec sheet-generation-test
  15
  (prop/for-all [sheets (gen/let
                          [columns (gen/not-empty (gen/vector gen/keyword))
                           sheet-count (gen/such-that (fn [i] (< 0 i)) gen/nat)]
                          (gen/vector-distinct-by
                            (comp ss/upper-case :sheet-name)
                            (gen/hash-map :sheet-name (gen/such-that
                                                        (ssc/max-length-constraint 31)
                                                        (gen/not-empty gen/string-alphanumeric)
                                                        30)
                                          :header (gen/return (map str columns))
                                          :row-fn (gen/return (apply juxt columns))
                                          :data (gen/not-empty
                                                  (gen/vector
                                                    (gen/fmap
                                                      (fn [row] (zipmap columns row))
                                                      (gen/vector
                                                        (gen/not-empty gen/string-ascii)
                                                        (count columns))))))
                            {:num-elements sheet-count}))]
                (is (= (count (spreadsheet/sheet-seq (create-workbook sheets)))
                       (count sheets)))))
