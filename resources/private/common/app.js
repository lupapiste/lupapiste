var LUPAPISTE = LUPAPISTE || {};

/**
 * Prototype for Lupapiste Single Page Apps.
 *
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

      var page = $("#" + pageId);
      if (page.length === 0) {
        error("Unknown page", pageId);
        // firefox bug: does not compute with hashbangs (LUPA-80)
        pageId = allowAnonymous ? "login" : pageId = "404";
        pagePath = [];
        page = $("#" + pageId);
      }

      page.addClass("visible");
      window.scrollTo(0, 0);
      self.currentPage = pageId;
    }

    hub.send("page-change", { pageId: pageId, pagePath: pagePath });
  };

  self.hashChanged = function () {
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

  self.connectionCheck = function () {
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

  self.initSubscribtions = function() {
    hub.subscribe("connection-online", function () {
      $(".connection-error").hide();
    });

    hub.subscribe("connection-offline", function () {
      $(".connection-error").show();
    });

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

     var model = {
       languages: loc.getSupportedLanguages(),
       currentLanguage: loc.getCurrentLanguage(),
       changeLanguage: function(lang) {hub.send("change-lang", { lang: lang });},
       startPage: self.startPage,
       allowAnonymous: self.allowAnonymous
     };
     $("nav").applyBindings(model);
   };

};
