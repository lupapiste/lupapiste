(ns lupapalvelu.ssokeys-api-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [clojure.test.check.generators :as gen]
            [schema.core :as sc]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :refer [apply-fixture]]
            [lupapalvelu.ssokeys :as sso]
            [mount.core :as mount]))

(mount/start #'mongo/connection)
(mongo/with-db test-db-name (apply-fixture "minimal"))

(apply-remote-minimal)

(def ensure-unique-ip-generator!
  (gen/such-that 
   #(->> (hash-map :ip %) (mongo/select-one :ssoKeys) (mongo/with-db test-db-name) nil?)
   ssg/ip-address))

(facts get-single-sign-on-keys
  (fact "admin is authorized to make the query"
    (query admin :get-single-sign-on-keys) => ok?)
  (fact "authorityAdmin is not authorized"
    (query sipoo :get-single-sign-on-keys) => unauthorized?)
  (fact "authority is not authorized"
    (query sonja :get-single-sign-on-keys) => unauthorized?)
  (fact "applicant is not authorized"
    (query pena :get-single-sign-on-keys) => unauthorized?))

(def CreateCommand  {:ip      ssc/IpAddress
                     :key     sso/UnencryptedKey
                     :comment (sc/maybe sc/Str)})

(facts add-single-sign-on-key

  (fact "new ip is added"
    (let [params (ssg/generate CreateCommand {ssc/IpAddress ensure-unique-ip-generator!})
          result (command admin :add-single-sign-on-key params) => ok?]
      (sc/check ssc/ObjectIdStr (:id result)) => nil))

  (fact "illegal ip"
    (let [params (-> (ssg/generate CreateCommand)
                     (assoc :ip "not.valid.ip"))]
      (command admin :add-single-sign-on-key params) => (partial expected-failure? :error.illegal-ip-address)))

  (fact "blank ip"
    (let [params (-> (ssg/generate CreateCommand)
                     (assoc :ip ""))]
      (command admin :add-single-sign-on-key params) => (partial expected-failure? :error.missing-parameters)))

  (fact "blank key"
    (let [params (-> (ssg/generate CreateCommand)
                     (assoc :key ""))]
      (command admin :add-single-sign-on-key params) => (partial expected-failure? :error.missing-parameters)))

  (fact "duplicate ip error"
    (let [params (ssg/generate CreateCommand {ssc/IpAddress ensure-unique-ip-generator!})]
      (command admin :add-single-sign-on-key params) => ok?
      (command admin :add-single-sign-on-key params) => (partial expected-failure? :error.ip-already-in-use))))

(def UpdateCommand  {:sso-id  ssc/ObjectIdStr
                     :ip      ssc/IpAddress
                     :key     sso/UnencryptedKey
                     :comment (ssc/min-length-string 1)})

(facts update-single-sign-on-key

  (fact "sso is updated"
    (mongo/with-db test-db-name
      (let [sso-id  (:id (mongo/select-one :ssoKeys {}))
            params  (-> (ssg/generate UpdateCommand {ssc/IpAddress ensure-unique-ip-generator!}) (assoc :sso-id sso-id))]
        (local-command admin :update-single-sign-on-key params) => ok?
        
        (select-keys (mongo/by-id :ssoKeys sso-id)
                     [:ip :id :comment])            =>  {:id sso-id 
                                                         :ip (:ip params) 
                                                         :comment (:comment params)})))
  
  (fact "illegal ip"
    (let [params (-> (ssg/generate UpdateCommand)
                     (assoc :ip "not.valid.ip"))]
      (command admin :update-single-sign-on-key params) => (partial expected-failure? :error.illegal-ip-address)))

  (fact "blank ip"
    (let [params (-> (ssg/generate UpdateCommand)
                     (assoc :ip ""))]
      (command admin :update-single-sign-on-key params) => (partial expected-failure? :error.missing-parameters)))

  (fact "blank key"
    (mongo/with-db test-db-name
      (let [sso-key (mongo/select-one :ssoKeys {})
            params  (-> (ssg/generate UpdateCommand {ssc/IpAddress ensure-unique-ip-generator!})
                        (assoc :key "")
                        (assoc :sso-id (:id sso-key)))]
        (local-command admin :update-single-sign-on-key params) => ok?
        (mongo/by-id :ssoKeys (:id sso-key)) => {:id (:id sso-key) 
                                                 :key (:key sso-key)
                                                 :crypto-iv (:crypto-iv sso-key)
                                                 :ip (:ip params) 
                                                 :comment (:comment params)}))))

(facts remove-single-sign-on-key
  (mongo/with-db test-db-name
    (let [sso-id  (:id (mongo/select-one :ssoKeys {}))]

      (fact "SSO key is removed"
        (local-command admin :remove-single-sign-on-key :sso-id sso-id) => ok?        
        (mongo/by-id :ssoKeys sso-id) => nil)

      (fact "SSO key cannot be removed again"
        (local-command admin :remove-single-sign-on-key :sso-id sso-id) => (partial expected-failure? :error.unknown-id)))))

