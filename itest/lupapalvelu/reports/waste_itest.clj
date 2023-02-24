(ns lupapalvelu.reports.waste-itest
  (:require [jsonista.core :as json]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(defn err [error]
  (fn [{body :body}]
    (-> body
        (json/read-value (json/object-mapper {:decode-key-fn true}))
        :text
        (= (name error)))))

(fact "No organization access"
  (raw sipoo :waste-report :organizationId "186-R"
       :year "2023")
  => http401?)

(fact "Bad year param"
  (raw sipoo :waste-report :organizationId "753-R")
  => (err :error.missing-parameters)
  (raw sipoo :waste-report :organizationId "753-R"
       :year "bad")
  => (err :a11y.error.year)
  (raw sipoo :waste-report :organizationId "753-R"
       :year "-1")
  => (err :a11y.error.year))

(fact "Good call"
  (raw sipoo :waste-report :organizationId "753-R"
       :year "   2022   ")
  => (contains
       {:status 200
        :headers (contains
                   {"Content-Type" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "Content-Disposition" #"^attachment; filename=\"waste-report_\d+\.xlsx\"$"})}))
