(ns lupapalvelu.organization-test
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :refer :all]
            [lupapalvelu.roles :as roles]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]))

(facts
 (let [organization {:operations-attachments {:kikka [["type-group-1" "type-id-123"]]}
                     :krysp {:good1 {:url "http://example.com" :ftpUser "user" :version "1.0.0"}
                             :bad1 {:ftpUser "user" :version ""}
                             :bad2 {:version "1.0.0"}
                             :bad3 {}
                             :bad4 {:ftpUser "user" :version "1.0.0"}
                             :bad5 {:ftpUser "  " :url " " :version "1.0.0"}
                             :http-good1 {:version "1.0.0" :http {:url "foo"}}
                             :http-good2 {:version "1.0.0" :http {:url "foo" :nonsense "ok"}}
                             :http-good3 {:version "1.0.0" :ftpUser "" :http {:url "foo" :nonsense "ok"}}
                             :http-bad1 {:http {:url "foo"}}
                             :http-bad2 {:version "1.0.0" :http {:url ""}}
                             :http-bad3 {:version "1.0.0" :ftpUser "" :http {:url "" :nonsense "ok"}}
                             }}
       valid-op     {:name "kikka"}
       invalid-op   {:name "kukka"}]
   (get-organization-attachments-for-operation organization valid-op) => [["type-group-1" "type-id-123"]]
   (get-organization-attachments-for-operation organization invalid-op) => nil
   (fact "KRYSP integration defined either FTP or HTTP"                        ; none of the required can't be blank
     (krysp-write-integration? organization :good1) => true?
     (fact "version can't be blank"
       (krysp-write-integration? organization :bad1) => false?)
     (fact "ftp user required"
       (krysp-write-integration? organization :bad2) => false?)
     (krysp-write-integration? organization :bad3) => false?
     (fact "url required"
       (krysp-write-integration? organization :bad4) => true)
     (krysp-write-integration? organization :bad5) => false
     (krysp-write-integration? organization :http-good1) => true?
     (krysp-write-integration? organization :http-good2) => true?
     (fact "blank ftpUser ok if http defined"
       (krysp-write-integration? organization :http-good2) => true?)
     (fact "version needed"
       (krysp-write-integration? organization :http-bad1) => false?)
     (fact "http url needed"
       (krysp-write-integration? organization :http-bad2) => false?)
     (fact "ftpUser and http.url blanks"
       (krysp-write-integration? organization :http-bad3) => false?))))

(facts upsert-handler-role!
  (fact "update existing"
    (upsert-handler-role! {:id ..org-id.. :handler-roles [{:id ..role-id..}]} {:id ..role-id.. :name ..name..}) => "done"
    (provided (update-organization ..org-id.. {$set {:handler-roles.0.id ..role-id.. :handler-roles.0.name ..name..}}) => "done"))

  (fact "insert new"
    (upsert-handler-role! {:id ..org-id.. :handler-roles [{:id ..role-id..}]} {:id ..new-role-id.. :name ..name..}) => "done"
    (provided (update-organization ..org-id.. {$set {:handler-roles.1.id ..new-role-id.. :handler-roles.1.name ..name..}}) => "done"))

  (fact "update existing when there is multiple handlers in org"
    (upsert-handler-role! {:id ..org-id.. :handler-roles [{:id ..role-id-0..} {:id ..role-id-1..} {:id ..role-id-2..} {:id ..role-id-3..}]} {:id ..role-id-2.. :name ..name..}) => "done"
    (provided (update-organization ..org-id.. {$set {:handler-roles.2.id ..role-id-2.. :handler-roles.2.name ..name..}}) => "done")))

