(ns lupapalvelu.pate-test-util)

(defn make-verdict [& kvs]
  (let [{:keys [code section modified published replaces replaced-by] :as verdict} (apply hash-map kvs)]
    (merge (select-keys verdict [:id :parent-verdict])
           {:category       "r"
            :schema-version 1
            :data           {:verdict-code    code
                             :handler         "Foo Bar"
                             :verdict-section section
                             :verdict-date    876543}
            :modified       (or published modified 1)
            :published      (when published {:published published})
            :replacement    (cond
                              replaces {:replaces replaces}
                              replaced-by {:replaced-by replaced-by})
            :template       {:inclusions ["verdict-code"
                                          "handler"
                                          "verdict-date"
                                          "verdict-section"
                                          "verdict-text"]
                             :giver      "viranhaltija"}
            :references     {:boardname "Broad board abroad"}})))

(defn address  [street zip city country]
  {:katu                 street
   :postinumero          zip
   :postitoimipaikannimi city
   :maa                  country})

(defn party-person [firstname lastname street zip city country]
  {:schema-info {:subtype "hakija"}
   :data        {:_selected "henkilo"
                 :henkilo   {:henkilotiedot {:etunimi  firstname
                                             :sukunimi lastname}
                             :osoite        (address street zip
                                                     city country)}}})

(defn party-company
  ([company-name street zip city country contact-first contact-last]
   {:schema-info {:subtype "hakija"}
    :data        {:_selected "yritys"
                  :yritys    {:yritysnimi    company-name
                              :osoite        (address street zip
                                                   city country)
                              :yhteyshenkilo {:henkilotiedot {:etunimi  contact-first
                                                              :sukunimi contact-last}}}}})
  ([company-name street zip city country]
   (party-company company-name street zip city country nil nil)))
