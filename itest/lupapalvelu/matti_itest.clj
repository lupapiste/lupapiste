(ns lupapalvelu.matti-itest
  "Matti admin and batchrun itests."
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.matti :as matti]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.organization-api :refer [decode-state-change-conf]]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.date :as date]
            [schema.core :as sc]))

(defonce db-name (str "test_matti_" (sade.core/now)))

(facts parse-params
  (fact "ids"
    (matti/parse-params {:organizationId "foo"
                         :ids            "
 id1

id2   id3

"
                         :validateXml false})
    => {:org-id    "foo"
        :ids       ["id1" "id2" "id3"]
        :send-verdicts? nil
        :send-state-changes? nil
        :validate? false})
  (fact "dates"
    (let [{:keys [start-date
                  end-date]} (matti/parse-params {:organizationId "foo"
                                                  :startDate      "18.02.2019"
                                                  :endDate        "20.02.2019"
                                                  :validateXml    true})]
      (number? start-date) => true
      (number? end-date) => true
      (date/iso-datetime start-date) => "2019-02-18T00:00:00+02:00"
      (date/iso-datetime end-date) => "2019-02-20T23:59:59+02:00")))

(defn vantaa-config []
  (:config (local-query admin :matti-config :organizationId "092-R")))

(defn vantaa-valid []
  (fact "Vantaa organization is valid"
    (sc/check org/Organization (mongo/by-id :organizations "092-R"))
    => nil))

