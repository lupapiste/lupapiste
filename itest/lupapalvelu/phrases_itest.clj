(ns lupapalvelu.phrases-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(apply-remote-minimal)

(defn err [error]
  (partial expected-failure? error))

(fact "No phrases yet"
  (query sipoo :organization-phrases) => {:ok true})

(fact "Add new phrase"
  (command sipoo :upsert-phrase
           :category :paatosteksti
           :tag "Tag"
           :phrase "Hello") => ok?)

(facts "One phrase listed"
  (let [phrases (:phrases (query sipoo :organization-phrases))]
    phrases => (just [(contains {:tag "Tag"
                                 :category "paatosteksti"
                                 :phrase "Hello"})])
    (fact "Update phrase"
      (let [phrase-id (-> phrases first :id)]
        (command sipoo :upsert-phrase
                 :phrase-id phrase-id
                 :category "kaava"
                 :tag "Gat"
                 :phrase "World")=> ok?
        (fact "Phrase updated"
          (:phrases (query sipoo :organization-phrases))
          => [{:id       phrase-id
               :tag      "Gat"
               :category "kaava"
               :phrase   "World"}])
        (fact "Add one more phrase"
          (command sipoo :upsert-phrase
                   :category "vakuus"
                   :tag "Bah"
                   :phrase "Humbug") => ok?)
        (fact "Delete the first phrase"
          (command sipoo :delete-phrase
                   :phrase-id phrase-id) => ok?)
        (fact "Check the phrases"
          (:phrases (query sipoo :organization-phrases))
          => (just [(contains {:category "vakuus"
                               :tag "Bah"
                               :phrase "Humbug"})]))))))

(fact "Bad category"
  (command sipoo :upsert-phrase
           :category "bad"
           :tag "Gat"
           :phrase "World")
  => (err :error.invalid-category))

(fact "Bad phrase id"
  (command sipoo :upsert-phrase
           :phrase-id "bad"
           :category "muutoksenhaku"
           :tag "Gat"
           :phrase "World")
  => (err :error.phrase-not-found))
