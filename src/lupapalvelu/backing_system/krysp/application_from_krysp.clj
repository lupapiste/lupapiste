(ns lupapalvelu.backing-system.krysp.application-from-krysp
  (:require [clojure.java.io :as io]
            [lupapalvelu.backing-system.krysp.common-reader :as krysp-cr]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.logging :as log :refer [debugf warnf]]
            [sade.common-reader :as scr]
            [sade.core :refer [fail!]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as sxml]
            [sade.validators :as v]
            [slingshot.slingshot :refer [try+]])
  (:import (org.xml.sax SAXParseException)))

(set! *warn-on-reflection* true)

(defn- get-tunnus-elems [permit-type xml-without-ns]
  (->> (krysp-cr/get-tunnus-xml-path permit-type :application-id)
       (sxml/select xml-without-ns)))

(defn- valid-lp-tunnus?
  [application-ids application-id-from-xml]
  (and (v/application-id? application-id-from-xml)
       (if (seq application-ids)
         ((set application-ids) application-id-from-xml)
         application-id-from-xml)))

(defn- valid-doc-id?
  [doc-ids doc-id-from-xml]
  (if (seq doc-ids)
    ((set doc-ids) doc-id-from-xml)
    doc-id-from-xml))

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
        (log/log-event :info {:run-by "Automatic review/verdict batchrun or check-for-verdict"
                                  :event "XML did not contain any valid application ids"
                                  :ids-used-for-fetching application-ids
                                  :ids-found-from-xml (reduce
                                                        #(let [lp-tunnus-from-xml (sxml/text %2)]
                                                           (if (ss/not-blank? lp-tunnus-from-xml)
                                                             (conj %1 lp-tunnus-from-xml)
                                                             %1))
                                                        []
                                                        application-ids-from-xml)}))))

(defn- ids-found-from-xml [application-ids-from-xml]
  (reduce
    (fn [acc app-id]
      (let [lp-tunnus-from-xml (sxml/text app-id)]
        (if (ss/not-blank? lp-tunnus-from-xml)
          (conj acc lp-tunnus-from-xml)
          acc)))
    []
    application-ids-from-xml))

