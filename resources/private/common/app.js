/**
 * Prototype for Lupapiste Single Page Apps
 */

if (typeof LUPAPISTE === "undefined") {
  var LUPAPISTE = {};
}

/**
 * @param {String} startPage   ID of the landing page
 * @param {Boolean} allowAnonymous  Allow all users to access the app. Default: require login.
 */
LUPAPISTE.App = function (startPage, allowAnonymous) {

  "use strict";

  var self = this;

  self.startPage = startPage;
  self.currentPage = undefined;
  self.session = undefined;
  self.allowAnonymous = allowAnonymous;

  this.createLogo = function () {
    var href = "#!/" + self.startPage;
    var link$ = $("<a class='brand' href='" + href + "'></a>");
    link$.append("<img src='/img/logo.png' alt='Lupapiste.fi'/>");
    var naviLinks$ = $("<span>").attr("id", "navi-right");
    _.each(loc.getSupportedLanguages(), function (lang) {
      if (lang !== loc.getCurrentLanguage()) {
        naviLinks$.append(
            $("<a>").attr("href", "#").text(loc("in_" + lang ) +" >>")
            .click(function (e) {
              hub.send("change-lang", { lang: lang });
              e.preventDefault();
            }));
      }
    });
    link$.append(naviLinks$);
    return link$;
  };

  this.createConnectionErrorContainer = function () {
    var span$ = $("<span class='connection-error' style='display: none;'></span>");
    return span$.text(loc("connection-error"));
  };

  this.createUserMenu = function () {
    var userMenu$ = $("<div class='user-menu'><a href='#!/mypage'><span id='user-name'></span></a>");
    if (!self.allowAnonymous) {
      userMenu$.append(" ");
      userMenu$.append($("<a>")
        .attr("href", "/" + loc.getCurrentLanguage() + "/logout")
        .text(loc("logout")));
    }
    return userMenu$;
  };

  this.createNaviLinks = function () {
    var naviLinks$ = $("<span>").attr("id", "main-nav");
    return naviLinks$;
  };

  /**
  * Complete the App initialization after DOM is loaded.
  */
  this.domReady = function () {
    $(window).hashchange(self.hashChanged);
    $(window).hashchange();
    $(window).unload(self.unload);

    self.connectionCheck();

    if (typeof LUPAPISTE.ModalDialog !== "undefined") {
      LUPAPISTE.ModalDialog.init();
    }
    var navWrapper = $("<div class='nav-wrapper'></div>");
    navWrapper.append(self.createLogo()).append(self.createConnectionErrorContainer());
    if (!self.allowAnonymous) {
      navWrapper.append(self.createUserMenu());
    }
    navWrapper.append(self.createNaviLinks());
    $("nav").append(navWrapper)
  };
  $(this.domReady);

  /**
  * Window unload event handler
  */
  this.unload = function () {
    trace("window.unload");
  };

  this.openPage = function (path) {
    var pageId = path[0];
    var pagePath = path.splice(1, path.length - 1);

    trace("pageId", pageId, "pagePath", pagePath);

    if (pageId !== self.currentPage) {

      $(".page").removeClass("visible");

      var page = $("#" + pageId);
      if (page.length === 0) {
        error("Unknown page", pageId);
        pageId = "404";
        pagePath = [];
        page = $("#" + pageId);
      }

      page.addClass("visible");
      window.scrollTo(0, 0);
      self.currentPage = pageId;
    }

    hub.send("page-change", { pageId: pageId, pagePath: pagePath });
  };

  this.hashChanged = function () {
    trace("hash changed");

    var hash = (location.hash || "").substr(3);

    if (hash === "") {
      window.location.hash = "!/" + self.startPage;
      return;
    }

    var path = hash.split("/");

    if (!self.allowAnonymous && self.session === undefined) {
      trace("session === undefined", hash, path);
      ajax.query("user")
        .success(function (e) {
          self.session = true;
          hub.send("login", e);
          self.hashChanged();
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

  this.connectionCheck = function () {
    /*
    ajax.get("/api/ping")
    .success(function() {
    hub.send("connection-online");
    setTimeout(self.connectionCheck, 15000);
    })
    .fail(function() {
    hub.send("connection-offline");
    setTimeout(self.connectionCheck, 5000);
    })
    .call();
    */
  };

  hub.subscribe("connection-online", function () {
    $(".connection-error").hide();
  });

  hub.subscribe("connection-offline", function () {
    $(".connection-error").show();
  });

  hub.subscribe("logout", function () {
    window.location = "/" + loc.getCurrentLanguage() + "/logout";
  });

};
