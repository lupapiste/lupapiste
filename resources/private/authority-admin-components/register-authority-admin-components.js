jQuery(document).ready(function() {
  "use strict";

  var components = [{name: "server-settings"},
                    {name: "suti-api"},
                    {name: "suti-admin"},
                    {name: "municipality-maps"},
                    {name: "municipality-maps-layers"},
                    {name: "municipality-maps-map"},
                    {name: "handler-roles"},
                    {name: "navi-sidebar"},
                    {name: "state-change-msg-settings"},
                    {name: "dmcity-admin"},
                    {name: "notice-form-configuration"},
                    {name: "notice-form-configuration-group"},
                    {name: "dmcity-admin-reviews"},
                    {name: "bulk-change-handlers"},
                    {name: "building-extra-attributes"},
                    {name: "fetch-headers"}];

  ko.registerLupapisteComponents(components);
});
