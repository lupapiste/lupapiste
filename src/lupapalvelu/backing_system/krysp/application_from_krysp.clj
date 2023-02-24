(ns lupapalvelu.backing-system.krysp.application-from-krysp
  (:require [taoensso.timbre :refer [debugf warn warnf]]
            [clojure.java.io :as io]
            [clojure.set :refer [rename-keys]]
            [clojure.string :as str]
            [lupapalvelu.backing-system.krysp.common-reader :as krysp-cr]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.logging :as logging]
            [sade.common-reader :as scr]
            [sade.core :refer [fail!]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]
            [sade.xml :as sxml]
            [sade.validators :as v]))

(defn- get-tunnus-elems [permit-type xml-without-ns]
  (->> (krysp-cr/get-tunnus-xml-path permit-type :application-id)
       (sxml/select xml-without-ns)))

(defn- valid-lp-tunnus?
  [application-ids application-id-from-xml]
  (and (v/application-id? application-id-from-xml)
       (if (seq application-ids)
         ((set application-ids) application-id-from-xml)
         application-id-from-xml)))

(defn- get-lp-tunnus
  "Returns the first lp-tunnus from xml that fulfills:
  1. lp-tunnus from xml is valid application id (see sade.validators/application-id?)
  2. If arg application-ids is not empty application-ids must contain the lp-tunnus from xml"
  [permit-type application-ids xml-without-ns]
  (let [application-ids-from-xml (get-tunnus-elems permit-type xml-without-ns)]
    (or (->> application-ids-from-xml
             (filter #(valid-lp-tunnus? application-ids (sxml/text %)))
             first
             sxml/text)
        (logging/log-event :info {:run-by "Automatic review/verdict batchrun or check-for-verdict"
                                  :event "XML did not contain any valid application ids"
                                  :ids-used-for-fetching application-ids
                                  :ids-found-from-xml (reduce
                                                        #(let [lp-tunnus-from-xml (sxml/text %2)]
                                                           (if (ss/not-blank? lp-tunnus-from-xml)
                                                             (conj %1 lp-tunnus-from-xml)
                                                             %1))
                                                        []
                                                        application-ids-from-xml)}))))

(defn- get-kuntalupatunnus [permit-type kuntalupa-ids xml-without-ns]
  (or (->> (sxml/select1 xml-without-ns (krysp-cr/get-tunnus-xml-path permit-type :kuntalupatunnus))
           :content
           first)
      (logging/log-event :info {:run-by "Automatic review/verdict batchrun or check-for-verdict"
                                :event "XML did not contain any valid kuntalupatunnus"
                                :ids-used-for-fetching kuntalupa-ids})))

(defn- group-content-by
  "Returns a map, where keys are based on content-fn and value is all the 'asia's
  in the given XML from the call to sade.xml/select. Asia is eg. RakennusvalvontaAsia element.
  So if XML contains multiple 'cases', value would be sequence of those.
  In most cases this sequence is of length 1, because usually there is only one 'asia' per XML."
  [content-fn permit-type lp-or-backend-ids xml-without-ns]
  (if-let [asia-key (permit/get-metadata permit-type :kuntagml-asia-key)]
    (->> (sxml/select xml-without-ns [asia-key])
         (group-by (partial content-fn permit-type lp-or-backend-ids)))
    (let [ids (ss/serialize lp-or-backend-ids)]
      (warnf "Permit type %s doesn't have :kuntagml-asia-key, skipping. IDs: %s" permit-type lp-or-backend-ids)
      (logging/log-event :warn {:run-by      "Review batchrun"
                                :event       "Permit type doesn't have :kuntagml-asia-key"
                                :permit-type permit-type
                                :ids         ids}))))

(defn- not-empty-content [permit-type xml]
  (cond
    (get-lp-tunnus permit-type nil xml) xml
    (get-kuntalupatunnus permit-type nil xml) xml))

(defn- fetch-application-xmls [organization permit-type ids search-type raw?]
  (let [org-id (get organization :id organization)]
    (if-let [{url :url creds :credentials} (org/get-krysp-wfs {:organization org-id :permitType permit-type})]
      (do
        (debugf "Start fetching XML, ids=%s search-type=%s raw?=%s" (ss/join "," ids) search-type raw?)
        (logging/log-event :debug
                           {:run-by "Automatic review/verdict batchrun"
                            :event "Start fetching XML(s)"
                            :ids ids
                            :search-type search-type})
        (cond->> (permit/fetch-xml-from-krysp permit-type url creds ids search-type raw?)
                 (not raw?) scr/strip-xml-namespaces
                 (not raw?) (not-empty-content permit-type)))
      (fail! :error.no-legacy-available))))

