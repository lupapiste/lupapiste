(ns lupapalvelu.conversion.config-test
  "Simple-table.xlsx contents:

  +------------------------------+---------------+-------------+-------+
  |ÄläMölÖ title                 |Other id (foo) |Meeting date |Amount |
  +------------------------------+---------------+-------------+-------+
  |Hello                         |world          |    12.8.2024|   1,00|
  +------------------------------+---------------+-------------+-------+
  |  Howdy                       |               |     1.1.2019|   2,00|
  +------------------------------+---------------+-------------+-------+
  |Something (totally) different |            123|     5.2.2025|   2,50|
  +------------------------------+---------------+-------------+-------+
  |                              |               |             |       |
  +------------------------------+---------------+-------------+-------+
  "
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.config :as cfg]
            [sade.date :as date]))

(deftest excel->maps-test
  (testing "excel files can be opened"
    (is (= [{:älä-mölö-title       "Hello"
             :other-id-foo         "world"
             :meeting-date         45516.0
             :amount               1.0}
            {:älä-mölö-title       "Howdy"
             :other-id-foo         nil
             :meeting-date         43466.0
             :amount               2.0}
            {:älä-mölö-title       "Something (totally) different"
             :other-id-foo         123.0
             :meeting-date         45693.0
             :amount               2.5}
            {:älä-mölö-title nil :other-id-foo nil :meeting-date ""}]
           (cfg/excel->maps "dev-resources/simple-table.xlsx"))))
  (testing "explicit date columns"
    (is (= [{:älä-mölö-title       "Hello"
             :other-id-foo         "world"
             :meeting-date         (date/zoned-date-time "12.8.2024")
             :amount               1.0}
            {:älä-mölö-title       "Howdy"
             :other-id-foo         nil
             :meeting-date         (date/zoned-date-time "1.1.2019")
             :amount               2.0}
            {:älä-mölö-title       "Something (totally) different"
             :other-id-foo         123.0
             :meeting-date         (date/zoned-date-time "5.2.2025")
             :amount               2.5}
            {:älä-mölö-title nil :other-id-foo nil :meeting-date nil}]
           (cfg/excel->maps "dev-resources/simple-table.xlsx" #{:meeting-date})))))

