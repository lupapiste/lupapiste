(ns lupapalvelu.invoices-itest
  (:require [lupapalvelu.invoices.test-util :as it]
            [lupapalvelu.itest-util :refer [command create-and-submit-application err fail? ok? query raktark-helsinki
                                            schema-error? sipoo-property-id with-local-actions with-test-system]]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer [$set $unset]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(def runeberg (date/timestamp "5.2.2020"))
(def kalevala (date/timestamp "28.2.2020"))
(def good     (date/timestamp "10.4.2020"))
(def mayday   (date/timestamp "1.5.2020"))

(def invoicing-disabled? (err :error.invoicing-disabled))

(defn row-ok?
  ([m]
   (just (merge {:ok true
                 :in (just {:modified pos?})}
                m)))
  ([] (row-ok? nil)))

(defn row-err? [k v]
  {:ok        true
   :row-error {k v}})

(with-test-system {:fixture            "minimal"
                   :schema-validation? false}
  (with-local-actions

    (fact "Invoicing not enabled for 753-R"
      (command it/biller :biller-invoices
               :organizationId "753-R"
               :states ["checked"]) => invoicing-disabled?
      (query it/municipal-authority :organization-price-catalogues
             :organizationId "753-R") => invoicing-disabled?
      (command it/biller :biller-transfer-batches
               :organizationId "753-R"
               :states ["open"]) => invoicing-disabled?)

    (let [{app-id :id
           :as    app} (it/dummy-submitted-application it/applicant-2)
          pientalo-id  (-> app :primaryOperation :id)]

      (fact "Invoicing not yet enabled"
        (query it/reader :invoices-tab :id app-id)
        => invoicing-disabled?
        (command it/authority :new-invoice-draft
                 :id app-id)
        => invoicing-disabled?)

      (fact "Enable invoicing for 753-R"
        (it/toggle-invoicing true) => ok?)

      (facts "New invoice"
        (fact "No price catalogue"
          (let [invoice (:invoice (command it/authority :new-invoice-draft :id app-id))]
            (fact "No operations/rows"
              (:price-catalogue-id invoice) => nil
              (:state invoice) => "draft"
              (:operations invoice) => empty?
              (:rows invoice) => empty?)
            (fact "Delete invoice"
              (mongo/by-id :invoices (:id invoice)) => truthy
              (command it/authority :delete-invoice :id app-id
                       :invoice-id (:id invoice)) => ok?
              (mongo/by-id :invoices (:id invoice)) => nil)))

        (facts "The most suitable price catalogue is selected"
          (let [past-id              (it/new-catalogue -1 true)
                current-id           (it/insert-catalog {:from   (date/timestamp (date/today))
                                                         :until  (date/timestamp (date/end-of-day (date/now)))
                                                         :prefix "one"})
                future-id            (it/new-catalogue 1 true)

                {invoice-id :id
                 :as        invoice} (:invoice (command it/authority :new-invoice-draft :id app-id))
                fetch-catalogue-id   #(:price-catalogue-id (mongo/by-id :invoices invoice-id))]
            (fact "No exact match, the latest published is selected"
              (:price-catalogue-id invoice) => future-id)
            (fact "Catalogue selection can be cleared"
              (command it/authority :set-invoice-field :id app-id
                       :invoice-id invoice-id
                       :field :price-catalogue-id
                       :value "")
              => (just {:ok  true
                        :in  (just {:modified pos?})
                        :out [:price-catalogue-id]})
              (fetch-catalogue-id) => nil)
            (fact "Draft catalogue cannot be selected"
              (command it/authority :set-invoice-field :id app-id
                       :invoice-id invoice-id
                       :field :price-catalogue-id
                       :value current-id)
              => (err :error.not-found))
            (fact "Unknown catalogue cannot be selected"
              (command it/authority :set-invoice-field :id app-id
                       :invoice-id invoice-id
                       :field :price-catalogue-id
                       :value "I don't exist")
              => (err :error.not-found))
            (fact "Catalogue for the different organization cannot be selected"
              (mongo/update-by-id :price-catalogues future-id
                                  {$set {:organization-id "186-R"}})
              (command it/authority :set-invoice-field :id app-id
                       :invoice-id invoice-id
                       :field :price-catalogue-id
                       :value future-id)
              => (err :error.not-found))
            (fact "Catalogue selected"
              (command it/authority :set-invoice-field :id app-id
                       :invoice-id invoice-id
                       :field :price-catalogue-id
                       :value past-id)
              => (just {:ok true
                        :in (just {:modified pos?})})
              (fetch-catalogue-id) => past-id)
            (fact "Exact match"
              (command it/municipal-authority :publish-price-catalogue
                       :price-catalogue-id current-id
                       :organizationId "753-R") => ok?
              (let [invoice (:invoice (command it/authority :new-invoice-draft :id app-id))]
                (fact "invoice matches"
                  invoice => (just {:application-id     app-id
                                    :application-payer? true
                                    :created            pos?
                                    :created-by         it/authority-user
                                    :description        app-id
                                    :id                 truthy
                                    :modified           pos?
                                    :modified-by        it/authority-user
                                    :operations         []
                                    :organization-id    "753-R"
                                    :price-catalogue-id current-id
                                    :rows               []
                                    :state              "draft"})))))))

      (let [invoice-id (-> (command it/authority-2 :new-invoice-draft :id app-id)
                           :invoice :id)
            cmd        (fn [cmd-name & args]
                         (apply command it/authority cmd-name
                                (concat [:id app-id :invoice-id invoice-id]
                                        args)))
            fetch      #(mongo/by-id :invoices invoice-id)
            fetch-row  #(->> (fetch) :rows (util/find-by-id %))
            ts         (date/timestamp (date/now))]
        (facts "Add operation to invoice"
          (fact "Unknown operation"
            (cmd :add-invoice-operation :operation-id "bad")
            => (err :error.operation-not-found))
          (fact "operation cannot be added if row limit exceeded"
            (let [old-config (it/query-invoicing-config-ok)]
              (it/merge-to-invoicing-config-ok {:max-invoice-row-count-in-transfer-batch 1})
              (fact "too many rows"
                (cmd :add-invoice-operation :operation-id pientalo-id)
                => (err :error.invoice.too-many-rows))
              (it/set-invoicing-config-ok {:invoicing-config old-config})))
          (fact "Success: operation with the catalogue products"
            (cmd :add-invoice-operation :operation-id pientalo-id)
            => (just {:ok        true
                      :modified  (partial < ts)
                      :operation {:id pientalo-id :name "pientalo"}
                      :rows      (just
                                   (just {:code           "one-0"
                                          :id             ss/not-blank?
                                          :max-unit-price 10
                                          :min-unit-price 2
                                          :operation-id   pientalo-id
                                          :price-per-unit 5
                                          :text           "one pientalo 0"
                                          :unit           "kpl"})
                                   (just {:code             "one-1"
                                          :discount-percent 10
                                          :id               ss/not-blank?
                                          :max-unit-price   11
                                          :min-unit-price   3
                                          :operation-id     pientalo-id
                                          :price-per-unit   6
                                          :text             "one pientalo purkaminen 1"
                                          :unit             "kpl"}))}))
          (fact "Operation cannot be added twice"
            (cmd :add-invoice-operation :operation-id pientalo-id)
            => (err :error.invalid-operation)))
        (let [invoice           (fetch)
              [row-id1 row-id2] (map :id (:rows invoice))]
          (fact "Invoice in mongo"
            invoice => (just {:application-id     app-id
                              :application-payer? true
                              :created            (partial > ts)
                              :created-by         it/authority-2-user
                              :description        app-id
                              :id                 invoice-id
                              :modified           (partial < ts)
                              :modified-by        it/authority-user
                              :operations         [{:id pientalo-id :name "pientalo"}]
                              :organization-id    "753-R"
                              :price-catalogue-id ss/not-blank?
                              :rows               [{:code           "one-0"
                                                    :id             row-id1
                                                    :max-unit-price 10
                                                    :min-unit-price 2
                                                    :operation-id   pientalo-id
                                                    :price-per-unit 5
                                                    :text           "one pientalo 0"
                                                    :unit           "kpl"}
                                                   {:code             "one-1"
                                                    :discount-percent 10
                                                    :id               row-id2
                                                    :max-unit-price   11
                                                    :min-unit-price   3
                                                    :operation-id     pientalo-id
                                                    :price-per-unit   6
                                                    :text             "one pientalo purkaminen 1"
                                                    :unit             "kpl"}]
                              :state              "draft"}))
          (fact "Deleting a non-existing row is a no-op (fails silently)"
            (cmd :delete-invoice-row :row-id "nowhere") => {:ok true})
          (fact "Delete an existing row"
            (cmd :delete-invoice-row :row-id row-id1)
            => (just {:modified pos? :ok true})
            (->> (fetch) :rows (map :id)) => [row-id2])
          (facts "Add bad row (not in catalogue)"
            (cmd :add-invoice-row :row-id "bad" :operation-id "bad-op")
            => (err :error.operation-not-found)
            (cmd :add-invoice-row :row-id "bad" :operation-id pientalo-id)
            => (err :error.not-found))
          (fact "Add good row from catalogue"
            (cmd :add-invoice-row :row-id it/fixed-id :operation-id pientalo-id)
            => (just {:modified pos?
                      :ok       true
                      :row      (just {:code           "fixed"
                                       :id             #(and (ss/not-blank? %) (not= % it/fixed-id))
                                       :operation-id   pientalo-id
                                       :price-per-unit 5
                                       :text           "Fixed price row"
                                       :unit           "m2"})}))
          (fact "Add custom row"
            (cmd :add-invoice-row :row-id "custom" :operation-id pientalo-id)
            => (just {:modified pos?
                      :ok       true
                      :row      (just {:custom?      true
                                       :id           ss/not-blank?
                                       :operation-id pientalo-id
                                       :unit         "kpl"})}))
          (let [[row-id fixed-id custom-id
                 :as rows] (->> (fetch) :rows (map :id))]
            (facts "Editing rows"
              (count rows) => 3
              (fact "Bad row"
                (cmd :set-invoice-row-field :row-id "bad"
                     :path [:units]
                     :value "1")
                => (err :error.not-found ))
              (fact "Bad field"
                (cmd :set-invoice-row-field :row-id row-id
                     :path [:bad]
                     :value "1")
                => (err :error.unsupported-field-or-value)
                (cmd :set-invoice-row-field :row-id row-id
                     :path [:product-constants :bad]
                     :value 1)
                => (err :error.unsupported-field-or-value))
              (fact "Bad value"
                (cmd :set-invoice-row-field :row-id row-id
                     :path [:units]
                     :value 10)
                => (err :error.unsupported-field-or-value)
                (cmd :set-invoice-row-field :row-id row-id
                     :path [:product-constants :toiminto]
                     :value 20)
                => (err :error.unsupported-field-or-value))
              (fact "Text and unit can only be edited for custom rows"
                (cmd :set-invoice-row-field :row-id row-id
                     :path [:text]
                     :value "Hello")
                => (row-err? :text :error.read-only)
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:unit]
                     :value "m2")
                => (row-err? :unit :error.read-only)
                (cmd :set-invoice-row-field :row-id custom-id
                     :path [:text]
                     :value " Hello ")
                => (row-ok? {:row-in {:text "Hello"}})
                (:text (fetch-row custom-id)) => "Hello"
                (cmd :set-invoice-row-field :row-id custom-id
                     :path [:unit]
                     :value "")
                => (err :error.unsupported-field-or-value)
                (cmd :set-invoice-row-field :row-id custom-id
                     :path [:unit]
                     :value " m3 ")
                => (err :error.unsupported-field-or-value)
                (cmd :set-invoice-row-field :row-id custom-id
                     :path [:unit]
                     :value "m3")
                => (row-ok? {:row-in {:unit "m3"}}))

              (fact "Units"
                ;; Price-per-unit for fixed-id row is 5
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value "10")
                => (row-ok? {:row-in {:total-cents 5000 :units 10}})
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value " ")
                => (row-ok? {:row-in  {:total-cents 0}
                             :row-out [:units]})
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value "0.0")
                => (row-ok? {:row-in {:total-cents 0
                                      :units       0.0}})
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value "-2")
                => (row-ok? {:row-in {:total-cents -1000 :units -2}})
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value "3.3333333")
                => (row-ok? {:row-in {:total-cents 1665 :units 3.33}})
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value " 3,445000 ")
                => (row-ok? {:row-in {:total-cents 1725 :units 3.45}})
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value " 3bad ")
                => (row-err? :units :error.invalid-value))

              (facts "Price per unit"
                (fact "Cannot change fixed"
                  (cmd :set-invoice-row-field :row-id fixed-id
                       :path [:price-per-unit]
                       :value "30")
                  => (row-err? :price-per-unit :error.read-only))
                (fact "Price range from a catalogue: [3, 11]"
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:price-per-unit]
                       :value "")
                  => (row-ok? {:row-in  {:total-cents 0}
                               :row-out [:price-per-unit]})
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:price-per-unit]
                       :value "0")
                  => (row-err? :price-per-unit [:invoice.error.min-unit-price "3,00 €"])
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:price-per-unit]
                       :value "2.995")
                  => (row-ok? {:row-in {:price-per-unit 3.0
                                        :total-cents    0}})
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:units]
                       :value "4")
                  (row-ok? {:row-in {:units       4
                                     :total-cents 1200}})
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:price-per-unit]
                       :value " 11,00444 ")
                  => (row-ok? {:row-in {:price-per-unit 11.0
                                        :total-cents    3960}})
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:discount-percent]
                       :value " ")
                  => (row-ok? {:row-in  {:total-cents 4400}
                               :row-out [:discount-percent]})
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:price-per-unit]
                       :value " 11,00544 ")
                  => (row-err? :price-per-unit [:invoice.error.max-unit-price "11,00 €"])
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:price-per-unit]
                       :value "0x07")
                  => (row-err? :price-per-unit :error.invalid-value)
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:min-unit-price]
                       :value "0")
                  => (err :error.unsupported-field-or-value)
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:min-unit-price]
                       :value 0)
                  => (err :error.unsupported-field-or-value)
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:max-unit-price]
                       :value "100")
                  => (err :error.unsupported-field-or-value)
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:max-unit-price]
                       :value 100)
                  => (err :error.unsupported-field-or-value))

                (fact "Custom row"
                  (cmd :set-invoice-row-field :row-id custom-id
                       :path [:price-per-unit]
                       :value " -20 ")
                  => (row-ok? {:row-in {:total-cents    0
                                        :price-per-unit -20}})
                  (cmd :set-invoice-row-field :row-id custom-id
                       :path [:units]
                       :value " 1 ")
                  => (row-ok? {:row-in {:total-cents -2000
                                        :units       1}})
                  (cmd :set-invoice-row-field :row-id custom-id
                       :path [:units]
                       :value " -1 ")
                  => (row-ok? {:row-in {:total-cents 2000
                                        :units       -1}})))


              (let [p-cmd  #(cmd :set-invoice-row-field :row-id row-id
                                 :path [:discount-percent]
                                 :value %)
                    p-err? (row-err? :discount-percent
                                     :invoice.error.percentage)
                    p-ok?  #(row-ok? {:row-in {:discount-percent %1
                                               :total-cents      %2}})]
                (facts "Discount percent"
                  (fetch-row row-id) => (contains {:total-cents    4400
                                                   :price-per-unit 11.0
                                                   :units          4})
                  (facts "Percentage range is [0, 100]"
                    (p-cmd "-0.005") => p-err?
                    (p-cmd "100.005") => p-err?
                    (p-cmd "bad") => (row-err? :discount-percent :error.invalid-value))

                  (facts "0 vs. empty"
                    (p-cmd "0") => (p-ok? 0 4400)
                    (p-cmd " ") => (row-ok? {:row-in  {:total-cents 4400}
                                             :row-out [:discount-percent]}))

                  (facts "With % sign"
                    (p-cmd " 25% ") => (p-ok? 25 3300)
                    (p-cmd " 25 % ") => (p-ok? 25 3300)
                    (p-cmd "%25") => (p-ok? 25 3300)
                    (p-cmd "%%25%%") => (p-ok? 25 3300)
                    (p-cmd "25 % %") => (row-err? :discount-percent :error.invalid-value))

                  (facts "Percentage calculation"
                    (p-cmd " 25 ") => (p-ok? 25 3300)
                    (p-cmd " 12,345000 ") => (p-ok? 12.35 3857)
                    (p-cmd " 33.33333 %") => (p-ok? 33.33 2933)
                    (p-cmd "% 100.00444") => (p-ok? 100.0 0)
                    (p-cmd "100") => (p-ok? 100 0)
                    (p-cmd "0.004") => (p-ok? 0.0 4400)
                    (p-cmd "1") => (p-ok? 1 4356)
                    (p-cmd "0.1 %") => (p-ok? 0.1 4396)
                    (p-cmd "0.05") => (p-ok? 0.05 4398)
                    (p-cmd "0.02") => (p-ok? 0.02 4399)
                    (p-cmd "0.01") => (p-ok? 0.01 4400)
                    (p-cmd "0.005") => (p-ok? 0.01 4400)
                    (p-cmd "0.0049") => (p-ok? 0.0 4400)))))

            (facts "Comment"
              (cmd :set-invoice-row-field :row-id fixed-id
                   :path [:comment]
                   :value "  Bonus ")
              => (row-ok? {:row-in {:comment "Bonus"}})
              (cmd :set-invoice-row-field :row-id fixed-id
                   :path [:comment]
                   :value "  ")
              => (row-ok? {:row-in {:comment ""}}))

            (facts "Product constants"
              (doseq [c    [:kustannuspaikka :alv :laskentatunniste
                            :projekti :kohde :toiminto :muu-tunniste]
                      :let [pkw (util/kw-path :product-constants c)]]
                (fact {:midje/description (name c)}
                  (cmd :set-invoice-row-field :row-id fixed-id
                       :path [:product-constants c]
                       :value "  Hello ")
                  => (row-ok? {:row-in {pkw "Hello"}})
                  (cmd :set-invoice-row-field :row-id fixed-id
                       :path [:product-constants c]
                       :value "   ")
                  => (row-ok? {:row-in {pkw ""}})))
              (cmd :set-invoice-row-field :row-id fixed-id
                   :path [:product-constants :bad]
                   :value "  Boom! ")
              => (err :error.unsupported-field-or-value))))

        (facts "Add new operation"
          (fact "... to application"
            (command it/applicant-2 :add-operation :id app-id
                     :operation "aita") => ok?)
          (let [aita-id (-> (mongo/by-id :applications app-id)
                            :secondaryOperations first :id)]
            (fact "... and invoice"
              (let [body (cmd :add-invoice-operation
                              :operation-id aita-id)]
                (fact "body"
                  body => (just {:modified  pos?
                                 :ok        true
                                 :operation {:id aita-id :name "aita"}
                                 :rows      (just
                                              (just {:id             #(and (ss/not-blank? %)
                                                                           (not= % it/fixed-id))
                                                     :operation-id   aita-id
                                                     :code           "fixed"
                                                     :text           "Fixed price row"
                                                     :unit           "m2"
                                                     :price-per-unit 5}))})
                  (fetch) => (contains {:modified   (:modified body)
                                        :operations [{:id pientalo-id :name "pientalo"}
                                                     {:id aita-id :name "aita"}]
                                        :rows       (just (contains {:operation-id pientalo-id})
                                                          (contains {:operation-id pientalo-id})
                                                          (contains {:operation-id pientalo-id})
                                                          (contains {:id           (-> body :rows first :id)
                                                                     :operation-id aita-id}))} )))
              (fact "Delete non-existing invoice operation"
                (let [old (fetch)]
                  (fact "response"
                    (cmd :delete-invoice-operation :operation-id "bad-id") => ok?
                    (fetch) => old)
                  (fact "Delete pientalo"
                    (let [body (cmd :delete-invoice-operation :operation-id pientalo-id)]
                      (fact "response"
                        body => (just {:ok       true
                                       :modified #(> % (:modified old))})
                        (fetch) => (contains {:modified   (:modified body)
                                              :operations [{:id aita-id :name "aita"}]
                                              :rows       (just (contains {:operation-id aita-id}))})))))))))

        (let [f-cmd  #(cmd :set-invoice-field :field %1 :value %2)
              oi-cmd #(f-cmd :organization-internal-invoice? %)
              f-ok?  (just {:in (just {:modified pos?}) :ok true})
              f-err? (err :error.unsupported-field-or-value)
              oi-get #(:organization-internal-invoice? (fetch))]
          (facts "Internal invoice"
            (oi-cmd true) => f-ok?
            (oi-get) => true
            (oi-cmd false) => f-ok?
            (oi-get) => false
            (oi-cmd "bad") => f-err?
            (oi-cmd "") => f-err?
            (oi-cmd nil) => f-err?)
          (fact "Bad field"
            (f-cmd :foobar "bad") => f-err?)
          (fact "Biller field"
            (f-cmd :payer-type "person") => f-err?)

          (facts "Backend id"
            (facts "Configuration"
              (fact "Backend-id not enabled"
                (query it/municipal-authority :invoicing-config :organizationId "753-R")
                => {:ok true :invoicing-config {}})
              (fact "Backend-id cannot be configured"
                (command it/municipal-authority :configure-invoicing-backend-id
                         :organizationId "753-R"
                         :op {:set {:numbers 8}})
                => (err :error.backend-id-not-enabled))
              (fact "Backend code cannot be added to the invoice"
                (command it/authority :set-invoice-field
                         :id app-id
                         :invoice-id invoice-id
                         :field :backend-code
                         :value "foo")
                => (err :error.unknown-backend-code))
              (fact "Configure invoicing and enable backend-id"
                (command it/admin :update-invoicing-config
                         :org-id "753-R"
                         :invoicing-config {:download?   true
                                            :local-sftp? false
                                            :backend-id? true}) => ok?)
              (fact "Now backend-id can be configured"
                (command it/municipal-authority :configure-invoicing-backend-id
                         :organizationId "753-R"
                         :op {:set {:numbers 8}}) => ok?)
              (fact "Numbers must be integers in range [1, 10] inclusive"
                (doseq [n [-1 "foo" 0 4.56 11]]
                  (fact {:midje/description n}
                    (command it/municipal-authority :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:set {:numbers n}})
                    => schema-error?)))
              (fact "Upsert: Code and text must be given"
                (doseq [upsert [{} nil {:code "foo"} {:text "bar"}
                                {:code 1 :text 2}  {:code "" :text ""}
                                {:code "Foo" :text "bar" :bad 1}]]
                  (fact "error"
                    (command it/municipal-authority :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:upsert upsert})
                    => schema-error?)))
              (fact "Upsert: Initial addition of code"
                (let [cfg    (:config (command it/municipal-authority :configure-invoicing-backend-id
                                               :organizationId "753-R"
                                               :op {:upsert {:code " foo "
                                                             :text " Hello world! "}}))
                      foo-id (-> cfg :codes first :id)]
                  (fact "response"
                    foo-id => truthy
                    cfg => {:numbers 8
                            :codes   [{:code "foo" :text "Hello world!" :id foo-id}]})
                  (fact "Add another code"
                    (command it/municipal-authority :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:upsert {:code " bar "
                                           :text " Ni hao! "}}) => ok?)
                  (fact "Id must match if given"
                    (command it/municipal-authority :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:upsert {:code "dum"
                                           :id   (mongo/create-id)
                                           :text "Bonjour!"}})
                    => (err :error.id-not-found)
                    (:config (command it/municipal-authority :configure-invoicing-backend-id
                                      :organizationId "753-R"
                                      :op {:upsert {:code " dum "
                                                    :id   foo-id
                                                    :text " Bonjour! "}}))
                    => (just {:numbers 8
                              :codes   (just {:code "dum"
                                              :id   foo-id
                                              :text "Bonjour!"}
                                             (just {:id   truthy
                                                    :code "bar"
                                                    :text "Ni hao!"}))}))
                  (fact "Code must be unique"
                    (command it/municipal-authority :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:upsert {:code " bar "
                                           :id   foo-id
                                           :text " Reserved! "}})
                    => (err :error.code-reserved)
                    (command it/municipal-authority :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:upsert {:code " dum      "
                                           :text " Reserved! "}})
                    => (err :error.code-reserved))
                  (fact "Delete: code id must exist"
                    (doseq [id ["bad-id" nil "  " ""]]
                      (fact "response"
                        (command it/municipal-authority :configure-invoicing-backend-id
                                 :organizationId "753-R"
                                 :op {:delete {:id id}})
                        => schema-error?)
                      (fact "If code is not found, delete just returns the current config"
                        (:config (command it/municipal-authority :configure-invoicing-backend-id
                                          :organizationId "753-R"
                                          :op {:delete {:id (mongo/create-id)}}))
                        => (just {:numbers 8
                                  :codes   (just {:code "dum"
                                                  :id   foo-id
                                                  :text "Bonjour!"}
                                                 (just {:id   truthy
                                                        :code "bar"
                                                        :text "Ni hao!"}))}))))
                  (fact "Delete: success"
                    (:config (command it/municipal-authority :configure-invoicing-backend-id
                                      :organizationId "753-R"
                                      :op {:delete {:id foo-id}}))
                    => (just {:numbers 8
                              :codes   (just [(just {:id   truthy
                                                     :code "bar"
                                                     :text "Ni hao!"})])})))))
            (let [set-code (partial f-cmd :backend-code)]
              (facts "Set backend code to invoice"
                (fact "Code must exist"
                  (set-code "bad") => (err :error.unknown-backend-code))
                (fact "Application must exist"
                  (command it/authority :set-invoice-field
                           :id "bad-id"
                           :invoice-id invoice-id
                           :field :backend-code
                           :value "bad")
                  => fail?)
                (fact "Invoice must exist"
                  (command it/authority :set-invoice-field
                           :id app-id
                           :invoice-id "bad-id"
                           :field :backend-code
                           :value "bad")
                  => fail?)
                (fact "Backend-id cannot be set"
                  (command it/authority :set-invoice-field
                           :id app-id
                           :invoice-id invoice-id
                           :field :backend-id
                           :value "BAR000123")
                  => fail?)
                (fact "Success"
                  (set-code "bar") => ok?
                  (:backend-code (fetch)) => "bar")
                (fact "Code can be cleared"
                  (set-code "  ") => (just {:in  (just {:modified pos?})
                                            :ok  true
                                            :out [:backend-code]})
                  (:backend-code (fetch)) => nil)
                (fact "Code cannot be set, if backend id has been set"
                  (mongo/update-by-id :invoices invoice-id {$set {:backend-id "XY000123"}})
                  (set-code "bar")
                  => (err :error.backend-id-already-defined)
                  (mongo/update-by-id :invoices invoice-id {$unset {:backend-id true}}))
                (facts "Nothing works if invoicing is not enabled"
                  (command it/admin :update-organization
                           :invoicingEnabled false
                           :municipality "753"
                           :permitType "R") => ok?
                  (fact "Backend-id cannot be configured"
                    (command it/municipal-authority :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:set {:numbers 8}})
                    => invoicing-disabled?)
                  (fact "Backend code cannot be added to the invoice"
                    (set-code "bar") => invoicing-disabled?))))
            (fact "Enable invoicing and set backend code"
              (command it/admin :update-organization
                       :invoicingEnabled true
                       :municipality "753"
                       :permitType "R")
              => ok?
              (let [{app-id2 :id} (it/dummy-submitted-application it/applicant-2)
                    invoice-id2   (-> (command it/authority-2 :new-invoice-draft :id app-id2)
                                      :invoice :id)]
                (fact "Invoice must match the application"
                  (command it/authority :set-invoice-field
                           :id app-id
                           :invoice-id invoice-id2
                           :field :backend-code
                           :value "bar")
                  => (err :error.invalid-invoice))
                (fact "Available codes"
                  (:codes (query it/biller :invoicing-backend-id-codes
                                 :id app-id))
                  => [{:code "bar" :text "Ni hao!" }])
                (fact "Set backend code successfully"
                  (command it/authority :set-invoice-field
                           :id app-id2
                           :invoice-id invoice-id2
                           :field :backend-code
                           :value "bar")
                  => ok?
                  (mongo/any? :invoices {:_id invoice-id2 :backend-code "bar"})
                  => true))))))

      (facts "Invoice worktime"
        (fact "Worktime cannot be set for non-YA application invoices"
          (let [invoice-id (-> (command it/authority :new-invoice-draft :id app-id)
                               :invoice :id)]
            (fact "response"
              (command it/authority :update-invoice-worktime :id app-id
                       :invoice-id invoice-id
                       :start-ts runeberg
                       :end-ts kalevala)
              => {:ok          false
                  :permit-type ["YA"]
                  :text        "error.invalid-permit-type"})))
        (fact "Worktime is enriched with application `started` and `closed` timestamps"
          (it/toggle-invoicing true "YA") => ok?
          (let [{ya-app-id :id} (create-and-submit-application it/applicant-2
                                                               :operation "ya-katulupa-vesi-ja-viemarityot"
                                                               :propertyId sipoo-property-id)
                invoice-id      (-> (command it/authority :new-invoice-draft :id ya-app-id)
                                    :invoice :id)]
            (mongo/update-by-id :applications ya-app-id {$set {:started runeberg
                                                               :closed  kalevala}})
            (fact "invoices"
              (:invoices (query it/authority :application-invoices :id ya-app-id))
              => (just (contains {:id            invoice-id
                                  :work-start-ms runeberg
                                  :work-end-ms   kalevala
                                  :workdays      {:billable-days 24 :days 24 :free-days 0}})))

            (fact "Update invoice worktime"
              (:workdays (command it/authority :update-invoice-worktime :id ya-app-id
                                  :invoice-id invoice-id
                                  :start-ts good
                                  :end-ts mayday))
              => {:billable-days 22 :days 22 :free-days 0})

            (fact "Start and end time can be on the same day"
              (:workdays (command it/authority :update-invoice-worktime :id ya-app-id
                                  :invoice-id invoice-id
                                  :start-ts (+ good 10000)
                                  :end-ts good))
              => {:billable-days 1 :days 1 :free-days 0})

            (fact "Bad params"
              (command it/authority :update-invoice-worktime :id ya-app-id
                       :invoice-id invoice-id
                       :start-ts kalevala
                       :end-ts runeberg)
              => (err :error.startdate-before-enddate)
              (command it/authority :update-invoice-worktime :id ya-app-id
                       :invoice-id invoice-id
                       :start-ts "bad"
                       :end-ts runeberg)
              => (err :error.illegal-value:schema-validation)
              (command it/authority :update-invoice-worktime :id ya-app-id
                       :invoice-id invoice-id
                       :start-ts (date/timestamp "12.8.1323")
                       :end-ts runeberg)
              => (err :error.illegal-value:schema-validation))))))))