(defn- get-doc-id
  "Returns the first doc-id from xml"
  [permit-type doc-ids xml-without-ns]
  (let [application-ids-from-xml (get-tunnus-elems permit-type xml-without-ns)]
    (or (->> application-ids-from-xml
             (filter #(valid-doc-id? doc-ids (sxml/text %)))
             first
             sxml/text)
        (log/log-event :info {:run-by "Automatic review/verdict batchrun or check-for-verdict"
                                  :event "XML did not contain any valid foreman documents ids"
                                  :ids-used-for-fetching doc-ids
                                  :ids-found-from-xml (ids-found-from-xml application-ids-from-xml)}))))

(defn- get-kuntalupatunnus [permit-type kuntalupa-ids xml-without-ns]
  (or (->> (sxml/select1 xml-without-ns (krysp-cr/get-tunnus-xml-path permit-type :kuntalupatunnus))
           :content
           first)
      (log/log-event :info {:run-by "Automatic review/verdict batchrun or check-for-verdict"
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
      (log/log-event :warn {:run-by      "Review batchrun"
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
        (log/log-event :debug
                       {:run-by          "Automatic review/verdict batchrun"
                        :event           "Start fetching XML(s)"
                        :organization-id org-id
                        :permit-type     permit-type
                        :ids             ids
                        :search-type     search-type})
        (cond->> (permit/fetch-xml-from-krysp permit-type url creds ids search-type raw?)
          (not raw?) scr/strip-xml-namespaces
          (not raw?) (not-empty-content permit-type)
          (not raw?) ss/trimwalk))
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

(defn get-valid-xml-by-foreman-doc-id
  "Returns fetched and namespace stripped XML if KuntaGML message has document's ID in correct place.
  If it doesn't include ID returns nil."
  [{:keys [permitType organization]} foreman-doc-id]
  (when-let [xml (get-application-xml-by-application-id {:id foreman-doc-id
                                                         :permitType permitType
                                                         :organization organization})]
    (if (env/dev-mode?)
      (when (get-doc-id permitType nil xml)
        xml)
      (when (get-doc-id permitType [foreman-doc-id] xml)
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

(defn- try-to-get-application-xmls
  "If throws? is false, ignores errors and returns empty map.
  Otherwise, returns a map of app-id->xml"
  [organization permit-type search-type app-ids throws?]
  (if throws?
    (get-application-xmls organization permit-type search-type app-ids)
    (try+
      (get-application-xmls organization permit-type search-type app-ids)
      (catch Exception e
        (log/log-event :error {:run-by "Automatic review checking"
                                   :organization-id (:id organization)
                                   :permit-type permit-type
                                   :exception (.getName (class e))
                                   :message (get &throw-context :message "")
                                   :event (format "Unable to get reviews [%s] from %s backend: %s - %s"
                                                  (ss/join "," app-ids)
                                                  (:id organization)
                                                  (.getName (class e))
                                                  (get &throw-context :message ""))})
        {}))))

(defn- get-application-xmls-for-chunk
  "Fetches application xmls and returns map of applications as keys and xmls as values.
  Tries at first to fetch the whole chunk at once, and if that fails, each application by themselves"
  [organization permit-type search-type application-chunk]
  (let [id-key        (if (= search-type :kuntalupatunnus) :kuntalupatunnus :id)
        app-ids       (map id-key application-chunk)
        get-xmls      (fn [ids throws?]
                        (util/map-keys #(util/find-by-key id-key % application-chunk)
                                       (try-to-get-application-xmls organization permit-type search-type ids throws?)))
        get-xmls-cons (fn [ids]
                           (reduce (fn [acc app-id]
                                     (into acc (get-xmls [app-id] false)))
                                   '()
                                   ids))]
    (try+
      (get-xmls app-ids true)

      (catch SAXParseException _
        (log/log-event :error {:run-by "Automatic review checking"
                                   :organization-id (:id organization)
                                   :permit-type permit-type
                                   :event (format "Could not understand response when getting reviews in chunks from %s backend"
                                                  (:id organization))})
        ;; Fallback into fetching xmls consecutively
        (get-xmls-cons app-ids))

      (catch [:sade.core/type :sade.core/fail
              :status         404] _
        (log/log-event :error {:run-by "Automatic review checking"
                                   :organization-id (:id organization)
                                   :permit-type permit-type
                                   :event (format "Unable to get reviews in chunks from %s backend: Got HTTP status 404"
                                                  (:id organization))})
        ;; Fallback into fetching xmls consecutively
        (get-xmls-cons app-ids))

      (catch [:sade.core/type :sade.core/fail] t
        (log/log-event :error {:run-by "Automatic review checking"
                                   :organization-id (:id organization)
                                   :permit-type permit-type
                                   :event (format "Unable to get reviews from %s backend: %s"
                                                  (:id organization)
                                                  (select-keys t [:status :text]))})
        ;; Fallback into fetching xmls consecutively
        (get-xmls-cons app-ids)))))

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
  (let [chunk-size     (get-in organization [:krysp (keyword permit-type) :fetch-chunk-size] 10)
        xmls-by-app-id (get-application-xmls-in-chunks organization permit-type :application-id applications chunk-size)
        found-app-ids  (map (comp :id first) xmls-by-app-id)
        not-found-apps (lazy-seq (remove (comp (set found-app-ids) :id) applications))]
    (log/log-event :info {:run-by            "Automatic review/verdict batchrun"
                              :event             "Application ids that were found in xml"
                              :organization-id   (:id organization)
                              :permit-type       permit-type
                              :found-app-ids     (vec (set found-app-ids))
                              :not-found-app-ids (map :id not-found-apps)})
    (lazy-cat xmls-by-app-id
              (get-application-xmls-by-backend-id organization permit-type not-found-apps chunk-size))))
