(ns lupapalvelu.pate-itest-util
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.util :as util]))

(defn err [error]
  (partial expected-failure? error))

(def timestamp util/to-millis-from-local-date-string)

(defn no-errors?
  "Checks PATE api response and ensures request didn't contain errors"
  [{:keys [errors ok]}]
  (and (nil? errors) (true? ok)))

(defn invalid-value? [{:keys [errors]}]
  (util/=as-kw (-> errors first last) :error.invalid-value))

(defn toggle-pate [org-id flag]
  (fact {:midje/description (format "Toggle Pate for %s: %s"
                                    org-id flag)}
    (command admin :set-organization-boolean-path
             :organizationId org-id
             :path "pate-enabled"
             :value flag)=> ok?))

(defn add-repeating-setting
  "Returns the id of the new item."
  [apikey category rep-dict add-dict & kvs]
  (let [id (get-in (command apikey :save-verdict-template-settings-value
                            :category category
                            :path [add-dict]
                            :value nil)
                   [:changes 0 0 1])]
    (doseq [[k v] (apply hash-map (flatten kvs))]
      (command apikey :save-verdict-template-settings-value
               :category category
               :path [rep-dict id k]
               :value v))
    id))

(defn check-file [app-id file-id exists?]
  (fact {:midje/description (str "Check that file "
                                 (if exists? "exists" "does not exist"))}
    (raw sonja :download-attachment :file-id file-id :id app-id)
    => (contains {:status (if exists? 200 404)})))

(defn init-verdict-template [apikey category]
  (command apikey :new-verdict-template
           :category (name category)))

(defn set-template-draft-value [apikey template-id path value]
  (fact {:midje/description (format "Draft value: %s %s"
                                    path value)}
        (command apikey :save-verdict-template-draft-value
                 :template-id template-id
                 :path (map keyword (flatten [path]))
                 :value value) => ok?))

(defn set-template-draft-values [apikey template-id & args]
  (doseq [[path value] (->arg-map args)]
    (set-template-draft-value apikey template-id path value)))

(defn publish-verdict-template [apikey template-id]
  (command apikey :publish-verdict-template
           :template-id template-id))

(defn fill-sisatila-muutos-application [apikey app-id]
  (let [{docs :documents
         :as  app} (query-application apikey app-id)
        doc-map    (reduce (fn [acc {:keys [id schema-info]}]
                             (assoc acc (:name schema-info) id))
                           {}
                           docs)
        update-doc (fn [doc-name updates]
                     (fact {:midje/description (str "Fill " doc-name)}
                       (command apikey :update-doc
                                :id app-id
                                :collection "documents"
                                :doc (get doc-map doc-name)
                                :updates updates)
                       => ok?))]
    (update-doc "hankkeen-kuvaus" [["kuvaus" "Description"]
                                   ["poikkeamat" "Deviation from mean."]])
    (update-doc "rakennuspaikka" [["hallintaperuste" "oma"]])
    (fact "Select building"
      (command apikey :merge-details-from-krysp
               :id app-id
               :buildingId "122334455R"
               :collection "documents"
               :documentId (get doc-map "rakennuksen-muuttaminen")
               :overwrite true
               :path "buildingId") => ok?
      (update-doc "rakennuksen-muuttaminen" [["buildingId" "122334455R"]]))

    (update-doc "rakennuksen-muuttaminen"
                [["rakennuksenOmistajat.0.yritys.yhteyshenkilo.henkilotiedot.etunimi" "Orvokki"]
                 ["rakennuksenOmistajat.0.yritys.yhteyshenkilo.henkilotiedot.sukunimi" "Omistaja"]
                 ["rakennuksenOmistajat.0.yritys.yhteyshenkilo.yhteystiedot.puhelin" "12345678"]
                 ["rakennuksenOmistajat.0.yritys.yhteyshenkilo.yhteystiedot.email" "orvokki.omistaja@example.com"]
                 ["rakennuksenOmistajat.0.omistajalaji" "asunto-oy tai asunto-osuuskunta"]])

    (fact "Remove other owner documents"
      (doseq [i [1 2 3]]
        (command apikey :remove-document-data
                 :id app-id
                 :collection "documents"
                 :doc (get doc-map "rakennuksen-muuttaminen")
                 :path ["rakennuksenOmistajat" i]) => ok?))
    (update-doc "rakennuksen-muuttaminen" [["kaytto.rakentajaTyyppi", "muu"]
                                           ["luokitus.energialuokka", "A"]
                                           ["luokitus.paloluokka", "P1"]])
    (update-doc "paatoksen-toimitus-rakval" [["henkilotiedot.etunimi" "Tove"]
                                             ["henkilotiedot.sukunimi" "Toimittaja"]
                                             ["osoite.katu" "Toimitustie 8"]
                                             ["osoite.postinumero" "12345"]
                                             ["osoite.postitoimipaikannimi" "Tornio"]])
    (update-doc "hakija-r" [["henkilo.henkilotiedot.etunimi" "Hakki"]
                            ["henkilo.henkilotiedot.sukunimi" "Hakija"]
                            ["henkilo.henkilotiedot.hetu" "260313-990F"]
                            ["henkilo.osoite.katu" "Hakugatan 20"]
                            ["henkilo.osoite.postinumero" "20202"]
                            ["henkilo.osoite.postitoimipaikannimi" "Hanko"]
                            ["henkilo.yhteystiedot.puhelin" "22222222"]
                            ["henkilo.yhteystiedot.email" "hakki.hakija@example.com"]])
    (fact "Remove non-mandatory parties"
      (doseq [s ["paasuunnittelija" "suunnittelija" "maksaja"]]
        (command sonja :remove-doc
                 :id app-id
                 :docId (get doc-map s)
                 :collection "documents") => ok?))
    (fact "Request two statements"
      (command sonja :request-for-statement
               :id app-id
               :functionCode nil
               :selectedPersons [{:id    "516560d6c2e6f603beb85147" ;; from minimal
                                  :email "sonja.sibbo@sipoo.fi"
                                  :name  "Sonja Sibbo"
                                  :text  "Paloviranomainen"}
                                 {:email "stake.holder@example.com"
                                  :name  "Stake Holder"
                                  :text  "Stakeholder"}]
               :saateText "Qing shuo!") => ok?)
    (fact "Sonja gives statement"
      (let [statement-id (->> (query-application sonja app-id)
                              :statements
                              (util/find-first #(= (get-in % [:person :id])
                                                   "516560d6c2e6f603beb85147"))
                              :id)]
        (command sonja :give-statement
                 :id app-id
                 :lang "fi"
                 :statementId statement-id
                 :status "puollettu"
                 :text "All righty then.") => ok?))))

