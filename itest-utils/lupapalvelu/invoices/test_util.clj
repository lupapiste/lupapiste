(ns lupapalvelu.invoices.test-util
  "Utility methods for accessing invoice and batch APIs from tests.
  Functions ending in `-ok` assert that the operation was successful."
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.invoices.schemas :refer [PriceCatalogueDraft]]
            [lupapalvelu.invoices.transfer-batch :as tb]
            [lupapalvelu.itest-util :as itest :refer [command local-raw ok?
                                                      query]]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(def applicant itest/mikko)           ; Hakijat
(def applicant-2 itest/pena)
(def reader itest/luukas)
(def biller itest/laura)              ; Laskuttaja
(def authority itest/sonja)           ; Viranomaiset
(def authority-2 itest/ronja)
(def municipal-authority itest/sipoo) ; Kunnan pääkäyttäjä
(def admin itest/admin)               ; Pääkäyttäjä

(def authority-user (usr/summary (min-util/find-user-from-minimal-by-apikey authority)))
(def authority-2-user (usr/summary (min-util/find-user-from-minimal-by-apikey authority-2)))
(def municipal-authority-user (usr/summary (min-util/find-user-from-minimal-by-apikey municipal-authority)))
(def biller-user (usr/summary (min-util/find-user-from-minimal-by-apikey biller)))

(defn get-transfer-batch-db
  [id]
  (mongo/by-id tb/transfer-batch-db-key id))

(defn get-invoice-db
  [invoice-id]
  (mongo/by-id "invoices" invoice-id))

(defn drop-test-data
  []
  (mongo/drop-collection tb/transfer-batch-db-key)
  (mongo/drop-collection :invoices))

(defn new-catalogue
  ([day-delta publish? {:keys [user org-id]
                        :or   {user   municipal-authority
                               org-id "753-R"}}]
   (let [from              (date/plus (date/today) :days day-delta)
         until             (date/end-of-day from)
         {:keys [id rows]} (:draft (command user :new-price-catalogue-draft
                                            :organizationId org-id))]
     (command user :edit-price-catalogue-draft
              :price-catalogue-id id
              :organizationId org-id
              :edit {:valid {:from  (date/timestamp from)
                             :until (date/timestamp until)}})
     (command user :edit-price-catalogue-draft
              :organizationId org-id
              :price-catalogue-id id
              :edit {:delete-row (-> rows first :id)})
     (when publish?
       (command user :publish-price-catalogue
                :price-catalogue-id id
                :organizationId org-id))
     id))
  ([day-delta publish?]
   (new-catalogue day-delta publish? {}))
  ([day-delta]
   (new-catalogue day-delta false)))

(defn new-invoice-ok
  "Create new invoice draft. Returns invoice id."
  [app-id]
  (let [result     (command authority :new-invoice-draft :id app-id)
        _          (fact "New invoice created" result => ok?)
        invoice-id (some-> result :invoice :id)]
    (fact {:midje/description (format "New invoice %s for application %s"
                                      invoice-id app-id)}
      invoice-id => truthy)
    invoice-id))

(defn delete-invoice
  [{:keys [user app-id invoice-id]
    :or   {user authority}}]
  (command user :delete-invoice :id app-id :invoice-id invoice-id))

(defn delete-invoice-ok
  [args]
  (fact "can delete invoice"
    (delete-invoice args) => ok?))

(defn set-invoice-field
  [{:keys [user app-id invoice-id field value]
    :or   {user authority}}]
  (command user :set-invoice-field :id app-id :invoice-id invoice-id :field field :value value))

(defn set-invoice-field-ok
  [{:keys [field value] :as args}]
  (fact {:midje/description (format "Can set invoice field %s to %s"
                                    field value)}
    (set-invoice-field args) => ok?))

(defn set-biller-invoice-field
  [{:keys [user app-id invoice-id field value]
    :or   {user biller}}]
  (command user :set-biller-invoice-field :id app-id :invoice-id invoice-id :field field :value value))

(defn set-biller-invoice-field-ok
  [{:keys [field value] :as args}]
  (fact {:midje/description (format "Can set invoice biller field %s to %s" field value)}
    (set-biller-invoice-field args) => ok?))

