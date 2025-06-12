(ns lupapalvelu.sheriff
  "A collection of deputy functions to help out the sheriff."
  (:require [cljts.io :as jts]
            [clojure.java.io :as io]
            [integrant.repl.state :as repl-state]
            [jsonista.core :as json]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as as-krysp]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.backing-system.krysp.common-reader :as krysp]
            [lupapalvelu.backing-system.krysp.foreman :as bs-foreman]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.conversion.config :as conv-cfg]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.domain-attachment :as domain-att]
            [lupapalvelu.exports.reporting-db :as reporting-db]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.json :as lp-json]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.prev-permit :as prev-permit]
            [lupapalvelu.reports.fastexcel :as fastexcel]
            [lupapalvelu.review :as review]
            [lupapalvelu.smoketest.core :as smoke]
            [lupapalvelu.smoketest.lupamonster] ;; Needed by mongochecks
            [lupapalvelu.system.system-util :as system-util]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.common-reader :as common-reader]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml])
  (:import [com.vividsolutions.jts.geom Coordinate GeometryFactory Polygon]))

;; NOTE: We use `(system-util/with-existing-system repl-state/system ..)` idiom
;; here to use the system from `user.clj` here. In other words, you must first
;; say `(go)` to start the system before running many of these functions.

(set! *warn-on-reflection* true)

;;
;; Docstore deputy functions
;;

(defn read-polygon ^Polygon [polygon-string]
  (jts/read-wkt-str polygon-string))

;; See, for example, TT-329
(defn point-within-polygon?
  "Is the point `[x y]` within `polygon`?"
  [[x y] ^Polygon polygon]
  (.within (.createPoint (GeometryFactory.)
                         (Coordinate. x y))
           polygon))

;;
;; Application wrangling
;;

