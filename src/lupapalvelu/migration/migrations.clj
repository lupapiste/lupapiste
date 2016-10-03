(ns lupapalvelu.migration.migrations
  (:require [monger.operators :refer :all]
            [taoensso.timbre :as timbre :refer [debug debugf info infof warn warnf error errorf]]
            [clojure.walk :as walk]
            [clojure.set :refer [rename-keys] :as set]
            [sade.util :refer [dissoc-in postwalk-map strip-nils abs fn->>] :as util]
            [sade.core :refer [def-]]
            [sade.strings :as ss]
            [sade.property :as p]
            [sade.validators :as v]
            [lupapalvelu.action :as action]
            [lupapalvelu.application-meta-fields :as app-meta-fields]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.attachment.accessibility :as attaccess]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.operations :as op]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.user :as user]
            [lupapalvelu.migration.attachment-type-mapping :as attachment-type-mapping]
            [lupapalvelu.tasks :refer [task-doc-validation]]
            [sade.env :as env]
            [sade.excel-reader :as er]
            [sade.coordinate :as coord]))

(defn drop-schema-data [document]
  (let [schema-info (-> document :schema :info (assoc :version 1))]
    (-> document
      (assoc :schema-info schema-info)
      (dissoc :schema))))

#_(defmigration schemas-be-gone-from-submitted
   {:apply-when (pos? (mongo/count :submitted-applications {:schema-version {$exists false}}))}
   (doseq [application (mongo/select :submitted-applications {:schema-version {$exists false}} {:documents true})]
     (mongo/update-by-id :submitted-applications (:id application) {$set {:schema-version 1
                                                                          :documents (map drop-schema-data (:documents application))}})))

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

#_(defmigration invalid-schema-infos-validation
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

#_(defmigration submitted-applications-kuntaroolikoodi-migraation
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

#_(defmigration document-data-cleanup-rel-1.12
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

#_(defmigration set-missing-default-values-for-keys-in-applications-LUPA-642
   (let [missing-keys-in-mongo [:buildings :shapes]
         keys-and-default-values (select-keys domain/application-skeleton missing-keys-in-mongo)]
     (doseq [collection [:applications :submitted-applications]
             [k d] keys-and-default-values]
       (mongo/update-by-query collection {k {$exists false}} {$set {k d}}))))

#_(defmigration kryps-config
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


#_(defmigration statementPersons-to-statementGivers
   {:apply-when (pos? (mongo/count  :organizations {:statementPersons {$exists true}}))}
   (doseq [organization (mongo/select :organizations {:statementPersons {$exists true}})]
     (mongo/update-by-id :organizations (:id organization)
                         {$set {:statementGivers (:statementPersons organization)}
                          $unset {:statementPersons 1}})))

#_(defmigration wfs-url-to-support-parameters
   (doseq [organization (mongo/select :organizations)]
     (doseq [[permit-type krysp-data] (:krysp organization)]
       (when-let [url (:url krysp-data)]
         (when (or (= (name permit-type) lupapalvelu.permit/R) (= (name permit-type) lupapalvelu.permit/P))
           (mongo/update-by-id :organizations (:id organization)
                               {$set {(str "krysp." (name permit-type) ".url") (str url "?outputFormat=KRYSP")}}))))))

#_(defmigration default-krysp-R-version
   {:apply-when (pos? (mongo/count :organizations {$and [{:krysp.R {$exists true}} {:krysp.R.version {$exists false}}]}))}
   (mongo/update-by-query :organizations
     {$and [{:krysp.R {$exists true}} {:krysp.R.version {$exists false}}]}
     {$set {:krysp.R.version "2.1.2"}}))

#_(defmigration default-krysp-P-version
  {:apply-when (pos? (mongo/count :organizations {$and [{:krysp.P {$exists true}} {:krysp.P.version {$exists false}}]}))}
  (mongo/update-by-query :organizations
    {$and [{:krysp.P {$exists true}} {:krysp.P.version {$exists false}}]}
    {$set {:krysp.P.version "2.1.2"}}))

#_(defmigration default-krysp-YA-version
  {:apply-when (pos? (mongo/count :organizations {$and [{:krysp.YA {$exists true}} {:krysp.YA.version {$exists false}}]}))}
  (mongo/update-by-query :organizations
    {$and [{:krysp.YA {$exists true}} {:krysp.YA.version {$exists false}}]}
    {$set {:krysp.YA.version "2.1.2"}}))


#_(defmigration attachment-applicationState
  (doseq [application (mongo/select :applications {"attachments.0" {$exists true}})]
    (mongo/update-by-id :applications (:id application)
      {$set {:attachments (attachments-with-applicationState application)}})))

(defn- remove-huoneistot-and-update-schema-name [document new-schema-name]
  (let [data (:data document)
        data-ilman-huoneistoja (dissoc data :huoneistot)]
    (->  document
      (assoc :data data-ilman-huoneistoja)
      (assoc-in  [:schema-info :i18name] (-> document :schema-info :name))
      (assoc-in  [:schema-info :name] new-schema-name))))

(defn get-operation-name [document]
  (get-in document [:schema-info :op :name]))

(defn get-schema-name [document]
  (get-in document [:schema-info :name]))

(defn remove-data-for [operation old-schema-name new-schema-name update-fn]
  (doseq [collection [:applications :submitted-applications]]
    (let [applications-to-update (mongo/select collection {:documents {$elemMatch {$and [{ "schema-info.op.name" operation} {"schema-info.name" old-schema-name}]}}})]
     (doseq [application applications-to-update]
       (let [new-documents (map (fn [document]
                                  (let [schema-name (get-schema-name document)
                                        operation-name (get-operation-name document)]
                                    (if (and (= operation-name operation) (= schema-name old-schema-name))
                                      (update-fn document new-schema-name)
                                      document)))
                                (:documents application))]
         (mongo/update-by-id collection (:id application) {$set {:documents new-documents}}))))))

(defn remove-huoneistot-for [operation old-schema-name new-schema-name]
  (remove-data-for operation old-schema-name new-schema-name remove-huoneistot-and-update-schema-name))

(comment
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
    (remove-huoneistot-for "parveke-tai-terassi" "rakennuksen-muuttaminen" "rakennuksen-muuttaminen-ei-huoneistoja")))

(defn- update-krysp-version-for-all-orgs [permit-type from to]
  (let [path (str "krysp." permit-type ".version")]
    (mongo/update-by-query :organizations {path from} {$set {path to}})))

(comment
  (defmigration ymparistolupa-organization-krysp-212
      {:apply-when (pos? (mongo/count :organizations {"krysp.YL.version" "2.1.1"}))}
      (update-krysp-version-for-all-orgs "YL" "2.1.1" "2.1.2"))

  (defmigration mal-organization-krysp-212
    {:apply-when (pos? (mongo/count :organizations {"krysp.MAL.version" "2.1.1"}))}
    (update-krysp-version-for-all-orgs "MAL" "2.1.1" "2.1.2"))

  (defmigration vvvl-organization-krysp-213
    {:apply-when (pos? (mongo/count :organizations {"krysp.VVVL.version" "2.1.1"}))}
    (update-krysp-version-for-all-orgs "VVVL" "2.1.1" "2.1.3"))

  (defmigration yi-organization-krysp-212
    {:apply-when (pos? (mongo/count :organizations {"krysp.YI.version" "2.1.1"}))}
    (update-krysp-version-for-all-orgs "YI" "2.1.1" "2.1.2")))

(defn- remove-ominaisuustiedot-and-update-schema-name [document new-schema-name]
  (let [data (-> (:data document)
               (dissoc :lammitys)
               (dissoc :verkostoliittymat)
               (dissoc :varusteet)
               (dissoc :luokitus))]
    (-> document
      (assoc :data data)
      (assoc-in  [:schema-info :i18name] (-> document :schema-info :name))
      (assoc-in  [:schema-info :name] new-schema-name))))

(defn- remove-ominaisuustiedot-huoneistot-and-update-schema-name [document new-schema-name]
  (let [data (-> (:data document)
               (dissoc :lammitys)
               (dissoc :verkostoliittymat)
               (dissoc :varusteet)
               (dissoc :huoneistot)
               (dissoc :luokitus))]

    (-> document
      (assoc :data data)
      (assoc-in  [:schema-info :i18name] (-> document :schema-info :name))
      (assoc-in  [:schema-info :name] new-schema-name))))

(defn remove-ominaisuudet-for [operation old-schema-name new-schema-name]
  (remove-data-for operation old-schema-name new-schema-name remove-ominaisuustiedot-and-update-schema-name))

(defn remove-ominaisuudet-huoneistot-for [operation old-schema-name new-schema-name]
  (remove-data-for operation old-schema-name new-schema-name remove-ominaisuustiedot-huoneistot-and-update-schema-name))

(comment
  (defmigration rakennuksen-ominaistieto-updates-julkisivu-muutos
    {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{"schema-info.op.name" "julkisivu-muutos"} {"schema-info.name" "rakennuksen-muuttaminen-ei-huoneistoja"}] }}}))}
    (remove-ominaisuudet-for "julkisivu-muutos" "rakennuksen-muuttaminen-ei-huoneistoja" "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"))

  (defmigration rakennuksen-ominaistieto-updates-markatila
    {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{"schema-info.op.name" "markatilan-laajentaminen"} {"schema-info.name" "rakennuksen-muuttaminen-ei-huoneistoja"}] }}}))}
    (remove-ominaisuudet-for "markatilan-laajentaminen" "rakennuksen-muuttaminen-ei-huoneistoja" "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"))

  (defmigration rakennuksen-ominaistieto-updates-takka
    {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{"schema-info.op.name" "takka-tai-hormi"} {"schema-info.name" "rakennuksen-muuttaminen-ei-huoneistoja"}] }}}))}
    (remove-ominaisuudet-for "takka-tai-hormi" "rakennuksen-muuttaminen-ei-huoneistoja" "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"))

  (defmigration rakennuksen-ominaistieto-updates-parveke
    {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{"schema-info.op.name" "parveke-tai-terassi"} {"schema-info.name" "rakennuksen-muuttaminen-ei-huoneistoja"}] }}}))}
    (remove-ominaisuudet-for "parveke-tai-terassi" "rakennuksen-muuttaminen-ei-huoneistoja" "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"))

  (defmigration rakennuksen-ominaistieto-updates-purku
    {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{"schema-info.op.name" "purkaminen"} {"schema-info.name" "purku"}] }}}))}
    (remove-ominaisuudet-huoneistot-for "purkaminen" "purku" "purkaminen")))

(defn- convert-neighbors [orig-neighbors]
  (if-not (empty? orig-neighbors)
    (for [[k v] orig-neighbors
            :let [propertyId (-> v :neighbor :propertyId)
                  owner (-> v :neighbor :owner)
                  status (-> v :status)]]
        {:id (name k)
         :propertyId propertyId
         :status status
         :owner owner})
    []))

#_(defmigration neighbors-to-sequable
   (doseq [collection [:applications :submitted-applications]
           application (mongo/select collection {} {:neighbors 1})]
     (when (map? (:neighbors application))
       (mongo/update-by-id collection (:id application)
         {$set {:neighbors (convert-neighbors (:neighbors application))}}))))

#_(defmigration remove-sijoituksen-and-tyon-tarkoitus
   (doseq [collection [:applications :submitted-applications]]
     (let [applications-to-update (mongo/select collection {:documents {$elemMatch {$or [{:schema-info.name "yleiset-alueet-hankkeen-kuvaus-kaivulupa"}
                                                                                         {:schema-info.name "sijoituslupa-sijoituksen-tarkoitus"}]}}})]
       (doseq [application applications-to-update]
         (let [new-documents (map
                               #(if (= "yleiset-alueet-hankkeen-kuvaus-kaivulupa" (-> % :schema-info :name))
                                  (update-in % [:data] dissoc :sijoituksen-tarkoitus)
                                  %)
                               (:documents application))
               new-documents (remove #(= "sijoituslupa-sijoituksen-tarkoitus" (-> % :schema-info :name)) new-documents)]
           (mongo/update-by-id collection (:id application) {$set {:documents new-documents}}))))))


#_(defmigration move-operations-flags-into-their-scope
   {:apply-when (pos? (mongo/count :organizations {:new-application-enabled {$exists true}}))}
   ;; Let's expect all (un-migrated) organizations to have the "new-application-enabled" flag.
  (doseq [organization (mongo/select :organizations {:new-application-enabled {$exists true}})]
    (let [new-scopes (map
                       #(merge % {:inforequest-enabled     (or (:inforequest-enabled organization) false)
                                  :new-application-enabled (or (:new-application-enabled organization) false)
                                  :open-inforequest        (or (:open-inforequest organization) false)
                                  :open-inforequest-email  (:open-inforequest-email organization)})
                       (:scope organization))]
      (mongo/update-by-id :organizations (:id organization)
       {$unset {:inforequest-enabled ""
                :new-application-enabled ""
                :open-inforequest ""
                :open-inforequest-email ""}
        $set {:scope new-scopes}}))))

#_(defmigration generate-verdict-ids
   (doseq [application (mongo/select :applications {"verdicts.0" {$exists true}} {:verdicts 1, :attachments 1})]
     (let [verdicts (map #(assoc % :id (mongo/create-id)) (:verdicts application))
           id-for-urlhash (reduce
                             #(let [hashes (->> %2 :paatokset (map :poytakirjat) flatten (map :urlHash) (remove nil?))]
                                (merge %1 (zipmap hashes (repeat (:id %2)))))
                             {} verdicts)
           attachments (map
                         (fn [{:keys [target] :as a}]
                           (if (= "verdict" (:type target ))
                             (if-let [hash (:id target)]
                               ; Attachment for verdict from krysp
                               (assoc a :target (assoc target :id (id-for-urlhash hash) :urlHash hash))
                               ; Attachment for manual verdict
                               (assoc-in a [:target :id] (:id (first verdicts))))
                             a))
                         (:attachments application))]

       (mongo/update-by-id :applications (:id application) {$set {:verdicts verdicts, :attachments attachments}}))))

