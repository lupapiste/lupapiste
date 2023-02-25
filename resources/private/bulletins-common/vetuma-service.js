LUPAPISTE.VetumaService = function() {
  "use strict";
  var self = this;

  self.authenticated = ko.observable(false);
  self.userInfo = ko.mapping.fromJS({
    firstName: undefined,
    lastName: undefined
  });

  var RELEVANT_USER_FIELDS = ["firstName", "lastName", "street", "zip", "city", "eidasId"];

  hub.subscribe("vetumaService::authenticateUser", function(params) {
    vetuma.getUser(function(resp) { // onFound
      ko.mapping.fromJS(_.pick(resp, RELEVANT_USER_FIELDS), self.userInfo);
      self.authenticated(true);
      hub.send("vetumaService::authStatusChanged", true);
    }, function() { // onNotFound
      if(params.errorType) {
        var errorMsgLockey = ["bulletins", "vetuma", params.errorType].join(".");
        hub.send("indicator", { message: errorMsgLockey,
                                style: "negative" });
      }
      self.authenticated(false);
    }, function(resp){ // onEidasFound
        ko.mapping.fromJS(_.pick(resp, RELEVANT_USER_FIELDS), self.userInfo);
        self.authenticated(true);
        hub.send("vetumaService::authStatusChanged", true);
    }
    );
  });

  hub.subscribe("vetumaService::logoutRequested", function() {
    vetuma.logoutUser(function() { // onSuccess
      self.authenticated(false);
      util.identLogoutRedirectBulletins();
    });
  });

  hub.send("vetumaService::serviceCreated", this);
};
