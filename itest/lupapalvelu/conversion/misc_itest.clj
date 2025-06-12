(ns lupapalvelu.conversion.misc-itest
  "Tests for conversion-related functionality that is not part of the actual conversion
  pipeline."
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [lupapalvelu.conversion.config :as conv-cfg]
            [lupapalvelu.conversion.core :as conv]
            [lupapalvelu.conversion.itest-util :as conv-test]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [net.cgrand.enlive-html :as enlive]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]))

(def ^:dynamic *test-dir* nil)

(defn dir-fixture [f]
  (binding [*test-dir* (conv-test/mk-test-dir)]
    (f)
    (conv-test/rm-test-dir *test-dir*)))

(use-fixtures :each dir-fixture (local-fixture-factory :db-name "test_conversion-misc-itest"))

(def jarvenpaa-xml
  "Attachments are irrelevant for this test, so let's remove them and make the test
  quicker."
  (-> (io/resource "krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml")
      slurp
      (xml/parse-string "utf8")
      (enlive/at [:yht:linkkiliitteeseen] nil)
      xml/element-to-string))

(def jarvenpaa-id "14-0241-R 3")

(def app-id "LP-186-2014-90001")

(defn exec-conversion [cmd filepath]
  (conv/execute-conversion-command {:cmd      cmd
                                    :cfgfile  filepath
                                    :cmd-help (constantly nil)
                                    :system   (test-system/get-common-components)}))

(defn store-and-convert [xml & kvs]
  {:pre [(conv-test/test-dir? *test-dir*)]}
  (let [filepath     (conv-test/tmp-edn-file *test-dir*)
        xml-filename (format "%s.xml" (random-uuid))]
    (-> (fs/path *test-dir* xml-filename)
        str
        (spit (util/pcond-> xml
                sequential? xml/element-to-string)))
    (apply conv-cfg/write-configuration filepath "186-R"
           :overwrite? true :glob xml-filename kvs)
    (exec-conversion :store filepath)
    (exec-conversion :convert filepath)))

(deftest misc-test
  (store-and-convert jarvenpaa-xml :force-terminal-state? true)
  (let [get-app         #(mongo/by-id :applications app-id)
        application     (get-app)
        op-ids          (fn []
                          (let [a (get-app)]
                            [(-> a :primaryOperation :id)
                             (-> a :secondaryOperations first :id)]))]
    (is (= "closed" (:state application)))
    (testing "Replace operation"
      (is (= "pientalo"
             (-> application :primaryOperation :name)
             (:operation-name application)))
      (is (:ok (command raktark-jarvenpaa :replace-operation :id app-id
                        :opId (first (op-ids))
                        :operation "varasto-tms")))
      (let [a (get-app)]
        (is (= "varasto-tms"
               (-> a :primaryOperation :name)
               (:operation-name a)))))

    (testing "Change primary operation"
      (let [[op-id1 op-id2] (op-ids)]
        (is (every? ss/not-blank? [op-id1 op-id2]))
        (is (:ok (command raktark-jarvenpaa :change-primary-operation :id app-id
                          :secondaryOperationId op-id2)))
        (is (= [op-id2 op-id1] (op-ids)))
        (let [a (get-app)]
        (is (= "pientalo"
               (-> a :primaryOperation :name)
               (:operation-name a))))))))
