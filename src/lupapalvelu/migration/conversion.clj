(ns lupapalvelu.migration.conversion
  "Support for conversion related migrations."
  (:require [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.conversion.location :as conv-xy]
            [lupapalvelu.conversion.state :as conv-state]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.db :as db]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as ops]
            [monger.operators :refer :all]
            [sade.common-reader :refer [strip-xml-namespaces]]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]))

(defn- ->int-or-s [a]
  (util/pcond->> (or (ss/trim a) "")
    (partial re-matches #"\d+") util/->int))

(defn- parse-combined-property-id
  "186-401-0010-0004-M-0605 -> [186-401-10-4 M0605]
   186-401-0010-0004        -> [186-401-10-4]"
  [property-id]
  (when-let [[a b] (some-> (ss/trim property-id)
                           ss/blank-as-nil
                           (ss/split #"-M-")
                           seq)]
    (cond-> [;; Remove zeropadding form id parts.
             (->> (ss/split a #"-")
                  (map ->int-or-s)
                  (ss/join "-" ))]
      (ss/not-blank? b) (conj (let [v (->int-or-s b)]
                                (format (if (int? v) "M%04d" "M%s")
                                        v))))))

(defn site-document-id-components
  "Old conversion functionality has combined property and area ids. This function finds
  rakennuspaikka-kuntagml document and returns extracted and parsed parts. The return
  value is a vector of document-id, friendly property-id and (optionally) friendly area
  id (määräalatunnus). Returns nil if nothing found."
  [{:keys [documents]}]
  (when-let [property-id (some->> documents
                                  (util/find-first (util/fn-> :schema-info :name
                                                              (= "rakennuspaikka-kuntagml")))
                                  :data :kiinteisto :kiinteistotunnus :value
                                  ss/blank-as-nil)]
    (some->> (parse-combined-property-id property-id)
             (cons property-id))))

(defn application-reset-state
  "Application reset state is the last of the first batchrun states. For example, if X is a
  user state and others are batchrun states, the reset state for the following would by C:
  A B C X D E. While there is a possibility of a false positive (say another batchrun has
  updated the application after the conversion) that can be accepted. Returns the reset
  state or nil."
  [{:keys [history]}]
  (some->> history
           (take-while (comp conv-util/conversion-or-batchrun-user? :user))
           last
           :state))

(defn conversion-document-reset-location
  "Resolve `reset-location` value (xy coordinates) from an already existing document. The
  value is either taken from `location` field, calculated from the document's KuntaGML
  message or it is nil."
  [{:keys [location kuntagml]}]
  (try
    (or (some-> location conv-xy/valid-xy-or-nil)
        (when-not (:error kuntagml)
          (some-> kuntagml :xml
                  (xml/parse-string "utf8")
                  strip-xml-namespaces
                  krysp-reader/kuntagml->xy)))
    (catch Exception _
      nil)))

(defn- state-graph
  ([application]
   (some-> application
           ops/get-primary-operation-metadata
           :state-graph-resolver
           (apply [application])))
  ([application operation-name]
   (state-graph (assoc-in application [:primaryOperation :name] operation-name))))

(defn fix-primary-operation-and-history
  "Replace `application` primary operation name with `new-op-name`. DOES NOT change any
  data. Application history is fixed if the new operation has a different state machine."
  [{app-id :id old-op :primaryOperation :as application} conversion-doc new-op-name
   sheriff-note]
  (let [new-schema (ops/get-operation-metadata new-op-name :schema)
        _          (assert new-schema "Known operation")]
    (mongo/update-by-query :applications
                           {:_id       app-id
                            :documents {$elemMatch {:schema-info.op.id (:id old-op)}}}
                           (db/with-sheriff-note sheriff-note
                             {$set {:primaryOperation.name           new-op-name
                                    :operation-name                  new-op-name
                                    :documents.$.schema-info.op.name new-op-name}}))
    (when-not (= (state-graph application) (state-graph application new-op-name))
      (conv-state/fix-history {:organization-id (:organization application)
                               :municipality    (:municipality application)}
                              conversion-doc
                              (assoc-in application [:primaryOperation :name]
                                        new-op-name)))))
