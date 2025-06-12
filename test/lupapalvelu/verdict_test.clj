(ns lupapalvelu.verdict-test
  (:require [clojure.java.io :as io]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.test-util :refer [expected-failure?]]
            [lupapalvelu.verdict :as verdict :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.common-reader :as cr]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.util :as util]
            [sade.xml :as xml :refer [map->xml]]))

(testable-privates lupapalvelu.verdict
                   get-verdicts-with-attachments
                   verdict-in-application-without-attachment?
                   matching-poytakirja verdict-party-finder
                   missing-verdict-attachments-query)

(facts "Verdicts parsing"
  (let [xml (sade.xml/parse (io/input-stream "dev-resources/krysp/verdict-r-no-verdicts.xml"))]
    (fact "No verdicts found in the attachment parsing phase"
      (count (get-verdicts-with-attachments {:permitType "R"} {} (now) xml permit/read-verdict-xml {})) => 0
      )))

(def tj-doc {:id          "foreman-v2-doc-id"
             :schema-info {:name "tyonjohtaja-v2"}
             :data {:sijaistus {:paattymisPvm {:value nil},
                                :alkamisPvm {:value nil},
                                :sijaistettavaHloSukunimi {:value ""},
                                :sijaistettavaHloEtunimi {:value ""}}
                    :kuntaRoolikoodi {:value "vastaava ty\u00f6njohtaja"},
                    :yhteystiedot {:email {:value "jukka.testaaja@example.com"}
                                   :puhelin {:value ""}}}})

(def tj-doc-v3 {:id          "foreman-v3-doc-id"
                :schema-info {:name "tyonjohtaja-v3"}
                :tj-document true
                :data        {:sijaistus       {:paattymisPvm             {:value nil},
                                                :alkamisPvm               {:value nil},
                                                :sijaistettavaHloSukunimi {:value ""},
                                                :sijaistettavaHloEtunimi  {:value ""}}
                              :kuntaRoolikoodi {:value "vastaava ty\u00f6njohtaja"},
                              :yhteystiedot    {:email   {:value "jukka.testaaja@example.com"}
                                                :puhelin {:value ""}}}})

(def tj-app {:id "2"
             :municipality "753"
             :permitType "R"
             :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
             :linkPermitData [{:id "1", :type "lupapistetunnus", :operation "kerrostalo-rivitalo"}]
             :documents [tj-doc]})

(def tj-v3-app {:id               "1"
                :municipality     "753"
                :permitType       "R"
                :primaryOperation {:name "kerrostalo-rivitalo"}
                :documents        [tj-doc-v3 tj-doc]})

(def link-app {:id "1"
               :municipality "753"
               :permitType "R"
               :primaryOperation {:name "kerrostalo-rivitalo"}})

(def cmd {:application tj-app :user {:username "sonja"} :created (now) :system {}})

(def cmd-v3 {:application tj-v3-app :user {:username "sonja"} :created (now) :system {}})

