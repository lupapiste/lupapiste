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
   * startPage (String)        ID of the landing page
   * allowAnonymous (Boolean)  Allow all users to access the app. Default: require login.
   * showUserMenu (Boolean)    Default: complement of allowAnonymous, i.e., show menu for users tthat have logged in
   * componentPages (Array)    Array of pageId strings. If provided, router skips visibility toggle for the given pageIds.
   *                            Thus page visibility toggle responsibility for provided pageIds is in controlling component.
   * @param
   */
  LUPAPISTE.App = function (params) {
    var self = this;

    self.defaultTitle = document.title;

    self.startPage = params.startPage;
    self.logoPath = params.logoPath;
    self.currentPage = "";
    self.session = undefined;
    self.allowAnonymous = params.allowAnonymous;
    self.showUserMenu = (params.showUserMenu !== undefined) ? params.showUserMenu : !params.allowAnonymous;
    self.componentPages = params.componentPages || [];
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

    self.openPage = function (path) {
      var pageId = path[0];
      var pagePath = path.splice(1, path.length - 1);

      trace("pageId", pageId, "pagePath", pagePath);

      if (pageId !== self.currentPage) {
        // if pages are component controlled, skip element selection
        if (!_.includes(self.componentPages, pageId) || !self.currentPage) {
          $(".page").removeClass("visible");

          var page$ = $("#" + pageId);
          if (page$.length === 0) {
            pageId = self.startPage;
            pagePath = [];
            page$ = $("#" + pageId);
          }

          if (page$.length === 0) {
            // Something is seriously wrong, even startPage was not found
            error("Unknown page " + pageId + " and failed to default to " + self.startPage);
            return;
          }

          page$.addClass("visible");
          // Set focus on the first field
          util.autofocus(page$);
        }
        window.scrollTo(0, 0);
        self.currentPage = pageId;
        // Reset title. Pages can override title when they handle page-load event.
        document.title = self.defaultTitle;
      }

      hub.send("page-load", { pageId: pageId, pagePath: pagePath, currentHash: "!/" + self.currentHash, previousHash: "!/" + self.previousHash });

      if (self.previousHash !== self.currentHash) {
        var previousPageId = self.previousHash.split("/")[0];
        hub.send("page-unload", { pageId: previousPageId, currentHash: "!/" + self.currentHash, previousHash: "!/" + self.previousHash });
      }
    };

    self.hashChanged = function () {
      self.previousHash = self.currentHash;
      self.currentHash = (location.hash || "").substr(3);

      var q = self.currentHash.indexOf("?");
      if (q > -1) {
        self.currentHash = self.currentHash.substring(0,q);
      }

      if (self.currentHash === "") {
        if (_.isFunction(window.location.replace)) {
          window.location.replace(startPageHref + "#!/" + self.startPage);
        } else {
          pageutil.openPage(self.startPage);
        }
        return;
      }

      var path = self.currentHash.split("/");

      if (!self.allowAnonymous && self.session === undefined) {
        ajax.query("user")
          .success(function (e) {
            if (e.user) {
              self.session = true;
              hub.send("login", e);
              self.hashChanged();
            } else {
              error("User query did not return user, response: ", e);
              self.session = false;
              hub.send("logout", e);
            }
          })
          .error(function (e) {
            self.session = false;
            hub.send("logout", e);
          })
          .call();
        return;
      }

      self.openPage((self.allowAnonymous || self.session) ? path : ["login"]);
    };

    self.connectionCheck = function () {
      ajax.get("/api/alive").raw(false)
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

    var isAuthorizedToCalendars = function() {
      return lupapisteApp.models.globalAuthModel.ok("my-calendars");
    };

    self.showArchiveMenuOptions = ko.observable(false);
    self.showCalendarMenuOptions = ko.observable(false);
    if (util.getIn(window, ["lupapisteApp", "models", "globalAuthModel"])) {
      self.showArchiveMenuOptions(isAuthorizedToTosAndSearch());
      self.showCalendarMenuOptions(isAuthorizedToCalendars());
    }
    hub.subscribe("global-auth-model-loaded", function() {
      self.showArchiveMenuOptions(isAuthorizedToTosAndSearch());
      self.showCalendarMenuOptions(isAuthorizedToCalendars());
    });

    /**
     * Complete the App initialization after DOM is loaded.
     */
    self.domReady = function () {
      self.initSubscribtions();

      $(window)
        .hashchange(self.hashChanged)
        .hashchange()
        .unload(self.unload);

      self.connectionCheck();

      if (typeof LUPAPISTE.ModalDialog !== "undefined") {
        LUPAPISTE.ModalDialog.init();
      }

      $(document.documentElement).keyup(function(event) { hub.send("keyup", event); });

      function openStartPage() {
        if (self.logoPath) {
          window.location = window.location.protocol + "//" + window.location.host + self.logoPath;
        } else if (self.startPage && self.startPage.charAt(0) !== "/") {
          if (self.currentHash === self.startPage) {
            // trigger start page re-rendering
            self.previousHash = self.currentHash;
            self.openPage([self.startPage]);
          } else {
            // open normally
            pageutil.openPage(self.startPage);
          }
        } else {
          // fallback
          window.location.href = startPageHref;
        }
      }

      var model = {
        currentLanguage: loc.getCurrentLanguage(),
        openStartPage: openStartPage,
        showUserMenu: self.showUserMenu,
        showArchiveMenuOptions: self.showArchiveMenuOptions,
        showCalendarMenuOptions: self.showCalendarMenuOptions,
        // TODO: sync with side-panel.js sidePanelPages
        sidePanelPages: ["application","attachment","statement","neighbors","verdict"]
      };

      $("#app").applyBindings(lupapisteApp.models.rootVMO);

      if (LUPAPISTE.Screenmessage) {
          LUPAPISTE.Screenmessage.refresh();
          model.screenMessage = LUPAPISTE.Screenmessage;
      }
      $(".brand").applyBindings( model );
      $(".header-menu").applyBindings( model ).css( "visibility", "visible");
      $("#sys-notification").applyBindings( model );
      $("footer").applyBindings(model).css("visibility", "visible");
    };
  };

})(jQuery);
