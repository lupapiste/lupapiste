(ns lupapalvelu.invoices.transfer-batch-itest
  (:require [lupapalvelu.invoices.test-util :as it]
            [lupapalvelu.itest-util :refer [expected-failure? ok? with-local-actions with-test-system]]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]))

(set! *warn-on-reflection* true)

(defn err [error]
  (partial expected-failure? error))

(with-test-system {:fixture "minimal"}
  (with-local-actions

    (facts "transfer batch"
      (fact "Cannot manipulate if invoicing not enabled"
        (doseq [resp [(it/create-transfer-batch {})
                      (it/delete-transfer-batch {:transfer-batch-id "1"})
                      (it/rename-transfer-batch {:transfer-batch-id "1" :name "foo"})
                      (it/open-transfer-batch {:transfer-batch-id "1"})
                      (it/close-transfer-batch {:transfer-batch-id "1"})]]
          (fact "not enabled" resp => (err :error.invoicing-disabled))))

      (it/toggle-invoicing-ok true)

      (fact "non-billers have no authority"
        (doseq [user [it/applicant it/reader it/authority it/municipal-authority it/admin]
                resp [(it/create-transfer-batch {:user user})
                      (it/delete-transfer-batch {:user user :transfer-batch-id "1"})
                      (it/rename-transfer-batch {:user user :transfer-batch-id "1" :name "foo"})
                      (it/open-transfer-batch {:user user :transfer-batch-id "1"})
                      (it/close-transfer-batch {:user user :transfer-batch-id "1"})]]
          (fact "not authorized" resp => (err :error.unauthorized))))

      ;; TODO: add more checks regarding tb history

      (fact "basic CRUD operations"
        (let [transfer-batch-id (it/create-transfer-batch-ok)]
          (fact "id returned"
            transfer-batch-id => string?)

          (fact "initial fields"
            (it/get-transfer-batch-db transfer-batch-id)
            => (contains {:id              transfer-batch-id
                          :state           "open"
                          :created         int?
                          :organization-id "753-R"
                          :history         (just [(just {:user   it/biller-user
                                                         :time   int?
                                                         :action "create"})])}))

          (fact "name must be str"
            (it/rename-transfer-batch {:transfer-batch-id transfer-batch-id :name 42})
            => (err :error.illegal-value:schema-validation))

          (fact "can rename"
            (it/rename-transfer-batch {:transfer-batch-id transfer-batch-id :name "new name"}) => ok?
            (it/get-transfer-batch-db transfer-batch-id) => (contains {:name "new name"}))

          (fact "cannot open an already open transfer batch"
            (it/open-transfer-batch {:transfer-batch-id transfer-batch-id})
            => (err :error.transfer-batch.wrong-state))

          (fact "cannot close an empty transfer batch"
            (it/close-transfer-batch {:transfer-batch-id transfer-batch-id})
            => (err :error.transfer-batch.empty))

          (it/create-and-confirm-invoice-ok {:transfer-batch-id transfer-batch-id})

          (fact "can close non-empty transfer batch"
            (let [{:keys [closed] :as resp} (it/close-transfer-batch {:transfer-batch-id transfer-batch-id})]
              (fact "response"
                resp => ok?
                closed => int?)

              (fact "fields after closing"
                (it/get-transfer-batch-db transfer-batch-id)
                => (contains {:id              transfer-batch-id
                              :state           "closed"
                              :created         int?
                              :closed          int?
                              :organization-id "753-R"
                              :history         (just [(just {:user   it/biller-user
                                                             :time   int?
                                                             :action "create"})
                                                      (just {:user   it/biller-user
                                                             :time   int?
                                                             :action "close"})])}))))

          (fact "can re-open after closing"
            (let [{:keys [opened] :as resp} (it/open-transfer-batch {:transfer-batch-id transfer-batch-id})]
              (fact "response"
                resp => ok?
                opened => int?)

              (fact "fields after re-opening"
                (it/get-transfer-batch-db transfer-batch-id)
                => (contains {:id              transfer-batch-id
                              :state           "open"
                              :created         int?
                              :organization-id "753-R"
                              :history         (just [(just {:user   it/biller-user
                                                             :time   int?
                                                             :action "create"})
                                                      (just {:user   it/biller-user
                                                             :time   int?
                                                             :action "close"})
                                                      (just {:user   it/biller-user
                                                             :time   int?
                                                             :action "open"})])}))))

          (facts "with included invoices"
            (let [{app-id :id :as app} (it/dummy-submitted-application it/applicant)
                  invoice-id           (it/new-invoice-ok app-id)
                  args                 {:app-id     app-id
                                        :invoice-id invoice-id}]

              (let [invoice (it/get-invoice-db invoice-id)]
                (fact "invoice not yet batched"
                  (:transfer-batch-id invoice) => nil?))

              (fact "cannot add non-confirmed invoice"
                (it/add-invoice-to-transfer-batch (assoc args :transfer-batch-id transfer-batch-id))
                => (err :error.invalid-invoice))

              (let [operation-id (it/app->operation app)]
                (fact "cannot confirm a draft invoice without operation nor rows"
                  (it/set-invoice-state (assoc args :state "checked")) => (err :error.invoice.no-rows)
                  (it/add-invoice-operation-ok (assoc args :operation-id operation-id))
                  (it/add-invoice-row-ok (assoc args :operation-id operation-id :row-id "custom"))))

              (fact "confirming an invoice adds it to an existing transfer batch"
                (it/set-invoice-state-ok (assoc args :state "checked"))
                (it/set-biller-invoice-state-ok (assoc args :state "confirmed"))

                (fact "invoice after confirmed"
                  (it/get-invoice-db invoice-id) => (contains {:id                invoice-id
                                                               :application-id    app-id
                                                               :transfer-batch-id transfer-batch-id
                                                               :state             "confirmed"})))

              (fact "cannot delete non-empty batch"
                (it/delete-transfer-batch {:transfer-batch-id transfer-batch-id}) => (err :error.transfer-batch.not-empty))

              (fact "can move invoice to another batch"
                (let [new-batch-id (it/create-transfer-batch-ok)]
                  (fact "moving successful"
                    (it/add-invoice-to-transfer-batch (assoc args :transfer-batch-id new-batch-id)) => ok?)))

              (fact "but not into a closed batch"
                (let [new-batch-id (it/create-transfer-batch-ok)
                      _            (it/create-and-confirm-invoice-ok {:transfer-batch-id new-batch-id})]
                  (it/close-transfer-batch-ok {:transfer-batch-id new-batch-id})
                  (fact "moving unsuccessful"
                    (it/add-invoice-to-transfer-batch (assoc args :transfer-batch-id new-batch-id)) => (err :error.not-found))))

              (fact "unconfirming an invoice removes it from a transfer batch"
                (fact "has transfer batch before change"
                  (:transfer-batch-id (it/get-invoice-db invoice-id)) => some?)
                (it/set-biller-invoice-state-ok (assoc args :state "checked"))
                (fact "has no transfer batch after change"
                  (:transfer-batch-id (it/get-invoice-db invoice-id)) => nil?))

              (fact "closing a transfer batch marks contained invoices as sent"
                (it/set-biller-invoice-state-ok (assoc args :state "confirmed"))
                (fact "has transfer batch before closing"
                  (:transfer-batch-id (it/get-invoice-db invoice-id)) => transfer-batch-id)
                (it/close-transfer-batch-ok {:transfer-batch-id transfer-batch-id})
                (fact "closing marks invoice as transferred"
                  (it/get-invoice-db invoice-id) => (contains {:transfer-batch-id transfer-batch-id
                                                               :state             "transferred"}))

                (fact "cannot alter invoice state"
                  (it/set-biller-invoice-state {:app-id app-id :invoice-id invoice-id :state "checked"})
                  => (err :error.invoice.illegal-state))
                (fact "cannot move invoice to another batch"
                  (let [new-batch-id (it/create-transfer-batch-ok)]
                    (fact "moving unsuccessful"
                      (it/add-invoice-to-transfer-batch (assoc args :transfer-batch-id new-batch-id))
                      => (err :error.invoice.illegal-state)))))))

          (let [transfer-batch-id (it/create-transfer-batch-ok)]
            (fact "can delete an empty batch"
              (it/delete-transfer-batch {:transfer-batch-id transfer-batch-id}) => ok?
              (it/get-transfer-batch-db transfer-batch-id) => nil?))))

      (it/drop-test-data)

      (fact "allocating invoices to batches"
        (fact "multiple empty batches"
          (let [tb-id-1 (it/create-transfer-batch-ok)
                _       (it/create-transfer-batch-ok)
                _       (it/create-transfer-batch-ok)
                _       (it/create-transfer-batch-ok)]
            (fact "all invoices are assigned to the first empty batch"
              (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-1})
              (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-1})
              (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-1})
              (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-1})))
          (it/drop-test-data))

        (fact "closed batches are skipped"
          (let [tb-id-1 (it/create-transfer-batch-ok)
                tb-id-2 (it/create-transfer-batch-ok)
                _       (it/create-transfer-batch-ok)
                _       (it/create-transfer-batch-ok)
                _       (it/create-transfer-batch-ok)]
            (it/create-and-confirm-invoice-ok {:transfer-batch-id tb-id-1})
            (it/close-transfer-batch-ok {:transfer-batch-id tb-id-1})
            (fact "all invoices are assigned to the second empty open batch"
              (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-2})
              (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-2})
              (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-2})
              (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-2})))
          (it/drop-test-data))

        (fact "when org has limit (3) on batch size"
          (it/merge-to-invoicing-config-ok {:max-invoice-row-count-in-transfer-batch 3})

          (fact "single row per invoice => three invoices per batch"
            (let [tb-id-1 (it/create-transfer-batch-ok)
                  tb-id-2 (it/create-transfer-batch-ok)
                  tb-id-3 (it/create-transfer-batch-ok)
                  _       (it/create-transfer-batch-ok)]
              (fact "invoices are grouped as [3/3, 3/3, 1/3] into three batches"
                (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-1})
                (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-1})
                (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-1})
                (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-2})
                (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-2})
                (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-2})
                (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-3})))
            (it/drop-test-data))

          (fact "two rows per invoice => one invoices per batch"
            (let [tb-id-1 (it/create-transfer-batch-ok)
                  tb-id-2 (it/create-transfer-batch-ok)
                  tb-id-3 (it/create-transfer-batch-ok)
                  _       (it/create-transfer-batch-ok)]
              (fact "invoices are grouped as [2/3, 2/3, 2/3] into three batches of size 3"
                (it/create-and-confirm-invoice-ok {:custom-row-count 2}) => (contains {:transfer-batch-id tb-id-1})
                (it/create-and-confirm-invoice-ok {:custom-row-count 2}) => (contains {:transfer-batch-id tb-id-2})
                (it/create-and-confirm-invoice-ok {:custom-row-count 2}) => (contains {:transfer-batch-id tb-id-3}))

              (fact "earlier non-empty batches are refilled if suitable invoices are supplied"
                (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-1})
                (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-2})
                (it/create-and-confirm-invoice-ok) => (contains {:transfer-batch-id tb-id-3}))))
          (it/drop-test-data))

        (fact "invoice with too many rows"
          (let [{app-id :id :as app} (it/dummy-submitted-application it/applicant)
                invoice-id           (it/new-invoice-ok app-id)
                operation-id         (it/app->operation app)
                args                 {:app-id       app-id
                                      :invoice-id   invoice-id
                                      :operation-id operation-id}]
            (fact "can add operation and 3 rows"
              (it/add-invoice-operation-ok args)
              (it/add-invoice-row (assoc args :row-id "custom")) => ok?
              (it/add-invoice-row (assoc args :row-id "custom")) => ok?
              (it/add-invoice-row (assoc args :row-id "custom")) => ok?)
            (fact ".. but adding more fails"
              (it/add-invoice-row (assoc args :row-id "custom")) => (err :error.invoice.too-many-rows)))
          (it/drop-test-data)))

      (it/drop-test-data))))
