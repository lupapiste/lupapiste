(ns lupapalvelu.matti-verdict-bulletins-itest
  (:require [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.matti-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(apply-remote-minimal)

(facts ""
  (let [{id :id} (init-verdict-template sipoo "r") =not=> nil?
        _ (publish-verdict-template sipoo id)
        {app-id :id} (create-and-submit-application sonja :operation "kerrostalo-rivitalo"
                                             :propertyId sipoo-property-id
                                             :x 406898.625 :y 6684125.375
                                             :address "Hitantine 108")]
    (command sonja :new-matti-verdict-draft :id app-id :template-id id)))