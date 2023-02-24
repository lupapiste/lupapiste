(ns lupapalvelu.verdict-invoices-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.core :as fix]
            [lupapalvelu.invoices :as inv]
            [lupapalvelu.invoices.schemas :refer [PriceCatalogue PriceCatalogueDraft]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.date :refer [timestamp]]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))



(def sunday (timestamp "2.12.2018"))
(def monday (timestamp "3.12.2018"))
(def tuesday (timestamp "4.12.2018"))
(def wednesday (timestamp "5.12.2018"))
(def thursday (timestamp "6.12.2018"))
(def friday (timestamp "7.12.2018"))

(def dummy-product-constants {:kustannuspaikka  "a"
                              :alv              "b"
                              :laskentatunniste "c"
                              :projekti         "d"
                              :kohde            "e"
                              :toiminto         "f"
                              :muu-tunniste     "g"})

(def user {:id        "user"
           :firstName "Verdict"
           :lastName  "Giver"
           :role      "authority"
           :username  "verdict.giver@sipoo.fi"
           :email     "verdict.giver@sipoo.fi"})

(defn insert-catalog
  [{:keys [from until org-id published? prefix]}]
  (let [user       (dissoc user :email)
        catalog-id (mongo/create-id)]
    (->> {:id              catalog-id
          :organization-id org-id
          :valid-from      from
          :valid-until     until
          :name            "Taxi"
          :type            "R"
          :rows            (-> (map-indexed (fn [i ops]
                                              (let [ops (map name (flatten [ops]))]
                                                {:id                (mongo/create-id)
                                                 :code              (str prefix "-" i)
                                                 :text              (->> [prefix ops i]
                                                                         flatten
                                                                         (ss/join " "))
                                                 :unit              "kpl"
                                                 :min-total-price   0
                                                 :price-per-unit    (inc i)
                                                 :max-total-price   (+ 10 (inc i))
                                                 :discount-percent  (* 10 i)
                                                 :product-constants dummy-product-constants
                                                 :operations        ops}))
                                            [:pientalo
                                             [:pientalo :purkaminen]
                                             [:kerrostalo-rivitalo :pientalo]
                                             :purkaminen
                                             [:laajentaminen :sisatila-muutos]])

                               (conj {:id               (mongo/create-id)
                                      :code             "puun-kaataminen"
                                      :text             "Catalogue row without min or max for unit price"
                                      :unit             "m2"
                                      :price-per-unit   5
                                      :discount-percent 0
                                      :operations       ["puun-kaataminen"]}))
          :state           (if published? "published" "draft")
          :meta            {:modified    sunday
                            :modified-by user}}
         util/strip-nils
         (sc/validate (if published? PriceCatalogue PriceCatalogueDraft))
         (mongo/insert :price-catalogues))
    catalog-id))

(defn make-application [{:keys [id ops]}]
  (let [[ox & oxs] (map-indexed (fn [i op]
                                  {:id          (mongo/create-id)
                                   :name        (name op)
                                   :created     12345
                                   :description (str "Operation " i)})
                                (flatten [ops]))]
    {:id                  id
     :organization        "753-R"
     :primaryOperation    ox
     :secondaryOperations oxs}))

(defn cmd [created application]
  {:created     created
   :application application
   :user        user})

(defn operation-id [{:keys [primaryOperation secondaryOperations]} op-name]
  (->> (concat [primaryOperation] [secondaryOperations])
       flatten
       (util/find-by-key :name op-name)
       :id))

(defn find-invoice [{app-id :id}]
  (mongo/select-one :invoices {:application-id app-id}))

(defn no-invoices []
  (fact "No invoices"
    (mongo/select :invoices {} {:id 1}) => empty?))


