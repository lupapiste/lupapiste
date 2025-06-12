LUPAPISTE.AddPropertyModel = function() {
  "use strict";
  var self = this;

  self.addProperty = function() {
    hub.send("show-dialog", {ltitle: "application.dialog.add-property.title",
                             size: "medium",
                             component: "add-property-dialog"});
  };
};
