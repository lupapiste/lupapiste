(ns lupapalvelu.invoices.util
  (:require [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.invoices.schemas :as schemas]
            [lupapalvelu.invoices.shared.schemas :refer [valid-percentage!]]
            [lupapalvelu.invoices.shared.util :as invoices-shared-util]
            [lupapalvelu.logging :refer [errorf]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$in]]
            [sade.core :refer [fail!]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as valid]
            [swiss.arrows :refer [-<>>]])
  (:import [clojure.lang Ratio]
           [java.math MathContext]
           [java.text NumberFormat]
           [java.util Locale]))

(set! *warn-on-reflection* true)

(def ^:private ^NumberFormat euro-fmt
  (NumberFormat/getCurrencyInstance (Locale. "FI" "FI")))

(defn backend-codes
  "IF the organization has invoicing backend-id support enabled and some codes defined, the
  return value is a list of maps with `:code` and `:text` keys. Otherwise returns
  nil. `organization` can be a delay."
  [organization]
  (let [{:keys [invoicing-config invoicing-backend-id-config]} (force organization)]
    (when (:backend-id? invoicing-config)
      (some->> (:codes invoicing-backend-id-config)
               (map #(select-keys % [:code :text]))
               seq))))

(defn generate-backend-id
  "Generates and returns new backend-id according to the `backend-code` and `organization`
  configuration. Organization can be delay. Returns nil if the backend-id is not configured
  or the given code is not supported."
  [organization backend-code]
  (when (util/find-by-key :code backend-code (backend-codes organization))
    (let [{cfg    :invoicing-backend-id-config
           org-id :id} (force organization)]
      (-<>> (:numbers cfg 1)
            (str "%s%0" <> "d") ; XY0000123
            (format <> backend-code (mongo/get-next-sequence-value (str "invoices-" org-id)))))))

(def operation->permit-category
  {"kortteli-yht-alue-muutos"    "Maisematyölupa"
   "muu-maisema-toimenpide"      "Maisematyölupa"
   "muu-tontti-tai-kort-muutos"  "Maisematyölupa"
   "paikoutysjarjestus-muutos"   "Maisematyölupa"
   "puun-kaataminen"             "Maisematyölupa"
   "rajankaynti"                 "Maisematyölupa"
   "tontin-ajoliittyman-muutos"  "Maisematyölupa"
   "tontin-jarjestelymuutos"     "Maisematyölupa"
   "poikkeamis"                  "Poikkeaminen"
   "purkaminen"                  "Purkamislupa"
   "aiemmalla-luvalla-hakeminen" "Rakennuslupa"
   "aloitusoikeus"               "Rakennuslupa"
   "asuinrakennus"               "Rakennuslupa"
   "auto-katos"                  "Rakennuslupa"
   "jakaminen-tai-yhdistaminen"  "Rakennuslupa"
   "jatkoaika"                   "Rakennuslupa"
   "julkinen-rakennus"           "Rakennuslupa"
   "julkisivu-muutos"            "Rakennuslupa"
   "kayttotark-muutos"           "Rakennuslupa"
   "kerrostalo-rivitalo"         "Rakennuslupa"
   "kerrostalo-rt-laaj"          "Rakennuslupa"
   "laajentaminen"               "Rakennuslupa"
   "linjasaneeraus"              "Rakennuslupa"
   "muu-laajentaminen"           "Rakennuslupa"
   "muu-rakennus-laaj"           "Rakennuslupa"
   "muu-rakentaminen"            "Rakennuslupa"
   "muu-uusi-rakentaminen"       "Rakennuslupa"
   "perus-tai-kant-rak-muutos"   "Rakennuslupa"
   "pientalo"                    "Rakennuslupa"
   "pientalo-laaj"               "Rakennuslupa"
   "rak-valm-tyo"                "Rakennuslupa"
   "raktyo-aloit-loppuunsaat"    "Rakennuslupa"
   "talousrakennus-laaj"         "Rakennuslupa"
   "teollisuusrakennus"          "Rakennuslupa"
   "teollisuusrakennus-laaj"     "Rakennuslupa"
   "vapaa-ajan-asuinrakennus"    "Rakennuslupa"
   "vapaa-ajan-rakennus-laaj"    "Rakennuslupa"
   "varasto-tms"                 "Rakennuslupa"
   "rasite-tai-yhteisjarjestely" "Rasite tai yhteisjärjestely"
   "aita"                        "Toimenpidelupa"
   "jatevesi"                    "Toimenpidelupa"
   "energiakaivo"                "Toimenpidelupa"
   "maalampo"                    "Toimenpidelupa"
   "mainoslaite"                 "Toimenpidelupa"
   "markatilan-laajentaminen"    "Toimenpidelupa"
   "masto-tms"                   "Toimenpidelupa"
   "parveke-tai-terassi"         "Toimenpidelupa"
   "sisatila-muutos"             "Toimenpidelupa"
   "takka-tai-hormi"             "Toimenpidelupa"})

(defn get-permit-category
  "Maps the operation to a more general and (for invoicing) more useful category"
  [{:keys [operation]}]
  (get operation->permit-category operation))

(def invoice-payer-fields (->> (keys schemas/InvoicePayerFields)
                               (map #(:k % %))
                               set))

(defn application-payer-info
  "Application payer information as invoice payer fields."
  [{:keys [documents]}]
  (let [specials               {:application-payer? true
                                :protected?         false
                                :country            "FIN"}
        info                   (->> (apply disj invoice-payer-fields (keys specials))
                                    (map #(vector % ""))
                                    (into specials))
        {:keys [_selected henkilo yritys
                laskuviite]}   (some-> documents
                                       (domain/get-document-by-subtype "maksaja")
                                       :data
                                       tools/unwrapped
                                       util/strip-blanks
                                       ss/trimwalk)
        company?               (= _selected "yritys")
        {:keys [henkilotiedot
                yhteystiedot]} (if company? (:yhteyshenkilo yritys) henkilo)
        {:keys [katu postinumero postitoimipaikannimi
                maa]}          (:osoite (if company? yritys henkilo))]
    (->> (merge {:first-name        (:etunimi henkilotiedot)
                 :last-name         (:sukunimi henkilotiedot)
                 :phone             (:puhelin yhteystiedot)
                 :email             (:email yhteystiedot)
                 :protected?        (boolean (:turvakieltoKytkin henkilotiedot))
                 :entity-address    katu
                 :postal-code       postinumero
                 :city              postitoimipaikannimi
                 :country           maa
                 :billing-reference laskuviite}
                (if company?
                  (let [{:keys [verkkolaskuTunnus ovtTunnus
                                valittajaTunnus]} (:verkkolaskutustieto yritys)]
                    {:entity-name (:yritysnimi yritys)
                     :payer-type  "company"
                     :company-id  (:liikeJaYhteisoTunnus yritys)
                     :netbill     verkkolaskuTunnus
                     :ovt         ovtTunnus
                     :operator    valittajaTunnus})
                  (let [{:keys [not-finnish-hetu ulkomainenHenkilotunnus
                                hetu]} henkilotiedot]
                    {:person-id  (if not-finnish-hetu
                                   ulkomainenHenkilotunnus
                                   hetu)
                     :payer-type "person"})))
         util/strip-blanks
         (merge info))))

(defn person-name
  "'First Last'. Parts are trimmed and can be missing. Returns nil for fully blank
  name. Note that `:first-name` and `:last-name` refer to the company contact person if
  the payer is a company."
  [{:keys [first-name last-name]}]
  (some->> [first-name last-name]
           (ss/trim-join " ")))

(defn company-payer?
  [{:keys [payer-type]}]
  (= payer-type "company"))

(def person-payer? (complement company-payer?))

(defn payer-name
  "Either `person-name` or the company name depending on the payer type. Nil for blanks."
  [{:keys [entity-name] :as invoice}]
  (if (company-payer? invoice)
    (some-> entity-name ss/trim ss/blank-as-nil)
    (person-name invoice)))

(defn payer-address
  "Country is included only if it is not Finland"
  ([lang {:keys [entity-address postal-code city country]}]
   (ss/trim-join ", "
                 entity-address
                 (ss/trim-join " " postal-code city)
                 (when (some-> (ss/blank-as-nil country) (not= "FIN"))
                   (i18n/localize lang :country country))))
  ([invoice]
   (payer-address :fi invoice)))

(defn- org-by-id
  [id]
  (when id
    (mongo/by-id :organizations id {:invoicing-config 1})))

(defn resolve-invoicing-config
  "Fetches invoicing config and MAY add following flags integration flags: `:sap?`,
  `:sap-excel?`, `:general?`. Credentials, if present, are decrypted.
  Returns nil if the config cannot be resolved."
  [command-or-organization-id]
  (when-let [{:keys [invoicing-config]} (if (string? command-or-organization-id)
                                          (org-by-id command-or-organization-id)
                                          (let [{:keys [data organization]} command-or-organization-id]
                                            (or (force organization)
                                                (some-> data :organizationId org-by-id))))]
    (let [{:keys [integration-url
                  invoicing-system
                  download?
                  credentials
                  local-sftp?]
           :as   invoicing-config} (ss/trimwalk invoicing-config)
          integration?             (or (ss/not-blank? integration-url)
                                       local-sftp?)
          sap?                     (= "sap-idoc" invoicing-system)
          sap-excel?               (= "sap-excel" invoicing-system)
          credentials              (some->> (bs-sec/get-credentials credentials)
                                            (zipmap [:username :password]))
          delivery-type            (invoices-shared-util/determine-delivery-type invoicing-config)]
      (-> invoicing-config
          (dissoc :download?)
          (util/assoc-when :general? integration?
                           :sap? sap?
                           :sap-excel? sap-excel?
                           :download? (and download? (not sap-excel?))
                           :credentials credentials
                           :delivery-type delivery-type)))))

(defn validate-config-credentials! [cfg]
  (if (invoices-shared-util/validate-config-credentials cfg)
    cfg
    (fail! :error.invalid-configuration)))

(defn resolve-invoice-payer-fields
  ([{:keys [application-payer? application-id] :as invoice} application]
   (cond-> invoice
     application-payer?
     (merge (application-payer-info
              (or application
                  (mongo/by-id :applications application-id [:documents]))))))
  ([invoice]
   (resolve-invoice-payer-fields invoice nil)))

(defmulti ^:private validate-invoice-field
  "Validates basic and payer information fields (the fields which are editable by the biller).
  Results are field-key - error loc-term maps."
  (fn [_ k _] k))

(defmethod validate-invoice-field :default [& _] nil)

(defn- finland? [{:keys [country]}] (= country "FIN"))
(defn- company? [{:keys [payer-type]}] (= payer-type "company"))

(defmethod validate-invoice-field :company-id
  [invoice _ v]
  (when (and (company? invoice)
             (not (valid/finnish-y? v)))
    {:company-id :error.illegal-y-tunnus}))

(defmethod validate-invoice-field :person-id
  [invoice _ v]
  (when (and (finland? invoice)
             (not (company? invoice))
             (not (valid/valid-hetu? v)))
    {:person-id :error.illegal-hetu}))

(defmethod validate-invoice-field :postal-code
  [invoice _ v]
  (when (and (finland? invoice)
             (not (valid/finnish-zip? v)))
    {:postal-code :error.illegal-zip}))

(defmethod validate-invoice-field :ovt
  [_ _ v]
  (when-not (valid/finnish-ovt? v)
    {:ovt :error.illegal-ovt-tunnus}))

(defmethod validate-invoice-field :email
  [_ _ v]
  (when-not (valid/valid-email? v)
    {:email :error.email}))

(defn validate-invoice-fields
  "Validates _filled_ invoice fields. Returns key - error-loc map. Returns an empty map if
  no errors."
  [invoice]
  (->> (util/strip-blanks invoice)
       (map (fn [[k v]]
              (validate-invoice-field invoice k v)))
       (apply merge {})))

(defn clean-invoice
  "Remove internal fields from the invoice"
  [invoice]
  (apply dissoc invoice schemas/internal-invoice-fields))

(def ^:private application-projection [:address :location :propertyId :documents :primaryOperation])

(defn enrich-invoice
  "Updates `invoice` with `application` (payer and enriched-data fields)"
  ([invoice {:keys [address location propertyId primaryOperation] :as application}]
   (when (nil? application)
     (errorf "Invoice %s refers to an unknown application %s!" (:id invoice) (:application-id invoice)))
   (let [invoice (resolve-invoice-payer-fields invoice application)]
     (-> invoice
         (assoc :errors (validate-invoice-fields invoice))
         (assoc-in [:enriched-data :application]
                   {:address     address
                    :location    location
                    :property-id propertyId
                    :operation   (:name primaryOperation)}))))
  ([{:keys [application-id] :as invoice}]
   (enrich-invoice invoice (mongo/by-id :applications application-id application-projection))))

(defn enrich-invoices
  "Sort of a caching shortcut for `enrich-invoice` where the needed applications are fetched
  in one mongo query. While this removes the `invoices` lazyness, it reduces the data
  access overhead."
  [invoices]
  (let [app-ids (some->> invoices
                         (map :application-id)
                         set)
        app-map (->> (mongo/select :applications
                                   {:_id {$in app-ids}}
                                   application-projection)
                     (map (juxt :id identity))
                     (into {}))]
    (map (fn [{:keys [application-id] :as invoice}]
           (enrich-invoice invoice (get app-map application-id)))
         invoices)))

(defn transfer-batch-filename
  "File-format is either `:excel` or `:xml`."
  ([batch file-format lang]
   (as-> (:name batch) $
     [(i18n/localize  (or lang :fi) :transfer-batch)
      $
      (date/iso-date (date/now) :local)]
     (ss/trim-join " " $)
     (str $ (case file-format
              :excel ".xlsx"
              :xml   ".xml"))
     ;; More lenient than `ss/encode-filename` (allows åäö)
     (ss/replace $ #"[^\wÄÖÅäöå\-_\. ]" "-")))
  ([batch file-format]
   (transfer-batch-filename batch file-format :fi))
  ([batch]
   (transfer-batch-filename batch :xml :fi)))

(defn transfer-batch-invoices
  "Fetches and enriches the invoices linked to the given transfer batch."
  [{:keys [id organization-id]}]
  (->> {:organization-id  organization-id
        :transfer-batch-id id}
       (mongo/select :invoices)
       enrich-invoices))

(defn parse-number
  "Trims and parses given string as a number. Supports both comma and dot separators for
  doubles. Returns nil for blanks. Throws on error."
  [s]
  (when-let [s (some-> (ss/blank-as-nil s)
                       ss/trim
                       (ss/replace #"," "."))]
    (if (some #{\.} s)
      (Double/parseDouble s)
      (Integer/parseInt s))))

(defn parse-percentage
  "Like `parse-number` but ignores preceding and trailing % symbols."
  [s]
  (some-> (ss/trim s)
          (ss/replace #"(^%+|%+$)" "")
          parse-number))

(defn parse-percentage-or-nil
  "Like `parse-percentage` but does not throw. Returns nil on bad values. This is useful
  when ignoring bad legacy data."
  [s]
  (try
    (parse-percentage s)
    (catch Exception _ nil)))

(defn safe-bigdec
  "Nil, NaN and Inf-safe wrapper for `bigdec` that enforces 16 digit precision for ratios. The latter is
  needed in order to avoid 'Non-terminating decimal expansion; no exact representable
  decimal result.' exception.

  (safe-bigdec (/ 10 3)) -> 3.333333333333333M
  (bigdec (/ 10 3)) -> (throws ArithmeticException)"
  ^BigDecimal [n]
  (cond
    (nil? n)                        nil
    (and (number? n) (NaN? n))      nil
    (and (number? n) (infinite? n)) nil
    (ratio? n)                      (.decimalValue ^Ratio n (MathContext. 16))
    :else                           (bigdec n)))

(defn num->cents
  "123.455 -> 12346. Nil-safe"
  [n]
  (some-> (safe-bigdec n)
          (.movePointRight 2)
          (.setScale 0 BigDecimal/ROUND_HALF_UP)
          (.intValue)))

(defn cents->num
  "12345 -> 123.45. Nil-safe"
  [cents]
  (some-> (safe-bigdec cents)
          (.movePointLeft 2)
          (.setScale 2 BigDecimal/ROUND_HALF_UP)
          (.doubleValue)))

(defn scale
  "Scales/rounds `n` to `d` decimals (default 2), if needed. Integers are returned
  untouched. Also, zero-decimals are returned as integers. Nil-safe."
  ([n ^long d]
   (if (or (nil? n) (integer? n))
     n
     (let [scaled (.setScale (safe-bigdec n) d BigDecimal/ROUND_HALF_UP)]
       (if (zero? d)
         (.intValue scaled)
         (.doubleValue scaled)))))
  ([n]
   (scale n 2)))

(defn euro-format
  "Formats given `amount` as a Euro string. Nil safe.
  Options:

  `:cents?` If true, the amount is in cents and will be converted (default false)

  `:symbol?` Is the Euro symbol (€) present in the result string (default true)"
  ([amount {:keys [cents? symbol?]}]
   (when amount
     (cond->  (.format euro-fmt (cond-> amount
                                  cents? cents->num
                                  true scale))
       (false? symbol?) (ss/replace #"[^0-9]+$" ""))))
  ([amount]
   (euro-format amount nil)))

(defn- good-vat [vat-string-or-num]
  (when-let [vat (util/pcond-> vat-string-or-num
                   string? parse-percentage-or-nil)]
    (when (and (pos? vat) (<= vat 100 ))
      vat)))

(defn extract-vat
  "Takes a total value (with tax, in cents), VAT definition (string or number) and returns
  the VAT tax component map:
  `:vat-percentage` (number) Percentage (always positive and max 100)
  `:vat-cents`      (int) VAT part of the `with-tax-amount-in-cents`.
  `:taxfree-cents`  (int) Amount in cents with the tax parts subtracted.

  Returns nil if a proper VAT percentage cannot be determined or
  `with-tax-amount-in-cents` is not positive."
  [with-tax-amount-in-cents vat-string-or-num]
  (when (some-> with-tax-amount-in-cents pos?)
    (when-let [vat (good-vat vat-string-or-num)]
      (let [vat-cents (-> (/ with-tax-amount-in-cents (+ 100 vat))
                          (* vat 1.0)
                          (scale 0))]
        {:vat-percentage vat
         :vat-cents      vat-cents
         :taxfree-cents  (- with-tax-amount-in-cents vat-cents)}))))

(defn calculate-percentage-cents
  "Returns integer that corresponds to `percentage` (number in range [0, 100], inclusive) of
  `amount-in-cents` (int)."
  [amount-in-cents percentage]
  (if (zero? (valid-percentage! percentage))
    0
    (-> percentage
        safe-bigdec
        (.movePointLeft 2)
        (.multiply (safe-bigdec amount-in-cents))
        (.setScale 0 BigDecimal/ROUND_HALF_UP)
        (.intValue))))

(defn add-vat
  "Takes a total taxfree value (in cents), VAT definition (string or number) and returns
  the VAT tax component map:
  `:vat-percentage` (number) Percentage (always positive and max 100)
  `:vat-cents`      (int) VAT amount for `taxfree-amount-in-cents`.
  `:with-tax-cents` (int) Amount with the tax added.

  Returns nil if a proper VAT percentage cannot be determined or `taxfree-amount-in-cents`
  is not positive."
  [taxfree-amount-in-cents vat-string-or-num]
  (when (some-> taxfree-amount-in-cents pos?)
    (when-let [vat (good-vat vat-string-or-num)]
      (let [vat-cents (calculate-percentage-cents taxfree-amount-in-cents vat)]
        {:vat-percentage vat
         :vat-cents      vat-cents
         :with-tax-cents (+ taxfree-amount-in-cents vat-cents)}))))

(defn xml-decimal-string
  "Formats given number into two decimal number string with dot as a decimal point.
  1     -> '1.00'
  1.2   -> '1.20'
  1.23  -> '1.23'
  1.234 -> '1.23'
  1.235 -> '1.24'"
  [number]
  (-> (safe-bigdec number)
      (.setScale 2 BigDecimal/ROUND_HALF_UP)
      .toString))

(defn calculate-discount
  "Calculates the discount for the given `amount-in-cents`. Returns `:amount-cents`,
  `:discount-cents` map. The `discount-percentage` range is [0, 100], inclusive. The
  default percentage is zero."
  ([amount-in-cents discount-percentage]
   (let [discount (calculate-percentage-cents amount-in-cents
                                              discount-percentage)]
     {:amount-cents   (- amount-in-cents discount)
      :discount-cents discount}))
  ([amount-in-cents]
   (calculate-discount amount-in-cents 0)))
