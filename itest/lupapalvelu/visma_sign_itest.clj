(ns lupapalvelu.visma-sign-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(defn get-process [process-id]
  (:process (query pena :dummy-find-sign-process :processId process-id)))

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
                     :company (ss/stringwalk #(str " " % " ") company-map)
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
  (http-get (str (server-address) "/api/raw/download-contract?processId=" process-id) {:throw-exceptions false}))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  ;; Reset emails
  (last-email)

  (fact "init-sign"
    (init-sign) => (contains {:stamp   #"[a-zA-Z0-9]{40}"
                              :company company-map
                              :signer  {:firstName "First"
                                        :lastName  "Last"
                                        :email     "a@b.c"
                                        :personId  "131052-308T"}
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
      (get-process-status process-id) => "new"
      (fact "Consequent call retuns also"
        (fetch-document process-id) => http200?)))

  (fact "Fetch document for unknown process"
    (fetch-document "foozaa") => http404?)

  (fact "Can't fetch document on cancelled process"
    (let [process-id (:id (init-sign))]
      (command pena :cancel-sign :processId process-id)
      (fetch-document process-id) => (contains {:body "error.process-cancelled" :status 400 })))

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
      (get-process-status process-id) => "new"))

  (defn valid-invitation-url? [url]
    (let [invitation-regex #".*?/fi/invitation/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{8}$"]
      (boolean (re-matches invitation-regex url))))

  (fact "Do sign process"
    (let [process-id (:id (init-sign))
          invitation (command pena :sign-contract :processId process-id)]
      (fact "should create invitation url"
        (valid-invitation-url? (:invitation-url invitation)) => true)

      (fact "should start polling"
        (command pena :start-polling :processId process-id) => ok?)
      (fact "document with unfullfilled invitations should be status pending"
        (Thread/sleep 5000)
        (query pena :is-signed :processId process-id) => {:ok true :status "pending"})
      (fact "should sign document"
        (command pena :dummy-sign-document :processId process-id) => ok?)
      (fact "signed document should be status done"
        (Thread/sleep 3000)
        (query pena :is-signed :processId process-id) => {:ok true :status "done"}
        (get-process-status process-id) => "done")))

  (fact "a@b.c has not yet been created"
    (query admin :user-by-email :email "a@b.c") => (contains {:user nil}))

  (fact "test email after signature "
    (let [url   (->> (sent-emails) first :body :plain (re-find #"http[^\s]+"))
          _     (fact url => some?)
          token (when url (re-find #"[^/]+$" url))]
      (fact "Pena confirms company registration (for a@b.c)"
        (http-get url {}) => http200?)
      (fact "Set the password for a@b.c"
        (http-token-call token {:password "zheshiwodemima"}) => http200?)))

  (let [id (->> (query pena :companies) :companies (util/find-by-key :name "company-name") :id)]
    (fact "Kaino cannot edit the created company info"
      (command kaino :company-update :company id :updates {:po "Beijing"}) => fail?)
    (fact "Kaino can edit Solita company info"
      (command kaino :company-update :company "solita" :updates {:po "Beijing"}) => ok?))

  (fact "Fail signing process by timeout"
    (let [process-id (:id (init-sign "fail@example.com" "050349-982Y"))  ]
      (command pena :sign-contract :processId process-id)
      (command pena :start-polling :processId process-id :attempts 0)
      (Thread/sleep 5000)
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

  (fact "Sign process fails"
    (let [process-id (:id (init-sign "test@test.com" "301206-366C"))
          invitation (command pena :sign-contract :processId process-id)]
      (fact "should create invitation url"
        (valid-invitation-url? (:invitation-url invitation)) => true)
      (fact "should start polling"
        (command pena :start-polling :processId process-id) => ok?)
      (fact "document with unfullfilled invitations should be status pending"
        (Thread/sleep 5000)
        (query pena :is-signed :processId process-id) => {:ok true :status "pending"})
      (fact "document singing fails"
        (command pena :dummy-cancel-sign-document :processId process-id) => ok?)
      (fact "signed document should be status done"
        (Thread/sleep 3000)
        (query pena :is-signed :processId process-id) => {:ok false :status "fail"}
        (get-process-status process-id) => "fail"))))
