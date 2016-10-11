(ns lupapalvelu.krysp-test-util)

(defmulti build-application-content-xml (comp set keys))

(defn build-kasittelyn-tilatieto-xml [{pvm :pvm tila :tila}]
  {:tag :rakval:kasittelynTilatieto,
   :attrs nil,
   :content [{:tag :yht:Tilamuutos,
              :attrs nil,
              :content [{:tag :yht:pvm, :attrs nil, :content [pvm]}
                        {:tag :yht:tila, :attrs nil, :content [tila]}
                        {:tag :yht:kasittelija, :attrs nil, :content [{:tag :yht:henkilo, :attrs nil, :content [{:tag :yht:nimi, :attrs nil, :content [{:tag :yht:sukunimi, :attrs nil, :content ["Lupaosasto"]}]}]}]}]}]})

(defmethod build-application-content-xml #{:tila :pvm} [options] (build-kasittelyn-tilatieto-xml options))

(defn build-lp-tunnus-xml [lp-tunnus]
  (when lp-tunnus
    {:tag :yht:muuTunnustieto,
     :attrs nil,
     :content [{:tag :yht:MuuTunnus,
                :attrs nil,
                :content [{:tag :yht:tunnus,
                           :attrs nil,
                           :content [lp-tunnus]}
                          {:tag
                           :yht:sovellus,
                           :attrs nil,
                           :content ["Lupapiste"]}]}]}))

(defn build-kuntalupatunnus-xml [kuntalupatunnus]
  (when kuntalupatunnus
    {:tag :yht:kuntalupatunnus,
     :attrs nil,
     :content [kuntalupatunnus]}))

(defn build-luvan-tunniste-tiedot-xml [{:keys [lp-tunnus kuntalupatunnus]}]
  {:tag :rakval:luvanTunnisteTiedot,
   :attrs nil,
   :content [{:tag
              :yht:LupaTunnus,
              :attrs nil,
              :content (->> [(build-kuntalupatunnus-xml kuntalupatunnus) (build-lp-tunnus-xml lp-tunnus)]
                            (remove nil?)
                            vec)}]})

(defmethod build-application-content-xml #{:lp-tunnus :kuntalupatunnus} [options] (build-luvan-tunniste-tiedot-xml options))
(defmethod build-application-content-xml #{:lp-tunnus}       [options] (build-luvan-tunniste-tiedot-xml options))
(defmethod build-application-content-xml #{:kuntalupatunnus} [options] (build-luvan-tunniste-tiedot-xml options))

(defn build-rakennusvalvonta-asiatieto-xml [app-options]
  {:tag :rakval:rakennusvalvontaAsiatieto,
   :attrs nil,
   :content [{:tag :rakval:RakennusvalvontaAsia,
              :attrs {:gml:id "rluvat.101939"},
              :content (mapv build-application-content-xml app-options)}]})

(defn build-multi-app-xml
  "Returns application xml map with ns. Currently supports only R applications.
  [[{:pvm \"2016-06-06Z\" :tila \"rakennusty\u00f6t aloitettu\"}
    {:lp-tunnus \"LP-123-2016-00001\" :kuntalupatunnus \"123-R-02\"}]
   [{:lp-tunnus \"LP-123-2016-00002\"}]]"
  [options-vec]
  {:tag :rakval:Rakennusvalvonta,
   :attrs {:xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset", :xmlns:ppst "http://www.paikkatietopalvelu.fi/gml/poikkeamispaatos_ja_suunnittelutarveratkaisu", :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance", :xmlns:ogc "http://www.opengis.net/ogc", :xmlns:wfs "http://www.opengis.net/wfs", :xmlns:mkos "http://www.paikkatietopalvelu.fi/gml/opastavattiedot/osoitteet", :xsi:schemaLocation "http://www.paikkatietopalvelu.fi/gml/yhteiset http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.6/yhteiset.xsd http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta/2.2.0/rakennusvalvonta.xsd http://www.opengis.net/wfs http://schemas.opengis.net/wfs/1.1.0/wfs.xsd", :xmlns:gml "http://www.opengis.net/gml", :xmlns:xlink "http://www.w3.org/1999/xlink", :xmlns:ows "http://www.opengis.net/ows", :xmlns:rakval "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta", :xmlns:kiito "http://www.paikkatietopalvelu.fi/gml/kiinteistotoimitus"},
   :content (cons {:tag :rakval:toimituksenTiedot,
                   :attrs nil,
                   :content [{:tag :yht:aineistonnimi, :attrs nil, :content ["CGI KRYSP"]}
                             {:tag :yht:aineistotoimittaja, :attrs nil, :content ["Testikunta"]}
                             {:tag :yht:tila, :attrs nil, :content ["valmis"]}
                             {:tag :yht:toimitusPvm, :attrs nil, :content ["2016-10-05"]}
                             {:tag :yht:kuntakoodi, :attrs nil, :content ["123"]}
                             {:tag :yht:kielitieto, :attrs nil, :content ["Suomi"]}]}
                  (mapv build-rakennusvalvonta-asiatieto-xml options-vec))})
