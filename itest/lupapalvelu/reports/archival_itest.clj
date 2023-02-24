(ns lupapalvelu.reports.archival-itest
  (:require [jsonista.core :as json]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.date :refer [timestamp]]))

(apply-remote-minimal)

(def new-year-day (timestamp "1.1.2021"))
(def runeberg (timestamp "5.2.2021"))

(defn err [error]
  (fn [{body :body}]
    (-> body
        (json/read-value (json/object-mapper {:decode-key-fn true}))
        :text
        (= (name error)))))

(fact "Archive not enabled in the organization"
  (raw sipoo :archival-report :organizationId "753-R"
       :startTs new-year-day
       :endTs runeberg)
  => (err :error.archive-not-enabled))

(fact "No organization access"
  (raw sipoo :archival-report :organizationId "186-R"
       :startTs new-year-day
       :endTs runeberg)
  => http401?)

(fact "Bad timestamps"
  (raw jarvenpaa :archival-report :organizationId "186-R"
       :endTs new-year-day
       :startTs runeberg)
  => (err :error.start-greater-than-end)
  (raw jarvenpaa :archival-report :organizationId "186-R"
       :startTs runeberg)
  => (err :error.illegal-value:schema-validation)
  (raw jarvenpaa :archival-report :organizationId "186-R"
       :startTs new-year-day
       :endTs "bad")
  => (err :error.illegal-value:schema-validation))

(fact "Good call"
  (raw jarvenpaa :archival-report :organizationId "186-R"
       :startTs new-year-day
       :endTs runeberg)
  => (contains
       {:status 200
        :headers (contains
                   {"Content-Type" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "Content-Disposition" #"^attachment;filename=\"Arkistointiraportti .*\.xlsx\"$"})}))