(defn bananize
  "Set pena as applicant, sonja as authority for an application. Also give Sonja authz
  to whichever organization the application belongs to. Note that in order to locally open
  a bananized application as Sonja, you need to have the organization in your database
  as well."
  [app-id]
  (system-util/with-existing-system repl-state/system
    (let [{:keys [organization] :as application} (mongo/by-id :applications app-id)
          projection                             [:id :firstName :lastName :username]
          pena                                   (-> (usr/get-user-by-email "pena@example.com") (select-keys projection) (assoc :role "writer"))
          {:keys [orgAuthz] :as sonja}           (-> (usr/get-user-by-email "sonja.sibbo@sipoo.fi") (assoc :role "authority"))
          updated-auth                           (-> (filter #(false? (contains? #{"pena" "sonja"} (:username %))) (:auth application))
                           (conj pena (select-keys sonja (conj projection :role))))]
      (mongo/update-by-id :applications app-id (assoc application :auth updated-auth))
      (usr/update-user-by-email "sonja.sibbo@sipoo.fi" {$set {:orgAuthz (assoc orgAuthz (keyword organization) ["authority" "approver"])}}))))

(defn reviews-from-xml
  "Simulates the review batchrun. Does not update mongo.
  `filepath` is the path to the KuntaGML file.
  `app-id` is the application id in the local mongo."
  [filepath app-id]
  (system-util/with-existing-system repl-state/system
    (let [kuntagml (xml/parse-string (slurp filepath) "utf-8")
          app      (mongo/by-id :applications app-id)
          user     (usr/batchrun-user (:organization app))]
      (review/read-reviews-from-xml user (now) app kuntagml))))

;;
;; Reporting
;;

(defn digi-report
  "Creates CSV report from JSON. Originally created for a digitizer report
  as specified in TT-18129, but can easily be modified and reused for other
  similar requirements as well.

  `fn-in`  Filename/path to input JSON (e.g., mongoexport).
  `fn-out` Filename/path to output CSV. Tabs are used as separators."
  [fn-in fn-out]
  (with-open [in (io/input-stream fn-in)
              w  (io/writer fn-out)]
    (letfn [(write-line [& xs]
              (.write w (str (ss/join "\t" xs) "\n")))]
      (write-line "Organisaatio" "LP-tunnus" "Osoite" "Pvm" "Etunimi" "Sukunimi" "Käyttäjätunnus")
      (doseq  [{:keys [organization address _id
                       history]} (json/read-value in (json/object-mapper {:decode-key-fn true}))
               :let              [{:keys [ts user]} (first history)]]
        (write-line organization _id address
                    (-> ts :$numberLong util/->long date/finnish-date)
                    (:firstName user)
                    (:lastName user)
                    (:username user))))))

(defn csv-report
  "Creates a CSV file from given `columns` and `rows`.
  `columns`: List of column configuration maps.
         `:value-fn`: Returns value of the cell when given the row as argument (default `identity`)
         `:text`: Column header.
  `rows`: List of maps
  `filename`: Output file."
  [columns rows filename]
  (with-open [w (io/writer filename)]
    (letfn [(write-line [xs]
              (.write w (str (ss/join "\t" xs) "\n")))]
      (write-line (map :text columns))
      (doseq [row rows]
        (write-line (map (fn [{:keys [value-fn]}]
                           ((or value-fn identity) row))
                         columns))))))

(defn attachment-type-report
  "All Lupapiste attachment types and their friendly names listed."
  [filename]
  (csv-report [{:text "Liitetyypin tunniste"}
               {:text "Liitetyyppi"
                :value-fn (partial i18n/localize :fi :attachmentType)}]
              (->> (vals att-type/attachment-types-by-permit-type)
                   flatten
                   distinct
                   (map (fn [{:keys [type-group type-id]}]
                          (str (name type-group) "." (name type-id))))
                   sort)
              filename))

(defn make-note
  "Makes a traditional `_sheriff-notes` item. Just prints out mongo-ready JSON note if `json?` is true."
  ([text json?]
   (if json?
     (println (format "{created: NumberLong(%s), note: \"%s\"}"
                      (now) text))
     {:created (now)
      :note    text}))
  ([text]
   (make-note text false)))

(defn push-note
  "Prints out JSON $push entry for sheriff-note. Useful in Studio 3T update dialog, for
  example. If `db?` is true (default false), then also `:reportingDbOverrideTS` is part of
  the update."
  ([text db?]
   (let [ts (now)]
     (lp-json/print-json (cond-> {$push {:_sheriff-notes {:note    text
                                                          :created ts}}}
                           db? (merge (reporting-db/reporting-db-timestamp-update ts))))))
  ([text] (push-note text false)))

(defn tag-reporting-ts
  "Convenience function for printing `:reportingDbOverrideTs` upsert and sheriff note."
  [prefix]
  (push-note (str prefix " reportingDbOverrideTs") true))

(defn push-state
  "Push update for history state entry and setting state. Does not update timestamps."
  [state]
  (lp-json/print-json {$push {:history {:state state
                                        :ts    (now)
                                        :user  {:firstName "Lupapiste"}}}
                       $set {:state state}}))

(defn year-ago
  "Timestamp (in millis) for the year ago from now."
  []
  (-> (date/now) (date/minus :year) date/timestamp))

(defn xml->application
  "Manual override for `prev-permit/fetch-prev-application`. The organization must exist. If
  `kuntalupatunnus` is not given, the first kuntalupatunnus found in xml is used."
  [org-id xml-filepath & [kuntalupatunnus]]
  (system-util/with-existing-system repl-state/system
    (let [xml             (some-> (slurp xml-filepath)
                                  (xml/parse-string "utf-8")
                                  common-reader/strip-xml-namespaces)
          kuntalupatunnus (or kuntalupatunnus
                              (some-> (xml/select1 xml :kuntalupatunnus)
                                      :content
                                      first))]

      (prev-permit/fetch-prev-application! {:user    (usr/batchrun-user [org-id])
                                            :system  (system-util/get-system-components)
                                            :created (now)
                                            :data    {:organizationId  org-id
                                                      :kuntalupatunnus kuntalupatunnus}}
                                           xml))))

(defn run-mongochecks
  "Mongochecks smoke tests against the local database."
  []
  (system-util/with-existing-system repl-state/system
    (smoke/execute-tests "mongochecks")))

(defn wfs-url
  "Builds an R WFS URL that can be used with `curl`, for example. This is sometimes useful
  when debugging connection or parameter issues. Usually, if only the KuntaGML message
  matters, the easier way is use Admin admin UI. `url` is the backing system url (see
  organization's `krysp.R.url`). `target-id` is either a backend or application
  id."
  ([url target-id permit-type]
   {:pre [url target-id permit-type]}
   (krysp/wfs-krysp-url-with-service url
                                     (case (keyword permit-type)
                                       :R krysp/rakval-case-type
                                       :P krysp/poik-case-type)
                                     (krysp/property-in (krysp/get-tunnus-path (name permit-type)
                                                                               (if (ss/starts-with-i? target-id "LP-")
                                                                                 :application-id
                                                                                 :kuntalupatunnus))
                                                        [target-id])))
  ([url target-id]
   (wfs-url url target-id "R")))

(defn read-xml
  "Slurps, parses and strips namespaces from given `xml-filename`. Note: slurp supports
  file:// protocol, too."
  [xml-filename]
  (-> (slurp xml-filename)
      (xml/parse-string  "utf8")
      common-reader/strip-xml-namespaces))

(defn location-info
  "Convenience utility for finding the location information from KuntaGML message."
  [xml-filename]
  (some-> (read-xml xml-filename)
          (krysp-reader/resolve-valid-location)))

(defn make-curl-script
  "Writes a shell script into `filename`. The script fetches KuntaGML messages for each
  given `backend-id`. `url` is the backing sysem URL and `prefix` the message filename
  prefix. Resulting files are named `prefix_nnnnn.xml` where `nnnnn` is a zero-padded
  running number."
  ([filename backend-ids url prefix permit-type]
   (->> backend-ids
        (map-indexed (fn [i bid]
                       (format "curl -o \"%s_%06d.xml\" \"%s\""
                               prefix (inc i) (wfs-url url bid permit-type))))
        (cons "#!/bin/sh")
        (ss/join "\n")
        (spit filename)))
  ([filename backend-ids url prefix]
   (make-curl-script filename backend-ids url prefix "R")))

(defn process-attachments-json
  "Read in the the mongo-exported application-array json (see LPK-6157) and generate a
  corresponding json, where the attachments approvals are reduced to to latest-version
  `ok-by` field that has `firstName`, `lastName` and `timestamp`."
  [filename-in filename-out]
  (letfn [(process-attachment [{:keys [approvals latestVersion] :as a}]
            (let [a               (dissoc a :approvals)
                  {:keys [timestamp user
                          state]} (some->> latestVersion
                                           :originalFileId
                                           keyword
                                           (get approvals))
                  ok-by           (when (= state "ok")
                                    (some-> (assoc (select-keys user [:firstName
                                                                      :lastName])
                                                   :timestamp timestamp)
                                            util/strip-blanks
                                            not-empty))]
              (cond-> a
                ok-by (assoc-in [:latestVersion :ok-by] ok-by))))]
    (with-open [in  (io/reader filename-in)
                out (io/writer filename-out)]
      (let [read-fn    (lp-json/make-json-array-reader in)
            out-mapper (json/object-mapper {:strip-nils true
                                            :pretty     false})]

        ;; Since the client side report handling system is very picky about the report
        ;; format, we have to do it partly manually.
        (.write out "[\n")
        (loop [v      (read-fn)
               comma? false]
          (if (= v ::lp-json/end)
            (do
              (.write out "\n]\n")
              (.flush out))
            (do
             (when comma?
                (.write out ",\n"))
             (.write out (json/write-value-as-string (update v :attachments #(map process-attachment %))
                                                     out-mapper))
              (recur (read-fn) true))))))))

(defn grayjson
  "Takes a raw Graylog message and returns parsed json or nil."
  [msg]
  (some-> (re-find #"[^{]+(\{.+\})" msg)
          second
          (lp-json/decode true)))

(defn export-as-kuntagml
  "Given an `app-id`, fetch the app from mongo and produce a valid KuntaGML XML representation or throw.
  No side-effects. Output format can be either `:str`, `:xml`, or `:edn`. XML validation
  can be skipped by setting `:validate?` option to false."
  [app-id & {:keys [output] :or {output :str} :as options}]
  (system-util/with-existing-system repl-state/system
    (if-let [app (mongo/by-id :applications app-id)]
      (let [org-id        (:organization app)
            org           (org/get-organization org-id)
            batchrun-user (usr/batchrun-user [org-id])
            system        (-> (system-util/get-system-components)
                              (assoc :config {:host "http://localhost:8000"}))
            xml-str       (as-krysp/full-batchrun-kuntagml org system app batchrun-user options)
            xml           (delay (xml/parse-string xml-str "utf-8"))]
        (case output
          :str xml-str
          :xml (force xml)
          :edn (xml/xml->edn (force xml))))
      :no-app-found)))

(comment
  (export-as-kuntagml "LP-753-2025-90001" :output :xml))

;; ------------------------------------
;; Logs to excel
;; ------------------------------------

(defn- user-fn [{:keys [username firstName lastName]}]
  (ss/trim-join ", "
                (ss/trim-join " " lastName firstName)
                username))

(defn- data-fn [data]
  (some-> (dissoc data :_) not-empty str))

(defn- columns
  [log-type]
  (let [fun (case log-type
              :gcp     first
              :graylog second)]
    (map (fn [[ks txt f]]
           (util/strip-nils
             {:path (fun ks)
              :text txt
              :fn   f}))
         [[[:timestamp :timestamp] "Aikaleima" date/zoned-date-time]
          [[:severity :severity] "Lokityyppi"]
          [[:jsonPayload.action-type :action-type] "Toiminnon tyyppi"]
          [[:jsonPayload.action :action] "Toiminto"]
          [[:jsonPayload.message :message] "Lokiviesti"]
          [[:jsonPayload.applicationId :application-id] "Hanke"]
          [[:jsonPayload.event.data :json.data] "Data" data-fn]
          [[:jsonPayload.userId :user-id] "Käyttäjätunniste"]
          [[:jsonPayload.event.user :json.user] "Käyttäjä" user-fn]
          [[:jsonPayload.session-id :session-id]  "Istuntotunniste"]
          [[:jsonPayload.event.web.client-ip :json.web.client-ip] "IP-osoite"]
          [[:jsonPayload.event.web.user-agent :user-agent] "Selain"]])))


(defn gcp-logs->excel
  [source-json-filename target-excel-filename]
  (fastexcel/->excel target-excel-filename
                     (columns :gcp)
                     (lp-json/slurp-json source-json-filename)))

(defn graylogs->excel
  [source-csv-filename target-excel-filename]
  (->> (conv-cfg/csv->maps source-csv-filename :separator \,)
       (map (fn [{:keys [message] :as m}]
              (let [g (grayjson message)]
                (cond-> (dissoc m :message)
                  g       (assoc :json g)
                  (not g) (assoc :message (last (ss/split message #" - " 2)))
                  true    (assoc :severity (first (ss/split message #"\s" 2)))))))
       (fastexcel/->excel target-excel-filename
                          (columns :graylog))))

(defn pate-verdict-kuntagml
  "Mimics _the production_ KuntaGML generation. Spits verdict message into local sheriff
  sftp folder. This is useful, when a verdict xml needs to be regenerated. The default
  KuntaGML version is 2.2.6."
  ([app-id verdict-id kuntagml-version]
   (system-util/with-existing-system repl-state/system
     (let [{:keys [pate-verdicts organization permitType]
            :as   application} (mongo/by-id :applications app-id)
           verdict             (util/find-by-id verdict-id pate-verdicts)
           verdict-attachment  (domain-att/get-attachment-by-id application (-> verdict :published :attachment-id))
           organization        {:id                               organization
                                :krysp                            {(keyword permitType) {:version kuntagml-version
                                                                                         :ftpUser "sheriff"}}
                                :use-attachment-links-integration true
                                :force-kuntagml-226-for-pre-2025  true}]
       (with-redefs [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system/get-begin-of-link (constantly "https://www.lupapiste.fi/api/raw/")]
         (as-krysp/verdict-as-kuntagml (util/mmap {:system (system-util/get-system-components)}
                                                  organization application)
                                       verdict
                                       verdict-attachment)))))
  ([app-id verdict-id]
   (pate-verdict-kuntagml app-id verdict-id "2.2.6")))

(defn application-kuntagml
  "Mimics _the production_ KuntaGML generation for application. Spits KuntaGML message into
  local sheriff sftp folder. The default KuntaGML version is 2.2.6."
  ([app-id kuntagml-version]
   (system-util/with-existing-system repl-state/system
     (let [{:keys [organization permitType]
            :as   application} (mongo/by-id :applications app-id)
           organization        {:id                               organization
                                :krysp                            {(keyword permitType) {:version kuntagml-version
                                                                                         :ftpUser "sheriff"}}
                                :use-attachment-links-integration true
                                :force-kuntagml-226-for-pre-2025  true}
           application         (as-krysp/post-process-app-for-krysp application organization)]
       (with-redefs [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system/get-begin-of-link (constantly "https://www.lupapiste.fi/api/raw/")]
         (as-krysp/save-application-as-krysp (util/mmap {:system (system-util/get-system-components)}
                                                        organization application)
                                             "fi" {:id app-id})))))
  ([app-id]
   (application-kuntagml app-id "2.2.6")))

(defn foreman-kuntagml
  "Mimics _the production_ KuntaGML generation for TJ-V3 foreman document. Spits KuntaGML
  message into local sheriff sftp folder and adds message to integration-messages
  collection in mongo. The default KuntaGML version is 2.2.6."
  ([app-id doc-id kuntagml-version]
   (system-util/with-existing-system repl-state/system
     (let [{:keys [organization permitType]
            :as   application} (mongo/by-id :applications app-id)
           organization        {:id                               organization
                                :krysp                            {(keyword permitType) {:version kuntagml-version
                                                                                         :ftpUser "sheriff"}}
                                :use-attachment-links-integration true
                                :force-kuntagml-226-for-pre-2025  true}]
       (with-redefs [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system/get-begin-of-link (constantly "https://www.lupapiste.fi/api/raw/")]
         (bs-foreman/send-foreman-msgs! (util/mmap {:system (system-util/get-system-components)}
                                                   application organization)
                                        app-id
                                        :fi
                                        (domain/get-document-by-id application doc-id))))))
  ([app-id doc-id]
   (foreman-kuntagml app-id doc-id "2.2.6")))

(defn kuntagml-checksum
  "Given a filename (of a KuntaGML `.xml` message), return the checksum."
  [filename]
  (-> filename slurp common-reader/checksum))

(defn parse-buildings
  "Convenience function for debugging KuntaGML messages.
  Return buildings summary for the given file."
  [fname]
  (-> fname
      slurp
      (xml/parse-string "utf-8")
      common-reader/strip-xml-namespaces
      building-reader/->buildings-summary))

(defn- operations->schemas
  "Return a vector of unique schemas used in given operation(s)"
  [operations]
  (->> operations
       (mapcat (fn [[_op-id operation]] (operations/operation->schemas operation)))
       distinct
       sort
       (into [])))

(defn- operations-per-schema
  "Return a map of schema name to set of operations"
  [operations]
  (reduce (fn [acc [op-id operation]]
            (let [schemas (operations/operation->schemas operation)]
              (reduce (fn [acc schema]
                        (update acc schema (fnil conj #{}) op-id))
                      acc
                      schemas)))
          (sorted-map)
          operations))

(defn- schemas-per-operation
  "Return a map of operation to set of schemas"
  [operations]
  (reduce (fn [acc [op-id operation]]
            (assoc acc op-id (operations/operation->schemas operation)))
          {}
          operations))

(defn export-operation-schema-excel
  "Produce an excel report describing which operations use which schemas

  * `filename`   -- Name of the file to write.
  * `transpose?` -- If false (default): column per operation, row per schema.
                    If true: column per schema, row per operation."
  [filename & {:keys [transpose?]}]
  (i18n/with-lang "fi"
    (let [operation-names (->> operations/operation-index
                               (map (juxt (comp keyword :op) (comp first :locs)))
                               (into {}))
          columns         (if-not transpose?
                            ;; column per operation, row per schema
                            (->> (sort (keys operations/operations))
                                 (map (fn [op-id]
                                        {:path op-id
                                         :text (get operation-names op-id)}))
                                 (cons {:path :schema
                                        :text "Lomake"})
                                 (into []))
                            ;; column per schema, row per operation
                            (->> (operations->schemas operations/operations)
                                 (map (fn [schema-name]
                                        {:path (keyword schema-name)
                                         :text (name schema-name)}))
                                 (cons {:path :op
                                        :text "Toimenpide"})
                                 (into [])))
          rows            (if-not transpose?
                            ;; column per operation, row per schema
                            (->> (operations-per-schema operations/operations)
                                 (mapv (fn [[schema-name operations]]
                                         (reduce (fn [row operation]
                                                   ;; Use numbers as cell values => easier to use SUM() etc.
                                                   (assoc row operation 1))
                                                 {:schema schema-name}
                                                 operations))))
                            ;; column per schema, row per operation
                            (->> (schemas-per-operation operations/operations)
                                 (map (fn [[op-id schemas]]
                                        (reduce (fn [row schema]
                                                  (assoc row (keyword schema) 1))
                                                {:op (get operation-names op-id)}
                                                schemas)))
                                 (sort-by :op)
                                 (into [])))]
      (fastexcel/->excel filename
                         columns
                         rows))))
