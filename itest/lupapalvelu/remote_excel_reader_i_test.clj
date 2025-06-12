(ns lupapalvelu.remote-excel-reader-i-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [lupapalvelu.imports.util :refer [excel-date->zoned-date-time]]
            [lupapalvelu.remote-excel-reader :refer [read-xlsx-rows-fast]]
            [sade.date :as date]))

(set! *warn-on-reflection* true)

(def error {:error :error.read-spreadsheet-fast
            :text  "status: 400"})

(deftest read-xlsx-rows-fast-test
  (testing "Simple table"
    (is (= {:rows       [["ÄläMölÖ title" "Other id (foo)" "Meeting date" "Amount"]
                         ["Hello" "world" "45516" "1"]
                         ["  Howdy  " "" "43466" "2"]
                         ["Something (totally) different" "123" "45693" "2.5"]
                         ["" "" ""]]
            :window1904 false}
           (read-xlsx-rows-fast "bob-id" (io/file "dev-resources/simple-table.xlsx")))))
  (testing "Not an excel file"
    (is (= error
           (read-xlsx-rows-fast "bob-id" (io/file "dev-resources/cake.png"))))
    (is (= error
           (read-xlsx-rows-fast "bob-id" (io/file "dev-resources/sipoon_alueet.zip")
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))))
  (testing "File not found"
    (is (thrown? java.nio.file.NoSuchFileException
                 (read-xlsx-rows-fast "bob-id" (io/file (str (random-uuid) ".xlsx"))))))
  (testing "Window 1904 dates"
    (is (= {:rows       [["ÄläMölÖ title" "Other id (foo)" "Meeting date" "Amount"]
                         ["Hello" "world" "44054" "1"]
                         ["  Howdy  " "" "42004" "2"]
                         ["Something (totally) different" "123" "44231" "2.5"]
                         ["" "" ""]]
            :window1904 true}
           (read-xlsx-rows-fast "bob-id" (io/file "dev-resources/simple-table-1904.xlsx"))))
    (is (= (excel-date->zoned-date-time 45516)
           (excel-date->zoned-date-time 44054 true)
           (date/zoned-date-time "12.8.2024")))))
