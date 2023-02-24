(ns lupapalvelu.sftp.core-test
  (:require [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.sftp.core :as sftp]
            [midje.sweet :refer :all]
            [monger.operators :refer [$set $push $unset]]
            [sade.util :as util]))

(let [projection [:sftpType :krysp :scope :invoicing-config]]
  (facts "get-organization-configuration"
    (fact "Empty organization"
      (sftp/get-organization-configuration "123-R")
      => {:sftpType    "legacy"
          :users       []
          :permitTypes []}
      (provided
        (lupapalvelu.mongo/by-id :organizations "123-R" projection) => {}))
    (fact "753-R from minimal"
      (sftp/get-organization-configuration "753-R")
      => (just {:sftpType    "legacy"
                :users       (just {:type        "backing-system"
                                    :username    "dev_sipoo"
                                    :permitTypes ["R"]}
                                   {:type        "backing-system"
                                    :username    "dev_poik_sipoo"
                                    :permitTypes ["P"]}
                                   (just {:type        "backing-system"
                                          :username    "dev_ymp_sipoo"
                                          :permitTypes (just "KT" "MM" "MAL"
                                                             "VVVL" "YI" "YL"
                                                             :in-any-order)})
                                   :in-any-order)
                :permitTypes (just "R" "P" "KT" "MM" "MAL"
                                   "VVVL" "YI" "YL" "YM"
                                   :in-any-order)})
      (provided
        (lupapalvelu.mongo/by-id :organizations "753-R" projection)
        => (select-keys (util/find-by-id "753-R" minimal/organizations)
                        projection)))
    (fact "297-R from minimal"
      (sftp/get-organization-configuration "297-R")
      => (just {:sftpType    "legacy"
                :users       (just {:type        "case-management"
                                    :username    "dev_ah_kuopio"
                                    :permitTypes ["P"]}
                                   (just {:type        "backing-system"
                                          :username    "dev_kuopio"
                                          :permitTypes (just "R" "P"
                                                             :in-any-order)})
                                   :in-any-order)
                :permitTypes (just "R" "P" :in-any-order)})
      (provided
        (lupapalvelu.mongo/by-id :organizations "297-R" projection)
        => (select-keys (util/find-by-id "297-R" minimal/organizations)
                        projection)))
    (fact "Invoicing organization"
      (sftp/get-organization-configuration "123-R")
      => (just {:sftpType    "gcs"
                :users       (just {:type        "case-management"
                                    :username    "smallcaps"
                                    :permitTypes ["YA"]}
                                   {:type        "backing-system"
                                    :username    "system"
                                    :permitTypes ["R"]}
                                   {:type        "invoicing"
                                    :username    "money"}
                                   :in-any-order)
                :permitTypes (just "R" "YA" "A" :in-any-order)})
      (provided
        (lupapalvelu.mongo/by-id :organizations "123-R" projection)
        => {:sftpType         "gcs"
            :krysp            {:R {:ftpUser "system"}}
            :scope            [{:permitType   "R"
                                :municipality "1"}
                               {:permitType   "R"
                                :municipality "2"}
                               {:permitType     "YA"
                                :municipality   "1"
                                :caseManagement {:ftpUser "smallcaps"}}
                               {:permitType   "A"
                                :municipality "1"}]
            :invoicing-config {:local-sftp-user "money"}}))))

