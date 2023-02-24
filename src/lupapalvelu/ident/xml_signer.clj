(ns lupapalvelu.ident.xml-signer
  "A namespace for creating XML signing functions.
   Separated from ad-login-util for use in both ad-login and suomifi messages (LPK-4219)."
  (:require
   [lupapalvelu.ident.ad-login-util :as ad-util]
   [saml20-clj.xml :as saml-xml])
  (:import [org.apache.xml.security Init]
           [org.apache.xml.security.utils Constants ElementProxy]
           [org.apache.xml.security.transforms Transforms]
           [org.apache.xml.security.c14n Canonicalizer]
           [org.apache.ws.security.message WSSecSignature WSSecHeader WSSecTimestamp]
           [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.security.cert X509Certificate]
           [org.apache.xml.security.signature XMLSignature]
           [org.apache.ws.security WSConstants WSEncryptionPart WSSConfig WsuIdAllocator]
           [org.w3c.dom Document]
           [org.apache.ws.security.components.crypto CertificateStore]
           [org.apache.ws.security.util WSTimeSource]
           [org.apache.axis2.transport TransportUtils]
           [org.apache.axis2.context MessageContext]
           [org.apache.axis2.saaj.util SAAJUtil]
           [org.apache.axis2.builder SOAPBuilder]))

(defn make-xml-signer
  "Ported from kirasystems/saml20-clj, changed the API - we want to use this without storing
  the private key in a keystore. Note that there is a separate version for signing XML documents with SOAP envelopes
  called make-xml-signer-soap"
  [certstring keystring]
  (Init/init)
  (ElementProxy/setDefaultPrefix Constants/SignatureSpecNS "")
  (let [private-key (ad-util/string->private-key keystring)
        cert        (ad-util/string->certificate certstring)
        sig-algo    XMLSignature/ALGO_ID_SIGNATURE_RSA_SHA256]
    (fn sign-xml-doc [xml-string]
      (let [^Document xmldoc (saml-xml/str->xmldoc xml-string)
            transforms (doto (new Transforms xmldoc)
                         (.addTransform Transforms/TRANSFORM_ENVELOPED_SIGNATURE)
                         (.addTransform Transforms/TRANSFORM_C14N_EXCL_OMIT_COMMENTS))
            sig (new XMLSignature xmldoc nil sig-algo
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
        (with-open [bos (ByteArrayOutputStream.)]
          (.canonicalizeSubtree canonicalizer xmldoc bos)
          (String. (.toByteArray bos) "UTF-8"))))))

(defn create-crypto-proxy
  "Creates a proxy of a class instance that implements the Java Crypto interface.
   Used for circumventing the use of Java keystores.
   Extends CertificateStore because it is the simplest existing implementation of the interface."
  [certstring keystring]
  (let [certificate (ad-util/string->certificate certstring)
        private-key (ad-util/string->private-key keystring)]
    (proxy [CertificateStore] [(into-array X509Certificate [certificate])]
      (getPrivateKey [^String _ ^String _] private-key))))

(defn- ^Document get-unsigned-soap-doc
  "Helper function for make-xml-signer-soap"
  [xml-string]
  (with-open [is (ByteArrayInputStream. (.getBytes xml-string))]
    (-> (TransportUtils/createSOAPMessage (MessageContext.) is "text/xml" (SOAPBuilder.))
        (SAAJUtil/getDocumentFromSOAPEnvelope))))

(defn sign-soap-envelope-xml-string
  "XML signer for SOAP messages, especially for the Suomi.fi viestit service. Returns a string containing the
   XML SOAP envelope with the signature.
   For test use `:current-inst` and `:id-allocator` can be provided in the options map to get a fixed result.
   `:current-inst` must be a java.util.Date, while `:id-allocator` must implement WsuIdAllocator
   "
  ([certstring keystring soap-xml-string]
   (sign-soap-envelope-xml-string certstring keystring {} soap-xml-string))
  ([certstring keystring {:keys [current-inst id-allocator]} soap-xml-string]
   (let [wss-config (WSSConfig/getNewInstance)
         _          (when current-inst
                      ;; For test use or otherwise outside time source. Default will be the instant when timestamp is generated.
                      (.setCurrentTime wss-config (reify WSTimeSource
                                                    (now [_]
                                                      current-inst))))
         _          (when id-allocator
                      ;; For test use, provide a stable ID generator instead of using the default UUIDs
                      (.setIdAllocator wss-config ^WsuIdAllocator id-allocator))
         c14n       (Canonicalizer/getInstance Canonicalizer/ALGO_ID_C14N_WITH_COMMENTS)
         cert       (ad-util/string->certificate certstring)
         crypto     (create-crypto-proxy certstring keystring)
         xml-doc    (get-unsigned-soap-doc soap-xml-string)
         ts         (doto (WSSecTimestamp. wss-config)
                      (.setTimeToLive 60))
         ts-part    (WSEncryptionPart. "Timestamp" WSConstants/WSU_NS "")
         body-part  (WSEncryptionPart. WSConstants/ELEM_BODY WSConstants/URI_SOAP11_ENV "")
         header     (doto (WSSecHeader.)
                      (.insertSecurityHeader xml-doc))
         signature  (doto (WSSecSignature. wss-config)
                      (.setUserInfo "" "") ; No need for actual credentials due to crypto-proxy
                      (.setKeyIdentifierType WSConstants/BST_DIRECT_REFERENCE)
                      (.setX509Certificate cert)
                      (.setUseSingleCertificate true)
                      (.setParts [ts-part body-part]))]
     (with-open [bos (ByteArrayOutputStream.)]
       (as-> xml-doc doc
             (.build ts doc header)
             (.build signature doc crypto header)
             (.canonicalizeSubtree c14n doc bos))
       (String. (.toByteArray bos) "UTF-8")))))
