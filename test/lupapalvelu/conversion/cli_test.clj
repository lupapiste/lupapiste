(ns lupapalvelu.conversion.cli-test
  (:require [babashka.fs :as fs]
            [lupapalvelu.conversion.cli :as cli]
            [midje.sweet :refer :all]))

(defn parse-check [k text]
  (fn [m]
    (re-find (re-pattern text) (k m))))

(def parse-error (partial parse-check :error))
(def parse-info (partial parse-check :info))

(against-background
  [(fs/exists? "good.edn" nil) => true
   (fs/exists? "bad.edn" nil) => false]
  (facts "parse-conversion-command-line"

    (facts "Bad arguments"
      (cli/parse-conversion-command-line "bad") => (parse-info "Unknown conversion command")
      (cli/parse-conversion-command-line "store" :bad-arg) => (parse-error "Unknown option")
      (cli/parse-conversion-command-line "store" "bad.edn") => (parse-error "Invalid value")
      (cli/parse-conversion-command-line "store" "good.edn" "-foo")
      => (parse-error "Unknown option")
      (cli/parse-conversion-command-line "store") => (parse-error "Required option")
      (cli/parse-conversion-command-line "tag" "good.edn" "--range" "h")
      => (parse-error "Coerce failure")
      (cli/parse-conversion-command-line "tag" "good.edn" "--range" "10")
      => (parse-error "Invalid value")
      (cli/parse-conversion-command-line "tag" "good.edn" "--range" "10" "20" "30")
      => (parse-error "Invalid value")
      (cli/parse-conversion-command-line "tag" "good.edn" "-r" "20" "10")
      => (parse-error "Invalid value")
      (cli/parse-conversion-command-line "tag" "good.edn" "-n" "bad")
      => (parse-error "Coerce failure")
      (cli/parse-conversion-command-line "tag" "good.edn" "--years" "bad")
      => (parse-error "Coerce failure")
      (cli/parse-conversion-command-line "tag" "good.edn" "-y" "2012" "bad")
      => (parse-error "Coerce failure")
      (cli/parse-conversion-command-line "tag" "good.edn" "--y" "2012")
      => (parse-error "Unknown option"))

   (facts "Good arguments"
     (cli/parse-conversion-command-line "store" "good.edn")
     => (just {:cmd :store :cfgfile "good.edn" :cmd-help fn?})

     (cli/parse-conversion-command-line "convert" "good.edn")
     => (just {:cmd :convert :cfgfile "good.edn" :cmd-help fn?})
     (cli/parse-conversion-command-line "convert" "good.edn" "-t" "one"
                                        "--tags" "two" "three"
                                        "--range" "10" "20"
                                        "--years" "2010" "2011"
                                        "--limit" "1000"
                                        "--threads" "32"
                                        "--retry")
     => (just {:cmd     :convert :cfgfile "good.edn"  :tags     ["one" "two" "three"]
               :range   [10 20]  :years   [2010 2011] :limit    1000
               :threads 32       :retry   true        :cmd-help fn?})

     (cli/parse-conversion-command-line "link" "good.edn")
     => (just {:cmd :link :cfgfile "good.edn" :cmd-help fn?})
     (cli/parse-conversion-command-line "link" "good.edn" "--relink")
     => (just {:cmd :link :cfgfile "good.edn" :relink true :cmd-help fn?})
     (cli/parse-conversion-command-line "link" "good.edn" "-t" "one" "two" "--range" "10" "190")
     => (just {:cmd      :link         :cfgfile "good.edn"
               :tags     ["one" "two"] :range   [10 190]
               :cmd-help fn?})

     (cli/parse-conversion-command-line "send" "good.edn")
     => (just {:cmd :send :cfgfile "good.edn" :cmd-help fn?})
     (cli/parse-conversion-command-line "send" "good.edn" "--state-changes")
     => (just {:cmd           :send :cfgfile  "good.edn"
               :state-changes true  :cmd-help fn?})
     (cli/parse-conversion-command-line "send" "good.edn" "--state-changes" "--kuntagml")
     => (just {:cmd           :send :cfgfile  "good.edn"
               :state-changes true  :kuntagml true
               :cmd-help      fn?})
     (cli/parse-conversion-command-line "send" "good.edn" "-s" "-k")
     => (just {:cmd           :send :cfgfile  "good.edn"
               :state-changes true  :kuntagml true
               :cmd-help      fn?})
     (cli/parse-conversion-command-line "send" "good.edn" "-s" "--kuntagml")
     => (just {:cmd           :send :cfgfile  "good.edn"
               :state-changes true  :kuntagml true
               :cmd-help      fn?})
     (cli/parse-conversion-command-line "send" "good.edn" "--state-changes" "-s")
     => (just {:cmd           :send :cfgfile  "good.edn"
               :state-changes true  :cmd-help fn?})
     (cli/parse-conversion-command-line "send" "good.edn" "-s" "-k")
     => (just {:cmd           :send :cfgfile  "good.edn"
               :state-changes true  :kuntagml true :cmd-help fn?})
     (cli/parse-conversion-command-line "send" "good.edn" "-s" "-k" "--last-state"
                                        "--state-changes" "--kuntagml")
     => (just {:cmd        :send :cfgfile  "good.edn" :state-changes true
               :last-state true  :kuntagml true       :cmd-help      fn?})

     (cli/parse-conversion-command-line "tag" "good.edn")
     => (just {:cmd :tag :cfgfile "good.edn" :cmd-help fn?})
     (cli/parse-conversion-command-line "tag" "good.edn" "--add" "hello" "world"
                             "--remove" "other")
     => (just {:cmd      :tag              :cfgfile "good.edn"
               :add      ["hello" "world"] :remove  ["other"]
               :cmd-help fn?})
     (cli/parse-conversion-command-line "tag" "good.edn" "--remove" "foo" "bar")
     => (just {:cmd    :tag          :cfgfile  "good.edn"
               :remove ["foo" "bar"] :cmd-help fn?})
     (cli/parse-conversion-command-line "tag" "good.edn" "--number" "22")
     => (just {:cmd    :tag :cfgfile  "good.edn"
               :number 22   :cmd-help fn?})

     (cli/parse-conversion-command-line "clean" "good.edn")
     => (just {:cmd :clean :cfgfile "good.edn" :cmd-help fn?})
     (cli/parse-conversion-command-line "clean" "good.edn" "--dry-run")
     => (just {:cmd     :clean :cfgfile  "good.edn"
               :dry-run true   :cmd-help fn?})
     (cli/parse-conversion-command-line "clean" "good.edn" "-d" "--force")
     => (just {:cmd      :clean :cfgfile "good.edn"
               :dry-run  true   :force   true
               :cmd-help fn?})
     (cli/parse-conversion-command-line "clean" "good.edn" "-f")
     => (just {:cmd   :clean :cfgfile  "good.edn"
               :force true   :cmd-help fn?})

     (cli/parse-conversion-command-line "backend-ids" "good.edn")
     => (just {:cmd      :backend-ids :cfgfile "good.edn"
               :cmd-help fn?})
     (cli/parse-conversion-command-line "backend-ids" "good.edn" "--tags" "one" "two" "-r" "10" "20")
     => (just {:cmd      :backend-ids  :cfgfile "good.edn"
               :tags     ["one" "two"] :range   [10 20]
               :cmd-help fn?})

     (cli/parse-conversion-command-line "fix-locations" "good.edn" "--tags" "one"  "-relax")
     => (just {:cmd      :fix-locations :cfgfile        "good.edn"
               :tags     ["one"]        :location-relax true
               :cmd-help fn?})

     (cli/parse-conversion-command-line "fix-locations" "good.edn" "-r" "1" "99"  "-relax")
     => (just {:cmd            :fix-locations :cfgfile "good.edn"
               :location-relax true           :range   [1 99]
               :cmd-help       fn?}))


   (let [help-check (parse-info #"^Batchrun calling convention")]
     (facts "Help"
       (cli/parse-conversion-command-line "help") => help-check
       (cli/parse-conversion-command-line nil) => help-check
       (cli/parse-conversion-command-line " ") => help-check
       (cli/parse-conversion-command-line "") => help-check)))

  (facts "parse-other-command-line"
    (facts "Bad arguments"
      (cli/parse-other-command-line "bad") => (parse-info "Unknown other command")
      (cli/parse-other-command-line "backend-ids" :bad-arg) => (parse-error "Unknown option")
      (cli/parse-other-command-line "backend-ids" "bad.edn") => (parse-error "Invalid value")
      (cli/parse-other-command-line "backend-ids" "good.edn" "-foo")
      => (parse-error "Unknown option")
      (cli/parse-other-command-line "backend-ids" "--created" "bad")
      => (parse-error "Invalid value for option :created")
      (cli/parse-other-command-line "backend-ids" "--sent" "16.5.2023" "15.5.2023")
      => (parse-error "Invalid value for option :sent")
      (cli/parse-other-command-line "send-states" "--org-id" "753-R")
      => (parse-error "Unknown option")
      (cli/parse-other-command-line "fix-missing-reviews" "-org-id" "753-R" "--states" "submitted")
      => (parse-error "Invalid value for option :states")
      (cli/parse-other-command-line "fix-missing-reviews" "-org-id" "753-R" "--states")
      => (parse-error "Error: Coerce failure"))

    (facts "Good arguments"
      (cli/parse-other-command-line "backend-ids")
      => (just {:cmd :backend-ids :cmd-help fn?})
      (cli/parse-other-command-line "backend-ids"
                                    "--organization-id" "123-R"
                                    "--application-ids" "LP-1" "LP-2" "LP-3"
                                    "--created" "100000" "2000000"
                                    "--sent" "1.2.2000" "1.2.2000"
                                    "--verdict-date" "5.5.2020" "6.5.2020"
                                    "--closed" "0" "2.2.2002"
                                    "--permit-type" "R")
      => (just {:application-ids ["LP-1" "LP-2" "LP-3"]
                :closed          [0 "2.2.2002"]
                :cmd             :backend-ids
                :cmd-help        fn?
                :created         [100000 2000000]
                :organization-id "123-R"
                :permit-type     "R"
                :sent            ["1.2.2000" "1.2.2000"],
                :verdict-date    ["5.5.2020" "6.5.2020"]})
      (cli/parse-other-command-line "backend-ids"
                                    "good.edn"
                                    "--organization-id" "123-R"
                                    "-app-ids" "LP-1"
                                    "--verdict-date" "16.5.2023")
      => (just {:cfgfile         "good.edn"
                :cmd             :backend-ids
                :cmd-help        fn?
                :verdict-date    ["16.5.2023"]
                :application-ids ["LP-1"]
                :organization-id "123-R"})
      (cli/parse-other-command-line "send-states"
                                    "--organization-id" "123-R"
                                    "--all")
      => (just {:cmd             :send-states
                :cmd-help        fn?
                :all             true
                :organization-id "123-R"})
      (cli/parse-other-command-line "send-kuntagml"
                                    "good.edn"
                                    "-org-id" "123-R"
                                    "-a"
                                    "-fp" "c"
                                    "-ic")
      => (just {:cmd               :send-kuntagml
                :cmd-help          fn?
                :cfgfile           "good.edn"
                :organization-id   "123-R"
                :application       true
                :force-path        :c
                :include-converted true})
      (cli/parse-other-command-line "send-kuntagml"
                                    "--organization-id" "123-R"
                                    "-v")
      => (just {:cmd             :send-kuntagml
                :cmd-help        fn?
                :organization-id "123-R"
                :verdict         true})
      (cli/parse-other-command-line "send-kuntagml"
                                    "--organization-id" "123-R"
                                    "-r")
      => (just {:cmd             :send-kuntagml
                :cmd-help        fn?
                :organization-id "123-R"
                :review          true})
      (cli/parse-other-command-line "send-kuntagml"
                                    "--organization-id" "123-R"
                                    "-a"
                                    "--force-path" "a")
      => (just {:cmd             :send-kuntagml
                :cmd-help        fn?
                :organization-id "123-R"
                :force-path      :a
                :application     true})
      (cli/parse-other-command-line "fix-missing-reviews" "-org-id" "753-R"
                                    "--states" "inUse" "ready")
      => (just {:cmd             :fix-missing-reviews
                :cmd-help        fn?
                :organization-id "753-R"
                :states          [:inUse :ready]}))))
