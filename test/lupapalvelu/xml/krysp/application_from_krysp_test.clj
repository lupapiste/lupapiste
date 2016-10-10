(ns lupapalvelu.xml.krysp.application-from-krysp-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.xml.krysp.application-from-krysp :refer :all]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.organization :as organization]
            [slingshot.slingshot :refer [try+]]
            [sade.common-reader :as scr]
            [sade.xml :as sxml]))

(testable-privates lupapalvelu.xml.krysp.application-from-krysp
                   get-lp-tunnus
                   get-kuntalupatunnus
                   not-empty-content
                   group-content-by)

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

(defmethod build-application-content-xml #{:lp-tunnus :kuntalupatunnus} [options] (build-luvan-tunniste-tiedot-xml))
(defmethod build-application-content-xml #{:lp-tunnus}       [options] (build-luvan-tunniste-tiedot-xml))
(defmethod build-application-content-xml #{:kuntalupatunnus} [options] (build-luvan-tunniste-tiedot-xml))

(defn build-rakennusvalvonta-asiatieto-xml [app-options]
  {:tag :rakval:rakennusvalvontaAsiatieto,
   :attrs nil,
   :content [{:tag :rakval:RakennusvalvontaAsia,
              :attrs {:gml:id "rluvat.101939"},
              :content (mapv build-application-content-xml app-options)}]})

(defn build-multi-app-xml [options-vec]
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

(facts get-lp-tunnus
  (fact "get tunnus from xml"
    (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001"}])
        scr/strip-xml-namespaces
        get-lp-tunnus) => "LP-123-2016-00001")

  (fact "get tunnus from empty xml"
    (-> (build-multi-app-xml [])
        scr/strip-xml-namespaces
        get-lp-tunnus) => nil)

  (fact "get tunnus from nil"
    (get-lp-tunnus nil) => nil)

  (fact "get tunnus from xml with multiple apps"
    (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001"} {:lp-tunnus "LP-123-2016-00002"}])
        scr/strip-xml-namespaces
        get-lp-tunnus) => "LP-123-2016-00001"))

(facts get-lp-tunnus
  (fact "get tunnus from xml"
    (-> (build-multi-app-xml [{:kuntalupatunnus "XYZ-123-G"}])
        scr/strip-xml-namespaces
        get-kuntalupatunnus) => "XYZ-123-G")

  (fact "get tunnus from empty xml"
    (-> (build-multi-app-xml [])
        scr/strip-xml-namespaces
        get-kuntalupatunnus) => nil)

  (fact "get tunnus from nil"
    (get-kuntalupatunnus nil) => nil)

  (fact "get tunnus from xml with multiple apps"
    (-> (build-multi-app-xml [{:kuntalupatunnus "XYZ-123-G"} {:kuntalupatunnus "XYZ-123-F"}])
        scr/strip-xml-namespaces
        get-kuntalupatunnus) => "XYZ-123-G"))

(facts not-empty-content
  (fact "content with lp-tunnus"
    (let [xml (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00002"}])
                  scr/strip-xml-namespaces)]
      (not-empty-content xml) => xml))

  (fact "content with kuntalupatunnus"
    (let [xml (-> (build-multi-app-xml [{:kuntalupatunnus "XYZ-123-G"}])
                  scr/strip-xml-namespaces)]
      (not-empty-content xml) => xml))

  (fact "content is empty"
    (let [xml (-> (build-multi-app-xml [])
                  scr/strip-xml-namespaces)]
      (not-empty-content xml) => nil))

  (fact "content is nil"
    (not-empty-content nil) => nil))


(facts group-content-by
  (fact "one application xml"
    (let [xml (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}])
                  scr/strip-xml-namespaces)]
      (group-content-by get-lp-tunnus xml) => {"LP-123-2016-00001" xml}))

  (fact "one application xml - group by kuntalupatunnus"
    (let [xml (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}])
                  scr/strip-xml-namespaces)]
      (group-content-by get-kuntalupatunnus xml) => { "XYZ-123-G" xml}))

  (fact "one application with many xmls contents"
    (let [xml (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}
                                        {:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-F"}])
                  scr/strip-xml-namespaces)]
      (group-content-by get-lp-tunnus xml) => {"LP-123-2016-00001" xml}))

  (fact "many applications"
    (let [xml (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}
                                        {:lp-tunnus "LP-123-2016-00002" :kuntalupatunnus "XYZ-123-F"}])
                  scr/strip-xml-namespaces)
          toimituksen-tiedot (sxml/select1 xml [:toimituksenTiedot])
          result (group-content-by get-lp-tunnus xml)]

      result => map?

      (keys result) => (just #{"LP-123-2016-00001" "LP-123-2016-00002"} :in-any-order :gaps-ok)

      (sxml/select1 (result "LP-123-2016-00001") [:toimituksenTiedot]) = toimituksen-tiedot

      (sxml/select1 (result "LP-123-2016-00002") [:toimituksenTiedot]) = toimituksen-tiedot

      (get-lp-tunnus (result "LP-123-2016-00001")) => "LP-123-2016-00001"

      (get-lp-tunnus (result "LP-123-2016-00002")) => "LP-123-2016-00002"

      (get-kuntalupatunnus (result "LP-123-2016-00001")) => "XYZ-123-G"

      (get-kuntalupatunnus (result "LP-123-2016-00002")) => "XYZ-123-F"))

  (fact "nil content"
    (group-content-by get-lp-tunnus nil) => {})

  (fact "empty content"
    (group-content-by get-lp-tunnus {}) => {})

  (fact "empty xml content"
    (->> (build-multi-app-xml [])
         scr/strip-xml-namespaces
         (group-content-by get-lp-tunnus)) => {}))