(let [permit-type :R
      conf        (fn [c] {:krysp {permit-type c}})]

  (facts "resolve-krysp-wfs"
    (against-background
      [(get-credentials anything) => ["pena" "pena"]]
      (fact "url must be present"
        (resolve-krysp-wfs (conf {:url "foo" :version "1"}) permit-type) => (contains {:url "foo" :version "1"})
        (resolve-krysp-wfs (conf {:url nil :version "1"}) permit-type) => nil
        (resolve-krysp-wfs (conf {:version "1"}) permit-type) => nil)))

  (facts "resolve-building-wfs"
    (fact "buildings must be present and have non-blank url"
      (resolve-building-wfs (conf {:url "foo" :version "1"}) permit-type) => nil
      (resolve-building-wfs (conf {:url nil :version "1"}) permit-type) => nil
      (resolve-building-wfs (conf {:buildings {:url "  "}
                                   :version   "1"})
                            permit-type) => nil)
    (fact "No credentials"
      (resolve-building-wfs (conf {:buildings {:url "foo"}
                                   :version   "1"})
                            permit-type) => {:url "foo" :version "1"})
    (fact "Credentials"
      (resolve-building-wfs (conf {:buildings {:url       "foo"
                                               :username  "pena"
                                               :password  "*******"
                                               :crypto-iv "xxx"}
                                   :version   "1"})
                            permit-type)
      => (contains {:url         "foo"
                    :credentials ["pena" "topsecret"]
                    :version     "1"})
      (provided (get-credentials {:username "pena" :password "*******" :crypto-iv "xxx" :url "foo"}) => ["pena" "topsecret"])))

  (fact "get-building-wfs without urls"
    (get-building-wfs ..query.. permit-type) => nil
    (provided
     (mongo/select-one :organizations ..query.. [:krysp]) => (conf {:version "1" :username "foo"})))

  (fact "get-building-wfs with :url"
    (get-building-wfs ..query.. permit-type) => (just {:url "foo" :version "1"})
    (provided
     (mongo/select-one :organizations ..query.. [:krysp]) => (conf {:url "foo" :version "1"})))

  (fact "get-building-wfs with :buildings, no credentials"
    (get-building-wfs ..query.. permit-type) => (just {:url "foo" :version "1"})
    (provided
     (mongo/select-one :organizations ..query.. [:krysp]) => (conf {:buildings {:url "foo"} :version "1"})))

  (fact "get-building-wfs with :buildings, credentials"
    (get-building-wfs ..query.. permit-type) => (just {:url "foo" :version "1" :credentials ["pena" "topsecret"]})
    (provided
     (mongo/select-one :organizations ..query.. [:krysp]) => (conf {:buildings {:url "foo"
                                                                                :username "pena"
                                                                                :password "*"
                                                                                :crypto-iv "x"}
                                                                    :version "1"})
     (get-credentials {:url "foo"
                       :username "pena"
                       :password "*"
                       :crypto-iv "x"}) => ["pena" "topsecret"]))

  (fact "get-building-wfs - :buildings is bigger priority"
    (get-building-wfs ..query.. permit-type) => (just {:url "foo" :version "1"})
    (provided
     (mongo/select-one :organizations ..query.. [:krysp]) => (conf {:buildings {:url "foo"} :version "1" :url "jee"}))))

(facts "bulletin-settings-for-scope"
  (fact "validation"
    (bulletin-settings-for-scope
      {:scope [{:permitType "R"
                :municipality "991"
                :bulletins false}]} "R" nil) => (throws AssertionError)
    (bulletin-settings-for-scope
      {:scope [{:permitType "R"
                :municipality "991"
                :bulletins false}]} nil "991") => (throws AssertionError))
  (fact "no active scope"
    (bulletin-settings-for-scope
      {:scope [{:permitType "R"
                :municipality "991"
                :bulletins false}]} "R" "991") => nil)
  (fact "active R scope"
    (bulletin-settings-for-scope
      {:scope [{:permitType "R"
                :municipality "991"
                :bulletins {:foo :bar}}
               {:permitType "P"
                :municipality "991"
                :bulletins false}]} "R" "991") => {:foo :bar}
    (bulletin-settings-for-scope
      {:scope [{:permitType "R"
                :municipality "991"
                :bulletins {:foo :bar}}
               {:permitType "P"
                :municipality "991"
                :bulletins false}]} "R" "992") => nil
    (bulletin-settings-for-scope
      {:scope [{:permitType "R"
                :municipality "991"
                :bulletins {:foo :bar}}
               {:permitType "P"
                :municipality "991"
                :bulletins false}]} "P" "991") => nil))

