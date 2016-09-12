(ns lupapalvelu.comment-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.comment]))

(testable-privates lupapalvelu.comment enrich-attachment-comment enrich-user-information)


(facts enrich-attachment-comment
  (fact "attachment type and id get merged in target"
    (let [comment     {:text "comment text" :target {:type :attachment :id "att-1"}}
          attachments [{:id "att-1" :type {:type-group "some-group" :type-id "some-type"}}]]
      (enrich-attachment-comment attachments comment) => {:text "comment text"
                                                          :target {:type :attachment
                                                                   :id "att-1"
                                                                   :attachmentId "att-1"
                                                                   :attachmentType {:type-group "some-group" :type-id "some-type"}}}))

  (fact "no matching attachment"
    (let [comment     {:text "comment text" :target {:type :attachment :id "att-1"}}
          attachments [{:id "att-2" :type {:type-group "some-group" :type-id "some-type"}}]]
      (enrich-attachment-comment attachments comment) => {:text "comment text"
                                                          :target {:type :attachment
                                                                   :id "att-1"}})))

(facts enrich-user-information
  (fact "application role determined by auth role"
    (let [auth    [{:id "user-id1" :role :owner :party-roles []}]
          comment {:text "comment text" :user {:id "user-id1" :role :applicant}}]
      (enrich-user-information auth comment) => {:text "comment text"
                                                 :user {:id "user-id1"
                                                        :role :applicant
                                                        :application-role :owner}}))

  (fact "application role determined by party-role"
    (let [auth    [{:id "user-id1" :role :owner :party-roles [:clown :batman]}]
          comment {:text "comment text" :user {:id "user-id1" :role :applicant}}]
      (enrich-user-information auth comment) => {:text "comment text"
                                                 :user {:id "user-id1"
                                                        :role :applicant
                                                        :application-role :clown}}))

  (fact "no matching auth role for applicant"
    (let [auth    [{:id "user-id2" :role :owner :party-roles [:clown :batman]}]
          comment {:text "comment text" :user {:id "user-id1" :role :applicant}}]
      (enrich-user-information auth comment) => {:text "comment text"
                                                 :user {:id "user-id1"
                                                        :role :applicant
                                                        :application-role :other-auth}}))

  (fact "no matching auth role for authority"
    (let [auth    [{:id "user-id2" :role :owner :party-roles [:clown :batman]}]
          comment {:text "comment text" :user {:id "user-id1" :role :authority}}]
      (enrich-user-information auth comment) => {:text "comment text"
                                                 :user {:id "user-id1"
                                                        :role :authority
                                                        :application-role :authority}}))

  (fact "matching party role for authority"
    (let [auth    [{:id "user-id1" :role :owner :party-roles [:batman]}]
          comment {:text "comment text" :user {:id "user-id1" :role :authority}}]
      (enrich-user-information auth comment) => {:text "comment text"
                                                 :user {:id "user-id1"
                                                        :role :authority
                                                        :application-role :batman}}))

  (fact "matching auth role for authority"
    (let [auth    [{:id "user-id1" :role :writer :party-roles []}]
          comment {:text "comment text" :user {:id "user-id1" :role :authority}}]
      (enrich-user-information auth comment) => {:text "comment text"
                                                 :user {:id "user-id1"
                                                        :role :authority
                                                        :application-role :other-auth}}))

  (fact "many auth roles"
    (let [auth    [{:id "user-id1" :role :owner          :party-roles []}
                   {:id "user-id1" :role :foreman        :party-roles []}
                   {:id "user-id1" :role :statementGiver :party-roles []}]
          comment {:text "comment text" :user {:id "user-id1" :role :applicant}}]
      (enrich-user-information auth comment) => {:text "comment text"
                                                 :user {:id "user-id1"
                                                        :role :applicant
                                                        :application-role :foreman}}))

  (fact "many auths, one matches user id"
    (let [auth    [{:id "user-id2" :role :owner           :party-roles []}
                   {:id "user-id2" :role :foreman         :party-roles [:clown]}
                   {:id "user-id1" :role :statementGiver  :party-roles []}
                   {:id "user-id2" :role :foreman         :party-roles [:batman]}]
          comment {:text "comment text" :user {:id "user-id1" :role :applicant}}]
      (enrich-user-information auth comment) => {:text "comment text"
                                                 :user {:id "user-id1"
                                                        :role :applicant
                                                        :application-role :statementGiver}})))
