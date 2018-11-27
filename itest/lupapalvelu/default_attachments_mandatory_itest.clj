(ns lupapalvelu.default-attachments-mandatory-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [sade.util :as util]))

(apply-remote-minimal)

(defn not-needed-test [op mandatory?]
  (fact {:midje/description (str "Add BIM as default attachment for " op)}
    (command sipoo :organization-operations-attachments
             :operation op
             :attachments [["tietomallit", "rakennuksen_tietomalli_BIM"]]) => ok?)
  (fact {:midje/description (format "New %s application" op)}
    (let [{:keys [id attachments]} (create-application pena
                                                       :propertyId sipoo-property-id
                                                       :operation (name op))]
      (fact {:midje/description (str "Setting attachment Not needed should "
                                     (if mandatory? "fail" "succeed"))}
        (command pena :set-attachment-not-needed
                 :id id
                 :attachmentId (-> attachments first :id)
                 :notNeeded true)
        => (if mandatory? fail? ok?)))))

(facts "Default attachments mandatory (dam) for operations"
  (letfn [(sipoo-r [] (:organization (query sipoo :organization-by-user)))
          (dam-operations []
            (:default-attachments-mandatory (sipoo-r)))
          (dam-operation? [op]
            (util/includes-as-kw? (dam-operations) op))
          (toggle-dam-operation [apikey op flag]
            (command apikey :toggle-default-attachments-mandatory-operation
                     :organizationId "753-R"
                     :operationId op
                     :mandatory flag))
          (err [error]
            (partial expected-failure? error))]
    (fact "Initially no dam operations"
      (dam-operations) => empty?)
    (fact "aita not dam operation"
      (dam-operation? :aita) => false)
    (fact "Sipoo YA auth admin cannot toggle Sipoo R operation"
      (toggle-dam-operation sipoo-ya :aita true)
      => (err :error.unauthorized))
    (fact "Unselected operation cannot be toggled"
      (toggle-dam-operation sipoo :foobar true)
      => (err :error.operation-not-found))
    (fact "Add aita twice as dam operation"
      (toggle-dam-operation sipoo :aita true)
      => ok?
      (toggle-dam-operation sipoo :aita true)
      => ok?)
    (fact "Aita is listed only once"
      (dam-operations) => ["aita"])
    (fact "Add pientalo"
      (toggle-dam-operation sipoo :pientalo true) => ok?
      (dam-operation? :pientalo) => true)
    (fact "Remove aita twice"
      (toggle-dam-operation sipoo :aita false) => ok?
      (dam-operation? :aita) => false
      (toggle-dam-operation sipoo :aita false) => ok?
      (dam-operations) => ["pientalo"])
    (facts "Application default attachments mandatory or not"
      (not-needed-test :aita false)
      (not-needed-test :pientalo true))))
