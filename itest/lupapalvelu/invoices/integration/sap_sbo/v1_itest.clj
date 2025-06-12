(ns lupapalvelu.invoices.integration.sap-sbo.v1-itest
  (:require [lupapalvelu.invoices.integration.deliver :as deliver]
            [lupapalvelu.invoices.test-util :as it]
            [lupapalvelu.itest-util :as itest :refer [ok? with-local-actions with-test-system]]
            [lupapalvelu.mml.geocoding.core :as geocoding]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.util :as util]
            [sade.xml :as sxml]))

(def default-config {:invoicing-system "sap-sbo-v1"
                     :local-sftp?      true
                     :download?        true
                     :backend-id?      true
                     :integration-url  "sftp://random-sftp-server/dummy-folder"
                     :credentials      {:username "my-username"
                                        :password "hunter2"}
                     :constants        {:tilauslaji     "123"
                                        :myyntiorg      "456"
                                        :jakelutie      "99"
                                        :sektori        "88"
                                        :laskuttaja     "ID123"
                                        :nimike         ""
                                        :tulosyksikko   ""
                                        :myyntitoimisto "1234"}})

(defn- find-catalogue-row
  [catalogue row-code]
  (->> catalogue
       :rows
       (util/find-by-key :code row-code)))

(def str->xml->edn (comp util/strip-empty-maps
                         util/strip-nils
                         sxml/xml->edn
                         sxml/parse))

