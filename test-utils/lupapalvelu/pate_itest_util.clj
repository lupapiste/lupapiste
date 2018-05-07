(ns lupapalvelu.pate-itest-util
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.util :as util]))


(defn no-errors?
  "Checks PATE api response and ensures request didn't contain errors"
  [{:keys [errors ok]}]
  (and (nil? errors) (true? ok)))

(defn invalid-value? [{:keys [errors]}]
  (util/=as-kw (-> errors first last) :error.invalid-value))


(defn init-verdict-template [as-user category]
  (let [{id :id :as template} (command as-user :new-verdict-template :category category)]
    template))

(defn set-template-draft-value [template-id path value]
  (fact {:midje/description (format "Draft value: %s %s"
                                    path value)}
        (command sipoo :save-verdict-template-draft-value
                 :template-id template-id
                 :path (map keyword (flatten [path]))
                 :value value) => ok?))

(defn set-template-draft-values [template-id & args]
  (doseq [[path value] (->arg-map args)]
    (set-template-draft-value template-id path value)))

(defn publish-verdict-template [as-user id]
  (command as-user :publish-verdict-template :template-id id))

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
