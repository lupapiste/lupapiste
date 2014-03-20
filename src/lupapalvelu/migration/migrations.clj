(ns lupapalvelu.migration.migrations
  (:require [monger.operators :refer :all]
            [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [lupapalvelu.operations :as op]
            [clojure.walk :as walk]
            [sade.util :refer [dissoc-in postwalk-map]]
            [sade.common-reader :refer [strip-nils]]))

(defn drop-schema-data [document]
  (let [schema-info (-> document :schema :info (assoc :version 1))]
    (-> document
      (assoc :schema-info schema-info)
      (dissoc :schema))))

(defmigration schemas-be-gone-from-submitted
  {:apply-when (pos? (mongo/count :submitted-applications {:schema-version {$exists false}}))}
  (doseq [application (mongo/select :submitted-applications {:schema-version {$exists false}} {:documents true})]
    (mongo/update-by-id :submitted-applications (:id application) {$set {:schema-version 1
                                                               :documents (map drop-schema-data (:documents application))}})))

(defn verdict-to-verdics [{verdict :verdict}]
  {$set {:verdicts (map domain/->paatos verdict)}
   $unset {:verdict 1}})

(defmigration verdicts-migraation
  {:apply-when (pos? (mongo/count  :applications {:verdict {$exists true}}))}
  (let [applications (mongo/select :applications {:verdict {$exists true}})]
    (doall (map #(mongo/update-by-id :applications (:id %) (verdict-to-verdics %)) applications))))


(defn fix-invalid-schema-infos [{documents :documents operations :operations :as application}]
  (let [updated-documents (doall (for [o operations]
                                   (let [operation-name (keyword (:name o))
                                         target-document-name (:schema (operation-name op/operations))
                                         created (:created o)
                                         document-to-update (some (fn [d] (if
                                                                            (and
                                                                              (= created (:created d))
                                                                              (= target-document-name (get-in d [:schema-info :name])))
                                                                            d)) documents)
                                         updated (when document-to-update
                                                   (update-in document-to-update [:schema-info]
                                                     merge
                                                     {:op o
                                                      :removable (= "R" (:permitType application))}))
                                         ]
                                     updated)))
        unmatched-operations (filter
                               (fn [{id :id :as op}]
                                 (nil? (some
                                         (fn [d]
                                           (when
                                             (= id (get-in d [:schema-info :op :id]))
                                             d))
                                         updated-documents)))
                               operations)
        updated-documents (into updated-documents (for [o unmatched-operations]
                                                    (let [operation-name (keyword (:name o))
                                                          target-document-name (:schema (operation-name op/operations))
                                                          created (:created o)
                                                          document-to-update (some (fn [d]
                                                                                     (if
                                                                                       (and
                                                                                         (< created (:created d))
                                                                                         (= target-document-name (get-in d [:schema-info :name])))
                                                                                       d)) documents)
                                                          updated (when document-to-update
                                                                    (update-in document-to-update [:schema-info]
                                                                      merge
                                                                      {:op o
                                                                       :removable (= "R" (:permitType application))}))]
                                                      updated)))
        result (map
                 (fn [{id :id :as d}]
                   (if-let [r (some (fn [nd] (when (= id (:id nd)) nd)) updated-documents)]
                     r
                     d)) documents)
        new-operations (filter
                         (fn [{id :id :as op}]
                           (some
                             (fn [d]
                               (when
                                 (= id (get-in d [:schema-info :op :id]))
                                 d))
                             result))
                         operations)]
    (-> application
      (assoc :documents (vec result))
      (assoc :operations new-operations)
      )))

(defmigration invalid-schema-infos-validation
  (doseq [collection [:applications :submitted-applications]]
    (let [applications (mongo/select collection {:infoRequest false})]
      (doall (map #(mongo/update-by-id collection (:id %) (fix-invalid-schema-infos %)) applications)))))

;; Old migration previoisly run to applications collection only

(defn- update-document-kuntaroolikoodi [{data :data :as document}]
  (let [to-update (tools/deep-find data [:patevyys :kuntaRoolikoodi])
        updated-document (when (not-empty to-update)
                           (assoc document :data (reduce
                                                   (fn [d [old-key v]]
                                                     (let [new-key (conj (subvec old-key 0 (count old-key)) :kuntaRoolikoodi)
                                                           cleaned-up (dissoc-in d (conj old-key :patevyys :kuntaRoolikoodi))]
                                                       (assoc-in cleaned-up new-key v)))
                                                   data to-update)))]
    (if updated-document
      updated-document
      document)))


(defn kuntaroolikoodiUpdate [{d :documents :as a}]
    (assoc a :documents (map update-document-kuntaroolikoodi d)))

(defmigration submitted-applications-kuntaroolikoodi-migraation
  (let [applications (mongo/select :submitted-applications)]
    (dorun (map #(mongo/update-by-id :submitted-applications (:id %) (kuntaroolikoodiUpdate %)) applications))))

(defn- strip-fax [doc]
  (postwalk-map (partial map (fn [[k v]] (when (not= :fax k) [k v]))) doc))

(defn- cleanup-uusirakennus [doc]
  (if (and (= "uusiRakennus" (get-in doc [:schema-info :name])) (get-in doc [:data :henkilotiedot]))
    (-> doc
      (dissoc-in [:data :userId])
      (dissoc-in [:data :henkilotiedot])
      (dissoc-in [:data :osoite])
      (dissoc-in [:data :yhteystiedot]))
    doc))

(defn- fix-hakija [doc]
  (if (and (= "hakija" (get-in doc [:schema-info :name])) (get-in doc [:data :henkilotiedot]))
    (if (or (get-in doc [:data :henkilo]) (get-in doc [:data :yritys]))
      ; Cleanup
      (-> doc
        (dissoc-in [:data :userId])
        (dissoc-in [:data :henkilotiedot])
        (dissoc-in [:data :osoite])
        (dissoc-in [:data :yhteystiedot]))
      ; Or move
      (assoc doc :data {:henkilo (:data doc)}))
    doc))

(defn- pre-verdict-applicationState [current-state]
  (let [current-state (keyword current-state)]
    (cond (#{:draft :open} current-state) current-state
          (= :cancelled current-state) :draft
          :else :submitted)))

(defn- post-verdict-applicationState [current-state] :verdictGiven)
;  (let [current-state (keyword current-state)]
;    (cond (= :closed current-state) :constructionStarted
;          (= :cancelled current-state) :verdictGiven
;          :else current-state))

(defn- set-applicationState-to-attachment [verdict-given state attachment]
  (let [first-version      (-> attachment :versions first)
        attachment-created (or (:created first-version) (:modified attachment) 0)]
    (if (and verdict-given (> attachment-created verdict-given))
      (assoc attachment :applicationState (post-verdict-applicationState state))
      (assoc attachment :applicationState (pre-verdict-applicationState state)))))

(defn- attachments-with-applicationState [application]
  (let [verdicts      (:verdicts application)
        verdict-given (when (-> verdicts count pos?) (-> verdicts first :timestamp))]
    (map (partial set-applicationState-to-attachment verdict-given (:state application)) (:attachments application))))

(defmigration document-data-cleanup-rel-1.12
  (doseq [collection [:applications :submitted-applications]]
    (let [applications (mongo/select collection)]
      (dorun
        (map
          (fn [app]
            (mongo/update-by-id collection (:id app)
              {$set {:documents (map
                                  (fn [doc] (-> doc
                                                strip-fax
                                                cleanup-uusirakennus
                                                fix-hakija
                                                strip-nils))
                                  (:documents app))}}))
          applications)))))

(defmigration vetuma-token-cleanup-LUPA-976
  (mongo/drop-collection :vetuma))

(defmigration set-missing-default-values-for-keys-in-applications-LUPA-642
  (let [missing-keys-in-mongo [:buildings :shapes]
        keys-and-default-values (select-keys domain/application-skeleton missing-keys-in-mongo)]
    (doseq [collection [:applications :submitted-applications]
            [k d] keys-and-default-values]
      (mongo/update-by-query collection {k {$exists false}} {$set {k d}}))))

(defmigration drop-pistesijanti-from-documents
  (while (not-empty (mongo/select :applications {"documents.data.osoite.pistesijanti" {$exists true}}))
    (mongo/update-by-query :applications {"documents" {$elemMatch {"data.osoite.pistesijanti" {$exists true}}}} {$unset {"documents.$.data.osoite.pistesijanti" 1}})))

(defmigration kryps-config
  (doseq [organization (mongo/select :organizations)]
    (when-let [url (:legacy organization)]
      (doseq [permit-type (map :permitType (:scope organization))]
        (mongo/update-by-id :organizations (:id organization)
          {$set {(str "krysp." permit-type ".url") url
                 (str "krysp." permit-type ".version") "2.1.2"}
           $unset {:legacy 1}})))

    (when-let [ftp (:rakennus-ftp-user organization)]
      (mongo/update-by-id :organizations (:id organization)
        {$set {:krysp.R.ftpUser ftp}
         $unset {:rakennus-ftp-user 1}}))

    (when-let [ftp (:yleiset-alueet-ftp-user organization)]
      (mongo/update-by-id :organizations (:id organization)
        {$set {:krysp.YA.ftpUser ftp}
         $unset {:yleiset-alueet-ftp-user 1}}))

    (when-let [ftp (:poikkari-ftp-user organization)]
      (mongo/update-by-id :organizations (:id organization)
        {$set {:krysp.P.ftpUser ftp}
         $unset {:poikkari-ftp-user 1}}))))


(defmigration statementPersons-to-statementGivers
  {:apply-when (pos? (mongo/count  :organizations {:statementPersons {$exists true}}))}
  (doseq [organization (mongo/select :organizations {:statementPersons {$exists true}})]
    (mongo/update-by-id :organizations (:id organization)
                        {$set {:statementGivers (:statementPersons organization)}
                         $unset {:statementPersons 1}})))

(defmigration wfs-url-to-support-parameters
  (doseq [organization (mongo/select :organizations)]
    (doseq [[permit-type krysp-data] (:krysp organization)]
      (when-let [url (:url krysp-data)]
        (when (or (= (name permit-type) lupapalvelu.permit/R) (= (name permit-type) lupapalvelu.permit/P))
          (mongo/update-by-id :organizations (:id organization)
                              {$set {(str "krysp." (name permit-type) ".url") (str url "?outputFormat=KRYSP")}}))))))

(defmigration default-krysp-R-version
  {:apply-when (pos? (mongo/count :organizations {$and [{:krysp.R {$exists true}} {:krysp.R.version {$exists false}}]}))}
  (mongo/update-by-query :organizations
    {$and [{:krysp.R {$exists true}} {:krysp.R.version {$exists false}}]}
    {$set {:krysp.R.version "2.1.2"}}))

(defmigration default-krysp-P-version
  {:apply-when (pos? (mongo/count :organizations {$and [{:krysp.P {$exists true}} {:krysp.P.version {$exists false}}]}))}
  (mongo/update-by-query :organizations
    {$and [{:krysp.P {$exists true}} {:krysp.P.version {$exists false}}]}
    {$set {:krysp.P.version "2.1.2"}}))

(defmigration default-krysp-YA-version
  {:apply-when (pos? (mongo/count :organizations {$and [{:krysp.YA {$exists true}} {:krysp.YA.version {$exists false}}]}))}
  (mongo/update-by-query :organizations
    {$and [{:krysp.YA {$exists true}} {:krysp.YA.version {$exists false}}]}
    {$set {:krysp.YA.version "2.1.2"}}))


(defmigration attachment-applicationState
  (doseq [application (mongo/select :applications {"attachments.0" {$exists true}})]
    (mongo/update-by-id :applications (:id application)
      {$set {:attachments (attachments-with-applicationState application)}})))

(defn- remove-huoneistot-and-update-schema-name [document new-schema-name]
  (let [data (:data document)
        data-ilman-huoneistoja (dissoc data :huoneistot)]
    (assoc-in (assoc-in (assoc document :data data-ilman-huoneistoja) [:schema-info :i18name] (-> document :schema-info :name)) [:schema-info :name] new-schema-name)))

(defn get-operation-name [document]
  (get-in document [:schema-info :op :name]))

(defn get-schema-name [document]
  (get-in document [:schema-info :name]))

(defn remove-huoneistot-for [operation old-schema-name new-schema-name]
  (let [applications-to-update (mongo/select :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" operation} {"schema-info.name" old-schema-name}]}}})]
    (doseq [application applications-to-update]
      (let [new-documents (map (fn [document]
                                 (let [schema-name (get-schema-name document)
                                       operation-name (get-operation-name document)]
                                   (if (and (= operation-name operation) (= schema-name old-schema-name))
                                     (remove-huoneistot-and-update-schema-name document new-schema-name)
                                     document)))
                               (:documents application))]
        (mongo/update-by-id :applications (:id application) {$set {:documents new-documents}})))))


(defmigration vapaa-ajan-asuinrakennus-updates
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" "vapaa-ajan-asuinrakennus"} {"schema-info.name" "uusiRakennus"}] }}}))}
  (remove-huoneistot-for "vapaa-ajan-asuinrakennus" "uusiRakennus" "uusi-rakennus-ei-huoneistoa"))
(defmigration varasto-updates
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" "varasto-tms"} {"schema-info.name" "uusiRakennus"}] }}}))}
  (remove-huoneistot-for "varasto-tms" "uusiRakennus" "uusi-rakennus-ei-huoneistoa"))
(defmigration julkisivu-muutos-updates
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" "julkisivu-muutos"} {"schema-info.name" "rakennuksen-muuttaminen"}] }}}))}
  (remove-huoneistot-for "julkisivu-muutos" "rakennuksen-muuttaminen" "rakennuksen-muuttaminen-ei-huoneistoja"))
(defmigration markatila-updates
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" "markatilan-laajentaminen"} {"schema-info.name" "rakennuksen-muuttaminen"}] }}}))}
  (remove-huoneistot-for "markatilan-laajentaminen" "rakennuksen-muuttaminen" "rakennuksen-muuttaminen-ei-huoneistoja"))
(defmigration takka-muutos-updates
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" "takka-tai-hormi"} {"schema-info.name" "rakennuksen-muuttaminen"}] }}}))}
  (remove-huoneistot-for "takka-tai-hormi" "rakennuksen-muuttaminen" "rakennuksen-muuttaminen-ei-huoneistoja"))
(defmigration parveke-muutos-updates
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" "parveke-tai-terassi"} {"schema-info.name" "rakennuksen-muuttaminen"}] }}}))}
  (remove-huoneistot-for "parveke-tai-terassi" "rakennuksen-muuttaminen" "rakennuksen-muuttaminen-ei-huoneistoja"))
