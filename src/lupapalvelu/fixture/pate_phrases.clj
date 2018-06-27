(ns lupapalvelu.fixture.pate-phrases
  "Enable Pate with phrases in every Sipoo organization."
  (:require [lupapalvelu.fixture.core :refer [deffixture]]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [sade.strings :as ss]))

(def phrases '({:category "paatosteksti",
                :tag      "Paatos",
                :phrase   "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum aliquet lorem enim, vel condimentum leo tincidunt sit amet. Etiam eget euismod sapien. Ut fermentum dolor nisl, non gravida tellus tristique in. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Pellentesque iaculis dapibus semper. In hac habitasse platea dictumst. Maecenas quis turpis diam. Duis dolor nulla, tincidunt eu metus vel, posuere tincidunt ex. Vivamus commodo vel elit eget iaculis. Quisque luctus lobortis justo, id cursus quam.",
                :id       "5b227464cea1d0310aa739b5"}
               {:category "vaativuus",
                :tag      "Vaativuus",
                :phrase   "Vestibulum eu efficitur elit. Sed a congue nisi. Nulla vulputate dui non lorem eleifend, a laoreet libero maximus. Curabitur finibus imperdiet sem sed iaculis. Donec sodales eros elementum turpis feugiat accumsan. Sed porta massa nisi, viverra consequat arcu blandit sit amet. Cras iaculis vehicula erat, eu porta tellus convallis porttitor. Proin lorem risus, iaculis ut rutrum eget, tempus quis orci. Nam vitae turpis nunc. Nulla tristique turpis quis luctus egestas. Nam eget eros vitae ligula iaculis ornare luctus imperdiet erat. Integer congue odio quis ipsum viverra, non pellentesque neque tincidunt. Proin imperdiet sapien dictum ex efficitur, eu tempor ligula elementum. Sed scelerisque condimentum arcu vel imperdiet. Sed consectetur ultricies egestas. Ut maximus urna tellus, ac euismod ante posuere vitae.",
                :id       "5b227477cea1d0310aa739b6"}
               {:category "kaava",
                :tag      "Kaava",
                :phrase   "Fusce at convallis nisl. Aenean lacus ipsum, facilisis non risus id, fermentum tempor tellus. Praesent sollicitudin scelerisque enim, non dapibus elit condimentum ut. Phasellus consectetur, massa sit amet eleifend congue, lorem justo viverra dui, sit amet scelerisque sem arcu non nisi. Fusce elementum dolor massa, id eleifend mauris accumsan sit amet. Pellentesque nulla odio, ultrices a neque ut, euismod pharetra est. Maecenas luctus, lacus id tempus mattis, diam nulla accumsan arcu, eu porta justo velit eget diam. Nam ultrices quam vitae odio imperdiet, at mattis diam lobortis. Nam iaculis dui quis quam vehicula tincidunt. Sed ipsum felis, fringilla ac auctor non, accumsan at lectus. Aenean sed porttitor erat. Ut tempus magna lacus, sit amet euismod lorem elementum fermentum.",
                :id       "5b227488cea1d0310aa739b7"}
               {:category "lupaehdot",
                :tag      "Muut",
                :phrase   "Mauris finibus ex vel semper dictum. Nulla facilisi. Nulla lacinia consectetur odio, eget commodo augue ullamcorper at. Proin sit amet finibus libero, nec commodo urna. Donec a laoreet lacus, sed mattis orci. Proin sed accumsan eros, vitae sollicitudin lectus. In hac habitasse platea dictumst. Sed dignissim justo ipsum, vel semper arcu elementum in. Cras dictum tellus sed lobortis porta. Morbi rutrum orci massa, ut placerat metus elementum eu.",
                :id       "5b227499cea1d0310aa739b8"}
               {:category "muutoksenhaku",
                :tag      "Muutos",
                :phrase   "Proin tempus tristique justo, quis convallis arcu congue et. Curabitur eget ullamcorper mi. Praesent id gravida purus. Curabitur sollicitudin porta eros sit amet commodo. Aenean non turpis pulvinar, vehicula augue eget, euismod lorem. Donec bibendum dictum ex eu faucibus. Donec nibh est, ullamcorper ut ipsum ac, aliquet pretium nunc. Donec efficitur diam eget justo vehicula blandit. Suspendisse faucibus commodo massa accumsan imperdiet. Etiam pharetra sodales dui quis condimentum.",
                :id       "5b2274adcea1d0310aa739b9"}
               {:category "naapurit",
                :tag      "Naapurit",
                :phrase   "Praesent sodales elit ut consectetur eleifend. Fusce hendrerit lacus non finibus interdum. Nunc dictum urna eros, tincidunt laoreet lectus eleifend in. Nullam vitae lorem vitae nulla congue tristique a nec arcu. Morbi tristique placerat vehicula. Ut ac nibh dictum, aliquam urna sed, bibendum lacus. Curabitur lobortis, eros vel porta placerat, justo risus viverra mauris, nec maximus mi risus non dui. Curabitur tortor tortor, tristique et varius nec, vulputate vitae orci. Aenean ac egestas ante. Praesent aliquam quis erat non faucibus.",
                :id       "5b2274bfcea1d0310aa739ba"}
               {:category "rakennusoikeus",
                :tag      "Rakennus",
                :phrase   "Fusce tincidunt congue ex, id tempor ex euismod eget. Suspendisse lobortis rutrum turpis, in fermentum augue molestie nec. In lacinia quam ut risus feugiat interdum. Praesent non quam eget est commodo tristique. In finibus justo ut tellus vulputate, sed ultrices odio rutrum. Morbi eget arcu at lacus congue fermentum. Ut eu commodo lectus, ac facilisis velit. Quisque non faucibus arcu. In dolor arcu, cursus sit amet pretium a, consequat quis sem. Fusce lobortis justo est, vitae tristique nulla posuere vitae. Donec volutpat vel velit in suscipit. Nunc id ipsum blandit, suscipit mauris eu, sollicitudin lorem. Fusce viverra, est a rhoncus gravida, nisl felis fermentum lorem, sed accumsan lorem neque ut lorem. Integer placerat ante vitae erat sodales efficitur.",
                :id       "5b2274dccea1d0310aa739bb"}
               {:category "sopimus",
                :tag      "Sopimus",
                :phrase   "Nulla ante tortor, accumsan et nisi eu, rhoncus vehicula est. Sed iaculis justo gravida, pretium mauris id, tempus tellus. Vivamus ac vestibulum ipsum, sed tristique ante. Maecenas tincidunt quam non sapien efficitur, non semper ligula vulputate. Donec justo leo, aliquam sed tortor eu, scelerisque tristique dolor. Mauris faucibus erat sit amet turpis sagittis, non sodales est suscipit. Nulla faucibus eget dolor vel consectetur. Etiam non urna hendrerit, vehicula quam tempor, cursus erat.",
                :id       "5b2274f1cea1d0310aa739bc"}
               {:category "toimenpide-julkipanoon",
                :tag      "Julkipano",
                :phrase   "Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Maecenas rhoncus turpis sit amet purus pretium, a congue nibh tempor. Proin dignissim eros in elementum tincidunt. Ut vehicula magna sit amet leo tincidunt gravida. Vivamus velit magna, finibus id magna in, tristique dignissim ex. Maecenas aliquet, magna sed tincidunt venenatis, neque urna porta justo, ac tincidunt leo risus quis risus. Vivamus sit amet ante eu nulla molestie cursus sed et nisl. Cras eleifend est eget lectus gravida porta. Vestibulum porta finibus hendrerit. Morbi in quam tristique, porttitor enim in, tincidunt quam. Quisque scelerisque ante quis massa tincidunt, quis pellentesque justo viverra. Cras id erat dapibus, ullamcorper diam id, interdum dolor. Aliquam semper, quam sed gravida pellentesque, tortor velit consectetur turpis, rutrum malesuada nulla est at massa. Duis eget blandit dolor.",
                :id       "5b227505cea1d0310aa739bd"}
               {:category "yleinen",
                :tag      "Yleinen",
                :phrase   "Proin sagittis, leo id cursus tempus, orci augue varius leo, non mattis ex nunc quis sapien. Morbi et luctus urna, a iaculis eros. Cras cursus arcu sit amet eros placerat, ac ornare nisl malesuada. Donec tincidunt neque ac odio ornare, quis feugiat eros imperdiet. Cras porttitor velit blandit commodo pellentesque. Suspendisse sed nulla velit. Curabitur rutrum erat diam, ac ullamcorper urna eleifend at. Donec aliquam tellus sit amet erat placerat finibus.",
                :id       "5b227516cea1d0310aa739be"}))

(def organizations (map (fn [{org-id :id :as org}]
                          (cond-> org
                            (ss/starts-with org-id "753-")
                            (assoc :scope (mapv (fn [sco] (assoc sco :pate-enabled true)) (:scope org))
                                   :phrases phrases)))
                        minimal/organizations))

(deffixture "pate-phrases" {}
  (mongo/clear!)
  (mongo/insert-batch :users minimal/users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations organizations))
