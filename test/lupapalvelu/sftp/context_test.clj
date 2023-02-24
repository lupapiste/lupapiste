(ns lupapalvelu.sftp.context-test
  (:require [lupapalvelu.sftp.context :as sftp-ctx]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.env :as env]))

(testable-privates lupapalvelu.sftp.context
                   resolve-directory-path ftp-user
                   context-properties safe-filepath? make-safe!
                   safe-subdirs-join
                   invoicing-ftp-user)

(facts "gcs-sftp?"
  (sftp-ctx/gcs-sftp? {}) => false
  (sftp-ctx/gcs-sftp? nil) => false
  (sftp-ctx/gcs-sftp? (delay {})) => false
  (sftp-ctx/gcs-sftp? {:sftpType "legacy"}) => false
  (sftp-ctx/gcs-sftp? (delay {:sftpType "legacy"})) => false
  (sftp-ctx/gcs-sftp? {:sftpType "gcs"}) => true
  (sftp-ctx/gcs-sftp? (delay {:sftpType "gcs"})) => true
  (sftp-ctx/gcs-sftp? {:sftpType "bad"}) => (throws))

(facts "legacy-sftp?"
  (sftp-ctx/legacy-sftp? {}) => true
  (sftp-ctx/legacy-sftp? nil) => true
  (sftp-ctx/legacy-sftp? (delay {})) => true
  (sftp-ctx/legacy-sftp? {:sftpType "legacy"}) => true
  (sftp-ctx/legacy-sftp? (delay {:sftpType "legacy"})) => true
  (sftp-ctx/legacy-sftp? {:sftpType "gcs"}) => false
  (sftp-ctx/legacy-sftp? (delay {:sftpType "gcs"})) => false
  (sftp-ctx/legacy-sftp? {:sftpType "bad"}) => (throws))

(against-background
  [(env/value :outgoing-directory) => "out"]
  (facts "resolve-directory-path"
    (resolve-directory-path {} "foo" "bar") => "out/foo/bar"
    (resolve-directory-path {:sftpType "gcs"} "foo" "bar") => "foo/bar"
    (resolve-directory-path (delay {:sftpType "gcs"}) "foo" "bar") => "foo/bar")

  (facts "permit-type-directory"
    (sftp-ctx/permit-type-directory {} "hello" "KT") => "out/hello/kiinteistotoimitus"
    (sftp-ctx/permit-type-directory {:sftpType "legacy"} "hello" "KT")
    => "out/hello/kiinteistotoimitus"
    (sftp-ctx/permit-type-directory {:sftpType "gcs"} "hello" "KT")
    => "hello/kiinteistotoimitus"
    (sftp-ctx/permit-type-directory {:sftpType "bad"} "hello" "KT")
    => (throws)
    (sftp-ctx/permit-type-directory {} " hello " "KT")
    => (throws)
    (sftp-ctx/permit-type-directory {} "hello" "BAD")
    => (throws))
  (facts "case-management-directory"
    (sftp-ctx/case-management-directory {} "world" :out)
    => "out/world/asianhallinta/from_lupapiste"
    (sftp-ctx/case-management-directory {:sftpType "legacy"} "world" :out)
    => "out/world/asianhallinta/from_lupapiste"
    (sftp-ctx/case-management-directory {:sftpType "legacy"} "world" :in)
    => "out/world/asianhallinta/to_lupapiste"
    (sftp-ctx/case-management-directory {:sftpType "gcs"} "world" :out)
    => "world/asianhallinta/from_lupapiste"
    (sftp-ctx/case-management-directory {:sftpType "gcs"} "world" :in)
    => "world/asianhallinta/to_lupapiste"
    (sftp-ctx/case-management-directory {:sftpType "bad"} "world" :in)
    => (throws)
    (sftp-ctx/case-management-directory {} "world/away" :in)
    => (throws)
    (sftp-ctx/case-management-directory {} "world" nil)
    => (throws)
    (sftp-ctx/case-management-directory {:sftpType "gcs"} "world" :bad)
    => (throws))
  (facts "invoicing-directory"
    (sftp-ctx/invoicing-directory nil "money") => "out/money/laskutus"
    (sftp-ctx/invoicing-directory {} "money") => "out/money/laskutus"
    (sftp-ctx/invoicing-directory {:sftpType "legacy"} "money") => "out/money/laskutus"
    (sftp-ctx/invoicing-directory {:sftpType "gcs"} "money") => "money/laskutus"
    (sftp-ctx/invoicing-directory {:sftpType "bad"} "money") => (throws)
    (sftp-ctx/invoicing-directory {:sftpType "gcs"} "money.maker") => (throws)))

