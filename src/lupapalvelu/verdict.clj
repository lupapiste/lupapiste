(ns lupapalvelu.verdict
  (:require [clojure.data :refer [diff]]
            [lupapalvelu.action :refer [update-application application->command] :as action]
            [lupapalvelu.appeal-common :as appeal-common]
            [lupapalvelu.application :as app]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.transformations :as doc-transformations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.inspection-summary :as inspection-summary]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.tiedonohjaus :as t]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict-review-util :as verdict-review-util]
            [monger.operators :refer :all]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :refer [fn-> fn->>] :as util]
            [sade.xml :as xml]
            [schema.core :refer [defschema] :as sc]
            [swiss.arrows :refer :all]
            [taoensso.timbre :refer [debug debugf info infof warn warnf error errorf]]))

(def Timestamp sc/Num) ;; Some timestamps are casted as double during mongo export

(defschema Katselmus
  {:katselmuksenLaji (sc/if ss/numeric? ssc/NatString (apply sc/enum tasks/task-types)) ;; TODO: cleanup numeric values
   (sc/optional-key :tarkastuksenTaiKatselmuksenNimi) sc/Str})

(defschema Maarays
  "Schema for additional requirements for verdict."
  {(sc/optional-key :sisalto)       sc/Str
   (sc/optional-key :maaraysPvm)    ssc/Timestamp
   (sc/optional-key :maaraysaika)   Timestamp
   (sc/optional-key :toteutusHetki) ssc/Timestamp})

(defschema Status
  "Schema for verdict status"
  (sc/if integer? ssc/Nat ssc/NatString))

(defschema Liite
  {:kuvaus sc/Str
   :tyyppi sc/Str})

(defschema Poytakirja
  "Schema for verdict record."
  {(sc/optional-key :paatoksentekija) (sc/maybe sc/Str)
   (sc/optional-key :paatoskoodi)     (sc/maybe sc/Str) ;; (apply sc/enum verdict-codes), data contains invalid values: nil "Peruutettu" "14" "annettu lausunto (ent. selitys)" "1" "lausunto/p\u00e4\u00e4tu00f6s (muu kuin rlk)" "11"
   (sc/optional-key :status)          (sc/maybe Status)
   (sc/optional-key :urlHash)         sc/Str
   (sc/optional-key :paatos)          (sc/maybe sc/Str)
   (sc/optional-key :paatospvm)       (sc/maybe Timestamp)
   (sc/optional-key :pykala)          (sc/maybe sc/Str)
   (sc/optional-key :liite)           Liite})

(defschema Paatos
  "Schema for single verdict fetched from backing system."
  {:id                               ssc/ObjectIdStr
   :poytakirjat                      [Poytakirja]
   (sc/optional-key :lupamaaraykset) {(sc/optional-key :maaraykset)                     [Maarays]
                                      (sc/optional-key :vaaditutKatselmukset)           [Katselmus]
                                      (sc/optional-key :vaaditutErityissuunnitelmat)    [sc/Str]
                                      (sc/optional-key :vaaditutTyonjohtajat)           sc/Str
                                      (sc/optional-key :vaadittuTyonjohtajatieto)       [sc/Str]
                                      (sc/optional-key :muutMaaraykset)                 [(sc/maybe sc/Str)]
                                      (sc/optional-key :autopaikkojaEnintaan)           ssc/Nat
                                      (sc/optional-key :autopaikkojaVahintaan)          ssc/Nat
                                      (sc/optional-key :autopaikkojaRakennettava)       ssc/Nat
                                      (sc/optional-key :autopaikkojaRakennettu)         ssc/Nat
                                      (sc/optional-key :autopaikkojaKiinteistolla)      ssc/Nat
                                      (sc/optional-key :autopaikkojaUlkopuolella)       ssc/Nat
                                      (sc/optional-key :takuuaikaPaivat)                ssc/NatString
                                      (sc/optional-key :kerrosala)                      sc/Str
                                      (sc/optional-key :kokonaisala)                    sc/Str
                                      (sc/optional-key :rakennusoikeudellinenKerrosala) sc/Str}
   (sc/optional-key :paivamaarat)    {(sc/optional-key :anto)                           (sc/maybe Timestamp)
                                      (sc/optional-key :lainvoimainen)                  (sc/maybe Timestamp)
                                      (sc/optional-key :aloitettava)                    Timestamp
                                      (sc/optional-key :voimassaHetki)                  Timestamp
                                      (sc/optional-key :viimeinenValitus)               Timestamp
                                      (sc/optional-key :raukeamis)                      Timestamp
                                      (sc/optional-key :paatosdokumentinPvm)            Timestamp
                                      (sc/optional-key :julkipano)                      Timestamp}})

