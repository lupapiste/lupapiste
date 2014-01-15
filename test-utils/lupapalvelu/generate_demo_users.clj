(ns lupapalvelu.generate-demo-users
  (require [lupapalvelu.mongo :as mongo]
           [lupapalvelu.user-api :as user-api]))


;luo testikayttajia

(defn- generate-users-for-organization [{id :id :as organization}]
  (let [username-prefix (clojure.string/lower-case (subs id 4))
        kuntano (subs id 0 3)
        email (str username-prefix "-koulutus-" )]
    (if (lupapalvelu.user/get-user-by-email (str email "20" "@" kuntano ".fi"))
      (println (str "skip " id))
      (pmap #(let [full-email (str email % "@" kuntano ".fi")]
             (if (lupapalvelu.user/get-user-by-email full-email)
               (println "user exits " full-email)
               (user-api/create-new-user {:role "authorityAdmin"
                                        :organizations [id]}
                                       {:email full-email
                                        :username full-email
                                        :role "authority"
                                        :firstName (str "Koulutus " %)
                                        :lastName (str "Kayttaja " %)
                                        :enabled true
                                        :organization id
                                        :password "koulutus"}))
             ) (range 1 21)))))



(defn generate-users []
  (let [organizations (mongo/select :organizations)]
    (println "!!!!!!!!!!!!!!!!!!")
    (println (count organizations))
    (doall (for [o organizations]
             (when (< (count (:id o)) 7) (generate-users-for-organization o))))))