(defn set-invoice-state
  [{:keys [state] :as args}]
  (set-invoice-field (assoc args :field :state :value state)))

(defn set-invoice-state-ok
  [{:keys [state] :as args}]
  (fact {:midje/description (format "Can set invoice state to %s" state)}
    (set-invoice-state args) => ok?))

(defn set-biller-invoice-state
  [{:keys [state] :as args}]
  (set-biller-invoice-field (assoc args :field :state :value state)))

(defn set-biller-invoice-state-ok
  [args]
  (fact "can change biller invoice state"
    (set-biller-invoice-state args) => ok?))

(defn toggle-invoicing
  ([flag permit-type municipality]
   (command admin :update-organization
            :invoicingEnabled flag
            :municipality municipality
            :permitType permit-type))
  ([flag permit-type]
   (toggle-invoicing flag permit-type "753"))
  ([flag]
   (toggle-invoicing flag "R")))

(defn toggle-invoicing-ok
  [& args]
  (fact "can toggle invoicing"
    (apply toggle-invoicing args) => ok?))

(defn create-transfer-batch
  ([{:keys [user org-id]
     :or   {user   biller
            org-id "753-R"}}]
   (command user :new-transfer-batch :organizationId org-id))
  ([]
   (create-transfer-batch {})))

(defn create-transfer-batch-ok
  "Creates an empty transfer batch. Returns the id."
  ([args]
   (let [{:keys [transfer-batch-id] :as resp} (create-transfer-batch args)]
     (fact "can create transfer batch"
       resp => ok?)
     transfer-batch-id))
  ([]
   (create-transfer-batch-ok {})))

(defn delete-transfer-batch
  [{:keys [user org-id transfer-batch-id]
    :or   {user   biller
           org-id "753-R"}}]
  (command user :delete-transfer-batch :organizationId org-id :transfer-batch-id transfer-batch-id))

(defn rename-transfer-batch
  [{:keys [user org-id transfer-batch-id name]
    :or   {user   biller
           org-id "753-R"}}]
  (command user :rename-transfer-batch :organizationId org-id :transfer-batch-id transfer-batch-id :name name))

(defn open-transfer-batch
  [{:keys [user org-id transfer-batch-id]
    :or   {user   biller
           org-id "753-R"}}]
  (command user :open-transfer-batch :organizationId org-id :transfer-batch-id transfer-batch-id))

(defn close-transfer-batch
  [{:keys [user org-id transfer-batch-id]
    :or   {user   biller
           org-id "753-R"}}]
  (command user :close-transfer-batch :organizationId org-id :transfer-batch-id transfer-batch-id))

(defn close-transfer-batch-ok
  [args]
  (fact "can close transfer batch"
    (close-transfer-batch args) => ok?))

(defn add-invoice-to-transfer-batch
  [{:keys [transfer-batch-id] :as args}]
  (set-biller-invoice-field (assoc args :field :transfer-batch-id :value transfer-batch-id)))

(defn add-invoice-operation
  [{:keys [user app-id invoice-id operation-id]
    :or   {user biller}}]
  (command user :add-invoice-operation :id app-id :invoice-id invoice-id :operation-id operation-id))

(defn add-invoice-operation-ok
  [args]
  (fact "can add invoice operation"
    (add-invoice-operation args) => ok?))

(defn add-invoice-row
  [{:keys [user app-id invoice-id operation-id row-id]
    :or   {user biller}}]
  (command user :add-invoice-row :id app-id :invoice-id invoice-id :operation-id operation-id :row-id row-id))

(defn add-invoice-row-ok
  [args]
  (let [resp (add-invoice-row args)]
    (fact "can add invoice row" resp => ok?)
    (-> resp :row :id)))

(defn set-invoice-row-field
  [{:keys [user app-id invoice-id row-id path value]
    :or   {user biller}}]
  (command user :set-invoice-row-field :id app-id :invoice-id invoice-id :row-id row-id :path path :value value))

(defn set-invoice-row-field-ok
  [{:keys [path value] :as args}]
  (fact {:midje/description (format "Can set invoice row path %s to %s"
                                    path value)}
    (set-invoice-row-field args) => ok?))

