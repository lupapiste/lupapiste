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
    language:       "",
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
    if( user.indicatorNote ) {
      hub.send( "indicator", {style: "primary",
                              message: user.indicatorNote,
                              sticky: true, html: true});
      delete user.indicatorNote;
    }
    ko.mapping.fromJS(_.defaults(user, defaults), {}, self);
  }

  constructor({});

  self.loaded = ko.pureComputed(function() {
    return self.id();
  });

  function isOutsideAuthority() {
    // Hash is not cleared when returned to the application list.
    var hash = _.get( window, "location.hash", "");
    var app = lupapisteApp.models.application;
    return self.role() === "authority"
      && app && hash.indexOf( app.id())
      && !_.find( self.orgAuthz(), app.organization());
  }

  self.isAuthorityAdmin = ko.pureComputed(function() {
    return self.role() === "authorityAdmin";
  });

  self.isAuthority = ko.pureComputed(function() {
    return self.role() === "authority" && !isOutsideAuthority();
  });

  self.isApplicant = ko.pureComputed(function() {
    return self.role() === "applicant" || isOutsideAuthority();
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
    ajax.query("user", {lang: loc.getCurrentLanguage()})
      .success(function (res) {
        if (res.user) {
          constructor(res.user);
        }
      })
      .call();
  });
};
