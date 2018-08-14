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

(def asian-tyypin-tarkenne-values                           ; enumeration from schema
  ["Lausuntopyynt\u00f6 ymp\u00e4rist\u00f6nsuojelulain valvontamenettelyst\u00e4"
   "Tiedoksi p\u00e4\u00e4t\u00f6s ymp\u00e4rist\u00f6nsuojelulain valvontamenettelyst\u00e4"
   "Lausuntopyynt\u00f6 pohjavesien suojelusuunnitelmasta"
   "Tiedoksi p\u00e4\u00e4t\u00f6s pohjavesien suojelusuunnitelmasta"
   "Lausuntopyynt\u00f6 maa-ainesten otosta ja k\u00e4sittelyst\u00e4"
   "Tiedoksi p\u00e4\u00e4t\u00f6s maa-ainesten otosta ja k\u00e4sittelyst\u00e4"
   "Lausuntopyynt\u00f6 maakuntakaavasta"
   "Ilmoitus yleiskaavan vireilletulosta"
   "Lausuntopyynt\u00f6 yleiskaavasta"
   "Yleiskaavap\u00e4\u00e4t\u00f6s tiedoksi"
   "Tiedoksi yleiskaavap\u00e4\u00e4t\u00f6ksen t\u00e4yt\u00e4nt\u00f6\u00f6npanom\u00e4\u00e4r\u00e4ys"
   "Ilmoitus yleiskaavan voimaantulosta"
   "Ilmoitus asemakaavan vireilletulosta"
   "Lausuntopyynt\u00f6 asemakaavasta"
   "Asemakaavap\u00e4\u00e4t\u00f6s tiedoksi"
   "Tiedoksi asemakaavan t\u00e4yt\u00e4nt\u00f6\u00f6npanom\u00e4\u00e4r\u00e4ys"
   "Ilmoitus asemakaavan voimaantulosta"
   "Ilmoitus poikkeamislupahakemuksesta"
   "Lausuntopyynt\u00f6 poikkeamishakemuksesta"
   "Tiedoksi p\u00e4\u00e4t\u00f6s poikkeamisluvasta"
   "Lausuntopyynt\u00f6 maisematy\u00f6luvasta"
   "Tiedoksi maisematy\u00f6lupap\u00e4\u00e4t\u00f6kset"
   "Lausuntopyynt\u00f6 purkamisaikomuksesta"
   "Lausuntopyynt\u00f6 purkamislupahakemuksesta"
   "Tiedoksi purkamislupap\u00e4\u00e4t\u00f6s"
   "Lausuntopyynt\u00f6 rakennusluvasta"
   "Tiedoksi p\u00e4\u00e4t\u00f6s rakennusluvasta"
   "Lausuntopyynt\u00f6 rakennusj\u00e4rjestyksest\u00e4"
   "Tiedoksi p\u00e4\u00e4t\u00f6s rakennusj\u00e4rjestyksest\u00e4"
   "Lausuntopyynt\u00f6 suunnittelutarveratkaisusta"
   "Tiedoksi p\u00e4\u00e4t\u00f6s suunnittelutarveratkaisusta"
   "Lausuntopyynt\u00f6 suojeluesityksest\u00e4"
   "Tiedoksi p\u00e4\u00e4t\u00f6s rakennussuojelusta"
   "Lausuntopyynt\u00f6 teiden suoja- ja n\u00e4kem\u00e4alueelle rakentamisen poikkeamishakemuksesta"
   "Tiedoksi p\u00e4\u00e4t\u00f6s teiden suoja- ja n\u00e4kem\u00e4alueelle rakentamisen poikkeamisluvasta"
   "Lausuntopyynt\u00f6 naapurin kuulemisesta teiden suoja- ja n\u00e4kem\u00e4alueen ulkopuolelle rakentamisesta"
   "Tiedoksi p\u00e4\u00e4t\u00f6s suoja- ja n\u00e4kem\u00e4alueelle rakentamisen poikkeamisluvasta"])