;; Note: this test contains a lot of duplication (minus fail cases) with the previous one
;; as the latter assertions do not work without asserting some subset of the earlier ones
(with-test-system {:fixture "minimal"}
  (with-local-actions

    (let [{app-id :id
           :as    app} (it/dummy-submitted-application it/applicant-2)
          pientalo-id  (-> app :primaryOperation :id)]

      (fact "Enable invoicing for 753-R"
        (it/toggle-invoicing true) => ok?)

      (facts "New invoice"
        (facts "The most suitable price catalogue is selected"
          (let [past-id              (it/new-catalogue -1 true)
                current-id           (it/insert-catalog {:from   (date/timestamp (date/today))
                                                         :until  (date/timestamp (date/end-of-day (date/now)))
                                                         :prefix "one"})
                future-id            (it/new-catalogue 1 true)

                {invoice-id :id
                 :as        invoice} (:invoice (command it/authority :new-invoice-draft :id app-id))
                fetch-catalogue-id   #(:price-catalogue-id (mongo/by-id :invoices invoice-id))]
            (fact "No exact match, the latest published is selected"
              (:price-catalogue-id invoice) => future-id)
            (fact "Catalogue selection can be cleared"
              (command it/authority :set-invoice-field :id app-id
                       :invoice-id invoice-id
                       :field :price-catalogue-id
                       :value "")
              => (just {:ok  true
                        :in  (just {:modified pos?})
                        :out [:price-catalogue-id]})
              (fetch-catalogue-id) => nil)
            (fact "Catalogue selected"
              (command it/authority :set-invoice-field :id app-id
                       :invoice-id invoice-id
                       :field :price-catalogue-id
                       :value past-id)
              => (just {:ok true
                        :in (just {:modified pos?})})
              (fetch-catalogue-id) => past-id)
            (fact "Exact match"
              (command it/municipal-authority :publish-price-catalogue
                       :price-catalogue-id current-id
                       :organizationId "753-R") => ok?
              (let [invoice (:invoice (command it/authority :new-invoice-draft :id app-id))]
                (fact "invoice matches"
                  invoice => (just {:application-id     app-id
                                    :application-payer? true
                                    :created            pos?
                                    :created-by         it/authority-user
                                    :description        app-id
                                    :id                 truthy
                                    :modified           pos?
                                    :modified-by        it/authority-user
                                    :operations         []
                                    :organization-id    "753-R"
                                    :price-catalogue-id current-id
                                    :rows               []
                                    :state              "draft"})))))))

      (let [invoice-id (-> (command it/authority-2 :new-invoice-draft :id app-id)
                           :invoice :id)
            cmd        (fn [cmd-name & args]
                         (apply command it/authority cmd-name
                                (concat [:id app-id :invoice-id invoice-id]
                                        args)))
            fetch      #(mongo/by-id :invoices invoice-id)
            fetch-row  #(->> (fetch) :rows (util/find-by-id %))
            ts         (date/timestamp (date/now))]
        (facts "Add operation to invoice"
          (fact "Success: operation with the catalogue products"
            (cmd :add-invoice-operation :operation-id pientalo-id)
            => (just {:ok        true
                      :modified  (partial <= ts)
                      :operation {:id pientalo-id :name "pientalo"}
                      :rows      (just
                                   (just {:code           "one-0"
                                          :id             ss/not-blank?
                                          :max-unit-price 10
                                          :min-unit-price 2
                                          :operation-id   pientalo-id
                                          :price-per-unit 5
                                          :text           "one pientalo 0"
                                          :unit           "kpl"})
                                   (just {:code             "one-1"
                                          :discount-percent 10
                                          :id               ss/not-blank?
                                          :max-unit-price   11
                                          :min-unit-price   3
                                          :operation-id     pientalo-id
                                          :price-per-unit   6
                                          :text             "one pientalo purkaminen 1"
                                          :unit             "kpl"}))})))
        (let [invoice           (fetch)
              [row-id1 row-id2] (map :id (:rows invoice))]
          (fact "Invoice in mongo"
            invoice => (just {:application-id     app-id
                              :application-payer? true
                              :created            (partial >= ts)
                              :created-by         it/authority-2-user
                              :description        app-id
                              :id                 invoice-id
                              :modified           (partial <= ts)
                              :modified-by        it/authority-user
                              :operations         [{:id pientalo-id :name "pientalo"}]
                              :organization-id    "753-R"
                              :price-catalogue-id ss/not-blank?
                              :rows               [{:code           "one-0"
                                                    :id             row-id1
                                                    :max-unit-price 10
                                                    :min-unit-price 2
                                                    :operation-id   pientalo-id
                                                    :price-per-unit 5
                                                    :text           "one pientalo 0"
                                                    :unit           "kpl"}
                                                   {:code             "one-1"
                                                    :discount-percent 10
                                                    :id               row-id2
                                                    :max-unit-price   11
                                                    :min-unit-price   3
                                                    :operation-id     pientalo-id
                                                    :price-per-unit   6
                                                    :text             "one pientalo purkaminen 1"
                                                    :unit             "kpl"}]
                              :state              "draft"}))
          (fact "Deleting a non-existing row is a no-op (fails silently)"
            (cmd :delete-invoice-row :row-id "nowhere") => {:ok true})
          (fact "Delete an existing row"
            (cmd :delete-invoice-row :row-id row-id1)
            => (just {:modified pos? :ok true})
            (->> (fetch) :rows (map :id)) => [row-id2])
          (fact "Add good row from catalogue"
            (cmd :add-invoice-row :row-id it/fixed-id :operation-id pientalo-id)
            => (just {:modified pos?
                      :ok       true
                      :row      (just {:code           "fixed"
                                       :id             #(and (ss/not-blank? %) (not= % it/fixed-id))
                                       :operation-id   pientalo-id
                                       :price-per-unit 5
                                       :text           "Fixed price row"
                                       :unit           "m2"})}))
          (fact "Add custom row"
            (cmd :add-invoice-row :row-id "custom" :operation-id pientalo-id)
            => (just {:modified pos?
                      :ok       true
                      :row      (just {:custom?      true
                                       :id           ss/not-blank?
                                       :operation-id pientalo-id
                                       :unit         "kpl"})}))
          (let [[row-id fixed-id custom-id
                 :as rows] (->> (fetch) :rows (map :id))]
            (facts "Editing rows"
              (count rows) => 3
              (fact "Text and unit can only be edited for custom rows"
                (cmd :set-invoice-row-field :row-id custom-id
                     :path [:text]
                     :value " Hello ")
                => (row-ok? {:row-in {:text "Hello"}})
                (:text (fetch-row custom-id)) => "Hello"
                (cmd :set-invoice-row-field :row-id custom-id
                     :path [:unit]
                     :value "m3")
                => (row-ok? {:row-in {:unit "m3"}}))

              (fact "Units"
                ;; Price-per-unit for fixed-id row is 5
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value "10")
                => (row-ok? {:row-in {:total-cents 5000 :units 10}})
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value " ")
                => (row-ok? {:row-in  {:total-cents 0}
                             :row-out [:units]})
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value "0.0")
                => (row-ok? {:row-in {:total-cents 0
                                      :units       0.0}})
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value "-2")
                => (row-ok? {:row-in {:total-cents -1000 :units -2}})
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value "3.3333333")
                => (row-ok? {:row-in {:total-cents 1665 :units 3.33}})
                (cmd :set-invoice-row-field :row-id fixed-id
                     :path [:units]
                     :value " 3,445000 ")
                => (row-ok? {:row-in {:total-cents 1725 :units 3.45}}))

              (facts "Price per unit"
                (fact "Price range from a catalogue: [3, 11]"
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:price-per-unit]
                       :value "")
                  => (row-ok? {:row-in  {:total-cents 0}
                               :row-out [:price-per-unit]})
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:price-per-unit]
                       :value "2.995")
                  => (row-ok? {:row-in {:price-per-unit 3.0
                                        :total-cents    0}})
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:units]
                       :value "4")
                  (row-ok? {:row-in {:units       4
                                     :total-cents 1200}})
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:price-per-unit]
                       :value " 11,00444 ")
                  => (row-ok? {:row-in {:price-per-unit 11.0
                                        :total-cents    3960}})
                  (cmd :set-invoice-row-field :row-id row-id
                       :path [:discount-percent]
                       :value " ")
                  => (row-ok? {:row-in  {:total-cents 4400}
                               :row-out [:discount-percent]}))

                (fact "Custom row"
                  (cmd :set-invoice-row-field :row-id custom-id
                       :path [:price-per-unit]
                       :value " -20 ")
                  => (row-ok? {:row-in {:total-cents    0
                                        :price-per-unit -20}})
                  (cmd :set-invoice-row-field :row-id custom-id
                       :path [:units]
                       :value " 1 ")
                  => (row-ok? {:row-in {:total-cents -2000
                                        :units       1}})
                  (cmd :set-invoice-row-field :row-id custom-id
                       :path [:units]
                       :value " -1 ")
                  => (row-ok? {:row-in {:total-cents 2000
                                        :units       -1}})))


              (let [p-cmd #(cmd :set-invoice-row-field :row-id row-id
                                :path [:discount-percent]
                                :value %)
                    p-ok? #(row-ok? {:row-in {:discount-percent %1
                                              :total-cents      %2}})]
                (facts "Discount percent"
                  (fetch-row row-id) => (contains {:total-cents    4400
                                                   :price-per-unit 11.0
                                                   :units          4})
                  (facts "0 vs. empty"
                    (p-cmd "0") => (p-ok? 0 4400)
                    (p-cmd " ") => (row-ok? {:row-in  {:total-cents 4400}
                                             :row-out [:discount-percent]}))

                  (facts "With % sign"
                    (p-cmd " 25% ") => (p-ok? 25 3300)
                    (p-cmd " 25 % ") => (p-ok? 25 3300)
                    (p-cmd "%25") => (p-ok? 25 3300)
                    (p-cmd "%%25%%") => (p-ok? 25 3300))

                  (facts "Percentage calculation"
                    (p-cmd " 25 ") => (p-ok? 25 3300)
                    (p-cmd " 12,345000 ") => (p-ok? 12.35 3857)
                    (p-cmd " 33.33333 %") => (p-ok? 33.33 2933)
                    (p-cmd "% 100.00444") => (p-ok? 100.0 0)
                    (p-cmd "100") => (p-ok? 100 0)
                    (p-cmd "0.004") => (p-ok? 0.0 4400)
                    (p-cmd "1") => (p-ok? 1 4356)
                    (p-cmd "0.1 %") => (p-ok? 0.1 4396)
                    (p-cmd "0.05") => (p-ok? 0.05 4398)
                    (p-cmd "0.02") => (p-ok? 0.02 4399)
                    (p-cmd "0.01") => (p-ok? 0.01 4400)
                    (p-cmd "0.005") => (p-ok? 0.01 4400)
                    (p-cmd "0.0049") => (p-ok? 0.0 4400)))))

            (facts "Comment"
              (cmd :set-invoice-row-field :row-id fixed-id
                   :path [:comment]
                   :value "  Bonus ")
              => (row-ok? {:row-in {:comment "Bonus"}})
              (cmd :set-invoice-row-field :row-id fixed-id
                   :path [:comment]
                   :value "  ")
              => (row-ok? {:row-in {:comment ""}}))

            (facts "Product constants"
              (doseq [c    [:kustannuspaikka :alv :laskentatunniste
                            :projekti :kohde :toiminto :muu-tunniste]
                      :let [pkw (util/kw-path :product-constants c)]]
                (fact {:midje/description (name c)}
                  (cmd :set-invoice-row-field :row-id fixed-id
                       :path [:product-constants c]
                       :value "  Hello ")
                  => (row-ok? {:row-in {pkw "Hello"}})
                  (cmd :set-invoice-row-field :row-id fixed-id
                       :path [:product-constants c]
                       :value "   ")
                  => (row-ok? {:row-in {pkw ""}}))))))

        (facts "Add new operation"
          (fact "... to application"
            (command it/applicant-2 :add-operation :id app-id
                     :operation "aita") => ok?)
          (let [aita-id (-> (mongo/by-id :applications app-id)
                            :secondaryOperations first :id)]
            (fact "... and invoice"
              (let [body (cmd :add-invoice-operation
                              :operation-id aita-id)]
                (fact "body"
                  body => (just {:modified  pos?
                                 :ok        true
                                 :operation {:id aita-id :name "aita"}
                                 :rows      (just
                                              (just {:id             #(and (ss/not-blank? %)
                                                                           (not= % it/fixed-id))
                                                     :operation-id   aita-id
                                                     :code           "fixed"
                                                     :text           "Fixed price row"
                                                     :unit           "m2"
                                                     :price-per-unit 5}))})
                  (fetch) => (contains {:modified   (:modified body)
                                        :operations [{:id pientalo-id :name "pientalo"}
                                                     {:id aita-id :name "aita"}]
                                        :rows       (just (contains {:operation-id pientalo-id})
                                                          (contains {:operation-id pientalo-id})
                                                          (contains {:operation-id pientalo-id})
                                                          (contains {:id           (-> body :rows first :id)
                                                                     :operation-id aita-id}))} )))
              (fact "Delete non-existing invoice operation"
                (let [old (fetch)]
                  (fact "response"
                    (cmd :delete-invoice-operation :operation-id "bad-id") => ok?
                    (fetch) => old)
                  (fact "Delete pientalo"
                    (let [body (cmd :delete-invoice-operation :operation-id pientalo-id)]
                      (fact "response"
                        body => (just {:ok       true
                                       :modified #(> % (:modified old))})
                        (fetch) => (contains {:modified   (:modified body)
                                              :operations [{:id aita-id :name "aita"}]
                                              :rows       (just (contains {:operation-id aita-id}))})))))))))

        (let [f-cmd  #(cmd :set-invoice-field :field %1 :value %2)
              oi-cmd #(f-cmd :organization-internal-invoice? %)
              f-ok?  (just {:in (just {:modified pos?}) :ok true})
              oi-get #(:organization-internal-invoice? (fetch))]
          (facts "Internal invoice"
            (oi-cmd true) => f-ok?
            (oi-get) => true
            (oi-cmd false) => f-ok?
            (oi-get) => false)

          (facts "Backend id"
            (facts "Configuration"
              (fact "Backend-id not enabled"
                (query it/municipal-authority :invoicing-config :organizationId "753-R")
                => (contains {:ok true}))
              (fact "Configure invoicing and enable backend-id"
                (command it/admin :update-invoicing-config
                         :org-id "753-R"
                         :invoicing-config {:download?   true
                                            :local-sftp? false
                                            :backend-id? true}) => ok?)
              (fact "Now backend-id can be configured"
                (command it/municipal-authority :configure-invoicing-backend-id
                         :organizationId "753-R"
                         :op {:set {:numbers 8}}) => ok?)
              (fact "Upsert: Initial addition of code"
                (let [cfg    (:config (command it/municipal-authority :configure-invoicing-backend-id
                                               :organizationId "753-R"
                                               :op {:upsert {:code " foo "
                                                             :text " Hello world! "}}))
                      foo-id (-> cfg :codes first :id)]
                  (fact "response"
                    foo-id => truthy
                    cfg => {:numbers 8
                            :codes   [{:code "foo" :text "Hello world!" :id foo-id}]})
                  (fact "Add another code"
                    (command it/municipal-authority :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:upsert {:code " bar "
                                           :text " Ni hao! "}}) => ok?)
                  (fact "Id must match if given"
                    (command it/municipal-authority :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:upsert {:code "dum"
                                           :id   (mongo/create-id)
                                           :text "Bonjour!"}})
                    => (err :error.id-not-found)
                    (:config (command it/municipal-authority :configure-invoicing-backend-id
                                      :organizationId "753-R"
                                      :op {:upsert {:code " dum "
                                                    :id   foo-id
                                                    :text " Bonjour! "}}))
                    => (just {:numbers 8
                              :codes   (just {:code "dum"
                                              :id   foo-id
                                              :text "Bonjour!"}
                                             (just {:id   truthy
                                                    :code "bar"
                                                    :text "Ni hao!"}))}))
                  (fact "Delete: code id must exist"
                    (doseq [id ["bad-id" nil "  " ""]]
                      (fact "response"
                        (command it/municipal-authority :configure-invoicing-backend-id
                                 :organizationId "753-R"
                                 :op {:delete {:id id}})
                        => schema-error?)
                      (fact "If code is not found, delete just returns the current config"
                        (:config (command it/municipal-authority :configure-invoicing-backend-id
                                          :organizationId "753-R"
                                          :op {:delete {:id (mongo/create-id)}}))
                        => (just {:numbers 8
                                  :codes   (just {:code "dum"
                                                  :id   foo-id
                                                  :text "Bonjour!"}
                                                 (just {:id   truthy
                                                        :code "bar"
                                                        :text "Ni hao!"}))}))))
                  (fact "Delete: success"
                    (:config (command it/municipal-authority :configure-invoicing-backend-id
                                      :organizationId "753-R"
                                      :op {:delete {:id foo-id}}))
                    => (just {:numbers 8
                              :codes   (just [(just {:id   truthy
                                                     :code "bar"
                                                     :text "Ni hao!"})])})))))
            (let [set-code (partial f-cmd :backend-code)]
              (facts "Set backend code to invoice"
                (fact "Success"
                  (set-code "bar") => ok?
                  (:backend-code (fetch)) => "bar")
                (fact "Code can be cleared"
                  (set-code "  ") => (just {:in  (just {:modified pos?})
                                            :ok  true
                                            :out [:backend-code]})
                  (:backend-code (fetch)) => nil)
                (facts "Nothing works if invoicing is not enabled"
                  (command it/admin :update-organization
                           :invoicingEnabled false
                           :municipality "753"
                           :permitType "R") => ok?
                  (fact "Backend-id cannot be configured"
                    (command it/municipal-authority :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:set {:numbers 8}})
                    => invoicing-disabled?)
                  (fact "Backend code cannot be added to the invoice"
                    (set-code "bar") => invoicing-disabled?))))
            (fact "Enable invoicing and set backend code"
              (command it/admin :update-organization
                       :invoicingEnabled true
                       :municipality "753"
                       :permitType "R")
              => ok?
              (let [{app-id2 :id} (it/dummy-submitted-application it/applicant-2)
                    invoice-id2   (-> (command it/authority-2 :new-invoice-draft :id app-id2)
                                      :invoice :id)]
                (fact "Available codes"
                  (:codes (query it/biller :invoicing-backend-id-codes
                                 :id app-id))
                  => [{:code "bar" :text "Ni hao!" }])
                (fact "Set backend code successfully"
                  (command it/authority :set-invoice-field
                           :id app-id2
                           :invoice-id invoice-id2
                           :field :backend-code
                           :value "bar")
                  => ok?
                  (mongo/any? :invoices {:_id invoice-id2 :backend-code "bar"})
                  => true))))))

      (facts "Change the invoice state"
        (let [invoice-id        (-> (command it/authority :new-invoice-draft :id app-id)
                                    :invoice :id)
              fetch             #(mongo/by-id :invoices invoice-id)
              row-cmd           #(command it/authority :set-invoice-row-field :id app-id
                                          :invoice-id invoice-id
                                          :row-id %1
                                          :path (flatten [%2])
                                          :value %3)
              _                 (command it/authority :add-invoice-operation :id app-id
                                         :invoice-id invoice-id
                                         :operation-id pientalo-id)
              [row-id1 row-id2] (->> (fetch) :rows (map :id))]
          (fact "rows"
            row-id1 => truthy
            row-id2 => truthy)
          (fact "Add amount, discount and VAT to the first row"
            (row-cmd row-id1 :units "10") => ok?
            (row-cmd row-id1 :discount-percent "15") => ok?
            (row-cmd row-id1 [:product-constants :alv] "24") => ok?)
          (fact "Add amount and VAT to the second row"
            (row-cmd row-id2 :units "20") => ok?
            (row-cmd row-id2 [:product-constants :alv] "13") => ok?)
          (fact "Backend code"
            (command it/authority :set-invoice-field :id app-id
                     :invoice-id invoice-id
                     :field :backend-code
                     :value "bar") => ok?)
          (let [{:keys [invoices]}          (query it/reader :application-invoices
                                                   :id app-id
                                                   :invoice-id invoice-id)
                {:keys [sum-vat-info rows]} (first invoices)]
            (fact "Query invoice (no VAT nor sums calculated)"
              (count invoices) => 1
              sum-vat-info => nil
              (map :vat-info rows) => [nil nil]))
          (fact "Bad state changes for authority"
            (doseq [new-state ["bad" nil "draft" "confirmed" "transferred"]]
              (fact {:midje/description (str "draft -> " (some-> new-state name))}
                (command it/authority :set-invoice-field :id app-id
                         :invoice-id invoice-id
                         :field :state
                         :value new-state) => fail?)))
          (fact "Mark the invoice as checked"
            (command it/authority :set-invoice-field :id app-id
                     :invoice-id invoice-id
                     :field :state
                     :value "checked") => ok?)
          (fact "Query invoice (VAT and sums calculated)"
            (let [{:keys [invoices]}          (query it/reader :application-invoices
                                                     :id app-id
                                                     :invoice-id invoice-id)
                  {:keys [sum-vat-info rows]} (first invoices)]
              (fact "response"
                (count invoices) => 1
                sum-vat-info => {:taxfree-cents 12985 :vat-cents 2065}
                rows => (just (contains {:discount-percent  15
                                         :price-per-unit    5
                                         :product-constants {:alv "24"}
                                         :units             10
                                         :total-cents       4250
                                         :vat-info          {:taxfree-cents  3427
                                                             :vat-cents      823
                                                             :vat-percentage 24}})
                              (contains {:discount-percent  10
                                         :price-per-unit    6
                                         :product-constants {:alv "13"}
                                         :units             20
                                         :total-cents       10800
                                         :vat-info          {:taxfree-cents  9558
                                                             :vat-cents      1242
                                                             :vat-percentage 13}}))))))))))

