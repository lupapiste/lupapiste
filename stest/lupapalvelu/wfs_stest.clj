(ns lupapalvelu.wfs-stest
  (:require [lupapalvelu.wfs :as wfs]
            [midje.sweet :refer :all]))

(fact "Get our capabilities"
  (wfs/get-our-capabilities) => (contains "WMS_Capabilities"))
