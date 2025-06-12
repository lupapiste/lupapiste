(ns lupapalvelu.migration.hide-itest
  (:require [lupapalvelu.invoices.test-util :as it]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer [command ok? with-local-actions with-test-system]]
            [lupapalvelu.migration.hide :as hide]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all])
  (:import [clojure.lang ExceptionInfo]))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (with-local-actions
    (let [now 1234]
      (fact "happy path"
        (let [{:keys [id]} (it/dummy-submitted-application it/applicant)
              old          (mongo/by-id :applications id)]
          (fact "single app hidden"
            (hide/hide-applications #{id} "a note" now) => 1

            (let [hidden (mongo/by-id :hidden-applications id)]
              (fact "application moved"
                (mongo/by-id :applications id) => nil?
                hidden => some?)
              (fact "Hidden includes a sheriff note"
                (= hidden
                   (merge old
                          {:_sheriff-notes [{:note    "a note"
                                             :created now}]})) => truthy)))))

      (fact "invoices block hiding"
        (it/toggle-invoicing-ok true)
        (let [{{app-id :id}     :application
               {invoice-id :id} :invoice} (it/create-draft-invoice-with-rows-ok
                                            {:custom-row-count 0})]
          (fact "cannot hide"
            (hide/hide-applications #{app-id} "a note" now)
            => (throws ExceptionInfo
                       #"Cannot hide apps with invoices!"
                       (fn [e]
                         (= (ex-data e)
                            {:invoices #{invoice-id}}))))))

      (fact "links block hiding"
        (let [{app-1-id :id} (it/dummy-submitted-application it/applicant)
              {app-2-id :id} (it/dummy-submitted-application it/applicant)
              link-id        (mongo/create-id)]
          (mongo/insert :app-links {:id   link-id
                                    :link [app-1-id app-2-id]})
          (fact "cannot hide"
            (hide/hide-applications #{app-1-id} "a note" now)
            => (throws ExceptionInfo
                       #"Cannot hide linked app!"
                       (fn [e]
                         (= (ex-data e)
                            {:app-links #{link-id}}))))))

      (fact "conversion blocks hiding"
        (let [{:keys [id]} (it/dummy-submitted-application it/applicant)]
          (mongo/update-by-id :applications id {$set {:facta-imported true}})
          (fact "cannot hide"
            (hide/hide-applications #{id} "a note" now)
            => (throws ExceptionInfo
                       #"Cannot hide converted applications!"
                       (fn [e]
                         (= (ex-data e)
                            {:applications #{id}}))))))

      (fact "verdicts block hiding"
        (let [{:keys [id]} (it/dummy-submitted-application it/applicant)]
          (command it/authority :check-for-verdict :id id) => ok?
          (fact "cannot hide"
            (hide/hide-applications #{id} "a note" now)
            => (throws ExceptionInfo
                       #"Cannot hide applications with verdicts!"
                       (fn [e]
                         (= (ex-data e)
                            {:applications #{id}}))))
          (fact "... but not for paper permits"
            (mongo/update-by-id :applications id
                                {$set {:primaryOperation.name :aiemmalla-luvalla-hakeminen}})
            (hide/hide-applications #{id} "a note" now) => 1))))))
