(ns lupapalvelu.ya-digging-permit-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.application :as app]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.operations :as operations]
            [sade.env :as env]
            [sade.property :as prop]))

(apply-remote-minimal)

(facts "creating digging permit"
  (fact "fails if source application is not sijoituslupa/sopimus"
    (let [source-app (create-and-submit-application pena :operation "kerrostalo-rivitalo")]
      (command pena :create-digging-permit :id (:id source-app)
               :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")
      => (partial expected-failure? "error.invalid-digging-permit-source")))

  (fact "fails if operation is not digging operation"
    (let [source-app (create-and-submit-application pena :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")]
      (give-verdict sonja (:id source-app)) => ok?
      (command pena :create-digging-permit :id (:id source-app)
               :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")
      => (partial expected-failure? "error.not-digging-permit-operation")))

  (fact "fails if operation is not selected for source permit's organization"
    (let [source-app (create-and-submit-application pena :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")]
      (give-verdict sonja (:id source-app)) => ok?
      (command pena :create-digging-permit :id (:id source-app)
               :operation "ya-katulupa-maalampotyot") ; not selected for Sipoo YA in minimal fixture
      => (partial expected-failure? "error.operations.hidden"))))
