(ns lupapalvelu.conversion.cli
  "Command-line parsing for `conversion` and `other` batchruns. A batchrun invocation
  consists of a subcommand and its arguments."
  (:require [babashka.cli :as cli]
            [clojure.set :as set]
            [babashka.fs :as fs]
            [lupapalvelu.conversion.schemas :refer [other-ts-fields
                                                    valid-date-or-range?]]
            [sade.strings :as ss]
            [lupapalvelu.states :as states]
            [sade.util :as util]))

(defn- valid-range? [[from to :as xs]]
  (and (= 2 (count xs)) (<= from to)))

(def conversion-spec
  {:cfgfile         {:desc     "Conversion configuration EDN file. See `ConversionEdn`."
                     :validate fs/exists?
                     :alias    :configuration.edn}
   :organization-id {:desc   "Target organization id."
                     :coerce :string
                     :alias  :org-id}
   :relink          {:desc   "Also relink the previously linked applications."
                     :coerce :boolean}
   :repair          {:desc   "Repair/reformat/replace backend-id links."
                     :coerce :boolean}
   :state-changes   {:desc   "Send state-change messages."
                     :coerce :boolean
                     :alias  :s}
   :last-state      {:desc   "Send state-change message _only_ for the _last_ application state"
                     :coerce :boolean}
   :kuntagml        {:desc   "Send the _original_ KuntaGML messages augmented with the converted application ids."
                     :coerce :boolean
                     :alias  :k}
   :dry-run         {:desc   "Only logs what is happening but does not actually remove anything."
                     :coerce :boolean
                     :alias  :d}
   :force           {:desc   "Loosens some deletion checks and thus can clean conversions that would be left untouched otherwise."
                     :coerce :boolean
                     :alias  :f}
   :tags            {:desc   "Only conversion documents that match EVERY tag are considered. Overrides the tags defined in the configuration.edn"
                     :coerce []
                     :alias  :t}
   :add             {:desc "Tags to be added" :coerce []}
   :remove          {:desc "Tags to be removed" :coerce []}
   :range           {:desc     "Limits target documents to the running number range, inclusive."
                     :coerce   [:int]
                     :validate valid-range?
                     :alias    :r}
   :number          {:ref      "<n>"
                     :coerce   :int
                     :validate pos?
                     :desc     "If given, renumbers the target documents. Starts with `<n>`."
                     :alias    :n}
   :years           {:coerce [:int]
                     :desc   "Limit the conversion documents to given years."
                     :alias  :y}
   :limit           {:desc     "Limit the number of the processed targets to maximum of <n>."
                     :ref      "<n>"
                     :coerce   :int
                     :validate pos?
                     :alias    :l}
   :threads         {:desc     "Thread pool size (the default is command specific but typically 8)."
                     :coerce   :int
                     :validate pos?}
   :retry           {:desc   "Only try to convert those documents that failed on the last conversion."
                     :coerce :boolean}
   :location-relax  {:desc   "Allow fix-locations to update locations without reset-location information (explicit or implicit)."
                     :coerce :boolean
                     :alias  :relax}})

