(ns lupapalvelu.ident.ad-login-util
  (:require [hiccup.core :as hiccup]
            [hiccup.page :refer [xml-declaration]]
            [lupapalvelu.logging :refer [error warn]]
            [lupapalvelu.organization :as org]
            [ring.util.response :refer :all]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [saml20-clj.shared :refer [certificate-x509 jcert->public-key]]
            [saml20-clj.sp :as saml-sp]
            [schema.core :as sc])
  (:import [java.io ByteArrayInputStream]
           [java.security KeyFactory]
           [java.security.cert CertificateFactory X509Certificate]
           [java.security.spec PKCS8EncodedKeySpec]
           [java.util Base64]
           [org.opensaml.saml2.core EncryptedAssertion Response]
           [org.opensaml.saml2.encryption Decrypter]
           [org.opensaml.xml.encryption InlineEncryptedKeyResolver]
           [org.opensaml.xml.security.keyinfo StaticKeyInfoCredentialResolver]
           [org.opensaml.xml.security.x509 BasicX509Credential]
           [org.opensaml.xml.signature SignatureValidator]))

(set! *warn-on-reflection* true)

(def ad-config
  {:app-name    (env/value :sso :entityId)
   :sp-cert     (env/value :sso :cert)
   :private-key (env/value :sso :privatekey)
   ;; We do not encrypt the RelayState param for now, since its contents are not sensitive
   ;;:nippy-opts  {:passphrase {:salted (env/value :sso :basic-auth :crypto-key)}}
   })

