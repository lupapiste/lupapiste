(ns lupapalvelu.attachment-itest
  (:use [lupapalvelu.attachment]
        [midje.sweet]
        [clojure.walk :only [keywordize-keys]])
  (:require [clojure.string :as s]
            [clj-http.client :as c]
            [cheshire.core :as json]))

;;
;; Integration test utils:
;; - these should be in some iutil.clj etc.
;;

(def pena "602cb9e58426c613c8b85abc")
(def veikko "5051ba0caa2480f374dcfeff")

(defn query [apikey query-name & args]
  (let [resp (c/get
               (str "http://localhost:8000/api/query/" (name query-name))
               {:headers {"authorization" (str "apikey=" apikey)
                          "accepts" "application/json;charset=utf-8"}
                :query-params (apply hash-map args)})]
    (if (= (:status resp) 200)
      (-> (:body resp) (json/decode) (keywordize-keys)))))

(defn command [apikey command-name & args]
  (let [resp (c/post
               (str "http://localhost:8000/api/command/" (name command-name))
               {:headers {"authorization" (str "apikey=" apikey)
                          "content-type" "application/json;charset=utf-8"}
                :body (json/encode (apply hash-map args))})]
    (if (= (:status resp) 200)
      (-> (:body resp) (json/decode) (keywordize-keys)))))

;;
;; Integration tests:
;;


(fact
  (let [application-id (:id (command :create-application :x 12 :y 34 :street "s" :city "c" :zip "z" :schemas ["hakija"]))
        application (:application (query :application :id "50b35e18e508563e19b54460"))]
    (nil? (:attachments application)) => falsey
    (empty? (:attachments application)) => truthy))

#_(defn add-attachment [doc-id]
  (update-or-create-attachment
    doc-id
    nil
    {:type-group "tg" :type-id "tid"}
    "file-id"
    "filename"
    "content-type"
    1111 ; size
    2222 ; created
    pena))

