(ns lupapalvelu.conversion.cleaner-itest
  (:require [lupapalvelu.conversion-test-util :as conv-test]
            [lupapalvelu.conversion.cleaner :as conv-clean]
            [lupapalvelu.conversion.config :as conv-cfg]
            [lupapalvelu.conversion.core :as conv]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.file-storage :as storage]
            [me.raynes.fs :as fs]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.core :refer [now]]
            [sade.util :as util]))

(def db-name "test_conversion-cleaner-itest")

(defn convert
  "Returns conversion document."
  [backend-id]
  (let [filename  (conv-test/write-test-xml (fs/temp-name "msg-" ".xml")
                                            :backend-id backend-id)]
    (conv/convert! (conv-test/write-config "092-R" :glob filename :path ""))
    (mongo/select-one :conversion {:backend-id backend-id})))

(defn files-exist? [{:keys [id attachments]}]
  (let [file-ids (->> attachments
                      (keep #(-> % :latestVersion :fileId))
                      seq)]
    (and file-ids
         (every? #(storage/application-file-exists? id %) file-ids))))

(mount/start #'mongo/connection)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (let [test-dir (conv-test/mk-test-dir)
        convert  (fn [backend-id]
                   (let [filename (conv-test/write-test-xml test-dir
                                                            (fs/temp-name "msg-" ".xml")
                                                            :backend-id backend-id)]
                     (conv/convert! (conv-test/write-config test-dir "092-R"
                                                            :glob filename :path ""))
                     ;; Returns conversion document.
                     (mongo/select-one :conversion {:backend-id backend-id})))]
    (against-background
      [(after :contents (conv-test/rm-test-dir test-dir))]
      (facts "A regular conversion can be deleted"
        (let [backend-id   "1-1234-56-ABC"
              {app-id :LP-id
               doc-id :id
               :as    doc} (convert backend-id)
              application  (mongo/by-id :applications app-id)]
          (files-exist? application) => true
          (mongo/insert :app-links {:id   "link-id"
                                    :link [app-id "other-tunnus"]})
          (mongo/any? :app-links {:link app-id}) => true
          doc => (contains {:converted true})
          (conv-clean/can-be-deleted? doc) => true
          (conv-clean/deletable-application! doc)
          => (contains {:id       app-id
                        :verdicts (just (contains {:kuntalupatunnus backend-id}))})
          (mongo/by-id :applications app-id) => truthy
          (fact "Delete application but retain document"
            (let [cleaned-doc (conv-clean/clean-conversion! doc)]
              cleaned-doc => (contains {:id        doc-id
                                        :converted false
                                        :LP-id     app-id})
              (mongo/by-id :applications app-id) => nil
              (mongo/any? :app-links {:link app-id}) => false
              (files-exist? application) => false
              (mongo/by-id :conversion doc-id) => cleaned-doc
              (fact "Non-existing application can be deleted"
                (conv-clean/can-be-deleted? cleaned-doc) => true
                (conv-clean/deletable-application! cleaned-doc) => nil
                (conv-clean/clean-conversion! cleaned-doc) => cleaned-doc)))
          (fact "Delete the remaining document."
            (conv-clean/can-be-deleted? doc) => true
            (conv-clean/deletable-application! doc) => nil
            (conv-clean/clean-conversion! doc {:delete-conversion-document? true})
            => nil
            (mongo/by-id :conversion doc-id) => nil))
        (fact "This time delete application and the document at the same time"
          (let [backend-id   "1-1234-56-ABC"
                {app-id :LP-id
                 doc-id :id
                 :as    doc} (convert backend-id)
                application  (mongo/by-id :applications app-id)]
            application => truthy
            (files-exist? application) => true
            (conv-clean/clean-conversion! doc {:delete-conversion-document? true})
            => nil
            (mongo/by-id :application app-id) => nil
            (files-exist? application) => false
            (mongo/by-id :conversion doc-id) => nil)))

      (let [[not-converted-id wrong-org-id archived-id invoiced-id filebanked-id
             bulletin-id linked-id authed-id
             atts-id]    (map #(format "2-%04d-22-FOO" %)
                              (range 1 10))
            delete-fails (fn [doc msg]
                           (let [p (re-pattern msg)]
                             (fact {:midje/description msg}
                               (conv-clean/can-be-deleted? doc) => false
                               (conv-clean/deletable-application! doc) => (throws p)
                               (conv-clean/clean-conversion! doc) => (throws p)
                               (mongo/by-id :conversion (:id doc)) => doc
                               (let [a (mongo/by-id :applications (:LP-id doc))]
                                 a => truthy
                                 (files-exist? a) => true))))
            force-delete (fn [doc]
                           (let [force {:force? true}
                                 a     (mongo/by-id :applications (:LP-id doc))]
                             (conv-clean/can-be-deleted? doc force) => true
                             (conv-clean/deletable-application! doc force) => truthy
                             (files-exist? a) => true
                             (conv-clean/clean-conversion! doc force) => truthy
                             (mongo/by-id :conversion (:id doc)) => doc
                             (mongo/by-id :applications (:LP-id doc)) => nil
                             (files-exist? a) => true))]
        (facts "Deletion stoppers"
          (fact "Not a converted application"
            (let [{app-id :LP-id
                   :as    doc} (convert not-converted-id)]
              app-id => truthy
              (mongo/update-by-id :applications app-id {$set   {:address "Not converted"}
                                                        $unset {:facta-imported true}})
              (delete-fails doc "Not a converted application")))
          (fact "Wrong organization"
            (let [{app-id :LP-id
                   :as    doc} (convert wrong-org-id)]
              app-id => truthy
              (mongo/update-by-id :applications app-id
                                  {$set {:address      "Different organization"
                                         :organization "123-R"}})
              (delete-fails doc "Wrong organization")))
          (fact "Archived"
            (let [{app-id :LP-id
                   :as    doc} (convert archived-id)]
              app-id => truthy
              (mongo/update-by-id :applications app-id
                                  {$set {:address          "Archived"
                                         :archived.initial (now)}})
              (delete-fails doc "Archived")))
          (fact "Invoiced"
            (let [{app-id :LP-id
                   :as    doc} (convert invoiced-id)]
              app-id => truthy
              (mongo/insert :invoices {:id             (mongo/create-id)
                                       :application-id app-id})
              (delete-fails doc "Invoices")))
          (fact "Filebank"
            (let [{app-id :LP-id
                   :as    doc} (convert filebanked-id)]
              app-id => truthy
              (mongo/insert :filebank {:id app-id})
              (delete-fails doc "Filebank")))
          (fact "Bulletin"
            (let [{app-id :LP-id
                   :as    doc} (convert bulletin-id)]
              app-id => truthy
              ;; Environmental bulletin
              (mongo/insert :application-bulletins {:id app-id})
              (delete-fails doc "Bulletin")
              (mongo/remove :application-bulletins app-id)
              (conv-clean/can-be-deleted? doc) => true
              ;; Building permit bulletin
              (mongo/insert :application-bulletins
                            {:id (format "%s_%s" app-id (mongo/create-id))})
              (delete-fails doc "Bulletin")))
          (fact "Linked file metadata"
            (let [{app-id :LP-id
                   :as    doc} (convert linked-id)]
              app-id => truthy
              (mongo/insert :linked-file-metadata
                            {:id            (mongo/create-id)
                             :target-entity {:application-id app-id}})
              (delete-fails doc "Linked file metadata")))
          (fact "Authed users"
            (let [{app-id :LP-id
                   :as    doc} (convert authed-id)]
              app-id => truthy
              (mongo/update-by-id :applications app-id
                                  {$set  {:address "Authed user"}
                                   $push {:auth {:id "some-other-id"}}})
              (delete-fails doc "Authed users")
              (fact "... can be deleted with force? option"
                (force-delete doc))))
          (fact "User attachments"
            (let [{app-id :LP-id
                   :as    doc} (convert atts-id)]
              app-id => truthy
              (mongo/update-by-id :applications app-id
                                  {$set  {:address "User attachment"}
                                   $push {:attachments {:latestVersion {:user {:id "random-id"}}}}})
              (delete-fails doc "User attachments")
              (fact "... with force?"
                (force-delete doc)))))
        (facts "Bad conversions"
          (let [good-id     "22-1001-YES"
                bad-id1     "22-0001-BAD"
                unknown-id  "22-2002-HUH"
                unknown-xml (conv-test/write-test-xml test-dir "unknown.xml" :backend-id unknown-id)
                good-xml    (conv-test/write-test-xml test-dir "not-bad.xml" :backend-id good-id)
                bad1-xml    (conv-test/write-test-xml test-dir "one-bad.xml" :backend-id bad-id1)
                bad-id2     "22-0002-BAD"
                bad2-xml    (conv-test/write-test-xml test-dir "two-bad.xml" :backend-id bad-id2)
                cfg-edn     (conv-test/write-config test-dir "092-R"
                                                    :glob (str test-dir "/*bad.xml")
                                                    :path "")
                cfg         (conv-cfg/read-configuration cfg-edn)]
            (fact "First, successful conversion"
              (conv/convert! cfg-edn)
              (map :backend-id (mongo/select :conversion {} [:backend-id]))
              => (just not-converted-id wrong-org-id archived-id invoiced-id
                       filebanked-id bulletin-id linked-id authed-id atts-id
                       good-id bad-id1 bad-id2
                       :in-any-order))
            (fact "Make bad conversions"
              (conv-test/write-test-xml test-dir "one-bad.xml"
                                        :backend-id bad-id1
                                        :application-id "LP-OOPSIE")
              (mongo/insert :applications {:id           (mongo/create-id)
                                           :organization "092-R"
                                           :verdicts     [{:kuntalupatunnus bad-id2}]})
              ;; Different organization, does not matter
              (mongo/insert :applications {:id           (mongo/create-id)
                                           :organization "753-R"
                                           :verdicts     [{:kuntalupatunnus good-id}]}))
            (facts "Make bad conversion removal target"
              (fact "Good"
                (conv/make-bad-conversion-removal-target cfg {:id good-id}) => nil
                (conv/make-bad-conversion-removal-target cfg {:filename good-xml}) => nil)
              (fact "Bad 1"
                (conv/make-bad-conversion-removal-target cfg {:id bad-id1})
                => nil
                (conv/make-bad-conversion-removal-target cfg {:filename bad1-xml})
                => (contains {:id             bad-id1
                              :conversion-doc (contains {:backend-id bad-id1})}))
              (fact "Bad 2"
                (let [check (contains {:id             bad-id2
                                       :conversion-doc (contains {:backend-id bad-id2})})]
                  (conv/make-bad-conversion-removal-target cfg {:id bad-id2})
                  => check
                  (conv/make-bad-conversion-removal-target cfg {:filename bad2-xml})
                  => check))
              (fact "Unknown"
                (conv/make-bad-conversion-removal-target cfg {:id unknown-id}) => nil
                (conv/make-bad-conversion-removal-target cfg {:filename unknown-xml}) => nil))

            (let [docs        (mongo/select :conversion {})
                  good-app-id (:LP-id (util/find-by-key :backend-id good-id docs))
                  good-app    (mongo/by-id :applications good-app-id)
                  bad-app-id1 (:LP-id (util/find-by-key :backend-id bad-id1 docs))
                  bad-app1    (mongo/by-id :applications bad-app-id1)
                  bad-app-id2 (:LP-id (util/find-by-key :backend-id bad-id2 docs))
                  bad-app2    (mongo/by-id :applications bad-app-id2)]

              (fact "Still good without backend-id application match"
                (mongo/update-by-id :applications good-app-id
                                    {$unset {:verdicts true}})
                (conv/make-bad-conversion-removal-target cfg {:id good-id}) => nil
                (conv/make-bad-conversion-removal-target cfg {:filename good-xml}) => nil)
              (mongo/update-by-id :applications bad-app-id2
                                  {$push {:auth {:id "needs-force"}}})
              (fact "Starting point"
                (mongo/count :applications
                             {:_id {$in [good-app-id bad-app-id1 bad-app-id2]}})
                => 3)
              (facts "Remove bad conversions wrapper"
                (against-background
                 [(lupapalvelu.conversion.core/remove-bad-conversions anything anything)
                  => nil :times 0]
                 (fact "Bad arguments"
                   (conv/remove-bad-conversions-wrapper "") => nil
                   (conv/remove-bad-conversions-wrapper cfg-edn "foo") => nil
                   (conv/remove-bad-conversions-wrapper cfg-edn cfg-edn) => nil
                   (conv/remove-bad-conversions-wrapper cfg-edn "-force" "-force") => nil
                   (conv/remove-bad-conversions-wrapper cfg-edn "-dry-run" "-dry-run" "-force") => nil))
               (against-background
                 [(lupapalvelu.conversion.cleaner/clean-conversion! anything anything)
                  => nil :times 0]
                 (fact "Dry run"
                   (conv/remove-bad-conversions-wrapper cfg-edn "-dry-run") => nil
                   (conv/remove-bad-conversions-wrapper "-force" cfg-edn "-dry-run") => nil))
               (fact "Files initially OK"
                (files-exist? good-app) => true
                (files-exist? bad-app1) => true
                (files-exist? bad-app2) => true)
               (fact "Clean for real"
                 (conv/remove-bad-conversions-wrapper cfg-edn) => nil)
               (fact "Bad 1 is gone Bad2 is not (since authed)"
                 (map :backend-id (mongo/select :conversion
                                                {:backend-id {$in [good-id bad-id1 bad-id2]}}))
                 => [good-id bad-id2]
                 (map :id (mongo/select :applications
                                        {:_id {$in [good-app-id bad-app-id1 bad-app-id2]}}))
                 => [good-app-id bad-app-id2]
                 (files-exist? good-app) => true
                 (files-exist? bad-app1) => false
                 (files-exist? bad-app2) => true)
               (fact "Id-based config and clean with -force"
                 (let [cfg-edn2 (conv-test/write-config test-dir "092-R" :ids [bad-id2])]
                   (conv/remove-bad-conversions-wrapper cfg-edn2 "-force")) => nil)
               (fact "Bad2 is now gone"
                 (map :backend-id (mongo/select :conversion
                                                {:backend-id {$in [good-id bad-id1 bad-id2]}}))
                 => [good-id]
                 (map :id (mongo/select :applications
                                        {:_id {$in [good-app-id bad-app-id1 bad-app-id2]}}))
                 => [good-app-id]
                 (files-exist? good-app) => true
                 (files-exist? bad-app1) => false
                 (files-exist? bad-app2) => false)))))))))
