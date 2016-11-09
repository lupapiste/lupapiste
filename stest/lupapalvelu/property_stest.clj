(ns lupapalvelu.property-stest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.property-location :refer :all]
            [lupapalvelu.mongo :as mongo]))

(facts property-location-info
  (against-background
    (mongo/select :propertyCache anything) => nil
    (mongo/insert-batch :propertyCache anything anything) => nil)

  (fact "single result"
    (let [results (property-location-info "75341600380021")
          {:keys [kiinttunnus wkt]} (first results)]
      (count results) => 1
      (fact "property id is echoed" kiinttunnus => "75341600380021")
      (fact "wkt" wkt => #"^POLYGON\(")))

  (fact "multiple areas from a single property"
    (let [results (property-location-info "75342300080021")
          {:keys [kiinttunnus wkt]} (first results)]

      (fact "3 results"
        (count results) => 3
        (map :wkt results) => (n-of #"^POLYGON\(" 3))))

  (fact "multiple property ids, multiple areas"
    (let [results (property-location-info ["75341600380021" "75342300050029" "75342300020195"])]
      (count results) => (+ 1 3 2) ; 1 + 3 from previous cases + 2 new
      (map :wkt results) => (n-of #"^POLYGON\(" 6))))
