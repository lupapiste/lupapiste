(ns lupapalvelu.review-pdf-test
  (:require [lupapalvelu.review-pdf :as review-pdf]
            [lupapalvelu.tasks  :as tasks]
            [midje.sweet :refer :all]
            [sade.strings :as ss]))

(facts "review-type-id"
  (review-pdf/review-type-id nil) => "katselmuksen_tai_tarkastuksen_poytakirja"
  (->> tasks/task-types
       (cons "foobar")
       (map (fn [tt]
              (let [tT (ss/join (map #(cond-> %
                                        (pos? (rand-int 2)) ss/upper-case)
                                     tt))]
                [tt (review-pdf/review-type-id (assoc-in {} [:data :katselmuksenLaji] tT))])))
       (into {})) => {"foobar"                                           "katselmuksen_tai_tarkastuksen_poytakirja"
                      "muu katselmus"                                    "katselmuksen_tai_tarkastuksen_poytakirja"
                      "muu tarkastus"                                    "katselmuksen_tai_tarkastuksen_poytakirja"
                      "aloituskokous"                                    "aloituskokouksen_poytakirja"
                      "rakennuksen paikan merkitseminen"                 "katselmuksen_tai_tarkastuksen_poytakirja"
                      "rakennuksen paikan tarkastaminen"                 "katselmuksen_tai_tarkastuksen_poytakirja"
                      "pohjakatselmus"                                   "katselmuksen_tai_tarkastuksen_poytakirja"
                      "rakennekatselmus"                                 "katselmuksen_tai_tarkastuksen_poytakirja"
                      "lämpö-, vesi- ja ilmanvaihtolaitteiden katselmus" "katselmuksen_tai_tarkastuksen_poytakirja"
                      "osittainen loppukatselmus"                        "katselmuksen_tai_tarkastuksen_poytakirja"
                      "loppukatselmus"                                   "loppukatselmuksen_poytakirja"
                      "ei tiedossa"                                      "katselmuksen_tai_tarkastuksen_poytakirja"})

(facts "tag-and-descrption"
  (let [house1 {:nationalId  "house1"
                :index       "1"
                :description "One"
                :operationId "op1"}
        house2 {:nationalId  "house2"
                :index       "2"
                :description "Two"
                :operationId "op2"}
        doc1 {:data {:tunnus "Tag1"}
              :schema-info {:op {:id "op1"}}}
        doc2 {:data {:tunnus "Tag2"}
              :schema-info {:op {:id "op2"}}}]
    (review-pdf/tag-and-description {:buildings [house1] :documents [doc1]}
                                    "house1" "1") => "Tag1 \u2013 One"
    (review-pdf/tag-and-description {:buildings [house1] :documents [doc1]}
                                    "house1" nil) => "Tag1 \u2013 One"
    (review-pdf/tag-and-description {:buildings [house1] :documents [doc1]}
                                    nil "1") => "Tag1 \u2013 One"
    (review-pdf/tag-and-description {:buildings [house1] :documents [doc2]}
                                    nil "1") => "One"
    (review-pdf/tag-and-description {:buildings [house1 (assoc house2 :description "   ")]
                                     :documents [doc2]}
                                    "house2" "2") => "Tag2"
    (review-pdf/tag-and-description {:buildings [house1 (assoc house2 :description "   ")]
                                     :documents [doc2]}
                                    "house2" "2") => "Tag2"))