(facts "Tyonjohtaja and suunnittelijan nimeaminen tests KRYSP 2.1.8"
  (let [xml (xml/parse (io/input-stream "dev-resources/krysp/verdict-r-2.1.8-foremen.xml"))]
    (facts
        (fact "Success when TJ data is ok, compared to XML. Email is same, kuntaRoolikoodi is same"
          (count (:verdicts (fetch-tj-suunnittelija-verdict cmd))) => 1)

      (fact "KRYSP version needs to be 2.1.8 or higher"
        (fetch-tj-suunnittelija-verdict cmd) => nil
        (provided
          (organization/resolve-organization "753" "R") => {:krysp {:R {:version "2.1.7"}}}))

      (fact "Operation name must be correct"
        (fetch-tj-suunnittelija-verdict (assoc-in cmd [:application :primaryOperation :name] "something-else")) => nil)

      (fact "kuntaRoolikoodi must not be nil"
        (fetch-tj-suunnittelija-verdict (util/dissoc-in cmd [:application :documents 0 :data :kuntaRoolikoodi])) => nil
        (provided
          (meta-fields/enrich-with-link-permit-data irrelevant) => (util/dissoc-in tj-app [:documents 0 :data :kuntaRoolikoodi])))

      (fact "Validator doesn't accept unknown kuntaRoolikoodi"
        (fetch-tj-suunnittelija-verdict
          (assoc-in cmd [:application :documents 0 :data :kuntaRoolikoodi :value] "KVV-ty\u00f6njohtaja")) => (partial expected-failure? "info.no-verdicts-found-from-backend")
        (provided
          (meta-fields/enrich-with-link-permit-data irrelevant) => (assoc-in tj-app [:documents 0 :data :kuntaRoolikoodi :value] "KVV-ty\u00f6njohtaja")))

      (fact "Validator error if document's email doesn't match with XML osapuoli email"
        (fetch-tj-suunnittelija-verdict
          (assoc-in cmd [:application :documents 0 :data :yhteystiedot :email :value] "teppo@example.com")) => (partial expected-failure? "info.no-verdicts-found-from-backend")
        (provided
          (meta-fields/enrich-with-link-permit-data irrelevant) => (assoc-in tj-app [:documents 0 :data :yhteystiedot :email :value] "teppo@example.com")))

      (against-background ; some fixed values so we can test verdict fetching process
        (krysp-fetch/get-application-xml-by-application-id anything) => nil
        (krysp-fetch/get-application-xml-by-backend-id anything anything) => nil
        (krysp-fetch/get-application-xml-by-application-id link-app) => xml
        (organization/resolve-organization "753" "R") => {:krysp {:R {:version "2.1.8"}}}
        (meta-fields/enrich-with-link-permit-data irrelevant) => tj-app
        (application/get-link-permit-apps irrelevant) => [link-app]
        (action/update-application irrelevant irrelevant) => nil
        (attachment/upload-and-attach! irrelevant irrelevant irrelevant) => nil
        (organization/get-organization nil) => "753-R"
        (organization/krysp-write-integration? "753-R" "R") => false))))

(facts "Tyonjohtajailmoitus v3 tests KRYSP 2.1.8"
  (let [xml (xml/parse (io/input-stream "dev-resources/krysp/verdict-r-2.1.8-foremen.xml"))]
    (facts
      (fact "Success when TJ data is ok, compared to XML. Email is same, kuntaRoolikoodi is same"
        (count (:verdicts (fetch-foreman-v3-verdict cmd-v3 "foreman-v3-doc-id"))) => 1)

      (fact "Document must be new foreman-v3 tj-document"
        (fetch-foreman-v3-verdict cmd-v3 "foreman-v2-doc-id") => nil)

      (fact "update-foreman-document is called for each verdict found"
        (fetch-foreman-v3-verdict cmd-v3 "foreman-v3-doc-id") => truthy
        (provided
          (verdict/update-foreman-document anything) => nil :times 1))

      (against-background ; some fixed values so we can test verdict fetching process
        (krysp-fetch/get-application-xml-by-application-id anything) => nil
        (krysp-fetch/get-application-xml-by-backend-id anything anything) => nil
        (krysp-fetch/get-application-xml-by-application-id tj-v3-app) => xml
        (organization/resolve-organization "753" "R") => {:krysp {:R {:version "2.1.8"}}}
        (meta-fields/enrich-with-link-permit-data irrelevant) => tj-v3-app
        (application/get-link-permit-apps irrelevant) => [tj-v3-app]
        (action/update-application irrelevant irrelevant) => nil
        (action/update-application irrelevant irrelevant irrelevant) => nil
        (attachment/upload-and-attach! irrelevant irrelevant irrelevant) => nil
        (organization/get-organization nil) => "753-R"
        (organization/krysp-write-integration? "753-R" "R") => false)
      )))

(def example-meaningful-tj-krysp
  {:tag :Rakennusvalvonta,
   :content [{:tag :rakennusvalvontaAsiatieto,
              :attrs nil,
              :content [{:tag :RakennusvalvontaAsia,
                         :content [{:tag :lisatiedot,
                                    :attrs nil,
                                    :content [{:tag :Lisatiedot,
                                               :attrs nil,
                                               :content [{:tag :salassapitotietoKytkin, :attrs nil, :content ["false"]}
                                                         {:tag :asioimiskieli, :attrs nil, :content ["suomi"]}
                                                         {:tag :suoramarkkinointikieltoKytkin,
                                                          :attrs nil,
                                                          :content ["false"]}]}]}
                                   {:tag :asianTiedot,
                                    :attrs nil,
                                    :content [{:tag :Asiantiedot,
                                               :attrs nil,
                                               :content [{:tag :vahainenPoikkeaminen, :attrs nil, :content nil}
                                                         {:tag :rakennusvalvontaasianKuvaus, :attrs nil, :content nil}]}]}
                                   {:tag :paatostieto,
                                    :attrs nil,
                                    :content [{:tag :Paatos,
                                               :attrs nil,
                                               :content [{:tag :poytakirja,
                                                          :attrs nil,
                                                          :content [{:tag :paatoskoodi,
                                                                     :attrs nil,
                                                                     :content ["hylätty"]}]}]}]}]}]}]})

