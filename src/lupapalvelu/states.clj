(ns lupapalvelu.states
  (:require [clojure.set :refer [difference union ]]
            [sade.strings :as ss]))

(def initial-state :draft)

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
   :complement-needed   [:sent :verdictGiven :canceled]
   :verdictGiven        [:constructionStarted :canceled]
   :constructionStarted [:closed :canceled]
   :closed   []
   :canceled []
   :extinct  [] ; Rauennut
   })

(def
  ^{:doc "See default-application-state-graph"}
  tj-ilmoitus-state-graph
  (merge
    (select-keys default-application-state-graph [:draft :open :canceled])
    {:submitted         [:closed  :canceled]
     :complement-needed [:closed]
     :closed            [:complement-needed]}))

(def
  ^{:doc "See default-application-state-graph"}
  tj-hakemus-state-graph
  (merge
    (select-keys default-application-state-graph [:draft :open :canceled :closed])
    {:submitted    [:sent :canceled]
     :sent         [:closed :complement-needed :canceled]
     :complement-needed [:sent :canceled]}))

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


(def pre-verdict-states #{:draft :info :answered :open :submitted :complement-needed :sent})

(def pre-sent-application-states #{:draft :open :submitted :complement-needed})

;;
;; Calculated state sets
;;

(def all-graphs
  (->>
    (ns-publics 'lupapalvelu.states)
    (filter #(ss/ends-with (name (first %)) "-graph"))
    (map (fn [v] @(second v)))))

(defn all-next-states
  "Returns a set of states that are after the start state in graph, including start state itself."
  [graph start & [results]]
  ;{:post [(do (println start results "returns" %) %)]}
  (let [results (set results)
        transitions (get graph start)]
    (cond
      (empty? transitions) #{start} ; terminal state
      (results start) results ; loop!
      :else (into (conj results start)
              (apply union (map #(all-next-states graph % (conj results start)) transitions))))))

(def post-verdict-states
 (let [graphs (filter :verdictGiven all-graphs)]
   (difference
     (apply union (map #(all-next-states % :verdictGiven) graphs))
     #{:canceled}
     ; ymp-application-state-graph loops back to pre verdict states
     pre-verdict-states)))

(def post-submitted-states
 (let [graphs (filter :submitted all-graphs)]
   (disj (apply union (map #(all-next-states % :verdictGiven) graphs)) :canceled :submitted)))

(def all-states (->> all-graphs (map keys) (apply concat) set))
(def all-inforequest-states (-> default-inforequest-state-graph keys set (disj :canceled)))
(def all-application-states (difference all-states all-inforequest-states))

(def terminal-states
  (->>
    all-graphs
    (map (fn [g] (->> g (filter #(empty? (second %))) (map first))))
    (apply concat)
    set))

(def all-but-draft-or-terminal (difference all-states #{:draft} terminal-states))
(def all-application-states-but-draft-or-terminal (difference all-application-states #{:draft} terminal-states))

(defn- drop-state-set [drop-states]
  (cond
    (and (= 1 (count drop-states)) (coll? (first drop-states))) (drop-state-set (first drop-states))
    (every? keyword? drop-states) (set drop-states)
    :else (throw (IllegalArgumentException. "Only keyword varargs or a single collection of keywords is supported"))))

(defn all-states-but [& drop-states]
  (difference all-states (drop-state-set drop-states)))

(defn all-application-states-but [& drop-states]
  (difference all-application-states (drop-state-set drop-states)))

(defn all-inforequest-states-but [& drop-states]
  (difference all-inforequest-states (drop-state-set drop-states)))


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
    (viz/save-graph (keys g) g :node->descriptor (fn [n] {:label (str (i18n/localize "fi" (name n)) "\n(" (name n) ")")}) :filename filename))
  )
