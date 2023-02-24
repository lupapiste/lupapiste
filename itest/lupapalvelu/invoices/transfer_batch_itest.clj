(ns lupapalvelu.invoices.transfer-batch-itest
  (:require [clojurewerkz.money.currencies :refer [EUR]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.invoices.transfer-batch :as tb :refer [add-invoice-to-transfer-batch!
                                                                transfer-batch-db-key
                                                                transfer-batch-db-key]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.money :as money]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.sftp.client :as sftp]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.core :as sade]
            [sade.date :as date]
            [sade.env :as env]
            [sade.util :as util]))

(def now-timestamp 12345)

(def dummy-product-constants {:kustannuspaikka  "a"
                              :alv              "b"
                              :laskentatunniste "c"
                              :projekti         "d"
                              :kohde            "e"
                              :toiminto         "f"
                              :muu-tunniste     "g"})

(def dummy-invoice {:id              "dummy-invoice-id-1"
                    :application-id  "LP-753-2018-90108"
                    :organization-id "753-R"
                    :sum             {:major    20
                                      :minor    2000
                                      :currency "EUR"
                                      :text     (money/->currency-text EUR 20.00)}

                    :state           "draft"
                    :operations      [{:operation-id "linjasaneeraus"
                                       :name         "linjasaneeraus"
                                       :invoice-rows [{:text              "Laskurivi1 kpl"
                                                       :type              "from-price-catalogue"
                                                       :unit              "kpl"
                                                       :price-per-unit    10
                                                       :units             2
                                                       :discount-percent  0
                                                       :product-constants dummy-product-constants}]}]})

(defn add-organization [id invoicing-enabled?]
  (mongo/insert :organizations {:id    id
                                :scope [{:permitType        "R"
                                         :invoicing-enabled invoicing-enabled?}]}))

(defn get-user [apikey]
  (util/find-first (util/fn-> :private :apikey (= apikey)) minimal/users))

(def laura-user (usr/summary (get-user laura)))

(defn auth-user
  "Adds auth authz for the user in orgs."
  [auth apikey & org-ids]
  (-> (get-user apikey)
      (update :orgAuthz (fn [authz]
                          (reduce (fn [acc k]
                                    (update acc (keyword k) conj auth))
                                  authz
                                  org-ids)))
      usr/with-org-auth))

(def biller-user (partial auth-user "biller"))

(defn create-new-tb! [org-id user & [props]]
  (let [transfer-batch (tb/->invoice-transfer-batch-db {} org-id user nil)
            id (mongo/create-id)
            transfer-batch-with-id (merge (assoc transfer-batch :id id)
                                          props)]
        (tb/validate-invoice-transfer-batch transfer-batch-with-id)
        (mongo/insert transfer-batch-db-key transfer-batch-with-id)
        id))

(defn get-transfer-batch-db [id]
  (mongo/by-id transfer-batch-db-key id))

(defn tb-created [tb]
  (get-in tb [:transfer-batch :created]))

(defn tb-state [tb]
  (get-in tb [:transfer-batch :state]))

(defn ->tb-list [tbs-by-org]
  (-> tbs-by-org vals flatten))

(defn create-invoice-with-nbr-rows [amount-of-rows]
  (let [row {:text "Laskurivi1 kpl"
             :type "from-price-catalogue"
             :unit "kpl"
             :price-per-unit 10
             :units 2
             :discount-percent 0
             :product-constants dummy-product-constants}]
    (assoc dummy-invoice
               :id (mongo/create-id)
               :operations [{:operation-id "linjasaneeraus"
                             :name "linjasaneeraus"
                             :invoice-rows (repeat amount-of-rows row)}])))

(defn dummy-submitted-application []
  (create-and-submit-local-application
    pena
    :operation "pientalo"
    :x "385770.46" :y "6672188.964"
    :address "Kaivokatu 1"))

(defn new-invoice
  "Create new invoice draft and initialize it with`invoice`. Returns invoice id."
  ([apikey app-id invoice]
   (let [invoice-id (some-> (local-command apikey :new-invoice-draft :id app-id)
                            :invoice :id)]
     (fact {:midje/description (format "New invoice %s for application %s"
                                       invoice-id app-id)}
       invoice-id => truthy
       (when invoice
         (local-command apikey :update-invoice :id app-id
                        :invoice (assoc invoice :id invoice-id)) => ok?))
     invoice-id))
  ([apikey app-id]
   (new-invoice apikey app-id nil)))

(defn invoice->confirmed [draft-invoice]
  (let [user           laura
        {:keys [id]}   (dummy-submitted-application)
        new-invoice-id (new-invoice user id draft-invoice)
        new-invoice    (:invoice (local-query user :fetch-invoice :id id :invoice-id new-invoice-id))]
    (local-command user :update-invoice :id id :invoice (assoc new-invoice :state "checked"))
    (local-command user :update-invoice :id id :invoice (assoc new-invoice :state "confirmed"))
    new-invoice-id))

