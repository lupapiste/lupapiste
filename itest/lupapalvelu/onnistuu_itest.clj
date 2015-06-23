(ns lupapalvelu.onnistuu-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :as u]
            [sade.http :as http]
            [lupapalvelu.onnistuu.process :as p]
            [sade.crypt :as crypt]
            [sade.env :as env]
            [cheshire.core :as json]
            [lupapalvelu.itest-util :refer [->cookie-store server-address decode-response
                                            admin query command
                                            last-email apply-remote-minimal
                                            ok? fail? http200? http302?
                                            ]]))

(defn get-process [process-id]
  (:process (u/query u/pena :find-sign-process :processId process-id)))

(defn get-process-status [process-id]
  (:status (get-process process-id)))

(defn init-sign []
  (-> (u/command u/pena :init-sign
                 :company {:name  "company-name"
                           :y     "2341528-4"
                           :accountType "account5"
                           :address1 "katu"
                           :zip "33100"
                           :po "Tampere"
                           :customAccountLimit nil}
                 :signer {:firstName   "First"
                          :lastName    "Last"
                          :email       "a@b.c"
                          :personId    "131052-308T"}
                 :lang "fi")
      :process-id
      get-process))

(defn init-sign-existing-user []
  (-> (u/command u/pena :init-sign
                 :company {:name  "company-name"
                           :y     "2341528-4"
                           :accountType "account5"
                           :address1 "katu"
                           :zip "33100"
                           :po "Tampere"
                           :customAccountLimit nil}
                 :signer {:firstName   "Pena"
                          :lastName    "Panaani"
                          :email       "in@va.lid"
                          :currentUser "777777777777777777000000"
                          :personId    "131052-308T"}
                 :lang "fi")
      :process-id
      get-process))

(fact "init-sign"
  (init-sign) => (contains {:stamp   #"[a-zA-Z0-9]{40}"
                            :company {:name "company-name"
                                      :y    "2341528-4"
                                      :accountType "account5"
                                      :address1 "katu"
                                      :zip "33100"
                                      :po "Tampere"
                                      :customAccountLimit nil}
                            :signer {:firstName   "First"
                                     :lastName    "Last"
                                     :email        "a@b.c"
                                     :personId    "131052-308T"}
                            :status  "created"
                            :lang    "fi"}))

(fact "cancel"
  (let [process-id (:id (init-sign))]
    (u/command u/pena :cancel-sign :processId process-id)
    (get-process-status process-id) => "cancelled"))

(fact "cancel unknown"
  (u/command u/pena :cancel-sign :processId "fooo") => {:ok false, :text "error.unknown"})

(fact "Fetch document"
    (let [process-id (:id (init-sign))]
      (http/get (str (u/server-address) "/api/sign/document/" process-id) :throw-exceptions false) => (contains {:status 200})
      (get-process-status process-id) => "started"))

(fact "Fetch document for unknown process"
  (http/get (str (u/server-address) "/api/sign/document/" "foozaa") :throw-exceptions false) => (contains {:status 404}))

(fact "Can't fetch document on cancelled process"
  (let [process-id (:id (init-sign))]
    (u/command u/pena :cancel-sign :processId process-id)
    (http/get (str (u/server-address) "/api/sign/document/" process-id) :throw-exceptions false) => (contains {:status 400})))

(fact "init-sign-for-existing-user"
  (init-sign-existing-user) => (contains {:stamp   #"[a-zA-Z0-9]{40}"
                                             :company {:name        "company-name"
                                                       :y           "2341528-4"
                                                       :accountType "account5"
                                                       :address1 "katu"
                                                       :zip "33100"
                                                       :po "Tampere"
                                                       :customAccountLimit nil}
                                             :signer  {:firstName   "Pena"
                                                       :lastName    "Panaani"
                                                       :email       "pena@example.com"
                                                       :currentUser "777777777777777777000020"
                                                       :personId    "131052-308T"}
                                             :status  "created"
                                             :lang    "fi"}))

(fact "Fetch document for existing user"
  (let [process-id (:id (init-sign-existing-user))]
    (http/get (str (u/server-address) "/api/sign/document/" process-id) :throw-exceptions false) => (contains {:status 200})
    (get-process-status process-id) => "started"))

(fact "Approve signin process"
  (let [process-id (:id (init-sign))
        _ (http/get (str (u/server-address) "/api/sign/document/" process-id) :throw-exceptions false) => (contains {:status 200})
        process (get-process process-id)
        stamp (:stamp process)
        crypto-key (-> (env/get-config) :onnistuu :crypto-key (crypt/str->bytes) (crypt/base64-decode))
        crypto-iv (crypt/make-iv)
        hetu (get-in process [:signer :personId])
        uuid (str (java.util.UUID/randomUUID))
        data (->> {:stamp      stamp
                   :document   (str "/dev/dummy-onnistuu/doc/" stamp)
                   :cancel     "cancel-url-not-used"
                   :signatures [{:type       :person
                                 :identifier hetu
                                 :name       "foobar"
                                 :timestamp  "foobar"
                                 :uuid       uuid}]}
                  (json/encode)
                  (crypt/str->bytes)
                  (crypt/encrypt crypto-key crypto-iv)
                  (crypt/base64-encode)
                  (crypt/bytes->str)
                  (crypt/url-encode))
        iv (-> crypto-iv (crypt/base64-encode) (crypt/bytes->str) (crypt/url-encode))
        store (atom {})
        params {:cookie-store (->cookie-store store)
                :throw-exceptions false}
        response   (http/get (str (u/server-address) "/api/sign/success/" process-id "?data=" data "&iv=" iv) params)]
    response => http200?
    (get-process-status process-id) => "done"))

(fact "Fail signin process"
  (let [process-id (:id (init-sign))
        _ (http/get (str (u/server-address) "/api/sign/document/" process-id) :throw-exceptions false) => (contains {:status 200})
        store (atom {})
        params {:cookie-store (->cookie-store store)
                :throw-exceptions false}
        response   (http/get (str (u/server-address) "/api/sign/fail/" process-id) params)]
    response => http200?
    (get-process-status process-id) => "fail"))
