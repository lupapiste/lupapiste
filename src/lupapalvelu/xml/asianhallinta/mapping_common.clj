(ns lupapalvelu.xml.asianhallinta.mapping_common
  (:require [lupapalvelu.document.asianhallinta_canonical :as ahc]
            [sade.util :as util]))

(def yhteystiedot-type
  [{:tag :Jakeluosoite}
   {:tag :Postinumero}
   {:tag :Postitoimipaikka}
   {:tag :Maa}
   {:tag :Email}
   {:tag :Puhelinnumero}])

(def yhteyshenkilo-type
  [{:tag :Etunimi}
   {:tag :Sukunimi}
   {:tag :Yhteystiedot :child yhteystiedot-type}])

(def henkilo-type
  (into yhteyshenkilo-type [{:tag :Henkilotunnus}
                            {:tag :VainSahkoinenAsiointi}
                            {:tag :Turvakielto}]))

(def yritys-type
  [{:tag :Nimi}
   {:tag :Ytunnus}
   {:tag :Yhteystidot :child yhteystiedot-type}
   {:tag :Yhteyshenkilo :child yhteyshenkilo-type}])
