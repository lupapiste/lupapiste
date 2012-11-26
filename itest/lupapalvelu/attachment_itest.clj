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
  (let [resp (command pena :create-application :x 408048 :y 6693225 :street "s" :city "c" :zip "z" :schemas ["hakija"])
        application-id (:id resp)
        application (:application (query pena :application :id application-id))]
    (nil? (:attachments application)) => falsey
    (empty? (:attachments application)) => truthy
    (let [resp (command veikko :create-attachment
                 :id application-id
                 :attachmentType {:type-group "tg"
                                  :type-id "tid"})
          attachment-id (:attachmentId resp)
          application (:application (query pena :application :id application-id))]
      (nil? attachment-id) => falsey
      (count (:attachments application)) => 1
      (first (:attachments application)) => (contains
                                              {:type {:type-group "tg"
                                                      :type-id "tid"}
                                               :state "requires_user_action"
                                               :latestVersion {:version {:minor 0 :major 0}}
                                               :versions []}))))
