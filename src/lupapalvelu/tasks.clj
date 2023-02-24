(ns lupapalvelu.tasks
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.migration.foreman-role-mapping :as foreman-role-mapping]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.review-pdf :as review-pdf]
            [lupapalvelu.task-util :as task-util]
            [monger.operators :refer :all]
            [sade.core :refer [def- fail now]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :refer [errorf]]))

(def task-schemas-version task-util/task-schemas-version)

(def task-types ["muu katselmus"
                 "muu tarkastus"
                 "aloituskokous"
                 "rakennuksen paikan merkitseminen"
                 "rakennuksen paikan tarkastaminen"
                 "pohjakatselmus"
                 "rakennekatselmus"
                 "l\u00e4mp\u00f6-, vesi- ja ilmanvaihtolaitteiden katselmus"
                 "osittainen loppukatselmus"
                 "loppukatselmus"
                 "ei tiedossa"])

(def task-states #{:requires_user_action :requires_authority_action :ok :sent :faulty_review_task})

(defn all-states-but [& states]
  (apply disj task-states states))

(defn deny-removal-of-vaadittu-lupaehtona [{:keys [application] {task-id :taskId} :data}]
  (when-let [task (util/find-by-id task-id (:tasks application))]
    (when (true? (get-in task [:data :vaadittuLupaehtona :value]))
      (fail :error.task-is-required))))

(def task-is-review? task-util/task-is-review?)

(defn background-source? [{:keys [source]}]
  (= (:type source) "background"))

(def- katselmuksenLaji
  {:name "katselmuksenLaji"
   :type :select :sortBy :displayname
   :css [:dropdown]
   :required true
   :readonly true
   :whitelist {:roles [:authority] :otherwise :disabled}
   :default "illegal"
   :body (mapv (partial hash-map :name) task-types)})

(def- katselmuksenLaji-ya
  {:name "katselmuksenLaji"
   :type :select :sortBy :displayname
   :css [:dropdown]
   :required true
   :whitelist {:roles [:authority] :otherwise :disabled}
   :body [{:name "Aloituskatselmus"}
          {:name "Loppukatselmus"}
          {:name "Muu valvontak\u00e4ynti"}]})

(def- task-katselmus-body
  [katselmuksenLaji
   {:name "vaadittuLupaehtona"
    :type :checkbox
    :inputType :check-string
    :whitelist {:roles [:authority] :otherwise :disabled}
    :i18nkey "vaadittuLupaehtona"}
   {:name "rakennus"
    :type :group
    :whitelist {:roles [:authority] :otherwise :disabled}
    :repeating true
    :repeating-init-empty true
    :uicomponent :docgen-review-buildings
    :body [{:name "rakennus" :type :group :body [{:name "jarjestysnumero" :type :string :hidden true}
                                                 {:name "valtakunnallinenNumero" :type :string :hidden true}
                                                 {:name "rakennusnro" :type :string :hidden true}
                                                 {:name "kiinttun" :type :string :hidden true}
                                                 {:name "kunnanSisainenPysyvaRakennusnumero" :type :string :hidden true}]}
           {:name "tila" :type :group :i18nkey "empty"
            :body [{:name "tila" :type :select :css [:dropdown] :sortBy :displayname
                    :whitelist {:roles [:authority] :otherwise :disabled}
                    :i18nkey "task-katselmus.katselmus.tila"
                    :body [{:name "osittainen"} {:name "lopullinen"}]}
                   {:name "kayttoonottava"
                    :i18nkey "task-katselmus.rakennus.tila.kayttoonottava"
                    :type :checkbox :inputType :checkbox-wrapper
                    :auth {:enabled [:is-end-review :is-other-review]}
                    :whitelist {:roles [:authority] :otherwise :disabled}}
                   ]}]}
   {:name "katselmus" :type :group
    :whitelist {:roles [:authority] :otherwise :disabled}
    :body
    [{:name "tila" :type :select :css [:dropdown :form-input] :sortBy :displayname
      :whitelist {:roles [:authority] :otherwise :disabled}
      :required true
      :body [{:name "osittainen"} {:name "lopullinen"}]}
     {:name "pitoPvm" :type :date :required true
      :whitelist {:roles [:authority] :otherwise :disabled}}
     {:name "pitaja" :type :review-officer-dropdown
      :whitelist {:roles [:authority] :otherwise :disabled}
      :dynamic true}
     {:name "tiedoksianto" :type :checkbox :inputType :checkbox-wrapper
      :whitelist {:roles [:authority] :otherwise :disabled}
      :auth {:enabled [:is-end-review]}}
     {:name "huomautukset" :type :group
      :body [{:name "kuvaus" :type :text :max-len 40000 :css []
              :whitelist {:roles [:authority] :otherwise :disabled}
              :auth {:disabled [:is-faulty-review]}}
             {:name "maaraAika" :type :date
              :whitelist {:roles [:authority] :otherwise :disabled}
              :auth {:disabled [:is-faulty-review]}}
             {:name "toteaja" :type :string
              :whitelist {:roles [:authority] :otherwise :disabled}
              :auth {:disabled [:is-faulty-review]}}
             {:name "toteamisHetki" :type :date
              :whitelist {:roles [:authority] :otherwise :disabled}
              :auth {:disabled [:is-faulty-review]}}]}
     {:name "lasnaolijat" :type :text :max-len 8000 :layout :full-width
      :css []
      :whitelist {:roles [:authority] :otherwise :disabled}
      :auth {:disabled [:is-faulty-review]}}
     {:name "poikkeamat" :type :text :max-len 8000 :layout :full-width
      :css []
      :whitelist {:roles [:authority] :otherwise :disabled}
      :auth {:disabled [:is-faulty-review]}}]}
   {:name "muuTunnus" :type :text :readonly true :hidden true}
   {:name "muuTunnusSovellus" :type :text :readonly true :hidden true}])

(def- task-katselmus-body-backend
  (-> task-katselmus-body
      (update-in [2 :body 1 :body 0 :body] conj
                 {:name "pidetty" :i18nkey "task-katselmus.katselmus.tila.pidetty"})
      (update-in [3 :body 0 :body] conj
                 {:name "pidetty" :i18nkey "task-katselmus.katselmus.tila.pidetty"})
      (update-in [2 :body 1 :body 0] assoc :readonly true)
      (update-in [3 :body 0] assoc :readonly true)))

(def- task-katselmus-body-ya
  (concat [katselmuksenLaji-ya]
          (update-in (tools/schema-body-without-element-by-name task-katselmus-body
                                                                "rakennus" "tila" "katselmuksenLaji" "tiedoksianto")
                     [1 :body 2 :body 0] assoc :required true)))

(schemas/defschemas
  task-schemas-version
  [{:info {:name "task-katselmus"
           :type :task
           :subtype :review
           :order 1
           :section-help "authority-fills"
           :i18nprefix "task-katselmus.katselmuksenLaji"
           :user-authz-roles #{}
           } ; Had :i18npath ["katselmuksenLaji"]
    :rows [["katselmuksenLaji" "vaadittuLupaehtona"]
           ["katselmus/tila" "katselmus/pitoPvm" "katselmus/pitaja"]
           ["katselmus/tiedoksianto::2"]
           ["rakennus::4"]
           {:h2 "task-katselmus.huomautukset"}
           ["katselmus/huomautukset/kuvaus::3"]
           ["katselmus/huomautukset/maaraAika" "katselmus/huomautukset/toteaja" "katselmus/huomautukset/toteamisHetki"]
           ["katselmus/lasnaolijat::3"]
           ["katselmus/poikkeamat::3"]]
    :template "form-grid-docgen-group-template"
    :body task-katselmus-body}

   {:info {:name "task-katselmus-backend"
           :type :task
           :subtype :review-backend
           :order 1
           :section-help "authority-fills"
           :i18name "task-katselmus"
           :i18nprefix "task-katselmus.katselmuksenLaji"
           :user-authz-roles #{}
           } ; Had :i18npath ["katselmuksenLaji"]
    :rows [["katselmuksenLaji" "vaadittuLupaehtona"]
           ["katselmus/tila" "katselmus/pitoPvm" "katselmus/pitaja"]
           ["katselmus/tiedoksianto::2"]
           ["rakennus::4"]
           {:h2 "task-katselmus.huomautukset"}
           ["katselmus/huomautukset/kuvaus::3"]
           ["katselmus/huomautukset/maaraAika" "katselmus/huomautukset/toteaja" "katselmus/huomautukset/toteamisHetki"]
           ["katselmus/lasnaolijat::3"]
           ["katselmus/poikkeamat::3"]]
    :template "form-grid-docgen-group-template"
    :body task-katselmus-body-backend}

   {:info {:name "task-katselmus-ya"
           :type :task
           :subtype :review
           :order 1
           :section-help "authority-fills"
           :i18name "task-katselmus"
           :i18nprefix "task-katselmus.katselmuksenLaji"
           :user-authz-roles #{}
           } ; Had :i18npath ["katselmuksenLaji"]
    :rows [["katselmuksenLaji" "katselmus/pitoPvm" "katselmus/pitaja" "vaadittuLupaehtona"]
           {:h2 "task-katselmus.huomautukset"}
           ["katselmus/huomautukset/kuvaus::3"]
           ["katselmus/huomautukset/maaraAika" "katselmus/huomautukset/toteaja" "katselmus/huomautukset/toteamisHetki"]
           ["katselmus/lasnaolijat::3"]
           ["katselmus/poikkeamat::3"]]
    :template "form-grid-docgen-group-template"
    :body task-katselmus-body-ya}

   {:info {:name "task-vaadittu-tyonjohtaja" :type :task :subtype :foreman :order 10}
    :body [{:name "osapuolena" :type :checkbox}
           {:name "asiointitunnus" :type :string :max-len 17}
           {:name "kuntaRoolikoodi" :type :select
            :sortBy :displayname
            :css [:dropdown--plain]
            :required true
            :readonly true
            :whitelist {:roles [:authority] :otherwise :disabled}
            :default "illegal"
            :body (->> schemas/kuntaroolikoodi-tyonjohtaja first :body drop-last)}]}

   {:info {:name "task-lupamaarays" :type :task :order 20}
    :rows [["maarays::3"] ["kuvaus::3"]]
    :template "form-grid-docgen-group-template"
    :body [{:name "maarays" :type :text :inputType :paragraph :max-len 20000 :readonly true}
           {:name "kuvaus"  :type :text :max-len 20000 }
           {:name "vaaditutErityissuunnitelmat" :type :text :max-len 20000 :hidden true}]}])


(defn task-doc-validation [schema-name doc]
  (let [schema (schemas/get-schema task-schemas-version schema-name)
        info   (model/document-info doc schema)]
    (model/validate-fields nil info nil (:data doc) [])))

(defn new-task [schema-name task-name data {:keys [created assignee state] :or {state :requires_user_action}} source]
  {:pre [schema-name source (or (map? data) (nil? data))]}
  (util/deep-merge
    (model/new-document (schemas/get-schema task-schemas-version schema-name) created)
    {:source source
     :taskname task-name
     :state state
     :data (if data (-> data tools/wrapped (tools/timestamped created)) {})
     :assignee (select-keys assignee [:id :firstName :lastName])
     :duedate nil
     :created created
     :closed nil}))

(defn set-state
  "Updates are called in the context of the task $elemMatch."
  [{created :created :as command} task-id state & [updates]]
  (action/update-application command
    {:tasks {$elemMatch {:id task-id}}}
    (util/deep-merge
      {$set {:tasks.$.state state :modified created}}
      updates)))


(defn merge-rakennustieto
  "Combines review buildings information from KuntaGML with the
  application buildings-array (the array may have also been generated
  from the XML).

  `rakennustieto-from-xml` is a list of :KatselmuksenRakennus elements.

  `rakennus-from-buildings` is buildings-array processed by
  `rakennus-data-from-buildings` and it is in the task format:

  {:0 {:rakennus {:jarjestysnumero                    '1'
                  :kunnanSisainenPysyvaRakennusnumero 'local-id'
                  :valtakunnallinenNumero             'vtj-prt'
                  :kiinttun                           'property-id'
                  :rakennusnro                        'short-id'}
       :tila     {:tila '' :kayttoonottava false}}
   :1 {:rakennus {...}
       :tila     {...}}}

  The return value is also in the same format."
  [rakennustieto-from-xml rakennus-from-buildings]
  (letfn [(find-rt [k ks]
            (util/find-by-keys (select-keys (get-in rakennus-from-buildings [k :rakennus])
                                            ks)
                               rakennustieto-from-xml))]
    (reduce (fn [acc k]
              ;; Find the most exact match
              (if-let [{:keys [kayttoonottoKytkin
                               katselmusOsittainen]} (some (partial find-rt k)
                                                           [[:jarjestysnumero :valtakunnallinenNumero]
                                                            [:jarjestysnumero :kunnanSisainenPysyvaRakennusnumero]
                                                            [:valtakunnallinenNumero]
                                                            [:kunnanSisainenPysyvaRakennusnumero]
                                                            [:jarjestysnumero]])]
                (cond-> acc
                  (boolean? kayttoonottoKytkin) (assoc-in [k :tila :kayttoonottava]
                                                          kayttoonottoKytkin)
                  (ss/not-blank? katselmusOsittainen) (assoc-in [k :tila :tila]
                                                                katselmusOsittainen))
                acc))
            rakennus-from-buildings
            (keys rakennus-from-buildings))))

(defn rakennus-data-from-buildings [initial-rakennus buildings]
  (reduce
    (fn [acc build]
      (let [index (->> (map (comp #(Integer/parseInt %) name) (keys acc))
                       (apply max -1) ; if no keys, starting index (inc -1) => 0
                       inc
                       str
                       keyword)
            default-tila {:tila ""
                          :kayttoonottava false}
            rakennus {:rakennus {:jarjestysnumero                    (:index build)
                                 :kiinttun                           (:propertyId build)
                                 :rakennusnro                        (:localShortId build)
                                 :valtakunnallinenNumero             (:nationalId build)
                                 :kunnanSisainenPysyvaRakennusnumero (:localId build)}
                      :tila (get build :task-tila default-tila)}]
        (assoc acc index rakennus)))
    initial-rakennus
    buildings))

(defn- get-muu-tunnus-data [katselmus]
  (->> (:muuTunnustieto katselmus)
       (map :MuuTunnus)
       first
       (krysp-reader/extract-muu-tunnus)))

(defn katselmus->task [meta source {:keys [buildings]} katselmus]
  (let [task-name (or (:tarkastuksenTaiKatselmuksenNimi katselmus) (:katselmuksenLaji katselmus))
        rakennustieto (map :KatselmuksenRakennus (:katselmuksenRakennustieto katselmus))
        first-huomautus (first (get-in katselmus [:huomautukset]))
        katselmus-data {:tila (get katselmus :osittainen)
                        :pitaja (get katselmus :pitaja)
                        :pitoPvm (date/finnish-date (get katselmus :pitoPvm) :zero-pad)
                        :lasnaolijat (get katselmus :lasnaolijat "")
                        :tiedoksianto (get katselmus :verottajanTvLlKytkin false)
                        :huomautukset {:kuvaus (or (-> first-huomautus :huomautus :kuvaus)
                                                   "")}
                        :poikkeamat (get katselmus :poikkeamat "")}
        data (merge {:katselmuksenLaji (get katselmus :katselmuksenLaji "muu katselmus")
                     :vaadittuLupaehtona (get katselmus :vaadittuLupaehtonaKytkin true)
                     :rakennus (merge-rakennustieto rakennustieto
                                                    (rakennus-data-from-buildings {} buildings))
                     :katselmus katselmus-data}
                    (get-muu-tunnus-data katselmus))

        schema-name (if (-> katselmus-data :tila (= "pidetty"))
                      "task-katselmus-backend"
                      ;; else
                      "task-katselmus")
        task (new-task schema-name task-name data meta source)]
    ;; (debugf "katselmus->task: made task with schema-name %s, id %s, katselmuksenLaji %s" schema-name (:id task) (:katselmuksenLaji data))
    task))

(defn task-coerced-from-foreman-task?
  "Foreman requirement tasks that don't have a proper kuntaRoolikoodi are coerced to generic requirement tasks instead.
   The :migrated-foreman-task - flag is
   true             for tasks that were coerced during migration,
   false            for tasks coerced from the backing system, and
   nil/non-existent for tasks that are not coerced foreman tasks"
  [task]
  (some? (:migrated-foreman-task task)))

(defn- coerce-freetext-foreman-task
  "Turns out even verdicts from backend don't necessarily have tasks that adhere to KuntaGML foreman roles.
   Use the migration logic for making foreman req tasks from the ones that have identifiable roles,
   and turn the others into generic requirement tasks.
   See TT-18179 for details."
  [role meta source]
  (-> (new-task "task-vaadittu-tyonjohtaja" role {} meta source)
      foreman-role-mapping/legacy-foreman-task-update-fn
      (assoc :migrated-foreman-task false)
      (update-in [:schema-info :subtype] keyword)))

(defn- verdict->tasks [verdict meta application]
  (map
    (fn [{lupamaaraykset :lupamaaraykset}]
      (let [source {:type "verdict" :id (:id verdict)}]
        (concat
          (map (partial katselmus->task meta source application) (:vaaditutKatselmukset lupamaaraykset))
          (map #(new-task "task-lupamaarays" (:sisalto %) {:maarays (:sisalto %)} meta source)
            (filter #(-> % :sisalto ss/blank? not) (:maaraykset lupamaaraykset)))
          ; KRYSP yhteiset 2.1.5+
          (map #(new-task "task-lupamaarays" % {:vaaditutErityissuunnitelmat %} meta source)
            (filter #(-> %  ss/blank? not) (:vaaditutErityissuunnitelmat lupamaaraykset)))
          (if (seq (:vaadittuTyonjohtajatieto lupamaaraykset))
            ; KRYSP yhteiset 2.1.1+
            (map #(coerce-freetext-foreman-task % meta source)
                 (:vaadittuTyonjohtajatieto lupamaaraykset))
            ; KRYSP yhteiset 2.1.0 and below
            (when-not (ss/blank? (:vaaditutTyonjohtajat lupamaaraykset))
              (map #(coerce-freetext-foreman-task % meta source)
                   (ss/split (:vaaditutTyonjohtajat lupamaaraykset) #"(,\s*)"))))
          ;; from YA verdict
          (map #(new-task "task-lupamaarays" % {:maarays %} meta source)
            (filter #(-> %  ss/blank? not) (:muutMaaraykset lupamaaraykset))))))
    (:paatokset verdict)))

(defn verdicts->tasks [application user timestamp]
  (let [meta {:created timestamp
              :assignee user}]
    (flatten (map #(verdict->tasks % meta application) (:verdicts application)))))

(defn task-schemas [{:keys [schema-version permitType]}]
  (let [allowed-task-schemas (permit/get-metadata permitType :allowed-task-schemas)]
    (filter
      #(and
         (= :task (-> % :info :type))
         (allowed-task-schemas (-> % :info :name)))
      (vals (schemas/get-schemas schema-version)))))

(defn task-attachments [{:keys [attachments]} task-id]
  (filter #(= task-id (get-in % [:target :id])) attachments))


(defn generate-task-pdfa
  "When called with `command` the `:organization` can be delay or not."
  ([application task user lang]
   (assert (map? application))
   (when (task-is-review? task)
     (review-pdf/create-review-attachment {:lang         lang
                                           :application  application
                                           :organization (org/get-organization (:organization application))
                                           :created      (now)
                                           :user         user}
                                          task)))
  ([command task]
   (when (task-is-review? task)
     (review-pdf/create-review-attachment command task))))

(defn- update-building
  "Update `new-building` with :task-tila, which is a tila information
  from the most exactly matching task building."
  [old-buildings new-building]
  (let [matcher   (get-in (rakennus-data-from-buildings {} [new-building])
                          [:0 :rakennus])
        olds      (->> (vals old-buildings)
                       tools/unwrapped
                       (map (fn [{:keys [rakennus tila]}]
                              (assoc rakennus ::tila tila))))
        find-fn   (fn [ks]
                    (util/find-by-keys (select-keys matcher ks) olds))
        old-match (some find-fn [[:jarjestysnumero :valtakunnallinenNumero]
                                 [:jarjestysnumero :kunnanSisainenPysyvaRakennusnumero]
                                 [:valtakunnallinenNumero]
                                 [:kunnanSisainenPysyvaRakennusnumero]
                                 [:jarjestysnumero]])]
    (util/assoc-when new-building :task-tila (::tila old-match))))

(defn update-task-buildings [new-buildings task]
  (if (not (task-is-review? task))
    task
    ;; else

    ;; Normally task's buildings (:data :rakennus) are updated by tasks/rakennus-data-from-buildings called in katselmus->task.  here we call it
    ;; again once for each task, to apply the task rakennus :tila data to the right buildings if supplied
    ;; with a ":task-tila"-decorated :buildings map.

    (let [old-buildings (-> task :data :rakennus)
          new-buildings-with-states (map (fn [new-building]
                                           (update-building old-buildings new-building)) new-buildings)
          task-rakennus (rakennus-data-from-buildings {} new-buildings-with-states)
          updated-task (assoc-in task [:data :rakennus] (tools/wrapped task-rakennus))]

      (if (> (count task-rakennus) (count new-buildings-with-states))
        (errorf "update-task-buildings: too many buildings: task has %s but :buildings %s" (count task-rakennus) (count new-buildings-with-states)))
      updated-task)))

(defn- faultify
  "Helper function for task->faulty."
  [{:keys [application created] :as command} task-id & [updates]]
  (let [review-attachments (att/get-attachments-by-target-type-and-id application
                                                                      {:type "task"
                                                                       :id   task-id})]
    (action/update-application command {$pull {:attachments {:id {$in (map :id review-attachments)}}}})
    (doseq [{{:keys [fileId originalFileId]} :latestVersion} review-attachments]
      (when-not (= fileId originalFileId)
        (att/delete-attachment-file-and-preview! application fileId)))
    (set-state command task-id :faulty_review_task
               {$set (merge {:tasks.$.faulty
                             {:timestamp created
                              :files     (map (util/fn-> :latestVersion
                                                         (select-keys [:originalFileId :filename]))
                                              review-attachments)}}
                            updates)})))

(defn task->faulty
  "Clear task attachments. Store original file ids. Set task state
  to :faulty_review_task. Update notes if
  given (katselmus/huomautukset/kuvaus)"
  ([command task-id]
   (faultify command task-id))
  ([{:keys [created] :as command} task-id notes]
   (faultify command task-id {:tasks.$.data.katselmus.huomautukset.kuvaus
                              {:value    (or (ss/trim notes) "")
                               :modified created}})))

(defn update-task-reviewer!
  "Sets the command's user as the reviewer for the task if it has not been set to some other value already.
  If the organization has the review officers list in use, find the closest matching
  officer on that list and assign them as the reviewer."
  [{:keys [application created] :as command} task-id]
  (when-let [reviewer-value (some->> application
                                     :tasks
                                     (util/find-by-id task-id)
                                     (task-util/default-reviewer-value command))]
    (action/update-application command
                               {:tasks {$elemMatch {:id task-id}}}
                               {$set {:tasks.$.data.katselmus.pitaja {:value    (:value reviewer-value)
                                                                      :modified created}
                                      :modified                      created}})))
