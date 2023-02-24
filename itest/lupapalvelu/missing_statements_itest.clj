(ns lupapalvelu.missing-statements-itest
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer [err]]
            [lupapalvelu.statement :as statement]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.util :as util]))

(def db-name "test_missing-statements-itest")

(mount/start #'mongo/connection)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (with-local-actions
    (let [{app-id :id} (create-and-submit-application pena :operation "pientalo")
          critic       (->> (util/find-by-id "753-R" minimal/organizations)
                            :statementGivers
                            (util/find-by-key :email (email-for-key sonja)))]
      (fact "Ronja requests four statements from Sonja"
        (doseq [_ (range 4)]
          (command ronja :request-for-statement :id app-id
                   :functionCode nil
                   :selectedPersons [critic]) => ok?))
      (let [[sid1 sid2 sid3
             sid4]        (->>  (mongo/by-id :applications app-id [:statements])
                                :statements (map :id))
            get-statement (fn [sid]
                            (->>  (mongo/by-id :applications app-id [:statements])
                                  :statements (util/find-by-id sid)))]
        (fact "Statements exist"
          sid1 => truthy
          sid2 => truthy
          sid3 => truthy)
        (fact "Sonja gives statement"
          (command sonja :give-statement :id app-id
                   :statementId sid1
                   :status "puollettu"
                   :text "OK"
                   :lang "fi") => ok?)
        (fact "Sonja gives statement via attachment"
          (let [att-id (-> (command sonja :create-attachments :id app-id
                                    :attachmentTypes [{:type-group "ennakkoluvat_ja_lausunnot"
                                                       :type-id    "lausunto"}])
                           :attachmentIds first)]
            att-id => truthy
            (mongo/update-by-query :applications
                                   {:_id         app-id
                                    :attachments {$elemMatch {:id att-id}}}
                                   {$set {:attachments.$.target {:type :statement
                                                                 :id   sid2}}})
            ;; Note that here the statement attachment is actually empty, but it does not
            ;; matter in terms of the test and should not happen in production
            (command sonja :give-statement :id app-id
                     :statementId sid2
                     :status "puollettu"
                     :in-attachment true
                     :text "OK"
                     :lang "fi") => ok?))
        (against-background
          [(lupapalvelu.statement/generate-statement-attachment
             anything anything anything anything) => (throw (ex-info "Yikes!" {}))]
          (fact "PDF generation fails when giving statement"
            (let [{:keys [modify-id]
                   :as   res} (command sonja :give-statement :id app-id
                                       :statementId sid3
                                       :status "puollettu"
                                       :text "Oh no!"
                                       :lang "fi")]
              res => (err :error.statement.give-failure)
              modify-id => truthy
              (:modify-id (get-statement sid3)) => modify-id)))

        (fact "Try again with success"
          (let [{:keys [modify-id state given]} (get-statement sid3)]
            state => "draft"
            given => nil
            (command sonja :give-statement :id app-id
                     :statementId sid3
                     :status "puollettu"
                     :text "Yeah"
                     :modify-id modify-id
                     :lang "sv")) => ok?)
        (fact "Four statements, three given"
          (let [{:keys [attachments statements comments]
                 :as   application} (mongo/by-id :applications app-id)
                att-id1             (:id (util/find-first (util/fn-> :source :id (= sid1))
                                                          attachments))
                att-id2             (:id (util/find-first (util/fn-> :target :id (= sid2))
                                                          attachments))
                att-id3             (:id (util/find-first (util/fn-> :source :id (= sid3))
                                                          attachments))]
            attachments => (just (contains {:id            att-id1
                                            :source        {:type "statements"
                                                            :id   sid1}
                                            :readOnly      true
                                            :latestVersion truthy})
                                 (contains {:id       att-id2
                                            :target   {:type "statement"
                                                       :id   sid2}
                                            :readOnly true})
                                 (contains {:id            att-id3
                                            :source        {:type "statements"
                                                            :id   sid3}
                                            :readOnly      true
                                            :latestVersion truthy}))
            (map #(select-keys % [:id :state :in-attachment :given])
                 statements) => (just (just {:id    sid1
                                             :state "given"
                                             :given pos?})
                                      (just {:id            sid2
                                             :state         "given"
                                             :in-attachment true
                                             :given         pos?})
                                      (just {:id    sid3
                                             :state "given"
                                             :given pos?})
                                      (just {:id    sid4
                                             :state "requested"}))
            comments => (just (contains {:target {:id att-id1 :type "attachment"}})
                              (contains {:target {:id sid1 :type "statement"}})
                              ;; No att-id2 comment, since it was not actually uploaded.
                              (contains {:target {:id sid2 :type "statement"}})
                              (contains {:target {:id att-id3 :type "attachment"}})
                              (contains {:target {:id sid3 :type "statement"}}))
            (fact "Fix missing statement attachments: no fixes needed"
              (statement/fix-missing-statement-attachments app-id) => nil)
            (fact "Bad application ids are ignored and logged"
              (statement/fix-missing-statement-attachments nil) => nil
              (statement/fix-missing-statement-attachments "LP-BAD") => nil)
            (facts "Nothing fixed"
              (mongo/by-id :applications app-id) => application)
            (fact "Mark last statement as given and fix again"
              (mongo/update-by-query :applications
                                     {:_id        app-id
                                      :statements {$elemMatch {:id sid4}}}
                                     {$set {:statements.$.state :given
                                            :statements.$.given 123456}})
              => 1
              (statement/fix-missing-statement-attachments app-id)
              (let [{atts2 :attachments
                     talk2 :comments} (-> (mongo/by-id :applications app-id))
                    att4              (last atts2)]
                (butlast atts2) => attachments
                att4 => (contains {:source        {:type "statements"
                                                   :id   sid4}
                                   :readOnly      true
                                   :latestVersion truthy})
                (butlast talk2) => comments
                (last talk2) => (contains {:target {:id   (:id att4)
                                                    :type "attachment"}})))
            (fact "Wipe out attachments, comments and fix again"
              (mongo/update-by-id :applications app-id {$set {:comments    []
                                                              :attachments []}})
              (statement/fix-missing-statement-attachments app-id)
              (let [{:keys [attachments
                            comments]} (mongo/by-id :applications app-id)]
                attachments => (just (contains {:source        {:type "statements"
                                                                :id   sid1}
                                                :readOnly      true
                                                :latestVersion truthy})
                                     (contains {:source        {:type "statements"
                                                                :id   sid3}
                                                :readOnly      true
                                                :latestVersion truthy})
                                     (contains {:source        {:type "statements"
                                                                :id   sid4}
                                                :readOnly      true
                                                :latestVersion truthy}))
                (count comments) => 3))))))))