(def conversion-commands
  [{:cmd         :store
    :call        "store configuration.edn"
    :description "Upserts conversion document with KuntaGML message. The message is either
  read from a file or fetched from the backing system. If the document already has the
  message, it can be overwritten with `overwrite?` configuration option."
    :args        [:cfgfile]
    :opts        [:threads]}
   {:cmd         :convert
    :call        "convert configuration.edn [options]"
    :description "Converts KuntaGML messages into Lupapiste applications. The messages
    must have been stored earlier (see `store` command)."
    :args        [:cfgfile]
    :opts        [:tags :range :years :limit :threads :retry]}
   {:cmd         :link
    :call        "link configuration.edn [options]"
    :description "Updates the link permits of the converted applications."
    :args        [:cfgfile]
    :opts        [:relink :tags :range :years :limit :repair]}
   {:cmd         :send
    :call        "send configuration.edn [options]"
    :description "Sends conversion results to dmcity. State-related options are mutually exclusive."
    :args        [:cfgfile]
    :opts        [:state-changes :kuntagml :last-state
                  :tags :range :years :limit]}
   {:cmd         :tag
    :call        "tag configuration.edn [options]"
    :description "Modify conversion documents' tags."
    :args        [:cfgfile]
    :opts        [:add :remove :number :range :years :limit]}
   {:cmd         :clean
    :call        "clean configuration.edn [options]"
    :description "Removes erroneous conversions. Should not be needed currently."
    :args        [:cfgfile]
    :opts        [:dry-run :force]}
   {:cmd         :nuke
    :call        "nuke configuration.edn [options]"
    :description "Like `clean` but removes every target conversion (both application and document). Not available in every environment."
    :args        [:cfgfile]
    :opts        [:dry-run]}
   {:cmd         :backend-ids
    :call        "backend-ids configuration.edn"
    :description "Adds missing backend-ids to the converted applications."
    :args        [:cfgfile]
    :opts        [:tags :range :years :limit]}
   {:cmd         :fix-history
    :call        "fix-history configuration.edn"
    :description "Reinitializes converted applications history and state information. Manually changed states are not reset."
    :args        [:cfgfile]
    :opts        [:tags :range :years :limit]}
   {:cmd         :import
    :call        "import configuration.edn"
    :description "Imports data as defined in the configuration file."
    :args        [:cfgfile]}
   {:cmd         :fix-verdicts
    :call        "fix-verdicts configuration.edn"
    :description "Updates applications with verdicts and reviews from KuntaGML."
    :args        [:cfgfile]}
   {:cmd         :fix-reviews
    :call        "fix-reviews configuration.edn"
    :description "Updates applications with reviews from KuntaGML."
    :args        [:cfgfile]
    :opts        [:limit :threads :tags :range :years]}
   {:cmd         :fetch-review-attachments
    :call        "fetch-review-attachments configuration.edn [options]"
    :description "Fetches missing review attachments for converted applications according to document KuntaGML."
    :args        [:cfgfile]
    :opts        [:limit :threads :tags :range :years]}
   {:cmd         :fix-foremen
    :call        "fix-foremen configuration.edn [options]"
    :description "Updates foreman responsibility period according to the document KuntaGML."
    :args        [:cfgfile]
    :opts        [:limit :tags :range :years]}
   {:cmd         :fix-locations
    :call        "fix-locations configuration.edn [options]"
    :description "Upserts both conversion documents and converted applications according to `:location-overrides` configuration."
    :args        [:cfgfile]
    :opts        [:limit :tags :range :years :location-relax :threads]}
   {:cmd         :fix-states
    :call        "fix-states configuration.edn [options]"
    :description "Recalculates and updates (if necessary) the current states of converted applications."
    :args        [:cfgfile]
    :opts        [:limit :tags :range :years :threads]}
   {:cmd         :help
    :call        "help"
    :description "Shows this help."}])

(defn join-lines [lines]
  (ss/join \newline (flatten lines)))

