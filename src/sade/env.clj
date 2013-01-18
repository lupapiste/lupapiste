(ns sade.env
  (:use [sade.log]))

(def mode (keyword (or (System/getProperty "lupapiste.mode") (System/getenv "LUPAPISTE_MODE") "dev")))
(def port (Integer/parseInt (or (System/getProperty "lupapiste.port") (System/getenv "LUPAPISTE_PORT") "8000")))

(debug "Server mode" (name mode) ", port" port)

(defn dev-mode? []
  (= :dev mode))

(defmacro in-dev [& body]
  `(if (dev-mode?)
     (do ~@body)))
