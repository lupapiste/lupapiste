var LUPAPISTE = LUPAPISTE || {};

// Hax. Should depend on currentUser.js
var currentUser = currentUser || {set: function() {}, get: function() {}, isAuthority: function() {return false;}};

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
          currentUser.set(e.user);
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
      LUPAPISTE.ModalDialog.open("#session-dead-dialog");
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

     var model = {
       languages: loc.getSupportedLanguages(),
       currentLanguage: loc.getCurrentLanguage(),
       changeLanguage: function(lang) {hub.send("change-lang", { lang: lang });},
       startPage: self.startPage,
       allowAnonymous: self.allowAnonymous
     };

     $("nav").applyBindings(model);

     function showApplicationList() {
       pageutil.hideAjaxWait();
       window.location.hash = "!/applications";
     }

     $("<div id='session-dead-dialog' class='window autosized-yes-no'>" +
         "<div class='dialog-header'>" +
           "<p class='dialog-title'></p>" +
         "</div>" +
         "<div class='dialog-content'>" +
           "<p></p>" +
           "<button class='btn btn-primary btn-dialog logout'></button>" +
         "</div>" +
       "</div>")
       .find(".dialog-title").text(loc("session-dead.title")).end()
       .find(".dialog-content p").text(loc("session-dead.message")).end()
       .find(".dialog-content button").text(loc("session-dead.logout")).end()
       .find(".logout").click(function() { hub.send("logout"); return false; }).end()
       .appendTo($("body"));
   };

};