(facts get-application-xml-by-application-id
  (fact "application found"
    (let [result (get-application-xml-by-application-id {:id "LP-123-2016-00001" :organization "123-R" :permitType "R"})]
      (fact "result is map"
        result => map?)
      (fact "result is xml"
        (keys result) => (just #{:tag :attrs :content}))

      (fact "contains lp-tunnus"
        (get-lp-tunnus result) => "LP-123-2016-00001")

      (fact "content"
        result =>  (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}])
                       scr/strip-xml-namespaces))) => truthy     ; truthy for getting midje understand provided

    (provided (organization/get-krysp-wfs anything) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["LP-123-2016-00001"] :application-id anything) => (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}])))

  (fact "application not found"
    (get-application-xml-by-application-id {:id "LP-123-2016-00001" :organization "123-R" :permitType "R"})  => nil

    (provided (organization/get-krysp-wfs anything) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["LP-123-2016-00001"] :application-id anything) => (build-multi-app-xml [])))

  (fact "raw enabled"
    (get-application-xml-by-application-id {:id "LP-123-2016-00001" :organization "123-R" :permitType "R"} true)  => ..some-content..

    (provided (organization/get-krysp-wfs anything) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["LP-123-2016-00001"] :application-id true) => ..some-content..))

  (fact "no endpoint defined"
    (try+
     (get-application-xml-by-application-id {:id "LP-123-2016-00001" :organization "123-R" :permitType "R"})
     (catch [:sade.core/type :sade.core/fail :text "error.no-legacy-available"] e
       true))  => truthy     ; truthy for getting midje understand provided

    (provided (organization/get-krysp-wfs anything) => nil)))

(facts get-application-xml-by-backend-id
  (fact "application found"
    (let [result (get-application-xml-by-backend-id {:id "LP-123-2016-00001" :organization "123-R" :permitType "R"} "XYZ-123-G")]
      (fact "result is map"
        result => map?)
      (fact "result is xml"
        (keys result) => (just #{:tag :attrs :content}))

      (fact "contains kuntalupatunnus"
        (get-kuntalupatunnus result) => "XYZ-123-G")

      (fact "content"
        result =>  (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}])
                       scr/strip-xml-namespaces))) => truthy     ; truthy for getting midje understand provided

    (provided (organization/get-krysp-wfs anything) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["XYZ-123-G"] :kuntalupatunnus anything) => (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}])))

  (fact "application not found"
    (get-application-xml-by-backend-id {:id "LP-123-2016-00001" :organization "123-R" :permitType "R"}  "XYZ-123-G")  => nil

    (provided (organization/get-krysp-wfs anything) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["XYZ-123-G"] :kuntalupatunnus anything) => (build-multi-app-xml []))))

(facts get-application-xmls
  (facts "single application - by application-id"
    (let [result (get-application-xmls "123-R" "R" :application-id ["LP-123-2016-00001"])]
      (fact "result is map"
        result => map?)

      (fact "result keys"
        (keys result) => ["LP-123-2016-00001"])

      (fact "contains lp-tunnus"
        (get-lp-tunnus (result "LP-123-2016-00001")) => "LP-123-2016-00001")

      (fact "contains kuntalupatunnus"
        (get-kuntalupatunnus (result "LP-123-2016-00001")) => "XYZ-123-G")

      (fact "content"
        (result "LP-123-2016-00001") =>  (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}])
                                             scr/strip-xml-namespaces))) => truthy ; truthy for getting midje understand provided


    (provided (organization/get-krysp-wfs anything) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["LP-123-2016-00001"] :application-id anything) => (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}])))

  (facts "single application - by kuntalupatunnus"
    (let [result (get-application-xmls "123-R" "R" :kuntalupatunnus ["XYZ-123-G"])]
      (fact "result is map"
        result => map?)

      (fact "result keys"
        (keys result) => ["XYZ-123-G"])

      (fact "contains lp-tunnus"
        (get-lp-tunnus (result "XYZ-123-G")) => "LP-123-2016-00001")

      (fact "contains kuntalupatunnus"
        (get-kuntalupatunnus (result "XYZ-123-G")) => "XYZ-123-G")

      (fact "content"
        (result "XYZ-123-G") =>  (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}])
                                     scr/strip-xml-namespaces))) => truthy ; truthy for getting midje understand provided


    (provided (organization/get-krysp-wfs anything) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["XYZ-123-G"] :kuntalupatunnus anything) => (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}])))

  (facts "multiple application - application-id"
    (let [result (get-application-xmls "123-R" "R" :application-id ["LP-123-2016-00001" "LP-123-2016-00002" "LP-123-2016-00003"])]
      (fact "result is map"
        result => map?)

      (fact "result keys - two applications found"
        (keys result) => (just #{"LP-123-2016-00001" "LP-123-2016-00003"} :in-any-order :gaps-ok))

      (fact "first xml contains lp-tunnus"
        (get-lp-tunnus (result "LP-123-2016-00001")) => "LP-123-2016-00001")

      (fact "second xml contains lp-tunnus"
        (get-lp-tunnus (result "LP-123-2016-00003")) => "LP-123-2016-00003")

      (fact "first xml content"
        (result "LP-123-2016-00001") =>  (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001"}])
                                             scr/strip-xml-namespaces))

      (fact "second xml content"
        (result "LP-123-2016-00003") =>  (-> (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00003"}])
                                             scr/strip-xml-namespaces))) => truthy ; truthy for getting midje understand provided


    (provided (organization/get-krysp-wfs anything) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["LP-123-2016-00001" "LP-123-2016-00002" "LP-123-2016-00003"] :application-id anything) => (build-multi-app-xml [{:lp-tunnus "LP-123-2016-00001"} {:lp-tunnus "LP-123-2016-00003"}]))))