(facts "buildings"
  (let [house (fn [vtj-prt tila & [in-use]] {:rakennus {:valtakunnallinenNumero vtj-prt}
                                             :tila     {:tila           tila
                                                        :kayttoonottava (boolean in-use)}})
        application {:buildings [{:nationalId "123"
                                  :description "One"
                                  :operationId "op1"}
                                 {:nationalId "321"
                                  :description "Three"
                                  :operationId "op3"}]
                     :documents [{:data {:tunnus "A"}
                                  :schema-info {:op {:id "op1"}}}
                                 {:data {:tunnus "C"}
                                  :schema-info {:op {:id "op3"}}}]}]
    (fact "One building"
      (review-pdf/buildings :fi application (assoc-in {} [:data :rakennus :0] (house "123" "osittainen")))
      => ["A \u2013 One: 123, osittainen katselmus"])
    (fact "Two buildings"
      (review-pdf/buildings :fi application (assoc-in {} [:data :rakennus] {:0 (house "123" "osittainen")
                                                                            :1 (house "321" "lopullinen" true)}))
      => ["A \u2013 One: 123, osittainen katselmus"
          "C \u2013 Three: 321, lopullinen katselmus, käyttöönotettu"])
    (fact "Two buildings, but only one in review"
      (review-pdf/buildings :fi application (assoc-in {} [:data :rakennus] {:0 (house "123" "")
                                                                            :1 (house "321" "lopullinen")}))
      => ["C \u2013 Three: 321, lopullinen katselmus"])
    (fact "Two buildings, none in review"
      (review-pdf/buildings :fi application (assoc-in {} [:data :rakennus] {:0 (house "123" "")
                                                                :1 (house "321" nil)}))
      => nil)
    (fact "No buildings"
      (review-pdf/buildings :fi application {}) => nil
      (review-pdf/buildings :fi application nil) => nil)))

(facts "foremen"
  (fact "No foreman applications"
    (review-pdf/foremen {}) => nil
    (provided (lupapalvelu.foreman-application-util/get-linked-foreman-applications anything) => []))
  (fact "One foreman application"
    (review-pdf/foremen {}) => [{:code     "erityisalojen työnjohtaja"
                                 :fullname "Tyyne Työnjohtaja"}]
    (provided (lupapalvelu.foreman-application-util/get-linked-foreman-applications anything)
              => [{:documents [{:schema-info {:name "tyonjohtaja-v2"}
                                :data        {:kuntaRoolikoodi {:value "erityisalojen työnjohtaja"}
                                              :henkilotiedot   {:etunimi  {:value "Tyyne"}
                                                                :sukunimi {:value "Työnjohtaja"}}}}]}]))
  (fact "Two foreman applications"
    (review-pdf/foremen {}) => [{:code     "erityisalojen työnjohtaja"
                                 :fullname "Tyyne Työnjohtaja"}
                                {:code     "vastaava työnjohtaja"
                                 :fullname "Vaski Vastaava"}]
    (provided (lupapalvelu.foreman-application-util/get-linked-foreman-applications anything)
              => [{:documents [{:schema-info {:name "tyonjohtaja-v2"}
                                :data        {:kuntaRoolikoodi {:value "erityisalojen työnjohtaja"}
                                              :henkilotiedot   {:etunimi  {:value "Tyyne"}
                                                                :sukunimi {:value "Työnjohtaja"}}}}]}
                  {:documents [{:schema-info {:name "tyonjohtaja-v2"}
                                :data        {:kuntaRoolikoodi {:value "vastaava työnjohtaja"}
                                              :henkilotiedot   {:etunimi  {:value "Vaski"}
                                                                :sukunimi {:value "Vastaava"}}}}]}])))

(facts "subtitle"
  (fact "Tila and taskname both OK"
    (review-pdf/subtitle :fi "  JOKU KATSELMUS  " "OSIttainen")
    => "Osittainen joku katselmus"
    (review-pdf/subtitle :fi "  muu ihmettely  " "loPULLINen")
    => "Lopullinen muu ihmettely")
  (fact "Tila bad or missing"
    (review-pdf/subtitle :fi "  JOKU KATSELMUS  " "bad")
    => "Joku katselmus"
    (review-pdf/subtitle :fi "  muu ihmettely  " "")
    => "Muu ihmettely"
    (review-pdf/subtitle :fi "  muu ihmettely  " "  ")
    => "Muu ihmettely"
    (review-pdf/subtitle :fi "  muu ihmettely  " nil)
    => "Muu ihmettely")
  (fact "Taskname missing"
    (review-pdf/subtitle :fi "" "lopullinen")
    => "Lopullinen"
    (review-pdf/subtitle :fi "  " "lopullinen")
    => "Lopullinen"
    (review-pdf/subtitle :fi nil "osittainen")
    => "Osittainen")
  (fact "Both bad or missing"
    (review-pdf/subtitle :fi "" "bad") => nil
    (review-pdf/subtitle :fi "  " " ") => nil
    (review-pdf/subtitle :fi nil nil) => nil
    (review-pdf/subtitle :fi nil "") => nil))

