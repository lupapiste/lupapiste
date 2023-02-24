(ns lupapalvelu.invoices.transfer-batch
  "Behold, the beast that handles invoice transfer batch. Transfer batch is a collection of invoices, but with additional super powers.
  However, transfer batch is a villain, that has state, and that makes things somewhat complicated.
  The hastly written code with last minute changes to sum calculation and row nbr calculation does not help.

  When taming the beast, ye should keep in mind these ancient secrets:
  - When invoice is added to transfer batch, the transfer batch may be closed.
    This is unearhly abomination, since it will alter the state of included invoices to :transferred
  - The counting of sums and row numbers depends on the order of following operations:
    - When adding or removing invoices you should:
       - first update the invoice model, either add or remove the key :transfer-batch-id
       - only after that ye should count sums or nbr of rows.

  There's no good excuse for mentioned behaviour, but it's there anyway.

  Sail safe"

  (:require
   [clojure.walk :as walk]
   [lupapalvelu.document.tools :refer [unwrapped]]
   [lupapalvelu.domain :as domain]
   [lupapalvelu.integrations.messages :as imessages]
   [lupapalvelu.invoices.general-api-xml-converter :as xml-converter]
   [lupapalvelu.invoices.schemas :refer [GeneralApiXMLInput
                                         IdocSapInput
                                         ->invoice-user
                                         InvoiceTransferBatch
                                         OrganizationsTransferBatchesRequest
                                         TransferBatchOrgsResponse]]
   [lupapalvelu.invoices.util :refer [add-history-entry enrich-with-backend-id]]
   [lupapalvelu.invoices.ya-sap-converter :as ya-sap-xml-converter]
   [lupapalvelu.money :as money]
   [lupapalvelu.mongo :as mongo]
   [lupapalvelu.organization :as org]
   [lupapalvelu.roles :as roles]
   [lupapalvelu.sftp.client :as sftp-client]
   [lupapalvelu.sftp.core :as sftp]
   [me.raynes.fs :as fs]
   [monger.operators :refer [$lte $gte $in $and $set]]
   [sade.core :refer [now fail fail!]]
   [sade.date :as date]
   [sade.env :as env]
   [sade.http :as http]
   [sade.strings :as ss]
   [sade.util :as util]
   [schema.core :as sc]
   [swiss.arrows :refer [-<>]]
   [taoensso.timbre :refer [debug warn error]])
  (:import [java.io File]
           [java.net URI]))

(defprotocol TransferBatch
  (send-to-sap [_ xml filename]))

(deftype TampereSAP []
  TransferBatch
  (send-to-sap [_ xml filename]
    (let [sftp-address            (env/value :invoices :tampere-ya :sftp :address)
          remote-sftp-dir-path    (env/value :invoices :tampere-ya :sftp :dir-path)
          remote-sftp-file-path   (str remote-sftp-dir-path "/" filename)
          auth {:username         (env/value :invoices :tampere-ya :sftp :username)
                :private-key-path (env/value :invoices :sftp :private-key-path)}]
      (sftp-client/upload-file! sftp-address xml remote-sftp-file-path auth))))

(deftype DevSAP []
  TransferBatch
  (send-to-sap [_ xml filename]
    (let [dir      (env/value :outgoing-directory)
          filename (str dir env/file-separator filename)
          tempfile (File/createTempFile "tre_sap" "xml")]
      (fs/mkdirs dir)
      (try
        (spit tempfile xml)
        (catch java.io.FileNotFoundException e
          (when (fs/exists? tempfile)
            (fs/delete tempfile))
          (error e (.getMessage e))
          (throw e)))
      (when (fs/exists? filename) (fs/delete filename))
      (fs/rename tempfile filename)
      (fs/chmod "+rw" filename))))

(def transfer-batch-db-key :invoice-transfer-batches)

(def default-nbr-rows 90)

(defn validate-invoice-transfer-batch [invoice-transfer-batch]
  (sc/validate InvoiceTransferBatch invoice-transfer-batch))

(defn validate-invoice-transfer-batches-orgs-response [invoice-transfer-batch-response]
  (sc/validate TransferBatchOrgsResponse invoice-transfer-batch-response))

