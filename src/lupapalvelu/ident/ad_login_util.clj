(ns lupapalvelu.ident.ad-login-util
  (:require [hiccup.core :as hiccup]
            [hiccup.page :refer [xml-declaration]]
            [ring.util.response :refer :all]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [saml20-clj.shared :refer [jcert->public-key certificate-x509]]
            [saml20-clj.sp :as saml-sp]
            [schema.core :as sc]
            [taoensso.timbre :refer [error]])
  (:import [java.io ByteArrayInputStream]
           [java.security KeyFactory]
           [java.security.cert CertificateFactory X509Certificate]
           [java.security.spec PKCS8EncodedKeySpec]
           [java.util Base64]
           [org.opensaml.saml2.encryption Decrypter]
           [org.opensaml.xml.encryption InlineEncryptedKeyResolver]
           [org.opensaml.xml.security.keyinfo StaticKeyInfoCredentialResolver]
           [org.opensaml.xml.security.x509 BasicX509Credential]
           [org.opensaml.xml.signature SignatureValidator]))

(def ad-config
  {:app-name    (env/value :sso :entityId)
   :sp-cert     (env/value :sso :cert)
   :private-key (env/value :sso :privatekey)
   ;; We do not encrypt the RelayState param for now, since its contents are not sensitive
   ;;:nippy-opts  {:passphrase {:salted (env/value :sso :basic-auth :crypto-key)}}
   })

