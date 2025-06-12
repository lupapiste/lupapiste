(ns lupapalvelu.ryhti.mappings-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.ryhti.api.mappings :as sut :refer [rakennusluokitus-code-value]]))

;; TODO: shouldn't this be a unit test, not an itest?

(deftest rakennusluokitus-code-value-test
  (testing "Keyword contains codeValue and normalized label; common cases"
    (is (= (rakennusluokitus-code-value {:prefLabel {:fi "Asuinrakennus"}
                                         :codeValue "01"})
           :asuinrakennus-01))
    (is (= (rakennusluokitus-code-value {:prefLabel {:fi "Teollisuusrakennus"}
                                         :codeValue "02"})
           :teollisuusrakennus-02)))
  (testing "Label and codeValue combine for uniqueness, even with same label"
    (is (not= (rakennusluokitus-code-value {:prefLabel {:fi "Muut liikenteen rakennukset"}
                                            :codeValue "0590"})
              (rakennusluokitus-code-value {:prefLabel {:fi "Muut liikenteen rakennukset"}
                                            :codeValue "059"}))))
  (testing "Special characters and Finnish diacritics normalized"
    (is (= (rakennusluokitus-code-value {:prefLabel {:fi "Käyttörakennus"} :codeValue "03"})
           :kayttorakennus-03))
    (is (= (rakennusluokitus-code-value {:prefLabel {:fi "Talorakennus käärmeö"} :codeValue "04"})
           :talorakennus-kaarmeo-04)))
  (testing "Removes special chars, trims, collapses hyphens, etc."
    (is (= (rakennusluokitus-code-value {:prefLabel {:fi "Talo 123!!"} :codeValue "123"})
           :talo-123-123))
    (is (= (rakennusluokitus-code-value {:prefLabel {:fi "  Talo -- 123  "} :codeValue "321"})
           :talo-123-321)))
  (testing "Removes leading/trailing hyphens"
    (is (= (rakennusluokitus-code-value {:prefLabel {:fi "-Talo--"} :codeValue "456"})
           :talo-456))))

(deftest document-schema-select-mappings
  (testing "hallintaperuste"
    (is (= (set (keys sut/rakennuspaikan-hallintaperuste))
           (-> (set (map :name (:body (schemas/get-subschema {:body schemas/rakennuspaikka} "hallintaperuste"))))
               ;; hidden in using schema-exclude
               (disj "ei tiedossa")))))

  (testing "kaavatilanne"
    (is (= (disj (set (keys sut/kaavatilanne))
                 ;; FIXME: Missing from schemas
                 "rakennuskaava"
                 "rantakaava"
                 "yleiskaava")
           (set (map :name (:body (schemas/get-subschema {:body schemas/rakennuspaikka} "kaavatilanne")))))))

  (testing "omistajalajit"
    (is (= (disj (set (keys sut/omistajalaji))
                 "hyvinvointialue")
           (set (map :name (:body (schemas/get-subschema {:body schemas/omistajalajit} "omistajalaji")))))))

  (testing "vaativuusluokat"
    (is (= (set (keys sut/vaativuusluokat))
           (disj (set (map (comp keyword :name) (:body schemas/patevyysluokka)))
                 (keyword "ei tiedossa")))))

  (testing "suunnittelijanrooli"
    ;; TODO: Some differences currently
    )

  (testing "etvra-tyyppi"
    (is (= (set (map keyword (keys sut/etvra-tyyppi)))
           (set (map (comp keyword :name) schemas/etvra-tyyppi))))))
