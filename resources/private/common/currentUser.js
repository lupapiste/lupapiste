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
    orgNames:       undefined,
    company: {
      id:   undefined,
      role: undefined
    },
    notification: {
      title:          undefined,
      titleI18nkey:   undefined,
      message:        undefined,
      messageI18nkey: undefined,
      id:             undefined
    },
    defaultFilter: {id: "", foremanFilterId: "",  companyFilterId: ""},
    applicationFilters: [],
    foremanFilters: [],
    companyApplicationFilters: []
  };

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
    ko.mapping.fromJS(_.defaults(user, defaults), {}, self);

    ajax.query("organization-names-by-user")
        .success(function (res) { self.orgNames(res.names); })
        .call();
  }

  constructor({});


  self.loaded = ko.pureComputed(function() {
    return self.id();
  });

  // Hash is cleared when returned to the application list. But the
  // we need to wrap it to the observable in order to trigger
  // isAuthority and isApplicant computeds.
  var hash = ko.observable();
  // TODO check if we could get rid of jquery hashchange.js dependency
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

  self.organizationAdminOrgs = ko.pureComputed(function () {
    if (self.role() === "authority") {
      var orgAuthz = ko.mapping.toJS(self.orgAuthz);
      return _(orgAuthz)
              .pickBy(function(roles) {
                return _.find(roles, _.partial(_.eq, "authorityAdmin"));
              })
              .keys()
              .value();
    } else { // not 'authorityAdmin'
      return [];
    }
  });

  self.isAuthority = ko.pureComputed(function() {
    return self.role() === "authority" && !isOutsideAuthority();
  });

  self.isApplicant = ko.pureComputed(function() {
    return self.role() === "applicant" || isOutsideAuthority();
  });

  self.isArchivist = ko.pureComputed(function() {
    var app = lupapisteApp.models.application;
    var orgAuths = util.getIn(self.orgAuthz, [ko.unwrap(app.organization)]);
    return self.role() === "authority" && app &&
      _.includes(orgAuths, "archivist");
  });

  self.isFinancialAuthority = ko.pureComputed(function() {
    return self.role() === "financialAuthority";
  });

  self.isBiller = ko.pureComputed(function() {
      return _.chain(self.orgAuthz())
          .values()
          .map(ko.unwrap)
          .flatten()
          .includes("biller")
          .value();
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
    } else if (notification.id()) {
      return {
        id: notification.id()
      };
    } else {
      return undefined;
    }
  }

  ko.computed(function() {
    if (self.showNotification()) {
      var fields = getNotificationFields(self.notification);
      if(fields.id) {
        hub.send("show-dialog", {
          size: "medium",
          id: "user-notification-dialog",
          component: "custom-notification-dialog",
          componentParams: {notificationId: fields.id}
        });
      }
      else {
        hub.send("show-dialog", {
          title: fields.title,
          id: "user-notification-dialog",
          size: "medium",
          component: "ok-dialog",
          componentParams: {text: fields.msg}
        });
      }
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
