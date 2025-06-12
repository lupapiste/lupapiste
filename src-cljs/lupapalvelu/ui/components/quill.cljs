(ns lupapalvelu.ui.components.quill
  "Low-level ClojureScript for Quill editor. See `lupapalvelu.quill` for more information."
  (:require ["react-quill-new$default" :as Quill]
            [lupapalvelu.ui.common :as common]
            [rum.core :as rum]))

(def minimal-toolbar
  [[{:header [1 2 3 4 false]}]
   [:bold :italic :underline]
   [{:list :bullet} {:list :ordered}]
   [{:color []} {:background []}]])

(def full-toolbar
  [[{:header [1 2 3 4 false]}]
   [:bold :italic :underline]
   [:strike :code {:script :super}]
   [:blockquote]
   [{:indent "-1"} {:indent "+1"}]
   [{:align []}]
   [{:list :bullet} {:list :ordered}]
   [{:color []} {:background []}]
   [:link :image]
   [:clean]])

(defn quill
  "Simple Quill editor with the reasonable defaults. Typically not used directly from the
  client (view) code, but via `lupapalvelu.ui.components.quill/quill-edit`."
  [{:keys [value read-only] :as options}]
  (js/React.createElement
    Quill
    (common/->js-options
      (merge {:theme   :snow
              :modules {:toolbar (when-not read-only
                                   full-toolbar)}
              :formats [:header :bold :italic :underline :strike :code
                        :script :blockquote :indent :align :list :color
                        :background :link :image]
              :value   (if (sequential? value)
                         (clj->js value)
                         value)}
             options))))

(rum/defc quill-edit
  "Controlled wrapper for Quill editor. Parameters:

   `initial-value` `options`

   Initial value can be anything that Quill supports. Typically either list of Quill
   operations or string (html or plain). Special (and optional) options:

   `disabled?` If truthy, the editor is in the read-only mode and the toolbar is not
               shown.

   `class` Additional classes for the editor.

   `callback` Function that receives the _full contents_ of the editor (as Quill
             operations), whenever _the user_ makes an edit. In other words, the new
             contents given via `initial-value` do not trigger callback.

    All the other options are passed as such to the underlying `quill/quill` wrapper."
  [initial-value {:keys [disabled? callback class]
                  :as   options}]
  (let [[value set-value!] (rum/use-state initial-value)]
    (rum/use-effect! (fn []
                       (set-value! initial-value)
                       js/undefined)
                     [initial-value])
    (quill (merge {:value      value
                   :on-change  (when callback
                                 (fn [content _delta source editor]
                                   (when (= source "user")
                                     (set-value! content)
                                     (let [ops (some-> (.getContents editor)
                                                       (common/oget :ops)
                                                       (common/->cljs))]
                                       (callback ops)))))
                   :class-name class
                   :read-only  disabled?}
                  (dissoc options [:disabled? :callback :class])))))
