(ns lupapalvelu.invoices-itest
  (:require
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-util :refer :all :as itu]
            [lupapalvelu.invoice-api]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.env :as env]))

(env/with-feature-value :invoices true
  (mongo/connect!)

  (mongo/with-db itu/test-db-name
    (lupapalvelu.fixture.core/apply-fixture "minimal")

  (defn dummy-submitted-application []
    (itu/create-and-submit-local-application
     pena
     :operation "pientalo"
     :x "385770.46" :y "6672188.964"
     :address "Kaivokatu 1"
     ;;:propertyId "09143200010023"
     ))


    (fact "Invoice"
          (fact "should be created given a valid invoice"
                (let [{:keys [id] :as app} (dummy-submitted-application)
                      invoice {:operations [{:operation-id "linjasaneeraus"
                                             :name "linjasaneeraus"
                                             :invoice-rows [{:text "Laskurivi1 kpl"
                                                             :unit "kpl"
                                                             :price-per-unit 10
                                                             :units 2}
                                                            {:text "Laskurivi2 m2 "
                                                             :unit "m2"
                                                             :price-per-unit 20.5
                                                             :units 15.8}
                                                            {:text "Laskurivi3 m3 "
                                                             :unit "m3"
                                                             :price-per-unit 20.5
                                                             :units 15.8}]}]}]
                  (itu/local-command sonja :insert-invoice :id id :invoice invoice) => ok?))

          (fact "should not be created when one of the invoice-rows has an unknown unit"
                (let [{:keys [id] :as app} (dummy-submitted-application)
                      invoice {:operations [{:operation-id "linjasaneeraus"
                                             :name "linjasaneeraus"
                                             :invoice-rows [{:text "Laskurivi1 kpl"
                                                             :unit "kpl"
                                                             :price-per-unit 10
                                                             :units 2}
                                                            {:text "Laskurivi2 m3 "
                                                             :unit "UNKOWN-UNIT"
                                                             :price-per-unit 20.5
                                                             :units 15.8}]}]}]
                  (itu/local-command sonja :insert-invoice :id id :invoice invoice) => fail?))
          )
    ))


(defn foo []
  lupapalvelu.invoices/InvoiceRow)

(foo)