(defn- paragraph
  ([text prefix max-width]
   (let [max-width (- max-width (count prefix) 1)
         words     (ss/split text #"\s+")]
     (loop [lines   []
            current (first words)
            [x & xs] (rest words)]
       (cond
         (nil? x)
         (->> (conj lines current)
              (keep #(some->> % ss/blank-as-nil (str prefix)))
              join-lines)

         (> (+ (count x) (count current)) max-width)
         (recur (conj lines (str current)) x xs)

         :else (recur lines (str current " " x) xs)))))
  ([text prefix] (paragraph text prefix 79))
  ([text] (paragraph text "  ")))

(defn- cmd-help
  ([{:keys [spec]}{:keys [cmd call description opts]} error-msg]
   (let [err? (ss/not-blank? error-msg)]
     (join-lines (cond-> []
                   err?        (conj (str "Error: " error-msg)
                                     (format "Command `%s` usage:" (name cmd)))
                   call        (conj "" call)
                   description (conj "" (paragraph description))
                   opts        (conj "" " Options:"
                                     (cli/format-opts {:spec (select-keys spec opts)}))))))
  ([cfg command] (cmd-help cfg command nil)))

(defn help [{:keys [commands category] :as cfg}]
  (join-lines ["Batchrun calling convention:"
               ""
               (str category " command param1 param2 ...")
               ""
               "where the supported commands, their parameters and (non-mandatory) [options] are:"
               (map (partial cmd-help cfg) commands)]))

(defn- parse-command-line [{:keys [commands spec require-args? category] :as cfg}
                          cmd-candidate & cmd-args]
  (let [{:keys [cmd args opts]
         :as   command} (util/find-by-key :cmd (keyword cmd-candidate) commands)
        params          (concat args opts)]
    (cond
      (or (= cmd :help) (ss/blank? cmd-candidate)) {:info (help cfg)}

      (nil? cmd) {:info (join-lines [(format "Unknown %s command: %s"
                                             category cmd-candidate)
                                     (help cfg)])}
      :else      (try
                   (assoc (cli/parse-opts (ss/trimwalk cmd-args)
                                          (cond-> {:args->opts args
                                                   :restrict   params
                                                   :spec       (select-keys spec params)}
                                            require-args? (assoc :require args)))
                          :cmd cmd
                          :cmd-help (partial cmd-help cfg))
                   (catch Exception e
                     {:error (cmd-help cfg command (ex-message e))})))))

(def parse-conversion-command-line (partial parse-command-line
                                            {:category      "conversion"
                                             :commands      conversion-commands
                                             :spec          conversion-spec
                                             :require-args? true}))

(def date-range-spec
  {:ref      "<from> [<to>]"
   :validate valid-date-or-range?
   :desc     "Application timestamp filter. Either a date or date range (from start of day to end of day, inclusive)."
   :coerce   []})

(def other-spec
  (->> other-ts-fields
       (map #(vector % date-range-spec))
       (into {:cfgfile           {:desc     "Other configuration EDN file."
                                  :validate fs/exists?
                                  :alias    :configuration.edn}
              :organization-id   (:organization-id conversion-spec)
              :permit-type       {:desc   "Permit type of the target applications"
                                  :coerce :string}
              :application-ids   {:desc   "Explicit list of the target application ids."
                                  :coerce []
                                  :alias  :app-ids}
              :all               {:desc   "Send every state change (from history)"
                                  :coerce :boolean}
              :last              {:desc   "Send only the last state change (from history)"
                                  :coerce :boolean}
              :application       {:desc   "Send application KuntaGML message"
                                  :coerce :boolean
                                  :alias  :a}
              :verdict           {:desc   "Send verdict KuntaGML messages"
                                  :coerce :boolean
                                  :alias  :v}
              :review            {:desc   "Send review KuntaGML messages"
                                  :coerce :boolean
                                  :alias  :r}
              :auto              {:desc   "Sends the appropriate KuntaGML messages according to the application contents."
                                  :coerce :boolean}
              :force-path        {:desc     "Sends messages to either `conversion` or `application` path regardless of the application contents."
                                  :validate #{:application :conversion :a :c}
                                  :coerce   :keyword
                                  :alias    :fp}
              :include-converted {:desc   "Sends also converted applications"
                                  :coerce :boolean
                                  :alias  :ic}
              :fetch             {:desc   "Fetch KuntaGML from the backing system instead of mongo."
                                  :coerce :boolean}
              :states            {:desc     "Target application (post-verdict) states."
                                  :coerce   [:keyword]
                                  :validate (fn [xs]
                                              (set/subset? (set xs) states/post-verdict-states))}})))

(def other-commands
  [{:cmd         :backend-ids
    :call        "backend-ids [configuration.edn] [options]"
    :description "Fetches KuntaGML for each appropriate application and updates `kuntalupatunnus` and `vrk-lupatunnus` if needed."
    :args        [:cfgfile]
    :opts        (concat other-ts-fields [:organization-id :permit-type :application-ids])}
   {:cmd         :help
    :call        "help"
    :description "Shows this help."}
   {:cmd         :send-states
    :call        "send-states [configuration.edn] [options]"
    :description "Sends state-change messages to dmcity."
    :args        [:cfgfile]
    :opts        [:organization-id :permit-type :application-ids :all :last
                  :force-path :include-converted]}
   {:cmd         :send-kuntagml
    :call        "send-kuntagml [configuration.edn] [options]"
    :description "Generates KuntaGML messages and sends them to dmcity."
    :args        [:cfgfile]
    :opts        [:organization-id :permit-type :application-ids :application
                  :verdict :review :force-path :include-converted :auto]}
   {:cmd         :fix-end-reviews
    :call        "fix-end-reviews [configuration.edn] [options]"
    :description "Updates review building states according to backing system KuntaGML messages."
    :args        [:cfgfile]
    :opts        [:organization-id :application-ids]}
   {:cmd         :fix-review-notes
    :call        "fix-review-notes [configuration.edn] [options]"
    :description "Updates review notes according to backing system KuntaGML messages."
    :args        [:cfgfile]
    :opts        [:organization-id :application-ids :fetch]}
   {:cmd         :fix-missing-reviews
    :call        "fix-missing-reviews [configuration.edn] [options]"
    :description "Updates reviews and adds missing ones according to backing system KuntaGML messages. The default target state is `closed`."
    :args        [:cfgfile]
    :opts        [:organization-id :application-ids :fetch :states]}
   {:cmd         :vtj-prts
    :call        "vtj-prts [configuration.edn] [options]"
    :description "Resolves missing document VTJ-PRTs (building ids) according to buildings array."
    :args        [:cfgfile]
    :opts        [:organization-id :application-ids]}])

(def parse-other-command-line (partial parse-command-line
                                       {:category "other"
                                        :commands other-commands
                                        :spec     other-spec}))
