(ns lupapalvelu.conversion.proxy-i-test
  "The end-to-end conversion proxy functionality can be reliably tested only in the true,
  non-local testing environment, where the client and the proxy are two separate
  instances."
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion-test-util :refer [with-proxy]]
            [lupapalvelu.conversion.core :as conv]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.proxy :as conv-p]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.strings :as ss]))

(defn test-proxy
  []
  {:host   (server-address)
   :role   "client"
   :secret "helloworld"})

(def backend-id "14-0241-R 3")
(def organization-id "186-R")

(defn xml? [m]
  (-> (:xml m) (ss/includes? backend-id)))

(defn error? [re m]
  (boolean (some->> (:error m) (re-find re))))

(defn toggle-proxy [enabled?]
  (:ok (command admin :set-organization-boolean-attribute
                :attribute "conversion-proxy-enabled"
                :enabled enabled?
                :organizationId organization-id)))

(def encode-ts #'conv-p/encode-timestamp)

(deftest fetch-xml-via-proxy-test
  (with-redefs [conv-p/http-get http-get]
    (with-test-system (-> {:fixture "minimal"}
                          test-system/with-remote-components)
      (testing "Local and remote"
        (is (error? #"Proxy not configured"
                    (with-proxy
                      nil
                      (conv-p/fetch-xml-via-proxy organization-id
                                                  backend-id))))
        (is (error? #"400 Bad request"
                    (with-proxy
                      (test-proxy)
                      (conv-p/fetch-xml-via-proxy "nowhere"
                                                  backend-id))))

        (is (error? #"400 .+Non-blank string"
                    (with-proxy
                      (test-proxy)
                      (conv-p/fetch-xml-via-proxy "  "
                                                  backend-id))))

        (is (error? #"400 .+Backend-id"
                    (with-proxy
                      (test-proxy)
                      (conv-p/fetch-xml-via-proxy organization-id
                                                  "nowhere"))))

        (is (error? #"400 Bad request"
                    (with-proxy
                      (test-proxy)
                      (conv-p/fetch-xml-via-proxy organization-id
                                                  backend-id))))

        (is (true? (toggle-proxy true)))

        (is (xml? (with-proxy
                    (test-proxy)
                    (conv-p/fetch-xml-via-proxy organization-id
                                                backend-id))))

        (is (true? (toggle-proxy false)))

        (is (error? #"400 Bad request"
                    (with-proxy
                      (test-proxy)
                      (conv-p/fetch-xml-via-proxy organization-id
                                                  backend-id)))))
      (testing "Remote only"
        (let [bad-ts (with-proxy (assoc (test-proxy) :secret "wrong!")
                       (encode-ts))]
          (with-redefs [conv-p/encode-timestamp (constantly bad-ts)]
            (is (error? #"401 Unauthorized"
                        (with-proxy
                          (test-proxy)
                          (conv-p/fetch-xml-via-proxy organization-id
                                                      backend-id))))))))))

(def conversion--store-kuntagml #'conv/conversion--store-kuntagml)

(defn store-kuntagml []
  (mongo/remove-many :conversion {:organization organization-id
                                  :backend-id   backend-id})
  (conversion--store-kuntagml {:organization-id organization-id
                               :targets         [{:id backend-id}]})
  (let [{:keys [conversion-error
                kuntagml]} (conv-doc/document organization-id backend-id)]
    (cond
      conversion-error :error
      (:xml kuntagml)  :xml)))

(deftest store-via-proxy-test
  ;; NOTE: Previously "local" and "remote" had different
  ;; databases, so clearing the `:krysp` locally did not affect
  ;; the remote. However now we only have a single DB so this test
  ;; doesn't make much sense anymore.
  (with-redefs [conv-p/http-get http-get]
    (with-test-system (-> {:fixture "minimal"}
                          test-system/with-remote-components)
      (testing "With proxy. Remote and local."
        (with-proxy (test-proxy)
          (is (= :error (store-kuntagml)))
          (is (toggle-proxy true))
          (is (= :xml (store-kuntagml)))))

      (mongo/update-by-id :organizations organization-id {$unset {:krysp true}})

      (testing "No proxy. Remote only."
        (with-proxy nil
          (is (= :error (store-kuntagml))))))))