(defn- any-non-numeric-value? [& xs]
  (not (every? #(or (number? %) (ss/numeric? %)) (remove nil? xs))))

(defn validate-organization-transferbatches-request [{{:keys [states from until limit] :as optional-params} :data :as command}]
  (try
    (when (any-non-numeric-value? from until limit)
      (throw (Exception. (str "Some optional filter was provided but its value was not a number." (format "from:% until:% limit:%" from until limit)))))

    (sc/validate OrganizationsTransferBatchesRequest (util/strip-nils {:states states
                                                                       :from  (util/->long from)
                                                                       :until (util/->long until)
                                                                       :limit (util/->long limit)}))
    nil
    (catch Exception e
      (warn "Invalid organization-transferbatches request" (.getMessage e))
      (fail :error.invalid-request))))

(defn ->invoice-transfer-batch-db
  "Enrich invoice-transfer-batch map with user data,
  so that it can be _inserted_ to database

  `invoice-transfer-batch` is invoice-transfer-batch map
  `org-id` Organization id added to invoice-transfer-batch
  `user` is user map to be transferred to `lupapalvelu.invoices/User`
  `timestamp` is the timestamp of the moment the transferbatch was created

  Returns `lupapalvelu.invoices.transfer-batch/InvoiceTransferBatch`"
  [invoice-transfer-batch org-id user timestamp]
  (merge invoice-transfer-batch
         {:created (or timestamp (now))
          :created-by (->invoice-user user)
          :organization-id org-id
          :state "open"
          :number-of-rows 0
          :invoices []
          :sum {:minor 0
                :major 0
                :currency "EUR"
                :text ""}}))

(defn- count-rows-in-operations [operations]
  (apply + (map (comp count :invoice-rows) operations)))

(defn- count-rows-in-invoice [invoice]
  (let [operations (:operations invoice)]
    (count-rows-in-operations operations)))

(defn- fetch-existing-transfer-batch [org-id]
  (mongo/select-one transfer-batch-db-key {:organization-id org-id :state "open"}))

(defn- create-new-transfer-batch! [org-id user & [timestamp]]
  (let [transfer-batch (->invoice-transfer-batch-db {} org-id user timestamp)
        id (mongo/create-id)
        transfer-batch-with-id (assoc transfer-batch :id id)]
    (validate-invoice-transfer-batch transfer-batch-with-id)
    (mongo/insert transfer-batch-db-key transfer-batch-with-id)
    id))

(defn- get-max-invoice-rows [org-id]
  (let [org-max-rows (:max-invoice-row-count-in-transferbatch (org/get-invoicing-config org-id))]
    (or org-max-rows default-nbr-rows)))

(defn- is-room-for-invoice? [tb nbr-invoice-rows org-id]
  (let [total (+ nbr-invoice-rows (:number-of-rows tb))]
    (>= (get-max-invoice-rows org-id) total)))

(defn set-transfer-batch-state
  "Changes transfer batch state"
  [transfer-batch state]
  (assoc transfer-batch :state state))

(defn- invoices-for-transfer-batch [transfer-batch]
  (let [invoice-ids (map :id (:invoices transfer-batch))]
    (mongo/select :invoices {:_id {$in invoice-ids}})))

(defn close-transfer-batch! [tb-id user-org-ids user & [timestamp]]
  (let [tb (mongo/by-id transfer-batch-db-key tb-id)
        tb-org-id (:organization-id tb)
        time (or timestamp (now))
        updated-tb (-<> tb
                       (set-transfer-batch-state "closed")
                       (assoc :closed time)
                       (assoc :modified time)
                       (add-history-entry user "Close transfer batch" time <>))
        orgs-in-tb-org? (some #{tb-org-id} user-org-ids)]
    (when orgs-in-tb-org?
      (validate-invoice-transfer-batch updated-tb)
      (mongo/update-by-id transfer-batch-db-key tb-id updated-tb)
      (mongo/update-by-query :invoices {:transferbatch-id (:id updated-tb)} {$set {:state "transferred"}})
      true)))

(defn- get-or-create-invoice-transfer-batch-for-org [org-id user invoice timestamp]
  (let [existing-transfer-batch (fetch-existing-transfer-batch org-id)
        nbr-invoice-rows (count-rows-in-invoice invoice)]
    (if (and existing-transfer-batch
             (is-room-for-invoice? existing-transfer-batch nbr-invoice-rows org-id))
      (:id existing-transfer-batch)
      (do
        (when existing-transfer-batch
          (close-transfer-batch! (:id existing-transfer-batch) [org-id] user timestamp))
        (create-new-transfer-batch! org-id user timestamp)))))

(defn- ->tb-invoice [invoice & [timestamp]]
  {:id (:id invoice)
   :added-to-transfer-batch (or timestamp (now))
   :organization-id (:organization-id invoice)})

(defn- calculate-tb-sum [tb]
  (let [invoices (mongo/select :invoices {:transferbatch-id (:id tb)})
        currency (money/->currency-code (get-in tb [:sum :currency]))
        sum-in-minor (reduce (fn [memo invoice]
                                     (let [minor (get-in invoice [:sum :minor] 0)]
                                       (+ memo minor))) 0 invoices)]
    (money/->MoneyResponse (money/minor->currency currency sum-in-minor))))

(defn- calculate-nbr-rows-for-tb [tb]
  (let [invoices-from-db (invoices-for-transfer-batch tb)]
    (reduce + (map (fn [invoice]
                     (count-rows-in-invoice invoice)) invoices-from-db))))

(defn- remove-invoice-from-tb [transfer-batch invoice]
  (let [id (:id invoice)
        new-invoices (->> (:invoices transfer-batch)
                          (remove (fn [i] (= id (:id i))))
                          vec)]
    (assoc transfer-batch :invoices new-invoices)))

(defn- invoice-to-transfer-batch [transfer-batch invoice timestamp]
  (let [invoice-data-to-tb (->tb-invoice invoice timestamp)
        invoices (:invoices transfer-batch)
        added-invoices (conj invoices invoice-data-to-tb)
        tb-with-invoices (assoc transfer-batch :invoices added-invoices)]
    (assoc tb-with-invoices :number-of-rows (calculate-nbr-rows-for-tb tb-with-invoices))))

(defn remove-invoice-from-transfer-batch! [invoice user timestamp]
  (let [tb (mongo/by-id transfer-batch-db-key (:transferbatch-id invoice))]
    (mongo/update-by-id :invoices (:id invoice) (dissoc invoice :transferbatch-id))
    (let [invoices (mongo/select :invoices {:transferbatch-id (:id tb)})
          sum (calculate-tb-sum tb)
          tb-without-invoice (remove-invoice-from-tb tb invoice)
          updated-tb (->> (assoc tb-without-invoice
                                 :sum sum
                                 :number-of-rows (calculate-nbr-rows-for-tb tb-without-invoice)
                                 :modified timestamp)
                          (add-history-entry user (str "Remove invoice " (:id invoice)) timestamp))]
      (mongo/update-by-id transfer-batch-db-key (:id updated-tb) updated-tb))))

(defn add-invoice-to-transfer-batch!
  "Inserts invoice to transfer batch. Checks if organization has open transfer batch, and adds invoice to that.
  If organization does not have open transfer batch, creates new one an inserts invoice to there.
  If transfer batch would have more rows than is should, closes transfer batch and creates new, and adds invoice there.
  Generates backend-id for the invoice if supported and not generated earlier."
  [{org-id :organization-id invoice-id :id :as invoice} user-who-adds-invoice timestamp]
  (let [tb-id (get-or-create-invoice-transfer-batch-for-org org-id user-who-adds-invoice invoice timestamp)
        transfer-batch (mongo/by-id transfer-batch-db-key tb-id)
        transfer-batch-with-invoice-added (-<> transfer-batch
                                              (invoice-to-transfer-batch invoice timestamp)
                                              (assoc :modified timestamp)
                                              (add-history-entry user-who-adds-invoice (str "Add invoice " invoice-id) timestamp <>))]
    (validate-invoice-transfer-batch transfer-batch-with-invoice-added)
    (mongo/update-by-id transfer-batch-db-key (:id transfer-batch) transfer-batch-with-invoice-added)
    (mongo/update-by-id :invoices
                        invoice-id
                        (-> (enrich-with-backend-id invoice)
                            (assoc :transferbatch-id (:id transfer-batch))))
    (let [sum (calculate-tb-sum transfer-batch-with-invoice-added)
          invoices (mongo/select :invoices {:transferbatch-id (:id transfer-batch-with-invoice-added)})
          nbr-rows (reduce (fn [memo invoice]
                             (+ memo (count-rows-in-invoice invoice))) 0 invoices)]
      (mongo/update-by-id transfer-batch-db-key (:id transfer-batch) (assoc transfer-batch-with-invoice-added :sum sum :number-of-rows nbr-rows)))
    (:id transfer-batch)))

(defn fetch-transfer-batches-for-org
  "Returns transfer batches as a vector for an organization sorted by newest first using :created"
  [org-id & [{:keys [states from until limit] :as filters}]]
  (let [filters (cond-> []
                  (seq states) (conj {:state   {$in  states}})
                  from         (conj {:created {$gte from}})
                  until        (conj {:created {$lte until}}))
        query {$and (concat [{:organization-id org-id}] filters)}]

    ;;sort-by :created ascending (-1 = newest first)
    (vec (mongo/select-ordered transfer-batch-db-key query {:created -1} (or limit 0)))))

(defn get-transfer-batch-for-orgs
  "Retrurns a transfer batch response, where transfer batches and corresponding invoices are mapped by organization id.
  Basically this is the way they're handled in billing ui."
  [org-ids & [filters]]
  (let [response (reduce (fn [memo org-id]
                           (let [transfer-batches-for-org (fetch-transfer-batches-for-org org-id filters)
                                 transfer-batches-for-response (mapv (fn [tb]
                                                                      (let [invoices-for-tb (vec (invoices-for-transfer-batch tb))
                                                                            invoice-count (count invoices-for-tb)
                                                                            invoice-row-count (:number-of-rows tb)]
                                                                        {:id (:id tb)
                                                                         :transfer-batch tb
                                                                         :invoice-count invoice-count
                                                                         :invoice-row-count invoice-row-count
                                                                         :invoices invoices-for-tb}))
                                                                     transfer-batches-for-org)]
                             (assoc memo org-id transfer-batches-for-response))) {} org-ids)]
    (validate-invoice-transfer-batches-orgs-response response)))

(defn user-has-role-in-transfer-batch-org? [user required-role {:keys [organization-id] :as transfer-batch}]
  (some #{organization-id} (doall (roles/organization-ids-by-roles user #{required-role}))))


(defn ->idoc-row [{:keys [sap-materialid sap-plant sap-profitcenter] :as org-config} invoice-row]
  {:sap-materialid   sap-materialid
   :sap-plant        sap-plant
   :sap-profitcenter sap-profitcenter
   :quantity         (:units invoice-row)
   :unitprice        (:price-per-unit invoice-row)
   :text             (:text invoice-row)})

(defn get-invoice-rows [{:keys [operations] :as invoice}]
  (mapcat :invoice-rows operations))

(defn- ->idoc-invoice-data [org-config {:keys [application] :as invoice-with-app}]
  (let [sap-number (:sap-number invoice-with-app)
        data       (cond-> {;;from org config
                           :sap-integration-id      (:sap-integration-id org-config)
                           :sap-ordertype           (:sap-ordertype org-config)
                           :sap-salesorganization   (:sap-salesorganization org-config)
                           :sap-distributionchannel (:sap-distributionchannel org-config)
                           :sap-division            (:sap-division org-config)
                           :sap-term-id             (:sap-term-id org-config)

                           ;;from application
                           :target                  {:street (:address application)}
                           :operation               (get-in application [:primaryOperation :name])

                           ;;from invoice
                           :customer                {}
                           :permit-id               (:application-id invoice-with-app)
                           :sap-bill-date           (:sap-bill-date invoice-with-app)
                           :description             (:description invoice-with-app)
                           :invoice-rows            (map (partial ->idoc-row org-config) (get-invoice-rows invoice-with-app))}
                     sap-number (assoc-in [:customer :sap-number] sap-number)
                     (seq (:billing-reference invoice-with-app)) (assoc :your-reference (:billing-reference invoice-with-app)))]
    (sc/validate IdocSapInput data)))


(defn enrich-with-application [invoice]
  (let [application (mongo/by-id :applications (:application-id invoice))]
    (assoc invoice :application application)))

(defn add-bill-date [timestamp invoice]
  (assoc invoice :sap-bill-date timestamp))

(def tampere-ya-sap-config
  {:sap-integration-id "334"
   :sap-ordertype "ZLUP"
   :sap-salesorganization "1111"
   :sap-distributionchannel "00"
   :sap-division "00"
   :sap-term-id ""
   :sap-materialid "502557"
   :sap-plant "1111"
   :sap-profitcenter "111886"})

(defn generate-idoc-xml [transfer-batch]
  (let [tb-invoices (invoices-for-transfer-batch transfer-batch)
        enriched-invoices (->> tb-invoices
                               (map enrich-with-application)
                               (map (partial add-bill-date (now))))
        invoices-for-xml (->> enriched-invoices
                              (map (partial ->idoc-invoice-data tampere-ya-sap-config))
                              (filter #(not (empty? (:invoice-rows %)))))]
    (ya-sap-xml-converter/->idoc-xml invoices-for-xml)))

(defn filename-prefix [{:keys [invoice-file-prefix default-invoice-file-prefix] :as invoice-config}]
  (let [prefix-without-whitespace (ss/replace invoice-file-prefix #"\s+" "")]
    (or (ss/blank-as-nil prefix-without-whitespace) default-invoice-file-prefix "")))

(def ^:private SAP-DATETIME (date/pattern-formatter "d.M.YYYY_HH.mm.ss"))

(defn- filename-for-xml-sap [prefix timestamp]
  (let [datetime (date/zoned-format timestamp SAP-DATETIME)]
    (str prefix datetime "_" timestamp ".xml")))

(defn mark-delivered! [{:keys [id] :as transfer-batch} user timestamp action-name]
  (let [updated-tb (add-history-entry user action-name timestamp transfer-batch)]
    (mongo/update-by-id transfer-batch-db-key id updated-tb)))

(defn get-delivery []
  (cond
    (ss/not-blank? (env/value :invoices :tampere-ya :sftp :address))
    (->TampereSAP)

    (and (ss/blank? (env/value :invoices :tampere-ya :sftp :address))
         (env/dev-mode?))
    (->DevSAP)

    :else
    (do
      (error "Can't send Tampere YA transfer batch, no address specified")
      (fail! :error.sftp.user-not-set))))

(defn deliver-tampere-ya-transfer-batch-to-sap! [transfer-batch user timestamp]
  (let [invoicing-config (org/get-invoicing-config (:organization-id transfer-batch))
        xml       (generate-idoc-xml transfer-batch)
        filename  (filename-for-xml-sap (filename-prefix (merge invoicing-config {:default-invoice-file-prefix "ID334_Lupapiste_"})) timestamp)
        deliverer (get-delivery)]
    (send-to-sap deliverer xml filename)
    (mark-delivered! transfer-batch (->invoice-user user) timestamp "send-to-sap")))


;;
;;  Deliver general transfer batch to invoicing system
;;

;; XML generation

(def system-id "Lupapiste")

(defn ->invoice-row [{:keys [code text unit units price-per-unit discount-percent product-constants] :as invoice-row}]
  (util/assoc-when {:code      (or code "")
                    :name      text
                    :unit      unit
                    :quantity  units
                    :discount-percent discount-percent
                    :unitprice price-per-unit}
                   :product-constants (->> product-constants
                                           (filter (comp ss/not-blank? val))
                                           (into {})
                                           not-empty)))

(defn- tidywalk
  "Walks `form`, trims string values and replaces nil values with empty strings."
  [form]
  (when-not (nil? form)
    (let [empty-string (constantly "")]
      (walk/postwalk (fn [v]
                       (cond-> v
                         (string? v) ss/trim
                         (nil? v)    empty-string))
                     form))))

(defn- parse-person-name
  "Last part is the lastname. Returns firstname(s) lastname pair."
  [fullname]
  (let [parts (ss/split (ss/trim fullname) #"\s+")]
    [(ss/join " " (butlast parts)) (or (last parts) "")]))

(sc/defn ^:private ^:always-validate ->invoice-data :- GeneralApiXMLInput
  [invoicing-config {:keys [application payer-type backend-id] :as invoice-with-app}]
  (let [{:keys [myyntiorg jakelutie
                sektori]}   (:constants invoicing-config)
        payer-doc           (unwrapped (first (domain/get-documents-by-subtype (:documents application) :maksaja)))
        organization-payer? (if payer-type
                              (= payer-type "company")
                              (some? (:company-id invoice-with-app)))
        data                {:invoice-type (if (:organization-internal-invoice? invoice-with-app) "INTERNAL" "EXTERNAL")
                             :reference    (:billing-reference invoice-with-app)
                             :payer        (merge
                                             {:payer-type (if organization-payer? "ORGANIZATION" "PERSON")}
                                             (if organization-payer?
                                               (let [yritys               (get-in payer-doc [:data :yritys])
                                                     ;; Old invoices do not have :company-contact-person
                                                     [firstname lastname] (if-let [contact (:company-contact-person invoice-with-app)]
                                                                            (parse-person-name contact)
                                                                            [(get-in yritys [:yhteyshenkilo :henkilotiedot :etunimi])
                                                                             (get-in yritys [:yhteyshenkilo :henkilotiedot :sukunimi])])]
                                                 {:organization {:id                  (:company-id invoice-with-app)                                                      ;; y-tunnus
                                                                 :name                (:entity-name invoice-with-app)
                                                                 :partner-code        (:partner-code invoice-with-app)
                                                                 :contact-firstname   firstname
                                                                 :contact-lastname    lastname
                                                                 :contact-turvakielto (str (or (get-in yritys [:yhteyshenkilo :henkilotiedot :turvakieltoKytkin]) false)) ;; ya-maksaja lacks :turvakieltoKytkin
                                                                 :streetaddress       (get-in yritys [:osoite :katu])
                                                                 :postalcode          (get-in yritys [:osoite :postinumero])
                                                                 :city                (get-in yritys [:osoite :postitoimipaikannimi])
                                                                 :country             (get-in yritys [:osoite :maa])
                                                                 :einvoice-address    (get-in yritys [:verkkolaskutustieto :verkkolaskuTunnus])
                                                                 :edi                 (:ovt invoice-with-app)
                                                                 ;; Old invoices do not have :operator
                                                                 :operator            (:operator invoice-with-app (get-in yritys [:verkkolaskutustieto :valittajaTunnus]))}})

                                               (let [person               (get-in payer-doc [:data :henkilo])
                                                     [firstname lastname] (parse-person-name (:entity-name invoice-with-app))]
                                                 {:person {:id            (:person-id invoice-with-app)                                        ;; hetu
                                                           :firstname     firstname
                                                           :lastname      lastname
                                                           :turvakielto   (str (or (get-in person [:henkilotiedot :turvakieltoKytkin]) false)) ;; ya-maksaja lacks :turvakieltoKytkin
                                                           :partner-code  (:partner-code invoice-with-app)
                                                           :streetaddress (get-in person [:osoite :katu])
                                                           :postalcode    (get-in person [:osoite :postinumero])
                                                           :city          (get-in person [:osoite :postitoimipaikannimi])
                                                           :country       (get-in person [:osoite :maa])}})))
                             ;; from organization's invoicing-config
                             :payee        {:payee-organization-id myyntiorg
                                            :payee-group           jakelutie
                                            :payee-sector          sektori}
                             ;; from application
                             :target       {:street (:address application)}
                             :operation    (get-in application [:primaryOperation :name])
                             ;; from invoice
                             :permit-id    (:application-id invoice-with-app)
                             :customer     {:client-number (str (:sap-number invoice-with-app))}
                             :invoice-rows (map ->invoice-row (get-invoice-rows invoice-with-app))}]
    (cond-> (tidywalk data)
      (:backend-id? invoicing-config)
      (util/assoc-when :backend-id backend-id))))

(defn generate-general-xml [invoicing-config transfer-batch timestamp]
  (let [invoices-for-xml (->> (invoices-for-transfer-batch transfer-batch)
                              (map enrich-with-application)
                              (map (partial ->invoice-data invoicing-config)))]
    (xml-converter/->xml timestamp system-id invoices-for-xml)))

;; sending via HTTP

(defn send-xml-to-invoicing-system-via-http! [xml {:keys [integration-url credentials] :as invoicing-config}]
  (let [stripped-url (-> integration-url ss/trim (ss/split #"\?") first)
        options      (cond-> {:body xml
                              :throw-exceptions false
                              :throw-fail!      true
                              :content-type     "text/xml;charset=UTF-8"
                              :socket-timeout   10000
                              :conn-timeout     10000}
                             credentials (assoc :basic-auth [(:username credentials) (:password credentials)]))
        resp (http/post stripped-url options)]
    (:status resp)))

;; sending via SFTP

(defn- sftp-connect-info [{:keys [credentials url-data]} filename]
  (let [{:keys [host path]} url-data]
    {:host                  host
     :remote-sftp-file-path (ss/join-non-blanks "/" (concat (ss/split path #"/") [filename]))
     :auth                  (if (ss/blank? (:password credentials))
                              {:username         (:username credentials)
                               :private-key-path (env/value :invoices :sftp :private-key-path)}
                              (select-keys credentials [:username :password]))}))

(defn send-xml-to-invoicing-system-via-sftp! [xml invoicing-config filename]
  (let [{:keys [host remote-sftp-file-path auth]} (sftp-connect-info invoicing-config filename)]
    (sftp-client/upload-file! host xml remote-sftp-file-path auth)))

(def ^:private SFTP-DATETIME (date/pattern-formatter "YYMMddHHmmss"))

(defn- filename-for-xml-sftp [prefix timestamp]
  (let [datetime (date/zoned-format timestamp SFTP-DATETIME)]
    (str prefix datetime ".xml")))

(defn- delivery-type [{:keys [url-data local-sftp?]}]
  (cond
    local-sftp?                   :local-sftp
    (= "sftp" (:scheme url-data)) :sftp
    :else                         :http))

(defn- validate-config! [{:keys [integration-url credentials local-sftp?] :as invoicing-config}]
  (sc/validate org/InvoicingConfig invoicing-config)
  (when-not (or local-sftp?
                (and
                  (ss/not-blank? integration-url)
                  (or (nil? credentials)
                      (and (= (count credentials) 2)
                           (every? #(contains? credentials %) [:username :password])
                           (ss/not-blank? (:username credentials))))))
    (fail! :error.invalid-configuration)))

(defn- save-integration-message! [user xml timestamp delivery-type]
  (let [message-id (imessages/create-id)]
    (imessages/save {:id message-id
                     :direction "out"
                     :messageType "deliver-general-transfer-batch-to-invoicing-system"
                     :format "xml"
                     :created timestamp
                     :status "processing"
                     :initiator (select-keys user [:id :username])
                     :action (str "deliver-general-transfer-batch-to-invoicing-system-over-" (name delivery-type))
                     :data xml})
    message-id))

(defn deliver-general-transfer-batch-to-invoicing-system! [{:keys [user created]}
                                                           {:keys [organization-id] :as transfer-batch}]
  (let [{:keys [integration-url]
         :as   invoicing-config} (org/get-invoicing-config organization-id)
        _                        (validate-config! invoicing-config)
        invoicing-config         (cond-> invoicing-config
                                   (some? integration-url) (-> (update :integration-url ss/trim)
                                                               (assoc :url-data (bean (URI. integration-url)))))

        xml                      (generate-general-xml invoicing-config transfer-batch created)
        delivery-type            (delivery-type invoicing-config)
        integration-message-id   (save-integration-message! user xml created delivery-type)]

    (debug "xml to send: " xml)

    (if (= :http delivery-type)
      (send-xml-to-invoicing-system-via-http! xml invoicing-config)
      (let [file-prefix (filename-prefix (merge invoicing-config {:default-invoice-file-prefix "Lupapiste_"}))
            filename    (filename-for-xml-sftp file-prefix created)]
        (if (:local-sftp? invoicing-config)
          (sftp/write-invoicing-file organization-id filename xml)
          (send-xml-to-invoicing-system-via-sftp! xml invoicing-config filename))))
    (imessages/set-message-status integration-message-id "processed")
    (mark-delivered! transfer-batch (->invoice-user user) created "send-to-invoicing-system")))
