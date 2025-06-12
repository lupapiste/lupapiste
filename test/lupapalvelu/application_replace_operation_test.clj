(ns lupapalvelu.application-replace-operation-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.application-replace-operation :as aro]
            [lupapalvelu.test-util :refer :all]
            [midje.checkers :refer :all]))

(def coll-contains-type? #'aro/coll-contains-type?)
(def copy-old-document-data #'aro/copy-old-document-data)
(def get-document-schema-names-for-operation #'aro/get-document-schema-names-for-operation)
(def get-existing-operation-attachment-types #'aro/get-existing-operation-attachment-types)
(def get-operation-by-key #'aro/get-operation-by-key)
(def new-attachment-duplicates #'aro/new-attachment-duplicates)
(def non-empty-attachment? #'aro/non-empty-attachment?)
(def not-needed-templates #'aro/not-needed-templates)
(def replace-operation-in-attachment? #'aro/replace-operation-in-attachment?)
(def required-document-schema-names-by-op #'aro/required-document-schema-names-by-op)
(def resolve-rakennuspaikka-documents #'aro/resolve-rakennuspaikka-documents)
(def single-operation-attachment? #'aro/single-operation-attachment?)
(def fill-valid-fields-updates #'aro/fill-valid-fields-updates)
(def truthy? #'aro/truthy?)

(deftest attachments-when-replacing-primary-operation-test
  (let [op-1          {:name "kerrostalo-rivitalo"
                       :id   "1"}
        op-2          {:name "teollisuusrakennus"
                       :id   "2"}
        op-3          {:name "auto-katos"
                       :id   "3"}
        attachment-1  {:type     {:type-group "paapiirustus" :type-id "julkisivupiirros"}
                       :versions [{:version {:major 0 :minor 1}}]
                       :id       "1"
                       :op       [op-1]}
        attachment-2  {:type     {:type-group "paapiirustus" :type-id "asemapiirros"}
                       :versions [{:version {:major 0 :minor 1}}]
                       :id       "2"
                       :op       [op-1 op-2]}
        attachment-3  {:type     {:type-group "paapiirustus" :type-id "pohjapiirustus"}
                       :versions [{:version {:major 0 :minor 1}}]
                       :id       "3"
                       :op       [op-3]}
        attachment-4  {:type     {:type-group "paapiirustus" :type-id "pohjapiirustus"}
                       :versions []
                       :id       "4"
                       :op       [op-1]}
        attachment-5  {:type     {:type-group "paapiirustus" :type-id "leikkauspiirros"}
                       :versions []
                       :id       "5"
                       :op       [op-1]}
        attachment-6  {:type     {:type-group "paapiirustus" :type-id "julkisivupiirros"}
                       :versions []
                       :id       "6"
                       :op       [op-1]}
        attachment-7  {:type     {:type-group "ennakkoluvat_ja_lausunnot" :type-id "naapurin_kuuleminen"}
                       :versions []
                       :id       "7"
                       :op       []}
        application-1 {:attachments         [attachment-1 attachment-3 attachment-7]
                       :primaryOperation    op-1
                       :secondaryOperations [op-3]}
        application-2 {:attachments         [attachment-5]
                       :primaryOperation    op-1
                       :secondaryOperations [op-3]}]

    (is (= {:a 1 :c "d"}
           (get-operation-by-key {:secondaryOperations [{:a 1 :c "d"} {:b 2 :e "f"}]} :a 1))
        "getting operations in application by key and value pair")

    (testing "getting attachments that can only belong to one operation"
      (is (true? (single-operation-attachment? op-1 attachment-1)))
      (is (false? (single-operation-attachment? op-2 attachment-2)))
      (is (false? (single-operation-attachment? op-2 attachment-1))))

    (testing "get attachment types for existing attachments"
      (is (= #{{:type-group "paapiirustus" :type-id "julkisivupiirros"}
               {:type-group "paapiirustus" :type-id "pohjapiirustus"}}
             (get-existing-operation-attachment-types [attachment-1 attachment-2
                                                       attachment-3 attachment-4] op-1)))


      (is (empty? (get-existing-operation-attachment-types [attachment-1 attachment-2
                                                            attachment-3 attachment-4] op-2)))


      (is (= #{{:type-group "paapiirustus" :type-id "pohjapiirustus"}}
             (get-existing-operation-attachment-types [attachment-1 attachment-2
                                                       attachment-3 attachment-4] op-3))))

    (testing "nonempty attachments are nonempty"
      (is (true? (non-empty-attachment? attachment-1)))
      (is (false? (non-empty-attachment? attachment-4))))

    (testing "a collection of attachments has the attachment in question of attachments to be deleted"
      (is (true? (coll-contains-type? [attachment-1 attachment-2 attachment-3] attachment-3)))
      (is (false? (coll-contains-type? [attachment-1 attachment-2 attachment-3] attachment-5)))
      (is (false? (coll-contains-type? [attachment-1 attachment-2 attachment-3] nil))))

    (testing "empty attachments that are duplicates appear on a list"
      (is (= ["6"] (new-attachment-duplicates [attachment-1 attachment-4
                                               attachment-6] op-1)))
      (is (empty? (new-attachment-duplicates [attachment-4 attachment-6] op-1))))

    (with-redefs [lupapalvelu.application-replace-operation/required-attachments-for-operations
                  (constantly #{{:type-group "paapiirustus"
                                 :type-id    "leikkauspiirros"}})]
      (testing "not needed empty attachments appear on a list of attachments to be deleted"
        (is (= ["7"] (not-needed-templates "753-R" application-1 {:id "3"})))
        (is (empty? (not-needed-templates "753-R" application-2 {:id "3"}))))

      (testing "operation in attachment is replaced when necessary"
        (let [new-op-att-types #{{:type-group "paapiirustus" :type-id "julkisivupiirros"}
                                 {:type-group "paapiirustus" :type-id "pohjapiirustus"}}]
          (is (true? (replace-operation-in-attachment? new-op-att-types op-1 attachment-1)))
          (is (false? (replace-operation-in-attachment? new-op-att-types op-1 attachment-2)))
          (is (false? (replace-operation-in-attachment? new-op-att-types op-1 attachment-3)))
          (is (true? (replace-operation-in-attachment? new-op-att-types op-1 attachment-4))))))))

(deftest documents-when-replacing-operation-test
  (let [org {:selected-operations                        [:tontin-ajoliittyman-muutos :auto-katos]
             :extended-construction-waste-report-enabled false}
        app {:primaryOperation    {:name :auto-katos}
             :secondaryOperations [{:name :tontin-ajoliittyman-muutos}]}]

    (testing "document schema names for operation"
      (is (= ["hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja"
              "rakennuspaikka-ilman-ilmoitusta" "suunnittelija"]
             (get-document-schema-names-for-operation org {:name "tontin-ajoliittyman-muutos"})))

      (is (= ["hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja" "rakennuspaikka"
              "paasuunnittelija" "suunnittelija" "laajennettuRakennusjateselvitys-v2"]
             (get-document-schema-names-for-operation org {:name "auto-katos"}))))

    (is (= {:auto-katos                 #{"hankkeen-kuvaus" "paatoksen-toimitus-rakval"
                                          "maksaja" "rakennuspaikka" "paasuunnittelija"
                                          "suunnittelija" "laajennettuRakennusjateselvitys-v2"}
            :tontin-ajoliittyman-muutos #{"hankkeen-kuvaus" "paatoksen-toimitus-rakval"
                                          "maksaja" "rakennuspaikka-ilman-ilmoitusta"
                                          "suunnittelija"}}
           (required-document-schema-names-by-op org app))
        "getting document schema names by operation")

    (testing "copying existing document data to new doc"
      (let [schema {:body [{:name "a"}
                           {:name "b"}
                           {:name "c"}]}]
        (is (= {:data {:a 1 :b 2 :c 3}}
               (copy-old-document-data schema
                                       {:data {:a 1 :b 2 :c 3 :d 4}}
                                       {:data {:a nil :b nil :c nil}})))

        (is (= {:data {:a 1 :b 2 :c 3 :d nil}}
               (copy-old-document-data schema
                                       {:data {:a 1 :b 2 :c 3}}
                                       {:data {:a nil :b nil :c nil :d nil}})))

        (is (= {:data {:a 1 :b 2 :c 3}}
               (copy-old-document-data schema
                                       {:data {:a 1 :b 2 :c 3 :d 4} :foo "bar"}
                                       {:data {:a nil :b nil :c nil}})))

        (is (= {:data {:a 1 :b 2 :c 3} :foo "bar"}
               (copy-old-document-data schema
                                       {:data {:a 1 :b 2 :c 3 :d 4}}
                                       {:data {:a nil :b nil :c nil} :foo "bar"})))))))

(deftest resolve-rakennuspaikka-documents-test
  (let [r-doc        {:schema-info {:type    "location"
                                    :version 1
                                    :name    "rakennuspaikka"}
                      :data        {:kaavatilanne         {:modified 12345
                                                           :value    "asemakaava"}
                                    :hankkeestaIlmoitettu {:hankkeestaIlmoitettuPvm {:modified 54321
                                                                                     :value    "14.9.2025"}}}}
        rii-doc      {:schema-info {:type    "location"
                                    :version 1
                                    :name    "rakennuspaikka-ilman-ilmoitusta"}
                      :data        {:kiinteisto {:tilanNimi {:modified 12345
                                                             :value    "Hideout"}}}}
        kuntagml-doc {:schema-info {:type    "location"
                                    :version 1
                                    :name    "rakennuspaikka-kuntagml"}
                      :data        {:osoite {:kunta {:modified 12345
                                                     :value    "Humppila"}}}}]
    (testing "No new documents."
      (is (nil? (resolve-rakennuspaikka-documents {} 12345 #{})))
      (is (= {:old r-doc}
             (resolve-rakennuspaikka-documents {:documents [r-doc]} 12345 #{})))
      (is (= {:old r-doc}
             (resolve-rakennuspaikka-documents {:documents [r-doc]} 12345 #{"rakennuspaikka"})))
      (is (= {:old rii-doc}
             (resolve-rakennuspaikka-documents {:documents [rii-doc]} 12345 #{})))
      (is (= {:old rii-doc}
             (resolve-rakennuspaikka-documents {:documents [rii-doc]} 12345
                                               #{"rakennuspaikka-ilman-ilmoitusta"})))
      (is (= {:old kuntagml-doc}
             (resolve-rakennuspaikka-documents {:facta-imported true
                                                :documents      [kuntagml-doc]} 12345 #{})))
      (is (= {:old kuntagml-doc}
             (resolve-rakennuspaikka-documents {:facta-imported true
                                                :documents      [kuntagml-doc]} 12345
                                               #{"rakennuspaikka"}))))

    (testing "New documents, no old ones"
      (is (midje? (just {:new (just {:id          truthy
                                     :created     12345
                                     :schema-info {:name    "rakennuspaikka"
                                                   :version 1
                                                   :type    :location}
                                     :data        {:kiinteisto
                                                   {:tilanNimi        {:value ""}
                                                    :rekisterointipvm {:value ""}
                                                    :maapintaala      {:value ""}
                                                    :vesipintaala     {:value ""}}}})})
                  (resolve-rakennuspaikka-documents {:schema-version 1
                                                     :documents      []} 12345 #{"rakennuspaikka"})))
      (is (midje? (just {:new (just {:id          truthy
                                     :created     12345
                                     :schema-info {:name    "rakennuspaikka-ilman-ilmoitusta"
                                                   :version 1
                                                   :type    :location}
                                     :data        {:kiinteisto
                                                   {:maapintaala      {:value ""}
                                                    :rekisterointipvm {:value ""}
                                                    :tilanNimi        {:value ""}
                                                    :vesipintaala     {:value ""}}}})})
                  (resolve-rakennuspaikka-documents {:schema-version 1
                                                     :documents      []} 12345 #{"rakennuspaikka-ilman-ilmoitusta"}))))
    (testing "New documents based on old ones"
      (is (midje? (just {:old rii-doc
                         :new (just {:id          truthy
                                     :created     98765
                                     :schema-info {:name    "rakennuspaikka"
                                                   :version 1
                                                   :type    :location}
                                     :data        {:kiinteisto {:tilanNimi {:modified 12345
                                                                            :value    "Hideout"}}}})})
                  (resolve-rakennuspaikka-documents {:schema-version 1
                                                     :documents      [rii-doc]} 98765
                                                    #{"rakennuspaikka"
                                                      "rakennuspaikka-ilman-ilmoitusta"}))
          "Rakennuspaikka is prioritized.")
      (is (midje? (just {:old r-doc
                         :new (just {:id          truthy
                                     :created     98765
                                     :schema-info {:name    "rakennuspaikka-ilman-ilmoitusta"
                                                   :version 1
                                                   :type    :location}
                                     :data        {:kaavatilanne {:modified 12345
                                                                  :value    "asemakaava"}
                                                   :kiinteisto   {:maapintaala      {:value ""}
                                                                  :rekisterointipvm {:value ""}
                                                                  :tilanNimi        {:value ""}
                                                                  :vesipintaala     {:value ""}}}})})
                  (resolve-rakennuspaikka-documents {:schema-version 1
                                                     :documents      [r-doc]}
                                                    98765
                                                    #{"rakennuspaikka-ilman-ilmoitusta"}))))))

(deftest truthy?-test
  (testing "True"
    (doseq [x [1 "hello" true [2] {:one 1} #{:a} [[]] #{nil}]]
      (is (true? (truthy? x)))))
  (testing "False"
    (doseq [x [nil false "" " " [] {} #{}]]
      (is (false? (truthy? x))))))

(deftest fill-valid-fields-updates-test
  (let [command    {:organization {}
                    :application  {}}
        aita-doc   {:schema-info {:name "kaupunkikuvatoimenpide-ei-tunnusta"}
                    :id          "aita-id"}
        kuvaus-doc {:schema-info {:name "hankkeen-kuvaus"}
                    :id          "kuvaus-id"}
        house-doc  {:schema-info {:name "uusiRakennus"}
                    :id          "house-id"}]
    (testing "No updates"
      (is (nil? (fill-valid-fields-updates command {} [])))
      (is (nil? (fill-valid-fields-updates command {:data {:foo {:value "bar"}}}
                                           [{:schema-info {:name "kaupunkikuvatoimenpide-ei-tunnusta"}}])))
      (is (nil? (fill-valid-fields-updates command
                                           {:data {:kayttotarkoitus            {:value    "not supported item"
                                                                                :modified 12345}
                                                   :unknown                    {:value "whatever"}
                                                   :tilapainenRakennelmaKytkin {:value "bad"}
                                                   :kuvaus                     {:value "  "}}}
                                           [aita-doc])))
      (is (nil? (fill-valid-fields-updates command
                                           {:data {:kayttotarkoitus            {}
                                                   :tilapainenRakennelmaKytkin {:modified 12345}
                                                   :kuvaus                     {:foo "bar"}}}
                                           [aita-doc]))))
    (testing "Updates"
      (is (= {"aita-id" {:kayttotarkoitus.value            "Aurinkopaneeli"
                         :kayttotarkoitus.modified         12345
                         :tilapainenRakennelmaKytkin.value true
                         :tilapainenRakennelmaKytkin.foo   "bar"
                         :kuvaus.value                     "Hello world"}}
             (fill-valid-fields-updates command
                                        {:data {:kayttotarkoitus            {:value    "Aurinkopaneeli"
                                                                             :modified 12345}
                                                :unknown                    {:value "whatever"}
                                                :tilapainenRakennelmaKytkin {:value true
                                                                             :foo   "bar"}
                                                :kuvaus                     {:value "Hello world"}}}
                                        [aita-doc])))
      (is (= {"aita-id"   {:kayttotarkoitus.value            "Aurinkopaneeli"
                           :kayttotarkoitus.modified         12345
                           :tilapainenRakennelmaKytkin.value true
                           :tilapainenRakennelmaKytkin.foo   "bar"
                           :kuvaus.value                     "Hello world"}
              "kuvaus-id" {:kuvaus.value "Hello world"}}
             (fill-valid-fields-updates command
                                        {:data {:kayttotarkoitus            {:value    "Aurinkopaneeli"
                                                                             :modified 12345}
                                                :unknown                    {:value "whatever"}
                                                :tilapainenRakennelmaKytkin {:value true
                                                                             :foo   "bar"}
                                                :kuvaus                     {:value "Hello world"}}}
                                        [aita-doc kuvaus-doc])))
      (is (= {"kuvaus-id" {:kuvaus.value "Hello world"}
              "house-id"  {:mitat.rakennusoikeusKuutiometreina.modified 9200
                           :mitat.rakennusoikeusKuutiometreina.value    "999"
                           :huoneistot.2.huoneistonumero.value          "002"
                           :huoneistot.4.huoneistonumero.value          "004"
                           :huoneistot.4.WCKytkin.value                 true
                           :huoneistot.4.WCKytkin.modified              9100}}
             (fill-valid-fields-updates
               command
               {:data {:mitat                      {;; Kerrosala does not pass validation
                                                    :kerrosala                    {:modified 12345
                                                                                   :value    "100"}
                                                    :rakennusoikeusKuutiometreina {:value    "999"
                                                                                   :modified 9200}}
                       :huoneistot                 {:2 {:WCKytkin        {:value    false
                                                                          :modified 9000}
                                                        :huoneistonumero {:value "002"}}
                                                    :4 {:WCKytkin        {:value    true
                                                                          :modified 9100}
                                                        :huoneistonumero {:value "004"}}}
                       :kayttotarkoitus            {:value    "Aurinkopaneeli"
                                                    :modified 12345}
                       :unknown                    {:value "whatever"}
                       :tilapainenRakennelmaKytkin {:value true
                                                    :foo   "bar"}
                       :kuvaus                     {:value "Hello world"}}}
                                         [kuvaus-doc house-doc]))))))
