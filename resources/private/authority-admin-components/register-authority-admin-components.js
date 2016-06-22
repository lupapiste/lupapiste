jQuery(document).ready(function() {
  "use strict";

  var components = [{name: "municipality-maps"},
                    {name: "municipality-maps-server"},
                    {name: "municipality-maps-layers"},
                    {name: "municipality-maps-map"}];

  ko.registerLupapisteComponents(components);
});
