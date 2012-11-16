(ns lupapalvelu.proxy-services
  (:require [clj-http.client :as client]))

;;
;; NLS:
;;

(defn nls [request]
  (client/get "https://ws.nls.fi/rasteriaineistot/image"
    {:query-params (:query-params request)
     :headers {"accept-encoding" (get-in [:headers "accept-encoding"] request)}
     :basic-auth ["***REMOVED***" "***REMOVED***"]
     :as :stream}))

;;
;; Sito "tepa":
;;

(defn tepa [request]
  (client/post "http://tepa.sito.fi/sade/lupapiste/karttaintegraatio/Kunta.asmx/Hae"
    {:body (:body request)
     :content-type :json
     :accept :json}))


;
; Utils:
;

(defn- secure
  "Takes a service function as an argument and returns a proxy function that invokes the original
  function. Proxy function returns what ever the service function returns, excluding some unsafe
  stuff. At the moment strips the 'Set-Cookie' headers."
  [f]
  (fn [request]
    (let [resp (f request)]
      (assoc resp :headers (dissoc (:headers resp) "set-cookie")))))

;;
;; Proxy services by name:
;;

(def services {"nls" (secure nls)
               "tepa" (secure tepa)})