(def foo-organization {:name       {:fi "Onpahan vaan organisaatio"}
                       :review-pdf {:rectification-enabled true
                                    :rectification-info    "Ohje oikaisuvaatimuksen tiimoilta"
                                    :contact               "Oudohko osoite"}})

(against-background
  [(lupapalvelu.foreman-application-util/get-linked-foreman-applications anything) => []
   (lupapalvelu.application-meta-fields/enrich-with-link-permit-data anything) => {}
   (lupapalvelu.tiedonohjaus/tos-function-with-name anything anything) => nil
   (lupapalvelu.organization/get-organization "foo") => foo-organization]
  (facts "review-properties"
    (let [bs-verdict     {:id              "bs-verdict"
                          :kuntalupatunnus "vellikello"
                          :timestamp       11}
          legacy-verdict {:id        "legacy-verdict"
                          :category  "r"
                          :legacy?   true
                          :data      {:kuntalupatunnus "leegio"}
                          :published {:published 22}}
          pate-verdict   {:id         "pate-verdict"
                          :category   "r"
                          :references {:organization-name "Paten parempi organisaatio"}
                          :published  {:published 3}}
          application    {:id           "app-id"
                          :address      "My address"
                          :propertyId   "75341608760002"
                          :organization "foo"
                          :documents    []}
          review         {:id       "review-id"
                          :taskname "This is review"
                          :data     {:katselmuksenLaji "aloituskokous"
                                     :katselmus        {:tila "osittainen"}}}
          options        {:lang         "fi"
                          :organization foo-organization
                          :application  application
                          :review       review}]
      (fact "Review from backing system verdit"
        (let [props (review-pdf/review-properties (-> options
                                                      (assoc-in [:application :verdicts] [bs-verdict])
                                                      (assoc-in [:review :source] {:type "verdict"
                                                                                   :id   (:id bs-verdict)})))]
          props => (contains {:construction-site    "753-416-876-2\nMy address"
                              :kuntalupatunnus      "vellikello"
                              :organization-contact "Oudohko osoite"
                              :organization-name    "Onpahan vaan organisaatio"
                              :subtitle             "Osittainen this is review"
                              :title                "Aloituskokouksen pöytäkirja"
                              :type-group           "katselmukset_ja_tarkastukset"
                              :type-id              "aloituskokouksen_poytakirja"})
          (:rectification props) => nil
          (:loppukatselmus? props) => nil))
      (fact "Review from legacy verdit"
        (review-pdf/review-properties (-> options
                                          (assoc-in [:application :pate-verdicts] [legacy-verdict])
                                          (assoc-in [:review :source] {:type "verdict"
                                                                       :id   (:id legacy-verdict)})))
        => (contains {:construction-site    "753-416-876-2\nMy address"
                      :kuntalupatunnus      "leegio"
                      :organization-contact "Oudohko osoite"
                      :organization-name    "Onpahan vaan organisaatio"}))
      (fact "Review from Pate verdit"
        (let [props (review-pdf/review-properties (-> options
                                                      (assoc-in [:application :pate-verdicts] [pate-verdict])
                                                      (assoc-in [:review :source] {:type "verdict"
                                                                                   :id   (:id pate-verdict)})))]
          props => (contains {:construction-site    "753-416-876-2\nMy address"
                              :organization-contact "Oudohko osoite"
                              :organization-name    "Paten parempi organisaatio"})
          (:kuntalupatunnus props) => nil))
      (fact "Review without verdict"
        (review-pdf/review-properties (-> options
                                          (assoc-in [:application :verdicts] [bs-verdict])
                                          (assoc-in [:application :pate-verdicts] [legacy-verdict pate-verdict])))
        => (contains {:construction-site    "753-416-876-2\nMy address"
                      :kuntalupatunnus      "leegio"
                      :organization-contact "Oudohko osoite"
                      :organization-name    "Onpahan vaan organisaatio"}))
      (fact "Final review (loppukatselmus)"
        (let [result (contains {:loppukatselmus? true
                                :rectification   "Ohje oikaisuvaatimuksen tiimoilta"
                                :title           "Loppukatselmuksen pöytäkirja"
                                :type-group      "katselmukset_ja_tarkastukset"
                                :type-id         "loppukatselmuksen_poytakirja"})]
          (review-pdf/review-properties (assoc-in options [:review :data :katselmuksenLaji] "loppukatselmus"))
          => result))
      (fact "Not an actual final review (osittainen loppukatselmus)"
        (let [result (contains {:title           "Katselmuksen pöytäkirja"
                                :type-group      "katselmukset_ja_tarkastukset"
                                :type-id         "katselmuksen_tai_tarkastuksen_poytakirja"})]
          (review-pdf/review-properties (assoc-in options [:review :data :katselmuksenLaji] "osittainen loppukatselmus"))
          => result))
      (fact "Final review without rectification"
        (let [result (review-pdf/review-properties (-> options
                                                       (assoc-in [:organization :review-pdf :rectification-enabled] false)
                                                       (assoc-in [:review :data :katselmuksenLaji] "loppukatselmus")))]
          (:rectification result) => nil
          result => (contains {:loppukatselmus? true
                               :title           "Loppukatselmuksen pöytäkirja"
                               :type-group      "katselmukset_ja_tarkastukset"
                               :type-id         "loppukatselmuksen_poytakirja"})))
      (fact "Attachment creation"
        (let [command {:created      1574755754702 ; 26.11.2019 10:09
                       :application  application
                       :organization foo-organization
                       :lang         "fi"}]
          (review-pdf/create-review-attachment command review) => "Great success!"
          (provided
            (lupapalvelu.pdf.html-template/html->pdf anything anything) => {:ok true}
            (lupapalvelu.pate.pdf/upload-and-attach-pdf!
              {:command            command
               :pdf                {:ok true}
               :attachment-options {:created         1574755754702
                                    :attachment-type {:type-group "katselmukset_ja_tarkastukset"
                                                      :type-id    "aloituskokouksen_poytakirja"}
                                    :target          {:type "task" :id "review-id"}
                                    :source          {:type "tasks" :id "review-id"}
                                    :locked          true
                                    :read-only       true
                                    :contents        "This is review"}
               :file-options       {:filename "app-id Aloituskokouksen pöytäkirja 26.11.2019 10.09.pdf"}})
            => "Great success!")))
      (fact "Attachment creation. Organization is delay."
        (let [command {:created      1574755754702 ; 26.11.2019 10.09
                       :application  application
                       :organization (delay foo-organization)
                       :lang         "fi"}]
          (review-pdf/create-review-attachment command review) => "Again great success!"
          (provided
            (lupapalvelu.pdf.html-template/html->pdf anything anything) => {:ok true}
            (lupapalvelu.pate.pdf/upload-and-attach-pdf!
              {:command            command
               :pdf                {:ok true}
               :attachment-options {:created         1574755754702
                                    :attachment-type {:type-group "katselmukset_ja_tarkastukset"
                                                      :type-id    "aloituskokouksen_poytakirja"}
                                    :target          {:type "task" :id "review-id"}
                                    :source          {:type "tasks" :id "review-id"}
                                    :locked          true
                                    :read-only       true
                                    :contents        "This is review"}
               :file-options       {:filename "app-id Aloituskokouksen pöytäkirja 26.11.2019 10.09.pdf"}})
            => "Again great success!"))))))
