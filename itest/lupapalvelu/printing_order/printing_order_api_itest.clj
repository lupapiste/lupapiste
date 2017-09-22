(ns lupapalvelu.printing-order.printing-order-api-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(facts "attachments for printing order"
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
          {:keys [primaryOperation attachments]} (query-application pena application-id)
          _ (upload-attachment pena application-id (first attachments) true :filename "dev-resources/test-pdf.pdf") ; upload file to asemapiirros placeholder
          _ (command pena :submit-application :id application-id)]
      resp => ok?
      (fact "feature not enabled in pre-verdict-state"
        (query pena :attachments-for-printing-order :id application-id) => (partial expected-failure? :error.command-illegal-state))
      (command veikko :check-for-verdict :id application-id) => ok?
      (fact "feature not in use for authorities"
        (query veikko :attachments-for-printing-order :id application-id) => (partial expected-failure? :error.unauthorized))
      (let [{:keys [attachments tagGroups] :as resp} (query pena :attachments-for-printing-order :id application-id)]
        resp => ok?
        (count attachments) => 1
        tagGroups => (contains [["paapiirustus"] ["other"]] :gaps-ok :in-any-order)))))

(facts "submit order"
       )