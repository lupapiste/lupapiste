(ns lupapalvelu.property-api-stest
  (:require [lupapalvelu.mml.property.api :as property-api]
            [midje.sweet :refer :all]
            [sade.coordinate :as coord]
            [sade.property :as property]))

(facts "property-detail-by-id!"
  (let [kiinteisto-tunnus "21146300010092"
        tiedot            (property-api/property-details-by-id! kiinteisto-tunnus)]
    (fact "kiinteistotunnus" (:kiinteistotunnus tiedot) => kiinteisto-tunnus)
    (fact "nimi" (:nimi tiedot) => #"^[A-ZÅÄÖa-zåäö -]+$")
    (fact "maapintala" (:maapintaala tiedot) => #"^0\.\d+$")))

(facts "property-info-by-id!"
  (fact "Jakkukyla"
    (property-api/property-info-by-id! "56442100060084")
    => {:propertyId "56442100060084" :municipality "139"
        :name       {:fi "Ii", :sv "Ii"}}))

(def jarvenpaa-point {:x "399309.136" :y "6709508.629"})
(def raasepori-island {:x "296734.231" :y "6647154.2190031"})

(def valid-property-id? property/db-property-id-pattern)

(facts "Lot queries"
  (let [[x y] ["404271" "6693892"]]
    (facts "lot(s) by property id or point"
      (fact "sipoo propertyid"
        (property-api/lot-property-id-by-point! x y)
        => valid-property-id?)
      (fact "tough bastards"
        (fact "raasepori island"
          (property-api/lot-property-id-by-point! (:x raasepori-island) (:y raasepori-island))
          => valid-property-id?)
        (fact "jarvenpaa-point"
          (property-api/lot-property-id-by-point! (:x jarvenpaa-point) (:y jarvenpaa-point))
          => valid-property-id?))

      (facts "lot by property-id returns sequence of lots with Polygon"
        (let [lots (property-api/lots-by-property-id! "71042400010059")]
          (count lots) => 1
          (first lots) => (just {:kiinttunnus "71042400010059"
                                 :lot-id      #"^\d+$"
                                 :wkt         #"^POLYGON \("
                                 :x           coord/valid-x?
                                 :y           coord/valid-y?}))))))
