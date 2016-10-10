(ns lupapalvelu.xml.krysp.reader-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [clj-time.coerce :as coerce]
            [sade.xml :as xml]
            [lupapalvelu.xml.krysp.reader :refer [->verdicts get-app-info-from-message application-state]]
            [lupapalvelu.xml.krysp.common-reader :refer [rakval-case-type property-equals wfs-krysp-url]]
            [lupapalvelu.krysp-test-util :refer [build-multi-app-xml]]
            [sade.common-reader :as cr]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]))

(defn- to-timestamp [yyyy-mm-dd]
  (coerce/to-long (coerce/from-string yyyy-mm-dd)))

(testable-privates lupapalvelu.xml.krysp.reader
  ->standard-verdicts
  standard-verdicts-validator
  simple-verdicts-validator
  ->simple-verdicts
  tj-suunnittelija-verdicts-validator
  party-with-paatos-data
  valid-sijaistustieto?
  osapuoli-path-key-mapping)

(fact "property-equals returns url-encoded data"
  (property-equals "_a_" "_b_") => "%3CPropertyIsEqualTo%3E%3CPropertyName%3E_a_%3C%2FPropertyName%3E%3CLiteral%3E_b_%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")

(fact "property-equals returns url-encoded xml-encoded data"
  (property-equals "<a>" "<b>") => "%3CPropertyIsEqualTo%3E%3CPropertyName%3E%26lt%3Ba%26gt%3B%3C%2FPropertyName%3E%3CLiteral%3E%26lt%3Bb%26gt%3B%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")

(defn verdict-skeleton [poytakirja-xml]
  {:tag :paatostieto
   :content [{:tag :paatostieto
              :content [{:tag :Paatos
                         :content [{:tag :poytakirja :content poytakirja-xml}]}]}]})

(def future-verdict (verdict-skeleton [{:tag :paatoskoodi :content ["hyv\u00e4ksytty"]}
                                       {:tag :paatoksentekija :content [""]}
                                       {:tag :paatospvm :content ["1970-01-02"]}]))

(def past-verdict (verdict-skeleton [{:tag :paatoskoodi :content ["hyv\u00e4ksytty"]}
                                     {:tag :paatoksentekija :content [""]}
                                     {:tag :paatospvm :content ["1970-01-01"]}]))

(facts "standard-verdicts-validator"
  (against-background
    (sade.util/get-timestamp-ago :day 1) => (+ (to-timestamp "1970-01-01") 100))
  (fact "Missing details"
    (standard-verdicts-validator (verdict-skeleton []) {}) => {:ok false, :text "info.paatos-details-missing"})
  (fact "Future date"
    (standard-verdicts-validator future-verdict {}) => {:ok false, :text "info.paatos-future-date"} )
  (fact "Past date"
    (standard-verdicts-validator past-verdict {}) => nil))

(defn simple-verdicts-skeleton [state verdict-date]
  {:tag :Kayttolupa
   :content [{:tag :kasittelytietotieto
              :content [{:tag :Kasittelytieto
                         :content [{:tag :muutosHetki    :content ["2014-01-29T13:57:15"]}
                                   {:tag :hakemuksenTila :content [state]}]}]}
             {:tag :paatostieto :content [{:tag :Paatos
                                           :content [{:tag :paatosdokumentinPvm :content [verdict-date]}]}]}]})

(facts "simple-verdicts-skeleton"
  (against-background
    (sade.core/now) => 100)
  (fact "Empty state"
    (simple-verdicts-validator (simple-verdicts-skeleton "" "") {}) => {:ok false, :text "info.application-backend-preverdict-state"})
  (fact "Still in application state"
    (simple-verdicts-validator (simple-verdicts-skeleton "hakemus" "1970-01-02") {}) => {:ok false, :text "info.application-backend-preverdict-state"})
  (fact "Nil date"
    (simple-verdicts-validator (simple-verdicts-skeleton nil nil) {}) => {:ok false, :text "info.paatos-date-missing"})
  (fact "Future date"
    (simple-verdicts-validator (simple-verdicts-skeleton "OK" "1970-01-02") {}) => {:ok false, :text "info.paatos-future-date"} )
  (fact "Past date"
    (simple-verdicts-validator (simple-verdicts-skeleton "OK" "1970-01-01") {}) => nil))

