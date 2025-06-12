(ns lupafront.test-preloads
  (:require [reagent.ratom :as ratom]
            [schema.core :as sc]))

(enable-console-print!)

;; Silence warning about "subscribe called outside reactive context"
(set! ratom/reactive? (constantly true))

(sc/set-fn-validation! true)

;; Mock some global JS functions
;; resources/private/common/util.js
(set! (.-util js/global) #js {:prop #js {:toHumanFormat (fn [& _] :FIXME)
                                         :toDbFormat    (fn [& _] :FIXME)}})
