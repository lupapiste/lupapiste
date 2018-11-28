(ns lupapalvelu.allu-api-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.util :as util]))

(apply-remote-minimal)

(defn err [error]
  (partial expected-failure? error))

(let [{app-id :id
       :as    app} (create-application pena
                                       :propertyId sipoo-property-id
                                       :operation :promootio)]
  (fact "Bad kind"
    (query pena :allu-sites :id app-id :kind "bad")
    => (err :error.allu-bad-kind))

  (facts "Promotion sites from minimal"
    (query pena :allu-sites :id app-id :kind "promotion")
    => {:ok    true
        :sites (map-indexed (fn [i n]
                              {:name   n
                               :id     (inc i)
                               :source "promotion"})
                            ["Asema-aukio 1" "Asema-aukio 2"
                             "Kaivopuisto" "Kolmensepänaukio"
                             "Mauno Koiviston aukio"
                             "Narinkka 1" "Narinkka 2"
                             "Narinkka 3" "Narinkka 4"
                             "Senaatintori" "Säiliö 468"])})
  (fact "Initially, no drawings"
    (:drawings app) => empty?)

  (fact "Select site with bad kind"
    (command pena :add-allu-drawing :id app-id
             :kind :bad
             :siteId 5)
    => (err :error.allu-bad-kind))

  (fact "Select non-existing site"
    (command pena :add-allu-drawing :id app-id
             :kind :promotion
             :siteId 99)
    => (err :error.not-found))

  (fact "Select Manu's plaza"
    (command pena :add-allu-drawing :id app-id
             :kind :promotion
             :siteId 5) => ok?
    (:drawings (query-application pena app-id))
    => (just [(contains {:id      string?
                         :allu-id 5
                         :source  "promotion"
                         :name    "Mauno Koiviston aukio"})]))

  (fact "Manu's plaza excluded from the site list"
    (let [{:keys [sites]} (query pena :allu-sites :id app-id :kind "promotion")]
      (count sites) => 10
      (util/find-by-id 5 sites) => nil))

  (fact "Reselection fails silently"
    (command pena :add-allu-drawing :id app-id
             :kind :promotion
             :siteId 5) => ok?
    (:drawings (query-application pena app-id))
    => (just [(contains {:id      string?
                         :allu-id 5
                         :source  "promotion"
                         :name    "Mauno Koiviston aukio"})]))

  (fact "Select The Container"
    (command pena :add-allu-drawing :id app-id
             :kind :promotion
             :siteId 11) => ok?
    (let [{:keys [drawings]} (query-application pena app-id)
          manu-id            (-> drawings first :id)
          cont-id            (-> drawings last :id)]
      drawings => (just [(contains {:id      manu-id
                                    :allu-id 5
                                    :source  "promotion"
                                    :name    "Mauno Koiviston aukio"})
                         (contains {:id      cont-id
                                    :allu-id 11
                                    :source  "promotion"
                                    :name    "Säiliö 468"})])
      (fact "Remove Manu's plaza from application"
        (command pena :remove-application-drawing :id app-id
                 :drawingId manu-id) => ok?
        (:drawings (query-application pena app-id))
        => (just [(contains {:id      cont-id
                             :allu-id 11
                             :source  "promotion"
                             :name    "Säiliö 468"})]))
      (fact "Removing non-existing drawing fails silently"
        (command pena :remove-application-drawing :id app-id
                 :drawingId 100) => ok?
        (:drawings (query-application pena app-id))
        => (just [(contains {:id      cont-id
                             :allu-id 11
                             :source  "promotion"
                             :name    "Säiliö 468"})]))))
  (fact "Authority vs. draft"
    (query sonja :allu-sites :id app-id :kind "promotion")
    => fail?))

(facts "Non-Allu application"
  (let [app-id (create-app-id pena
                              :propertyId sipoo-property-id
                              :operation :ya-kayttolupa-terassit)]
    (query pena :allu-sites :id app-id :kind "promotion") => fail?))