(against-background
  [(sade.env/value :outgoing-directory) => "out"]
  (facts "update-organization-configuration"
   (fact "One scope, backing system sftp"
     (sftp/update-organization-configuration "123-R"
                                             {:sftpType "legacy"
                                              :users    [{:type        "backing-system"
                                                          :username    "hello"
                                                          :permitTypes ["R"]}]})
     => nil
     (provided
       (lupapalvelu.mongo/by-id :organizations "123-R" [:scope])
       => {:scope [{:permitType "R"}]}
       (babashka.fs/directory? "out/hello/rakennus") => true
       (lupapalvelu.mongo/update-by-id :organizations "123-R"
                                       {$set   {:sftpType        "legacy"
                                                :krysp.R.ftpUser "hello"
                                                :scope           [{:permitType "R"}]}
                                        $unset {:invoicing-config.local-sftp-user true}})
       => nil))
   (fact "One scope, case management sftp"
     (sftp/update-organization-configuration "123-R"
                                             {:sftpType "legacy"
                                              :users    [{:type        "case-management"
                                                          :username    "hello"
                                                          :permitTypes ["R"]}]})
     => nil
     (provided
       (lupapalvelu.mongo/by-id :organizations "123-R" [:scope])
       => {:scope [{:permitType "R"}]}
       (babashka.fs/directory? "out/hello/asianhallinta/from_lupapiste") => true
       (lupapalvelu.mongo/update-by-id :organizations "123-R"
                                       {$set   {:sftpType "legacy"
                                                :scope    [{:permitType     "R"
                                                            :caseManagement {:enabled true
                                                                             :version "1.1"
                                                                             :ftpUser "hello"}}]}
                                        $unset {:krysp.R.ftpUser                  true
                                                :invoicing-config.local-sftp-user true}})
       => nil))
   (fact "One scope, invoicing only"
     (sftp/update-organization-configuration "123-R"
                                             {:sftpType "legacy"
                                              :users    [{:type     "invoicing"
                                                          :username "hello"}]})
     => nil
     (provided
       (lupapalvelu.mongo/by-id :organizations "123-R" [:scope])
       => {:scope [{:permitType "R"}]}
       (babashka.fs/directory? "out/hello/laskutus") => true
       (lupapalvelu.mongo/update-by-id :organizations "123-R"
                                       {$set   {:sftpType                         "legacy"
                                                :scope                            [{:permitType "R"}]
                                                :invoicing-config.local-sftp-user "hello"}
                                        $unset {:krysp.R.ftpUser true}})
       => nil))
   (fact "Many scopes, every feature"
     (sftp/update-organization-configuration "123-R"
                                             {:sftpType "legacy"
                                              :users    [{:type        "backing-system"
                                                          :username    "hello"
                                                          ;; KT is not supported by the organization
                                                          :permitTypes ["R" "MM" "KT"]}
                                                         {:type        "case-management"
                                                          :username    "world"
                                                          :permitTypes ["P" "YA"]}
                                                         {:type     "invoicing"
                                                          :username "money"}]})
     => nil
     (provided
       (lupapalvelu.mongo/by-id :organizations "123-R" [:scope])
       => {:sftpType "gcs"
           :krysp    {:R {:ftpUser "system"}}
           :scope    [{:permitType   "R"
                       :municipality "1"}
                      {:permitType   "R"
                       :municipality "2"}
                      {:permitType     "YA"
                       :municipality   "1"
                       :caseManagement {:ftpUser "smallcaps" :version "2.0"}}
                      {:permitType   "YA"
                       :municipality "2"}
                      {:permitType     "A"
                       :municipality   "1"
                       :caseManagement {:enabled true :version "1" :ftpUser "allu"}}
                      {:permitType     "P"
                       :caseManagement {:enabled true :version "2" :ftpUser "ping"}}
                      {:permitType     "MM"
                       :caseManagement {:enabled false :version "3"}}]}
       (babashka.fs/directory? "out/hello/rakennus") => true
       (babashka.fs/directory? "out/hello/maankaytonmuutos") => true
       (babashka.fs/directory? "out/world/asianhallinta/from_lupapiste") => true
       (babashka.fs/directory? "out/money/laskutus") => true
       (lupapalvelu.mongo/update-by-id
         :organizations "123-R"
         {$set   {:sftpType                         "legacy"
                  :scope                            [{:permitType   "R"
                                                      :municipality "1"}
                                                     {:permitType   "R"
                                                      :municipality "2"}
                                                     {:permitType     "YA"
                                                      :municipality   "1"
                                                      :caseManagement {:enabled true :version "2.0" :ftpUser "world"}}
                                                     {:permitType     "YA"
                                                      :municipality   "2"
                                                      :caseManagement {:enabled true :version "1.1" :ftpUser "world"}}
                                                     {:permitType     "A"
                                                      :municipality   "1"
                                                      :caseManagement {:enabled false :version "1"}}
                                                     {:permitType     "P"
                                                      :caseManagement {:enabled true :version "2" :ftpUser "world"}}
                                                     {:permitType     "MM"
                                                      :caseManagement {:enabled false :version "3"}}]
                  :krysp.R.ftpUser                  "hello"
                  :krysp.MM.ftpUser                 "hello"
                  :invoicing-config.local-sftp-user "money"}
          $unset {:krysp.P.ftpUser  true
                  :krysp.YA.ftpUser true
                  :krysp.A.ftpUser  true}})
       => nil))
   (fact "Legacy: no such directory"
     (sftp/update-organization-configuration "123-R"
                                             {:sftpType "legacy"
                                              :users    [{:type     "invoicing"
                                                          :username "hello"}
                                                         {:type        "backing-system"
                                                          :username    "hello"
                                                          :permitTypes ["R"]}
                                                         {:type        "case-management"
                                                          :username    "hello"
                                                          :permitTypes ["P"]}]})
     => (just {:ok          false
               :text        "error.sftp.directories-not-found"
               :directories (just "out/hello/laskutus" "out/hello/rakennus"
                                  "out/hello/asianhallinta/from_lupapiste"
                                  :in-any-order)})
     (provided
       (lupapalvelu.mongo/by-id :organizations "123-R" [:scope])
       => {:scope [{:permitType "R"}
                   {:permitType "P"}]}
       (babashka.fs/directory? anything) => false
       (lupapalvelu.mongo/update-by-id :organizations "123-R" anything)
       => nil :times 0))

   (fact "GCS: make directories"
     (sftp/update-organization-configuration "123-R"
                                             {:sftpType "gcs"
                                              :users    [{:type        "backing-system"
                                                          :username    "hello"
                                                          :permitTypes ["R"]}]})
     => nil
     (provided
       (lupapalvelu.mongo/by-id :organizations "123-R" [:scope])
       => {:scope [{:permitType "R"}
                   {:permitType "P"}]}
       (babashka.fs/directory? anything) => false :times 0
       (lupapalvelu.sftp.context/gcs-make-dirs "hello/rakennus" :subdirs? true :cm? false) => nil :times 1
       (lupapalvelu.mongo/update-by-id
         :organizations "123-R"
         {$set   {:sftpType        "gcs"
                  :scope           [{:permitType "R"}
                                    {:permitType "P"}]
                  :krysp.R.ftpUser "hello"}
          $unset {:krysp.P.ftpUser                  true
                  :invoicing-config.local-sftp-user true}})
       => nil))))

