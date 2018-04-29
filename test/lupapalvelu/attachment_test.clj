(ns lupapalvelu.attachment-test
  (:require [clojure.string :as s]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.strings :refer [encode-filename]]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.attachment :refer :all]
            [lupapalvelu.document.attachments-canonical :refer :all]
            [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment.metadata :refer :all]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [clojure.test :refer [is]]
            [schema.core :as sc]
            [sade.core :refer [now]]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]
            [lupapalvelu.attachment.onkalo-client :as oc]
            [lupapalvelu.action :as action]
            [lupapalvelu.storage.file-storage :as storage]))

(testable-privates lupapalvelu.attachment
                   attachment-file-ids
                   version-number
                   latest-version-after-removing-file
                   build-version-updates
                   default-metadata-for-attachment-type
                   make-ram-attachment
                   attachment-assignment-info
                   version-approval-path
                   signature-updates
                   manually-set-construction-time
                   update-latest-version-file!)

(def ascii-pattern #"[a-zA-Z0-9\-\.]+")

(defspec make-attachement-spec
  (prop/for-all [attachment-id           ssg/object-id
                 now                     ssg/timestamp
                 target                  (ssg/generator Target)
                 required?               gen/boolean
                 requested-by-authority? gen/boolean
                 locked?                 gen/boolean
                 application-state       (gen/elements states/all-states)
                 operation               (ssg/generator Operation)
                 attachment-type         (ssg/generator Type)
                 metadata                (ssg/generator {sc/Keyword sc/Str})
                 ;; Optional parameters
                 contents                (ssg/generator (sc/maybe sc/Str))
                 read-only?              (ssg/generator (sc/maybe sc/Bool))
                 source                  (ssg/generator (sc/maybe Source))]
                (let [validation-error (->> (make-attachment now target required? requested-by-authority? locked? application-state operation attachment-type metadata attachment-id contents read-only? source)
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

(fact "make attachments"
  (let [type1 (ssg/generate Type)
        type2 (ssg/generate Type)]
    (make-attachments 999 :draft [{:type type1} {:type type2}] nil false true true)
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

(fact "All attachments are localized"
  (let [attachment-group-type-paths (->> (vals att-type/attachment-types-by-permit-type)
                                         (apply concat)
                                         (map (juxt :type-group :type-id)))]
    (fact "Meta: collected all types"
      (set (map second attachment-group-type-paths)) => att-type/all-attachment-type-ids)

    (doseq [lang ["fi" "sv"]
            path attachment-group-type-paths
            :let [i18n-path (cons :attachmentType path)
                  args (map name (cons lang i18n-path))
                  info-args (concat args ["info"])]]

      (fact {:midje/description (str lang " " (s/join "." (rest args)))}
        (apply i18n/has-term? args) => true)

      (fact {:midje/description (str lang " " (s/join "." (rest info-args)))}
        (apply i18n/has-term? info-args) => true))))


(fact "make attachments with metadata"
  (let [type1 (ssg/generate Type)
        type2 (ssg/generate Type)
        types-with-metadata [{:type type1 :metadata {:foo "bar"}}
                             {:type type2 :metadata {:bar "baz"}}]]
    (make-attachments 999 :draft types-with-metadata nil false true true)
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
    (make-attachments 999 :draft [{:type type1} {:type type2}] {:groupType "operation" :operations [{:id op-id :name "Foo"}]} false true true)
    => (just [{:id                   "5790633c66e8f95ecc4287be"
               :locked               false
               :modified             999
               :groupType            :operation
               :op                   [{:id op-id :name "Foo"}]
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
               :op                   [{:id op-id :name "Foo"}]
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

(facts remove-operations-updates
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
  (prop/for-all [attachment      (ssg/generator Attachment {Version nil  [Version] (gen/elements [[]])})
                 file-id         (ssg/generator ssc/ObjectIdStr)
                 archivability   (ssg/generator (sc/maybe {:archivable         sc/Bool
                                                           :archivabilityError (apply sc/enum nil conversion/archivability-errors)
                                                           :missing-fonts      (sc/eq ["Arial"])
                                                           :conversionLog      (sc/eq ["error"])}))
                 user            (ssg/generator user/SummaryUser)
                 general-options (ssg/generator {:filename sc/Str
                                                 :contentType sc/Str
                                                 :size sc/Int
                                                 :created ssc/Timestamp
                                                 :stamped (sc/maybe sc/Bool)})]
                (let [options (merge {:fileId file-id :original-file-id file-id} archivability general-options)
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
                  (is (= (:conversionLog version) (:conversionLog options))))))

(defspec make-version-update-existing {:num-tests 20 :max-size 100}
  (prop/for-all [[attachment options] (gen/fmap (fn [[att ver fids opt]] [(-> (update att :versions assoc 0 (assoc ver :originalFileId (first fids)))
                                                                              (assoc :latestVersion (assoc ver :originalFileId (first fids))))
                                                                          (assoc opt :fileId (last fids) :original-file-id (first fids))])
                                                (gen/tuple (ssg/generator Attachment {Version nil [Version] (gen/elements [[]])})
                                                           (ssg/generator Version)
                                                           (gen/vector-distinct ssg/object-id {:num-elements 2})
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
                 file-id        (ssg/generator ssc/ObjectIdStr)
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
                           :type {:type-group "kiinteiston_hallinta"
                                  :type-id "rasitesopimus"}
                           :latestVersion {:created 123}}
                          {:id "rasitesopimus2"
                           :type {:type-group "kiinteiston_hallinta"
                                  :type-id "rasitesopimus"}
                           :latestVersion {:created 321}}
                          {:id "rasitesopimus3"
                           :type {:type-group "kiinteiston_hallinta"
                                  :type-id "rasitesopimus"}}
                          {:id "yhtiojarjestys"
                           :type {:type-group "kiinteiston_hallinta"
                                  :type-id "yhtiojarjestys"}
                           :latestVersion {:created 888}}
                          {:id "empty"}]]

         (fact "Finnish"
               (map :id (sorted-attachments {:application {:attachments attachments} :lang :fi}))
               => ["empty" "rasitesopimus2" "rasitesopimus" "rasitesopimus3" "yhtiojarjestys"])
         (fact "Swedish"
               (map :id (sorted-attachments {:application {:attachments attachments} :lang :sv}))
               => ["empty" "yhtiojarjestys" "rasitesopimus2" "rasitesopimus" "rasitesopimus3"])))

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
    (get-attachment-meta attachment application)
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
      (attachment-metadata-check att3 app ["op1" "op2" "op3"]))))

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
        (:contents (get-attachment-latest-version-file user id false application)) => :from-mongo
        (:contents (get-attachment-latest-version-file user id true application)) => :from-mongo-preview)

      (fact "file is downloaded from Onkalo if not in MongoDB"
        (let [v2 (assoc version :fileId nil :originalFileId nil)
              att2 (assoc attachment :version [v2]
                                     :latestVersion v2)
              app2 (assoc application :attachments [att2])]
          (:contents (get-attachment-latest-version-file user id false app2)) => :from-onkalo
          (:contents (get-attachment-latest-version-file user id true app2)) => :from-onkalo-preview)))

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
           {:attachments.id id}
           {$set {"attachments.$.versions.0.fileId" nil
                  "attachments.$.versions.0.originalFileId" nil}}) => nil
         (action/update-application
           (action/application->command app2)
           {:attachments.id id}
           {$set {"attachments.$.versions.1.fileId" nil
                  "attachments.$.versions.1.originalFileId" nil}}) => nil
         (action/update-application
           (action/application->command app2)
           {:attachments.id id}
           {$set {:attachments.$.latestVersion.fileId nil
                  :attachments.$.latestVersion.originalFileId nil}}) => :application-updated]

        (fact "archived attachments can be deleted from MongoDB"
          (delete-archived-attachments-files-from-mongo! app2 att2) => :application-updated)))))

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
          {:attachments.id id}
          {$set {"attachments.$.versions.1.fileId" "foo"
                 "attachments.$.latestVersion.fileId" "foo"
                 "attachments.$.versions.1.contentType" "bar"
                 "attachments.$.latestVersion.contentType" "bar"
                 "attachments.$.versions.1.modified" ts
                 "attachments.$.latestVersion.modified" ts
                 "attachments.$.versions.1.archivable" true
                 "attachments.$.latestVersion.archivable" true}}) => :updated))))
