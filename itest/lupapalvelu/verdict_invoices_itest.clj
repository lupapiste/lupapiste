(ns lupapalvelu.verdict-invoices-itest
  (:require [lupapalvelu.fixture.core :as fix]
            [lupapalvelu.invoices :as inv]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))



(def timestamp util/to-millis-from-local-date-string)

(def sunday (timestamp "2.12.2018"))
(def monday (timestamp "3.12.2018"))
(def tuesday (timestamp "4.12.2018"))
(def wednesday (timestamp "5.12.2018"))
(def thursday (timestamp "6.12.2018"))
(def friday (timestamp "7.12.2018"))

(defn insert-catalog
  [{:keys [from until org-id published? prefix]}]
  (mongo/insert :price-catalogues {:organization-id org-id
                                   :state           (if published?
                                                      :published
                                                      :draft)
                                   :valid-from      from
                                   :valid-until     until
                                   :rows            (map-indexed (fn [i ops]
                                                                   (let [ops (map name (flatten [ops]))]
                                                                     {:code             (str prefix "-" i)
                                                                      :text             (->> [prefix ops i]
                                                                                             flatten
                                                                                             (ss/join " "))
                                                                      :unit             "kpl"
                                                                      :price-per-unit   (inc i)
                                                                      :discount-percent (* 10 i)
                                                                      :operations ops}))
                                                                 [:pientalo
                                                                  [:pientalo :purkaminen]
                                                                  [:kerrostalo-rivitalo :pientalo]
                                                                  :purkaminen
                                                                  [:laajentaminen :sisatila-muutos]])}))

(defn make-application [{:keys [id submitted ops]}]
  (let [[ox & oxs] (map-indexed (fn [i op]
                                  {:id          (mongo/create-id)
                                   :name        (name op)
                                   :created     12345
                                   :description (str "Operation " i)})
                                (flatten [ops]))]
    {:id                  id
     :organization        "753-R"
     :history             [(if submitted
                             {:state "submitted"
                              :ts    submitted}
                             {:state "draft"
                              :ts    sunday})]
     :primaryOperation    ox
     :secondaryOperations oxs}))

(def user {:id        "user"
           :firstName "Verdict"
           :lastName  "Giver"
           :role      "authority"
           :username  "verdict.giver@sipoo.fi"
           :email     "verdict.giver@sipoo.fi"})

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

(mongo/connect!)

