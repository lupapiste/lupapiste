(ns lupapalvelu.tasks
  (:require [taoensso.timbre :as timbre :refer [debug debugf info infof warn warnf error errorf]]
            [clojure.string :as s]
            [clojure.set :refer [rename-keys]]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer [def-]]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.tools :as tools]))

(def task-schemas-version 1)

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

(def task-states #{:requires_user_action :requires_authority_action :ok :sent})

(defn all-states-but [& states]
  (apply disj task-states states))

(defn task-is-review? [task]
  (some->> (get-in task [:schema-info :name])
           (schemas/get-schema task-schemas-version)
           (#(get-in % [:info :subtype]))
           (keyword)
           (contains? #{:review :review-backend})))

(def- katselmuksenLaji
  {:name "katselmuksenLaji"
   :type :select :sortBy :displayname
   :css [:dropdown--plain]
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
    :readonly-after-sent true
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
                    :readonly-after-sent true
                    :whitelist {:roles [:authority] :otherwise :disabled}
                    :i18nkey "task-katselmus.katselmus.tila"
                    :body [{:name "osittainen"} {:name "lopullinen"}]}
                   {:name "kayttoonottava" :readonly-after-sent true
                    :type :checkbox :inputType :checkbox-wrapper
                    :auth {:enabled [:is-end-review]}
                    :whitelist {:roles [:authority] :otherwise :disabled}}
                   ]}]}
   {:name "katselmus" :type :group
    :whitelist {:roles [:authority] :otherwise :disabled}
    :body
    [{:name "tila" :type :select :css [:dropdown :form-input] :sortBy :displayname
      :readonly-after-sent true
      :whitelist {:roles [:authority] :otherwise :disabled}
      :required true
      :body [{:name "osittainen"} {:name "lopullinen"}]}
     {:name "pitoPvm" :type :date :required true :readonly-after-sent true
      :whitelist {:roles [:authority] :otherwise :disabled}}
     {:name "pitaja" :type :string :required true :readonly-after-sent true
      :whitelist {:roles [:authority] :otherwise :disabled}}
     {:name "tiedoksianto" :type :checkbox :inputType :checkbox-wrapper
      :readonly-after-sent true
      :whitelist {:roles [:authority] :otherwise :disabled}
      :auth {:enabled [:is-end-review]}}
     {:name "huomautukset" :type :group
      :body [{:name "kuvaus" :type :text :max-len 20000 :css []
              :whitelist {:roles [:authority] :otherwise :disabled} }
             {:name "maaraAika" :type :date :whitelist {:roles [:authority] :otherwise :disabled}}
             {:name "toteaja" :type :string :whitelist {:roles [:authority] :otherwise :disabled}}
             {:name "toteamisHetki" :type :date :whitelist {:roles [:authority] :otherwise :disabled}}]}
     {:name "lasnaolijat" :type :text :max-len 4000 :layout :full-width :css [] :readonly-after-sent true
      :whitelist {:roles [:authority] :otherwise :disabled}}
     {:name "poikkeamat" :type :text :max-len 4000 :layout :full-width :css [] :readonly-after-sent true
      :whitelist {:roles [:authority] :otherwise :disabled}}]}
   {:name "muuTunnus" :type :text
    :readonly true :hidden true}])

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
           {:name "asiointitunnus" :type :string :max-len 17}]}

   {:info {:name "task-lupamaarays" :type :task :order 20}
    :rows [["maarays::3"] ["kuvaus::3"]]
    :template "form-grid-docgen-group-template"
    :body [{:name "maarays" :type :text :inputType :paragraph :max-len 20000 :readonly true}
           {:name "kuvaus"  :type :text :max-len 20000 }
           {:name "vaaditutErityissuunnitelmat" :type :text :hidden true}]}])


(defn task-doc-validation [schema-name doc]
  (println "schema-name" schema-name doc)
  (let [schema (schemas/get-schema task-schemas-version schema-name)
        info   (model/document-info doc schema)]
    (model/validate-fields nil info nil (:data doc) [])))