#_(defmigration convert-task-source-ids-v2
   (doseq [{verdicts :verdicts :as application} (mongo/select :applications {"tasks.0" {$exists true}} {:verdicts 1, :tasks 1})]
     (let [id-for-kuntalupatunnus (reduce #(if (:kuntalupatunnus %2) (assoc %1 (:kuntalupatunnus %2) (:id %2)) %1) {} verdicts)
           verdict-ids (set (map :id verdicts))
           tasks (map
                   (fn [{:keys [source] :as task} ]
                     (if (and (= "verdict" (:type source)) (not (verdict-ids (:id source))) )
                       (if (empty? verdicts)
                         (assoc task :source {}) ; no verdicts, clear invalid source
                         (let [kuntalupatunnus (first (clojure.string/split (:id source) #"/"))
                               verdict-id (or
                                            (id-for-kuntalupatunnus kuntalupatunnus)
                                            (:id (last verdicts)))]
                           (if verdict-id
                             (assoc-in task [:source :id] verdict-id)
                             (do
                               (warnf "Unable to resolve source id,\n  application id: %s,\n  task: %s,\n  id-for-kuntalupatunnus: %s \n" (:id application) task id-for-kuntalupatunnus)
                               task))))
                       task))
                   (:tasks application))]
       (when-not (= tasks (:tasks application))
         (mongo/update-by-id :applications (:id application) {$set {:tasks tasks}})))))

#_(defmigration unify-attachment-latest-version
   (doseq [application (mongo/select :applications {"state" {$in ["sent", "verdictGiven" "complement-needed", "constructionStarted"]}} {:attachments 1})
           {:keys [latestVersion] :as attachment} (:attachments application)]
     (let [last-version (last (:versions attachment))
           last-version-index (dec (count (:versions attachment)))]
       (when (and last-version (not= latestVersion last-version))
         (println (:id application) (:id attachment) "last version is out of sync")

         (assert (= (:version last-version) (:version latestVersion)))

         (when-not (= (:fileId last-version) (:fileId latestVersion)) (mongo/delete-file-by-id (:fileId last-version)))

         (println "Replacing " last-version " with " (:latestVersion attachment))

         (assert
           (pos?
             (mongo/update-by-query
               :applications
               {:_id (:id application), :attachments {$elemMatch {:id (:id attachment)}}}
               {$set {(str "attachments.$.versions." last-version-index) (:latestVersion attachment)}})))))))

#_(defmigration fix-missing-organizations
   {:apply-when (pos? (mongo/count :applications {:organization nil}))}
   (doseq [{:keys [id municipality permitType]} (mongo/select :applications {:organization nil} {:municipality 1, :permitType 1})]
     (let [organization-id (:id (organization/resolve-organization municipality permitType))]
       (assert organization-id)
       (mongo/update-by-id :applications id {$set {:organization organization-id}}))))

(defn flatten-huoneisto-data [{documents :documents}]
  (map
    (fn [doc]
      (if-let [to-update (seq (tools/deep-find doc :huoneistot ))]
        (reduce
          #(let [[p v] %2
                 path (conj p :huoneistot)]
             (reduce
               (fn [old-doc [n _]]
                 (update-in old-doc (conj path n)
                            (fn [old-data]
                              (-> old-data
                                (merge (:huoneistoTunnus old-data))
                                (dissoc :huoneistoTunnus)
                                (merge (:huoneistonTyyppi old-data))
                                (dissoc :huoneistonTyyppi)
                                (merge (:varusteet old-data))
                                (dissoc :varusteet)
                                ))))
               %1
               v)) doc to-update)
        doc
        )) documents))

#_(defmigration flatten-huoneisto
   (doseq [collection [:applications :submitted-applications]
           application (mongo/select collection {:infoRequest false})]
     (if (some seq (map #(tools/deep-find % :huoneistot) (:documents application)))
       (let [updated-documents (flatten-huoneisto-data application)]
         (mongo/update-by-id collection (:id application) {$set {:documents updated-documents}})))))


#_(defmigration add-location-to-rakennuspaikat
   {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{"schema-info.name" {$in ["rakennuspaikka"
                                                                                                          "poikkeusasian-rakennuspaikka"
                                                                                                          "vesihuolto-kiinteisto"
                                                                                                          "kiinteisto"]}}
                                                                                {:schema-info.type {$exists false}}]}}}))}
   (doseq [collection [:applications :submitted-applications]]
     (let [names #{"rakennuspaikka" "poikkeusasian-rakennuspaikka" "vesihuolto-kiinteisto" "kiinteisto"}
           applications-to-update (mongo/select collection)]
       (doseq [application applications-to-update]
         (let [new-documents (map
                               #(if (contains? names (-> % :schema-info :name))
                                 (update-in % [:schema-info] assoc :type "location")
                                 %)
                               (:documents application))]
           (mongo/update-by-id collection (:id application) {$set {:documents new-documents}}))))))

#_(defmigration tutkinto-mapping
   (let [mapping (er/read-map "tutkinto-mapping.xlsx")]
     (doseq [collection [:applications :submitted-applications]
            application (mongo/select collection {"documents.data.patevyys.koulutus.value" {$exists true}} {:documents 1})]
      (let [id (:id application)
            documents (map
                        (fn [doc]
                          (if-let [koulutus (get-in doc [:data :patevyys :koulutus :value])]
                            (let [normalized (-> koulutus ss/trim ss/lower-case)]
                              (if-not (ss/blank? normalized)
                                (let [mapped     (get mapping normalized "muu")
                                      modified (get-in doc [:data :patevyys :koulutus :modified])]
                                  (debugf "%s/%s: Mapping '%s' to %s" collection id koulutus mapped)
                                  (assoc-in doc [:data :patevyys :koulutusvalinta] {:value mapped, :modified modified}))
                                doc))
                            doc)) (:documents application))]
        (mongo/update-by-id collection id {$set {:documents documents}})))))

(def- operation-mappings
  {:asuinrakennus         [:kerrostalo-rivitalo :pientalo]
   :muu-uusi-rakentaminen [:muu-uusi-rakentaminen :teollisuusrakennus]
   :laajentaminen         [:kerrostalo-rt-laaj :pientalo-laaj :vapaa-ajan-rakennus-laaj :talousrakennus-laaj :teollisuusrakennus-laaj :muu-rakennus-laaj]
   :kayttotark-muutos     [:kayttotark-muutos :sisatila-muutos]
   :muu-laajentaminen     [:linjasaneeraus]
   :puun-kaataminen       [:puun-kaataminen :rak-valm-tyo]
   :jatkoaika             [:raktyo-aloit-loppuunsaat]})

(defn- copy-attachment-configs [old-op-attachments]
  (reduce (fn [new-attachment-configs [old-op-key old-attachment-config]]
            (if (old-op-key operation-mappings)
              (let [attachment-config (mapcat (fn [op-key] [op-key old-attachment-config]) (old-op-key operation-mappings))]
                (merge new-attachment-configs (apply hash-map attachment-config)))
              new-attachment-configs)) {} old-op-attachments))

(defn new-operations-attachments [old-operations-attachments]
  (merge old-operations-attachments (copy-attachment-configs old-operations-attachments)))

(defn new-selected-operations [old-ops]
  (when-not (nil? old-ops) ; no old ops -> do not mark explicit new ops
    (->> old-ops
         (map (fn [op]
                (if (operation-mappings op)
                  (operation-mappings op)
                  op)))
         flatten)))

#_(defmigration import-new-operations-for-organisations
   (doseq [org (mongo/select :organizations {:scope.permitType "R"})]
     (let [old-selected-operations (map keyword (:selected-operations org))
           old-operations-attachments (:operations-attachments org)]
       (organization/update-organization (:id org) {$set {:selected-operations    (new-selected-operations old-selected-operations)
                                                          :operations-attachments (new-operations-attachments old-operations-attachments)}}))))

(defn update-applications-array
  "Updates an array k in every application by mapping the array with f.
   Applications are fetched using the given query.
   Return the number of applications updated."
  [k f query]
  {:pre [(keyword? k) (fn? f) (map? query)]}
  (reduce + 0
          (for [collection [:applications :submitted-applications]
                application (mongo/select collection query {k 1})]
            (mongo/update-n collection {:_id (:id application)} {$set {k (map f (k application))}}))))

(defn update-bulletin-versions [k f query]
  "Maps f over each element of list with key k in application
   bulletins, obtained by the given query. Returns the number
   of bulletins updated."
  {:pre [(keyword? k) (fn? f) (map? query)]}
  (let [version-key (keyword (str "versions." (name k)))]
    (reduce + 0
      (for [bulletin (mongo/select :application-bulletins query {:versions 1})]
        (mongo/update-n :application-bulletins
          {:_id (:id bulletin)}
          {$set
            {:versions
              (map
                (fn [versio]
                  (assoc versio k (map f (get versio k))))
                (:versions bulletin))}})))))

(defn- populate-buildingids-to-doc [doc]
  (let [rakennusnro (get-in doc [:data :rakennusnro])
        manuaalinen-rakennusnro (get-in doc [:data :manuaalinen_rakennusnro])]
    (cond
      (-> manuaalinen-rakennusnro :value ss/blank? not) (update-in doc [:data] #(assoc % :buildingId (assoc manuaalinen-rakennusnro :value "other")))
      (:value rakennusnro) (update-in doc [:data] #(assoc % :buildingId rakennusnro))
      :else doc)))

#_(defmigration populate-buildingids-to-docs
   (update-applications-array
     :documents
     populate-buildingids-to-doc
     {:documents {$elemMatch {$or [{:data.rakennusnro.value {$exists true}} {:data.manuaalinen_rakennusnro.value {$exists true}}]}}}))

#_(defmigration populate-buildingids-to-buildings
   (update-applications-array
     :buildings
     #(assoc % :localShortId (:buildingId %), :nationalId nil, :localId nil)
     {:buildings.0 {$exists true}}))


#_(defmigration update-app-links-with-apptype
   (doseq [app-link (mongo/select :app-links)]
     (let [linkpermit-id (some
                           (fn [[k v]]
                             (when (= "linkpermit" (:type v))
                               (name k)))
                           app-link)
           app           (first (mongo/select :applications {:_id linkpermit-id}))
           apptype       (->> app :operations first :name)]
       (mongo/update-by-id
         :app-links
         (:id app-link)
         {$set {(str linkpermit-id ".apptype") apptype}}))))

(defn- merge-versions [old-versions {:keys [user version] :as new-version}]
  (let [next-ver (attachment/next-attachment-version (:version (last old-versions)) user)]
    (concat old-versions [(assoc new-version :version next-ver)])))

(defn- fixed-versions [required-flags-migration-time attachments-backup updated-attachments]
  (for [attachment attachments-backup
        :let [updated-attachment (some #(when (= (:id %) (:id attachment)) %) updated-attachments)]]
    (if updated-attachment
      (let [new-versions (filter (fn [v] (> (get v :created 0) required-flags-migration-time)) (:versions updated-attachment))]
        (update-in attachment [:versions] #(reduce merge-versions % new-versions)))
      attachment)))

(defn- removed-versions [attachments fs-file-ids]
  (for [attachment attachments]
    (update-in attachment [:versions] #(filter (fn [v] (fs-file-ids (:fileId v))) %))))

#_(defmigration restore-attachments
   (when (pos? (mongo/count :applicationsBackup))
     (let [fs-file-ids (set (map :id (mongo/select :fs.files)))
          required-flags-migration-time (:time (mongo/select-one :migrations {:name "required-flags-for-attachment-templates"}))]

       (assert (pos? required-flags-migration-time))

       (doseq [id (map :id (mongo/select :submitted-applications {} [:_id]))]
         (let [attachments-backup (:attachments (mongo/by-id :applicationsBackup id [:attachments]))
               restored-ids (set (map :id attachments-backup))

               current-attachments (:attachments (mongo/by-id :applications id [:attachments]))
               updated-attachments (filter #(some (fn [v] (> (get v :created 0) required-flags-migration-time)) (:versions %)) current-attachments)

               new-attachments (filter
                                 (fn [a] (> (:modified a) required-flags-migration-time))
                                 (remove #(restored-ids (:id %)) current-attachments))

               attachments (if (seq updated-attachments)
                             (concat
                               (fixed-versions required-flags-migration-time attachments-backup updated-attachments)
                               new-attachments)
                             (concat attachments-backup new-attachments))

               removed (removed-versions attachments fs-file-ids)
               latest-versions-updated (map (fn [a] (assoc a :latestVersion (-> a :versions last))) removed)
               ]
           (mongo/update-by-id :applications id {$set {:attachments latest-versions-updated}})
           )))))


(defn- is-attachment-added-on-application-creation [application attachment]
  ;; inside 100 ms window, just in case
  (< (abs (- (:modified attachment) (:created application))) 100))

#_(defmigration required-flags-for-attachment-templates-v2
   (doseq [collection [:applications :submitted-applications]
           application (mongo/select collection {"attachments.0" {$exists true}})]
     (mongo/update-by-id collection (:id application)
       {$set {:attachments (map
                             #(assoc % :required (is-attachment-added-on-application-creation application %))
                             (:attachments application))}})))

#_(defmigration new-forPrinting-flag
    (update-applications-array
      :attachments
      #(assoc % :forPrinting false)
      {:attachments.0 {$exists true}}))

#_(defmigration app-required-fields-filling-obligatory
  {:apply-when (pos? (mongo/count :organizations {:app-required-fields-filling-obligatory {$exists false}}))}
  (doseq [organization (mongo/select :organizations {:app-required-fields-filling-obligatory {$exists false}})]
    (mongo/update-by-id :organizations (:id organization)
      {$set {:app-required-fields-filling-obligatory false}})))

#_(defmigration kopiolaitos-info
  {:apply-when (pos? (mongo/count :organizations {$or [{:kopiolaitos-email {$exists false}}
                                                       {:kopiolaitos-orderer-address {$exists false}}
                                                       {:kopiolaitos-orderer-email {$exists false}}
                                                       {:kopiolaitos-orderer-phone {$exists false}}]}))}
  (doseq [organization (mongo/select :organizations {$or [{:kopiolaitos-email {$exists false}}
                                                          {:kopiolaitos-orderer-address {$exists false}}
                                                          {:kopiolaitos-orderer-email {$exists false}}
                                                          {:kopiolaitos-orderer-phone {$exists false}}]})]
    (mongo/update-by-id :organizations (:id organization)
      {$set {:kopiolaitos-email           (or (:kopiolaitos-email organization) nil)
             :kopiolaitos-orderer-address (or (:kopiolaitos-orderer-address organization) nil)
             :kopiolaitos-orderer-email   (or (:kopiolaitos-orderer-email organization) nil)
             :kopiolaitos-orderer-phone   (or (:kopiolaitos-orderer-phone organization) nil)}})))

(def known-good-domains #{"luukku.com" "suomi24.fi" "turku.fi" "kolumbus.fi"
                          "gmail.fi" "gmail.com" "aol.com" "sweco.fi" "me.com"
                          "hotmail.com" "fimnet.fi" "hotmail.fi"
                          "welho.com" "parkano.fi" "rautjarvi.fi" "lupapiste.fi"
                          "elisanet.fi" "elisa.fi" "yit.fi" "jarvenpaa.fi" "jippii.fi"})

#_(defmigration cleanup-activation-collection-v3
   (let [active-emails (map :email (mongo/select :users {:enabled true, :role "applicant"} {:email 1}))]
     (mongo/remove-many :activation {:email {$in active-emails}}))
   (doseq [{:keys [id email]} (mongo/select :activation)]
     (let [known-domain (known-good-domains (ss/suffix email "@"))]
       (when (or (.endsWith email ".f") (and (not known-domain) (not (sade.dns/valid-mx-domain? email))))
        (mongo/remove :activation id)))))

#_(defmigration comment-roles-v2
   (update-applications-array
     :comments
     #(if (= (:roles %) ["applicant" "authority"]) (assoc % :roles [:applicant :authority :oirAuthority]) %)
     {"comments.0" {$exists true}, :openInfoRequest true}))

#_(defmigration select-all-operations-for-organizatio-if-none-selected
   (let [organizations (mongo/select :organizations {$or [{:selected-operations {$size 0}},
                                                          {:selected-operations {$exists false}},
                                                          {:selected-operations nil}]})]
        (doseq [organization organizations]
          (let [org-permit-types (set (map :permitType (:scope organization)))
                operations (map first (filter (fn [[_ v]] (org-permit-types (name (:permit-type v))))  op/operations))]
               (mongo/update-by-id :organizations (:id organization)
                                   {$set {:selected-operations operations}})))
     ))

#_(defmigration user-organization-cleanup
   {:apply-when (pos? (mongo/count :users {:organization {$exists true}}))}
   (mongo/update-by-query :users {:organization {$exists true}} {$unset {:organization 0}}))

(defmigration rename-foreman-competence-documents
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {"schema-info.name" {$regex #"^tyonjohtaja"}
                                                                         "data.patevyys"    {$exists true}}}}))}
  (update-applications-array
    :documents
    (fn [doc]
      (if (re-find #"^tyonjohtaja" (-> doc :schema-info :name))
        (update-in doc [:data] clojure.set/rename-keys {:patevyys :patevyys-tyonjohtaja})
        doc))
    {"documents.schema-info.name" {$regex #"^tyonjohtaja"}}))

(defmigration rename-suunnittelutarveratkaisun-lisaosa-changed-fields
  {:apply-when (pos? (mongo/count :applications {$or [{"documents.data.vaikutukset_yhdyskuntakehykselle.etaisyyys_alakouluun" {$exists true}}
                                                      {"documents.data.vaikutukset_yhdyskuntakehykselle.etaisyyys_ylakouluun" {$exists true}}]}))}
  (update-applications-array
    :documents
    (fn [doc]
      (if (= (-> doc :schema-info :name) "suunnittelutarveratkaisun-lisaosa")
        (update-in doc [:data :vaikutukset_yhdyskuntakehykselle]
                   clojure.set/rename-keys {:etaisyyys_alakouluun :etaisyys_alakouluun :etaisyyys_ylakouluun :etaisyys_ylakouluun})
        doc))
    {"documents.schema-info.name" "suunnittelutarveratkaisun-lisaosa"}))

(defmigration create-transfered-to-backing-system-transfer-entry
  (doseq [collection [:applications :submitted-applications]
          application (mongo/select collection {$and [{:transfers {$not {$elemMatch {:type "exported-to-backing-system"}}}}
                                                      {:sent {$ne nil}}]})]
    (mongo/update-by-id collection (:id application)
                        {$push {:transfers {:type "exported-to-backing-system"
                                            :timestamp (:sent application)}}})))

(defn- change-patevyys-muu-key [doc]
  (let [koulutusvalinta-path             [:data :patevyys             :koulutusvalinta :value]
        tyonjohtaja-koulutusvalinta-path [:data :patevyys-tyonjohtaja :koulutusvalinta :value]]
    (cond
     (= "muu" (get-in doc koulutusvalinta-path))             (assoc-in doc koulutusvalinta-path "other")
     (= "muu" (get-in doc tyonjohtaja-koulutusvalinta-path)) (assoc-in doc tyonjohtaja-koulutusvalinta-path "other")
     :else doc)))

(defmigration patevyys-muu-key-to-other
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$or [{:data.patevyys.koulutusvalinta.value "muu"}
                                                                              {:data.patevyys-tyonjohtaja.koulutusvalinta.value "muu"}]}}}))}
  (update-applications-array
    :documents
    change-patevyys-muu-key
    {:documents {$elemMatch {$or [{:data.patevyys.koulutusvalinta.value "muu"}
                                  {:data.patevyys-tyonjohtaja.koulutusvalinta.value "muu"}]}}}))