(with-test-system {:fixture "minimal"}
  (with-local-actions

    (facts "biller-invoices"
      (let [args {:user   it/biller
                  :org-id "753-R"
                  :states ["checked"]}]

        (fact "cannot fetch invoices if not enabled"
          (it/biller-invoices args) => (err :error.invoicing-disabled))

        (it/toggle-invoicing-ok true)

        (fact "authorization needed"
          (doseq [user [it/applicant it/admin]]
            (fact "can NOT fetch invoices if not authorized"
              (it/biller-invoices (assoc args :user user)) => (err :error.unauthorized)))
          (doseq [user [it/reader it/biller it/authority]]
            (fact "can fetch invoices if authorized"
              (it/biller-invoices (assoc args :user user)) => ok?)))

        (facts "invoices are enriched for the biller"
          (let [{app-id :id
                 :as    app} (it/dummy-submitted-application it/applicant-2)]
            (fact "no results"
              (it/biller-invoices-ok args) => empty?)

            (fact "minimal invoice"
              (let [{{invoice-id :id} :invoice} (it/create-draft-invoice-with-rows-ok {:existing-application app})
                    _                           (it/set-invoice-state-ok {:app-id app-id :invoice-id invoice-id :state "checked"})
                    args                        (merge args {:include [invoice-id]
                                                             :limit   5})]
                (fact "valid query is valid"
                  (it/biller-invoices args) => ok?)

                (fact "count matches"
                  (count (it/biller-invoices-ok args)) => 1
                  (count (it/biller-invoices-ok (assoc args :states ["confirmed"]))) => 0
                  (count (it/biller-invoices-ok (assoc args :states ["transferred"]))) => 0)

                (fact "invalid queries"
                  (fact "should fail when no state given"
                    (it/biller-invoices (dissoc args :states))
                    => (err :error.illegal-value:schema-validation))
                  (fact "should fail given draft state param"
                    (it/biller-invoices (assoc args :states ["draft"]))
                    => (err :error.illegal-value:schema-validation))
                  (fact "should fail given unknown state param"
                    (it/biller-invoices (assoc args :states ["foo"]))
                    => (err :error.illegal-value:schema-validation))
                  (fact "should fail given invalid limit param"
                    (it/biller-invoices (assoc args :limit -1))
                    => (err :error.illegal-value:schema-validation)))

                (it/set-invoice-state-ok {:app-id app-id :invoice-id invoice-id :state "draft"})
                (it/delete-invoice-ok {:app-id app-id :invoice-id invoice-id})))

            (fact "with operation"
              (let [invoice-id  (it/new-invoice-ok app-id)
                    pientalo-id (-> app :primaryOperation :id)
                    args        {:user         it/authority
                                 :app-id       app-id
                                 :invoice-id   invoice-id
                                 :operation-id pientalo-id}]

                (fact "can add operation"
                  (it/add-invoice-operation args) => ok?)

                (fact "can add row"
                  (it/add-invoice-row (assoc args :row-id "custom")) => ok?)

                (fact "can mark checked"
                  (it/set-invoice-state (assoc args :state "checked")) => ok?)

                (let [invoice (first (it/biller-invoices-ok {:user   it/biller
                                                             :org-id "753-R"
                                                             :states ["checked"]}))]
                  (fact "operations"
                    (:operations invoice) => (one-of (just {:id   string?
                                                            :name "pientalo"}))))

                (it/set-invoice-state-ok {:app-id app-id :invoice-id invoice-id :state "draft"})
                (it/delete-invoice-ok {:app-id app-id :invoice-id invoice-id})))

            (let [basic-data {:application-id     app-id
                              :application-payer? true
                              :company-id         ""
                              :country            "FIN"
                              :created            int?
                              :description        app-id
                              :email              ""
                              :enriched-data      {:application {:address     "Kaivokatu 1"
                                                                 :operation   "pientalo"
                                                                 :location    [385770.46 6672188.964]
                                                                 :property-id "75312312341234"}}
                              :errors             {}
                              :modified           int?
                              :netbill            ""
                              :operations         (just [(just {:id string? :name "pientalo"})])
                              :operator           ""
                              :organization-id    "753-R"
                              :ovt                ""
                              :person-id          ""
                              :phone              ""
                              :protected?         false
                              :rows               (just [(just {:custom?        true
                                                                :id             string?
                                                                :operation-id   string?
                                                                :price-per-unit 10
                                                                :text           "Row 1"
                                                                :total-cents    1000
                                                                :unit           "kpl"
                                                                :units          1})])
                              :state              "checked"
                              :sum-total-cents    1000}]
              (fact "with billing info from application when payer is a person"
                (it/copy-applicant-details-into-payer-document-ok {:application app :applicant it/applicant-2 :reference "laskuviite-pena"})
                (let [{{invoice-id :id} :invoice} (it/create-draft-invoice-with-rows-ok {:existing-application app})
                      _                           (it/set-invoice-state-ok {:app-id app-id :invoice-id invoice-id :state "checked"})
                      invoice                     (first (it/biller-invoices-ok args))]
                  (fact "invoice"
                    invoice => (contains (merge basic-data
                                                {:billing-reference "laskuviite-pena"
                                                 :city              "PIIPPOLA"
                                                 :entity-address    "Paapankuja 12"
                                                 :entity-name       ""
                                                 :first-name        "Pena"
                                                 :id                invoice-id
                                                 :last-name         "Panaani"
                                                 :payer-type        "person"
                                                 :person-id         "010203-040A"
                                                 :postal-code       "92620"})))
                  (it/set-invoice-state-ok {:app-id app-id :invoice-id invoice-id :state "draft"})
                  (it/delete-invoice-ok {:app-id app-id :invoice-id invoice-id})))

              (fact "with billing info from application when payer is a company"
                (it/copy-business-details-into-payer-document-ok {:application app :applicant it/applicant-2})
                (let [{{invoice-id :id} :invoice} (it/create-draft-invoice-with-rows-ok {:existing-application app})
                      _                           (it/set-invoice-state-ok {:app-id app-id :invoice-id invoice-id :state "checked"})
                      invoice                     (first (it/biller-invoices-ok args))]
                  (fact "invoice"
                    invoice => (contains (merge basic-data
                                                {:billing-reference "laskuviite-pena-yritys"
                                                 :city              "PIIPPOLA"
                                                 :company-id        "0813000-2"
                                                 :entity-address    "Lumekatu 5"
                                                 :entity-name       "Feikki Oy"
                                                 :first-name        "Simo"
                                                 :id                invoice-id
                                                 :last-name         "Simonen"
                                                 :netbill           "1234"
                                                 :operator          "DABAFIHH"
                                                 :ovt               "003708130002"
                                                 :payer-type        "company"
                                                 :postal-code       "92620"})))
                  (it/set-invoice-state-ok {:app-id app-id :invoice-id invoice-id :state "draft"})
                  (it/delete-invoice-ok {:app-id app-id :invoice-id invoice-id})))

              (fact "query without batches"
                (let [inv-id-1 (:id (it/create-and-check-invoice-ok {:existing-application app}))
                      inv-id-2 (:id (it/create-and-check-invoice-ok {:existing-application app}))
                      inv-id-3 (:id (it/create-and-check-invoice-ok {:existing-application app}))]

                  (fact "Invoices returned latest first"
                    (->> (it/biller-invoices-ok args)
                         (mapv :id)) => [inv-id-3 inv-id-2 inv-id-1])

                  (fact "can limit"
                    (->> (it/biller-invoices-ok (assoc args :limit 2))
                         (mapv :id)) => [inv-id-3 inv-id-2])

                  (fact "can include"
                    (->> (it/biller-invoices-ok (assoc args :include [inv-id-1 inv-id-3]))
                         (mapv :id)) => [inv-id-3 inv-id-1])

                  (fact "can exclude"
                    (->> (it/biller-invoices-ok (assoc args :exclude [inv-id-1 inv-id-3]))
                         (mapv :id)) => [inv-id-2])

                  (doseq [id [inv-id-1 inv-id-2 inv-id-3]]
                    (it/set-invoice-state-ok {:app-id app-id :invoice-id id :state "draft"})
                    (it/delete-invoice-ok {:app-id app-id :invoice-id id}))))

              (fact "query by batch-id works"
                ;; Note that query by batch-id does not support `:limit`
                (let [tb-id-1  (it/create-transfer-batch-ok)
                      tb-id-2  (it/create-transfer-batch-ok)
                      tb-id-3  (it/create-transfer-batch-ok)
                      inv-id-1 (:id (it/create-and-confirm-invoice-ok {:existing-application app
                                                                       :transfer-batch-id    tb-id-1}))
                      inv-id-2 (:id (it/create-and-confirm-invoice-ok {:existing-application app
                                                                       :transfer-batch-id    tb-id-1}))
                      inv-id-3 (:id (it/create-and-confirm-invoice-ok {:existing-application app
                                                                       :transfer-batch-id    tb-id-1}))
                      ;; One invoice in batch #2
                      inv-id-4 (:id (it/create-and-confirm-invoice-ok {:existing-application app
                                                                       :transfer-batch-id    tb-id-2}))]

                  (fact "by default results are latest first"
                    (fact "TB #1 contains 3 invoices"
                      (->> (it/biller-invoices-ok (-> args
                                                      (dissoc :states)
                                                      (assoc :batch-ids [tb-id-1])))
                           (mapv :id)) => [inv-id-3 inv-id-2 inv-id-1])
                    (fact "TB #2 contains 1 invoices"
                      (->> (it/biller-invoices-ok (-> args
                                                      (dissoc :states)
                                                      (assoc :batch-ids [tb-id-2])))
                           (map :id)) => [inv-id-4])
                    (fact "TB #3 is empty"
                      (it/biller-invoices-ok (-> args
                                                 (dissoc :states)
                                                 (assoc :batch-ids [tb-id-3]))) => empty?)
                    (fact "Can query both batches at once"
                      (->> (it/biller-invoices-ok (-> args
                                                      (dissoc :states)
                                                      (assoc :batch-ids [tb-id-1 tb-id-2])))
                           (map :id)) => [inv-id-4 inv-id-3 inv-id-2 inv-id-1]))

                  (fact "can include only specific"
                    (->> (it/biller-invoices-ok (-> args
                                                    (dissoc :states)
                                                    (assoc :include [inv-id-2 inv-id-3] :batch-ids [tb-id-1])))
                         (mapv :id)) => [inv-id-3 inv-id-2])
                  (fact "can exclude"
                    (->> (it/biller-invoices-ok (-> args
                                                    (dissoc :states)
                                                    (assoc :exclude [inv-id-1 inv-id-3] :batch-ids [tb-id-1
                                                                                                    tb-id-2])))
                         (mapv :id)) => [inv-id-4 inv-id-2])

                  (doseq [id [inv-id-1 inv-id-2 inv-id-3 inv-id-4]]
                    (it/set-biller-invoice-state-ok {:app-id app-id :invoice-id id :state "checked"})
                    (it/set-invoice-state-ok {:app-id app-id :invoice-id id :state "draft"})
                    (it/delete-invoice-ok {:app-id app-id :invoice-id id})))))))))))

