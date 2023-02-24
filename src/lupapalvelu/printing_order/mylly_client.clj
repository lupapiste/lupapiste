(ns lupapalvelu.printing-order.mylly-client
  (:require [cljstache.core :as clostache]
            [clj-time.core :as t]
            [clojure.data.codec.base64 :as base64]
            [clojure.walk :as walk]
            [plumbing.core :refer [for-map]]
            [schema.core :as sc]
            [slingshot.slingshot :refer [throw+ try+]]
            [slingshot.support :refer [rethrow]]
            [taoensso.timbre :as timbre]
            [sade.common-reader :as cr]
            [sade.env :as env]
            [sade.http :as http]
            [sade.xml :as xml]
            [lupapalvelu.json :as json]
            [lupapalvelu.printing-order.domain :as domain])
  (:import [java.io ByteArrayOutputStream]
           [org.joda.time DateTime]))

(defn parse-soap [response]
  (some-> response
          xml/parse
          cr/strip-xml-namespaces
          xml/xml->edn
          (#(if (contains? % :Envelope) (:Envelope %) %))))
;;
;; common
;;
(def ^:dynamic *log-request* false)

(defmacro with-logging [& body]
  `(binding [*log-request* true]
     ~@body))

(defn convert-values-to-message [form]
  (walk/prewalk (fn [x]
                  (if (map? x)
                    (for-map [[k v] x]
                      k (cond
                          (keyword? v) (name v)
                          (instance? Boolean v) (if v "1" "0")
                          (= "" v) " "
                          :else v)) x)) form))

(defn render [action data]
  (let [file (subs (str action) 1)]
    (clostache/render-resource
      (str "printing-order/mylly/" file ".xml")
      (convert-values-to-message data))))


(defn post [url action-ns action xml]
  (http/post url {:content-type (str "text/xml;charset=UTF-8")
                  :headers {"SOAPAction" (str action-ns "/" (name action))}
                  :throw-exceptions false
                  :body xml}))

(defn call* [url action-ns action data]
  (let [time (System/currentTimeMillis)
        request (render action data)
        {:keys [body status]} (post url action-ns action request)
        total-time (- (System/currentTimeMillis) time)]
    (when-not (= status 200)
      (timbre/error (pr-str (json/encode {:action action, :time total-time, :status status, :body body})))
      (throw+ {:type ::error :error-path [:mylly :unknown]} (str "SOAP status " status) body))
    (let [result (parse-soap body)]
      (timbre/info (pr-str (json/encode {:action action, :time total-time})))
      (-> result :Body vals first))))

(defn encode-file-from-stream [orig]
  (with-open [output (ByteArrayOutputStream.)]
    (base64/encoding-transfer orig output)
    (.toString output)))

(def authentication-service-ns
  "http://kopijyva.fi/OrderingSystem.AuthenticationService/IAuthenticationService")

(def authentication-service-url
  (-> (env/get-config) :printing-order :mylly :auth :url))

(def order-service-ns
  "http://kopijyva.fi/OrderingSystem.IntegrationService/IOrderService")

(def order-service-url
  (-> (env/get-config) :printing-order :mylly :order :url))

(defn in-dummy-mode? []
  (get-in (env/get-config) [:printing-order :mylly :dummy-mode]))

(defn fetch-login-token! []
  (:LoginResult (call* authentication-service-url authentication-service-ns :Login
                       (select-keys (-> (env/get-config) :printing-order :mylly)
                                    [:username :password]))))

(defn send-order! [url token data]
  (call* url order-service-ns :AddOrder (assoc data :token token)))

(defn order-too-large? [{:keys [files]}]
  (let [sizes (map :size files)]
    (loop [sum   0
           coll  sizes]
      (cond
        (empty? coll)                              false
        (> (+ sum (first sizes)) (* 1024 1048576)) true
        :else                                      (recur (+ sum (first sizes)) (rest coll))))))

(sc/defn login-and-send-order! [order :- domain/PrintingOrder]
  (if (in-dummy-mode?)
    {:ok          true
     :orderNumber (str "dev-" (.getMillis ^DateTime (t/now)))}
    (if (order-too-large? order)
      (do
        (timbre/error "order-too-large")
        {:ok false
         :reason :error.order-too-large})
      (try+
        (let [token  (fetch-login-token!)
              result (send-order! order-service-url token order)]
          {:ok true
           :orderNumber (get-in result [:AddOrderResult :Order :CAD :SonetOrderNumber])})
        (catch [:type ::error] []
          (timbre/error "login-and-send-order failed")
          {:ok false})))))
