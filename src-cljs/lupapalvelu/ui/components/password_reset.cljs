(ns lupapalvelu.ui.components.password-reset
  "See `resources/private/password-reset/password-reset.*`."
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.components.password-field :as pw]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [sade.strings :as ss]
            [schema.core :as sc :refer [defschema]]))

(defn- validate-password-min-length
  [k]
  (fn [state]
    (when-let [v (get state k)]
      (when-not (pw/valid-password-length? v)
        :error.password.minlength))))

(defn- validate-password-quality
  [k]
  (fn [state]
    (when-let [v (get state k)]
      (when-not (pw/valid-password-quality? v)
        ;; Password field already indicates quality,
        ;; use empty string to indicate error without error message
        ""))))

(defn- validate-password-equal
  [k1 k2]
  (fn [state]
    (let [v1 (get state k1)
          v2 (get state k2)]
      (when (not= v1 v2)
        :error.equal))))

;; To mimic the old `password-reset.html` behaviour, assign different
;; validators to each of the two password fields
(def ^:private validators
  {::error-msg-1 (some-fn (validate-password-min-length ::password-1)
                          (validate-password-quality ::quality))
   ::error-msg-2 (validate-password-equal ::password-1 ::password-2)})

(defn- apply-validators
  [state]
  (reduce
    (fn [state [error-k validator]]
      (assoc state error-k (validator state)))
    state
    validators))

(defn- update-state
  [state k v]
  (-> state
      (assoc k (ss/blank-as-nil v))
      apply-validators))

(defn- update-state!
  [*state k v]
  (r/rswap! *state update-state k v))

(defn- password-primary
  [*state {:keys [quality-options disabled?]
           :or   {disabled? false}}]
  (r/with-let [*password       (r/cursor *state [::password-1])
               *quality        (r/cursor *state [::quality])
               *show-passwords (r/cursor *state [::show-passwords?])
               *error-msg      (r/cursor *state [::error-msg-1])]
    (let [password  (deref *password)
          quality   (deref *quality)
          error-msg (deref *error-msg)]
      [pw/password-field (cond-> {:id             "setpw-password-1"
                                  :label          (common/loc :setpw.password.1)
                                  :show-password? (deref *show-passwords)
                                  :disabled?      disabled?
                                  :value          password
                                  :on-change      (fn [v]
                                                    (pw/get-password-quality-async
                                                      {:password v
                                                       :callback (fn [v]
                                                                   (update-state! *state ::quality v))
                                                       :opts     quality-options})
                                                    (update-state! *state ::password-1 v))}
                           (and password quality)     (assoc :quality quality)
                           (and password
                                (keyword? error-msg)) (assoc :error-message (common/loc error-msg)))])))

(defn- password-secondary
  [*state {:keys [disabled?]
           :or   {disabled? false}}]
  (r/with-let [*password       (r/cursor *state [::password-2])
               *show-passwords (r/cursor *state [::show-passwords?])
               *error-msg      (r/cursor *state [::error-msg-2])]
    (let [password  (deref *password)
          error-msg (deref *error-msg)]
      [pw/password-field (cond-> {:id             "setpw-password-2"
                                  :label          (common/loc :setpw.password.2)
                                  :show-password? (deref *show-passwords)
                                  :disabled?      disabled?
                                  :value          password
                                  :on-change      (fn [v]
                                                    (update-state! *state ::password-2 v))}
                           (and password
                                (keyword? error-msg)) (assoc :error-message (common/loc error-msg)))])))

(defn- show-password
  [*state {:keys [disabled?]
           :or   {disabled? false}}]
  (r/with-let [*show-passwords (r/cursor *state [::show-passwords?])
               id              "password-visibility-toggle-reset"]
    [:div.show-password.gap--v2
     [ui-input/checkbox
      {:id        id
       :disabled? disabled?
       :checked?  (boolean (deref *show-passwords))
       :on-change (fn [checked?] (reset! *show-passwords checked?))}]
     [:label {:for id}
      (common/loc :a11y.show-passwords)]]))

(defn- state->valid-password
  [state]
  (let [{::keys [password-1]} state]
    (when (and (seq (::password-1 state))
               (seq (::password-2 state))
               (nil? (::error-msg-1 state))
               (nil? (::error-msg-2 state)))
      password-1)))

(defschema PasswordResetOpts
  {:on-change                   (sc/pred fn? 'on-change-callback)
   ;; Caller should supply us with the necessary details (e.g. token, email, ..)
   ;; so that we may ask the backend to evaluate the password
   :quality-options             pw/PasswordQualityOpts
   (sc/optional-key :disabled?) sc/Bool})

(sc/defn view
  "Two password fields and a `show passwords?` button"
  [{:keys [on-change] :as opts} :- PasswordResetOpts]
  ;; Keep state in an atom so that we don't accidentally store
  ;; passwords in re-frame app-db etc.
  (r/with-let [*state (r/atom {::password-1      nil
                               ::password-2      nil
                               ::error-msg-1     nil
                               ::error-msg-2     nil
                               ::quality         nil
                               ::show-passwords? false})
               ;; Use a reaction to dedupe the password value so that we only
               ;; invoke `on-change` when the value actually changes
               *valid (ratom/make-reaction (fn [] (state->valid-password (deref *state))))
               ;; Keep invoking `on-change` every time `*valid` changes
               *track (r/track! (fn [] (on-change (deref *valid))))]
    [:div.flex--column.flex--gap2
     [password-primary *state opts]
     [password-secondary *state opts]
     [show-password *state opts]]
    (finally
      (r/dispose! *track))))
