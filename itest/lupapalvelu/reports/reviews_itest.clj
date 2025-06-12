(ns lupapalvelu.reports.reviews-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (fact "No organization access"
    (raw sipoo :reviews-period-report :organizationId "186-R") => http401?)

  (fact "Bad param"
    (decode-body (raw sipoo :reviews-period-report)) => (err :error.missing-parameters))

  (fact "Good call"
    (raw sipoo :reviews-period-report :organizationId "753-R")
    => (contains
         {:status  200
          :headers (contains
                     {"Content-Type"        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                      "Content-Disposition" #"^attachment; filename=\"Pidetyt katselmukset_\d+\.xlsx\"$"})})))
