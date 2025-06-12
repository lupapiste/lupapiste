(ns lupapalvelu.attachment-test
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lupapalvelu.action :as action]
            [lupapalvelu.attachment :refer :all]
            [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.attachment.metadata :refer :all]
            [lupapalvelu.attachment.onkalo-client :as oc]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment.version :refer [next-attachment-version]]
            [lupapalvelu.document.attachments-canonical]
            [lupapalvelu.generators.attachment :as att-gen]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.integrations-api]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.ryhti.util :as ryhti-util]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.user :as user]
            [midje.checking.checkers.collection :refer [has-suffix]]
            [midje.experimental :as experimental]
            [midje.sweet :refer :all :exclude [has-suffix]]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [sade.strings :refer [encode-filename]]
            [sade.util :as util]
            [schema.core :as sc]))

(testable-privates lupapalvelu.attachment
                   attachment-file-ids
                   version-number
                   latest-version-after-removing-file
                   build-version-updates
                   attachment-assignment-info
                   version-approval-path
                   signature-updates
                   manually-set-construction-time
                   update-latest-version-file!)

(testable-privates lupapalvelu.document.attachments-canonical
                   get-liite-for-lausunto)

(testable-privates lupapalvelu.integrations.document-export
                   linked-file->pseudo-attachment)