(facts organization-statement-giver-context
  (fact "is statementGiver"
    (:permissions (organization-statement-giver-context {:user         {:id "1" :email "pena@example.com"}
                                                         :application  {:auth [{:id "1" :role "statementGiver"}]}
                                                         :organization (delay {:statementGivers [{:email "pena@example.com"}]})}))
    => #{:organization/give-statement}

    (provided (lupapalvelu.permissions/get-permissions-by-role :organization :statementGiver) => #{:organization/give-statement}))

  (fact "statementGiver not authorized in application"
    (:permissions (organization-statement-giver-context {:user         {:id "1" :email "pena@example.com"}
                                                         :application  {:auth [{:id "2" :role "statementGiver"}]}
                                                         :organization (delay {:statementGivers [{:email "pena@example.com"}]})}))
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0))

  (fact "is statementGiver - multiple roles"
    (:permissions (organization-statement-giver-context {:user         {:id "1" :email "pena@example.com"}
                                                         :application  {:auth [{:id "1" :role "writer"} {:id "1" :role "statementGiver"}]}
                                                         :organization (delay {:statementGivers [{:email "pena@example.com"}]})}))
    => #{:organization/give-statement}

    (provided (lupapalvelu.permissions/get-permissions-by-role :organization :statementGiver) => #{:organization/give-statement} :times 1))

  (fact "no statementGiver authorization in application"
    (:permissions (organization-statement-giver-context {:user         {:id "1" :email "pena@example.com"}
                                                         :application  {:auth [{:id "1" :role "writer"}]}
                                                         :organization (delay {:statementGivers [{:email "pena@example.com"}]})}))
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0))

  (fact "not statementGiver in organization"
    (:permissions (organization-statement-giver-context {:user         {:id "1" :email "pena@example.com"}
                                                         :application  {:auth [{:id "1" :role "statementGiver"}]}
                                                         :organization (delay {:statementGivers [{:email "mikko@example.com"}]})}))
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0))

  (fact "no application in command"
    (:permissions (organization-statement-giver-context {:user         {:id "1" :email "pena@example.com"}}))
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0)))

(defn- krysp-unset-map [type _]
  {$unset
   {(str "krysp." type ".username")  1
    (str "krysp." type ".password")  1
    (str "krysp." type ".crypto-iv") 1}})

(facts "dissoc-credentials"
  (facts "no updates"
    (fact "conf can be nil"
      (dissoc-credentials "new" nil "R") => nil)
    (fact "set new username, no $unsets"
      (dissoc-credentials "new" {:credentials ["" "pw"]} "R") => nil)
    (fact "old username can be presents"
      (dissoc-credentials "new" {:credentials ["old" "pw"]} "R") => nil)
    (fact "new is blank, but no old"
      (dissoc-credentials "" {:credentials ["" "pw"]} "R") => nil))
  (facts "updates (new is blank)"
    (dissoc-credentials "" {:credentials ["old" "pw"]} "R") => (partial krysp-unset-map "R")
    (dissoc-credentials "" {:credentials ["old" "pw"]} "testi") => (partial krysp-unset-map "testi")))

(facts "krysp-url checkers"
  (fact "with url"
    (some-krysp-url? {:krysp {:A {:url "jee"} :B {:url "foo"}}}) => true
    (krysp-urls-not-set? {:krysp {:A {:url "jee"} :B {:url "foo"}}}) => false)
  (fact "with other blank"
    (some-krysp-url? {:krysp {:A {:url "jee"} :B {:url ""}}}) => true
    (krysp-urls-not-set? {:krysp {:A {:url "jee"} :B {:url ""}}}) => false)
  (fact "with both blank"
    (some-krysp-url? {:krysp {:A {:url ""} :B {:url ""}}}) => false
    (krysp-urls-not-set? {:krysp {:A {:url ""} :B {:url ""}}}) => true))

