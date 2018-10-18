(ns lupapalvelu.ident.ad-login-util
  (:require [taoensso.timbre :refer [debug infof warn error errorf]]
            [hiccup.core :as hiccup]
            [noir.core :refer [defpage]]
            [monger.operators :refer [$set]]
            [ring.util.response :refer :all]
            [sade.core :refer [def-]]
            [sade.env :as env]
            [sade.strings :as ss]
            [saml20-clj.xml :as saml-xml])
  (:import [org.apache.xml.security Init]
           [org.apache.xml.security.utils Constants ElementProxy]
           [org.apache.xml.security.transforms Transforms]
           [org.apache.xml.security.c14n Canonicalizer]
           [java.io ByteArrayInputStream]
           [java.util Base64]))

(defn parse-certificate
  "Strip the -----BEGIN CERTIFICATE----- and -----END CERTIFICATE----- headers and newlines
  from certificate."
  [certstring]
  (ss/replace certstring #"[\n ]|(BEGIN|END) CERTIFICATE|(BEGIN|END) PRIVATE KEY|-{5}" ""))

(defn string->certificate [certstring]
  (let [cf (java.security.cert.CertificateFactory/getInstance "X.509")
        bytestream (ByteArrayInputStream. (.getBytes certstring))]
    (.generateCertificate cf bytestream)))

(defn string->private-key [keystring]
  (let [kf (java.security.KeyFactory/getInstance "RSA")
        pksc8EncodedBytes (.decode (Base64/getDecoder) (parse-certificate keystring))
        keySpec (java.security.spec.PKCS8EncodedKeySpec. pksc8EncodedBytes)]
    (.generatePrivate kf keySpec)))

(defn make-saml-signer
  "Ported from kirasystems/saml20-clj, changed the API - we want to use this without storing
  the private key in a keystore."
  [certstring keystring]
  (Init/init)
  (ElementProxy/setDefaultPrefix Constants/SignatureSpecNS "")
  (let [private-key (string->private-key keystring)
        cert (string->certificate certstring)
        sig-algo org.apache.xml.security.signature.XMLSignature/ALGO_ID_SIGNATURE_RSA_SHA256]
    ;; https://svn.apache.org/repos/asf/santuario/xml-security-java/trunk/samples/org/apache/xml/security/samples/signature/CreateSignature.java
    ;; http://stackoverflow.com/questions/2052251/is-there-an-easier-way-to-sign-an-xml-document-in-java
    ;; Also useful: http://www.di-mgt.com.au/xmldsig2.html
    (fn sign-xml-doc [xml-string]
      (let [xmldoc (saml-xml/str->xmldoc xml-string)
            transforms (doto (new Transforms xmldoc)
                         (.addTransform Transforms/TRANSFORM_ENVELOPED_SIGNATURE)
                         (.addTransform Transforms/TRANSFORM_C14N_EXCL_OMIT_COMMENTS))
            sig (new org.apache.xml.security.signature.XMLSignature xmldoc nil sig-algo
                     Canonicalizer/ALGO_ID_C14N_EXCL_OMIT_COMMENTS)
            canonicalizer (Canonicalizer/getInstance Canonicalizer/ALGO_ID_C14N_EXCL_OMIT_COMMENTS)]
        (.. xmldoc
            (getDocumentElement)
            (appendChild (.getElement sig)))
        (doto sig
          (.addDocument "" transforms Constants/ALGO_ID_DIGEST_SHA1)
          (.addKeyInfo cert)
          (.addKeyInfo (.getPublicKey cert))
          (.sign private-key))
        (String. (.canonicalizeSubtree canonicalizer xmldoc) "UTF-8")))))

(defn make-saml-decrypter
  "Ported from kirasystems/saml20-clj, changed the API - we want to use this without storing
  the private key in a keystore."
  [keystring]
  (let [private-key (string->private-key keystring)
        decryption-cred (doto (new org.opensaml.xml.security.x509.BasicX509Credential)
                          (.setPrivateKey private-key))
        decrypter (new org.opensaml.saml2.encryption.Decrypter
                       nil
                       (new org.opensaml.xml.security.keyinfo.StaticKeyInfoCredentialResolver decryption-cred)
                       (new org.opensaml.xml.encryption.InlineEncryptedKeyResolver))]
    decrypter))

(defn metadata
  "Ported from kirasystems/saml20-clj, removed 'http://example.com/SingleLogout' endpoint"
  ([app-name acs-uri certificate-str sign-request?]
   (str
     (hiccup.page/xml-declaration "UTF-8")
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
