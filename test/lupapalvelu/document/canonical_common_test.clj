(ns lupapalvelu.document.canonical-common-test
  (:require [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.permit :as permit]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.document.canonical-common
                   get-handler
                   foreman-tasks-by-role
                   get-vastattava-tyotieto)

(facts "all permit types have canonical fn"
  (doseq [permit (keys (permit/permit-types))
          :when (not (contains? #{"A" "ARK" "YM"} (str permit)))
          :let [app (merge domain/application-skeleton {:id "123" :permitType permit :permitSubtype "poikkeamislupa"})]]
    (fact {:midje/description (str permit)}
      (application->canonical app "fi") => truthy)))

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
                :closed (* 6 day)
                :finished (* 6 day)
                :agreementPrepared (* 6 day)
                :agreementSigned (* 7 day)
                :inUse (* 8 day))
              (update-in [:verdicts] conj {:timestamp (* 7 day)}))]

   (fact "complementNeeded" (state-timestamp (assoc app :state "complementNeeded")) => (* 2 day))
   (fact "submitted" (state-timestamp (assoc app :state "submitted")) => (* 3 day))
   (fact "sent" (state-timestamp (assoc app :state "sent")) => (* 3 day))
   (fact "constructionStarted" (state-timestamp (assoc app :state "constructionStarted")) => (* 5 day))
   (fact "closed" (state-timestamp (assoc app :state "closed")) => (* 6 day))
   (fact "verdictGiven" (state-timestamp (assoc app :state "verdictGiven")) => (* 7 day))
   (fact "agreementSigned" (state-timestamp (assoc app :state "agreementSigned")) => (* 7 day))
   (fact "inUse" (state-timestamp (assoc app :state "inUse")) => (* 8 day))

   (all-state-timestamps app) => {:complementNeeded (* 2 day)
                                  :submitted (* 3 day)
                                  :sent (* 3 day)
                                  :constructionStarted (* 5 day)
                                  :closed (* 6 day)
                                  :verdictGiven (* 7 day)
                                  :foremanVerdictGiven (* 7 day)
                                  :finished (* 6 day)
                                  :agreementPrepared (* 6 day)
                                  :agreementSigned (* 7 day)
                                  :inUse (* 8 day)}

   (all-state-timestamps (dissoc app :closed :verdicts :started :complementNeeded))
   => {:complementNeeded nil
       :submitted (* 3 day)
       :sent (* 3 day)
       :constructionStarted nil
       :closed nil
       :verdictGiven nil
       :foremanVerdictGiven nil
       :finished (* 6 day)
       :agreementPrepared (* 6 day)
       :agreementSigned (* 7 day)
       :inUse (* 8 day)}))

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

(facts get-handler
  (fact "empty handlers"
    (get-handler {:handlers []}) => "")

  (fact "nil handlers"
    (get-handler {:handlers nil}) => "")

  (fact "no general handler"
    (get-handler {:handlers [{:firstName "first-name" :lastName "last-name"}]}) => "")

  (fact "with general handler"
    (get-handler {:handlers [{:firstName "first-name" :lastName "last-name" :general true}]})
    => {:henkilo {:nimi {:etunimi "first-name", :sukunimi "last-name"}}})

  (fact "multiple handlers with general"
    (get-handler {:handlers [{:firstName "other-first-name" :lastName "other-last-name"}
                             {:firstName "first-name" :lastName "last-name" :general true}
                             {:firstName "other-first-name-2" :lastName "other-last-name-2"}]})
    => {:henkilo {:nimi {:etunimi "first-name", :sukunimi "last-name"}}}))

(facts positive-integer
  (fact "positive integer string"
    (positive-integer "1") => "1")

  (fact "positive integer long"
    (positive-integer 1000) => 1000)

  (fact "positive integer keyword"
    (positive-integer :9) => :9)

  (fact "negative integer string"
    (positive-integer "-1") => nil)

  (fact "negative integer long"
    (positive-integer -1) => nil)

  (fact "0 as string"
    (positive-integer "0") => nil)

  (fact "nil"
    (positive-integer nil) => nil))

(facts foreman-tasks-by-role
  (let [tasks (assoc (zipmap [:ulkopuolinenKvvTyo :sisapuolinenKvvTyo
                              :ivLaitoksenAsennustyo :ivLaitoksenKorjausJaMuutostyo
                              :rakennuksenPurkaminen :uudisrakennustyoIlmanMaanrakennustoita :maanrakennustyot
                              :uudisrakennustyoMaanrakennustoineen :rakennuksenMuutosJaKorjaustyo :linjasaneeraus
                              :some :unsupported :keys]
                             (map (constantly true) (range 100)))
                     :muuMika "Some other task")
        ftbr  #(keys (foreman-tasks-by-role {:kuntaRoolikoodi % :vastattavatTyotehtavat tasks}))]
    (ftbr "KVV-ty\u00f6njohtaja") => (just [:ulkopuolinenKvvTyo :sisapuolinenKvvTyo :muuMika] :in-any-order)
    (ftbr "IV-ty\u00f6njohtaja") => (just [:ivLaitoksenAsennustyo :ivLaitoksenKorjausJaMuutostyo :muuMika] :in-any-order)
    (ftbr "erityisalojen ty\u00f6njohtaja") => [:muuMika]
    (ftbr "vastaava ty\u00f6njohtaja") => (just [:rakennuksenPurkaminen :uudisrakennustyoIlmanMaanrakennustoita
                                                 :maanrakennustyot :uudisrakennustyoMaanrakennustoineen
                                                 :rakennuksenMuutosJaKorjaustyo :linjasaneeraus :muuMika]
                                                :in-any-order)
    (ftbr "ty\u00f6njohtaja") => (just [:rakennuksenPurkaminen :uudisrakennustyoIlmanMaanrakennustoita
                                        :maanrakennustyot :uudisrakennustyoMaanrakennustoineen
                                        :rakennuksenMuutosJaKorjaustyo :linjasaneeraus :muuMika]
                                       :in-any-order)
    (ftbr "ei tiedossa") => nil
    (ftbr "not supported role") => nil))

(facts get-vastattava-tyotieto
  (let [get-vt #(get-vastattava-tyotieto {:vastattavatTyotehtavat %} "fi")
        wrap   #(just (->> %
                           (mapv (partial assoc-in {} [:VastattavaTyo :vastattavaTyo]))
                           (hash-map :vastattavaTyotieto))
                      :in-any-order)]
    (get-vt {}) => nil
    (get-vt {:ulkopuolinenKvvTyo true}) => (wrap ["Ulkopuolinen KVV-työ"])
    (get-vt {:ulkopuolinenKvvTyo true
             :sisapuolinenKvvTyo false}) => (wrap ["Ulkopuolinen KVV-työ"])
    (get-vt {:ulkopuolinenKvvTyo true
             :sisapuolinenKvvTyo true}) => (wrap ["Ulkopuolinen KVV-työ" "Sisäpuolinen KVV-työ"])
    (get-vt {:muuMikaValue "test"}) => (wrap [])
    (get-vt {:muuMika true
             :muuMikaValue "test"}) => (wrap ["test"])
    (get-vt {:muuMika false
             :muuMikaValue "test"}) => (wrap [])
    (get-vt {:muuMika true
             :muuMikaValue ""}) => (wrap [])
    (get-vt {:ulkopuolinenKvvTyo true
             :sisapuolinenKvvTyo false
             :muuMika true
             :muuMikaValue "test"}) => (wrap ["Ulkopuolinen KVV-työ" "test"])))