(mongo/with-db test-db-name
  (fix/apply-fixture "minimal")
  (let [draft-app    (make-application {:id "draft" :ops :pientalo})
        tuesday-app  (make-application {:id        "tuesday"
                                        :ops       :pientalo
                                        :submitted tuesday})
        thursday-app (make-application {:id        "thursday"
                                        :ops       [:pientalo :purkaminen :unknown]
                                        :submitted thursday})]
    (insert-catalog {:from       monday
                     :until      wednesday
                     :org-id     "753-R"
                     :published? true
                     :prefix     "Item"})

    (fact "Invoicing not enabled"
      (inv/new-verdict-invoice (cmd friday tuesday-app) nil) => nil
      (provided (inv/invoicing-enabled anything) => {:ok false})
      (no-invoices))

    (fact "Application not submitted"
      (inv/new-verdict-invoice (cmd friday draft-app) nil) => nil
      (provided (inv/invoicing-enabled anything) => nil)
      (no-invoices))

    (fact "Organization mismatch"
      (inv/new-verdict-invoice (cmd friday (assoc tuesday-app
                                                  :organization "foo"))
                               nil) => nil
      (provided (inv/invoicing-enabled anything) => nil)
      (no-invoices))

    (fact "No active catalog"
      (inv/new-verdict-invoice (cmd friday thursday-app) nil) => nil
      (provided (inv/invoicing-enabled anything) => nil)
      (no-invoices))

    (fact "Invoice is created"
      (inv/new-verdict-invoice (cmd tuesday tuesday-app) nil)
      => nil
      (provided (inv/invoicing-enabled anything) => nil)
      (find-invoice tuesday-app)
      => (just {:application-id  "tuesday"
                :created         tuesday
                :created-by      {:firstName "Verdict"
                                  :id        "user"
                                  :lastName  "Giver"
                                  :role      "authority"
                                  :username  "verdict.giver@sipoo.fi"}
                :id              not-empty
                :operations      [{:invoice-rows [{:discount-percent 0
                                                   :price-per-unit   1
                                                   :code             "Item-0"
                                                   :text             "Item pientalo 0"
                                                   :type             "from-price-catalogue"
                                                   :unit             "kpl"
                                                   :units            0}
                                                  {:discount-percent 10
                                                   :price-per-unit   2
                                                   :code             "Item-1"
                                                   :text             "Item pientalo purkaminen 1"
                                                   :type             "from-price-catalogue"
                                                   :unit             "kpl"
                                                   :units            0}
                                                  {:discount-percent 20
                                                   :price-per-unit   3
                                                   :code             "Item-2"
                                                   :text             "Item kerrostalo-rivitalo pientalo 2"
                                                   :type             "from-price-catalogue"
                                                   :unit             "kpl"
                                                   :units            0}]
                                   :name         "pientalo"
                                   :operation-id (-> tuesday-app :primaryOperation :id)}]
                :organization-id "753-R"
                :state           "draft"}))

    (fact "Draft catalogs are ignored"
      (insert-catalog {:from   (inc wednesday)
                       :org-id "753-R"
                       :prefix "Draft"})
      (inv/new-verdict-invoice (cmd friday thursday-app) nil) => nil
      (provided (inv/invoicing-enabled anything) => nil)
      (find-invoice thursday-app) => nil)

    (fact "Thursday application can be invoiced with a new catalog"
      (insert-catalog {:from       (inc wednesday)
                       :org-id     "753-R"
                       :prefix     "New"
                       :published? true})
      (inv/new-verdict-invoice (cmd friday thursday-app) nil) => nil
      (provided (inv/invoicing-enabled anything) => nil)
      (find-invoice thursday-app)
      => (just {:application-id  "thursday"
                :created         friday
                :created-by      {:firstName "Verdict"
                                  :id        "user"
                                  :lastName  "Giver"
                                  :role      "authority"
                                  :username  "verdict.giver@sipoo.fi"}
                :id              ss/not-blank?
                :operations      [{:invoice-rows [{:discount-percent 0
                                                   :price-per-unit   1
                                                   :code             "New-0"
                                                   :text             "New pientalo 0"
                                                   :type             "from-price-catalogue"
                                                   :unit             "kpl"
                                                   :units            0}
                                                  {:discount-percent 10
                                                   :price-per-unit   2
                                                   :code             "New-1"
                                                   :text             "New pientalo purkaminen 1"
                                                   :type             "from-price-catalogue"
                                                   :unit             "kpl"
                                                   :units            0}
                                                  {:discount-percent 20
                                                   :price-per-unit   3
                                                   :code             "New-2"
                                                   :text             "New kerrostalo-rivitalo pientalo 2"
                                                   :type             "from-price-catalogue"
                                                   :unit             "kpl"
                                                   :units            0}]
                                   :name         "pientalo"
                                   :operation-id (operation-id thursday-app "pientalo")}
                                  {:invoice-rows [{:discount-percent 10
                                                   :price-per-unit   2
                                                   :code             "New-1"
                                                   :text             "New pientalo purkaminen 1"
                                                   :type             "from-price-catalogue"
                                                   :unit             "kpl"
                                                   :units            0}
                                                  {:discount-percent 30
                                                   :price-per-unit   4
                                                   :code             "New-3"
                                                   :text             "New purkaminen 3"
                                                   :type             "from-price-catalogue"
                                                   :unit             "kpl"
                                                   :units            0}]
                                   :name         "purkaminen"
                                   :operation-id (operation-id thursday-app "purkaminen")}
                                  {:invoice-rows []
                                   :name         "unknown"
                                   :operation-id (operation-id thursday-app "unknown")}]
                :organization-id "753-R"
                :state           "draft"}))

    (facts "Publish verdict -> invoice is created"
      (fact "Toggle invoicing ON"
        (local-command admin :update-organization
                       :invoicingEnabled true
                       :municipality "753"
                       :permitType "R") => ok?)
      (let [{op     :primaryOperation
             app-id :id} (create-and-submit-local-application pena
                                                              :propertyId sipoo-property-id
                                                              :operation "kerrostalo-rivitalo")]
        (fact "Give verdict and check invoice"
          (give-local-legacy-verdict sonja app-id)
          (-> (local-query sonja :application-invoices :id app-id)
              :invoices
              first)
          => (just {:application-id  "LP-753-2018-90001"
                    :created         pos?
                    :created-by      {:firstName "Sonja"
                                      :id        "777777777777777777000023"
                                      :lastName  "Sibbo"
                                      :role      "authority"
                                      :username  "sonja"}
                    :id              ss/not-blank?
                    :operations      [{:invoice-rows [{:discount-percent 20
                                                       :price-per-unit   3
                                                       :code             "New-2"
                                                       :text             "New kerrostalo-rivitalo pientalo 2"
                                                       :type             "from-price-catalogue"
                                                       :unit             "kpl"
                                                       :units            0}]
                                       :name         "kerrostalo-rivitalo"
                                       :operation-id (:id op)}]
                    :organization-id "753-R"
                    :state           "draft"}))))))
