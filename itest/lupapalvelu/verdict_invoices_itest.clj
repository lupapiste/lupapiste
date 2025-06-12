(ns lupapalvelu.verdict-invoices-itest
  (:require [lupapalvelu.invoices.core :as inv]
            [lupapalvelu.invoices.schemas :refer [PriceCatalogue PriceCatalogueDraft]]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
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

(def by-user (usr/summary user))

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
     :secondaryOperations oxs
     :address (str "Address " id)}))

(defn cmd
  [created application]
  {:created     created
   :application application
   :user        user})

(defn find-invoice [{app-id :id}]
  (mongo/select-one :invoices {:application-id app-id}))

(defn no-invoices []
  (fact "No invoices"
    (mongo/any? :invoices {}) => false?))

(defn op-id [{:keys [primaryOperation secondaryOperations]} op-name]
  (->> (cons primaryOperation secondaryOperations)
       (util/find-by-key :name (name op-name))
       :id))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (let [tuesday-app  (make-application {:id  "LP-666-2020-00001"
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

    (let [cid (insert-catalog {:from       monday
                               :until      wednesday
                               :org-id     "753-R"
                               :published? true
                               :prefix     "Item"})]
      (fact "Invoice is created"
        (let [pientalo-id (op-id tuesday-app :pientalo)]
          cid => truthy
          pientalo-id => truthy
          (inv/new-verdict-invoice (cmd tuesday tuesday-app) nil)
          => nil
          (provided (inv/invoicing-enabled anything) => nil)
          (find-invoice tuesday-app)
          => (just {:application-id     "LP-666-2020-00001"
                    :application-payer? true
                    :created            tuesday
                    :created-by         by-user
                    :description        "LP-666-2020-00001"
                    :id                 truthy
                    :modified           tuesday
                    :modified-by        by-user
                    :operations         [{:id   pientalo-id
                                          :name "pientalo"}]
                    :organization-id    "753-R"
                    :price-catalogue-id cid
                    :rows               (just (just {:code              "Item-0"
                                                     :id                truthy
                                                     :max-unit-price    11
                                                     :min-unit-price    0
                                                     :operation-id      pientalo-id
                                                     :price-per-unit    1
                                                     :product-constants dummy-product-constants
                                                     :text              "Item pientalo 0"
                                                     :unit              "kpl"})
                                              (just {:code              "Item-1"
                                                     :discount-percent  10
                                                     :id                truthy
                                                     :max-unit-price    12
                                                     :min-unit-price    0
                                                     :operation-id      pientalo-id
                                                     :price-per-unit    2
                                                     :product-constants dummy-product-constants
                                                     :text              "Item pientalo purkaminen 1"
                                                     :unit              "kpl"})
                                              (just {:code              "Item-2"
                                                     :discount-percent  20
                                                     :id                truthy
                                                     :max-unit-price    13
                                                     :min-unit-price    0
                                                     :operation-id      pientalo-id
                                                     :price-per-unit    3
                                                     :product-constants dummy-product-constants
                                                     :text              "Item kerrostalo-rivitalo pientalo 2"
                                                     :unit              "kpl"}))
                    :state              "draft"})))

      (fact "Invoice with invoice rows without min or max unit price created
          when catalogue has rows without min or max values for unit price"
        (let [tuesday-app-puun-kaataminen (make-application {:id  "LP-666-2020-00003"
                                                             :ops :puun-kaataminen})
              puu-id                      (op-id tuesday-app-puun-kaataminen
                                                 :puun-kaataminen)]
          puu-id => truthy
          (inv/new-verdict-invoice (cmd tuesday tuesday-app-puun-kaataminen) nil)
          => nil
          (provided (inv/invoicing-enabled anything) => nil)
          (find-invoice tuesday-app-puun-kaataminen)
          => (just {:application-id     "LP-666-2020-00003"
                    :description        "LP-666-2020-00003"
                    :created            tuesday
                    :created-by         by-user
                    :id                 truthy
                    :price-catalogue-id cid
                    :modified           tuesday
                    :modified-by        by-user
                    :operations         [{:name "puun-kaataminen"
                                          :id   puu-id}]
                    :rows               (just
                                          (just {:id             truthy
                                                 :operation-id   puu-id
                                                 :price-per-unit 5
                                                 :code           "puun-kaataminen"
                                                 :text           "Catalogue row without min or max for unit price"
                                                 :unit           "m2"}))
                    :organization-id    "753-R"
                    :application-payer? true
                    :state              "draft"}))))


    (fact "Application is invoiced with the best matching catalog."
      (let [from-wed  (insert-catalog {:from       wednesday
                                       :org-id     "753-R"
                                       :prefix     "Wed"
                                       :published? true})
            _from-fri (insert-catalog {:from       friday
                                       :org-id     "753-R"
                                       :prefix     "Fri"
                                       :published? true})
            _til-wed  (insert-catalog {:until      wednesday
                                       :org-id     "753-R"
                                       :prefix     "Til"
                                       :published? true})
            [pientalo-id purku-id
             katu-id] (map (partial op-id thursday-app)
                           [:pientalo :purkaminen :ya-katulupa-maalampotyot])]

        (inv/new-verdict-invoice (cmd thursday thursday-app) nil) => nil
        (provided (inv/invoicing-enabled anything) => nil)
        (find-invoice thursday-app)
        => (just {:application-id     "LP-666-2020-00002"
                  :description        "LP-666-2020-00002"
                  :created            thursday
                  :created-by         by-user
                  :id                 truthy
                  :price-catalogue-id from-wed
                  :modified           thursday
                  :modified-by        by-user
                  :operations         [{:name "pientalo" :id pientalo-id}
                                       {:name "purkaminen" :id purku-id}
                                       {:name "ya-katulupa-maalampotyot" :id katu-id}]
                  :rows               (just
                                        (just {:id                truthy
                                               :operation-id      pientalo-id
                                               :price-per-unit    1
                                               :min-unit-price    0
                                               :max-unit-price    11
                                               :code              "Wed-0"
                                               :text              "Wed pientalo 0"
                                               :unit              "kpl"
                                               :product-constants dummy-product-constants})
                                        (just {:id                truthy
                                               :operation-id      pientalo-id
                                               :discount-percent  10
                                               :price-per-unit    2
                                               :min-unit-price    0
                                               :max-unit-price    12
                                               :code              "Wed-1"
                                               :text              "Wed pientalo purkaminen 1"
                                               :unit              "kpl"
                                               :product-constants dummy-product-constants})
                                        (just {:id                truthy
                                               :operation-id      pientalo-id
                                               :discount-percent  20
                                               :price-per-unit    3
                                               :min-unit-price    0
                                               :max-unit-price    13
                                               :code              "Wed-2"
                                               :text              "Wed kerrostalo-rivitalo pientalo 2"
                                               :unit              "kpl"
                                               :product-constants dummy-product-constants})
                                        (just {:id                truthy
                                               :operation-id      purku-id
                                               :discount-percent  10
                                               :price-per-unit    2
                                               :min-unit-price    0
                                               :max-unit-price    12
                                               :code              "Wed-1"
                                               :text              "Wed pientalo purkaminen 1"
                                               :unit              "kpl"
                                               :product-constants dummy-product-constants})
                                        (just {:id                truthy
                                               :operation-id      purku-id
                                               :discount-percent  30
                                               :price-per-unit    4
                                               :min-unit-price    0
                                               :max-unit-price    14
                                               :code              "Wed-3"
                                               :text              "Wed purkaminen 3"
                                               :unit              "kpl"
                                               :product-constants dummy-product-constants}))
                  :organization-id    "753-R"
                  :application-payer? true
                  :state              "draft"})))

    (facts "Publish verdict -> invoice is created"
      (fact "Toggle invoicing ON"
        (local-command admin :update-organization
                       :invoicingEnabled true
                       :municipality "753"
                       :permitType "R") => ok?)
      (let [{op :primaryOperation app-id :id}
            (create-and-submit-local-application pena
                                                 :operation "kerrostalo-rivitalo"
                                                 :x "385770.46" :y "6672188.964"
                                                 :address "Kaivokatu 1")
            by-sonja {:firstName "Sonja"
                      :id        "777777777777777777000023"
                      :lastName  "Sibbo"
                      :role      "authority"
                      :username  "sonja"}]

        (fact "Give verdict and check invoice"
          (give-local-legacy-verdict sonja app-id :R true)
          (-> (local-query sonja :application-invoices :id app-id) :invoices first)
          => (just {:application-id     app-id
                    :description        app-id
                    :created            pos?
                    :created-by         by-sonja
                    :id                 truthy
                    :price-catalogue-id truthy
                    :modified           pos?
                    :operations         [{:name "kerrostalo-rivitalo"
                                          :id   (:id op)}]
                    :rows               (just
                                          (just {:id                truthy
                                                 :operation-id      (:id op)
                                                 :discount-percent  20
                                                 :price-per-unit    3
                                                 :min-unit-price    0
                                                 :max-unit-price    13
                                                 :code              "Wed-2"
                                                 :text              "Wed kerrostalo-rivitalo pientalo 2"
                                                 :unit              "kpl"
                                                 :product-constants dummy-product-constants}))
                    :organization-id    "753-R"
                    :state              "draft"
                    :application-payer? true}))

        (fact "..but not if app already has an invoice"
          (let [{op :primaryOperation app-id :id}
                (create-and-submit-local-application pena
                                                     :operation "kerrostalo-rivitalo"
                                                     :x "385770.46" :y "6672188.964"
                                                     :address "Kaivokatu 1")]

            (local-command sonja :new-invoice-draft :id app-id)

            (fact "single draft invoice exists"
              (let [[invoice :as invoices] (-> (local-query sonja :application-invoices :id app-id) :invoices)]
                (fact "only one" (count invoices) => 1)
                (fact "earlier draft"
                  invoice => (just {:application-id     app-id
                                    :description        app-id
                                    :created            pos?
                                    :created-by         by-sonja
                                    :id                 truthy
                                    :price-catalogue-id truthy
                                    :modified           pos?
                                    :operations         empty?
                                    :rows               empty?
                                    :organization-id    "753-R"
                                    :state              "draft"
                                    :application-payer? true}))

                (fact "Give verdict and check no new verdict invoice"
                  (give-local-legacy-verdict sonja app-id :R true)
                  (let [[other-invoice :as invoices] (-> (local-query sonja :application-invoices :id app-id) :invoices)]
                    (fact "only one" (count invoices) => 1)
                    (fact "original invoice not modified"
                      (= other-invoice invoice) => truthy)))))))))))