(def ascii-pattern #"[a-zA-Z0-9\-\.]+")

(defspec make-attachment-spec
  (prop/for-all [{:keys
                  [attachment-id now target required? requested-by-authority?
                   locked? application-state operation attachment-type metadata
                   ;; Optional parameter
                   contents read-only? source]} att-gen/make-attachment-generators]
                (let [validation-error (->> (make-attachment now target required? requested-by-authority? locked? application-state operation attachment-type metadata
                                                             (util/mmap attachment-id
                                                                        contents
                                                                        read-only?
                                                                        source))
                                            (sc/check Attachment))]
                  (is (nil? validation-error)))))

(facts "Test file name encoding"
  (fact (encode-filename nil)                                 => nil)
  (fact (encode-filename "foo.txt")                           => "foo.txt")
  (fact (encode-filename (apply str (repeat 255 \x)))         => (apply str (repeat 255 \x)))
  (fact (encode-filename (apply str (repeat 256 \x)))         => (apply str (repeat 255 \x)))
  (fact (encode-filename (apply str (repeat 256 \x) ".txt"))  => (has-suffix ".txt"))
  (fact (encode-filename "\u00c4\u00e4kk\u00f6si\u00e4")      => (just ascii-pattern))
  (fact (encode-filename "/root/secret")                      => (just ascii-pattern))
  (fact (encode-filename "\\Windows\\cmd.exe")                => (just ascii-pattern))
  (fact (encode-filename "12345\t678\t90")                    => (just ascii-pattern))
  (fact (encode-filename "12345\n678\r\n90")                  => (just ascii-pattern)))

(def test-attachments [{:id "1", :latestVersion {:version {:major 9, :minor 7}}}])

(facts "Facts about next-attachment-version"
  (fact (next-attachment-version {:major 1 :minor 1} {:role :authority})  => {:major 1 :minor 2})
  (fact (next-attachment-version {:major 1 :minor 1} {:role :dude})       => {:major 2 :minor 0})
  (fact (next-attachment-version nil {:role :authority})  => {:major 0 :minor 1})
  (fact (next-attachment-version nil {:role :dude})       => {:major 1 :minor 0}))

(fact "version number"
  (version-number {:version {:major 1 :minor 16}}) => 1016)

(fact "Find latest version"
  (let [application {:attachments [{:id :attachment1
                                    :versions []}
                                   {:id :attachment2
                                    :versions [{:version { :major 1 :minor 0 }
                                                :fileId :file1
                                                :originalFileId :originalFileId1}
                                               {:version { :major 1 :minor 1 }
                                                :fileId :file2
                                                :originalFileId :originalFileId2}]}]}
        attachment (last (:attachments application))]
    (latest-version-after-removing-file attachment :file1) => {:version {:major 1 :minor 1}
                                                               :fileId :file2
                                                               :originalFileId :originalFileId2}

    (attachment-file-ids attachment) => (just #{:file1 :originalFileId1 :file2 :originalFileId2})
    (attachment-latest-file-id application :attachment2) => :file2))

(fact "make attachments with ryhti-data-2026"
  (let [type1 (ssg/generate Type)
        type2 (ssg/generate Type)]
    (fact "New attachments get proper ryhti flag"
      (make-attachments 999 :draft [{:type type1} {:type type2}] nil {:locked?                 false
                                                                      :required?               true
                                                                      :requested-by-authority? true
                                                                      :ryhti-data-2026         true})
      => (just [{:id                   "5790633c66e8f95ecc4287be"
                 :locked               false
                 :modified             999
                 :op                   nil
               ;;:state                :requires_user_action
                 :target               nil
                 :type                 type1
                 :applicationState     :draft
                 :contents             nil
                 :signatures           []
                 :versions             []
                 :auth                 []
                 :notNeeded            false
                 :required             true
                 :requestedByAuthority true
                 :ryhti                {:attachmentDocumentKey "8f05d1a9-1bac-4016-a218-159765a211ea"}
                 :forPrinting          false
                 :readOnly             false
                 :backendId            nil}
                {:id                   "5790633c66e8f95ecc4287be"
                 :locked               false
                 :modified             999
                 :op                   nil
               ;;:state                :requires_user_action
                 :target               nil
                 :type                 type2
                 :applicationState     :draft
                 :contents             nil
                 :signatures           []
                 :versions             []
                 :auth                 []
                 :notNeeded            false
                 :required             true
                 :requestedByAuthority true
                 :ryhti                {:attachmentDocumentKey "8f05d1a9-1bac-4016-a218-159765a211ea"}
                 :forPrinting          false
                 :readOnly             false
                 :backendId            nil}])
      (provided
       (mongo/create-id) => "5790633c66e8f95ecc4287be"
       (ryhti-util/ryhti-id :attachmentDocumentKey) => {:ryhti {:attachmentDocumentKey "8f05d1a9-1bac-4016-a218-159765a211ea"}}))))

(fact "make attachments"
  (let [type1 (ssg/generate Type)
        type2 (ssg/generate Type)]
    (make-attachments 999 :draft [{:type type1} {:type type2}] nil {:locked? false :required? true :requested-by-authority? true})
    => (just [{:id                   "5790633c66e8f95ecc4287be"
               :locked               false
               :modified             999
               :op                   nil
               ;;:state                :requires_user_action
               :target               nil
               :type                 type1
               :applicationState     :draft
               :contents             nil
               :signatures           []
               :versions             []
               :auth                 []
               :notNeeded            false
               :required             true
               :requestedByAuthority true
               :forPrinting          false
               :readOnly             false
               :backendId            nil}
              {:id                   "5790633c66e8f95ecc4287be"
               :locked               false
               :modified             999
               :op                   nil
               ;;:state                :requires_user_action
               :target               nil
               :type                 type2
               :applicationState     :draft
               :contents             nil
               :signatures           []
               :versions             []
               :auth                 []
               :notNeeded            false
               :required             true
               :requestedByAuthority true
               :forPrinting          false
               :readOnly             false
               :backendId            nil}])
    (provided
     (mongo/create-id) => "5790633c66e8f95ecc4287be")))

(fact "attachment can be found with file-id"
  (get-attachment-info-by-file-id {:attachments [{:versions [{:fileId "123"}
                                                             {:fileId "234"}]}
                                                 {:versions [{:fileId "345"}
                                                             {:fileId "456"}]}]} "456") => {:versions [{:fileId "345"}
                                                                                                       {:fileId "456"}]})

(let [attachments [{:id 1 :versions [{:fileId "11"} {:fileId "21"}]}
                   {:id 2 :versions [{:fileId "12"} {:fileId "22"}]}
                   {:id 3 :versions [{:fileId "13"} {:fileId "23"}]}]]

  (facts "create-sent-timestamp-update-statements"
    (create-sent-timestamp-update-statements attachments ["12" "23"] 123) => {"attachments.1.sent" 123
                                                                              "attachments.2.sent" 123}))

(def localization-keys-without-info
  #{"attachmentType.tietomallien_liitteet.tarkastustulos_bcf"
    "attachmentType.tietomallien_liitteet.tarkastustulos"
    "attachmentType.tietomallien_liitteet.tarkastusraportti"
    "attachmentType.tietomallien_liitteet.muu_tietomallin_liite"})

(fact "All attachments are localized"
  (let [attachment-group-type-paths (->> att-type/attachment-types-as-type-array
                                         (map (juxt :type-group :type-id)))]
    (fact "Meta: collected all types. There should be over 270 types to test."
      (> (count attachment-group-type-paths) 270) => true
      (set/subset? (->> attachment-group-type-paths (map second) set) att-type/all-attachment-type-ids) => truthy)

    (doseq [lang ["fi" "sv"]
            path attachment-group-type-paths
            :let [i18n-path (cons :attachmentType path)
                  args (map name (cons lang i18n-path))
                  key (str/join "." (rest args))
                  info-args (concat args ["info"])
                  file-name-key (str/join "." (rest args))]]

      (fact {:midje/description (str lang " " file-name-key)}
        (apply i18n/has-term? args) => true)

      (when (not (contains? localization-keys-without-info key))
        (fact {:midje/description (str lang " " (str/join "." (rest info-args)))}
              (apply i18n/has-term? info-args) => true)))))

(fact "make attachments with metadata"
  (let [type1               (ssg/generate Type)
        type2               (ssg/generate Type)
        types-with-metadata [{:type     type1
                              :metadata {:foo "bar"}}
                             {:type     type2
                              :metadata {:bar "baz"}}]]
    (make-attachments 999 :draft types-with-metadata nil {:locked? false :required? true :requested-by-authority? true})
    => (just [{:id                   "5790633c66e8f95ecc4287be"
               :locked               false
               :modified             999
               :op                   nil
               ;;:state                :requires_user_action
               :target               nil
               :type                 type1
               :applicationState     :draft
               :contents             nil
               :signatures           []
               :versions             []
               :auth                 []
               :notNeeded            false
               :required             true
               :requestedByAuthority true
               :forPrinting          false
               :metadata             {:foo "bar"}
               :readOnly             false
               :backendId            nil}
              {:id                   "5790633c66e8f95ecc4287be"
               :locked               false
               :modified             999
               :op                   nil
               ;;:state                :requires_user_action
               :target               nil
               :type                 type2
               :applicationState     :draft
               :contents             nil
               :signatures           []
               :versions             []
               :auth                 []
               :notNeeded            false
               :required             true
               :requestedByAuthority true
               :forPrinting          false
               :metadata             {:bar "baz"}
               :readOnly             false
               :backendId            nil}])
    (provided
     (mongo/create-id) => "5790633c66e8f95ecc4287be")))

(fact "make attachments with group"
  (let [type1 (ssg/generate Type)
        type2 (ssg/generate Type)
        op-id (ssg/generate ssc/ObjectIdStr)]
    (make-attachments 999 :draft [{:type type1} {:type type2}] {:groupType  "operation"
                                                                :operations [{:id   op-id
                                                                              :name "Foo"}]} {:locked? false :required? true :requested-by-authority? true})
    => (just [{:id                   "5790633c66e8f95ecc4287be"
               :locked               false
               :modified             999
               :groupType            :operation
               :op                   [{:id   op-id
                                       :name "Foo"}]
               ;;:state                :requires_user_action
               :target               nil
               :type                 type1
               :applicationState     :draft
               :contents             nil
               :signatures           []
               :versions             []
               :auth                 []
               :notNeeded            false
               :required             true
               :requestedByAuthority true
               :forPrinting          false
               :readOnly             false
               :backendId            nil}
              {:id                   "5790633c66e8f95ecc4287be"
               :locked               false
               :modified             999
               :groupType            :operation
               :op                   [{:id   op-id
                                       :name "Foo"}]
               ;;:state                :requires_user_action
               :target               nil
               :type                 type2
               :applicationState     :draft
               :contents             nil
               :signatures           []
               :versions             []
               :auth                 []
               :notNeeded            false
               :required             true
               :requestedByAuthority true
               :forPrinting          false
               :readOnly             false
               :backendId            nil}])
    (provided
     (mongo/create-id) => "5790633c66e8f95ecc4287be")))

(facts "facts about attachment metadata"
  (fact "visibility"
    (let [no-metadata {}
          no-metadata2 {:metadata {}}
          nakyvyys-not-public {:metadata {:nakyvyys "test"}}
          jluokka-nakyvyys-not-public {:metadata {:nakyvyys "test"
                                                  :julkisuusluokka "test2"}}
          nakyvyys-public {:metadata {:nakyvyys "julkinen"
                                      :julkisuusluokka "test2"}}
          jluokka-public {:metadata {:nakyvyys "test"
                                     :julkisuusluokka "julkinen"}}
          both-public {:metadata {:nakyvyys "julkinen"
                                  :julkisuusluokka "julkinen"}}
          only-julkisuusluokka {:metadata {:julkisuusluokka "julkinen"}}]

      (public-attachment? no-metadata) => true
      (public-attachment? no-metadata2) => true
      (public-attachment? nakyvyys-not-public) => false
      (public-attachment? jluokka-nakyvyys-not-public) => false
      (fact "julkisuusluokka overrules nakyvyys" (public-attachment? nakyvyys-public) => false)
      (public-attachment? jluokka-public) => false
      (public-attachment? both-public) => true
      (public-attachment? only-julkisuusluokka) => true)))

(facts "remove-operations-updates"
  (fact "one operation mathces removed"
    (remove-operation-updates {:groupType :operation :op [{:id ..op-1.. :name ..name-1..}]} ..op-1..)
    => {:groupType nil :op nil})

  (fact "two operations - one mathces removed op"
    (remove-operation-updates {:groupType :operation :op [{:id ..op-1.. :name ..name-1..} {:id ..op-2.. :name ..name-2..}]} ..op-1..)
    => {:groupType :operation :op [{:id ..op-2.. :name ..name-2..}]})

  (fact "two-operations - unmatching op"
    (remove-operation-updates {:groupType :operation :op [{:id ..op-1.. :name ..name-1..} {:id ..op-2.. :name ..name-2..}]} ..op-3..)
    => nil))


(defspec make-version-new-attachment {:num-tests 20 :max-size 100}
  (prop/for-all [attachment      (ssg/generator Attachment {Version   nil
                                                            [Version] (gen/elements [[]])})
                 file-id         (ssg/generator sssc/UUIDStr)
                 archivability   (ssg/generator (sc/maybe {:archivable         sc/Bool
                                                           :archivabilityError (apply sc/enum nil conversion/archivability-errors)
                                                           :missing-fonts      (sc/eq ["Arial"])
                                                           :conversionLog      (sc/eq ["error"])}))
                 user            (ssg/generator user/SummaryUser)
                 general-options (ssg/generator {:filename    sc/Str
                                                 :contentType sc/Str
                                                 :size        sc/Int
                                                 :created     ssc/Timestamp
                                                 :stamped     (sc/maybe sc/Bool)})
                 ryhti-data-2026 (ssg/generator (sc/maybe sc/Bool))]
                (let [options (merge {:fileId           file-id
                                      :original-file-id file-id
                                      :ryhti-data-2026 ryhti-data-2026} archivability general-options)
                      version (make-version attachment user options)]
                  (is (not (nil? (get-in version [:version :minor]))))
                  (is (not (nil? (get-in version [:version :major]))))
                  (is (= (:fileId version)         file-id))
                  (is (= (:originalFileId version) file-id))
                  (is (= (:created version) (:created options)))
                  (is (= (:user version) user))
                  (is (= (:filename version) (:filename options)))
                  (is (= (:contentType version) (:contentType options)))
                  (is (= (:size version) (:size options)))
                  (is (or (not (:stamped options)) (:stamped version)))
                  (is (or (not (:archivable options)) (:archivable version)))
                  (is (= (:archivabilityError version) (:archivabilityError options)))
                  (is (= (:missing-fonts version) (:missing-fonts options)))
                  (is (= (:conversionLog version) (:conversionLog options)))
                  ;; when ryhti-data-2026 false no ryhti related fields
                  (is (or (and (not ryhti-data-2026) (not (get-in version [:ryhti :fileKey])))
                          (and ryhti-data-2026 (get-in version [:ryhti :fileKey])))))))

(defspec make-version-update-existing {:num-tests 20 :max-size 100}
  (prop/for-all [[attachment options] (gen/fmap (fn [[att ver fids opt]] [(-> (update att :versions assoc 0 (assoc ver :originalFileId (first fids)))
                                                                              (assoc :latestVersion (assoc ver :originalFileId (first fids))))
                                                                          (assoc opt :fileId (last fids) :original-file-id (first fids))])
                                                (gen/tuple (ssg/generator Attachment {Version nil [Version] (gen/elements [[]])})
                                                           (ssg/generator Version)
                                                           (gen/vector-distinct ssg/uuid {:num-elements 2})
                                                           (ssg/generator {:filename sc/Str
                                                                           :contentType sc/Str
                                                                           :size sc/Int
                                                                           :created ssc/Timestamp})))
                 user (ssg/generator user/SummaryUser)]
                (let [version (make-version attachment user options)]
                  (is (= (:version version) (get-in attachment [:latestVersion :version])))
                  (is (= (:fileId version) (:fileId options)))
                  (is (= (:originalFileId version) (:original-file-id options)))
                  (is (= (:created version) (:created options)))
                  (is (= (:user version) user))
                  (is (= (:filename version) (:filename options)))
                  (is (= (:contentType version) (:contentType options)))
                  (is (= (:size version) (:size options))))))

(defspec build-version-updates-new-attachment {:num-tests 20 :max-size 100}
  (prop/for-all [attachment     (ssg/generator Attachment {Version nil [Version] (gen/elements [[]])})
                 version-model  (ssg/generator Version)
                 file-id        (ssg/generator sssc/UUIDStr)
                 user           (ssg/generator user/SummaryUser)
                 options        (ssg/generator {:created      ssc/Timestamp
                                                :target       (sc/maybe Target)
                                                :stamped      (sc/maybe sc/Bool)
                                                :comment?     sc/Bool
                                                :comment-text sc/Str})
                 state  (gen/elements (cons nil attachment-states))]
                (let [version-model (assoc version-model :fileId file-id)
                      options       (if state (assoc options :state state) options)
                      updates       (build-version-updates user attachment version-model options)]
                  (is (= (get-in updates [$addToSet :attachments.$.auth :role])
                         (if (:stamped options) :stamper :uploader)))
                  (is (= (get-in updates [$set :modified]) (:created options)))
                  (is (= (get-in updates [$set :attachments.$.modified]) (:created options)))
                  (is (if (:target options)
                        (= (get-in updates [$set :attachments.$.target]) (:target options))
                        (not (contains? (get updates $set) :attachments.$.target))))
                  (is (= (get-in updates [$set :attachments.$.latestVersion]) version-model))
                  (is (= (:state (get-in updates [$set (version-approval-path (:originalFileId version-model))]))
                         (or (:state options) :requires_authority_action)))
                  (is (= (get-in updates [$push :attachments.$.versions]) version-model)))))

(defspec build-version-updates-update-existing-version {:num-tests 20 :max-size 100}
  (prop/for-all [[attachment version-model] (gen/fmap (fn [[att fids]]
                                                        (let [ver (assoc (get-in att [:versions 1]) :originalFileId (first fids))]
                                                          [(-> (assoc-in att [:versions 1] ver)
                                                               (assoc :latestVersion ver))
                                                           ver]))
                                                      (gen/tuple (ssg/generator Attachment {Version   nil
                                                                                            [Version] (gen/vector-distinct (ssg/generator Version) {:min-elements 3})})
                                                                 (gen/vector-distinct ssg/object-id {:num-elements 2})))
                 options (ssg/generator {:created ssc/Timestamp})
                 user (ssg/generator user/SummaryUser)]
                (let [updates (build-version-updates user attachment version-model options)]
                  (and (not (contains? (get updates $set) :attachments.$.latestVersion))
                       (= (get-in updates [$set "attachments.$.versions.1"]) version-model)))))

(facts attachment-assignment-info
  (fact "without contents"
    (attachment-assignment-info {:id ..id..
                                 :type {:type-group "some-type-group" :type-id "some-type-id"}})
    => {:id ..id.. :type-key "attachmentType.some-type-group.some-type-id"})

  (fact "with contents"
    (attachment-assignment-info {:id ..id..
                                 :type {:type-group "some-type-group" :type-id "some-type-id"}
                                 :contents "some content"})
    => {:id ..id.. :type-key "attachmentType.some-type-group.some-type-id" :description "some content"}))

(facts "Sorted attachments"
       (let [attachments [{:id "rasitesopimus"
                           :contents "Rasitesopimus"
                           :type {:type-group "kiinteiston_hallinta"
                                  :type-id "rasitesopimus"}
                           :latestVersion {}}
                          {:id "rasitesopimus2"
                           :contents "Rasitesopimus 2"
                           :type {:type-group "kiinteiston_hallinta"
                                  :type-id "rasitesopimus"}
                           :latestVersion {}}
                          {:id "rasitesopimus3"
                           :contents "Rasitesopimus 3"
                           :type {:type-group "kiinteiston_hallinta"
                                  :type-id "rasitesopimus"}}
                          {:id "energiaselvitys" ; SV: Energikalkyl
                           :type {:type-group "selvitykset"
                                  :type-id "energiataloudellinen_selvitys"}}
                          {:id "energiatodistus" ; SV: Energicertifikat
                           :type {:type-group "selvitykset"
                                  :type-id "energiatodistus"}}
                          {:id "empty"}]]

         (fact "Finnish"
               (map :id (sorted-attachments {:application {:attachments attachments} :lang :fi}))
               => ["rasitesopimus" "rasitesopimus2" "empty" "energiaselvitys" "energiatodistus" "rasitesopimus3"])
         (fact "Swedish"
               (map :id (sorted-attachments {:application {:attachments attachments} :lang :sv}))
               => ["rasitesopimus" "rasitesopimus2" "empty" "energiatodistus" "energiaselvitys" "rasitesopimus3"])))

(facts signature-updates

  (fact "no original-signature provided -> create new"
    (signature-updates {:fileId ..file-id.. :version {:major ..major.. :minor ..minor..}} ..user.. ..ts.. nil)
    => {$push {:attachments.$.signatures {:fileId ..file-id.. :version {:major ..major.. :minor ..minor..} :created ..ts.. :user ..user-summary..}}}
    (provided (lupapalvelu.user/summary ..user..) => ..user-summary..))

  (fact "New version is added and signature is copied from existing version"
    (signature-updates {:fileId ..file-id.. :version ..version..}
                       ..user..
                       ..ts..
                       [{:created ..orig-created.. :user ..orig-user.. :fileId ..orig-file-id.. :version ..orig-version..}])
    => {$push {:attachments.$.signatures {$each [{:fileId ..file-id.. :version ..version.. :created ..orig-created.. :user ..orig-user..}]}}})

  (fact "Multiple signatures may be copied"
    (signature-updates {:fileId ..file-id.. :version ..version..}
                       ..user..
                       ..ts..
                       [{:created ..orig-created.. :user ..orig-user.. :fileId ..orig-file-id.. :version ..orig-version..}
                        {:created ..orig-created2.. :user ..orig-user2.. :fileId ..orig-file-id.. :version ..orig-version..}])
    => {$push {:attachments.$.signatures {$each [{:fileId ..file-id.. :version ..version.. :created ..orig-created.. :user ..orig-user..}
                                                 {:fileId ..file-id.. :version ..version.. :created ..orig-created2.. :user ..orig-user2..}]}}})

  (fact "Passing an empty list of copied signatures does not generate a signature"
    (signature-updates {:fileId ..file-id.. :version ..version..}
                       ..user..
                       ..ts..
                       [])
    => nil))

(facts "Manually set construction time"
  (fact "draft -> verdictGiven"
    (manually-set-construction-time {:applicationState "verdictGiven" :originalApplicationState "draft"})
             => true)
  (fact "info -> verdictGiven"
    (manually-set-construction-time {:applicationState "verdictGiven" :originalApplicationState "info"})
    => true)
  (fact "answered -> verdictGiven"
    (manually-set-construction-time {:applicationState "verdictGiven" :originalApplicationState "answered"})
    => true)
  (fact "No original application state"
    (manually-set-construction-time {:applicationState "verdictGiven"})
    => false)
  (fact "open -> submitted"
    (manually-set-construction-time {:applicationState "submitted" :originalApplicationState "open"})
    => false))

(testable-privates lupapalvelu.document.attachments-canonical
                   get-attachment-meta)

(defn attachment-metadata-check [{att-id :id :as attachment} application op-ids]
  (fact {:midje/description (format "%s -> %s" att-id op-ids)}
    (get-attachment-meta attachment application {})
    => (just (->> (map #(list "toimenpideId" %) op-ids)
                  (cons ["liiteId" att-id])
                  (filter identity)
                  (map #(hash-map :Metatieto {:metatietoArvo (second %) :metatietoNimi (first %)}
                                  :metatieto {:metatietoArvo (second %) :metatietoNimi (first %)})))
             :in-any-order)))

(facts "Attachment meta and operations"
  (let [doc1 (assoc-in {:data []} [:schema-info :op :id] "op1")
        doc2 (assoc-in {:data []} [:schema-info :op :id] "op2")
        doc3 (assoc-in {:data []} [:schema-info :op :id] "op3")
        att1 {:id "att1" :op [{:id "op1"}]}
        att2 {:id "att2" :op [{:id "op1"} {:id "op2"}]}
        att3 {:id "att3"}
        app  {:primaryOperation    {:id "op1"}
              :secondaryOperations [{:id "op2"} {:id "op3"}]
              :documents           [doc1 doc2 doc3]
              :attachments         [att1 att2 att3]}]
    (fact "Attachment with one operation"
      (attachment-metadata-check att1 app ["op1"]))
    (fact "Attachment with two operations"
      (attachment-metadata-check att2 app ["op1" "op2"]))
    (fact "Attachment without explicit operation is linked to every operation"
      (attachment-metadata-check att3 app ["op1" "op2" "op3"]))
    (fact "No toimenpideId metadata for foreman application attachments"
      (attachment-metadata-check att1
                                 (assoc-in app
                                           [:primaryOperation :name]
                                           "tyonjohtajan-nimeaminen-v2")
                                 []))))

(defn extra-metadata-check
  [organization attachment meta-k meta-v]
  (fact {:midje/description (format "%s = %s" meta-k meta-v)}
    (->> (get-attachment-meta attachment {} organization)
         (util/find-first #(-> % :metatieto :metatietoNimi (= meta-k)))
         :metatieto
         :metatietoArvo)
    => meta-v))

(facts "Attachment extra metadata"
  (let [org  {:krysp-extra-attachment-metadata-enabled true}
        att1 {:metadata          {:nakyvyys "julkinen"}
              :applicationState  :draft
              :latestVersion     {:originalFileId "abc1"}
              :approvals         {:abc1 {:state     "ok"
                                         :timestamp 1574771617554
                                         :user      {:firstName "Aatu"
                                                     :lastName  "Atachmentti"}}
                                  :abc2 {:state "requires_user_action"}}}
        att2 {:metadata          {:nakyvyys "asiakas-ja-viranomainen"}
              :applicationState  "verdictGiven"}
        att3 (assoc-in att2 [:metadata :nakyvyys] "viranomainen")
        att4 (assoc att2 :applicationState "constructionStarted")
        ;; Attachment with salassapito metadata
        att5 {:metadata         {:nakyvyys                  "julkinen"
                                 :julkisuusluokka           "osittain-salassapidettava"
                                 :salassapitoaika           25
                                 :salassapitoperuste        "Jääkaapin ovessa on salasana"
                                 :salassapidettavat-osat    "Liitteen sivut 3-5"
                                 :security-period-end       #inst "2050-01-01T00:00:00.000Z"
                                 :henkilotiedot             "sisaltaa"
                                 :suojaustaso               "ei-luokiteltu"
                                 :turvallisuusluokka        "ei-turvallisuusluokkaluokiteltu"
                                 :kayttajaryhma             "viranomaisryhma"
                                 :kayttajaryhmakuvaus       "muokkausoikeus"}
              :applicationState "verdictGiven"}
        ;; Attachment without salassapito metadata
        att6 {:metadata         {:nakyvyys "julkinen"}
              :applicationState "verdictGiven"}]

    (facts "Organization with extra metadata disabled"
      (extra-metadata-check {} att1 "rakentamisenaikainen" nil)
      (extra-metadata-check {} att1 "nakyvyys" nil)
      (extra-metadata-check {} att1 "dokumentinHyvaksymispvm" nil)
      (extra-metadata-check {} att1 "dokumentinHyvaksyja" nil)
      (extra-metadata-check {} att5 "julkisuusluokka" nil)
      (extra-metadata-check {} att5 "salassapitoaika" nil))

    (facts "Application state"
      (extra-metadata-check org att1 "rakentamisenaikainen" nil)
      (extra-metadata-check org att2 "rakentamisenaikainen" "true")
      (extra-metadata-check org att4 "rakentamisenaikainen" "true"))

    (facts "Readable by"
      (extra-metadata-check org att1 "nakyvyys" "julkinen")
      (extra-metadata-check org att2 "nakyvyys" "hankkeen osapuolet ja viranomaiset")
      (extra-metadata-check org att3 "nakyvyys" "liitteen lisääjä ja viranomaiset"))

    (facts "Approval"
      (extra-metadata-check org att1 "dokumentinHyvaksymispvm" "26.11.2019")
      (extra-metadata-check org att2 "dokumentinHyvaksymispvm" nil)
      (extra-metadata-check org att1 "dokumentinHyvaksyja" "Aatu Atachmentti")
      (extra-metadata-check org att2 "dokumentinHyvaksyja" nil))

    (facts "Salassapito metadata"
      (extra-metadata-check org att5 "julkisuusluokka" "osittain-salassapidettava")
      (extra-metadata-check org att5 "salassapitoaika" "25")
      (extra-metadata-check org att5 "salassapitoperuste" "Jääkaapin ovessa on salasana")
      (extra-metadata-check org att5 "salassapidettavat-osat" "Liitteen sivut 3-5")
      (extra-metadata-check org att5 "salassapitoaikapaattyy" "2050-01-01T02:00:00+02:00")
      (extra-metadata-check org att5 "henkilotieto" "sisaltaa")
      (extra-metadata-check org att5 "suojaustaso" "ei-luokiteltu")
      (extra-metadata-check org att5 "turvallisuusluokka" "ei-turvallisuusluokkaluokiteltu")
      (extra-metadata-check org att5 "kayttajaryhma" "viranomaisryhma")
      (extra-metadata-check org att5 "kayttajaryhmakuvaus" "muokkausoikeus"))

    (facts "Salassapito metadata absent when not set"
      (extra-metadata-check org att6 "julkisuusluokka" nil)
      (extra-metadata-check org att6 "salassapitoaika" nil)
      (extra-metadata-check org att6 "salassapitoperuste" nil)
      (extra-metadata-check org att6 "salassapidettavat-osat" nil)
      (extra-metadata-check org att6 "salassapitoaikapaattyy" nil)
      (extra-metadata-check org att6 "henkilotieto" nil)
      (extra-metadata-check org att6 "suojaustaso" nil)
      (extra-metadata-check org att6 "turvallisuusluokka" nil)
      (extra-metadata-check org att6 "kayttajaryhma" nil)
      (extra-metadata-check org att6 "kayttajaryhmakuvaus" nil))))

(facts "BIM linked file salassapito metadata in KRYSP export"
  (let [org {:krysp-extra-attachment-metadata-enabled true}
        bim-linked-file {:target-entity {:target-type    "bim-model-file"
                                         :file-id        "bim-file-id-123"
                                         :application-id "LP-753-2024-00001"
                                         :operation-id   "op-id-456"}
                         :metadata      {:name                "test-model.ifc"
                                         :model-type          "architectural"
                                         :julkisuusluokka     "salainen"
                                         :salassapitoaika     25
                                         :salassapitoperuste  "JulkL 24 § 1 mom 6 kohta"
                                         :security-period-end #inst "2050-01-01T00:00:00.000Z"
                                         :henkilotiedot       "sisaltaa"
                                         :suojaustaso         "ei-luokiteltu"
                                         :turvallisuusluokka  "ei-turvallisuusluokkaluokiteltu"
                                         :kayttajaryhma       "viranomaisryhma"
                                         :kayttajaryhmakuvaus "muokkausoikeus"
                                         :nakyvyys            "viranomainen"}
                         :versions      [{:storage-system   "gcs"
                                          :file-version-id  "version-id-789"
                                          :version          {:major 0 :minor 1}
                                          :uploaded-at      1234567890
                                          :metadata         {:name "test-model.ifc"}}]}
        ;; Convert to pseudo attachment as done in move-attachment-to-backing-system command
        pseudo-att (linked-file->pseudo-attachment bim-linked-file)]

    (facts "Pseudo attachment has metadata for KRYSP export"
      (fact "metadata contains salassapito fields"
        (:metadata pseudo-att) => (contains {:julkisuusluokka "salainen"
                                             :salassapitoaika 25})))

    (facts "Salassapito metadata is exported via extra-attachment-meta"
      (extra-metadata-check org pseudo-att "julkisuusluokka" "salainen")
      (extra-metadata-check org pseudo-att "salassapitoaika" "25")
      (extra-metadata-check org pseudo-att "salassapitoperuste" "JulkL 24 § 1 mom 6 kohta")
      (extra-metadata-check org pseudo-att "salassapitoaikapaattyy" "2050-01-01T02:00:00+02:00")
      (extra-metadata-check org pseudo-att "henkilotieto" "sisaltaa")
      (extra-metadata-check org pseudo-att "suojaustaso" "ei-luokiteltu")
      (extra-metadata-check org pseudo-att "turvallisuusluokka" "ei-turvallisuusluokkaluokiteltu")
      (extra-metadata-check org pseudo-att "kayttajaryhma" "viranomaisryhma")
      (extra-metadata-check org pseudo-att "kayttajaryhmakuvaus" "muokkausoikeus")
      (extra-metadata-check org pseudo-att "nakyvyys" "liitteen lisääjä ja viranomaiset"))

    (facts "Salassapito metadata not exported when organization flag is disabled"
      (extra-metadata-check {} pseudo-att "julkisuusluokka" nil)
      (extra-metadata-check {} pseudo-att "salassapitoaika" nil))

    (facts "BIM model file has no parent reference"
      (fact "metadata does not contain parent-file-id"
        (:parent-file-id (:metadata pseudo-att)) => nil)
      (extra-metadata-check org pseudo-att "parent_id" nil))))

(facts "BIM attachment parent reference in KRYSP export"
  (let [org {:krysp-extra-attachment-metadata-enabled true}
        ;; BIM attachment with parent reference
        bim-attachment {:target-entity {:target-type    "bim-inspection-report"
                                        :file-id        "attachment-file-id-456"
                                        :parent-file-id "parent-model-id-123"
                                        :application-id "LP-753-2024-00001"
                                        :operation-id   "op-id-456"}
                        :metadata      {:name            "inspection-report.pdf"
                                        :julkisuusluokka "julkinen"
                                        :nakyvyys        "julkinen"}
                        :versions      [{:storage-system   "gcs"
                                         :file-version-id  "version-id-abc"
                                         :version          {:major 0 :minor 1}
                                         :uploaded-at      1234567890
                                         :metadata         {:name "inspection-report.pdf"}}]}
        pseudo-att (linked-file->pseudo-attachment bim-attachment)]

    (facts "Pseudo-attachment includes parent-file-id"
      (fact "metadata contains parent-file-id"
        (:parent-file-id (:metadata pseudo-att)) => "parent-model-id-123"))

    (facts "Parent reference is exported via extra-attachment-meta"
      (extra-metadata-check org pseudo-att "parent_id" "parent-model-id-123"))))

(deftest bim-pseudo-attachment-type-uses-strings
  (testing "linked-file->pseudo-attachment produces string type-id and type-group, not keywords"
    (let [bim-model  {:target-entity {:target-type    "bim-model-file"
                                      :file-id        "bim-file-id"
                                      :application-id "LP-753-2024-00001"
                                      :operation-id   "op-id"}
                      :metadata      {:name       "model.ifc"
                                      :model-type "architectural"
                                      :nakyvyys   "julkinen"}
                      :versions      [{:file-version-id "version-id"
                                       :version         {:major 0
                                                         :minor 1}
                                       :uploaded-at     1234567890
                                       :metadata        {:name "model.ifc"}}]}
          pseudo-att (linked-file->pseudo-attachment bim-model)]
      (is (= "tietomallit" (get-in pseudo-att [:type :type-group])))
      (is (= "arkkitehtimalli_BIM" (get-in pseudo-att [:type :type-id]))))))

(defn- find-meta-value
  "Finds a metadata value by name from get-attachment-meta result."
  [meta-entries meta-name]
  (->> meta-entries
       (some #(when (= meta-name (-> % :metatieto :metatietoNimi))
                (-> % :metatieto :metatietoArvo)))))

(deftest bim-pseudo-attachment-rakentamisenaikainen-metadata-test
  (let [org {:krysp-extra-attachment-metadata-enabled true}
        ;; BIM model file with construction-time phase (post-verdict)
        bim-model-construction {:target-entity {:target-type    "bim-model-file"
                                                :file-id        "bim-file-id-raka"
                                                :application-id "LP-753-2024-00001"
                                                :operation-id   "op-id-456"}
                                :metadata      {:name        "test-model.ifc"
                                               :model-type  "architectural"
                                               :model-phase "construction-time"
                                               :nakyvyys    "julkinen"}
                                :versions      [{:storage-system   "gcs"
                                                 :file-version-id  "version-id-raka"
                                                 :version          {:major 0 :minor 1}
                                                 :uploaded-at      1234567890
                                                 :metadata         {:name "test-model.ifc"}}]}
        ;; BIM model file with submission-time phase (pre-verdict)
        bim-model-submission (assoc-in bim-model-construction [:metadata :model-phase] "submission-time")
        ;; BIM model file with completion-time phase (post-verdict)
        bim-model-completion (assoc-in bim-model-construction [:metadata :model-phase] "completion-time")
        ;; BIM model file without model-phase
        bim-model-no-phase (update bim-model-construction :metadata dissoc :model-phase)]

    (testing "construction-time model-phase produces rakentamisenaikainen=true"
      (let [pseudo-att   (linked-file->pseudo-attachment bim-model-construction)
            meta-entries (get-attachment-meta pseudo-att {} org)]
        (is (= "verdictGiven" (:applicationState pseudo-att)))
        (is (= "true" (find-meta-value meta-entries "rakentamisenaikainen")))))

    (testing "completion-time model-phase produces rakentamisenaikainen=true"
      (let [pseudo-att   (linked-file->pseudo-attachment bim-model-completion)
            meta-entries (get-attachment-meta pseudo-att {} org)]
        (is (= "verdictGiven" (:applicationState pseudo-att)))
        (is (= "true" (find-meta-value meta-entries "rakentamisenaikainen")))))

    (testing "submission-time model-phase does not produce rakentamisenaikainen"
      (let [pseudo-att   (linked-file->pseudo-attachment bim-model-submission)
            meta-entries (get-attachment-meta pseudo-att {} org)]
        (is (= "submitted" (:applicationState pseudo-att)))
        (is (nil? (find-meta-value meta-entries "rakentamisenaikainen")))))

    (testing "Without model-phase, no applicationState is derived"
      (let [pseudo-att   (linked-file->pseudo-attachment bim-model-no-phase)
            meta-entries (get-attachment-meta pseudo-att {} org)]
        (is (nil? (:applicationState pseudo-att)))
        (is (nil? (find-meta-value meta-entries "rakentamisenaikainen")))))

    (testing "Without organization flag, rakentamisenaikainen is not present"
      (let [pseudo-att   (linked-file->pseudo-attachment bim-model-construction)
            meta-entries (get-attachment-meta pseudo-att {} {})]
        (is (nil? (find-meta-value meta-entries "rakentamisenaikainen")))))))

(facts included-in-published-bulletin?
  (fact "attachment is included in bulletins"
    (included-in-published-bulletin? {:history [{:bulletin-published true}]
                                      :attachments [{:id ..att-id..}]}
                                     (delay [{:versions [{:bulletinState "verdictGiven"
                                                          :appealPeriodStartsAt 0
                                                          :appealPeriodEndsAt java.lang.Long/MAX_VALUE
                                                          :attachments [{:id ..att-id..}]}]}])
                                     ..att-id..)
    => truthy)

  (fact "application is not published as bulletins"
    (included-in-published-bulletin? {:history []
                                      :attachments [{:id ..att-id..}]}
                                     (delay [{:versions [{:bulletinState "verdictGiven"
                                                          :appealPeriodStartsAt 0
                                                          :appealPeriodEndsAt java.lang.Long/MAX_VALUE
                                                          :attachments [{:id ..att-id..}]}]}])
                                     ..att-id..)
    => falsey)

  (fact "appeal period is ended"
    (included-in-published-bulletin? {:history [{:bulletin-published true}]
                                      :attachments [{:id ..att-id..}]}
                                     (delay [{:versions [{:bulletinState "verdictGiven"
                                                          :appealPeriodStartsAt 0
                                                          :appealPeriodEndsAt 1
                                                          :attachments [{:id ..att-id..}]}]}])
                                     ..att-id..)
    => falsey)

  (fact "no bulletins"
    (included-in-published-bulletin? {:history [{:bulletin-published true}]
                                      :attachments [{:id ..att-id..}]}
                                     (delay nil)
                                     ..att-id..)
    => falsey)

  (fact "multiple bulletins - one bulletin is active"
    (included-in-published-bulletin? {:history [{:bulletin-published true}]
                                      :attachments [{:id ..att-id..}]}
                                     (delay [{:versions [{:bulletinState "verdictGiven"
                                                          :appealPeriodStartsAt 0
                                                          :appealPeriodEndsAt 1
                                                          :attachments [{:id ..att-id..}]}]}
                                             {:versions [{:bulletinState "verdictGiven"
                                                          :appealPeriodStartsAt 0
                                                          :appealPeriodEndsAt java.lang.Long/MAX_VALUE
                                                          :attachments [{:id ..att-id..}]}]}])
                                     ..att-id..)
    => truthy))

(facts "about linking files to Onkalo"
  (let [user {:id "foo"}
        file-id "12345"
        original-file-id "67890"
        onkalo-file-id "abcdef"
        version (merge (ssg/generate Version)
                       {:user user
                        :fileId file-id
                        :originalFileId original-file-id
                        :onkaloFileId onkalo-file-id})
        {:keys [id] :as attachment} (merge (ssg/generate Attachment)
                                           {:versions [version]
                                            :latestVersion version
                                            :metadata {:nakyvyys :julkinen
                                                       :tila :arkistoitu}
                                            :auth [{:role "uploader"
                                                    :id "foo"}]})
        app-id "LP-XXX-2017-00001"
        application {:id app-id
                     :attachments [attachment]
                     :organization "186-R"
                     :auth [{:role "writer"
                             :id "foo"}]}]
    (against-background
      [(storage/download app-id file-id attachment) => {:contents :from-mongo}
       (storage/download-preview app-id file-id attachment) => {:contents :from-mongo-preview}
       (oc/get-file "186-R" onkalo-file-id false) => {:contents :from-onkalo}
       (oc/get-file "186-R" onkalo-file-id true) => {:contents :from-onkalo-preview}]

      (fact "file is normally downloaded from MongoDB"
        (:contents (get-attachment-latest-version-file {} user id false application)) => :from-mongo
        (:contents (get-attachment-latest-version-file {} user id true application)) => :from-mongo-preview)

      (fact "file is downloaded from Onkalo if not in MongoDB"
        (let [v2 (assoc version :fileId nil :originalFileId nil)
              att2 (assoc attachment :version [v2]
                                     :latestVersion v2)
              app2 (assoc application :attachments [att2])]
          (:contents (get-attachment-latest-version-file {} user id false app2)) => :from-onkalo
          (:contents (get-attachment-latest-version-file {} user id true app2)) => :from-onkalo-preview)))

    (let [file-id-2 "foobar"
          original-file-id-2 "foobar2"
          v2 (merge (ssg/generate Version)
                    {:user user
                     :fileId file-id-2
                     :originalFileId original-file-id-2})
          att2 (merge attachment
                      {:versions [v2 version]})
          app2 (assoc application :attachments [att2])]

      (against-background
        [(storage/delete app2 file-id) => true
         (storage/delete app2 original-file-id) => true
         (storage/delete app2 (str file-id "-preview")) => true
         (storage/delete app2 (str original-file-id "-preview")) => true
         (storage/delete app2 file-id-2) => true
         (storage/delete app2 original-file-id-2) => true
         (storage/delete app2 (str file-id-2 "-preview")) => true
         (storage/delete app2 (str original-file-id-2 "-preview")) => true
         (action/update-application
           (action/application->command app2)
           {:attachments {$elemMatch {:id id}}}
           {$set {"attachments.$.versions.0.fileId" nil
                  "attachments.$.versions.0.originalFileId" nil}}) => nil
         (action/update-application
           (action/application->command app2)
           {:attachments {$elemMatch {:id id}}}
           {$set {"attachments.$.versions.1.fileId" nil
                  "attachments.$.versions.1.originalFileId" nil}}) => nil
         (action/update-application
           (action/application->command app2)
           {:attachments {$elemMatch {:id id}}}
           {$set {:attachments.$.latestVersion.fileId nil
                  :attachments.$.latestVersion.originalFileId nil}}) => :application-updated]

        (fact "archived attachments can be deleted from MongoDB"
          (delete-archived-attachments-files-from-storage! app2 att2) => :application-updated)))))

(facts "about in-place attachment conversion"
  (let [id "afdagdgahd"
        attachment {:id id
                    :versions [{:fileId "1"
                                :originalFileId "4"}
                               {:fileId "2"
                                :originalFileId "3"}]
                    :latestVersion {:fileId "2"
                                    :originalFileId "3"}}
        ts (now)]
    (fact "update-latest-version-file! works properly"
      (update-latest-version-file! {}
                                   attachment
                                   {:result {:archivable true}
                                    :file {:fileId "foo"
                                           :contentType "bar"}}
                                   ts) => :updated
      (provided
        (action/update-application
          (action/application->command {})
          {:attachments {$elemMatch {:id id}}}
          {$set {"attachments.$.versions.1.fileId" "foo"
                 "attachments.$.latestVersion.fileId" "foo"
                 "attachments.$.versions.1.contentType" "bar"
                 "attachments.$.latestVersion.contentType" "bar"
                 "attachments.$.versions.1.modified" ts
                 "attachments.$.latestVersion.modified" ts
                 "attachments.$.versions.1.archivable" true
                 "attachments.$.latestVersion.archivable" true}}) => :updated))))


(experimental/for-all
  "statement-attachments"
  [statement-attachment (ssg/generator
                          Attachment
                          {Type   (gen/return {:type-id    :lausunto
                                               :type-group :ennakkoluvat_ja_lausunnot})
                           Target (gen/hash-map
                                    :id ssg/object-id
                                    :type (gen/return :statement))})]
  {:max-size 20 :num-tests 250}
  (let [{{:keys [tyyppi kuvaus]} :Liite} (get-liite-for-lausunto
                                           (update statement-attachment :latestVersion
                                                   merge
                                                   {:fileId   (mongo/create-id)
                                                    :filename (ssg/generate ssc/NonBlankStr)})
                                           {:title "Test Application"}
                                           nil
                                           "test://")]
    (fact "tyyppi is fixed"
      tyyppi => "lausunto")
    (fact "kuvaus is correct"
      kuvaus => #(str/starts-with? % "Test Application: lausunto-"))))

(against-background
  [(lupapalvelu.user/get-user-by-id "bob-id" anything) => {:language "en"}
   (lupapalvelu.user/get-user-by-id "tove-id" anything) => {:language "sv"}
   (lupapalvelu.user/get-user-by-id "pena-id" anything) => {:language "fi"}
   (lupapalvelu.user/get-user-by-id "unknown-id" anything) => nil
   (lupapalvelu.user/get-user-by-id nil anything) => nil]
  (facts "Resolve comment attachment language"
    (resolve-lang-for-comments-attachment {}) => "fi"
   (resolve-lang-for-comments-attachment nil) => "fi"
   (resolve-lang-for-comments-attachment {:handlers []}) => "fi"
   (resolve-lang-for-comments-attachment {:handlers [{:id     "bob-id"
                                                      :roleId "pena-id"
                                                      :userId "tove-id"}]})
   => "sv"
   (resolve-lang-for-comments-attachment {:handlers [{}
                                                     {:id     "bob-id"
                                                      :roleId "pena-id"
                                                      :userId "tove-id"}]})
   => "fi"
   (resolve-lang-for-comments-attachment {:handlers [{:userId "bob-id"}]})
   => "en"
   (resolve-lang-for-comments-attachment {:handlers [{:userId "pena-id"}]})
   => "fi"
   (resolve-lang-for-comments-attachment {:handlers [{:userId "unknown-id"}]})
   => "fi"))

(facts "make-attachment archive-only flag"
  (let [attachment-type {:type-group :muut :type-id :paatoksen_aikaiset_hankkeen_tiedot}
        base-args       [0 nil false false false :verdictGiven nil attachment-type nil]]
    (fact "archiveOnly is not set by default"
      (-> (apply make-attachment base-args)
          (contains? :archiveOnly))
      => false)
    (fact "archiveOnly true is set when passed"
      (:archiveOnly (apply make-attachment (concat base-args [{:archive-only? true}])))
      => true)
    (fact "archive-only? false does not set the key"
      (-> (apply make-attachment (concat base-args [{:archive-only? false}]))
          (contains? :archiveOnly))
      => false)))