(facts "ftp-user"
  (ftp-user {}) => nil
  (ftp-user {:ftpUser "howdy"}) => nil
  (ftp-user {:ftpUser "howdy"
             :version ""}) => nil
  (ftp-user {:ftpUser "howdy"
             :version "1.0"}) => "howdy"
  (ftp-user {:ftpUser "howdy"
             :enabled false
             :version "1.0"}) => "howdy"
  (ftp-user {:ftpUser " howdy "
             :enabled true
             :version "1.0"}) => "howdy"
  (ftp-user {:ftpUser ""
             :enabled true
             :version "1.0"}) => nil
  (ftp-user {:ftpUser "howdy"
             :enabled true
             :version ""}) => nil)

(against-background
  [(env/value :outgoing-directory) => "out"]
  (facts "context-properties"
    (context-properties {} "R") => nil
    (context-properties {:krysp {:R {:ftpUser "riekko"
                                     :version "1.2.3"}}}
                        "P") => nil
    (context-properties {:krysp {:R {:ftpUser "riekko"
                                     :version "1.2.3"}}}
                        "R")
    => {:user "riekko" :directory "out/riekko/rakennus" :cm? false}
    (context-properties {:krysp {:R {:ftpUser "riekko"
                                     :version "1.2.3"}
                                 :P {:ftpUser "pyy"
                                     :version "4.5.6"}}}
                        "P")
    => {:user "pyy" :directory "out/pyy/poikkeusasiat" :cm? false}
    (context-properties {:krysp {:R  {:ftpUser "riekko"
                                      :version "1.2.3"}
                                 :P  {:ftpUser "pyy"
                                      :version "4.5.6"}
                                 :KT {:ftpUser "kottarainen"
                                      :version "7.8.9"}}}
                        "KT")
    => {:cm? false :directory "out/kottarainen/kiinteistotoimitus" :user "kottarainen"}
    (context-properties {:sftpType "gcs"
                         :krysp    {:R  {:ftpUser "riekko"
                                         :version "1.2.3"}
                                    :P  {:ftpUser "pyy"
                                         :version "4.5.6"}
                                    :KT {:ftpUser "kottarainen"
                                         :version "7.8.9"}}}
                        "R")
    => {:user "riekko" :directory "riekko/rakennus" :cm? false}
    (context-properties {:sftpType "legacy"
                         :krysp    {:R  {:ftpUser "riekko"
                                         :version "1.2.3"}
                                    :P  {:ftpUser "pyy"
                                         :version "4.5.6"}
                                    :KT {:ftpUser "kottarainen"
                                         :version "7.8.9"}}
                         :scope    [{:permitType     "R"
                                     :caseManagement {:enabled false
                                                      :ftpUser "ah_riekko"
                                                      :version "3.2.1"}}]}
                        "R")
    => {:user "riekko" :directory "out/riekko/rakennus" :cm? false}
    (context-properties {:sftpType "legacy"
                         :krysp    {:R  {:ftpUser "riekko"
                                         :version "1.2.3"}
                                    :P  {:ftpUser "pyy"
                                         :version "4.5.6"}
                                    :KT {:ftpUser "kottarainen"
                                         :version "7.8.9"}}
                         :scope    [{:permitType     "R"
                                     :caseManagement {:enabled true
                                                      :ftpUser "ah_riekko"
                                                      :version "3.2.1"}}]}
                        "R")
    => {:user      "ah_riekko"
        :directory "out/ah_riekko/asianhallinta/from_lupapiste"
        :cm?       true}
    (context-properties {:sftpType "gcs"
                         :krysp    {:R  {:ftpUser "riekko"
                                         :version "1.2.3"}
                                    :P  {:ftpUser "pyy"
                                         :version "4.5.6"}
                                    :KT {:ftpUser "kottarainen"
                                         :version "7.8.9"}}
                         :scope    [{:permitType     "R"
                                     :caseManagement {:enabled true
                                                      :ftpUser "ah_riekko"
                                                      :version "3.2.1"}}
                                    {:permitType     "YA"
                                     :caseManagement {:enabled true
                                                      :ftpUser ""
                                                      :version "4.3.2"}}]}
                        "YA")
    => nil
    (context-properties {:sftpType "gcs"
                         :krysp    {:R  {:ftpUser "riekko"
                                         :version "1.2.3"}
                                    :P  {:ftpUser "pyy"
                                         :version "4.5.6"}
                                    :KT {:ftpUser "kottarainen"
                                         :version "7.8.9"}}
                         :scope    [{:permitType     "R"
                                     :caseManagement {:enabled true
                                                      :ftpUser "ah_riekko"
                                                      :version "3.2.1"}}
                                    {:permitType     "YA"
                                     :caseManagement {:enabled true
                                                      :ftpUser "ah_pollo"
                                                      :version "4.3.2"}}]}
                        "YA")
    => {:user      "ah_pollo"
        :directory "ah_pollo/asianhallinta/from_lupapiste"
        :cm?       true}))

