(ns lupapalvelu.attachment-test
  (:require [clojure.string :as s]
            [sade.strings :refer [encode-filename]]
            [monger.core :as monger]
            [lupapalvelu.core :as core]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.attachment :refer :all]
            [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(def ascii-pattern #"[a-zA-Z0-9\-\.]+")

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

(testable-privates lupapalvelu.attachment next-attachment-version allowed-attachment-types-contain?)

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
  (make-attachments 999 [:a :b]) => (just
                                      [{:id "123"
                                        :locked false
                                        :modified 999
                                        :op nil
                                        :state :requires_user_action
                                        :target nil
                                        :type :a
                                        :versions []}
                                       {:id "123"
                                        :locked false
                                        :modified 999
                                        :op nil
                                        :state :requires_user_action
                                        :target nil
                                        :type :b
                                        :versions []}])
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

  (facts "create-update-statements"
    (create-update-statements attachments #(= (:id %) 1) "foo" "bar") => {"attachments.0.foo" "bar"}
    (create-update-statements attachments #(#{1 3} (:id %)) "foo" "bar") => {"attachments.0.foo" "bar"
                                                                             "attachments.2.foo" "bar"}
    (create-update-statements attachments (partial by-file-ids ["22"]) "foo" "bar") => {"attachments.1.foo" "bar"}
    (create-update-statements attachments (partial by-file-ids #{"21" "13"}) "foo" "bar") => {"attachments.0.foo" "bar"
                                                                                              "attachments.2.foo" "bar"})
  (facts "create-sent-timestamp-update-statements"
    (create-sent-timestamp-update-statements attachments ["12" "23"] 123) => {"attachments.1.sent" 123
                                                                              "attachments.2.sent" 123}))