(with-test-system {:fixture "minimal"}
  (with-local-actions

    (it/toggle-invoicing-ok true)

    (it/set-invoicing-config-ok {:invoicing-config default-config})

    (let [transfer-batch-id    (it/create-transfer-batch-ok)
          ;; We need a 8 digit backend id => "25" + 6 digit counter
          _                    (it/configure-backend-id-ok {:numbers 6})
          _                    (it/upsert-backend-id-ok {:code "25" :text "Invoices for the current year"})
          ;; We need codes for the rows => create a price catalogue
          catalogue-id         (it/insert-catalog {:from                 (date/timestamp (date/today))
                                                   :until                (date/timestamp (date/end-of-day (date/now)))
                                                   :prefix               "one"
                                                   ;; mock operations so that creating the invoice does not automatically
                                                   ;; add rows to the invoice
                                                   :operations           [:foo
                                                                          :bar]
                                                   ;; Codes must be 8 digits long
                                                   :code-fn              (fn [i]
                                                                           (str (+ 10000000 i)))
                                                   ;; Project must be 16 digits long
                                                   :product-constants-fn (constantly {:projekti "1122334455667788"})})
          _                    (it/publish-catalogue-ok {:catalogue-id catalogue-id})
          catalogue            (mongo/by-id :price-catalogues catalogue-id)
          catalogue-row-id     (:id (find-catalogue-row catalogue "10000000"))
          catalogue-row-id-2   (:id (find-catalogue-row catalogue "10000001"))
          {app-id :id :as app} (it/dummy-submitted-application it/applicant)
          ;; Two invoices, 2 + 2 rows
          invoice-1-before     (it/create-and-confirm-invoice-ok
                                 {:existing-application app
                                  :transfer-batch-id    transfer-batch-id
                                  :custom-row-count     0
                                  :catalogue-row-ids    [catalogue-row-id
                                                         catalogue-row-id-2]
                                  :row-text-fn          (fn [_id] "Rivi 1")
                                  :row-ppu-fn           (fn [_id] "12")
                                  :row-units-fn         (fn [_id] "3")
                                  :sap-number           "1234567890"})
          invoice-2-before     (it/create-and-confirm-invoice-ok
                                 {:existing-application app
                                  :transfer-batch-id    transfer-batch-id
                                  :custom-row-count     0
                                  :catalogue-row-ids    [catalogue-row-id
                                                         catalogue-row-id-2]
                                  :row-text-fn          (fn [_id] "Rivi 2")
                                  :row-ppu-fn           (fn [_id] "2")
                                  :row-units-fn         (fn [_id] "1")
                                  :sap-number           "1122334455"})
          _                    (it/copy-applicant-details-into-payer-document-ok {:application app
                                                                                  :applicant   it/applicant
                                                                                  :reference   "laskuviite-mikko"})]

      (with-redefs [geocoding/address-by-point! (constantly {:name        "City of Town"
                                                             :postal-code "12345"})]

        (facts "happy path where the applicant is the payer"
          (let [now                           (date/now)
                batch-before                  (it/get-transfer-batch-db transfer-batch-id)
                {:keys [status headers body]} (it/download-transfer-batch-xml {:transfer-batch-id transfer-batch-id})]

            (fact "download does not alter batch or invoices"
              (= batch-before (it/get-transfer-batch-db transfer-batch-id)) => truthy
              (= invoice-1-before (it/get-invoice-db (:id invoice-1-before))) => truthy
              (= invoice-2-before (it/get-invoice-db (:id invoice-2-before))) => truthy)

            (fact "Can download transfer batch as xml"
              status => 200
              headers => (contains {"Content-Type"        "text/xml;charset=UTF-8"
                                    "Content-Disposition" (format "attachment;filename=\"Lupapiste_%s.xml\""
                                                                  (date/zoned-format now (date/pattern-formatter "YYMMddHHmmss")))})
              body => string?)

            (when (string? body)
              (let [as-data (str->xml->edn body)]
                (fact "valid body"
                  as-data => {:SBO_SalesOrderContainer {:SBO_SalesOrder [{:BillText            app-id
                                                                          :DistributionChannel "99"
                                                                          :Division            "88"
                                                                          :LineItem            [{:Material            "10000000"
                                                                                                 :MaterialDescription "one foo 0"
                                                                                                 :NetPrice            "5.00"
                                                                                                 :Quantity            "3.000"
                                                                                                 :Unit                "kpl"
                                                                                                 :WBS_Element         "1122334455667788"}
                                                                                                {:Material            "10000001"
                                                                                                 :MaterialDescription "one bar 1"
                                                                                                 ;; Discount 10%
                                                                                                 :NetPrice            "5.40"
                                                                                                 :Quantity            "3.000"
                                                                                                 :Unit                "kpl"
                                                                                                 :WBS_Element         "1122334455667788"}]
                                                                          :OrderParty          {:CustomerID         "210281-9988"
                                                                                                :PriorityAddress1   "Rambokuja 6"
                                                                                                :PriorityCity       "PAKETTIAUTOMAATTI"
                                                                                                :PriorityName1      "Intonen, Mikko"
                                                                                                :PriorityPostalcode "01155"
                                                                                                :SAPCustomerID      "1234567890"}
                                                                          :OrderType           "123"
                                                                          :PONumber            "laskuviite-mikko"
                                                                          :Reference           "25000001"
                                                                          :SalesOffice         "1234"
                                                                          :SalesOrg            "456"
                                                                          :SenderId            "ID123"}
                                                                         {:BillText            app-id
                                                                          :DistributionChannel "99"
                                                                          :Division            "88"
                                                                          :LineItem            [{:Material            "10000000"
                                                                                                 :MaterialDescription "one foo 0"
                                                                                                 :NetPrice            "2.00"
                                                                                                 :Quantity            "1.000"
                                                                                                 :Unit                "kpl"
                                                                                                 :WBS_Element         "1122334455667788"}
                                                                                                {:Material            "10000001"
                                                                                                 :MaterialDescription "one bar 1"
                                                                                                 ;; Discount 10%
                                                                                                 :NetPrice            "5.40"
                                                                                                 :Quantity            "1.000"
                                                                                                 :Unit                "kpl"
                                                                                                 :WBS_Element         "1122334455667788"}]
                                                                          :OrderParty          {:CustomerID         "210281-9988"
                                                                                                :PriorityAddress1   "Rambokuja 6"
                                                                                                :PriorityCity       "PAKETTIAUTOMAATTI"
                                                                                                :PriorityName1      "Intonen, Mikko"
                                                                                                :PriorityPostalcode "01155"
                                                                                                :SAPCustomerID      "1122334455"}
                                                                          :OrderType           "123"
                                                                          :PONumber            "laskuviite-mikko"
                                                                          :Reference           "25000002"
                                                                          :SalesOffice         "1234"
                                                                          :SalesOrg            "456"
                                                                          :SenderId            "ID123"}]}})))

            (facts ".. and the actual transfer"
              (facts "when delivery succeeds"
                (with-redefs [deliver/do-deliver! (constantly nil)]
                  (let [batch-before (it/get-transfer-batch-db transfer-batch-id)]
                    (fact "no sent timestamp"
                      (:sent batch-before) => nil?)
                    (fact "response indicates success"
                      (it/process-transfer-batch-integration {:transfer-batch-id transfer-batch-id})
                      => ok?)
                    (let [batch-after (it/get-transfer-batch-db transfer-batch-id)]
                      (fact "transfer timestamp recorded"
                        (:sent batch-after) => int?)
                      (fact "history added"
                        (last (:history batch-after)) => (just {:user       it/biller-user
                                                                :time       int?
                                                                :action     "send-to-invoicing-via-local-sftp"
                                                                :message-id string?}))
                      (fact "other fields are the same"
                        (dissoc batch-after :sent :history) => (dissoc batch-before :history)))

                    (let [invoices [(it/get-invoice-db (:id invoice-1-before))
                                    (it/get-invoice-db (:id invoice-2-before))]]
                      (fact "transfer timestamp recorded on invoices"
                        invoices => (partial every? :sent)))))))))

        (facts "happy path where a company is the payer"
          (let [transfer-batch-id             (it/create-transfer-batch-ok)
                {app-id :id :as app}          (it/dummy-submitted-application it/applicant)
                ;; Two invoices, 3 + 2 rows
                _                             (it/create-and-confirm-invoice-ok
                                                {:existing-application app
                                                 :transfer-batch-id    transfer-batch-id
                                                 :custom-row-count     0
                                                 :catalogue-row-ids    [catalogue-row-id
                                                                        catalogue-row-id-2]
                                                 :row-text-fn          (fn [_id] "Rivi 1")
                                                 :row-ppu-fn           (fn [_id] "12")
                                                 :row-units-fn         (fn [_id] "3")
                                                 :sap-number           "1234567890"})
                _                             (it/create-and-confirm-invoice-ok
                                                {:existing-application app
                                                 :transfer-batch-id    transfer-batch-id
                                                 :custom-row-count     0
                                                 :catalogue-row-ids    [catalogue-row-id
                                                                        catalogue-row-id-2]
                                                 :row-text-fn          (fn [_id] "Rivi 2")
                                                 :row-ppu-fn           (fn [_id] "2")
                                                 :row-units-fn         (fn [_id] "1")
                                                 :sap-number           "1122334455"})

                _                             (it/copy-business-details-into-payer-document-ok {:application app
                                                                                                :applicant   it/applicant
                                                                                                :reference   "laskuviite-mikko"})
                now                           (date/now)
                {:keys [status headers body]} (it/download-transfer-batch-xml {:transfer-batch-id transfer-batch-id})]

            (fact "Can download transfer batch as xml"
              status => 200
              headers => (contains {"Content-Type"        "text/xml;charset=UTF-8"
                                    "Content-Disposition" (format "attachment;filename=\"Lupapiste_%s.xml\""
                                                                  (date/zoned-format now (date/pattern-formatter "YYMMddHHmmss")))})
              body => string?)

            (when (string? body)
              (let [as-data (str->xml->edn body)]
                (fact "valid body"
                  as-data => {:SBO_SalesOrderContainer {:SBO_SalesOrder [{:BillText            app-id
                                                                          :DistributionChannel "99"
                                                                          :Division            "88"
                                                                          :LineItem            [{:Material            "10000000"
                                                                                                 :MaterialDescription "one foo 0"
                                                                                                 :NetPrice            "5.00"
                                                                                                 :Quantity            "3.000"
                                                                                                 :Unit                "kpl"
                                                                                                 :WBS_Element         "1122334455667788"}
                                                                                                {:Material            "10000001"
                                                                                                 :MaterialDescription "one bar 1"
                                                                                                 ;; Discount 10%
                                                                                                 :NetPrice            "5.40"
                                                                                                 :Quantity            "3.000"
                                                                                                 :Unit                "kpl"
                                                                                                 :WBS_Element         "1122334455667788"}]
                                                                          :OrderParty          {:CustomerOVT        "003708130002"
                                                                                                :CustomerYID        "0813000-2"
                                                                                                :PriorityAddress1   "Lumekatu 5"
                                                                                                :PriorityCity       "PIIPPOLA"
                                                                                                :PriorityName1      "Feikki Oy"
                                                                                                :PriorityName2      "Simonen, Simo"
                                                                                                :PriorityPostalcode "92620"
                                                                                                :SAPCustomerID      "1234567890"}
                                                                          :OrderType           "123"
                                                                          :PONumber            "laskuviite-mikko"
                                                                          :Reference           "25000003"
                                                                          :SalesOffice         "1234"
                                                                          :SalesOrg            "456"
                                                                          :SenderId            "ID123"}
                                                                         {:BillText            app-id
                                                                          :DistributionChannel "99"
                                                                          :Division            "88"
                                                                          :LineItem            [{:Material            "10000000"
                                                                                                 :MaterialDescription "one foo 0"
                                                                                                 :NetPrice            "2.00"
                                                                                                 :Quantity            "1.000"
                                                                                                 :Unit                "kpl"
                                                                                                 :WBS_Element         "1122334455667788"}
                                                                                                {:Material            "10000001"
                                                                                                 :MaterialDescription "one bar 1"
                                                                                                 ;; Discount 10%
                                                                                                 :NetPrice            "5.40"
                                                                                                 :Quantity            "1.000"
                                                                                                 :Unit                "kpl"
                                                                                                 :WBS_Element         "1122334455667788"}]
                                                                          :OrderParty          {:CustomerOVT        "003708130002"
                                                                                                :CustomerYID        "0813000-2"
                                                                                                :PriorityAddress1   "Lumekatu 5"
                                                                                                :PriorityCity       "PIIPPOLA"
                                                                                                :PriorityName1      "Feikki Oy"
                                                                                                :PriorityName2      "Simonen, Simo"
                                                                                                :PriorityPostalcode "92620"
                                                                                                :SAPCustomerID      "1122334455"}
                                                                          :OrderType           "123"
                                                                          :PONumber            "laskuviite-mikko"
                                                                          :Reference           "25000004"
                                                                          :SalesOffice         "1234"
                                                                          :SalesOrg            "456"
                                                                          :SenderId            "ID123"}]}})))))))))
