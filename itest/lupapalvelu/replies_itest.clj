(ns lupapalvelu.replies-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.date :as date]))

(defn now-ts [change-fn]
  (-> (date/now) (change-fn) (date/timestamp)))

(defn- create-app-with-neighbor [& args]
  (let [application-id (apply create-app-id pena args)
        _ (comment-application pena application-id true)
        resp (command sonja "neighbor-add" :id application-id :propertyId "12312312341234" :name "n" :street "s" :city "c" :zip "z" :email "e")
        neighborId (:neighborId resp)
        application (query-application pena application-id)
        neighbors (:neighbors application)]
    (fact resp => ok?)
    [application neighborId neighbors]))

(defn vetuma-stamp! []
  (:stamp (vetuma! {:userid "123" :firstName "Pekka" :lastName "Banaani"})))

(def one-week-ms 604800000)

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components)
  (fact "Disable YM KuntaGML integration"
    (command sipoo :set-krysp-endpoint
             :organizationId "753-R"
             :permitType "YM"
             :username ""
             :password ""
             :url ""
             :version "") => ok?)

  (facts "Create replies"
    (let [[{application-id :id :as application} neighborId] (create-app-with-neighbor :operation "lannan-varastointi")
          nid                                               (name neighborId)
          neighborEmail                                     "abba@example.com"
          _                                                 (upload-attachment-to-all-placeholders pena application)
          cmd-1                                             (command sonja :neighbor-send-invite
                                                                     :id application-id
                                                                     :neighborId neighborId
                                                                     :email neighborEmail)
          email                                             (query pena :last-email)
          body                                              (get-in email [:message :body :plain])
          [_ _ _ token]                                     (re-find #"(?sm)/neighbor/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)" body)
          stamp                                             (vetuma-stamp!)
          cmd-2                                             (command sonja :submit-application :id application-id)
          cmd-3                                             (command sonja :approve-application :id application-id :lang "fi")
          cmd-4                                             (command sonja :move-to-proclaimed
                                                                     :id application-id
                                                                     :proclamationStartsAt (now)
                                                                     :proclamationEndsAt (now-ts #(date/plus % :day))
                                                                     :proclamationText "foo")
          {:keys [bulletin]}                                (query sonja :public-bulletin :bulletinId application-id)]
      (fact "commands ok"
        cmd-1 => ok?
        cmd-2 => ok?
        cmd-3 => ok?
        cmd-4 => ok?)
      (without-anti-csrf
        (fact "Can create reply"
          (command sonja :create-reply
                   :id application-id
                   :reply {:id             :new
                           :application-id application-id
                           :related-issues #{}
                           :message        ""
                           :reply-due-date (+ (now) one-week-ms)}) => ok?)

        (fact "Can delete reply"
          (let [{replies :replies} (query sonja :replies :id application-id)
                reply-id           (->> replies keys first)]
            (count replies) => 1
            (command sonja :delete-reply :id application-id :replyId reply-id) => ok?)
          (query sonja :replies :id application-id) => {:ok true :replies {}})

        (fact "No issues related to application should exist"
          (let [{{:keys [bulletins neighbors statements]} :replies-targets} (query sonja :get-reply-target-issues :id application-id)]
            [(count bulletins) (count neighbors) (count statements)] => [0 0 0]))

        (fact "Sonja gives statement"
          (let [statement-id (command sonja :create-manual-statement :id application-id :functionCode nil)]
            (command sonja :give-statement :id  application-id
                     :statementId (:statementId statement-id)
                     :status "puollettu"
                     :text "This is a statement"
                     :lang "fi") => ok?))

        (fact "neighbor gives response"
          (command nil :neighbor-response
                   :applicationId application-id
                   :neighborId nid
                   :stamp stamp
                   :token token
                   :response "comments"
                   :message "I am neighbour") => ok?)

        (fact "Sonja gives bulletin"
          (command sonja :add-manual-bulletin-comment
                   :id application-id
                   :comment "foo"
                   :bulletinId application-id
                   :bulletinVersionId (:versionId bulletin)
                   :comment "Vastustan"
                   :contact {:firstName "Teemu"
                             :lastName  "Testaaja"
                             :street    "Striit"
                             :zip       "12345"
                             :city      "Siti"
                             :email     "maili@foo.com"}) => ok?)

        (fact "Can create a reply with a related issues"
          (let [{issues :replies-targets}                (query sonja :get-reply-target-issues :id application-id)
                {:keys [bulletins neighbors statements]} issues]
            [(count bulletins) (count neighbors) (count statements)] => [1 1 1]

            (fact "Should return no reply connected to issue"
              (query sonja :issue-reply-state :id application-id :issueId (-> statements first :id)) =>
              {:ok true :state "no-reply"})

            (fact "Should be able to create reply with connected issues"
              (let [reply (command sonja :create-reply
                                   :id application-id
                                   :reply {:id             :new
                                           :application-id application-id
                                           :related-issues #{{:id         (-> bulletins first :id)
                                                              :issue-type :bulletin}
                                                             {:id         (-> statements first :id)
                                                              :issue-type :statement}
                                                             {:id         (-> neighbors first :id)
                                                              :issue-type :neighbor}}
                                           :message        "This is a message"
                                           :reply-due-date (+ (now) one-week-ms)})]
                reply => ok?

                (fact "Should return state waiting-for-answer when issue connected to reply with no answer"
                  (query sonja :issue-reply-state :id application-id :issueId (-> statements first :id)) =>
                  {:ok true :state "waiting-for-answer"})

                (fact "can not create a reply with issue of wrong type"
                  (command sonja :create-reply
                           :id application-id :reply {:id             :new
                                                      :application-id application-id
                                                      :related-issues #{{:id "42" :type :non-existant-type}}
                                                      :message        ""
                                                      :reply-due-date (+ (now) one-week-ms)})
                  => {:ok false :text "error.illegal-value:schema-validation"})

                (fact "Can answer a reply"
                  (command pena :answer-reply
                           :id application-id
                           :replyId (-> reply :reply :id)
                           :answer {:nothing-to-add false :message "message"}) => ok?)

                (fact "Should return state answered when issue connected to reply has answer"
                  (query sonja :issue-reply-state :id application-id :issueId (-> statements first :id)) =>
                  {:ok true :state "answered"})

                (fact "Should be able to query for which reply connected to some issue"
                  (let [{replies :replies} (query sonja :replies-connected-to-issue
                                                  :id application-id
                                                  :issueId (-> statements first :id))]
                    (mapv :id replies) => [(-> reply :reply :id)]))))))))))