(defn parse-certificate
  "Strip the -----BEGIN CERTIFICATE----- and -----END CERTIFICATE----- headers and newlines
  from certificate."
  ^String [certstring]
  (ss/replace certstring #"[\n ]|(BEGIN|END) CERTIFICATE|(BEGIN|END) PRIVATE KEY|-{5}" ""))

(defn string->certificate ^X509Certificate [^String certstring]
  (let [cf         (CertificateFactory/getInstance "X.509")
        bytestream (ByteArrayInputStream. (.getBytes certstring))]
    (.generateCertificate cf bytestream)))

(defn string->private-key [keystring]
  (let [kf                (KeyFactory/getInstance "RSA")
        pksc8EncodedBytes (.decode (Base64/getDecoder) (parse-certificate keystring))
        keySpec           (PKCS8EncodedKeySpec. pksc8EncodedBytes)]
    (.generatePrivate kf keySpec)))

(defn make-saml-decrypter
  "Ported from kirasystems/saml20-clj, changed the API - we want to use this without storing
  the private key in a keystore."
  ^Decrypter [keystring]
  (let [private-key     (string->private-key keystring)
        decryption-cred (doto (new BasicX509Credential)
                          (.setPrivateKey private-key))
        decrypter       (new Decrypter
                             nil
                             (new StaticKeyInfoCredentialResolver decryption-cred)
                             (new InlineEncryptedKeyResolver))]
    decrypter))

(defn metadata
  "Ported from kirasystems/saml20-clj, removed 'http://example.com/SingleLogout' endpoint"
  ([app-name acs-uri certificate-str sign-request?]
   (str
     (xml-declaration "UTF-8")
     (hiccup/html
       [:md:EntityDescriptor {:xmlns:md "urn:oasis:names:tc:SAML:2.0:metadata",
                              :ID       (ss/replace acs-uri #"[:/]" "_"),
                              :entityID app-name}
        [:md:SPSSODescriptor
         (cond-> {:AuthnRequestsSigned        "true",
                  :WantAssertionsSigned       "true",
                  :protocolSupportEnumeration "urn:oasis:names:tc:SAML:2.0:protocol"}
           (not sign-request?) (dissoc :AuthnRequestsSigned))
         [:md:KeyDescriptor {:use "signing"}
          [:ds:KeyInfo {:xmlns:ds "http://www.w3.org/2000/09/xmldsig#"}
           [:ds:X509Data
            [:ds:X509Certificate certificate-str]]]]
         [:md:KeyDescriptor {:use "encryption"}
          [:ds:KeyInfo {:xmlns:ds "http://www.w3.org/2000/09/xmldsig#"}
           [:ds:X509Data
            [:ds:X509Certificate certificate-str]]]]
         [:md:NameIDFormat "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"]
         [:md:NameIDFormat "urn:oasis:names:tc:SAML:2.0:nameid-format:transient"]
         [:md:NameIDFormat "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent"]
         [:md:NameIDFormat "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified"]
         [:md:NameIDFormat "urn:oasis:names:tc:SAML:1.1:nameid-format:X509SubjectName"]
         [:md:AssertionConsumerService {:Binding "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST", :Location acs-uri, :index "0", :isDefault "true"}]]]))))

(defn remove-namespaces-from-kws
  "Convert namespaced map keys like :http://schemas.microsoft.com/identity/claims/tenantid -> :tenantid"
  [m]
  (into {} (for [[k v] m]
             (let [newkey (-> k name (ss/split #"/") last keyword)]
               [newkey v]))))

(defn parse-saml-info
  "The saml-info map returned by saml20-clj comes in a wacky format, so its best to
  parse it into a more manageable form (without string keys or single-element lists etc)."
  [element]
  (cond
    (and (seq? element) (= (count element) 1)) (parse-saml-info (first element))
    (seq? element) (mapv parse-saml-info element)
    (map? element) (into {} (for [[k v] (remove-namespaces-from-kws element)] [(keyword k) (parse-saml-info v)]))
    :else element))

(defn make-acs-uri [host domain]
  (format "%s/api/saml/ad-login/%s" host domain))

(def oauth-role->client-type {:departmental         "docdepartmental"
                              :departmental-limited "docdepartmental"
                              :reporter             "bi-app"
                              :director             "bi-app"})

(defn resolve-roles
  "Takes a map of corresponding roles (key = role in Lupis, value is AD-group)
  and a seq of user roles from the SAML, returns a set of corresponding LP roles."
  [org-roles ad-params]
  (let [ad-roles-set (if (string? ad-params) #{ad-params} (set ad-params))
        roles        (for [[lp-role ad-role] org-roles]
                       (when (ad-roles-set ad-role)
                         (name lp-role)))]
    (->> roles (remove nil?) set)))

(defn make-oauth-roles [organization-id roles]
  (map (fn [role]
         {:organization organization-id
          :client-type  (-> role keyword oauth-role->client-type)
          :role         role})
       roles))

(defn resolve-authz
  "Takes an ad-login (as returned by `lupapalvelu.ident.ad-login/get-ad-login-by-domain`)
  and a sequence of ad-groups received in the SAML response, returns a map containing a parsed orgAuthz element and
  oauth-roles. e.g.
  {:oauth-roles ({:organization \"609-R\", :client-id \"docdepartmental\", :roles [\"departmental\"]}
                 {:organization \"753-R\", :client-id \"bi-app\", :roles [\"reporter\"]})
   :orgAuthz {\"609-R\" (\"reader\"), \"753-R\" ()}}"
  [{:keys [organizations enabled]} ad-groups]
  (when enabled
    (reduce (fn [acc {:keys [id role-mapping]}]
              (let [organization        (org/get-organization id)
                    allowed-oauth-roles (org/allowed-oauth-roles-in-organization organization)
                    oauth-mapping       (select-keys role-mapping allowed-oauth-roles)
                    oauth-roles         (resolve-roles oauth-mapping ad-groups)
                    allowed-roles       (org/allowed-roles-in-organization organization)
                    orgauthz-mapping    (select-keys role-mapping allowed-roles)
                    orgauthz-roles      (resolve-roles orgauthz-mapping ad-groups)]
                (-> acc
                    (update-in [:oauth-roles] #(concat % (make-oauth-roles id oauth-roles)))
                    (assoc-in [:orgAuthz (keyword id)] orgauthz-roles))))
            {}
            organizations)))

(defn- get-signature-validator
  "Turns the provided Identity Provider certification string into a SAML signature validator"
  ^SignatureValidator [idp-cert]
  (let [idp-pubkey   (-> idp-cert certificate-x509 jcert->public-key)
        public-creds (doto (new BasicX509Credential)
                       (.setPublicKey idp-pubkey))]
    (new SignatureValidator public-creds)))

(defn- valid-signature?
  "Returns true if any of the validators apply to the given SAML response's signature"
  [^Response saml-obj validators]
  (when-let [signature (.getSignature saml-obj)]
    (some (fn [^SignatureValidator validator]
            (try
              (nil? (.validate validator signature)) ; validator returns nil when ok
              (catch Exception e
                (warn "Signature not valid:" (.getMessage e))
                false)))
          validators)))

(defn parse-assertions
  "Assertions in the given SAML response. Returns the user-related assertions or nil, if the
  assertions are not found or the response is not valid. A valid SAML response either must
  be signed OR its every assertion must be signed. `idp-certs` are the IdP certificate for
  the corresponding signing (private) key. `decrypter` is an
  `org.opensaml.saml2.encryption.Decrypter` instance for the encrypted assertions (if
  there are any). Sample return value:

  [{:Group ['GG_Lupapiste_RAVA_read'
            'GG_Lupapiste_RAVA_Arkistonhoitaja'],
    :emailaddress 'terttu.panaani@pori.fi',
    :givenname 'Terttu',
    :name 'Panaani Terttu',
    :surname 'Panaani'}]"
  ([^Response saml-resp idp-certs sp-private-key]
   {:pre [saml-resp idp-certs sp-private-key]}
   (try
     (when (:success? (saml-sp/parse-saml-resp-status saml-resp))
       (let [validators (map get-signature-validator idp-certs)
             decrypter  (doto (make-saml-decrypter sp-private-key)
                          ;; https://stackoverflow.com/questions/24364686
                          (.setRootInNewDocument true))
             assertions (->> (.getEncryptedAssertions saml-resp)
                             (map (fn [^EncryptedAssertion assertion]
                                    (.decrypt decrypter assertion)))
                             (concat (.getAssertions saml-resp)))]
         (when (or (valid-signature? saml-resp validators)
                   (every? #(valid-signature? % validators) assertions))
           (->> assertions
                (map saml-sp/parse-saml-assertion)
                parse-saml-info
                util/sequentialize ; parse-saml-info can return coll or single item
                (map :attrs)
                not-empty))))
     (catch Exception e
       (error "Parse assertions failed:" e))))
  ([saml-resp idp-certs]
   (parse-assertions saml-resp idp-certs (:private-key ad-config))))

(def email-checker (sc/checker ssc/Email))

(defn valid-email
  "Returns canonized `email` or nil if the address is not in the valid format."
  [email]
  (let [email (ss/canonize-email email)]
    (when-not (email-checker email)
      email)))

(defn email-domain
  "Domain part of correctly formatted `email` address."
  [email]
  (some-> (valid-email email)
          (ss/split #"@")
          last))
