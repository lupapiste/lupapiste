(ns lupapalvelu.onnistuu-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.onnistuu.process :as p]
            [sade.crypt :as crypt]
            [sade.env :as env]
            [cheshire.core :as json]
            [lupapalvelu.itest-util :refer [->cookie-store server-address decode-response
                                            admin query command http-get
                                            last-email apply-remote-minimal
                                            ok? fail? http200? http302? http400? http404?] :as u]))

(apply-remote-minimal)

(defn get-process [process-id]
  (:process (query u/pena :find-sign-process :processId process-id)))

(defn get-process-status [process-id]
  (:status (get-process process-id)))

(defn init-sign []
  (-> (command u/pena :init-sign
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
  (-> (command u/pena :init-sign
                 :company {:name  "company-name"
                           :y     "2341528-4"
                           :accountType "account5"
                           :address1 "katu"
                           :zip "33100"
                           :po "Tampere"
                           :customAccountLimit nil}
                 :signer {:firstName   ""
                          :lastName    ""
                          :email       "pena@example.com"
                          :personId    nil}
                 :lang "fi")
      :process-id
      get-process))

(defn fetch-document [process-id]
  (http-get (str (server-address) "/api/sign/document/" process-id) {:throw-exceptions false}))

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
    (command u/pena :cancel-sign :processId process-id)
    (get-process-status process-id) => "cancelled"))

(fact "cancel unknown"
  (command u/pena :cancel-sign :processId "fooo") => fail?)

(fact "Fetch document"
  (let [process-id (:id (init-sign))]
    (fetch-document process-id) => http200?
    (get-process-status process-id) => "started"))

(fact "Fetch document for unknown process"
  (fetch-document "foozaa") => http404?)

(fact "Can't fetch document on cancelled process"
  (let [process-id (:id (init-sign))]
    (command u/pena :cancel-sign :processId process-id)
    (fetch-document process-id) => http400?))

(fact "Start and cancel signing (LPK-946)"
  (let [process-id (:id (init-sign))]
    (fetch-document process-id) => http200?
    (command u/pena :cancel-sign :processId process-id) => ok?
    (get-process-status process-id) => "cancelled"
    (fetch-document process-id) => http404?))

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
                                                       :personId    "010203-040A"}
                                             :status  "created"
                                             :lang    "fi"}))

(fact "Fetch document for existing user"
  (let [process-id (:id (init-sign-existing-user))]
    (fetch-document process-id) => http200?
    (get-process-status process-id) => "started"))

(fact "Approve signing process"
  (let [process-id (:id (init-sign))
        _ (fetch-document process-id) => http200?
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
                  (crypt/encrypt crypto-key crypto-iv :rijndael)
                  (crypt/base64-encode)
                  (crypt/bytes->str)
                  (crypt/url-encode))
        iv (-> crypto-iv (crypt/base64-encode) (crypt/bytes->str) (crypt/url-encode))
        store (atom {})
        params {:cookie-store (->cookie-store store)
                :throw-exceptions false}
        response   (http-get (str (u/server-address) "/api/sign/success/" process-id "?data=" data "&iv=" iv) params)]
    response => http200?
    (get-process-status process-id) => "done"))

(fact "Fail signing process"
  (let [process-id (:id (init-sign))
        _ (fetch-document process-id) => http200?
        store (atom {})
        params {:cookie-store (->cookie-store store)
                :throw-exceptions false}
        response   (http-get (str (u/server-address) "/api/sign/fail/" process-id) params)]
    response => http200?
    (get-process-status process-id) => "fail"))
