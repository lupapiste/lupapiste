LUPAPISTE.VetumaService = function() {
  "use strict";
  var self = this;

  self.authenticated = ko.observable(false);
  self.userInfo = ko.mapping.fromJS({
    firstName: undefined,
    lastName: undefined
  });

  hub.subscribe("vetumaService::authenticateUser", function(params) {
    vetuma.getUser(function(resp) { // onFound
      ko.mapping.fromJS(_.pick(resp, ["firstName", "lastName"]), self.userInfo);
      self.authenticated(true);
    }, function() { // onNotFound
      if(params.errorType) {
        var errorMsgLockey = ["bulletins", "vetuma", params.errorType].join(".");
        hub.send("indicator", { message: errorMsgLockey,
                                style: "negative" });
      }
      self.authenticated(false);
    });  
  });

  hub.subscribe("vetumaService::logoutRequested", function() {
    vetuma.logoutUser(function() { // onSuccess
      self.authenticated(false);
    });
  });

  hub.send("vetumaService::serviceCreated", this);
};
