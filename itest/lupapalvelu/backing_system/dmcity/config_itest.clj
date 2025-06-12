(ns lupapalvelu.backing-system.dmcity.config-itest
  (:require [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
            [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]))

(def sipoo-cfg   #(dm-cfg/config "753-R"))
(def bad-upsert  (throws #"753-R: Bad dmcity configuration upsert."))
(def schema-boom (throws #"does not match schema"))

(with-test-system {:fixture "minimal"}
  (facts "config"
    (facts "No configuration"
      (dm-cfg/config nil) => nil
      (dm-cfg/config {}) => nil
      (dm-cfg/config "753-R") => nil
      (dm-cfg/config "BAD") => nil
      (dm-cfg/config (delay (mongo/by-id :organizations "186-R"))) => nil)

    (facts "Configuration"
      ;; From minimal
      (doseq [a ["837-R"
                 (mongo/by-id :organizations "837-R")
                 (delay (mongo/by-id :organizations "837-R"))]]
        (dm-cfg/config a) => (contains {:username "test-user"
                                        :vault    "test-vault"}))))

  (let [cfg (dm-cfg/simple->config {:username " Hello "
                                    :password " World "
                                    :vault    " pole "
                                    :url      "https://example.net/dmcity"})]
    (fact "Base config"
      cfg => (just {:username "Hello" :password  truthy
                    :vault    "pole"  :crypto-iv truthy
                    :url      "https://example.net/dmcity"}))
    (facts "upsert"
      (fact "No such organization"
        (dm-cfg/upsert "404-R" (constantly cfg))
        (dm-cfg/config "404-R") => nil)

      (fact "No existing configuration: insert"
        (dm-cfg/upsert "753-R" (constantly cfg))
        (sipoo-cfg) => cfg
        (dm-cfg/config->simple (sipoo-cfg))
        => {:username "Hello"
            :password "World"
            :vault    "pole"
            :url      "https://example.net/dmcity"})
      (fact "Existing configuration: good updates"
        (dm-cfg/upsert "753-R" update :username ss/upper-case)
        (sipoo-cfg) => (assoc cfg :username "HELLO"))
      (facts "Existing configuration: bad updates"
        (dm-cfg/upsert "753-R" dissoc :username)
        => bad-upsert

        (fact "Credentials must be properly encoded"
          (dm-cfg/upsert "753-R" assoc :password "foobar")
          => bad-upsert
          (dm-cfg/upsert "753-R" merge (bs-sec/encode-credentials "HELLO" "foobar"))
          (dm-cfg/config->simple (sipoo-cfg))
          => {:username "HELLO"
              :password "foobar"
              :vault    "pole"
              :url      "https://example.net/dmcity"})))

    (facts "toggle-target"
      (dm-cfg/toggle-target "753-R" :R true)
      (dm-cfg/toggle-target "753-R" "YA" "yeah")
      (dm-cfg/toggle-target "753-R" :link true)
      (:targets (dm-cfg/config->simple (sipoo-cfg)))
      => {:R true :YA true :link true}
      (dm-cfg/toggle-target "753-R" :BAD true)
      => schema-boom

      (fact "Url required for some targets"
        (dm-cfg/toggle-target "753-R" :building false)
        => bad-upsert
        (dm-cfg/upsert "753-R" assoc-in [:targets :building]
                       {:enabled false :url " http://build.example.com "})
        (-> (dm-cfg/config->simple (sipoo-cfg))
            :targets :building)
        => {:enabled false
            :url     "http://build.example.com"}
        (dm-cfg/get-target "753-R" :building)
        => {:enabled false
            :url     "http://build.example.com"}
        (dm-cfg/get-target (sipoo-cfg) :building)
        => {:enabled false
            :url     "http://build.example.com"}
        (dm-cfg/target-enabled? "753-R" "building")
        => false
        (dm-cfg/get-target "BAD" :bad) => nil
        (dm-cfg/get-target "753-R" :bad) => nil
        (dm-cfg/target-enabled? "BAD" :bad) => false
        (dm-cfg/target-enabled? "753-R" :bad) => false))

    (facts "get-path"
      (dm-cfg/get-path "753-R" :R :application)
      => "Rakennusvalvontapaatos"
      (dm-cfg/get-path "753-R" "R" "review")
      => "Katselmustieto"
      (dm-cfg/get-path "753-R" :R :bad) => nil
      (dm-cfg/get-path "753-R" :BAD :verdict) => nil
      (dm-cfg/upsert "753-R" assoc-in
                     [:targets :YA :verdict]
                     "  RoadToNowhere  ")
      => bad-upsert
      (dm-cfg/get-path "753-R" "YA" :verdict) => nil
      (dm-cfg/get-path "753-R" :conversion "R")
      => "RakennuslupienKonversio"

      (facts "link format"
        (dm-cfg/get-path "753-R" :link :fmt) => nil
        (provided
          (env/value :dmcity :link-format) => nil)
        (dm-cfg/get-path "753-R" :link :fmt) => "{{hello}}"
        (provided
          (env/value :dmcity :link-format) => "{{hello}}")
        (dm-cfg/upsert "753-R" assoc-in [:targets :link :fmt] "{{world}}")
        (dm-cfg/get-path "753-R" :link :fmt) => "{{world}}"
        (dm-cfg/upsert "753-R" assoc-in [:targets :link :fmt] "    ")
        (dm-cfg/get-path "753-R" :link :fmt) => "{{hello}}"
        (provided
          (env/value :dmcity :link-format) => "{{hello}}"))))

  (fact "trim-base-url"
    (dm-cfg/trim-base-url "") => ""
    (dm-cfg/trim-base-url nil) => nil
    (dm-cfg/trim-base-url " hello/world//// ") => "hello/world"
    (dm-cfg/trim-base-url "http://build.example.com/yeah")
    => "http://build.example.com/yeah")

  (let [simple    {:username "hello" :password "world"
                   :vault    "pole"
                   :url      "https://example.net/dmcity"}
        cfg       (dm-cfg/simple->config simple)
        cfg-check {:username  "hello" :password truthy
                   :crypto-iv truthy
                   :vault     "pole"
                   :url       "https://example.net/dmcity"}
        cfg2      (assoc cfg :targets {:R        {:enabled     true
                                                  :application "my-apps"
                                                  :review      "my-revs"}
                                       :state    {:enabled false
                                                  :state   "my-state"}
                                       :building {:enabled true
                                                  :url     "http://build.example.com"}
                                       :address  {:enabled true
                                                  :url     "http://addr.example.com"}})]
    (facts "update-config"
      cfg => (just cfg-check)
      (dm-cfg/update-config nil simple)
      => (just cfg-check)
      (dm-cfg/update-config cfg2 simple)
      => (just (assoc cfg-check :targets {:R     {:enabled     false
                                                  :application "my-apps"
                                                  :review      "my-revs"}
                                          :state {:enabled false
                                                  :state   "my-state"}}))
      (dm-cfg/update-config cfg2 (assoc simple :targets {:R        true
                                                         :P        true
                                                         :building {:enabled false
                                                                    :url     "http://house.net"}}))
      => (just (assoc cfg-check :targets {:R        {:enabled     true
                                                     :application "my-apps"
                                                     :review      "my-revs"}
                                          :P        {:enabled true}
                                          :building {:enabled false
                                                     :url     "http://house.net"}
                                          :state    {:enabled false
                                                     :state   "my-state"}})))

    (facts "building-wfs"
      (dm-cfg/upsert "753-R" (constantly cfg2)) => nil
      (dm-cfg/building-wfs "753-R") => {:url         "http://build.example.com"
                                        :credentials ["hello" "world"]}

      (dm-cfg/toggle-target "753-R" :building false) => nil
      (dm-cfg/building-wfs "753-R") => nil)

    (facts "address-endpoint"
      (dm-cfg/address-endpoint "404") => nil
      (dm-cfg/address-endpoint "753") => {:url         "http://addr.example.com"
                                          :no-bbox-srs true}
      (dm-cfg/toggle-target "753-R" :address false)
      (dm-cfg/address-endpoint "753") => nil)

    (facts "dmcity-application-url"
      (let [app {:id         "LP-753-2026-90001"
                 :permitType "R"}]
        (fact "Not enabled"
          (dm-cfg/dmcity-application-url cfg app) => nil
          (dm-cfg/dmcity-application-url (dm-cfg/update-config cfg (assoc simple :targets {:R true :link false}))
                                         app) => nil
          (dm-cfg/dmcity-application-url (dm-cfg/update-config cfg (assoc simple :targets {:R false :link true}))
                                         app) => nil)

        (let [cfg (dm-cfg/update-config cfg (assoc simple :targets {:R true :link true}))]
          (fact "Enabled and link"
            (dm-cfg/dmcity-application-url cfg app)
            => "https://example.net/dev/dummylink/pole/LP-753-2026-90001")
          (fact "With port"
            (dm-cfg/dmcity-application-url (dm-cfg/update-config cfg (assoc simple
                                                                            :url "http://www.example.com:5432"
                                                                            :targets {:R true :link true}))
                                           app)
            => "http://www.example.com:5432/dev/dummylink/pole/LP-753-2026-90001")
          (fact "Foreman application, no link"
            (dm-cfg/dmcity-application-url cfg (assoc-in app [:primaryOperation :name] "tyonjohtajan-nimeaminen-v2"))
            => nil))))))
