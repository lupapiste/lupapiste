(ns lupapalvelu.sheriff
  (:require [midje.sweet :refer :all]
            [lupapalvelu.sheriff :refer :all]))

(fact point-within-polygon
  (point-within-polygon? [0.5, 0.5]
                         (read-polygon "POLYGON((0.0 0.0, 1.0 0.0, 1.0 1.0, 0.0 1.0, 0.0 0.0))"))
  => true
  (point-within-polygon? [1.5, 1.5]
                         (read-polygon "POLYGON((0.0 0.0, 1.0 0.0, 1.0 1.0, 0.0 1.0, 0.0 0.0))"))
  => false)
