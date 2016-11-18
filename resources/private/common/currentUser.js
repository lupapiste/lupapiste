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

  var fbPixel = null;
  if (LUPAPISTE.config.facebook && LUPAPISTE.config.facebook.url && analytics && analytics.isTrackingEnabled()) {
    fbPixel = function () {
      var fbUrl = LUPAPISTE.config.facebook.url;
      return "<img height='1' width='1' style='display:none' src='" + fbUrl + "'/>";
    };
  }

  function constructor(user) {
    if( !_.isEmpty(user) && !user.language && !user.virtual ) {
      user.language = loc.getCurrentLanguage();
      ajax.command("update-user", _.pick(user, ["firstName", "lastName", "language"]))
        .success(function() {
          hub.send("indicator", {style: "primary",
                                 message: "user.language.note",
                                 sticky: true, html: true});
        })
        .call();
    }
    if (user.firstLogin) {
      hub.send("first-login", {user: user});
    }
    ko.mapping.fromJS(_.defaults(user, defaults), {}, self);
  }

  constructor({});

  hub.subscribe("first-login", function() {
    if (_.isFunction(fbPixel)) {
      // send a bit to Facebook about firstLogin
      info("Triggering first login Facebook pixel");
      hub.send("indicator", {style: "hidden",
                             rawMessage: fbPixel(),
                             html: true});
    }
  });


  self.loaded = ko.pureComputed(function() {
    return self.id();
  });

  // Hash is cleared when returned to the application list. But the
  // we need to wrap it to the observable in order to trigger
  // isAuthority and isApplicant computeds.
  var hash = ko.observable();

  $(window).on( "hashchange", function() {
    // Null-safe regarding location
    hash( _.get( window, "location.hash" ));
  } );

  function isOutsideAuthority() {
    var app = lupapisteApp.models.application;
    return self.role() === "authority"
      && app && _.includes( hash(), app.id())
      && !_.get( self.orgAuthz(), app.organization());
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

  // Role in the context of the current application.
  self.applicationRole = _.cond( [[self.isAuthority, _.constant( "authority")],
                                  [self.isApplicant, _.constant( "applicant")],
                                  [_.stubTrue, self.role ]]);

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
