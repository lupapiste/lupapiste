(ns lupapalvelu.states
  (:require [clojure.set :refer [difference union]]))


(def all-application-states #{:draft :open :submitted :sent :complement-needed
                              :verdictGiven :constructionStarted :closed :canceled})
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
  {:draft      [:open :canceled]
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

; TODO draft versions this forward

(def
  ^{:doc "See default-application-state-graph"}
  ymp-application-state-graph
  (merge
    (select-keys default-application-state-graph [:draft :open :submitted :sent :complement-needed :canceled])
    {:verdictGiven [:final :appealed]
     :appealed     [:complement-needed :verdictGiven :final] ; Valitettu
     :final        [] ; Lain voimainen
     }))

(def
  ^{:doc "See default-application-state-graph"}
  tonttijako-application-state-graph
  (merge
    (select-keys default-application-state-graph [:draft :open])
    {:submitted [:hearing]
     :hearing [:proposal]
     :proposal [:proposalApproved]
     :proposalApproved [:final :appealed]
     :appealed [:final] ; Valitettu
     :final    [] ; Lain voimainen
     }))

(def
  ^{:doc "See default-application-state-graph"}
  kt-application-state-graph
  (merge
    (select-keys default-application-state-graph [:draft :open]) ; is open needed?
    {:submitted [:survey]
     :survey [:sessionProposal] ; Maastotyot
     :sessionProposal [:sessionHeld] ; Kokouskutsu
     :sessionHeld [:registered] ; Kokous pidetty
     :registered [] ; Kiinteistorekisterissa
     }))
