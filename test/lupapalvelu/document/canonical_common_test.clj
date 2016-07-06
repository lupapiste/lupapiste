(ns lupapalvelu.document.canonical-common-test
  (:require [lupapalvelu.document.canonical-common :refer :all]
            [midje.sweet :refer :all]))

(facts "timestamps"
  (let [day (* 24 60 60 1000)
        app (-> lupapalvelu.domain/application-skeleton
              (assoc
                :created 0
                :opened day
                :complementNeeded (* 2 day)
                :submitted (* 3 day)
                :sent (* 4 day)
                :started (* 5 day)
                :closed (* 6 day))
              (update-in [:verdicts] conj {:timestamp (* 7 day)}))]

   (fact "complementNeeded" (state-timestamp (assoc app :state "complementNeeded")) => (* 2 day))
   (fact "submitted" (state-timestamp (assoc app :state "submitted")) => (* 3 day))
   (fact "sent" (state-timestamp (assoc app :state "sent")) => (* 3 day))
   (fact "constructionStarted" (state-timestamp (assoc app :state "constructionStarted")) => (* 5 day))
   (fact "closed" (state-timestamp (assoc app :state "closed")) => (* 6 day))
   (fact "verdictGiven" (state-timestamp (assoc app :state "verdictGiven")) => (* 7 day))

   (all-state-timestamps app) => {:complementNeeded (* 2 day)
                                  :submitted (* 3 day)
                                  :sent (* 3 day)
                                  :constructionStarted (* 5 day)
                                  :closed (* 6 day)
                                  :verdictGiven (* 7 day)
                                  :foremanVerdictGiven (* 7 day)}

   (all-state-timestamps (dissoc app :closed :verdicts :started :complementNeeded)) => {:complementNeeded nil
                                                                                        :submitted (* 3 day)
                                                                                        :sent (* 3 day)
                                                                                        :constructionStarted nil
                                                                                        :closed nil
                                                                                        :verdictGiven nil
                                                                                        :foremanVerdictGiven nil}

   ))

(facts "Format maara-alatunnus"
  (fact "nil" (format-maara-alatunnus nil) => nil)
  (fact "blank" (format-maara-alatunnus "") => nil)
  (fact "space" (format-maara-alatunnus " ") => nil)
  (fact "1 num" (format-maara-alatunnus "1") => "M0001")
  (fact "2 num" (format-maara-alatunnus "12") => "M0012")
  (fact "3 num" (format-maara-alatunnus "123") => "M0123")
  (fact "4 num" (format-maara-alatunnus "1234") => "M1234")
  (fact "5 num" (format-maara-alatunnus "12345") => nil)
  (fact "M+1 num" (format-maara-alatunnus "M1") => "M0001")
  (fact "M+4 num" (format-maara-alatunnus "M1234") => "M1234")
  (fact "M+5 num" (format-maara-alatunnus "M12345") => nil)
  (fact "some odd data from prod"
    (format-maara-alatunnus "K286-T4") => nil
    (format-maara-alatunnus " 1:64") => nil
    (format-maara-alatunnus "696-415-7-11") => nil))

(facts "maaraalatunnus"
  (fact "nil" (maaraalatunnus nil) => nil)
  (fact "empty" (maaraalatunnus {}) => nil)
  (fact "1 num" (maaraalatunnus {:kiinteistoTunnus "kt", :maaraalaTunnus "1"}) => "ktM0001")
  (fact "2 num" (maaraalatunnus {:kiinteistoTunnus "kt", :maaraalaTunnus "12"}) => "ktM0012")
  (fact "M+3 num" (maaraalatunnus {:kiinteistoTunnus "kt", :maaraalaTunnus "M123"}) => "ktM0123")
  (fact "4 num" (maaraalatunnus {:kiinteistoTunnus "kt", :maaraalaTunnus "1234"}) => "ktM1234")
  (fact "5 num" (maaraalatunnus {:kiinteistoTunnus "kt", :maaraalaTunnus "12345"}) => nil)
  (fact "M+1 num with app" (maaraalatunnus {:kiinteistoTunnus "kt", :maaraalaTunnus "1"}, {:propertyId "kiinteisto"}) => "kiinteistoM0001")
  )

(facts address->osoitetieto
  (address->osoitetieto {}) => nil
  (address->osoitetieto {:street nil :zip "12312" :city "kaupunki"}) => nil
  (address->osoitetieto {:street ""  :zip "12312" :city "kaupunki"}) => nil
  (address->osoitetieto {:street "katukatu"}) => {:osoitenimi {:teksti "katukatu"}}
  (address->osoitetieto {:street "katukatu" :zip "12312" :city "kaupunki"}) => {:osoitenimi {:teksti "katukatu"} :postinumero "12312" :postitoimipaikannimi "kaupunki"})

(facts "schema-info-filter"
  (let [doc {:schema-info {:op  {}
                           :foo :bar}}]
    (schema-info-filter [doc] :op)        => (contains doc)
    (schema-info-filter [doc] :quux)      => empty?
    (schema-info-filter [doc] :foo)       => (contains doc)
    (schema-info-filter [doc] :foo "bar") => (contains doc)
    (schema-info-filter [doc] :foo "not") => empty?))
