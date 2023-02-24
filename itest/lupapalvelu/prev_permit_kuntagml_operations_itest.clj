(ns lupapalvelu.prev-permit-kuntagml-operations-itest
  "Fill kuntagml-toimenpide information from message."
  (:require [clojure.java.io :as io]
            [clojure.set :refer [rename-keys]]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.util :as util]
            [sade.xml :as xml]))

(def xml-resource (io/resource "krysp/dev/kuntagml-toimenpiteet.xml"))

(fact "Test file is valid"
  (validator/validate (slurp xml-resource) "R" "2.2.2") => nil)

(def xml (xml/parse (io/input-stream xml-resource)))

(defn check-toimenpide [datas toimenpide & kvs]
  (let [data (util/find-first (util/fn-> :kuntagml-toimenpide :toimenpide
                                         :value (= toimenpide))
                              datas)
        expected (merge {:perusparannuskytkin            false
                         :rakennustietojaEimuutetaKytkin false
                         :muutostyolaji                  nil}
                        {:toimenpide toimenpide}
                        (rename-keys (apply hash-map kvs)
                                     {:basic     :perusparannuskytkin
                                      :no-change :rakennustietojaEimuutetaKytkin
                                      :work      :muutostyolaji}))
        value    (util/map-values :value (:kuntagml-toimenpide data))]
    (fact {:midje/description (str "Toimenpide: " toimenpide)}
      data => truthy
      value => expected)))

(mount/start #'mongo/connection)
(mongo/with-db test-db-name
  (fixture/apply-fixture "minimal")
  (with-local-actions
    (against-background
      [(krysp-fetch/get-application-xml-by-backend-id anything anything) => xml]
      (facts "KuntaGML operations"
        (let [{app-id :id} (command raktark-jarvenpaa
                                    :create-application-from-previous-permit
                                    :lang "fi"
                                    :organizationId "186-R"
                                    :kuntalupatunnus "This is my id"
                                    :x "6707184.319"
                                    :y "393021.589"
                                    :address "Any old address"
                                    :authorizeApplicants false
                                    :propertyId "")
              application  (query-application raktark-jarvenpaa app-id)
              datas        (map :data (domain/get-documents-by-name application
                                                                    "aiemman-luvan-toimenpide"))]
          (fact "Application is created"
            application => truthy)

          (fact "Six target documents"
            (count datas) => 6)

          (check-toimenpide datas "uusi")

          (check-toimenpide datas "laajennus" :basic true)

          (check-toimenpide datas "uudelleenrakentaminen"
                            :basic true
                            :work "perustusten ja kantavien rakenteiden muutos- ja korjaustyöt")
          (check-toimenpide datas "purkaminen")

          (check-toimenpide datas "muuMuutosTyo"
                            :basic true
                            :work "rakennuksen pääasiallinen käyttötarkoitusmuutos"
                            :no-change true)

          (check-toimenpide datas "kaupunkikuvaToimenpide")

          (facts "Change permit"
            (let [{app-id :id} (command raktark-jarvenpaa :create-change-permit :id app-id)
                  doc-id       (->> (query-application raktark-jarvenpaa app-id)
                                    :documents
                                    (util/find-first (util/fn-> :schema-info :name
                                                                (= "aiemman-luvan-toimenpide")))
                                    :id)]
              doc-id => truthy
              (fact "Submit, approve and return for modifications"
                (command raktark-jarvenpaa :submit-application :id app-id) => ok?
                (command raktark-jarvenpaa :approve-application :id app-id :lang "fi") => ok?
                (command raktark-jarvenpaa :request-for-complement :id app-id) => ok?)
              (fact "Cannot be approved without toimenpide"
                (command raktark-jarvenpaa :update-doc :id app-id
                         :collection "documents"
                         :doc doc-id
                         :updates [["kuntagml-toimenpide.toimenpide" nil]]) => ok?
                (command raktark-jarvenpaa :approve-application :id app-id :lang "en")
                => {:details "Operation type has not been selected."
                    :ok      false
                    :text    "error.integration.create-message"})
              (fact "Fix and try again"
                (command raktark-jarvenpaa :update-doc :id app-id
                         :collection "documents"
                         :doc doc-id
                         :updates [["kuntagml-toimenpide.toimenpide"
                                    "kaupunkikuvaToimenpide"]]) => ok?
                (command raktark-jarvenpaa :approve-application :id app-id :lang "en") => ok?))))))))
