(ns lupapalvelu.onnistuu-itest
  (:require [lupacrypto.core :as crypt]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.json :as json]
            [midje.sweet :refer :all]
            [sade.env :as env]
            [sade.util :as util]))

(apply-remote-minimal)
; Reset emails
(last-email)

(defn get-process [process-id]
  (:process (query pena :find-sign-process :processId process-id)))

(defn get-process-status [process-id]
  (:status (get-process process-id)))

(def company-map {:name  "company-name"
                  :y     "2341528-4"
                  :accountType "account5"
                  :billingType "monthly"
                  :address1 "katu"
                  :zip "33100"
                  :po "Tampere"
                  :customAccountLimit nil})

(defn init-sign
  ([email person-id]
   (-> (command pena :init-sign
                :company company-map
                :signer {:firstName "First"
                         :lastName  "Last"
                         :email     email
                         :personId  person-id}
                :lang "fi")
       :process-id
       get-process))
  ([] (init-sign "a@b.c" "131052-308T")))

(defn init-sign-existing-user []
  (-> (command pena :init-sign
               :company company-map
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
                            :company company-map
                            :signer {:firstName   "First"
                                     :lastName    "Last"
                                     :email       "a@b.c"
                                     :personId    "131052-308T"}
                            :status  "created"
                            :lang    "fi"}))

(fact "cancel"
  (let [process-id (:id (init-sign))]
    (command pena :cancel-sign :processId process-id) => ok?
    (get-process-status process-id) => "cancelled"))

(fact "cancel unknown"
  (command pena :cancel-sign :processId "fooo") => fail?)

(fact "Fetch document"
  (let [process-id (:id (init-sign))]
    (fetch-document process-id) => http200?
    (get-process-status process-id) => "started"
    (fact "Consequent call retuns also"
      (fetch-document process-id) => http200?)))

(fact "Fetch document for unknown process"
  (fetch-document "foozaa") => http404?)

(fact "Can't fetch document on cancelled process"
  (let [process-id (:id (init-sign))]
    (command pena :cancel-sign :processId process-id)
    (fetch-document process-id) => http400?))

(fact "Start and cancel signing (LPK-946)"
  (let [process-id (:id (init-sign))]
    (fetch-document process-id) => http200?
    (command pena :cancel-sign :processId process-id) => ok?
    (get-process-status process-id) => "cancelled"
    (fetch-document process-id) => http400?))

(fact "init-sign-for-existing-user"
      (init-sign-existing-user) => (contains {:stamp   #"[a-zA-Z0-9]{40}"
                                              :company company-map
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
            _              (fetch-document process-id) => http200?
            process        (get-process process-id)
            stamp          (:stamp process)
            crypto-key     (-> (env/get-config) :onnistuu :crypto-key (crypt/str->bytes) (crypt/base64-decode))
            crypto-iv      (crypt/make-iv)
            hetu       (get-in process [:signer :personId])
            uuid       (str (java.util.UUID/randomUUID))
            data           (->> {:stamp      stamp
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
            iv             (-> crypto-iv (crypt/base64-encode) (crypt/bytes->str) (crypt/url-encode))
            store          (atom {})
            params         {:cookie-store     (->cookie-store store)
                            :throw-exceptions false}
            response       (http-get (str (server-address) "/api/sign/success/" process-id "?data=" data "&iv=" iv) params)]
    response => http200?
    (get-process-status process-id) => "done"))


(fact "a@b.c has not yet been created"
  (query admin :user-by-email :email "a@b.c")
  => (contains {:user nil}))

(let [url   (->> (sent-emails) first :body :plain (re-find #"http[^\s]+"))
      token (re-find #"[^/]+$" url)]
  (fact "Pena confirms company registration (for a@b.c)"
    (http-get url {}) => http200?)
  (fact "Set the password for a@b.c"
    (http-token-call token {:password "zheshiwodemima"})
    => http200?))

(fact "a@b.c personId details are correct"
  (:user (query admin :user-by-email :email "a@b.c"))
  => (contains {:personId       "131052-308T"
                :personIdSource "identification-service"}))

(let [id (->> (query pena :companies) :companies (util/find-by-key :name "company-name") :id)]
  (fact "Kaino cannot edit the created company info"
        (command kaino :company-update :company id :updates {:po "Beijing"}) => fail?)
  (fact "Kaino can edit Solita company info"
        (command kaino :company-update :company "solita" :updates {:po "Beijing"}) => ok?))

(fact "Fail signing process"
  (let [process-id (:id (init-sign "fail@example.com" "050349-982Y"))
        _ (fetch-document process-id) => http200?
        store (atom {})
        params {:cookie-store (->cookie-store store)
                :query-params {:onnistuu_error 60 :onnistuu_message "fail"}
                :throw-exceptions false}
        response   (http-get (str (server-address) "/api/sign/fail/" process-id) params)]
    response => http200?
    (get-process-status process-id) => "fail"))

(facts "Email in use checks"
       (letfn [(err [msg] (partial expected-failure? msg))]
         (fact "Pena cannot register company as Teppo"
               (command pena :init-sign :company company-map :lang :fi :signer {:email "teppo@example.com"})
               => (err :email-in-use))
         (fact "Sonja is authority and cannot register company"
               (command sonja :init-sign :company company-map :lang :fi :signer {:email "sonja.sibbo@sipoo.fi"})
               => (err :error.not-applicant))
         (fact "Kaino cannot register another company"
               (command kaino :init-sign :company company-map :lang :fi :signer {:email "kaino@solita.fi"})
               => (err :error.already-in-company))))
