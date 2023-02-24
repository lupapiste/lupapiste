(ns lupapalvelu.verdict-robot.core
  "Pate robot integration message management"
  (:require [lupapalvelu.integrations.messages :as msg]
            [lupapalvelu.location :as location]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.columns :as cols]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.states :as states]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict-robot.schemas
             :refer [Paatos Paivamaarat Katselmus HankkeenVaativuus Vakuus
                     Rakennus Naapuri Lausunto Base PaatosSanoma PoistoSanoma
                     ApplicationOperationLocations OperationLocationsMessage LocationAck
                     Toimija]]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [slingshot.slingshot :refer [throw+]]))

(defn- date-or-nil
  "Local datestring in `Pvm` format or nil (blank or bad input)."
  [datestring]
  (try
    (date/iso-date datestring :local)
    (catch Exception _ nil)))

(defn dict-value
  "Dictionary value for the given kw-path. Options can either have :verdict key or be verdict
  itself. The returned values are transformed into message formats (e.g., timestamp -> `Pvm`). Nil
  is returned for missing and blank values."
  [options kw-path]
  (let [{data :data :as opts} (get options :verdict options)
        path                  (cols/pathify kw-path)
        value                 (get-in data path)
        {schema :schema}      (schema-util/dict-resolve path
                                                        (:dictionary
                                                         (cols/verdict-schema opts)))]
    (assert schema (str "Schema missing for " path))

    (util/pcond-> (cond-> value
                    (:date schema) date-or-nil)
      string? (-> ss/trim ss/blank-as-nil))))

(sc/defn ^:always-validate ->Paivamaarat :- Paivamaarat
  [verdict]
  (into {}
        (for [[k v] (assoc (vc/verdict-dates verdict) :paatos (vc/verdict-date verdict))
              :let [date (date-or-nil v)]
              :when date]
          [(-> k name (str "Pvm") keyword) date])))

(sc/defn ^:always-validate ->Katselmukset :- (sc/maybe [Katselmus])
  [{:keys [verdict application]}]
  (some->> (:tasks application)
           (filter (fn [{:keys [schema-info source]}]
                     (and (util/=as-kw (:name schema-info) "task-katselmus")
                          (util/=as-kw (:subtype schema-info) "review")
                          (= source {:id   (:id verdict)
                                     :type "verdict"}))))
           (map (fn [{:keys [id taskname data]}]
                  {:nimi taskname
                   :tunnus id
                   :laji (-> data :katselmuksenLaji :value)}))
           seq))

(sc/defn ^:always-validate ->HankkeenVaativuus :- (sc/maybe HankkeenVaativuus)
  [options]
  (-> {:vaativuus ({:small       "vähäinen"
                    :medium      "tavanomainen"
                    :large       "vaativa"
                    :extra-large "poikkeuksellisen vaativa"} (util/make-kw (dict-value options :complexity)))
       :selite    (dict-value options :complexity-text)}
      util/strip-nils
      not-empty))

(sc/defn ^:always-validate ->Vakuus :- (sc/maybe Vakuus)
  [options]
  (when (dict-value options :collateral-flag)
    (not-empty (util/assoc-when {}
                                :pvm   (dict-value options :collateral-date)
                                :summa (dict-value options :collateral)
                                :laji  (dict-value options :collateral-type)))))

(sc/defn ^:always-validate ->Rakennukset :- (sc/maybe [Rakennus])
  [options]
  (->> (pdf/verdict-buildings options)
       (filter :show-building)
       (map (fn [{:keys [building-id tag description paloluokka vss-luokka
                         rakennetut-autopaikat kiinteiston-autopaikat autopaikat-yhteensa]}]
              (util/map-values (comp ss/blank-as-nil ss/trim)
                               {:tunnus                building-id
                                :tunniste              tag
                                :selite                description
                                :paloluokka            paloluokka
                                :vssLuokka             vss-luokka
                                :rakennetutAutopaikat  rakennetut-autopaikat
                                :kiinteistonAutopaikat kiinteiston-autopaikat
                                :autopaikatYhteensa    autopaikat-yhteensa})))
       (map util/strip-nils)
       (remove empty?)
       seq))


(defn plans [verdict]
  (let [lang (util/make-kw (cols/language verdict))]
    (->> (vc/verdict-required-plans verdict)
         (map lang)
         seq)))

(sc/defn ^:always-validate ->Toimija :- (sc/maybe Toimija)
  [verdict title-dict :- sc/Keyword name-dict :- sc/Keyword]
  (-> {:nimike (dict-value verdict title-dict)
       :nimi   (dict-value verdict name-dict)}
      util/strip-nils
      not-empty))

(defn verdict-giver [verdict]
  (->Toimija (util/pcond-> verdict
               vc/board-verdict? (assoc :data {:giver (get-in verdict [:references :boardname])}))
             :giver-title :giver))