(defn get-application-xml-by-application-id [{:keys [id organization permitType]} & [raw?]]
  (fetch-application-xmls organization permitType [id] :application-id raw?))

(defn get-application-xml-by-backend-id [{:keys [organization permitType]} backend-id & [raw?]]
  (when backend-id
    (fetch-application-xmls organization permitType [backend-id] :kuntalupatunnus raw?)))

(defn get-valid-xml-by-application-id
  "Returns fetched and namespace stripped XML iff KuntaGML message has application's ID in correct place.
  If it doesn't include ID returns nil."
  [{:keys [id permitType] :as application}]
  (when-let [xml (get-application-xml-by-application-id application)]
    (if (env/dev-mode?)
      (when (get-lp-tunnus permitType nil xml)
        xml)
      (when (get-lp-tunnus permitType [id] xml)
        xml))))

(defn get-local-application-xml-by-filename
  "For local testing of Krysp import"
  [filename permit-type]
  (->> filename
       io/input-stream
       sxml/parse
       scr/strip-xml-namespaces
       (not-empty-content permit-type)))

(defmulti get-application-xmls
  "Get application xmls from krysp.

  Returns hashmap where key is the app-ID/kuntalupatunnus of the XML
  and value is the XML data structure, with each `:content` concatenated together
   (as there might be data of several XMLs in one message, so we group them by ID)."
  {:arglists '([organization permit-type search-type ids])}
  (fn [_ _ search-type & _]
    (keyword search-type)))

(defmethod get-application-xmls :application-id
  [organization permit-type _ application-ids]
  (->> (fetch-application-xmls organization permit-type application-ids :application-id false)
       (group-content-by get-lp-tunnus permit-type application-ids)))

(defmethod get-application-xmls :kuntalupatunnus
  [organization permit-type _ backend-ids]
  (->> (fetch-application-xmls organization permit-type backend-ids :kuntalupatunnus false)
       (group-content-by get-kuntalupatunnus permit-type backend-ids)))

(defn- get-application-xmls-for-chunk
  "Fetches application xmls and returns map of applications as keys and xmls as values."
  [organization permit-type search-type application-chunk]
  (let [id-key (if (= search-type :kuntalupatunnus) :kuntalupatunnus :id)]
    (->> (map id-key application-chunk)
         (get-application-xmls organization permit-type search-type)
         ; TODO now key is the whole application, maybe make it slightly more ligthweight and take only
         ; :id and :permitType? Seems that currently only user downstream is fetch-reviews-for-organization-permit-type
         (util/map-keys #(util/find-by-key id-key % application-chunk)))))

(defn- get-application-xmls-in-chunks [organization permit-type search-type applications chunk-size]
  (when-not (empty? applications)
    (->> (partition chunk-size chunk-size nil applications)
         (mapcat (partial get-application-xmls-for-chunk organization permit-type search-type))
         (remove (comp nil? first)) ; poistetaan ne app-xml:t joista ei tunnistettu lupatunnusta/hakemus-id:ta, ne on jotenkin rikki!!!
         )))

(defn- get-application-xmls-by-backend-id [organization permit-type applications chunk-size]
  (let [apps-with-kuntalupatunnus (->> applications
                                       (map (fn [app] (assoc app :kuntalupatunnus (some :kuntalupatunnus (:verdicts app)))))
                                       (filter :kuntalupatunnus))]
    (get-application-xmls-in-chunks organization permit-type :kuntalupatunnus apps-with-kuntalupatunnus chunk-size)))

(defn fetch-xmls-for-applications [organization permit-type applications]
  (let [chunk-size (get-in organization [:krysp (keyword permit-type) :fetch-chunk-size] 10)
        xmls-by-app-id (get-application-xmls-in-chunks organization permit-type :application-id applications chunk-size)
        found-app-ids  (map (comp :id first) xmls-by-app-id)
        not-found-apps (lazy-seq (remove (comp (set found-app-ids) :id) applications))]
    (logging/log-event :info {:run-by "Automatic review/verdict batchrun"
                              :event "Application ids that were found in xml"
                              :found-app-ids (vec (set found-app-ids))
                              :not-found-app-ids (map :id not-found-apps)})
    (lazy-cat xmls-by-app-id
              (get-application-xmls-by-backend-id organization permit-type not-found-apps chunk-size))))
