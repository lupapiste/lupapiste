(ns lupapalvelu.sheriff
  "A collection of deputy functions to help out the sheriff."
  (:require [clj-time.coerce :as timec]
            [clj-time.core :as time]
            [cljts.io :as jts]
            [clojure.java.io :as io]
            [jsonista.core :as json]
            [lupapalvelu.backing-system.krysp.common-reader :as krysp]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.json :as lp-json]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.prev-permit :as prev-permit]
            [lupapalvelu.review :as review]
            [lupapalvelu.smoketest.core :as smoke]
            [lupapalvelu.smoketest.lupamonster] ;; Needed by mongochecks
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.common-reader :as common-reader]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml])
  (:import [com.vividsolutions.jts.geom Polygon Coordinate GeometryFactory]))

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
  (let [{:keys [organization] :as application} (mongo/by-id :applications app-id)
        projection [:id :firstName :lastName :username]
        pena (-> (usr/get-user-by-email "pena@example.com") (select-keys projection) (assoc :role "writer"))
        {:keys [orgAuthz] :as sonja} (-> (usr/get-user-by-email "sonja.sibbo@sipoo.fi") (assoc :role "authority"))
        updated-auth (-> (filter #(false? (contains? #{"pena" "sonja"} (:username %))) (:auth application))
                         (conj pena (select-keys sonja (conj projection :role))))]
    (mongo/update-by-id :applications app-id (assoc application :auth updated-auth))
    (usr/update-user-by-email "sonja.sibbo@sipoo.fi" {$set {:orgAuthz (assoc orgAuthz (keyword organization) ["authority" "approver"])}})))

(defn reviews-from-xml
  "Simulates the review batchrun. Does not update mongo.
  `filepath` is the path to the KuntaGML file.
  `app-id` is the application id in the local mongo."
  [filepath app-id]
  (let [kuntagml (xml/parse-string (slurp filepath) "utf-8")
        app      (mongo/by-id :applications app-id)
        user     (usr/batchrun-user (:organization app))]
    (review/read-reviews-from-xml user (now) app kuntagml)))

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
              (->> (vals lupapalvelu.attachment.type/attachment-types-by-permit-type)
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
  example"
  [text]
  (println (json/write-value-as-string
             {$push {:_sheriff-notes {:note    text
                                      :created (now)}}})))

(defn year-ago
  "Timestamp (in millis) for the year ago from now."
  []
  (-> (clj-time.core/now)
      (clj-time.core/minus (clj-time.core/months 12))
      clj-time.coerce/to-long))

(defn xml->application
  "Manual override for `prev-permit/fetch-prev-application`. The organization must exist. If
  `kuntalupatunnus` is not given, the first kuntalupatunnus found in xml is used."
  [org-id xml-filepath & [kuntalupatunnus]]
  (let [xml             (some-> (slurp xml-filepath)
                                (xml/parse-string "utf-8")
                                common-reader/strip-xml-namespaces)
        kuntalupatunnus (or kuntalupatunnus
                            (some-> (xml/select1 xml :kuntalupatunnus)
                                    :content
                                    first))]

    (prev-permit/fetch-prev-application! {:user    (usr/batchrun-user [org-id])
                                          :created (now)
                                          :data    {:organizationId  org-id
                                                    :kuntalupatunnus kuntalupatunnus}}
                                         xml)))

(defn run-mongochecks
  "Mongochecks smoke tests against the local database."
  []
  (smoke/execute-tests "mongochecks"))

(defn wfs-url
  "Builds an R WFS URL that can be used with `curl`, for example. This is sometimes useful
  when debugging connection or parameter issues. Usually, if only the KuntaGML message
  matters, the easier way is use Admin admin UI. `url` is the backing system url (see
  organization's `krysp.R.url`). `target-id` is either a backend or application
  id."
  ([url target-id permit-type]
   (krysp/wfs-krysp-url-with-service url
                                     (case (keyword permit-type)
                                       :R krysp/rakval-case-type
                                       :P krysp/poik-case-type)
                                     (krysp/property-in (krysp/get-tunnus-path (name permit-type)
                                                                               (if (ss/starts-with-i target-id "LP-")
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
  [filename backend-ids url prefix]
  (->> backend-ids
       (map-indexed (fn [i bid]
                      (format "curl -o \"%s_%05d.xml\" \"%s\""
                              prefix (inc i) (wfs-url url bid))))
       (cons "#!/bin/sh")
       (ss/join "\n")
       (spit filename)))

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
