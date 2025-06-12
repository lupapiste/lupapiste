(ns lupapalvelu.ryhti-stest
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.ryhti.api.ryhti-api :as ryhti-api]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.env :as env]
            [schema.core :as sc]))

(def org-id "186-R")
(def municipality "186")

(deftest ryhti-api
  (let [client-secret (env/value :ryhti :api :client-secret)]
    (with-redefs [ryhti-api/municipality-config (constantly {:municipality  municipality
                                                             :client-id     "ryhti-test-järvenpää"
                                                             :client-secret client-secret})
                  ;; Don't cache tokens in Mongo, just fetch a new token
                  ryhti-api/get-token!          (fn [org-id municipality]
                                                  (:token (ryhti-api/authenticate! org-id municipality)))]
      (testing "Permanent building permit id"
        ;; Ryhti doesn't seem to be too picky about what the permit-id is
        ;; so we can use the current unix timestamp to always have a unique id
        (let [permit-id (str (now))]
          (testing "Can create new building permit id"
            (is (->> (ryhti-api/get-permanent-permit-id! org-id municipality permit-id :building)
                     (sc/validate {:response                  (sc/eq "Pysyvä lupatunnus luotu.")
                                   :created                   (sc/eq true)
                                   :permanentPermitIdentifier sc/Str}))))
          (testing "Can retrive previously created building permit id"
            (is (->> (ryhti-api/get-permanent-permit-id! org-id municipality permit-id :building)
                     (sc/validate {:response                  (sc/eq "Pysyvä lupatunnus on jo annettu.")
                                   :created                   (sc/eq false)
                                   :permanentPermitIdentifier sc/Str})))))))))
