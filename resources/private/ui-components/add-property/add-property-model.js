LUPAPISTE.AddPropertyModel = function(params) {
  "use strict";
  var self = this;

  var changeLoc = params.changeLoc;
  var app = params.application;

  self.addProperty = function() {
    //changeLoc.changeLocation(app);
    hub.send("show-dialog", {title: "ASDSASDAS", // ltitle
                             size: "medium",
                             component: "change-location-dialog"});
  };
};