(ns lupapalvelu.printing-order.printing-order-api-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [lupapalvelu.printing-order.mylly-client :as mylly]
            [lupapalvelu.mongo :as mongo]))

(apply-remote-minimal)

(facts "printing order itests"
  (let [{application-id :id :as response}
          (create-app pena :propertyId tampere-property-id :operation "kerrostalo-rivitalo")]
    response => ok?
    (comment-application pena application-id true) => ok?

    (let [resp (command veikko
                        :create-attachments
                        :id application-id
                        :attachmentTypes [{:type-group "paapiirustus" :type-id "asemapiirros"}
                                          {:type-group "paapiirustus" :type-id "pohjapiirustus"}]
                        :group nil)
          {:keys [attachments]} (query-application pena application-id)
          att-id (upload-attachment pena application-id (first attachments) true :filename "dev-resources/test-pdf.pdf") ; upload file to asemapiirros placeholder
          _ (command pena :submit-application :id application-id)]
      resp => ok?
      (command veikko :check-for-verdict :id application-id) => ok?
      (fact "feature not in use for authorities"
        (query veikko :attachments-for-printing-order :id application-id) => (partial expected-failure? :error.unauthorized))
      (fact "fetch available attachments for printing order"
        (let [{:keys [attachments tagGroups] :as resp} (query pena :attachments-for-printing-order :id application-id)]
          resp => ok?
          (count attachments) => 1
          tagGroups => (contains [["paapiirustus"] ["other"]] :gaps-ok :in-any-order)))

      (facts "submit order"
        (let [order-amounts {att-id 1}
              contacts {:orderer {:firstName "Pena"
                                  :lastName "Panaani"
                                  :streetAddress "PL 109"
                                  :postalCode "99999"
                                  :city "Korvatunturi"
                                  :email "pena@example.com"}
                        :payer-same-as-orderer true
                        :delivery-same-as-orderer true}]
          (fact "submit"
            (command pena :submit-printing-order :id application-id :order order-amounts :contacts contacts) => ok?
             (Thread/sleep 1000)
             (:orders (query pena :my-printing-orders)) => (has some (contains {:application application-id :acknowledged? true}))))))))