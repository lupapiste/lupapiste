(ns lupapalvelu.campaign-itest
  (:require [cheshire.core :as json]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.crypt :as crypt]
            [sade.env :as env]))

(apply-remote-minimal)

(def cookie-store (atom {}))

(fact "Get anti CSRF token"
      (login "foo" "bar" {:cookie-store (->cookie-store cookie-store)}) => fail?)

(defn request [rest]
  (update-in (merge {:follow-redirects false
                     :throw-exceptions false
                     :cookie-store (->cookie-store cookie-store)}
                    rest)
             [:headers "x-anti-forgery-token"]
             (constantly (get-anti-csrf-from-store cookie-store))))

(defn response [raw-response]
  (let [{:keys [status body]} (decode-response raw-response)]
    (when (= status 200)
      body)))

(defn anon-query [query-name & args]
  (response (http-get
             (str (server-address) "/api/query/" (name query-name))
             (request {:headers {"accepts" "application/json;charset=utf-8"}
                       :query-params (apply hash-map args)}))))

(defn anon-command [command-name & args]
  (response (http-post
             (str (server-address) "/api/command/" (name command-name))
             (request {:headers {"content-type" "application/json;charset=utf-8"}
                       :body (json/encode (apply hash-map args))}))))

(defn err [error]
  (fn [{text :text}]
    (= text (name error))))

(fact "No campaigns yet"
      (query admin :campaigns) => (contains {:campaigns []}))

(fact "Add expired campaign"
      (command admin :add-campaign :code "  PAST  "
               :starts "2017-1-1" :ends "2017-2-1"
               :account5 0 :account15 0 :account30 0
               :lastDiscountDate "2017-2-28") => ok?)

(fact "Campaign is returned"
      (query admin :campaigns)
      => (contains {:campaigns [{:code "past"
                                 :starts "2017-01-01" :ends "2017-02-01"
                                 :account5 0 :account15 0 :account30 0
                                 :lastDiscountDate "2017-2-28"}]}))

(fact "Add campaign that has not yet been started"
      (command admin :add-campaign :code "future"
               :starts "2217-1-1" :ends "2217-2-1"
               :account5 0 :account15 0 :account30 0
               :lastDiscountDate "2217-2-28")=> ok?)

(fact "There are now two campaigns"
      (-> (query admin :campaigns) :campaigns count) => 2)

(fact "Neither campagn is active"
      (anon-query :campaign :code "past") => (err :error.campaign-not-found)
      (anon-query :campaign :code "future") => (err :error.campaign-not-found))

(fact "Add active campaign"
      (command admin :add-campaign :code "active"
               :starts "2017-1-1" :ends "2217-2-1"
               :account5 5 :account15 15 :account30 30
               :lastDiscountDate "2217-2-28") => ok?)

(fact "There are now three campaigns"
      (-> (query admin :campaigns) :campaigns count) => 3)

(fact "Acive is active"
      (anon-query :campaign :code "active")
      => (contains {:campaign {:code "active"
                               :starts "2017-01-01" :ends "2217-02-01"
                               :account5 5 :account15 15 :account30 30
                               :lastDiscountDate "2217-2-28"}}))

;; See also campaign-test for more extensive pre-checker tests.
(facts "Bad campaigns"
       (fact "Missing key"
             (command admin :add-campaign :code "future2"
                      :starts "2217-1-1" :ends "2217-2-1"
                      :account5 0 :account15 0
                      :lastDiscountDate "2217-2-28")
             => (err :error.invalid-campaign))
       (fact "Duplicate"
             (command admin :add-campaign :code "future"
                      :starts "2217-1-1" :ends "2217-2-1"
                      :account5 1 :account15 1 :account30 1
                      :lastDiscountDate "2217-2-28")
             => (err :error.campaign-conflict))
       (fact "Bad active period"
             (command admin :add-campaign :code "future2"
               :starts "2217-3-1" :ends "2217-2-1"
               :account5 0 :account15 0 :account30 3
               :lastDiscountDate "2217-2-28")
             => (err :error.campaign-period)))

(fact "Delete campaign"
      (command admin :delete-campaign :code "past") => ok?
      (->> (query admin :campaigns)
           :campaigns
           (map :code)) => (just ["active" "future"] :in-any-order))

(fact "Deleting non-existent campaign fails silently"
      (command admin :delete-campaign :code "not-found") => ok?)

;; See onnistuu-itest for regular company signing

(defn get-process [process-id]
  (:process (anon-query :find-sign-process :processId process-id)))

(defn get-process-status [process-id]
  (:status (get-process process-id)))

(defn fetch-document [process-id]
  (http-get (str (server-address) "/api/sign/document/" process-id) {:throw-exceptions false}))

(defn init-sign-command [campaign-code]
  (anon-command :init-sign
                :company {:name "Gongsi"
                          :y "0000000-0"
                          :accountType "account5"
                          :billingType "monthly"
                          :address1 "Jianguomen"
                          :zip "12345"
                          :po "Tampere"
                          :customAccountLimit nil
                          :campaign campaign-code}
                :signer {:firstName "Foo"
                         :lastName "Bar"
                         :email "foo@bar.baz"
                         :personId "131052-308T"}
                :lang "fi"))

(defn init-sign [campaign-code]
  (-> (init-sign-command campaign-code)
      :process-id
      get-process))

(defn finalize-signing [process]
  (fact {:midje/description (str "Finalize signing. Campaign "
                                 (-> process :company :campaign))}
        (let [process-id (:id process)
              stamp (:stamp process)
              crypto-key (-> (env/get-config) :onnistuu
                             :crypto-key (crypt/str->bytes)
                             (crypt/base64-decode))
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
              iv (-> crypto-iv (crypt/base64-encode) (crypt/bytes->str)
                     (crypt/url-encode))
              store (atom {})
              params {:cookie-store (->cookie-store store)
                      :throw-exceptions false}]
          (http-get (str (server-address) "/api/sign/success/"
                                process-id "?data=" data "&iv=" iv) params)
          => http200?
          (get-process-status process-id) => "done")))


(facts "Company registration with active campaign"
       (let [process (init-sign "  AcTiVe ")]
         (fact "Active campaign code is retained"
               process => (contains {:company (contains {:campaign "active"})}))
         (fact "Contract fetch OK"
               (fetch-document (:id process)) => http200?)
         (finalize-signing process)
         (fact "Last email is campaign mail"
               (let [{:keys [subject body]} (last-email)]
                 (fact "Campaign subject"
                       subject => (contains "KAMPANJA"))
                 (fact "Campaign code in the message body"
                       (:plain body) => (contains "Kampanjakoodi: active"))))))

(facts "Company registration with bad campaign"
       (init-sign-command "   bad  ") => (err :error.campaign-not-found))
(facts "Company registration with inactive campaign"
       (init-sign-command "future") => (err :error.campaign-not-found))

(fact "Just in case, company registration without campaign"
      (let [process (init-sign "")]
         (fact "Contract fetch OK"
               (fetch-document (:id process)) => http200?)
         (finalize-signing process)
         (fact "Last email is not campaign mail"
               (let [{:keys [subject body]} (last-email)]
                 (fact "No campaign subject"
                       subject =not=> (contains "KAMPANJA"))
                 (fact "No campaign code in the message body"
                       (:plain body) =not=> (contains "Kampanjakoodi"))))))
