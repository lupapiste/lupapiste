(ns lupapalvelu.verdict-test
  (require [midje.sweet :refer :all]
           [midje.util :refer [testable-privates]]
           [clojure.java.io :as io]
           [lupapalvelu.itest-util :refer [expected-failure?]]
           [lupapalvelu.action :as action]
           [lupapalvelu.application :as application]
           [lupapalvelu.application-meta-fields :as meta-fields]
           [lupapalvelu.domain :as domain]
           [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
           [lupapalvelu.verdict :refer :all]
           [lupapalvelu.permit :as permit]
           [lupapalvelu.organization :as organization]
           [sade.core :refer [now]]
           [sade.xml :as xml]
           [sade.util :as util]))

(testable-privates lupapalvelu.verdict get-verdicts-with-attachments)

(facts "Verdicts parsing"
  (let [xml (sade.xml/parse (slurp "dev-resources/krysp/no-verdicts.xml"))]
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

(def command {:application tj-app :user {:username "sonja"} :created (now)})

(facts "Tyonjohtaja and suunnittelijan nimeaminen tests"
  (let [xml (xml/parse (slurp "resources/krysp/sample/verdict - 2.1.8 - Tekla.xml"))]
    (facts
      (fact "Success when TJ data is ok, compared to XML. Email is same, kuntaRoolikoodi is same"
        (count (:verdicts (fetch-tj-suunnittelija-verdict command))) => 1)

      (fact "KRYSP version needs to be 2.1.8 or higher"
        (fetch-tj-suunnittelija-verdict command) => nil
        (provided
         (organization/resolve-organization "753" "R") => {:krysp {:R {:version "2.1.7"}}}))

      (fact "Operation name must be correct"
        (fetch-tj-suunnittelija-verdict (assoc-in command [:application :primaryOperation :name] "something-else")) => nil)

      (fact "kuntaRoolikoodi must not be nil"
        (fetch-tj-suunnittelija-verdict (util/dissoc-in command [:application :documents 0 :data :kuntaRoolikoodi])) => nil
        (provided
          (meta-fields/enrich-with-link-permit-data irrelevant) => (util/dissoc-in tj-app [:documents 0 :data :kuntaRoolikoodi])))

      (fact "Validator doesn't accept unknown kuntaRoolikoodi"
        (fetch-tj-suunnittelija-verdict
          (assoc-in command [:application :documents 0 :data :kuntaRoolikoodi :value] "KVV-ty\u00f6njohtaja")) => (partial expected-failure? "info.no-verdicts-found-from-backend")
        (provided
          (meta-fields/enrich-with-link-permit-data irrelevant) => (assoc-in tj-app [:documents 0 :data :kuntaRoolikoodi :value] "KVV-ty\u00f6njohtaja")))

      (fact "Validator error if document's email doesn't match with XML osapuoli email"
        (fetch-tj-suunnittelija-verdict
          (assoc-in command [:application :documents 0 :data :yhteystiedot :email :value] "teppo@example.com")) => (partial expected-failure? "info.no-verdicts-found-from-backend")
        (provided
          (meta-fields/enrich-with-link-permit-data irrelevant) => (assoc-in tj-app [:documents 0 :data :yhteystiedot :email :value] "teppo@example.com")))

      (against-background ; some fixed values so we can test verdict fetching process
        (krysp-fetch/get-application-xml anything anything) => nil
        (krysp-fetch/get-application-xml link-app :application-id) => xml
        (organization/resolve-organization "753" "R") => {:krysp {:R {:version "2.1.8"}}}
        (meta-fields/enrich-with-link-permit-data irrelevant) => tj-app
        (application/get-link-permit-app irrelevant) => link-app
        (action/update-application irrelevant irrelevant) => nil
        (lupapalvelu.attachment/attach-file! irrelevant) => nil))))
