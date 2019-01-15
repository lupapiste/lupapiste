(ns lupapalvelu.exports.reporting-db
  (:require [clojure.set :as set]
            [lupapalvelu.data-skeleton :as ds]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.rakennuslupa-canonical :refer [application-to-canonical katselmus-canonical]]
            [lupapalvelu.document.poikkeamis-canonical :refer [poikkeus-application-to-canonical]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-canonical :refer [verdict-canonical]]
            [lupapalvelu.pate.verdict-common :as vc]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.util :as util]))

;;
;; Helpers
;;

(defn- sequentialize [xs]
  (if xs
    (util/sequentialize xs)
    []))

(defn- str->num
  "Change the given `keys-to-update` in `a-map` into numerical values,
  if possible. Otherwise set them to `nil`"
  [a-map keys-to-update]
  (util/update-values a-map
                      keys-to-update
                      #(util/->int % nil)))

(def get-katselmustieto
  (ds/from-context [:context :Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto :Katselmus]))


(def rakennusvalvonta-asia
  (ds/from-context [:canonical :Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]))

(def osapuolet
  (ds/from-context [rakennusvalvonta-asia :osapuolettieto :Osapuolet]))

;;
;; The application report
;;

(def reporting-app-skeleton
  {:id (ds/access :id) ;; Application id
   :araFunding (ds/access :araFunding) ;; Does the project receive ARA funding?
   :state (ds/access :state) ;; The internal Lupapiste representation of state, ie. not a 1-to-1 mapping with KuntaGML
   :permitType (ds/access :permitType)
   :location-etrs-tm35fin (ds/access :location)
   :location-wgs84 (ds/access :location-wgs84)
   :address (ds/access :address)
   :propertyId (ds/access :propertyId)
   :organization (ds/access :organization)
   :municipality (ds/access :municipality)
   :stateChangeTs (ds/access :stateChangeTs) ;; The timestamp of the latest state change, also see `:state`
   :createdTs (ds/access :created)
   :modifiedTs (ds/access :modified)
   :projectDescription (ds/access :projectDescription)

   ;; Katselmus
   :reviews (ds/array-from :reviews
                           {:type           (ds/access :review-type) ;; KatselmuksenlajiSelite: Liittyy toimenpiteeseen
                            :date           (ds/access :review-date) ;; Pitopvm
                            :reviewer       (ds/access :review-reviewer)
                            :verottajanTvLl (ds/access :review-verottajanTvLl)})

   :statements (ds/access :statements)
   :parties (ds/access :parties)
   :planners (ds/access :planners)
   :foremen (ds/access :foremen)

   ;; TODO Viiteluvat

   ;; Päätös
   :verdicts (ds/array-from :verdicts
                            (ds/access :context))

   :operations (ds/array-from
                :operations
                {:id (ds/access :operation-id) ;; Lupapiste operation id
                 :primary (ds/access :operation-primary?) ;; Is this the primary operation in Lupapiste
                 :kuvaus (ds/access :operation-description)
                 :nimi (ds/access :operation-name-fi) ;; The KuntaGML `kuvaus`, not the internal `:name`
                 :rakennus (ds/access :operation-building)
                 :rakennelma (ds/access :operation-structure)})

   :links (ds/access :links)})

;;
;; Accessors
;;

(defn- verdict-via-canonical
  "Builds a representation of the given verdict for reporting db API
  using mostly the canonical representation."
  [lang verdict]
  (-> (:Paatos (verdict-canonical lang
                                  verdict))
      (assoc :kuntalupatunnus
             (vc/verdict-municipality-permit-id verdict))
      (update-in [:lupamaaraykset :maaraystieto]
                 (partial mapv (comp :sisalto :Maarays)))
      (update-in [:lupamaaraykset :vaadittuErityissuunnitelmatieto]
                 (partial mapv :VaadittuErityissuunnitelma))
      (update-in [:lupamaaraykset :vaadittuTyonjohtajatieto]
                 (partial mapv (comp :tyonjohtajaRooliKoodi :VaadittuTyonjohtaja)))
      (update-in [:lupamaaraykset :vaaditutKatselmukset]
                 (partial mapv :Katselmus))))


(defn ->statements [canonical-statements]
  (->> canonical-statements
       (map (comp :lausuntotieto :Lausunto))
       (remove nil?)
       (map :Lausunto)
       (mapv #(update % :puoltotieto :Puolto))))

(defn- operation-name-fi [operation]
  (i18n/localize "fi" (str "operations." (:name operation))))

(defn- operation-building [context]
  ;; We know that :rakennustieto is not sequential, i.e. there's only one building per operation
  (some-> ((ds/from-context [:context :rakennustieto :Rakennus]) context)
          (update :omistajatieto (comp (partial mapv :Omistaja)
                                       sequentialize))
          (dissoc :alkuHetki :sijaintitieto :yksilointitieto)
          (set/rename-keys {:rakennuksenTiedot :tiedot :omistajatieto :omistajat})

          ;; Change various numerical values into actual numbers
          (util/safe-update-in [:tiedot :asuinhuoneistot :huoneisto]
                               (partial mapv #(str->num % [:huoneistoala :huoneluku])))
          (util/safe-update-in [:tiedot :varusteet] #(str->num % [:saunoja]))

          (update :tiedot #(str->num % [:energiatehokkuusluku :kellaripinta-ala :kerrosala
                                        :kerrosluku :kokonaisala :rakennusoikeudellinenKerrosala
                                        :tilavuus :kellarinpinta-ala]))))

(defn- operation-structure [context]
  (some-> ((ds/from-context [:context :rakennelmatieto :Rakennelma]) context)
          (dissoc :alkuHetki :sijaintitieto :yksilointitieto)))

(defn- operation-id [canonical-operation]
  (or (get-in canonical-operation [:rakennustieto :Rakennus :yksilointitieto])
      (get-in canonical-operation [:rakennelmatieto :Rakennelma :yksilointitieto])))

(defn- merge-data-from-canonical-operation [operation canonical-operations]
  (merge operation
         (util/find-first #(= (:id operation)
                              (operation-id %))
                          canonical-operations)))

(defn- operations [context]
  (let [operations (conj (-> context :application :secondaryOperations)
                         (-> context :application :primaryOperation (assoc :primary? true)))
        canonical-operations ((ds/from-context [rakennusvalvonta-asia :toimenpidetieto
                                                sequentialize
                                                (partial mapv :Toimenpide)])
                              context)]
    (mapv #(merge-data-from-canonical-operation % canonical-operations)
          operations)))

(defn- ->planner [planner]
  (-> planner
      :Suunnittelija
      (str->num [:kokemusvuodet :valmistumisvuosi])))

(defn- ->foreman [foreman]
  (-> foreman
      :Tyonjohtaja
      (update :sijaistustieto (comp (partial map :Sijaistus)
                                    sequentialize))
      (update :vastattavaTyotieto (comp (partial map (comp :vastattavaTyo
                                                           :VastattavaTyo))
                                        sequentialize))
      (set/rename-keys {:sijaistustieto     :sijaistukset
                        :vastattavaTyotieto :vastattavatTyot})
      (dissoc :vastattavatTyotehtavat)
      (str->num [:kokemusvuodet :valmistumisvuosi :valvottavienKohteidenMaara])))

(defn- get-link
  "Get the data for the app that is linked to app with `app-id`"
  [{:keys [link] :as link-data} app-id]
  (let [linked-id (->> link (remove #(util/=as-kw % app-id)) first)]
    {:id (name linked-id)
     :permitType (:apptype (get link-data (keyword linked-id)))}))

(defn- app-links [context]
  (let [app-id (-> context :application :id keyword)]
    (->> (:app-links context)
         (filter #(get % app-id))
         (map #(get-link % app-id)))))

(defn reporting-app-accessors [application lang]
  {:id (ds/from-context [:application :id])
   :address (ds/from-context [:application :address])
   :propertyId (ds/from-context [:application :propertyId])
   :organization (ds/from-context [:application :organization])
   :municipality (ds/from-context [:application :municipality])
   :araFunding (ds/from-context [:application #(domain/get-document-by-name % "hankkeen-kuvaus")
                                 :data tools/unwrapped :rahoitus]
                                false)
   :context (ds/from-context [:context])
   :created (ds/from-context [:application :created])
   :location (ds/from-context [:application :location])
   :location-wgs84 (ds/from-context [:application :location-wgs84])
   :modified (ds/from-context [:application :modified])

   :operations operations
   :operation-id (ds/from-context [:context :id])
   :operation-description (ds/from-context [:context :description])
   :operation-name-fi (ds/from-context [:context operation-name-fi])
   :operation-building operation-building
   :operation-primary? (ds/from-context [:context :primary?] false)
   :operation-structure operation-structure

   :projectDescription (ds/from-context [:canonical :Rakennusvalvonta :rakennusvalvontaAsiatieto
                                         :RakennusvalvontaAsia :asianTiedot :Asiantiedot
                                         :rakennusvalvontaasianKuvaus])

   :parties (ds/from-context [osapuolet :osapuolitieto
                              sequentialize (partial mapv :Osapuoli)])
   :planners (ds/from-context [osapuolet :suunnittelijatieto
                               sequentialize (partial mapv ->planner)])
   :foremen (ds/from-context [osapuolet :tyonjohtajatieto
                              sequentialize (partial mapv ->foreman)])

   :reviews (ds/from-context [:application :tasks (partial mapv #(katselmus-canonical application lang % nil))])
   :review-date (ds/from-context [get-katselmustieto :pitoPvm])
   :review-type (ds/from-context [get-katselmustieto :katselmuksenLaji])
   :review-reviewer (ds/from-context [get-katselmustieto :pitaja])
   :review-verottajanTvLl (ds/from-context [get-katselmustieto :verottajanTvLlKytkin] false)
   ;; TODO Propably not enough review data at the moment

   :state (ds/from-context [:application :state])
   :stateChangeTs (ds/from-context [:application :history (partial filterv :state) last :ts])
   :statements (ds/from-context [rakennusvalvonta-asia :lausuntotieto sequentialize
                                 ->statements])
   :permitType (ds/from-context [:application :permitType])

   :verdicts (fn [ctx]
               ((ds/from-context [:application vc/all-verdicts
                                  #(map (partial verdict-via-canonical (:lang ctx))
                                        %)])
                ctx))
   :links app-links})

(defn ->reporting-result [application app-links lang]
  ;; TODO check permit type, R or P (or others as well?)
  (let [application-canonical (if (= (:permitType application) "R")
                                (application-to-canonical application lang)
                                (poikkeus-application-to-canonical application lang))]
    (ds/build-with-skeleton reporting-app-skeleton
                            {:application application
                             :canonical application-canonical
                             :app-links app-links
                             :lang lang}
                            (reporting-app-accessors application lang))))

(def permit-types-for-reporting-db ["R" "P"])

(defn- applications-to-report
  "Fetch applications of relevant permit types that are modified
  between `start-ts` and `end-ts`"
  [start-ts end-ts]
  (let [query {:modified {$gte start-ts
                          $lte end-ts}
               :permitType {$in permit-types-for-reporting-db}}]
    (domain/get-multiple-applications-no-access-checking query)))

(defn- links-for-reported-apps
  "Fetch application links to the given `apps`"
  [apps]
  (mongo/select :app-links {:link {$in (mapv :id apps)}}))

(defn applications [start-ts end-ts]
  {:pre [(number? start-ts) (number? end-ts)]}
  (let [apps (applications-to-report start-ts end-ts)
        app-links (links-for-reported-apps apps)]
    (mapv #(->reporting-result % app-links "fi")
          apps)))