(mount/start #'mongo/connection)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")

  (vantaa-valid)
  (facts "Matti configuration"
    (fact "Only  matti or dmcity backend systems supported"
      (local-query admin :matti-config :organizationId "092-R")
      => (err :error.unsupported-organization)
      (local-command admin :update-organization-backend-systems
                     :org-id "092-R"
                     :backend-systems {:R "matti"}) => ok?)
    (fact "Initially empty config"
      (vantaa-config)
      => {:url         nil
          :username    nil
          :password    nil
          :vault       nil
          :buildingUrl nil
          :enabled     {:R           nil
                        :P           nil
                        :stateChange nil}})
    (facts "Functionalities can be always enabled"
      (fact "But only for Vantaa"
        (local-command admin :toggle-matti-functionality
                       :organizationId "753-R"
                       :function "R"
                       :enabled true)
        => fail?)
      (fact "Parameter checks"
        (local-command admin :toggle-matti-functionality
                       :organizationId "092-R"
                       :function "bad"
                       :enabled true)
        => fail?
        (local-command admin :toggle-matti-functionality
                       :organizationId "092-R"
                       :function "R")
        => fail?
        (local-command admin :toggle-matti-functionality
                       :organizationId "092-R"
                       :enabled true)
        => fail?
        (local-command admin :toggle-matti-functionality
                       :organizationId "092-R"
                       :function "stateChange"
                       :enabled "bad")
        => fail?)
      (fact "Enable stateChange"
        (local-command admin :toggle-matti-functionality
                       :organizationId "092-R"
                       :function "stateChange"
                       :enabled true)
        => ok?
        (vantaa-config)
        => {:url         nil
            :username    nil
            :password    nil
            :vault       nil
            :buildingUrl nil
            :enabled     {:R           nil
                          :P           nil
                          :stateChange true}}
        (mongo/by-id :organizations "092-R" {:state-change-msg-enabled 1})
        => (contains {:state-change-msg-enabled true}))
      (fact "Disable stateChange"
        (local-command admin :toggle-matti-functionality
                       :organizationId "092-R"
                       :function "stateChange"
                       :enabled false)
        => ok?
        (vantaa-config)
        => {:url         nil
            :username    nil
            :password    nil
            :vault       nil
            :buildingUrl nil
            :enabled     {:R           nil
                          :P           nil
                          :stateChange false}}
        (mongo/by-id :organizations "092-R" {:state-change-msg-enabled 1})
        => (contains {:state-change-msg-enabled false}))
      (fact "Enable R HTTP"
        (local-command admin :toggle-matti-functionality
                       :organizationId "092-R"
                       :function "R"
                       :enabled true)
        => ok?
        (vantaa-config)
        => {:url         nil
            :username    nil
            :password    nil
            :vault       nil
            :buildingUrl nil
            :enabled     {:R           true
                          :P           nil
                          :stateChange false}}
        (mongo/by-id :organizations "092-R" {:krysp 1})
        => (contains {:krysp (contains {:R (contains {:http {:enabled true}})})}))
      (fact "Disable R and enable P HTTP"
        (local-command admin :toggle-matti-functionality
                       :organizationId "092-R"
                       :function "R"
                       :enabled false)
        => ok?
        (local-command admin :toggle-matti-functionality
                       :organizationId "092-R"
                       :function "P"
                       :enabled true)
        => ok?
        (vantaa-config)
        => {:url         nil
            :username    nil
            :password    nil
            :vault       nil
            :buildingUrl nil
            :enabled     {:R           false
                          :P           true
                          :stateChange false}}
        (mongo/by-id :organizations "092-R" {:krysp 1})
        => (contains {:krysp (contains {:R (contains {:http {:enabled false}})
                                        :P (contains {:http {:enabled true}})})})))
    (facts "Update Matti configuration"
      (fact "Successful update"
        (local-command admin :update-matti-config
                       :organizationId "092-R"
                       :url "  http://foo.bar "
                       :username " hello "
                       :password " world  "
                       :vault "   fortress  "
                       :buildingUrl "  http://build.com/here   ")
        => ok?
        (vantaa-valid)
        (fact "Config changed"
          (vantaa-config)
          => {:url         "http://foo.bar"
              :username    "hello"
              :password    "world"
              :vault       "fortress"
              :buildingUrl "http://build.com/here"
              :enabled     {:R           false
                            :P           true
                            :stateChange false}})
        (let [vantaa (mongo/by-id :organizations "092-R")]
          (fact "State change endpoint check"
            (decode-state-change-conf vantaa)
            => (contains {:state-change-endpoint (just {:auth-type           "other"
                                                        :basic-auth-username ""
                                                        :basic-auth-password ""
                                                        :crypto-iv-s         string?
                                                        :header-parameters   (just [{:name  "x-username"
                                                                                     :value "hello"}
                                                                                    {:name  "x-password"
                                                                                     :value "world"}
                                                                                    {:name  "x-vault"
                                                                                     :value "fortress"}]
                                                                                   :in-any-order)
                                                        :url                 "http://foo.bar/HakemuksenTilapaivitys"})}))
          (fact "Vantaa krysp definitions"
            (let [http {:crypto-iv string?
                        :partner   "matti"
                        :headers   [{:key   "x-vault"
                                     :value "fortress"}]
                        :password  #(not= % "world") ;; encrypted
                        :username  "hello"
                        :url       "http://foo.bar"}]
              (:krysp vantaa)
              => (contains
                   {:R (contains
                         {:buildings
                          (contains {:url "http://build.com/here"})
                          :http (contains (assoc http
                                                 :enabled false
                                                 :path {:verdict             "Rakennusvalvontapaatos"
                                                        :application         "Rakennusvalvontapaatos"
                                                        :review              "Katselmustieto"
                                                        :building-extinction "Rakennusvalvontapaatos"
                                                        :conversion          "RakennuslupienKonversio"}))})
                    :P (contains {:buildings (contains {:url "http://build.com/here"})
                                  :http      (contains (assoc http
                                                              :enabled true
                                                              :path {:verdict     "Poikkeamispaatos"
                                                                     :application "Poikkeamispaatos"
                                                                     :review      "Katselmustieto"}))})})))
          (facts "Shared krysp credentials"
            (let [credentials (-> vantaa :krysp :R :buildings (select-keys [:username :password :crypto-iv]))]
              (fact "Credentials OK"
                (org/decode-credentials (:password credentials) (:crypto-iv credentials))
                => "world")
              (fact "no credential in krysp permit type level"
                (:krysp vantaa)
                =not=> (contains {:R (contains credentials)
                                  :P (contains credentials)}))
              (fact "http"
                (-> vantaa :krysp :R :http)
                => (contains credentials)
                (-> vantaa :krysp :P :http)
                => (contains credentials))
              (fact "buildings"
                (-> vantaa :krysp :R :buildings)
                => (contains credentials)
                (-> vantaa :krysp :P :buildings)
                => (contains credentials))))))

      (facts "Special cases"
        (let [params {:organizationId "092-R"
                      :url            "  http://foo.bar "
                      :username       " hello "
                      :password       " world  "
                      :vault          "   fortress  "
                      :buildingUrl    "  http://build.com/here   "}
              cmd-fn (fn [m] (apply (partial local-command admin :update-matti-config)
                                    (flatten (seq m))))]
          (fact "Vantaa only"
            (cmd-fn (assoc params :organizationId "753-R")) => fail?)
          (fact "State change url forward slash"
            (cmd-fn (assoc params :url "http://end.in.slash/")) => ok?
            (-> (mongo/by-id :organizations "092-R" {:state-change-endpoint 1})
                :state-change-endpoint
                :url)
            => "http://end.in.slash/HakemuksenTilapaivitys")
          (fact "Every param is mandatory and cannot be blank"
            (doseq [k (keys params)]
              (cmd-fn (dissoc params k)) => fail?
              (cmd-fn (assoc params k "  ")) => fail?))))

      (fact "Final checks"
        (vantaa-valid)
        (mongo/by-id :organizations "092-R" [:krysp.R.http.auth-type
                                             :krysp.P.http.auth-type])
        => (contains {:krysp (just {:R {:http {:auth-type "x-header"}}
                                    :P {:http {:auth-type "x-header"}}})})))))
