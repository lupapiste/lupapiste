(ns lupapalvelu.vetuma-itest-util
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as e]
            [lupapalvelu.itest-util :refer [http-get http-post] :as itu]
            [sade.strings :as ss]
            [sade.xml :as xml]
            [lupapalvelu.vetuma :as vetuma]))

(testable-privates lupapalvelu.vetuma mac-of keys-as-keywords)

(def vetuma-endpoint (str (itu/server-address) "/api/vetuma"))

(defn default-vetuma-params [cookie-store]
  {:cookie-store cookie-store
   :follow-redirects false
   :throw-exceptions false})

(def default-token-query {:query-params {:success "/success"
                                         :cancel "/cancel"
                                         :error "/error"}})

(defn default-vetuma-response-data [trid]
  {"TRID" trid
   "STATUS" "SUCCESSFUL"
   "SUBJECTDATA" "ETUNIMI=Jukka, SUKUNIMI=Palmu"
   "EXTRADATA" "HETU=123456-7890"
   "USERID" "123456-7890"
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
    (fact "Form contains application ID" body => (contains "TESTIASIAKAS11"))
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

(defn authenticate-to-vetuma! [cookie-store]
  (let [vetuma-params (default-vetuma-params cookie-store)
        trid (vetuma-init vetuma-params default-token-query)]
    (vetuma-fake-respose vetuma-params (default-vetuma-response-data trid))))
