//
// Provides services for application auth.
//
//

LUPAPISTE.AuthService = function() {
  "use strict";
  var self = this;

  self.auth = ko.observableArray();

  // TODO: Query for auth array
  lupapisteApp.models.application.auth.subscribe(function(auth) {
    self.auth(ko.toJS(auth));
  });

  // Find first matching role for user
  self.getRole = function(userId) {
    return _.get(_.find(self.auth(), ["id", userId]), "role");
  };

  // Find all matching roles for user
  self.getRoles = function(userId) {
    return _.get(_.filter(self.auth(), ["id", userId]), "role");
  };

};
