(ns lupapalvelu.ui.dom
  (:require [reagent.dom.client :as rdc]
            [schema.core :as sc]))

(defprotocol PDomMounter
  (-init-and-mount! [this dom-id params]
    "Persist the `dom-id` and mount")
  (-cleanup [this]
    "Unmounts any mounted components")
  (-mount! [this]
    "Try to mount the view"))

(defonce ^:private roots (atom {}))

(defn get-react-root [dom-id]
  (when dom-id
    (when-let [el (.getElementById js/document dom-id)]
      (or (get @roots dom-id)
          ;; Reagent create-root just calls React fn directly, we can use this for rum also.
          (let [root (rdc/create-root el)]
            (swap! roots assoc dom-id root)
            root)))))

(defn mount-component [dom-id params render-type view-fn]
  (when-let [^js root (get-react-root dom-id)]
    (when-let [view (view-fn)]
      (case render-type
        :reagent (rdc/render root (into [view] params))
        ;; Rum components can be mounted by React call directly
        :rum     (.render root view)))))

(defrecord DomMounter [*attrs render-type view-fn]
  PDomMounter
  (-mount! [this]
    ;; Does one DomMounter ever handle more than one dom-id?
    ;; Could cleanup the whole doseq away.
    (let [attrs (deref *attrs)]
      (doseq [[dom-id params] attrs]
        (mount-component dom-id params render-type view-fn)))
    ;; Returns cleanup fn for easier use from KO cljs-component wrapper
    (fn cleanup []
      (-cleanup this)))
  (-cleanup [_this]
    (doseq [[dom-id _] @*attrs]
      (when-let [^js root (get-react-root dom-id)]
        (.unmount root)
        (swap! roots dissoc dom-id))))
  (-init-and-mount! [this dom-id params]
    (swap! *attrs assoc dom-id params)
    (-mount! this)))

(sc/defn init
  "Initialize a mounter for given view:
   - `render-type` : Either Rum or Reagent
   - `view-fn` : A view producing function"
  [render-type :- (sc/enum :reagent :rum)
   view-fn :- (sc/pred fn?)]
  (->DomMounter (atom {}) render-type view-fn))

(defn mount!
  "Mount the view to the DOM into the element with the given `dom-id`"
  [mounter dom-id & params]
  (-init-and-mount! mounter dom-id params))

(defn reload!
  "Try to reload the view"
  [mounter]
  (-mount! mounter))

(defn ^:export ko-render
  "Helper for ko.init.js to use React 19"
  [component el]
  ;; Use el as key
  (let [^js root (or (get @roots el)
                     (let [root (rdc/create-root el)]
                       (swap! roots assoc el root)
                       root))]
    (.render root component)
    (fn cleanup []
      (.unmount root)
      (swap! roots dissoc el))))