(defn invoice-with! [draft-invoice user & [{:keys [application-id backend-code] :as properties}]]
  (let [user       (or user laura)
        app-id     (or application-id (:id (dummy-submitted-application)))
        invoice-id (new-invoice user app-id draft-invoice)]
    (when backend-code
      (local-command user :set-invoice-backend-code :id app-id
                     :invoice-id invoice-id
                     :code backend-code))
    (local-command user :update-invoice :id app-id :invoice {:id invoice-id :state "checked"})
    (local-command user :update-invoice :id app-id :invoice (merge {:id invoice-id}
                                                                   properties))
    (:invoice (local-query user :fetch-invoice :id app-id :invoice-id invoice-id))))

(defn invoice->checked [id & [user]]
  (let [invoice (mongo/by-id :invoices id)]
    (local-command (or user laura) :update-invoice :id (:application-id invoice) :invoice (assoc invoice :state "checked"))
    id))

(defn get-org-transfer-batches [org]
  (get (:transfer-batches (local-query laura :organizations-transferbatches :organizationId "753-R"))
       org))

(defn get-tb-from-db [org]
  (let [tb-result (:transfer-batches (local-query laura :organizations-transferbatches :organizationId "753-R"))
        org-tb (get tb-result org)]
    (first org-tb)))

(deftype NoopDeliverer []
  tb/TransferBatch
  (send-to-sap [_ _ _] nil))

(deftype ExceptionDeliverer []
  tb/TransferBatch
  (send-to-sap [_ _ _] (throw (Exception. "Miserable failure"))))

