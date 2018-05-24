// Shared service for handler and assignments filter services.
LUPAPISTE.OrganizationsUsersService = function() {
  "use strict";

  var self = this;

  var organizationsUsers = ko.observableArray([]);

  self.isInitialized = ko.observable( false );

  self.users = ko.pureComputed( function() {
    return organizationsUsers();
  });

  function mapUser(user) {
    user.fullName = _.filter([user.lastName, user.firstName]).join("\u00a0") || user.email;
    if (!user.enabled) {
      user.fullName += " " + loc("account.not-in-use");
    }
    return user;
  }

  ko.computed( function() {
    var authModel = lupapisteApp.models.globalAuthModel;
    if( !self.isInitialized() && authModel.isInitialized() ) {
      if( authModel.ok("users-in-same-organizations" )) {
        ajax.query("users-in-same-organizations")
        .success(function(res) {
          organizationsUsers(_(res.users).map(mapUser).sortBy("fullName").value());
          self.isInitialized( true );
        })
        .call();
        return true;
      } else {
        // No authority.
        self.isInitialized( true );
      }
    }
  });
};
