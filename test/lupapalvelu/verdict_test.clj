(ns lupapalvelu.verdict-test
  (require [midje.sweet :refer :all]
           [midje.util :refer [testable-privates]]
           [clojure.java.io :as io]
           [lupapalvelu.action :as action]
           [lupapalvelu.application :as application]
           [lupapalvelu.application-meta-fields :as meta-fields]
           [lupapalvelu.domain :as domain]
           [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
           [lupapalvelu.verdict :refer :all]
           [lupapalvelu.permit :as permit]
           [lupapalvelu.organization :as organization]
           [sade.core :refer [now]]))

(testable-privates lupapalvelu.verdict get-verdicts-with-attachments)

(facts "Verdicts parsing"
  (let [xml (sade.xml/parse (slurp "dev-resources/krysp/no-verdicts.xml"))]
    (fact "No verdicts found in the attachment parsing phase"
      (count (get-verdicts-with-attachments {:permitType "R"} {} (now) xml (permit/get-verdict-reader "R"))) => 0
      )))

(def tj-doc {:data
             {:sijaistus
              {:paattymisPvm {:value nil},
               :alkamisPvm {:value nil},
               :sijaistettavaHloSukunimi {:value ""},
               :sijaistettavaHloEtunimi {:value ""}}
              :kuntaRoolikoodi {:value "vastaava työnjohtaja"},
              :yhteystiedot
              {:email {:value "jukka.vanttinen@trimble.com"}
               :puhelin {:value ""}}}})

(def tj-app {:id "2"
             :municipality "753"
             :permitType "R"
             :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
             :linkPermitData [{:id "1", :type "lupapistetunnus", :operation "kerrostalo-rivitalo"}]})

(def link-app {:id "1"
               :municipality "753"
               :permitType "R"
               :primaryOperation {:name "kerrostalo-rivitalo"}})

(facts "Tyonjohtaja and suunnittelijan nimeaminen tests"
  (fact "Whole"
    (fetch-tj-suunnittelija-verdict {:application tj-app :user {:username "sonja"} :created (now)}) => false
  (provided
    (organization/resolve-organization "753" "R") => {:krysp {:R {:version "2.1.8"}}}
    (meta-fields/enrich-with-link-permit-data irrelevant) => tj-app
    (application/get-link-permit-app irrelevant) => link-app
    (krysp-fetch/get-application-xml link-app :application-id) => (sade.xml/parse (slurp "resources/krysp/sample/verdict - 2.1.8 - Tekla.xml"))
    (domain/get-document-by-name tj-app irrelevant) => tj-doc
    (action/update-application irrelevant irrelevant) => nil
    )))