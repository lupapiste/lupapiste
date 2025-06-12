(ns sade.password
  (:require [sade.strings :as ss])
  (:import [com.nulabinc.zxcvbn StandardDictionaries
                                StandardKeyboards
                                Strength
                                Zxcvbn
                                ZxcvbnBuilder]
           [com.nulabinc.zxcvbn.io ClasspathResource]
           [com.nulabinc.zxcvbn.matchers Dictionary
                                         DictionaryLoader
                                         Keyboard
                                         SlantedKeyboardLoader]))

(set! *warn-on-reflection* true)

(def ^:private max-password-length
  "Limit length due to Denial-of-Service risk"
  128)

(defn- load-keyboard
  "Load a keyboard layout file from classpath"
  ^Keyboard [keyboard-name resource]
  (.load (SlantedKeyboardLoader. keyboard-name (ClasspathResource. resource))))

(defn- load-dictionary
  "Load a dictionary file from classpath"
  ^Dictionary [dict-name resource]
  (.load (DictionaryLoader. dict-name (ClasspathResource. resource))))

(def ^:private ^Zxcvbn zxcvbn
  (-> (ZxcvbnBuilder.)
      (.dictionaries (StandardDictionaries/loadAllDictionaries))
      (.keyboards (StandardKeyboards/loadAllKeyboards))
      (.keyboard (load-keyboard "fi-qwerty" "/sade/password/fi_qwerty.txt"))
      (.dictionary (load-dictionary "fi-common" "/sade/password/fi_common.txt"))
      (.dictionary (load-dictionary "fi-lastnames" "/sade/password/fi_lastnames.txt"))
      (.dictionary (load-dictionary "fi-female-firstnames" "/sade/password/fi_female_firstnames.txt"))
      (.dictionary (load-dictionary "fi-male-firstnames" "/sade/password/fi_male_firstnames.txt"))
      (.dictionary (load-dictionary "fi-custom" "/sade/password/custom.txt"))
      (.build)))

(defn user->dict
  "Parse potential password tokens from user information"
  [{:keys [firstName lastName email]}]
  (let [split-by (fn [re]
                   (fn [token]
                     (into [token] (ss/split token re))))]
    (->> [firstName lastName email]
         (keep identity)
         (mapcat (split-by #"@"))
         (mapcat (split-by #"\."))
         distinct
         (into []))))

(defn measure-password-quality
  "Estimates password quality in the range of 0 (poor) to 4 (excellent)."
  ([password]
   (measure-password-quality password {}))
  ([password user]
   (if (or (ss/blank? password)
           (-> password count (> max-password-length)))
     0
     (let [user-dict          (user->dict user)
           ^Strength strength (.measure zxcvbn password user-dict)]
       (.getScore strength)))))