(defn ^String parse-certificate
  "Strip the -----BEGIN CERTIFICATE----- and -----END CERTIFICATE----- headers and newlines
  from certificate."
  [certstring]
  (ss/replace certstring #"[\n ]|(BEGIN|END) CERTIFICATE|(BEGIN|END) PRIVATE KEY|-{5}" ""))

(defn ^X509Certificate string->certificate [^String certstring]
  (let [cf (CertificateFactory/getInstance "X.509")
        bytestream (ByteArrayInputStream. (.getBytes certstring))]
    (.generateCertificate cf bytestream)))

(defn string->private-key [keystring]
  (let [kf (KeyFactory/getInstance "RSA")
        pksc8EncodedBytes (.decode (Base64/getDecoder) (parse-certificate keystring))
        keySpec (PKCS8EncodedKeySpec. pksc8EncodedBytes)]
    (.generatePrivate kf keySpec)))

(defn make-saml-decrypter
  "Ported from kirasystems/saml20-clj, changed the API - we want to use this without storing
  the private key in a keystore."
  [keystring]
  (let [private-key (string->private-key keystring)
        decryption-cred (doto (new BasicX509Credential)
                          (.setPrivateKey private-key))
        decrypter (new Decrypter
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
       [:md:EntityDescriptor {:xmlns:md  "urn:oasis:names:tc:SAML:2.0:metadata",
                              :ID  (ss/replace acs-uri #"[:/]" "_") ,
                              :entityID  app-name}
        [:md:SPSSODescriptor
         (cond-> {:AuthnRequestsSigned "true",
                  :WantAssertionsSigned "true",
                  :protocolSupportEnumeration "urn:oasis:names:tc:SAML:2.0:protocol"}
           (not sign-request?) (dissoc :AuthnRequestsSigned))
         [:md:KeyDescriptor  {:use  "signing"}
          [:ds:KeyInfo  {:xmlns:ds  "http://www.w3.org/2000/09/xmldsig#"}
           [:ds:X509Data
            [:ds:X509Certificate certificate-str]]]]
         [:md:KeyDescriptor  {:use  "encryption"}
          [:ds:KeyInfo  {:xmlns:ds  "http://www.w3.org/2000/09/xmldsig#"}
           [:ds:X509Data
            [:ds:X509Certificate certificate-str]]]]
         [:md:NameIDFormat  "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"]
         [:md:NameIDFormat  "urn:oasis:names:tc:SAML:2.0:nameid-format:transient"]
         [:md:NameIDFormat  "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent"]
         [:md:NameIDFormat  "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified"]
         [:md:NameIDFormat  "urn:oasis:names:tc:SAML:1.1:nameid-format:X509SubjectName"]
         [:md:AssertionConsumerService  {:Binding  "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST", :Location acs-uri, :index  "0", :isDefault  "true"}]]]))))

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

(defn make-acs-uri [domain]
  (format "%s/api/saml/ad-login/%s" (env/value :host) domain))

(defn resolve-roles
  "Takes a map of corresponding roles (key = role in Lupis, value is AD-group)
  and a seq of user roles from the SAML, returns a set of corresponding LP roles."
  [org-roles ad-params]
  (let [ad-roles-set (if (string? ad-params) #{ad-params} (set ad-params))
        orgAuthz     (for [[lp-role ad-role] org-roles]
                       (when (ad-roles-set ad-role)
                         (name lp-role)))]
    (->> orgAuthz (remove nil?) set)))

(defn resolve-authz
  "Takes a sequence of ad-settings (as returned by `lupapalvelu.organization/get-organizations-by-ad-domain`)
  and a sequence of ad-groups received in the SAML response, returns a parsed orgAuthz element."
  [ad-settings ad-groups]
  (into {} (for [org-setting ad-settings
                 :let [{:keys [id ad-login]} org-setting
                       resolved-roles (resolve-roles (:role-mapping ad-login) ad-groups)]
                 :when (:enabled ad-login)]
             [(keyword id) resolved-roles])))

(defn parse-assertions
  "Assertions in the given SAML response. Returns the user-related assertions or nil, if the
  assertions are not found or the response is not valid. A valid SAML response either must
  be signed OR its every assertion must be signed. `idp-cert` is the IdP certificate for
  the correspnding signing (private) key. `decrypter` is an
  `org.opensaml.saml2.encryption.Decrypter` instance for the encrypted assertions (if
  there are any). Sample return value:

  {:Group ['GG_Lupapiste_RAVA_read'
           'GG_Lupapiste_RAVA_Arkistonhoitaja'],
   :emailaddress 'terttu.panaani@pori.fi',
   :givenname 'Terttu',
   :name 'Panaani Terttu',
   :surname 'Panaani'}"
  ([saml-resp idp-cert sp-private-key]
   {:pre [saml-resp idp-cert sp-private-key]}
   (try
     (when (:success? (saml-sp/parse-saml-resp-status saml-resp))
       (let [idp-pubkey       (-> idp-cert certificate-x509 jcert->public-key)
             public-creds     (doto (new BasicX509Credential)
                                (.setPublicKey idp-pubkey))
             validator        (new SignatureValidator public-creds)
             valid-signature? (fn [obj]
                                (try
                                  (when-let [sig (.getSignature obj)]
                                    (nil? (.validate validator sig)))
                                  (catch Exception e
                                    (error "Signature not valid:" (.getMessage e)))))
             decrypter   (doto (make-saml-decrypter sp-private-key)
                           ;; https://stackoverflow.com/questions/24364686
                           (.setRootInNewDocument true))
             assertions (->> (.getEncryptedAssertions saml-resp)
                             (map #(.decrypt decrypter %))
                             (concat (.getAssertions saml-resp)))]
         (when (or (valid-signature? saml-resp)
                   (every? valid-signature? assertions))
           (->> assertions
                (map saml-sp/parse-saml-assertion)
                parse-saml-info
                :attrs
                not-empty))))
     (catch Exception e
       (error "Parse assertions failed:" e))))
  ([saml-resp idp-cert]
   (parse-assertions saml-resp idp-cert (:private-key ad-config))))

(defn valid-email
  "Returns canonized `email` or nil if the address is not in the valid format."
  [email]
  (let [email (ss/canonize-email email)]
    (when-not (sc/check ssc/Email email)
      email)))

(defn email-domain
  "Domain part of correctly formatted `email` address."
  [email]
  (some-> (valid-email email)
          (ss/split #"@")
          last))
