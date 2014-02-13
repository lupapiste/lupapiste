(ns lupapalvelu.xml.krysp.ymparisto-ilmoitukset-mapping
  (:require [lupapalvelu.xml.krysp.mapping-common :as mapping-common]))

(def ilmoitus_to_krysp
  {:tag :Ilmoitukset
   :ns "ymi"
   :attr {:xsi:schemaLocation "http://www.paikkatietopalvelu.fi/gml/yhteiset
                               http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.0/yhteiset.xsd
                               http://www.paikkatietopalvelu.fi/gml/ymparisto/ilmoitukset
                               http://www.paikkatietopalvelu.fi/gml/ymparisto/ilmoitukset/2.1.1/ilmoitukset.xsd"
          :xmlns:ymi "http://www.paikkatietopalvelu.fi/gml/ymparisto/ilmoitukset"
          :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
          :xmlns:xlink "http://www.w3.org/1999/xlink"
          :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"}
   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           :tag :melutarina
           :child [{:tag :kasittelytietotieto
                    :child mapping-common/kasittelytieto}
                   {:tag :luvanTunnisteTiedot
                    :child [mapping-common/lupatunnus]}
                   {:tag :lausuntotieto
                    :child mapping-common/lausunto}
                   {:tag :ilmoittaja
                    :child [{:tag :nimi}
                            {:tag :postiosoite :child mapping-common/postiosoite-children-ns-yht}
                            {:tag :sahkopostiosoite}
                            {:tag :yhteyshenkilo :child mapping-common/henkilo-child-ns-yht}]}
                   {:tag :toiminnanSijainti :child [{:tag :Osoite :child mapping-common/postiosoite-children-ns-yht}
                                                   {:tag :Kunta}
                                                   mapping-common/sijantiType
                                                   {:tag :Kiinteistorekisterinumero}]}
                   {:tag :toimintatieto :child [{:tag :Toiminta :child [{:tag :rakentaminen :child [{:tag :louhinta}
                                                                                                    {:tag :murskaus}
                                                                                                    {:tag :paalutus}
                                                                                                    {:tag :muu}]}
                                                                        {:tag :tapahtuma :child [{:tag :ulkoilmakonsertti}
                                                                                                 {:tag :muu}]}]}]}
                   {:tag :toiminnanKesto :child [{:tag :alkuHetki}
                                                 {:tag :loppuHetki}]}
                   {:tag :melutiedot :child [{:tag :koneidenLkm}
                                             {:tag :melutaso :child [{:tag :db}
                                                                     {:tag :paiva}
                                                                     {:tag :yo}
                                                                     {:tag :mittaaja}]}]}
                   {:tag :koontikentta}]]}
  )