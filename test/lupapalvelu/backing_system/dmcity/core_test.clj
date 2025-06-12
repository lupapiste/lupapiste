(ns lupapalvelu.backing-system.dmcity.core-test
  (:require [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
             [lupapalvelu.backing-system.dmcity.core :as dmcity]
             [lupapalvelu.backing-system.dmcity.schemas :refer [DmcityIntegration]]
             [midje.sweet :refer :all]
             [midje.util :refer [testable-privates]]
             [schema.core :as sc]))

(testable-privates lupapalvelu.backing-system.dmcity.core
                   resolve-url)

(def cfg (-> {:username "hello" :password "word" :vault "pole"
              :url      "http://example.net/dmcity"
              :targets  {:R true :state true}}
             dm-cfg/simple->config
             (update-in [:targets :R] merge {:application "my-apps"
                                             :review      "my-revs"})))
(sc/validate DmcityIntegration cfg)

(def sipoo-r {:id "753-R" :dmcity cfg})
(def sipoo-ya {:id     "753-YA"
               :dmcity (sc/validate DmcityIntegration
                                    (assoc-in cfg [:targets :YA :enabled] true))})

(def sipoo-c
  (update sipoo-r :dmcity
          #(sc/validate DmcityIntegration
                        (assoc-in % [:targets :conversion :enabled] true))))

(def application {:id         "LP-753-2023-12345"
                  :permitType "R"})

(def user {:id "the-user"})

(facts "resolve-url"
  (fact "No config"
    (resolve-url {:id "FOO-R"} application user :application)
    => {:text "No dmcity configuration for FOO-R."})

  (fact "No messages for foreman applications"
    (resolve-url sipoo-r
                 (assoc-in application [:primaryOperation :name]
                           "tyonjohtajan-nimeaminen-v2")
                 :application
                 user)
    => {:text "Foreman application messages are not sent to dmcity."})

  (fact "Target not enabled/available"
    (resolve-url sipoo-r application user :what-where-how?)
    => {:text "No such path for R: what-where-how?"}
    (resolve-url sipoo-r application user :YA)
    => {:text "Target YA not supported or not enabled for 753-R."})

  (fact "Permit type not enabled/supported"
    (resolve-url sipoo-r (assoc application :permitType "P") user :application)
    => {:text "Permit type P not supported or not enabled in 753-R."}
    (resolve-url sipoo-r (assoc application :permitType "W") user :application)
    => {:text "Permit type W not supported or not enabled in 753-R."})

  (let [converted (assoc application :facta-imported true)]
    (facts "Conversion"
      (facts "Conversion not enabled"
        (fact "Fallback to :application"
          (resolve-url sipoo-r application user :conversion)
          => {:url "http://example.net/dmcity/my-apps"})
        (resolve-url sipoo-r (assoc application :verdicts [{:kuntalupatunnus "factum"}])
                     user :application)
        => {:url "http://example.net/dmcity/my-apps"}
        (resolve-url sipoo-r converted user :application)
        => {:url "http://example.net/dmcity/my-apps"}
        (resolve-url sipoo-r converted user :review)
        => {:url "http://example.net/dmcity/my-revs"}
        (resolve-url sipoo-r converted user :state)
        => {:url "http://example.net/dmcity/HakemuksenTilapaivitys"})
      (let [c-r     {:url "http://example.net/dmcity/RakennuslupienKonversio"}
            c-state {:url "http://example.net/dmcity/HakemuksenTilapaivitysKonversio"}]
        (facts "Conversion enabled"
          (resolve-url sipoo-c application user :conversion) => c-r
          (resolve-url sipoo-c (assoc application :verdicts [{:kuntalupatunnus "factum"}])
                       user :application)
          => c-r
          (resolve-url sipoo-c converted user :verdict) => c-r
          (fact "Review path is the same for conversion"
            (resolve-url sipoo-c converted user :review)
            => {:url "http://example.net/dmcity/my-revs"})
          (resolve-url sipoo-c converted user :state) => c-state)))

    (fact "state"
      (resolve-url sipoo-r application user :state)
      => {:url "http://example.net/dmcity/HakemuksenTilapaivitys"})

    (fact "YA review"
      (resolve-url sipoo-ya (assoc application :permitType "YA") user :review)
      => {:text "No review support for YA in dmcity."})

    (fact "Regular path"
      (resolve-url sipoo-r application user :application)
      => {:url "http://example.net/dmcity/my-apps"})

    (facts "Force options"
      (facts "force-enabled?"
        (resolve-url sipoo-r (assoc application :permitType "P") user :application)
        => {:text "Permit type P not supported or not enabled in 753-R."}
        (resolve-url sipoo-r (assoc application :permitType "P") user :application
                     {:force-enabled? true})
        => {:url "http://example.net/dmcity/Poikkeamispaatos"}
        (let [converted (assoc application :facta-imported true)]
          (fact "Does not enable conversion targets"
            (resolve-url sipoo-r converted user :application
                         {:force-enabled? true})
            => {:url "http://example.net/dmcity/my-apps"})))
      (facts "force-path"
        (resolve-url sipoo-c application user :application)
        => {:url "http://example.net/dmcity/my-apps"}
        (resolve-url sipoo-c application user :application
                     {:force-path :conversion})
        => {:url "http://example.net/dmcity/RakennuslupienKonversio"}
        (resolve-url sipoo-c application user :state
                     {:force-path :conversion})
        => {:url "http://example.net/dmcity/HakemuksenTilapaivitysKonversio"}
        (facts "force-path: application"
          (resolve-url sipoo-c application user :application
                       {:force-path :application})
          => {:url "http://example.net/dmcity/my-apps"}
          (resolve-url sipoo-c application user :state
                       {:force-path :application})
          => {:url "http://example.net/dmcity/HakemuksenTilapaivitys"}
          (resolve-url sipoo-c converted user :application)
          => {:url "http://example.net/dmcity/RakennuslupienKonversio"}
          (resolve-url sipoo-c converted user :state)
          => {:url "http://example.net/dmcity/HakemuksenTilapaivitysKonversio"}
          (resolve-url sipoo-c converted user :application
                       {:force-path :application})
          => {:url "http://example.net/dmcity/my-apps"}
          (resolve-url sipoo-c converted user :state
                       {:force-path :application})
          => {:url "http://example.net/dmcity/HakemuksenTilapaivitys"}))
      (facts "both"
        (resolve-url sipoo-r (assoc application :permitType "P") user :application
                     {:force-enabled? true :force-path :conversion})
        => {:url "http://example.net/dmcity/Poikkeamispaatos"}
        (resolve-url sipoo-c application user :application
                     {:force-enabled? true :force-path :conversion})
        => {:url "http://example.net/dmcity/RakennuslupienKonversio"}
        (resolve-url sipoo-r application user :application
                     {:force-enabled? true :force-path :conversion})
        => {:url "http://example.net/dmcity/my-apps"}))))

(facts "enabled-for-app?"
  (fact "Regular application with matching permit type => true"
    (dm-cfg/enabled-for-app? sipoo-r application) => true)

  (fact "Inforequest => false even with matching permit type"
    (dm-cfg/enabled-for-app? sipoo-r (assoc application :infoRequest true)) => false)

  (fact "Non-matching permit type => false"
    (dm-cfg/enabled-for-app? sipoo-r (assoc application :permitType "YA")) => false)

  (fact "Inforequest with non-matching permit type => false"
    (dm-cfg/enabled-for-app? sipoo-r (assoc application :permitType "YA" :infoRequest true)) => false))
