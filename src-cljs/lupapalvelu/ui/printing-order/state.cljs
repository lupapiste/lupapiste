(ns lupapalvelu.ui.printing-order.state)

(def empty-component-state {:attachments []
                            :tagGroups   []
                            :order       {}
                            :id          nil})

(defonce component-state (atom empty-component-state))

(defn will-unmount
  [& _] (reset! component-state empty-component-state))