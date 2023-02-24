(ns lupapalvelu.conversion.conversion-runner-itest
  (:require [clojure.java.io :as io]
            [lupapalvelu.conversion.config :as conv-cfg]
            [lupapalvelu.conversion.core :as conv]
            [lupapalvelu.conversion.kuntagml-converter :as conv-k]
            [lupapalvelu.conversion-test-util :as conv-test]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.core :refer [now]]
            [sade.util :as util]
            [sade.xml :as xml]))

(def db-name (str "test_conversion-runner-itest_" (now)))

(def non-terminal-testfile "dev-resources/krysp/0777-88-D.xml")

(def test-backend-id "99-0000-13-A")

(def non-terminal-test-backend-id "0777-88-D")

(def järvenpää-xml (conv-test/transform-xml
                     (io/resource "krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml")))

(def järvenpää-id "14-0241-R 3")

(def non-kuntagml-file "dev-resources/mml/yhteystiedot-JU.xml")

(def vantaa-fallback-location-info {:address    "Kielotie 20 C"
                                    :propertyId "09206101180002"
                                    :x          391513.021
                                    :y          6685671.373})

(defn parse-muu-tunnus
  "Returns [sovellus tunnus] or nil."
  [xml-string]
  (when-let [node (xml/select1 (xml/parse-string xml-string "utf8")
                               [:rakval:luvanTunnisteTiedot :yht:LupaTunnus
                                :yht:muuTunnustieto :yht:MuuTunnus])]
    (mapv #(xml/get-text node [%]) [:yht:sovellus :yht:tunnus])))

(defn conversions
  ([q]
   (seq (mongo/select :conversion q)))
  ([]
   (conversions {:organization "092-R"})))

(let [test-dir (conv-test/mk-test-dir)]
  (against-background
    [(after :contents (conv-test/rm-test-dir test-dir))]

    (mount/start #'mongo/connection)
    (mongo/with-db db-name
      (fixture/apply-fixture "minimal")

      (facts "resolve-filepath"
        (conv-cfg/resolve-filepath "" "foo/bar.txt")
        => (conv-test/abspath "foo/bar.txt")
        (conv-cfg/resolve-filepath "/" "foo/bar.txt")
        => (conv-test/abspath "/foo/bar.txt")
        (conv-cfg/resolve-filepath "/" "bar.txt")
        => (conv-test/abspath "/bar.txt")
        (conv-cfg/resolve-filepath (conv-test/tmp-edn-file test-dir) "bar.txt")
        => (conv-test/abspath (str test-dir "/bar.txt"))
        (conv-cfg/resolve-filepath "/hii/hoo/doh.txt" "/foo/bar.txt")
        => (conv-test/abspath "/foo/bar.txt"))

      (fact "Configuration file"
        (fact "Write: Bad arguments"
          (conv-test/write-config test-dir "092-R" :permit-type "BAD"
                                  :ids ["99-0001-13-A" "99-0002-13-A"])
          => (throws)
          (conv-test/write-config test-dir "092-R" :permit-type "YA" :ids ["  "])
          => (throws)
          (conv-test/write-config test-dir "092-R" :permit-type "YA"
                                  :ids ["99-0001-13-A" "bad"]) =>
          (throws))
        (fact "Write: id not found"
          (conv-test/write-config test-dir "123-R" :glob non-kuntagml-file :path "/foo/bar") => nil)
        (fact "Relative paths: path not blank"
          (util/read-edn-file (conv-test/write-config test-dir "123-R"
                                                      :glob conv-test/testfile
                                                      :path "/foo/bar" :skip-id-validation? true))
          => {:organization-id       "123-R"
              :permit-type           "R"
              :overwrite?            false
              :force-terminal-state? false
              :files                 ["/foo/bar/99-0000-13-A.xml"]})
        (fact "Relative paths: path blank"
          (util/read-edn-file (conv-test/write-config test-dir "123-R"
                                                      :glob conv-test/testfile
                                                      :path "" :skip-id-validation? true))
          => {:organization-id       "123-R"
              :permit-type           "R"
              :overwrite?            false
              :force-terminal-state? false
              :files                 ["99-0000-13-A.xml"]})
        (fact "Read: Bad data"
          (conv-cfg/read-configuration "foobar.edn") => (throws)
          (provided (sade.util/read-edn-file "foobar.edn") => {})
          (conv-cfg/read-configuration "foobar.edn") => (throws)
          (provided (sade.util/read-edn-file "foobar.edn") => {:organization-id "092-R"
                                                               :permit-type     "bad"})
          (conv-cfg/read-configuration "foobar.edn") => (throws)
          (provided (sade.util/read-edn-file "foobar.edn") => {:organization-id "092-R"
                                                               :permit-type     "R"
                                                               :backend-ids     ["bad"]}))
        (fact "Configuration can have missing files"
          (conv-cfg/read-configuration "foobar.edn")
          => {:organization-id "092-R"
              :permit-type     "R"
              :municipality    "092"
              :targets         [{:id "99-0001-13-A"}
                                {:id "99-0002-13-A"}
                                {:filename (conv-test/abspath "no-such-file.xml")}]}
          (provided (sade.util/read-edn-file "foobar.edn")
                    => {:organization-id "092-R"
                        :permit-type     "R"
                        :backend-ids     ["99-0001-13-A" "99-0002-13-A"]
                        :files           ["no-such-file.xml"]}))
        (fact "Read: Good data"
          (conv-cfg/read-configuration "foobar.edn")
          => {:organization-id "092-R"
              :permit-type     "R"
              :municipality    "092"
              :overwrite?      true
              :targets         [{:id "99-0001-13-A"}
                                {:id "99-0002-13-A"}
                                {:filename (conv-test/abspath conv-test/testfile)}]}
          (provided (sade.util/read-edn-file "foobar.edn")
                    => {:organization-id " 092-R "
                        :permit-type     " R "
                        :overwrite?      true
                        :backend-ids     ["  99-0001-13-A " " 99-0002-13-A "]
                        :files           [(str "  " conv-test/testfile "  ")]})))

      (facts "Send conversion messages wrapper"
        (fact "No args"
          (conv/send-conversion-messages-wrapper) => nil
          (provided
            (conv/send-conversion-messages anything anything anything) => nil :times 0))
        (fact "Bad options"
          (conv/send-conversion-messages-wrapper "-foobar" "file.edn") => nil
          (provided
            (conv/send-conversion-messages anything anything anything) => nil :times 0)
          (conv/send-conversion-messages-wrapper "-kuntagml" "-KUNTAGML" "file.edn") => nil
          (provided
            (conv/send-conversion-messages anything anything anything) => nil :times 0)
          (conv/send-conversion-messages-wrapper "-kuntagml" "-state-changes" "-state-changes" "file.edn") => nil
          (provided
            (conv/send-conversion-messages anything anything anything) => nil :times 0))
        (fact "Filename is mandatory"
          (conv/send-conversion-messages-wrapper "-state-changes" "-kuntagml") => nil
          (provided
            (conv/send-conversion-messages anything anything anything) => nil :times 0))
        (fact "Case insensitive"
          (conv/send-conversion-messages-wrapper "-sTaTe-ChaNgeS" "-KUNTAGML" "CONFIG.EDN") => nil
          (provided
            (conv/send-conversion-messages "CONFIG.EDN" true true) => nil :times 1))
        (fact "Options are optional"
          (conv/send-conversion-messages-wrapper "config.edn") => nil
          (provided
            (conv/send-conversion-messages "config.edn" false false) => nil :times 1)
          (conv/send-conversion-messages-wrapper "config.edn" "-kuntagml") => nil
          (provided
            (conv/send-conversion-messages "config.edn" false true) => nil :times 1)
          (conv/send-conversion-messages-wrapper "-state-changes" "config.edn") => nil
          (provided
            (conv/send-conversion-messages "config.edn" true false) => nil :times 1)))

      (facts "XML with application id"
        (fact "The testfile does not contain any application id"
          (parse-muu-tunnus (slurp conv-test/testfile)) => nil)

        (fact "Augment testfile with application id"
          (let [xmlstr (conv/xml-with-application-id conv-test/testfile "LP-MY-BETTER-IDENTIFIER")]
            (string? xmlstr) => true
            (parse-muu-tunnus xmlstr) => ["Lupapiste" "LP-MY-BETTER-IDENTIFIER"])))

      (fact "No conversions pending"
        (conversions) => nil)
      (facts "Generate configuration file and run conversion"
        (let [edn-file (conv-test/write-config test-dir "092-R"
                                               :glob conv-test/testfile)]
          (fact "Write and read configuration"
            (conv-cfg/read-configuration edn-file)
            => {:organization-id       "092-R"
                :permit-type           "R"
                :municipality          "092"
                :overwrite?            false
                :force-terminal-state? false
                :targets               [{:filename (conv-test/abspath conv-test/testfile)}]})
          (conv/convert! edn-file) => nil
          (let [[{app-id :LP-id :as r} & _ :as results] (conversions)]
            (count results) => 1
            r => (contains {:backend-id test-backend-id
                            :converted  true})
            (count (:conversion-timestamps r)) => 1
            (fact "Application is created"
              (let [{:keys [verdicts created]
                     :as   a} (mongo/by-id :applications app-id)]
                a => (contains {:facta-imported true
                                :propertyId     (:propertyId vantaa-fallback-location-info)
                                :address        (:address vantaa-fallback-location-info)
                                :municipality   "092"
                                :organization   "092-R"})
                (count verdicts) => 1
                (:kuntalupatunnus (first verdicts)) => test-backend-id
                ;; Add auth to the application
                (mongo/update-by-id :applications app-id {$set  {:address "Conversion Corner"}
                                                          $push {:auth {:id "hello"}}})


                (fact "Overwrite false leaves the application as is"
                  (conv/convert! edn-file) => nil
                  (:address (mongo/by-id :applications app-id))
                  => "Conversion Corner"
                  (conversions) => results)

                (let [edn-file   (conv-test/write-config test-dir "092-R"
                                                         :overwrite? true
                                                         :glob conv-test/testfile)
                      unchanged? (fn []
                                   (fact "No conversion"
                                     (:address (mongo/by-id :applications app-id))
                                     => "Conversion Corner"
                                     (conversions) => results))]
                  (fact "Overwrite fails since the application is authed"
                    (conv/convert! edn-file) => nil
                    (unchanged?))
                  (mongo/update-by-id :applications app-id {$pull {:auth {:id "hello"}}})
                  (fact "Only previously imported can be overwritten"
                    (mongo/update-by-id :applications app-id {$unset {:facta-imported 1}})
                    (conv/convert! edn-file) => nil
                    (unchanged?)
                    (fact "Successful overwrite"
                      (mongo/update-by-id :applications app-id {$set {:facta-imported true
                                                                      :foo            "bar"}})
                      (conv/convert! edn-file) => nil
                      (let [[{app-id2 :LP-id :as r} & _ :as results] (conversions)
                            a                                        (mongo/by-id :applications app-id)]
                        (count results) => 1
                        app-id2 => app-id
                        (count (:conversion-timestamps r)) => 2
                        (:converted r) => true
                        (> (:created a) created) => true
                        (contains? a :foo) => false
                        (:address a) => (:address vantaa-fallback-location-info)))
                    (let [application  (mongo/by-id :applications app-id)
                          organization (mongo/by-id :organizations "092-R")
                          user         (usr/batchrun-user ["092-R"])
                          kuntagml     (conv/xml-with-application-id conv-test/testfile app-id)]
                      (facts "Send messages to backing system"
                        (conv/send-conversion-messages edn-file true true) => nil
                        (provided
                          (lupapalvelu.matti/send-state-changes user organization application nil)
                          => nil :times 1
                          (lupapalvelu.matti/send-kuntagml user organization application :conversion kuntagml)
                          => nil :times 1))))))))))

      (facts "Terminal state coercion"
        (let [convert-and-get (fn [& [force?]]
                                (let [edn-file (conv-test/write-config test-dir "092-R"
                                                                       :glob non-terminal-testfile
                                                                       :overwrite? true
                                                                       :force-terminal-state? force?)
                                      _        (conv/convert! edn-file)
                                      results  (conversions {:backend-id non-terminal-test-backend-id})]
                                  (mongo/by-id :applications (-> results first :LP-id))))]

          (let [{non-forced-state :state
                 verdicts         :verdicts} (convert-and-get)
                {forced-state :state}        (convert-and-get true)]
            (fact "Force-terminal-state false"
              non-forced-state => "submitted")
            (fact "Force-terminal-state true"
              forced-state => "closed")
            (fact "Draft verdict is created"
              verdicts => (just (just {:id              truthy
                                       :draft           true
                                       :kuntalupatunnus "0777-88-D"
                                       :timestamp       nil
                                       :paatokset       []}))))))

      (facts "Non-Vantaa conversion"
        (let [xml-filepath (str test-dir "/jarvenpaa-kuntagml.xml")
              _            (spit xml-filepath järvenpää-xml )
              edn-file     (conv-test/write-config test-dir "186-R"
                                                   :glob xml-filepath :path "")]
          (mongo/any? :applications {:organization "186-R"}) => false
          (conversions {:organization "186-R"}) => nil
          (conv/convert! edn-file) => nil
          (let [[{app-id :LP-id :as r} & _
                 :as results] (conversions {:organization "186-R"})
                {:keys [attachments verdicts]
                 :as   a}     (mongo/by-id :applications app-id)
                vid           (:id (first verdicts))]
            (count results) => 1
            r = > (contains {:converted    true
                             :organization "186-R"})
            a => (contains {:facta-imported true
                            :municipality   "186"
                            :organization   "186-R"
                            :propertyId     "18600303560006"
                            :address        "Kylykuja 3-5 D 35b-c"
                            :verdicts       (just (contains {:kuntalupatunnus järvenpää-id}))})
            (count attachments) => 1
            (first attachments)
            => (contains {:target        (contains {:id      vid
                                                    :type    "verdict"
                                                    :urlHash truthy})
                          :contents      "Päätösote"
                          :type          { :type-id   "paatosote"
                                          :type-group "paatoksenteko" }
                          :latestVersion (contains {:archivable     true
                                                    :autoConversion true
                                                    :filename       "sample-attachment.pdf"})})
            (fact "Add missing backend ids"
              (mongo/update-by-id :applications app-id {$unset {:verdicts true}})
              (fact "Verdicts are gone"
                (:verdicts (mongo/by-id :applications app-id)) => nil)
              (fact "Add missing"
                (conv/add-missing-backend-ids "186-R")
                (let [{:keys [verdicts]} (mongo/by-id :applications app-id)]
                  verdicts => (just (just {:id              truthy
                                           :draft           true
                                           :kuntalupatunnus järvenpää-id
                                           :timestamp       nil
                                           :paatokset       []}))
                  (fact "Not added if not needed"
                    (conv/add-missing-backend-ids "186-R")
                    (:verdicts (mongo/by-id :applications app-id))
                    => verdicts)))))))

      (let [foo-id       "00-1234-FOO"
            foo-xml      (conv-test/write-test-xml test-dir "foo-test.xml" :backend-id foo-id)
            bar-id       "11-5678-BAR"
            bar-xml      (conv-test/write-test-xml test-dir "bar-test.xml" :backend-id bar-id)
            vakuus-xml   (conv-test/write-test-xml test-dir "vakuus-test.xml" :backend-id "00-0022-77-VAK" )
            lp-id-xml    (conv-test/write-test-xml test-dir "lp-id.xml" :application-id "LP-123")
            blank-id-xml (conv-test/write-test-xml test-dir "blank-id.xml" :backend-id "")
            cfg          {:organization-id "092-R"
                          :permit-type     "R"
                          :municipality    "092"}]
        (facts "Make conversion target"
          (fact "Vakuustieto backend-id not supported"
            (conv/make-conversion-target cfg {:filename vakuus-xml}) => nil)
          (fact "File is mandatory"
            (conv/make-conversion-target cfg {:id test-backend-id}) => nil)
          (fact "XML cannot have application id"
            (conv/make-conversion-target cfg {:filename lp-id-xml}) => nil)
          (fact "Existing conversion, no overwrite"
            (conv/make-conversion-target cfg {:filename conv-test/testfile}) => nil)
          (fact "Existing conversion, overwrite"
            (fact "Old application exists"
              (let [{app-id :id} (mongo/select-one :applications
                                                   {:verdicts.kuntalupatunnus test-backend-id}
                                                   [:id])]
                app-id => truthy

                (let [t (conv/make-conversion-target (assoc cfg :overwrite? true)
                                                     {:filename conv-test/testfile})]
                  t => (just {:filename       conv-test/testfile
                              :id             test-backend-id
                              :conversion-doc (contains {:id           truthy
                                                         :backend-id   test-backend-id
                                                         :LP-id        app-id
                                                         :converted    false
                                                         :organization "092-R"})})
                  (fact "Application has been removed"
                    (mongo/by-id :applications app-id) => nil)
                  (fact "Existing conversion, no application, no overwrite"
                    (conv/make-conversion-target cfg {:filename conv-test/testfile})
                    => t)))))
          (fact "Backend id already in use"
            (mongo/insert :applications {:id           "LP-FOO-BAR"
                                         :organization "092-R"
                                         :verdicts     [{:kuntalupatunnus foo-id}]})
            (conv/make-conversion-target cfg {:filename foo-xml}) => nil)
          (fact "No backend id"
            (conv/make-conversion-target cfg {:filename blank-id-xml}) => nil)
          (fact "New conversion"
            (let [t (conv/make-conversion-target cfg {:filename bar-xml})]
              t => (just {:id             bar-id
                          :filename       bar-xml
                          :conversion-doc (contains {:backend-id bar-id
                                                     :converted  false
                                                     :linked     false
                                                     :LP-id      truthy})})
              (mongo/by-id :conversion (-> t :conversion-doc :id)) => truthy
              (mongo/by-id :applications (-> t :conversion-doc :LP-id)) => nil)))))

    (fact "Default location"
      (conv-k/default-location {} "123-foo" "092-R" :fallback)
      => vantaa-fallback-location-info
      (conv-k/default-location {} "123-foo" "092-R" :override)
      => nil
      (conv-k/default-location {} "123-foo" "505-R" :fallback)
      => nil
      (conv-k/default-location {:location-overrides {"123-foo" {:x 1 :y 2}}}
                               "123-foo" "505-R" :override)
      => {:x 1 :y 2}
      (conv-k/default-location {:location-overrides {"123-foo" {:x 1 :y 2}}
                                :location-fallback  {:x 3 :y 4}}
                               "123-foo" "505-R" :fallback)
      => {:x 3 :y 4})))
