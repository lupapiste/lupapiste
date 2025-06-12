(ns lupapalvelu.dummy-krysp-service
  (:require [clojure.java.io :as io]
            [hiccup.core :as core]
            [lupapalvelu.api-common :refer [basic-401]]
            [lupapalvelu.http.noir.util :as noir-util]
            [lupapalvelu.integrations.messages :as imessages]
            [lupapalvelu.json :as json]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.util-components :as components]
            [net.cgrand.enlive-html :as enlive]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [ring.util.request :as ring-request]
            [sade.env :as env]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [schema.core :as sc]))

(sc/defschema DummyVerdict
  {:type-name                         sc/Str
   :template-file                     sc/Str
   (sc/optional-key :kuntalupatunnus) (sc/maybe sc/Str)
   (sc/optional-key :asiointitunnus)  (sc/maybe sc/Str)
   })

;; Dummy KRYSP Service that also enables the variation of the interface responses according to various parameters.
;;
;; Also includes a simple HTML form to configure additional override configurations,
;; for example, to force the service to return a different verdict XML per application or municipal verdict id.

(when (env/feature? :dummy-krysp)

  (def default-dummy-verdicts [{:type-name "rakval:ValmisRakennus", :kuntalupatunnus nil, :template-file "krysp/dev/building.xml"}
                               {:type-name "rakval:RakennusvalvontaAsia", :kuntalupatunnus nil, :template-file "krysp/dev/verdict.xml"}
                               {:type-name "rakval:RakennusvalvontaAsia", :kuntalupatunnus nil, :template-file "krysp/dev/verdict-foreman-v3.xml", :foreman-verdict true}
                               {:type-name "rakval:RakennusvalvontaAsia", :kuntalupatunnus "14-0241-R 3", :template-file "krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml"}
                               {:type-name "rakval:RakennusvalvontaAsia", :kuntalupatunnus "895-2015-001", :template-file "krysp/dev/verdict-rakval-with-area-like-location.xml"}
                               {:type-name "rakval:RakennusvalvontaAsia", :kuntalupatunnus "895-2015-002", :template-file "krysp/dev/verdict-rakval-with-building-location.xml"}
                               {:type-name "rakval:RakennusvalvontaAsia", :kuntalupatunnus "475-2016-001", :template-file "krysp/dev/verdict-rakval-missing-location.xml"}
                               {:type-name "ymy:Ymparistolupa", :kuntalupatunnus nil, :template-file "krysp/dev/verdict-yl.xml"}
                               {:type-name "ymm:MaaAineslupaAsia", :kuntalupatunnus nil, :template-file "krysp/dev/verdict-mal.xml"}
                               {:type-name "ymv:Vapautus", :kuntalupatunnus nil, :template-file "krysp/dev/verdict-vvvl.xml"}
                               {:type-name "ppst:Poikkeamisasia,ppst:Suunnittelutarveasia", :kuntalupatunnus nil, :template-file "krysp/dev/verdict-p.xml"}
                               {:type-name "kiito:every-type", :kuntalupatunnus nil, :template-file "krysp/dev/verdict-kt.xml"}
                               {:type-name "ymi:melutarina,ymi:koeluontoinentoiminta,ymi:poikkeus" :kuntalupatunnus nil :template-file "krysp/dev/verdict-yi.xml"}])

  (defonce dummy-verdicts (atom default-dummy-verdicts))

  (defn verdict-xml [typeName & [search-key search-val]]
    (let [verdicts-by-type (filter (comp #{typeName} :type-name) @dummy-verdicts)]
      (-> (or
            (when (and search-val search-key) (util/find-by-key search-key search-val verdicts-by-type))
            ;; presumably foreman document id
            (when (and (= :asiointitunnus search-key)
                       (not (v/application-id? search-val)))
              (util/find-first :foreman-verdict @dummy-verdicts))
            (first verdicts-by-type))
          :template-file)))

  (defn override-xml [xml-file overrides]
    (let [xml            (enlive/xml-resource xml-file)
          overridden-xml (reduce (fn [nodes override]
                                   (enlive/transform nodes
                                                     (->> (:selector override)
                                                          (map keyword))
                                                     (enlive/content (:value override))))
                                 xml overrides)]
      (apply str (enlive/emit* overridden-xml))))

  (defn- fix-urls
    "Update hard-coded URLs (e.g. `http://localhost:8000`) to match the running system."
    [document system]
    (let [port (components/system->http-port system)]
      (ss/replace document #"localhost:8000" (str "localhost:" port))))

  (defpage "/dev/krysp" {typeName :typeName r :request filter :filter overrides :overrides}
    (let [system (noir-util/get-system)]
      (if-not (ss/blank? typeName)
        (let [filter-type-name (-> filter sade.xml/parse (sade.common-reader/all-of [:PropertyIsEqualTo :PropertyName]))
              search-literal   (-> filter sade.xml/parse (sade.common-reader/all-of [:PropertyIsEqualTo :Literal]))
              search-key       (case filter-type-name
                                 "rakval:luvanTunnisteTiedot/yht:LupaTunnus/yht:kuntalupatunnus"                         :kuntalupatunnus
                                 "rakval:luvanTunnisteTiedot/yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus" :asiointitunnus
                                 nil)
              typeName         (if (ss/starts-with? typeName "kiito:") "kiito:every-type" typeName)
              overrides        (json/decode overrides true)]
          (cond
            (not-empty overrides)
            (resp/content-type "application/xml; charset=utf-8" (-> (override-xml (io/resource (verdict-xml typeName)) overrides)
                                                                    (fix-urls system)))

            (and (#{:kuntalupatunnus :asiointitunnus} search-key) search-literal)
            (resp/content-type "application/xml; charset=utf-8" (-> (slurp (io/resource (verdict-xml typeName search-key search-literal)))
                                                                    (fix-urls system)))

            :else
            (resp/content-type "application/xml; charset=utf-8" (-> (slurp (io/resource (verdict-xml typeName)))
                                                                    (fix-urls system)))))
        (when (= r "GetCapabilities")
          (resp/content-type "application/xml; charset=utf-8" (-> (slurp (io/resource "krysp/dev/capabilities.xml"))
                                                                  (fix-urls system)))))))

  (defpage [:post "/dev/krysp"] {}
    (let [system    (noir-util/get-system)
          xml       (sade.xml/parse (io/input-stream (:body (request/ring-request))))
          xml-no-ns (sade.common-reader/strip-xml-namespaces xml)
          typeName  (sade.xml/select1-attribute-value xml-no-ns [:Query] :typeName)]
      (when (= typeName "yak:Sijoituslupa,yak:Kayttolupa,yak:Liikennejarjestelylupa,yak:Tyolupa")
        (resp/content-type "application/xml; charset=utf-8" (-> (slurp (io/resource "krysp/dev/verdict-ya.xml"))
                                                                (fix-urls system))))))

  (defpage [:post "/dev/krysp/manager/add"] {} ;;
    (let [request                                                                                        (request/ring-request)
          {typeName :new-type-name filterKey :new-filter-key filterValue :new-filter file :verdict-file} (util/map-keys keyword (:form-params request))]
      (swap! dummy-verdicts conj (merge {:type-name     typeName
                                         :template-file file}
                                        (when (and filterValue (#{"kuntalupatunnus"} filterKey))
                                          {:kuntalupatunnus filterValue})
                                        (when (and filterValue (#{"asiointitunnus"} filterKey))
                                          {:asiointitunnus filterValue})))
      (resp/redirect "/dev/krysp/manager")))

  (defpage [:get "/dev/krysp/manager"] {}
    (core/html [:html
                [:head [:title "Dummy Dev Krysp Manager"]
                 [:script {:src "/lp-static/lib/jquery-3.7.1.min.js"}]
                 [:script {:src "/app/0/common.js?lang=fi"}]]
                [:body {:style "background-color: #fbc742; padding: 4em; font-size: 14px; font-family: Courier"}
                 [:h3 "Dummy Dev Krysp Manager"]
                 [:form {:action "/dev/krysp/manager/add" :method :post}
                  [:div
                   [:table {:border "1px"}
                    [:thead
                     [:tr
                      [:th "typeName"]
                      [:th "filter"]
                      [:th "verdict file"]]
                     [:tr
                      [:td [:input {:type "text" :name "new-type-name"}]]
                      [:td [:select {:name "new-filter-key"}
                            [:option {:value "kuntalupatunnus"} "kuntalupatunnus = "]
                            [:option {:value "asiointitunnus"} "asiointitunnus = "]]
                       [:input {:type "text" :name "new-filter"}]]
                      [:td [:input {:type "text" :name "verdict-file"}]]
                      [:td [:button {:type "submit"} "Add"]]]
                     (map (fn [{:keys [type-name kuntalupatunnus template-file]}]
                            [:tr
                             [:td type-name]
                             [:td (when kuntalupatunnus
                                    (str "kuntalupatunnus = " kuntalupatunnus))]
                             [:td template-file]
                             [:td " "]]) @dummy-verdicts)]]]]]]))

  (defn dummy-dmcity-authentication
    "Returns nil when authed, 401 Unauthorized response otherwise"
    [request ctx]
    (let [good  {"x-vault"    "test-vault"
                 "x-username" "test-user"
                 "x-password" "test-secret"}
          creds (select-keys (:headers request)
                             ["x-vault" "x-username" "x-password"])]
      (when-not (= good creds)
        (log/warnf "Dummy dmcity: Bad credentials for %s: %s"
                   ctx creds)
        (resp/status 401 "Unauthorized"))))

  ;; Dummy dmcity endpoint.
  ;; Returns 200 OK if the request contains the correct authentication headers.
  ;; Otherwise 401 Unauthorized.
  (defpage [:any "/dev/dummy-dmcity/:target"] {:keys [target]}
    (or (dummy-dmcity-authentication (request/ring-request) target)
        (do (log/infof "Dummy dmcity: Good credentials for %s." target)
            (resp/status 200 "OK"))))

  (defpage [:post "/dev/dummy-dmcity/endpoint/:target"] {:keys [target]}
    (let [request (request/ring-request)]
      (or (dummy-dmcity-authentication request target)
          (let [body-str (ring-request/body-string request)]
            (log/info "Message received for" target)
            (imessages/save (merge
                              {:id           (mongo/create-id)
                               :direction    "in"
                               :transferType "http"
                               :created      (sade.core/now)
                               :status       "received"
                               :data         {:target target
                                              :body   body-str}}
                              (if (ss/starts-with? body-str "<?xml")
                                {:messageType (str "KuntaGML " target)
                                 :format      "xml"
                                 :application {:id (re-find #"L[PX]-\d{3}-\d{4}-\d{5}" body-str)}}
                                {:messageType (str "state-change: " target)
                                 :format      "json"
                                 :application (select-keys (json/decode body-str true) [:id])})))
            (resp/status 200 "OK")))))

  (defpage [:get "/dev/dummylink/:vault/:application-id"] {:keys [vault application-id]}
    (core/html [:html
                [:head [:title "Dummy dmcity"]]
                [:body {:style "padding: 2em; background-color: #5287aa; color: white"}
                 [:pre " ________  _____ ______   ________  ___  _________    ___    ___      ________  ___  __        ___    ___ ___       ___  ________   _______\n|\\   ___ \\|\\   _ \\  _   \\|\\   ____\\|\\  \\|\\___   ___\\ |\\  \\  /  /|    |\\   ____\\|\\  \\|\\  \\     |\\  \\  /  /|\\  \\     |\\  \\|\\   ___  \\|\\  ___ \\\n\\ \\  \\_|\\ \\ \\  \\\\\\__\\ \\  \\ \\  \\___|\\ \\  \\|___ \\  \\_| \\ \\  \\/  / /    \\ \\  \\___|\\ \\  \\/  /|_   \\ \\  \\/  / | \\  \\    \\ \\  \\ \\  \\\\ \\  \\ \\   __/|\n \\ \\  \\ \\\\ \\ \\  \\\\|__| \\  \\ \\  \\    \\ \\  \\   \\ \\  \\   \\ \\    / /      \\ \\_____  \\ \\   ___  \\   \\ \\    / / \\ \\  \\    \\ \\  \\ \\  \\\\ \\  \\ \\  \\_|/__\n  \\ \\  \\_\\\\ \\ \\  \\    \\ \\  \\ \\  \\____\\ \\  \\   \\ \\  \\   \\/  /  /        \\|____|\\  \\ \\  \\\\ \\  \\   \\/  /  /   \\ \\  \\____\\ \\  \\ \\  \\\\ \\  \\ \\  \\_|\\ \\\n   \\ \\_______\\ \\__\\    \\ \\__\\ \\_______\\ \\__\\   \\ \\__\\__/  / /            ____\\_\\  \\ \\__\\\\ \\__\\__/  / /      \\ \\_______\\ \\__\\ \\__\\\\ \\__\\ \\_______\\\n    \\|_______|\\|__|     \\|__|\\|_______|\\|__|    \\|__|\\___/ /            |\\_________\\|__| \\|__|\\___/ /        \\|_______|\\|__|\\|__| \\|__|\\|_______|\n                                                    \\|___|/             \\|_________|         \\|___|/"]
                 [:h2 (format "Application %s (vault %s)" application-id vault)]]]))

  (defpage [:get "/dev/private-krysp"] []
    (let [request (request/ring-request)
          [u p]   (http/decode-basic-auth request)]
      (if (= u p "pena")
        (resp/content-type "application/xml; charset=utf-8"
                           (slurp (io/resource "krysp/dev/capabilities.xml")))
        basic-401))))
