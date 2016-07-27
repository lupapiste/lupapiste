(ns lupapalvelu.idf.idf-e2e-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [sade.core :refer [now]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :refer [get-user-by-id]]
            [lupapalvelu.idf.idf-api :refer :all]
            [lupapalvelu.idf.idf-client :refer :all]
            [lupapalvelu.idf.idf-core :refer [calculate-mac]]))

;;
;; Helpers & fixture
;;

;; Parameter documented
(def static-params
  {:etunimi "Testi" :sukunimi "M\u00e4kinen"
   :puhelin "0400-000123"
   :katuosoite "\u00c5kerlundinkatu 11"
   :postinumero "33100"
   :postitoimipaikka  "Tampere"
   :suoramarkkinointilupa true
   :ammattilainen ""
   :app "lupapiste.fi"
   :id "531985deb7c54d1c366bccf7"})

(def cookies (doto (->cookie-store (atom {}))
               (.addCookie test-db-cookie)))

(def local-db-name (str "test_idf-itest_" (now)))

(defn- calculate-default-mac [ts email]
  (calculate-mac (assoc static-params :email email) "lupapiste.fi" ts :receive))

(def documented-params
  (let [ts (now)
        email "testi.makinen@lupapiste.fi"]
    (merge static-params
      {:email email
       :ts ts
       :mac (calculate-default-mac ts email)})))

(defn- do-post [query-params]
  (http-post
    (str (server-address) "/api/id-federation" )
    {;:debug true :debug-body true
     :form-params query-params
     :follow-redirects false
     :throw-exceptions false
     :cookie-store cookies}))


;; Setup remote data
(apply-remote-minimal)
(last-email) ; inbox zero

;; Setup local data
(mongo/connect!)
(mongo/with-db local-db-name (fixture/apply-fixture "minimal"))

;;
;; Tests
;;
(fact "Server responds to documented request"
  (let [resp (do-post documented-params)
        user-id (first (clojure.string/split-lines (:body resp)))]
    (:status resp) => 200
    user-id => (partial re-matches #"[a-f0-9]{24}")

    (fact "User is linked to partner app"
      (let [user-resp (query admin :users :userId user-id)
            user (first (:users user-resp))]
        user-resp => ok?
        (get-in user [:partnerApplications :lupapiste :id]) => (:id documented-params)))

    (fact "User receives activation link"
      (let [email (last-email)]
        (:to email) => (contains (:email documented-params))
        (:subject email) => "Lupapiste: K\u00e4ytt\u00e4j\u00e4tunnuksen aktivointi"
        (get-in email [:body :plain]) => (partial re-matches #"(?sm).+/app/fi/welcome#!/link-account/\w{48}\s.+")))))

(fact "Old user is linked"
  (let [test-id "123"]
    (mongo/with-db local-db-name
      (let [user (assoc (get-user-by-id pena-id) :id test-id)]
        (fact "Meta: Old user is not yet linked to partner app"
          (get-in user [:partnerApplications :lupapiste :id]) => nil?
          (get-in user [:partnerApplications :rakentajafi :id]) => nil?)

        (fact "send-user-data succeeds"
          (send-user-data user "rakentaja.fi" :cookie-store cookies) => true) ; In local & dev profiles sends actually to localhost

        (fact "Local user is linked to partner app: got pena's id in response"
          (let [linked-user (get-user-by-id pena-id)]
            (get-in linked-user [:partnerApplications :rakentajafi :id]) => pena-id
            (get-in linked-user [:partnerApplications :rakentajafi :created]) => truthy
            (get-in linked-user [:partnerApplications :rakentajafi :origin]) => false))))

    (fact "User is linked on remote end too: we sent test-id"
      (let [user-resp (query admin :users :userId pena-id)
            remote-user (first (:users user-resp))]
        user-resp => ok?
        (get-in remote-user [:partnerApplications :lupapiste :id]) => test-id
        (get-in remote-user [:partnerApplications :lupapiste :created]) => truthy
        (get-in remote-user [:partnerApplications :lupapiste :origin]) => true))))

(fact "Invalid app"
    (let [resp (do-post (assoc documented-params :app "attacker"))]
      (:status resp) => 400
      (:body resp) => (contains "Invalid app")))

(fact "Invalid mac"
  (let [resp (do-post (assoc documented-params :mac "3d5251fb99d198cd01b280838acea3c40acb25698d1537448ad82c0000000000"))]
    (:status resp) => 400
    (:body resp) => (contains "Invalid mac")))

(fact "Invalid timestamp"
  (let [ts (- (:ts documented-params) (* 10 60 1000) 200)
        resp (do-post (assoc documented-params :ts ts :mac (calculate-default-mac ts (:email documented-params))))]
    (:status resp) => 400
    (:body resp) => (contains "Invalid timestamp")))

(fact "Timestamp within tolerance"
  (let [ts (- (:ts documented-params) (* 9 60 1000))
        resp (do-post (assoc documented-params :ts ts :mac (calculate-default-mac ts (:email documented-params))))]
    (:status resp) => 200))
