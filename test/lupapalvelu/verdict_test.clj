(ns lupapalvelu.verdict-test
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.itest-util :refer [expected-failure? ->xml]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.verdict :refer :all]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.common-reader :as cr]
            [sade.core :refer [now]]
            [sade.util :as util]
            [sade.xml :as xml]))

(testable-privates lupapalvelu.verdict get-verdicts-with-attachments verdict-in-application-without-attachment? matching-poytakirja)

(facts "Verdicts parsing"
  (let [xml (sade.xml/parse (slurp "dev-resources/krysp/verdict-r-no-verdicts.xml"))]
    (fact "No verdicts found in the attachment parsing phase"
      (count (get-verdicts-with-attachments {:permitType "R"} {} (now) xml permit/read-verdict-xml {})) => 0
      )))

(def tj-doc {:schema-info {:name "tyonjohtaja-v2"}
             :data {:sijaistus {:paattymisPvm {:value nil},
                                :alkamisPvm {:value nil},
                                :sijaistettavaHloSukunimi {:value ""},
                                :sijaistettavaHloEtunimi {:value ""}}
                    :kuntaRoolikoodi {:value "vastaava ty\u00f6njohtaja"},
                    :yhteystiedot {:email {:value "jukka.testaaja@example.com"}
                                   :puhelin {:value ""}}}})

(def tj-app {:id "2"
             :municipality "753"
             :permitType "R"
             :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
             :linkPermitData [{:id "1", :type "lupapistetunnus", :operation "kerrostalo-rivitalo"}]
             :documents [tj-doc]})

(def link-app {:id "1"
               :municipality "753"
               :permitType "R"
               :primaryOperation {:name "kerrostalo-rivitalo"}})

(def cmd {:application tj-app :user {:username "sonja"} :created (now)})

(facts "Tyonjohtaja and suunnittelijan nimeaminen tests KRYSP 2.1.8"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-2.1.8-foremen.xml"))]
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
        (lupapalvelu.attachment/upload-and-attach! irrelevant irrelevant irrelevant) => nil
        (organization/get-organization nil) => "753-R"
        (organization/krysp-integration? "753-R" "R") => false))))

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
                                                         {:tag :rakennusvalvontaasianKuvaus, :attrs nil, :content nil}]}]}]}]}]})

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

(defn- check-for-tj-verdict [application xml fact-label paatospvm]
  (let [verdict (verdict-xml-with-foreman-designer-verdicts application xml)]
    (fact (str "paatostieto - " fact-label)
      (let [pt (cr/all-of verdict [:RakennusvalvontaAsia])]
        (keys pt) => (contains [:paatostieto])
        (-> pt :paatostieto :Paatos :poytakirja :paatospvm) => paatospvm))))

(facts "special foreman/designer verdict"
  (let [xml (verdict-xml-with-foreman-designer-verdicts example-vast-tj-application example-meaningful-tj-krysp)]
    (fact "paatostieto is injected before lisatiedot"
          (keys (cr/all-of xml [:RakennusvalvontaAsia])) => (just [:paatostieto :lisatiedot :asianTiedot])))
  (facts "from 2.2.2 KuntaGML"
    (let [xml (->> (slurp "dev-resources/krysp/verdict-r-2.2.2-foremen.xml")
                   xml/parse
                   cr/strip-xml-namespaces)]
      (check-for-tj-verdict example-vast-tj-application xml "application #1" "2018-01-15")
      (check-for-tj-verdict example-iv-tj-application xml "application #2" "2018-01-17"))))

(facts "Section requirement for verdicts"
       (let [org        {:section {:operations ["pool" "house"]
                                   :enabled    true}}
             pool       {:primaryOperation {:name "pool"}}
             no-xml1    (->xml {:root {:foo {:bar "nope"}}})
             no-xml2    (->xml {:root {:foo {:bar "nope"}
                                       :paatostieto {:hii "hoo"}}})
             blank-xml1 (->xml {:root {:foo {:bar "nope"}
                                       :paatostieto {:pykala ""}}})
             blank-xml2 (->xml {:root {:foo         {:bar "nope"}
                                       :paatostieto {:doh {:pykala ""}}}})
             wrong-path (->xml {:root {:pykala 22}})
             good1      (->xml {:root {:paatostieto {:pykala 22}}})
             good2      (->xml {:root {:another {:paatostieto {:pykala "33"}}}})
             good3      (->xml {:root {:paatostieto {:between {:pykala "33"}}}})
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

(facts "updating verdict paatosote attachments"

  (fact "verdict-in-application-without-attachment?"
    (let [app {:verdicts [{:kuntalupatunnus "KL-1"
                           :paatokset [{:poytakirjat [{:urlHash "I-have-an-attachment"}
                                                      {:urlHash "So-do-I"}]}]}
                          {:kuntalupatunnus "KL-3"
                           :paatokset [{:poytakirjat []}]}
                          {:kuntalupatunnus "KL-4"
                           :paatokset [{:poytakirjat [{:urlHash nil}]}]}]}]
      ;; KL-1: all elements in :poytakirjat array have :urlHash
      (verdict-in-application-without-attachment? app {:kuntalupatunnus "KL-1"}) => false
      ;; KL-2 is not present in the :verdicts array
      (verdict-in-application-without-attachment? app {:kuntalupatunnus "KL-2"}) => false
      ;; KL-3 is present and :poytakirjat is empty
      (verdict-in-application-without-attachment? app {:kuntalupatunnus "KL-3"}) => true
      ;; KL-3 is present and :urlHash is nil
      (verdict-in-application-without-attachment? app {:kuntalupatunnus "KL-4"}) => true))
  (fact matching-poytakirja
        (matching-poytakirja {:a 1 :b 2 :d 3}
                             [:a :b :c]
                             [{:a 1 :b 3 :c 3}
                              {:a 1 :b 2 :d 4}]) => {:a 1 :b 2 :d 4}
        (matching-poytakirja {:a 1 :b 2}
                             [:a :b :c]
                             [{:a 1 :b 3}
                              {:a 1}]) => nil))
