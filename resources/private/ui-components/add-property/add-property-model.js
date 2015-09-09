LUPAPISTE.AddPropertyModel = function(params) {
  "use strict";
  var self = this;

  var changeLoc = params.changeLoc;
  var app = params.application;

  self.addProperty = function() {
    hub.send("show-dialog", {ltitle: "application.dialog.add-property.title",
                             size: "medium",
                             component: "add-property-dialog"});
  };
};