(facts "pate-scope?"
  (fact "pate enabled for scope"
    (pate-scope? {:permitType "R" :organization "178-R" :municipality "178"}) => true
    (provided
      (mongo/by-id :organizations "178-R") => {:scope [{:municipality "178"
                                                        :permitType "R"
                                                        :pate {:enabled true}}]}))
  (fact "pate not enabled for scope"
    (pate-scope? {:permitType "P" :organization "178-R" :municipality "178"}) => false
    (provided
      (mongo/by-id :organizations "178-R") => {:scope [{:municipality "178"
                                                        :permitType "P"
                                                        :pate {:enabled false}}]}))
  (fact "pate not set"
    (pate-scope? {:permitType "P" :organization "178-R" :municipality "178"}) => falsey
    (provided
      (mongo/by-id :organizations "178-R") => {:scope [{:municipality "178"
                                                        :permitType "P"}]})))

(facts "allowed roles in organization"
       (fact "Remove permanent archive and invoicing roles"
         (let [organization                    {:foo-role                  "I shall pass"
                                                :permanent-archive-enabled false
                                                :reporting-enabled         true
                                                :scope                     [{:invoicing-enabled false}]}
               roles-without-billing-archiving (->> roles/all-org-authz-roles
                                                    (remove roles/permanent-archive-authority-roles)
                                                    (remove roles/invoicing-roles)
                                                    (remove roles/departmental-roles))]
               (allowed-roles-in-organization organization) => (just roles-without-billing-archiving)))

       (fact "Remove invoicing roles, show permanent archive roles"
         (let [organization                    {:permanent-archive-enabled true
                                                :reporting-enabled         true
                                                :scope                     [{:invoicing-enabled false}]}
               roles-without-billing-archiving (->> roles/all-org-authz-roles
                                                    (remove roles/invoicing-roles)
                                                    (remove roles/departmental-roles))]
               (allowed-roles-in-organization organization) => (just roles-without-billing-archiving)))

       (fact "Remove permanent archive roles, show invoicing roles"
         (let [organization                    {:permanent-archive-enabled false
                                                :reporting-enabled         true
                                                :scope                     [{:invoicing-enabled false}
                                                                            {:invoicing-enabled false}
                                                                            {:invoicing-enabled true}]}
               roles-without-billing-archiving (->> roles/all-org-authz-roles
                                                    (remove roles/permanent-archive-authority-roles)
                                                    (remove roles/departmental-roles))]
               (allowed-roles-in-organization organization) => (just roles-without-billing-archiving)))

       (fact "Allow invoicing roles and permanent archive roles"
         (let [organization                    {:permanent-archive-enabled true
                                                :reporting-enabled         true
                                                :scope                     [{:invoicing-enabled false}
                                                                            {:invoicing-enabled true}
                                                                            {:invoicing-enabled false}]}
               roles-without-billing-archiving (remove roles/departmental-roles roles/all-org-authz-roles)]
               (allowed-roles-in-organization organization) => (just roles-without-billing-archiving)))


  (fact "Remove reporting roles"
    (let [organization            {:permanent-archive-enabled true
                                   :scope                     [{:invoicing-enabled true}]}
          roles-without-reporting (->> roles/all-org-authz-roles
                                       (remove roles/reporting-roles)
                                       (remove roles/departmental-roles))]
      (allowed-roles-in-organization organization) => (just roles-without-reporting)))

  (fact "Departmental roles"
    (let [organization {:docstore-info {:docDepartmentalInUse true}}]
      (allowed-roles-in-organization organization) => (contains :departmental))))

(facts "has-permit-type?"
       (fact "returns nil when organization does not have given permit type in its scope"
             (has-permit-type? {:scope [{:permitType "FOO"}]} "R") => falsey)

       (fact "returns true when organization has given permit type in its scope"
             (has-permit-type? {:scope [{:permitType "FOO"}{:permitType "R"}]} :R) => truthy))