(facts "KRYSP verdict"
  (let [xml (xml/parse (slurp "resources/krysp/dev/verdict.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]

    (fact "xml is parsed" cases => truthy)

    (fact "validator finds verdicts" (standard-verdicts-validator xml {}) => nil)

    (fact "xml has 2 cases" (count cases) => 2)
    (fact "second case has 2 verdicts" (-> cases last :paatokset count) => 2)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")

    (let [verdict        (-> cases last :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)]

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:autopaikkojaEnintaan lupamaaraykset) => 10
        (:autopaikkojaVahintaan lupamaaraykset) => 1
        (:autopaikkojaRakennettava lupamaaraykset) => 2
        (:autopaikkojaRakennettu lupamaaraykset) => 0
        (:autopaikkojaKiinteistolla lupamaaraykset) => 7
        (:autopaikkojaUlkopuolella lupamaaraykset) => 3
        (:kerrosala lupamaaraykset) => "100"
        (:kokonaisala lupamaaraykset) => "110"
        (:vaaditutTyonjohtajat lupamaaraykset) => "Vastaava ty\u00f6njohtaja, Vastaava IV-ty\u00f6njohtaja, Ty\u00f6njohtaja"
        (let [katselmukset (:vaaditutKatselmukset lupamaaraykset)
              maaraykset   (:maaraykset lupamaaraykset)]
          (facts "katselmukset"
            (count katselmukset) => 2
            (:katselmuksenLaji (first katselmukset)) => "aloituskokous"
            (:tarkastuksenTaiKatselmuksenNimi (last katselmukset)) => "K\u00e4ytt\u00f6\u00f6nottotarkastus")
          (facts "m\u00e4\u00e4r\u00e4ykset"
            (count maaraykset) => 2
            (:sisalto (first maaraykset)) => "Radontekninen suunnitelma"
            (:maaraysaika (first maaraykset)) => (to-timestamp "2013-08-28")
            (:toteutusHetki (last maaraykset)) => (to-timestamp "2013-08-31")))

        (facts "second verdict"
          (let [katselmukset2 (-> cases last :paatokset last :lupamaaraykset :vaaditutKatselmukset)]
            (count katselmukset2) => 1
            katselmukset2 => sequential?)
          (let [maaraykset2 (-> cases last :paatokset last :lupamaaraykset :maaraykset)]
            (count maaraykset2) => 1
            maaraykset2 => sequential?))
        )

      (facts "paivamaarat data is correct"
        paivamaarat    => truthy
        (:aloitettava paivamaarat) => (to-timestamp "2013-09-01")
        (:lainvoimainen paivamaarat) => (to-timestamp "2013-09-02")
        (:voimassaHetki paivamaarat) => (to-timestamp "2013-09-03")
        (:raukeamis paivamaarat) => (to-timestamp "2013-09-04")
        (:anto paivamaarat) => (to-timestamp "2013-09-05")
        (:viimeinenValitus paivamaarat) => (to-timestamp "2013-09-06")
        (:julkipano paivamaarat) => (to-timestamp "2013-09-07"))

      (facts "p\u00f6yt\u00e4kirjat data is correct"
        poytakirjat    => truthy
        (count poytakirjat) => 2

        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:paatos pk1) => "P\u00e4\u00e4t\u00f6s 1"
          (:paatoskoodi pk1) => "my\u00f6nnetty"
          (:paatoksentekija pk1) => "viranomainen"
          (:paatospvm pk1) => (to-timestamp "2013-08-01")
          (:pykala pk1) => 1
          (:kuvaus liite) => "kuvaus 1"
          (:linkkiliitteeseen liite) => "http://localhost:8000/dev/sample-verdict.pdf"
          (:muokkausHetki liite) => (to-timestamp "2013-09-01T12:00:00")
          (:versionumero liite) => "1"
          (get-in liite [:tekija :henkilo :nimi :sukunimi]) => "Tarkkanen"
          (:tyyppi liite) => "paatos"
          (:metadata liite) => {:nimi "arvo"})

        (facts "second verdict"
          (let [poytakirjat2 (-> cases last :paatokset last :poytakirjat)]
            (count poytakirjat2) => 1
            poytakirjat2 => sequential?))))))

