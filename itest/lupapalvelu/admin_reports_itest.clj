(ns lupapalvelu.admin-reports-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.date :as date]))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "Reports"
    (fact "Bad dates"
      (decode-body (raw admin :verdicts-contracts-report
                        :startTs (date/timestamp "15.10.2022")
                        :endTs (date/timestamp "14.10.2022")))
      => (err :error.invalid-date))
    (facts "Good dates. Range is spanned from the beginning of the day to the end."
      (doseq [[start end] [["14.10.2022" "15.10.2022"]
                           ["14.10.2022" "14.10.2022"]
                           ["14.10.2022 14:00" "14.10.2022 10:00"]]]
        (fact {:midje/description (str start " - " end)}
          (raw admin :verdicts-contracts-report
               :startTs (date/timestamp start)
               :endTs (date/timestamp end))
          => http200?)))))
