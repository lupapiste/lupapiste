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
      title:          undefined,
      titleI18nkey:   undefined,
      message:        undefined,
      messageI18nkey: undefined
    },
    defaultFilter: {id: "", foremanFilterId: ""},
    applicationFilters: [],
    foremanFilters: []
  };

  function constructor(user) {
    ko.mapping.fromJS(_.defaults(user, defaults), {}, self);
  }

  constructor({});

  self.loaded = ko.pureComputed(function() {
    return self.id();
  });

  self.isAuthorityAdmin = ko.pureComputed(function() {
    return self.role() === "authorityAdmin";
  });

  self.isAuthority = ko.pureComputed(function() {
    return self.role() === "authority";
  });

  self.isApplicant = ko.pureComputed(function() {
    return self.role() === "applicant";
  });

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
    return !_.isEmpty(getNotificationFields(self.notification));
  });

  function getNotificationFields(notification) {
    if(notification.titleI18nkey() && notification.messageI18nkey()) {
      return {
        title: loc(notification.titleI18nkey()),
        msg: loc(notification.messageI18nkey())
      };
    } else if (notification.title() && notification.message()) {
      return {
        title: notification.title(),
        msg: notification.message()
      };
    } else {
      return undefined;
    }
  }

  ko.computed(function() {
    if (self.showNotification()) {
      var fields = getNotificationFields(self.notification);
      hub.send("show-dialog", {title: fields.title,
                               id: "user-notification-dialog",
                               size: "medium",
                               component: "ok-dialog",
                               closeOnClick: true,
                               componentParams: {text: fields.msg}
                              });
    }
  });

  hub.subscribe({eventType: "dialog-close", id: "user-notification-dialog"}, function() {
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
