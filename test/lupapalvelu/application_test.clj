(ns lupapalvelu.application-test
  (:use [lupapalvelu.application]
        [midje.sweet])
  (:require [lupapalvelu.operations :as operations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.schemas :as schemas]))

(facts "count-unseen-comment"
  (count-unseen-comment {:id "user1"} {:comments [{:created 10 :text "a" :user {:id "user2"}}]}) => 1
  (count-unseen-comment {:id ..id..} {:comments [{:created 10 :text "a" :user {:id ..id..}}]}) => 0
  (count-unseen-comment {:id "user1"} {:comments [{:created 0 :text "a" :user {:id "user2"}}]}) => 0
  (count-unseen-comment {:id "user1"} {:comments [{:created 10 :text "" :user {:id "user2"}}]}) => 0)

(facts "count-attachments-requiring-action"
  (count-attachments-requiring-action {:role "applicant"} {:attachments [{:state "requires_user_action"}]}) => 0
  (count-attachments-requiring-action {:role "applicant"} {:attachments [{:state "requires_user_action" :versions []}]}) => 0
  (count-attachments-requiring-action {:role "applicant"} {:attachments [{:state "requires_user_action" :versions [{:version {}}]}]}) => 1
  (count-attachments-requiring-action {:role "applicant"} {:attachments [{:state "requires_authority_action" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "applicant"} {:attachments [{:state "ok" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "authority"} {:attachments [{:state "requires_authority_action" :versions [{:version {}}]}]}) => 1
  (count-attachments-requiring-action {:role "authority"} {:attachments [{:state "requires_user_action" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "authority"} {:attachments [{:state "ok" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "authorityAdmin"} {:attachments [{:state "requires_authority_action" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "authorityAdmin"} {:attachments [{:state "requires_user_action" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "authorityAdmin"} {:attachments [{:state "ok" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "admin"} {:attachments [{:state "requires_authority_action" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "admin"} {:attachments [{:state "requires_user_action" :versions [{:version {}}]}]}) => 0
  (count-attachments-requiring-action {:role "admin"} {:attachments [{:state "ok" :versions [{:version {}}]}]}) => 0)

(facts "sorting parameter parsing"
  (make-sort {:iSortCol_0 0 :sSortDir_0 "asc"})  => {:infoRequest 1}
  (make-sort {:iSortCol_0 1 :sSortDir_0 "desc"}) => {:address -1}
  (make-sort {:iSortCol_0 2 :sSortDir_0 "desc"}) => {}
  (make-sort {:iSortCol_0 3 :sSortDir_0 "desc"}) => {}
  (make-sort {:iSortCol_0 4 :sSortDir_0 "asc"})  => {:submitted 1}
  (make-sort {:iSortCol_0 5 :sSortDir_0 "desc"}) => {}
  (make-sort {:iSortCol_0 6 :sSortDir_0 "desc"}) => {}
  (make-sort {:iSortCol_0 7 :sSortDir_0 "asc"})  => {:modified 1}
  (make-sort {:iSortCol_0 8 :sSortDir_0 "asc"})  => {:state 1}
  (make-sort {:iSortCol_0 9 :sSortDir_0 "asc"})  => {:authority 1}
  (make-sort {:iSortCol_0 {:injection "attempt"}
              :sSortDir_0 "; drop database;"})   => {}
  (make-sort {})                                 => {}
  (make-sort nil)                                => {})

(fact "update-document"
  (update-application {:application ..application.. :data {:id ..id..}} ..changes..) => truthy
  (provided
    ..application.. =contains=> {:id ..id..}
    (mongo/update :applications {:_id ..id..} ..changes..) => true))


(def make-documents #'lupapalvelu.application/make-documents)

(defn find-by-schema? [docs schema-name]
  (domain/get-document-by-name {:documents docs} schema-name))

(defn has-schema? [schema] (fn [docs] (find-by-schema? docs schema)))

(comment
  (facts
    (against-background (operations/operations :foo) => {:schema "foo" :required ["a" "b"] :attachments []}
      (operations/operations :bar) => {:schema "bar" :required ["b" "c"] :attachments []}
      (schemas/schemas "hakija")   => {:info {:name "hakija"}, :data []}
      (schemas/schemas "foo")      => {:info {:name "foo"}, :data []}
      (schemas/schemas "a")        => {:info {:name "a"}, :data []}
      (schemas/schemas "b")        => {:info {:name "b"}, :data []}
      (schemas/schemas "bar")      => {:info {:name "bar"}, :data []}
      (schemas/schemas "c")        => {:info {:name "c"}, :data []})
    (let [user {:name "foo"}
          created 12345]
      (let [docs (make-documents user created nil :foo)]
        (count docs) => 4
        (find-by-schema? docs "hakija") => (contains {:data {:henkilo {:henkilotiedot user}}})
        docs => (has-schema? "foo")
        docs => (has-schema? "a")
        docs => (has-schema? "b"))
      ; use-case: "create-application"
      (let [docs (make-documents user created [{:schema {:name "hakija"}}] :foo)]
        (count docs) => 4)
      ; use-case "add-operation"
      (let [docs (make-documents user created nil :foo)
            docs (make-documents nil created docs :bar)]
        (count docs) => 2
        docs => (has-schema? "bar")
        docs => (has-schema? "c")))))

(comment
  ; Should rewrite this as a couple of unit tests
  (fact "Assert that proper documents are created"

    (let [id (:id (create-app :operation "foo"))
          app (:application (query pena :application :id id))
          docs (:documents app)]
      (count docs) => 4 ; foo, a, b and "hakija".
      (find-by-schema? docs "foo") => truthy
      (find-by-schema? docs "a") => truthy
      (find-by-schema? docs "b") => truthy
      (-> (find-by-schema? docs "foo") :schema :info) => (contains {:op "foo" :removable true})
      ; Add operation:
      (command pena :add-operation :id id :operation "bar")
      (let [app (:application (query pena :application :id id))
            docs (:documents app)]
        (count docs) => 6 ; foo, a, b and "hakija" + bar and c
        (find-by-schema? docs "bar") => truthy
        (find-by-schema? docs "c") => truthy
        (-> (find-by-schema? docs "bar") :schema :info) => (contains {:op "bar" :removable true})))))