(def example-vast-tj-application
  {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
   :permitSubtype "tyonjohtaja-hakemus"
   :documents [{:schema-info {:name "tyonjohtaja-v2"}
                :data {:kuntaRooliKoodi {:value "vastaava ty\u00f6njohtaja"}
                       :henkilotiedot {:hetu {:value "251057-9662"}}}}]
   :linkPermitData [{:type "lupapistetunnus"
                     :id "LP-638-2018-90003"}]})

(def example-iv-tj-application
  {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
   :permitSubtype "tyonjohtaja-hakemus"
   :documents [{:schema-info {:name "tyonjohtaja-v2"}
                :data {:kuntaRooliKoodi {:value "IV-ty\u00f6njohtaja"}
                       :henkilotiedot {:hetu {:value "070470-987M"}}}}]
   :linkPermitData [{:type "lupapistetunnus"
                     :id "LP-638-2018-90013"}]})

(def example-tj-v3-application
  {:primaryOperation {:name "muu-uusi-rakentaminen"}
   :documents [{:id "foreman-v3-doc-id"
                :schema-info {:name "tyonjohtaja-v3"}
                :data {:kuntaRoolikoodi {:value "vastaava ty\u00f6njohtaja"}
                       :henkilotiedot {:hetu {:value "210281-9988"}}}}]
   :linkPermitData [{:type "lupapistetunnus"
                     :id "LP-638-2018-90013"}]})

(defn- check-for-tj-verdict [application xml fact-label paatospvm krysp-version]
  (let [verdict             (verdict-xml-with-foreman-designer-verdicts application xml)
        pt                  (cr/all-of verdict [:RakennusvalvontaAsia])
        path-to-poytakirja  [:paatostieto :Paatos :poytakirja]]
    (fact (str "paatostieto - " fact-label)
      (keys pt) => (contains [:paatostieto])
      (get-in pt (conj path-to-poytakirja :paatospvm)) => paatospvm)
    (fact (str "paatospykala - " fact-label)
      (get-in pt (conj path-to-poytakirja :paatoskoodi)) => "hyv\u00e4ksytty")
    (when (> 2.23 krysp-version)
      (fact (str "paatoksentekija - " fact-label)
        (get-in pt (conj path-to-poytakirja :paatoksentekija)) => ""))
    (when (> krysp-version 2.22)
      (fact (str "paatoksentekija - " fact-label)
        (get-in pt (conj path-to-poytakirja :paatoksentekija)) => "Pena")
      (fact (str "pykala - " fact-label)
        (get-in pt (conj path-to-poytakirja :pykala)) => "1")
      (fact (str "liite - " fact-label)
        (get-in pt (conj path-to-poytakirja :liite)) => {:kuvaus "kuvaus 1"
                                                         :linkkiliitteeseen "http://localhost:8000/dev/sample-verdict.pdf"}))))



