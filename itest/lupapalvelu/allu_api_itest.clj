(ns lupapalvelu.allu-api-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.document.tools :refer [doc-name]]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]))

(use-fixtures :once (local-fixture-factory :db-name "test_allu-api-itest"))

(deftest allu-applicaton-test
  (let [{app-id :id}    (create-local-app pena
                                          :propertyId sipoo-property-id
                                          :operation "promootio")
        {:keys [documents]
         :as   app}     (query-application pena app-id)
        promotion-sites (map-indexed (fn [i n]
                                       (let [[n section] (if (vector? n) n [n nil])]
                                         (util/assoc-when {:name   n
                                                           :id     (inc i)
                                                           :source "promotion"}
                                                          :allu-section section)))
                                     [["Asema-aukio (1)" "A"] ["Asema-aukio (2)" "B"]
                                      "Kaivopuisto" "Kolmensepänaukio"
                                      "Mauno Koiviston aukio"
                                      ["Narinkka (6)" "A"] ["Narinkka (7)" "B"]
                                      ["Narinkka (8)" "C"] ["Narinkka (9)" "D"]
                                      "Senaatintori" "Säiliö 468"])
        dog-sites       (map-indexed (fn [i n]
                                       {:name   n
                                        :id     (+ 100 i)
                                        :source "dog-training-event"})
                                     ["Heikinlaakson kenttä"
                                      "Kivikon kenttä"
                                      "Koneen kenttä"])
        find-doc-id     (fn [document-name]
                          (->> documents
                               (util/find-first #(= (doc-name %) document-name))
                               :id))
        payer-doc-id    (find-doc-id "yleiset-alueet-maksaja")]
    (testing "Fill documents"
      (is (:ok (command pena :update-doc
                        :id app-id
                        :doc (find-doc-id "promootio")
                        :updates [["promootio.promootio-name" "Party hardy"]])))
      (is (:ok (command pena :update-doc
                        :id app-id
                        :doc (find-doc-id "promootio-time")
                        :updates [["promootio-time.start-date" "13.3.2019"]
                                  ["promootio-time.end-date" "15.3.2019"]])))
      (is (:ok (command pena :update-doc
                        :id app-id
                        :doc (find-doc-id "hakija-ya")
                        :updates [["yritys.yritysnimi" "Hypecom"]
                                  ["yritys.yhteyshenkilo.henkilotiedot.etunimi" "Hyper"]
                                  ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" "Hippo"]])))
      (testing "Payer with some bad information (email, postal code)"
        (is (:ok (command pena :update-doc
                          :id app-id
                          :doc payer-doc-id
                          :updates [["yritys.yritysnimi" "Hypecom"]
                                    ["yritys.yhteyshenkilo.henkilotiedot.etunimi" "Hippor"]
                                    ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" "Potamus"]
                                    ["yritys.yhteyshenkilo.yhteystiedot.email" "thisisnotmybeautifulemail"]
                                    ["yritys.osoite.katu" "Super Street"]
                                    ["yritys.osoite.postinumero" "98765"]])))))

    (testing "Bad kind"
      (is (expected-failure? :error.allu-bad-kind
                             (query pena :allu-sites :id app-id :kind "bad"))))

    (testing "Promotion sites from minimal"
      (is (= {:ok true :sites promotion-sites}
             (query pena :allu-sites :id app-id :kind "promotion"))))

    (testing "Dog training event sites from minimal"
      (is (midje? (just {:ok    true
                         :sites (just dog-sites :in-any-order)})
                  (query pena :allu-sites :id app-id :kind "dog-training-event"))))

    (testing "All sites"
      (is (= {:ok    true
              :sites (concat promotion-sites dog-sites)}
             (query pena :allu-sites :id app-id))))

    (testing "Initially, no drawings"
      (is (empty? (:drawings app))))

    (testing "Add hand-drawn user drawing"
      (is (:ok (command pena :save-application-drawings :id app-id
                        :drawings [{:area     "",
                                    :category "123",
                                    :desc     "",
                                    :geometry "POINT(404379.929 6693811.582)",
                                    :height   "",
                                    :id       1,
                                    :length   "",
                                    :name     "Pointer"}]))))

    (testing "Select site with bad kind"
      (is (expected-failure? :error.allu-bad-kind
                             (command pena :add-allu-drawing :id app-id
                                      :kind :bad
                                      :siteId 5))))

    (testing "Select non-existing site"
      (is (expected-failure? :error.not-found
                             (command pena :add-allu-drawing :id app-id
                                      :kind :promotion
                                      :siteId 99))))

    (testing "Select Manu's plaza"
      (is (:ok (command pena :add-allu-drawing :id app-id
                        :kind :promotion
                        :siteId 5)))
      (is (midje? (just [(contains {:id   1
                                    :name "Pointer"})
                         (contains {:id      string?
                                    :allu-id 5
                                    :source  "promotion"
                                    :name    "Mauno Koiviston aukio"})])
                  (:drawings (query-application pena app-id)))))

    (testing "Manu's plaza excluded from the site list"
      (let [{:keys [sites]} (query pena :allu-sites :id app-id :kind "promotion")]
        (is (= 10 (count sites)))
        (is (nil? (util/find-by-id 5 sites)))))

    (testing "Reselection fails silently"
      (is (:ok (command pena :add-allu-drawing :id app-id
                        :kind :promotion
                        :siteId 5)))
      (is (midje? (just [(contains {:id   1
                                    :name "Pointer"})
                         (contains {:id      string?
                                    :allu-id 5
                                    :source  "promotion"
                                    :name    "Mauno Koiviston aukio"})])
                  (:drawings (query-application pena app-id)))))

    (testing "Select The Container"
      (is (:ok (command pena :add-allu-drawing :id app-id
                        :kind :promotion
                        :siteId 11))))
    (let [{:keys [drawings]} (query-application pena app-id)
          manu-id            (-> drawings second :id)
          cont-id            (-> drawings last :id)]
      (testing "drawings"
        (is (midje? (just [(contains {:id   1
                                      :name "Pointer"})
                           (contains {:id      manu-id
                                      :allu-id 5
                                      :source  "promotion"
                                      :name    "Mauno Koiviston aukio"})
                           (contains {:id      cont-id
                                      :allu-id 11
                                      :source  "promotion"
                                      :name    "Säiliö 468"})])
                    drawings)))
      (testing "Remove Manu's plaza from application"
        (is (:ok (command pena :remove-application-drawing :id app-id
                          :drawingId manu-id)))
        (is (midje? (just [(contains {:id   1
                                      :name "Pointer"})
                           (contains {:id      cont-id
                                      :allu-id 11
                                      :source  "promotion"
                                      :name    "Säiliö 468"})])
                    (:drawings (query-application pena app-id)))))
      (testing "Removing non-existing drawing fails silently"
        (is (:ok (command pena :remove-application-drawing :id app-id
                          :drawingId 100)))
        (is (midje? (just [(contains {:id   1
                                      :name "Pointer"})
                           (contains {:id      cont-id
                                      :allu-id 11
                                      :source  "promotion"
                                      :name    "Säiliö 468"})])
                    (:drawings (query-application pena app-id))))))

    (testing "Filter Allu drawings."
      (testing "Unknown kind"
        (is (fail? (command pena :filter-allu-drawings
                            :id app-id
                            :kind "badkind"))))
      (testing "Current drawing matches the promotion kind"
        (is (= {:ok true :text "no changes"}
               (command pena :filter-allu-drawings
                        :id app-id
                        :kind "promotion")))
        (is (midje? (just [(contains {:id   1
                                      :name "Pointer"})
                           (contains {:source  "promotion"
                                      :allu-id 11
                                      :name    "Säiliö 468"})])
                    (:drawings (query-application pena app-id)))))
      (testing "Filter for bridge-banner"
        (is (:ok (command pena :filter-allu-drawings
                          :id app-id
                          :kind "bridge-banner")))
        (is (midje? (just [(contains {:id   1
                                      :name "Pointer"})])
                    (:drawings (query-application pena app-id))))))
    (testing "Authority vs. draft"
      (is (fail? (query sonja :allu-sites :id app-id :kind "promotion"))))

    (testing "Application kinds."
      (testing "short-term-rental"
        (is (= ["bridge-banner" "benji" "promotion-or-sales" "urban-farming"
                "keskuskatu-sales" "summer-theater" "dog-training-field"
                "dog-training-event" "small-art-and-culture" "season-sale"
                "circus" "art" "storage-area" "other"]
               (:kinds (query pena :application-kinds :id app-id :type "short-term-rental")))))

      (testing "promotion"
        (is (nil? (:kinds (query pena :application-kinds :id app-id :type "promotion")))))

      (testing "Unsupported application type"
        (is (expected-failure? :error.allu-bad-application-type
                               (query pena :application-kinds :id app-id :type "unsupported")))))

    (env/with-feature-value :allu true
      (testing "Submit application."
        (testing "Fails on postal code"
          (let [{:keys [details]
                 :as   response} (command pena :submit-application :id app-id)]
            (is (expected-failure? :error.integration.create-message
                                   response))
            (is (ss/includes? details "Bad Finnish postal code: 98765"))))
        (testing "Fix postal code by changing the country"
          (is (:ok (command pena :update-doc
                            :id app-id
                            :doc payer-doc-id
                            :updates [["yritys.osoite.maa" "NOR"]]))))
        (testing "Fails on email"
          (let [{:keys [details]
                 :as   response} (command pena :submit-application :id app-id)]
            (is (expected-failure? :error.integration.create-message
                                   response))
            (is (every? (partial ss/includes? details)
                        [":invoicingCustomer" ":email" "Email" "a-java.lang.String"]))))
        (testing "Fix email"
          (is (:ok (command pena :update-doc
                            :id app-id
                            :doc payer-doc-id
                            :updates [["yritys.yhteyshenkilo.yhteystiedot.email" "hippo.potamus@example.net"]]))))
        (testing "Application state is draft and it can be successfully submitted"
          (is (= "draft" (:state (query-application pena app-id))))
          (is (:ok (command pena :submit-application :id app-id)))
          (is (= "submitted" (:state (query-application pena app-id)))))))

    (testing "Verdicts cannot be given for Allu applications"
      (is (expected-failure? :error.unsupported-permit-type
                             (command sonja :new-legacy-verdict-draft :id app-id))))))
