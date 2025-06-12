(ns lupapalvelu.fixture.bulletin-pate-orgs
  "Bulletin api test fixture."
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.system.util-components :as components]
            [monger.operators :refer :all]))

(def users
  [{:id        "123123123123123"
    :username  "test-hyvinkaa"
    :role      "authority"
    :enabled   true
    :email     "test@hyvinge.fi"
    :orgAuthz  {:106-BULLETIN-OP-TEST #{:authority :approver}}
    :firstName "Test"
    :lastName  "Hyvinkaa"
    :language  "fi"
    :phone     "03121991"
    :street    "Katuosoite 1 a 1"
    :zip       "33404"
    :city      "Hyvinkää"
    :private   {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuer"
                :apikey   "5056e6d3aa24a1c901e6b9er"}}])

(defn orgs
  [{:keys [http-port]}]
  (let [http-port (or http-port 8000)]
    ;; bulletin test org
    [{:id                                   "106-BULLETIN-OP-TEST"
      :name                                 (minimal/names {:fi "Hyvinkään rakennusvalvonta"
                                                            :sv "Hyvinkään rakennusvalvonta"})
      :scope                                [{:municipality            "106"
                                              :permitType              "R"
                                              :inforequest-enabled     true
                                              :new-application-enabled true
                                              :bulletins               {:enabled                          true
                                                                        :notification-email               "sonja.sibbo@sipoo.fi"
                                                                        :descriptions-from-backend-system false}
                                              :pate                    {:enabled true}}
                                             {:municipality            "106"
                                              :permitType              "P"
                                              :inforequest-enabled     true
                                              :new-application-enabled true
                                              :bulletins               {:enabled                          true
                                                                        :notification-email               "sonja.sibbo@sipoo.fi"
                                                                        :descriptions-from-backend-system false}
                                              :pate                    {:enabled true}}
                                             {:municipality            "106"
                                              :permitType              "KT"
                                              :inforequest-enabled     true
                                              :new-application-enabled true
                                              :bulletins               {:enabled                          true
                                                                        :notification-email               "sonja.sibbo@sipoo.fi"
                                                                        :descriptions-from-backend-system false}
                                              :pate                    {:enabled true}}
                                             {:municipality            "106"
                                              :permitType              "YA"
                                              :inforequest-enabled     true
                                              :new-application-enabled true
                                              :bulletins               {:enabled                          true
                                                                        :notification-email               "sonja.sibbo@sipoo.fi"
                                                                        :descriptions-from-backend-system false}
                                              :pate                    {:enabled true}}]
      :links                                [(minimal/link {:fi "Hyvinkää", :sv "Hyvinge"}
                                                           "http://hyvinkaa.fi")
                                             (minimal/link {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                                                           "http://hyvinkaa.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta")]
      :krysp                                {:R {:url     (minimal/local-krysp http-port)
                                                 :version "2.1.2"
                                                 :ftpUser "dev_hyvinkaa"}
                                             :P {:url     (minimal/local-krysp http-port)
                                                 :version "2.1.2"
                                                 :ftpUser "dev_hyvinkaa"}}
      :dmcity                               (merge {:url     (minimal/dummy-dmcity http-port)
                                                    :vault   "test-vault"
                                                    :targets {:P     {:enabled     true
                                                                      :application "hakemus-path"
                                                                      :review      "katselmus-path"}
                                                              :state {:enabled true}}}
                                                   (bs-sec/encode-credentials "test-user" "test-secret"))
      :handler-roles                        [{:id      "abba11111111111111111997"
                                              :name    {:fi "Käsittelijä"
                                                        :sv "Handläggare"
                                                        :en "Handler"}
                                              :general true}]
      :statementGivers                      [{:id    "516560d6c2e6f603beccc144"
                                              :text  "Paloviranomainen",
                                              :email "olli.uleaborg@ouka.fi",
                                              :name  "Olli Uleåborg"}]
      :selected-operations                  (mapv first (filter (fn [[_ v]] (#{"R" "YA" "KT" "P"} (name (:permit-type v)))) operations/operations))
      :permanent-archive-enabled            false
      :digitizer-tools-enabled              false
      :automatic-review-fetch-enabled       true
      :automatic-ok-for-attachments-enabled true
      :multiple-operations-supported        true}]))

(defmethod fixture/fixture :bulletin-pate-orgs
  [_ system]
  (mongo/clear!)
  (mongo/insert-batch :users users)
  (mongo/insert-batch :organizations (orgs {:http-port (components/system->http-port system)})))