(facts "KRYSP verdict 2.1.8"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-2.1.8.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]

    (fact "xml is parsed" cases => truthy)
    (fact "validator finds verdicts" (standard-verdicts-validator xml {}) => nil)

    (let [verdict        (-> cases last :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)
          maaraykset     (:maaraykset lupamaaraykset)
          vaaditut-erityissuunnitelmat (:vaaditutErityissuunnitelmat lupamaaraykset)]

      (fact "vaaditut erityissuunnitelmat"
          vaaditut-erityissuunnitelmat => sequential?
          vaaditut-erityissuunnitelmat => (just ["ES 1" "ES 22" "ES 333"] :in-any-order))

      (fact "m\u00e4\u00e4r\u00e4ykset"
        (count maaraykset) => 2
        (:sisalto (first maaraykset)) => "Radontekninen suunnitelma"
        (:maaraysaika (first maaraykset)) => (to-timestamp "2013-08-28")
        (:toteutusHetki (last maaraykset)) => (to-timestamp "2013-08-31")))))

(facts "KRYSP verdict 2.1.8"
 (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-2.1.8-foremen.xml"))
       cases (->verdicts xml :R permit/read-verdict-xml)]

   (fact "xml is parsed" cases => truthy)
   (fact "validator finds verdicts" (standard-verdicts-validator xml {}) => nil)

   (let [verdict (first (:paatokset (last cases)))
         lupamaaraykset (:lupamaaraykset verdict)
         vaaditut-erityissuunnitelmat (:vaaditutErityissuunnitelmat lupamaaraykset)]

     ;; In xml message, just one vaadittuErityissuunnitelma element is provided
     ;; where there are multiple "vaadittuErityissuunnitelma"s combined as one string, separated by line break.
     ;; Testing here that the reader divides those as different elements properly.
     (fact "vaaditut erityissuunnitelmat style"
         vaaditut-erityissuunnitelmat => sequential?
         vaaditut-erityissuunnitelmat => (just ["Rakennesuunnitelmat" "Vesi- ja viem\u00e4risuunnitelmat" "Ilmanvaihtosuunnitelmat"] :in-any-order)))))


(facts "KRYSP verdict 2.2.0"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-2.2.0.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]

    (fact "xml is parsed" cases => truthy)
    (fact "validator finds verdicts" (standard-verdicts-validator xml {}) => nil)

    (let [verdict        (-> cases last :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)
          maaraykset     (:maaraykset lupamaaraykset)]

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:rakennusoikeudellinenKerrosala lupamaaraykset) => "101"
        (:vaaditutTyonjohtajat lupamaaraykset) => "IV-ty\u00f6njohtaja, KVV-ty\u00f6njohtaja, vastaava ty\u00f6njohtaja"
        (:vaaditutErityissuunnitelmat lupamaaraykset) => (just ["ES 1" "ES 22" "ES 333"] :in-any-order))

      (fact "m\u00e4\u00e4r\u00e4ykset"
        (count maaraykset) => 2
        (:sisalto (first maaraykset)) => "Radontekninen suunnitelma"
        (:maaraysaika (first maaraykset)) => (to-timestamp "2013-08-28")
        (:toteutusHetki (last maaraykset)) => (to-timestamp "2013-08-31")))))

