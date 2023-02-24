var LUPAPISTE = LUPAPISTE || {};

(function($) {
  "use strict";

  var startPageHref = window.location.href.replace(window.location.hash, "");
  var mainWindow = !window.parent || window.parent === window;
  if (mainWindow) {
    window.name = "lupapiste";
  }

  /**
   * Prototype for Lupapiste Single Page Apps.
   *
   * params:
   * usagePurpose (extends {type: String}) The usage purpose, e.g. {type: "authority-admin", orgId: "753-R"}.
   * startPage (String)        ID of the landing page
   * allowAnonymous (Boolean)  Allow all users to access the app. Default: require login.
   * showUserMenu (Boolean)    Default: complement of allowAnonymous, i.e., show menu for users tthat have logged in
   */
  LUPAPISTE.App = function (params) {
    var self = this;

    self.defaultTitle = document.title;

    self.usagePurpose = ko.observable(params.usagePurpose);
    self.startPage = params.startPage;
    self.logoPath = params.logoPath;
    self.currentPage = "";
    //self.session = undefined;
    self.allowAnonymous = params.allowAnonymous;
    self.showUserMenu = (params.showUserMenu !== undefined) ? params.showUserMenu : !params.allowAnonymous;
    self.previousHash = "";
    self.currentHash = "";

    // Global models
    self.models = {};

    // Global services
    self.services = {};

    /**
     * Prepends given title to browser window title.
     *
     * @param {String} title
     */
    self.setTitle = function(title) {
      document.title = _.compact([title, self.defaultTitle]).join(" - ");
    };

    /**
    * Window unload event handler
    */
    self.unload = function () {
      trace("window.unload");
    };

    self.connectionCheck = function () {
      ajax.get( /bulletins/.test( self.startPage )
                ? "/api/alive?bulletins=1"
                : "/api/alive").raw(false)
        .success(function() {
          hub.send("connection", {status: "online"});
          setTimeout(self.connectionCheck, 10000);
        })
        .error(function(e) {
          if (e.text === "error.unauthorized") {
            hub.send("connection", {status: "session-dead"});
          } else {
            hub.send("connection", {status: "lockdown", text: e.text});
          }
          setTimeout(self.connectionCheck, 10000);
        })
        .fail(function() {
          hub.send("connection", {status: "offline"});
          setTimeout(self.connectionCheck, 2000);
        })
        .call();
    };

    self.getHashbangUrl = function() {
      var href = window.location.href;
      var hash = window.location.hash;
      var separator = href.indexOf("?") >= 0 ? "&" : "?";
      if (hash && hash.length > 0) {
        var withoutHash = href.substring(0, href.indexOf("#"));
        return withoutHash + separator + "redirect-after-login=" + encodeURIComponent(hash.substring(1, hash.length));
      } else {
        // No hashbang. Go directly to front page.
        return "/app/" + loc.getCurrentLanguage();
      }
    };

    self.redirectToHashbang = function() {
      window.location = self.getHashbangUrl();
      return false;
    };

    var offline = false;
    var wasLoggedIn = false;
    var lockdown = false;

    function unlock() {
      if (lockdown) {
        lockdown = false;
        if (lupapisteApp.models.globalAuthModel) {
          lupapisteApp.models.globalAuthModel.refreshWithCallback({});
        }
      }
    }

    function UserMenu() {
      var self = this;
      self.open = ko.observable(false);
      self.usagePurposes = ko.observableArray();

      function purposeName(purpose) {
        return purpose.type === "authority-admin" ? "authorityAdmin.settings" : "permit.service";
      }

      function purposeOrgName(purpose) {
        return ko.pureComputed(function () {
          if (purpose.type === "authority-admin") {
            var orgNames = lupapisteApp.models.currentUser.orgNames();
            return orgNames ? orgNames[purpose.orgId][loc.currentLanguage] : purpose.orgId;
          } else {
            return undefined;
          }
        });
      }

      function purposeIcon(purpose) { return purpose.type === "authority-admin" ? "lupicon-gear" : "lupicon-house"; }

      function purposeLink(purpose) {
        return "/app/" + loc.currentLanguage + "/" + purpose.type
             + (purpose.type === "authority-admin" ? "/" + purpose.orgId : "");
      }

      self.alternativeUsagePurposes = ko.pureComputed(function () {
        var currentUsagePurpose = lupapisteApp.usagePurpose();
        return self.usagePurposes().filter(function (purposeModel) {
          return purposeModel.type !== currentUsagePurpose.type
              || purposeModel.orgId !== currentUsagePurpose.orgId;
        });
      });

      self.cancel = _.partial(self.open, false);
      self.toggleOpen = function () { self.open(!self.open()); };

      ajax.query("usage-purposes", {})
        .success(function (res) { self.usagePurposes(_.map(res.usagePurposes, function (purpose) {
          var purposeModel = _.extend(purpose, {
            name: purposeName(purpose),
            orgName: purposeOrgName(purpose),
            iconClasses: {},
            href: purposeLink(purpose)
          });
          purposeModel.iconClasses[purposeIcon(purpose)] = true;
          return purposeModel;
        })); })
        .call();

      hub.subscribe("dialog-close", self.cancel);
      $(document).on("click", self.cancel);
    }

    hub.subscribe("login", function() { wasLoggedIn = true; });

    hub.subscribe({eventType: "connection", status: "online"}, function () {
      if (offline) {
        offline = false;
        pageutil.hideAjaxWait();
      }
      unlock();
    });

    hub.subscribe({eventType: "connection", status: "offline"}, function () {
      if (!offline) {
        offline = true;
        pageutil.showAjaxWait(loc("connection.offline"));
      }
    });

    hub.subscribe({eventType: "connection", status: "session-dead"}, function () {
      if (wasLoggedIn) {
        LUPAPISTE.ModalDialog.showDynamicOk(loc("session-dead.title"), loc("session-dead.message"),
            {title: loc("session-dead.logout"), fn: self.redirectToHashbang});
        hub.subscribe("dialog-close", self.redirectToHashbang, true);
      }
      unlock();
    });

    hub.subscribe({eventType: "connection", status: "lockdown"}, function (e) {
      if (!lockdown && lupapisteApp.models.globalAuthModel) {
        lupapisteApp.models.globalAuthModel.refreshWithCallback({});
      }
      lockdown = true;
      hub.send("indicator", {style: "negative", message: e.text});
    });

    self.initSubscribtions = function() {
      hub.subscribe({eventType: "keyup", keyCode: 27}, LUPAPISTE.ModalDialog.close);
      hub.subscribe("logout", function () {
        window.location = "/app/" + loc.getCurrentLanguage() + "/logout";
      });
    };

    var isAuthorizedToTosAndSearch = function() {
      return lupapisteApp.models.globalAuthModel.ok("permanent-archive-enabled") &&
        lupapisteApp.models.globalAuthModel.ok("tos-operations-enabled");
    };

    self.calendarsEnabledInAuthModel = ko.observable(false);

    self.showArchiveMenuOptions = ko.observable(false);
    self.showCalendarMenuOptions = ko.pureComputed(function() {
      var isApplicant = lupapisteApp.models.currentUser.isApplicant();
      var enabledInAuthModel = self.calendarsEnabledInAuthModel();
      return enabledInAuthModel || (isApplicant && features.enabled("ajanvaraus"));
    });

    if (util.getIn(window, ["lupapisteApp", "models", "globalAuthModel"])) {
      self.showArchiveMenuOptions(isAuthorizedToTosAndSearch());
      self.calendarsEnabledInAuthModel(lupapisteApp.models.globalAuthModel.ok("calendars-enabled"));
    }
    hub.subscribe("global-auth-model-loaded", function() {
      self.showArchiveMenuOptions(isAuthorizedToTosAndSearch());
      self.calendarsEnabledInAuthModel(lupapisteApp.models.globalAuthModel.ok("calendars-enabled"));
    });

    /**
     * Complete the App initialization after DOM is loaded.
     */
    self.domReady = function () {
      self.initSubscribtions();

      window.addEventListener("unload", self.unload);

      self.connectionCheck();

      if (typeof LUPAPISTE.ModalDialog !== "undefined") {
        LUPAPISTE.ModalDialog.init();
      }

      // used in operations tree + modal dialog , could be removed
      $(document.documentElement).on("keyup",function(event) { hub.send("keyup", event); });

      function openStartPage() {
        if (self.logoPath) {
          window.location = window.location.protocol + "//" + window.location.host + self.logoPath;
        } else if (self.startPage && self.startPage.charAt(0) !== "/") {
          pageutil.openPage(self.startPage);
        } else {
          // fallback
          window.location.href = startPageHref;
        }
      }

      var model = {
        currentLanguage: loc.getCurrentLanguage(),
        openStartPage: openStartPage,
        showUserMenu: self.showUserMenu,
        userMenu: new UserMenu(),
        showArchiveMenuOptions: self.showArchiveMenuOptions,
        showCalendarMenuOptions: self.showCalendarMenuOptions,
        calendarMenubarVisible: self.calendarMenubarVisible,
        // TODO: sync with side-panel.js sidePanelPages
        sidePanelPages: ["application","attachment","statement","neighbors","verdict"]
      };


      $("#app").applyBindings(lupapisteApp.models.rootVMO);

      $(".brand").applyBindings( model );
      $(".header-menu").applyBindings( model ).css( "visibility", "visible");
      $("footer").applyBindings(model).css("visibility", "visible");
    };
  };

})(jQuery);
