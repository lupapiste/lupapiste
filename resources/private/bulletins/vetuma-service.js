LUPAPISTE.VetumaService = function() {
  "use strict";
  var self = this;

  self.authenticated = ko.observable(false);
  self.userInfo = ko.mapping.fromJS({
    firstName: undefined,
    lastName: undefined
  });

  hub.subscribe("vetumaService::authenticateUser", function() {
    vetuma.getUser(function(resp) { // onFound
      ko.mapping.fromJS(_.pick(resp, ["firstName", "lastName"]), self.userInfo);
      self.authenticated(true);
    }, function() { // onNotFound
      self.authenticated(false);
    });  
  });

  hub.send("vetumaService::serviceCreated", this);
};