(facts "CGI sample verdict"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 case" (count cases) => 1)
    (fact "case has 1 verdict" (-> cases last :paatokset count) => 1)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")

    (let [verdict         (-> cases first :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)
          katselmukset   (:vaaditutKatselmukset lupamaaraykset)]

      (fact "paatos" verdict => truthy)
      (fact "lupamaaraykset is parsed" lupamaaraykset => truthy)

      (facts "katselmukset"
        (count katselmukset) => 1
        (:katselmuksenLaji (first katselmukset)) => "loppukatselmus")

      (facts "paivamaarat data is correct"
        paivamaarat    => truthy
        (:aloitettava paivamaarat) => pos?
        (:aloitettava paivamaarat) => (to-timestamp "2016-10-08")
        (:lainvoimainen paivamaarat) => (to-timestamp "2013-10-08")
        (:voimassaHetki paivamaarat) => (to-timestamp "2018-10-08")
        (:raukeamis paivamaarat) => nil
        (:anto paivamaarat) => (to-timestamp "2013-09-06")
        (:viimeinenValitus paivamaarat) => (to-timestamp "2013-10-07")
        (:julkipano paivamaarat) => (to-timestamp "2013-09-04"))


      (facts "p\u00f6yt\u00e4kirjat data is correct"
        poytakirjat    => truthy
        (count poytakirjat) => 1

        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:paatos pk1) => nil
          (:paatoskoodi pk1) => "my\u00f6nnetty"
          (:paatoksentekija pk1) => "Rakennuslautakunta"
          (:paatospvm pk1) => (to-timestamp "2013-09-03")
          (:pykala pk1) => 12
          (:kuvaus liite) => "P\u00e4\u00e4t\u00f6sote"
          (:linkkiliitteeseen liite) => "http://localhost:8000/dev/sample-attachment.txt"
          (:muokkausHetki liite) => (to-timestamp "2013-09-03T15:27:46")
          (:tyyppi liite) => "P\u00e4\u00e4t\u00f6sote")))))

(facts "Tekla sample verdict"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-buildings.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]

    (fact "xml is parsed" cases => truthy)

    (fact "validator finds verdicts" (standard-verdicts-validator xml {}) => nil)

    (fact "xml has one case" (count cases) => 1)
    (fact "case has 1 verdict" (-> cases last :paatokset count) => 1)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "109-2014-140")

    (let [verdict        (-> cases first :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)
          katselmukset   (:vaaditutKatselmukset lupamaaraykset)
          maaraykset     (:maaraykset lupamaaraykset)]

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:kerrosala lupamaaraykset) => "134.000"
        (:kokonaisala lupamaaraykset) => "134.000"
        (:vaaditutTyonjohtajat lupamaaraykset) => "Vastaava ty\u00f6njohtaja, KVV-ty\u00f6njohtaja, IV-ty\u00f6njohtaja"
        (facts "katselmukset"
          (count katselmukset) => 4
          (:katselmuksenLaji (first katselmukset)) => "aloituskokous"
          (:katselmuksenLaji (last katselmukset)) => "loppukatselmus")
        (facts "m\u00e4\u00e4r\u00e4ykset"
          (count maaraykset) => 3
          (:sisalto (first maaraykset)) => "Vastaavan ty\u00f6njohtajan on yll\u00e4pidett\u00e4v\u00e4 rakennusty\u00f6n tarkastusasiakirjaa, johon eri rakennusvaiheiden vastuuhenkil\u00f6t ja ty\u00f6vaiheiden tarkastuksia suorittavat henkil\u00f6t varmentavat tekem\u00e4ns\u00e4 tarkastukset (MRL 150 \u00a7 3. mom., MRA 77 \u00a7)."))
      )))

