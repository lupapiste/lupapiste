(ns lupapalvelu.verdict-test
  (require [midje.sweet :refer :all]
           [midje.util :refer [testable-privates]]
           [lupapalvelu.itest-util :refer [expected-failure?]]
           [lupapalvelu.action :as action]
           [lupapalvelu.application :as application]
           [lupapalvelu.application-meta-fields :as meta-fields]
           [lupapalvelu.domain :as domain]
           [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
           [lupapalvelu.verdict :refer :all]
           [lupapalvelu.permit :as permit]
           [lupapalvelu.organization :as organization]
           [sade.common-reader :as cr]
           [sade.core :refer [now]]
           [sade.xml :as xml]
           [sade.util :as util]))

(testable-privates lupapalvelu.verdict get-verdicts-with-attachments)

(facts "Verdicts parsing"
  (let [xml (sade.xml/parse (slurp "dev-resources/krysp/verdict-r-no-verdicts.xml"))]
    (fact "No verdicts found in the attachment parsing phase"
      (count (get-verdicts-with-attachments {:permitType "R"} {} (now) xml (permit/get-verdict-reader "R"))) => 0
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

(facts "Tyonjohtaja and suunnittelijan nimeaminen tests"
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
        (application/get-link-permit-app irrelevant) => link-app
        (action/update-application irrelevant irrelevant) => nil
        (lupapalvelu.attachment/upload-and-attach-file! irrelevant irrelevant) => nil))))

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

(def example-application
  {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}})

(facts "special foreman/designer verdict"
  (let [xml (verdict-xml-with-foreman-designer-verdicts example-application example-meaningful-tj-krysp)]
    (fact "paatostieto is injected before lisatiedot"
          (keys (cr/all-of xml [:RakennusvalvontaAsia])) => (just [:paatostieto :lisatiedot :asianTiedot]))))

(facts verdict-attachment-type
  (fact "R"
    (verdict-attachment-type {:permitType "R"}) => {:type-group "paatoksenteko" :type-id "paatosote"})
  (fact "P"
    (verdict-attachment-type {:permitType "P"}) => {:type-group "paatoksenteko" :type-id "paatosote"})
  (fact "YA"
    (verdict-attachment-type {:permitType "YA"}) => {:type-group "muut" :type-id "paatosote"})
  (fact "YI"
    (verdict-attachment-type {:permitType "YI"}) => {:type-group "muut" :type-id "paatosote"})
  (fact "VVVL"
    (verdict-attachment-type {:permitType "VVVL"}) => {:type-group "muut" :type-id "paatosote"})
  (fact "R - with type"
    (verdict-attachment-type {:permitType "R"} anything) => {:type-group "paatoksenteko" :type-id anything})
  (fact "YA - with type"
    (verdict-attachment-type {:permitType "YA"} anything) => {:type-group "muut" :type-id anything}))
