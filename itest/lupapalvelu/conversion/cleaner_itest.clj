(ns lupapalvelu.conversion.cleaner-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.cleaner :as conv-clean]
            [lupapalvelu.conversion.config :as conv-cfg]
            [lupapalvelu.conversion.core :as conv]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.itest-util :as conv-test]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.file-storage :as storage]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.util :as util]))

(def db-name "test_conversion-cleaner-itest")

(def ^:dynamic *test-dir* nil)

(defn dir-fixture [f]
  (binding [*test-dir* (conv-test/mk-test-dir)]
    (try
      (f)
      (finally
        (conv-test/rm-test-dir *test-dir*)))))

(use-fixtures :each dir-fixture (local-fixture-factory (-> {:db-name db-name}
                                                           test-system/with-remote-components)))

(defn files-exist? [{:keys [id attachments]}]
  (let [file-ids (->> attachments
                      (keep #(-> % :latestVersion :fileId))
                      seq)]
    (and file-ids
         (every? #(storage/application-file-exists? id %) file-ids))))

(defn no-files? [{:keys [id attachments]}]
  (let [file-ids (->> attachments
                      (keep #(-> % :latestVersion :fileId))
                      seq)]
    (and file-ids
         (not-any? #(storage/application-file-exists? id %) file-ids))))

(defn convert
  ([backend-id store-only?]
   (let [filename (format "msg-%s.xml" (random-uuid))
         _        (conv-test/write-test-xml *test-dir*
                                            filename
                                            :backend-id backend-id)
         cfg      (conv-test/write-config *test-dir* "092-R" :glob filename)]
     (conv/execute-conversion-command {:cmd     :store
                                       :cfgfile cfg
                                       :system  (test-system/get-common-components)})
     (when-not store-only?
       (conv/execute-conversion-command {:cmd     :convert
                                         :cfgfile cfg
                                         :system  (test-system/get-common-components)})))
   ;; Returns conversion document.
   (conv-doc/document "092-R" backend-id))
  ([backend-id] (convert backend-id false)))

(deftest regular-conversion-deletion-test
  (testing "A regular conversion can be deleted"
    (let [backend-id   "1-1234-56-ABC"
          {app-id :LP-id
           doc-id :id
           :as    doc} (convert backend-id)
          application  (mongo/by-id :applications app-id)]
      (is (true? (files-exist? application)))
      (mongo/insert :app-links {:id   "link-id"
                                :link [app-id "other-tunnus"]})
      (is (true? (mongo/any? :app-links {:link app-id})))
      (is (true? (:converted doc)))
      (is (true? (conv-clean/can-be-deleted? doc)))
      (let [deletable (conv-clean/deletable-application! doc)]
        (is (= app-id (:id deletable)))
        (is (= [backend-id] (mapv :kuntalupatunnus (:verdicts deletable)))))
      (is (some? (mongo/by-id :applications app-id)))
      (testing "Delete application but retain document"
        (let [cleaned-doc (conv-clean/clean-conversion! doc)]
          (is (= {:id    doc-id
                  :LP-id app-id}
                 (select-keys cleaned-doc [:id :LP-id :converted])))
          (is (nil? (mongo/by-id :applications app-id)))
          (is (false? (mongo/any? :app-links {:link app-id})))
          (is (false? (files-exist? application)))
          (is (= cleaned-doc (conv-doc/document doc-id)))
          (testing "Non-existing application can be deleted"
            (is (true? (conv-clean/can-be-deleted? cleaned-doc)))
            (is (nil? (conv-clean/deletable-application! cleaned-doc)))
            (with-redefs [sade.core/now (constantly 987654321)]
              (is (= (update cleaned-doc :history util/conjv {:op :clean :ts 987654321})
                     (conv-clean/clean-conversion! cleaned-doc)))))))
      (testing "Delete the remaining document."
        (is (true? (conv-clean/can-be-deleted? doc)))
        (is (nil? (conv-clean/deletable-application! doc)))
        (is (nil? (conv-clean/clean-conversion! doc {:delete-conversion-document? true})))
        (is (nil? (mongo/by-id :conversion doc-id))))))
  (testing "This time delete application and the document at the same time"
    (let [backend-id   "1-1234-56-ABC"
          {app-id :LP-id
           doc-id :id
           :as    doc} (convert backend-id)
          application  (mongo/by-id :applications app-id)]
      (is (some? application))
      (is (true? (files-exist? application)))
      (is (nil? (conv-clean/clean-conversion! doc {:delete-conversion-document? true})))
      (is (nil? (mongo/by-id :applications app-id)))
      (is (false? (files-exist? application)))
      (is (nil? (mongo/by-id :conversion doc-id))))))

(deftest deletion-stoppers-test
  (let [[not-converted-id wrong-org-id archived-id invoiced-id filebanked-id
         bulletin-id linked-id authed-id
         atts-id]     (map #(format "2-%04d-22-FOO" %)
                           (range 1 10))
        delete-fails  (fn [doc msg]
                        (let [p (re-pattern msg)]
                          (testing msg
                            (is (false? (conv-clean/can-be-deleted? doc)))
                            (is (thrown-with-msg? Exception p
                                                  (conv-clean/deletable-application! doc)))
                            (is (thrown-with-msg? Exception p
                                                  (conv-clean/clean-conversion! doc)))
                            (is (= doc (conv-doc/document (:id doc))))
                            (let [a (mongo/by-id :applications (:LP-id doc))]
                              (is (some? a))
                              (is (true? (files-exist? a)))))))
        force-delete  (fn [doc]
                        (let [force {:force? true}
                              a     (mongo/by-id :applications (:LP-id doc))]
                          (is (true? (conv-clean/can-be-deleted? doc force)))
                          (is (some? (conv-clean/deletable-application! doc force)))
                          (is (true? (files-exist? a)))
                          (is (some? (conv-clean/clean-conversion! doc force)))
                          (let [new-doc (mongo/by-id :conversion (:id doc))]
                            (is (some? new-doc))
                            (is (not (:converted new-doc)))
                            (is (= "clean"
                                   (-> new-doc :history last :op))))
                          (is (nil? (mongo/by-id :applications (:LP-id doc))))
                          (is (true? (no-files? a)))))
        can-be-nuked? #(conv-clean/can-be-deleted? % {:nuke? true})]
    (testing "Not a converted application"
      (let [{app-id :LP-id
             :as    doc} (convert not-converted-id)]
        (is (some? app-id))
        (mongo/update-by-id :applications app-id {$set   {:address "Not converted"}
                                                  $unset {:facta-imported true}})
        (delete-fails doc "Not a converted application")
        (is (false? (can-be-nuked? doc)))))
    (testing "Wrong organization"
      (let [{app-id :LP-id
             :as    doc} (convert wrong-org-id)]
        (is (some? app-id))
        (mongo/update-by-id :applications app-id
                            {$set {:address      "Different organization"
                                   :organization "123-R"}})
        (delete-fails doc "Wrong organization")
        (is (false? (can-be-nuked? doc)))))
    (testing "Archived"
      (let [{app-id :LP-id
             :as    doc} (convert archived-id)]
        (is (some? app-id))
        (mongo/update-by-id :applications app-id
                            {$set {:address          "Archived"
                                   :archived.initial (now)}})
        (delete-fails doc "Archived")
        (is (true? (can-be-nuked? doc)))))
    (testing "Invoiced"
      (let [{app-id :LP-id
             :as    doc} (convert invoiced-id)]
        (is (some? app-id))
        (mongo/insert :invoices {:id             (mongo/create-id)
                                 :application-id app-id})
        (delete-fails doc "Invoices")
        (is (true? (can-be-nuked? doc)))))
    (testing "Filebank"
      (let [{app-id :LP-id
             :as    doc} (convert filebanked-id)]
        (is (some? app-id))
        (mongo/insert :filebank {:id app-id})
        (delete-fails doc "Filebank")
        (is (true? (can-be-nuked? doc)))))
    (testing "Bulletin"
      (let [{app-id :LP-id
             :as    doc} (convert bulletin-id)]
        (is (some? app-id))
        ;; Environmental bulletin
        (mongo/insert :application-bulletins {:id app-id})
        (delete-fails doc "Bulletin")
        (is (true? (can-be-nuked? doc)))
        (mongo/remove :application-bulletins app-id)
        (is (true? (conv-clean/can-be-deleted? doc)))
        (is (true? (can-be-nuked? doc)))
        ;; Building permit bulletin
        (mongo/insert :application-bulletins
                      {:id (format "%s_%s" app-id (mongo/create-id))})
        (delete-fails doc "Bulletin")
        (is (true? (can-be-nuked? doc)))))
    (testing "Linked file metadata"
      (let [{app-id :LP-id
             :as    doc} (convert linked-id)]
        (is (some? app-id))
        (mongo/insert :linked-file-metadata
                      {:id            (mongo/create-id)
                       :target-entity {:application-id app-id}})
        (delete-fails doc "Linked file metadata")
        (is (true? (can-be-nuked? doc)))))
    (testing "Authed users"
      (let [{app-id :LP-id
             :as    doc} (convert authed-id)]
        (is (some? app-id))
        (mongo/update-by-id :applications app-id
                            {$set  {:address "Authed user"}
                             $push {:auth {:id "some-other-id"}}})
        (delete-fails doc "Authed users")
        (is (true? (can-be-nuked? doc)))
        (testing "... can be deleted with force? option"
          (force-delete doc))))
    (testing "User attachments"
      (let [{app-id :LP-id
             :as    doc} (convert atts-id)]
        (is (some? app-id))
        (mongo/update-by-id :applications app-id
                            {$set  {:address "User attachment"}
                             $push {:attachments {:latestVersion {:user {:id "random-id"}}}}})
        (delete-fails doc "User attachments")
        (is (true? (can-be-nuked? doc)))
        (testing "... with force?"
          (force-delete doc))))))

(deftest nuke-conversion-test
  (let [nuke {:nuke? true}]
    (testing "Nuke bypasses archived, invoices, filebank, bulletin, linked file metadata, authed users and user attachments"
      (let [{app-id :LP-id
             doc-id :id
             :as    doc} (convert "3-0001-22-NU")
            application  (mongo/by-id :applications app-id)]
        (is (some? application))
        ;; Set up every stopper that nuke bypasses
        (mongo/update-by-id :applications app-id
                            {$set  {:address          "Nuked"
                                    :archived.initial (now)}
                             $push {:auth        {:id "outsider"}
                                    :attachments {:latestVersion {:user {:id "random-user"}}}}})
        (mongo/insert :invoices {:id             (mongo/create-id)
                                 :application-id app-id})
        (mongo/insert :filebank {:id app-id})
        (mongo/insert :application-bulletins {:id app-id})
        (mongo/insert :linked-file-metadata
                      {:id            (mongo/create-id)
                       :target-entity {:application-id app-id}})
        ;; Regular and force delete both fail
        (is (false? (conv-clean/can-be-deleted? doc)))
        (is (false? (conv-clean/can-be-deleted? doc {:force? true})))
        ;; Nuke succeeds
        (is (true? (conv-clean/can-be-deleted? doc nuke)))
        (is (some? (conv-clean/deletable-application! doc nuke)))
        (is (true? (files-exist? application)))
        (let [result (conv-clean/clean-conversion! doc nuke)]
          (is (some? result))
          (is (= doc-id (:id result)))
          (is (true? (mongo/any? :conversion {:_id (:id result)}))))
        (is (nil? (mongo/by-id :applications app-id)))
        (is (false? (files-exist? application)))))
    (testing "Nuke does not bypass facta-imported check"
      (let [{app-id :LP-id
             :as    doc} (convert "3-0002-22-NU")]
        (mongo/update-by-id :applications app-id
                            {$set   {:address "Not imported"}
                             $unset {:facta-imported true}})
        (is (false? (conv-clean/can-be-deleted? doc nuke)))
        (is (thrown-with-msg? Exception #"Not a converted application"
                              (conv-clean/clean-conversion! doc nuke)))))
    (testing "Nuke does not bypass wrong organization check"
      (let [{app-id :LP-id
             :as    doc} (convert "3-0003-22-NU")]
        (mongo/update-by-id :applications app-id
                            {$set {:address      "Wrong org"
                                   :organization "999-R"}})
        (is (false? (conv-clean/can-be-deleted? doc nuke)))
        (is (thrown-with-msg? Exception #"Wrong organization"
                              (conv-clean/clean-conversion! doc nuke)))))
    (testing "conversion--nuke"
      (let [{app-id :LP-id
             doc-id :id
             :as    doc} (convert "3-0004-22-NU")
            application  (mongo/by-id :applications app-id)]
        (mongo/update-by-id :applications app-id
                            {$set {:archived.initial (now)}})
        (is (false? (conv-clean/can-be-deleted? doc)))
        (is (true? (conv-clean/can-be-deleted? doc nuke)))
        (testing "Document only"
          (is (some? (convert "3-0005-22-NU" true))))
        (testing "Feature not enabled"
          (env/with-feature-value :conversion-nuke false
            (is (thrown-with-msg? Exception #"Nuke command not supported."
                         (conv/conversion--nuke {:organization-id "092-R"
                                                 :municipality    "092"} true)))))
        (testing "Feature enabled"
          (env/with-feature-value :conversion-nuke true
            (conv/conversion--nuke {:organization-id "092-R"
                                    :municipality    "092"} true)))
        (let [bids    (->> (mongo/select :conversion {} [:backend-id])
                           (mapv :backend-id)
                           set)
              app-ids (mapv :id (mongo/select :applications {} [:id]))]
          (is (= #{"3-0001-22-NU" "3-0002-22-NU" "3-0003-22-NU"
                   "3-0004-22-NU" "3-0005-22-NU"}
                 bids))
         (is (= 3 (count app-ids))))
        (env/with-feature-value :conversion-nuke true
          (conv/conversion--nuke {:organization-id "092-R"
                                  :municipality    "092"} false))
        (let [bids    (->> (mongo/select :conversion {} [:backend-id])
                           (mapv :backend-id)
                           set)
              app-ids (mapv :id (mongo/select :applications {} [:id]))]
          (is (= #{"3-0002-22-NU" "3-0003-22-NU"} bids))
          (is (= 2 (count app-ids))))
        (is (nil? (mongo/by-id :applications app-id)))
        (is (nil? (mongo/by-id :conversion doc-id)))
        (is (false? (files-exist? application)))))))

(deftest bad-conversions-test
  (let [good-id     "22-1001-YES"
        bad-id1     "22-0001-BAD"
        unknown-id  "22-2002-HUH"
        unknown-xml (conv-test/write-test-xml *test-dir* "unknown.xml" :backend-id unknown-id)
        good-xml    (conv-test/write-test-xml *test-dir* "not-bad.xml" :backend-id good-id)
        bad1-xml    (conv-test/write-test-xml *test-dir* "one-bad.xml" :backend-id bad-id1)
        bad-id2     "22-0002-BAD"
        bad2-xml    (conv-test/write-test-xml *test-dir* "two-bad.xml" :backend-id bad-id2)
        cfg-edn     (conv-test/write-config *test-dir* "092-R" :glob "*bad.xml")
        cfg         (conv-cfg/read-configuration cfg-edn)]
    (testing "First, successful conversion"
      (conv/execute-conversion-command {:cmd :store :cfgfile cfg-edn :system (test-system/get-common-components)})
      (conv/execute-conversion-command {:cmd :convert :cfgfile cfg-edn :system (test-system/get-common-components)})
      (is (= (set (map :backend-id (mongo/select :conversion {} [:backend-id])))
             (set [good-id bad-id1 bad-id2]))))
    (testing "Make bad conversions"
      (conv-test/write-test-xml *test-dir* "one-bad.xml"
                                :backend-id bad-id1
                                :application-id "LP-123-2023-11111")
      (mongo/insert :applications {:id           "LP-092-2022-04321"
                                   :organization "092-R"
                                   :verdicts     [{:kuntalupatunnus bad-id2}]})
      ;; Different organization, does not matter
      (mongo/insert :applications {:id           "LP-092-2023-00321"
                                   :organization "753-R"
                                   :verdicts     [{:kuntalupatunnus good-id}]}))
    (testing "Make bad conversion removal target"
      (testing "Good"
        (is (nil? (conv/make-bad-conversion-removal-target cfg {:id good-id})))
        (is (nil? (conv/make-bad-conversion-removal-target cfg {:filename good-xml}))))
      (testing "Bad 1"
        (is (nil? (conv/make-bad-conversion-removal-target cfg {:id bad-id1})))
        (let [result (conv/make-bad-conversion-removal-target cfg {:filename bad1-xml})]
          (is (= bad-id1 (:id result)))
          (is (= bad-id1 (-> result :conversion-doc :backend-id)))))
      (testing "Bad 2"
        (let [check (fn [result]
                      (is (= bad-id2 (:id result)))
                      (is (= bad-id2 (-> result :conversion-doc :backend-id))))]
          (check (conv/make-bad-conversion-removal-target cfg {:id bad-id2}))
          (check (conv/make-bad-conversion-removal-target cfg {:filename bad2-xml}))))
      (testing "Unknown"
        (is (nil? (conv/make-bad-conversion-removal-target cfg {:id unknown-id})))
        (is (nil? (conv/make-bad-conversion-removal-target cfg {:filename unknown-xml})))))

    (let [docs        (mongo/select :conversion {})
          good-app-id (:LP-id (util/find-by-key :backend-id good-id docs))
          good-app    (mongo/by-id :applications good-app-id)
          bad-app-id1 (:LP-id (util/find-by-key :backend-id bad-id1 docs))
          bad-app1    (mongo/by-id :applications bad-app-id1)
          bad-app-id2 (:LP-id (util/find-by-key :backend-id bad-id2 docs))
          bad-app2    (mongo/by-id :applications bad-app-id2)]

      (testing "Still good without backend-id application match"
        (mongo/update-by-id :applications good-app-id
                            {$unset {:verdicts true}})
        (is (nil? (conv/make-bad-conversion-removal-target cfg {:id good-id})))
        (is (nil? (conv/make-bad-conversion-removal-target cfg {:filename good-xml}))))
      (mongo/update-by-id :applications bad-app-id2
                          {$push {:auth {:id "needs-force"}}})
      (testing "Starting point"
        (is (= 3 (mongo/count :applications
                              {:_id {$in [good-app-id bad-app-id1 bad-app-id2]}}))))
      (testing "Remove bad conversions"
        (testing "Dry run"
          (let [clean-called (atom false)]
            (with-redefs [conv-clean/clean-conversion! (fn [& _]
                                                         (reset! clean-called true)
                                                         nil)]
              (conv/execute-conversion-command {:cmd     :clean :cfgfile cfg-edn
                                                :system  (test-system/get-common-components)
                                                :dry-run true})
              (conv/execute-conversion-command {:cmd     :clean :cfgfile cfg-edn
                                                :system  (test-system/get-common-components)
                                                :dry-run true   :force   true})
              (is (false? @clean-called)))))
        (testing "Files initially OK"
          (is (true? (files-exist? good-app)))
          (is (true? (files-exist? bad-app1)))
          (is (true? (files-exist? bad-app2))))
        (testing "Clean for real"
          (is (nil? (conv/execute-conversion-command {:cmd :clean :cfgfile cfg-edn :system (test-system/get-common-components)}))))
        (testing "Bad 1 is gone Bad2 is not (since authed)"
          (is (= #{good-id bad-id2}
                 (set (map :backend-id (mongo/select :conversion
                                                     {:backend-id {$in [good-id bad-id1 bad-id2]}})))))
          (is (= #{good-app-id bad-app-id2}
                 (set (map :id (mongo/select :applications
                                             {:_id {$in [good-app-id bad-app-id1 bad-app-id2]}})))))
          (is (true? (files-exist? good-app)))
          (is (false? (files-exist? bad-app1)))
          (is (true? (files-exist? bad-app2))))
        (testing "Id-based config and clean with -force"
          (let [cfg-edn2 (conv-test/write-config *test-dir* "092-R" :ids [bad-id2])]
            (is (nil? (conv/execute-conversion-command {:cmd    :clean :cfgfile cfg-edn2
                                                        :system (test-system/get-common-components)
                                                        :force  true})))))
        (testing "Bad2 is now gone"
          (is (= [good-id]
                 (map :backend-id (mongo/select :conversion
                                                {:backend-id {$in [good-id bad-id1 bad-id2]}}))))
          (is (= [good-app-id]
                 (map :id (mongo/select :applications
                                        {:_id {$in [good-app-id bad-app-id1 bad-app-id2]}}))))
          (is (true? (files-exist? good-app)))
          (is (false? (files-exist? bad-app1)))
          (is (false? (files-exist? bad-app2))))))))
