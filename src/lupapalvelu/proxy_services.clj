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

;;
;; Proxy services by name:
;;

(def services {"nls" nls})
