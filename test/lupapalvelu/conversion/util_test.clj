(ns lupapalvelu.conversion.util-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.conversion.util :as util]))

(fact "Conversion of permit ids from 'database-format' works as expected"
  (util/db-format->permit-id "12-0089-D 17") => "17-0089-12-D"
  (util/db-format->permit-id "10-0088-A 98") => "98-0088-10-A"
  (util/db-format->permit-id "16-0549-DJ 75") => "75-0549-16-DJ")