(sc/defn ^:always-validate ->Paatos :- Paatos
  [{:keys [verdict] :as options}]
  (util/assoc-when {:tunnus       (:id verdict)
                    :paatostieto  (helper/verdict-code-map (or (keyword (vc/verdict-code verdict))
                                                                  :ei-tiedossa))
                    :paatostyyppi (-> verdict :template :giver)
                    :kieli        (cols/language verdict)
                    :paivamaarat  (->Paivamaarat verdict)}
                   :paatoksentekija (verdict-giver verdict)
                   :kasittelija (->Toimija verdict :handler-title :handler)
                   :paatosteksti    (vc/verdict-text verdict)
                   ;; TODO: If this replacement verdict is replaced, we won't report the original verdict!
                   ;; See LPK-6564
                   :korvaaPaatoksen (vc/replaced-verdict-id verdict)
                   :pykala (vc/verdict-section verdict)
                   :perustelut (dict-value options :rationale)
                   :sovelletutOikeusohjeet (dict-value options :legalese)
                   :toimenpidetekstiJulkipanoon (or (dict-value options :bulletin-op-description)
                                                    (dict-value options :operation))
                   :vaaditutTyonjohtajat (vc/verdict-required-foremen verdict)
                   :vaaditutKatselmukset (->Katselmukset options)
                   :vaaditutErityissuunnitelmat (plans verdict)
                   :muutLupaehdot (seq (vc/verdict-required-conditions verdict))
                   :naapurienKuuleminen (dict-value options :neighbors)
                   :hankkeenVaativuus (->HankkeenVaativuus options)
                   :lisaselvitykset (dict-value options :extra-info)
                   :poikkeamiset (dict-value options :deviations)
                   :rakennusoikeus (dict-value options :rights)
                   :kaavanKayttotarkoitus (dict-value options :purpose)
                   :aloittamisoikeusVakuudella (->Vakuus options)
                   :rakennushanke (dict-value options :operation)
                   :osoite (dict-value options :address)
                   :rakennukset (->Rakennukset options)))

(sc/defn ^:always-validate ->Naapuri :- Naapuri
  [{:keys [propertyId status]}]
  {:kiinteistotunnus propertyId
   :pvm              (-> status first :created date-or-nil)
   :kuultu           (boolean (util/find-by-key :state "mark-done" status))})

(sc/defn ^:always-validate ->Lausunto :- (sc/maybe Lausunto)
  [{:keys [given person status]}]
  (when given
    {:lausunnonantaja (ss/join-non-blanks " " (map ss/trim [(:text person) (:name person)]))
     :pvm             (date-or-nil given)
     :lausuntotieto   status}))

(defn tag-label [organization tag]
  (->> organization
       :tags
       (util/find-by-id tag)
       :label
       ss/trim))

(sc/defn ^:always-validate ->Base :- Base
  [application]
  {:versio           2
   :asiointitunnus   (:id application)
   :kiinteistotunnus (:propertyId application)
   :osoite           (ss/trim (:address application))})

(sc/defn ^:always-validate ->PaatosSanoma :- PaatosSanoma
  [{:keys [command application] :as options}]
  (let [organization (force (:organization command))
        array-fn     (fn [array-k item-fn]
                       (some->> (array-k application)
                                (map item-fn)
                                (remove nil?)
                                seq))]
    (util/assoc-when (->Base application)
                     :paatos           (->Paatos options)
                     :naapurit (array-fn :neighbors ->Naapuri)
                     :lausunnot (array-fn :statements ->Lausunto)
                     :menettely (some-> (:tosFunction application)
                                        ss/blank-as-nil
                                        (tiedonohjaus/tos-function-with-name (:id organization))
                                        :name)
                     :avainsanat (array-fn :tags (partial tag-label organization)))))

(sc/defn ^:always-validate ->PoistoSanoma :- PoistoSanoma
  [application verdict-id]
  (assoc (->Base application) :poistettuPaatos verdict-id))

(defn- pate-robot?  [{:keys [application organization]}]
  (-> (org/resolve-organization-scope (:municipality application)
                                      (:permitType application)
                                      @organization)
      :pate :robot boolean))

(defn robot-integration?
  "Robot integration is available if the integration is set for the scope AND the verdict is not legacy AND
  the verdict category is r."
  [{:keys [command verdict]}]
  (and (vc/has-category? verdict :r)
       (not (vc/legacy? verdict))
       (pate-robot? command)))

(defn- base-template [user created]
  {:id           (mongo/create-id)
   :direction    "out"
   :transferType "http"
   :partner      "robot"
   :format       "json"
   :created      created
   :status       "published"
   :initiator    (select-keys user [:id :username])})

(defn- template [{:keys [user created application]} verdict-id]
  (merge (base-template user created)
         {:target      {:id   verdict-id
                        :type "verdict"}
          :application (util/map-values name
                                        (select-keys application [:id :organization]))}))

(defn create-verdict-message
  [{:keys [command verdict] :as options}]
  (msg/save (merge (template command (:id verdict))
                   {:messageType "publish-verdict"
                    :data        (->PaatosSanoma options)})))

