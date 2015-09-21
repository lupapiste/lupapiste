(ns lupapalvelu.wfs-stest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.wfs :as wfs]
            [sade.env :as env]))

(fact "ktjkii returns"
  (against-background (env/feature? :disable-ktj-on-create) => false)
  (let [kiinteisto-tunnus "21146300010092"
        tiedot (wfs/rekisteritiedot-xml kiinteisto-tunnus)]
    (fact "kiinteistotunnus" (:kiinteistotunnus tiedot) => kiinteisto-tunnus)
    (fact "nimi" (:nimi tiedot) => "HIEKKAMETS\u00c4")
    (fact "maapintala" (:maapintaala tiedot) => "0.5990")))
