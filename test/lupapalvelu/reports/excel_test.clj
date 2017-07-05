(ns lupapalvelu.reports.excel-test
  (:require [dk.ative.docjure.spreadsheet :as spreadsheet]
            [lupapalvelu.reports.excel :refer :all]
            [clojure.test :refer [is]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]))


(defspec sheet-generation-test
  20
  (prop/for-all [sheets (gen/let
                          [columns (gen/such-that not-empty (gen/vector gen/keyword))
                           sheet-count (gen/such-that (fn [i] (< 0 i)) gen/int)]
                          (gen/such-that
                            not-empty
                            (gen/vector-distinct-by
                              :sheet-name
                              (gen/hash-map :sheet-name (gen/not-empty gen/string-alphanumeric)
                                            :header (gen/return (map str columns))
                                            :row-fn (gen/return (apply juxt columns))
                                            :data (gen/such-that not-empty
                                                                 (gen/vector
                                                                   (gen/fmap
                                                                     (fn [row] (zipmap columns row))
                                                                     (gen/vector
                                                                       (gen/not-empty gen/string-ascii)
                                                                       (count columns))))))
                              {:num-elements sheet-count})))]
                (is (= (count (spreadsheet/sheet-seq (create-workbook sheets)))
                       (count sheets)))))
