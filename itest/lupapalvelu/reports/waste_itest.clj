(ns lupapalvelu.reports.waste-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (fact "No organization access"
    (raw sipoo :waste-report :organizationId "186-R"
         :year "2023")
    => http401?)

  (fact "Bad year param"
    (decode-body (raw sipoo :waste-report :organizationId "753-R"))
    => (err :error.missing-parameters)
    (decode-body (raw sipoo :waste-report :organizationId "753-R"
                      :year "bad"))
    => (err :a11y.error.year)
    (decode-body (raw sipoo :waste-report :organizationId "753-R"
                      :year "-1"))
    => (err :a11y.error.year))

  (fact "Good call"
    (raw sipoo :waste-report :organizationId "753-R"
         :year "   2022   ")
    => (contains
         {:status  200
          :headers (contains
                     {"Content-Type"        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                      "Content-Disposition" #"^attachment; filename=\"waste-report_\d+\.xlsx\"$"})})))
