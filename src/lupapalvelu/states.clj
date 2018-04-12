(ns lupapalvelu.states
  (:require [clojure.set :refer [difference union]]
            [lupapiste-commons.states :as common-states]
            [sade.strings :as ss]
            [sade.util :refer [map-values]]))

(defn initial-state [graph]
  {:pre [(map? graph)], :post [%]}
  (cond
    ; Check the most common case first
    (contains? graph :draft) :draft

    ; First key of an ordered map
    (instance? clojure.lang.PersistentArrayMap graph) (first (keys graph))

    ; Fallback calculation. (A state can't be the initial state without transitions.)
    :else
    (let [states  (into #{} (for [[k v] graph :when (seq v)] k))
          targets (->> graph vals (apply concat) set)
          initial-states (difference states targets)]
      (assert (= 1 (count initial-states)))
      (first initial-states))))

(def ^{:doc "Possible state transitions for applications.
          Key is the starting state, first in the value vector is the default next state and
          the rest are other possible next states."}
  full-application-state-graph
  common-states/full-application-state-graph)

(def
  ^{:doc "Possible state transitions for inforequests.
          Key is the starting state, first in the value vector is the default next state and
          the rest are other possible next states."}
  default-inforequest-state-graph
  (array-map
    :info     [:answered :canceled]
    :answered [:info]
    :canceled []))

(def default-application-state-graph common-states/default-application-state-graph)

(def ^{:doc "See default-application-state-graph"}
  tj-ilmoitus-state-graph common-states/tj-ilmoitus-state-graph)

(def ^{:doc "See default-application-state-graph"}
  tj-hakemus-state-graph common-states/tj-hakemus-state-graph)

;; New YA states

(def ya-kayttolupa-state-graph common-states/ya-kayttolupa-state-graph)
(def ya-sijoituslupa-state-graph common-states/ya-sijoituslupa-state-graph)
(def ya-sijoitussopimus-state-graph common-states/ya-sijoitussopimus-state-graph)
(def ya-jatkoaika-state-graph common-states/ya-jatkoaika-state-graph)
(def ya-tyolupa-state-graph default-application-state-graph)

(def ark-state-graph common-states/ark-state-graph)

(def r-jatkoaika-state-graph common-states/r-jatkoaika-state-graph)
(def r-muutoslupa-state-graph common-states/r-muutoslupa-state-graph)

; TODO draft versions this forward

(def
  ^{:doc "See default-application-state-graph"}
  ymp-application-state-graph
  (merge
    (select-keys default-application-state-graph [:draft :open :submitted :complementNeeded :canceled])
    {:sent [:verdictGiven :complementNeeded :canceled]
     :verdictGiven [:final :appealed :canceled]
     :appealed     [:verdictGiven :final :canceled] ; Valitettu
     :final        [] ; Lain voimainen
     }))

(def ^{:doc "See default-application-state-graph"}
  tonttijako-application-state-graph common-states/tonttijako-application-state-graph)

(def
  ^{:doc "See default-application-state-graph"}
  kt-application-state-graph
  (merge
    (select-keys default-application-state-graph [:draft :open :canceled]) ; is open needed?
    {:submitted [:survey :draft :canceled]
     :survey [:sessionProposal :canceled] ; Maastotyot
     :sessionProposal [:sessionHeld :canceled] ; Kokouskutsu
     :sessionHeld [:registered :canceled] ; Kokous pidetty
     :registered [] ; Kiinteistorekisterissa
     }))

(def
  ^{:doc "States for bulletin version snapshot"}
  bulletin-version-states
  {:proclaimed [:verdictGiven]
   :verdictGiven [:final]
   :final []})


(def pre-verdict-states #{:draft :info :answered :open :submitted :complementNeeded :sent :underReview})

(def pre-sent-application-states #{:draft :open :submitted :complementNeeded :underReview})

(def create-doc-states #{:draft :answered :open :submitted :complementNeeded :underReview})

(def update-doc-states #{:draft :open :submitted :complementNeeded :underReview})

(def approve-doc-states #{:open :submitted :complementNeeded :underReview})


;;
;; Calculated state sets
;;

(def ^:private all-graphs
  "A looback from 'submitted' to 'draft' was allowed for LPK-2345. This is ignored for the purposes of calculating the state graphs."
  (->>
    (ns-publics 'lupapalvelu.states)
    (filter #(ss/ends-with (name (first %)) "-graph"))
    (map (fn [v] @(second v)))
    (map (fn [graph]
           (map-values #(into [] (remove (partial = :draft) %))
                       graph)))))

(defn all-next-states
  "Returns a set of states that are after the start state in graph, including start state itself.
   If start is not part of the graph, empty set will be returned."
  [graph start & [results]]
  (let [results (set results)
        transitions (get graph start)]
    (cond
      (not (contains? graph start)) #{}
      (empty? transitions) #{start} ; terminal state
      (results start) results ; loop!
      :else (into (conj results start)
              (apply union (map #(all-next-states graph % (conj results start)) transitions))))))

(def verdict-given-states #{:verdictGiven                   ; R + others
                            :foremanVerdictGiven :acknowledged ; foreman applications
                            :finished :agreementPrepared :agreementSigned  ; YA cases
                            :archived ; Archiving projects (ARK)
                            :ready    ; R jatkoaika
                            })

(def post-verdict-states
 (let [graphs (filter (comp (partial some verdict-given-states) keys) all-graphs)]
   (difference
     (apply union (map (fn [g] (apply union (map #(all-next-states g %) verdict-given-states))) graphs))
     #{:canceled}
     ; ymp-application-state-graph loops back to pre verdict states
     pre-verdict-states)))

(def ya-post-verdict-states
  (disj post-verdict-states :agreementPrepared))

(def post-submitted-states
  "Submitted state and all states after. Cancelled omitted."
  (let [graphs (filter :submitted all-graphs)]
    (disj (apply union (map #(all-next-states % :submitted) graphs)) :canceled)))

(def post-sent-states
  "Sent state and all states after. Cancelled omitted."
  (let [graphs (filter :sent all-graphs)]
    (disj (apply union (map #(all-next-states % :sent) graphs)) :canceled)))

(def all-states (->> all-graphs (map keys) (apply concat) set))
(def all-inforequest-states (-> default-inforequest-state-graph keys set (disj :canceled)))
(def all-archiving-project-states (-> ark-state-graph keys set))
(def all-application-states (difference all-states all-inforequest-states (disj all-archiving-project-states :canceled :open)))
(def all-application-or-archiving-project-states (difference all-states all-inforequest-states))

(defn terminal-state? [graph state]
  {:pre [(map? graph) (keyword? state)]}
  (let [transitions (graph state)
        loopback-transitions (-> (first transitions) graph set (disj :canceled))]
    (or
      (empty? transitions)
      (and
        (= 1 (count transitions))
        (= 1 (count loopback-transitions))
        (= (first loopback-transitions) state)))))

(def terminal-states
  (->>
    all-graphs
    (map (fn [g] (->> g (filter #(terminal-state? g (first %))) (map first))))
    (apply concat)
    set))

(def give-verdict-states (union #{:submitted :sent}
                                (difference verdict-given-states
                                            terminal-states)))

(def all-but-draft-or-terminal (difference all-states #{:draft} terminal-states))
(def all-application-states-but-draft-or-terminal (difference all-application-states #{:draft} terminal-states))

(def pre-verdict-but-draft (difference pre-verdict-states #{:draft}))
(def all-but-draft (difference all-states #{:draft}))

(def post-verdict-but-terminal (difference post-verdict-states terminal-states))

(def archival-final-states (-> terminal-states
                               (disj :extinct :canceled :answered :registered)
                               (conj :foremanVerdictGiven :underReview)))

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

(defn all-application-or-archiving-project-states-but [& drop-states]
  (difference all-application-or-archiving-project-states (drop-state-set drop-states)))

(def all-application-or-archiving-project-states-but-draft-or-terminal
  (all-application-or-archiving-project-states-but (conj terminal-states :draft)))

(comment
  (require ['rhizome.viz :as 'viz])
  (require ['lupapalvelu.i18n :as 'i18n])
  (doseq [sym (->>
                (ns-publics 'lupapalvelu.states)
                (filter #(ss/ends-with (name (first %)) "-graph"))
                (map first))
          :let [g (var-get (resolve sym))
                filename (str "target/" (name sym) ".png")]]
    (viz/save-graph (keys g) g :node->descriptor (fn [n] {:label (str (i18n/localize "fi" (name n)) "\n(" (name n) ")")}) :filename filename))
  )
