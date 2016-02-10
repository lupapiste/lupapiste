(ns lupapalvelu.attachment-test
  (:require [clojure.string :as s]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.strings :refer [encode-filename]]
            [sade.env :as env]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.attachment :refer :all]
            [lupapalvelu.attachment-metadata :refer :all]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.states :as states]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [clojure.test :refer [is]]
            [schema.core :as sc]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]))

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
                 metadata                (ssg/generator  {sc/Str sc/Str})
                 ;; Optional parameters
                 contents                (ssg/generator (sc/maybe sc/Str))
                 read-only?              (ssg/generator (sc/maybe sc/Bool))
                 source                  (ssg/generator (sc/maybe Source))]
                (let [validation-error (->> (make-attachment now target required? requested-by-authority? locked? application-state operation attachment-type metadata attachment-id contents read-only? source)
                                            (sc/check Attachment))]
                  (is (nil? validation-error)  "Validation-error"))))

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

(facts "Test parse-attachment-type"
  (fact (parse-attachment-type "foo.bar")  => {:type-group :foo, :type-id :bar})
  (fact (parse-attachment-type "foo.")     => nil)
  (fact (parse-attachment-type "")         => nil)
  (fact (parse-attachment-type nil)        => nil))

(def test-attachments [{:id "1", :latestVersion {:version {:major 9, :minor 7}}}])

(facts "Test attachment-latest-version"
  (fact (attachment-latest-version test-attachments "1")    => {:major 9, :minor 7})
  (fact (attachment-latest-version test-attachments "none") => nil?))

(facts "Facts about next-attachment-version"
  (fact (next-attachment-version {:major 1 :minor 1} {:role :authority})  => {:major 1 :minor 2})
  (fact (next-attachment-version {:major 1 :minor 1} {:role :dude})       => {:major 2 :minor 0})
  (fact (next-attachment-version nil {:role :authority})  => {:major 0 :minor 1})
  (fact (next-attachment-version nil {:role :dude})       => {:major 1 :minor 0}))

(facts "Facts about allowed-attachment-types-contain?"
  (let [allowed-types [["a" ["1" "2"]] [:b [:3 :4]]]]
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :a :type-id :1}) => truthy)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :a :type-id :2}) => truthy)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :b :type-id :3}) => truthy)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :b :type-id :4}) => truthy)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :b :type-id :5}) => falsey)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :c :type-id :1}) => falsey)))

(fact "version number"
  (version-number {:version {:major 1 :minor 16}}) => 1016)

(fact "Find latest version"
  (let [application {:attachments [{:id :attachment1
                                    :versions []}
                                   {:id :attachment2
                                    :versions [{:version { :major 1 :minor 0 }
                                                :fileId :file1}
                                               {:version { :major 1 :minor 1 }
                                                :fileId :file2}]}]}
        attachments (:attachments application)]
    (latest-version-after-removing-file attachments :attachment2 :file1) => {:version { :major 1 :minor 1}
                                                                             :fileId :file2}

    (attachment-file-ids application :attachment2) => [:file1 :file2]
    (attachment-latest-file-id application :attachment2) => :file2))

(fact "make attachments"
  (make-attachments 999 :draft [{:type :a} {:type :b}] false true true) => (just
                                                                             [{:id "123"
                                                                               :locked false
                                                                               :modified 999
                                                                               :op nil
                                                                               :state :requires_user_action
                                                                               :target nil
                                                                               :type :a
                                                                               :applicationState :draft
                                                                               :contents nil
                                                                               :signatures []
                                                                               :versions []
                                                                               :auth []
                                                                               :notNeeded false
                                                                               :required true
                                                                               :requestedByAuthority true
                                                                               :forPrinting false
                                                                               :readOnly false}
                                                                              {:id "123"
                                                                               :locked false
                                                                               :modified 999
                                                                               :op nil
                                                                               :state :requires_user_action
                                                                               :target nil
                                                                               :type :b
                                                                               :applicationState :draft
                                                                               :contents nil
                                                                               :signatures []
                                                                               :versions []
                                                                               :auth []
                                                                               :notNeeded false
                                                                               :required true
                                                                               :requestedByAuthority true
                                                                               :forPrinting false
                                                                               :readOnly false}])
  (provided
    (mongo/create-id) => "123"))

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