(defn query-invoicing-config-ok
  ([{:keys [user org-id]
     :or   {user   biller
            org-id "753-R"}}]
   (let [resp (query user :invoicing-config :organizationId org-id)]
     (fact "can query invoicing config"
       resp => ok?)
     (select-keys resp [:invoicing-config :backend-id-config])))
  ([]
   (query-invoicing-config-ok {})))

(defn set-invoicing-config-ok
  [{:keys [user org-id invoicing-config]
    :or   {user   admin
           org-id "753-R"}}]
  (let [resp (command user :update-invoicing-config :org-id org-id :invoicing-config invoicing-config)]
    (fact "can update invoicing config"
      resp => ok?)))

(defn merge-to-invoicing-config-ok
  ([args config-to-merge]
   (let [{:keys [invoicing-config]} (query-invoicing-config-ok args)]
     (set-invoicing-config-ok (assoc args :invoicing-config (merge invoicing-config config-to-merge)))))
  ([config-to-merge]
   (merge-to-invoicing-config-ok {} config-to-merge)))

(defn configure-invoicing-backend-id
  [{:keys [user org-id op]
    :or   {user   municipal-authority
           org-id "753-R"}}]
  (let [resp (command user :configure-invoicing-backend-id :organizationId org-id :op op)]
    (fact {:midje/description (format "Can configure invoicing backend id with op: %s"
                                      op)}
      resp => ok?)
    resp))

(defn dummy-submitted-application [apikey]
  (itest/create-and-submit-local-application
    apikey
    :operation "pientalo"
    :x "385770.46" :y "6672188.964"
    :address "Kaivokatu 1"))

(defn app->operation
  [{{operation-id :id} :primaryOperation}]
  operation-id)

(defn create-draft-invoice-with-rows-ok
  "Creates a draft invoice with an operation and row(s)."
  ([{:keys [existing-application custom-row-count
            row-text-fn row-ppu-fn row-units-fn
            catalogue-row-ids]
     :or   {custom-row-count 1
            row-text-fn      (fn [idx] (format "Row %s" (inc idx)))
            row-ppu-fn       (constantly "10")
            row-units-fn     (constantly "1")}}]
   (let [;; Use an existing app if provided or create one
         {app-id             :id
          {operation-id :id} :primaryOperation
          :as                application} (or existing-application
                                              (dummy-submitted-application applicant))
         invoice-id                       (new-invoice-ok app-id)
         cmd-args                         {:app-id       app-id
                                           :invoice-id   invoice-id
                                           :operation-id operation-id}]
     ;; Add an operation, quite a few transfer batch API methods require this
     (add-invoice-operation-ok cmd-args)
     ;; Optionally add catalogue rows
     (run! (fn [id]
             (let [row-id   (add-invoice-row-ok (assoc cmd-args :row-id id))
                   cmd-args (assoc cmd-args :row-id row-id)]
               (set-invoice-row-field-ok (assoc cmd-args :path [:text] :value (row-text-fn id)))
               (set-invoice-row-field-ok (assoc cmd-args :path [:price-per-unit] :value (row-ppu-fn id)))
               (set-invoice-row-field-ok (assoc cmd-args :path [:units] :value (row-units-fn id)))))
           catalogue-row-ids)
     ;; Optionally add custom rows
     (run! (fn [idx]
             (let [row-id   (add-invoice-row-ok (assoc cmd-args :row-id "custom"))
                   cmd-args (assoc cmd-args :row-id row-id)]
               (set-invoice-row-field-ok (assoc cmd-args :path [:text] :value (row-text-fn idx)))
               (set-invoice-row-field-ok (assoc cmd-args :path [:price-per-unit] :value (row-ppu-fn idx)))
               (set-invoice-row-field-ok (assoc cmd-args :path [:units] :value (row-units-fn idx)))))
           (range custom-row-count))
     {:application application
      :invoice     (get-invoice-db invoice-id)
      :cmd-args    cmd-args}))
  ([]
   (create-draft-invoice-with-rows-ok {})))