(defn cfg-check [flag & users]
  (fact {:midje/description (str (if flag "Good" "Bad")
                                 " configuration: " users)}
    (sftp/valid-configuration? {:sftpType "gcs" :users users}) => flag))

(def bad-cfg (partial cfg-check false))
(def good-cfg (partial cfg-check true))

(defn user-cfg [type username & permit-types]
  {:type type :username username :permitTypes permit-types})

(def bs-u (partial user-cfg "backing-system"))
(def cm-u (partial user-cfg "case-management"))

(facts "valid-configuration?"
  (facts "Ambiguous configurations"
    (bad-cfg {:type "invoicing" :username "money"}
             {:type "invoicing" :username "dosh"})
    (bad-cfg (bs-u "a" "R") (cm-u "a" "R"))
    (bad-cfg (bs-u "a" "R" "P" "YA") (bs-u "b" "KT" "P"))
    (bad-cfg (cm-u "a" "R" "P" "YA") (cm-u "b" "KT" "P")))
  (facts "Harmless (non-ambiguous) mistakes"
    (good-cfg {:type "invoicing" :username "money"}
              {:type "invoicing" :username "money"})
    (good-cfg (bs-u "a" "R" "R" "R"))
    (good-cfg (bs-u "a" "R" "P") (bs-u "a" "P" "YA") (bs-u "a" "YA" "YM"))
    (good-cfg (cm-u "a" "R" "R" "R"))
    (good-cfg (cm-u "a" "R" "P") (cm-u "a" "P" "YA") (cm-u "a" "YA" "YM")))
  (fact "Proper configuration"
    (good-cfg {:type "invoicing" :username "money"}
              (bs-u "a" "R" "P")
              (cm-u "a" "YA")
              (bs-u "a" "KT" "MM")
              (cm-u "a" "A"))))