(facts "get-organization-name"
  (fact "Organization: supported lang"
    (get-organization-name {:name {:en "Hello"}} "en") => "Hello")
  (fact "Organization: not supported lang. Fallback to Finnish"
    (get-organization-name {:name {:en "Hello"
                                   :fi "Moro"}} "cn") => "Moro")
  (fact "Organization: with-lang, supported"
    (i18n/with-lang "en"
      (get-organization-name {:name {:en "Hello"}})) => "Hello")
  (fact "Organization: with-lang, not supported, fallback to Finnish"
    (i18n/with-lang "cn"
      (get-organization-name {:name {:fi "Moro"}})) => "Moro")
  (fact "Organization: with-lang, not supported, no fallback"
    (i18n/with-lang "cn"
      (get-organization-name {:id   "foo"
                              :name {:en "Hello"}})) => "???ORG:foo???")
  (fact "Organization: not supported lang, no fallback"
    (get-organization-name {:id   "foo"
                            :name {:en "Hello"}} "cn") => "???ORG:foo???")
  (fact "Organization: lang is keyword"
    (get-organization-name {:id   "foo"
                            :name {:en "Hello"}} :en) => "Hello")
  (fact "Organization: lang is nil. Fallback to Finnish"
    (get-organization-name {:id   "foo"
                            :name {:fi "Moro"}} nil) => "Moro")


  (fact "Org-id: supported lang"
    (get-organization-name "Idefix" "en") => "Asterix"
    (provided (get-org-from-org-or-id "Idefix") => {:id "Idefix" :name {:en "Asterix" :fi "Obelix"}}))
  (fact "Org-id: not supported lang. Fallback to Finnish"
    (get-organization-name "Idefix" "cn") => "Obelix"
    (provided (get-org-from-org-or-id "Idefix") => {:id "Idefix" :name {:en "Asterix" :fi "Obelix"}}))
  (fact "Orgid: with-lang, supported"
    (i18n/with-lang "en"
      (get-organization-name "Idefix")) => "Asterix"
    (provided (get-org-from-org-or-id "Idefix") => {:id "Idefix" :name {:en "Asterix" :fi "Obelix"}}))
  (fact "Org-id: with-lang, not supported, fallback to Finnish"
    (i18n/with-lang "cn"
      (get-organization-name "Idefix")) => "Obelix"
    (provided (get-org-from-org-or-id "Idefix") => {:id "Idefix" :name {:en "Asterix" :fi "Obelix"}}))
  (fact "Org-id: with-lang, not supported, no fallback"
    (i18n/with-lang "cn"
      (get-organization-name "Idefix")) => "???ORG:Idefix???"
    (provided (get-org-from-org-or-id "Idefix") => {:id "Idefix" :name {:en "Asterix"}}))
  (fact "Org-id: not supported lang, no fallback"
    (get-organization-name "Idefix" "cn") => "???ORG:Idefix???"
    (provided (get-org-from-org-or-id "Idefix") => {:id "Idefix" :name {:en "Asterix"}}))
  (fact "Org-id: lang is keyword"
    (get-organization-name "Idefix" :en) => "Asterix"
    (provided (get-org-from-org-or-id "Idefix") => {:id "Idefix" :name {:en "Asterix"}})))