(defn add-attachment
  "Adds attachment to the application. Contents is mainly for logging. Returns attachment id."
  [app-id contents type-group type-id & [target]]
  (let [file-id               (upload-file-and-bind sonja
                                                    app-id
                                                    (merge {:contents contents
                                                            :type     {:type-group type-group
                                                                       :type-id    type-id}}
                                                           (when target
                                                             {:target target})))
        {:keys [attachments]} (query-application sonja app-id)
        {attachment-id :id}   (util/find-first (fn [{:keys [latestVersion]}]
                                                 (= (:originalFileId latestVersion) file-id))
                                               attachments)]
    (fact {:midje/description (str "New attachment: " contents)}
      attachment-id => truthy)
    {:attachment-id attachment-id
     :file-id       file-id}))

(defn add-condition [add-cmd fill-cmd condition]
  (let [changes      (:changes (add-cmd))
        condition-id (-> changes first first last keyword)]
    (fact "Add new condition"
      condition-id => truthy
      (when condition
        (fact "Fill the added condition"
          (fill-cmd condition-id condition)
          => ok?)))
    condition-id))

(defn remove-condition [remove-cmd condition-id]
  (fact "Remove condition"
    (let [removals (:removals (remove-cmd))
          removed-id (-> removals first last keyword)]
      removed-id => condition-id)))

(defn check-conditions [conditions-query kv]
  (fact "Check conditions"
    (conditions-query)
    => (reduce-kv (fn [acc k v]
                    (assoc acc k (if v
                                   {:condition v}
                                   {})))
                  {}
                  (apply hash-map kv))))

(defn add-template-condition [apikey template-id condition]
  (add-condition #(command apikey :save-verdict-template-draft-value
                           :template-id template-id
                           :path [:add-condition]
                           :value true)
                 #(command apikey :save-verdict-template-draft-value
                   :template-id template-id
                   :path [:conditions %1 :condition]
                   :value %2)
                 condition))

(defn remove-template-condition [apikey template-id condition-id]
  (remove-condition #(command apikey :save-verdict-template-draft-value
                              :template-id template-id
                              :path [:conditions condition-id :remove-condition]
                              :value true)
                    condition-id))


(defn check-template-conditions [apikey template-id & kv]
  (check-conditions #(-> (query apikey :verdict-template :template-id template-id)
                         :draft :conditions)
                    kv))

(defn add-verdict-condition [apikey app-id verdict-id condition]
  (add-condition #(command apikey :edit-pate-verdict
                           :id app-id
                           :verdict-id verdict-id
                           :path [:add-condition]
                           :value true)
                 #(command apikey :edit-pate-verdict
                           :id app-id
                           :verdict-id verdict-id
                           :path [:conditions %1 :condition]
                           :value %2)
                 condition))

(defn remove-verdict-condition [apikey app-id verdict-id condition-id]
  (remove-condition #(command apikey :edit-pate-verdict
                           :id app-id
                           :verdict-id verdict-id
                           :path [:conditions condition-id :remove-condition]
                           :value true)
                    condition-id))

(defn check-verdict-conditions [apikey app-id verdict-id & kv]
  (check-conditions #(-> (query apikey :pate-verdict
                                :id app-id
                                :verdict-id verdict-id)
                         :verdict :data :conditions)
                    kv))

(defn edit-verdict [apikey app-id verdict-id path value]
  (let [result (command apikey :edit-pate-verdict :id app-id
                        :verdict-id verdict-id
                        :path (flatten [path])
                        :value value)]
    (fact {:midje/description (format "Edit verdict: %s -> %s" path value)}
      result => no-errors?)
    result))

(defn fill-verdict [apikey app-id verdict-id & kvs]
  (doseq [[k v] (apply hash-map kvs)]
    (edit-verdict apikey app-id verdict-id k v)))

(defn open-verdict [apikey app-id verdict-id]
  (query apikey :pate-verdict :id app-id :verdict-id verdict-id))