(facts "safe-filepath?"
  (safe-filepath? "ABCDEFGHIJKLMNOPQRSTUVWXYZÅÄÖ_-.1234567890 abcdefghijklmnopqrstuvwxyzåäö")
  => true
  (safe-filepath? "") => false
  (safe-filepath? " ") => false
  (safe-filepath? nil) => false
  (safe-filepath? "/abs/path/bad") => false
  (safe-filepath? "rel/path/good") => true
  (safe-filepath? " rel/path/good ") => true
  (safe-filepath? "rel/../bad") => false)

(facts "make-safe?"
  (make-safe! nil) => (throws Exception "Unsafe filename: ")
  (make-safe! " ") => (throws Exception "Unsafe filename: ")
  (make-safe! "/") => (throws Exception "Unsafe filename: /")
  (make-safe! "/hello" "world")
  => (throws Exception "Unsafe filename: /hello/world")
  (make-safe! "rel" "path" "good") => "rel/path/good"
  (make-safe! ["rel" ["path"] "good"]) => "rel/path/good"
  (make-safe! "bad/.." "path") => (throws Exception "Unsafe filename: bad/../path")
  (make-safe! "bad" ".." "path") => (throws Exception "Unsafe filename: bad/../path")
  (make-safe! "bad" [["/../"] "path"]) => (throws Exception "Unsafe filename: bad/../path"))

(facts "safe-subdirs-join"
  (safe-subdirs-join "dir" []) => "dir"
  (safe-subdirs-join "dir" ["  " nil ""]) => "dir"
  (safe-subdirs-join "dir" ["/bad"]) => (throws)
  (safe-subdirs-join "dir" ["good" "path"]) => "dir/good/path"
  (safe-subdirs-join "dir" ["good" " " nil "path"]) => "dir/good/path")

(facts "invoicing-ftp-user"
  (invoicing-ftp-user {}) => nil
  (invoicing-ftp-user nil) => nil
  (invoicing-ftp-user {:invoicing-config {:local-sftp?     false
                                          :local-sftp-user "  money  "}}) => nil
  (invoicing-ftp-user {:invoicing-config {:local-sftp?     true
                                          :local-sftp-user "  money  "}}) => "money"
  (invoicing-ftp-user {:invoicing-config {:local-sftp?     true
                                          :local-sftp-user "    "}}) => nil
  (invoicing-ftp-user {:invoicing-config {:local-sftp? true}}) => nil
  (invoicing-ftp-user {:invoicing-config {:local-sftp? true}
                       :scope            [{:permitType        "R"
                                           :invoicing-enabled true}]
                       :krysp            {:R {:ftpUser " r-money "}}}) => "r-money"
  (invoicing-ftp-user {:invoicing-config {:local-sftp? true}
                       :scope            [{:permitType        "R"
                                           :invoicing-enabled true}]
                       :krysp            {:R {:ftpUser "  "}}}) => nil
  (invoicing-ftp-user {:invoicing-config {:local-sftp? true}
                       :scope            [{:permitType        "R"
                                           :invoicing-enabled false}]
                       :krysp            {:R {:ftpUser " r-money "}}}) => nil
  (invoicing-ftp-user {:invoicing-config {:local-sftp? true}
                       :scope            [{:permitType "R"}
                                          {:permitType        "P"
                                           :invoicing-enabled true
                                           :caseManagement    {:enabled true
                                                               :ftpUser " cm-money "}}
                                          {:permitType        "R"
                                           :invoicing-enabled false}]
                       :krysp            {:R {:ftpUser " r-money "}
                                          :P {:ftpUser " p-money "}}}) => "cm-money"
  (invoicing-ftp-user {:invoicing-config {:local-sftp? true}
                       :scope            [{:permitType "R"}
                                          {:permitType        "P"
                                           :invoicing-enabled true
                                           :caseManagement    {:enabled false
                                                               :ftpUser " cm-money "}}
                                          {:permitType        "R"
                                           :invoicing-enabled false}]
                       :krysp            {:R {:ftpUser " r-money "}
                                          :P {:ftpUser " p-money "}}}) => "p-money")