(mount/start #'mongo/connection)


(mongo/with-db test-db-name
  (fix/apply-fixture "minimal")
  (let [draft-app    (make-application {:id "draft" :ops :pientalo})
        tuesday-app  (make-application {:id  "LP-666-2020-00001"
                                        :ops :pientalo})
        thursday-app (make-application {:id  "LP-666-2020-00002"
                                        :ops [:pientalo :purkaminen :ya-katulupa-maalampotyot]})
        pub-id       (insert-catalog {:from       monday
                                      :until      wednesday
                                      :org-id     "753-R"
                                      :published? true
                                      :prefix     "Item"})
        draft-id     (insert-catalog {:from       monday
                                      :until      wednesday
                                      :org-id     "753-R"
                                      :published? false
                                      :prefix     "Item"})]

    (fact "Invoicing not enabled"
      (inv/new-verdict-invoice (cmd friday tuesday-app) nil) => nil
      (provided (inv/invoicing-enabled anything) => {:ok false})
      (no-invoices))

    (fact "Organization mismatch"
      (inv/new-verdict-invoice (cmd friday (assoc tuesday-app
                                                  :organization "foo"))
                               nil) => nil
      (provided (inv/invoicing-enabled anything) => nil)
      (no-invoices))

    (fact "Catalogue not published"
      (mongo/remove :price-catalogues pub-id) => true
      (inv/new-verdict-invoice (cmd tuesday tuesday-app) nil) => nil
      (provided (inv/invoicing-enabled anything) => nil)
      (no-invoices))

    (fact "No catalogues"
      (mongo/remove :price-catalogues draft-id) => true
      (inv/new-verdict-invoice (cmd tuesday tuesday-app) nil) => nil
      (provided (inv/invoicing-enabled anything) => nil)
      (no-invoices))

    (fact "Invoice is created"
      (let [cid (insert-catalog {:from       monday
                                 :until      wednesday
                                 :org-id     "753-R"
                                 :published? true
                                 :prefix     "Item"})]
        cid => truthy
        (inv/new-verdict-invoice (cmd tuesday tuesday-app) nil)
        => nil
        (provided (inv/invoicing-enabled anything) => nil)
        (find-invoice tuesday-app)
        => (just {:application-id     "LP-666-2020-00001"
                  :description        "LP-666-2020-00001"
                  :created            tuesday
                  :created-by         {:firstName "Verdict"
                                       :id        "user"
                                       :lastName  "Giver"
                                       :role      "authority"
                                       :username  "verdict.giver@sipoo.fi"}
                  :id                 not-empty
                  :price-catalogue-id cid
                  :history            [{:action "created-from-verdict"
                                        :state  "draft"
                                        :time   tuesday
                                        :user   {:firstName "Verdict"
                                                 :id        "user"
                                                 :lastName  "Giver"
                                                 :role      "authority"
                                                 :username  "verdict.giver@sipoo.fi"}}]
                  :modified           tuesday
                  :operations         [{:invoice-rows [{:discount-percent  0
                                                        :price-per-unit    1
                                                        :min-unit-price    0
                                                        :max-unit-price    11
                                                        :code              "Item-0"
                                                        :text              "Item pientalo 0"
                                                        :type              "from-price-catalogue"
                                                        :unit              "kpl"
                                                        :units             0
                                                        :order-number      0
                                                        :product-constants dummy-product-constants}
                                                       {:discount-percent  10
                                                        :price-per-unit    2
                                                        :min-unit-price    0
                                                        :max-unit-price    12
                                                        :code              "Item-1"
                                                        :text              "Item pientalo purkaminen 1"
                                                        :type              "from-price-catalogue"
                                                        :unit              "kpl"
                                                        :units             0
                                                        :order-number      1
                                                        :product-constants dummy-product-constants}
                                                       {:discount-percent  20
                                                        :price-per-unit    3
                                                        :min-unit-price    0
                                                        :max-unit-price    13
                                                        :code              "Item-2"
                                                        :text              "Item kerrostalo-rivitalo pientalo 2"
                                                        :type              "from-price-catalogue"
                                                        :unit              "kpl"
                                                        :units             0
                                                        :order-number      2
                                                        :product-constants dummy-product-constants}]
                                        :name         "pientalo"
                                        :operation-id (-> tuesday-app :primaryOperation :id)}]
                  :organization-id    "753-R"
                  :payer-type         "person"
                  :state              "draft"})))

    (fact "Invoice with invoice rows without min or max unit price created
          when catalogue has rows without min or max values for unit price"
      (let [tuesday-app-puun-kaataminen (make-application {:id  "LP-666-2020-00003"
                                                           :ops :puun-kaataminen})]

        (inv/new-verdict-invoice (cmd tuesday tuesday-app-puun-kaataminen) nil)
        => nil
        (provided (inv/invoicing-enabled anything) => nil)
        (find-invoice tuesday-app-puun-kaataminen)
        => (just {:application-id     "LP-666-2020-00003"
                  :description        "LP-666-2020-00003"
                  :created            tuesday
                  :created-by         {:firstName "Verdict"
                                       :id        "user"
                                       :lastName  "Giver"
                                       :role      "authority"
                                       :username  "verdict.giver@sipoo.fi"}
                  :id                 not-empty
                  :price-catalogue-id truthy
                  :history            [{:action "created-from-verdict"
                                        :state  "draft"
                                        :time   tuesday
                                        :user   {:firstName "Verdict"
                                                 :id        "user"
                                                 :lastName  "Giver"
                                                 :role      "authority"
                                                 :username  "verdict.giver@sipoo.fi"}}]
                  :modified           tuesday
                  :operations         [{:invoice-rows [{:discount-percent 0
                                                        :price-per-unit   5
                                                        :code             "puun-kaataminen"
                                                        :text             "Catalogue row without min or max for unit price"
                                                        :type             "from-price-catalogue"
                                                        :unit             "m2"
                                                        :units            0
                                                        :order-number     0}]
                                        :name         "puun-kaataminen"
                                        :operation-id (-> tuesday-app-puun-kaataminen :primaryOperation :id)}]
                  :organization-id    "753-R"
                  :payer-type         "person"
                  :state              "draft"})))


    (fact "Application is invoiced with the best matching catalog."
      (let [from-wed (insert-catalog {:from       wednesday
                                      :org-id     "753-R"
                                      :prefix     "Wed"
                                      :published? true})
            from-fri (insert-catalog {:from       friday
                                      :org-id     "753-R"
                                      :prefix     "Fri"
                                      :published? true})
            til-wed  (insert-catalog {:until      wednesday
                                      :org-id     "753-R"
                                      :prefix     "Til"
                                      :published? true})]
        (inv/new-verdict-invoice (cmd thursday thursday-app) nil) => nil
        (provided (inv/invoicing-enabled anything) => nil)
        (find-invoice thursday-app)
        => (just {:application-id     "LP-666-2020-00002"
                  :description        "LP-666-2020-00002"
                  :created            thursday
                  :created-by         {:firstName "Verdict"
                                       :id        "user"
                                       :lastName  "Giver"
                                       :role      "authority"
                                       :username  "verdict.giver@sipoo.fi"}
                  :id                 ss/not-blank?
                  :price-catalogue-id from-wed
                  :history            [{:action "created-from-verdict"
                                        :state  "draft"
                                        :time   thursday
                                        :user   {:firstName "Verdict"
                                                 :id        "user"
                                                 :lastName  "Giver"
                                                 :role      "authority"
                                                 :username  "verdict.giver@sipoo.fi"}}]
                  :modified           thursday
                  :operations         [{:invoice-rows [{:discount-percent  0
                                                        :price-per-unit    1
                                                        :min-unit-price    0
                                                        :max-unit-price    11
                                                        :code              "Wed-0"
                                                        :text              "Wed pientalo 0"
                                                        :type              "from-price-catalogue"
                                                        :unit              "kpl"
                                                        :units             0
                                                        :order-number      0
                                                        :product-constants dummy-product-constants}
                                                       {:discount-percent  10
                                                        :price-per-unit    2
                                                        :min-unit-price    0
                                                        :max-unit-price    12
                                                        :code              "Wed-1"
                                                        :text              "Wed pientalo purkaminen 1"
                                                        :type              "from-price-catalogue"
                                                        :unit              "kpl"
                                                        :units             0
                                                        :order-number      1
                                                        :product-constants dummy-product-constants}
                                                       {:discount-percent  20
                                                        :price-per-unit    3
                                                        :min-unit-price    0
                                                        :max-unit-price    13
                                                        :code              "Wed-2"
                                                        :text              "Wed kerrostalo-rivitalo pientalo 2"
                                                        :type              "from-price-catalogue"
                                                        :unit              "kpl"
                                                        :units             0
                                                        :order-number      2
                                                        :product-constants dummy-product-constants}]
                                        :name         "pientalo"
                                        :operation-id (operation-id thursday-app "pientalo")}
                                       {:invoice-rows [{:discount-percent  10
                                                        :price-per-unit    2
                                                        :min-unit-price    0
                                                        :max-unit-price    12
                                                        :code              "Wed-1"
                                                        :text              "Wed pientalo purkaminen 1"
                                                        :type              "from-price-catalogue"
                                                        :unit              "kpl"
                                                        :units             0
                                                        :order-number      1
                                                        :product-constants dummy-product-constants}
                                                       {:discount-percent  30
                                                        :price-per-unit    4
                                                        :min-unit-price    0
                                                        :max-unit-price    14
                                                        :code              "Wed-3"
                                                        :text              "Wed purkaminen 3"
                                                        :type              "from-price-catalogue"
                                                        :unit              "kpl"
                                                        :units             0
                                                        :order-number      3
                                                        :product-constants dummy-product-constants}]
                                        :name         "purkaminen"
                                        :operation-id (operation-id thursday-app "purkaminen")}
                                       {:invoice-rows []
                                        :name         "ya-katulupa-maalampotyot"
                                        :operation-id (operation-id thursday-app "ya-katulupa-maalampotyot")}]
                  :organization-id    "753-R"
                  :payer-type         "person"
                  :state              "draft"})))

    (facts "Publish verdict -> invoice is created"
      (fact "Toggle invoicing ON"
        (local-command admin :update-organization
                       :invoicingEnabled true
                       :municipality "753"
                       :permitType "R") => ok?)
      (let [{op :primaryOperation app-id :id :as application}
            (create-and-submit-local-application pena
                                                 :operation "kerrostalo-rivitalo"
                                                 :x "385770.46" :y "6672188.964"
                                                 :address "Kaivokatu 1")
            maksaja-doc-id (:id (domain/get-document-by-name application "maksaja"))]

        (fact "Give verdict and check invoice"
          (let [updates [["henkilo.henkilotiedot.etunimi" "Pena"]
                         ["henkilo.henkilotiedot.sukunimi" "Panaani"]
                         ["henkilo.henkilotiedot.hetu" "010203-040A"]
                         ["henkilo.osoite.katu" "Paapankuja 12"]
                         ["henkilo.osoite.postinumero" "10203"]
                         ["henkilo.osoite.postitoimipaikannimi" "Piippola"]
                         ["laskuviite" "laskuviite-pena"]]]
            (update-document! pena app-id maksaja-doc-id updates true))
          (Thread/sleep 2000)
          (give-local-legacy-verdict sonja app-id)
          (-> (local-query sonja :application-invoices :id app-id) :invoices first)
          => (just {:application-id     app-id
                    :description        app-id
                    :created            pos?
                    :created-by         {:firstName "Sonja"
                                         :id        "777777777777777777000023"
                                         :lastName  "Sibbo"
                                         :role      "authority"
                                         :username  "sonja"}
                    :id                 ss/not-blank?
                    :history            not-empty
                    :price-catalogue-id truthy
                    :modified           pos?
                    :operations         [{:invoice-rows [{:discount-percent  20
                                                          :price-per-unit    3
                                                          :min-unit-price    0
                                                          :max-unit-price    13
                                                          :code              "Wed-2"
                                                          :text              "Wed kerrostalo-rivitalo pientalo 2"
                                                          :type              "from-price-catalogue"
                                                          :unit              "kpl"
                                                          :units             0
                                                          :order-number      0
                                                          :product-constants dummy-product-constants}]
                                          :name         "kerrostalo-rivitalo"
                                          :operation-id (:id op)}]
                    :organization-id    "753-R"
                    :state              "draft"
                    :entity-name        "Pena Panaani"
                    :person-id          "010203-040A"
                    :entity-address     "Paapankuja 12 10203 Piippola"
                    :payer-type         "person"
                    :billing-reference  "laskuviite-pena"}))))))