(let [attachments [{:id 1 :versions [{:fileId "11", :version {:major 1, :minor 1}}
                                     {:fileId "21", :version {:major 2, :minor 1}}]}
                   {:id 2 :versions [{:fileId "12", :version {:major 1, :minor 2}}
                                     {:fileId "22", :version {:major 2, :minor 2}}]}]]

  (facts "get attachment version by file id"
    (get-version-by-file-id (first attachments) "11") => {:fileId "11", :version {:major 1, :minor 1}}
    (get-version-by-file-id (first attachments) "21") => {:fileId "21", :version {:major 2, :minor 1}}
    (get-version-by-file-id (first attachments) "10") => nil)

  (facts "get attachment version number by file id"
    (get-version-number {:attachments attachments} 2 "12") => {:major 1, :minor 2}
    (get-version-number {:attachments attachments} 1 "11") => {:major 1, :minor 1}
    (get-version-number {:attachments attachments} 0 "12") => nil))

(fact "attachment type IDs are unique"
  (let [known-duplicates (set (conj attachment-types-osapuoli
                                :ote_asunto-osakeyhtion_kokouksen_poytakirjasta
                                :ote_alueen_peruskartasta
                                :ote_asemakaavasta
                                :ote_kauppa_ja_yhdistysrekisterista
                                :asemapiirros
                                :ote_yleiskaavasta
                                :jaljennos_perunkirjasta
                                :valokuva :rasitesopimus
                                :valtakirja
                                :muu
                                :paatos
                                :paatosote))
        all-except-commons (remove known-duplicates all-attachment-type-ids)
        all-unique (set all-except-commons)]

    (count all-except-commons) => (count all-unique)))

(fact "All attachments are localized"
  (let [attachment-group-type-paths (->>
                                      (vals attachment-types-by-permit-type)
                                      set
                                      (apply concat)
                                      (partition 2)
                                      (map (fn [[g ts]] (map (fn [t] [g t]) ts)))
                                      (apply concat))]
    (fact "Meta: collected all types"
      (set (map second attachment-group-type-paths)) => all-attachment-type-ids)

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
  (let [types-with-metadata [{:type :a :metadata {"foo" "bar"}} {:type :b :metadata {"bar" "baz"}}]]
    (make-attachments 999 :draft types-with-metadata false true true) => (just
                                                                           [{:id "123"
                                                                             :locked false
                                                                             :modified 999
                                                                             :op nil
                                                                             :state :requires_user_action
                                                                             :target nil
                                                                             :type :a
                                                                             :applicationState :draft
                                                                             :contents nil
                                                                             :signatures []
                                                                             :versions []
                                                                             :auth []
                                                                             :notNeeded false
                                                                             :required true
                                                                             :requestedByAuthority true
                                                                             :forPrinting false
                                                                             :metadata {"foo" "bar"}
                                                                             :readOnly false}
                                                                            {:id "123"
                                                                             :locked false
                                                                             :modified 999
                                                                             :op nil
                                                                             :state :requires_user_action
                                                                             :target nil
                                                                             :type :b
                                                                             :applicationState :draft
                                                                             :contents nil
                                                                             :signatures []
                                                                             :versions []
                                                                             :auth []
                                                                             :notNeeded false
                                                                             :required true
                                                                             :requestedByAuthority true
                                                                             :forPrinting false
                                                                             :metadata {"bar" "baz"}
                                                                             :readOnly false}])
    (provided
      (mongo/create-id) => "123")))

(facts "facts about attachment metada"
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
          both-public {:metadata {:nakyvyys "test"
                                  :julkisuusluokka "julkinen"}}
          only-julkisuusluokka {:metadata {:julkisuusluokka "julkinen"}}]

      (public-attachment? no-metadata) => true
      (public-attachment? no-metadata2) => true
      (public-attachment? nakyvyys-not-public) => false
      (public-attachment? jluokka-nakyvyys-not-public) => false
      (fact "julkisuusluokka overrules nakyvyys" (public-attachment? nakyvyys-public) => false)
      (public-attachment? jluokka-public) => true
      (public-attachment? both-public) => true
      (public-attachment? only-julkisuusluokka) => true)))