(let [scope-753   {:permitType   "R"
                   :municipality "753"}
      fake-sipoo  {:id    "753-R"
                   :scope [scope-753]}
      scope-297   {:permitType   "R"
                   :municipality "297"}
      scope-077   {:permitType   "R"
                   :municipality "077"}
      fake-kuopio {:id    "297-R"
                   :scope [scope-297 scope-077]}]
  (against-background
    [(lupapalvelu.organization/get-organizations {:scope {$elemMatch {:municipality "753"}}}) => [fake-sipoo]
     (lupapalvelu.organization/get-organizations {:scope {$elemMatch {:municipality "753"
                                                                      :permitType   "R"}}}) => [fake-sipoo]
     (lupapalvelu.organization/get-organizations {:scope {$elemMatch {:municipality "753"
                                                                      :permitType   "P"}}}) => []
     (lupapalvelu.organization/get-organizations {:scope {$elemMatch {:municipality "297"}}}) => [fake-kuopio]
     (lupapalvelu.organization/get-organizations {:scope {$elemMatch {:municipality "297"
                                                                      :permitType   "R"}}}) => [fake-kuopio]
     (lupapalvelu.organization/get-organizations {:scope {$elemMatch {:municipality "077"}}}) => [fake-kuopio]
     (lupapalvelu.organization/get-organizations {:scope {$elemMatch {:municipality "077"
                                                                      :permitType   "R"}}}) => [fake-kuopio]
     (lupapalvelu.organization/get-organizations {:scope {$elemMatch {:municipality nil
                                                                      :permitType   "R"}}}) => []
     (lupapalvelu.organization/get-organizations {:scope {$elemMatch {:municipality nil}}}) => []]
   (facts "resolve-organizations"
     (resolve-organizations "753") => [fake-sipoo]
     (resolve-organizations "753" "R") => [fake-sipoo]
     (resolve-organizations "753" "P") => []
     (resolve-organizations "bad") => []
     (resolve-organizations "bad" "R") => []
     (resolve-organizations "297") => [fake-kuopio]
     (resolve-organizations "077") => [fake-kuopio]
     (fact "Juankoski is merged into Kuopio"
       (resolve-organizations "174") => [fake-kuopio]))
   (facts "resolve-organization"
     (resolve-organization "753" "R") => fake-sipoo
     (resolve-organization "753" "P") => nil
     (resolve-organization "297" "R") => fake-kuopio
     (resolve-organization "077" "R") => fake-kuopio
     (resolve-organization "174" "R") => fake-kuopio)
   (facts "resolve-organization-scope"
     (resolve-organization-scope "753" "R") => scope-753
     (resolve-organization-scope "753" "P") => (throws AssertionError)
     (resolve-organization-scope "753" "BAD") => (throws AssertionError)
     (resolve-organization-scope "297" "R") => scope-297
     (resolve-organization-scope "077" "R") => scope-077
     (resolve-organization-scope "174" "R") => scope-297)))

(facts "is-pure-ymp-org-user?"
  (let [testCases [{:case                               "Only one type / one organization"
                    :permit-types-of-user-organizations [["YI"]]
                    :expected                           true}
                   {:case                               "All YMP types / one organization"
                    :permit-types-of-user-organizations [["YI" "YM" "VVVL" "MAL" "YL"]]
                    :expected                           true}
                   {:case                               "YMP types only / many organization"
                    :permit-types-of-user-organizations [["YI" "YM" "VVVL" "MAL" "YL"]
                                                         ["YI" "YM" "VVVL"]
                                                         ["MAL" "YL"]]
                    :expected                           true}
                   {:case                               "Only non-YMP types one organization"
                    :permit-types-of-user-organizations [["R"]]
                    :expected                           false}
                   {:case                               "Only non-YMP types / one organization"
                    :permit-types-of-user-organizations [["R"]]
                    :expected                           false}
                   {:case                               "Both YMP and non-YMP types / one organization"
                    :permit-types-of-user-organizations [["YA" "YL"]]
                    :expected                           false}
                   {:case                               "YMP and non-YMP types in different organization"
                    :permit-types-of-user-organizations [["R" "P"]
                                                         ["YI" "YM" "VVVL" "MAL" "YL"]]
                    :expected                           false}
                   {:case                               "No organizations"
                    :permit-types-of-user-organizations []
                    :expected                           nil}]]
    (doseq [{:keys [case
                    permit-types-of-user-organizations
                    expected]} testCases
            :let [user-organizations
                  (->> permit-types-of-user-organizations
                       (map (partial map (partial hash-map :permitType)))
                       (map (partial hash-map :scope)))]]
      (fact {:midje/description case}
        (is-pure-ymp-org-user? user-organizations) => expected))))
