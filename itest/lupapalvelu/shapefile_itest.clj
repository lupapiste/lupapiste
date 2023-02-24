(ns lupapalvelu.shapefile-itest
  "Upload application shapefile. The organization areas shapefile functionality is tested in
  `organization-test`"
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(fact "Pena creates application and uploads shapefile"
  (let [{drawings :drawings
         app-id   :id} (create-application pena :operation "pientalo" :propertyId sipoo-property-id)]
    (fact "Initially, no drawings"
      drawings => empty)
    (fact "Upload good shapefile (Sipoo areas)"
      (:body (decode-response (upload-application-shapefile pena app-id)))
      => ok?)
    (fact "There are seven drawings"
      (:drawings (query-application pena app-id))
      => (just [(contains {:geometry #"MULTIPOLYGON"
                           :id       #"^feature-"
                           :name     "Pohjoinen"})
                (contains {:geometry #"MULTIPOLYGON"
                           :id       #"^feature-"
                           :name     "Keski"})
                (contains {:geometry #"MULTIPOLYGON"
                           :id       #"^feature-"
                           :name     "Länsi"})
                (contains {:geometry #"MULTIPOLYGON"
                           :id       #"^feature-"
                           :name     "Söderkulla"})
                (contains {:geometry #"MULTIPOLYGON"
                           :id       #"^feature-"
                           :name     "Nikkilä"})
                (contains {:geometry #"MULTIPOLYGON"
                           :id       #"^feature-"
                           :name     "Hindsby"})
                (contains {:geometry #"MULTIPOLYGON"
                           :id       #"^feature-"
                           :name     "Ranta"})]
               :in-any-order))
    (fact "Upload bad file (not a zip)"
      (:body (decode-response (upload-application-shapefile pena app-id "dev-resources/test-attachment.txt")))
      => {:ok false
          :text (i18n/localize :fi :error.illegal-shapefile)})))