(env/with-feature-value :invoices true
  (mount/start #'mongo/connection)

  (mongo/with-db test-db-name
    (lupapalvelu.fixture.core/apply-fixture "invoicing-enabled")

    (facts "add-invoice-to-invoice-transfer-batch"
      (let [invoice-one-id (mongo/create-id)
            invoice-one    (merge dummy-invoice {:id invoice-one-id})
            user           laura-user]
        (mongo/insert :invoices invoice-one)


        (let [invoice-1-added-time 1111
              invoice-2-added-time 1122]

          (fact "when invoice is inserted to transfer batch,
                 and when organization doesn't have existing ones,
                 a new transfer batch is created with the invoice and number of rows is 1
                 and transfer batch has a single history entry"
            (let [transfer-batch-id      (add-invoice-to-transfer-batch! invoice-one user invoice-1-added-time)
                  transfer-batch-from-db (get-transfer-batch-db transfer-batch-id)]
              (:invoices transfer-batch-from-db) => [{:id                      invoice-one-id
                                                      :organization-id         (:organization-id invoice-one)
                                                      :added-to-transfer-batch invoice-1-added-time}]
              (:organization-id transfer-batch-from-db) => (:organization-id invoice-one)
              (:number-of-rows transfer-batch-from-db) => 1
              (:history transfer-batch-from-db) => [{:user   user
                                                     :time   invoice-1-added-time
                                                     :action (str "Add invoice " invoice-one-id)
                                                     :state  "open"}]))

          (against-background [(sade/now) => now-timestamp]
            (fact "when another invoice is added with 3 rows,
                   number of rows in 4, when existing invoice had 1 row
                   and modified timestamp is set and second history entry added"
              (let [two-operations-with-total-3-rows {:operations [{:operation-id "linjasaneeraus KAKKONEN"
                                                                    :name         "linjasaneeraus kakkonen"
                                                                    :invoice-rows [{:text              "Laskurivi1 kpl"
                                                                                    :type              "from-price-catalogue"
                                                                                    :unit              "kpl"
                                                                                    :price-per-unit    10
                                                                                    :units             2
                                                                                    :discount-percent  0
                                                                                    :product-constants dummy-product-constants}]}
                                                                   {:operation-id "joku muu"
                                                                    :name         "joku muu"
                                                                    :invoice-rows [{:text              "Laskurivi1 kpl"
                                                                                    :type              "from-price-catalogue"
                                                                                    :unit              "kpl"
                                                                                    :price-per-unit    10
                                                                                    :units             2
                                                                                    :discount-percent  0
                                                                                    :product-constants dummy-product-constants}
                                                                                   {:text              "Laskurivi1 kpl"
                                                                                    :type              "from-price-catalogue"
                                                                                    :unit              "kpl"
                                                                                    :price-per-unit    10
                                                                                    :units             2
                                                                                    :discount-percent  0
                                                                                    :product-constants dummy-product-constants}]}]}
                    invoice-two-id                   (mongo/create-id)
                    invoice-two                      (merge dummy-invoice {:id invoice-two-id} two-operations-with-total-3-rows)
                    invoice-2-added-time             1122]
                (mongo/insert :invoices invoice-two)
                (let [transfer-batch-id (add-invoice-to-transfer-batch! invoice-two laura-user invoice-2-added-time)
                      transfer-batch    (get-transfer-batch-db transfer-batch-id)]
                  (:number-of-rows transfer-batch) => 4
                  (:modified transfer-batch) => invoice-2-added-time
                  (:history transfer-batch) => [{:user   user
                                                 :time   invoice-1-added-time
                                                 :action (str "Add invoice " invoice-one-id)
                                                 :state  "open"}
                                                {:user   user
                                                 :time   invoice-2-added-time
                                                 :action (str "Add invoice " invoice-two-id)
                                                 :state  "open"}]
                  (:sum transfer-batch) => {:currency "EUR" :major 40 :minor 4000 :text (money/->currency-text EUR 40.00)}))

              (mongo/drop-collection transfer-batch-db-key)
              (mongo/drop-collection :invoices))))

        (fact "Close transfer-batch and add new invoice to new transfer-batch, when too many rows in transfer-batch"
          (let [invoice-5-1 (create-invoice-with-nbr-rows 5)
                invoice-5-2 (create-invoice-with-nbr-rows 5)
                invoice-5-3 (create-invoice-with-nbr-rows 5)]
            (mongo/insert :invoices invoice-5-1)
            (mongo/insert :invoices invoice-5-2)
            (mongo/insert :invoices invoice-5-3)

            (with-redefs [tb/default-nbr-rows 10]
              (let [tb-id (add-invoice-to-transfer-batch! invoice-5-1 laura-user now-timestamp)
                    tb    (get-transfer-batch-db tb-id)]
                (:state tb) => "open"
                (:number-of-rows tb) => 5
                (:modified tb) => now-timestamp

                (add-invoice-to-transfer-batch! invoice-5-2 laura-user (+ now-timestamp 1))
                (let [tb (get-transfer-batch-db tb-id)]
                  (:number-of-rows tb) => 10
                  (:state tb) => "open"
                  (:modified tb) => (+ now-timestamp 1)

                  (let [user      {:id        "leenan-id"
                                   :firstName "Leena"
                                   :lastName  "Laskuttaja"
                                   :role      "authority"
                                   :username  "leena"}
                        second-id (add-invoice-to-transfer-batch! invoice-5-3 user (+ now-timestamp 2))
                        tb1       (get-transfer-batch-db tb-id)
                        tb2       (get-transfer-batch-db second-id)]
                    (:state tb1) => "closed"
                    (:number-of-rows tb1) => 10
                    (:modified tb1) => (+ now-timestamp 2)

                    (fact "Last history entry of the full transfer batch should indicate closing"
                      (last (:history tb1)) => {:user   {:id        "leenan-id"
                                                         :firstName "Leena"
                                                         :lastName  "Laskuttaja"
                                                         :role      "authority"
                                                         :username  "leena"}
                                                :time   (+ now-timestamp 2)
                                                :action "Close transfer batch"
                                                :state  "closed"})

                    (:state tb2) => "open"
                    (:number-of-rows tb2) => 5
                    (:modified tb2) => (+ now-timestamp 2))))))
          (mongo/drop-collection transfer-batch-db-key)
          (mongo/drop-collection :invoices))))

    (def test-invoice {:operations [{:operation-id "48d5cbc2244fbb2023856df4"
                                     :name         "linjasaneeraus"
                                     :invoice-rows [{:text              "Row 1 kpl"
                                                     :code              "111"
                                                     :type              "from-price-catalogue"
                                                     :unit              "kpl"
                                                     :price-per-unit    10
                                                     :units             2
                                                     :discount-percent  0
                                                     :product-constants dummy-product-constants}
                                                    {:text              "Row 2 m2"
                                                     :code              "111"
                                                     :type              "from-price-catalogue"
                                                     :unit              "m2"
                                                     :price-per-unit    20.5
                                                     :units             15.8
                                                     :discount-percent  50
                                                     :product-constants dummy-product-constants}
                                                    {:text             "Custom row m3"
                                                     :type             "custom"
                                                     :unit             "m3"
                                                     :price-per-unit   20.5
                                                     :units            15.8
                                                     :discount-percent 100}]}]})

    (against-background [(sade/now) => now-timestamp]
      (fact "Should return transfer batch with one invoice when invoice is transferred to confirmed"
        (mongo/drop-collection tb/transfer-batch-db-key)
        (let [invoice-id          (invoice->confirmed test-invoice)
              org-transfer-batch  (get-tb-from-db "753-R")
              transferred-invoice (mongo/by-id :invoices invoice-id)
              tb-id               (:id org-transfer-batch)]
          (:invoice-count org-transfer-batch) => 1
          (get-in org-transfer-batch [:transfer-batch :created]) => now-timestamp
          (:invoice-row-count org-transfer-batch) => 3
          (:transferbatch-id transferred-invoice) => tb-id
          (:sum (:transfer-batch org-transfer-batch)) => {:currency "EUR" :major 181 :minor 18195 :text (money/->currency-text EUR 181.95)})))

    (fact "Transfer batch is closed when close-transfer-batch command is called"
      (mongo/drop-collection tb/transfer-batch-db-key)
      (let [invoice-id   (invoice->confirmed test-invoice)
            invoice-id-2 (invoice->confirmed test-invoice)
            tb           (:transfer-batch (get-tb-from-db "753-R"))]
        (:state tb) => "open"

        (fact "close-transfer-batch succeeds"
          (local-command laura :close-transfer-batch :organizationId "753-R" :transfer-batch-id (:id tb)) => ok?
          (provided (sade/now) => now-timestamp))

        (let [tb        (:transfer-batch (get-tb-from-db "753-R"))
              invoice   (mongo/by-id :invoices invoice-id)
              invoice-2 (mongo/by-id :invoices invoice-id-2)]

          (fact "Transfer batch doc in db should have state, timestamps and history entry indicate closing"
            (:state tb) => "closed"
            (:closed tb) => now-timestamp
            (:modified tb) => now-timestamp
            (last (:history tb)) => {:user   {:id        "laura-laskuttaja"
                                              :firstName "Laura"
                                              :lastName  "Laskuttaja"
                                              :role      "authority"
                                              :username  "laura"}
                                     :time   now-timestamp
                                     :action "Close transfer batch"
                                     :state  "closed"})

          (fact "All invoices in transfer batch should be marked as transferred"
            (:state invoice) => "transferred"
            (:state invoice-2) => "transferred"))))

    (fact "Should not close transfer batch when user doesn't have correct organization"
      (mongo/drop-collection tb/transfer-batch-db-key)
      (invoice->confirmed test-invoice)
      (let [tb (:transfer-batch (get-tb-from-db "753-R"))]
        (:state tb) => "open"
        (local-command naantali :close-transfer-batch :transfer-batch-id (:id tb)) => fail?
        (let [tb (:transfer-batch (get-tb-from-db "753-R"))]
          (:state tb) => "open")))

    (fact "When adding invoice and existing transfer batch is closed, create new transfer batch"
      (mongo/drop-collection tb/transfer-batch-db-key)
      (invoice->confirmed test-invoice)
      (let [tbs_first (get-org-transfer-batches "753-R")]
        (count tbs_first) => 1
        (local-command laura :close-transfer-batch :organizationId "753-R" :transfer-batch-id (:id (first tbs_first)))
        (invoice->confirmed test-invoice)
        (let [tbs_second (get-org-transfer-batches "753-R")]
          (count tbs_second) => 2)))

    (facts "When removing invoice, ie. changing invoice state back to checked from confirmed,
            the invoice is removed from transfer batch and
            transfer batch modified timestamp gets updated and
            history entry added"
      (mongo/drop-collection tb/transfer-batch-db-key)
      (let [invoice-added-ts   1111
            invoice-removed-ts 2222]

        (fact "add first invoice and make it confirmed"
          (let [invoice-1-id (invoice->confirmed test-invoice)
                tb           (get-tb-from-db "753-R")
                tb-tb        (:transfer-batch tb)]
            (:sum tb-tb) => {:currency "EUR" :major 181 :minor 18195 :text (money/->currency-text EUR 181.95)}
            (count (:invoices tb-tb)) => 1
            (:number-of-rows tb-tb) => 3))

        (against-background [(sade/now) => invoice-added-ts]
          (facts "add second invoice and make it confirmed"
            (let [invoice-2-id (invoice->confirmed test-invoice)]
              (fact "check invoice transfer batch"
                (let [tb    (get-tb-from-db "753-R")
                      tb-tb (:transfer-batch tb)]
                  (:sum tb-tb) => {:currency "EUR" :major 363 :minor 36390 :text (money/->currency-text EUR 363.90)}
                  (:number-of-rows tb-tb) => 6
                  (count (:invoices tb-tb)) => 2
                  (:modified tb-tb) => invoice-added-ts))

              (against-background [(sade/now) => invoice-removed-ts]
                (fact "second invoice to checked state"
                  (let [invoice-2-checked (invoice->checked invoice-2-id laura)]
                    (fact "check invoice transfer batch"
                      (let [tb    (get-tb-from-db "753-R")
                            tb-tb (:transfer-batch tb)]
                        (:sum tb-tb) => {:currency "EUR" :major 181 :minor 18195 :text (money/->currency-text EUR 181.95)}
                        (:number-of-rows tb-tb) => 3
                        (count (:invoices tb-tb)) => 1
                        (:modified tb-tb) => invoice-removed-ts
                        (last (:history tb-tb)) => {:user   {:id        "laura-laskuttaja"
                                                             :firstName "Laura"
                                                             :lastName  "Laskuttaja"
                                                             :role      "authority"
                                                             :username  "laura"}
                                                    :time   invoice-removed-ts
                                                    :action (str "Remove invoice " invoice-2-checked)
                                                    :state  "open"}))))))))))



    (facts "organizations-transferbatches"
      (fact "finds all transfer batches for org when given no extra filters"
        (mongo/drop-collection tb/transfer-batch-db-key)
        (create-new-tb! "753-R" laura-user {:created (date/timestamp "1.1.2000")})
        (create-new-tb! "753-R" laura-user {:created (date/timestamp "1.2.2000")})

        (let [tbs-by-org (:transfer-batches (local-query laura :organizations-transferbatches :organizationId "753-R"))
              tb-list    (-> tbs-by-org vals flatten)]
          (count tb-list) => 2
          (map (fn [x] (get-in x [:transfer-batch :created])) tb-list)
          => [(date/timestamp "1.2.2000")
              (date/timestamp "1.1.2000")]))

      (facts "with filters"

        (fact "should fail given invalid states value"
          (local-query laura :organizations-transferbatches :organizationId "753-R" :states ["I_AM_NOT_A_VALID_STATE"])
          => {:ok false :text "error.invalid-request"})

        (fact "should fail given invalid from value"
          (local-query sonja :organizations-transferbatches :organizationId "753-R" :from "I-AM-NOT-A-TIMESTAMP-FOO")
          => {:ok false :text "error.invalid-request"})

        (fact "should fail given invalid until value"
          (local-query sonja :organizations-transferbatches :organizationId "753-R" :until "I-AM-NOT-A-TIMESTAMP-FOO")
          => {:ok false :text "error.invalid-request"})

        (fact "should fail given invalid limit value"
          (local-query sonja :organizations-transferbatches :organizationId "753-R" :limit "I-AM-NOT-AN-INTEGER")
          => {:ok false :text "error.invalid-request"})

        (add-organization "FOO-ORG" true)
        (biller-user laura "FOO-ORG")

        (facts "should return transfer batch that are in the given state"
          (mongo/drop-collection tb/transfer-batch-db-key)

          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.1.2000") :id "open-tb-1" :state "open"})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "2.1.2000") :id "open-tb-2" :state "open"})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "2.2.2000") :id "closed-tb-2" :state "closed"})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.2.2000") :id "closed-tb-1" :state "closed"})

          (let [get-ids-of-tbs-in-states (fn [states]
                                           (let [resp (local-query laura :organizations-transferbatches :organizationId "FOO-ORG" :states (vec states))]
                                             (fact "resp ok"
                                               resp => ok?)
                                             (->> resp
                                                  :transfer-batches
                                                  ->tb-list
                                                  (map :id)
                                                  set)))]
            (fact "open"
              (get-ids-of-tbs-in-states #{"open"}) => #{"open-tb-1" "open-tb-2"})

            (fact "closed"
              (get-ids-of-tbs-in-states #{"closed"}) => #{"closed-tb-1" "closed-tb-2"})

            (fact "open and closed"
              (get-ids-of-tbs-in-states #{"open" "closed"}) => #{"open-tb-1" "open-tb-2"
                                                                 "closed-tb-1" "closed-tb-2"})))

        (fact "should return transfer batches sorted by :created newest first"
          (mongo/drop-collection tb/transfer-batch-db-key)

          ;;Transfer batches not inserted in the same order as :created dictates to test sorting
          (create-new-tb! "FOO-ORG" laura-user {:id "d" :created (date/timestamp "1.8.2000")})
          (create-new-tb! "FOO-ORG" laura-user {:id "b" :created (date/timestamp "1.1.2000")})
          (create-new-tb! "FOO-ORG" laura-user {:id "a" :created (date/timestamp "1.4.2000")})
          (create-new-tb! "FOO-ORG" laura-user {:id "c" :created (date/timestamp "1.3.2000")})

          (let [tb-list (->tb-list (:transfer-batches (local-query laura :organizations-transferbatches :organizationId "FOO-ORG")))]

            (map tb-created tb-list) => [(date/timestamp "1.8.2000")
                                         (date/timestamp "1.4.2000")
                                         (date/timestamp "1.3.2000")
                                         (date/timestamp "1.1.2000")]))

        (fact "should return transfer batches sorted by :created newest first"
          (mongo/drop-collection tb/transfer-batch-db-key)

          ;;Transfer batches not inserted in the same order as :created dictates to test sorting
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.1.2000")})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.4.2000")})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.3.2000")})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.2.2000")})

          (let [tb-list (->tb-list (:transfer-batches (local-query laura :organizations-transferbatches
                                                                   :organizationId "FOO-ORG"
                                                                   :limit 2)))]
            (map tb-created tb-list) => [(date/timestamp "1.4.2000")
                                         (date/timestamp "1.3.2000")]))

        (facts "should return invoices created between from and until"
          (mongo/drop-collection tb/transfer-batch-db-key)
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.1.2000")})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.2.2000")})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.3.2000")})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.4.2000")})

          (fact "until is not given and"

            (fact "from matches exactly a created timestamp in the db. Matching transferbatch should be included."
              (let [tb-list (->tb-list (:transfer-batches (local-query laura :organizations-transferbatches :organizationId "FOO-ORG"
                                                                       :from (date/timestamp "1.2.2000"))))]
                (count tb-list) => 3
                (map tb-created tb-list) => [(date/timestamp "1.4.2000")
                                             (date/timestamp "1.3.2000")
                                             (date/timestamp "1.2.2000")]))

            (fact "from is between created timestamps of transferbatches"
              (let [tb-list (->tb-list (:transfer-batches (local-query laura :organizations-transferbatches :organizationId "FOO-ORG"
                                                                       :from (date/timestamp "10.2.2000"))))]
                (count tb-list) => 2
                (map tb-created tb-list) => [(date/timestamp "1.4.2000")
                                             (date/timestamp "1.3.2000")])))
          (fact "from is not given and"

            (fact "until matches exactly a created timestamp in the db. Matching transfer batch should be included."
              (let [tb-list (->tb-list (:transfer-batches (local-query laura :organizations-transferbatches :organizationId "FOO-ORG"
                                                                       :until (date/timestamp "1.2.2000"))))]
                (count tb-list) => 2
                (map tb-created tb-list) => [(date/timestamp "1.2.2000")
                                             (date/timestamp "1.1.2000")]))

            (fact "until is between created timestamps of invoices"
              (let [tb-list (->tb-list (:transfer-batches (local-query laura :organizations-transferbatches :organizationId "FOO-ORG"
                                                                       :until (date/timestamp "1.7.2000"))))]
                (count tb-list) => 4
                (map tb-created tb-list) => [(date/timestamp "1.4.2000")
                                             (date/timestamp "1.3.2000")
                                             (date/timestamp "1.2.2000")
                                             (date/timestamp "1.1.2000")])))

          (fact "when from, until and limit are strings representing the numbers"
            (let [tb-list (->tb-list (:transfer-batches (local-query laura :organizations-transferbatches :organizationId "FOO-ORG"
                                                                     :from (str (date/timestamp "1.2.2000"))
                                                                     :until (str (date/timestamp "1.4.2000"))
                                                                     :limit "2")))]
              (count tb-list) => 2
              (map tb-created tb-list) => [(date/timestamp "1.4.2000")
                                           (date/timestamp "1.3.2000")])))

        (fact "should apply all filters together"
          (mongo/drop-collection tb/transfer-batch-db-key)

          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.1.1999") :state "open"})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.1.2000") :state "closed"})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.2.2000") :state "open"})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.2.2000") :state "closed"})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.3.2000") :state "open"})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "2.3.2000") :state "closed"})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.4.2000") :state "open"})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "4.3.2000") :state "closed"})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "5.4.2000") :state "open"})
          (create-new-tb! "FOO-ORG" laura-user {:created (date/timestamp "1.8.2000") :state "closed"})

          (let [tb-list (->tb-list (:transfer-batches (local-query laura :organizations-transferbatches :organizationId "FOO-ORG"
                                                                   :states ["open"]
                                                                   :from (date/timestamp "1.2.2000")
                                                                   :until (date/timestamp "1.10.2000")
                                                                   :limit 4)))]
            (count tb-list) => 4
            (map (juxt tb-created tb-state) tb-list) => [[(date/timestamp "5.4.2000") "open"]
                                                         [(date/timestamp "1.4.2000") "open"]
                                                         [(date/timestamp "1.3.2000") "open"]
                                                         [(date/timestamp "1.2.2000") "open"]]))

        (add-organization "BAR-ORG" true)
        (biller-user laura "BAR-ORG")

        (fact "when user has given the exact target organization as a parameter"
          (mongo/drop-collection tb/transfer-batch-db-key)

          (create-new-tb! "FOO-ORG" laura-user {:id "foo-1" :created (date/timestamp "1.4.2000")})
          (create-new-tb! "BAR-ORG" laura-user {:id "bar-1" :created (date/timestamp "1.3.2000")})
          (create-new-tb! "FOO-ORG" laura-user {:id "foo-2" :created (date/timestamp "1.2.2000")})
          (create-new-tb! "BAR-ORG" laura-user {:id "bar-2" :created (date/timestamp "1.1.2000")})

          (let [tb-list (->tb-list (:transfer-batches (local-query laura :organizations-transferbatches :organizationId "FOO-ORG"
                                                                   :limit 2
                                                                   :organization-id "FOO-ORG")))]
            (map (juxt :id tb-created) tb-list) => [["foo-1" (date/timestamp "1.4.2000")]
                                                    ["foo-2" (date/timestamp "1.2.2000")]]))))

    (defn update-tyoaika-document! [application updates]
      (let [maksaja-doc-id (:id (domain/get-document-by-name application "tyoaika"))]
        (local-command pena :update-doc :id (:id application) :doc maksaja-doc-id :updates updates) => ok?))

    (facts "process-sap-idoc-transferbatch-integration"
      (mongo/drop-collection tb/transfer-batch-db-key)
      (mongo/drop-collection :invoices)

      (let [{application-id :id :as app} (create-and-submit-local-application
                                           pena
                                           :operation "ya-katulupa-vesi-ja-viemarityot"
                                           :address "Kaivokatu 1")
            {tb-id :transferbatch-id}    (invoice-with! test-invoice laura {:state          "confirmed"
                                                                            :application-id application-id
                                                                            :sap-number     "123457890"})]

        (update-tyoaika-document! app [["tyoaika-alkaa-ms" 1554066000000] ;;01.4.2019 00:00 GMT+3
                                       ["tyoaika-paattyy-ms" 1556571600000]]) ;;30.4.2019 00:00 GMT+3

        (against-background [(tb/get-delivery) => (->NoopDeliverer)]

          (fact "returns unauthorized when user has does not have the role authority"
            (local-command pena :process-sap-idoc-transfer-batch-integration
                           :transfer-batch-id tb-id)
            => {:ok false :text "error.unauthorized"})

          (fact "returns error when user has the role authority but is not in the organization in which the transfer batch has been created"
            (local-command naantali :process-sap-idoc-transfer-batch-integration
                           :transfer-batch-id tb-id)
            => {:ok false :text "error.unauthorized"})

          (fact "returns ok when user has rights to handle the transfer batch"
            (local-command laura :process-sap-idoc-transfer-batch-integration
                           :transfer-batch-id tb-id)
            => {:ok true :transfer-batch-id tb-id}))


        (fact "returns error when sending SAP XML file to customer SFTP server fails"
          (local-command laura :process-sap-idoc-transfer-batch-integration
                         :transfer-batch-id tb-id)
          => {:ok false :text "error.invoice-delivery-failed"}
          (provided (tb/get-delivery) => (->ExceptionDeliverer)))))



    (facts "process-general-api-transfer-batch-integration"
      (mongo/drop-collection tb/transfer-batch-db-key)
      (mongo/drop-collection :invoices)


      (let [{application-id :id org-id :organization
             :as            app} (create-and-submit-local-application
                                   pena
                                   :operation "ya-katulupa-vesi-ja-viemarityot"
                                   :address "Kaivokatu 1")
            invoicing-config     {:integration-url                       "url"
                                  :invoice-file-prefix                   "file-prefix"
                                  :credentials                           {:username "username" :password "password"}
                                  :integration-requires-customer-number? false
                                  :constants                             {:sektori      "1"
                                                                          :nimike       "2"
                                                                          :jakelutie    "3"
                                                                          :tulosyksikko "4"
                                                                          :laskuttaja   "5"
                                                                          :myyntiorg    "6"
                                                                          :tilauslaji   "7"}
                                  :backend-id?                           true}]
        (facts "Invoicing configuration"
           (fact "Update config"
             (local-command admin :update-invoicing-config
                            :org-id org-id
                            :invoicing-config invoicing-config) => ok?)
           (fact "Admin has full access"
             (:invoicing-config (local-query admin :invoicing-config :organizationId org-id))
             => invoicing-config)
           (facts "Other authorities cannot access backend server details"
             (doseq [apikey [sonja laura sipoo-ya]]
               (fact {:midje/description apikey}
                 (:invoicing-config (local-query apikey :invoicing-config :organizationId org-id))
                 => (dissoc invoicing-config :integration-url :credentials))))
           (fact "Applicant or authorities from other organizations cannot access"
             (doseq [apikey [pena luukas kosti sipoo olli]]
               (fact {:midje/description apikey}
                 (local-query apikey :invoicing-config :organizationId org-id)
                 => unauthorized?))))
        (fact "Configure backend id"
          (local-command sipoo-ya :configure-invoicing-backend-id
                         :organizationId org-id
                         :op {:set {:numbers 4}}) => ok?
          (local-command sipoo-ya :configure-invoicing-backend-id
                         :organizationId org-id
                         :op {:upsert {:code "WAT" :text "What invoice is this?"}}) => ok?)
        (fact "Backend-id configuration is part of invoicing-config response"
          (local-query laura :invoicing-config :organizationId org-id)
          => (contains {:invoicing-config  (dissoc invoicing-config :integration-url :credentials)
                        :backend-id-config (just {:numbers 4
                                                  :codes   (just [(just {:code "WAT"
                                                                         :id   truthy
                                                                         :text "What invoice is this?"})])})}))
        (let [maksaja-doc-id       (:id (first (domain/get-documents-by-subtype (:documents app) :maksaja)))
              {tb-id :transferbatch-id
               bid   :backend-id} (invoice-with! test-invoice laura {:state          "confirmed"
                                                                        :backend-code   "WAT"
                                                                        :application-id application-id
                                                                        :sap-number     "123457890"})]
          (fact "Backend-id has been generated"
            bid => "WAT0001")

          (let [updates [["_selected" "yritys"]
                         ["laskuviite" "laskuviite-pena-yritys"]
                         ["yritys.companyId" "esimerkki"]
                         ["yritys.yritysnimi" "Feikki Oy"]
                         ["yritys.liikeJaYhteisoTunnus" "0813000-2"]
                         ["yritys.osoite.katu" "Lumekatu 5"]
                         ["yritys.osoite.postinumero" "12345"]
                         ["yritys.osoite.postitoimipaikannimi" "Huijala"]
                         ["yritys.osoite.maa" "FIN"]
                         ["yritys.yhteyshenkilo.henkilotiedot.etunimi" "Simo"]
                         ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" "Simonen"]
                         ["yritys.verkkolaskutustieto.verkkolaskuTunnus" "samplebilling"]
                         ["yritys.verkkolaskutustieto.ovtTunnus" "003708130002"]
                         ["yritys.verkkolaskutustieto.valittajaTunnus" "BAWCFI22"]]]
           (update-document! pena application-id maksaja-doc-id updates true))

         (against-background [(tb/send-xml-to-invoicing-system-via-http! anything anything) => nil]

                             (fact "returns unauthorized when user has does not have the role authority"
                               (local-command pena :process-general-api-transfer-batch-integration
                                              :transfer-batch-id tb-id)
                               => {:ok false :text "error.unauthorized"})

                             (fact "returns error when user has the role authority but is not in the organization in which the transfer batch has been created"
                               (local-command naantali :process-general-api-transfer-batch-integration
                                              :transfer-batch-id tb-id)
                               => {:ok false :text "error.unauthorized"})

                             (fact "returns ok when user has rights to handle the transfer batch"
                               (local-command laura :process-general-api-transfer-batch-integration
                                              :transfer-batch-id tb-id)
                               => {:ok true :transfer-batch-id tb-id}))


         (fact "returns error when sending XML file via http to customer invoicing system fails"
           (local-command laura :process-general-api-transfer-batch-integration
                          :transfer-batch-id tb-id)
           => {:ok false :text "error.invoice-delivery-failed"}
           (provided
             (tb/send-xml-to-invoicing-system-via-http! irrelevant irrelevant)
             =throws=> (Exception. "Miserable failure") :times 1))

         (fact "returns error when sending invoicing XML file via sftp to customer invoicing system fails"
           (let [invoicing-config (-> invoicing-config
                                      (assoc :integration-url "sftp://url/polku")
                                      (assoc-in [:credentials :password] ""))]
             (local-command admin :update-invoicing-config
                            :org-id (:organization app)
                            :invoicing-config invoicing-config) => ok?
             (local-command laura :process-general-api-transfer-batch-integration
                            :transfer-batch-id tb-id)
             => {:ok false :text "error.invoice-delivery-failed"}
             (provided
               (sftp/upload-file! irrelevant irrelevant irrelevant irrelevant)
               =throws=> (Exception. "Miserable failure in sftp sending") :times 1)))

         (facts "Local SFTP"
           (let [org-id (:organization app)]
             (fact "Enable local SFTP for Sipoo-YA"
               org-id => "753-YA"
               (local-command admin :update-invoicing-config
                              :org-id (:organization app)
                              :invoicing-config (assoc invoicing-config :local-sftp? true))
               => ok?)
             (fact "The transferbatch is now stored locally"
               (local-command laura :process-general-api-transfer-batch-integration
                              :transfer-batch-id tb-id)
               => ok?
               (provided
                 (lupapalvelu.sftp.core/write-invoicing-file "753-YA" anything anything)
                 => true :times 1))
             (fact "Remove ftpUser -> write fails"
               (mongo/update-by-id :organizations "753-YA" {$set {:krysp.YA.ftpUser "  "}})
               (local-command laura :process-general-api-transfer-batch-integration
                              :transfer-batch-id tb-id)
               => (partial expected-failure? "error.invoice-delivery-failed" ))))

         (facts "Download transfer batch xml"
           (fact "Missing batch"
             (local-raw laura :download-transfer-batch-xml :transfer-batch-id "bad-id")
             => {:status 404})
           (fact "User not biller"
             (local-raw sonja :download-transfer-batch-xml :transfer-batch-id tb-id)
             => unauthorized?)
           (fact "User not biller in the transfer-batch organization"
             (local-raw vantaa-r-biller :download-transfer-batch-xml :transfer-batch-id tb-id)
             => {:status 401}
             )
           (fact "Downloading not enabled"
             (local-raw laura :download-transfer-batch-xml :transfer-batch-id tb-id)
             => {:status 403})
           (fact "Downloading enabled. Success"
             (mongo/update-by-id :organizations (:organization app) {$set {:invoicing-config.download? true}})
             (local-raw laura :download-transfer-batch-xml :transfer-batch-id tb-id)
             => (just {:status  200
                       :headers (contains {"Content-Type"        "text/xml"
                                           "Content-Disposition" (format "attachment;filename=\"%s.xml\""
                                                                         tb-id)})
                       :body    truthy}))
           (fact "No invoices. Failure"
             (mongo/update-by-id :invoice-transfer-batches tb-id {$set {:invoices []}})
             (local-raw laura :download-transfer-batch-xml :transfer-batch-id tb-id)
             => (contains {:status 500
                           :body   (contains "XML-sanoman luonti ei onnistunut.")}))))))))
