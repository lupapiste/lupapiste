(ns lupapalvelu.migration.hide
  "Sometimes we get a request to delete application(s). We mustn't do that, but
  as a compromise we can try to hide them (`:applications` -> `:hidden-applications`)."
  (:require [lupapalvelu.application-utils :as au]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-common :as vc]
            [monger.operators :refer [$in]]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

(sc/defn ^:always-validate can-hide?
  "Useful for `:apply-when` checks."
  [app-ids :- #{ssc/ApplicationId}]
  (and (not-empty app-ids)
       (= (count app-ids)
          (mongo/count :applications {:_id {$in app-ids}}))
       (not (mongo/any? :hidden-applications {:_id {$in app-ids}}))))

(sc/defn hide-applications :- sc/Int
  {:always-validate true}
  [app-ids :- #{ssc/ApplicationId}
   note-text :- ssc/NonBlankStr
   time :- sc/Int]
  (let [note       {:note    note-text
                    :created time}
        app-q      {:_id {$in app-ids}}
        linked-q   {:link {$in app-ids}}
        invoices-q {:application-id {$in app-ids}}
        get-ids    (fn [coll q]
                     (->> (mongo/select coll q [:id])
                          (keep :id)
                          set))]
    (cond
      ;; Abort if application is linked
      (mongo/any? :app-links linked-q)
      (throw (ex-info "Cannot hide linked app!" {:app-links (get-ids :app-links linked-q)}))

      ;; Abort if application has invoices
      (mongo/any? :invoices invoices-q)
      (throw (ex-info "Cannot hide apps with invoices!" {:invoices (get-ids :invoices invoices-q)}))

      :else ; Proceed
      (let [applications (mongo/select :applications app-q)
            converted    (->> applications (filter :facta-imported) (map :id) set)
            ;; We allow the hiding of paper permits with verdicts, since those typically
            ;; duplicate converted applications.
            decided      (->> applications (remove au/paper-permit?)
                              (filter (comp seq vc/all-verdicts)) (map :id) set)]
        (cond
          ;; Abort if applications are converted
          (seq converted)
          (throw (ex-info "Cannot hide converted applications!" {:applications converted}))

          ;; Abort if applications have verdict(s)
          (seq decided)
          (throw (ex-info "Cannot hide applications with verdicts!" {:applications decided}))

          :else ; Proceed
          (->> applications
               (map (fn [{:keys [id] :as app}]
                      (log/info "Hiding application" id)
                      (let [app (update app :_sheriff-notes (fnil conj []) note)]
                        (mongo/insert :hidden-applications app))
                      (mongo/remove-by-id :applications id)))
               count))))))
