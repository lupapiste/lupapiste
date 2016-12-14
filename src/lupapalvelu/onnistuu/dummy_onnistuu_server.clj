(ns lupapalvelu.onnistuu.dummy-onnistuu-server
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [noir.request :as request]
            [cheshire.core :as json]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5]]
            [hiccup.form :as form]
            [clj-http.client :as http]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.local :as tl]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.crypt :as c])
  (:import [org.joda.time DateTime DateTimeZone]))

(warn "Starting Onnistuu.fi dummy server...")

(defonce processes (atom {}))

(def now-str
  (let [fmt (partial tf/unparse (tf/formatter "yyyy-MM-dd HH:mm:ss" (DateTimeZone/forID "Europe/Helsinki")))]
    (fn [] (fmt (t/now)))))

(defn process-page [{:strs [stamp] :as process} message]
  (html
    (html5
      [:head
       [:title "Onnistuu.fi dummy service"]
       [:script {:src "/app/latest/cdn-fallback.js"}]
       [:style {:type "text/css"}
        "body {background: #DFDBDB; color: #1D43A5; font-family: sans-serif;}
        tr td:nth-child(2) {font-family: monospace; padding-left: 5px;}
        a, a:visited {text-decoration: underline; color: #1D43A5;}
        a:hover {text-decoration: underline; color: #EC4863;}
        h3 {margin: 25px 0px;}
        .action {padding: 2px 10px;}
        .success {margin: 15px 0px;}"]]
      [:body
       [:h1 "Test company sign service"]
       [:h2 "Status: " [:span {:data-test-id "onnistuu-dummy-status"} message]]
       [:table
        [:tr [:td "stamp"]          [:td {:data-test-id "stamp"}          (get process "stamp")]]
        [:tr [:td "document"]       [:td {:data-test-id "document"}       (get process "document")]]
        [:tr [:td "return-success"] [:td {:data-test-id "return-success"} (get process "return_success")]]
        [:tr [:td "return-failure"] [:td {:data-test-id "return-failure"} (get process "return_failure")]]
        [:tr [:td "type"]           [:td {:data-test-id "type"}           (-> process (get "requirements") first (get "type"))]]
        [:tr [:td "identifier"]     [:td {:data-test-id "identifier"}     (-> process (get "requirements") first (get "identifier"))]]]
       [:form.success {:action (str "/dev/dummy-onnistuu/action/success/" stamp) :method "GET"}
        [:label {:for "company-name"} "Company name:"]
        [:input {:id "company-name" :name "company-name" :type "text" :value "Test company"}]
        [:button {:type "submit" :data-test-id "onnistuu-dummy-success"} "OK"]]
       [:h3 "Debug actions:"]
       (for [s ["fail" "fake"]]
         [:div.action [:a {:href (str "/dev/dummy-onnistuu/action/" s "/" stamp) :data-test-id (str "onnistuu-dummy-" s)} s]])])))

(defpage [:post "/dev/dummy-onnistuu"] {:keys [data iv return_failure customer]}
  (let [headers     (-> request/*request* :headers (select-keys ["cookie"]))
        crypto-iv   (-> iv (c/str->bytes) (c/base64-decode))
        crypto-key  (-> (env/get-config) :onnistuu :crypto-key (c/str->bytes) (c/base64-decode))
        process     (->> data
                         (c/str->bytes)
                         (c/base64-decode)
                         (c/decrypt crypto-key crypto-iv :rijndael)
                         (c/bytes->str)
                         (json/decode))
        process     (assoc process
                           "return_failure" return_failure
                           "uuid" (str (java.util.UUID/randomUUID)))
        document    ^String (process "document")
        ; Only localhost:8000 works as a target in every environment
        url         (str "http://localhost:8000" (subs document (.indexOf document "/" 8)))
        doc-resp    (http/get url {:headers headers, :throw-exceptions false})]
    (if (not= 200 (:status doc-resp))
     (process-page process (str "can't fetch document: " url \space (select-keys doc-resp [:status :body])))
     (do
       (swap! processes assoc (get process "stamp") process)
       (process-page process "ready")))))

(defn respond-success [{:strs [stamp uuid return_success] :as process} company-name]
  (let [crypto-key (-> (env/get-config) :onnistuu :crypto-key (c/str->bytes) (c/base64-decode))
        crypto-iv  (c/make-iv)
        hetu       (-> process (get "requirements") first (get "identifier"))
        data       (->> {:stamp         stamp
                         :document      (str (env/value :host) "/dev/dummy-onnistuu/doc/" stamp)
                         :cancel        "cancel-url-not-used"
                         :signatures    [{:type        :person
                                          :identifier  hetu
                                          :name        company-name
                                          :timestamp   (now-str)
                                          :uuid        uuid}]}
                        (json/encode)
                        (c/str->bytes)
                        (c/encrypt crypto-key crypto-iv :rijndael)
                        (c/base64-encode)
                        (c/bytes->str)
                        (c/url-encode))
        iv         (-> crypto-iv (c/base64-encode) (c/bytes->str) (c/url-encode))]
    (resp/redirect (str return_success "?data=" data "&iv=" iv))))

(defn respond-fail [{:strs [return_failure]}]
  (resp/redirect (str return_failure "?error=666&message=oh%20noes")))

(defn respond-fake [process]
  (process-page process "fake not done yet"))

(defpage [:get "/dev/dummy-onnistuu/action/:action/:stamp"] {:keys [action stamp company-name]}
  (let [process (get @processes stamp)]
    (condp = action
      "success"   (respond-success process company-name)
      "fail"      (respond-fail process)
      "fake"      (respond-fake process))))

(defpage [:get "/dev/dummy-onnistuu/doc/:stamp"] {:keys [stamp]}
  (if-let [pdf (mongo/download-find {:metadata.process.stamp stamp})]
    (let [{:keys [content contentType]} pdf
          document (content)]
      (->> (content) (resp/status 200) (resp/content-type contentType)))
    (resp/status 404 "Not fould")))
