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

(def hakija-type
  "Choice, Henkilo or Yritys"
  [{:tag :Henkilo :child henkilo-type}
   {:tag :Yritys :child yritys-type}])

(def hakijat-type
  [{:tag :Hakija :child hakija-type}])

(def verkkolaskutustieto-type
  [{:tag :OVT-tunnus}
   {:tag :Verkkolaskutunnus}
   {:tag :Operaattoritunnus}])

(def maksaja-type
  [{:tag :Henkilo :child henkilo-type}
   {:tag :Yritys :child yritys-type}
   {:tag :Laskuviite}
   {:tag :Verkkolaskutustieto :child verkkolaskutustieto-type}])
