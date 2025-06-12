(ns lupapalvelu.invoices.integration.general-itest
  (:require [lupapalvelu.invoices.integration.deliver :as deliver]
            [lupapalvelu.invoices.test-util :as it]
            [lupapalvelu.itest-util :as itest :refer [expected-failure? ok? with-local-actions with-test-system]]
            [lupapalvelu.mml.geocoding.core :as geocoding]
            [lupapalvelu.sftp.client :as sftp-client]
            [lupapalvelu.sftp.core :as sftp]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.http :as http]
            [sade.xml :as sxml]))

(defn err [error]
  (partial expected-failure? error))

(def default-config {:local-sftp?     true
                     :download?       true
                     :integration-url "sftp://random-sftp-server/dummy-folder"
                     :credentials     {:username "my-username"
                                       :password "hunter2"}
                     :constants       {:tilauslaji     "C-1"
                                       :myyntiorg      "C-2"
                                       :jakelutie      "C-3"
                                       :sektori        "C-4"
                                       :laskuttaja     "C-5"
                                       :nimike         "C-6"
                                       :tulosyksikko   "C-7"
                                       :myyntitoimisto "C-8"}})

(with-test-system {:fixture "minimal"}
  (with-local-actions
    (fact "Cannot access API if invoicing disabled"
      (doseq [resp [(it/download-transfer-batch-xml {:transfer-batch-id "1"})
                    (it/process-transfer-batch-integration {:transfer-batch-id "1"})]]
        (fact "not enabled" resp => (err :error.invoicing-disabled))))

    (it/toggle-invoicing-ok true)

    (fact "non-billers have no authority"
      (doseq [user [it/applicant it/reader it/authority it/municipal-authority it/admin]
              resp [(it/download-transfer-batch-xml {:user user :transfer-batch-id "1"})
                    (it/process-transfer-batch-integration {:user user :transfer-batch-id "1"})]]
        (fact "not authorized" resp => (err :error.unauthorized))))

    (fact "unknown transfer batch id results in error"
      (it/download-transfer-batch-xml {:transfer-batch-id "-1"}) => (err :error.not-found)
      (it/process-transfer-batch-integration {:transfer-batch-id "-1"}) => (err :error.not-found))

    (let [transfer-batch-id (it/create-transfer-batch-ok)]

      (fact "accessing an empty transfer batch is an error"
        (it/download-transfer-batch-xml {:transfer-batch-id transfer-batch-id})
        => (err :error.transfer-batch.empty)
        (it/process-transfer-batch-integration {:transfer-batch-id transfer-batch-id})
        => (err :error.transfer-batch.empty))

      (let [{application-id :id
             :as            app} (it/dummy-submitted-application it/applicant)
            ;; Two invoices, 3 + 2 rows
            invoice-1-before     (it/create-and-confirm-invoice-ok
                                   {:existing-application app
                                    :transfer-batch-id    transfer-batch-id
                                    :custom-row-count     3
                                    :row-text-fn          (fn [idx] (format "1 - Rivi: %s" (inc idx)))
                                    :row-ppu-fn           (fn [idx] (nth ["1" "5" "10"] idx))
                                    :row-units-fn         (fn [idx] (nth ["2" "3" "4"] idx))
                                    :sap-number           "112"})
            invoice-2-before     (it/create-and-confirm-invoice-ok
                                   {:existing-application app
                                    :transfer-batch-id    transfer-batch-id
                                    :custom-row-count     2
                                    :row-text-fn          (fn [idx] (format "2 - Rivi: %s" (inc idx)))
                                    :row-ppu-fn           (fn [idx] (nth ["3" "8"] idx))
                                    :row-units-fn         (fn [idx] (nth ["1" "5"] idx))
                                    :sap-number           "456"})
            _                    (it/copy-applicant-details-into-payer-document-ok {:application app
                                                                                    :applicant   it/applicant
                                                                                    :reference   "laskuviite-mikko"})]

        (fact "accessing a transfer batch without feature enabled is an error"
          (it/download-transfer-batch-xml {:transfer-batch-id transfer-batch-id})
          => (err :error.integration.download-not-supported)
          (it/process-transfer-batch-integration {:transfer-batch-id transfer-batch-id})
          => (err :error.integration.general-not-supported))

        (fact "Initially config is empty"
          (it/query-invoicing-config-ok) => empty?)

        (fact "Update config"
          (it/set-invoicing-config-ok {:invoicing-config default-config}))

        (with-redefs [geocoding/address-by-point! (constantly {:name        "City of Town"
                                                               :postal-code "00400"})]

          (facts "happy path where the applicant is the payer"
            (let [now                           (date/now)
                  batch-before                  (it/get-transfer-batch-db transfer-batch-id)
                  {:keys [status headers body]} (it/download-transfer-batch-xml {:transfer-batch-id transfer-batch-id})]

              (fact "download does not alter batch or invoices"
                (= batch-before (it/get-transfer-batch-db transfer-batch-id)) => truthy
                (= invoice-1-before (it/get-invoice-db (:id invoice-1-before))) => truthy
                (= invoice-2-before (it/get-invoice-db (:id invoice-2-before))) => truthy )

              (fact "Can download transfer batch as xml"
                status => 200
                headers => (contains {"Content-Type"        "text/xml;charset=UTF-8"
                                      "Content-Disposition" (format "attachment;filename=\"Lupapiste_%s.xml\""
                                                                    (date/zoned-format now (date/pattern-formatter "YYMMddHHmmss")))})
                body => string?)

              (let [as-data (sxml/xml->edn (sxml/parse-string body "utf-8"))]
                (fact "valid body"
                  as-data => {:InvoiceTransfer {:From     {:System {:Id "Lupapiste"}}
                                                :Invoices {:Invoice [{:Type               "EXTERNAL"
                                                                      :ApplicationContext {:Id             application-id
                                                                                           :PropertyId     "75312312341234"
                                                                                           :StreetAddress  "Kaivokatu 1"
                                                                                           :PostalCode     "00400"
                                                                                           :PermitType     "pientalo"
                                                                                           :PermitCategory "Rakennuslupa"}
                                                                      :Reference          "laskuviite-mikko"
                                                                      :Payee              {:Organization {:Id     "C-2"
                                                                                                          :Group  "C-3"
                                                                                                          :Sector "C-4"}}
                                                                      :Payer              {:Type       "PERSON"
                                                                                           :CustomerId "112"
                                                                                           :Person     {:Id          "210281-9988"
                                                                                                        :FirstName   "Mikko"
                                                                                                        :LastName    "Intonen"
                                                                                                        :Turvakielto "false"
                                                                                                        :Address     {:StreetAddress "Rambokuja 6"
                                                                                                                      :PostalCode    "01155"
                                                                                                                      :City          "PAKETTIAUTOMAATTI"
                                                                                                                      :Country       "FIN"}}}
                                                                      :Rows               {:Row [{:Product {:Name            " 1 - Rivi: 1"
                                                                                                            :Unit            "PIECE"
                                                                                                            :Quantity        "2.00"
                                                                                                            :DiscountPercent "0.00"
                                                                                                            :UnitPrice       "1.00"}}
                                                                                                 {:Product {:Name            " 1 - Rivi: 2"
                                                                                                            :Unit            "PIECE"
                                                                                                            :Quantity        "3.00"
                                                                                                            :DiscountPercent "0.00"
                                                                                                            :UnitPrice       "5.00"}}
                                                                                                 {:Product {:Name            " 1 - Rivi: 3"
                                                                                                            :Unit            "PIECE"
                                                                                                            :Quantity        "4.00"
                                                                                                            :DiscountPercent "0.00"
                                                                                                            :UnitPrice       "10.00"}}]}}
                                                                     {:Type               "EXTERNAL"
                                                                      :ApplicationContext {:Id             application-id
                                                                                           :PropertyId     "75312312341234"
                                                                                           :StreetAddress  "Kaivokatu 1"
                                                                                           :PostalCode     "00400"
                                                                                           :PermitType     "pientalo"
                                                                                           :PermitCategory "Rakennuslupa"}
                                                                      :Reference          "laskuviite-mikko"
                                                                      :Payee              {:Organization {:Id     "C-2"
                                                                                                          :Group  "C-3"
                                                                                                          :Sector "C-4"}}
                                                                      :Payer              {:Type       "PERSON"
                                                                                           :CustomerId "456"
                                                                                           :Person     {:Id          "210281-9988"
                                                                                                        :FirstName   "Mikko"
                                                                                                        :LastName    "Intonen"
                                                                                                        :Turvakielto "false"
                                                                                                        :Address     {:StreetAddress "Rambokuja 6"
                                                                                                                      :PostalCode    "01155"
                                                                                                                      :City          "PAKETTIAUTOMAATTI"
                                                                                                                      :Country       "FIN"}}}
                                                                      :Rows               {:Row [{:Product {:Name            " 2 - Rivi: 1"
                                                                                                            :Unit            "PIECE"
                                                                                                            :Quantity        "1.00"
                                                                                                            :DiscountPercent "0.00"
                                                                                                            :UnitPrice       "3.00"}}
                                                                                                 {:Product {:Name            " 2 - Rivi: 2"
                                                                                                            :Unit            "PIECE"
                                                                                                            :Quantity        "5.00"
                                                                                                            :DiscountPercent "0.00"
                                                                                                            :UnitPrice       "8.00"}}]}}]}}}))

              (facts ".. and the actual transfer"

                (fact "using the wrong method (sap-excel)"
                  (it/download-transfer-batch-excel {:transfer-batch-id transfer-batch-id})
                  => (err :error.integration.sap-excel-not-supported))

                (facts "mock failures, but also use opportunity to check params"
                  (facts "with local-sftp"
                    (with-redefs [sftp/write-invoicing-file (fn [_org-id filename _xml]
                                                              (fact "local-sftp parameters ok"
                                                                (re-matches #"Lupapiste_\d+.xml" filename) => truthy)
                                                              (throw (ex-info "Intentionally failing" {})))]
                      (fact "response indicates an unknown error"
                        (it/process-transfer-batch-integration {:transfer-batch-id transfer-batch-id})
                        => (err :error.unknown))))

                  (facts "with sftp"
                    (it/set-invoicing-config-ok {:invoicing-config (assoc default-config :local-sftp? false)})
                    (with-redefs [sftp-client/upload-file! (fn [host _xml path auth]
                                                             (fact "sftp parameters ok"
                                                               host => "random-sftp-server"
                                                               (re-matches #"dummy-folder/Lupapiste_\d+.xml" path) => truthy
                                                               auth => {:username "my-username"
                                                                        :password "hunter2"})
                                                             (throw (ex-info "Intentionally failing" {})))]
                      (fact "response indicates an unknown error"
                        (it/process-transfer-batch-integration {:transfer-batch-id transfer-batch-id})
                        => (err :error.unknown))))

                  (facts "with anonymous sftp"
                    (it/set-invoicing-config-ok {:invoicing-config (-> default-config
                                                                       (assoc :local-sftp? false)
                                                                       (dissoc :credentials))})
                    (with-redefs [sftp-client/upload-file! (fn [host _xml path auth]
                                                             (fact "sftp parameters ok"
                                                               host => "random-sftp-server"
                                                               (re-matches #"dummy-folder/Lupapiste_\d+.xml" path) => truthy
                                                               auth => {:username         nil
                                                                        :private-key-path "/home/lupapiste/.ssh/invoices_id_rsa"})
                                                             (throw (ex-info "Intentionally failing" {})))]
                      (fact "response indicates an invalid configuration (password is required with :sftp-password)"
                        (it/process-transfer-batch-integration {:transfer-batch-id transfer-batch-id})
                        => (err :error.invalid-configuration))))

                  (facts "with http"
                    (it/set-invoicing-config-ok {:invoicing-config (assoc default-config
                                                                          :local-sftp? false
                                                                          :integration-url "https://random-https-server/dummy-folder")})
                    (with-redefs [http/post (fn [url options]
                                              (fact "http parameters ok"
                                                url => "https://random-https-server/dummy-folder"
                                                options => (contains {:body             string?
                                                                      :throw-exceptions false
                                                                      :throw-fail!      true
                                                                      :content-type     "text/xml;charset=UTF-8"
                                                                      :socket-timeout   10000
                                                                      :conn-timeout     10000
                                                                      :basic-auth       ["my-username" "hunter2"]}))
                                              (throw (ex-info "Intentionally failing" {})))]
                      (fact "response indicates an unknown error"
                        (it/process-transfer-batch-integration {:transfer-batch-id transfer-batch-id})
                        => (err :error.unknown))))

                  (facts "with anonymous http"
                    (it/set-invoicing-config-ok {:invoicing-config (-> default-config
                                                                       (assoc :local-sftp? false
                                                                              :integration-url "https://random-https-server/dummy-folder")
                                                                       (dissoc :credentials))})
                    (with-redefs [http/post (fn [url options]
                                              (fact "http parameters ok"
                                                url => "https://random-https-server/dummy-folder"
                                                options => (contains {:body             string?
                                                                      :throw-exceptions false
                                                                      :throw-fail!      true
                                                                      :content-type     "text/xml;charset=UTF-8"
                                                                      :socket-timeout   10000
                                                                      :conn-timeout     10000}))
                                              (throw (ex-info "Intentionally failing" {})))]
                      (fact "response indicates an unknown error"
                        (it/process-transfer-batch-integration {:transfer-batch-id transfer-batch-id})
                        => (err :error.unknown)))))

                (it/set-invoicing-config-ok {:invoicing-config default-config})

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
                  {application-id :id
                   :as            app}          (it/dummy-submitted-application it/applicant)
                  ;; Two invoices, 3 + 2 rows
                  _                             (it/create-and-confirm-invoice-ok
                                                  {:existing-application app
                                                   :transfer-batch-id    transfer-batch-id
                                                   :custom-row-count     3
                                                   :row-text-fn          (fn [idx] (format "1 - Rivi: %s" (inc idx)))
                                                   :row-ppu-fn           (fn [idx] (nth ["1" "5" "10"] idx))
                                                   :row-units-fn         (fn [idx] (nth ["2" "3" "4"] idx))
                                                   :sap-number           "112"})
                  _                             (it/create-and-confirm-invoice-ok
                                                  {:existing-application app
                                                   :transfer-batch-id    transfer-batch-id
                                                   :custom-row-count     2
                                                   :row-text-fn          (fn [idx] (format "2 - Rivi: %s" (inc idx)))
                                                   :row-ppu-fn           (fn [idx] (nth ["3" "8"] idx))
                                                   :row-units-fn         (fn [idx] (nth ["1" "5"] idx))
                                                   :sap-number           "456"})
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

              (let [as-data (sxml/xml->edn (sxml/parse-string body "utf-8"))]
                (fact "valid body"
                  as-data => {:InvoiceTransfer {:From     {:System {:Id "Lupapiste"}}
                                                :Invoices {:Invoice [{:Type               "EXTERNAL"
                                                                      :ApplicationContext {:Id             application-id
                                                                                           :PropertyId     "75312312341234"
                                                                                           :StreetAddress  "Kaivokatu 1"
                                                                                           :PostalCode     "00400"
                                                                                           :PermitType     "pientalo"
                                                                                           :PermitCategory "Rakennuslupa"}
                                                                      :Reference          "laskuviite-mikko"
                                                                      :Payee              {:Organization {:Id     "C-2"
                                                                                                          :Group  "C-3"
                                                                                                          :Sector "C-4"}}
                                                                      :Payer              {:Type         "ORGANIZATION"
                                                                                           :CustomerId   "112"
                                                                                           :Organization {:Address         {:City          "PIIPPOLA"
                                                                                                                            :Country       "FIN"
                                                                                                                            :PostalCode    "92620"
                                                                                                                            :StreetAddress "Lumekatu 5"}
                                                                                                          :Contact         {:FirstName   "Simo"
                                                                                                                            :LastName    "Simonen"
                                                                                                                            :Turvakielto "false"}
                                                                                                          :EDI             "003708130002"
                                                                                                          :EInvoiceAddress "1234"
                                                                                                          :Id              "0813000-2"
                                                                                                          :Name            "Feikki Oy"
                                                                                                          :Operator        "DABAFIHH"}}
                                                                      :Rows               {:Row [{:Product {:Name            " 1 - Rivi: 1"
                                                                                                            :Unit            "PIECE"
                                                                                                            :Quantity        "2.00"
                                                                                                            :DiscountPercent "0.00"
                                                                                                            :UnitPrice       "1.00"}}
                                                                                                 {:Product {:Name            " 1 - Rivi: 2"
                                                                                                            :Unit            "PIECE"
                                                                                                            :Quantity        "3.00"
                                                                                                            :DiscountPercent "0.00"
                                                                                                            :UnitPrice       "5.00"}}
                                                                                                 {:Product {:Name            " 1 - Rivi: 3"
                                                                                                            :Unit            "PIECE"
                                                                                                            :Quantity        "4.00"
                                                                                                            :DiscountPercent "0.00"
                                                                                                            :UnitPrice       "10.00"}}]}}
                                                                     {:Type               "EXTERNAL"
                                                                      :ApplicationContext {:Id             application-id
                                                                                           :PropertyId     "75312312341234"
                                                                                           :StreetAddress  "Kaivokatu 1"
                                                                                           :PostalCode     "00400"
                                                                                           :PermitType     "pientalo"
                                                                                           :PermitCategory "Rakennuslupa"}
                                                                      :Reference          "laskuviite-mikko"
                                                                      :Payee              {:Organization {:Id     "C-2"
                                                                                                          :Group  "C-3"
                                                                                                          :Sector "C-4"}}
                                                                      :Payer              {:Type         "ORGANIZATION"
                                                                                           :CustomerId   "456"
                                                                                           :Organization {:Address         {:City          "PIIPPOLA"
                                                                                                                            :Country       "FIN"
                                                                                                                            :PostalCode    "92620"
                                                                                                                            :StreetAddress "Lumekatu 5"}
                                                                                                          :Contact         {:FirstName   "Simo"
                                                                                                                            :LastName    "Simonen"
                                                                                                                            :Turvakielto "false"}
                                                                                                          :EDI             "003708130002"
                                                                                                          :EInvoiceAddress "1234"
                                                                                                          :Id              "0813000-2"
                                                                                                          :Name            "Feikki Oy"
                                                                                                          :Operator        "DABAFIHH"}}
                                                                      :Rows               {:Row [{:Product {:Name            " 2 - Rivi: 1"
                                                                                                            :Unit            "PIECE"
                                                                                                            :Quantity        "1.00"
                                                                                                            :DiscountPercent "0.00"
                                                                                                            :UnitPrice       "3.00"}}
                                                                                                 {:Product {:Name            " 2 - Rivi: 2"
                                                                                                            :Unit            "PIECE"
                                                                                                            :Quantity        "5.00"
                                                                                                            :DiscountPercent "0.00"
                                                                                                            :UnitPrice       "8.00"}}]}}]}}}))))
          (facts "2 character country code in payer works"
            (fact "set default config and update it with alpha 2 country code"
              (it/set-invoicing-config-ok {:invoicing-config (assoc default-config
                                                                    :use-alpha-2-country-code? true)}))
            (facts "happy path where a company is the payer"
              (let [transfer-batch-id     (it/create-transfer-batch-ok)
                    {_application-id :id
                     :as             app} (it/dummy-submitted-application it/applicant)
                    ;; Two invoices, 3 + 2 rows
                    _                     (it/create-and-confirm-invoice-ok
                                            {:existing-application app
                                             :transfer-batch-id    transfer-batch-id
                                             :custom-row-count     3
                                             :row-text-fn          (fn [idx] (format "1 - Rivi: %s" (inc idx)))
                                             :row-ppu-fn           (fn [idx] (nth ["1" "5" "10"] idx))
                                             :row-units-fn         (fn [idx] (nth ["2" "3" "4"] idx))
                                             :sap-number           "112"})
                    _                     (it/create-and-confirm-invoice-ok
                                            {:existing-application app
                                             :transfer-batch-id    transfer-batch-id
                                             :custom-row-count     2
                                             :row-text-fn          (fn [idx] (format "2 - Rivi: %s" (inc idx)))
                                             :row-ppu-fn           (fn [idx] (nth ["3" "8"] idx))
                                             :row-units-fn         (fn [idx] (nth ["1" "5"] idx))
                                             :sap-number           "456"})
                    _                     (it/copy-business-details-into-payer-document-ok {:application app
                                                                                            :applicant   it/applicant
                                                                                            :reference   "laskuviite-mikko"})
                    {:keys [body]}        (it/download-transfer-batch-xml {:transfer-batch-id transfer-batch-id})
                    as-data               (sxml/xml->edn (sxml/parse-string body "utf-8"))]

                (fact "valid country code in place"
                  (-> as-data
                      :InvoiceTransfer
                      :Invoices
                      :Invoice
                      first
                      :Payer
                      :Organization
                      :Address
                      :Country) => "FI")))))))))