(sc/defn ^:always-validate messages
  "Returns list of `Sanoma` instances. Not validated, since older messages may not match the current schema."
  [org-id :- ssc/NonBlankStr
   from   :- ssc/Timestamp
   until  :- (sc/maybe ssc/Timestamp)
   all    :- sc/Bool]
  (let [query  (cond-> {:partner                  "robot"
                        :direction                "out"
                        :created                  (util/assoc-when {$gt from}
                                                                   $lt until)
                        :application.organization org-id}
                 (not all) (assoc :acknowledged nil))]
    (for [{:keys [id data]} (mongo/select :integration-messages query [:id :data])]
      (assoc data :sanomatunnus id))))

(sc/defn ^:always-validate ack-messages
  [org-id  :- ssc/NonBlankStr
   msg-ids :- [ssc/NonBlankStr]]
  (mongo/update-by-query :integration-messages
                         {:_id                      {$in msg-ids}
                          :partner                  "robot"
                          :direction                "out"
                          :application.organization org-id}
                         {$set {:acknowledged (now)}}))


(defn remove-verdict
  "Removes the verdict integration message and adds a removal notification (`PoistoSanoma`). Used as a
  `on-success` function by `delete-pate-verdict` command."
  [{:keys [application data] :as command} _]
  (when (pate-robot? command)
    (when-let [verdict-id (:verdict-id data)]
      (let [query {:partner        "robot"
                   :direction      "out"
                   :messageType    "publish-verdict"
                   :application.id (:id application)
                   :target.id      verdict-id
                   :target.type    "verdict"}]
        (when (pos? (mongo/count :integration-messages query))
          (mongo/remove-many :integration-messages query)
          (msg/save (assoc (template command verdict-id)
                           :messageType "delete-verdict"
                           :data (->PoistoSanoma application verdict-id))))))))

;; ------------------------------
;; Operation locations
;; ------------------------------

(defn resolve-operation-locations
  "Remove nil and acked unchanged locations."
  [{app-id :id :as application} all?]
  (let [ack (when-not all?
              (mongo/by-id :ack-operation-locations app-id))]
    (remove (fn [{:keys [id location]}]
              (or (not location)
                  (when-let [ack-op (get ack (keyword id))]
                    (and (= (:location ack-op) location)
                         (:acknowledged ack-op)))))
            (location/location-operation-list application))))

(defn- auth-check! [user org-id]
  (when-not (usr/user-is-authority-in-organization? user org-id)
    (throw+ [401 "Unauthorized"])))

(sc/defn ^:always-validate operation-locations :- [ApplicationOperationLocations]
  [user
   org-id :- ssc/NonBlankStr
   from   :- ssc/Timestamp
   until  :- (sc/maybe ssc/Timestamp)
   all?    :- sc/Bool]
  (auth-check! user org-id)
  (let [{:keys [scope]} (mongo/by-id :organizations org-id)
        permit-types    (or (some->> scope
                                     (filter (util/fn-> :pate :robot))
                                     (map :permitType)
                                     seq)
                            (throw+ [403 "Forbidden"]))
        query           {:documents.schema-info.op.location.modified (util/assoc-when {$gt from}
                                                                                      $lt until)
                         :organization                               org-id
                         :state                                      {$in states/post-sent-states}
                         :permitType                                 {$in permit-types}}]
    (or (seq (for [{app-id :id :as application} (mongo/select :applications query)
                   :let                         [loc-ops (resolve-operation-locations application all?)]
                   :when                        (seq loc-ops)]
               {:application-id app-id
                :operations     loc-ops}))
        (throw+ [404 "Not found"]))))

(sc/defn ^:always-validate save-operation-locations-integration-message :- OperationLocationsMessage
  [user org-id :- ssc/NonBlankStr data :- [ApplicationOperationLocations]]
  (let [{msg-id :id :as message} (base-template user (now))
        message-data             {:message-id msg-id :data data}]
    (msg/save (assoc message
                     :messageType "operation-locations"
                     :target {:id org-id :type "organization"}
                     :data message-data))
    message-data))

(defn ack-operation-locations [user org-id message-id]
  (auth-check! user org-id)
  (let [data      (or (some-> (mongo/select-one :integration-messages
                                                {:_id          message-id
                                                 :messageType  "operation-locations"
                                                 :target       {:id   org-id
                                                                :type "organization"}
                                                 :acknowledged {$exists false}})
                              ;; Not a typo, but :data within messsage data.
                              :data :data)
                      (throw+ [404 "Not found"]))
        timestamp (now)]
    (msg/mark-acknowledged-and-return message-id timestamp)
    (doseq [{:keys [application-id
                    operations]} data
            :let                 [updates (->> operations
                                               (map (fn [{:keys [id location]}]
                                                      [(keyword id) {:location     location
                                                                     :message-id   message-id
                                                                     :acknowledged timestamp}]))
                                               (into {})
                                               (sc/validate LocationAck))]]
      (mongo/update-by-id :ack-operation-locations application-id {$set updates} :upsert true))))
