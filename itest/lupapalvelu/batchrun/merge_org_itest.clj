(ns lupapalvelu.batchrun.merge-org-itest
  (:require [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp]
            [lupapalvelu.batchrun.merge-org :as batchrun]
            [lupapalvelu.conversion.itest-util :as conv-test]
            [lupapalvelu.invoices.test-util :as itu]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itest :refer [command ok? with-local-actions with-test-system]]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.core :as sade]
            [sade.date :as date]))

(set! *warn-on-reflection* true)

(def applicant itest/mikko)           ; Hakijat
(def biller itest/laura)              ; Laskuttaja
(def authority itest/sonja)           ; Viranomaiset

(defn dummy-submitted-application [apikey]
  (itest/create-and-submit-local-application
    apikey
    :operation "pientalo"
    :propertyId itest/sipoo-property-id
    :x "385770.46" :y "6672188.964"
    :address "Kaivokatu 1"))

(defn- find-user-authz
  [email]
  (let [user (mongo/select-one :users {:email email})]
    (fact "can find user" user => some?)
    (select-keys user [:role :orgAuthz :_sheriff-notes])))

(with-test-system {:fixture "minimal"}
  (with-local-actions
    (facts "examples where batchrun aborts"
      (let [dest-org "069-R"]
        (fact "Organization not found"
          (batchrun/do-merge! {:dest-org dest-org
                               :src-org  "000-A"
                               :now      (sade/now)
                               :ticket   "FOO-123"}) => (throws #"Could not find organization"))
        (fact "cannot merge unto itself"
          (batchrun/do-merge! {:dest-org dest-org
                               :src-org  dest-org
                               :now      (sade/now)
                               :ticket   "FOO-123"}) => (throws #"Same target and source organization"))
        (fact "source has price catalogues"
          (let [source-org "297-R"]
            (itu/insert-catalog {:from            (date/timestamp (date/today))
                                 :until           (date/timestamp (date/end-of-day (date/now)))
                                 :prefix          "one"
                                 :organization-id source-org})

            (batchrun/do-merge! {:dest-org dest-org
                                 :src-org  source-org
                                 :now      (sade/now)
                                 :ticket   "FOO-123"})) => (throws #"Pre-checks failed"))
        (fact "source has transfer batches"
          (let [source-org "753-R"]
            (itu/toggle-invoicing-ok true)
            (itu/create-transfer-batch-ok {:user   biller
                                           :org-id source-org})

            (batchrun/do-merge! {:dest-org dest-org
                                 :src-org  source-org
                                 :now      (sade/now)
                                 :ticket   "FOO-123"})) => (throws #"Pre-checks failed"))))))

(with-test-system {:fixture "minimal"}
  (with-local-actions
    (facts "migrate authority users"
      (let [now        (sade/now)
            source-org "753-R"
            dest-org   "186-R"]

        (fact "add luukas as reader to destination"
          (mongo/update-by-query :users {:email "luukas.lukija@sipoo.fi"} {$set {:orgAuthz.186-R ["reader"]}}))

        (fact "existing authority users before batchrun"
          (fact "admin"
            (find-user-authz "admin@sipoo.fi")
            => {:role     "authority"
                :orgAuthz {:753-R ["authorityAdmin"]}})
          (fact "sonja"
            (find-user-authz "sonja.sibbo@sipoo.fi")
            => {:role     "authority"
                :orgAuthz {:753-R         ["authority" "approver"]
                           :753-YA        ["authority" "approver"]
                           :998-R-TESTI-2 ["authority" "approver"]}})
          (fact "ronja"
            (find-user-authz "ronja.sibbo@sipoo.fi")
            => {:role     "authority"
                :orgAuthz {:753-R  ["authority" "authorityAdmin"]
                           :753-YA ["authorityAdmin"]}})
          (fact "luukas"
            (find-user-authz "luukas.lukija@sipoo.fi")
            => {:role     "authority"
                :orgAuthz {:186-R ["reader"]
                           :753-R ["reader"]}})
          (fact "kosti"
            (find-user-authz "kosti.kommentoija@sipoo.fi")
            => {:role     "authority"
                :orgAuthz {:753-R ["commenter"]}}))

        (fact "Batchrun is successful"
          (batchrun/do-merge! {:dest-org dest-org
                               :src-org  source-org
                               :now      now
                               :ticket   "FOO-123"}) => nil?)

        (fact "existing authority users after batchrun"
          (fact "admin retains old authorityAdmin privilege"
            (find-user-authz "admin@sipoo.fi")
            => {:role           "authority"
                :orgAuthz       {:753-R ["authorityAdmin"]
                                 :186-R ["authorityAdmin"]}
                :_sheriff-notes [{:note   "Granted access to 186-R"
                                  :ticket "FOO-123"
                                  :now    now}]})
          (fact "sonja moved to destination org"
            (find-user-authz "sonja.sibbo@sipoo.fi")
            => {:role           "authority"
                :orgAuthz       {:186-R         ["authority" "approver"]
                                 :753-YA        ["authority" "approver"]
                                 :998-R-TESTI-2 ["authority" "approver"]}
                :_sheriff-notes [{:note "Granted access to 186-R" :ticket "FOO-123" :now now}
                                 {:note "Removed access from 753-R" :ticket "FOO-123" :now now}]})
          (fact "ronja becomes auth & authAdmin in destination org but also retains authAdmin in src org"
            (find-user-authz "ronja.sibbo@sipoo.fi")
            => {:role           "authority"
                :orgAuthz       {:186-R  ["authority" "authorityAdmin"]
                                 :753-R  ["authorityAdmin"]
                                 :753-YA ["authorityAdmin"]}
                :_sheriff-notes [{:note "Granted access to 186-R" :ticket "FOO-123" :now now}
                                 {:note "Removed other authorizations from 753-R" :ticket "FOO-123" :now now}]})
          (fact "luukas get no new auths as he already had access to destination org"
            (find-user-authz "luukas.lukija@sipoo.fi")
            => {:role           "authority"
                :orgAuthz       {:186-R ["reader"]}
                :_sheriff-notes [{:note   "Removed access from 753-R"
                                  :ticket "FOO-123"
                                  :now    now}]})
          (fact "kosti moved to destination org"
            (find-user-authz "kosti.kommentoija@sipoo.fi")
            => {:role           "authority"
                :orgAuthz       {:186-R ["commenter"]}
                :_sheriff-notes [{:note "Granted access to 186-R" :ticket "FOO-123" :now now}
                                 {:note "Removed access from 753-R" :ticket "FOO-123" :now now}]}))

        (fact "source organization"
          (let [org (mongo/by-id :organizations source-org)]
            (fact "has sheriff note"
              (:_sheriff-notes org) => [{:note   "Deactivated due to merge with 186-R"
                                         :ticket "FOO-123"
                                         :now    now}
                                        {:note    "Dropped scope fields due to merge"
                                         :ticket  "FOO-123"
                                         :now     now
                                         :dropped [{:bulletins    {:descriptions-from-backend-system false
                                                                   :enabled                          true
                                                                   :notification-email               "sonja.sibbo@sipoo.fi"}
                                                    :municipality "753"
                                                    :pate         {:enabled false}
                                                    :permitType   "R"}]}])))

        (fact "destination organization"
          (let [org (mongo/by-id :organizations dest-org)]
            (fact "has sheriff note"
              (:_sheriff-notes org) => [{:note   "Merged scopes from 753-R"
                                         :ticket "FOO-123"
                                         :now    now}])))))))

(defn test-xml []
  (conv-test/read-xml "dev-resources/krysp/verdict-r.xml"))

;; Creating a bulletin (and a verdict) was painfully slow -> separate test
(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (with-local-actions
    (facts "applications and application bulletins"
      (let [now                          (sade/now)
            source-org                   "753-R"
            dest-org                     "186-R"
            {app-id :id :as application} (dummy-submitted-application applicant)]

        (fact "application is in source org" (:organization application) => source-org)

        (fact "create bulletin"
          (command authority :update-app-bulletin-op-description :id app-id :description "foo") => ok?
          ;; This takes a moment..
          (command authority :check-for-verdict :id app-id) => ok?

          (provided
            (krysp/get-application-xml-by-application-id anything) => (test-xml)))

        (let [[{bulletin-id :id :as bulletin} :as bulletins] (mongo/select :application-bulletins {})]
          (fact "single bulletin" (count bulletins) => 1)
          (fact "bulletin is in source org" (get-in bulletin [:versions 0 :organization]) => source-org)

          (fact "Batchrun is successful"
            (batchrun/do-merge! {:dest-org dest-org
                                 :src-org  source-org
                                 :now      now
                                 :ticket   "FOO-123"}) => nil?)

          (let [application (mongo/by-id :applications app-id)
                bulletin    (mongo/by-id :application-bulletins bulletin-id)]
            (fact "application moved" (:organization application) => dest-org)
            (fact "application has sheriff note" (:_sheriff-notes application)
                  => [{:note   "Moved from 753-R"
                       :ticket "FOO-123"
                       :now    now}])
            (fact "bulletin moved" (get-in bulletin [:versions 0 :organization]) => dest-org)))))))