(facts "special foreman/designer verdict"
  (let [xml (verdict-xml-with-foreman-designer-verdicts example-vast-tj-application example-meaningful-tj-krysp)]
    (fact "paatostieto is injected before lisatiedot"
      (keys (cr/all-of xml [:RakennusvalvontaAsia])) => (just [:paatostieto :lisatiedot :asianTiedot])))
  (facts "from 2.2.2 KuntaGML"
    (let [xml (->> (io/input-stream "dev-resources/krysp/verdict-r-2.2.2-foremen.xml")
                   xml/parse
                   cr/strip-xml-namespaces)]
      (check-for-tj-verdict example-vast-tj-application xml "application #1" "2018-01-15" 2.22)
      (check-for-tj-verdict example-iv-tj-application xml "application #2" "2018-01-17" 2.22)))

  (facts "from 2.2.3 KuntaGML"
    (let [xml (->> (io/input-stream "dev-resources/krysp/verdict-r-2.2.3-foremen.xml")
                   xml/parse
                   cr/strip-xml-namespaces)]
      (check-for-tj-verdict example-iv-tj-application xml "application #3" "2018-01-17" 2.23)))

  (facts "Finding verdicts with email, name and phone number in addition to hetu"
    (let [henk-tiedot (merge {:etunimi  {:value "Veijo"}
                              :sukunimi {:value "Viranomainen"}
                              :hetu     {:value "210281-9988"}}
                             {:puhelin {:value "0123456789"}
                              :email   {:value "Veijo.Viranomainen@example.com"}})
          henkilo-hetu {:tag :henkilotunnus :attrs nil :content ["210281-9988"]}
          henkilo-ulkomainen-hetu {:tag :ulkomainenHenkilotunnus :attrs nil :content ["12345"]}
          henkilo-err-ulkomainen-hetu {:tag :ulkomainenHenkilotunnus :attrs nil :content ["00000"]}
          henkilo-err-hetu {:tag :henkilotunnus :attrs nil :content ["000000-0000"]}
          henkilo-empty-hetu {:tag :henkilotunnus :attrs nil :content [""]}
          henkilo-nimi {:tag :nimi :attrs nil :content [{:tag :sukunimi :attrs nil :content ["Viranomainen"]}
                                                        {:tag :etunimi :attrs nil :content ["Veijo"]}]}
          henkilo-sukunimi {:tag :nimi :attrs nil :content [{:tag :sukunimi :attrs nil :content ["Viranomainen Veijo"]}]}
          henkilo-email {:tag :sahkopostiosoite :attrs nil :content ["Veijo.Viranomainen@example.com"]}
          henkilo-puhelin {:tag :puhelin :attrs nil :content ["0123456789"]}
          henkilo-err-puhelin {:tag :puhelin :attrs nil :content ["0000000000"]}
          henkilo-content [{:tag :osoite :attrs nil :content [{:tag :osoitenimi, :attrs nil :content [{:tag :teksti, :attrs {:xml:lang "und"}, :content ["Mets\u00e4npojankuja 1"]}]}
                                                              {:tag :postinumero :attrs nil, :content ["03220"]}
                                                              {:tag :postitoimipaikannimi, :attrs nil, :content ["TERVALAMPI"]}]}]
          henkilo-data (fn [content] {:tag :henkilo :attrs nil, :content (concat henkilo-content content)})
          party {:tag :Tyonjohtaja :attrs nil :content [{:tag :tyonjohtajaRooliKoodi :attrs nil :content ["vastaava ty\u00f6njohtaja"]}
                                                        {:tag :VRKrooliKoodi :attrs nil :content ["ty\u00f6njohtaja"]}
                                                        {:tag :vaadittuPatevyysluokka :attrs nil, :content ["A"]}
                                                        {:tag :koulutus :attrs nil :content ["arkkitehti"]}
                                                        {:tag :valmistumisvuosi :attrs nil :content ["1994"]}
                                                        {:tag :alkamisPvm :attrs nil :content ["2015-11-29"]}
                                                        {:tag :hakemuksenSaapumisPvm :attrs nil :content ["2015-11-30"]}
                                                        {:tag :sijaistettavaHlo :attrs nil :content []}
                                                        {:tag :paatosPvm :attrs nil :content ["2015-11-29"]}
                                                        {:tag :paatostyyppi :attrs nil :content ["hyl\u00e4tty"]}]}]
      (verdict-party-finder "vastaava ty\u00f6njohtaja" henk-tiedot [party]) => nil

      (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-hetu]))]
        (verdict-party-finder "vastaava ty\u00f6njohtaja" henk-tiedot [amended-party party]) => amended-party)

      (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-hetu]))]
        (verdict-party-finder "vastaava ty\u00f6njohtaja" {} [amended-party party]) => nil
        (verdict-party-finder "vastaava ty\u00f6njohtaja" (assoc henk-tiedot :hetu "") [amended-party party])
        => nil)

      (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-hetu]))]
        (verdict-party-finder "nonnonnoo-ty\u00f6njohtaja" henk-tiedot [amended-party party]) => nil)

      (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-email]))]
        (verdict-party-finder "vastaava ty\u00f6njohtaja" henk-tiedot [party amended-party]) => amended-party)

      (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-nimi]))]
        (verdict-party-finder "vastaava ty\u00f6njohtaja" henk-tiedot [amended-party]) => amended-party)

      (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-sukunimi]))]
        (verdict-party-finder "vastaava ty\u00f6njohtaja" henk-tiedot [amended-party]) => amended-party)

      (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-puhelin]))]
        (verdict-party-finder "vastaava ty\u00f6njohtaja" henk-tiedot [amended-party]) => amended-party)

      (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-err-puhelin]))]
        (verdict-party-finder "vastaava ty\u00f6njohtaja" henk-tiedot [amended-party]) => nil)

      (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-err-hetu henkilo-nimi]))]
        (verdict-party-finder "vastaava ty\u00f6njohtaja" henk-tiedot [amended-party]) => nil)

      (fact "ulkomainen hetu"
        (let [henk-tiedot (assoc henk-tiedot :ulkomainenHenkilotunnus {:value "12345"})]
          (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-ulkomainen-hetu]))]
            (verdict-party-finder "vastaava työnjohtaja"
                                  (assoc henk-tiedot :not-finnish-hetu {:value true})
                                  [amended-party])
            => amended-party)

          (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-err-ulkomainen-hetu]))]
            (verdict-party-finder "vastaava työnjohtaja"
                                  (assoc henk-tiedot :not-finnish-hetu {:value true})
                                  [amended-party])
            => nil)

          (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-hetu]))]
            (verdict-party-finder "vastaava työnjohtaja"
                                  (assoc henk-tiedot :not-finnish-hetu {:value true})
                                  [amended-party])
            => nil)

          (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-ulkomainen-hetu]))]
            (verdict-party-finder "vastaava työnjohtaja"
                                  (assoc henk-tiedot :ulkomainenHenkilotunnus {:value "12345"} :not-finnish-hetu {:value false})
                                  [amended-party])
            => nil)

          (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-err-ulkomainen-hetu henkilo-hetu]))]
            (verdict-party-finder "vastaava työnjohtaja"
                                  (assoc henk-tiedot :ulkomainenHenkilotunnus {:value "12345"} :not-finnish-hetu {:value false})
                                  [amended-party])
            => amended-party)))

      (fact "Empy fields are ignored"
        (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-err-hetu henkilo-nimi]))]
          (verdict-party-finder "vastaava ty\u00f6njohtaja"  (assoc-in henk-tiedot [:hetu :value] "") [amended-party])
          => amended-party)

        (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-err-hetu henkilo-nimi]))]
          (verdict-party-finder "vastaava ty\u00f6njohtaja"  (assoc henk-tiedot :hetu "") [amended-party])
          => amended-party)

        (let [amended-party (update-in party [:content] conj (henkilo-data [henkilo-empty-hetu henkilo-nimi]))]
          (verdict-party-finder "vastaava ty\u00f6njohtaja"  henk-tiedot [amended-party])
          => amended-party))
      (let [xml     (verdict-xml-with-foreman-designer-verdicts example-tj-v3-application
                                                                (assoc-in example-meaningful-tj-krysp
                                                                          [:content 0 :content 0 :content 0]
                                                                          (update-in party [:content] conj (henkilo-data [henkilo-hetu])))
                                                                "foreman-v3-doc-id")
            xml-bad (verdict-xml-with-foreman-designer-verdicts example-tj-v3-application
                                                                (assoc-in example-meaningful-tj-krysp
                                                                          [:content 0 :content 0 :content 0]
                                                                          (update-in party [:content] conj (henkilo-data [henkilo-hetu])))
                                                                "bad-id")]
        (fact "paatoskoodi is read from the correct party's decision on the kuntaGML message"
          (cr/all-of xml [:RakennusvalvontaAsia :paatoskoodi]) => "hylätty")
        (fact "paatoskoodi is not read from the correct party because the document id does not match"
          (cr/all-of xml-bad [:RakennusvalvontaAsia :paatoskoodi]) => nil)))))

