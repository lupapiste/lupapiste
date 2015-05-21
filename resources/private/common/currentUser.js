LUPAPISTE.CurrentUser = function() {
  "use strict";

  var self = this;

  var defaults = {
    id:             "",
    email:          "",
    role:           "",
    oir:            "",
    organizations:  [],
    firstName:      "",
    lastName:       "",
    phone:          "",
    username:       "",
    orgAuthz:       undefined,
    company: {
      id:   undefined,
      role: undefined
    },
    notification: {
      title:      undefined,
      titleLoc:   undefined,
      message:    undefined,
      messageLoc: undefined
    }
  };

  function constructor(user) {
    ko.mapping.fromJS(_.defaults(user, defaults), {}, self);
  }

  constructor({});

  self.isAuthority = function() {
    return self.role() === "authority";
  };

  self.isApplicant = function() {
    return self.role() === "applicant";
  };

  self.isCompanyUser = ko.pureComputed(function() {
    return !_.isEmpty(ko.unwrap(self.company.id()));
  });

  self.displayName = ko.pureComputed(function() {
    var username = self.username() || "";
    if (self.firstName() || self.lastName()) {
      username = self.firstName() + " " + self.lastName();
    }
    return username;
  });

  self.showNotification = ko.pureComputed(function() {
    return self.notification.titleLoc() && self.notification.messageLoc();
  });

  ko.computed(function() {
    if (self.showNotification()) {
      hub.send("show-dialog", {title: self.notification.titleLoc(),
                               id: "user-notification-dialog",
                               size: "medium",
                               component: "ok-dialog",
                               componentParams: {text: self.notification.messageLoc()}
                              });
    }
  });

  hub.subscribe({type: "dialog-close", id: "user-notification-dialog"}, function() {
    ajax.command("remove-user-notification")
      .complete(function () {
        hub.send("reload-current-user");
      })
      .call();
  });

  hub.subscribe("login", function(data) {
    if (data.user) {
      constructor(data.user);
    }
  });

  hub.subscribe("reload-current-user", function() {
    ajax.query("user")
      .success(function (res) {
        if (res.user) {
          constructor(res.user);
        }
      })
      .call();
  });
};
