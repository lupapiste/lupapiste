var LUPAPISTE = LUPAPISTE || {};

(function($) {
  "use strict";

  var startPageHref = window.location.href;

  /**
   * Prototype for Lupapiste Single Page Apps.
   *
   * params:
   * startPage (String)        ID of the landing page
   * allowAnonymous (Boolean)  Allow all users to access the app. Default: require login.
   * showUserMenu (Boolean)    Default: complement of allowAnonymous, i.e., show menu for users tthat have logged in
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
    self.previousHash = "";
    self.currentHash = "";

    // Global models
    self.models = {};

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
        window.scrollTo(0, 0);
        self.currentPage = pageId;

        // Reset title. Pages can override title when they handle page-load event.
        document.title = self.defaultTitle;

        // Set focus on the first field
        util.autofocus(page$);
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
      if (self.currentHash === "") {
        if (_.isFunction(window.location.replace)) {
          window.location.replace(startPageHref + "#!/" + self.startPage);
        } else {
          window.location.hash = "!/" + self.startPage;
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
        .error(function() {
          hub.send("connection", {status: "session-dead"});
        })
        .fail(function() {
          hub.send("connection", {status: "offline"});
          setTimeout(self.connectionCheck, 2000);
        })
        .call();
    };

    self.redirectToHashbang = function() {
      var href = window.location.href;
      var hash = window.location.hash;
      if (hash && hash.length > 0) {
        var withoutHash = href.substring(0, href.indexOf("#"));
        window.location = withoutHash + "?redirect-after-login=" + encodeURIComponent(hash.substring(1, hash.length));
      } else {
        // No hashbang. Go directly to front page.
        window.location = "/app/" + loc.getCurrentLanguage();
      }
      return false;
    };

    var offline = false;
    var wasLoggedIn = false;

    hub.subscribe("login", function() { wasLoggedIn = true; });

    hub.subscribe({type: "connection", status: "online"}, function () {
      if (offline) {
        offline = false;
        pageutil.hideAjaxWait();
      }
    });

    hub.subscribe({type: "connection", status: "offline"}, function () {
      if (!offline) {
        offline = true;
        pageutil.showAjaxWait(loc("connection.offline"));
      }
    });

    hub.subscribe({type: "connection", status: "session-dead"}, function () {
      if (wasLoggedIn) {
        LUPAPISTE.ModalDialog.mask.unbind("click");
        LUPAPISTE.ModalDialog.showDynamicOk(loc("session-dead.title"), loc("session-dead.message"),
            {title: loc("session-dead.logout"), fn: self.redirectToHashbang});
      }
    });

    self.initSubscribtions = function() {
      hub.subscribe({type: "keyup", keyCode: 27}, LUPAPISTE.ModalDialog.close);
      hub.subscribe("logout", function () {
        window.location = "/app/" + loc.getCurrentLanguage() + "/logout";
      });
    };

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
            window.location.hash = "!/" + self.startPage;
          }
        } else {
          // fallback
          window.location.href = startPageHref;
        }
      }

      var model = {
        languages: loc.getSupportedLanguages(),
        currentLanguage: loc.getCurrentLanguage(),
        changeLanguage: function(lang) {hub.send("change-lang", { lang: lang });},
        openStartPage: openStartPage,
        showUserMenu: self.showUserMenu
      };

      if (LUPAPISTE.Screenmessage) {
        LUPAPISTE.Screenmessage.refresh();
        model.screenMessage = LUPAPISTE.Screenmessage;
      }

      $("#app").applyBindings(lupapisteApp.models.rootVMO);
      $("nav").applyBindings(model).css("visibility", "visible");
      $("footer").applyBindings(model).css("visibility", "visible");
    };
  };

})(jQuery);