(facts "case not found"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-not-found.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has no cases" (count cases) => 0)
    (fact "validator does not find verdicts" (standard-verdicts-validator xml {}) => {:ok false, :text "info.no-verdicts-found-from-backend"})))

(facts "nil xml"
  (let [cases (->verdicts nil :R permit/read-verdict-xml)]
    (seq cases) => nil
    (count cases) => 0))

(facts "no verdicts"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-no-verdicts.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 case" (count cases) => 1)
    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")
    (fact "validator does not find verdicts" (standard-verdicts-validator xml {}) => {:ok false, :text "info.no-verdicts-found-from-backend"})
    (fact "case has no verdicts" (-> cases last :paatokset count) => 0)))



(facts "KRYSP verdict with reviews"
  (let [xml (-> "resources/krysp/dev/r-verdict-review.xml" slurp xml/parse)
        reviews (lupapalvelu.xml.krysp.review-reader/xml->reviews xml)
        katselmus-tasks (map (partial lupapalvelu.tasks/katselmus->task {} {} {}) reviews)
        aloitus-review-task (nth katselmus-tasks 0)
        non-empty (complement clojure.string/blank?)]
    (fact "xml has 11 reviews" (count reviews) => 11)
    (fact "huomautukset" (get-in aloitus-review-task [:data :katselmus :huomautukset :kuvaus :value]) => non-empty)
    (fact "katselmuksenLaji" (get-in aloitus-review-task [:data :katselmuksenLaji :value]) => "aloituskokous")
    (fact "tunnustieto" (get-in aloitus-review-task [:data :muuTunnus]) => truthy)))

;; YA verdict

(facts "KRYSP ya-verdict"
  (let [xml (xml/parse (slurp "resources/krysp/dev/verdict-ya.xml"))
        cases (->verdicts xml :YA permit/read-verdict-xml)]

    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 cases" (count cases) => 1)
    (fact "has 1 verdicts" (-> cases last :paatokset count) => 1)

    (fact "validator finds verdicts" (simple-verdicts-validator xml {}) => nil)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "523")

    (let [verdict (first (:paatokset (last cases)))
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)]

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:takuuaikaPaivat lupamaaraykset) => "760"
        (let [muutMaaraykset (:muutMaaraykset lupamaaraykset)]
          muutMaaraykset => sequential?
          (count muutMaaraykset) => 2
          (last muutMaaraykset) => "Kaivu: Viheralueet rakennettava."))

      (facts "paivamaarat data is correct"
        paivamaarat => truthy
        (:paatosdokumentinPvm paivamaarat) => (to-timestamp "2014-01-29"))

      (facts "p\u00f6yt\u00e4kirjat data is correct"
        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:kuvaus liite) => "liite"
          (:linkkiliitteeseen liite) => "http://localhost:8000/dev/sample-attachment.txt"
          (:muokkausHetki liite) => (to-timestamp "2014-01-29T13:58:15")
          (:tyyppi liite) => "Muu liite")))))

(facts "Ymparisto verdicts"
  (doseq [permit-type ["YL" "MAL" "VVVL"]]
    (let [xml (xml/parse (slurp (str "resources/krysp/dev/verdict-" permit-type ".xml")))
          cases (->verdicts xml permit-type permit/read-verdict-xml)]

      (fact "xml is parsed" cases => truthy)
      (fact "xml has 1 cases" (count cases) => 1)
      (fact "has 1 verdicts" (-> cases last :paatokset count) => 1)

      (fact "validator finds verdicts" (simple-verdicts-validator xml {}) => nil)

      (fact "kuntalupatunnus"
        (:kuntalupatunnus (last cases)) => #(.startsWith % "638-2014-"))

    (let [verdict (first (:paatokset (last cases)))
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)]

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:takuuaikaPaivat lupamaaraykset) => "5"
        (let [muutMaaraykset (:muutMaaraykset lupamaaraykset)]
          muutMaaraykset => sequential?
          (count muutMaaraykset) => 1
          (last muutMaaraykset) => "Lupaehdot vapaana tekstin\u00e4"))

      (facts "paivamaarat data is correct"
        paivamaarat => truthy
        (:paatosdokumentinPvm paivamaarat) => (to-timestamp "2014-04-11"))

      (facts "p\u00f6yt\u00e4kirjat data is correct"
        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:kuvaus liite) => "paatoksenTiedot"
          (:linkkiliitteeseen liite) => "http://localhost:8000/dev/sample-attachment.txt"
          (:muokkausHetki liite) => (to-timestamp "2014-03-29T13:58:15")
          (:tyyppi liite) => "Muu liite"))))))