(defn create-and-check-invoice-ok
  "Creates a checked invoice. Can create custom invoice rows.
  Returns the invoice from db."
  ([args]
   (let [{{invoice-id :id} :invoice
          :keys            [cmd-args]} (create-draft-invoice-with-rows-ok args)]
     (set-invoice-state-ok (assoc cmd-args :state "checked"))
     (get-invoice-db invoice-id)))
  ([]
   (create-and-check-invoice-ok {})))

(defn create-and-confirm-invoice-ok
  "Creates a confirmed invoice. Can create custom invoice rows.
  Returns the invoice from db."
  ([{:keys [transfer-batch-id sap-number billing-reference]
     :as   args}]
   (let [{{invoice-id :id} :invoice
          :keys            [cmd-args]} (create-draft-invoice-with-rows-ok args)]
     ;; draft -> checked -> confirmed
     (set-invoice-state-ok (assoc cmd-args :state "checked"))
     (set-biller-invoice-state-ok (assoc cmd-args :state "confirmed"))
     ;; Optionally move to a specific transfer batch
     (when transfer-batch-id
       (add-invoice-to-transfer-batch (assoc cmd-args :transfer-batch-id transfer-batch-id)))
     ;; Other optional biller fields
     (when sap-number
       (set-biller-invoice-field-ok (assoc cmd-args :field :sap-number :value sap-number)))
     (when billing-reference
       (set-biller-invoice-field-ok (assoc cmd-args :field :billing-reference :value billing-reference)))
     (get-invoice-db invoice-id)))
  ([]
   (create-and-confirm-invoice-ok {})))

(defn application->payer-document-id
  [application]
  (-> application
      :documents
      (domain/get-documents-by-subtype :maksaja)
      first
      :id))

(defn copy-applicant-details-into-payer-document-ok
  [{:keys [application applicant reference]}]
  (let [document-id        (application->payer-document-id application)
        {:keys [firstName
                lastName
                street
                city
                zip
                personId]} (min-util/find-user-from-minimal-by-apikey applicant)
        updates            [["henkilo.henkilotiedot.etunimi" firstName]
                            ["henkilo.henkilotiedot.sukunimi" lastName]
                            ["henkilo.henkilotiedot.hetu" personId]
                            ["henkilo.osoite.katu" street]
                            ["henkilo.osoite.postinumero" zip]
                            ["henkilo.osoite.postitoimipaikannimi" city]
                            ["laskuviite" reference]]]
    (itest/update-document! applicant (:id application) document-id updates true)))

