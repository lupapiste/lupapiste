(ns lupapalvelu.ssokey-test
  (:require [midje.util :refer [testable-privates]]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.validators :as v]
            [schema.core :as sc]
            [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [lupapalvelu.test-util :refer [assert-assertion-error assert-validation-error]]
            [lupapalvelu.ssokeys :refer :all]))

(testable-privates lupapalvelu.ssokeys encode-key)

(defspec encode-secret-key
  (prop/for-all [secret-key gen/string]
                (->> (encode-key secret-key)
                     ((every-pred (comp (partial not= secret-key) :key)
                                  (comp not ss/blank? :key)
                                  (comp not ss/blank? :crypto-iv))))))

(defspec create-sso
  (prop/for-all [ip      ssg/ip-address
                 key     (ssg/generator UnencryptedKey)
                 comment (gen/one-of [ssg/blank-string gen/string])]
                (create-sso-key ip key comment)))

(defspec create-sso-with-failing-ip 
  (prop/for-all [ip      (gen/such-that (comp not v/ip-address?) ssg/not-blank-string)
                 key     (gen/such-that (comp not ss/blank?) (ssg/generator UnencryptedKey))
                 comment (gen/one-of [ssg/blank-string gen/string])]
                (assert-validation-error [:ip] (create-sso-key ip key comment))))

(defspec create-sso-with-empty-ip
  (prop/for-all [ip      ssg/blank-string
                 key     (gen/such-that (comp not ss/blank?) (ssg/generator UnencryptedKey))
                 comment (gen/one-of [ssg/blank-string gen/string])]
                (assert-assertion-error :ip (create-sso-key ip key comment))))

(defspec create-sso-with-empty-key
  (prop/for-all [ip      ssg/ip-address
                 key     ssg/blank-string
                 comment (gen/one-of [ssg/blank-string gen/string])]
                (assert-assertion-error :secret-key (create-sso-key ip key comment))))

(defspec update-sso-ip 50
  (prop/for-all [{secret-key :key comment :comment :as sso-key} (ssg/generator SsoKey {sc/Str ssg/not-blank-string})
                 ip  ssg/ip-address]
                (let [{new-ip :ip} (update-sso-key sso-key ip secret-key comment)]
                  (is (= ip new-ip) "IP did not update properly"))))

(defspec update-sso-secret-key 50
  (prop/for-all [{ip :ip old-key :key old-iv :crypto-iv comment :comment :as sso-key} (ssg/generator SsoKey {sc/Str ssg/not-blank-string})
                 secret-key (gen/one-of [ssg/blank-string (ssg/generator UnencryptedKey)])]
                (let [{encrypted-key :key new-iv :crypto-iv} (update-sso-key sso-key ip secret-key comment)]                  
                  (if (ss/blank? secret-key)
                    (do (is (= old-key encrypted-key) "Unexpected key update")
                        (is (= old-iv new-iv)         "Unexpected crypto-iv update"))
                    (do (is (not= secret-key encrypted-key) "Secret key not encrypted") 
                        (is (not= old-key encrypted-key) "Key did not update")
                        (is (not= old-iv new-iv) "Crypto-iv did not update"))))))

(defspec update-sso-comment 50
  (prop/for-all [{ip :ip secret-key :key :as sso-key} (ssg/generator SsoKey {sc/Str ssg/not-blank-string})
                 comment    (gen/one-of [ssg/blank-string gen/string])]
                (let [{new-comment :comment :as new-sso-key} (update-sso-key sso-key ip secret-key comment)]
                  (if (ss/blank? comment)
                    (is (not (contains? new-sso-key :comment)) "Comment did not unset")
                    (is (= comment new-comment) "Comment did not update properly")))))

(def illegal-id (gen/such-that (comp not nil? (sc/checker ssc/ObjectIdStr)) gen/string-alphanumeric))

(defspec update-sso-with-illegal-id 50
  (prop/for-all [{ip :ip :as sso-key} (ssg/generator SsoKey {sc/Str ssg/not-blank-string})
                 id  illegal-id]
                (assert-validation-error [:id] (update-sso-key (assoc sso-key :id id) ip "" ""))))

(def illegal-ip (gen/such-that (comp not v/ip-address?) gen/string-alphanumeric))

(defspec update-sso-with-empty-ip 50
  (prop/for-all [sso-key (ssg/generator SsoKey {sc/Str ssg/not-blank-string})
                 ip      illegal-ip]
                (assert-validation-error [:ip] (update-sso-key sso-key ip "" ""))))
