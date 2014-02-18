(ns lupapalvelu.xml.krysp.ymparistolupa-mapping
  (:require [clojure.walk :as walk]
            [sade.util :as util]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.ymparistolupa-canonical :as ymparistolupa-canonical]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.xml.emit :refer [element-to-xml]]))

(def toiminta-aika-children [{:tag :alkuHetki} ; time
                             {:tag :loppuHetki} ; time
                             {:tag :vuorokausivaihtelu}]) ; string

(def tuotanto-children [{:tag :yhteistuotanto} ; string
                        {:tag :erillistuotanto}]) ; string

(def Varastointipaikka {:tag :Varastointipaikka
                        :child [mapping-common/yksilointitieto
                                mapping-common/alkuHetki
                                (mapping-common/sijaintitieto "yht")
                                {:tag :kuvaus}]}); string

(def ymparistolupaType
  [{:tag :kasittelytietotieto :child [{:tag :Kasittelytieto :child mapping-common/ymp-kasittelytieto-children}]}
   {:tag :luvanTunnistetiedot :child [mapping-common/lupatunnus]}
   ; 0-n {:tag :valvontatapahtumattieto :child []}
   ;{:tag :paatostieto :child []}
   {:tag :lausuntotieto :child [mapping-common/lausunto]}
   ; 0-n {:tag :hakija :child []}
   {:tag :toiminta
    :child [{:tag :peruste} ; string
            {:tag :kuvaus} ;string
            {:tag :luvanHakemisenSyy}]} ;optional string
   ; 0-n {:tag :laitoksentiedot :child []}
   {:tag :voimassaOlevatLuvat
    :child [{:tag :luvat :child [{:tag :lupa :child [{:tag :tunnistetieto} ; string
                                                     {:tag :kuvaus} ; string
                                                     {:tag :liite :child mapping-common/liite-children}]}]}
            {:tag :vakuutukset :child [{:tag :vakuutus :child [{:tag :vakuutusyhtio} ; string
                                                               {:tag :vakuutusnumero}]}]}]} ; string
   {:tag :alueJaYmparisto :child [{:tag :kiinteistonLaitokset :child [{:tag :kiinteistorekisteritunnus} ; string
                                                                      {:tag :laitos}]}]} ; string
   {:tag :tiedotToiminnanSijainnista :child [{:tag :TiedotToiminnanSijainnista :child [mapping-common/yksilointitieto
                                                                                       mapping-common/alkuHetki
                                                                                       (mapping-common/sijaintitieto "yht")
                                                                                       {:tag :ymparistoolosuhteet :child mapping-common/liite-children}
                                                                                       {:tag :ymparistonLaatu :child mapping-common/liite-children}
                                                                                       {:tag :asutus :child mapping-common/liite-children}
                                                                                       {:tag :kaavoitustilanne :child mapping-common/liite-children}
                                                                                       {:tag :rajanaapurit :child [{:tag :luettelo :child mapping-common/liite-children}]}]}]}

   {:tag :referenssiPiste :child []}
   {:tag :koontiKentta :child []}
   ; 0-n {:tag :liitetieto :child []}
   ]
  )

(def ymparistolupa_to_krysp
  {:tag :Ymparistoluvat
   :ns "ymy"
   :attr {:xsi:schemaLocation "http://www.paikkatietopalvelu.fi/gml/yhteiset
                               http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.0/yhteiset.xsd
                               http://www.paikkatietopalvelu.fi/gml/ymparisto/ymparistoluvat
                               http://www.paikkatietopalvelu.fi/gml/ymparisto/ymparistoluvat/2.1.1/ymparistoluvat.xsd
                               http://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/gml.xsd"
          :xmlns:ymy "http://www.paikkatietopalvelu.fi/gml/ymparisto/ymparistoluvat"
          :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
          :xmlns:gml "http://www.opengis.net/gml"
          :xmlns:xlink "http://www.w3.org/1999/xlink"
          :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"}
   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :ymparistolupatieto :child [{:tag :Ymparistolupa :child ymparistolupaType}]}
           ]})


(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent.
   3rd parameter (submitted-application) is not used on YL applications."
  [application lang _ krysp-version output-dir begin-of-link]
  (let [attachments []
        statement-attachments []
        canonical (ymparistolupa-canonical/ymparistolupa-canonical application lang)
        xml (element-to-xml canonical ymparistolupa_to_krysp)
        ]

    (mapping-common/write-to-disk application attachments statement-attachments xml krysp-version output-dir)))

(permit/register-function permit/YL :app-krysp-mapper save-application-as-krysp)