(defmigration remove-organizations-field-from-dummy-and-applicant
  {:apply-when (pos? (mongo/count :users {$and [{:organizations {$exists true}} {:role {$in ["dummy", "applicant"]}}]}))}
  (mongo/update-by-query :users {$and [{:organizations {$exists true}} {:role {$in ["dummy", "applicant"]}}]} {$unset {:organizations 1}}))

(defn organizations->org-authz [coll]
  (let [users (mongo/select coll {:organizations {$exists true}})]
    (reduce + 0
            (for [{:keys [organizations id role]} users]
              (let [org-authz (into {} (for [org organizations] [(str "orgAuthz." org) [role]]))]
                (if (empty? org-authz)
                  (mongo/update-n coll {:_id id} {$set {:orgAuthz {}}
                                                  $unset {:organizations 1}})
                  (mongo/update-n coll {:_id id} {$set org-authz
                                                  $unset {:organizations 1}})))))))

(defmigration add-org-authz
  {:apply-when (pos? (mongo/count :users {:organizations {$exists true}}))}
  (organizations->org-authz :users))

(defmigration tyonjohtaja-v1-vastuuaika-cleanup
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {"schema-info.name" "tyonjohtaja", "data.vastuuaika" {$exists true}}}}))}
  (update-applications-array
    :documents
    (fn [doc]
      (if (= (-> doc :schema-info :name) "tyonjohtaja")
        (util/dissoc-in doc [:data :vastuuaika])
        doc))
    {:documents {$elemMatch {"schema-info.name" "tyonjohtaja", "data.vastuuaika" {$exists true}}}}))

(defmigration application-authority-default-keys
  {:apply-when (pos? (mongo/count :applications {:authority.lastName {$exists false}}))}
  (mongo/update-n :applications {:authority.lastName {$exists false}} {$set {:authority (:authority domain/application-skeleton)}} :multi true))