(defn copy-business-details-into-payer-document-ok
  [{:keys [application applicant company-name business-id street zip city
           edi-code operator-code invoice-code reference first-name last-name]
    :or   {company-name  "Feikki Oy"
           business-id   "0813000-2"
           street        "Lumekatu 5"
           zip           "92620"
           city          "Huijala"
           reference     "laskuviite-pena-yritys"
           first-name    "Simo"
           last-name     "Simonen"
           ;; OVT-tunnus: 0037 + business-id
           edi-code      "003708130002"
           ;; For banks: HELSFIHH, NDEAFIHH ..
           ;; For operators: can also be a EDI code
           operator-code "DABAFIHH"
           ;; No idea
           invoice-code  "1234"}}]
  (let [document-id (application->payer-document-id application)
        updates     [["_selected" "yritys"]
                     ["yritys.yritysnimi" company-name]
                     ["yritys.liikeJaYhteisoTunnus" business-id]
                     ["yritys.osoite.katu" street]
                     ["yritys.osoite.postinumero" zip]
                     ["yritys.osoite.postitoimipaikannimi" city]
                     ["yritys.verkkolaskutustieto.ovtTunnus" edi-code]
                     ["yritys.verkkolaskutustieto.verkkolaskuTunnus" invoice-code]
                     ["yritys.verkkolaskutustieto.valittajaTunnus" operator-code]
                     ["laskuviite" reference]
                     ["yritys.yhteyshenkilo.henkilotiedot.etunimi" first-name]
                     ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" last-name]]]
    (itest/update-document! applicant (:id application) document-id updates true)))

(defn biller-invoices
  "Returns raw response from [[command]]."
  [{:keys [user org-id] :as args}]
  (command user :biller-invoices (merge {:organizationId org-id}
                                        (select-keys args [:include :exclude :limit :states :batch-ids]))))

(defn biller-invoices-ok
  [args]
  (let [resp (biller-invoices args)]
    (fact "can fetch invoices for biller"
      resp => ok?)
    (vec (:invoices resp))))

(def fixed-id (mongo/create-id))

(defn insert-catalog
  [{:keys [from until prefix user organization-id code-fn product-constants-fn operations]
    :or   {organization-id "753-R"}}]
  (let [user       municipal-authority-user
        catalog-id (mongo/create-id)]
    (->> {:id              catalog-id
          :organization-id organization-id
          :valid-from      from
          :valid-until     until
          :name            (ss/trim-join " " prefix "prices")
          :type            "R"
          :rows            (-> (map-indexed (fn [i ops]
                                              (let [ops (map name (flatten [ops]))]
                                                (merge
                                                  {:id               (mongo/create-id)
                                                   :code             (if code-fn
                                                                       (code-fn i)
                                                                       (str prefix "-" i))
                                                   :text             (->> [prefix ops i]
                                                                          flatten
                                                                          (ss/join " "))
                                                   :unit             "kpl"
                                                   :min-total-price  (+ 2 i)
                                                   :price-per-unit   (+ 5 i)
                                                   :max-total-price  (+ 10 i)
                                                   :discount-percent (* 10 i)
                                                   :operations       ops}
                                                  (when product-constants-fn
                                                    {:product-constants (product-constants-fn i)}))))
                                            (or operations
                                                [:pientalo
                                                 [:pientalo :purkaminen]
                                                 :purkaminen
                                                 [:laajentaminen :sisatila-muutos]]))
                               vec
                               (conj {:id               fixed-id
                                      :code             "fixed"
                                      :text             "Fixed price row"
                                      :unit             "m2"
                                      :price-per-unit   5
                                      :discount-percent 0
                                      :operations       ["aita"]}))
          :state           "draft"
          :meta            {:modified    (date/timestamp (date/now))
                            :modified-by user}}
         util/strip-nils
         (sc/validate PriceCatalogueDraft)
         (mongo/insert :price-catalogues))
    catalog-id))

(defn publish-catalogue
  [{:keys [user catalogue-id organization-id]
    :or   {user            municipal-authority
           organization-id "753-R"}}]
  (command user :publish-price-catalogue {:price-catalogue-id catalogue-id
                                          :organizationId     organization-id}))

(defn publish-catalogue-ok
  [args]
  (let [resp (publish-catalogue args)]
    (fact "can publish catalogue"
      resp => ok?)
    resp))

(defn configure-backend-id
  [{:keys [user numbers organization-id]
    :or   {user            municipal-authority
           organization-id "753-R"}}]
  (command user :configure-invoicing-backend-id
           {:organizationId organization-id
            :op             {:set {:numbers numbers}}}))

(defn configure-backend-id-ok
  [args]
  (let [resp (configure-backend-id args)]
    (fact "can configure backend id"
      resp => ok?)
    resp))

(defn upsert-backend-id
  [{:keys [user organization-id code text]
    :or   {user            municipal-authority
           organization-id "753-R"}}]
  (command user :configure-invoicing-backend-id
           {:organizationId organization-id
            :op             {:upsert {:code code
                                      :text text}}}))

(defn upsert-backend-id-ok
  [args]
  (let [resp (upsert-backend-id args)]
    (fact "can upsert backend id"
      resp => ok?)
    resp))

(defn download-transfer-batch-xml
  [{:keys [user org-id lang transfer-batch-id]
    :or   {user   biller
           org-id "753-R"}}]
  (local-raw user :download-transfer-batch-xml
             (util/strip-nils {:organizationId    org-id
                               :lang              lang
                               :transfer-batch-id transfer-batch-id})))

(defn process-transfer-batch-integration
  [{:keys [user org-id transfer-batch-id]
    :or   {user   biller
           org-id "753-R"}}]
  (command user :process-transfer-batch-integration :organizationId org-id :transfer-batch-id transfer-batch-id))

(defn download-transfer-batch-excel
  [{:keys [user org-id transfer-batch-id]
    :or   {user   biller
           org-id "753-R"}}]
  (local-raw user :download-transfer-batch-excel :organizationId org-id :transfer-batch-id transfer-batch-id))
