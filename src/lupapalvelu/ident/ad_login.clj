(ns lupapalvelu.ident.ad-login
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.request :as request]
            [lupapalvelu.security :as security]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$set]]
            [ring.util.response :refer :all]
            [sade.core :refer [def-]]
            [sade.session :as ssess]
            [sade.strings :as ss]
            [sade.xml :as sxml]
            [schema.core :as sc]
            [saml20-clj.sp :as saml-sp]
            [saml20-clj.routes :as saml-routes]
            [saml20-clj.shared :as saml-shared]))

;; Headerit idp:sta
(def ad-header-translations
  {:ad-cn        :fullName
   :ad-firstname :firstName
   :ad-givenname :givenName
   :ad-sn        :lastName
   :ad-mail      :email
   :ad-address   :street
   :ad-zip       :zip
   :ad-city      :city
   :ad-role      :role
   :ad-org-authz :orgAuthz})

(defn- parse-certificate
  "Strip the ---BEGIN CERTIFICATE--- and ---END CERTIFICATE--- headers and newlines
  from certificate."
  [certstring]
  (->> (ss/split certstring #"\n") rest drop-last ss/join))

;; Nää ehkä propertieseihin?
(def config
  {:app-name "Lupapiste"
   :base-uri "http://localhost:8000"
   :idp-uri  "http://localhost:7000" ;; The dockerized, locally running mock-saml instance (https://github.com/lupapiste/mock-saml)
   :idp-cert (parse-certificate (slurp "./idp-public-cert.pem")) ;; This needs to live in Mongo's organizations collection
   :keystore-file "./keystore"
   :keystore-password (System/getenv "KEYSTORE_PASS") ;; The default password from the dev guide, needs to be set in environment variable
   :key-alias "jetty" ;; The normal Lupis certificate
   })

(defn xml-tree->edn
  "Takes an xml tree returned by the clojure.data.xml parser, recursively parses it into as readable and flat a Clojure map
  as is feasible."
  [element]
  (cond
    (nil? element) nil
    (string? element) element
    (and (= 1 (count element)) (string? (first element))) (first element)
    (and (map? element) (= :AttributeValue (:tag element))) (xml-tree->edn (first (:content element)))
    (and (map? element) (empty? element)) {}
    (and (= 1 (count element)) (sequential? element)) (xml-tree->edn (first element))
    (sequential? element) (mapv xml-tree->edn element)
    (and (map? element) (= :Attribute (:tag element))) {(keyword (:Name (:attrs element))) (xml-tree->edn (:content element))}
    (map? element) {(:tag element) (xml-tree->edn (:content element))}
    :else nil))

(defn parse-saml-resp
  "Takes an SAML message in string format, returns it parsed into a Clojure map.
  Isn't really all that necessary, since the saml-info var (inside a let binding
  in ad-login route) contains the same info..."
  [xml-string]
  (-> xml-string sxml/parse xml-tree->edn))

(defn parse-saml-info
  "The saml-info map returned by saml20-clj comes in a wacky format, so its best to
  parse it into a more manageable form (without string keys or single-element lists etc)."
  [element]
  (cond
    (and (seq? element) (= (count element) 1)) (parse-saml-info (first element))
    (seq? element) (mapv parse-saml-info element)
    (map? element) (into {} (for [[k v] element] [(keyword k) (parse-saml-info v)]))
    :else element))

(def- mutables
  (assoc (saml-sp/generate-mutables)
         :xml-signer (saml-sp/make-saml-signer (:keystore-file config)
                                               (:keystore-password config)
                                               (:key-alias config)
                                               :algorithm :sha256)))

(def- saml-req-factory!
  (saml-sp/create-request-factory mutables
                                  (:idp-uri config)
                                  saml-routes/saml-format
                                  (:app-name config)
                                  (:acs-uri config)))

(defpage [:get "/api/saml/ad-login"] []
  (let [saml-request (saml-req-factory!)
        hmac-relay-state (saml-routes/create-hmac-relay-state (:secret-key-spec mutables) "target")
        req (request/ring-request)
        sessionid (get-in req [:session :id])
        trid (security/random-password)]
    (saml-sp/get-idp-redirect (:idp-uri config)
                              saml-request
                              hmac-relay-state)))

(defpage [:post "/api/saml/ad-login"] {params :params session :session}
  (let [req (request/ring-request)
        decrypter (saml-sp/make-saml-decrypter (:keystore-file config)
                                               (:keystore-password config)
                                               (:key-alias config))
        sp-cert (saml-shared/get-certificate-b64 (:keystore-file config)
                                                 (:keystore-password config)
                                                 (:key-alias config))
        acs-uri (str (:base-uri config) "/saml")
        prune-fn! (partial saml-sp/prune-timed-out-ids! (:saml-id-timeouts mutables))
        state {:mutables mutables
               :saml-req-factory! saml-req-factory!
               :timeout-pruner-fn! prune-fn!
               :certificate-x509 sp-cert}
        xml-response (saml-shared/base64->inflate->str (get-in req [:params :SAMLResponse]))
        relay-state (get-in req [:params :RelayState])
        [valid-relay-state? continue-url] (saml-routes/valid-hmac-relay-state? (:secret-key-spec mutables) relay-state)
        saml-resp (saml-sp/xml-string->saml-resp xml-response)
        valid-signature? (if (:idp-cert config)
                           (saml-sp/validate-saml-response-signature saml-resp (:idp-cert config))
                           false)
        valid? (and valid-relay-state? valid-signature?)
        saml-info (when valid? (saml-sp/saml-resp->assertions saml-resp decrypter))
        parsed-saml-info (parse-saml-info saml-info)
        {:keys [email firstName lastName]} (get-in parsed-saml-info [:assertions :attrs])
        _ (info parsed-saml-info)
        _ (clojure.pprint/pprint parsed-saml-info)
        ]
    (if valid?
      ; (response/status 200 (response/content-type "text/plain" (str saml-info)))
      (let [user (or (usr/get-user-by-email email)
                     (usr/create-new-user {:role "admin"}
                                          {:firstName firstName
                                           :lastName lastName
                                           :role "authority"
                                           :email email
                                           :username email
                                           :enabled true
                                           :orgAuthz {:753-R ["authorityAdmin"]}}
                                          ))
            response (ssess/merge-to-session
                       req
                       (response/redirect "http://localhost:8000/app/fi/authority") ; Fix!
                       {:user user})]
        response)
      (do
        (error "SAML validation failed")
        (response/status 403 (response/content-type "text/plain" "Validation of SAML response failed"))))))
