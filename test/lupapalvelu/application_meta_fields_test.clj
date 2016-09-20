(ns lupapalvelu.application-meta-fields-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.user :as usr]
            [lupapalvelu.test-util :as test-util]
            [lupapalvelu.application-meta-fields :as amf]))

(testable-privates lupapalvelu.application-meta-fields count-unseen-comments count-unseen-statements count-unseen-verdicts count-attachments-requiring-action indicator-sum)

(facts "foreman-index-update"
  (let [expected-firstname "Etunimi"
        expected-name (str "Ilkka " expected-firstname) ; Ilkka from dummy doc genaration
        tj-v1 (assoc-in (test-util/dummy-doc "tyonjohtaja") [:data :henkilotiedot :etunimi :value] expected-firstname)
        tj-v2 (assoc-in (test-util/dummy-doc "tyonjohtaja-v2") [:data :henkilotiedot :etunimi :value] expected-firstname)]

    (fact "TJ v1"
      (let [update (amf/foreman-index-update {:documents [tj-v1]})]
        (get-in update ["$set" :foreman]) => expected-name
        ; Dummy doc contais first value for select in schema
        (get-in update ["$set" :foremanRole]) => "KVV-ty\u00F6njohtaja"))

    (fact "TJ v2"
      (let [update (amf/foreman-index-update {:documents [tj-v2]})]
        (get-in update ["$set" :foreman]) => expected-name
        ; Dummy doc contais first value for select in schema
        (get-in update ["$set" :foremanRole]) => "vastaava ty\u00F6njohtaja"))))

(facts "count-unseen-comments"
  (count-unseen-comments nil nil) => 0
  (count-unseen-comments {} {}) => 0
  (count-unseen-comments {:id "user1"} {:comments [{:created 10 :text "a" :user {:id "user2"}}]}) => 1
  (count-unseen-comments {:id ..id..} {:comments [{:created 10 :text "a" :user {:id ..id..}}]}) => 0
  (count-unseen-comments {:id "user1"} {:comments [{:created 0 :text "a" :user {:id "user2"}}]}) => 0
  (count-unseen-comments {:id "user1"} {:comments [{:created 10 :text "" :user {:id "user2"}}]}) => 0)

(facts "count-unseen-statements"
  (count-unseen-statements nil nil) => 0
  (count-unseen-statements {} {}) => 0
  (count-unseen-statements {:id "user1" :email "person1@example.com"} {:statements []}) => 0
  (count-unseen-statements {:id "user1" :email "person1@example.com"} {:statements [{}]}) => 0
  (count-unseen-statements {:id "user1" :email "person1@example.com"} {:statements [{:given 0}]}) => 0
  (count-unseen-statements {:id "user1" :email "person1@example.com"} {:statements [{:given 0 :person {:email "person2@example.com"}}]}) => 0
  (count-unseen-statements {:id "user1" :email "person1@example.com"} {:statements [{:given 1 :person {:email "person2@example.com"}}]}) => 1
  (count-unseen-statements {:id "user1" :email "person1@example.com"} {:statements [{:given 1 :person {:email "person1@example.com"}}]}) => 0
  (count-unseen-statements {:id "user1" :email "person1@example.com"} {:statements [{:given 1 :person {:email "PERSON1@example.com"}}]}) => 0
  (count-unseen-statements {:id "user1" :email "person1@example.com"} {:statements [{:given 1 :person {:email "person2@example.com"}}] :_statements-seen-by {:user1 1}}) => 0)

(facts "count-unseen-verdicts"
  (count-unseen-verdicts nil nil) => 0
  (count-unseen-verdicts {} {}) => 0
  (count-unseen-verdicts {} {:verdict [{}]}) => 0
  (count-unseen-verdicts {:role "applicant"} {:verdicts [{}]}) => 0
  (count-unseen-verdicts {:role "applicant"} {:verdicts [{:timestamp 1}]}) => 1
  (count-unseen-verdicts {:role "applicant"} {:verdicts [{:timestamp 1} {:timestamp 2}]}) => 2
  (count-unseen-verdicts {:role "applicant"} {:verdicts [{:timestamp 0}]}) => 0
  (count-unseen-verdicts {:role "authority"} {:verdicts [{:timestamp 0}]}) => 0
  (count-unseen-verdicts {:role "authority"} {:verdicts [{:timestamp 1}]}) => 0
  (count-unseen-verdicts {:id "user1" :role "applicant"} {:verdicts [{:timestamp 1}] :_verdicts-seen-by {:user1 1}}) => 0)

(facts "count-attachments-requiring-action"
  (count-attachments-requiring-action nil nil) => 0
  (count-attachments-requiring-action {} {}) => 0
  (count-attachments-requiring-action {:role "applicant"} {:attachments [{:state "requires_user_action"}]}) => 0
  (count-attachments-requiring-action {:role "applicant"} {:attachments [{:state "requires_user_action" :versions []}]}) => 0
  (count-attachments-requiring-action {:role "applicant"} {:attachments [{:state "requires_user_action" :versions [{:version {}}]}]}) => 1
  (count-attachments-requiring-action {:role "applicant"} {:attachments [{:state "requires_authority_action" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "applicant"} {:attachments [{:state "ok" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "authority"} {:attachments [{:state "requires_authority_action", :latestVersion {:created 1}, :versions [{:version {}}]}]}) => 1
  (count-attachments-requiring-action {:role "authority"} {:attachments [{:state "requires_authority_action", :latestVersion {:created 1 :user usr/batchrun-user-data}, :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "authority"} {:_attachment_indicator_reset 1, :attachments [{:state "requires_authority_action", :latestVersion {:created 2}, :versions [{:version {}}]}]}) => 1
  (count-attachments-requiring-action {:role "authority"} {:_attachment_indicator_reset 2, :attachments [{:state "requires_authority_action", :latestVersion {:created 2}, :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "authority"} {:attachments [{:state "requires_user_action" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "authority"} {:attachments [{:state "ok" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "authorityAdmin"} {:attachments [{:state "requires_authority_action" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "authorityAdmin"} {:attachments [{:state "requires_user_action" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "authorityAdmin"} {:attachments [{:state "ok" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "admin"} {:attachments [{:state "requires_authority_action" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "admin"} {:attachments [{:state "requires_user_action" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "admin"} {:attachments [{:state "ok" :versions [{:version {}}]}]}) => 0)

(facts "indicator-sum"
  (indicator-sum nil nil) => 0
  (indicator-sum {} {}) => 0
  (indicator-sum nil {:unseenStatements 1}) => 1
  (indicator-sum nil {:unseenVerdicts 1}) => 1
  (indicator-sum nil {:attachmentsRequiringAction 1}) => 0
  (indicator-sum nil {:unseenStatements 1 :unseenVerdicts 3 :attachmentsRequiringAction 5}) => 4
  (indicator-sum nil {:unseenStatements 1 :unseenVerdicts 3 :attachmentsRequiringAction 5 :unseenComments 7}) => 4)
