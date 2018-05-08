(ns lupapalvelu.dummy-krysp-service
  (:require [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal spy]]
            [schema.core :as sc]
            [net.cgrand.enlive-html :as enlive]
            [sade.env :as env]
            [clojure.string :as s]
            [sade.strings :as ss]
            [cheshire.core :as json]
            [noir.response :as resp]
            [clojure.java.io :as io]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [lupapalvelu.api-common :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.integrations.messages :as imessages]
            [ring.util.request :as ring-request]
            [sade.http :as http]
            [clj-time.core :as t]
            [sade.util :as util]
            [hiccup.core :as core]))

(sc/defschema DummyVerdict
  {:type-name      sc/Str
   :template-file  sc/Str
   (sc/optional-key :kuntalupatunnus) (sc/maybe sc/Str)
   (sc/optional-key :asiointitunnus) (sc/maybe sc/Str)
   })

;; Dummy KRYSP Service that also enables the variation of the interface responses according to various parameters.
;;
;; Also includes a simple HTML form to configure additional override configurations,
;; for example, to force the service to return a different verdict XML per application or municipal verdict id.

(when (env/feature? :dummy-krysp)

  (def default-dummy-verdicts [{:type-name "rakval:ValmisRakennus", :kuntalupatunnus nil, :template-file "krysp/dev/building.xml"}
                               {:type-name "rakval:RakennusvalvontaAsia", :kuntalupatunnus nil, :template-file "krysp/dev/verdict.xml"}
                               {:type-name "rakval:RakennusvalvontaAsia", :kuntalupatunnus "14-0241-R 3", :template-file "krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml"}
                               {:type-name "rakval:RakennusvalvontaAsia", :kuntalupatunnus "895-2015-001", :template-file "krysp/dev/verdict-rakval-with-area-like-location.xml"}
                               {:type-name "rakval:RakennusvalvontaAsia", :kuntalupatunnus "895-2015-002", :template-file "krysp/dev/verdict-rakval-with-building-location.xml"}
                               {:type-name "rakval:RakennusvalvontaAsia", :kuntalupatunnus "475-2016-001", :template-file "krysp/dev/verdict-rakval-missing-location.xml"}
                               {:type-name "ymy:Ymparistolupa", :kuntalupatunnus nil, :template-file "krysp/dev/verdict-yl.xml"}
                               {:type-name "ymm:MaaAineslupaAsia", :kuntalupatunnus nil, :template-file "krysp/dev/verdict-mal.xml"}
                               {:type-name "ymv:Vapautus", :kuntalupatunnus nil, :template-file "krysp/dev/verdict-vvvl.xml"}
                               {:type-name "ppst:Poikkeamisasia,ppst:Suunnittelutarveasia", :kuntalupatunnus nil, :template-file "krysp/dev/verdict-p.xml"}
                               {:type-name "kiito:every-type", :kuntalupatunnus nil, :template-file "krysp/dev/verdict-kt.xml"}])

  (defonce dummy-verdicts (atom default-dummy-verdicts))

  (defn verdict-xml [typeName & [search-key search-val]]
    (let [verdicts-by-type (filter (comp #{typeName} :type-name) @dummy-verdicts)]
      (-> (or
            (when (and search-val search-key)) (util/find-by-key search-key search-val verdicts-by-type)
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

  (defpage "/dev/krysp" {typeName :typeName r :request filter :filter overrides :overrides}
           (if-not (s/blank? typeName)
             (let [filter-type-name (-> filter sade.xml/parse (sade.common-reader/all-of [:PropertyIsEqualTo :PropertyName]))
                   search-literal (-> filter sade.xml/parse (sade.common-reader/all-of [:PropertyIsEqualTo :Literal]))
                   search-key (case filter-type-name
                                "rakval:luvanTunnisteTiedot/yht:LupaTunnus/yht:kuntalupatunnus" :kuntalupatunnus
                                "rakval:luvanTunnisteTiedot/yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus" :asiointitunnus
                                nil)
                   typeName (if (ss/starts-with typeName "kiito:") "kiito:every-type" typeName)
                   overrides (-> (json/decode overrides)
                                 (clojure.walk/keywordize-keys))]
               (cond
                 (not-empty overrides) (resp/content-type "application/xml; charset=utf-8" (override-xml (io/resource (verdict-xml typeName)) overrides))
                 (and (#{:kuntalupatunnus :asiointitunnus} search-key) search-literal)
                   (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource (verdict-xml typeName :kuntalupatunnus search-literal))))
                 :else (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource (verdict-xml typeName))))))
             (when (= r "GetCapabilities")
               (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource "krysp/dev/capabilities.xml"))))))

  (defpage [:post "/dev/krysp"] {}
           (let [xml (sade.xml/parse (slurp (:body (request/ring-request))))
                 xml-no-ns (sade.common-reader/strip-xml-namespaces xml)
                 typeName (sade.xml/select1-attribute-value xml-no-ns [:Query] :typeName)]
             (when (= typeName "yak:Sijoituslupa,yak:Kayttolupa,yak:Liikennejarjestelylupa,yak:Tyolupa")
               (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource "krysp/dev/verdict-ya.xml"))))))

  (defpage [:post "/dev/krysp/manager/add"] {} ;;
           (let [request (request/ring-request)
                 {typeName :new-type-name filterKey :new-filter-key filterValue :new-filter file :verdict-file} (util/map-keys keyword (:form-params request))]
           (swap! dummy-verdicts conj (merge {:type-name typeName
                                              :template-file file}
                                             (when (and filterValue (#{"kuntalupatunnus"} filterKey))
                                               {:kuntalupatunnus filterValue})
                                             (when (and filterValue (#{"asiointitunnus"} filterKey))
                                               {:asiointitunnus filterValue})))
           (resp/redirect "/dev/krysp/manager")))

  (defpage [:get "/dev/krysp/manager"] {}
    (core/html [:html
               [:head [:title "Dummy Dev Krysp Manager"]
                [:script {:type "text/javascript" :src "https://ajax.aspnetcdn.com/ajax/jQuery/jquery-1.11.3.min.js"}]
                [:script {:type "text/javascript" :src "/app/0/common.js?lang=fi"}]]
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

  (defn krysp-endpoint-authentication
    [request]
    (let [[u p] (http/decode-basic-auth request)]
      (debugf "%s requesting krysp receiver endpoint, db cookie being: %s"
             u (get-in request [:cookies "test_db_name" :value]))
      (and (= u "kuntagml") (= p "kryspi"))))

  (defpage [:post "/dev/krysp/receiver/:path"] {path :path}
           (let [request (request/ring-request)]
             (if (krysp-endpoint-authentication request)
               (let [body-str (ring-request/body-string request)]
                 (if (ss/starts-with body-str "<?xml")
                   (do
                     (imessages/save {:id           (mongo/create-id) :direction "in" :messageType (str "KuntaGML " path)
                                      :transferType "http" :format "xml" :created (sade.core/now)
                                      :status       "received" :data body-str
                                      :application  {:id (re-find #"L[PX]-\d{3}-\d{4}-\d{5}" body-str)}})
                     (resp/status 200 "OK"))
                   (resp/status 400 "Not XML")))
               basic-401)))

  (defpage [:get "/dev/private-krysp"] []
           (let [request (request/ring-request)
                 [u p] (http/decode-basic-auth request)]
             (if (= u p "pena")
               (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource "krysp/dev/capabilities.xml")))
               basic-401))))