(defschema Signature
  {:created ssc/Timestamp
   :user    usr/SummaryUser})

(defschema Verdict
  "Schema for verdict wrapper for verdicts with same kuntalupatunnus."
  {:id                           ssc/ObjectIdStr
   :kuntalupatunnus              (sc/maybe sc/Str)
   :timestamp                    (sc/maybe ssc/Timestamp)
   (sc/optional-key :source)     (sc/enum "ah")
   (sc/optional-key :draft)      sc/Bool
   (sc/optional-key :sopimus)    (sc/maybe sc/Bool)
   :paatokset                    [Paatos]
   (sc/optional-key :signatures) [Signature]
   (sc/optional-key :metadata)   (sc/eq nil)})

(defn- backend-id->verdict [backend-id]
  {:id              (mongo/create-id)
   :kuntalupatunnus backend-id
   :timestamp       nil
   :paatokset       []
   :draft           true})

(defn verdict-tab-action? [{action-name :action}]
  (boolean (#{:publish-verdict :check-for-verdict :process-ah-verdict :fetch-verdicts} (keyword action-name))))

(defn- get-poytakirja!
  "Fetches the verdict attachments listed in the verdict xml. If the
  fetch is successful, uploads and attaches them to the
  application. Returns pk (with urlHash assoced if upload and attach
  was successful).

  At least outlier verdicts (KT) poytakirja can have multiple
  attachments. On the other hand, traditional (e.g., R) verdict
  poytakirja can only have one attachment."
  [application user timestamp verdict-id pk & options]
  (apply verdict-review-util/get-poytakirja! application user timestamp {:type "verdict" :id verdict-id} pk options))

(defn- verdict-attachments [application user timestamp verdict]
  {:pre [application]}
  (when (:paatokset verdict)
    (let [verdict-id (mongo/create-id)]
      (-> (assoc verdict :id verdict-id, :timestamp timestamp)
          (update :paatokset
                  (fn->> (map #(update % :poytakirjat (partial map (partial get-poytakirja! application user timestamp verdict-id))))
                         (map #(assoc % :id (mongo/create-id)))
                         (filter seq)))))))

(defn- get-verdicts-with-attachments [application user timestamp xml reader & reader-args]
  (->> (apply krysp-reader/->verdicts xml (:permitType application) reader reader-args)
       (map (partial verdict-attachments application user timestamp))
       (filter seq)))


(defn- get-app-descriptions [{:keys [permitType]} xml]
  (krysp-reader/read-permit-descriptions-from-xml permitType (cr/strip-xml-namespaces xml)))

(defn- get-task-updates [application user created verdicts app-xml]
  {$push {:tasks {$each (-> (assoc application
                                   :verdicts verdicts
                                   :buildings (building-reader/->buildings-summary app-xml))
                            (tasks/verdicts->tasks user created))}}})

(defn find-verdicts-from-xml
  "Returns a monger update map"
  ([command app-xml]
    (find-verdicts-from-xml command app-xml true))
  ([{:keys [application user created organization] :as command} app-xml update-state?]
   {:pre [(every? command [:application :user :created]) app-xml]}
   (let [organization (if organization @organization (org/get-organization (:organization application)))]
     (when-let [verdicts-with-attachments (seq (get-verdicts-with-attachments application user created app-xml permit/read-verdict-xml organization))]
       (inspection-summary/process-verdict-given application)
       (util/deep-merge
         {$set {:verdicts verdicts-with-attachments, :modified created}}
         (get-task-updates application user created verdicts-with-attachments app-xml)
         (permit/read-verdict-extras-xml application app-xml)
         (when (and update-state? (not (states/post-verdict-states (keyword (:state application)))))
           (app-state/state-transition-update (sm/verdict-given-state application) created application user)))))))

(defn find-tj-suunnittelija-verdicts-from-xml
  [{:keys [application user created] :as command} doc app-xml osapuoli-type target-kuntaRoolikoodi]
  {:pre [(every? command [:application :user :created]) app-xml]}
  (when-let [verdicts-with-attachments (seq (get-verdicts-with-attachments application user created app-xml permit/read-tj-suunnittelija-verdict-xml doc osapuoli-type target-kuntaRoolikoodi))]
    (util/deep-merge
     (app-state/state-transition-update (sm/verdict-given-state application) created application user)
     {$set {:verdicts verdicts-with-attachments}})))

(defn- get-tj-suunnittelija-doc-name
  "Returns name of first party document of operation"
  [operation-name]
  (let [operation (get operations/operations (keyword operation-name))
        schemas (cons (:schema operation) (:required operation))]
    (some
      #(when
         (= :party
           (keyword
             (get-in (schemas/get-schema {:name %}) [:info :type])))
         %)
      schemas)))

;; Trimble writes verdict for tyonjohtaja/suunnittelija applications to their link permits.
(defn fetch-tj-suunnittelija-verdict [{{:keys [municipality permitType] :as application} :application :as command}]
  (let [application-op-name (-> application :primaryOperation :name)
        organization (organization/resolve-organization municipality permitType)
        krysp-version (get-in organization [:krysp (keyword permitType) :version])]
    (when (and
            (#{"tyonjohtajan-nimeaminen-v2" "tyonjohtajan-nimeaminen" "suunnittelijan-nimeaminen"} application-op-name)
            (util/version-is-greater-or-equal krysp-version {:major 2 :minor 1 :micro 8}))
      (let [application (meta-fields/enrich-with-link-permit-data application)
            link-permit (first (app/get-link-permit-apps application))
            link-permit-xml (krysp-fetch/get-application-xml-by-application-id link-permit)
            osapuoli-type (cond
                            (or (= "tyonjohtajan-nimeaminen" application-op-name) (= "tyonjohtajan-nimeaminen-v2" application-op-name)) "tyonjohtaja"
                            (= "suunnittelijan-nimeaminen" application-op-name) "suunnittelija")
            doc-name (get-tj-suunnittelija-doc-name application-op-name)
            doc (tools/unwrapped (domain/get-document-by-name application doc-name))
            target-kuntaRoolikoodi (get-in doc [:data :kuntaRoolikoodi])]
        (when (and link-permit-xml osapuoli-type doc target-kuntaRoolikoodi)
          (or
            (krysp-reader/tj-suunnittelija-verdicts-validator doc link-permit-xml osapuoli-type target-kuntaRoolikoodi)
            (let [updates (find-tj-suunnittelija-verdicts-from-xml command doc link-permit-xml osapuoli-type target-kuntaRoolikoodi)]
              (action/update-application command updates)
              (ok :verdicts (get-in updates [$set :verdicts])))))))))

(defn special-foreman-designer-verdict?
  "Some verdict providers handle foreman and designer verdicts a bit
  differently. These 'special' verdicts contain reference permit id in
  MuuTunnus. xml should be without namespaces"
  [application xml]
  (let [op-name (-> application :primaryOperation :name)
        link-permit-id (-> application :linkPermitData first :id)]
    (and (#{"tyonjohtajan-nimeaminen-v2" "tyonjohtajan-nimeaminen" "suunnittelijan-nimeaminen"} op-name)
         (not-empty (enlive/select xml [:luvanTunnisteTiedot :MuuTunnus :tunnus (enlive/text-pred #(= link-permit-id %))])))))

(defn- get-personal-information-value [personal-info key]
  (-> personal-info key :value))

(defn- match-xml-text-to-doc-info
  "Matches party-xml to document information. If doc-key is nimi, then checks if both first name and last name match
  and checks if both names are in last name field. If xml-field is missing, returns nil, otherwise true or false."
  [personal-info doc-key xml-key party]
  (if (= :nimi doc-key)
    (let [doc-etunimi (get-personal-information-value personal-info :etunimi)
          doc-sukunimi (get-personal-information-value personal-info :sukunimi)
          xml-etunimi (xml/get-text party [:etunimi])
          xml-sukunimi (xml/get-text party [:sukunimi])]
      (when-not (nil? xml-sukunimi)
        (or (and (= doc-etunimi xml-etunimi)
                 (= doc-sukunimi xml-sukunimi))
            (= (str doc-sukunimi " " doc-etunimi) xml-sukunimi))))
    (let [xml-text (xml/get-text party [xml-key])
          doc-text (get-personal-information-value personal-info doc-key)]
      (when (every? ss/not-blank? [xml-text doc-text])
        (= xml-text doc-text)))))

(defn- verdict-party-finder
  "Hetu is not always available for verdict matching, so it is necessary to compare other attributes to document data sometimes.
  This function compares role, hetu, email, name and telephone number and returns the match if found."
  [roolikoodi henkilotiedot-from-doc parties-from-xml]
  (let [rooli-filtered-parties (filter (fn [party] (or (= roolikoodi (xml/get-text party [:tyonjohtajaRooliKoodi]))
                                                       (= roolikoodi (xml/get-text party [:suunnittelijaRooliKoodi]))))
                                       parties-from-xml)]
    (loop [info-fields      {:hetu    :henkilotunnus
                             :email   :sahkopostiosoite
                             :nimi    [:etunimi :sukunimi]
                             :puhelin :puhelin}
           parties          rooli-filtered-parties
           possible-matches []]
      (cond
        (or (empty? info-fields)
            (and (empty? parties) (empty? possible-matches)))
        nil

        (empty? parties)
        (recur (rest info-fields) possible-matches [])

        :else
        (let [[doc-key xml-key] (first info-fields)
              party             (first parties)
              match?            (match-xml-text-to-doc-info henkilotiedot-from-doc doc-key xml-key party)]
          (cond
            (nil? match?) (recur info-fields (rest parties) (conj possible-matches party))
            match?        party
            (not match?)  (recur info-fields (rest parties) possible-matches)))))))

(defn verdict-xml-with-foreman-designer-verdicts
  "Normalizes special foreman/designer verdict by creating a proper
  paatostieto. Takes data from foreman/designer's party details. The
  resulting paatostieto element overrides old one. Returns the xml
  with paatostieto.
  Note: This must only be called with special verdict xml (see above)"
  [application xml]
  (let [op-name      (-> application :primaryOperation :name)
        tag          (if (ss/starts-with op-name "tyonjohtajan-") :Tyonjohtaja :Suunnittelija)
        op-doc       (domain/get-document-by-operation application (-> application :primaryOperation :id))
        henk-tiedot  (-> op-doc :data :henkilotiedot)
        yht-tiedot   (-> op-doc :data :yhteystiedot)
        roolikoodi   (-> op-doc :data :kuntaRoolikoodi :value)
        parties      (enlive/select xml [tag])
        party        (verdict-party-finder roolikoodi (merge henk-tiedot yht-tiedot) parties)
        attachment   (-> party (enlive/select [:liitetieto :Liite]) first enlive/unwrap)
        date         (xml/get-text party [:paatosPvm])
        decision     (xml/get-text party [:paatostyyppi])
        verdict-xml  [{:tag :Paatos
                       :content [{:tag :poytakirja
                                  :content [{:tag :paatoskoodi :content [decision]}
                                            {:tag :paatoksentekija :content [""]}
                                            {:tag :paatospvm :content [date]}
                                            {:tag :liite :content attachment}]}]}]
        paatostieto  {:tag :paatostieto :content verdict-xml}
        placeholders #{:paatostieto :muistiotieto :lisatiedot
                       :liitetieto  :kayttotapaus :asianTiedot}
        [rakval]     (enlive/select xml [:RakennusvalvontaAsia])
        place        (some #(placeholders (:tag %)) (:content rakval))]
    (case place
      :paatostieto (enlive/at xml [:RakennusvalvontaAsia :paatostieto] (enlive/content verdict-xml))
      nil          (enlive/at xml [:RakennusvalvontaAsia] (enlive/append paatostieto))
      (enlive/at xml [:RakennusvalvontaAsia place] (enlive/before paatostieto)))))

(defn- normalize-special-verdict
  "Normalizes special foreman/designer verdicts by
  creating a traditional paatostieto element from the proper special
  verdict party.
    application: Application that requests verdict.
    app-xml:     Verdict xml message
  Returns either normalized app-xml (without namespaces) or app-xml if
  the verdict is not special."
  [application app-xml]
  (let [xml (cr/strip-xml-namespaces app-xml)]
    (if (special-foreman-designer-verdict? (meta-fields/enrich-with-link-permit-data application) xml)
      (verdict-xml-with-foreman-designer-verdicts application xml)
      app-xml)))

(defn- verdict-task?
  "True if given task is 'rooted' via source chain to the verdict.
   tasks: tasks of the application
   verdict-id: Id of the target verdict
   task: task to be analyzed."
  [tasks verdict-id {{source-type :type source-id :id} :source}]
  (case (keyword source-type)
    :verdict (= verdict-id source-id)
    :task (verdict-task? tasks verdict-id (some #(when (= (:id %) source-id) %) tasks))
    false))

(defn deletable-verdict-task-ids
  "Task ids that a) can be deleted and b) belong to the
  verdict with the given id."
  [{:keys [tasks]} verdict-id]
  (->> tasks
       (filter #(and (not= (-> % :state keyword) :sent)
                     (verdict-task? tasks verdict-id %)))
       (map :id)))

(defn task-ids->attachments
  "All the attachments that belong to the tasks with the given ids."
  [application task-ids]
  (->> task-ids
       (map (partial tasks/task-attachments application))
       flatten))

(defn delete-verdict
  "Deletes given verdict. If rewind? is true, the application state is
  rewound to the previous state if there are no logner vericts afte
  the deletion."
  [{:keys [application created user] :as command} {verdict-id :id :as verdict} rewind?]
  (let [target                        {:type "verdict" :id verdict-id} ; key order seems to be significant!
        is-verdict-attachment?        #(= (select-keys (:target %) [:id :type]) target)
        attachments                   (filter is-verdict-attachment? (:attachments application))
        {:keys [sent state verdicts]} application
        ;; Deleting the only given verdict? Return sent or submitted state.
        step-back?                    (and rewind?
                                           (not (:draft verdict))
                                           (= 1 (count (remove :draft verdicts)))
                                           (empty? (filter :published (:pate-verdicts application)))
                                           (states/verdict-given-states (keyword state)))
        task-ids                      (deletable-verdict-task-ids application verdict-id)
        attachments                   (concat attachments (task-ids->attachments application task-ids))
        updates                       (merge {$pull {:verdicts {:id verdict-id}
                                                     :comments {:target target}
                                                     :tasks    {:id {$in task-ids}}}}
                                             (when step-back?
                                               (app-state/state-transition-update (if (and sent (sm/valid-state? application :sent))
                                                                                    :sent
                                                                                    :submitted)
                                                                                  created
                                                                                  application
                                                                                  user)))]
      (update-application command updates)
      (bulletins/process-delete-verdict (:id application) verdict-id)
      (attachment/delete-attachments! application (remove nil? (map :id attachments)))
      (appeal-common/delete-by-verdict command verdict-id)
      (child-to-attachment/delete-child-attachment application :verdicts verdict-id)
      (when step-back?
        (notifications/notify! :application-state-change command))))

(defn- replace-backing-system-verdicts-from-xml
  "Saves verdict's from valid app-xml to application. Returns (ok) with
  updated verdicts and tasks. Note: nukes the old backing system
  verdicts (including the corresponding tasks, attachemnts and
  appeals)."
  [{:keys [application] :as command} app-xml]
  (doseq [verdict (:verdicts application)]
    (delete-verdict command verdict false))
  (let [updates (find-verdicts-from-xml command app-xml)
        app-descriptions (get-app-descriptions application app-xml)
        verdicts (get-in updates [$set :verdicts])]
    (when updates
      (let [doc-updates (doc-transformations/get-state-transition-updates command (sm/verdict-given-state application))]
        (update-application command (:mongo-query doc-updates) (util/deep-merge (:mongo-updates doc-updates) updates))
        (bulletins/process-check-for-verdicts-result command verdicts app-descriptions)
        (t/mark-app-and-attachments-final! (:id application) (:created command))))
    (ok :verdicts verdicts
        :tasks (get-in updates [$push :tasks])
        :state (get-in updates [$set :state] (:state application)))))

(defn backend-id-mongo-updates
  [{verdicts :verdicts} backend-ids]
  (some->> backend-ids
           (remove (set (map :kuntalupatunnus verdicts)))
           (map backend-id->verdict)
           ; Need to force realization here for testing purposes
           vec
           (assoc-in {} [$push :verdicts $each])))

(defn validate-section-requirement
  "Validator that fails if the organization requires section (pykala)
  in verdicts and app-xml is missing one (muutoslupa permits are
  excluded from validation) Note: besides organization, the
  requirement is also operation-specific. The requirement is fulfilled
  if _any_ paatostieto element contains at least one non-blank
  pykala."
  [{:keys [primaryOperation permitSubtype]} app-xml {section :section}]
  (let [{:keys [enabled operations]} section]
    (when (and enabled
               (util/not=as-kw permitSubtype :muutoslupa)
               (contains? (set operations) (:name primaryOperation))
               (not (some-<> app-xml
                             cr/strip-xml-namespaces
                             (xml/select [:paatostieto :pykala])
                             not-empty
                             (some (util/fn-> :content first ss/not-blank?) <>))))
      (fail :info.section-required-in-verdict))))

(defn do-check-for-verdict [{:keys [application organization] :as command}]
  {:pre [(every? command [:application :user :created])]}
  (if-let [app-xml (or (krysp-fetch/get-application-xml-by-application-id application)
                         ;; LPK-1538 If fetching with application-id fails try to fetch application with first to find backend-id
                         (krysp-fetch/get-application-xml-by-backend-id application (some :kuntalupatunnus (:verdicts application))))]
    (let [app-xml          (normalize-special-verdict application app-xml)
          organization     (if organization @organization (org/get-organization (:organization application)))
          validation-error (or (permit/validate-verdict-xml (:permitType application) app-xml organization)
                               (validate-section-requirement application
                                                             app-xml
                                                             organization))]
      (if-not validation-error
        (replace-backing-system-verdicts-from-xml command app-xml)
        (let [extras-updates     (permit/read-verdict-extras-xml application app-xml)
              backend-id-updates (->> (seq (krysp-reader/->backend-ids app-xml))
                                      (backend-id-mongo-updates application))]
          (some->> (util/deep-merge extras-updates backend-id-updates) (update-application command))
          validation-error)))
    ;; LPK-2459
    (when (or (foreman/foreman-app? application) (app/designer-app? application))
      (debug "Checking foreman/designer verdict...")
      (fetch-tj-suunnittelija-verdict command))))

(defn get-state-updates [user created {current-state :state :as application} app-xml]
  (let [new-state (->> (cr/strip-xml-namespaces app-xml)
                       (krysp-reader/application-state)
                       krysp-reader/krysp-state->application-state)]
    (cond
      (nil? new-state) nil
      (sm/can-proceed? application new-state)  (app-state/state-transition-update new-state created application user)
      (not= new-state (keyword current-state)) (errorf "Invalid state transition. Failed to update application %s state from '%s' to '%s'."
                                                       (:id application) current-state (name new-state)))))

(defmethod attachment/edit-allowed-by-target :verdict [{user :user application :application}]
  (when-not (auth/application-authority? application user)
    (fail :error.unauthorized)))

;; Notifications

(defn state-change-email-model
  "Generic state change email. :state-text is set per application state.
  When state changes and if notify is invoked as post-fn from command,
  result must contain new state in :state key."
  [command conf recipient]
  (assoc
   (notifications/create-app-model command conf recipient)
   :state-text #(i18n/localize % "email.state-description" (get-in command [:application :state]))))

(def state-change {:subject-key    "state-change"
                   :template       "application-state-change.md"
                   :application-fn (fn [{id :id}] (domain/get-application-no-access-checking id))
                   :tab-fn         (fn [command] (cond (verdict-tab-action? command) "verdict"))
                   :model-fn       state-change-email-model})

(notifications/defemail :application-state-change state-change)

(notifications/defemail :undo-cancellation
                        {:subject-key    "undo-cancellation"
                         :application-fn (fn [{id :id}] (domain/get-application-no-access-checking id))
                         :model-fn       (fn [command conf recipient]
                                           (assoc (notifications/create-app-model command conf recipient)
                                             :state-text #(i18n/localize % "email.state-description.undoCancellation")))})


;; Fetch missing verdict attachments

(defn- missing-verdict-attachments-query [{:keys [start end organizations]}]
  (merge {:verdicts {$elemMatch {$and [{:timestamp {$gt start
                                                    $lt end}
                                        :permitType {$nin ["ARK"]}}
                                       {$or [{:paatokset.poytakirjat {$size 0}}
                                             {:paatokset.poytakirjat.urlHash {$exists false}}]}]}}}
         (when (not-empty organizations)
           {:organization {$in organizations}})))

(defn applications-with-missing-verdict-attachments
  "Options:
   - start:         consider verdicts with timestamp > start
   - end:           consider verdicts with timestamp < end
   - organizations: when defined, only consider given organizations"
  [options]
  (mongo/select :applications
                (missing-verdict-attachments-query options)
                [:organization :permitType]))

(defn get-verdicts-from-xml [application organization xml]
  (krysp-reader/->verdicts xml (:permitType application) permit/read-verdict-xml organization))

(defn matching-verdict
  "Return a verdict with a matching kuntalupatunnus from verdicts"
  [verdict verdicts]
  (util/find-first #(= (:kuntalupatunnus %)
                       (:kuntalupatunnus verdict))
                   verdicts))

(defn- verdict-in-application-without-attachment?
  "Is the verdict from xml present in the application without an attachment?"
  [application verdict-from-xml]
  (boolean
   (when-let [existing-verdict (matching-verdict verdict-from-xml
                                                 (:verdicts application))]
     (let [url-hashes (->> existing-verdict
                           :paatokset
                           (map :poytakirjat)
                           (mapcat (partial map :urlHash)))]
       (or (empty? url-hashes)
           (some empty? url-hashes))))))



(defn- matching-poytakirja
  "Returns the first element from update-pks that has equal values for
  keys that 1) are in match keys and 2) are present in app-pk"
  [app-pk match-keys update-pks]
  (let [app-pk-match (select-keys app-pk match-keys)
        ;; Only compare the keys that are actually present in the poytakirja
        app-pk-match-keys (keys app-pk-match)]
    (util/find-first #(= app-pk-match
                         (select-keys % app-pk-match-keys))
                     update-pks)))


(defn- poytakirja-matches
  "Return a sequence of pairs of matching poytakirja's such that the
  first one is from the existing application and the second one, if
  any, is from the update"
  [app-paatos update-paatos match-keys]
  (map (juxt identity
             #(matching-poytakirja %
                                   match-keys
                                   (:poytakirjat update-paatos)))
       (:poytakirjat app-paatos)))

(defn- poytakirja-update [[app-pk update-pk]]
  (cond (nil? update-pk)        {:action :keep   :pk app-pk}
        (:urlHash app-pk)       {:action :keep   :pk app-pk}
        (or (:liite update-pk)
            (:Liite update-pk)) {:action :update :pk update-pk}
        :else                   {:action :keep   :pk app-pk}))

(defn- update-poytakirja-entries!
  [app-paatos update-paatos application user timestamp app-verdict-id]
  (let [update-actions (->> (poytakirja-matches app-paatos update-paatos
                                                [:paatoskoodi :paatospvm :paatoksentekija :pykala :status])
                            (map poytakirja-update))]
    (assoc app-paatos
           :poytakirjat
           (doall
            (for [{:keys [action pk]} update-actions]
              (case action
                :keep   pk
                :update (get-poytakirja! application
                                         user
                                         timestamp
                                         app-verdict-id
                                         pk
                                         :set-app-modified? false)
                pk))))))

(defn- add-verdict-attachments!
  "When available, add a new verdict attachments (note, side effect)
  and return an updated verdict with information on the added
  attachments in relevant :poytakirjat arrays."
  [application user timestamp verdicts-from-xml app-verdict]
  (if-let [update-verdict (matching-verdict app-verdict
                                            verdicts-from-xml)]
    ;; There is no straightforward way to match :paatokset elements,
    ;; but this should not be a problem, since all the verdicts in
    ;; production contain only one element in :paatokset.
    (if (= (count (:paatokset app-verdict))
           (count (:paatokset update-verdict)))
      (assoc app-verdict
             :paatokset
             (map #(update-poytakirja-entries! %1
                                               %2
                                               application
                                               user timestamp
                                               (:id app-verdict))
                  (:paatokset app-verdict)
                  (:paatokset update-verdict)))
      (do (warn "Cannot match :paatokset elements")
          app-verdict))
    app-verdict))

(defn- store-verdict-updates!
  "Store the updated verdicts to mongo. Return the ones that were
  actually updated."
  [{:keys [application] :as command} updated-verdicts]
  (when (not= (:verdicts application) updated-verdicts)
    (info (str "Updating verdicts for application " (:id application)))
    (action/update-application command
                               {$set {:verdicts updated-verdicts}}))
  (->> updated-verdicts
       (map vector (:verdicts application))
       (filter (partial apply not=))
       (map (juxt (comp :id first)
                  (comp second
                        (partial apply diff))))
       (into {})))

;; Having application and verdict xml, update application verdicts:
;; 1. get verdicts from xml
;; 2. only consider verdicts that
;; - are already present in the application
;; - do not have poytakirja attachments
;; 3. update the verdicts on application that have a new verdict attachments
;; - add verdict attachment with get-poytakirja!
;; - update verdict entry
;; 4. store the updates to mongo
(defn update-verdict-attachments-from-xml!
  "Update the verdict's poytakirja attachments from the xml, return
  updated verdicts"
  [{:keys [application organization user created] :as command} app-xml]
  (let [organization    (if organization
                          @organization
                          (org/get-organization (:organization application)))
        update-verdicts (->> (get-verdicts-from-xml application
                                                    organization app-xml)
                             (filter (partial verdict-in-application-without-attachment?
                                              application)))]
    (->> (:verdicts application)
         (map (partial add-verdict-attachments! application user created update-verdicts))
         (store-verdict-updates! command)
         (ok :id (:id application) :updated-verdicts))))
