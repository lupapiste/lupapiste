(ns lupapalvelu.ui.docgen.feature.focus
  (:require ["react" :as react]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.docgen.component.accordion-state :as accordion-state]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [sade.util :as util]
            [schema.core :as sc]))

;; NOTE: These parameters are also referenced in `scroll-service.js`
;;       to stop the scroll service from scrolling too eagerly.
(def focus-query-params {(sc/optional-key :focus-doc)  sc/Str
                         (sc/optional-key :focus-path) sc/Str})

(rf/reg-sub ::focus-path
  :<- [:route/query-parameters]
  :-> :focus-path)

(rf/reg-sub ::focus-doc
  :<- [:route/query-parameters]
  :-> :focus-doc)

(rf/reg-event-fx ::focused
  (fn [{:keys [db]} [_ focus-args]]
    {:db (assoc db ::focused focus-args)}))

(rf/reg-sub ::last-focus-args :-> ::focused)

(defn open-accordion-for-focus-fx
  "Open target accordion if we're about to focus on a field within."
  [{:keys [db]}]
  (let [{:keys [focus-doc]} (router/db->query-parameters db)]
    {:db (cond-> db
           ;; We're about to focus on a field in a moment
           (and focus-doc (nil? (::focused db)) )
           (accordion-state/toggle-accordion focus-doc true))}))

(defn forget-focus-fx
  "Forget if we've previously focused on a field

  1. click on a link to focus on a field -> should open page and focus
  2. move to another page -> should forget focus
  3. click on a link to focus on the same field -> should open page and focus again  "
  [{:keys [db]}]
  {:db (dissoc db ::focused)})

(defn use-focus
  "Given a `doc-id` and an optional `path` identifying a field, returns a React ref.
  The returned ref should be assigned to the corresponding target element as `:ref`.
  If the given `doc-id` and `path` should be in focus, this effect will scroll to the
  input and set focus to it.

  The enclosing component should be a functional one to support hooks (e.g. use `:f>`)."
  ([doc-id]
   (use-focus doc-id nil))
  ([doc-id path]
   (let [should-focus?    (and (= doc-id (<sub [::focus-doc]))
                               ;; focusing on a doc in general or a specific path within?
                               (or (and (nil? path)
                                        (nil? (<sub [::focus-path])))
                                   (= (path/path->str path) (<sub [::focus-path]))))
         focus-args       (when should-focus?
                            (util/mmap doc-id path))
         already-focused? (when should-focus?
                            (= focus-args
                               (<sub [::last-focus-args])))
         ref              (react/useRef nil)]
     (react/useEffect
       (fn []
         (let [node (.-current ref)]
           (if (and node
                    should-focus?
                    ;; Don't re-focus if we've already focused on the element.
                    ;; This might happen if we close the accordion and re-open it
                    ;; => we don't want to immediately focus on the field again
                    (not already-focused?))
             (let [timeout (js/setTimeout
                             (fn []
                               (let [node (.-current ref)]
                                 (.scrollIntoView node #js {:behavior "auto"
                                                            :block    (if (:path focus-args)
                                                                        ;; put target field in the center of the screen
                                                                        "center"
                                                                        ;; put target document accordion at the start of the screen
                                                                        "start")})
                                 (.focus node)
                                 (>evt [::focused focus-args])))
                             ;; If the target accordion is closed, we need to give it a bit
                             ;; of time to open.
                             100)]
               (fn []
                 ;; cleanup the timeout just in case
                 (js/clearTimeout timeout)))
             js/undefined)))
       #js [should-focus? already-focused? focus-args])
     ref)))
