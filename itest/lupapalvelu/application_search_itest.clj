(ns lupapalvelu.application-search-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet  :refer :all]
            ))

(apply-remote-minimal)

(facts "Search"
  (let [application-id (create-app-id mikko :municipality sonja-muni :address "Hakukuja 123")]

    application-id => truthy
    (datatables mikko :applications-for-datatables :params {:filter-kind "applications" :filter-state "all"}) => ok?

    )
  )

