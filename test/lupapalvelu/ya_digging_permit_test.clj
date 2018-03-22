(ns lupapalvelu.ya-digging-permit-test
  (:require [lupapalvelu.ya-digging-permit :refer :all]
            [midje.sweet :refer :all]

            [lupapalvelu.application :as app]
            [lupapalvelu.mock.organization :as mock-org]
            [lupapalvelu.mock.user :as mock-usr]
            [lupapalvelu.user :as usr]
            [sade.env :as env]))

(fact "digging-permit-can-be-created?"
  (digging-permit-can-be-created? {:permitType "not YA"}) => false
  (digging-permit-can-be-created? {:permitType    "YA"
                                   :permitSubtype "sijoituslupa"
                                   :state         :draft}) => false
  (digging-permit-can-be-created? {:permitType "YA"
                                   :state      :verdictGiven}) => false
  (digging-permit-can-be-created? {:permitType    "YA"
                                   :permitSubtype "sijoituslupa"
                                   :state         :verdictGiven}) => true)

(fact "organization-digging-operations"
  (organization-digging-operations {:selected-operations ["ya-katulupa-muu-liikennealuetyo" ; digging operation
                                                          "kerrostalo-rivitalo"             ; not digging operation
                                                          ]})
  => [["yleisten-alueiden-luvat"
       [["katulupa"
         [["liikennealueen-rajaaminen-tyokayttoon"
           [["muu-liikennealuetyo" :ya-katulupa-muu-liikennealuetyo]]]]]]]])

(facts "new-digging-permit"
  (against-background (env/feature? :disable-ktj-on-create) => true)
  (mock-org/with-all-mocked-orgs
    (with-redefs [app/make-application-id (constantly "LP-123")
                  usr/get-user-by-id mock-usr/users-by-id]
      (let [invalid-source-application (app/do-create-application {:data {:operation "kerrostalo-rivitalo"
                                                                          :x 0 :y 0
                                                                          :address "Latokuja 1"
                                                                          :propertyId "75342600060211"
                                                                          :messages []}
                                                                   :user mock-usr/pena
                                                                   :created 12345})
            source-application (-> (app/do-create-application {:data {:operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen"
                                                                      :x 0 :y 0
                                                                      :address "Latokuja 1"
                                                                      :propertyId "75342600060211"
                                                                      :messages []}
                                                               :user mock-usr/pena
                                                               :created 12345})
                                   (assoc :state :verdictGiven))]

        (fact "source application must be sijoituslupa"
          (new-digging-permit invalid-source-application
                              mock-usr/pena 23456 "ya-katulupa-vesi-ja-viemarityot"
                              mock-org/sipoo-ya)
          => (throws #"digging-permit-can-be-created?")
          (new-digging-permit source-application mock-usr/pena 23456 "ya-katulupa-vesi-ja-viemarityot"
                              mock-org/sipoo-ya)
          => truthy)

        (fact "source application must be in post verdict state"
          (new-digging-permit (assoc source-application :state :draft)
                              mock-usr/pena 23456 "ya-katulupa-vesi-ja-viemarityot"
                              mock-org/sipoo-ya)
          => (throws #"digging-permit-can-be-created?")

          (new-digging-permit (assoc source-application :state :open)
                              mock-usr/pena 23456 "ya-katulupa-vesi-ja-viemarityot"
                              mock-org/sipoo-ya)
          => (throws #"digging-permit-can-be-created?")

          (new-digging-permit (assoc source-application :state :submitted)
                              mock-usr/pena 23456 "ya-katulupa-vesi-ja-viemarityot"
                              mock-org/sipoo-ya)
          => (throws #"digging-permit-can-be-created?"))

        (fact "operation must be a digging operation selected by the organization of the source application"
          (new-digging-permit source-application mock-usr/pena 12345 "kerrostalo-rivitalo" mock-org/sipoo-ya)
          => (throws #"digging-permit-operation?"))

        (fact "digging permit has the same location, property id and address as the source application"
          (-> (new-digging-permit source-application
                                  mock-usr/pena 23456 "ya-katulupa-vesi-ja-viemarityot"
                                  mock-org/sipoo-ya)
              (select-keys [:location :location-wgs84 :propertyId :propertyIdSource :address]))
          => (select-keys source-application [:location :location-wgs84 :propertyId :propertyIdSource :address]))

        (fact "digging permit has correct primary operation"
          (-> (new-digging-permit source-application
                                  mock-usr/pena 23456 "ya-katulupa-vesi-ja-viemarityot"
                                  mock-org/sipoo-ya)
              :primaryOperation :name)
          => "ya-katulupa-vesi-ja-viemarityot")))))
