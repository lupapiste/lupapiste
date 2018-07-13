(ns lupapalvelu.phrases-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(apply-remote-minimal)

(def org-id "753-R")

(defn err [error]
  (partial expected-failure? error))

(fact "No phrases yet"
  (query sipoo :organization-phrases :org-id org-id)
  => {:ok true :phrases []})

(fact "Add new phrase"
  (command sipoo :upsert-phrase
           :org-id org-id
           :category :paatosteksti
           :tag "Tag"
           :phrase "Hello") => ok?)

(facts "One phrase listed"
  (let [phrases (:phrases (query sipoo :organization-phrases :org-id org-id))]
    phrases => (just [(contains {:tag "Tag"
                                 :category "paatosteksti"
                                 :phrase "Hello"})])
    (fact "Update phrase"
      (let [phrase-id (-> phrases first :id)]
        (command sipoo :upsert-phrase
                 :org-id org-id
                 :phrase-id phrase-id
                 :category "kaava"
                 :tag "Gat"
                 :phrase "World")=> ok?
        (fact "Phrase updated"
          (:phrases (query sipoo :organization-phrases :org-id org-id))
          => [{:id       phrase-id
               :tag      "Gat"
               :category "kaava"
               :phrase   "World"}])
        (fact "Add one more phrase"
          (command sipoo :upsert-phrase
                   :org-id org-id
                   :category "yleinen"
                   :tag "Bah"
                   :phrase "Humbug") => ok?)
        (fact "Delete the first phrase"
          (command sipoo :delete-phrase
                   :org-id org-id
                   :phrase-id phrase-id) => ok?)
        (fact "Check the phrases"
          (:phrases (query sipoo :organization-phrases :org-id org-id))
          => (just [(contains {:category "yleinen"
                               :tag "Bah"
                               :phrase "Humbug"})]))))))

(facts "Application phrases"
  (fact "Phrases for 753-R application"
    (let [app-id (create-app-id pena
                                :operation :pientalo
                                :propertyId sipoo-property-id)]
      (fact "Application organization is 753-R"
        (:organization (query-application pena app-id)) => "753-R")
      (:phrases (query sonja :application-phrases :id app-id))
      => (just [(contains {:category "yleinen"
                           :tag      "Bah"
                           :phrase   "Humbug"})])))
  (fact "No phrases for 753-YA application"
    (let [app-id (create-app-id pena
                                :operation :ya-katulupa-vesi-ja-viemarityot
                                :propertyId sipoo-property-id)]
      (fact "Application organization is 753-YA"
        (:organization (query-application pena app-id)) => "753-YA")
      (:phrases (query sonja :application-phrases :id app-id))
      => [])))

(fact "Bad category"
  (command sipoo :upsert-phrase
           :org-id org-id
           :category "bad"
           :tag "Gat"
           :phrase "World")
  => (err :error.invalid-category))

(fact "Bad phrase id"
  (command sipoo :upsert-phrase
           :org-id org-id
           :phrase-id "bad"
           :category "muutoksenhaku"
           :tag "Gat"
           :phrase "World")
  => (err :error.phrase-not-found))

(fact "Bad org-id"
  (query sipoo :organization-phrases :org-id "bad")
  => (err :error.invalid-organization))
