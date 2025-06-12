(ns lupapalvelu.backing-system.asianhallinta.mapping-common)

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
   {:tag :Yhteystiedot :child yhteystiedot-type}
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

(def metatieto-type
  [{:tag :Avain}
   {:tag :Arvo}])

(def liite-type
  [{:tag :Kuvaus}
   {:tag :Tyyppi}
   {:tag :LinkkiLiitteeseen}
   {:tag :Luotu}
   {:tag :Metatiedot :child [{:tag :Metatieto :child metatieto-type}]}])

(def liite-type-1_2
  [{:tag :Kuvaus}
   {:tag :KuvausFi}
   {:tag :KuvausSv}
   {:tag :Tyyppi}
   {:tag :LinkkiLiitteeseen}
   {:tag :Luotu}
   {:tag :Metatiedot :child [{:tag :Metatieto :child metatieto-type}]}])

(def muu-tunnus-type
  [{:tag :Tunnus}
   {:tag :Sovellus}])

(def viitelupa-type
  "Choice, AsianTunnus or MuuTunnus"
  [{:tag :AsianTunnus}
   {:tag :MuuTunnus :child muu-tunnus-type}])

(def toimenpide-type
  [{:tag :ToimenpideTunnus}
   {:tag :ToimenpideTeksti}])

;;
;; Schema version 1.3 support for statements
;;

(def lausuntopyynto-type
  [{:tag :LausuntoTunnus}
   {:tag :Saateteksti}
   {:tag :Pyytaja}
   {:tag :PyyntoPvm}
   {:tag :Maaraaika}])

(def lausunto-type
  (concat
    [{:tag :AsianTunnus}
     {:tag :Lausunnonantaja}
     {:tag :LausuntoPvm}
     {:tag :Puolto}
     {:tag :LausuntoTeksti}]
    lausuntopyynto-type))

(def asian-tyypin-tarkenne-values  ;; enumeration from schema
  ["Lausuntopyyntö ympäristönsuojelulain valvontamenettelystä"
   "Tiedoksi päätös ympäristönsuojelulain valvontamenettelystä"
   "Lausuntopyyntö pohjavesien suojelusuunnitelmasta"
   "Tiedoksi päätös pohjavesien suojelusuunnitelmasta"
   "Lausuntopyyntö maa-ainesten otosta ja käsittelystä"
   "Tiedoksi päätös maa-ainesten otosta ja käsittelystä"
   "Lausuntopyyntö maakuntakaavasta"
   "Ilmoitus yleiskaavan vireilletulosta"
   "Lausuntopyyntö yleiskaavasta"
   "Yleiskaavapäätös tiedoksi"
   "Tiedoksi yleiskaavapäätöksen täytäntöönpanomääräys"
   "Ilmoitus yleiskaavan voimaantulosta"
   "Ilmoitus asemakaavan vireilletulosta"
   "Lausuntopyyntö asemakaavasta"
   "Asemakaavapäätös tiedoksi"
   "Tiedoksi asemakaavan täytäntöönpanomääräys"
   "Ilmoitus asemakaavan voimaantulosta"
   "Ilmoitus poikkeamislupahakemuksesta"
   "Lausuntopyyntö poikkeamishakemuksesta"
   "Tiedoksi päätös poikkeamisluvasta"
   "Lausuntopyyntö maisematyöluvasta"
   "Tiedoksi maisematyölupapäätökset"
   "Lausuntopyyntö purkamisaikomuksesta"
   "Lausuntopyyntö purkamislupahakemuksesta"
   "Tiedoksi purkamislupapäätös"
   "Lausuntopyyntö rakennuslupahakemuksesta"
   "Tiedoksi päätös rakennusluvasta"
   "Lausuntopyyntö rakennusjärjestyksestä"
   "Tiedoksi päätös rakennusjärjestyksestä"
   "Lausuntopyyntö suunnittelutarveratkaisusta"
   "Tiedoksi päätös suunnittelutarveratkaisusta"
   "Lausuntopyyntö suojeluesityksestä"
   "Tiedoksi päätös rakennussuojelusta"
   "Lausuntopyyntö teiden suoja- ja näkemäalueelle rakentamisen poikkeamishakemuksesta"
   "Tiedoksi päätös teiden suoja- ja näkemäalueelle rakentamisen poikkeamisluvasta"
   "Lausuntopyyntö naapurin kuulemisesta teiden suoja- ja näkemäalueen ulkopuolelle rakentamisesta"
   "Tiedoksi päätös suoja- ja näkemäalueelle rakentamisen poikkeamisluvasta"])
