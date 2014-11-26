LUPAPISTE.ForemanModel = function() {
  "use strict";
  var self = this;
  self.application = null;

  self.refresh = function(application) {
    self.application = application;
  }

  self.inviteForeman = function() {
    console.log("invite");
  }

  self.submit = function(model) {
    console.log("submit");
  }
}