(facts "Section requirement for verdicts"
       (let [org        {:section {:operations ["pool" "house"]
                                   :enabled    true}}
             pool       {:primaryOperation {:name "pool"}}
             no-xml1    (map->xml {:root {:foo {:bar "nope"}}})
             no-xml2    (map->xml {:root {:foo      {:bar "nope"}
                                       :paatostieto {:hii "hoo"}}})
             blank-xml1 (map->xml {:root {:foo      {:bar "nope"}
                                       :paatostieto {:pykala ""}}})
             blank-xml2 (map->xml {:root {:foo      {:bar "nope"}
                                       :paatostieto {:doh {:pykala ""}}}})
             wrong-path (map->xml {:root {:pykala 22}})
             good1      (map->xml {:root {:paatostieto {:pykala 22}}})
             good2      (map->xml {:root {:another {:paatostieto {:pykala "33"}}}})
             good3      (map->xml {:root {:paatostieto {:between {:pykala "33"}}}})
             fail-check (partial expected-failure? :info.section-required-in-verdict)]
         (fact "No paatostieto element"
               (validate-section-requirement pool no-xml1 org) => fail-check)
         (fact "No pykala element"
               (validate-section-requirement pool no-xml2 org) => fail-check)
         (fact "Muutoslupa"
               (validate-section-requirement (assoc pool :permitSubtype "muutoslupa") no-xml2 org) => nil)
         (fact "Blank section 1"
               (validate-section-requirement pool blank-xml1 org) => fail-check)
         (fact "Blank section 2"
               (validate-section-requirement pool blank-xml2 org) => fail-check)
         (fact "Pykala outside of paatostieto element"
               (validate-section-requirement pool wrong-path org) => fail-check)
         (fact "Good sections 1"
               (validate-section-requirement pool good1 org) => nil)
         (fact "Good sections 2"
               (validate-section-requirement pool good2 org) => nil)
         (fact "Good section 3"
               (validate-section-requirement pool good3 org) => nil)
         (fact "Organization does not require section"
               (validate-section-requirement pool no-xml1 {:section {:operations ["pool" "house"]
                                                                     :enabled    false}}) => nil)
         (fact "Section not required for the operation"
               (validate-section-requirement pool no-xml1 {:section {:operations ["sauna" "house"]
                                                                     :enabled    true}}) => nil)))

