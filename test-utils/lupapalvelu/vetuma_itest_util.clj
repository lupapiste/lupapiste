(ns lupapalvelu.vetuma-itest-util
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as e]
            [lupapalvelu.itest-util :refer [http-get http-post server-address] :as itu]
            [sade.strings :as ss]
            [sade.env :as env]
            [sade.util :as util]
            [sade.xml :as xml]
            [lupapalvelu.vetuma :as vetuma]
            [cheshire.core :as json]))

(testable-privates lupapalvelu.vetuma mac-of keys-as-keywords)

(def vetuma-endpoint (str (itu/server-address) "/api/vetuma"))

(defn default-vetuma-params [cookie-store]
  {:cookie-store cookie-store
   :follow-redirects false
   :throw-exceptions false})

(def default-token-query {:query-params {:success "/success"
                                         :cancel "/cancel"
                                         :error "/error"}})

(defn register [base-opts user]
  (itu/decode-body
    (http-post
      (str (server-address) "/api/command/register-user")
      (util/deep-merge
        base-opts
        {;:debug true
         :headers {"content-type" "application/json;charset=utf-8"}
         :body    (json/encode user)}))))

(def new-user-details
  {:phone "0500"
   :city "Tammerfors"
   :zip "12345"
   :street "Osootes"
   :password "salasana"
   :email "jukka@example.com"
   :rakentajafi false
   :allowDirectMarketing true
   :architect true
   :graduatingYear "1978"
   :fise "foobar"
   :fiseKelpoisuus "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)"
   :degree "diplomi-insin\u00f6\u00f6ri"})

(defn stamped-new-user-details [stamp]
  (assoc new-user-details :stamp stamp))

(defn default-vetuma-response-data [trid]
  {"TRID" trid
   "STATUS" "SUCCESSFUL"
   "SUBJECTDATA" "ETUNIMI=Jukka, SUKUNIMI=Palmu"
   "EXTRADATA" "HETU=160935-0806"
   "USERID" "160935-0806"
   "VTJDATA" "<VTJHenkiloVastaussanoma/>"})

(defn vetuma-init
  "Initializes Vetuma session. Returns transaction ID (TRID)."
  [request-opts token-query]
  ; Request welcome page and query features to init session
  (http-get (str (itu/server-address) "/app/wi/welcome") request-opts)
  (http-get (str (itu/server-address) "/api/query/features") request-opts)
  (let [{:keys [status body]} (http-get vetuma-endpoint (merge request-opts token-query))
        form (xml/parse body)
        trid (xml/select1-attribute-value form [(e/attr= :id "TRID")] :value)]

    (fact "Init returned OK" status => 200)
    (fact "Form contains application ID" body => (contains (env/value :vetuma :rcvid)))
    (fact "Form contains transaction ID" trid =not=> ss/blank?)
    (fact "Form contains standard error url" (xml/select1-attribute-value form [(e/attr= :id "ERRURL")] :value) => (contains "/api/vetuma/error"))
    (fact "Form contains standard cancel url" (xml/select1-attribute-value form [(e/attr= :id "CANURL")] :value) => (contains "/api/vetuma/cancel"))

    trid))


(defn vetuma-fake-respose [request-opts vetuma-data]
  {:pre [(map? vetuma-data)]}
  (let [base-post (merge (zipmap (map (comp ss/upper-case name) vetuma/response-mac-keys) (repeat "0")) vetuma-data)
        mac (mac-of (assoc (keys-as-keywords base-post) :key (:key (vetuma/config))) vetuma/response-mac-keys)
        vetuma-post (assoc base-post :mac mac)
        endpoint (case (vetuma-data "STATUS")
                   "SUCCESSFUL" vetuma-endpoint
                   "ERROR"      (str vetuma-endpoint "/error")
                   "REJECTED"   (str vetuma-endpoint "/error")
                   "FAILURE"    (str vetuma-endpoint "/error")
                   "CANCELLED"  (str vetuma-endpoint "/cancel"))]

    (http-post endpoint (assoc request-opts :form-params vetuma-post))))

(defn vetuma-finish [request-opts trid]
  (vetuma-fake-respose request-opts (default-vetuma-response-data trid)))

(defn authenticate-to-vetuma! [cookie-store]
  (let [vetuma-params (default-vetuma-params cookie-store)
        trid (vetuma-init vetuma-params default-token-query)]
    (vetuma-fake-respose vetuma-params (default-vetuma-response-data trid))))
