(ns lupapalvelu.migration.migrations
  (:require [monger.operators :refer :all]
            [taoensso.timbre :as timbre :refer [debug debugf info infof warn warnf error errorf]]
            [clojure.walk :as walk]
            [sade.util :refer [dissoc-in postwalk-map strip-nils abs] :as util]
            [sade.core :refer [def-]]
            [sade.strings :as ss]
            [sade.property :as p]
            [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.application-meta-fields :as app-meta-fields]
            [lupapalvelu.operations :as op]
            [sade.env :as env]
            [sade.excel-reader :as er]))

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
  (let [next-ver (lupapalvelu.attachment/next-attachment-version (:version (last old-versions)) user)]
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
    (if (util/rakennustunnus? (:VTJ_PRT new-id))
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
    (let [[_ protocol username password url] (re-matches #"(.*://)?(.*:)?(.*@)?(.*$)" url)
          username (apply str (butlast username))
          password (apply str (butlast password))]
      (when (and url (not (empty? username)) (not (empty? password)))
        [permit-type (merge krysp-config {:url (str protocol url) :username username :password password})]))))

(defn update-krysp-config
  [{organization-id :id} permit-type {url :url username :username password :password version :version}]
  (when username
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
;;
;; ****** NOTE! ******
;;  When you are writing a new migration that goes through the collections "Applications" and "Submitted-applications"
;;  do not manually write like this
;;     (doseq [collection [:applications :submitted-applications] ...)
;;  but use the "update-applications-array" function existing in this namespace.
;; *******************

