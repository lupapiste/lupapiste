(ns lupapalvelu.idf.idf-api
  "Identity federation server: create users from partner applications to Lupapiste"
  (:require [taoensso.timbre :as timbre :refer [debug info warn error]]
            [ring.util.response :as resp]
            [sade.env :as env]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.dns :as dns]
            [sade.core :refer :all]
            [sade.validators :as v]
            [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.idf.idf-core :refer :all]))

(def- timestamp-tolerance-ms (* 10 60 1000)) ; 10 minutes

(defn- strip-nonletters [s] (when s (clojure.string/replace s #"[^\p{L} -]" "")))

(notifications/defemail :activate-linked-account
  {:recipients-fn notifications/from-user
   :subject-key "email.title.account-activation"
   :model-fn      (fn [{{token :token} :data} _ recipient]
                    {:link #(str (env/value :host) "/app/" (name %) "/welcome#!/link-account/" token)
                     :user recipient})})

(defn- bad-request [body]
  (resp/status (resp/response body) 400))

(defn- internal-server-error [body]
  (resp/status (resp/response body) 500))

(defn- incorrect-parter [app]
  (when-not (known-partner? app)
    (bad-request "Invalid app parameter")))

(defn- incorrect-mac
  [first-name last-name email phone street zip city marketing architect app id ts mac]
  (let [expected-mac (calculate-mac first-name last-name email phone street zip city marketing architect app id ts :receive)]
    (when-not (= expected-mac mac)
      (bad-request "Invalid mac"))))

(defn- request-expired [^String timestamp]
  (try
    (let [ts (java.lang.Long/parseLong timestamp)
          clock-drift (util/abs (- (now) ts))]
      (when (> clock-drift timestamp-tolerance-ms)
        (bad-request "Invalid timestamp: too old")))
    (catch NumberFormatException e
      (bad-request "Invalid timestamp: NaN"))))

(defn- invalid-email [email]
  (when-not (and (v/valid-email? email) (or (env/value :email :skip-mx-validation) (dns/valid-mx-domain? email)))
    (bad-request "Invalid email parameter")))

(defn- invalid-boolean [b]
  (when-not (or (ss/blank? b) (= "true" b) (= "false" b))
    (bad-request "Invalid boolean parameter")))

(defn- invalid-parameters [first-name last-name email phone street zip city marketing architect]
  (or
    (invalid-email email)
    (invalid-boolean marketing)
    (invalid-boolean architect)))

(defn- create-user! [first-name last-name email phone marketing architect]
  (let [user-data {:email email
                   :role "dummy"
                   :phone phone
                   :enabled false
                   :allowDirectMarketing (Boolean/parseBoolean marketing)
                   :architect (Boolean/parseBoolean architect)}
        user (user/create-new-user nil user-data)
        token-id (token/make-token :activate-linked-account, {}, (select-keys user [:email :phone]), :ttl ttl/idf-create-user-token-ttl, :auto-consume false)]
    (notifications/notify! :activate-linked-account {:data {:token token-id} :user {:firstName first-name :lastName last-name :email email}})
    user))

(defn- link-user! [first-name last-name email phone marketing architect app id ts]
  (let [user (or
               (user/get-user-by-email email)
               (create-user! first-name last-name email phone marketing architect))]
    (debug email app id)
    (link-account! email app id ts true)
    (:id user)))

(defn handle-create-user-request
  "Creates a new user. Returns ring response"
  [first-name last-name email phone street zip city marketing architect app id ^String ts mac]
  (resp/charset
    (or
      (incorrect-parter app)
      (incorrect-mac first-name last-name email phone street zip city marketing architect app id ts mac)
      (request-expired ts)
      (invalid-parameters first-name last-name email phone street zip city marketing architect)
      (try
        (let [first-name (strip-nonletters first-name)
              last-name  (strip-nonletters last-name)
              email      (user/canonize-email email)
              phone      (ss/trim phone)
              ts         (util/to-long ts)
              user-id (link-user! first-name last-name email phone marketing architect app id ts)]
          (resp/response (str user-id "\n")))
        (catch Throwable t
          (error t)
          (internal-server-error (.getMessage t)))))
    "UTF-8"))

(defquery get-link-account-token
  {:parameters [tokenId]
   :input-validators [(partial action/non-blank-parameters [:tokenId])]
   :user-roles #{:anonymous}}
  [_]
  (ok :data
    (let [skeleton {:email "" :phone ""}
          token (token/get-token tokenId)]
     (merge skeleton
       (when (= (:token-type token) :activate-linked-account)
         (select-keys (:data token) [:email :phone]))))))
