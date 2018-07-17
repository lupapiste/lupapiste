jQuery(document).ready(function() {
  "use strict";

  var components = [{name: "server-settings"},
                    {name: "suti-api"},
                    {name: "suti-admin"},
                    {name: "municipality-maps"},
                    {name: "municipality-maps-layers"},
                    {name: "municipality-maps-map"},
                    {name: "inspection-summary-templates-list"},
                    {name: "inspection-summary-template-bubble"},
                    {name: "select-inspection-summary-template-for-operation"},
                    {name: "handler-roles"},
                    {name: "triggers"},
                    {name: "navi-sidebar"},
                    {name: "default-verdict-template"},
                    {name: "state-change-msg-settings"}];

  ko.registerLupapisteComponents(components);
});