(defn new-task [schema-name task-name data {:keys [created assignee state] :as meta :or {state :requires_user_action}} source]
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


(defn merge-rakennustieto [rakennustieto-from-xml rakennus-from-buildings]
  (let [match-rt (fn [[rak-index rak-map]]
                   (let [rak-rak-map (:rakennus rak-map)
                         match-by (fn [k one-rt]
                                    (and (not-empty (k rak-rak-map))
                                         (= (k rak-rak-map) (k one-rt))))
                         match-by-key (fn [key] (doall  (util/find-first (partial match-by key) rakennustieto-from-xml)))
                         match-by-vn (match-by-key :valtakunnallinenNumero)
                         match-by-ks (match-by-key :kunnanSisainenPysyvaRakennusnumero)
                         match-by-j (match-by-key :jarjestysnumero)
                         result (or match-by-vn match-by-ks match-by-j)]
                     result))
        merge-one-to-one (fn [[rakennus-index rakennus-map] rakennustieto]
                           (let [v1 (if (:katselmusOsittainen rakennustieto)
                                      (assoc-in rakennus-map [:tila :tila] (:katselmusOsittainen rakennustieto))
                                      rakennus-map)
                                 v2 (if (:kayttoonottoKytkin rakennustieto)
                                      (assoc-in v1 [:tila :kayttoonottava] (:kayttoonottoKytkin rakennustieto))
                                      v1)]
                             [rakennus-index v2]
                             ))
        merge-one (fn [one-rakennus]
                    (merge-one-to-one one-rakennus (match-rt one-rakennus)))
        result (into {} (map merge-one rakennus-from-buildings))]
    result
    ))

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

(defn katselmus->task [meta source {:keys [buildings]} katselmus]
  (let [task-name (or (:tarkastuksenTaiKatselmuksenNimi katselmus) (:katselmuksenLaji katselmus))
        rakennustieto (map :KatselmuksenRakennus (:katselmuksenRakennustieto katselmus))
        get-muuTunnus (fn [katselmus]
                        ;; get first {:sovellus "BackendName" :tunnus "BackendIdNumber"} that doesn't have :sovellus "lupapiste"
                        ;; and combine to a single string like "BackendName-BackendIdNumber" (or just "BackendIdNumber" of "BackendName" is missing or empty)
                        (let [MuuTunnus (-> (util/find-first #(not= "lupapiste" (-> % :MuuTunnus :sovellus sade.strings/lower-case)) (:muuTunnustieto katselmus))
                                            :MuuTunnus)
                              sovellus (:sovellus MuuTunnus)
                              tunnus (:tunnus MuuTunnus "")
                              ]
                          (if (and (not-empty sovellus) (not-empty tunnus))
                            (str tunnus "-" sovellus)
                            ;; else
                            tunnus)))
        first-huomautus (first (get-in katselmus [:huomautukset]))
        katselmus-data {:tila (get katselmus :osittainen)
                        :pitaja (get katselmus :pitaja)
                        :pitoPvm (util/to-local-date (get katselmus :pitoPvm))
                        :lasnaolijat (get katselmus :lasnaolijat "")
                        :huomautukset {:kuvaus (or (-> first-huomautus :huomautus :kuvaus)
                                                   "")}
                        :poikkeamat (get katselmus :poikkeamat "")
                        }
        data {:katselmuksenLaji (get katselmus :katselmuksenLaji "muu katselmus")
              :vaadittuLupaehtona true
              :rakennus (merge-rakennustieto rakennustieto
                                             (rakennus-data-from-buildings {} buildings))
              :katselmus katselmus-data
              :muuTunnus (get-muuTunnus katselmus)
              }

        schema-name (if (-> katselmus-data :tila (= "pidetty"))
                      "task-katselmus-backend"
                      ;; else
                      "task-katselmus")
        task (new-task schema-name task-name data meta source)]
    ;; (debugf "katselmus->task: made task with schema-name %s, id %s, katselmuksenLaji %s" schema-name (:id task) (:katselmuksenLaji data))
    task))

(defn- verdict->tasks [verdict meta application]
  (map
    (fn [{lupamaaraykset :lupamaaraykset}]
      (let [source {:type "verdict" :id (:id verdict)}]
        (concat
          (map (partial katselmus->task meta source application) (:vaaditutKatselmukset lupamaaraykset))
          (map #(new-task "task-lupamaarays" (:sisalto %) {:maarays (:sisalto %)} meta source)
            (filter #(-> % :sisalto s/blank? not) (:maaraykset lupamaaraykset)))
          ; KRYSP yhteiset 2.1.5+
          (map #(new-task "task-lupamaarays" % {:vaaditutErityissuunnitelmat %} meta source)
            (filter #(-> %  s/blank? not) (:vaaditutErityissuunnitelmat lupamaaraykset)))
          (if (seq (:vaadittuTyonjohtajatieto lupamaaraykset))
            ; KRYSP yhteiset 2.1.1+
            (map #(new-task "task-vaadittu-tyonjohtaja" % {} meta source) (:vaadittuTyonjohtajatieto lupamaaraykset))
            ; KRYSP yhteiset 2.1.0 and below
            (when-not (s/blank? (:vaaditutTyonjohtajat lupamaaraykset))
              (map #(new-task "task-vaadittu-tyonjohtaja" % {} meta source)
                (s/split (:vaaditutTyonjohtajat lupamaaraykset) #"(,\s*)"))))
          ;; from YA verdict
          (map #(new-task "task-lupamaarays" % {:maarays %} meta source)
            (filter #(-> %  s/blank? not) (:muutMaaraykset lupamaaraykset))))))
    (:paatokset verdict)))

(defn verdicts->tasks [application timestamp]
  (let [owner (first (auth/get-auths-by-role application :owner))
        meta {:created timestamp
              :assignee (user/get-user-by-id (:id owner))}]
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


(defn generate-task-pdfa [application task user lang]
  (assert (map? application))
  (when (task-is-review? task)
    (child-to-attachment/create-attachment-from-children user application :tasks (:id task) lang)))

(defn- update-building [old-buildings new-building]
  ;; old-buildings comes from an existing task's :data :rakennus map
  ;; new-building is element of application top-level :buildings seq
  ;; new-rakennukset is :buildings format data from new xml that may be decroated with per-building, per-review state under :task-tila
  ;; top-key = keys for top-level :buildings map, task-key = keys for task :rakennus
  (let [match-old-by-id (fn [top-key task-key]
                          (util/find-first (fn [old-building]
                                        (let [task-value (-> old-building :rakennus task-key :value)
                                              top-value (-> new-building top-key)
                                              ]
                                          ;; (debugf "comparing keys rakennukset %s vs :buildings %s - values: %s v %s" task-key top-key task-value top-value)
                                          (and (not= nil task-value)
                                               (not= nil top-value)
                                               (= top-value task-value))))
                                      (vals old-buildings)))
        ;; default-tila {:tila "" :kayttoonottava ""}
        matching-old (or (match-old-by-id :nationalId :valtakunnallinenNumero)
                         (match-old-by-id :localId :kunnanSisainenPysyvaRakennusnumero)
                         (match-old-by-id :index :jarjestysnumero))
        update-from-old (fn [new-building old-rakennus]
                          (if (contains? (:tila old-rakennus) :tila)
                            (assoc new-building :task-tila (tools/unwrapped  (:tila old-rakennus)))
                            ;; else
                            (debugf ":tila :tila missing from old-rakennus :tila")
                            ))]
    (if-not (nil? matching-old)
      (update-from-old new-building matching-old)
      ;; else
      new-building)))

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
