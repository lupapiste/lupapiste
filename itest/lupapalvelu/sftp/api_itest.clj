(ns lupapalvelu.sftp.api_itest
  (:require [clj-uuid :as uuid]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer [err]]
            [lupapalvelu.sftp-itest-util :refer [gcs-remove-test-folder
                                                 fs-remove-test-dirs]]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.xml.gcs-writer :as gcsw]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.env :as env]
            [sade.strings :as ss]))

(mount/start #'mongo/connection)
(mongo/with-db "sftp_api_itest"
  (fixture/apply-fixture "minimal")
  (let [org-id           "753-R"
        outdir           (env/value :outgoing-directory)
        [alice bob carol david] (map (fn [_] (str "sftp_" (uuid/v1) "_test")) (range 4))]
    (with-local-actions
     (fact "Initial configuration"
       (:configuration (query admin :sftp-organization-configuration
                              :organizationId org-id))
       => (just {:sftpType    "legacy"
                 :users       (just {:type        "backing-system" :username "dev_sipoo"
                                     :permitTypes ["R"]}
                                    {:type        "backing-system" :username "dev_poik_sipoo"
                                     :permitTypes ["P"]}
                                    (just {:type        "backing-system" :username "dev_ymp_sipoo"
                                           :permitTypes (just "YI" "YL" "MAL" "VVVL" "KT" "MM"
                                                              :in-any-order)})
                                    :in-any-order)
                 :permitTypes (just "R" "P" "YM" "YI" "YL" "MAL" "VVVL" "KT" "MM"
                                    :in-any-order)}))
     (fact "Nukem"
       (command admin :update-sftp-organization-configuration
                :organizationId org-id
                :sftpType "legacy"
                :users []) => ok?
       (:configuration (query admin :sftp-organization-configuration
                              :organizationId org-id))
       => (just {:sftpType    "legacy"
                 :users       []
                 :permitTypes (just "R" "P" "YM" "YI" "YL" "MAL" "VVVL" "KT" "MM"
                                    :in-any-order)})
       (let [{:keys [scope invoicing-config
                     krysp]} (mongo/by-id :organizations org-id
                                          [:krysp :invoicing-config :scope])]
         (fact "No krysp sftp users"
           (->> krysp
                vals
                (not-any? :ftpUser)) => true)
         (fact "No scope sftp users"
           (not-any? #(let [{:keys [enabled ftpUser]} (:caseManagement %)]
                        (or enabled ftpUser))
                     scope) => true)
         (fact "No invoicing sftp user"
           (:local-sftp-user invoicing-config) => nil)))
     (fact "No such organization"
       (command admin :update-sftp-organization-configuration
                :organizationId "BAD-ID"
                :sftpType "legacy"
                :users [])
       => (err :error.organization-not-found)
       (query admin :sftp-organization-configuration
              :organizationId "BAD-ID")
       => (err :error.organization-not-found))
     (facts "Bad updates"
       (command admin :update-sftp-organization-configuration
                :organizationId org-id
                :sftpType "BAD"
                :users [])
       => (err :error.illegal-value:schema-validation)
       (command admin :update-sftp-organization-configuration
                :organizationId org-id
                :sftpType "legacy"
                :users [{:type "backing-system" :username "wordle" :permitTypes ["R"]}
                        {:type "case-management" :username "wordle" :permitTypes ["R"]}])
       => (err :error.invalid-configuration))
      (against-background
        [(after :contents (fs-remove-test-dirs alice bob carol))]
        (let [target-dirs (map #(ss/join-file-path outdir (first %) (second %))
                               [[alice "rakennus"]
                                [bob "asianhallinta/from_lupapiste"]
                                [alice "maankaytonmuutos"]
                                [carol "laskutus"]])]
          (facts "Good updates: but directories are missing"
            (command admin :update-sftp-organization-configuration
                     :organizationId org-id
                     :sftpType "legacy"
                     :users [{:type "backing-system" :username alice :permitTypes ["R"]}
                             {:type "case-management" :username bob :permitTypes ["P"]}
                             {:type "backing-system" :username alice :permitTypes ["MM"]}
                             {:type "case-management" :username bob :permitTypes ["KT"]}
                             {:type "invoicing" :username carol}
                             {:type "invoicing" :username carol}])
            => (just {:ok          false :text "error.sftp.directories-not-found"
                      :directories (just target-dirs :in-any-order)}))
          (fact "Create directories"
            (doseq [dir target-dirs]
              (sftp-ctx/fs-make-dirs dir
                                     :cm? (ss/ends-with dir "from_lupapiste")
                                     :subdirs? (not (ss/ends-with dir "laskutus")))))
          (facts "Good updates"
            (command admin :update-sftp-organization-configuration
                     :organizationId org-id
                     :sftpType "legacy"
                     :users [{:type "backing-system" :username alice :permitTypes ["R"]}
                             {:type "case-management" :username bob :permitTypes ["P"]}
                             {:type "backing-system" :username alice :permitTypes ["MM"]}
                             {:type "case-management" :username bob :permitTypes ["KT"]}
                             {:type "invoicing" :username carol}
                             {:type "invoicing" :username carol}])
            => ok?
            (:configuration (query admin :sftp-organization-configuration
                                   :organizationId org-id))
            => (just {:sftpType    "legacy"
                      :users       (just (just {:type        "backing-system" :username alice
                                                :permitTypes (just "R" "MM" :in-any-order)})
                                         (just {:type        "case-management" :username bob
                                                :permitTypes (just "P" "KT" :in-any-order)})
                                         {:type "invoicing" :username carol}
                                         :in-any-order)
                      :permitTypes (just "R" "P" "YM" "YI" "YL" "MAL" "VVVL" "KT" "MM"
                                         :in-any-order)}))
          (facts "Invoicing config vs. local-sftp-user"
            (fact "Sftp user is part of the configuration"
              (-> (query admin :invoicing-config :organizationId org-id)
                  :invoicing-config :local-sftp-user) => carol)
            (fact "Invoicing sftp user cannot be changed via invoicing configuration"
              (command admin :update-invoicing-config
                       :org-id org-id
                       :invoicing-config {:invoice-file-prefix "hello"
                                          :local-sftp-user     "world"}) => ok?
              (:invoicing-config (query admin :invoicing-config :organizationId org-id))
              => (contains {:invoice-file-prefix "hello"
                            :local-sftp-user     carol})))))
      (when (env/feature? :gcs)
        (against-background
          [(after :contents (gcs-remove-test-folder david 15))]
          (letfn [(gcs-dir? [& parts]
                   (gcsw/file-exists? (str (ss/join-file-path parts) "/")))]
           (facts "GCS sftp configuration creates folders"
             (fact "Directory does not exist"
               (gcs-dir? david) => false)
             (fact "Update sftp configuration"
               (command admin :update-sftp-organization-configuration
                        :organizationId org-id
                        :sftpType "gcs"
                        :users [{:type "backing-system" :username david :permitTypes ["R" "YA" "YL"]}
                                {:type "case-management" :username david :permitTypes ["P" "A"]}
                                {:type "invoicing" :username david}])
               => ok?)
             (fact "Directories are created"
               (gcs-dir? david) => true
               (gcs-dir? david "asianhallinta") => true
               (doseq [dir ["rakennus" "ymparisto" "asianhallinta/from_lupapiste"
                            "asianhallinta/to_lupapiste"]]
                 (gcs-dir? david dir) => true
                 (gcs-dir? david dir "error") => true
                 (gcs-dir? david dir "archive") => true)
               (gcs-dir? david "laskutus") => true)
             (fact "Non-supported scopes are ignored"
               (gcs-dir? david "yleiset_alueet") => false)
             (fact "Configuration is updated"
               (:configuration (query admin :sftp-organization-configuration
                                      :organizationId org-id))
               => (just {:sftpType    "gcs"
                         :users       (just (just {:type        "backing-system" :username david
                                                   :permitTypes (just "R" "YL" :in-any-order)})
                                            (just {:type        "case-management" :username david
                                                   :permitTypes ["P"]})
                                            {:type "invoicing" :username david}
                                            :in-any-order)
                         :permitTypes (just "R" "P" "YM" "YI" "YL" "MAL" "VVVL" "KT" "MM"
                                            :in-any-order)})))))))))