(with-test-system {:fixture "minimal"}
  (with-local-actions
    (fact "Enable invoicing for 753-R"
      (it/toggle-invoicing true) => ok?)

    (facts "tests related to invoice deletion and auth"

      (facts "delete-invoice command"
        (let [{app-id :id}    (it/dummy-submitted-application it/applicant)
              {app-id-2 :id}  (it/dummy-submitted-application it/applicant)
              invoice-id      (it/new-invoice-ok app-id)
              invoice-from-db (mongo/by-id "invoices" invoice-id)
              args            {:user       it/authority
                               :app-id     app-id
                               :invoice-id invoice-id}]

          (fact "can NOT delete invoice when invoice id is not valid"
            (it/delete-invoice (assoc args :invoice-id "wrong-id")) => (err :error.invalid-invoice)
            (mongo/by-id "invoices" invoice-id) => invoice-from-db)

          (fact "can NOT delete invoice when application id is not valid"
            (it/delete-invoice (assoc args :app-id "wrong-id")) => (err :error.application-not-accessible)
            (mongo/by-id "invoices" invoice-id) => invoice-from-db)

          (fact "can NOT delete invoice when application id is not the application id for invoice"
            (it/delete-invoice (assoc args :app-id app-id-2)) => (err :error.invalid-invoice)
            (mongo/by-id "invoices" invoice-id) => invoice-from-db)

          (fact "can NOT delete invoice with state checked"
            (let [{{invoice-id :id} :invoice
                   {app-id :id}     :application} (it/create-draft-invoice-with-rows-ok)
                  _                               (it/set-invoice-state-ok {:app-id app-id :invoice-id invoice-id :state "checked"})
                  invoice-from-db                 (mongo/by-id "invoices" invoice-id)]
              (fact "can't delete"
                (:state invoice-from-db) => "checked"
                (command it/authority :delete-invoice :id app-id :invoice-id invoice-id) => (err :error.invalid-invoice)
                (mongo/by-id "invoices" invoice-id) => invoice-from-db)))

          (doseq [user [it/applicant-2 it/municipal-authority it/admin]]
            (fact "can NOT delete invoice if no access to application"
              (it/delete-invoice (assoc args :user user)) => (err :error.application-not-accessible)))

          (doseq [user [it/applicant it/reader]]
            (fact "can NOT delete invoices if not authorized"
              (it/delete-invoice (assoc args :user user)) => (err :error.unauthorized)))

          (fact "happy path: can delete invoice with state draft"
            (fact "result"
              (mongo/by-id "invoices" invoice-id) => truthy
              (it/delete-invoice args) => ok?
              (mongo/by-id "invoices" invoice-id) => nil))))

      (facts "authorization tests"
        (let [{{invoice-id :id} :invoice
               {app-id :id}     :application} (it/create-draft-invoice-with-rows-ok)
              args                            {:app-id app-id :invoice-id invoice-id}]

          (fact "Enable invoicing in Helsinki (091-R)"
            (command it/admin :update-organization
                     :invoicingEnabled true
                     :municipality "091"
                     :permitType "YA") => ok?)

          (fact "Application for Helsinki"
            (let [{hki-app-id :id} (create-and-submit-application it/applicant
                                                                  :operation "ya-kayttolupa-terassit"
                                                                  :propertyId "09141600550007"
                                                                  :address "Fleminginkatu 1")]
              (fact "app id"
                hki-app-id => truthy)
              (fact "Municipal-Auth-User authority can update Municipal-Auth-User invoice"
                (it/set-invoice-state (assoc args :user it/authority :state "checked")) => ok?)
              (fact "Helsinki authority cannot update Municipal-Auth-User invoice"
                (it/set-invoice-state (assoc args :app-id hki-app-id :user raktark-helsinki :state "checked"))
                => (err :error.invalid-invoice))
              (fact "Helsinki authority cannot update Municipal-Auth-User invoice worktimes"
                (command raktark-helsinki :update-invoice-worktime :id hki-app-id :invoice-id invoice-id :start-ts kalevala :end-ts good)
                => (err :error.invalid-invoice))))

          (fact "Invoice application-id and organization-id cannot be modified"
            (it/set-invoice-field (merge args {:user  it/authority
                                               :field :application-id
                                               :value "LP-BAD"})) => (err :error.unsupported-field-or-value)
            (mongo/by-id "invoices" invoice-id) => (contains {:organization-id "753-R"
                                                              :application-id  app-id})))))))

(with-test-system {:fixture "minimal"}
  (with-local-actions
    (it/toggle-invoicing-ok true)

    (facts "backend-code"
      (it/set-invoicing-config-ok {:invoicing-config {:backend-id? true}})

      (fact "no backend-code set"
        (let [{:keys [invoice]} (it/create-draft-invoice-with-rows-ok)]
          (fact "no backend-code on draft invoice"
            (:backend-code invoice) => nil?)))

      (fact "single backend-code defined"
        (it/configure-invoicing-backend-id {:op {:upsert {:code "code-1"
                                                          :text "First"}}})

        (let [{:keys [invoice]} (it/create-draft-invoice-with-rows-ok)]
          (fact "backend-code automatically selected"
            (:backend-code invoice) => "code-1")))

      (fact "multiple backend-codes defined"
        (it/configure-invoicing-backend-id {:op {:upsert {:code "code-2"
                                                          :text "Second"}}})
        (let [{:keys [invoice]} (it/create-draft-invoice-with-rows-ok)]
          (fact "no backend-code on draft invoice"
            (:backend-code invoice) => nil?))))))
