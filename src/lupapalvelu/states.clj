(ns lupapalvelu.states
  (:require [clojure.set :refer [difference union]]))


(def all-application-states #{:draft :open :submitted :sent :complement-needed
                              :verdictGiven :constructionStarted :closed :canceled
                              :extinct
                              :hearing :proposal :proposalApproved
                              :survey :sessionProposal :sessionHeld :registered
                              :appealed :final
                              })
(def all-inforequest-states #{:info :answered})
(def all-states             (union all-application-states all-inforequest-states))

(def pre-verdict-states #{:draft :info :answered :open :submitted :complement-needed :sent})
(def post-verdict-states (difference all-application-states pre-verdict-states #{:canceled}))

(def pre-sent-application-states #{:draft :open :submitted :complement-needed})

(def post-submitted-states #{:sent :complement-needed :verdictGiven :constructionStarted :closed})

(def terminal-states #{:canceled :closed :final :extinct :registered})

(def all-but-draft-or-terminal (difference all-states #{:draft} terminal-states))
(def all-application-states-but-draft-or-terminal (difference all-application-states #{:draft} terminal-states))

(defn all-states-but [drop-states]
  (difference all-states (set drop-states)))

(defn all-application-states-but [drop-states]
  (difference all-application-states (set drop-states)))

(defn all-inforequest-states-but [drop-states]
  (difference all-inforequest-states (set drop-states)))

(def
  ^{:doc "Possible state transitions for inforequests.
          Key is the starting state, first in the value vector is the default next state and
          the rest are other possible next states."}
  default-inforequest-state-graph
  {:info     [:answered :canceled]
   :answered [:info]
   :canceled []})

(def
  ^{:doc "Possible state transitions for applications.
          Key is the starting state, first in the value vector is the default next state and
          the rest are other possible next states."}
  default-application-state-graph
  {:draft      [:open :submitted :canceled]
   :open       [:submitted :canceled]
   :submitted  [:sent :verdictGiven :canceled]
   :sent       [:verdictGiven :complement-needed :canceled]
   :complement-needed   [:sent :verdictGiven :cancelled]
   :verdictGiven        [:constructionStarted :canceled]
   :constructionStarted [:closed :cancelled]
   :closed   []
   :canceled []
   :extinct  [] ; Rauennut
   })

(def
  ^{:doc "See default-application-state-graph"}
  tj-ilmoitus-state-graph
  (merge
    (select-keys default-application-state-graph [:draft :open :closed :canceled])
    {:submitted    [:closed :canceled]}))

(def
  ^{:doc "See default-application-state-graph"}
  tj-hakemus-state-graph
  (merge
    (select-keys default-application-state-graph [:draft :open :canceled])
    {:submitted    [:sent :canceled]
     :sent         [:closed :complement-needed :canceled]
     :complement-needed [:closed :canceled]}))

; TODO draft versions this forward

(def
  ^{:doc "See default-application-state-graph"}
  ymp-application-state-graph
  (merge
    (select-keys default-application-state-graph [:draft :open :submitted :sent :complement-needed :canceled])
    {:verdictGiven [:final :appealed :canceled]
     :appealed     [:complement-needed :verdictGiven :final :canceled] ; Valitettu
     :final        [] ; Lain voimainen
     }))

(def
  ^{:doc "See default-application-state-graph"}
  tonttijako-application-state-graph
  (merge
    (select-keys default-application-state-graph [:draft :open :canceled])
    {:submitted [:hearing :canceled]
     :hearing [:proposal :canceledl]
     :proposal [:proposalApproved :canceled]
     :proposalApproved [:final :appealed :canceled]
     :appealed [:final :canceled] ; Oikaisuvaatimus
     :final    [] ; Lain voimainen
     }))

(def
  ^{:doc "See default-application-state-graph"}
  kt-application-state-graph
  (merge
    (select-keys default-application-state-graph [:draft :open :canceled]) ; is open needed?
    {:submitted [:survey :canceled]
     :survey [:sessionProposal :canceled] ; Maastotyot
     :sessionProposal [:sessionHeld :canceled] ; Kokouskutsu
     :sessionHeld [:registered :canceled] ; Kokous pidetty
     :registered [] ; Kiinteistorekisterissa
     }))

(comment
  (require ['rhizome.viz :as 'viz])
  (require ['lupapalvelu.i18n :as 'i18n])
  (doseq [sym ['default-inforequest-state-graph
               'default-application-state-graph
               'tj-hakemus-state-graph
               'tj-ilmoitus-state-graph
               'ymp-application-state-graph
               'tonttijako-application-state-graph
               'kt-application-state-graph]
          :let [g (var-get (resolve sym))
                filename (str "target/" (name sym) ".png")]]
    (viz/save-graph (keys g) g :node->descriptor (fn [n] {:label (str (i18n/localize "fi" (name n)) "\n(" (name n) ")")}) :filename filename)))
