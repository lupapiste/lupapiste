(ns lupapalvelu.dmcity.dmcity-api-itest
  "dmCity admin and batchrun itests.

  Note: Tests for `send-messages-to-dmcity` command are in `other_itest.clj` with the
  similar use cases (and test data)"
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [schema.core :as sc]))

(def org-id "186-R")

(def auth-admin jarvenpaa)

(defn query-org-config []
  (select-keys (query admin :dmcity-configuration :organizationId org-id)
               [:configuration :permit-types]))

(defn org-mongo-config []
  (:dmcity (mongo/by-id :organizations org-id [:dmcity])))

(defn org-valid []
  (fact "Target organization is valid"
    (sc/check org/Organization (mongo/by-id :organizations org-id))
    => nil))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (with-local-actions

    (org-valid)

    (facts "dmcity configuration"
      (fact "No such organization"
        (query admin :dmcity-configuration
               :organizationId "BAD-R"))
      (fact "Initially empty config"
        (query-org-config)
        => (contains {:configuration nil
                      :permit-types  ["R"]}))
      (facts "Toggling or deletion not supported without existing configuration"
        (fact "Toggle"
          (command admin :toggle-dmcity-configuration-target
                   :organizationId org-id
                   :target "R"
                   :enabled true)
          => (err :error.dmcity.not-configured))
        (fact "Delete"
          (command admin :delete-dmcity-configuration
                   :organizationId org-id)
          => (err :error.dmcity.not-configured))
        (facts "Authority admin toggles"
          (query auth-admin :dmcity-configuration-flags
                 :organizationId org-id)
          => (err :error.dmcity.not-configured)
          (command auth-admin :set-dmcity-configuration-flags
                   :organizationId org-id
                   :flags {:R false})
          => (err :error.dmcity.not-configured)))

      (facts "Upsert configuration"
        (facts "Minimal configuration"
          (command admin :upsert-dmcity-configuration
                   :organizationId org-id
                   :configuration {:username " donna "
                                   :password " marvel "
                                   :vault    " fortress "})
          => (err :error.dmcity.configuration)
          (command admin :upsert-dmcity-configuration
                   :organizationId org-id
                   :configuration {:username " donna "
                                   :password " marvel "
                                   :vault    " fortress "
                                   :url      " http://example.net/dmcity/" })
          => ok?
          (query-org-config)
          => {:configuration {:username "donna"
                              :password "marvel"
                              :vault    "fortress"
                              :url      "http://example.net/dmcity"}
              :permit-types  ["R"]}
          (org-mongo-config)
          => (just {:username  "donna"
                    :password  #(and (not= % "marvel") not-empty)
                    :crypto-iv truthy
                    :vault     "fortress"
                    :url       "http://example.net/dmcity"})
          (fact "Authority admin"
            (query auth-admin :dmcity-configuration-flags
                   :organizationId org-id)
            => {:ok    true
                :flags {:R false :state false :link false}}))
        (facts "Bad upserts"
          (command admin :upsert-dmcity-configuration
                   :organizationId org-id
                   :configuration {:username " donna "
                                   :password " marvel "
                                   :vault    " fortress "
                                   :url      "http://example.net/dmcity/"
                                   :bad      "key"})
          => (err :error.dmcity.configuration)
          (command admin :upsert-dmcity-configuration
                   :organizationId org-id
                   :configuration {:username " donna "
                                   :password " marvel "
                                   :vault    " fortress "
                                   :url      "http://example.net/dmcity/"
                                   :targets  {:R "blaah"}})
          => (err :error.dmcity.configuration)
          (command admin :upsert-dmcity-configuration
                   :organizationId org-id
                   :configuration {:username " donna "
                                   :password " marvel "
                                   :vault    "  "
                                   :url      "http://example.net/dmcity/"})
          => (err :error.dmcity.configuration)
          (command admin :upsert-dmcity-configuration
                   :organizationId "404-YA"
                   :configuration {:username " donna "
                                   :password " marvel "
                                   :vault    " yeah "
                                   :url      "http://example.net/dmcity/"})
          => (err :error.organization-not-found)
          (fact "Authority admin"
            (command auth-admin :set-dmcity-configuration-flags
                     :organizationId org-id
                     :flags {:foo true})
            => (err :error.dmcity.unsupported-flag)
            (command auth-admin :set-dmcity-configuration-flags
                     :organizationId org-id
                     :flags {:P false})
            => (err :error.dmcity.unsupported-flag)
            (command auth-admin :set-dmcity-configuration-flags
                     :organizationId org-id
                     :flags {:state true
                             :R     "hello"})
            => (err :error.illegal-value:schema-validation))))

      (facts "redirect-to-dmcity"
        ;; Tampere (837-R) has dmcity config in minimal
        (let [{app-id :id} (create-local-app pena
                                             :x 329072
                                             :y 6823200
                                             :propertyId "83712103620001"
                                             :address "Brewery")
              application  (query-application local-query pena app-id)]
          (generate-documents! application pena true)
          (command pena :submit-application :id app-id) => ok?
          (fact "Wrong state"
            (local-raw veikko :redirect-to-dmcity :id app-id)
            => (err :error.command-illegal-state))
          (command veikko :approve-application :id app-id :lang "fi") => ok?
          (fact "Success. Dummy link."
            (local-raw veikko :redirect-to-dmcity :id app-id)
            => {:headers {"Location" (str (server-address)
                                          "/dev/dummylink/test-vault/LP-837-2026-90001")}
                :status  303} )))

      (fact "Upsert does not touch paths"
        (command admin :upsert-dmcity-configuration
                 :organizationId "837-R"
                 :configuration {:username " tam "
                                 :password " pere "
                                 :vault    " finlayson "
                                 :url      "http://example.net/dmcity/"
                                 :targets  {:R false}})
        => ok?
        (:dmcity (mongo/by-id :organizations "837-R" [:dmcity]))
        => (contains {:username "tam"
                      :vault    "finlayson"
                      :targets  (contains {:R {:enabled     false
                                               :application "hakemus-path"
                                               :review      "katselmus-path"}})})
        (fact "Authority admin"
          (command veikko :set-dmcity-configuration-flags
                   :organizationId "837-R"
                   :flags {:R true})
          => ok?
          (:dmcity (mongo/by-id :organizations "837-R" [:dmcity]))
          => (contains {:username "tam"
                        :vault    "finlayson"
                        :targets  (contains {:R {:enabled     true
                                                 :application "hakemus-path"
                                                 :review      "katselmus-path"}})})))

      (fact "Full configuration: scope does not matter"
        (command admin :upsert-dmcity-configuration
                 :organizationId org-id
                 :configuration {:username " full "
                                 :password " freight "
                                 :vault    " youwza "
                                 :url      " http://example.net/every/ "
                                 :targets  {:R          true :P true :YA true :link true
                                            :conversion false
                                            :building   {:enabled true
                                                         :url     " http://build.example.com "}
                                            :address    {:enabled false
                                                         :url     " http://addr.example.com "}}})
        => ok?
        (org-mongo-config) => (just {:username  "full"
                                     :password  #(and (not= % "freight") not-empty)
                                     :crypto-iv truthy
                                     :vault     "youwza"
                                     :url       "http://example.net/every"
                                     :targets   {:R          {:enabled true}
                                                 :P          {:enabled true}
                                                 :YA         {:enabled true}
                                                 :link       {:enabled true}
                                                 :conversion {:enabled false}
                                                 :building   {:enabled true
                                                              :url     "http://build.example.com"}
                                                 :address    {:enabled false
                                                              :url     "http://addr.example.com"}}})
        (query-org-config)
        => {:configuration {:username "full"
                            :password "freight"
                            :vault    "youwza"
                            :url      "http://example.net/every"
                            :targets  {:R          true  :P    true :YA true
                                       :conversion false :link true
                                       :building   {:enabled true
                                                    :url     "http://build.example.com"}
                                       :address    {:enabled false
                                                    :url     "http://addr.example.com"}}}
            :permit-types  ["R"]}
        (fact "Authority admin only sees supported flags"
          (:flags (query auth-admin :dmcity-configuration-flags
                         :organizationId org-id))
          => {:R true :building true :address false :state false :link true}
          (command auth-admin :set-dmcity-configuration-flags
                   :organizationId org-id
                   :flags {:P false})
          => (err :error.dmcity.unsupported-flag)
          (command auth-admin :set-dmcity-configuration-flags
                   :organizationId org-id
                   :flags {:state true :building false :link false})
          => ok?
          (-> (query-org-config) :configuration :targets)
          => {:R          true  :P     true
              :YA         true  :state true
              :conversion false :link  false
              :building   {:enabled false
                           :url     "http://build.example.com"}
              :address    {:enabled false
                           :url     "http://addr.example.com"}}))

      (facts "Targets with blank urls are removed"
        (command admin :upsert-dmcity-configuration
                 :organizationId org-id
                 :configuration {:username " full "
                                 :password " freight "
                                 :vault    " youwza "
                                 :url      " http://example.net/every/ "
                                 :targets  {:R        true :P true :YA true
                                            :building {:enabled true
                                                       :url     ""}}})
        => ok?
        (org-mongo-config) => (just {:username  "full"
                                     :password  #(and (not= % "freight") not-empty)
                                     :crypto-iv truthy
                                     :vault     "youwza"
                                     :url       "http://example.net/every"
                                     :targets   {:R          {:enabled true}
                                                 :P          {:enabled true}
                                                 :YA         {:enabled true}
                                                 :state      {:enabled false}
                                                 :link       {:enabled false}
                                                 :conversion {:enabled false}}})
        (query-org-config)
        => {:configuration {:username "full"
                            :password "freight"
                            :vault    "youwza"
                            :url      "http://example.net/every"
                            :targets  {:R          true
                                       :P          true
                                       :YA         true
                                       :state      false
                                       :link       false
                                       :conversion false}}
            :permit-types  ["R"]})

      (facts "Toggle targets"
        (facts "Good targets"
          (command admin :toggle-dmcity-configuration-target
                   :organizationId org-id
                   :target "R"
                   :enabled false) => ok?
          (command admin :toggle-dmcity-configuration-target
                   :organizationId org-id
                   :target "P"
                   :enabled false) => ok?
          (command admin :toggle-dmcity-configuration-target
                   :organizationId org-id
                   :target "YA"
                   :enabled false) => ok?
          (command admin :toggle-dmcity-configuration-target
                   :organizationId org-id
                   :target "conversion"
                   :enabled true) => ok?
          (command admin :toggle-dmcity-configuration-target
                   :organizationId org-id
                   :target "link"
                   :enabled true) => ok?)
        (-> (query-org-config) :configuration :targets)
        => {:R  false :P          false :state false
            :YA false :conversion true  :link  true}
        (facts "Bad targets"
          (command admin :toggle-dmcity-configuration-target
                   :organizationId org-id
                   :target "A"
                   :enabled false)
          => (err :error.dmcity.configuration)
          (command admin :toggle-dmcity-configuration-target
                   :organizationId org-id
                   :target "address"
                   :enabled false)
          => (err :error.dmcity.configuration)
          (command admin :toggle-dmcity-configuration-target
                   :organizationId "404-R"
                   :target "P"
                   :enabled false)
          => (err :error.dmcity.not-configured)
          (command admin :toggle-dmcity-configuration-target
                   :organizationId "753-YA"
                   :target "YA"
                   :enabled false)
          => (err :error.dmcity.not-configured))

        (facts "Address and building can be toggled if the urls exist"
          (command admin :upsert-dmcity-configuration
                   :organizationId org-id
                   :configuration {:username " full "
                                   :password " freight "
                                   :vault    " youwza "
                                   :url      " http://example.net/every/ "
                                   :targets  {:R        true
                                              :building {:enabled true
                                                         :url     " http://build.example.com "}
                                              :address  {:enabled false
                                                         :url     " http://addr.example.com "}}})
          => ok?
          (command admin :toggle-dmcity-configuration-target
                   :organizationId org-id
                   :target "address"
                   :enabled true) => ok?
          (command admin :toggle-dmcity-configuration-target
                   :organizationId org-id
                   :target "building"
                   :enabled false) => ok?
          (-> (query-org-config) :configuration :targets)
          => {:R        true  :P          false
              :YA       false :conversion false
              :state    false
              :link     false
              :building {:enabled false
                         :url     "http://build.example.com"}
              :address  {:enabled true
                         :url     "http://addr.example.com"}}))

      (facts "Change base url"
        (command admin :set-dmcity-url
                 :organizationId "092-R"
                 :url " http://foo.example.com/hello/ ")
        => (err :error.dmcity.not-configured)
        (command admin :set-dmcity-url
                 :organizationId org-id
                 :url "bad")
        => (err :error.dmcity.configuration)
        (command admin :set-dmcity-url
                 :organizationId org-id
                 :url " http://foo.example.com/hello/// ")
        => ok?
        (:url (org-mongo-config)) => "http://foo.example.com/hello")

      (facts "Delete configuration"
        (command admin :delete-dmcity-configuration
                 :organizationId "404-R")
        => (err :error.dmcity.not-configured)
        (command admin :delete-dmcity-configuration
                 :organizationId "753-YA")
        => (err :error.dmcity.not-configured)
        (command admin :delete-dmcity-configuration
                 :organizationId org-id)
        => ok?
        (command admin :delete-dmcity-configuration
                 :organizationId org-id)
        => (err :error.dmcity.not-configured)
        (org-mongo-config) => nil))))