(facts "Maaraykset from verdict message"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-empty-maarays-element.xml"))
        verdicts (->verdicts xml :R permit/read-verdict-xml)
        paatokset (:paatokset (first verdicts))
        lupamaaraykset (:lupamaaraykset (first paatokset))
        maaraykset (:maaraykset lupamaaraykset)]
    maaraykset =not=> (contains [nil])
    (count maaraykset) => 3))

(facts "wfs-krysp-url works correctly"
  (fact "without ? returns url with ?"
    (wfs-krysp-url "http://localhost" rakval-case-type (property-equals "test" "lp-1")) => "http://localhost?request=GetFeature&typeName=rakval%3ARakennusvalvontaAsia&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Etest%3C%2FPropertyName%3E%3CLiteral%3Elp-1%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")
  (fact "with ? returns url with ?"
    (wfs-krysp-url "http://localhost" rakval-case-type (property-equals "test" "lp-1")) => "http://localhost?request=GetFeature&typeName=rakval%3ARakennusvalvontaAsia&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Etest%3C%2FPropertyName%3E%3CLiteral%3Elp-1%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")
  (fact "without extraparam returns correct"
    (wfs-krysp-url "http://localhost?output=KRYSP" rakval-case-type (property-equals "test" "lp-1")) => "http://localhost?output=KRYSP&request=GetFeature&typeName=rakval%3ARakennusvalvontaAsia&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Etest%3C%2FPropertyName%3E%3CLiteral%3Elp-1%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E"))



