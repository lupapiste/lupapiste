(ns lupapalvelu.visma-sign.dummy-visma-sign-server
  (:require [hiccup.core :refer [html]]
            [hiccup.page :refer [html5]]
            [lupapalvelu.http.noir.util :as noir-util]
            [lupapalvelu.json :as json]
            [lupapalvelu.logging :refer [warn]]
            [lupapalvelu.security :refer [random-password]]
            [noir.core :refer [defpage]]
            [noir.response :as resp]))

(set! *warn-on-reflection* true)

(warn "Starting Visma Sign dummy server...")

(defonce *data (atom {:documents {} :files {}}))

(defn sign-page [{:keys [files status invitations] document-name :name}]
  (html
    (html5
      [:head
       [:title "Visma Sign dummy service"]
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
       [:h2 "Document: " [:span document-name]]
       [:table
        [:tr [:td "Status"] [:td status]]
        [:tr [:td "File"] [:td (-> files first :filename)]]
        [:tr [:td "Invitation id"] [:td (-> invitations first :uuid)]]
        [:tr [:td "Invitation passphrase"] [:td (-> invitations first :passphrase)]]]
       [:button
        {:onclick (str "fetch('/dev/dummy-visma-sign/api/v1/document/" (-> files first :filename) "/sign', {method: 'POST'})
                  .then(response => { if (response.ok) window.location.reload();})")}
        "Sign document"]
       [:button
        {:onclick (str "fetch('/dev/dummy-visma-sign/api/v1/document/" (-> files first :filename) "/cancel', {method: 'POST'})
                  .then(response => { window.location.reload();})")}
        "Cancel/Fail document signing"]
       (when (or (= status :signed)
                 (= status :cancelled))
         [:h3 (str "Document " (name status) " you can now close this window")])])))

(defpage [:post "/dev/dummy-visma-sign/api/v1/document/"]
  {:keys [document headers]}
  (if document
    (let [doc-id (random-password 40)]
      (swap! *data assoc-in [:documents doc-id] {:status :new
                                                 :uuid   doc-id
                                                 :name   (:name document)})
      (->> document
           (resp/status 201)
           (resp/set-headers {"Content-Type" "text/html"
                              "location"     (str (noir-util/hostname) "/api/v1/document/" doc-id)})))
    (->> {:error             "Invalid document"
          :validation_errors [{:path  "[document][name]"
                               :error "This value should not be blank"}]}
         (resp/json)
         (resp/status 404))))

(defpage [:get "/dev/dummy-visma-sign/api/v1/document/:doc-id"]
  {:keys [doc-id]}
  (if-let [doc (get-in @*data [:documents doc-id])]
    (resp/json doc)
    (resp/status 404 "Document not found")))

(defpage [:post "/dev/dummy-visma-sign/api/v1/document/:doc-id/files"]
  {:keys [doc-id filename body] :as req}
  (if (contains? (get @*data :documents) doc-id)
    (do (swap! *data (fn [current-state]
                       (-> current-state
                           (update-in [:documents doc-id :files] conj {:filename filename})
                           (assoc-in [:files filename] body))))
        (->> filename
             (resp/status 201)
             (resp/set-headers {"Content-Type" "application/json"})))
    (resp/status 404 "File not found")))

(defpage [:get "/dev/dummy-visma-sign/api/v1/document/:doc-id/files/0"]
  {:keys [doc-id]}
  (let [data     @*data
        filename (-> data (get-in [:documents doc-id :files]) first :filename)
        pdf      (-> data (get-in [:files filename]))]
    (if pdf
      (->> pdf
           (resp/status 200)
           (resp/set-headers {"Content-Type" "application/pdf"}))
      (resp/status 404 "File not found"))))

(defpage [:post "/dev/dummy-visma-sign/api/v1/document/:doc-id/invitations"]
  [_ {:keys [doc-id]}]
  (if (contains? (get @*data :documents) doc-id)
    (let [invitation {:uuid (str (random-uuid))
                      :passphrase    "12345678"}]
      (swap! *data update-in [:documents doc-id] merge {:invitations [invitation]
                                                        :status      :pending})
      (->> [invitation]
           (json/encode)
           (resp/status 201)
           (resp/set-headers {"Content-Type" "application/json"})))
    (resp/status 404 "Invitation not found")))

(defpage [:get "/dev/dummy-visma-sign/fi/invitation/:uuid/:passphrase"]
  {:keys [uuid passphrase]}
  (let [docs     (vals (get @*data :documents))
        document (->> docs
                      (filter #(let [invitation (-> % :invitations first)]
                                 (and (= (:uuid invitation) uuid)
                                      (= (:passphrase invitation) passphrase))))

                      first)]
    (when document (sign-page document))))

(defpage [:post "/dev/dummy-visma-sign/api/v1/document/:doc-id/sign"]
  {:keys [doc-id]}
  (swap! *data assoc-in [:documents doc-id :status] :signed)
  (->> doc-id (resp/status 200)))

(defpage [:post "/dev/dummy-visma-sign/api/v1/document/:doc-id/cancel"]
  {:keys [doc-id]}
  (swap! *data assoc-in [:documents doc-id :status] :cancelled)
  (->> doc-id (resp/status 200)))