(defmigration strip-FI-from-y-tunnus
  {:apply-when (pos? (mongo/count :companies {:y {$regex #"^FI"}}))}
  (doseq [company (mongo/select :companies {:y {$regex #"^FI"}})]
    (mongo/update-by-id :companies (:id company) {$set {:y (subs (:y company) 2)}})))

(defmigration company-default-account-type
  {:apply-when (pos? (mongo/count :companies {:accountType {$exists false}}))}
  (mongo/update-n :companies {:accountType {$exists false}} {$set {:accountType "account15"}} :multi true))

(def invoicing-operator-mapping
  {
;   "003701274855102 OKOYFIHH" "OKOYFIHH"
;   "0036714377140" "003714377140"
;   "003703575029 " "003703575029"
;   "00370357529" "003703575029"
;   "003703675029" "003703575029"
;   "003708599126/Liaison Technologies Oy" "003708599126"
;   "003710948874 " "003710948874"
;   "003714377140 Enfo" "003714377140"
;   "003714377140ENFO" "003714377140"
;   "003721291126 " "003721291126"
;   "BASWARE (BAWCFI22)" "BAWCFI22"
;   "BAWCF122" "BAWCFI22"
;   "BasWare" "BAWCFI22"
;   "Basware" "BAWCFI22"
   "Basware Oyj" "BAWCFI22"
;   "CGI / 003703575029" "003703575029"
;   "Danske Bank" "DNBAFIHX"
;   "Enfo" "003714377140"
;   "Enfo Oyj" "003714377140"
;   "Enfo Oyj 003714377140" "003714377140"
;   "Enfo Zender Oy" "003714377140"
;   "Enfo Zender Oy / 003714377140" "003714377140"
;   "Liaison" "003708599126"
;   "Logica" "003703575029"
;   "Nordea (NDEAFIHH)" "NDEAFIHH"
;   "OKOYFIHH " "OKOYFIHH"
;   "Opus Capita Group Oy" "003710948874"
   "OpusCapita Group Oy " "003710948874"
;   "OpusCapita Group Oy" "003710948874"
;   "OpusCapita Group Oy  003710948874" "003710948874"
;   "Tieto Oyj" "003701011385"
;   "dabafihh" "DABAFIHH"
;   "enfo" "003714377140"
;   "logica 00370357502" "003703575029"
   "logica, 00370357502" "003703575029"
;   "003701011385 OKOYFIHH" "OKOYFIHH"
;   "003715482348" "OKOYFIHH"
   })

(defmigration convert-invoicing-operator-values-from-documents-v2
  (let [old-op-names (keys invoicing-operator-mapping)
        path         [:data :yritys :verkkolaskutustieto :valittajaTunnus :value]
        query        (->> (cons :documents path)
                          (map name)
                          (clojure.string/join "."))]
    (update-applications-array
      :documents
      (fn [doc]
        (if-let [current-val (get-in doc path)]
          (let [replace-val (get invoicing-operator-mapping current-val)]
            (assoc-in doc path replace-val))
          doc))
      {query {$in old-op-names}})))

; To find current unmapped operator values
(comment
  (let [cur-vals (mongo/distinct :applications "documents.data.yritys.verkkolaskutustieto.valittajaTunnus.value")]
    (remove (fn [val] (some #(= val %) (map :name lupapalvelu.document.schemas/e-invoice-operators))) cur-vals)))

(defmigration add-permanent-archive-property-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:permanent-archive-enabled {$exists false}}))}
  (doseq [organization (mongo/select :organizations {:permanent-archive-enabled {$exists false}})]
    (mongo/update-by-id :organizations (:id organization)
      {$set {:permanent-archive-enabled false}})))

(defmigration unset-company-address2
  {:apply-when (pos? (mongo/count :companies {:address2 {$exists true}}))}
  (mongo/update-n :companies {:address2 {$exists true}} {$unset {:address2 1}} :multi true))

(defmigration tutkinto-mapping-in-own-info
  (let [target-keys-set (conj (set (map :name (:body lupapalvelu.document.schemas/koulutusvalinta))) "other")
        mapping (er/read-map "tutkinto-mapping.xlsx")]
    (doseq [user (mongo/select :users {"degree" {$exists true}} {:degree 1})]
      (when-let [koulutus (:degree user)]
        (let [normalized (-> koulutus ss/trim ss/lower-case)]
          (when-not (or (ss/blank? normalized) (target-keys-set normalized))
            (let [mapped (get mapping normalized "")]
              (debugf "%s/%s: Mapping '%s' to %s" :users (:id user) koulutus mapped)
              (mongo/update-by-id :users (:id user) {$set {:degree mapped}}))))))))

(comment
  (let [cur-vals (mongo/distinct :users "degree")]
    (remove (fn [val] (some #(= val %) (map :name (:body lupapalvelu.document.schemas/koulutusvalinta)))) cur-vals)))

(defmigration separate-operations-to-primary-and-secondary-operations
  {:apply-when (or (pos? (mongo/count :applications {:operations {$exists true}})) (pos? (mongo/count :submitted-applications {:operations {$exists true}})))}
  (doseq [collection [:applications :submitted-applications]
          application (mongo/select collection {:operations {$exists true}} {:operations 1})
          :let [primaryOperation (-> application :operations first)
                secondaryOperations (-> application :operations rest)]]
    (mongo/update-by-id collection (:id application) {$set   {:primaryOperation    primaryOperation
                                                              :secondaryOperations secondaryOperations}
                                                      $unset {:operations 1}})))

(defmigration hakija-documents-to-hakija-r
  {:apply-when (or (pos? (mongo/count :applications {$and [{:permitType "R"} {:documents {$elemMatch {"schema-info.name" "hakija"}}}]}))
                   (pos? (mongo/count :submitted-applications {$and [{:permitType "R"} {:documents {$elemMatch {"schema-info.name" "hakija"}}}]})))}
  (update-applications-array
    :documents
    (fn [{schema-info :schema-info :as doc}]
      (if (= "hakija" (:name schema-info))
        (assoc-in doc [:schema-info :name] "hakija-r")
        doc))
    {$and [{:permitType "R"} {:documents {$elemMatch {"schema-info.name" "hakija"}}}]}))

(defmigration add-subtype-for-maksaja-documents
  {:apply-when (or (pos? (mongo/count :applications {"documents" {$elemMatch {"schema-info.name" "maksaja", "schema-info.subtype" {$exists false}}}}))
                   (pos? (mongo/count :submitted-applications {"documents" {$elemMatch {"schema-info.name" "maksaja", "schema-info.subtype" {$exists false}}}})))}
  (update-applications-array
    :documents
    (fn [{schema-info :schema-info :as doc}]
      (if (and (= "maksaja" (:name schema-info)) (ss/blank? (:subtype schema-info)))
        (assoc-in doc [:schema-info :subtype] "maksaja")
        doc))
    {"documents" {$elemMatch {"schema-info.name" "maksaja", "schema-info.subtype" {$exists false}}}}))

(defmigration convert-statement-values
  {:apply-when (pos? (mongo/count :applications {"statements.status" {"$in" ["yes" "no" "condition"]}}))}
  (update-applications-array
      :statements
      (fn [{status :status :as statement}]
        (assoc statement :status (case status
                                   "yes"       "puoltaa"
                                   "no"        "ei-puolla"
                                   "condition" "ehdoilla"
                                   status)))
      {"statements.status" {"$in" ["yes" "no" "condition"]}}))

(defmigration location-to-array
  {:apply-when (pos? (mongo/count :applications {:location {$type 3}}))}
  (reduce + 0
          (for [collection [:applications :submitted-applications]
                application (mongo/select collection {} {:location 1})]
            (let [{:keys [x y]} (:location application)]
              (mongo/update-n collection {:_id (:id application)} {$set {:location [x y]}})))))

(defn- rakennustunnus [property-id building-number]
  (let [parts (rest (re-matches p/db-property-id-pattern property-id))]
    (apply format "%s-%s-%s-%s %s" (concat parts [building-number]))))

(defn- resolve-new-national-id? [building-number national-id]
  (and (= 3 (count building-number)) (ss/blank? national-id)))

(defn- find-national-id [conversion-table application-id property-id building-number]
  (let [lookup (rakennustunnus property-id building-number)
        new-id (mongo/select-one conversion-table {:RAKENNUSTUNNUS lookup})]
    (if (v/rakennustunnus? (:VTJ_PRT new-id))
      new-id
      (println application-id lookup new-id))))

(defn- building-raki-conversion [conversion-table application-id building]
  (let [old-id (:buildingId building)
        national-id (:nationalId building)
        property-id (:propertyId building)]
    (if (and (resolve-new-national-id? old-id national-id) (not (.endsWith property-id "00000000")))
      (if-let [new-id (find-national-id conversion-table application-id property-id old-id)]
        (assoc building
          :buildingId (:VTJ_PRT new-id)
          :nationalId (:VTJ_PRT new-id)
          :localId (:KUNNAN_PYSYVA_RAKNRO new-id))
        building)
      building)))

(defn- document-raki-conversion [conversion-table application-id property-id doc]
  (let [old-id (get-in doc [:data :buildingId :value])
        national-id (get-in doc [:data :valtakunnallinenNumero :value])]
    (if (resolve-new-national-id? old-id national-id)
      (if-let [new-id (find-national-id conversion-table application-id property-id old-id)]
        (-> doc
          (assoc-in [:data :buildingId :value] (:VTJ_PRT new-id))
          (assoc-in [:data :valtakunnallinenNumero :value] (:VTJ_PRT new-id)))
        doc)
      doc)))

(defn- task-raki-conversion [conversion-table application-id property-id task]
  (if (empty? (get-in task [:data :rakennus]))
    task
    (update-in task [:data :rakennus]
     #(reduce
        (fn [m [k v]]
          (assoc m k
            (let [old-id (get-in v [:rakennus :rakennusnro :value])
                  national-id (get-in v [:rakennus :valtakunnallinenNumero :value])]
              (if (resolve-new-national-id? old-id national-id) ; task has old style short id but no national id
                (if-let [new-id (find-national-id conversion-table application-id property-id old-id)]
                  (assoc-in v [:rakennus :valtakunnallinenNumero :value] (:VTJ_PRT new-id))
                  v)
                v)
              )))
        {} %))))

(defn- raki-conversion [municipality conversion-table]
  {:pre [(string? municipality) (string? conversion-table)]}
  (let [query {$and [{:municipality municipality}
                     {$or [{"buildings.0" {$exists true}}
                           {"documents.data.buildingId" {$exists true}}
                           {"tasks.data.rakennus" {$exists true}}]}]}]
    (doseq [collection [:applications :submitted-applications]
            {:keys [id buildings documents tasks propertyId]} (mongo/select collection query)]
      (mongo/update-by-id collection id
        {$set {:buildings (map (partial building-raki-conversion conversion-table id) buildings)
               :documents (map (partial document-raki-conversion conversion-table id propertyId) documents)
               :tasks (map (partial task-raki-conversion conversion-table id propertyId) tasks)}}))))

(defmigration jarvenpaa-raki (raki-conversion "186" "jarvenpaa_raki"))


(defmigration ilmoitusHakemusValitsin-permitSubtype
  (reduce + 0
    (for [collection [:applications :submitted-applications]
          application (mongo/select collection
                        {$or [{:primaryOperation.name "tyonjohtajan-nimeaminen-v2"}
                              {:primaryOperation nil, :documents.schema-info.name "tyonjohtaja-v2"}]}
                        {:documents 1})
          :let [doc (domain/get-document-by-name application "tyonjohtaja-v2")
                val (-> doc :data :ilmoitusHakemusValitsin :value)
                subtype (if (= "ilmoitus" val)
                          :tyonjohtaja-ilmoitus
                          :tyonjohtaja-hakemus)]]
      (mongo/update-n collection
        {:_id (:id application), :documents {$elemMatch {:id (:id doc)}}}
        {$set {:permitSubtype subtype}
         $unset {:documents.$.data.ilmoitusHakemusValitsin 1}}))))

(defn- add-approver-role-for-authority-in-org
  [[org org-roles]]
  [org (if (some (partial = "authority") org-roles)
         (conj org-roles "approver")
         org-roles)])

(defn- add-approver-roles-for-authority
  [{org-authz :orgAuthz :as user}]
  (->> org-authz
       (map add-approver-role-for-authority-in-org)
       (into {})
       (assoc user :orgAuthz)))

(defmigration approver-roles-for-authorities
  (doseq [authority (mongo/select :users {:role "authority" :orgAuthz {$exists true}})]
    (mongo/update-by-id :users (:id authority) (add-approver-roles-for-authority authority))))

(defmigration rip-rakennus-schema-part-from-ya-katselmukset
  {:apply-when (pos? (mongo/count :applications {:permitType "YA"
                                                 :tasks {$elemMatch {:schema-info.name "task-katselmus"}}}))}
  (update-applications-array
    :tasks
    (fn [task]
      (if (= "task-katselmus" (-> task :schema-info :name))
        (-> task
          (dissoc-in [:data :rakennus])
          (assoc-in [:schema-info :name] "task-katselmus-ya"))
        task))
    {:permitType "YA"
     :tasks {$elemMatch {:schema-info.name "task-katselmus"}}}))

(defn extract-credentials-from-krysp-address
  [[permit-type {url :url :as krysp-config}]]
  (when url
    (let [[_ protocol username password url] (re-matches #"(https?://)([^:]*:)?([^@]*@)?(.*$)" url)
          username (apply str (butlast username))
          password (apply str (butlast password))]
      (when (and url (not (empty? username)) (not (empty? password)))
        [permit-type (merge krysp-config {:url (str protocol url) :username username :password password})]))))

(defn update-krysp-config
  [{organization-id :id} permit-type {url :url username :username password :password version :version}]
  (when (and username password)
    (organization/set-krysp-endpoint organization-id url username password (name permit-type) version)))

(defn move-credentials-out-from-organization-krysp-addresses
  [{krysp-configs :krysp :as organization}]
  (doseq [[permit-type config] (map extract-credentials-from-krysp-address krysp-configs)]
    (update-krysp-config organization permit-type config)))

(defmigration move-credentials-out-from-krysp-addresses
  (doseq [organization (mongo/select :organizations)]
    (move-credentials-out-from-organization-krysp-addresses organization)))

(defmigration foreman-index-v2
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (let [applications (mongo/select collection {} [:documents])]
        (count (map #(mongo/update-by-id collection (:id %) (app-meta-fields/foreman-index-update %)) applications))))))

; 2015-10-14:
;> db.applications.distinct("state", {"permitSubtype": "tyonjohtaja-hakemus"})
;[
;        "canceled",
;        "verdictGiven",
;        "draft",
;        "open",
;        "submitted",
;        "answered",
;        "sent",
;        "complement-needed"
;]

(defmigration tyonjohtaja-hakemus-verdict-given-mapping
  {:apply-when (pos? (mongo/count :applications {:permitSubtype "tyonjohtaja-hakemus", :state "verdictGiven"}))}
  (mongo/update-by-query :applications {:permitSubtype "tyonjohtaja-hakemus", :state "verdictGiven"} {$set {:state :foremanVerdictGiven}}))


; 2015-10-14:
;> db.applications.distinct("state", {"permitSubtype": "tyonjohtaja-ilmoitus"})
;[
;        "closed",
;        "verdictGiven",
;        "canceled",
;        "draft",
;        "open",
;        "submitted",
;        "complement-needed"
;]

(defmigration tyonjohtaja-ilmoitus-closed-mapping
  {:apply-when (pos? (mongo/count :applications {:permitSubtype "tyonjohtaja-ilmoitus", :state "closed"}))}
  (reduce + 0
    (for [{:keys [id closed]} (mongo/select :applications {:permitSubtype "tyonjohtaja-ilmoitus", :state "closed"} [:closed])]
      (mongo/update-by-query :applications {:_id id} {$set {:state :acknowledged, :acknowledged closed}, $unset {:closed 1}}))))

(defmigration tyonjohtaja-ilmoitus-verdict-given-mapping
  {:apply-when (pos? (mongo/count :applications {:permitSubtype "tyonjohtaja-ilmoitus", :state "verdictGiven"}))}
  (reduce + 0
    (for [{:keys [id verdicts]} (mongo/select :applications {:permitSubtype "tyonjohtaja-ilmoitus", :state "verdictGiven"} [:verdicts])
          :let [timestamp (->> (map :timestamp verdicts) (filter number?) (apply min))]]
      (mongo/update-by-query :applications {:_id id} {$set {:state :acknowledged, :acknowledged timestamp}}))))

(defn pena?
  "Pena has been deleted from prod db"
  [user] (= "777777777777777777000020" (:id user)))

(defn init-application-history [{:keys [created opened infoRequest convertedToApplication permitSubtype] :as application}]
  (let [owner-auth (first (auth/get-auths-by-role application :owner))
        owner-user (user/find-user (select-keys owner-auth [:id]))
        creator (cond
                  (= permitSubtype "muutoslupa") (:user (mongo/by-id :muutoslupa created))
                  owner-user owner-user
                  (pena? owner-auth) (merge owner-auth {:role "applicant" :firstName "Testaaja" :lastName "Solita"}))

        _ (assert (:id creator) (:id application))

        state (if (= permitSubtype "muutoslupa")
                (if (user/authority? creator) "open" "draft")
                (cond
                  (or infoRequest convertedToApplication)  "info"
                  (= opened created) "open"
                  (and (ss/blank? (:personId creator)) (user/authority? creator)) "open"
                  :else "draft"))]
    {$set {:history [{:state state, :ts created, :user (user/summary creator)}]}}))

(defmigration init-history
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (let [applications (mongo/select collection {:history.0 {$exists false}} [:created :opened :auth :infoRequest :convertedToApplication :permitSubtype])]
        (count (map #(mongo/update-by-id collection (:id %) (init-application-history %)) applications))))))

(defmigration complement-needed-camelcase
  (mongo/update-by-query :applications {:state "complement-needed"} {$set {:state "complementNeeded"}}))

(defn change-valid-pdfa-to-archivable [version]
  {:post [%]}
  (if (contains? version :valid-pdfa)
    (let [valid-pdfa? (boolean (:valid-pdfa version))]
      (-> (assoc version :archivable valid-pdfa? :archivabilityError (when-not valid-pdfa? :invalid-pdfa))
          (dissoc :valid-pdfa)))
    version))

(defn update-valid-pdfa-to-arhivable-on-attachment-versions [attachment]
  {:post [%]}
  (if (:latestVersion attachment)
    (-> (assoc attachment :versions (map change-valid-pdfa-to-archivable (:versions attachment)))
        (assoc :latestVersion (change-valid-pdfa-to-archivable (:latestVersion attachment))))
    attachment))

(defmigration set-general-archivability-boolean-v2
  {:apply-when (pos? (mongo/count :applications {"attachments.versions.valid-pdfa" {$exists true}}))}
  (update-applications-array
    :attachments
    update-valid-pdfa-to-arhivable-on-attachment-versions
    {"attachments.versions.valid-pdfa" {$exists true}}))

(defn update-document-tila-metadata [doc]
  (if-let [tila (get-in doc [:metadata :tila])]
    (let [new-tila (if (.equalsIgnoreCase "valmis" tila) :valmis :luonnos)]
      (assoc-in doc [:metadata :tila] new-tila))
    doc))

(defn update-array-metadata [application]
  (->> [:attachments :verdicts :statements]
       (map (fn [k] (if-let [docs (seq (k application))]
                      [k (map update-document-tila-metadata docs)]
                      nil)))
       (remove nil?)
       (into {})))

(defmigration update-tila-metadata-value-in-all-metadata-maps
  {:apply-when (pos? (mongo/count :applications {$and [{"metadata.tila" {$exists true}} {"metadata.tila" {$nin ["luonnos" "valmis" "arkistoitu"]}}]}))}
  (doseq [application (mongo/select :applications {$and [{"metadata.tila" {$exists true}} {"metadata.tila" {$nin ["luonnos" "valmis" "arkistoitu"]}}]})]
    (let [data-for-$set (-> (update-array-metadata application)
                            (merge {:metadata (:metadata (update-document-tila-metadata application))}))]
      (mongo/update-n :applications {:_id (:id application)} {$set data-for-$set}))))

(defmigration r-application-hankkeen-kuvaus-documents-to-hankkeen-kuvaus-rakennuslupa-v2
  {:apply-when (or (pos? (mongo/count :applications {$and [{:permitType "R"} {:documents {$elemMatch {"schema-info.name" "hankkeen-kuvaus"}}}]}))
                   (pos? (mongo/count :submitted-applications {$and [{:permitType "R"} {:documents {$elemMatch {"schema-info.name" "hankkeen-kuvaus"}}}]})))}
  (update-applications-array
    :documents
    (fn [{schema-info :schema-info :as doc}]
      (if (= "hankkeen-kuvaus" (:name schema-info))
        (assoc-in doc [:schema-info :name] "hankkeen-kuvaus-rakennuslupa")
        doc))
    {$and [{:permitType "R"} {:documents {$elemMatch {"schema-info.name" "hankkeen-kuvaus"}}}]}))

(defmigration validate-verdict-given-date
  {:apply-when (pos? (mongo/count :organizations {:validate-verdict-given-date {$exists false}}))}
  (mongo/update-n :organizations {} {$set {:validate-verdict-given-date true}} :multi true))

(defmigration set-validate-verdict-given-date-in-helsinki
  (mongo/update-n :organizations {:_id "091-R"} {$set {:validate-verdict-given-date false}}))

(defmigration reset-pdf2pdf-page-counter-to-zero
  {:apply-when (pos? (mongo/count :statistics {$and [{:type "pdfa-conversion"} {"years.2015" {$exists true}}]}))}
  (mongo/update :statistics {:type "pdfa-conversion"} {$unset {"years.2015" ""}}))

(defmigration ya-katselmukset-remove-tila-and-convert-katselmuksenLaji
  {:apply-when (pos? (mongo/count :applications {:permitType "YA"
                                                 :tasks {$elemMatch {$and [{"schema-info.name" "task-katselmus-ya"}
                                                                           {$or [{"data.tila" {$exists true}}
                                                                                 {"data.katselmuksenLaji.value" {$exists true
                                                                                                                 $nin ["Aloituskatselmus"
                                                                                                                       "Loppukatselmus"
                                                                                                                       "Muu valvontak\u00e4ynti"
                                                                                                                       ""]}}]}]}}}))}
  (update-applications-array
    :tasks
    (fn [task]
      (if (= "task-katselmus-ya" (-> task :schema-info :name))
        (let [new-task (-> task
                         (dissoc-in [:data :tila])  ;; cannot be transferred in YA Krysp
                         (#(if-not (-> % :data :katselmuksenLaji :value ((fn [laji]
                                                                           (or
                                                                             (ss/blank? laji)
                                                                             (#{"Aloituskatselmus" "Loppukatselmus" "Muu valvontak\u00e4ynti"} laji)))))
                             (update-in % [:data :katselmuksenLaji :value] (fn [old-katselmuksenLaji]
                                                                             (case old-katselmuksenLaji
                                                                               "aloituskokous" "Aloituskatselmus"
                                                                               "loppukatselmus" "Loppukatselmus"
                                                                               "Muu valvontak\u00e4ynti")))
                             %)))]
          new-task)
        task))
    {:permitType "YA"
     :tasks {$elemMatch {$and [{"schema-info.name" "task-katselmus-ya"}
                               {$or [{"data.tila" {$exists true}}
                                     {"data.katselmuksenLaji.value" {$exists true
                                                                     $nin ["Aloituskatselmus"
                                                                           "Loppukatselmus"
                                                                           "Muu valvontak\u00e4ynti"
                                                                           ""]}}]}]}}}))


(defmigration ya-katselmukset-fix-remove-tila
  {:apply-when (pos? (mongo/count :applications {:permitType "YA"
                                                 :tasks {$elemMatch {$and [{"schema-info.name" "task-katselmus-ya"}
                                                                           {"data.katselmus.tila" {$exists true}}]}}}))}
  (update-applications-array :tasks
    (fn [task]
      (if (= "task-katselmus-ya" (-> task :schema-info :name))
        (dissoc-in task [:data :katselmus :tila])
        task))
    {:permitType "YA"
     :tasks {$elemMatch {$and [{"schema-info.name" "task-katselmus-ya"}
                               {"data.katselmus.tila" {$exists true}}]}}}))

(defn update-statement-state-by-status [{status :status state :state :as statement}]
  (if state
    statement
    (assoc statement :state (if status :given :requested))))

(defmigration statement-state
  {:apply-when (pos? (mongo/count :applications {:statements {$elemMatch {"state" {$exists false}}}}))}
  (update-applications-array :statements
                             update-statement-state-by-status
                             {:statements {$elemMatch {"state" {$exists false}}}}))


;; BSON type 8 == Boolean (https://docs.mongodb.org/manual/reference/operator/query/type/)
(defmigration convert-attachments-requestedByAuthority-to-boolean
  {:apply-when (pos? (mongo/count :applications {:attachments {$elemMatch {$and [{"requestedByAuthority" {$exists true}}
                                                                                 {"requestedByAuthority" {$not {$type 8}}}]}}}))}
  (update-applications-array :attachments
    (fn [attachment]
      (if-not (util/boolean? (:requestedByAuthority attachment))
        (update attachment :requestedByAuthority boolean)
        attachment))
    {:attachments {$elemMatch {$and [{"requestedByAuthority" {$exists true}}
                                     {"requestedByAuthority" {$not {$type 8}}}]}}}))


(def maisematyo-operations ["muu-tontti-tai-kort-muutos" "kortteli-yht-alue-muutos"
                            "muu-maisema-toimenpide" "paikoutysjarjestus-muutos"
                            "rak-valm-tyo" "puun-kaataminen" "kaivuu"
                            "tontin-jarjestelymuutos" "tontin-ajoliittyman-muutos"])

(defmigration maisematyo-ilman-hankeilmoitusta
  {:apply-when (pos? (mongo/count :applications {$and [{:primaryOperation.name {$in maisematyo-operations}} {:documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]}))}
  (update-applications-array
    :documents
    (fn [{schema-info :schema-info :as doc}]
      (if (= "rakennuspaikka" (:name schema-info))
        (-> doc
          (assoc-in [:schema-info :name] "rakennuspaikka-ilman-ilmoitusta")
          (update :data dissoc :hankkeestaIlmoitettu))
        doc))
    {$and [{:primaryOperation.name {$in maisematyo-operations}} {:documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]}))

(defmigration municipality-map-server-password-encrypted
  {:apply-when (pos? (mongo/count :organizations {:map-layers.server.crypto-iv {$exists false}
                                                  :map-layers.server.password {$nin [nil ""]}}))}
  (doseq [org (mongo/select :organizations {:map-layers.server.crypto-iv {$exists false}
                                            :map-layers.server.password {$nin [nil ""]}})
          :let [{:keys [server]} (:map-layers org)
                {:keys [url username password]} server]]
    (organization/update-organization-map-server (:id org) url username password)))

(defmigration poikkarit-ilman-hankeilmoitusta
  {:apply-when (pos? (mongo/count :applications {:permitType "P", :documents.data.hankkeestaIlmoitettu {$exists true}}))}
  (update-applications-array
    :documents
    (fn [{schema-info :schema-info :as doc}]
      (if (= "poikkeusasian-rakennuspaikka" (:name schema-info))
        (update doc :data dissoc :hankkeestaIlmoitettu)
        doc))
    {:permitType "P", :documents.data.hankkeestaIlmoitettu {$exists true}}))

(defmigration generate-attachment-auths
  (update-applications-array
    :attachments
    (fn [{versions :versions :as attachment}]
      (assoc attachment :auth (distinct (map attaccess/auth-from-version versions))))
    {:attachments.0 {$exists true}}))

(defn add-missing-person-data-to-statement [statement]
  (let [user-id (-> (get-in statement [:person :email] "")
                    user/get-user-by-email
                    :id)]
    (->> (:person statement)
         (merge {:userId (or user-id "") :email "" :name "" :text ""})
         (assoc statement :person))))

(defmigration statement-person-userId
  {:apply-when (pos? (mongo/count :applications {:statements {$elemMatch {:person.email {$exists true},
                                                                          :person.userId {$exists false}}}}))}
  (update-applications-array :statements
                             add-missing-person-data-to-statement
                             {:statements {$elemMatch {:person.email {$exists true},
                                                       :person.userId {$exists false}}}}))

(defn convert-coordinates [application]
  (let [location       (:location application)
        location-wgs84 (coord/convert "EPSG:3067" "WGS84" 5 location)]
    {$set {:location-wgs84 location-wgs84}}))

(defmigration add-wgs84-location-for-applications
  {:apply-when (pos? (mongo/count :applications {:location-wgs84 {$exists false}}))}
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (let [applications (mongo/select collection {:location-wgs84 {$exists false}})]
        (count (map #(mongo/update-by-id collection (:id %) (convert-coordinates %)) applications))))))


(defmigration cleanup-numeric-history-state
  {:apply-when (pos? (mongo/count :applications {"history.state" {$type 18}}))}
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (mongo/update-n :applications {"history.state" {$type 18}} {$pull {:history {:state {$type 18}}}} :multi true))))

(defmigration cleanup-construction-started-history-state
  {:apply-when (pos? (mongo/count :applications {"history.state" "constructionStarted"}))}
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (mongo/update-n :applications {"history.state" "constructionStarted"} {$pull {:history {:state "constructionStarted"}}} :multi true))))

(defn populate-application-history [{:keys [opened submitted sent canceled started complementNeeded closed startedBy closedBy history verdicts] :as application}]
  (let [user-summary (fn [user] (when (seq user) (user/summary user)))
        history-states (set (map :state history))
        verdict-state (sm/verdict-given-state application)
        verdict-ts  (:timestamp (->>  verdicts (remove :draft) (sort-by :timestamp) first))
        all-entries [(when (and opened (not (history-states "open"))) {:state :open, :ts opened, :user nil})
                     (when (and submitted(not (history-states "submitted"))) {:state :submitted, :ts submitted, :user nil})
                     (when (and sent (not (history-states "sent"))) {:state :sent, :ts sent, :user nil})
                     (when (and complementNeeded (not (history-states "complementNeeded"))) {:state :complementNeeded, :ts complementNeeded, :user nil})
                     (when (and verdict-ts (not (history-states verdict-state))) {:state verdict-state, :ts verdict-ts, :user nil})
                     (when (and started (not (history-states "constructionStarted"))) {:state :constructionStarted, :ts started, :user (user-summary startedBy)})
                     (when (and closed (not (history-states "closed"))) {:state :closed, :ts closed, :user (user-summary closedBy)})
                     (when (and canceled (not (history-states "canceled"))) {:state :canceled, :ts canceled, :user nil})]
        new-entries (remove nil? all-entries)]
    {$push {:history {$each new-entries}}}))

(defmigration populate-history-v2
  {:apply-when (pos? (mongo/count :applications {:history.1 {$exists false}, :state {$nin ["draft", "open", "canceled"]}, :infoRequest false}))}
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (let [applications (mongo/select collection {:state {$ne "draft"}, :infoRequest false} [:opened :sent :submitted :canceled :complementNeeded :started :closed :startedBy :closedBy :history :verdicts :permitType :primaryOperation :state])]
        (count (map #(mongo/update-by-id collection (:id %) (populate-application-history %)) applications))))))

(defn user-summary [{email :email id :id first-name :firstName last-name :lastName :as user}]
  (user/summary
   (if (and (= id "-") (= first-name "Lupapiste"))
     (assoc user :username "eraajo@lupapiste.fi")
     user)))

(defn remove-unwanted-fields-from-attachment-auth [attachment]
  (update attachment :auth (partial mapv user-summary)))

(defmigration cleanup-attachment-auth-user-summary-v2
  {:apply-when (pos? (+ (mongo/count :applications
                                     {$or [{:attachments {$elemMatch {:auth {$elemMatch {:email    {$exists true}}}}}}
                                           {:attachments {$elemMatch {:auth {$elemMatch {:id       {$exists true}
                                                                                         :username {$exists false}}}}}}]})
                        (mongo/count :submitted-applications
                                     {$or [{:attachments {$elemMatch {:auth {$elemMatch {:email    {$exists true}}}}}}
                                           {:attachments {$elemMatch {:auth {$elemMatch {:id       {$exists true}
                                                                                         :username {$exists false}}}}}}]})))}
  (update-applications-array :attachments
                             remove-unwanted-fields-from-attachment-auth
                             {$or [{:attachments {$elemMatch {:auth {$elemMatch {:email    {$exists true}}}}}}
                                   {:attachments {$elemMatch {:auth {$elemMatch {:id       {$exists true}
                                                                                 :username {$exists false}}}}}}]}))

(defn remove-unwanted-fields-from-attachment-versions-user [attachment]
  (update attachment :versions (partial mapv (fn [v] (update v :user user-summary)))))

(defn remove-unwanted-fields-from-attachment-latestVersion-user [attachment]
  (if (:latestVersion attachment)
    (update-in attachment [:latestVersion :user] user-summary)
    attachment))

(defn remove-unwanted-fields-from-attachment-versions-user-and-latestVersion-user [attachment]
  (-> attachment
      remove-unwanted-fields-from-attachment-versions-user
      remove-unwanted-fields-from-attachment-latestVersion-user))

(defmigration cleanup-attachment-versions-user-summary-v2
  {:apply-when (pos? (+ (mongo/count :applications
                                     {$or [{:attachments {$elemMatch {:versions {$elemMatch {:user.email    {$exists true}}}}}}
                                           {:attachments {$elemMatch {:versions {$elemMatch {:user.id       {$exists true}
                                                                                             :user.username {$exists false}}}}}}
                                           {:attachments {$elemMatch {:latestVersion.user.email    {$exists true}}}}
                                           {:attachments {$elemMatch {:latestVersion.user.id       {$exists true}
                                                                      :latestVersion.user.username {$exists false}}}}]})
                        (mongo/count :submitted-applications
                                     {$or [{:attachments {$elemMatch {:versions {$elemMatch {:user.email    {$exists true}}}}}}
                                           {:attachments {$elemMatch {:versions {$elemMatch {:user.id       {$exists true}
                                                                                             :user.username {$exists false}}}}}}
                                           {:attachments {$elemMatch {:latestVersion.user.email    {$exists true}}}}
                                           {:attachments {$elemMatch {:latestVersion.user.id       {$exists true}
                                                                      :latestVersion.user.username {$exists false}}}}]})))}
  (update-applications-array :attachments
                             remove-unwanted-fields-from-attachment-versions-user-and-latestVersion-user
                             {$or [{:attachments {$elemMatch {:versions {$elemMatch {:user.email    {$exists true}}}}}}
                                   {:attachments {$elemMatch {:versions {$elemMatch {:user.id       {$exists true}
                                                                                     :user.username {$exists false}}}}}}
                                   {:attachments {$elemMatch {:latestVersion.user.email    {$exists true}}}}
                                   {:attachments {$elemMatch {:latestVersion.user.id       {$exists true}
                                                              :latestVersion.user.username {$exists false}}}}]}))

(defn merge-required-fields-into-attachment [attachment]
  (merge {:locked               false
          :applicationState     "draft"
          :target               nil
          :requestedByAuthority false
          :notNeeded            false
          :op                   nil
          :signatures           []
          :auth                 []}
         attachment))

(defmigration add-required-fields-into-attachments-v2
  {:apply-when (pos? (mongo/count :applications {$or [{:attachments {$elemMatch {:locked               {$exists false}}}}
                                                      {:attachments {$elemMatch {:applicationState     {$exists false}}}}
                                                      {:attachments {$elemMatch {:target               {$exists false}}}}
                                                      {:attachments {$elemMatch {:requestedByAuthority {$exists false}}}}
                                                      {:attachments {$elemMatch {:notNeeded            {$exists false}}}}
                                                      {:attachments {$elemMatch {:op                   {$exists false}}}}
                                                      {:attachments {$elemMatch {:signatures           {$exists false}}}}
                                                      {:attachments {$elemMatch {:auth                 {$exists false}}}}]}))}
  (update-applications-array :attachments
                             merge-required-fields-into-attachment
                             {$or [{:attachments {$elemMatch {:locked               {$exists false}}}}
                                   {:attachments {$elemMatch {:applicationState     {$exists false}}}}
                                   {:attachments {$elemMatch {:target               {$exists false}}}}
                                   {:attachments {$elemMatch {:requestedByAuthority {$exists false}}}}
                                   {:attachments {$elemMatch {:notNeeded            {$exists false}}}}
                                   {:attachments {$elemMatch {:op                   {$exists false}}}}
                                   {:attachments {$elemMatch {:signatures           {$exists false}}}}
                                   {:attachments {$elemMatch {:auth                 {$exists false}}}}]}))

(defn applicationState-as-camelCase [attachment]
  (if (= (:applicationState attachment) "complement-needed")
    (assoc attachment :applicationState "complementNeeded")
    attachment))

(defmigration attachment-applicationState-as-camelCase
  {:apply-when (pos? (+ (mongo/count :applications {:attachments.applicationState "complement-needed"})
                        (mongo/count :submitted-applications {:attachments.applicationState "complement-needed"})))}
  (update-applications-array :attachments
                             applicationState-as-camelCase
                             {:attachments.applicationState "complement-needed"}))

(defn set-target-timestamps-as-nil [{target :target :as attachment}]
  (if (number? target)
    (assoc attachment :target nil)
    attachment))

(defmigration cleanup-attachment-target-with-timestamp-as-value
  {:apply-when (pos? (+ (mongo/count :applications {:attachments {$elemMatch {:target {$type 18}}}})
                        (mongo/count :submitted-applications {:attachments {$elemMatch {:target {$type 18}}}})))}
  (update-applications-array :attachments
                             set-target-timestamps-as-nil
                             {:attachments {$elemMatch {:target {$type 18}}}}))

(defn set-target-with-nil-valued-map-as-nil [{target :target :as attachment}]
  (if (or (every? nil? (vals target)) (every? (partial = "undefined") (vals target)))
    (assoc attachment :target nil)
    attachment))

(defmigration cleanup-attachment-target-nil-valued-maps
  (update-applications-array :attachments
                             set-target-with-nil-valued-map-as-nil
                             {$or [{:attachments {$elemMatch {:target.type {$type 10}}}}
                                   {:attachments {$elemMatch {:target.type "undefined"}}}]}))

(defn set-verdict-id-for-nil-valued-verdict-targets [verdict-id {{target-id :id target-type :type} :target :as attachment}]
  (if (and (nil? target-id) (= "verdict" target-type))
    (assoc-in attachment [:target :id] verdict-id)
    attachment))

(defn update-verdict-id-in-attachment-target [query]
  (reduce + 0
    (for [collection [:applications :submitted-applications]
          {attachments :attachments verdicts :verdicts app-id :id :as a} (mongo/select collection query {:verdicts 1 :attachments 1})]
      (mongo/update-n collection
                      {:_id app-id}
                      {$set {:attachments (map (partial set-verdict-id-for-nil-valued-verdict-targets (:id (first verdicts))) attachments)}}))))

(defmigration update-attachment-target-verdict-id-when-nil
  {:apply-when (pos? (+ (mongo/count :applications
                                     {:attachments {$elemMatch {:target.id   {$type 10}
                                                                :target.type "verdict"}}
                                      :verdicts {$size 1}})
                        (mongo/count :submitted-applications
                                     {:attachments {$elemMatch {:target.id   {$type 10}
                                                                :target.type "verdict"}}
                                      :verdicts {$size 1}})))}
  (update-verdict-id-in-attachment-target {:attachments {$elemMatch {:target.id   {$type 10}
                                                                :target.type "verdict"}}
                                           :verdicts {$size 1}}))

(defn remove-attachment-op-operation-type [attachment]
  (update attachment :op dissoc :operation-type))

(defmigration remove-operation-type-from-attachment-op
  {:apply-when (pos? (+ (mongo/count :applications
                                     {:attachments {$elemMatch {:op.operation-type {$exists true}}}})
                        (mongo/count :submitted-applications
                                     {:attachments {$elemMatch {:op.operation-type {$exists true}}}})))}
  (update-applications-array :attachments
                             remove-attachment-op-operation-type
                             {:attachments {$elemMatch {:op.operation-type {$exists true}}}}))

(defn remove-attachment-versions-accepted [attachment]
  (update attachment :versions (partial map #(dissoc % :accepted))))

(defn remove-attachment-latestVersion-accepted [{latest-version :latestVersion :as attachment}]
  (if latest-version
    (update attachment :latestVersion dissoc :accepted)
    attachment))

(defn remove-accepted-field-from-attachment-versions-and-latest-version [attachment]
  (-> attachment
      remove-attachment-versions-accepted
      remove-attachment-latestVersion-accepted))

(defmigration remove-accepted-from-attachment-versions
  {:apply-when (pos? (+ (mongo/count :applications
                                     {$or [{:attachments.versions.accepted {$exists true}}
                                           {:attachments.latestVersion.accepted {$exists true}}]})
                        (mongo/count :submitted-applications
                                     {$or [{:attachments.versions.accepted {$exists true}}
                                           {:attachments.latestVersion.accepted {$exists true}}]})))}
  (update-applications-array :attachments
                             remove-accepted-field-from-attachment-versions-and-latest-version
                             {$or [{:attachments.versions.accepted {$exists true}}
                                   {:attachments.latestVersion.accepted {$exists true}}]}))

(defn pull-auth-update [{:keys [id role]}]
  {$pull {:auth {:role role :id id}}})

(defn remove-owners-double-auth-updates [auths]
  (let [{owner-id :id :as owner-auth} (some
                                        #(when (= (:role %) "owner") %)
                                        auths)]
    (when-let [removable-auths (seq
                                 (filter
                                   #(and
                                     (= (:id %) owner-id)
                                     (or (= (:role %) "writer") (= (:role %) "reader")))
                                   auths))]
      (map pull-auth-update removable-auths))))

(defmigration double-auths-in-foreman-applications ; LPK-1331
  (reduce + 0
          (for [collection [:applications :submitted-applications]
                application (mongo/select collection
                                          {:primaryOperation.name "tyonjohtajan-nimeaminen-v2"
                                           :auth.2 {$exists true} ; >= 3 auths
                                           :created {$gt 1444780800000}} ; since version 1.108 14.10.2015
                                          [:auth])]
            (if-let [updates (remove-owners-double-auth-updates (:auth application))]
              (do
                (doseq [update-clause updates]
                  (mongo/update-by-id collection (:id application) update-clause))
                1) ; one application updated
              0))))

(defmigration duplicate-comments-cleanup
  (reduce + 0
          (for [collection [:applications :submitted-applications]
                application (mongo/select collection
                                          {:attachments.versions.archivable true ; for applications that have pdf/a checked attachments
                                           :created {$gt 1433462400000}}
                                          [:comments])]
            (let [comments (:comments application)
                  distinct-comments (distinct comments)]
              (if (< (count distinct-comments) (count comments))
                (mongo/update-n collection {:_id (:id application)} {$set {:comments distinct-comments}})
                0)))))

(defn set-signature-fileId [versions {version :version created :created :as signature}]
  (->> (filter #(and (= (:version %) version) (< (:created %) created)) versions)
       first
       :fileId
       (assoc signature :fileId)))

(defn add-fileId-for-signatures [{signatures :signatures versions :versions :as attachment}]
  (->> (map (partial set-signature-fileId versions) signatures)
       (filter :fileId)
       (assoc attachment :signatures)))

(defmigration add-fileId-for-attachment-signatures
  {:apply-when (pos? (+ (mongo/count :applications
                                     {:attachments.signatures {$elemMatch {:version {$exists true} :fileId {$exists false}}}})
                        (mongo/count :submitted-applications
                                     {:attachments.signatures {$elemMatch {:version {$exists true} :fileId {$exists false}}}})))}
  (update-applications-array :attachments
                             add-fileId-for-signatures
                             {:attachments.signatures {$elemMatch {:version {$exists true} :fileId {$exists false}}}}))

;; Cleanup QA attachments
(defmigration rename-not-needed
  {:apply-when (pos? (mongo/count :applications {:attachments.not-needed {$exists true}}))}
  (update-applications-array :attachments
    #(-> % (assoc :notNeeded (:not-needed %)) (dissoc :not-needed))
    {:attachments.not-needed {$exists true}}))

;; Cleanup QA attachments
(defmigration rename-requested-by-authority
  {:apply-when (pos? (mongo/count :applications {:attachments.requested-by-authority {$exists true}}))}
  (update-applications-array :attachments
    #(-> % (assoc :requestedByAuthority (:requested-by-authority %)) (dissoc :requested-by-authority))
    {:attachments.requested-by-authority {$exists true}}))

(defn guest-authority? [roles]
  (contains? (set roles) "guestAuthority"))

(defn remove-guest-authorities [org-auths]
  (reduce (fn [acc [k v]]
            (assoc acc k (remove #(= "guestAuthority" %) v))) {} org-auths))

(defmigration no-more-guest-authority-org-authz
  (let [users (->> (mongo/select :users {:orgAuthz {$exists true}})
                   (filter (fn [u] (some guest-authority? (vals (:orgAuthz u))))))]
    (doseq [{id :id auths :orgAuthz} users]
      (mongo/update-by-id :users id {$set {:orgAuthz (remove-guest-authorities auths)}}))))

(defn rename-kaupunkikuvatoimenpide-documents-with-op [operations-to-rename doc]
  (if (and (= "kaupunkikuvatoimenpide" (get-in doc [:schema-info :name]))
           (set operations-to-rename)  (get-in doc [:schema-info :op :name]))
    (update doc :schema-info assoc :name "kaupunkikuvatoimenpide-ei-tunnusta" :i18name "kaupunkikuvatoimenpide")
    doc))

(defmigration schemas-without-building-identifier []
  {:apply-when (pos? (+ (mongo/count :applications
                                     {:documents {$elemMatch {:schema-info.name "kaupunkikuvatoimenpide",
                                                              :schema-info.op.name {$in ["aita"]}}}})
                        (mongo/count :submitted-applications
                                     {:documents {$elemMatch {:schema-info.name "kaupunkikuvatoimenpide",
                                                              :schema-info.op.name {$in ["aita"]}}}})))}
  (update-applications-array :documents
                             (partial rename-kaupunkikuvatoimenpide-documents-with-op #{"aita"})
                             {:documents {$elemMatch {:schema-info.name "kaupunkikuvatoimenpide",
                                                      :schema-info.op.name {$in ["aita"]}}}}))

(defn add-original-file-id-for-version [{:keys [fileId originalFileId] :as version}]
  (assoc version :originalFileId (or originalFileId fileId)))

(defn add-original-file-id-for-versions-and-latestVersion [{latest-version :latestVersion :as attachment}]
  (if latest-version
    (-> attachment
        (update :versions (partial map add-original-file-id-for-version))
        (update :latestVersion add-original-file-id-for-version))
    attachment))

(defmigration add-original-file-id-for-attachment-versions
  {:apply-when (pos? (mongo/count :applications
                                  {:attachments.versions {$elemMatch {:fileId {$exists true}
                                                                      :originalFileId {$exists false}}}}))}
  (update-applications-array :attachments
                             add-original-file-id-for-versions-and-latestVersion
                             {:attachments.versions {$elemMatch {:fileId {$exists true}
                                                                 :originalFileId {$exists false}}}}))

#_(defmigration rename-hankkeen-kuvaus-rakennuslupa-back-to-hankkeen-kuvaus ;; TODO: migrate, LPK-1448
  {:apply-when (or (pos? (mongo/count :applications {:documents {"schema-info.name" "hankkeen-kuvaus-rakennuslupa"}}))
                   (pos? (mongo/count :submitted-applications {:documents {"schema-info.name" "hankkeen-kuvaus-rakennuslupa"}})))}
  (update-applications-array
    :documents
    (fn [{{name :name} :schema-info :as doc}]
      (if (= "hankkeen-kuvaus-rakennuslupa" (:name schema-info))
        (-> (update doc :data dissoc :hankkeenVaativuus)
            (assoc-in [:schema-info :name] "hankkeen-kuvaus"))
        doc))
    {:documents {"schema-info.name" "hankkeen-kuvaus-rakennuslupa"}}))

(defmigration init-designer-subtype
              (update-applications-array
                :documents
                (fn [doc]
                  (if (re-matches #".*suunnittelija" (str (get-in doc [:schema-info :name])))
                    (assoc-in doc [:schema-info :subtype] "suunnittelija")
                    doc))
                {"documents.schema-info.name" {$regex #".*suunnittelija"}}))

(defmigration init-designer-index
              (reduce + 0
                      (for [collection [:applications :submitted-applications]]
                        (let [applications (mongo/select collection {:documents.schema-info.subtype "suunnittelija"} {:documents 1})]
                          (count (map #(mongo/update-by-id collection (:id %) (app-meta-fields/designers-index-update %)) applications))))))

(defn update-attachment-type [mapping type-key attachment]
  (let [type (->> (type-key attachment) (map (fn [kv-pair] (mapv keyword kv-pair))) (into {}))]
    (assoc attachment type-key (get mapping type type))))

(defmigration application-attachment-type-update
  (update-applications-array :attachments
                             (partial update-attachment-type attachment-type-mapping/attachment-mapping :type)
                             {:permitType  {$in ["R" "P"]}
                              :attachments {$gt {$size 0}}}))

(defmigration application-ya-osapuoli-attachment-type-update
  (update-applications-array :attachments
                             (partial update-attachment-type attachment-type-mapping/osapuoli-attachment-mapping :type)
                             {:permitType  {$in ["YA"]}
                              :attachments {$gt {$size 0}}}))

(defmigration update-user-attachments
  (reduce (fn [cnt user] (+ cnt (mongo/update-n :users {:_id (:id user)}
                                                {$set {:attachments (->> (:attachments user)
                                                                         (map (partial update-attachment-type attachment-type-mapping/osapuoli-attachment-mapping :attachment-type)))}})))
          0
          (mongo/select :users {:attachments {$gt {$size 0}}} {:attachments 1})))

(def r-or-p-operation? (->> (filter (comp #{"R" "P"} :permit-type val) op/operations) keys set))
(def ya-operation? (->> op/ya-operations keys set))

(defn update-operations-attachment-type [mapping [type-group type-id]]
  (let [{new-group :type-group new-id :type-id} (-> {:type-group (keyword type-group) :type-id (keyword type-id)}
                                                    attachment-type-mapping/attachment-mapping)]
    (if (and new-group new-id)
      [new-group  new-id]
      [type-group type-id])))

(defn update-operations-attachments-types [mapping operation-pred [operation attachment-types]]
  (if (operation-pred (keyword operation))
    [operation (->> attachment-types (map (partial update-operations-attachment-type mapping)) distinct)]
    [operation attachment-types]))

(defmigration organization-operation-attachments-type-update
  (reduce (fn [cnt org] (+ cnt (mongo/update-n :organizations {:_id (:id org)}
                                               {$set {:operations-attachments (->> (:operations-attachments org)
                                                                                   (map (partial update-operations-attachments-types attachment-type-mapping/attachment-mapping r-or-p-operation?))
                                                                                   (map (partial update-operations-attachments-types attachment-type-mapping/osapuoli-attachment-mapping ya-operation?))
                                                                                   (into {}))}})))
          0
          (mongo/select :organizations {:operations-attachments {$gt {$size 0}}} {:operations-attachments 1})))

(defmigration add-id-for-verdicts-paatokset-v2
  (update-applications-array :verdicts
                             (fn [verdict] (update verdict :paatokset (fn [paatokset] (map #(if (:id %) % (assoc % :id (mongo/create-id))) paatokset))))
                             {:verdicts.paatokset {$elemMatch {:id {$exists false}, :poytakirjat {$exists true}}}}))

(defmigration clean-vaadittuTyonjohtajatieto-in-verdicts
  (update-applications-array :verdicts
                             (fn [verdict]
                               (update verdict :paatokset
                                          (fn [paatokset] (map (fn [paatos]
                                                                 (update-in paatos [:lupamaaraykset :vaadittuTyonjohtajatieto]
                                                                            (fn [tj] (cond (map? tj)    [(get-in tj [:VaadittuTyonjohtaja :tyonjohtajaLaji])]
                                                                                           (vector? tj) (map #(or (get-in % [:VaadittuTyonjohtaja :tyonjohtajaLaji]) %) tj)
                                                                                           :else        tj))))
                                                               paatokset))))
                             {:verdicts.paatokset.lupamaaraykset.vaadittuTyonjohtajatieto {$type 3}}))

(defmigration application-attachment-type-update-v2
  (update-applications-array :attachments
                             (partial update-attachment-type attachment-type-mapping/attachment-mapping :type)
                             {:permitType  {$in ["R" "P"]}
                              :attachments.type.type-id {$in [:selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta
                                                              :aitapiirustus
                                                              :vesi_ja_viemariliitoslausunto_tai_kartta
                                                              :sopimusjaljennos
                                                              :karttaaineisto
                                                              :selvitys_rakennuspaikan_korkeusasemasta
                                                              :riskianalyysi]}}))

(defmigration organization-operation-attachments-type-update-v2
  (reduce (fn [cnt org] (+ cnt (mongo/update-n :organizations {:_id (:id org)}
                                               {$set {:operations-attachments (->> (:operations-attachments org)
                                                                                   (map (partial update-operations-attachments-types attachment-type-mapping/attachment-mapping r-or-p-operation?))
                                                                                   (into {}))}})))
          0
          (mongo/select :organizations {:operations-attachments {$gt {$size 0}}} {:operations-attachments 1})))

(defn update-katselmus-buildings
  "Assocs missing buildings from buildings array to repeating :rakennus data for katselmus tasks."
  [buildings {{rakennus :rakennus} :data :as katselmus-doc}]
  (let [saved-building-indices (map (util/fn-> :rakennus :jarjestysnumero :value) (vals rakennus))
        unselected-buildings   (remove (fn [{idx :index}] (some #(= idx %) saved-building-indices)) buildings)
        building-keys          [:index :nationalId :localShortId :propertyId :localId]
        rakennus-keys          [:jarjestysnumero :valtakunnallinenNumero :rakennusnro :kiinttun :kunnanSisainenPysyvaRakennusnumero]
        new-rakennus-map       (reduce
                                 (fn [acc building]
                                   (let [next-idx-kw (->> (map (comp #(Integer/parseInt %) name) (keys acc))
                                                          (apply max -1) ; if no keys, starting index (inc -1) => 0
                                                          inc
                                                          str
                                                          keyword)
                                         rakennus-data (-> (rename-keys building (zipmap building-keys rakennus-keys))
                                                           (select-keys rakennus-keys))]
                                     (assoc acc next-idx-kw {:rakennus (tools/wrapped rakennus-data)})))
                                 rakennus
                                 unselected-buildings)]
    (when (seq unselected-buildings)
      (let [updated-katselmus (assoc-in katselmus-doc [:data :rakennus] new-rakennus-map)]
        (if (model/has-errors? (task-doc-validation "task-katselmus" updated-katselmus))
          (errorf "Migration: task-katselmus validation error for doc-id %s" (:id katselmus-doc))
          updated-katselmus)))))

(defmigration buildings-to-katselmus-tasks
  (reduce + 0
          (for [collection [:applications :submitted-applications]
                {:keys [id tasks buildings]} (mongo/select collection {:tasks {$elemMatch {:schema-info.name "task-katselmus" :state {$ne "sent"}}}} [:tasks :buildings])]
            (let [katselmus-tasks (filter #(and
                                            (= (get-in % [:schema-info :name]) "task-katselmus")
                                            (not= "sent" (:state %)))
                                          tasks)
                  updated-tasks   (->> (map (partial update-katselmus-buildings buildings) katselmus-tasks)
                                       (remove nil?))
                  task-updates    (when (seq updated-tasks)
                                    (apply merge
                                           (map #(mongo/generate-array-updates :tasks
                                                                               tasks
                                                                               (fn [task] (= (:id task) (:id %)))
                                                                               "data.rakennus"
                                                                               (get-in % [:data :rakennus]))
                                                updated-tasks)))]
              (if (map? task-updates)
                (mongo/update-n collection {:_id id} {$set task-updates})
                0)))))

(defn remove-empty-buildings [{{rakennus :rakennus} :data :as katselmus-doc}]
  (let [rakennus-keys (keys rakennus)
        new-rakennus-map (reduce
                           (fn [acc index]
                             (let [{rakennus-data :rakennus} (index acc)
                                   unwrapped-data (tools/unwrapped rakennus-data)
                                   all-empty? (every? util/empty-or-nil? (vals unwrapped-data))]
                               (if all-empty?
                                 (dissoc acc index)
                                 acc)))
                           rakennus
                           rakennus-keys)]
    (when-not (= (count rakennus-keys) (count (keys new-rakennus-map)))
      ; keys have been dissociated, return katselmus document with updated rakennus data
      (assoc-in katselmus-doc [:data :rakennus] new-rakennus-map))))


(defmigration cleanup-empty-buildings-from-tasks
  (reduce + 0
          (for [collection [:applications :submitted-applications]
                {:keys [id tasks]} (mongo/select collection {:tasks {$elemMatch {:schema-info.name "task-katselmus" :state {$ne "sent"}}}} [:tasks])]
            (let [katselmus-tasks (filter #(and
                                            (= (get-in % [:schema-info :name]) "task-katselmus")
                                            (not= "sent" (:state %)))
                                          tasks)
                  updated-tasks   (->> (map remove-empty-buildings katselmus-tasks)
                                       (remove nil?))
                  task-updates    (when (seq updated-tasks)
                                    (apply merge
                                           (map #(mongo/generate-array-updates :tasks
                                                                               tasks
                                                                               (fn [task] (= (:id task) (:id %)))
                                                                               "data.rakennus"
                                                                               (get-in % [:data :rakennus]))
                                                updated-tasks)))]
              (if (map? task-updates)
                (mongo/update-n collection {:_id id} {$set task-updates})
                0)))))

(defmigration image-jpeg
  {:apply-when (pos? (mongo/count :fs.files {:contentType "image/jpg"}))}
  (mongo/update-n :fs.files {:contentType "image/jpg"} {$set {:contentType "image/jpeg"}} :multi true))

(defmigration fix-content-types
  (let [content-types (mongo/distinct :fs.files "contentType")
        all-unallowed-types (set (remove #(re-matches mime/mime-type-pattern %) content-types))
        localized-unallowed-types #{"application/vnd.ms-outlook" "application/msworks"}
        unallowed-types (set/difference all-unallowed-types localized-unallowed-types)]

    (reduce + 0
      (for [f (mongo/select :fs.files {:contentType {$in unallowed-types}})
           :let [{:keys [id filename]} f
                 content-type (mime/mime-type filename)]]
        (mongo/update-n :fs.files {:_id id} {$set {:contentType content-type}})))))

(defn remove-FI-prefix-from-auth-y [k auth]
  (if-let [company-y (get auth k)]
    (assoc auth k (ss/replace company-y "FI" ""))
    auth))

(defmigration sanitize-auth-company-y
  {:apply-when (pos? (mongo/count :applications {:auth.y #"FI"}))}
  (update-applications-array :auth (partial remove-FI-prefix-from-auth-y :y) {:auth.y #"FI"}))

(defmigration sanitize-auth-company-username
  {:apply-when (pos? (mongo/count :applications {:auth.username #"FI"}))}
  (update-applications-array :auth (partial remove-FI-prefix-from-auth-y :username) {:auth.username #"FI"}))

(defn change-empty-role-as-reader [{role :role :as auth}]
  (if (= role "")
    (assoc auth :role "reader")
    auth))

(defmigration sanitize-auth-role
  {:apply-when (pos? (mongo/count :applications {:auth.role ""}))}
  (update-applications-array :auth change-empty-role-as-reader {:auth.role ""}))

(defn remove-auth-inviter-nils [{inviter :inviter :as auth}]
  (if (nil? inviter)
    (dissoc auth :inviter)
    auth))

(defmigration sanitize-auth-inviter
  {:apply-when (pos? (mongo/count :applications {:auth.inviter {$type 10}}))}
  (update-applications-array :auth remove-auth-inviter-nils {:auth.inviter {$type 10}}))

(defn remove-auth-invite-documentId-null-strings [{{document-id :documentId} :invite :as auth}]
  (if (= document-id "null")
    (assoc-in auth [:invite :documentId] nil)
    auth))

(defmigration sanitize-auth-invite-documentId
  {:apply-when (pos? (mongo/count :applications {:auth.invite.documentId "null"}))}
  (update-applications-array :auth remove-auth-invite-documentId-null-strings {:auth.invite.documentId "null"}))

(defn remove-auth-invite-nil-fields [{{:keys [path text documentId documentName] :as invite} :invite :as auth}]
  (if (:email invite)
    (cond-> auth
      (nil? path)              (update :invite #(dissoc % :path))
      (nil? text)              (update :invite #(dissoc % :text))
      (ss/blank? documentId)   (update :invite #(dissoc % :documentId))
      (ss/blank? documentName) (update :invite #(dissoc % :documentName)))
    auth))

(defmigration sanitize-auth-invite-optional-fields
  {:apply-when (pos? (mongo/count :applications {$or [{:auth.invite.path {$type 10}}
                                                      {:auth.invite.text {$type 10}}
                                                      {:auth.invite.documentId {$type 10}}
                                                      {:auth.invite.documentId ""}
                                                      {:auth.invite.documentName {$type 10}}
                                                      {:auth.invite.documentName ""}]}))}
  (update-applications-array :auth
                             remove-auth-invite-nil-fields
                             {$or [{:auth.invite.path {$type 10}}
                                                      {:auth.invite.text {$type 10}}
                                                      {:auth.invite.documentId {$type 10}}
                                                      {:auth.invite.documentId ""}
                                                      {:auth.invite.documentName {$type 10}}
                                                      {:auth.invite.documentName ""}]}))

(def statement-status->new
  {"puoltaa"           "puollettu"
   "ei-puolla"         "ei-puollettu"
   "ehdoilla"          "ehdollinen"
   "jatetty-poydalle"  "poydalle"

   "puollettu"         "puollettu"
   "ei-puollettu"      "ei-puollettu"
   "ehdollinen"        "ehdollinen"
   "poydalle"          "poydalle"
   "ei-huomautettavaa" "ei-huomautettavaa"
   "ei-lausuntoa"      "ei-lausuntoa"
   "lausunto"          "lausunto"
   "kielteinen"        "kielteinen"
   "palautettu"        "palautettu"})

(defn- update-statement-status [{status :status :as statement}]
  (if status
    (update statement :status statement-status->new)
    statement))

(defmigration map-statement-statuses-into-new-krysp
  {:apply-when (pos? (mongo/count :applications {:statements.status {$in ["puoltaa" "ei-puolla" "ehdoilla" "jatetty-poydalle"]}}))}
  (update-applications-array :statements
                             update-statement-status
                             {:statements.status {$in ["puoltaa" "ei-puolla" "ehdoilla" "jatetty-poydalle"]}}))

(defn- remove-nils-from-statement-fields [{given :given status :status reminder-sent :reminder-sent metadata :metadata :as statement}]
  (cond-> statement
    (nil? given) (dissoc :given)
    (nil? status) (dissoc :status)
    (nil? reminder-sent) (dissoc :reminder-sent)
    (nil? metadata) (dissoc :metadata)))

(defmigration sanitize-nils-in-statement-fields
  {:apply-when (pos? (mongo/count :applications {$or [{:statements.given {$type 10}}
                                                      {:statements.status {$type 10}}
                                                      {:statements.reminder-sent {$type 10}}
                                                      {:statements.metadata {$type 10}}]}))}
  (update-applications-array :statements
                             remove-nils-from-statement-fields
                             {$or [{:statements.given {$type 10}}
                                   {:statements.status {$type 10}}
                                   {:statements.reminder-sent {$type 10}}
                                   {:statements.metadata {$type 10}}]}))

(defmigration hakija-tj-v2
  {:apply-when (pos? (mongo/count :applications {:primaryOperation.name "tyonjohtajan-nimeaminen-v2"
                                                 :documents.schema-info.name "hakija-r"}))}
  (letfn [(pred [doc] (= "hakija-r" (get-in doc [:schema-info :name])))]
    (reduce + 0
      (for [{:keys [id documents]} (mongo/select :applications
                                     {:primaryOperation.name "tyonjohtajan-nimeaminen-v2"
                                      :documents.schema-info.name "hakija-r"}
                                     [:documents])]
        (let [updates {$set (mongo/generate-array-updates :documents documents pred "schema-info.name" "hakija-tj")}]
          (mongo/update-n :applications {:_id id} updates))))))

(defn- flatten-kesto [kesto]
  (apply merge
         (select-keys kesto [:alku :loppu])
         (->> (dissoc kesto :alku :loppu) vals)))

(defn- change-kesto-doc-as-repeating [doc]
  (if (and (= (get-in doc [:schema-info :name]) "ymp-ilm-kesto")
           (get-in doc [:data :kesto :arki]))
    (update-in doc [:data :kesto] (fn->> flatten-kesto (hash-map :0)))
    doc))

(defmigration change-meluilmoitus-kesto-as-repeating
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {:data.kesto.arki {$exists true}
                                                                         :schema-info.name "ymp-ilm-kesto"}}}))}
  (update-applications-array :documents
                             change-kesto-doc-as-repeating
                             {:documents {$elemMatch {:data.kesto.arki {$exists true}
                                                      :schema-info.name "ymp-ilm-kesto"}}}))

(defmigration add-archived-timestamps
  {:apply-when (pos? (mongo/count :applications {:archived {$exists false}}))}
  (mongo/update-by-query :applications {:archived {$exists false}} {$set {:archived {:application nil
                                                                                     :completed nil}}}))

(defmigration add-archiving-start-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:permanent-archive-in-use-since {$exists false}}))}
  (mongo/update-by-query :organizations {:permanent-archive-in-use-since {$exists false}} {$set {:permanent-archive-in-use-since 0}}))

(defn add-ym-in-scope [org]
  (let [ym-municipalities (->> (filter (comp #{"YM"} :permitType) (:scope org))
                               (map :municipality)
                               (set))]
    (->> (map :municipality (:scope org))
         (distinct)
         (remove ym-municipalities)
         (reduce #(update %1 :scope conj {:open-inforequest-email nil,
                                          :open-inforequest false,
                                          :new-application-enabled true,
                                          :inforequest-enabled true,
                                          :municipality %2,
                                          :permitType "YM"}) org))))

(defmigration add-ym-permit-type-to-all-ymp-orgs
  {:apply-when (pos? (mongo/count :organizations {:_id #"YMP" :scope.0 {$exists true} :scope.permitType {$not {"$eq" "YM"}}}))}
  (->> (mongo/select :organizations {:_id #"YMP" :scope.permitType {$not {"$eq" "YM"}}})
       (map add-ym-in-scope)
       (run! #(mongo/update-by-id :organizations (:id %) {$set {:scope (:scope %)}}))))

(defmigration add-missing-submit-rights-to-company-users-v2
  {:apply-when (pos? (mongo/count :users {:company.id {$exists true}
                                          :company.submit nil}))}
  (mongo/update-by-query :users
                         {:company.id {$exists true}
                          :company.submit nil}
                         {$set {:company.submit true}}))

(defmigration add-missing-submit-rights-to-company-tokens-v2
  {:apply-when (pos? (mongo/count :token {:token-type {$in [:new-company-user :invite-company-user]}
                                          :data.submit {$exists false}}))}
  (mongo/update-by-query :token
                         {:token-type {$in [:new-company-user :invite-company-user]}
                          :data.submit {$exists false}}
                         {$set {:data.submit true}}))


;; update also bulletins as per change-meluilmoitus-kesto-as-repeating
(defmigration change-meluilmoitus-bulletins-kesto-as-repeating
  {:apply-when (pos? (mongo/count :application-bulletins {:versions.documents {$elemMatch {:data.kesto.arki {$exists true}
                                                                         :schema-info.name "ymp-ilm-kesto"}}}))}
  (update-bulletin-versions :documents
                            change-kesto-doc-as-repeating
                            {:versions.documents {$elemMatch {:data.kesto.arki {$exists true}
                                                     :schema-info.name "ymp-ilm-kesto"}}}))

(defmigration add-calendars-property-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:calendars-enabled {$exists false}}))}
  (doseq [organization (mongo/select :organizations {:calendars-enabled {$exists false}})]
    (mongo/update-by-id :organizations (:id organization)
                        {$set {:calendars-enabled false}})))

(defmigration remove-invites-application-id
  {:apply-when (pos? (mongo/count :applications {:auth.invite.application {$exists true}}))}
  (update-applications-array
    :auth
    (fn [a] (util/dissoc-in a [:invite :application]))
    {:auth.invite.application {$exists true}}))

(defn set-missing-metadata-laskentaperuste [{:keys [metadata] :as attachment}]
  (if (and (= "toistaiseksi" (get-in metadata [:sailytysaika :arkistointi])) (nil? (get-in metadata [:sailytysaika :laskentaperuste])))
    (assoc-in attachment [:metadata :sailytysaika :laskentaperuste] "rakennuksen_purkamisp\u00e4iv\u00e4")
    attachment))

(defmigration add-missing-laskentaperuste-to-metadata-maps
  {:apply-when (pos? (mongo/count :applications {$or [{:metadata.sailytysaika.arkistointi "toistaiseksi"
                                                       :metadata.sailytysaika.laskentaperuste {$exists false}}
                                                      {:processMetadata.sailytysaika.arkistointi "toistaiseksi"
                                                       :processMetadata.sailytysaika.laskentaperuste {$exists false}}
                                                      {:attachments {$elemMatch {:metadata.sailytysaika.arkistointi "toistaiseksi"
                                                                                 :metadata.sailytysaika.laskentaperuste {$exists false}}}}]}))}
  (doseq [collection [:applications :submitted-applications]]
    (mongo/update-by-query collection
                           {:metadata.sailytysaika.arkistointi "toistaiseksi"
                            :metadata.sailytysaika.laskentaperuste {$exists false}}
                           {$set {:metadata.sailytysaika.laskentaperuste "rakennuksen_purkamisp\u00e4iv\u00e4"}})
    (mongo/update-by-query collection
                           {:processMetadata.sailytysaika.arkistointi "toistaiseksi"
                            :processMetadata.sailytysaika.laskentaperuste {$exists false}}
                           {$set {:processMetadata.sailytysaika.laskentaperuste "rakennuksen_purkamisp\u00e4iv\u00e4"}}))
  (update-applications-array :attachments set-missing-metadata-laskentaperuste {:attachments {$elemMatch {:metadata.sailytysaika.arkistointi "toistaiseksi"
                                                                                                          :metadata.sailytysaika.laskentaperuste {$exists false}}}}))

(defmigration paasuunnittelija-is-removable
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {"schema-info.name" "paasuunnittelija", "schema-info.removable" false}}}))}
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (mongo/update-by-query collection
        {:documents {$elemMatch {:schema-info.name "paasuunnittelija", :schema-info.removable false}}}
        {$set {:documents.$.schema-info.removable true}}))))

(defn operation-cleanup-updates-for-application [{attachments :attachments}]
  (->>  ["description" "optional" "created" "attachment-op-selector"]
        (map #(mongo/generate-array-updates :attachments attachments (constantly true) (str "op." %) ""))
        (apply merge)
        (hash-map $unset)))

(defmigration attachment-operation-cleanup
  {:apply-when (or (pos? (mongo/count :applications {$or [{:attachments.op.description {$exists true}}
                                                          {:attachments.op.optional {$exists true}}
                                                          {:attachments.op.created {$exists true}}
                                                          {:attachments.op.attachment-op-selector {$exists true}}]}))
                   (pos? (mongo/count :submitted-applications {$or [{:attachments.op.description {$exists true}}
                                                                    {:attachments.op.optional {$exists true}}
                                                                    {:attachments.op.created {$exists true}}
                                                                    {:attachments.op.attachment-op-selector {$exists true}}]})))}
  (doseq [collection  [:applications :submitted-applications]
          application (mongo/select collection
                                    {$or [{:attachments.op.description {$exists true}}
                                          {:attachments.op.optional {$exists true}}
                                          {:attachments.op.created {$exists true}}
                                          {:attachments.op.attachment-op-selector {$exists true}}]}
                                    {:attachments true})]
         (mongo/update-by-id collection (:id application) (operation-cleanup-updates-for-application application))))

(defmigration unset-organization-municipalities-legacy-key
  {:apply-when (pos? (mongo/count :organizations {:municipalities {$exists true}}))}
  (mongo/update-by-query :organizations {:municipalities {$exists true}} {$unset {:municipalities 1}}))

(defmigration attachment-operation-cleanup-v2
  {:apply-when (or (pos? (mongo/count :applications {$or [{:attachments.op.description {$exists true}}
                                                          {:attachments.op.optional {$exists true}}
                                                          {:attachments.op.created {$exists true}}
                                                          {:attachments.op.attachment-op-selector {$exists true}}]}))
                   (pos? (mongo/count :submitted-applications {$or [{:attachments.op.description {$exists true}}
                                                                    {:attachments.op.optional {$exists true}}
                                                                    {:attachments.op.created {$exists true}}
                                                                    {:attachments.op.attachment-op-selector {$exists true}}]})))}
  (doseq [collection  [:applications :submitted-applications]
          application (mongo/select collection
                                    {$or [{:attachments.op.description {$exists true}}
                                          {:attachments.op.optional {$exists true}}
                                          {:attachments.op.created {$exists true}}
                                          {:attachments.op.attachment-op-selector {$exists true}}]}
                                    {:attachments true})]
    (mongo/update-by-id collection (:id application) (operation-cleanup-updates-for-application application))))

;; migraatio, joka ajettiin vain kerran perumaan yhden batchrunin tulokset, koska
;; generoidut pdf:t eivat olleet valideja.
#_(defmigration verdict-polling-pdf-failure-removal
  {:apply-when (pos? (mongo/count :applications {:tasks.source.type "background"}))}
  (println "PDF korjausmigraatio: background-sourcellisia hakemuksia on "
    (mongo/count :applications {:tasks.source.type "background"}))
  (doseq [failed (mongo/select :applications {:tasks.source.type "background"})]
    (doseq [task (:tasks failed)]
      (if (= "background" (:type (:source task)))
        (do
          (println " - failannut taski" (:id task))
          (doseq [att (:attachments failed)]
            (if (= (:id task) (:id (:source att)))
              (try
                (println "   + poistetaan taskiin " (:id task) " linkattu liite " (:id att) " hakemukselta " (:_id failed))
                (attachment/delete-attachment! failed (:id att))
                (catch Exception e
                  (println "   + Virhe poistettaessa liitetta")))))
          (println " - poistetaan taski " (:id task) " hakemukselta " (:id failed))
          (action/update-application
            (action/application->command failed)
            {$pull {:tasks {:id (:id task)}}})
          (println " - taski poistettu"))))))

(defn operation-id-cleanup-updates
  "For attachments that have op.id set to nil, nillify whole op ({\"attachments.$.op\" nil})"
  [{attachments :attachments}]
  (mongo/generate-array-updates :attachments attachments #(and (:op %) (nil? (get-in % [:op :id]))) "op" nil))

(defmigration attachment-op-nil-cleanup
  {:apply-when (or (pos? (mongo/count :applications {:attachments.op.id {$type 10}}))
                   (pos? (mongo/count :submitted-applications {:attachments.op.id {$type 10}})))}
  (doseq [collection  [:applications :submitted-applications]
          application (mongo/select collection {:attachments.op.id {$type 10}} [:attachments])]
    (mongo/update-by-id collection (:id application) {$set (operation-id-cleanup-updates application)})))

(defmigration add-use-attachment-links-integration-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:use-attachment-links-integration {$exists false}}))}
  (mongo/update-by-query :organizations {:use-attachment-links-integration {$exists false}} {$set {:use-attachment-links-integration false}}))

(defmigration remove-old-foreman-operation-from-organization-selected-operations
  {:apply-when (pos? (mongo/count :organizations {:selected-operations "tyonjohtajan-nimeaminen"}))}
  (mongo/update :organizations {} {$pull {:selected-operations "tyonjohtajan-nimeaminen"}} :multi true))

; schema change results: changed document title and no more hankkeestaIlmoitettu element inside document
(defn- change-rakennuspaikka-to-toiminnan-sijainti [doc]
  (if (= "rakennuspaikka" (get-in doc [:schema-info :name]))
    (-> doc
      (assoc-in [:schema-info :name] "toiminnan-sijainti")
      (update :data dissoc :hankkeestaIlmoitettu))
  doc))

(defmigration rakennuspaikka-to-toiminnan-sijainti
  {:apply-when (or (pos? (mongo/count :applications {$and [{:permitType "YL"} {:documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]}))
                   (pos? (mongo/count :submitted-applications {$and [{:permitType "YL"} {:documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]})))}
  (update-applications-array :documents
                             change-rakennuspaikka-to-toiminnan-sijainti
                             {$and [{:permitType "YL"} {:documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]}))

(defmigration rakennuspaikka-to-toiminnan-sijainti-bulletins
  {:apply-when (pos? (mongo/count :application-bulletins {$and [{:versions.permitType "YL"} {:versions.documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]}))}
  (update-bulletin-versions :documents
                            change-rakennuspaikka-to-toiminnan-sijainti
                            {$and [{:versions.permitType "YL"} {:versions.documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]}))

(defmigration remove-suunnittelija-from-puun-kaataminen-applications
  {:apply-when (pos? (mongo/count :applications {$and [{:primaryOperation.name "puun-kaataminen"}
                                                       {:state {$in ["draft" "open"]}}
                                                       {:documents.schema-info.name "suunnittelija"}]}))}
  (mongo/update-n :applications
                  {$and [{:primaryOperation.name "puun-kaataminen"}
                         {:state {$in ["draft" "open"]}}
                         {:documents.schema-info.name "suunnittelija"}]}
                  {$pull {:documents {:schema-info.name "suunnittelija"}}}
                  :multi true))

(when (env/feature? :english)
  (defmigration organization-name-english-defaults
    {:apply-when (pos? (mongo/count  :organizations {:name.en {$exists false}}))}
    (doseq [organization (mongo/select :organizations {:name.en {$exists false}})]
      (mongo/update-by-id :organizations (:id organization)
                          {$set {:name.en (-> organization :name :fi)}}))))

(defn- english-default-for-link [link]
  (if (not (-> link :name :en))
    (assoc-in link [:name :en] (-> link :name :fi))
    link))

(when (env/feature? :english)
  (defmigration set-english-defaults-for-organization-link-names
    {:apply-when (pos? (mongo/count :organizations
                                    {$and
                                     [{:links {$exists true}}
                                      {:links {$elemMatch {:name.en {$exists false}}}}]}))}
    (reduce + 0
            (for [organization (mongo/select :organizations
                                             {$and
                                              [{:links {$exists true}}
                                               {:links {$elemMatch {:name.en {$exists false}}}}]}
                                             {:links 1})]
              (mongo/update-n :organizations
                              {:_id (:id organization)}
                              {$set {:links (map english-default-for-link
                                                 (:links organization))}})))))

(defn- missing-url-map [lang]
  {(keyword (str "url." (name lang))) {$exists false}})

(defn- default-urls-for-link [link]
  (let [default-url (if (string? (:url link))
                      (:url link)
                      (-> link :url :fi))]
    (assert (string? default-url) (str "was actually " default-url))
    (assoc link :url
           (merge (i18n/localization-schema default-url)
                  (if (map? (:url link))
                    (:url link)
                    {})))))

(defmigration set-language-defaults-for-organization-link-urls
  {:apply-when (pos? (mongo/count :organizations
                                  {:links {$elemMatch {$or (map missing-url-map
                                                                i18n/supported-langs)}}}))}
  (reduce + 0
          (for [organization (mongo/select :organizations
                                           {:links {$elemMatch {$or (map missing-url-map
                                                                         i18n/supported-langs)}}}
                                           {:links 1})]
            (mongo/update-n :organizations
                            {:_id (:id organization)}
                            {$set {:links (map default-urls-for-link
                                               (:links organization))}}))))

;;
;; ****** NOTE! ******
;;  When you are writing a new migration that goes through subcollections
;;  in the collections "Applications" and "Submitted-applications"
;;  do not manually write like this
;;     (doseq [collection [:applications :submitted-applications] ...)
;;  but use the "update-applications-array" function existing in this namespace.
;; *******************