(facts "valid-missing-options!"
  (valid-missing-options! nil) => (throws)
  (valid-missing-options! {}) => (throws)
  (valid-missing-options! {:start 1725548500000 :end 1725548500010})
  => {:start 1725548500000 :end 1725548500010}
  (valid-missing-options! {:start         1725548500000
                           :end           1725548500010
                           :organizations []})
  => {:start 1725548500000 :end 1725548500010 :organizations []}
  (valid-missing-options! {:start         1725548500000
                           :end           1725548500010
                           :organizations ["123-R"]})
  => {:start 1725548500000 :end 1725548500010 :organizations ["123-R"]}
  (valid-missing-options! {:start 1725548600000 :end 1725548500010})
  => (throws)
  (valid-missing-options! {:application-ids ["LP-123-2024-00001"]})
  => {:application-ids ["LP-123-2024-00001"]}
  (valid-missing-options! {:application-ids []})
  => (throws)
  (valid-missing-options! {:application-ids nil})
  => (throws)
  (valid-missing-options! {:application-ids [nil]})
  => (throws)
  (valid-missing-options! {:start           1725548500000
                           :end             1725548500010
                           :application-ids ["LP-123-2024-00001"]})
  => (throws))

(facts "missing-verdict-attachments-query"
  (missing-verdict-attachments-query {:start 1725548500000 :end 1725548500010})
  => {:permitType     {$ne "ARK"}
      :facta-imported {$ne true}
      :verdicts       {$elemMatch {:draft                         {$ne true}
                                   :timestamp                     {$gt 1725548500000
                                                                   $lt 1725548500010}
                                   :paatokset.poytakirjat.0       {$exists true}
                                   :paatokset.poytakirjat.urlHash {$exists false}}}}
  (missing-verdict-attachments-query {:start         1725548500000
                                      :end           1725548500010
                                      :organizations []})
  => {:permitType     {$ne "ARK"}
      :facta-imported {$ne true}
      :verdicts       {$elemMatch {:draft                         {$ne true}
                                   :timestamp                     {$gt 1725548500000
                                                                   $lt 1725548500010}
                                   :paatokset.poytakirjat.0       {$exists true}
                                   :paatokset.poytakirjat.urlHash {$exists false}}}}
  (missing-verdict-attachments-query {:start         1725548500000
                                      :end           1725548500010
                                      :organizations ["123-R"]})
  => {:permitType     {$ne "ARK"}
      :facta-imported {$ne true}
      :organization   {$in #{"123-R"}}
      :verdicts       {$elemMatch {:draft                         {$ne true}
                                   :timestamp                     {$gt 1725548500000
                                                                   $lt 1725548500010}
                                   :paatokset.poytakirjat.0       {$exists true}
                                   :paatokset.poytakirjat.urlHash {$exists false}}}}
  (missing-verdict-attachments-query {:application-ids ["LP-123-2024-00001"
                                                        "LP-123-2024-00001"]})
  => {:permitType     {$ne "ARK"}
      :facta-imported {$ne true}
      :_id            {$in #{"LP-123-2024-00001"}}
      :verdicts       {$elemMatch {:draft                         {$ne true}
                                   :paatokset.poytakirjat.0       {$exists true}
                                   :paatokset.poytakirjat.urlHash {$exists false}}}})

(facts "updating verdict paatosote attachments"

  (fact "verdict-in-application-without-attachment?"
    (let [app {:verdicts [{:kuntalupatunnus "KL-1"
                           :paatokset       [{:poytakirjat [{:urlHash "I-have-an-attachment"}
                                                            {:urlHash "So-do-I"}]}]}
                          {:kuntalupatunnus "KL-3"
                           :paatokset       [{:poytakirjat []}]}
                          {:kuntalupatunnus "KL-4"
                           :paatokset       [{:poytakirjat [{:urlHash nil}]}]}]}]
      ;; KL-1: all elements in :poytakirjat array have :urlHash
      (verdict-in-application-without-attachment? app {:kuntalupatunnus "KL-1"}) => false
      ;; KL-2 is not present in the :verdicts array
      (verdict-in-application-without-attachment? app {:kuntalupatunnus "KL-2"}) => false
      ;; KL-3 is present and :poytakirjat is empty
      (verdict-in-application-without-attachment? app {:kuntalupatunnus "KL-3"}) => true
      ;; KL-3 is present and :urlHash is nil
      (verdict-in-application-without-attachment? app {:kuntalupatunnus "KL-4"}) => true))

  (fact "matching-poytakirja"
    (let [early-ts    (date/timestamp (date/today))
          late-ts     (-> (date/now) date/end-of-day date/timestamp)
          tomorrow-ts (-> (date/now) (date/plus :day) date/timestamp)]
      (matching-poytakirja {:a 1 :b 2 :d 3}
                           [:a :b :c]
                           [{:a 1 :b 3 :c 3}
                            {:a 1 :b 2 :d 4}]) => {:a 1 :b 2 :d 4}
      (matching-poytakirja {:a 1 :b 2}
                           [:a :b :c]
                           [{:a 1 :b 3}
                            {:a 1}]) => nil
      (matching-poytakirja {:a 1 :paatospvm (now) :b 2}
                           [:a :b :c :paatospvm]
                           [{:a 1 :b 2}
                            {:a 1 :paatospvm early-ts :b 2 :c 3}])
      => {:a 1 :paatospvm early-ts :b 2 :c 3}
      (matching-poytakirja {:a 1 :paatospvm (now) :b 2}
                           [:a :b :c :paatospvm]
                           [{:a 1 :b 2}
                            {:a 1 :paatospvm late-ts :b 2 :c 3}])
      => {:a 1 :paatospvm late-ts :b 2 :c 3}
      (matching-poytakirja {:a 1 :paatospvm (now) :b 2}
                           [:a :b :c :paatospvm]
                           [{:a 1 :b 2}
                            {:a 1 :paatospvm tomorrow-ts :b 2 :c 3}])
      => nil)))

(fact "Precheck: no-sent-backing-system-verdict-tasks"
  (let [error (partial expected-failure? :error.verdicts-have-sent-tasks)]
    (no-sent-backing-system-verdict-tasks {:application {:verdicts [{:id "vid1"}]
                                                         :tasks    []}})
    => nil
    (no-sent-backing-system-verdict-tasks {:application {:verdicts [{:id "vid1"}]
                                                         :tasks    [{:source {:type "hello"
                                                                              :id   "vid1"}
                                                                     :state  "sent"}]}})
    => nil
    (no-sent-backing-system-verdict-tasks {:application {:verdicts [{:id "vid1"}]
                                                         :tasks    [{:source {:type "verdict"
                                                                              :id   "vid1"}
                                                                     :state  "foo"}]}})
    => nil
    (no-sent-backing-system-verdict-tasks {:application {:verdicts [{:id "vid1"}]
                                                         :tasks    [{:source {:type "verdict"
                                                                              :id   "vid1"}
                                                                     :state  "sent"}]}})
    => error
    (no-sent-backing-system-verdict-tasks {:application {:verdicts [{:id "vid1"}]
                                                         :tasks    [{:source {:type "verdict"
                                                                              :id   "vid1"}
                                                                     :state  "foo"}
                                                                    {:source {:type "verdict"
                                                                              :id   "vid1"}
                                                                     :state  "ok"}]}})
    => error))

(defn make-verdict [kuntalupatunnus draft?]
  (cond-> {:kuntalupatunnus kuntalupatunnus
           :random (str (gensym "random"))}
    draft? (assoc :draft true)))

(against-background
  [(mongo/create-id) => "mongo-id"]
  (facts "backend-id-mongo-updates"
    (let [draft (make-verdict "one" true)
          pub1  (make-verdict "two" false)
          pub2  (make-verdict "three" false)]
      (fact "No old verdicts: no backend-ids"
        (backend-id-mongo-updates {} []) => nil
        (backend-id-mongo-updates {} [{:kuntalupatunnus nil}
                                      {:kuntalupatunnus ""}
                                      {:kuntalupatunnus "  "}])
        => nil)
      (fact "No old verdicts"
        (doseq [ids [[{:kuntalupatunnus " new "}]
                     [{:kuntalupatunnus " new "}
                      {:kuntalupatunnus " "}]
                     [{}
                      {:kuntalupatunnus " new "}]]]
          (backend-id-mongo-updates {} ids)
          => {$push {:verdicts {$each [{:id              "mongo-id"
                                        :kuntalupatunnus "new"
                                        :draft           true
                                        :timestamp       nil
                                        :paatokset       []}]}}}))
      (fact "Old published"
        (backend-id-mongo-updates {:verdicts [pub1]}
                                  [{:kuntalupatunnus "new"
                                    :vrk-lupatunnus  "vrk"}])
        => {$push {:verdicts {$each [{:id              "mongo-id"
                                      :kuntalupatunnus "new"
                                      :vrk-lupatunnus  "vrk"
                                      :draft           true
                                      :timestamp       nil
                                      :paatokset       []}]}}})
      (fact "Old draft"
        (backend-id-mongo-updates {:verdicts [draft]}
                                  [{:kuntalupatunnus "new"
                                    :vrk-lupatunnus  "vrk"}])
        => {$set {:verdicts [{:id              "mongo-id"
                              :kuntalupatunnus "new"
                              :vrk-lupatunnus  "vrk"
                              :draft           true
                              :timestamp       nil
                              :paatokset       []}]}})
      (fact "Old published and the same draft: no changes"
        (backend-id-mongo-updates {:verdicts [pub1 draft]}
                                  [{:kuntalupatunnus "one"
                                    :vrk-lupatunnus  "vrk"}])
        => nil)
      (fact "The draft has been published"
        (backend-id-mongo-updates {:verdicts [pub1 draft]}
                                  [{:kuntalupatunnus "one"
                                    :vrk-lupatunnus  "vrk"}]
                                  false)
        => {$set {:verdicts [pub1
                             {:id              "mongo-id"
                              :kuntalupatunnus "one"
                              :vrk-lupatunnus  "vrk"
                              :timestamp       nil
                              :paatokset       []}]}})
      (fact "Draft no longer valid"
        (backend-id-mongo-updates {:verdicts [pub1 draft]}
                                  [{:kuntalupatunnus "two"
                                    :vrk-lupatunnus  "vrk"}])
        => {$pull {:verdicts {:kuntalupatunnus {$in #{"one"}}}}}
        )
      (fact "Many verdicts"
        (backend-id-mongo-updates {:verdicts [pub1 pub2 draft]}
                                  [{:kuntalupatunnus "four"
                                    :vrk-lupatunnus  "vrk"}
                                   {:kuntalupatunnus "five"}])

        => {$set {:verdicts [pub1 pub2
                             {:id              "mongo-id"
                              :kuntalupatunnus "four"
                              :vrk-lupatunnus  "vrk"
                              :draft           true
                              :timestamp       nil
                              :paatokset       []}
                             {:id              "mongo-id"
                              :kuntalupatunnus "five"
                              :draft           true
                              :timestamp       nil
                              :paatokset       []}]}}))))