(facts* "Testing information parsed from a verdict xml message for application creation"
  (let [xml (xml/parse (slurp "resources/krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml"))
        info (get-app-info-from-message xml "14-0241-R 3") => truthy
        {:keys [id kuntalupatunnus municipality rakennusvalvontaasianKuvaus vahainenPoikkeaminen rakennuspaikka ensimmainen-rakennus hakijat]} info]

    (fact "info contains the needed keys" (every? (partial contains info)
                                            [:id :kuntalupatunnus :municipality :rakennusvalvontaasianKuvaus :vahainenPoikkeaminen :rakennuspaikka :ensimmainen-rakennus :hakijat]))

    (fact "invalid kuntalupatunnus" (get-app-info-from-message xml "invalid-kuntalupatunnus") => nil)

    (fact "kuntalupatunnus" kuntalupatunnus => "14-0241-R 3")
    (fact "municipality" municipality => "186")
    (fact "rakennusvalvontaasianKuvaus" rakennusvalvontaasianKuvaus => "Rakennetaan yksikerroksinen lautaverhottu omakotitalo jossa kytketty autokatos/ varasto.")
    (fact "vahainenPoikkeaminen" vahainenPoikkeaminen => "Poikekkaa meill\u00e4!")
    (facts "hakijat"
      (fact "count" (count hakijat) => 6)
      (fact "with email address" (filter identity (map #(get-in % [:henkilo :sahkopostiosoite]) hakijat)) => (just #{"pena@example.com" "mikko@example.com" " \\t   "})))

    (facts "Rakennuspaikka"
      (let [{:keys [x y address propertyId] :as rakennuspaikka} rakennuspaikka]
        (fact "contains all the needed keys" (every? (-> rakennuspaikka keys set) [:x :y :address :propertyId]))
        (fact "x" x => #(and (instance? Double %) (= 393033.614 %)))
        (fact "y" y => #(and (instance? Double %) (= 6707228.994 %)))
        (fact "address" address => "Kylykuja 3-5 D 35b-c")
        (fact "propertyId" propertyId => "18600303560006")))))


(facts* "Tests for TJ/suunnittelijan verdicts parsing"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-2.1.8-foremen.xml"))
        osapuoli {:alkamisPvm "2015-07-07"
                  :paattymisPvm "2015-07-10"
                  :sijaistettavaHlo "Pena Panaani"
                  :paatosPvm 1435708800000 ; 01.07.2015
                  :paatostyyppi "hyv\u00e4ksytty"}
        sijaistus {:alkamisPvm "07.07.2015"
                   :paattymisPvm "10.07.2015"
                   :sijaistettavaHloEtunimi "Pena"
                   :sijaistettavaHloSukunimi "Panaani"}]

    (facts "Sijaistus"
      (valid-sijaistustieto? nil nil) => falsey
      (valid-sijaistustieto? nil sijaistus) => falsey
      (valid-sijaistustieto? osapuoli nil) => truthy
      (fact "True when values match"
        (valid-sijaistustieto? osapuoli sijaistus) => truthy)
      (fact "Dates must match"
        (valid-sijaistustieto? (assoc osapuoli :alkamisPvm "2015-07-06") sijaistus) => falsey
        (valid-sijaistustieto? osapuoli (assoc sijaistus :paattymisPvm "09.07.2015")) => falsey)
      (fact "Name must match"
        (valid-sijaistustieto? (assoc osapuoli :sijaistettavaHlo "Panaani Pena") sijaistus) => falsey
        (valid-sijaistustieto? osapuoli (assoc sijaistus :sijaistettavaHloEtunimi "Paavo")) => falsey)
      (fact "Name can have whitespace"
        (valid-sijaistustieto? osapuoli (assoc sijaistus :sijaistettavaHloSukunimi "  Panaani ")) => truthy))

    (facts "Osapuoli with paatos data"
      (party-with-paatos-data nil nil) => falsey
      (party-with-paatos-data [osapuoli] sijaistus) => osapuoli
      (fact "PaatosPvm must be there"
        (party-with-paatos-data [(dissoc osapuoli :paatosPvm)] anything) => nil)
      (fact "Paatostyyppi must be correct"
        (party-with-paatos-data [(assoc osapuoli :paatostyyppi "test")] anything) => nil
        (party-with-paatos-data [(assoc osapuoli :paatostyyppi "hyl\u00e4tty")] sijaistus) => truthy)
      (fact "Sijaistus can be nil"
        (party-with-paatos-data [osapuoli] nil) => truthy)
      (fact "Sijaistus can be empty"
        (party-with-paatos-data [osapuoli] {}) => truthy))))

(facts application-state
  (fact "state found"
    (->> (build-multi-app-xml [[{:pvm "2016-09-09Z" :tila "rakennusty\u00f6t aloitettu"}]])
         (cr/strip-xml-namespaces)
         (application-state)) => "rakennusty\u00f6t aloitettu")

  (fact "nil xml"
    (application-state nil) => nil)

  (fact "not found"
    (->> (build-multi-app-xml [[{:lp-tunnus "123"}]])
         (cr/strip-xml-namespaces)
         (application-state)) => nil)

  (fact "multiple state - pick the latest"
    (->> (build-multi-app-xml [[{:pvm "2016-09-09Z" :tila "rakennusty\u00f6t aloitettu"}
                                {:pvm "2016-09-11Z" :tila "lupa rauennut"}
                                {:pvm "2016-09-10Z" :tila "rakennusty\u00f6t keskeytetty"}]])
         (cr/strip-xml-namespaces)
         (application-state)) => "lupa rauennut"))
