(ns lupapalvelu.allu-api-itest
  (:require [lupapalvelu.document.tools :refer [doc-name]]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.util :as util]))

(apply-remote-minimal)

(defn err [error]
  (partial expected-failure? error))

(let [{:keys [documents] app-id :id :as app} (create-application pena
                                                                 :propertyId sipoo-property-id
                                                                 :operation :promootio)
      _                                      (fact (command pena :update-doc
                                                            :id app-id
                                                            :doc (:id (first (filter #(= (doc-name %) "promootio-time") documents)))
                                                            :updates [["promootio-time.start-date" "13.3.2019"]
                                                                      ["promootio-time.end-date" "15.3.2019"]]) => ok?)
      promotion-sites                        (map-indexed (fn [i n]
                                                            (let [[n section] (if (vector? n) n [n nil])]
                                                              (util/assoc-when {:name   n
                                                                                :id     (inc i)
                                                                                :source "promotion"}
                                                                               :allu-section section)))
                                                          [["Asema-aukio (1)" "A"] ["Asema-aukio (2)" "B"]
                                                           "Kaivopuisto" "Kolmensep\u00e4naukio"
                                                           "Mauno Koiviston aukio"
                                                           ["Narinkka (6)" "A"] ["Narinkka (7)" "B"]
                                                           ["Narinkka (8)" "C"] ["Narinkka (9)" "D"]
                                                           "Senaatintori" "S\u00e4ili\u00f6 468"])
      dog-sites                              (map-indexed (fn [i n]
                                                            {:name   n
                                                             :id     (+ 100 i)
                                                             :source "dog-training-event"})
                                                          ["Heikinlaakson kentt\u00e4"
                                                           "Kivikon kentt\u00e4"
                                                           "Koneen kentt\u00e4"])]
  (fact "Bad kind"
    (query pena :allu-sites :id app-id :kind "bad")
    => (err :error.allu-bad-kind))

  (facts "Promotion sites from minimal"
    (query pena :allu-sites :id app-id :kind "promotion")
    => {:ok    true
        :sites promotion-sites})

  (facts "Dog training event sites from minimal"
    (query pena :allu-sites :id app-id :kind "dog-training-event")
    => (just {:ok    true
              :sites (just dog-sites :in-any-order)}))

  (facts "All sites"
    (query pena :allu-sites :id app-id)
    => {:ok    true
        :sites (concat promotion-sites dog-sites)})

  (fact "Initially, no drawings"
    (:drawings app) => empty?)

  (fact "Add hand-drawn user drawing"
    (command pena :save-application-drawings :id app-id
             :drawings [{:area     "",
                         :category "123",
                         :desc     "",
                         :geometry "POINT(404379.929 6693811.582)",
                         :height   "",
                         :id       1,
                         :length   "",
                         :name     "Pointer"}]) => ok?)

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
    => (just [(contains {:id     1
                         :name   "Pointer"})
              (contains {:id      string?
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
    => (just [(contains {:id     1
                         :name   "Pointer"})
              (contains {:id      string?
                         :allu-id 5
                         :source  "promotion"
                         :name    "Mauno Koiviston aukio"})]))

  (fact "Select The Container"
    (command pena :add-allu-drawing :id app-id
             :kind :promotion
             :siteId 11) => ok?)
  (let [{:keys [drawings]} (query-application pena app-id)
        manu-id            (-> drawings second :id)
        cont-id            (-> drawings last :id)]
    drawings => (just [(contains {:id     1
                                  :name   "Pointer"})
                       (contains {:id      manu-id
                                  :allu-id 5
                                  :source  "promotion"
                                  :name    "Mauno Koiviston aukio"})
                       (contains {:id      cont-id
                                  :allu-id 11
                                  :source  "promotion"
                                  :name    "S\u00e4ili\u00f6 468"})])
    (fact "Remove Manu's plaza from application"
      (command pena :remove-application-drawing :id app-id
               :drawingId manu-id) => ok?
      (:drawings (query-application pena app-id))
      => (just [(contains {:id     1
                           :name   "Pointer"})
                (contains {:id      cont-id
                           :allu-id 11
                           :source  "promotion"
                           :name    "S\u00e4ili\u00f6 468"})]))
    (fact "Removing non-existing drawing fails silently"
      (command pena :remove-application-drawing :id app-id
               :drawingId 100) => ok?
      (:drawings (query-application pena app-id))
      => (just [(contains {:id     1
                           :name   "Pointer"})
                (contains {:id      cont-id
                           :allu-id 11
                           :source  "promotion"
                           :name    "S\u00e4ili\u00f6 468"})])))

  (facts "Filter Allu drawings"
    (fact "Unknown kind"
      (command pena :filter-allu-drawings
               :id app-id
               :kind "badkind") => fail?)
    (fact "Current drawing matches the promotion kind"
      (command pena :filter-allu-drawings
               :id app-id
               :kind "promotion") => {:ok true :text "no changes"}
      (:drawings (query-application pena app-id))
      => (just [(contains {:id     1
                           :name   "Pointer"})
                (contains {:source  "promotion"
                           :allu-id 11
                           :name    "Säiliö 468"})]))
    (fact "Filter for bridge-banner"
      (command pena :filter-allu-drawings
               :id app-id
               :kind "bridge-banner") => ok?
      (:drawings (query-application pena app-id))
      => (just [(contains {:id     1
                           :name   "Pointer"})])))
  (fact "Authority vs. draft"
    (query sonja :allu-sites :id app-id :kind "promotion")
    => fail?)

  (facts "Application kinds"
    (fact "short-term-rental"
      (:kinds (query pena :application-kinds :id app-id :type "short-term-rental"))
      => ["bridge-banner" "benji" "promotion-or-sales" "urban-farming"
          "keskuskatu-sales" "summer-theater" "dog-training-field"
          "dog-training-event" "small-art-and-culture" "season-sale"
          "circus" "art" "storage-area" "other"])

    (fact "promotion"
      (:kinds (query pena :application-kinds :id app-id :type "promotion"))
      => nil)

    (fact "Unsupported application type"
      (query pena :application-kinds :id app-id :type "unsupported")
      => (err :error.allu-bad-application-type)))

  (fact "Submit application"
    (command pena :submit-application :id app-id) => ok?)
  (fact "Verdicts cannot be given for Allu applications"
    (command sonja :new-legacy-verdict-draft :id app-id)
    => (err :error.unsupported-permit-type)))

(facts "Non-Allu application"
  (let [app-id (create-app-id pena
                              :propertyId sipoo-property-id
                              :operation :ya-kayttolupa-terassit)]
    (query pena :allu-sites :id app-id :kind "promotion") => fail?))
