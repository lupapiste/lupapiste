(ns lupapalvelu.property-stest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.property-location :refer :all]
            [lupapalvelu.mongo :as mongo]))

(facts property-lots-info
  (against-background
    (mongo/select :propertyCache anything) => nil
    (mongo/insert-batch :propertyCache anything anything) => nil)

  (fact "single result"
    (let [results (property-lots-info "75341600380021")
          {:keys [kiinttunnus wkt]} (first results)]
      (count results) => 1
      (fact "property id is echoed" kiinttunnus => "75341600380021")
      (fact "wkt" wkt => #"^POLYGON\(")))

  (fact "multiple areas from a single property"
    (let [results (property-lots-info "75342300080021")
          {:keys [kiinttunnus wkt]} (first results)]

      (fact "3 results"
        (count results) => 3
        (map :wkt results) => (n-of #"^POLYGON\(" 3))))

  (fact "multiple property ids, multiple areas"
    (let [results (property-lots-info ["75341600380021" "75342300080021" "75342300020194"])]
      (count results) => (+ 1 3 1) ; 1 + 3 from previous cases + 1 new
      (map :wkt results) => (n-of #"^POLYGON\(" 5))))
