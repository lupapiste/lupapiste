(ns lupapalvelu.activation
  (:require [monger.operators :refer :all]
            [hiccup.core :refer :all]
            [sade.env :as env]
            [sade.email :as email]
            [sade.strings :refer [lower-case]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.security :as security]))

;;
;; FIXME:
;; should use notification email system
;;

(def ^:private activation-mail-default-styles
"
html {
  background-color: #f0f0f0;
}
body {
  font-family: Dosis,sans-serif;
  font-size: 14px;
  padding: 18px;
  margin: 12px;
  background-color: #ffffff;
}

h1 {
  font-size: 16px;
}

p {
  font-size: 14px;
}")

(defn gen-activation-mail [{:keys [key service-name styles user] :or {styles activation-mail-default-styles}}]
  (let [href (str (env/value :host) (env/value :activation :path) key)]
    (html [:html
           [:head
            [:style {:type "text/css"} styles]]
           [:body
            [:h1 (str "Kiitos rekister\u00f6itymisest\u00e4, " (:firstName user) ".")]
            [:p "Tervetuloa Lupapisteen k\u00e4ytt\u00e4j\u00e4ksi. Aktivoi k\u00e4ytt\u00e4j\u00e4tunnuksesi klikkaamalla "
             [:a {:href href}" t\u00e4st\u00e4"]
             ". Jos linkki ei toimi, kopioi selaimeesi osoite " href]
            [:p "Jos et ole rekister\u00f6itynyt Lupapisteeseen, voit j\u00e4tt\u00e4\u00e4 t\u00e4m\u00e4n s\u00e4hk\u00f6postin huomiotta."]
            [:p "Yst\u00e4v\u00e4llisin terveisin,"]
            [:p "Lupapiste - tiimi"]]])))

(defn send-activation-mail-for [user]
  (let [key     (security/random-password)
        service-name (env/value :activation :service)
        subject (str service-name ": K\u00e4ytt\u00e4j\u00e4tunnuksen aktivointi")
        message (gen-activation-mail {:key key :service-name service-name :user user})
        userid  (or (:_id user) (:id user))
        email   (:email user)]
    (mongo/insert :activation {:user-id userid :email email :activation-key key})
    (email/send-mail email subject :html message)))

(defn get-activation-key [userid]
  (mongo/select-one :activation {:user-id userid}))

(defn activate-account [activation-key]
  (let [act     (mongo/select-one :activation {:activation-key activation-key})
        userid  (:user-id act)
        success (mongo/update-by-id :users userid {$set {:enabled true}})]
    (when success
      (mongo/remove :activation (:_id act))
      (merge (user/non-private (mongo/select-one :users {:_id userid})) {:id userid}))))

(defn activate-account-by-email [email]
  (let [act     (mongo/select-one :activation {:email (lower-case email)})
        userid  (:user-id act)
        success (mongo/update-by-id :users userid {$set {:enabled true}})]
    (when success
      (mongo/remove :activation (:_id act))
      (merge (user/non-private (mongo/select-one :users {:_id userid})) {:id userid}))))

(defn activations []
  (mongo/select :activation))
